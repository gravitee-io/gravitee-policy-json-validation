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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.reactive.api.context.http.HttpPlainExecutionContext;
import io.gravitee.gateway.reactive.api.context.http.HttpPlainRequest;
import io.gravitee.policy.jsonvalidation.configuration.JsonValidationPolicyConfiguration;
import io.gravitee.reporter.api.v4.metric.Metrics;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    class OnResponseTest {}

    private static class MyCustomException extends RuntimeException {}
}
