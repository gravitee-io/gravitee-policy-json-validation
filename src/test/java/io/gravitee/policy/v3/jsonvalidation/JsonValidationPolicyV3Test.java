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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.http.MediaType;
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
import io.reactivex.rxjava3.core.Maybe;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

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

    private static final String TYPED_PERSON_SCHEMA = """
        {
            "title": "Person",
            "type": "object",
            "properties": {
                "name": {
                    "type": "string"
                },
                "age": {
                    "type": "integer"
                },
                "email": {
                    "type": "string",
                    "minLength": 5
                }
            },
            "required": ["name", "age", "email"]
        }""";

    private static final String NESTED_PERSON_SCHEMA = """
        {
            "type": "object",
            "properties": {
                "name": {
                    "type": "string"
                },
                "child": {
                    "type": "object",
                    "properties": {
                        "age": {
                            "type": "integer"
                        },
                        "email": {
                            "type": "string",
                            "minLength": 5
                        }
                    }
                }
            },
            "required": ["name"]
        }""";

    private static final String DEEP_REF_BREAKING_SCHEMA = """
        {
            "type": "object",
            "properties": {
                "name": {
                    "type": "string"
                },
                "child": {
                    "$ref": "#/definitions/Missing"
                }
            },
            "required": ["name"]
        }""";

    private static final String SLASH_KEY_SCHEMA = """
        {
            "type": "object",
            "properties": {
                "a/b": {
                    "type": "integer"
                }
            }
        }""";

    private static final String TILDE_KEY_SCHEMA = """
        {
            "type": "object",
            "properties": {
                "a~b": {
                    "type": "integer"
                }
            }
        }""";

    private static final String ARRAY_ITEMS_SCHEMA = """
        {
            "type": "object",
            "properties": {
                "items": {
                    "type": "array",
                    "items": {
                        "type": "integer"
                    }
                }
            }
        }""";

    private static final String AGE_TYPE_VIOLATION =
        "/age — instance type (string) does not match any allowed primitive type (allowed: [\"integer\"])";

    private static final String EMAIL_MINLENGTH_VIOLATION = "/email — string \"x\" is too short (length: 1, required minimum: 5)";

    private Metrics metrics;

    @InjectMocks
    private JsonValidationPolicyV3 policy;

    @BeforeEach
    public void beforeAll() {
        factory = ServiceLoaderHelper.loadFactory(BufferFactory.class);
        metrics = Metrics.on(System.currentTimeMillis()).build();

        lenient().when(configuration.getSchema()).thenReturn(JSON_SCHEMA);
        lenient().when(configuration.getErrorMessage()).thenReturn("custom error");
        lenient().when(mockRequest.metrics()).thenReturn(metrics);
        lenient().when(mockExecutionContext.getTemplateEngine()).thenReturn(TemplateEngine.templateEngine());
    }

    @Test
    public void shouldAcceptValidPayload() throws IOException {
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
    public void shouldAcceptValidResponsePayload() throws IOException {
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

        assertThat(hasCalledEndOnReadWriteStreamParentClass).isTrue();

        policyAssertions();
    }

    @Test
    void shouldReportPerFieldDetailOnRequestPayloadFailureWhenFlagOn() {
        when(configuration.getScope()).thenReturn(PolicyScope.REQUEST_CONTENT);
        when(configuration.getSchema()).thenReturn(TYPED_PERSON_SCHEMA);
        when(configuration.isReturnDetailedErrorReport()).thenReturn(true);

        Buffer buffer = factory.buffer("{\"name\":\"Ada\",\"age\":\"twenty\",\"email\":\"x\"}");
        var readWriteStream = policy.onRequestContent(mockRequest, mockResponse, mockExecutionContext, mockPolicychain);
        readWriteStream.write(buffer);
        readWriteStream.end();

        PolicyResult value = captureFailure();
        assertThat(value.key()).isEqualTo(JsonValidationPolicyV3.JSON_INVALID_PAYLOAD_KEY);
        assertThat(value.statusCode()).isEqualTo(HttpStatusCode.BAD_REQUEST_400);
        assertThat(value.contentType()).isEqualTo(MediaType.TEXT_PLAIN);
        assertThat(value.message()).contains(AGE_TYPE_VIOLATION).contains(EMAIL_MINLENGTH_VIOLATION);
    }

    @ParameterizedTest
    @MethodSource("rfc6901PointerCases")
    void shouldEncodePointerPerRfc6901OnRequestPayloadFailureWhenFlagOn(String schema, String payload, String expectedPointerFragment) {
        when(configuration.getScope()).thenReturn(PolicyScope.REQUEST_CONTENT);
        when(configuration.getSchema()).thenReturn(schema);
        when(configuration.isReturnDetailedErrorReport()).thenReturn(true);

        Buffer buffer = factory.buffer(payload);
        var readWriteStream = policy.onRequestContent(mockRequest, mockResponse, mockExecutionContext, mockPolicychain);
        readWriteStream.write(buffer);
        readWriteStream.end();

        PolicyResult value = captureFailure();
        assertThat(value.key()).isEqualTo(JsonValidationPolicyV3.JSON_INVALID_PAYLOAD_KEY);
        assertThat(value.contentType()).isEqualTo(MediaType.TEXT_PLAIN);
        assertThat(value.message()).contains(expectedPointerFragment);
    }

    static Stream<Arguments> rfc6901PointerCases() {
        return Stream.of(
            Arguments.of(SLASH_KEY_SCHEMA, "{\"a/b\":\"x\"}", "/a~1b — "),
            Arguments.of(TILDE_KEY_SCHEMA, "{\"a~b\":\"x\"}", "/a~0b — "),
            Arguments.of(ARRAY_ITEMS_SCHEMA, "{\"items\":[1,\"x\",3]}", "/items/1 — ")
        );
    }

    @Test
    void shouldReportPerFieldDetailOnRequestPayloadFailureWhenUncheckedFlagOn() {
        when(configuration.getScope()).thenReturn(PolicyScope.REQUEST_CONTENT);
        when(configuration.getSchema()).thenReturn(TYPED_PERSON_SCHEMA);
        when(configuration.isReturnDetailedErrorReport()).thenReturn(true);
        when(configuration.isValidateUnchecked()).thenReturn(true);

        Buffer buffer = factory.buffer("{\"name\":\"Ada\",\"age\":\"twenty\",\"email\":\"x\"}");
        var readWriteStream = policy.onRequestContent(mockRequest, mockResponse, mockExecutionContext, mockPolicychain);
        readWriteStream.write(buffer);
        readWriteStream.end();

        PolicyResult value = captureFailure();
        assertThat(value.key()).isEqualTo(JsonValidationPolicyV3.JSON_INVALID_PAYLOAD_KEY);
        assertThat(value.statusCode()).isEqualTo(HttpStatusCode.BAD_REQUEST_400);
        assertThat(value.contentType()).isEqualTo(MediaType.TEXT_PLAIN);
        assertThat(value.message()).contains(AGE_TYPE_VIOLATION).contains(EMAIL_MINLENGTH_VIOLATION);
    }

    @Test
    void shouldReportAllViolationsInDetailPathRegardlessOfConfiguredDeepCheckFlag() {
        when(configuration.getScope()).thenReturn(PolicyScope.REQUEST_CONTENT);
        when(configuration.getSchema()).thenReturn(TYPED_PERSON_SCHEMA);
        when(configuration.isReturnDetailedErrorReport()).thenReturn(true);
        when(configuration.isDeepCheck()).thenReturn(false);

        Buffer buffer = factory.buffer("{\"age\":\"twenty\",\"email\":\"x\"}");
        var readWriteStream = policy.onRequestContent(mockRequest, mockResponse, mockExecutionContext, mockPolicychain);
        readWriteStream.write(buffer);
        readWriteStream.end();

        PolicyResult value = captureFailure();
        assertThat(value.message()).contains("name").contains("required").contains(AGE_TYPE_VIOLATION).contains(EMAIL_MINLENGTH_VIOLATION);
    }

    @Test
    void shouldNotEvaluateSpelErrorMessageOnDetailPathWhenFlagOn() {
        when(configuration.getScope()).thenReturn(PolicyScope.REQUEST_CONTENT);
        when(configuration.getSchema()).thenReturn(TYPED_PERSON_SCHEMA);
        when(configuration.isReturnDetailedErrorReport()).thenReturn(true);
        lenient().when(configuration.getErrorMessage()).thenReturn("{\"msg\":\"${request.id}\"}");

        Buffer buffer = factory.buffer("{\"name\":\"Ada\",\"age\":\"twenty\",\"email\":\"x\"}");
        var readWriteStream = policy.onRequestContent(mockRequest, mockResponse, mockExecutionContext, mockPolicychain);
        readWriteStream.write(buffer);
        readWriteStream.end();

        PolicyResult value = captureFailure();
        assertThat(value.message()).contains(AGE_TYPE_VIOLATION).contains(EMAIL_MINLENGTH_VIOLATION);
        assertThat(value.message()).doesNotContain("request.id").doesNotContain("${");
    }

    @Test
    void shouldPreferDetailOverConfiguredErrorMessageWhenFlagOn() {
        when(configuration.getScope()).thenReturn(PolicyScope.REQUEST_CONTENT);
        when(configuration.getSchema()).thenReturn(TYPED_PERSON_SCHEMA);
        when(configuration.isReturnDetailedErrorReport()).thenReturn(true);
        when(configuration.getErrorMessage()).thenReturn("{\"msg\":\"configured\"}");

        Buffer buffer = factory.buffer("{\"name\":\"Ada\",\"age\":\"twenty\",\"email\":\"x\"}");
        var readWriteStream = policy.onRequestContent(mockRequest, mockResponse, mockExecutionContext, mockPolicychain);
        readWriteStream.write(buffer);
        readWriteStream.end();

        PolicyResult value = captureFailure();
        assertThat(value.message()).contains(AGE_TYPE_VIOLATION).contains(EMAIL_MINLENGTH_VIOLATION);
        assertThat(value.message()).doesNotContain("configured");
    }

    @Test
    void shouldReportPerFieldDetailOnResponsePayloadFailureWhenFlagOn() {
        when(configuration.getScope()).thenReturn(PolicyScope.RESPONSE_CONTENT);
        when(configuration.getSchema()).thenReturn(TYPED_PERSON_SCHEMA);
        when(configuration.isReturnDetailedErrorReport()).thenReturn(true);

        Buffer buffer = factory.buffer("{\"name\":\"Ada\",\"age\":\"twenty\",\"email\":\"x\"}");
        var readWriteStream = policy.onResponseContent(mockRequest, mockResponse, mockExecutionContext, mockPolicychain);
        readWriteStream.write(buffer);
        readWriteStream.end();

        PolicyResult value = captureFailure();
        assertThat(value.key()).isEqualTo(JsonValidationPolicyV3.JSON_INVALID_RESPONSE_PAYLOAD_KEY);
        assertThat(value.statusCode()).isEqualTo(HttpStatusCode.INTERNAL_SERVER_ERROR_500);
        assertThat(value.message()).contains(AGE_TYPE_VIOLATION).contains(EMAIL_MINLENGTH_VIOLATION);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void shouldPassThroughResponseInStraightModeRegardlessOfDetailFlag(boolean flagOn) {
        when(configuration.getScope()).thenReturn(PolicyScope.RESPONSE_CONTENT);
        when(configuration.getSchema()).thenReturn(TYPED_PERSON_SCHEMA);
        when(configuration.isStraightRespondMode()).thenReturn(true);
        lenient().when(configuration.isReturnDetailedErrorReport()).thenReturn(flagOn);

        Buffer buffer = factory.buffer("{\"name\":\"Ada\",\"age\":\"twenty\",\"email\":\"x\"}");
        var readWriteStream = policy.onResponseContent(mockRequest, mockResponse, mockExecutionContext, mockPolicychain);
        final AtomicBoolean hasCalledEndOnReadWriteStreamParentClass = spyEndHandler(readWriteStream);
        readWriteStream.write(buffer);
        readWriteStream.end();

        assertThat(hasCalledEndOnReadWriteStreamParentClass).isTrue();
        assertThat(metrics.getMessage()).contains("instance type").contains("/age");
        verify(mockPolicychain, times(0)).streamFailWith(any());
    }

    @Test
    void shouldUseGenericMessageOnRequestFormatFailureWhenFlagOn() {
        when(configuration.getScope()).thenReturn(PolicyScope.REQUEST_CONTENT);
        when(configuration.getSchema()).thenReturn(TYPED_PERSON_SCHEMA);
        when(configuration.getErrorMessage()).thenReturn(null);
        lenient().when(configuration.isReturnDetailedErrorReport()).thenReturn(true);

        Buffer buffer = factory.buffer("{\"name\":");
        var readWriteStream = policy.onRequestContent(mockRequest, mockResponse, mockExecutionContext, mockPolicychain);
        readWriteStream.write(buffer);
        readWriteStream.end();

        PolicyResult value = captureFailure();
        assertThat(value.key()).isEqualTo(JsonValidationPolicyV3.JSON_INVALID_FORMAT_KEY);
        assertThat(value.statusCode()).isEqualTo(HttpStatusCode.BAD_REQUEST_400);
        assertThat(value.message()).isEqualTo(JsonValidationPolicyV3.BAD_REQUEST);
    }

    @Test
    void shouldFallBackToGenericMessageWhenDetailBuildThrowsProcessingException() {
        when(configuration.getScope()).thenReturn(PolicyScope.REQUEST_CONTENT);
        when(configuration.getSchema()).thenReturn(DEEP_REF_BREAKING_SCHEMA);
        when(configuration.getErrorMessage()).thenReturn(null);
        when(configuration.isReturnDetailedErrorReport()).thenReturn(true);

        Logger logger = (Logger) LoggerFactory.getLogger(JsonValidationPolicyV3.class);
        Level originalLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.WARN);
        try {
            Buffer buffer = factory.buffer("{\"child\":{}}");
            var readWriteStream = policy.onRequestContent(mockRequest, mockResponse, mockExecutionContext, mockPolicychain);
            readWriteStream.write(buffer);
            readWriteStream.end();

            PolicyResult value = captureFailure();
            assertThat(value.key()).isEqualTo(JsonValidationPolicyV3.JSON_INVALID_PAYLOAD_KEY);
            assertThat(value.statusCode()).isEqualTo(HttpStatusCode.BAD_REQUEST_400);
            assertThat(value.message()).isEqualTo(JsonValidationPolicyV3.BAD_REQUEST);
            assertThat(value.contentType()).isEqualTo(MediaType.TEXT_PLAIN);
            assertThat(appender.list).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getFormattedMessage()).contains("Detailed validation report generation failed");
            });
        } finally {
            logger.setLevel(originalLevel);
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void shouldSurfaceSchemaAnomalyDetailWhenUncheckedAndBrokenSchemaAndFlagOn() {
        when(configuration.getScope()).thenReturn(PolicyScope.REQUEST_CONTENT);
        when(configuration.getSchema()).thenReturn(DEEP_REF_BREAKING_SCHEMA);
        when(configuration.getErrorMessage()).thenReturn(null);
        when(configuration.isReturnDetailedErrorReport()).thenReturn(true);
        when(configuration.isValidateUnchecked()).thenReturn(true);

        Buffer buffer = factory.buffer("{\"child\":{}}");
        var readWriteStream = policy.onRequestContent(mockRequest, mockResponse, mockExecutionContext, mockPolicychain);
        readWriteStream.write(buffer);
        readWriteStream.end();

        PolicyResult value = captureFailure();
        assertThat(value.key()).isEqualTo(JsonValidationPolicyV3.JSON_INVALID_PAYLOAD_KEY);
        assertThat(value.statusCode()).isEqualTo(HttpStatusCode.BAD_REQUEST_400);
        assertThat(value.contentType()).isEqualTo(MediaType.TEXT_PLAIN);
        assertThat(value.message()).isEqualTo(
            "(root) — JSON Reference \"#/definitions/Missing\" cannot be resolved; (root) — object has missing required properties ([\"name\"])"
        );
        assertThat(value.message()).isNotEqualTo(JsonValidationPolicyV3.BAD_REQUEST);
    }

    @Test
    void shouldLabelRootPointerWhenRootPropertyViolationAndFlagOn() {
        when(configuration.getScope()).thenReturn(PolicyScope.REQUEST_CONTENT);
        when(configuration.getSchema()).thenReturn(JSON_SCHEMA);
        when(configuration.getErrorMessage()).thenReturn(null);
        when(configuration.isReturnDetailedErrorReport()).thenReturn(true);

        Buffer buffer = factory.buffer("{}");
        var readWriteStream = policy.onRequestContent(mockRequest, mockResponse, mockExecutionContext, mockPolicychain);
        readWriteStream.write(buffer);
        readWriteStream.end();

        PolicyResult value = captureFailure();
        assertThat(value.message()).isEqualTo("(root) — object has missing required properties ([\"name\"])");
    }

    @Test
    void shouldUseGenericMessageOnResponseFormatFailureWhenFlagOn() {
        when(configuration.getScope()).thenReturn(PolicyScope.RESPONSE_CONTENT);
        when(configuration.getSchema()).thenReturn("\"msg\":\"error\"}");
        when(configuration.getErrorMessage()).thenReturn(null);
        lenient().when(configuration.isReturnDetailedErrorReport()).thenReturn(true);

        Buffer buffer = factory.buffer("{\"name\":\"foo\"}");
        var readWriteStream = policy.onResponseContent(mockRequest, mockResponse, mockExecutionContext, mockPolicychain);
        readWriteStream.write(buffer);
        readWriteStream.end();

        PolicyResult value = captureFailure();
        assertThat(value.key()).isEqualTo(JsonValidationPolicyV3.JSON_INVALID_RESPONSE_FORMAT_KEY);
        assertThat(value.statusCode()).isEqualTo(HttpStatusCode.INTERNAL_SERVER_ERROR_500);
        assertThat(value.message()).isEqualTo(JsonValidationPolicyV3.INTERNAL_ERROR);
    }

    @Test
    void shouldKeepMetricsMessageDistinctFromConsumerDetailWhenFlagOn() {
        when(configuration.getScope()).thenReturn(PolicyScope.REQUEST_CONTENT);
        when(configuration.getSchema()).thenReturn(TYPED_PERSON_SCHEMA);
        when(configuration.isReturnDetailedErrorReport()).thenReturn(true);

        Buffer buffer = factory.buffer("{\"name\":\"Ada\",\"age\":\"twenty\",\"email\":\"x\"}");
        var readWriteStream = policy.onRequestContent(mockRequest, mockResponse, mockExecutionContext, mockPolicychain);
        readWriteStream.write(buffer);
        readWriteStream.end();

        PolicyResult value = captureFailure();
        assertThat(value.message()).contains(AGE_TYPE_VIOLATION).contains(EMAIL_MINLENGTH_VIOLATION);
        assertThat(metrics.getMessage()).contains("instance type").contains("/age");
        assertThat(metrics.getMessage()).isNotEqualTo(value.message());
    }

    @Test
    void shouldRecordMetricsUsingConfiguredDeepCheckWhenFlagOn() {
        when(configuration.getScope()).thenReturn(PolicyScope.REQUEST_CONTENT);
        when(configuration.getSchema()).thenReturn(NESTED_PERSON_SCHEMA);
        when(configuration.isReturnDetailedErrorReport()).thenReturn(true);
        when(configuration.isDeepCheck()).thenReturn(true);

        Buffer buffer = factory.buffer("{\"child\":{\"age\":\"twenty\",\"email\":\"x\"}}");
        var readWriteStream = policy.onRequestContent(mockRequest, mockResponse, mockExecutionContext, mockPolicychain);
        readWriteStream.write(buffer);
        readWriteStream.end();

        captureFailure();
        assertThat(metrics.getMessage()).contains("/child/age").contains("/child/email");
    }

    @Test
    void shouldKeepConfiguredErrorMessageOnRequestPayloadFailureWhenFlagOff() {
        when(configuration.getScope()).thenReturn(PolicyScope.REQUEST_CONTENT);
        when(configuration.getSchema()).thenReturn(TYPED_PERSON_SCHEMA);

        Buffer buffer = factory.buffer("{\"name\":\"Ada\",\"age\":\"twenty\",\"email\":\"x\"}");
        var readWriteStream = policy.onRequestContent(mockRequest, mockResponse, mockExecutionContext, mockPolicychain);
        readWriteStream.write(buffer);
        readWriteStream.end();

        PolicyResult value = captureFailure();
        assertThat(value.message()).isEqualTo("custom error");
        assertThat(value.key()).isEqualTo(JsonValidationPolicyV3.JSON_INVALID_PAYLOAD_KEY);
        assertThat(value.statusCode()).isEqualTo(HttpStatusCode.BAD_REQUEST_400);
        assertThat(value.contentType()).isEqualTo(MediaType.TEXT_PLAIN);
    }

    @Test
    void shouldEvaluateSpelInConfiguredErrorMessageWhenFlagOff() {
        when(configuration.getScope()).thenReturn(PolicyScope.REQUEST_CONTENT);
        when(configuration.getSchema()).thenReturn(TYPED_PERSON_SCHEMA);
        var templateEngine = mock(TemplateEngine.class);
        when(mockExecutionContext.getTemplateEngine()).thenReturn(templateEngine);
        when(configuration.getErrorMessage()).thenReturn("{\"msg\":\"${ctx.attribute}\"}");
        when(templateEngine.eval("{\"msg\":\"${ctx.attribute}\"}", String.class)).thenReturn(Maybe.just("{\"msg\":\"evaluated\"}"));

        Buffer buffer = factory.buffer("{\"name\":\"Ada\",\"age\":\"twenty\",\"email\":\"x\"}");
        var readWriteStream = policy.onRequestContent(mockRequest, mockResponse, mockExecutionContext, mockPolicychain);
        readWriteStream.write(buffer);
        readWriteStream.end();

        PolicyResult value = captureFailure();
        assertThat(value.message()).isEqualTo("{\"msg\":\"evaluated\"}");
        assertThat(value.message()).doesNotContain("${ctx.attribute}");
        assertThat(value.key()).isEqualTo(JsonValidationPolicyV3.JSON_INVALID_PAYLOAD_KEY);
        assertThat(value.statusCode()).isEqualTo(HttpStatusCode.BAD_REQUEST_400);
    }

    @Test
    void shouldUseBadRequestTextPlainWhenNoErrorMessageAndFlagOff() {
        when(configuration.getScope()).thenReturn(PolicyScope.REQUEST_CONTENT);
        when(configuration.getSchema()).thenReturn(TYPED_PERSON_SCHEMA);
        when(configuration.getErrorMessage()).thenReturn(null);

        Buffer buffer = factory.buffer("{\"name\":\"Ada\",\"age\":\"twenty\",\"email\":\"x\"}");
        var readWriteStream = policy.onRequestContent(mockRequest, mockResponse, mockExecutionContext, mockPolicychain);
        readWriteStream.write(buffer);
        readWriteStream.end();

        PolicyResult value = captureFailure();
        assertThat(value.message()).isEqualTo("Bad Request");
        assertThat(value.contentType()).isEqualTo(MediaType.TEXT_PLAIN);
        assertThat(value.key()).isEqualTo(JsonValidationPolicyV3.JSON_INVALID_PAYLOAD_KEY);
        assertThat(value.statusCode()).isEqualTo(HttpStatusCode.BAD_REQUEST_400);
    }

    @Test
    void shouldFailStreamWhenSpelErrorMessageEvaluationFails() {
        when(configuration.getScope()).thenReturn(PolicyScope.REQUEST_CONTENT);
        when(configuration.getSchema()).thenReturn(TYPED_PERSON_SCHEMA);
        var templateEngine = mock(TemplateEngine.class);
        when(mockExecutionContext.getTemplateEngine()).thenReturn(templateEngine);
        when(configuration.getErrorMessage()).thenReturn("{\"msg\":\"${ctx.broken}\"}");
        when(templateEngine.eval("{\"msg\":\"${ctx.broken}\"}", String.class)).thenReturn(
            Maybe.error(new RuntimeException("SpEL evaluation failed"))
        );

        Buffer buffer = factory.buffer("{\"name\":\"Ada\",\"age\":\"twenty\",\"email\":\"x\"}");
        var readWriteStream = policy.onRequestContent(mockRequest, mockResponse, mockExecutionContext, mockPolicychain);
        readWriteStream.write(buffer);
        readWriteStream.end();

        PolicyResult value = captureFailure();
        assertThat(value.key()).isEqualTo(JsonValidationPolicyV3.JSON_INVALID_PAYLOAD_KEY);
        assertThat(value.statusCode()).isEqualTo(HttpStatusCode.BAD_REQUEST_400);
        assertThat(value.message()).isEqualTo(JsonValidationPolicyV3.BAD_REQUEST);
        assertThat(value.contentType()).isEqualTo(MediaType.TEXT_PLAIN);
    }

    private PolicyResult captureFailure() {
        ArgumentCaptor<PolicyResult> policyResult = ArgumentCaptor.forClass(PolicyResult.class);
        verify(mockPolicychain, times(1)).streamFailWith(policyResult.capture());
        return policyResult.getValue();
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
        assertThat(value.message()).isEqualTo("custom error");
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

    @Nested
    class BuildDetailedMessageTest {

        @Test
        void emptyDeepReportCollapsesToEmptyOptional() throws IOException {
            JsonNode parsedSchema = JsonLoader.fromString("{}");
            JsonNode jsonNode = JsonLoader.fromString("{\"name\":\"Ada\"}");

            Optional<String> detailedMessage = policy.buildDetailedMessage(parsedSchema, jsonNode);

            assertThat(detailedMessage).isEmpty();
        }

        @Test
        void unexpectedRuntimeExceptionPropagatesInsteadOfBecomingEmptyOptional() throws IOException {
            JsonNode parsedSchema = JsonLoader.fromString(JSON_SCHEMA);

            assertThatThrownBy(() -> policy.buildDetailedMessage(parsedSchema, null)).isInstanceOf(RuntimeException.class);
        }
    }
}
