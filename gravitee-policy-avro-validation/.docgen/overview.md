You can use the `avro-validation` policy to validate AVRO payloads from Kafka endpoint. 

It supports schema registry, getting schema ID from confluent native mechanism, or schema name and version from SpEL.

## Phase

### Native API - Kafka Gateway

| onRequest | onResponse | PUBLISH (onMessageRequest) | SUBSCRIBE (onMessageResponse) |
|-----------|------------|----------------------------|-------------------------------|
|           |            | X                          | X                             |