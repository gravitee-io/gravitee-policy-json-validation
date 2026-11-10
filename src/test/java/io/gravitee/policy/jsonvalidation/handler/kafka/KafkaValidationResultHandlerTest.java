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

import io.gravitee.gateway.reactive.api.context.kafka.KafkaConnectionContext;
import io.gravitee.gateway.reactive.api.context.kafka.KafkaExecutionContext;
import io.gravitee.gateway.reactive.api.context.kafka.KafkaMessageExecutionContext;
import io.gravitee.gateway.reactive.api.context.kafka.KafkaRequest;
import io.gravitee.gateway.reactive.api.context.kafka.KafkaResponse;
import io.gravitee.gateway.reactive.api.context.kafka.topicidentity.TopicIdentity;
import io.gravitee.gateway.reactive.api.context.kafka.topicidentity.TopicIdentityRegistry;
import io.gravitee.policy.jsonvalidation.configuration.errorhandling.NativeErrorHandling;
import io.gravitee.policy.jsonvalidation.kafka.stub.KafkaMessageStub;
import io.reactivex.rxjava3.core.Completable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.kafka.common.InvalidRecordException;
import org.apache.kafka.common.Uuid;
import org.apache.kafka.common.message.ProduceResponseData;
import org.apache.kafka.common.protocol.Errors;
import org.apache.kafka.common.requests.AbstractResponse;
import org.apache.kafka.common.requests.FetchResponse;
import org.apache.kafka.common.requests.ProduceRequest;
import org.apache.kafka.common.requests.ProduceResponse;
import org.apache.kafka.common.requests.ShareFetchResponse;
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
        KafkaValidationResultHandler handler = createValidationResultHandler(errorHandling.onPublish());

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
                    var partitionResponses = actualResponse.data().responses().find(TEST_TOPIC, Uuid.ZERO_UUID).partitionResponses();
                    return partitionResponses
                        .stream()
                        .allMatch(partitionResponse -> partitionResponse.errorCode() == Errors.INVALID_RECORD.code());
                })
            );
        }

        @Test
        public void testOnErrorWithAcksZero() {
            // acks=0: Kafka builds no error response, as the producer expects none.
            when(produceRequest.getErrorResponse(anyInt(), any(InvalidRecordException.class))).thenReturn(null);
            when(ctx.interruptWith(Errors.INVALID_RECORD)).thenReturn(Completable.complete());

            handler.onError(msgCtx, message, "Validation failed").test().assertComplete();

            verify(ctx).interruptWith(Errors.INVALID_RECORD);
            verify(ctx, never()).interruptWith(any(AbstractResponse.class));
        }

        @Test
        public void testOnErrorNamesTheInvalidPartitionOfATopicGivenById(
            @Mock KafkaConnectionContext connectionContext,
            @Mock TopicIdentityRegistry registry
        ) {
            // From PRODUCE v13 the response names its topics by id only.
            Uuid topicId = Uuid.randomUuid();
            ProduceResponse failedProduceResponse = createFailedProduceResponseWithTwoPartitions(Errors.INVALID_RECORD);
            failedProduceResponse
                .data()
                .responses()
                .forEach(topicResponse -> topicResponse.setName("").setTopicId(topicId));
            lenient().when(produceRequest.getErrorResponse(anyInt(), any(InvalidRecordException.class))).thenReturn(failedProduceResponse);
            when(ctx.connectionContext()).thenReturn(connectionContext);
            when(connectionContext.topicIdentityRegistry()).thenReturn(registry);
            when(registry.findById(topicId)).thenReturn(Optional.of(new TopicIdentity(topicId, TEST_TOPIC, TopicIdentity.Source.BROKER)));
            when(ctx.interruptWith(any(ProduceResponse.class))).thenReturn(Completable.complete());

            handler.onError(msgCtx, message, "Validation failed").test().assertComplete();

            var partitions = failedProduceResponse.data().responses().iterator().next().partitionResponses();
            assertThat(partitions)
                .extracting(ProduceResponseData.PartitionProduceResponse::errorMessage)
                .containsExactly(null, "Another partition contains invalid record(s)");
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
        KafkaValidationResultHandler handler = createValidationResultHandler(errorHandling.onSubscribe());

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

        @Test
        public void testOnErrorOnShareFetchResponse() {
            when(response.delegate()).thenReturn(ShareFetchResponse.of(Errors.NONE, 0, new LinkedHashMap<>(), List.of(), 30_000));

            handler.onError(msgCtx, message, "Validation failed").test().assertComplete();

            // A share consumer would neither process nor acknowledge the acquired records of an invalidated partition:
            // the record is marked for the gateway to acknowledge it as REJECT instead
            verify(ctx, never()).interruptWith(any(AbstractResponse.class));
            assertThat(message.<String>internalAttribute("gravitee.kafka.share.disposition")).isEqualTo("REJECT");
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
        KafkaValidationResultHandler handler = createValidationResultHandler(errorHandling.onSubscribe());

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
