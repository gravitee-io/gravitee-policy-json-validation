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
import io.gravitee.resource.schema_registry.api.Schema;
import io.gravitee.resource.schema_registry.api.SchemaRegistryResource;
import io.gravitee.validation.schema.SchemaIdExtractor;
import io.gravitee.validation.schema.SchemaIdSource;
import io.gravitee.validation.schema.SchemaReference;
import io.gravitee.validation.schema.SchemaReferenceExtractor;
import io.gravitee.validation.schema.SchemaRegistryBasedResolver;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.nio.ByteBuffer;

/**
 * @author GraviteeSource Team
 */
public class ResourceBasedSchemaResolver implements SchemaResolver {

    private final SchemaRegistryBasedResolver<KafkaMessageExecutionContext, KafkaMessage> delegate;

    public ResourceBasedSchemaResolver(AvroValidationPolicyConfiguration configuration) {
        this(
            configuration.getSchemaSource().getResourceName(),
            configuration.getSchemaIdSource(),
            configuration.getSchemaIdEvalString(),
            configuration.getSchemaVersionEvalString()
        );
    }

    public ResourceBasedSchemaResolver(String resourceName) {
        this.delegate = new SchemaRegistryBasedResolver<>(
            context -> schemaRegistryResource(context, resourceName),
            (SchemaIdExtractor<KafkaMessageExecutionContext, KafkaMessage>) (context, message) -> resolveNativeSchemaId(message)
        );
    }

    public ResourceBasedSchemaResolver(String resourceName, String schemaIdEvalString, String schemaVersionEvalString) {
        this.delegate = new SchemaRegistryBasedResolver<>(
            context -> schemaRegistryResource(context, resourceName),
            (SchemaReferenceExtractor<KafkaMessageExecutionContext, KafkaMessage>) (context, message) ->
                resolveSchemaReference(context.getTemplateEngine(message), schemaIdEvalString, schemaVersionEvalString)
        );
    }

    private ResourceBasedSchemaResolver(
        String resourceName,
        SchemaIdSource schemaIdSource,
        String schemaIdEvalString,
        String schemaVersionEvalString
    ) {
        this.delegate = SchemaIdSource.EVAL.equals(schemaIdSource)
            ? new SchemaRegistryBasedResolver<>(
                context -> schemaRegistryResource(context, resourceName),
                (SchemaReferenceExtractor<KafkaMessageExecutionContext, KafkaMessage>) (context, message) ->
                    resolveSchemaReference(context.getTemplateEngine(message), schemaIdEvalString, schemaVersionEvalString)
            )
            : new SchemaRegistryBasedResolver<>(
                context -> schemaRegistryResource(context, resourceName),
                (SchemaIdExtractor<KafkaMessageExecutionContext, KafkaMessage>) (context, message) -> resolveNativeSchemaId(message)
            );
    }

    @Override
    public Single<Schema> resolveSchema(KafkaMessageExecutionContext context, KafkaMessage message) {
        return delegate.resolveSchema(context, message);
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

    private static Maybe<String> resolveNativeSchemaId(KafkaMessage message) {
        return Maybe.fromCallable(() -> {
            byte[] bytes = message.content().getBytes();

            if (bytes == null || bytes.length < 5) {
                throw new IllegalArgumentException("Message too short for Confluent framing");
            }
            if (bytes[0] != 0x00) {
                throw new IllegalArgumentException("This is not a confluent message");
            }

            return Integer.toString(ByteBuffer.wrap(bytes, 1, 4).getInt());
        });
    }
}
