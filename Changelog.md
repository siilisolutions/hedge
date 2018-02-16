# Version history

## 0.1.2

* Fixed critical bug in Azure input/output bindings
* Added proper exception handling
* Added documentation for exception handling best practices

## Hedge 0.1.1

* Fixed broken build

## Hedge 0.1.0

* Unified logging with [Timbre](https://github.com/ptaoussanis/timbre)
* AWS deployment and template creation fixes
* Faster Azure deployment
* Timer triggered functions for AWS and Azure
* Queue triggered functions for AWS and Azure
* Azure only:
  * Output support for queues, table storage and Cosmos DB tables
  * Input support for table storage and Cosmos DB tables
* Task changes
  * Renamed tasks. Use `boot` to check new task names
  * build.boot: Refer [README.md](README.md#buildboot) and/or example project for more information for recommended usage
* Documentation updates

## Hedge 0.0.4

* AWS native deployment using SAM/CloudFormation
* documentation improvements

## Hedge 0.0.3

* improvements all around
* initial AWS Lambda support
