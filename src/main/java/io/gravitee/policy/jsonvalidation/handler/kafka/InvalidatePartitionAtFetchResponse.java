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

import io.gravitee.gateway.reactive.api.context.kafka.KafkaMessageExecutionContext;
import io.gravitee.gateway.reactive.api.message.kafka.KafkaMessage;
import io.reactivex.rxjava3.core.Completable;
import org.apache.kafka.common.protocol.Errors;
import org.apache.kafka.common.record.internal.MemoryRecords;
import org.apache.kafka.common.requests.FetchResponse;
import org.apache.kafka.common.requests.ShareFetchResponse;

/**
 * Refuses an invalid record on its way to a consumer.
 *
 * <p>On a Fetch response, the partition of the record is answered with {@code CORRUPT_MESSAGE} and no record.
 *
 * <p>On a ShareFetch response (share groups, KIP-932), the record is marked as rejected and left in the response: the
 * gateway takes it out before the response reaches the consumer and acknowledges it as {@code REJECT}, so the broker
 * moves it to the group's dead-letter queue, or archives it. Invalidating the partition instead would hand the consumer
 * acquired records without their content, which it neither processes nor acknowledges: the broker would deliver them
 * again after their lock expires, over and over.
 *
 * @author GraviteeSource Team
 */
class InvalidatePartitionAtFetchResponse implements KafkaValidationResultHandler {

    /** Internal attribute through which the gateway learns what to do with a record of a ShareFetch response. */
    static final String SHARE_DISPOSITION_ATTRIBUTE = "gravitee.kafka.share.disposition";

    /** The record is refused: the gateway acknowledges it as {@code REJECT}. */
    static final String SHARE_DISPOSITION_REJECT = "REJECT";

    @Override
    public Completable onError(KafkaMessageExecutionContext ctx, KafkaMessage message, String errorMessage) {
        if (ctx.executionContext().response().delegate() instanceof ShareFetchResponse) {
            message.internalAttribute(SHARE_DISPOSITION_ATTRIBUTE, SHARE_DISPOSITION_REJECT);
            return Completable.complete();
        }

        KafkaSource currentSource = new KafkaSource(message.topic(), message.indexPartition());

        FetchResponse fetchResponse = ctx.executionContext().response().delegate();
        fetchResponse.data().setErrorCode(Errors.CORRUPT_MESSAGE.code());

        fetchResponse
            .data()
            .responses()
            .stream()
            .filter(currentSource::isSameTopicAs)
            .findFirst()
            .flatMap(topicResponse -> topicResponse.partitions().stream().filter(currentSource::isSamePartitionAs).findFirst())
            .ifPresent(partitionData -> {
                partitionData.setErrorCode(Errors.CORRUPT_MESSAGE.code());
                partitionData.setRecords(MemoryRecords.EMPTY);
            });

        return ctx.executionContext().interruptWith(fetchResponse);
    }
}
