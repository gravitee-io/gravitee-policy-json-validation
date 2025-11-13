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
package io.gravitee.policy.jsonvalidation.swagger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.policy.api.swagger.Policy;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * @author Florent CHAMFROY (florent.chamfroy at graviteesource.com)
 * @author GraviteeSource Team
 */
class JsonValidationOAIOperationVisitorTest {

    private final JsonValidationOAIOperationVisitor visitor = new JsonValidationOAIOperationVisitor();

    @Test
    void operationWithoutRequestBody() {
        Operation operationMock = mock(Operation.class);

        when(operationMock.getRequestBody()).thenReturn(null);
        Optional<Policy> policy = visitor.visit(mock(OpenAPI.class), operationMock);
        assertThat(policy).isEmpty();
    }

    @Test
    void operationWithoutApplicationJsonRequestBody() {
        Operation operationMock = mock(Operation.class);

        Content content = mock(Content.class);
        RequestBody requestBody = mock(RequestBody.class);
        when(operationMock.getRequestBody()).thenReturn(requestBody);
        when(requestBody.getContent()).thenReturn(content);
        when(content.get("application/json")).thenReturn(null);

        Optional<Policy> policy = visitor.visit(mock(OpenAPI.class), operationMock);
        assertThat(policy).isEmpty();
    }

    @Test
    void operationWithEmptyRequestBody() {
        Operation operationMock = mock(Operation.class);

        MediaType applicationJson = mock(MediaType.class);
        Content content = mock(Content.class);
        RequestBody requestBody = mock(RequestBody.class);
        when(operationMock.getRequestBody()).thenReturn(requestBody);
        when(requestBody.getContent()).thenReturn(content);
        when(content.get("application/json")).thenReturn(applicationJson);
        when(applicationJson.getSchema()).thenReturn(mock(Schema.class));
        when(applicationJson.getSchema().getSpecVersion()).thenReturn(SpecVersion.V30);

        try (MockedStatic<Json> theMock = Mockito.mockStatic(Json.class)) {
            theMock.when(() -> Json.pretty(any(Schema.class))).thenReturn("");
            Optional<Policy> policy = visitor.visit(mock(OpenAPI.class), operationMock);
            assertThat(policy).isEmpty();
        }
    }

    @Test
    void operationWithJsonRequestBody() throws Exception {
        final String jsonSchema = "a beautiful json schema";

        Operation operationMock = mock(Operation.class);

        MediaType applicationJson = mock(MediaType.class);
        Content content = mock(Content.class);
        RequestBody requestBody = mock(RequestBody.class);
        when(operationMock.getRequestBody()).thenReturn(requestBody);
        when(requestBody.getContent()).thenReturn(content);
        when(content.get("application/json")).thenReturn(applicationJson);
        when(applicationJson.getSchema()).thenReturn(mock(Schema.class));

        try (MockedStatic<Json> theMock = Mockito.mockStatic(Json.class)) {
            theMock.when(() -> Json.pretty(any(Schema.class))).thenReturn(jsonSchema);

            Optional<Policy> policy = visitor.visit(mock(OpenAPI.class), operationMock);
            assertThat(policy).isPresent();

            String configuration = policy.get().getConfiguration();
            assertThat(configuration).isNotNull();
            Map<String, String> readConfig = new ObjectMapper().readValue(configuration, new TypeReference<>() {});
            assertThat(readConfig).containsEntry("schema", jsonSchema);
        }
    }

    @Test
    void operationWithSchemaV31JsonRequestBody() throws Exception {
        final String jsonSchema = "a beautiful json schema";

        Operation operationMock = mock(Operation.class);

        MediaType applicationJson = mock(MediaType.class);
        Content content = mock(Content.class);
        RequestBody requestBody = mock(RequestBody.class);
        when(operationMock.getRequestBody()).thenReturn(requestBody);
        when(requestBody.getContent()).thenReturn(content);
        when(content.get("application/json")).thenReturn(applicationJson);
        when(applicationJson.getSchema()).thenReturn(mock(Schema.class));
        when(applicationJson.getSchema().getSpecVersion()).thenReturn(SpecVersion.V31);

        try (MockedStatic<Json31> theMock = Mockito.mockStatic(Json31.class)) {
            theMock.when(() -> Json31.pretty(any(Schema.class))).thenReturn(jsonSchema);

            Optional<Policy> policy = visitor.visit(mock(OpenAPI.class), operationMock);
            assertThat(policy).isPresent();

            String configuration = policy.get().getConfiguration();
            assertThat(configuration).isNotNull();
            Map<String, String> readConfig = new ObjectMapper().readValue(configuration, new TypeReference<>() {});
            assertThat(readConfig).containsEntry("schema", jsonSchema);
        }
    }

    @Nested
    class IntegrationTest {

        private final JsonValidationOAIOperationVisitor visitor = new JsonValidationOAIOperationVisitor();

        @Test
        void operationWithOpenAPI31TypeArraySchema() throws Exception {
            // Load the OpenAPI 3.1 spec file with type as array
            InputStream specStream = getClass().getResourceAsStream("/openapi-v31-with-type-array.yaml");
            assertThat(specStream).isNotNull();

            String specContent = new String(specStream.readAllBytes(), StandardCharsets.UTF_8);

            // Parse the OpenAPI spec with resolveFully option (as mentioned in the visitor comment)
            ParseOptions options = new ParseOptions();
            options.setResolveFully(true);
            SwaggerParseResult parseResult = new OpenAPIV3Parser().readContents(specContent, null, options);

            OpenAPI openAPI = parseResult.getOpenAPI();
            assertThat(openAPI).isNotNull();
            assertThat(parseResult.getMessages()).isEmpty();

            // Get the POST operation from /pets path
            Operation operation = openAPI.getPaths().get("/pets").getPost();
            assertThat(operation).isNotNull();
            assertThat(operation.getRequestBody()).isNotNull();

            // Verify the schema has SpecVersion.V31
            Schema<?> schema = operation.getRequestBody().getContent().get("application/json").getSchema();
            assertThat(schema.getSpecVersion()).isEqualTo(SpecVersion.V31);

            // Visit the operation with the visitor
            Optional<Policy> policy = visitor.visit(openAPI, operation);

            // Verify policy is created
            assertThat(policy).isPresent();

            // Verify the policy configuration contains the expected schema with type as array
            String configuration = policy.get().getConfiguration();
            assertThat(configuration).isEqualTo(
                """
                {
                  "scope" : "REQUEST_CONTENT",
                  "schema" : "{\\n  \\"type\\" : [ \\"integer\\", \\"null\\" ],\\n  \\"description\\" : \\"The age of the pet in months. Can be null if the age is unknown.\\",\\n  \\"title\\" : \\"PetAge\\"\\n}",
                  "validateUnchecked" : false,
                  "deepCheck" : false,
                  "straightRespondMode" : false
                }"""
            );
        }
    }
}
