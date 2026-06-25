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
package io.gravitee.policy.jsonvalidation;

import static io.gravitee.policy.jsonvalidation.configuration.errorhandling.PublishValidationErrorStrategy.FAIL_WITH_INVALID_RECORD;
import static io.gravitee.policy.jsonvalidation.configuration.errorhandling.SubscribeErrorHandlingStrategy.ADD_RECORD_HEADER;
import static io.gravitee.policy.jsonvalidation.configuration.errorhandling.SubscribeErrorHandlingStrategy.INVALIDATE_PARTITION;
import static io.gravitee.policy.jsonvalidation.kafka.factory.TestKafkaApiMessageFactory.createFailedProduceResponseWithTwoPartitions;
import static io.gravitee.policy.jsonvalidation.kafka.factory.TestKafkaApiMessageFactory.createFetchResponseWithTwoPartitions;
import static io.gravitee.policy.jsonvalidation.kafka.factory.TestNativeErrorHandlingConfigurationFactory.TEST_HEADER_NAME;
import static io.gravitee.policy.jsonvalidation.kafka.factory.TestNativeErrorHandlingConfigurationFactory.createNativeErrorHandling;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.gravitee.el.TemplateEngine;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.reactive.api.ExecutionFailure;
import io.gravitee.gateway.reactive.api.context.http.*;
import io.gravitee.gateway.reactive.api.context.kafka.*;
import io.gravitee.gateway.reactive.api.message.DefaultMessage;
import io.gravitee.gateway.reactive.api.message.Message;
import io.gravitee.gateway.reactive.api.message.kafka.KafkaMessage;
import io.gravitee.policy.jsonvalidation.configuration.JsonValidationPolicyConfiguration;
import io.gravitee.policy.jsonvalidation.configuration.schema.SchemaSource;
import io.gravitee.policy.jsonvalidation.configuration.schema.SchemaSourceType;
import io.gravitee.policy.jsonvalidation.kafka.stub.KafkaMessageStub;
import io.gravitee.reporter.api.v4.metric.Metrics;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import java.io.IOException;
import java.util.regex.Pattern;
import org.apache.kafka.common.InvalidRecordException;
import org.apache.kafka.common.protocol.Errors;
import org.apache.kafka.common.requests.AbstractResponse;
import org.apache.kafka.common.requests.ProduceRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class JsonValidationPolicyTest {

    private static final String JSON_SCHEMA = """
        {
            "title": "Person",
            "type": "object",
            "properties": {
                "name": {
                    "type": "string"
                },
                "preferences": {
                    "type": "object",
                    "properties": {
                        "newsletter": {
                            "type": "boolean"
                        }
                    }
                }
            },
            "required": ["name"]
        }""";

    private static final String DETAILED_SCHEMA = """
        {
          "$schema": "http://json-schema.org/draft-04/schema#",
          "type": "object",
          "properties": {
            "name": { "type": "string" },
            "age": { "type": "integer" },
            "email": { "type": "string", "minLength": 5 }
          },
          "required": ["name"]
        }""";

    private static final String DEDUP_SCHEMA = """
        {
          "$schema": "http://json-schema.org/draft-04/schema#",
          "type": "object",
          "properties": { "age": { "type": "integer" } },
          "patternProperties": { "^age$": { "type": "integer" } }
        }""";

    private static final String UNRESOLVABLE_REF_SCHEMA = """
        {
          "$schema": "http://json-schema.org/draft-04/schema#",
          "type": "object",
          "properties": {
            "name": { "$ref": "http://nonexistent.invalid/missing.json#" }
          }
        }""";

    private static final String NESTED_SCHEMA = """
        {
          "$schema": "http://json-schema.org/draft-04/schema#",
          "type": "object",
          "properties": {
            "address": {
              "type": "object",
              "properties": { "street": { "type": "integer" } }
            }
          }
        }""";

    private static final String AGE_VIOLATION =
        "/age — instance type (string) does not match any allowed primitive type (allowed: [\"integer\"])";
    private static final String EMAIL_VIOLATION = "/email — string \"x\" is too short (length: 1, required minimum: 5)";
    private static final String REQUIRED_NAME_VIOLATION = "object has missing required properties ([\"name\"])";
    private static final String INVALID_DETAILED_PAYLOAD = "{\"name\":\"Ada\",\"age\":\"twenty\",\"email\":\"x\"}";
    private static final String NON_JSON = "qwerty";

    @Mock
    private JsonValidationPolicyConfiguration configuration;

    @BeforeEach
    void setUp() {
        SchemaSource schemaSource = SchemaSource.builder().sourceType(SchemaSourceType.STATIC_SCHEMA).staticSchema(JSON_SCHEMA).build();

        when(configuration.getSchemaSource()).thenReturn(schemaSource);
    }

    @ExtendWith(MockitoExtension.class)
    @Nested
    class OnRequestTest {

        @Mock
        HttpPlainExecutionContext ctx;

        @Mock
        HttpPlainRequest request;

        @Captor
        ArgumentCaptor<ExecutionFailure> executionFailureCaptor;

        @BeforeEach
        void setUp() {
            var metrics = Metrics.builder().build();
            lenient().when(ctx.metrics()).thenReturn(metrics);
            lenient().when(ctx.interruptWith(any())).thenReturn(Completable.error(new MyCustomException()));
            when(ctx.request()).thenReturn(request);
        }

        @Test
        void badBodyNotJson() throws IOException {
            // Given
            JsonValidationPolicy policy = new JsonValidationPolicy(configuration);
            when(request.body()).thenReturn(Maybe.just(Buffer.buffer("qwerty")));

            // When
            policy
                .onRequest(ctx)
                .test()
                .assertError(throwable -> throwable instanceof MyCustomException);

            verify(ctx).interruptWith(executionFailureCaptor.capture());
            assertThat(executionFailureCaptor.getValue().key()).isEqualTo("JSON_INVALID_FORMAT");
        }

        @Test
        void goodFormat() throws IOException {
            // Given
            JsonValidationPolicy policy = new JsonValidationPolicy(configuration);
            when(request.body()).thenReturn(Maybe.just(Buffer.buffer("{\"name\":\"foo\"}")));

            // When
            policy.onRequest(ctx).test().assertComplete();
        }

        @Test
        void badBody() throws IOException {
            // Given
            JsonValidationPolicy policy = new JsonValidationPolicy(configuration);
            when(request.body()).thenReturn(Maybe.just(Buffer.buffer("{\"name2\":\"foo\"}")));

            // When
            policy
                .onRequest(ctx)
                .test()
                .assertError(throwable -> throwable instanceof MyCustomException);

            verify(ctx).interruptWith(executionFailureCaptor.capture());
            assertThat(executionFailureCaptor.getValue().key()).isEqualTo("JSON_INVALID_PAYLOAD");
        }

        @Test
        void badPayloadWithNoConfiguredMessageReturnsGenericBadRequest() throws IOException {
            when(configuration.isReturnDetailedErrorReport()).thenReturn(false);
            JsonValidationPolicy policy = new JsonValidationPolicy(configuration);
            when(request.body()).thenReturn(Maybe.just(Buffer.buffer("{\"name2\":\"foo\"}")));

            policy
                .onRequest(ctx)
                .test()
                .assertError(throwable -> throwable instanceof MyCustomException);

            verify(ctx).interruptWith(executionFailureCaptor.capture());
            assertThat(executionFailureCaptor.getValue().message()).isEqualTo("Bad Request");
        }

        @Test
        void badBodyIncorrectType() throws IOException {
            // Given
            JsonValidationPolicy policy = new JsonValidationPolicy(configuration);
            when(request.body()).thenReturn(Maybe.just(Buffer.buffer("{\"name\":123}")));

            // When
            policy
                .onRequest(ctx)
                .test()
                .assertError(throwable -> throwable instanceof MyCustomException);

            verify(ctx).interruptWith(executionFailureCaptor.capture());
            assertThat(executionFailureCaptor.getValue().key()).isEqualTo("JSON_INVALID_PAYLOAD");
        }

        @Test
        void badBodyNestedObject() throws IOException {
            // Given
            JsonValidationPolicy policy = new JsonValidationPolicy(configuration);
            // Payload similar to user's
            String payload = """
                {
                  "name": "foo",
                  "preferences": {
                    "newsletter": "string-instead-of-boolean"
                  }
                }""";
            when(request.body()).thenReturn(Maybe.just(Buffer.buffer(payload)));

            // When
            policy
                .onRequest(ctx)
                .test()
                .assertError(throwable -> throwable instanceof MyCustomException);

            verify(ctx).interruptWith(executionFailureCaptor.capture());
            assertThat(executionFailureCaptor.getValue().key()).isEqualTo("JSON_INVALID_PAYLOAD");
        }

        @Test
        void badBodyWithDetailedErrorReport_metricsKeepRawReport() throws IOException {
            when(configuration.isReturnDetailedErrorReport()).thenReturn(false);
            var metricsWithFlagOff = Metrics.builder().build();
            when(ctx.metrics()).thenReturn(metricsWithFlagOff);
            JsonValidationPolicy policyFlagOff = new JsonValidationPolicy(configuration);
            when(request.body()).thenReturn(Maybe.just(Buffer.buffer("{\"name2\":\"foo\"}")));

            policyFlagOff
                .onRequest(ctx)
                .test()
                .assertError(throwable -> throwable instanceof MyCustomException);

            when(configuration.isReturnDetailedErrorReport()).thenReturn(true);
            var metricsWithFlagOn = Metrics.builder().build();
            when(ctx.metrics()).thenReturn(metricsWithFlagOn);
            JsonValidationPolicy policyFlagOn = new JsonValidationPolicy(configuration);
            policyFlagOn
                .onRequest(ctx)
                .test()
                .assertError(throwable -> throwable instanceof MyCustomException);

            verify(ctx, times(2)).interruptWith(executionFailureCaptor.capture());
            String detailedMessage = executionFailureCaptor.getValue().message();
            assertThat(metricsWithFlagOn.getErrorMessage()).isNotNull();
            assertThat(metricsWithFlagOn.getErrorMessage()).isEqualTo(metricsWithFlagOff.getErrorMessage());
            assertThat(metricsWithFlagOn.getErrorMessage()).isNotEqualTo(detailedMessage);
        }

        @Test
        void badBodyWithDetailedErrorReport_errorKeyUnchanged() throws IOException {
            when(configuration.isReturnDetailedErrorReport()).thenReturn(false);
            JsonValidationPolicy policyFlagOff = new JsonValidationPolicy(configuration);
            when(request.body()).thenReturn(Maybe.just(Buffer.buffer("{\"name2\":\"foo\"}")));

            policyFlagOff
                .onRequest(ctx)
                .test()
                .assertError(throwable -> throwable instanceof MyCustomException);
            verify(ctx).interruptWith(executionFailureCaptor.capture());
            var failureFlagOff = executionFailureCaptor.getValue();

            when(configuration.isReturnDetailedErrorReport()).thenReturn(true);
            JsonValidationPolicy policyFlagOn = new JsonValidationPolicy(configuration);
            policyFlagOn
                .onRequest(ctx)
                .test()
                .assertError(throwable -> throwable instanceof MyCustomException);

            verify(ctx, times(2)).interruptWith(executionFailureCaptor.capture());
            var failureFlagOn = executionFailureCaptor.getValue();
            assertThat(failureFlagOn.key()).isEqualTo("JSON_INVALID_PAYLOAD");
            assertThat(failureFlagOff.key()).isEqualTo("JSON_INVALID_PAYLOAD");
        }

        @Test
        void badBodyWithDetailedErrorReport_keyAndStatusUnchanged() throws IOException {
            when(configuration.isReturnDetailedErrorReport()).thenReturn(true);
            JsonValidationPolicy policy = new JsonValidationPolicy(configuration);
            when(request.body()).thenReturn(Maybe.just(Buffer.buffer("{\"name2\":\"foo\"}")));

            policy
                .onRequest(ctx)
                .test()
                .assertError(throwable -> throwable instanceof MyCustomException);

            verify(ctx).interruptWith(executionFailureCaptor.capture());
            var failure = executionFailureCaptor.getValue();
            assertThat(failure.key()).isEqualTo("JSON_INVALID_PAYLOAD");
            assertThat(failure.statusCode()).isEqualTo(400);
        }

        private String detailedMessageFor(String body) throws IOException {
            return detailedMessageForSchema(DETAILED_SCHEMA, body);
        }

        private String detailedMessageForSchema(String schema, String body) throws IOException {
            var schemaSource = SchemaSource.builder().sourceType(SchemaSourceType.STATIC_SCHEMA).staticSchema(schema).build();
            when(configuration.getSchemaSource()).thenReturn(schemaSource);
            when(configuration.isReturnDetailedErrorReport()).thenReturn(true);
            JsonValidationPolicy policy = new JsonValidationPolicy(configuration);
            when(request.body()).thenReturn(Maybe.just(Buffer.buffer(body)));

            policy
                .onRequest(ctx)
                .test()
                .assertError(throwable -> throwable instanceof MyCustomException);

            verify(ctx).interruptWith(executionFailureCaptor.capture());
            return executionFailureCaptor.getValue().message();
        }

        @Test
        void detailReportsAllSiblingViolationsIncludingRequired() throws IOException {
            String message = detailedMessageFor("{\"age\":\"twenty\",\"email\":\"x\"}");

            assertThat(message).contains(REQUIRED_NAME_VIOLATION);
            assertThat(message).contains(AGE_VIOLATION);
            assertThat(message).contains(EMAIL_VIOLATION);
        }

        @Test
        void detailIsNotGenericWhenNoConfiguredMessage() throws IOException {
            String message = detailedMessageFor("{\"name\":\"Ada\",\"age\":\"twenty\",\"email\":\"x\"}");

            assertThat(message).doesNotContain("Bad Request");
            assertThat(message).contains(AGE_VIOLATION);
        }

        @Test
        void detailedReportBypassesSpelInConfiguredMessage() throws IOException {
            lenient().when(configuration.getErrorMessage()).thenReturn("{#request.headers['x-test']}");
            String message = detailedMessageFor("{\"name\":\"Ada\",\"age\":\"twenty\",\"email\":\"x\"}");

            assertThat(message).contains(AGE_VIOLATION);
            assertThat(message).doesNotContain("{#request.headers['x-test']}");
            verify(ctx, never()).getTemplateEngine();
        }

        @Test
        void flagOffEvaluatesSpelInConfiguredMessage() throws IOException {
            when(configuration.isReturnDetailedErrorReport()).thenReturn(false);
            when(configuration.getErrorMessage()).thenReturn("{#request.headers['x-test']}");
            TemplateEngine templateEngine = mock(TemplateEngine.class);
            when(ctx.getTemplateEngine()).thenReturn(templateEngine);
            when(templateEngine.eval("{#request.headers['x-test']}", String.class)).thenReturn(Maybe.just("my-value"));
            JsonValidationPolicy policy = new JsonValidationPolicy(configuration);
            when(request.body()).thenReturn(Maybe.just(Buffer.buffer("{\"name2\":\"foo\"}")));

            policy
                .onRequest(ctx)
                .test()
                .assertError(throwable -> throwable instanceof MyCustomException);

            verify(ctx).interruptWith(executionFailureCaptor.capture());
            verify(templateEngine).eval("{#request.headers['x-test']}", String.class);
            var failure = executionFailureCaptor.getValue();
            assertThat(failure.message()).contains("my-value");
            assertThat(failure.message()).doesNotContain("{#request.headers");
        }

        @Test
        void spelEvalFailureFallsBackToGenericBadRequest() throws IOException {
            when(configuration.isReturnDetailedErrorReport()).thenReturn(false);
            when(configuration.getErrorMessage()).thenReturn("{#request.headers['x-test']}");
            TemplateEngine templateEngine = mock(TemplateEngine.class);
            when(ctx.getTemplateEngine()).thenReturn(templateEngine);
            when(templateEngine.eval("{#request.headers['x-test']}", String.class)).thenReturn(
                Maybe.error(new RuntimeException("SpEL eval failed"))
            );
            JsonValidationPolicy policy = new JsonValidationPolicy(configuration);
            when(request.body()).thenReturn(Maybe.just(Buffer.buffer("{\"name2\":\"foo\"}")));

            policy
                .onRequest(ctx)
                .test()
                .assertError(throwable -> throwable instanceof MyCustomException);

            verify(ctx).interruptWith(executionFailureCaptor.capture());
            assertThat(executionFailureCaptor.getValue().message()).isEqualTo("Bad Request");
        }

        @Test
        void detailRendersNestedPointer() throws IOException {
            String message = detailedMessageForSchema(NESTED_SCHEMA, "{\"address\":{\"street\":\"not-an-int\"}}");

            assertThat(message).contains("/address/street");
        }

        @Test
        void deduplicationPreventsDuplicateViolationLines() throws IOException {
            String message = detailedMessageForSchema(DEDUP_SCHEMA, "{\"age\":\"twenty\"}");

            int occurrences = message.split(Pattern.quote(AGE_VIOLATION), -1).length - 1;
            assertThat(occurrences).isEqualTo(1);
        }

        @Test
        void validateUncheckedSurfacesSchemaAnomalyInDetailedReport() throws IOException {
            var schemaSource = SchemaSource.builder()
                .sourceType(SchemaSourceType.STATIC_SCHEMA)
                .staticSchema(UNRESOLVABLE_REF_SCHEMA)
                .build();
            when(configuration.getSchemaSource()).thenReturn(schemaSource);
            when(configuration.isValidateUnchecked()).thenReturn(true);
            when(configuration.isReturnDetailedErrorReport()).thenReturn(true);
            JsonValidationPolicy policy = new JsonValidationPolicy(configuration);
            when(request.body()).thenReturn(Maybe.just(Buffer.buffer("{\"name\":\"foo\"}")));

            policy
                .onRequest(ctx)
                .test()
                .assertError(throwable -> throwable instanceof MyCustomException);

            verify(ctx).interruptWith(executionFailureCaptor.capture());
            var failure = executionFailureCaptor.getValue();
            assertThat(failure.key()).isEqualTo("JSON_INVALID_PAYLOAD");
            assertThat(failure.statusCode()).isEqualTo(400);
            assertThat(failure.message())
                .doesNotContain("Bad Request")
                .contains("unable to dereference URI \"http://nonexistent.invalid/missing.json#\"");
        }

        @Test
        void nonJsonIsGenericFormatWithoutDetailEvenWhenDetailEnabled() throws IOException {
            lenient().when(configuration.isReturnDetailedErrorReport()).thenReturn(true);
            JsonValidationPolicy policy = new JsonValidationPolicy(configuration);
            when(request.body()).thenReturn(Maybe.just(Buffer.buffer("not json")));

            policy
                .onRequest(ctx)
                .test()
                .assertError(throwable -> throwable instanceof MyCustomException);

            verify(ctx).interruptWith(executionFailureCaptor.capture());
            var failure = executionFailureCaptor.getValue();
            assertThat(failure.key()).isEqualTo("JSON_INVALID_FORMAT");
            assertThat(failure.message()).isEqualTo("Bad Request");
        }

        @Test
        void runtimeExceptionInDetailBuildingProducesGracefulErrorAndLogsAtError() throws IOException {
            when(configuration.isReturnDetailedErrorReport()).thenReturn(true);
            JsonValidationPolicy policy = new JsonValidationPolicy(configuration) {
                @Override
                protected java.util.Optional<String> buildDetailedMessage(
                    com.fasterxml.jackson.databind.JsonNode schema,
                    com.fasterxml.jackson.databind.JsonNode content
                ) {
                    throw new RuntimeException("injected");
                }
            };
            when(request.body()).thenReturn(Maybe.just(Buffer.buffer("{\"name2\":\"foo\"}")));

            ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(
                JsonValidationPolicy.class
            );
            ch.qos.logback.classic.Level originalLevel = logger.getLevel();
            ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
            appender.start();
            logger.addAppender(appender);
            logger.setLevel(ch.qos.logback.classic.Level.ERROR);
            try {
                policy
                    .onRequest(ctx)
                    .test()
                    .assertError(throwable -> throwable instanceof MyCustomException);

                verify(ctx).interruptWith(executionFailureCaptor.capture());
                assertThat(executionFailureCaptor.getValue().statusCode()).isEqualTo(400);
                org.assertj.core.api.Assertions.assertThat(appender.list).anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(ch.qos.logback.classic.Level.ERROR);
                    assertThat(event.getFormattedMessage()).contains("Unexpected error during JSON validation");
                });
            } finally {
                logger.setLevel(originalLevel);
                logger.detachAppender(appender);
                appender.stop();
            }
        }

        @Test
        void detailedReportIsCappedWhenMoreThanFiftyElementsFail() throws IOException {
            String arrayOfIntegersSchema = """
                {
                  "$schema": "http://json-schema.org/draft-04/schema#",
                  "type": "array",
                  "items": { "type": "integer" }
                }""";
            StringBuilder payload = new StringBuilder("[");
            for (int i = 0; i < 100; i++) {
                if (i > 0) payload.append(", ");
                payload.append("\"item").append(i).append("\"");
            }
            payload.append("]");

            String message = detailedMessageForSchema(arrayOfIntegersSchema, payload.toString());

            long violationCount = message
                .chars()
                .filter(c -> c == '—')
                .count();
            assertThat(violationCount).isLessThanOrEqualTo(50);
        }
    }

    @ExtendWith(MockitoExtension.class)
    @Nested
    class OnResponseTest {

        @Mock
        HttpPlainExecutionContext ctx;

        @Mock
        HttpPlainResponse response;

        @Captor
        ArgumentCaptor<ExecutionFailure> executionFailureCaptor;

        @BeforeEach
        void setUp() {
            var metrics = Metrics.builder().build();
            lenient().when(ctx.metrics()).thenReturn(metrics);
            lenient().when(ctx.interruptWith(any())).thenReturn(Completable.error(new MyCustomException()));
            when(ctx.response()).thenReturn(response);
        }

        private ExecutionFailure failureFor(String body, boolean detailed) throws IOException {
            var schemaSource = SchemaSource.builder().sourceType(SchemaSourceType.STATIC_SCHEMA).staticSchema(DETAILED_SCHEMA).build();
            when(configuration.getSchemaSource()).thenReturn(schemaSource);
            lenient().when(configuration.isReturnDetailedErrorReport()).thenReturn(detailed);
            JsonValidationPolicy policy = new JsonValidationPolicy(configuration);
            when(response.body()).thenReturn(Maybe.just(Buffer.buffer(body)));

            policy
                .onResponse(ctx)
                .test()
                .assertError(throwable -> throwable instanceof MyCustomException);

            verify(ctx).interruptWith(executionFailureCaptor.capture());
            return executionFailureCaptor.getValue();
        }

        @Test
        void detailContainsViolations() throws IOException {
            var failure = failureFor("{\"name\":\"Ada\",\"age\":\"twenty\",\"email\":\"x\"}", true);

            assertThat(failure.statusCode()).isEqualTo(500);
            assertThat(failure.message()).contains(AGE_VIOLATION);
            assertThat(failure.message()).contains(EMAIL_VIOLATION);
        }

        @Test
        void flagOffMessageIsGenericWithoutDetail() throws IOException {
            var failure = failureFor("{\"name\":\"Ada\",\"age\":\"twenty\",\"email\":\"x\"}", false);

            assertThat(failure.statusCode()).isEqualTo(500);
            assertThat(failure.message()).isEqualTo("Internal Error");
        }

        @Test
        void nonJsonIsGenericFormatWithoutDetail() throws IOException {
            var failure = failureFor("not json", true);

            assertThat(failure.statusCode()).isEqualTo(500);
            assertThat(failure.message()).isEqualTo("Internal Error");
        }

        @Test
        void straightRespondWithDetailedReportPassesResponseThroughUnchanged() throws IOException {
            var schemaSource = SchemaSource.builder().sourceType(SchemaSourceType.STATIC_SCHEMA).staticSchema(DETAILED_SCHEMA).build();
            when(configuration.getSchemaSource()).thenReturn(schemaSource);
            when(configuration.isStraightRespondMode()).thenReturn(true);
            lenient().when(configuration.isReturnDetailedErrorReport()).thenReturn(true);
            var policy = new JsonValidationPolicy(configuration);
            when(response.body()).thenReturn(Maybe.just(Buffer.buffer(INVALID_DETAILED_PAYLOAD)));

            policy.onResponse(ctx).test().assertComplete();

            verify(ctx, never()).interruptWith(any());
        }

        @Test
        void straightRespondWithDetailedReportRecordsValidationReportInMetrics() throws IOException {
            var schemaSource = SchemaSource.builder().sourceType(SchemaSourceType.STATIC_SCHEMA).staticSchema(DETAILED_SCHEMA).build();
            when(configuration.getSchemaSource()).thenReturn(schemaSource);
            when(configuration.isStraightRespondMode()).thenReturn(true);
            lenient().when(configuration.isReturnDetailedErrorReport()).thenReturn(true);
            var metrics = Metrics.builder().build();
            when(ctx.metrics()).thenReturn(metrics);
            var policy = new JsonValidationPolicy(configuration);
            when(response.body()).thenReturn(Maybe.just(Buffer.buffer(INVALID_DETAILED_PAYLOAD)));

            policy.onResponse(ctx).test().assertComplete();

            assertThat(metrics.getErrorMessage())
                .isNotBlank()
                .contains("instance type (string) does not match any allowed primitive type (allowed: [\"integer\"])")
                .contains("string \"x\" is too short (length: 1, required minimum: 5)");
        }

        @Test
        void formatErrorUsesFormatKey() throws IOException {
            JsonValidationPolicy policy = new JsonValidationPolicy(configuration);
            when(response.body()).thenReturn(Maybe.just(Buffer.buffer("qwerty")));

            policy
                .onResponse(ctx)
                .test()
                .assertError(throwable -> throwable instanceof MyCustomException);

            verify(ctx).interruptWith(executionFailureCaptor.capture());
            assertThat(executionFailureCaptor.getValue().key()).isEqualTo("JSON_INVALID_RESPONSE_FORMAT");
        }

        @Test
        void payloadErrorUsesPayloadKey() throws IOException {
            JsonValidationPolicy policy = new JsonValidationPolicy(configuration);
            when(response.body()).thenReturn(Maybe.just(Buffer.buffer("{\"name2\":\"foo\"}")));

            policy
                .onResponse(ctx)
                .test()
                .assertError(throwable -> throwable instanceof MyCustomException);

            verify(ctx).interruptWith(executionFailureCaptor.capture());
            assertThat(executionFailureCaptor.getValue().key()).isEqualTo("JSON_INVALID_RESPONSE_PAYLOAD");
        }
    }

    @Nested
    class OnMessageRequestTest {

        @Mock
        HttpMessageExecutionContext ctx;

        @Mock
        HttpMessageRequest request;

        @Captor
        ArgumentCaptor<java.util.function.Function<Message, Maybe<Message>>> messageCaptor;

        @Captor
        ArgumentCaptor<ExecutionFailure> executionFailureCaptor;

        @BeforeEach
        void setUp() {
            when(ctx.request()).thenReturn(request);
            when(request.onMessage(any())).thenReturn(Completable.complete());
        }

        @Test
        void testOnMessageRequest_validationSucceeds() throws IOException {
            JsonValidationPolicy policy = new JsonValidationPolicy(configuration);
            DefaultMessage message = DefaultMessage.builder().id("id").content(Buffer.buffer("{\"name\":\"foo\"}")).build();
            policy.onMessageRequest(ctx).test().assertComplete();
            verify(request).onMessage(messageCaptor.capture());
            messageCaptor.getValue().apply(message).test().assertValue(message);
        }

        @Test
        void testOnMessageRequest_validationFails() throws IOException {
            var metrics = Metrics.builder().build();
            lenient().when(ctx.metrics()).thenReturn(metrics);
            when(ctx.interruptMessageWith(any())).thenReturn(Maybe.error(new MyCustomException()));
            JsonValidationPolicy policy = new JsonValidationPolicy(configuration);
            DefaultMessage message = DefaultMessage.builder().id("id").content(Buffer.buffer("{\"name2\":\"foo\"}")).build();
            policy.onMessageRequest(ctx).test().assertComplete();
            verify(request).onMessage(messageCaptor.capture());
            messageCaptor
                .getValue()
                .apply(message)
                .test()
                .assertError(throwable -> throwable instanceof MyCustomException);

            verify(ctx).interruptMessageWith(executionFailureCaptor.capture());
            assertThat(executionFailureCaptor.getValue().key()).isEqualTo("JSON_INVALID_MESSAGE_REQUEST_PAYLOAD");
        }

        private ExecutionFailure messageFailureFor(String body) throws IOException {
            var schemaSource = SchemaSource.builder().sourceType(SchemaSourceType.STATIC_SCHEMA).staticSchema(DETAILED_SCHEMA).build();
            when(configuration.getSchemaSource()).thenReturn(schemaSource);
            lenient().when(configuration.isReturnDetailedErrorReport()).thenReturn(true);
            lenient().when(ctx.metrics()).thenReturn(Metrics.builder().build());
            when(ctx.interruptMessageWith(any())).thenReturn(Maybe.error(new MyCustomException()));
            JsonValidationPolicy policy = new JsonValidationPolicy(configuration);
            DefaultMessage message = DefaultMessage.builder().id("id").content(Buffer.buffer(body)).build();

            policy.onMessageRequest(ctx).test().assertComplete();
            verify(request).onMessage(messageCaptor.capture());
            messageCaptor
                .getValue()
                .apply(message)
                .test()
                .assertError(throwable -> throwable instanceof MyCustomException);

            verify(ctx).interruptMessageWith(executionFailureCaptor.capture());
            return executionFailureCaptor.getValue();
        }

        @Test
        void messageRequestPayloadFailureCarriesDetail() throws IOException {
            var failure = messageFailureFor(INVALID_DETAILED_PAYLOAD);

            assertThat(failure.statusCode()).isEqualTo(400);
            assertThat(failure.key()).isEqualTo("JSON_INVALID_MESSAGE_REQUEST_PAYLOAD");
            assertThat(failure.message()).contains(AGE_VIOLATION);
            assertThat(failure.message()).contains(EMAIL_VIOLATION);
        }

        @Test
        void messageRequestFormatErrorUsesFormatKeyWithNoDetail() throws IOException {
            var failure = messageFailureFor(NON_JSON);

            assertThat(failure.key()).isEqualTo("JSON_INVALID_MESSAGE_REQUEST_FORMAT");
            assertThat(failure.statusCode()).isEqualTo(400);
            assertThat(failure.message()).isEqualTo("Bad Request");
        }
    }

    @Nested
    class OnMessageResponseTest {

        @Mock
        HttpMessageExecutionContext ctx;

        @Mock
        HttpMessageResponse response;

        @Captor
        ArgumentCaptor<java.util.function.Function<Message, Maybe<Message>>> messageCaptor;

        @Captor
        ArgumentCaptor<ExecutionFailure> executionFailureCaptor;

        @BeforeEach
        void setUp() {
            when(ctx.response()).thenReturn(response);
            lenient().when(response.onMessage(any())).thenReturn(Completable.complete());
        }

        @Test
        void testOnMessageResponse_validationSucceeds() throws IOException {
            JsonValidationPolicy policy = new JsonValidationPolicy(configuration);
            DefaultMessage message = DefaultMessage.builder().id("id").content(Buffer.buffer("{\"name\":\"foo\"}")).build();
            policy.onMessageResponse(ctx).test().assertComplete();
            verify(response).onMessage(messageCaptor.capture());
            messageCaptor.getValue().apply(message).test().assertValue(message);
        }

        @Test
        void testOnMessageResponse_validationFails() throws IOException {
            var metrics = Metrics.builder().build();
            lenient().when(ctx.metrics()).thenReturn(metrics);
            lenient().when(ctx.interruptMessageWith(any())).thenReturn(Maybe.error(new MyCustomException()));
            JsonValidationPolicy policy = new JsonValidationPolicy(configuration);
            DefaultMessage message = DefaultMessage.builder().id("id").content(Buffer.buffer("{\"name2\":\"foo\"}")).build();
            policy.onMessageResponse(ctx).test().assertComplete();
            verify(response).onMessage(messageCaptor.capture());
            messageCaptor
                .getValue()
                .apply(message)
                .test()
                .assertError(throwable -> throwable instanceof MyCustomException);

            verify(ctx).interruptMessageWith(executionFailureCaptor.capture());
            assertThat(executionFailureCaptor.getValue().key()).isEqualTo("JSON_INVALID_MESSAGE_RESPONSE_PAYLOAD");
        }

        private ExecutionFailure messageFailureFor(String body) throws IOException {
            var schemaSource = SchemaSource.builder().sourceType(SchemaSourceType.STATIC_SCHEMA).staticSchema(DETAILED_SCHEMA).build();
            when(configuration.getSchemaSource()).thenReturn(schemaSource);
            lenient().when(configuration.isReturnDetailedErrorReport()).thenReturn(true);
            lenient().when(ctx.metrics()).thenReturn(Metrics.builder().build());
            when(ctx.interruptMessageWith(any())).thenReturn(Maybe.error(new MyCustomException()));
            JsonValidationPolicy policy = new JsonValidationPolicy(configuration);
            DefaultMessage message = DefaultMessage.builder().id("id").content(Buffer.buffer(body)).build();

            policy.onMessageResponse(ctx).test().assertComplete();
            verify(response).onMessage(messageCaptor.capture());
            messageCaptor
                .getValue()
                .apply(message)
                .test()
                .assertError(throwable -> throwable instanceof MyCustomException);

            verify(ctx).interruptMessageWith(executionFailureCaptor.capture());
            return executionFailureCaptor.getValue();
        }

        @Test
        void messageResponsePayloadFailureCarriesDetail() throws IOException {
            var failure = messageFailureFor(INVALID_DETAILED_PAYLOAD);

            assertThat(failure.statusCode()).isEqualTo(400);
            assertThat(failure.key()).isEqualTo("JSON_INVALID_MESSAGE_RESPONSE_PAYLOAD");
            assertThat(failure.message()).contains(AGE_VIOLATION);
            assertThat(failure.message()).contains(EMAIL_VIOLATION);
        }

        @Test
        void messageResponseFormatErrorUsesFormatKeyWithNoDetail() throws IOException {
            var failure = messageFailureFor(NON_JSON);

            assertThat(failure.key()).isEqualTo("JSON_INVALID_MESSAGE_RESPONSE_FORMAT");
            assertThat(failure.statusCode()).isEqualTo(400);
            assertThat(failure.message()).isEqualTo("Bad Request");
        }
    }

    @Nested
    class OnKafkaMessageRequestTest {

        @Mock
        KafkaMessageExecutionContext msgCtx;

        @Mock
        KafkaExecutionContext ctx;

        @Mock
        KafkaMessageRequest messageRequest;

        @Mock
        KafkaRequest request;

        @Mock
        ProduceRequest produceRequest;

        @Captor
        ArgumentCaptor<java.util.function.Function<KafkaMessage, Maybe<KafkaMessage>>> messageCaptor;

        @BeforeEach
        void setUp() {
            lenient().when(msgCtx.executionContext()).thenReturn(ctx);

            when(msgCtx.request()).thenReturn(messageRequest);
            when(messageRequest.onMessage(any())).thenReturn(Completable.complete());
            when(configuration.getNativeErrorHandling()).thenReturn(createNativeErrorHandling(FAIL_WITH_INVALID_RECORD));

            lenient().when(ctx.request()).thenReturn(request);
            lenient().when(request.delegate()).thenReturn(produceRequest);
        }

        @Test
        void testOnMessageRequest_validationSucceeds() throws IOException {
            JsonValidationPolicy policy = new JsonValidationPolicy(configuration);
            KafkaMessageStub message = new KafkaMessageStub("{\"name\":\"foo\"}");

            policy.onMessageRequest(msgCtx).test().assertComplete();

            verify(messageRequest).onMessage(messageCaptor.capture());
            messageCaptor.getValue().apply(message).test().assertValue(message);
        }

        @Test
        void testOnMessageRequest_validationFailsWithInvalidRecord() throws IOException {
            JsonValidationPolicy policy = new JsonValidationPolicy(configuration);
            KafkaMessageStub message = new KafkaMessageStub("{\"name2\":\"foo\"}");

            when(produceRequest.getErrorResponse(anyInt(), any(InvalidRecordException.class))).thenReturn(
                createFailedProduceResponseWithTwoPartitions(Errors.INVALID_RECORD)
            );
            when(ctx.interruptWith(any(AbstractResponse.class))).thenReturn(Completable.error(new MyCustomException()));

            policy.onMessageRequest(msgCtx).test().assertComplete();

            verify(messageRequest).onMessage(messageCaptor.capture());
            messageCaptor
                .getValue()
                .apply(message)
                .test()
                .assertError(throwable -> throwable instanceof MyCustomException);
        }
    }

    @Nested
    class OnKafkaMessageResponseTest {

        @Mock
        KafkaMessageExecutionContext msgCtx;

        @Mock
        KafkaExecutionContext ctx;

        @Mock
        KafkaMessageResponse messageResponse;

        @Mock
        KafkaResponse response;

        @Captor
        ArgumentCaptor<java.util.function.Function<KafkaMessage, Maybe<KafkaMessage>>> messageCaptor;

        @BeforeEach
        void setUp() {
            lenient().when(msgCtx.executionContext()).thenReturn(ctx);

            when(msgCtx.response()).thenReturn(messageResponse);
            when(messageResponse.onMessage(any())).thenReturn(Completable.complete());

            lenient().when(ctx.response()).thenReturn(response);
            lenient().when(response.delegate()).thenReturn(createFetchResponseWithTwoPartitions());
        }

        @Test
        void testOnMessageResponse_validationSucceeds() throws IOException {
            when(configuration.getNativeErrorHandling()).thenReturn(createNativeErrorHandling(INVALIDATE_PARTITION));

            JsonValidationPolicy policy = new JsonValidationPolicy(configuration);
            KafkaMessageStub message = new KafkaMessageStub("{\"name\":\"foo\"}");

            policy.onMessageResponse(msgCtx).test().assertComplete();

            verify(messageResponse).onMessage(messageCaptor.capture());
            messageCaptor.getValue().apply(message).test().assertValue(message);
        }

        @Test
        void testOnMessageResponse_validationFailsWithInvalidatePartition() throws IOException {
            when(configuration.getNativeErrorHandling()).thenReturn(createNativeErrorHandling(INVALIDATE_PARTITION));

            JsonValidationPolicy policy = new JsonValidationPolicy(configuration);
            KafkaMessageStub message = new KafkaMessageStub("{\"name2\":\"foo\"}");

            when(ctx.interruptWith(any(AbstractResponse.class))).thenReturn(Completable.error(new MyCustomException()));

            policy.onMessageResponse(msgCtx).test().assertComplete();

            verify(messageResponse).onMessage(messageCaptor.capture());
            messageCaptor
                .getValue()
                .apply(message)
                .test()
                .assertError(throwable -> throwable instanceof MyCustomException);
        }

        @Test
        void testOnMessageResponse_validationFailsWithAddRecordHeader() throws IOException {
            when(configuration.getNativeErrorHandling()).thenReturn(createNativeErrorHandling(ADD_RECORD_HEADER));

            JsonValidationPolicy policy = new JsonValidationPolicy(configuration);
            KafkaMessageStub originalMessage = new KafkaMessageStub("{\"name2\":\"foo\"}");

            policy.onMessageResponse(msgCtx).test().assertComplete();

            verify(messageResponse).onMessage(messageCaptor.capture());
            messageCaptor
                .getValue()
                .apply(originalMessage)
                .test()
                .assertValue(actualMessage -> actualMessage.recordHeaders().containsKey(TEST_HEADER_NAME));
        }
    }

    private static class MyCustomException extends RuntimeException {}
}
