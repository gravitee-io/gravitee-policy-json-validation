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
import io.gravitee.gateway.reactive.api.context.kafka.KafkaMessageExecutionContext;
import io.gravitee.gateway.reactive.api.message.kafka.KafkaMessage;
import io.gravitee.resource.api.ResourceManager;
import io.gravitee.resource.schema_registry.api.Schema;
import io.gravitee.resource.schema_registry.api.SchemaRegistryResource;
import io.gravitee.validation.schema.SchemaContractViolationException;
import io.gravitee.validation.schema.SchemaNotFoundException;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.apache.avro.SchemaNormalization;

/**
 * Governance resolver: reads the producer's embedded Confluent schema id, then verifies the referenced schema
 * matches the schema registered under the topic's subject (Confluent {@code TopicNameStrategy}: {@code <topic>-value})
 * before accepting it. The subject is the authority — a producer cannot validate against an arbitrary registered schema.
 *
 * <p>On a match, the producer's (writer) schema is returned so the payload can be decoded correctly, including
 * across compatible schema versions. On a mismatch (or when the id / subject cannot be resolved) the lookup fails,
 * which is routed through the policy's configured error-handling strategy.
 *
 * <p><strong>v1 limitation:</strong> the producer schema is compared against the subject's {@code latest} version
 * (canonical form). Accepting any version registered under the subject requires a schema-registry-resource
 * subject/version membership lookup that is not yet available.
 */
public class SubjectGatedSchemaResolver implements AvroSchemaResolver {

    // Confluent TopicNameStrategy for the record value.
    private static final String VALUE_SUBJECT_SUFFIX = "-value";
    private static final String LATEST_VERSION = "latest";

    private final String resourceName;

    public SubjectGatedSchemaResolver(AvroValidationPolicyConfiguration configuration) {
        this(configuration.getSchemaSource().getResourceName());
    }

    public SubjectGatedSchemaResolver(String resourceName) {
        this.resourceName = resourceName;
    }

    @Override
    public Single<Schema> resolveSchema(KafkaMessageExecutionContext context, KafkaMessage message) {
        final SchemaRegistryResource<?> resource = context
            .getComponent(ResourceManager.class)
            .getResource(resourceName, SchemaRegistryResource.class);
        if (resource == null) {
            return Single.error(new IllegalStateException("Unable to resolve schema registry resource"));
        }

        final String subject = message.topic() + VALUE_SUBJECT_SUFFIX;

        return ResourceBasedSchemaResolver.resolveNativeSchemaId(message)
            .flatMap(producerId ->
                resource
                    .getSchemaById(producerId)
                    .switchIfEmpty(Maybe.error(new SchemaNotFoundException(producerId)))
                    .flatMap(writerSchema ->
                        resource
                            .getSchema(subject, LATEST_VERSION)
                            .switchIfEmpty(Maybe.error(new SchemaNotFoundException(subject, LATEST_VERSION)))
                            .map(subjectSchema -> {
                                // Fast path: identical registry id => same schema, no parsing needed. Only fall back to
                                // canonical-form comparison (which parses both schemas) when the ids differ. This keeps
                                // the common case cheap, consistent with the policy's per-message parsing trade-off.
                                boolean matches =
                                    producerId.equals(subjectSchema.getId()) || sameCanonicalForm(writerSchema, subjectSchema);
                                if (!matches) {
                                    throw new SchemaContractViolationException(
                                        "Producer schema (id %s) is not the schema registered for subject %s".formatted(producerId, subject)
                                    );
                                }
                                // Decode with the producer's validated writer schema.
                                return writerSchema;
                            })
                    )
            )
            .toSingle();
    }

    private static boolean sameCanonicalForm(Schema writerSchema, Schema subjectSchema) {
        org.apache.avro.Schema parsedWriter = new org.apache.avro.Schema.Parser().parse(writerSchema.getContent());
        org.apache.avro.Schema parsedSubject = new org.apache.avro.Schema.Parser().parse(subjectSchema.getContent());
        return SchemaNormalization.toParsingForm(parsedWriter).equals(SchemaNormalization.toParsingForm(parsedSubject));
    }
}
