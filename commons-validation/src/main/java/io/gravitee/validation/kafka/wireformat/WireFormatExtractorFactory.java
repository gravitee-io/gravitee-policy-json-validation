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

/**
 * Builds the {@link WireFormatExtractor} for a configured {@link WireFormat}.
 */
public final class WireFormatExtractorFactory {

    private WireFormatExtractorFactory() {}

    public static WireFormatExtractor create(WireFormat wireFormat, String headerName) {
        WireFormat effective = wireFormat == null ? WireFormat.CONFLUENT_4B : wireFormat;
        return switch (effective) {
            case CONFLUENT_4B -> new ConfluentWireFormatExtractor();
            case APICURIO_8B -> new ApicurioLegacyWireFormatExtractor();
            case HEADER -> new HeaderWireFormatExtractor(headerName);
        };
    }
}
