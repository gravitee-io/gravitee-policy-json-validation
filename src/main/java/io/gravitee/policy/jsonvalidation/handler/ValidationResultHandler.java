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
package io.gravitee.policy.jsonvalidation.handler;

import io.gravitee.gateway.reactive.api.context.base.BaseExecutionContext;
import io.gravitee.gateway.reactive.api.message.Message;
import io.reactivex.rxjava3.core.Completable;

/**
 * @author GraviteeSource Team
 */
public interface ValidationResultHandler<T extends BaseExecutionContext, M extends Message> {
    default Completable onSuccess(T ctx, M message) {
        return Completable.complete();
    }

    Completable onError(T ctx, M message, String errorMessage);
}
