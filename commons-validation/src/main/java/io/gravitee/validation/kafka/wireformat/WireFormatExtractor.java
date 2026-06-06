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
package io.gravitee.validation.kafka.wireformat;

import io.gravitee.gateway.reactive.api.message.kafka.KafkaMessage;
import io.reactivex.rxjava3.core.Maybe;

/**
 * Extracts the embedded schema id (and the Avro payload offset) from a Kafka record, according to a given
 * {@link WireFormat}. Implementations error when the record does not carry an id in the expected framing.
 */
@FunctionalInterface
public interface WireFormatExtractor {
    Maybe<EmbeddedSchemaRef> extract(KafkaMessage message);
}
