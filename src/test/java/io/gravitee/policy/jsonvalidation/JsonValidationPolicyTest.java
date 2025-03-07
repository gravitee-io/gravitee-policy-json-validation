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
import io.gravitee.gateway.reactive.api.context.http.*;
import io.gravitee.gateway.reactive.api.message.DefaultMessage;
import io.gravitee.gateway.reactive.api.message.Message;
import io.gravitee.policy.jsonvalidation.configuration.JsonValidationPolicyConfiguration;
import io.gravitee.reporter.api.v4.metric.Metrics;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JsonValidationPolicyTest {

    private static final String JSON_SCHEMA =
        """
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

    @ExtendWith(MockitoExtension.class)
    @Nested
    class OnRequestTest {

        @Mock
        HttpPlainExecutionContext ctx;

        @Mock
        HttpPlainRequest request;

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
            policy.onRequest(ctx).test().assertError(throwable -> throwable instanceof MyCustomException);
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
            policy.onRequest(ctx).test().assertError(throwable -> throwable instanceof MyCustomException);
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
            messageCaptor.getValue().apply(message).test().assertError(throwable -> throwable instanceof MyCustomException);
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

        @BeforeEach
        void setUp() {
            when(ctx.response()).thenReturn(response);
            lenient().when(response.onMessage(any())).thenReturn(Completable.complete());
        }

        @Test
        void testOnMessageReesponse_validationSucceeds() throws IOException {
            JsonValidationPolicy policy = new JsonValidationPolicy(configuration);
            DefaultMessage message = DefaultMessage.builder().id("id").content(Buffer.buffer("{\"name\":\"foo\"}")).build();
            policy.onMessageResponse(ctx).test().assertComplete();
            verify(response).onMessage(messageCaptor.capture());
            messageCaptor.getValue().apply(message).test().assertValue(message);
        }

        @Test
        void testOnMessageReesponse_validationFails() throws IOException {
            var metrics = Metrics.builder().build();
            lenient().when(ctx.metrics()).thenReturn(metrics);
            lenient().when(ctx.interruptMessageWith(any())).thenReturn(Maybe.error(new MyCustomException()));
            JsonValidationPolicy policy = new JsonValidationPolicy(configuration);
            DefaultMessage message = DefaultMessage.builder().id("id").content(Buffer.buffer("{\"name2\":\"foo\"}")).build();
            policy.onMessageResponse(ctx).test().assertComplete();
            verify(response).onMessage(messageCaptor.capture());
            messageCaptor.getValue().apply(message).test().assertError(throwable -> throwable instanceof MyCustomException);
        }
    }

    private static class MyCustomException extends RuntimeException {}
}
