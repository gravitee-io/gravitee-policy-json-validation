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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import io.gravitee.gateway.reactive.api.context.kafka.KafkaMessageExecutionContext;
import io.gravitee.helpers.SchemaImpl;
import io.gravitee.resource.api.ResourceManager;
import io.gravitee.resource.schema_registry.api.SchemaRegistryResource;
import io.gravitee.validation.kafka.handler.support.KafkaMessageStub;
import io.gravitee.validation.schema.SchemaContractViolationException;
import io.gravitee.validation.schema.SchemaNotFoundException;
import io.reactivex.rxjava3.core.Maybe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests the governance gate: the producer's embedded schema id must resolve to the schema registered under the
 * topic's subject ({@code <topic>-value}) before being accepted.
 */
@ExtendWith(MockitoExtension.class)
class SubjectGatedSchemaResolverTest {

    private static final String SCHEMA = """
        {"type":"record","name":"User","namespace":"io.gravitee.avrovalidation","fields":[{"name":"name","type":"string"}]}
        """;

    private static final String OTHER_SCHEMA = """
        {"type":"record","name":"User","namespace":"io.gravitee.avrovalidation","fields":[{"name":"name","type":"int"}]}
        """;

    // magic(1) + schemaId(4 = 27) + avro body
    private static final byte[] AVRO_MESSAGE = { 0x00, 0x00, 0x00, 0x00, 0x1B, 0x0C, 0x4D, 0x61, 0x63, 0x69, 0x65, 0x6B };
    private static final String SUBJECT = "test-topic-value"; // KafkaMessageStub.topic() == "test-topic"

    @Mock
    private KafkaMessageExecutionContext ctx;

    @Mock
    private ResourceManager resourceManager;

    @Mock
    private SchemaRegistryResource<?> registryResource;

    private SubjectGatedSchemaResolver resolver;

    @BeforeEach
    void setup() {
        lenient().when(ctx.getComponent(ResourceManager.class)).thenReturn(resourceManager);
        lenient().when(resourceManager.getResource(eq("schemas"), eq(SchemaRegistryResource.class))).thenReturn(registryResource);
        resolver = new SubjectGatedSchemaResolver("schemas");
    }

    @Test
    void accepts_when_producer_schema_matches_subject() {
        when(registryResource.getSchemaById("27")).thenReturn(Maybe.just(new SchemaImpl(SCHEMA)));
        when(registryResource.getSchema(eq(SUBJECT), eq("latest"))).thenReturn(Maybe.just(new SchemaImpl(SCHEMA)));

        resolver
            .resolveSchema(ctx, new KafkaMessageStub(AVRO_MESSAGE))
            .test()
            .assertNoErrors()
            .assertValue(schema -> schema.getContent().equals(SCHEMA));
    }

    @Test
    void rejects_when_producer_schema_differs_from_subject() {
        when(registryResource.getSchemaById("27")).thenReturn(Maybe.just(new SchemaImpl(SCHEMA)));
        when(registryResource.getSchema(eq(SUBJECT), eq("latest"))).thenReturn(Maybe.just(new SchemaImpl(OTHER_SCHEMA)));

        resolver.resolveSchema(ctx, new KafkaMessageStub(AVRO_MESSAGE)).test().assertError(SchemaContractViolationException.class);
    }

    @Test
    void rejects_when_producer_id_unknown_in_registry() {
        when(registryResource.getSchemaById("27")).thenReturn(Maybe.empty());

        resolver.resolveSchema(ctx, new KafkaMessageStub(AVRO_MESSAGE)).test().assertError(SchemaNotFoundException.class);
    }

    @Test
    void rejects_when_no_subject_registered() {
        when(registryResource.getSchemaById("27")).thenReturn(Maybe.just(new SchemaImpl(SCHEMA)));
        when(registryResource.getSchema(eq(SUBJECT), eq("latest"))).thenReturn(Maybe.empty());

        resolver.resolveSchema(ctx, new KafkaMessageStub(AVRO_MESSAGE)).test().assertError(SchemaNotFoundException.class);
    }
}
