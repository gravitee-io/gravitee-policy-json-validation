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

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.gravitee.validation.configuration.errorhandling.PublishErrorHandling;
import io.gravitee.validation.configuration.errorhandling.PublishValidationErrorStrategy;
import io.gravitee.validation.kafka.handler.support.KafkaMessageStub;
import org.junit.jupiter.api.Test;

/**
 * Verifies the audit (LOG) publish strategy forwards the record instead of rejecting it.
 */
class LogInvalidRecordTest {

    @Test
    void factory_returns_log_handler_for_log_strategy() {
        var config = new PublishErrorHandling();
        config.setStrategy(PublishValidationErrorStrategy.LOG);

        KafkaValidationResultHandler handler = KafkaValidationResultHandlerFactory.createValidationResultHandler(config);

        assertInstanceOf(LogInvalidRecord.class, handler);
    }

    @Test
    void log_handler_passes_record_through_on_error() {
        new LogInvalidRecord().onError(null, new KafkaMessageStub("payload"), "boom").test().assertComplete().assertNoErrors();
    }
}
