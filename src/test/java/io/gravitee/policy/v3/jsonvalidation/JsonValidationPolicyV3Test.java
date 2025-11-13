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
package io.gravitee.policy.v3.jsonvalidation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.util.ServiceLoaderHelper;
import io.gravitee.el.TemplateEngine;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.Response;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.buffer.BufferFactory;
import io.gravitee.gateway.api.stream.BufferedReadWriteStream;
import io.gravitee.gateway.api.stream.ReadWriteStream;
import io.gravitee.gateway.api.stream.SimpleReadWriteStream;
import io.gravitee.policy.api.PolicyChain;
import io.gravitee.policy.api.PolicyResult;
import io.gravitee.policy.jsonvalidation.configuration.JsonValidationPolicyConfiguration;
import io.gravitee.policy.jsonvalidation.configuration.PolicyScope;
import io.gravitee.reporter.api.http.Metrics;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class JsonValidationPolicyV3Test {

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

    private Metrics metrics;

    @InjectMocks
    private JsonValidationPolicyV3 policy;

    @BeforeEach
    public void beforeAll() {
        factory = ServiceLoaderHelper.loadFactory(BufferFactory.class);
        metrics = Metrics.on(System.currentTimeMillis()).build();

        when(configuration.getSchema()).thenReturn(JSON_SCHEMA);
        lenient().when(configuration.getErrorMessage()).thenReturn("{\"msg\":\"error\"}");
        lenient().when(mockRequest.metrics()).thenReturn(metrics);
        lenient().when(mockExecutionContext.getTemplateEngine()).thenReturn(TemplateEngine.templateEngine());
    }

    @Test
    public void shouldAcceptValidPayload() {
        when(configuration.getScope()).thenReturn(PolicyScope.REQUEST_CONTENT);
        JsonValidationPolicyV3 policy = new JsonValidationPolicyV3(configuration);
        Buffer buffer = factory.buffer("{\"name\":\"foo\"}");
        var readWriteStream = policy.onRequestContent(mockRequest, mockResponse, mockExecutionContext, mockPolicychain);

        final AtomicBoolean hasCalledEndOnReadWriteStreamParentClass = spyEndHandler(readWriteStream);

        readWriteStream.write(buffer);
        readWriteStream.end();

        assertThat(hasCalledEndOnReadWriteStreamParentClass).isTrue();
    }

    @Test
    public void shouldValidateRejectInvalidPayload() {
        when(configuration.getScope()).thenReturn(PolicyScope.REQUEST_CONTENT);

        Buffer buffer = factory.buffer("{\"name\":1}");
        var readWriteStream = policy.onRequestContent(mockRequest, mockResponse, mockExecutionContext, mockPolicychain);

        final AtomicBoolean hasCalledEndOnReadWriteStreamParentClass = spyEndHandler(readWriteStream);

        readWriteStream.write(buffer);
        readWriteStream.end();

        assertThat(hasCalledEndOnReadWriteStreamParentClass).isFalse();

        policyAssertions(JsonValidationPolicyV3.JSON_INVALID_PAYLOAD_KEY);
    }

    @Test
    public void shouldValidateUncheckedRejectInvalidPayload() {
        when(configuration.getScope()).thenReturn(PolicyScope.REQUEST_CONTENT);

        Buffer buffer = factory.buffer("{\"name\":1}");
        var readWriteStream = policy.onRequestContent(mockRequest, mockResponse, mockExecutionContext, mockPolicychain);

        final AtomicBoolean hasCalledEndOnReadWriteStreamParentClass = spyEndHandler(readWriteStream);

        readWriteStream.write(buffer);
        readWriteStream.end();

        assertThat(hasCalledEndOnReadWriteStreamParentClass).isFalse();

        policyAssertions(JsonValidationPolicyV3.JSON_INVALID_PAYLOAD_KEY);
    }

    @Test
    public void shouldMalformedPayloadBeRejected() {
        when(configuration.getScope()).thenReturn(PolicyScope.REQUEST_CONTENT);

        Buffer buffer = factory.buffer("{\"name\":");
        var readWriteStream = policy.onRequestContent(mockRequest, mockResponse, mockExecutionContext, mockPolicychain);

        final AtomicBoolean hasCalledEndOnReadWriteStreamParentClass = spyEndHandler(readWriteStream);

        readWriteStream.write(buffer);
        readWriteStream.end();

        assertThat(hasCalledEndOnReadWriteStreamParentClass).isFalse();

        policyAssertions(JsonValidationPolicyV3.JSON_INVALID_FORMAT_KEY);
    }

    @Test
    public void shouldMalformedJsonSchemaBeRejected() {
        when(configuration.getScope()).thenReturn(PolicyScope.REQUEST_CONTENT);
        when(configuration.getSchema()).thenReturn("\"msg\":\"error\"}");

        Buffer buffer = factory.buffer("{\"name\":\"foo\"}");
        var readWriteStream = policy.onRequestContent(mockRequest, mockResponse, mockExecutionContext, mockPolicychain);

        final AtomicBoolean hasCalledEndOnReadWriteStreamParentClass = spyEndHandler(readWriteStream);

        readWriteStream.write(buffer);
        readWriteStream.end();

        assertThat(hasCalledEndOnReadWriteStreamParentClass).isFalse();

        policyAssertions(JsonValidationPolicyV3.JSON_INVALID_FORMAT_KEY);
    }

    @Test
    public void shouldAcceptValidResponsePayload() {
        when(configuration.getScope()).thenReturn(PolicyScope.RESPONSE_CONTENT);
        JsonValidationPolicyV3 policy = new JsonValidationPolicyV3(configuration);
        Buffer buffer = factory.buffer("{\"name\":\"foo\"}");
        var readWriteStream = policy.onResponseContent(mockRequest, mockResponse, mockExecutionContext, mockPolicychain);
        final AtomicBoolean hasCalledEndOnReadWriteStreamParentClass = spyEndHandler(readWriteStream);

        readWriteStream.write(buffer);
        readWriteStream.end();

        assertThat(hasCalledEndOnReadWriteStreamParentClass).isTrue();
    }

    @Test
    public void shouldValidateResponseInvalidPayload() {
        when(configuration.getScope()).thenReturn(PolicyScope.RESPONSE_CONTENT);

        Buffer buffer = factory.buffer("{\"name\":1}");
        var readWriteStream = policy.onResponseContent(mockRequest, mockResponse, mockExecutionContext, mockPolicychain);
        final AtomicBoolean hasCalledEndOnReadWriteStreamParentClass = spyEndHandler(readWriteStream);

        readWriteStream.write(buffer);
        readWriteStream.end();

        assertThat(hasCalledEndOnReadWriteStreamParentClass).isFalse();

        policyAssertions(JsonValidationPolicyV3.JSON_INVALID_RESPONSE_PAYLOAD_KEY, HttpStatusCode.INTERNAL_SERVER_ERROR_500);
    }

    @Test
    public void shouldValidateResponseInvalidSchema() {
        when(configuration.getScope()).thenReturn(PolicyScope.RESPONSE_CONTENT);
        when(configuration.getSchema()).thenReturn("\"msg\":\"error\"}");

        Buffer buffer = factory.buffer("{\"name\":\"foo\"}");
        var readWriteStream = policy.onResponseContent(mockRequest, mockResponse, mockExecutionContext, mockPolicychain);

        final AtomicBoolean hasCalledEndOnReadWriteStreamParentClass = spyEndHandler(readWriteStream);

        readWriteStream.write(buffer);
        readWriteStream.end();

        assertThat(hasCalledEndOnReadWriteStreamParentClass).isFalse();

        policyAssertions(JsonValidationPolicyV3.JSON_INVALID_RESPONSE_FORMAT_KEY, HttpStatusCode.INTERNAL_SERVER_ERROR_500);
    }

    @Test
    public void shouldValidateResponseInvalidPayloadStraightRespondMode() {
        when(configuration.getScope()).thenReturn(PolicyScope.RESPONSE_CONTENT);
        when(configuration.isStraightRespondMode()).thenReturn(true);

        Buffer buffer = factory.buffer("{\"name\":1}");
        var readWriteStream = policy.onResponseContent(mockRequest, mockResponse, mockExecutionContext, mockPolicychain);

        final AtomicBoolean hasCalledEndOnReadWriteStreamParentClass = spyEndHandler(readWriteStream);

        readWriteStream.write(buffer);
        readWriteStream.end();

        assertThat(hasCalledEndOnReadWriteStreamParentClass).isTrue();

        policyAssertions();
    }

    @Test
    public void shouldValidateResponseInvalidSchemaStraightRespondMode() {
        when(configuration.getScope()).thenReturn(PolicyScope.RESPONSE_CONTENT);
        when(configuration.getSchema()).thenReturn("\"msg\":\"error\"}");
        when(configuration.isStraightRespondMode()).thenReturn(true);

        Buffer buffer = factory.buffer("{\"name\":\"foo\"}");
        var readWriteStream = policy.onResponseContent(mockRequest, mockResponse, mockExecutionContext, mockPolicychain);

        final AtomicBoolean hasCalledEndOnReadWriteStreamParentClass = spyEndHandler(readWriteStream);

        readWriteStream.write(buffer);
        readWriteStream.end();

        assertThat(hasCalledEndOnReadWriteStreamParentClass).isFalse();

        policyAssertions();
    }

    private void policyAssertions() {
        assertThat(metrics.getMessage()).isNotEmpty();
        ArgumentCaptor<PolicyResult> policyResult = ArgumentCaptor.forClass(PolicyResult.class);
        verify(mockPolicychain, times(0)).streamFailWith(policyResult.capture());
    }

    private void policyAssertions(String key) {
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

    /**
     * Replace the endHandler of the resulting ReadWriteStream of the policy execution.
     * This endHandler will set an {@link AtomicBoolean} to {@code true} if its called.
     * It will allow us to verify if super.end() has been called on {@link BufferedReadWriteStream#end()}
     * @param readWriteStream: the {@link ReadWriteStream} to modify
     * @return an AtomicBoolean set to {@code true} if {@link SimpleReadWriteStream#end()}, else {@code false}
     */
    private AtomicBoolean spyEndHandler(ReadWriteStream readWriteStream) {
        final AtomicBoolean hasCalledEndOnReadWriteStreamParentClass = new AtomicBoolean(false);
        readWriteStream.endHandler(__ -> hasCalledEndOnReadWriteStreamParentClass.set(true));
        return hasCalledEndOnReadWriteStreamParentClass;
    }
}
