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
package io.gravitee.avrovalidation.configuration;

import io.gravitee.avrovalidation.configuration.schema.SerializationForm;
import io.gravitee.policy.api.PolicyConfiguration;
import io.gravitee.validation.configuration.errorhandling.NativeErrorHandling;
import io.gravitee.validation.schema.SchemaIdSource;
import io.gravitee.validation.schema.SchemaSource;
import lombok.Getter;
import lombok.Setter;

/**
 * @author GraviteeSource Team
 */
@Getter
@Setter
public class AvroValidationPolicyConfiguration implements PolicyConfiguration {

    private SchemaSource schemaSource;

    private SerializationForm serializationForm;

    private SchemaIdSource schemaIdSource;

    private String schemaIdEvalString;

    private String schemaVersionEvalString;

    private NativeErrorHandling nativeErrorHandling;

    /**
     * When {@code true} (default), a record with {@code null} content (e.g. a compaction tombstone) is skipped
     * and forwarded without validation. When {@code false}, a {@code null} record is treated as invalid.
     */
    private boolean allowNulls = true;

    /**
     * When {@code true}, a record with zero-length content is skipped and forwarded without validation.
     * Defaults to {@code false} (an empty payload is treated as invalid).
     */
    private boolean allowEmpty = false;
}
