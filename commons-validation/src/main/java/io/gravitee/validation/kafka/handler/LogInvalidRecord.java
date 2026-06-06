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
package io.gravitee.validation.kafka.handler;

import io.gravitee.gateway.reactive.api.context.kafka.KafkaMessageExecutionContext;
import io.gravitee.gateway.reactive.api.message.kafka.KafkaMessage;
import io.reactivex.rxjava3.core.Completable;
import lombok.extern.slf4j.Slf4j;

/**
 * Audit handler: logs the validation failure and forwards the record unchanged.
 *
 * <p>Lets operators observe schema violations (e.g. before switching to hard rejection) without
 * impacting traffic. The broker still receives the record.
 */
@Slf4j
class LogInvalidRecord implements KafkaValidationResultHandler {

    @Override
    public Completable onError(KafkaMessageExecutionContext ctx, KafkaMessage message, String errorMessage) {
        log.warn(
            "Schema validation failed for record on topic [{}] partition [{}] offset [{}]: {} (forwarded — audit mode)",
            message.topic(),
            message.indexPartition(),
            message.offset(),
            sanitize(errorMessage)
        );
        return Completable.complete();
    }

    /**
     * Strips control characters from the (possibly payload-derived) validation error before logging, to avoid
     * log injection / forging.
     */
    private static String sanitize(String errorMessage) {
        return errorMessage == null ? "" : errorMessage.replaceAll("[\\r\\n\\t]", " ");
    }
}
