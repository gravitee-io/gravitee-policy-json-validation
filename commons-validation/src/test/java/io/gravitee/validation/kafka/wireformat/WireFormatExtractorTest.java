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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.gravitee.validation.kafka.handler.support.KafkaMessageStub;
import org.junit.jupiter.api.Test;

class WireFormatExtractorTest {

    @Test
    void confluent_4b_reads_id_and_offset() {
        var message = new KafkaMessageStub(new byte[] { 0x00, 0x00, 0x00, 0x00, 0x1B, 0x0C, 0x4D });
        var ref = WireFormatExtractorFactory.create(WireFormat.CONFLUENT_4B).extract(message).blockingGet();
        assertEquals("27", ref.schemaId());
        assertEquals(5, ref.payloadOffset());
    }

    @Test
    void none_has_no_id_and_zero_offset() {
        var message = new KafkaMessageStub(new byte[] { 0x0C, 0x4D });
        var ref = WireFormatExtractorFactory.create(WireFormat.NONE).extract(message).blockingGet();
        assertNull(ref.schemaId());
        assertEquals(0, ref.payloadOffset());
    }

    @Test
    void confluent_rejects_message_without_magic_byte() {
        var message = new KafkaMessageStub(new byte[] { 0x0C, 0x4D, 0x61, 0x62, 0x63 });
        WireFormatExtractorFactory.create(WireFormat.CONFLUENT_4B).extract(message).test().assertError(IllegalArgumentException.class);
    }
}
