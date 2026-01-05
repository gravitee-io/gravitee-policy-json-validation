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

import io.gravitee.policy.jsonvalidation.configuration.errorhandling.PublishErrorHandling;
import io.gravitee.policy.jsonvalidation.configuration.errorhandling.SubscribeErrorHandling;

/**
 * @author GraviteeSource Team
 */
public class KafkaValidationResultHandlerFactory {

    public static KafkaValidationResultHandler createValidationResultHandler(PublishErrorHandling configuration) {
        return switch (configuration.strategy()) {
            case FAIL_WITH_INVALID_RECORD -> new FailProduceRequestWithInvalidRecord();
        };
    }

    public static KafkaValidationResultHandler createValidationResultHandler(SubscribeErrorHandling configuration) {
        return switch (configuration.strategy()) {
            case INVALIDATE_PARTITION -> new InvalidatePartitionAtFetchResponse();
            case ADD_RECORD_HEADER -> new AddHeaderToInvalidRecord(configuration.headerName());
        };
    }
}
