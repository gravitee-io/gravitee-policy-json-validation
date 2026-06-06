
<!-- GENERATED CODE - DO NOT ALTER THIS OR THE FOLLOWING LINES -->
# JSON Validation

[![Gravitee.io](https://img.shields.io/static/v1?label=Available%20at&message=Gravitee.io&color=1EC9D2)](https://download.gravitee.io/#graviteeio-apim/plugins/policies/gravitee-policy-json-validation/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/gravitee-io/gravitee-policy-json-validation/blob/master/LICENSE.txt)
[![Releases](https://img.shields.io/badge/semantic--release-conventional%20commits-e10079?logo=semantic-release)](https://github.com/gravitee-io/gravitee-policy-json-validation/releases)
[![CircleCI](https://circleci.com/gh/gravitee-io/gravitee-policy-json-validation.svg?style=svg)](https://circleci.com/gh/gravitee-io/gravitee-policy-json-validation)

## Overview
You can use the `json-validation` policy to validate JSON payloads. This policy
uses [JSON Schema Validator](https://github.com/java-json-tools/json-schema-validator).

For HTTP protocols: it returns 400 BAD REQUEST when request validation fails and 500 INTERNAL ERROR when response
validation fails, with a custom error message body.

For native protocols (Kafka Gateway): it executes configured strategy (rejects produce request, invalidates partition or
appends record header).

It supports multiple sources of the schema used for validation (static or schema registry resource based with dynamic
schema subject mapping).

It can inject processing report messages into request metrics for analytics.

## Phase

### V3 engine

| onRequestContent | onResponseContent |
|------------------|-------------------|
| X                | X                 |

### V4 engine

| onRequest | onResponse | onMessageRequest | onMessageResponse |
|-----------|------------|------------------|-------------------|
| X         | X          | X                | X                 |

### Native API - Kafka Gateway

| onRequest | onResponse | PUBLISH (onMessageRequest) | SUBSCRIBE (onMessageResponse) |
|-----------|------------|----------------------------|-------------------------------|
|           |            | X                          | X                             |


## Usage
## Configuration

| Property            | Required           | Description                                                                                                                                                                                                                                             | Type                         | Default                 |
|---------------------|--------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------|-------------------------|
| scope               | X                  | Policy scope from where the policy is executed                                                                                                                                                                                                          | Policy scope                 | REQUEST_CONTENT         |
| errorMessage        | X                  | Custom error message in JSON format. SpEL is allowed.                                                                                                                                                                                                   | string                       | {"error":"Bad request"} |
| schema (deprecated) |                    | Deprecated configuration for JSON schema, use schema source instead.                                                                                                                                                                                    | string                       |                         |
| schemaSource        | X                  | Defines the schema source to resolve the validation schema.                                                                                                                                                                                             | Schema Source object         |                         |
| deepCheck           |                    | Validate descendant even if JSON parent container is invalid                                                                                                                                                                                            | boolean                      | false                   |
| validateUnchecked   |                    | Unchecked validation means that conditions which would normally cause the processing to stop with an exception are instead inserted into the resulting report. Warning: anomalous events (e.g. invalid schema or unresolved JSON Reference) are masked. | boolean                      | false                   |
| straightRespondMode |                    | Only for RESPONSE scope. Straight respond mode means that responses failed to validate are still sent to the user without replacement. Validation failure messages are written to metrics for inspection.                                               | boolean                      | false                   |
| nativeErrorHandling | X (for Native API) | Defines error handling strategy for consumer/producer if policy is used in native API (Kafka Gateway protocol).                                                                                                                                         | Native Error Handling object |                         |

### Schema Source

Specifies the source used to resolve schemas for validation. You can choose between:

- **Static schema** – provide the schema definition directly.
- **Resource-based schema registry** – provide the resource name and a mapping expression to resolve the schema subject dynamically (e.g., using `{#message.topic}` to derive the subject from the topic name).

### Native Error Handling

The `nativeErrorHandling` option is available **only for Native API (Kafka Gateway protocol)** and defines the
validation error handling strategy for `SUBSCRIBE` and `PUBLISH` phases.

#### onSubscribe (SUBSCRIBE phase)

| Strategy             | Description                                                                      |
|----------------------|----------------------------------------------------------------------------------|
| INVALIDATE_PARTITION | The entire partition is marked as invalid when a record validation error occurs. |
| ADD_RECORD_HEADER    | A record header containing validation error information is added to the record.  |

#### onPublish (PUBLISH phase)

| Strategy                 | Description                                                                                    |
|--------------------------|------------------------------------------------------------------------------------------------|
| FAIL_WITH_INVALID_RECORD | Entire produce request is failed (all partitions) and no messages are delivered to the broker. |



## Errors
These templates are defined at the API level, in the "Entrypoint" section for v4 APIs, or in "Response Templates" for v2 APIs.
The error keys sent by this policy are as follows:

| Key |
| ---  |
| JSON_INVALID_PAYLOAD |
| JSON_INVALID_FORMAT |
| JSON_INVALID_RESPONSE_PAYLOAD |
| JSON_INVALID_RESPONSE_FORMAT |



## Phases
The `json-validation` policy can be applied to the following API types and flow phases.

### Compatible API types

* `PROXY`
* `MESSAGE`
* `NATIVE KAFKA`

### Supported flow phases:

* Publish
* Subscribe
* Request
* Response

## Compatibility matrix
Strikethrough text indicates that a version is deprecated.

| Plugin version| APIM |
| --- | ---  |
|1.x|4.5 and lower |
|2.x|4.6 and greater |


## Configuration options


#### 
| Name <br>`json name`  | Type <br>`constraint`  | Mandatory  | Default  | Description  |
|:----------------------|:-----------------------|:----------:|:---------|:-------------|
| Deep check<br>`deepCheck`| boolean|  | | Instructs the validator as to whether it should validate children even if the container (array or object) fails to validate.|
| Http error message<br>`errorMessage`| string|  | | Http error message to send when request is not valid. Status code is 400 as Bad request for REQUEST scope. Status code is 500 as Internal Error for RESPONSE scope (without straight respond mode). e.g: {"error":"Bad request"}|
| Error handling strategy<br>`nativeErrorHandling`| object|  | | <br/>See "Error handling strategy" section.|
| SchemaSource<br>`schemaSource`| object|  | | Schema source<br/>See "SchemaSource" section.|
| Straight respond mode<br>`straightRespondMode`| boolean|  | | Only for RESPONSE scope. Straight respond mode means that responses failed to validate still will be sent to user without replacement. Validation failures messages are still being written to the metrics for further inspection.|
| Validate unchecked<br>`validateUnchecked`| boolean|  | | Unchecked validation means that conditions which would normally cause the processing to stop with an exception are instead inserted into the resulting report. Warning: this means that anomalous events like an unresolvable JSON Reference, or an invalid schema, are masked!|


#### Error handling strategy (Object)
| Name <br>`json name`  | Type <br>`constraint`  | Mandatory  | Description  |
|:----------------------|:-----------------------|:----------:|:-------------|
| PublishErrorHandling<br>`onPublish`| object|  | Error handling for publish phase<br/>See "PublishErrorHandling" section.|
| SubscribeErrorHandling<br>`onSubscribe`| object|  | Error handling for subscribe phase<br/>See "SubscribeErrorHandling" section.|


#### PublishErrorHandling (Object)
| Name <br>`json name`  | Type <br>`constraint`  | Mandatory  | Description  |
|:----------------------|:-----------------------|:----------:|:-------------|
| Strategy<br>`strategy`| object| ✅| Strategy of PublishErrorHandling<br>Values: `FAIL_WITH_INVALID_RECORD`|


#### PublishErrorHandling: Fail with invalid record `strategy = "FAIL_WITH_INVALID_RECORD"` 
| Name <br>`json name`  | Type <br>`constraint`  | Mandatory  | Default  | Description  |
|:----------------------|:-----------------------|:----------:|:---------|:-------------|
| No properties | | | | | | | 

#### SubscribeErrorHandling (Object)
| Name <br>`json name`  | Type <br>`constraint`  | Mandatory  | Description  |
|:----------------------|:-----------------------|:----------:|:-------------|
| Strategy<br>`strategy`| object| ✅| Strategy of SubscribeErrorHandling<br>Values: `INVALIDATE_PARTITION` `ADD_RECORD_HEADER`|


#### SubscribeErrorHandling: Invalidate partition `strategy = "INVALIDATE_PARTITION"` 
| Name <br>`json name`  | Type <br>`constraint`  | Mandatory  | Default  | Description  |
|:----------------------|:-----------------------|:----------:|:---------|:-------------|
| No properties | | | | | | | 

#### SubscribeErrorHandling: Add record header `strategy = "ADD_RECORD_HEADER"` 
| Name <br>`json name`  | Type <br>`constraint`  | Mandatory  | Default  | Description  |
|:----------------------|:-----------------------|:----------:|:---------|:-------------|
| Record header name<br>`headerName`| string| ✅| | Record header name to append validation error|


#### SchemaSource (Object)
| Name <br>`json name`  | Type <br>`constraint`  | Mandatory  | Description  |
|:----------------------|:-----------------------|:----------:|:-------------|
| Source Type<br>`sourceType`| object| ✅| Source Type of SchemaSource<br>Values: `STATIC_SCHEMA` `SCHEMA_REGISTRY_RESOURCE`|


#### SchemaSource: Static schema `sourceType = "STATIC_SCHEMA"` 
| Name <br>`json name`  | Type <br>`constraint`  | Mandatory  | Default  | Description  |
|:----------------------|:-----------------------|:----------:|:---------|:-------------|
| JSON Schema<br>`staticSchema`| string| ✅| | JSON Schema used for request payload validation|


#### SchemaSource: Schema registry `sourceType = "SCHEMA_REGISTRY_RESOURCE"` 
| Name <br>`json name`  | Type <br>`constraint`  | Mandatory  | Default  | Description  |
|:----------------------|:-----------------------|:----------:|:---------|:-------------|
| Resource name<br>`resourceName`| string| ✅| | Name of the schema registry resource|
| Schema mapping<br>`schemaMapping`| string| ✅| | EL that evaluates to schema subject|




## Examples

*Proxy API on Request phase*
```json
{
  "api": {
    "definitionVersion": "V4",
    "type": "PROXY",
    "name": "JSON Validation example API",
    "flows": [
      {
        "name": "Common Flow",
        "enabled": true,
        "selectors": [
          {
            "type": "HTTP",
            "path": "/",
            "pathOperator": "STARTS_WITH"
          }
        ],
        "request": [
          {
            "name": "JSON Validation",
            "enabled": true,
            "policy": "json-validation",
            "configuration":
              {
                  "schemaSource": {
                      "sourceType": "STATIC_SCHEMA",
                      "staticSchema": "{\"title\": \"Person\", \"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\"}}, \"required\": [\"name\"]}"
                  },
                  "errorMessage": "Payload does not match configured schema",
                  "validateUnchecked": false,
                  "deepCheck": false
              }
          }
        ]
      }
    ]
  }
}

```
*Proxy API on Response phase*
```json
{
  "api": {
    "definitionVersion": "V4",
    "type": "PROXY",
    "name": "JSON Validation example API",
    "flows": [
      {
        "name": "Common Flow",
        "enabled": true,
        "selectors": [
          {
            "type": "HTTP",
            "path": "/",
            "pathOperator": "STARTS_WITH"
          }
        ],
        "response": [
          {
            "name": "JSON Validation",
            "enabled": true,
            "policy": "json-validation",
            "configuration":
              {
                  "schemaSource": {
                      "sourceType": "STATIC_SCHEMA",
                      "staticSchema": "{\"title\": \"Person\", \"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\"}}, \"required\": [\"name\"]}"
                  },
                  "errorMessage": "Payload does not match configured schema",
                  "validateUnchecked": false,
                  "deepCheck": false
              }
          }
        ]
      }
    ]
  }
}

```
*Message API on Request phase*
```yaml
apiVersion: "gravitee.io/v1alpha1"
kind: "ApiV4Definition"
metadata:
    name: "json-validation-message-api-crd"
spec:
    name: "JSON Validation example"
    type: "MESSAGE"
    flows:
      - name: "Common Flow"
        enabled: true
        selectors:
            matchRequired: false
            mode: "DEFAULT"
        message_request:
          - name: "JSON Validation"
            enabled: true
            policy: "json-validation"
            configuration:
              deepCheck: false
              errorMessage: Payload does not match configured schema
              schemaSource:
                  sourceType: STATIC_SCHEMA
                  staticSchema: '{"title": "Person", "type": "object", "properties": {"name": {"type": "string"}}, "required": ["name"]}'
              validateUnchecked: false

```
*Message API on Response phase*
```yaml
apiVersion: "gravitee.io/v1alpha1"
kind: "ApiV4Definition"
metadata:
    name: "json-validation-message-api-crd"
spec:
    name: "JSON Validation example"
    type: "MESSAGE"
    flows:
      - name: "Common Flow"
        enabled: true
        selectors:
            matchRequired: false
            mode: "DEFAULT"
        message_response:
          - name: "JSON Validation"
            enabled: true
            policy: "json-validation"
            configuration:
              deepCheck: false
              errorMessage: Payload does not match configured schema
              schemaSource:
                  sourceType: STATIC_SCHEMA
                  staticSchema: '{"title": "Person", "type": "object", "properties": {"name": {"type": "string"}}, "required": ["name"]}'
              validateUnchecked: false

```
*Kafka subscribe with INVALIDATE_PARTITION strategy*
```yaml
apiVersion: "gravitee.io/v1alpha1"
kind: "ApiV4Definition"
metadata:
    name: "json-validation-kafka-native-api-crd"
spec:
    name: "JSON Validation example"
    type: "NATIVE"
    flows:
      - name: "Common Flow"
        enabled: true
        selectors:
            matchRequired: false
            mode: "DEFAULT"
        subscribe:
          - name: "JSON Validation"
            enabled: true
            policy: "json-validation"
            configuration:
              nativeErrorHandling:
                  onSubscribe:
                      strategy: INVALIDATE_PARTITION
              schemaSource:
                  resourceName: externalSchemaRegistry
                  schemaMapping: '{#message.topic}'
                  sourceType: SCHEMA_REGISTRY_RESOURCE

```
*Kafka subscribe with ADD_RECORD_HEADER strategy*
```yaml
apiVersion: "gravitee.io/v1alpha1"
kind: "ApiV4Definition"
metadata:
    name: "json-validation-kafka-native-api-crd"
spec:
    name: "JSON Validation example"
    type: "NATIVE"
    flows:
      - name: "Common Flow"
        enabled: true
        selectors:
            matchRequired: false
            mode: "DEFAULT"
        subscribe:
          - name: "JSON Validation"
            enabled: true
            policy: "json-validation"
            configuration:
              nativeErrorHandling:
                  onSubscribe:
                      headerName: ValidationError
                      strategy: ADD_RECORD_HEADER
              schemaSource:
                  resourceName: externalSchemaRegistry
                  schemaMapping: '{#message.topic}'
                  sourceType: SCHEMA_REGISTRY_RESOURCE

```
*Kafka publish with FAIL_WITH_INVALID_RECORD strategy*
```yaml
apiVersion: "gravitee.io/v1alpha1"
kind: "ApiV4Definition"
metadata:
    name: "json-validation-kafka-native-api-crd"
spec:
    name: "JSON Validation example"
    type: "NATIVE"
    flows:
      - name: "Common Flow"
        enabled: true
        selectors:
            matchRequired: false
            mode: "DEFAULT"
        publish:
          - name: "JSON Validation"
            enabled: true
            policy: "json-validation"
            configuration:
              nativeErrorHandling:
                  onPublish:
                      strategy: FAIL_WITH_INVALID_RECORD
              schemaSource:
                  resourceName: externalSchemaRegistry
                  schemaMapping: '{#message.topic}'
                  sourceType: SCHEMA_REGISTRY_RESOURCE

```


## Changelog

### [2.2.0-alpha.1](https://github.com/gravitee-io/gravitee-policy-json-validation/compare/2.1.0...2.2.0-alpha.1) (2026-01-26)


##### Features

* added support for kafka gateway protocol ([e77fd4b](https://github.com/gravitee-io/gravitee-policy-json-validation/commit/e77fd4bf756011596352c1bc148e9b7bd68f385d))
* integration with schema registry resources ([c6ad54d](https://github.com/gravitee-io/gravitee-policy-json-validation/commit/c6ad54d92e80653d214005c94cbfc92fd18ab6f5))

### [2.1.0](https://github.com/gravitee-io/gravitee-policy-json-validation/compare/2.0.3...2.1.0) (2025-11-13)


##### Features

* support JSON Schema v3.1 serialization in JsonValidationOAIOperationVisitor ([1472a48](https://github.com/gravitee-io/gravitee-policy-json-validation/commit/1472a48f67ae27d5a9515742a3286e2600f04b28))

#### [2.0.3](https://github.com/gravitee-io/gravitee-policy-json-validation/compare/2.0.2...2.0.3) (2025-03-13)


##### Bug Fixes

* JSON validation policy message not published ([0a3b3f7](https://github.com/gravitee-io/gravitee-policy-json-validation/commit/0a3b3f7125ce5a9e748217d997a81b84ab1f61d1))

#### [2.0.2](https://github.com/gravitee-io/gravitee-policy-json-validation/compare/2.0.1...2.0.2) (2025-01-17)


##### Bug Fixes

* naming ([7c390b0](https://github.com/gravitee-io/gravitee-policy-json-validation/commit/7c390b0173d2144dc3bdc108cb520cedae8cd1a2))

#### [2.0.1](https://github.com/gravitee-io/gravitee-policy-json-validation/compare/2.0.0...2.0.1) (2025-01-17)


##### Bug Fixes

* change the error code ([44bbf67](https://github.com/gravitee-io/gravitee-policy-json-validation/commit/44bbf67c89584c33f2a9e2a930a0ccf8112eb3a7))

### [2.0.0](https://github.com/gravitee-io/gravitee-policy-json-validation/compare/1.7.1...2.0.0) (2025-01-07)


##### chore

* **deps:** bump gravitee-parent to 22 ([3301141](https://github.com/gravitee-io/gravitee-policy-json-validation/commit/33011415b2cf7b2f7430451a853a8a177b45653c))


##### Features

* **async:** allow use policy in async API ([df608a9](https://github.com/gravitee-io/gravitee-policy-json-validation/commit/df608a9b7eaf323b99b514fff8509bdc0ee48dfb))


##### BREAKING CHANGES

* **async:** now compatible with APIM 4.6 or greater

APIM-7216
* **deps:** now use JDK 17 as source and target compilation

#### [1.7.1](https://github.com/gravitee-io/gravitee-policy-json-validation/compare/1.7.0...1.7.1) (2024-06-17)


##### Bug Fixes

* improve json-schema with V4 PolicyStudio ([310021d](https://github.com/gravitee-io/gravitee-policy-json-validation/commit/310021d2277d5937611de0633496f4a6b49294ae))

### [1.7.0](https://github.com/gravitee-io/gravitee-policy-json-validation/compare/1.6.2...1.7.0) (2023-12-19)


##### Features

* enable policy on REQUEST phase for message APIs ([69bda3f](https://github.com/gravitee-io/gravitee-policy-json-validation/commit/69bda3fb7787f160fa44774f8884eba57dbae8cd)), closes [gravitee-io/issues#9430](https://github.com/gravitee-io/issues/issues/9430)

#### [1.6.2](https://github.com/gravitee-io/gravitee-policy-json-validation/compare/1.6.1...1.6.2) (2023-07-20)


##### Bug Fixes

* update policy description ([c868322](https://github.com/gravitee-io/gravitee-policy-json-validation/commit/c86832205e2f2ee08ac1d91ea799aa57b3f92a7d))

#### [1.6.1](https://github.com/gravitee-io/gravitee-policy-json-validation/compare/1.6.0...1.6.1) (2022-03-28)


##### Bug Fixes

* stop propagating request to backend if not valid ([877f812](https://github.com/gravitee-io/gravitee-policy-json-validation/commit/877f812294f72ac87c8cc9b4c5ad76f87d0b86bf))

