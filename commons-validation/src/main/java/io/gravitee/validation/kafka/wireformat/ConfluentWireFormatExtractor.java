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
import java.nio.ByteBuffer;

/**
 * Confluent wire format: {@code magic(0x00) + schemaId(4 bytes, big-endian) + Avro body}.
 */
class ConfluentWireFormatExtractor implements WireFormatExtractor {

    private static final int MAGIC_PLUS_ID = 5;

    @Override
    public Maybe<EmbeddedSchemaRef> extract(KafkaMessage message) {
        return Maybe.fromCallable(() -> {
            byte[] bytes = message.content() == null ? null : message.content().getBytes();
            if (bytes == null || bytes.length < MAGIC_PLUS_ID) {
                throw new IllegalArgumentException("Message too short for Confluent framing");
            }
            if (bytes[0] != 0x00) {
                throw new IllegalArgumentException("Not a Confluent-framed message (missing 0x00 magic byte)");
            }
            int id = ByteBuffer.wrap(bytes, 1, 4).getInt();
            return new EmbeddedSchemaRef(Integer.toString(id), MAGIC_PLUS_ID);
        });
    }
}
