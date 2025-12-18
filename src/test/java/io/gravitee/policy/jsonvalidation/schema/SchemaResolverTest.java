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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.fge.jackson.JsonLoader;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.Response;
import io.gravitee.gateway.reactive.api.context.http.HttpMessageExecutionContext;
import io.gravitee.gateway.reactive.api.context.http.HttpPlainExecutionContext;
import io.gravitee.policy.jsonvalidation.configuration.JsonValidationPolicyConfiguration;
import java.io.IOException;
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

    @ExtendWith(MockitoExtension.class)
    @Nested
    class InlineSchemaResolverTest {

        @Mock
        JsonValidationPolicyConfiguration configuration;

        private SchemaResolver resolver;

        @BeforeEach
        void setUp() throws IOException {
            when(configuration.getSchema()).thenReturn(JSON_SCHEMA);
            resolver = SchemaResolverFactory.createSchemaResolver(configuration);
        }

        @Test
        public void testResolveSchema_V2() throws IOException {
            var result = resolver.resolveSchema(mock(ExecutionContext.class), mock(Request.class), mock(Response.class));

            assertThat(result).isEqualTo(JsonLoader.fromString(JSON_SCHEMA));
        }

        @Test
        public void testResolveSchema_V4Proxy() throws IOException {
            var result = resolver.resolveSchema(mock(HttpPlainExecutionContext.class));

            assertThat(result).isEqualTo(JsonLoader.fromString(JSON_SCHEMA));
        }

        @Test
        public void testResolveSchema_V4Message() throws IOException {
            var result = resolver.resolveSchema(mock(HttpMessageExecutionContext.class));

            assertThat(result).isEqualTo(JsonLoader.fromString(JSON_SCHEMA));
        }

        @Test
        public void testResolveSchema_KafkaNative() throws IOException {
            var result = resolver.resolveSchema(mock(HttpMessageExecutionContext.class));

            assertThat(result).isEqualTo(JsonLoader.fromString(JSON_SCHEMA));
        }
    }
}
