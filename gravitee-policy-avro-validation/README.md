
<!-- GENERATED CODE - DO NOT ALTER THIS OR THE FOLLOWING LINES -->
# AVRO Validation

[![Gravitee.io](https://img.shields.io/static/v1?label=Available%20at&message=Gravitee.io&color=1EC9D2)](https://download.gravitee.io/#graviteeio-apim/plugins/policies/gravitee-policy-avro-validation/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/gravitee-io/gravitee-policy-avro-validation/blob/master/LICENSE.txt)
[![Releases](https://img.shields.io/badge/semantic--release-conventional%20commits-e10079?logo=semantic-release)](https://github.com/gravitee-io/gravitee-policy-avro-validation/releases)
[![CircleCI](https://circleci.com/gh/gravitee-io/gravitee-policy-avro-validation.svg?style=svg)](https://circleci.com/gh/gravitee-io/gravitee-policy-avro-validation)

## Overview
You can use the `avro-validation` policy to validate AVRO payloads from Kafka endpoint. 

It supports schema registry, getting schema ID from confluent native mechanism, or schema name and version from SpEL.

## Phase

### Native API - Kafka Gateway

| onRequest | onResponse | PUBLISH (onMessageRequest) | SUBSCRIBE (onMessageResponse) |
|-----------|------------|----------------------------|-------------------------------|
|           |            | X                          | X                             |


## Usage
## Configuration

| Property                | Required                       | Description                                                                                                     | Type                         | Default         |
|-------------------------|--------------------------------|---------------------------------------------------------------------------------------------------------------|------------------------------|-----------------|
| schemaSource            | X                              | Defines the schema source (schema registry resource) used to resolve the validation schema.                   | Schema Source object         |                 |
| schemaIdSource          | X                              | How the schema to validate against is located: `FROM_RECORD` or `EXPRESSION`.                                  | enum                         | FROM_RECORD     |
| wireFormat              | X                              | How the schema id / Avro payload is framed in the record: `CONFLUENT_4B`, `APICURIO_8B`, `HEADER`, `NONE`.     | enum                         | CONFLUENT_4B    |
| schemaIdHeader          | X (for `HEADER` wire format)   | Record header carrying the schema id.                                                                          | string                       |                 |
| schemaIdEvalString      | X (for `EXPRESSION` source)    | EL that evaluates to the schema subject (e.g. `{#message.topic}-value`).                                       | string                       |                 |
| schemaVersionEvalString | X (for `EXPRESSION` source)    | EL that evaluates to the schema version.                                                                       | string                       |                 |
| validationDepth         |                                | `CONTENT` decodes the payload; `SCHEMA_ONLY` only checks the id resolves to the topic subject (no decode).     | enum                         | CONTENT         |
| nativeErrorHandling     | X                              | Error handling strategy for the consumer/producer when used on a Native API (Kafka Gateway protocol).         | Native Error Handling object |                 |

### Schema source strategy

How the schema to validate against is located:

- **`FROM_RECORD`** – use the schema id carried in the record (in the wire-format envelope or a record header) and enforce that it resolves to the schema registered under the topic's subject (`<topic>-value`) before accepting the record. The subject is the authority — a producer cannot validate against an arbitrary registered schema. The payload is then decoded with the producer's (validated) writer schema.
- **`EXPRESSION`** – resolve the schema by an Expression Language mapping that evaluates to a subject and version (e.g. `{#message.topic}-value`), ignoring the producer's id.

### Wire format

How the schema id / Avro payload is framed in the record. This determines where the Avro body starts (envelope to skip) and — for `FROM_RECORD` — where the id is read:

- **`CONFLUENT_4B`** – Confluent wire format: magic byte `0x00` + 4-byte schema id, then the Avro body.
- **`APICURIO_8B`** – Apicurio legacy wire format: magic byte `0x00` + 8-byte global id, then the Avro body.
- **`HEADER`** – schema id carried in a Kafka record header (`schemaIdHeader`); the body is the bare Avro payload.
- **`NONE`** – no envelope; the body is bare Avro with no id (use with `EXPRESSION`).

### Validation depth

- **`CONTENT`** (default) – resolve the schema and deserialize the payload to confirm it conforms. Strongest guarantee; adds one Avro decode per record.
- **`SCHEMA_ONLY`** – verify only that the record's embedded id resolves to the topic subject, without deserializing the payload. Lower latency; requires `schemaIdSource = FROM_RECORD`.

### Native Error Handling

The `nativeErrorHandling` option is available **only for Native API (Kafka Gateway protocol)** and defines the
validation error handling strategy for the `SUBSCRIBE` and `PUBLISH` phases.

#### onSubscribe (SUBSCRIBE phase)

| Strategy             | Description                                                                                                            |
|----------------------|----------------------------------------------------------------------------------------------------------------------|
| INVALIDATE_PARTITION | The affected partition is returned to the consumer with a `CORRUPT_MESSAGE` error and its records stripped.           |
| ADD_RECORD_HEADER    | The record is forwarded with a header (`headerName`) flagging it as invalid. The header value is a fixed, non-sensitive marker (`headerValue`, default a generic message) — the raw error is not exposed to consumers. |

#### onPublish (PUBLISH phase)

| Strategy                 | Description                                                                                     |
|--------------------------|------------------------------------------------------------------------------------------------|
| FAIL_WITH_INVALID_RECORD | The entire produce request is rejected with `INVALID_RECORD`; no messages are delivered to the broker. |
| LOG                      | Audit mode: the violation is logged/metered and the record is forwarded unchanged.             |




## Phases
The `avro-validation` policy can be applied to the following API types and flow phases.

### Compatible API types

* `NATIVE KAFKA`

### Supported flow phases:

* Publish
* Subscribe

## Compatibility matrix
Strikethrough text indicates that a version is deprecated.

| Plugin version| APIM |
| --- | ---  |
|1.x|4.6 and greater |


## Configuration options


#### 
| Name <br>`json name`  | Type <br>`constraint`  | Mandatory  | Default  | Description  |
|:----------------------|:-----------------------|:----------:|:---------|:-------------|
| Error handling strategy<br>`nativeErrorHandling`| object|  | | <br/>See "Error handling strategy" section.|
| Schema subject mapping<br>`schemaIdEvalString`| string|  | | EL that evaluates to the schema subject (used when schema source strategy is Expression). E.g. {#message.topic}-value.|
| Schema id header<br>`schemaIdHeader`| string|  | | Record header carrying the schema id (used when wire format is Record header).|
| Schema source strategy<br>`schemaIdSource`| enum (string)|  | `FROM_RECORD`| How the schema to validate against is located. FROM_RECORD uses the id carried in the record (enforced against the topic subject). EXPRESSION resolves the schema by an EL mapping (e.g. derived from the topic), ignoring the producer's id.<br>Values: `FROM_RECORD` `EXPRESSION`|
| Schema source<br>`schemaSource`| object|  | | <br/>See "Schema source" section.|
| Schema version mapping<br>`schemaVersionEvalString`| string|  | | EL that evaluates to the schema version (used when schema source strategy is Expression).|
| Validation depth<br>`validationDepth`| enum (string)|  | `CONTENT`| CONTENT decodes the payload to confirm it conforms. SCHEMA_ONLY only checks that the embedded id resolves to the topic subject (no deserialization, lower latency; requires the Embedded schema id source).<br>Values: `CONTENT` `SCHEMA_ONLY`|
| Wire format<br>`wireFormat`| enum (string)|  | `CONFLUENT_4B`| How the schema id / Avro payload is framed in the record. Determines where the Avro body starts (envelope to skip), and — for FROM_RECORD — where the id is read. NONE = bare Avro (no envelope).<br>Values: `CONFLUENT_4B` `APICURIO_8B` `HEADER` `NONE`|


#### Error handling strategy (Object)
| Name <br>`json name`  | Type <br>`constraint`  | Mandatory  | Description  |
|:----------------------|:-----------------------|:----------:|:-------------|
| PublishErrorHandling<br>`onPublish`| object|  | Error handling for publish phase<br/>See "PublishErrorHandling" section.|
| SubscribeErrorHandling<br>`onSubscribe`| object|  | Error handling for subscribe phase<br/>See "SubscribeErrorHandling" section.|


#### PublishErrorHandling (Object)
| Name <br>`json name`  | Type <br>`constraint`  | Mandatory  | Description  |
|:----------------------|:-----------------------|:----------:|:-------------|
| Strategy<br>`strategy`| object| ✅| Strategy of PublishErrorHandling<br>Values: `FAIL_WITH_INVALID_RECORD` `LOG`|


#### PublishErrorHandling: Fail with invalid record `strategy = "FAIL_WITH_INVALID_RECORD"` 
| Name <br>`json name`  | Type <br>`constraint`  | Mandatory  | Default  | Description  |
|:----------------------|:-----------------------|:----------:|:---------|:-------------|
| No properties | | | | | | | 

#### PublishErrorHandling: Log only (audit) `strategy = "LOG"` 
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
| Record header name<br>`headerName`| string| ✅| | Record header name used to flag invalid records|
| Record header value<br>`headerValue`| string|  | | Fixed, non-sensitive value written to the header. The raw validation error is not exposed to consumers to avoid leaking payload data. Defaults to a generic marker.|


#### Schema source (Object)
| Name <br>`json name`  | Type <br>`constraint`  | Mandatory  | Default  | Description  |
|:----------------------|:-----------------------|:----------:|:---------|:-------------|
| Resource name<br>`resourceName`| string| ✅| | Name of the schema registry resource|
| Source type<br>`sourceType`| string| ✅| `SCHEMA_REGISTRY_RESOURCE`| Values: `SCHEMA_REGISTRY_RESOURCE`|




## Examples

*Kafka subscribe with INVALIDATE_PARTITION strategy*
```yaml
apiVersion: "gravitee.io/v1alpha1"
kind: "ApiV4Definition"
metadata:
    name: "avro-validation-kafka-native-api-crd"
spec:
    name: "AVRO Validation example"
    type: "NATIVE"
    flows:
      - name: "Common Flow"
        enabled: true
        selectors:
            matchRequired: false
            mode: "DEFAULT"
        subscribe:
          - name: "AVRO Validation"
            enabled: true
            policy: "avro-validation"
            configuration:
              nativeErrorHandling:
                  onSubscribe:
                      strategy: INVALIDATE_PARTITION
              schemaIdEvalString: '{#message.topic}-value'
              schemaIdSource: EXPRESSION
              schemaSource:
                  resourceName: externalSchemaRegistry
                  sourceType: SCHEMA_REGISTRY_RESOURCE
              schemaVersionEvalString: latest
              validationDepth: CONTENT
              wireFormat: NONE

```
*Kafka subscribe with ADD_RECORD_HEADER strategy*
```yaml
apiVersion: "gravitee.io/v1alpha1"
kind: "ApiV4Definition"
metadata:
    name: "avro-validation-kafka-native-api-crd"
spec:
    name: "AVRO Validation example"
    type: "NATIVE"
    flows:
      - name: "Common Flow"
        enabled: true
        selectors:
            matchRequired: false
            mode: "DEFAULT"
        subscribe:
          - name: "AVRO Validation"
            enabled: true
            policy: "avro-validation"
            configuration:
              nativeErrorHandling:
                  onSubscribe:
                      headerName: X-Schema-Validation
                      headerValue: Schema validation failed
                      strategy: ADD_RECORD_HEADER
              schemaIdSource: FROM_RECORD
              schemaSource:
                  resourceName: externalSchemaRegistry
                  sourceType: SCHEMA_REGISTRY_RESOURCE
              validationDepth: CONTENT
              wireFormat: CONFLUENT_4B

```
*Kafka publish with FAIL_WITH_INVALID_RECORD strategy*
```yaml
apiVersion: "gravitee.io/v1alpha1"
kind: "ApiV4Definition"
metadata:
    name: "avro-validation-kafka-native-api-crd"
spec:
    name: "AVRO Validation example"
    type: "NATIVE"
    flows:
      - name: "Common Flow"
        enabled: true
        selectors:
            matchRequired: false
            mode: "DEFAULT"
        publish:
          - name: "AVRO Validation"
            enabled: true
            policy: "avro-validation"
            configuration:
              nativeErrorHandling:
                  onPublish:
                      strategy: FAIL_WITH_INVALID_RECORD
              schemaIdSource: FROM_RECORD
              schemaSource:
                  resourceName: externalSchemaRegistry
                  sourceType: SCHEMA_REGISTRY_RESOURCE
              validationDepth: CONTENT
              wireFormat: CONFLUENT_4B

```


## Changelog

### [2.2.0-alpha.2](https://github.com/gravitee-io/gravitee-policy-json-validation/compare/2.2.0-alpha.1...2.2.0-alpha.2) (2026-03-10)


##### Bug Fixes

* Improve JSON validation error handling ([d21be00](https://github.com/gravitee-io/gravitee-policy-json-validation/commit/d21be00ddf6aa079cb510c92d2800cdeb71620da))

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

