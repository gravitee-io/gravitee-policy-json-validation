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
package io.gravitee.policy.jsonvalidation.schema;

import com.github.fge.jackson.JsonLoader;
import io.gravitee.gateway.reactive.api.context.base.BaseExecutionContext;
import io.gravitee.gateway.reactive.api.context.http.HttpMessageExecutionContext;
import io.gravitee.gateway.reactive.api.context.kafka.KafkaMessageExecutionContext;
import io.gravitee.gateway.reactive.api.message.Message;
import io.gravitee.gateway.reactive.api.message.kafka.KafkaMessage;
import io.gravitee.resource.schema_registry.api.Schema;
import io.reactivex.rxjava3.core.Single;
import java.io.IOException;
import lombok.RequiredArgsConstructor;

/**
 * @author GraviteeSource Team
 */
@RequiredArgsConstructor
public class StaticSchemaResolver implements ValidatableSchemaResolver {

    private final StaticSchema staticSchema;

    StaticSchemaResolver(String schema) {
        this.staticSchema = new StaticSchema(schema);
    }

    @Override
    public Single<Schema> resolveSchema(BaseExecutionContext ctx) {
        return Single.just(staticSchema);
    }

    @Override
    public Single<Schema> resolveSchema(HttpMessageExecutionContext ctx, Message message) {
        return Single.just(staticSchema);
    }

    @Override
    public Single<Schema> resolveSchema(KafkaMessageExecutionContext ctx, KafkaMessage message) {
        return Single.just(staticSchema);
    }

    @Override
    public void validate() throws IOException {
        JsonLoader.fromString(staticSchema.getContent());
    }
}
