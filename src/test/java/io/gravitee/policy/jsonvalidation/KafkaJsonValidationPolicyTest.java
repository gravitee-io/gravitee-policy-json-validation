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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.reactive.api.context.kafka.KafkaExecutionContext;
import io.gravitee.gateway.reactive.api.context.kafka.KafkaMessageExecutionContext;
import io.gravitee.gateway.reactive.api.context.kafka.KafkaMessageRequest;
import io.gravitee.gateway.reactive.api.context.kafka.KafkaMessageResponse;
import io.gravitee.gateway.reactive.api.message.kafka.KafkaMessage;
import io.gravitee.policy.JsonValidationException;
import io.gravitee.policy.jsonvalidation.configuration.JsonValidationPolicyConfiguration;
import io.gravitee.reporter.api.v4.metric.Metrics;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import java.io.IOException;
import java.util.function.Function;
import org.apache.kafka.common.protocol.Errors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KafkaJsonValidationPolicyTest {

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

    @Mock
    private JsonValidationPolicyConfiguration configuration;

    @BeforeEach
    void setUp() {
        when(configuration.getSchema()).thenReturn(JSON_SCHEMA);
    }

    @Nested
    class OnMessageRequestTest {

        @Mock
        KafkaMessageExecutionContext ctx;

        @Mock
        KafkaExecutionContext executionContext;

        @Mock
        KafkaMessageRequest request;

        @Mock
        KafkaMessage kafkaMessage;

        @Captor
        ArgumentCaptor<Function<KafkaMessage, Maybe<KafkaMessage>>> messageCaptor;

        @BeforeEach
        void setUp() {
            when(ctx.request()).thenReturn(request);
            when(request.onMessage(any())).thenReturn(Completable.complete());
        }

        @Test
        void should_validate_message_when_json_is_valid() throws IOException {
            // Given
            JsonValidationPolicy policy = new JsonValidationPolicy(configuration);
            when(kafkaMessage.content()).thenReturn(Buffer.buffer("{\"name\":\"foo\"}"));

            // When
            policy.onMessageRequest(ctx).test().assertComplete();

            // Then
            verify(request).onMessage(messageCaptor.capture());
            messageCaptor.getValue().apply(kafkaMessage).test().assertValue(kafkaMessage);
        }

        @Test
        void should_interrupt_when_json_does_not_match_schema() throws IOException {
            // Given
            var metrics = Metrics.builder().build();
            when(ctx.metrics()).thenReturn(metrics);
            when(ctx.executionContext()).thenReturn(executionContext);
            when(executionContext.interruptWith(Errors.INVALID_REQUEST)).thenReturn(Completable.error(new KafkaValidationException()));

            JsonValidationPolicy policy = new JsonValidationPolicy(configuration);
            when(kafkaMessage.content()).thenReturn(Buffer.buffer("{\"name2\":\"foo\"}"));

            // When
            policy.onMessageRequest(ctx).test().assertComplete();

            // Then
            verify(request).onMessage(messageCaptor.capture());
            messageCaptor.getValue().apply(kafkaMessage).test().assertError(KafkaValidationException.class);
        }

        @Test
        void should_throw_exception_when_content_is_not_valid_json() throws IOException {
            // Given
            JsonValidationPolicy policy = new JsonValidationPolicy(configuration);
            when(kafkaMessage.content()).thenReturn(Buffer.buffer("not json"));

            // When
            policy.onMessageRequest(ctx).test().assertComplete();

            // Then
            verify(request).onMessage(messageCaptor.capture());
            Assertions.assertThrows(JsonValidationException.class, () -> messageCaptor.getValue().apply(kafkaMessage));
        }
    }

    @Nested
    class OnMessageResponseTest {

        @Mock
        KafkaMessageExecutionContext ctx;

        @Mock
        KafkaExecutionContext executionContext;

        @Mock
        KafkaMessageResponse response;

        @Mock
        KafkaMessage kafkaMessage;

        @Captor
        ArgumentCaptor<Function<KafkaMessage, Maybe<KafkaMessage>>> messageCaptor;

        @BeforeEach
        void setUp() {
            when(ctx.response()).thenReturn(response);
            when(response.onMessage(any())).thenReturn(Completable.complete());
        }

        @Test
        void should_validate_message_when_json_is_valid() throws IOException {
            // Given
            JsonValidationPolicy policy = new JsonValidationPolicy(configuration);
            when(kafkaMessage.content()).thenReturn(Buffer.buffer("{\"name\":\"foo\"}"));

            // When
            policy.onMessageResponse(ctx).test().assertComplete();

            // Then
            verify(response).onMessage(messageCaptor.capture());
            messageCaptor.getValue().apply(kafkaMessage).test().assertValue(kafkaMessage);
        }

        @Test
        void should_interrupt_when_json_does_not_match_schema() throws IOException {
            // Given
            var metrics = Metrics.builder().build();
            when(ctx.metrics()).thenReturn(metrics);
            when(ctx.executionContext()).thenReturn(executionContext);
            when(executionContext.interruptWith(Errors.INVALID_REQUEST)).thenReturn(Completable.error(new KafkaValidationException()));

            JsonValidationPolicy policy = new JsonValidationPolicy(configuration);
            when(kafkaMessage.content()).thenReturn(Buffer.buffer("{\"invalid\":\"data\"}"));

            // When
            policy.onMessageResponse(ctx).test().assertComplete();

            // Then
            verify(response).onMessage(messageCaptor.capture());
            messageCaptor.getValue().apply(kafkaMessage).test().assertError(KafkaValidationException.class);
        }

        @Test
        void should_throw_exception_when_content_is_not_valid_json() throws IOException {
            // Given
            JsonValidationPolicy policy = new JsonValidationPolicy(configuration);
            when(kafkaMessage.content()).thenReturn(Buffer.buffer("invalid json"));

            // When
            policy.onMessageResponse(ctx).test().assertComplete();

            // Then
            verify(response).onMessage(messageCaptor.capture());
            Assertions.assertThrows(JsonValidationException.class, () -> messageCaptor.getValue().apply(kafkaMessage));
        }
    }

    private static class KafkaValidationException extends RuntimeException {}
}
