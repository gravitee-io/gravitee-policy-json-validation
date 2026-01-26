# [2.2.0-alpha.1](https://github.com/gravitee-io/gravitee-policy-json-validation/compare/2.1.0...2.2.0-alpha.1) (2026-01-26)


### Features

* added support for kafka gateway protocol ([e77fd4b](https://github.com/gravitee-io/gravitee-policy-json-validation/commit/e77fd4bf756011596352c1bc148e9b7bd68f385d))
* integration with schema registry resources ([c6ad54d](https://github.com/gravitee-io/gravitee-policy-json-validation/commit/c6ad54d92e80653d214005c94cbfc92fd18ab6f5))

# [2.1.0](https://github.com/gravitee-io/gravitee-policy-json-validation/compare/2.0.3...2.1.0) (2025-11-13)


### Features

* support JSON Schema v3.1 serialization in JsonValidationOAIOperationVisitor ([1472a48](https://github.com/gravitee-io/gravitee-policy-json-validation/commit/1472a48f67ae27d5a9515742a3286e2600f04b28))

## [2.0.3](https://github.com/gravitee-io/gravitee-policy-json-validation/compare/2.0.2...2.0.3) (2025-03-13)


### Bug Fixes

* JSON validation policy message not published ([0a3b3f7](https://github.com/gravitee-io/gravitee-policy-json-validation/commit/0a3b3f7125ce5a9e748217d997a81b84ab1f61d1))

## [2.0.2](https://github.com/gravitee-io/gravitee-policy-json-validation/compare/2.0.1...2.0.2) (2025-01-17)


### Bug Fixes

* naming ([7c390b0](https://github.com/gravitee-io/gravitee-policy-json-validation/commit/7c390b0173d2144dc3bdc108cb520cedae8cd1a2))

## [2.0.1](https://github.com/gravitee-io/gravitee-policy-json-validation/compare/2.0.0...2.0.1) (2025-01-17)


### Bug Fixes

* change the error code ([44bbf67](https://github.com/gravitee-io/gravitee-policy-json-validation/commit/44bbf67c89584c33f2a9e2a930a0ccf8112eb3a7))

# [2.0.0](https://github.com/gravitee-io/gravitee-policy-json-validation/compare/1.7.1...2.0.0) (2025-01-07)


### chore

* **deps:** bump gravitee-parent to 22 ([3301141](https://github.com/gravitee-io/gravitee-policy-json-validation/commit/33011415b2cf7b2f7430451a853a8a177b45653c))


### Features

* **async:** allow use policy in async API ([df608a9](https://github.com/gravitee-io/gravitee-policy-json-validation/commit/df608a9b7eaf323b99b514fff8509bdc0ee48dfb))


### BREAKING CHANGES

* **async:** now compatible with APIM 4.6 or greater

APIM-7216
* **deps:** now use JDK 17 as source and target compilation

## [1.7.1](https://github.com/gravitee-io/gravitee-policy-json-validation/compare/1.7.0...1.7.1) (2024-06-17)


### Bug Fixes

* improve json-schema with V4 PolicyStudio ([310021d](https://github.com/gravitee-io/gravitee-policy-json-validation/commit/310021d2277d5937611de0633496f4a6b49294ae))

# [1.7.0](https://github.com/gravitee-io/gravitee-policy-json-validation/compare/1.6.2...1.7.0) (2023-12-19)


### Features

* enable policy on REQUEST phase for message APIs ([69bda3f](https://github.com/gravitee-io/gravitee-policy-json-validation/commit/69bda3fb7787f160fa44774f8884eba57dbae8cd)), closes [gravitee-io/issues#9430](https://github.com/gravitee-io/issues/issues/9430)

## [1.6.2](https://github.com/gravitee-io/gravitee-policy-json-validation/compare/1.6.1...1.6.2) (2023-07-20)


### Bug Fixes

* update policy description ([c868322](https://github.com/gravitee-io/gravitee-policy-json-validation/commit/c86832205e2f2ee08ac1d91ea799aa57b3f92a7d))

## [1.6.1](https://github.com/gravitee-io/gravitee-policy-json-validation/compare/1.6.0...1.6.1) (2022-03-28)


### Bug Fixes

* stop propagating request to backend if not valid ([877f812](https://github.com/gravitee-io/gravitee-policy-json-validation/commit/877f812294f72ac87c8cc9b4c5ad76f87d0b86bf))
