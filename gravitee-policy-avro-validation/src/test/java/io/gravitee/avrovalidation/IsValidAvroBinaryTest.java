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
package io.gravitee.avrovalidation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.helpers.SchemaImpl;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the pure {@link AvroValidationPolicy#isValidAvroBinary} decoder. The decoder is given an explicit
 * payload offset (the envelope having been parsed by the wire-format extractor upstream).
 */
class IsValidAvroBinaryTest {

    private static final String SCHEMA = """
        {
          "type": "record",
          "name": "User",
          "namespace": "io.gravitee.avrovalidation",
          "fields": [{ "name": "name", "type": "string" }]
        }
        """;

    // magic(1) + schemaId(4=27) + avro body for "Maciek" -> payload offset 5
    private static final byte[] CONFLUENT_OK = { 0x00, 0x00, 0x00, 0x00, 0x1B, 0x0C, 0x4D, 0x61, 0x63, 0x69, 0x65, 0x6B };

    // CONFLUENT_OK with extra trailing bytes
    private static final byte[] CONFLUENT_TRAILING = { 0x00, 0x00, 0x00, 0x00, 0x1B, 0x0C, 0x4D, 0x61, 0x63, 0x69, 0x65, 0x6B, 0x02, 0x4D };

    // Bare avro body (no envelope) for "Maciek" -> payload offset 0
    private static final byte[] SIMPLE_OK = { 0x0C, 0x4D, 0x61, 0x63, 0x69, 0x65, 0x6B };

    @Test
    void valid_at_confluent_offset() {
        assertTrue(AvroValidationPolicy.isValidAvroBinary(Buffer.buffer(CONFLUENT_OK), schema(), 5).isSuccess());
    }

    @Test
    void trailing_bytes_rejected() {
        assertFalse(AvroValidationPolicy.isValidAvroBinary(Buffer.buffer(CONFLUENT_TRAILING), schema(), 5).isSuccess());
    }

    @Test
    void valid_at_zero_offset_for_bare_payload() {
        assertTrue(AvroValidationPolicy.isValidAvroBinary(Buffer.buffer(SIMPLE_OK), schema(), 0).isSuccess());
    }

    @Test
    void wrong_offset_rejected() {
        // Decoding the bare body as if it had a 5-byte envelope mis-reads it.
        assertFalse(AvroValidationPolicy.isValidAvroBinary(Buffer.buffer(SIMPLE_OK), schema(), 5).isSuccess());
    }

    @Test
    void offset_past_end_rejected() {
        assertFalse(AvroValidationPolicy.isValidAvroBinary(Buffer.buffer(SIMPLE_OK), schema(), 100).isSuccess());
    }

    private static SchemaImpl schema() {
        return new SchemaImpl(SCHEMA);
    }
}
