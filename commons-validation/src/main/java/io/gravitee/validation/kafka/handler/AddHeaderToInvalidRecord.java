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

import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.reactive.api.context.kafka.KafkaMessageExecutionContext;
import io.gravitee.gateway.reactive.api.message.kafka.KafkaMessage;
import io.reactivex.rxjava3.core.Completable;
import lombok.extern.slf4j.Slf4j;

/**
 * Tags an invalid record with a header so downstream consumers can detect and triage it (e.g. route to a DLQ),
 * then forwards the record.
 *
 * <p>The header carries a fixed, non-sensitive marker — configurable via {@code headerValue}, defaulting to
 * {@value #DEFAULT_HEADER_VALUE}. The detailed validation error is deliberately <strong>not</strong> written to the
 * header: Avro decode errors frequently embed fragments of the (potentially sensitive) payload, which would leak into
 * the record headers delivered to consumers. The detailed error is only logged at debug level for operators.
 */
@Slf4j
class AddHeaderToInvalidRecord implements KafkaValidationResultHandler {

    static final String DEFAULT_HEADER_VALUE = "Schema validation failed";

    private final String validationErrorHeaderName;
    private final String validationErrorHeaderValue;

    AddHeaderToInvalidRecord(String validationErrorHeaderName, String validationErrorHeaderValue) {
        this.validationErrorHeaderName = validationErrorHeaderName;
        this.validationErrorHeaderValue = (validationErrorHeaderValue == null || validationErrorHeaderValue.isBlank())
            ? DEFAULT_HEADER_VALUE
            : validationErrorHeaderValue;
    }

    @Override
    public Completable onError(KafkaMessageExecutionContext ctx, KafkaMessage message, String errorMessage) {
        // The detailed error may contain payload fragments — log it (sanitized) instead of exposing it to consumers.
        log.debug(
            "Tagging invalid record on topic [{}] partition [{}]: {}",
            message.topic(),
            message.indexPartition(),
            sanitize(errorMessage)
        );
        message.putRecordHeader(validationErrorHeaderName, Buffer.buffer(validationErrorHeaderValue));
        return Completable.complete();
    }

    private static String sanitize(String errorMessage) {
        return errorMessage == null ? "" : errorMessage.replaceAll("[\\r\\n\\t]", " ");
    }
}
