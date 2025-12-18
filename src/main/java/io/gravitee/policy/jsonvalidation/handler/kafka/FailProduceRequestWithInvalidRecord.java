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

import static org.apache.kafka.common.requests.AbstractResponse.DEFAULT_THROTTLE_TIME;

import io.gravitee.gateway.reactive.api.context.kafka.KafkaMessageExecutionContext;
import io.gravitee.gateway.reactive.api.message.kafka.KafkaMessage;
import io.reactivex.rxjava3.core.Completable;
import org.apache.kafka.common.InvalidRecordException;
import org.apache.kafka.common.requests.ProduceRequest;
import org.apache.kafka.common.requests.ProduceResponse;

/**
 * @author GraviteeSource Team
 */
class FailProduceRequestWithInvalidRecord implements KafkaValidationResultHandler {

    @Override
    public Completable onError(KafkaMessageExecutionContext ctx, KafkaMessage message, String errorMessage) {
        KafkaSource failedSource = new KafkaSource(message.topic(), message.indexPartition());

        Throwable throwable = new InvalidRecordException("Partition contains invalid record(s) at offset: " + message.offset());

        ProduceRequest produceRequest = ctx.executionContext().request().delegate();
        ProduceResponse errorResponse = produceRequest.getErrorResponse(DEFAULT_THROTTLE_TIME, throwable);

        errorResponse
            .data()
            .responses()
            .forEach(topicResponse ->
                topicResponse
                    .partitionResponses()
                    .forEach(partitionResponse -> {
                        if (!failedSource.isSameTopicAndPartitionAs(topicResponse, partitionResponse)) {
                            partitionResponse.setErrorMessage("Another partition contains invalid record(s)");
                        }
                    })
            );

        return ctx.executionContext().interruptWith(errorResponse);
    }
}
