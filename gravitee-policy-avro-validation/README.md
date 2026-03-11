
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

| Property                | Required                 | Description                                                                                                     | Type                         | Default                 |
|-------------------------|--------------------------|-----------------------------------------------------------------------------------------------------------------|------------------------------|-------------------------|
| scope                   | X                        | Policy scope from where the policy is executed                                                                  | Policy scope                 | REQUEST_CONTENT         |
| schemaSource            | X                        | Defines the schema source to resolve the validation schema.                                                     | Schema Source object         |                         |
| schemaIdSource          | X                        | Defines the source of schema id: native for confluent form or SpEL for both confluent and simple.               | Schema Source object         |                         |
| serializationForm       | X                        | Defines the serialization form: confluent or simple.                                                            | Schema Source object         |                         |
| schemaIdEvalString      | X (for Custom id source) | Defines the SpEL for schema ID.                                                                                 | Schema Source object         |                         |
| schemaVersionEvalString | X (for Custom id source) | Defines the SpEL for schema version.                                                                            | Schema Source object         |                         |
| nativeErrorHandling     | X                        | Defines error handling strategy for consumer/producer if policy is used in native API (Kafka Gateway protocol). | Native Error Handling object |                         |

### Schema Source

Specifies the source used to resolve schemas for validation. Only available option is:

- **Resource-based schema registry** – provide the resource name and a mapping expression to resolve the schema subject dynamically (e.g., using `{#message.topic}` to derive the subject from the topic name) or based on native confluent mechanism (schema id encoded on bytes 2 to 5).

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
| Name <br>`json name`  | Type <br>`constraint`  | Mandatory  | Description  |
|:----------------------|:-----------------------|:----------:|:-------------|
| Error handling strategy<br>`nativeErrorHandling`| object|  | <br/>See "Error handling strategy" section.|
| Schema source<br>`schemaSource`| object|  | <br/>See "Schema source" section.|
| SerializationForm<br>`serializationForm`| object|  | Serialization form<br/>See "SerializationForm" section.|


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


#### Schema source (Object)
| Name <br>`json name`  | Type <br>`constraint`  | Mandatory  | Default  | Description  |
|:----------------------|:-----------------------|:----------:|:---------|:-------------|
| Resource name<br>`resourceName`| string| ✅| | Name of the schema registry resource|
| Source type<br>`sourceType`| string| ✅| `SCHEMA_REGISTRY_RESOURCE`| Values: `SCHEMA_REGISTRY_RESOURCE`|


#### SerializationForm (Object)
| Name <br>`json name`  | Type <br>`constraint`  | Mandatory  | Description  |
|:----------------------|:-----------------------|:----------:|:-------------|
| Serialization Form<br>`serializationForm`| object| ✅| Serialization Form of SerializationForm<br>Values: `CONFLUENT` `SIMPLE`|


#### SerializationForm: Confluent `serializationForm = "CONFLUENT"` 
| Name <br>`json name`  | Type <br>`constraint`  | Mandatory  | Default  | Description  |
|:----------------------|:-----------------------|:----------:|:---------|:-------------|
| Schema identifier source<br>`schemaIdSource`| string| ✅| | |


#### SerializationForm: Simple `serializationForm = "SIMPLE"` 
| Name <br>`json name`  | Type <br>`constraint`  | Mandatory  | Default  | Description  |
|:----------------------|:-----------------------|:----------:|:---------|:-------------|
| Schema name mapping<br>`schemaIdEvalString`| string| ✅| | EL that evaluates to schema name|
| `schemaIdSource`| string| ✅| `EVAL` (constant)| |
| Schema version mapping<br>`schemaVersionEvalString`| string| ✅| | EL that evaluates to schema version|




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
              schemaSource:
                  resourceName: externalSchemaRegistry
                  schemaMapping: '{#message.topic}'
                  sourceType: SCHEMA_REGISTRY_RESOURCE

```


## Changelog


