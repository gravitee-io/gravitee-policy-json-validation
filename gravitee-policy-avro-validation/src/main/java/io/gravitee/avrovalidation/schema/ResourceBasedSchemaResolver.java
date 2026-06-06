/*
 * Copyright © 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.avrovalidation.schema;

import io.gravitee.avrovalidation.configuration.AvroValidationPolicyConfiguration;
import io.gravitee.avrovalidation.configuration.schema.SerializationForm;
import io.gravitee.el.TemplateEngine;
import io.gravitee.gateway.reactive.api.context.kafka.KafkaMessageExecutionContext;
import io.gravitee.gateway.reactive.api.message.kafka.KafkaMessage;
import io.gravitee.resource.api.ResourceManager;
import io.gravitee.resource.schema_registry.api.SchemaRegistryResource;
import io.gravitee.validation.schema.SchemaReference;
import io.gravitee.validation.schema.SchemaReferenceExtractor;
import io.gravitee.validation.schema.SchemaRegistryBasedResolver;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * EXPRESSION resolver: resolves the schema by an Expression Language mapping that evaluates to a schema subject and
 * version (e.g. derived from {@code #message.topic}). The schema is fetched by subject from the schema registry
 * resource — the authority is the operator-configured mapping; the producer's embedded id is ignored.
 *
 * @author GraviteeSource Team
 */
public class ResourceBasedSchemaResolver implements AvroSchemaResolver {

    private static final int CONFLUENT_ENVELOPE_LENGTH = 5;

    private final SchemaRegistryBasedResolver<KafkaMessageExecutionContext, KafkaMessage> delegate;
    private final int payloadOffset;

    public ResourceBasedSchemaResolver(AvroValidationPolicyConfiguration configuration) {
        this(
            configuration.getSchemaSource().getResourceName(),
            configuration.getSchemaIdEvalString(),
            configuration.getSchemaVersionEvalString(),
            configuration.getSerializationForm()
        );
    }

    public ResourceBasedSchemaResolver(
        String resourceName,
        String schemaIdEvalString,
        String schemaVersionEvalString,
        SerializationForm serializationForm
    ) {
        this.delegate = new SchemaRegistryBasedResolver<>(
            context -> schemaRegistryResource(context, resourceName),
            (SchemaReferenceExtractor<KafkaMessageExecutionContext, KafkaMessage>) (context, message) ->
                resolveSchemaReference(context.getTemplateEngine(message), schemaIdEvalString, schemaVersionEvalString)
        );
        // SIMPLE = bare Avro (no envelope); otherwise the Confluent envelope (magic + 4-byte id) precedes the body.
        this.payloadOffset = serializationForm == SerializationForm.SIMPLE ? 0 : CONFLUENT_ENVELOPE_LENGTH;
    }

    @Override
    public Single<ResolvedSchema> resolveSchema(KafkaMessageExecutionContext context, KafkaMessage message) {
        return delegate.resolveSchema(context, message).map(schema -> new ResolvedSchema(schema, payloadOffset));
    }

    private static SchemaRegistryResource<?> schemaRegistryResource(KafkaMessageExecutionContext context, String resourceName) {
        return context.getComponent(ResourceManager.class).getResource(resourceName, SchemaRegistryResource.class);
    }

    private static Maybe<SchemaReference> resolveSchemaReference(
        TemplateEngine templateEngine,
        String schemaIdEvalString,
        String schemaVersionEvalString
    ) {
        return templateEngine
            .eval(schemaIdEvalString, String.class)
            .flatMap(id -> templateEngine.eval(schemaVersionEvalString, String.class).map(version -> new SchemaReference(id, version)));
    }
}
