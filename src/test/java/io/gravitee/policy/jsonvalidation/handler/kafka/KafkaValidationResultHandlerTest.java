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
package io.gravitee.policy.jsonvalidation.handler.kafka;

import static io.gravitee.policy.jsonvalidation.configuration.errorhandling.PublishValidationErrorStrategy.FAIL_WITH_INVALID_RECORD;
import static io.gravitee.policy.jsonvalidation.configuration.errorhandling.SubscribeErrorHandlingStrategy.ADD_RECORD_HEADER;
import static io.gravitee.policy.jsonvalidation.configuration.errorhandling.SubscribeErrorHandlingStrategy.INVALIDATE_PARTITION;
import static io.gravitee.policy.jsonvalidation.handler.kafka.KafkaValidationResultHandlerFactory.createValidationResultHandler;
import static io.gravitee.policy.jsonvalidation.kafka.factory.TestKafkaApiMessageFactory.*;
import static io.gravitee.policy.jsonvalidation.kafka.factory.TestNativeErrorHandlingConfigurationFactory.TEST_HEADER_NAME;
import static io.gravitee.policy.jsonvalidation.kafka.factory.TestNativeErrorHandlingConfigurationFactory.createNativeErrorHandling;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import io.gravitee.gateway.reactive.api.context.kafka.KafkaExecutionContext;
import io.gravitee.gateway.reactive.api.context.kafka.KafkaMessageExecutionContext;
import io.gravitee.gateway.reactive.api.context.kafka.KafkaRequest;
import io.gravitee.gateway.reactive.api.context.kafka.KafkaResponse;
import io.gravitee.policy.jsonvalidation.configuration.errorhandling.NativeErrorHandling;
import io.gravitee.policy.jsonvalidation.kafka.stub.KafkaMessageStub;
import io.reactivex.rxjava3.core.Completable;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.kafka.common.InvalidRecordException;
import org.apache.kafka.common.protocol.Errors;
import org.apache.kafka.common.requests.AbstractResponse;
import org.apache.kafka.common.requests.FetchResponse;
import org.apache.kafka.common.requests.ProduceRequest;
import org.apache.kafka.common.requests.ProduceResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class KafkaValidationResultHandlerTest {

    @ExtendWith(MockitoExtension.class)
    @Nested
    class FailProduceRequestWithInvalidRecordTest {

        @Mock
        KafkaMessageExecutionContext msgCtx;

        @Mock
        KafkaExecutionContext ctx;

        @Mock
        KafkaRequest request;

        @Mock
        ProduceRequest produceRequest;

        NativeErrorHandling errorHandling = createNativeErrorHandling(FAIL_WITH_INVALID_RECORD);
        KafkaValidationResultHandler handler = createValidationResultHandler(errorHandling.getOnPublish());

        KafkaMessageStub message = new KafkaMessageStub("{\"name\":\"foo\"}");

        @BeforeEach
        void setUp() {
            lenient().when(ctx.request()).thenReturn(request);
            lenient().when(msgCtx.executionContext()).thenReturn(ctx);
            lenient().when(request.delegate()).thenReturn(produceRequest);
        }

        @Test
        public void testOnSuccess() {
            handler.onSuccess(msgCtx, message).test().assertComplete();
        }

        @Test
        public void testOnError() {
            ProduceResponse failedProduceResponse = createFailedProduceResponseWithTwoPartitions(Errors.INVALID_RECORD);
            lenient().when(produceRequest.getErrorResponse(anyInt(), any(InvalidRecordException.class))).thenReturn(failedProduceResponse);

            when(ctx.interruptWith(any(ProduceResponse.class))).thenReturn(Completable.complete());

            handler.onError(msgCtx, message, "Validation failed").test().assertComplete();

            verify(ctx).interruptWith(
                argThat((ProduceResponse actualResponse) -> {
                    var partitionResponses = actualResponse.data().responses().find(TEST_TOPIC).partitionResponses();
                    return partitionResponses
                        .stream()
                        .allMatch(partitionResponse -> partitionResponse.errorCode() == Errors.INVALID_RECORD.code());
                })
            );
        }
    }

    @ExtendWith(MockitoExtension.class)
    @Nested
    class InvalidatePartitionAtFetchResponseTest {

        @Mock
        KafkaMessageExecutionContext msgCtx;

        @Mock
        KafkaExecutionContext ctx;

        @Mock
        KafkaResponse response;

        NativeErrorHandling errorHandling = createNativeErrorHandling(INVALIDATE_PARTITION);
        KafkaValidationResultHandler handler = createValidationResultHandler(errorHandling.getOnSubscribe());

        KafkaMessageStub message = new KafkaMessageStub("{\"name\":\"foo\"}");

        @BeforeEach
        void setUp() {
            lenient().when(ctx.response()).thenReturn(response);
            lenient().when(msgCtx.executionContext()).thenReturn(ctx);
            lenient().when(response.delegate()).thenReturn(createFetchResponseWithTwoPartitions());
        }

        @Test
        public void testOnSuccess() {
            handler.onSuccess(msgCtx, message).test().assertComplete();
        }

        @Test
        public void testOnError() {
            when(ctx.interruptWith(any(FetchResponse.class))).thenReturn(Completable.complete());

            handler.onError(msgCtx, message, "Validation failed").test().assertComplete();

            verify(ctx).interruptWith(
                argThat((FetchResponse actualResponse) -> {
                    var topicResponse = actualResponse
                        .data()
                        .responses()
                        .stream()
                        .filter(resp -> Objects.equals(resp.topic(), TEST_TOPIC))
                        .findFirst()
                        .orElseThrow();

                    var currentPartition = topicResponse
                        .partitions()
                        .stream()
                        .filter(partitionData -> partitionData.partitionIndex() == message.indexPartition())
                        .findFirst()
                        .orElseThrow();

                    var otherPartitions = topicResponse
                        .partitions()
                        .stream()
                        .filter(partitionData -> partitionData.partitionIndex() != message.indexPartition())
                        .collect(Collectors.toSet());

                    boolean corruptMessageErrorSet = actualResponse.error() == Errors.CORRUPT_MESSAGE;
                    boolean currentPartitionErrorSet = currentPartition.errorCode() == Errors.CORRUPT_MESSAGE.code();
                    boolean currentPartitionCleared = currentPartition.records().sizeInBytes() == 0;

                    boolean otherPartitionsRemainsUntouched = otherPartitions
                        .stream()
                        .allMatch(partition -> partition.errorCode() == Errors.NONE.code() && partition.records().sizeInBytes() > 0);

                    return corruptMessageErrorSet && currentPartitionErrorSet && currentPartitionCleared && otherPartitionsRemainsUntouched;
                })
            );
        }
    }

    @ExtendWith(MockitoExtension.class)
    @Nested
    class AddRecordHeaderTest {

        @Mock
        KafkaMessageExecutionContext msgCtx;

        @Mock
        KafkaExecutionContext ctx;

        @Mock
        KafkaResponse response;

        NativeErrorHandling errorHandling = createNativeErrorHandling(ADD_RECORD_HEADER);
        KafkaValidationResultHandler handler = createValidationResultHandler(errorHandling.getOnSubscribe());

        KafkaMessageStub message = new KafkaMessageStub("{\"name\":\"foo\"}");

        @BeforeEach
        void setUp() {
            lenient().when(ctx.response()).thenReturn(response);
            lenient().when(msgCtx.executionContext()).thenReturn(ctx);
            lenient().when(response.delegate()).thenReturn(createFetchResponseWithTwoPartitions());
        }

        @Test
        public void testOnSuccess() {
            handler.onSuccess(msgCtx, message).test().assertComplete();
        }

        @Test
        public void testOnError() {
            handler.onError(msgCtx, message, "Validation failed").test().assertComplete();

            assertThat(message.recordHeaders()).containsKey(TEST_HEADER_NAME);
            verify(ctx, never()).interruptWith(any(AbstractResponse.class));
        }
    }
}
