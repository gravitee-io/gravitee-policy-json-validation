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

import io.gravitee.common.http.HttpHeaders;
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
import io.gravitee.policy.jsonvalidation.configuration.JsonValidationPolicyConfiguration;
import io.gravitee.policy.jsonvalidation.configuration.PolicyScope;
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
        HttpHeaders headers = spy(new HttpHeaders());

        when(mockRequest.headers()).thenReturn(headers);
        when(mockResponse.headers()).thenReturn(headers);
        when(configuration.getErrorMessage()).thenReturn("{\"msg\":\"error\"}");
        when(configuration.getSchema()).thenReturn(jsonschema);
        when(mockRequest.metrics()).thenReturn(metrics);
        when(mockExecutionContext.getTemplateEngine()).thenReturn(TemplateEngine.templateEngine());

        policy = new JsonValidationPolicy(configuration);
    }

    @Test
    public void shouldAcceptValidPayload() {
        assertThatCode(() -> {
        when(configuration.getScope()).thenReturn(PolicyScope.REQUEST_CONTENT);
            JsonValidationPolicy policy = new JsonValidationPolicy(configuration);
            Buffer buffer = factory.buffer("{\"name\":\"foo\"}");
            ReadWriteStream readWriteStream = policy.onRequestContent(mockRequest, mockResponse, mockExecutionContext, mockPolicychain);
            readWriteStream.write(buffer);
            readWriteStream.end();
        }).doesNotThrowAnyException();
    }

    @Test
    public void shouldValidateRejectInvalidPayload() {
        when(configuration.getScope()).thenReturn(PolicyScope.REQUEST_CONTENT);

        Buffer buffer = factory.buffer("{\"name\":1}");
        ReadWriteStream readWriteStream = policy.onRequestContent(mockRequest, mockResponse, mockExecutionContext, mockPolicychain);
        readWriteStream.write(buffer);
        readWriteStream.end();

        policyAssertions(JsonValidationPolicy.JSON_INVALID_PAYLOAD_KEY);
    }

    @Test
    public void shouldValidateUncheckedRejectInvalidPayload() {
        when(configuration.getScope()).thenReturn(PolicyScope.REQUEST_CONTENT);

        Buffer buffer = factory.buffer("{\"name\":1}");
        ReadWriteStream readWriteStream = policy.onRequestContent(mockRequest, mockResponse, mockExecutionContext, mockPolicychain);
        readWriteStream.write(buffer);
        readWriteStream.end();

        policyAssertions(JsonValidationPolicy.JSON_INVALID_PAYLOAD_KEY);
    }

    @Test
    public void shouldMalformedPayloadBeRejected() {
        when(configuration.getScope()).thenReturn(PolicyScope.REQUEST_CONTENT);

        Buffer buffer = factory.buffer("{\"name\":");
        ReadWriteStream readWriteStream = policy.onRequestContent(mockRequest, mockResponse, mockExecutionContext, mockPolicychain);
        readWriteStream.write(buffer);
        readWriteStream.end();

        policyAssertions(JsonValidationPolicy.JSON_INVALID_FORMAT_KEY);
    }

    @Test
    public void shouldMalformedJsonSchemaBeRejected() {
        when(configuration.getScope()).thenReturn(PolicyScope.REQUEST_CONTENT);
        when(configuration.getSchema()).thenReturn("\"msg\":\"error\"}");

        Buffer buffer = factory.buffer("{\"name\":\"foo\"}");
        ReadWriteStream readWriteStream = policy.onRequestContent(mockRequest, mockResponse, mockExecutionContext, mockPolicychain);
        readWriteStream.write(buffer);
        readWriteStream.end();

        policyAssertions(JsonValidationPolicy.JSON_INVALID_FORMAT_KEY);
    }

    @Test
    public void shouldAcceptValidResponsePayload() {
        assertThatCode(() -> {
            when(configuration.getScope()).thenReturn(PolicyScope.RESPONSE_CONTENT);
            JsonValidationPolicy policy = new JsonValidationPolicy(configuration);
            Buffer buffer = factory.buffer("{\"name\":\"foo\"}");
            ReadWriteStream readWriteStream = policy.onResponseContent(mockRequest, mockResponse, mockExecutionContext, mockPolicychain);
            readWriteStream.write(buffer);
            readWriteStream.end();
        }).doesNotThrowAnyException();
    }

    @Test
    public void shouldValidateResponseInvalidPayload() {
        when(configuration.getScope()).thenReturn(PolicyScope.RESPONSE_CONTENT);

        Buffer buffer = factory.buffer("{\"name\":1}");
        ReadWriteStream readWriteStream = policy.onResponseContent(mockRequest, mockResponse, mockExecutionContext, mockPolicychain);
        readWriteStream.write(buffer);
        readWriteStream.end();

        policyAssertions(JsonValidationPolicy.JSON_INVALID_RESPONSE_PAYLOAD_KEY, HttpStatusCode.INTERNAL_SERVER_ERROR_500);
    }

    @Test
    public void shouldValidateResponseInvalidSchema() {
        when(configuration.getScope()).thenReturn(PolicyScope.RESPONSE_CONTENT);
        when(configuration.getSchema()).thenReturn("\"msg\":\"error\"}");

        Buffer buffer = factory.buffer("{\"name\":\"foo\"}");
        ReadWriteStream readWriteStream = policy.onResponseContent(mockRequest, mockResponse, mockExecutionContext, mockPolicychain);
        readWriteStream.write(buffer);
        readWriteStream.end();

        policyAssertions(JsonValidationPolicy.JSON_INVALID_RESPONSE_FORMAT_KEY, HttpStatusCode.INTERNAL_SERVER_ERROR_500);
    }

    @Test
    public void shouldValidateResponseInvalidPayloadStraightRespondMode() {
        when(configuration.getScope()).thenReturn(PolicyScope.RESPONSE_CONTENT);
        when(configuration.isStraightRespondMode()).thenReturn(true);

        Buffer buffer = factory.buffer("{\"name\":1}");
        ReadWriteStream readWriteStream = policy.onResponseContent(mockRequest, mockResponse, mockExecutionContext, mockPolicychain);
        readWriteStream.write(buffer);
        readWriteStream.end();

        policyAssertions();
    }

    @Test
    public void shouldValidateResponseInvalidSchemaStraightRespondMode() {
        when(configuration.getScope()).thenReturn(PolicyScope.RESPONSE_CONTENT);
        when(configuration.getSchema()).thenReturn("\"msg\":\"error\"}");
        when(configuration.isStraightRespondMode()).thenReturn(true);

        Buffer buffer = factory.buffer("{\"name\":\"foo\"}");
        ReadWriteStream readWriteStream = policy.onResponseContent(mockRequest, mockResponse, mockExecutionContext, mockPolicychain);
        readWriteStream.write(buffer);
        readWriteStream.end();

        policyAssertions();
    }

    private void policyAssertions() {
        assertThat(metrics.getMessage()).isNotEmpty();
        ArgumentCaptor<PolicyResult> policyResult = ArgumentCaptor.forClass(PolicyResult.class);
        verify(mockPolicychain, times(0)).streamFailWith(policyResult.capture());
    }

    private void policyAssertions(String key){
        policyAssertions(key, HttpStatusCode.BAD_REQUEST_400);
    }

    private void policyAssertions(String key, int statusCode) {
        assertThat(metrics.getMessage()).isNotEmpty();
        ArgumentCaptor<PolicyResult> policyResult = ArgumentCaptor.forClass(PolicyResult.class);
        verify(mockPolicychain, times(1)).streamFailWith(policyResult.capture());
        PolicyResult value = policyResult.getValue();
        assertThat(value.message()).isEqualTo(configuration.getErrorMessage());
        assertThat(value.statusCode()).isEqualTo(statusCode);
        assertThat(value.key()).isEqualTo(key);
    }
}
