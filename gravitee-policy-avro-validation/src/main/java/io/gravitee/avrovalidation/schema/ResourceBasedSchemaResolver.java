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
import io.gravitee.el.TemplateEngine;
import io.gravitee.gateway.reactive.api.context.kafka.KafkaMessageExecutionContext;
import io.gravitee.gateway.reactive.api.message.kafka.KafkaMessage;
import io.gravitee.resource.api.ResourceManager;
import io.gravitee.resource.schema_registry.api.SchemaRegistryResource;
import io.gravitee.validation.kafka.wireformat.EmbeddedSchemaRef;
import io.gravitee.validation.kafka.wireformat.WireFormatExtractor;
import io.gravitee.validation.kafka.wireformat.WireFormatExtractorFactory;
import io.gravitee.validation.schema.SchemaReference;
import io.gravitee.validation.schema.SchemaReferenceExtractor;
import io.gravitee.validation.schema.SchemaRegistryBasedResolver;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * EXPRESSION resolver: resolves the schema by an Expression Language mapping that evaluates to a schema subject and
 * version (e.g. derived from {@code #message.topic}). The schema is chosen by the operator-configured mapping; the
 * producer's embedded id is ignored. The wire format is still used to locate where the Avro payload begins (envelope
 * to skip) — use {@code NONE} for bare Avro.
 *
 * @author GraviteeSource Team
 */
public class ResourceBasedSchemaResolver implements AvroSchemaResolver {

    private final SchemaRegistryBasedResolver<KafkaMessageExecutionContext, KafkaMessage> delegate;
    private final WireFormatExtractor wireFormatExtractor;

    public ResourceBasedSchemaResolver(AvroValidationPolicyConfiguration configuration) {
        this(
            configuration.getSchemaSource().getResourceName(),
            configuration.getSchemaIdEvalString(),
            configuration.getSchemaVersionEvalString(),
            WireFormatExtractorFactory.create(configuration.getWireFormat())
        );
    }

    public ResourceBasedSchemaResolver(
        String resourceName,
        String schemaIdEvalString,
        String schemaVersionEvalString,
        WireFormatExtractor wireFormatExtractor
    ) {
        this.delegate = new SchemaRegistryBasedResolver<>(
            context -> schemaRegistryResource(context, resourceName),
            (SchemaReferenceExtractor<KafkaMessageExecutionContext, KafkaMessage>) (context, message) ->
                resolveSchemaReference(context.getTemplateEngine(message), schemaIdEvalString, schemaVersionEvalString)
        );
        this.wireFormatExtractor = wireFormatExtractor;
    }

    @Override
    public Single<ResolvedSchema> resolveSchema(KafkaMessageExecutionContext context, KafkaMessage message) {
        // Schema comes from the EL mapping; the wire format only provides where the Avro body starts.
        return Single.zip(
            delegate.resolveSchema(context, message),
            wireFormatExtractor.extract(message).map(EmbeddedSchemaRef::payloadOffset).toSingle(),
            ResolvedSchema::new
        );
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
