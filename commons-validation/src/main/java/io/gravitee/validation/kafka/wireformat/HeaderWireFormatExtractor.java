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

import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.reactive.api.message.kafka.KafkaMessage;
import io.reactivex.rxjava3.core.Maybe;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Schema id carried in a Kafka record header (e.g. Apicurio's header strategy). The body is the bare Avro payload
 * (offset 0). The header value is interpreted as an 8-byte long, a 4-byte int, or a UTF-8 string, by length.
 */
class HeaderWireFormatExtractor implements WireFormatExtractor {

    private final String headerName;

    HeaderWireFormatExtractor(String headerName) {
        if (headerName == null || headerName.isBlank()) {
            throw new IllegalArgumentException("A schema-id header name is required for the HEADER wire format");
        }
        this.headerName = headerName;
    }

    @Override
    public Maybe<EmbeddedSchemaRef> extract(KafkaMessage message) {
        return Maybe.fromCallable(() -> {
            Buffer header = message.recordHeaders() == null ? null : message.recordHeaders().get(headerName);
            if (header == null) {
                throw new IllegalArgumentException("Missing schema id header: " + headerName);
            }
            byte[] bytes = header.getBytes();
            String id = switch (bytes.length) {
                case 8 -> Long.toString(ByteBuffer.wrap(bytes).getLong());
                case 4 -> Integer.toString(ByteBuffer.wrap(bytes).getInt());
                default -> new String(bytes, StandardCharsets.UTF_8);
            };
            return new EmbeddedSchemaRef(id, 0);
        });
    }
}
