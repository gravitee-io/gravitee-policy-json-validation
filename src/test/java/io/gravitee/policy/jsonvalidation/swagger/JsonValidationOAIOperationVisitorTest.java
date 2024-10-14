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

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.policy.api.swagger.Policy;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import java.util.HashMap;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * @author Florent CHAMFROY (florent.chamfroy at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class JsonValidationOAIOperationVisitorTest {

    private JsonValidationOAIOperationVisitor visitor = new JsonValidationOAIOperationVisitor();

    @Test
    public void operationWithoutRequestBody() {
        Operation operationMock = mock(Operation.class);

        when(operationMock.getRequestBody()).thenReturn(null);
        Optional<Policy> policy = visitor.visit(mock(OpenAPI.class), operationMock);
        assertFalse(policy.isPresent());
    }

    @Test
    public void operationWithoutApplicationJsonRequestBody() {
        Operation operationMock = mock(Operation.class);

        Content content = mock(Content.class);
        RequestBody requestBody = mock(RequestBody.class);
        when(operationMock.getRequestBody()).thenReturn(requestBody);
        when(requestBody.getContent()).thenReturn(content);
        when(content.get("application/json")).thenReturn(null);

        Optional<Policy> policy = visitor.visit(mock(OpenAPI.class), operationMock);
        assertFalse(policy.isPresent());
    }

    @Test
    public void operationWithEmptyRequestBody() {
        Operation operationMock = mock(Operation.class);

        MediaType applicationJson = mock(MediaType.class);
        Content content = mock(Content.class);
        RequestBody requestBody = mock(RequestBody.class);
        when(operationMock.getRequestBody()).thenReturn(requestBody);
        when(requestBody.getContent()).thenReturn(content);
        when(content.get("application/json")).thenReturn(applicationJson);

        try (MockedStatic<Json> theMock = Mockito.mockStatic(Json.class)) {
            theMock.when(() -> Json.pretty(any(Schema.class))).thenReturn("");
            Optional<Policy> policy = visitor.visit(mock(OpenAPI.class), operationMock);
            assertFalse(policy.isPresent());
        }
    }

    @Test
    public void operationWithJsonRequestBody() throws Exception {
        final String jsonSchema = "a beautiful json schema";

        Operation operationMock = mock(Operation.class);

        Schema schema = mock(Schema.class);
        MediaType applicationJson = mock(MediaType.class);
        Content content = mock(Content.class);
        RequestBody requestBody = mock(RequestBody.class);
        when(operationMock.getRequestBody()).thenReturn(requestBody);
        when(requestBody.getContent()).thenReturn(content);
        when(content.get("application/json")).thenReturn(applicationJson);
        when(applicationJson.getSchema()).thenReturn(schema);

        try (MockedStatic<Json> theMock = Mockito.mockStatic(Json.class)) {
            theMock.when(() -> Json.pretty(any(Schema.class))).thenReturn(jsonSchema);

            Optional<Policy> policy = visitor.visit(mock(OpenAPI.class), operationMock);
            assertTrue(policy.isPresent());

            String configuration = policy.get().getConfiguration();
            assertNotNull(configuration);
            HashMap readConfig = new ObjectMapper().readValue(configuration, HashMap.class);
            assertEquals(jsonSchema, readConfig.get("schema"));
        }
    }
}
