/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.policy.jsonvalidation;

import io.gravitee.policy.api.PolicyConfiguration;
import io.gravitee.common.http.HttpMethod;

@SuppressWarnings("unused")
public class JsonValidationPolicyConfiguration implements PolicyConfiguration {

    private String errorMessage;

    private String schema;

    private boolean validateUnchecked;

    private boolean deepCheck;

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public boolean isValidateUnchecked() {
        return validateUnchecked;
    }

    public void setValidateUnchecked(boolean validateUnchecked) {
        this.validateUnchecked = validateUnchecked;
    }

    public boolean isDeepCheck() {
        return deepCheck;
    }

    public void setDeepCheck(boolean deepCheck) {
        this.deepCheck = deepCheck;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
