= Json Validation Policy

ifdef::env-github[]
image:https://ci.gravitee.io/buildStatus/icon?job=gravitee-io/gravitee-policy-json-validation/master["Build status", link="https://ci.gravitee.io/job/gravitee-io/job/gravitee-policy-json-validation/"]
image:https://badges.gitter.im/Join Chat.svg["Gitter", link="https://gitter.im/gravitee-io/gravitee-io?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge"]
endif::[]

== Phase

[cols="2*", options="header"]
|===
^|onRequestContent
^|onResponseContent

^.^| X
^.^|

|===

== Description

The Json Validation policy allows json payload validation. This policy uses https://github.com/java-json-tools/json-schema-validator
Return Error 400 BAD REQUEST when validation failed with with custom error message body.
Inject processing report messages into request metrics for analytics.


== Configuration

|===
|Property |Required |Description |Type| Default

.^|errorMessage
^.^|X
|Custom error message in Json format. Spel is allowed.
^.^|string
|{"error":"Bad request"}

.^|schema
^.^|X
|Json schema.
^.^|string
|

.^|deepCheck
^.^|
|Validate descendant even if json parent container is invalid.
^.^|boolean
^.^|false

.^|validateUnchecked
^.^|
|Unchecked validation means that conditions which would normally cause the processing to stop with an exception are instead inserted into the resulting report. Warning: this means that anomalous events like an unresolvable JSON Reference, or an invalid schema, are masked!.
^.^|boolean
^.^|false

|===


== Http Status Code

|===
|Code |Message

.^| ```400```
| In case of:

* Invalid payload

* Invalid jsonschema

* Invalid error message json format

|===