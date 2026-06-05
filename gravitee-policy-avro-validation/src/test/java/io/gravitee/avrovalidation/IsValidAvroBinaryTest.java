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

import io.gravitee.avrovalidation.configuration.schema.SerializationForm;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.helpers.SchemaImpl;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the pure {@link AvroValidationPolicy#isValidAvroBinary} decoder, covering both serialization forms.
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

    // magic(1) + schemaId(4=27) + avro body for "Maciek"
    private static final byte[] CONFLUENT_OK = { 0x00, 0x00, 0x00, 0x00, 0x1B, 0x0C, 0x4D, 0x61, 0x63, 0x69, 0x65, 0x6B };

    // CONFLUENT_OK with extra trailing bytes
    private static final byte[] CONFLUENT_TRAILING = { 0x00, 0x00, 0x00, 0x00, 0x1B, 0x0C, 0x4D, 0x61, 0x63, 0x69, 0x65, 0x6B, 0x02, 0x4D };

    // Bare avro body (no Confluent envelope) for "Maciek"
    private static final byte[] SIMPLE_OK = { 0x0C, 0x4D, 0x61, 0x63, 0x69, 0x65, 0x6B };

    @Test
    void confluent_valid() {
        assertTrue(AvroValidationPolicy.isValidAvroBinary(Buffer.buffer(CONFLUENT_OK), schema(), SerializationForm.CONFLUENT).isSuccess());
    }

    @Test
    void confluent_trailing_bytes_rejected() {
        assertFalse(
            AvroValidationPolicy.isValidAvroBinary(Buffer.buffer(CONFLUENT_TRAILING), schema(), SerializationForm.CONFLUENT).isSuccess()
        );
    }

    @Test
    void simple_form_valid_without_envelope() {
        assertTrue(AvroValidationPolicy.isValidAvroBinary(Buffer.buffer(SIMPLE_OK), schema(), SerializationForm.SIMPLE).isSuccess());
    }

    @Test
    void simple_payload_rejected_when_read_as_confluent() {
        // First byte is 0x0C (not the 0x00 magic byte) -> rejected in Confluent mode.
        assertFalse(AvroValidationPolicy.isValidAvroBinary(Buffer.buffer(SIMPLE_OK), schema(), SerializationForm.CONFLUENT).isSuccess());
    }

    @Test
    void backward_compatible_overload_defaults_to_confluent() {
        assertTrue(AvroValidationPolicy.isValidAvroBinary(Buffer.buffer(CONFLUENT_OK), schema()).isSuccess());
    }

    private static SchemaImpl schema() {
        return new SchemaImpl(SCHEMA);
    }
}
