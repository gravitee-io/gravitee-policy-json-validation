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
package io.gravitee.policy.jsonvalidation.schema;

import static io.gravitee.policy.jsonvalidation.schema.SchemaResolverFactory.createSchemaResolver;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import io.gravitee.el.TemplateEngine;
import io.gravitee.gateway.reactive.api.context.http.HttpMessageExecutionContext;
import io.gravitee.gateway.reactive.api.context.http.HttpPlainExecutionContext;
import io.gravitee.gateway.reactive.api.context.kafka.KafkaMessageExecutionContext;
import io.gravitee.gateway.reactive.api.message.Message;
import io.gravitee.gateway.reactive.api.message.kafka.KafkaMessage;
import io.gravitee.policy.jsonvalidation.configuration.JsonValidationPolicyConfiguration;
import io.gravitee.policy.jsonvalidation.configuration.schema.SchemaSource;
import io.gravitee.policy.jsonvalidation.configuration.schema.SchemaSourceType;
import io.gravitee.resource.api.ResourceManager;
import io.gravitee.resource.schema_registry.api.SchemaRegistryResource;
import io.reactivex.rxjava3.core.Maybe;
import java.io.IOException;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class SchemaResolverTest {

    private static final String JSON_SCHEMA = """
        {
            "title": "Person",
            "type": "object",
            "properties": {
                "name": {
                    "type": "string"
                }
            },
            "required": ["name"]
        }""";

    @SuppressWarnings("deprecation")
    @ExtendWith(MockitoExtension.class)
    @Nested
    class LegacySchemaResolverTest {

        @Mock
        JsonValidationPolicyConfiguration configuration;

        private SchemaResolver resolver;
        private JsonNode referenceSchema;

        @BeforeEach
        void setUp() throws IOException {
            when(configuration.getSchema()).thenReturn(JSON_SCHEMA);
            resolver = createSchemaResolver(configuration);
            referenceSchema = JsonLoader.fromString(JSON_SCHEMA);
        }

        @Test
        public void testResolveSchema_V4Proxy() {
            resolver
                .resolveSchema(mock(HttpPlainExecutionContext.class))
                .test()
                .assertComplete()
                .assertValue(schema -> Objects.equals(JsonLoader.fromString(schema.getContent()), referenceSchema));
        }

        @Test
        public void testResolveSchema_V4Message() {
            resolver
                .resolveSchema(mock(HttpMessageExecutionContext.class))
                .test()
                .assertComplete()
                .assertValue(schema -> Objects.equals(JsonLoader.fromString(schema.getContent()), referenceSchema));
        }

        @Test
        public void testResolveSchema_KafkaNative() {
            resolver
                .resolveSchema(mock(KafkaMessageExecutionContext.class))
                .test()
                .assertComplete()
                .assertValue(schema -> Objects.equals(JsonLoader.fromString(schema.getContent()), referenceSchema));
        }
    }

    @ExtendWith(MockitoExtension.class)
    @Nested
    class StaticSchemaResolverTest {

        @Mock
        JsonValidationPolicyConfiguration configuration;

        private SchemaResolver resolver;
        private JsonNode referenceSchema;

        @BeforeEach
        void setUp() throws IOException {
            SchemaSource schemaSource = SchemaSource.builder().sourceType(SchemaSourceType.STATIC_SCHEMA).staticSchema(JSON_SCHEMA).build();

            when(configuration.getSchemaSource()).thenReturn(schemaSource);

            resolver = createSchemaResolver(configuration);
            referenceSchema = JsonLoader.fromString(JSON_SCHEMA);
        }

        @Test
        public void testResolveSchema_V4Proxy() {
            resolver
                .resolveSchema(mock(HttpPlainExecutionContext.class))
                .test()
                .assertComplete()
                .assertValue(schema -> Objects.equals(JsonLoader.fromString(schema.getContent()), referenceSchema));
        }

        @Test
        public void testResolveSchema_V4Message() {
            resolver
                .resolveSchema(mock(HttpMessageExecutionContext.class), mock(Message.class))
                .test()
                .assertComplete()
                .assertValue(schema -> Objects.equals(JsonLoader.fromString(schema.getContent()), referenceSchema));
        }

        @Test
        public void testResolveSchema_KafkaNative() {
            resolver
                .resolveSchema(mock(KafkaMessageExecutionContext.class), mock(KafkaMessage.class))
                .test()
                .assertComplete()
                .assertValue(schema -> Objects.equals(JsonLoader.fromString(schema.getContent()), referenceSchema));
        }
    }

    @ExtendWith(MockitoExtension.class)
    @Nested
    class ResourceBasedSchemaResolverTest {

        private static final String TEST_SCHEMA_REGISTRY_RESOURCE_NAME = "testSchemaRegistryResource";
        private static final String TEST_SCHEMA_SUBJECT = "testSchema";

        @Mock
        JsonValidationPolicyConfiguration configuration;

        @Mock
        TemplateEngine templateEngine;

        @Mock
        SchemaRegistryResource<?> schemaRegistryResource;

        @Mock
        ResourceManager resourceManager;

        private JsonNode referenceSchema;

        @BeforeEach
        void setUp() throws IOException {
            referenceSchema = JsonLoader.fromString(JSON_SCHEMA);

            when(resourceManager.getResource(TEST_SCHEMA_REGISTRY_RESOURCE_NAME, SchemaRegistryResource.class)).thenReturn(
                schemaRegistryResource
            );
            when(schemaRegistryResource.getSchema(TEST_SCHEMA_SUBJECT)).thenReturn(
                Maybe.just(new TestSchema(JSON_SCHEMA, TEST_SCHEMA_SUBJECT))
            );
        }

        @Test
        public void testResolveSchema_V4Proxy() {
            HttpPlainExecutionContext ctx = mock(HttpPlainExecutionContext.class);

            when(ctx.getTemplateEngine()).thenReturn(templateEngine);
            when(ctx.getComponent(ResourceManager.class)).thenReturn(resourceManager);

            when(templateEngine.eval("{#request.headers['X-Schema-Name']}", String.class)).thenReturn(Maybe.just(TEST_SCHEMA_SUBJECT));

            when(configuration.getSchemaSource()).thenReturn(
                SchemaSource.builder()
                    .sourceType(SchemaSourceType.SCHEMA_REGISTRY_RESOURCE)
                    .resourceName(TEST_SCHEMA_REGISTRY_RESOURCE_NAME)
                    .schemaMapping("{#request.headers['X-Schema-Name']}")
                    .build()
            );

            createSchemaResolver(configuration)
                .resolveSchema(ctx)
                .test()
                .assertComplete()
                .assertValue(schema -> Objects.equals(JsonLoader.fromString(schema.getContent()), referenceSchema));
        }

        @Test
        public void testResolveSchema_V4Message() {
            Message message = mock(Message.class);
            HttpMessageExecutionContext ctx = mock(HttpMessageExecutionContext.class);

            when(ctx.getTemplateEngine(message)).thenReturn(templateEngine);
            when(ctx.getComponent(ResourceManager.class)).thenReturn(resourceManager);

            when(templateEngine.eval("{#message.attributes['X-Schema-Name']}", String.class)).thenReturn(Maybe.just(TEST_SCHEMA_SUBJECT));

            when(configuration.getSchemaSource()).thenReturn(
                SchemaSource.builder()
                    .sourceType(SchemaSourceType.SCHEMA_REGISTRY_RESOURCE)
                    .resourceName(TEST_SCHEMA_REGISTRY_RESOURCE_NAME)
                    .schemaMapping("{#message.attributes['X-Schema-Name']}")
                    .build()
            );

            createSchemaResolver(configuration)
                .resolveSchema(ctx, message)
                .test()
                .assertComplete()
                .assertValue(schema -> Objects.equals(JsonLoader.fromString(schema.getContent()), referenceSchema));
        }

        @Test
        public void testResolveSchema_KafkaNative() {
            KafkaMessage kafkaMessage = mock(KafkaMessage.class);
            KafkaMessageExecutionContext ctx = mock(KafkaMessageExecutionContext.class);

            when(ctx.getTemplateEngine(kafkaMessage)).thenReturn(templateEngine);
            when(ctx.getComponent(ResourceManager.class)).thenReturn(resourceManager);

            when(templateEngine.eval("{#message.topic}", String.class)).thenReturn(Maybe.just(TEST_SCHEMA_SUBJECT));

            when(configuration.getSchemaSource()).thenReturn(
                SchemaSource.builder()
                    .sourceType(SchemaSourceType.SCHEMA_REGISTRY_RESOURCE)
                    .resourceName(TEST_SCHEMA_REGISTRY_RESOURCE_NAME)
                    .schemaMapping("{#message.topic}")
                    .build()
            );

            createSchemaResolver(configuration)
                .resolveSchema(ctx, kafkaMessage)
                .test()
                .assertComplete()
                .assertValue(schema -> Objects.equals(JsonLoader.fromString(schema.getContent()), referenceSchema));
        }
    }
}
