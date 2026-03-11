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
package io.gravitee.validation.kafka.handler.support;

import io.gravitee.validation.configuration.errorhandling.NativeErrorHandling;
import io.gravitee.validation.configuration.errorhandling.PublishErrorHandling;
import io.gravitee.validation.configuration.errorhandling.PublishValidationErrorStrategy;
import io.gravitee.validation.configuration.errorhandling.SubscribeErrorHandling;
import io.gravitee.validation.configuration.errorhandling.SubscribeErrorHandlingStrategy;

public class TestNativeErrorHandlingConfigurationFactory {

    public static final String testHeaderName = "Test-Validation-Error-Header";

    public static NativeErrorHandling createNativeErrorHandling(PublishValidationErrorStrategy strategy) {
        NativeErrorHandling nativeErrorHandling = new NativeErrorHandling();
        PublishErrorHandling publishErrorHandlingConfiguration = new PublishErrorHandling();
        publishErrorHandlingConfiguration.setStrategy(strategy);
        nativeErrorHandling.setOnPublish(publishErrorHandlingConfiguration);
        return nativeErrorHandling;
    }

    public static NativeErrorHandling createNativeErrorHandling(SubscribeErrorHandlingStrategy strategy) {
        NativeErrorHandling nativeErrorHandling = new NativeErrorHandling();
        SubscribeErrorHandling subscribeErrorHandling = new SubscribeErrorHandling();
        subscribeErrorHandling.setStrategy(strategy);
        subscribeErrorHandling.setHeaderName(testHeaderName);
        nativeErrorHandling.setOnSubscribe(subscribeErrorHandling);
        return nativeErrorHandling;
    }
}
