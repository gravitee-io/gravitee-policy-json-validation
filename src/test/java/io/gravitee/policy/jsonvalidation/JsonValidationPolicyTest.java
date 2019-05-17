/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.policy.jsonvalidation;

import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.util.ServiceLoaderHelper;
import io.gravitee.el.TemplateEngine;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.Response;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.buffer.BufferFactory;
import io.gravitee.gateway.api.stream.ReadWriteStream;
import io.gravitee.policy.api.PolicyChain;
import io.gravitee.policy.api.PolicyResult;
import io.gravitee.reporter.api.http.Metrics;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class JsonValidationPolicyTest {

    @Mock
    private Request mockRequest;

    @Mock
    private Response mockResponse;

    @Mock
    private ExecutionContext mockExecutionContext;

    @Mock
    private PolicyChain mockPolicychain;

    @Mock
    private JsonValidationPolicyConfiguration configuration;

    private BufferFactory factory;

    private String jsonschema = "{\n" +
            "    \"title\": \"Person\",\n" +
            "    \"type\": \"object\",\n" +
            "    \"properties\": {\n" +
            "        \"name\": {\n" +
            "            \"type\": \"string\"\n" +
            "        }\n" +
            "    },\n" +
            "    \"required\": [\"name\"]\n" +
            "}";

    private Metrics metrics;

    private JsonValidationPolicy policy;

    @Before
    public void beforeAll() {
        factory = ServiceLoaderHelper.loadFactory(BufferFactory.class);
        metrics = Metrics.on(System.currentTimeMillis()).build();

        when(configuration.getErrorMessage()).thenReturn("{\"msg\":\"error\"}");
        when(configuration.getSchema()).thenReturn(jsonschema);
        when(mockRequest.metrics()).thenReturn(metrics);
        when(mockExecutionContext.getTemplateEngine()).thenReturn(TemplateEngine.templateEngine());

        policy = new JsonValidationPolicy(configuration);
    }

    @Test
    public void shouldAcceptValidPayload() {
        assertThatCode(() -> {
            JsonValidationPolicy policy = new JsonValidationPolicy(configuration);
            Buffer buffer = factory.buffer("{\"name\":\"foo\"}");
            ReadWriteStream readWriteStream = policy.onRequestContent(mockRequest, mockResponse, mockExecutionContext, mockPolicychain);
            readWriteStream.write(buffer);
            readWriteStream.end();
        }).doesNotThrowAnyException();
    }

    @Test
    public void shouldValidateRejectInvalidPayload() {
        ArgumentCaptor<PolicyResult> policyResult = ArgumentCaptor.forClass(PolicyResult.class);

        Buffer buffer = factory.buffer("{\"name\":1}");
        ReadWriteStream readWriteStream = policy.onRequestContent(mockRequest, mockResponse, mockExecutionContext, mockPolicychain);
        readWriteStream.write(buffer);
        readWriteStream.end();

        policyAssertions(JsonValidationPolicy.JSON_INVALID_PAYLOAD_KEY);
    }

    @Test
    public void shouldValidateUncheckedRejectInvalidPayload() {
        ArgumentCaptor<PolicyResult> policyResult = ArgumentCaptor.forClass(PolicyResult.class);

        Buffer buffer = factory.buffer("{\"name\":1}");
        ReadWriteStream readWriteStream = policy.onRequestContent(mockRequest, mockResponse, mockExecutionContext, mockPolicychain);
        readWriteStream.write(buffer);
        readWriteStream.end();

        policyAssertions(JsonValidationPolicy.JSON_INVALID_PAYLOAD_KEY);
    }

    @Test
    public void shouldMalformedPayloadBeRejected() {
        ArgumentCaptor<PolicyResult> policyResult = ArgumentCaptor.forClass(PolicyResult.class);

        Buffer buffer = factory.buffer("{\"name\":");
        ReadWriteStream readWriteStream = policy.onRequestContent(mockRequest, mockResponse, mockExecutionContext, mockPolicychain);
        readWriteStream.write(buffer);
        readWriteStream.end();

        policyAssertions(JsonValidationPolicy.JSON_INVALID_FORMAT_KEY);
    }

    @Test
    public void shouldMalformedJsonSchemaBeRejected() {
        when(configuration.getSchema()).thenReturn("\"msg\":\"error\"}");

        Buffer buffer = factory.buffer("{\"name\":\"foo\"}");
        ReadWriteStream readWriteStream = policy.onRequestContent(mockRequest, mockResponse, mockExecutionContext, mockPolicychain);
        readWriteStream.write(buffer);
        readWriteStream.end();

        policyAssertions(JsonValidationPolicy.JSON_INVALID_FORMAT_KEY);
    }

    private void policyAssertions(String key) {
        assertThat(metrics.getMessage()).isNotEmpty();
        ArgumentCaptor<PolicyResult> policyResult = ArgumentCaptor.forClass(PolicyResult.class);
        verify(mockPolicychain, times(1)).streamFailWith(policyResult.capture());
        PolicyResult value = policyResult.getValue();
        assertThat(value.message()).isEqualTo(configuration.getErrorMessage());
        assertThat(value.statusCode() == HttpStatusCode.BAD_REQUEST_400);
        assertThat(value.key().equals(key));
    }
}
