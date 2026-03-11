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
package io.gravitee.policy.v3.jsonvalidation;

import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.stream.BufferedReadWriteStream;
import io.gravitee.gateway.api.stream.SimpleReadWriteStream;
import java.util.function.BiConsumer;

class PassThroughBuffer extends BufferedReadWriteStream {

    private final Buffer buffer = Buffer.buffer();
    private final BiConsumer<Buffer, Runnable> consumer;

    public PassThroughBuffer(BiConsumer<Buffer, Runnable> consumer) {
        this.consumer = consumer;
    }

    @Override
    public SimpleReadWriteStream<Buffer> write(Buffer content) {
        buffer.appendBuffer(content);
        return this;
    }

    @Override
    public void end() {
        consumer.accept(buffer, this::writeBufferAndEnd);
    }

    private void writeBufferAndEnd() {
        if (buffer.length() > 0) {
            super.write(buffer);
        }
        super.end();
    }
}
