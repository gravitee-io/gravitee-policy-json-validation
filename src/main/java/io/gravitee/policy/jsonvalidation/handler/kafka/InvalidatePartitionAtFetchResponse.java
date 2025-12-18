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
import org.apache.kafka.common.record.MemoryRecords;
import org.apache.kafka.common.requests.FetchResponse;

/**
 * @author GraviteeSource Team
 */
class InvalidatePartitionAtFetchResponse implements KafkaValidationResultHandler {

    @Override
    public Completable onError(KafkaMessageExecutionContext ctx, KafkaMessage message, String errorMessage) {
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
