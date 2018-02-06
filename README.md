# Hedge · [![Join the chat at https://gitter.im/siilisolutions/hedge](https://badges.gitter.im/siilisolutions/hedge.svg)](https://gitter.im/siilisolutions/hedge?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge) [![License](https://img.shields.io/badge/License-EPL%201.0-red.svg)](https://opensource.org/licenses/EPL-1.0) [![Build Status](https://travis-ci.org/siilisolutions/hedge.svg?branch=master)](https://travis-ci.org/siilisolutions/hedge)

![Hedge](docs/images/hedgecube.png "Hedge")

Hedge is a platform agnostic ClojureScript framework for deploying ring compatible handlers to various environments with focus on serverless deployments.

## Repository Contents

 - [/library](/library) contains wrapping and transformation code for adapting handlers to various platforms
 - [/boot](/boot) contains Hedge specific Boot tasks for code generation
 - [/acceptance](/acceptance) contains Concordion based acceptance test suite which also works as technical documentation on how to use Hedge

## Known Limitations

 - asynchronous ring handlers not yet supported

## Supported Platforms

### [Azure Functions](https://azure.microsoft.com/en-us/services/functions/)
### [AWS Lambda](https://aws.amazon.com/lambda/)

## License

Copyright © 2016-2018 [Siili Solutions Plc.](http://www.siili.com)

Distributed under the [Eclipse Public License 1.0.](https://www.eclipse.org/legal/epl-v10.html)

## Table of Contents
1. Forewords
1. Preparations and Required Software
1. How Hedge Works
1. Preparing Hedge Authentication Against Azure
1. Authentication for AWS
1. Supported Handler Types
1. Basic Serverless Function Project Structure
1. hedge.edn
1. Handler signatures
1. Testing
1. Deploying to Azure
1. Deploying to AWS   
1. Other Usage Examples

### Forewords

This document gets you started writing and deploying serverless functions to different environments. Hedge is under development, so things can change.

### Preparations and Required Software

* Java JDK 8 - Boot runs on JVM
* [Boot-CLJ](https://github.com/boot-clj/boot) - Required to build ClojureScript projects and use Hedge
* [Node.js](https://nodejs.org/en/download/) - Required for running unit tests
* [Azure CLI](https://docs.microsoft.com/en-us/cli/azure/install-azure-cli?view=azure-cli-latest) - (Optional) Can be used when creating Service Principal for Azure and managing Azure resources
* [AWS CLI](https://aws.amazon.com/cli/) - (Optional) Can be used to create credentials and to remove old test stacks from AWS

### How Hedge Works

ClojureScript is compiled and optimized into JavaScript than can be run on Node.js or similair runtime environment available in serverless environments. Hedge takes care of generating code that is compatible and runnable on the deployment target. Hedge can then use provided authentication profile and deploy the compiled code to the serverless environment.

### Preparing Hedge Authentication Against Azure

Requirements: You must be subscription owner to be able to create service principals, or ask a subscription owner to generate the principal for you. This document describes how you can create service principal with Azure CLI from the command line.

If you haven't already performed log in with Azure CLI, do it and follow on-screen instructions.

    az login

Make sure you are using correct subscription

    az account list --output table

Optionally change the subscription

    az account set --subscription "<your subscription name>"

Example: Create Service Principal with the name "MyNameServicePrincipal"

    az ad sp create-for-rbac --name "MyNameServicePrincipal" --sdk-auth > MyNameServicePrincipal.json

Hint: Use a meaningful name that you can identify, so you can find it later if you need to remove it. Keep the generated file in a secure place, because it contains contributor role credentials by default that are able to affect things in your whole subscription.

Configure your environment

    export AZURE_AUTH_LOCATION=/path-to-your/MyNameServicePrincipal.json

### Authentication Against AWS

Easiest way to create credentials for AWS to follow [AWS guide](https://docs.aws.amazon.com/cli/latest/userguide/cli-chap-getting-started.html#cli-quick-configuration) for their CLI.

1. Create new access key and secret access key in [AWS IAM](https://console.aws.amazon.com/iam/home?#home) console
2. Setup credentials locally
  * By running command `aws configure` or
  * By creating `~/.aws/credentials` file [manually](https://docs.aws.amazon.com/cli/latest/userguide/cli-config-files.html)

Note: If you for some reason need to change authentication profile name you can use environment variable `AWS_PROFILE` with
name of the profile you wish to use.

If you don't want to save access key and secret access key into disk they can be
passed to hedge by using environment variable `AWS_ACCESS_KEY_ID` and
`AWS_SECRET_ACCESS_KEY`

To store environment variables for current shell session use command
`export <VARIABLE_NAME-1>=<VALUE_1> <VARIABLE_NAME-2>=<VALUE_2>`

### Supported Handler Types

| **Handler type** | **Trigger**          | **Trigger Input**    | **Output**    | **Azure** | **AWS** |
|------------------|----------------------|----------------------|---------------|-----------|---------|
| :api             | HTTP Request         | HTTP Request         | HTTP Response | Yes       | Yes     |
| :timer           | Cron schedule        | Timer                | result->JS    | Yes       | WIP     |
| :queue           | New message in queue | Message              | result->JS    | Yes       |         |

Other handler types are planned.

Notes on Azure handlers: In Azure you can configure the output to be passed as return value to other Azure services (i.e. queue, table storage etc).

### Basic Serverless Project Structure

A basic structure can be found in one of the examples in [AWS example](https://github.com/jikuja/hedge-example-aws) or [Azure example](https://github.com/jikuja/hedge-example-azure) repositories.

Note: Example directories might be merged to master repo at some point.

* src/ - This directory contains your CLJS source files
* target/ - Optionally persisted compiled output directory
* resources/hedge.edn - Includes configuration information that maps your program function to the serverless function entry point. You can configure here if a function is protected with authentication code or available without authentication. Also function type is defined here (api, timer, ...)
* test/ - This directory contains your unit tests
* boot.properties - Boot properties
* build.boot - Your build configuration file
* package.json - Optionally, if you use external Node modules
* node_modules - Optionally, if you have installed Node modules

Note: Current implementation of Hedge requires to select target cloud in `build.boot` file.
Refer example repositories for more info. This feature will be changed later.

### hedge.edn

**resources/hedge.edn** contains the configuration for the given functions.

Example of a configuration for two apis, two timers and one queue triggered function:

```
{:api {"api1" {:handler my_cool_function.core/crunch-my-data :authorization :anonymous}
       "api2" {:handler my_cool_function.core/hello :authorization :function}}

 :timer {"timer1" {:handler my_cool_function.core/timer-handler :cron "*/10 * * * *"}
         "timer2" {:handler my_cool_function.core/timer-handler-broken :cron "*/10 * * * *"}}

 :queue {:"queue" {:handler my_cool_function.core/timer-handler
                   :queue "nameOfQueue"
                   :connection "environmental variable that holds the connection string (Azure)"}}}
```

Common structure:

`{:handler-type {"name-given-to-api" {:handler namespace.core/name-of-function}}}`

Azure **:api** specific:

`:authorization :anonymous` or `:authorization :function` - defines if the HTTP endpoint public or access key protected

**:timer** specific:
`:cron "{minutes} {hours} {day-of-month} {months} {day-of-week}"`

Example on cron expression that will trigger every minute: `"*/1 * * * *"`

Note on used cron expression:
- Only simple expressions are supported
  - *, numbers and / wildcard
  - setting both day-of-month and day-of-week is not supported (AWS limitation)
  - names of days/months and L, W, ? and # wildcards are not supported
- Azure has a field for seconds and AWS doesn't (field results to 0 when generating Azure function.json)
- AWS has a field for years and Azure doesn't (field results to *)

Azure users: You can modify the resulted function.json if you need to incorporate seconds in your cron expression

Azure **:queue** specific:
`:accessRights "Manage"` or `:accessRights "Listen"` - if defined, will use servicebus queue/topic queue instead of storage queue
`:connection "ENV_VARIABLE"` - function app environmental variable that holds the connection string to the queue service (for example AzureWebJobsStorage for storage queues)
`:subscription "subscriptionName"` - if defined together with `:accessRights`, will use a service bus topic & subscription. Subscription will be created if it does not exist.

If your function throws an unhandled exception or fails, the message is returned to the queue, retried and finally put into poison queue (azure storage queue) or dead-letter queue (servicebus queue).

Note: It might be that it is not possible to put a servicebus topic message to dead-letter queue (when your function fails), because design decisions in Azure function runtime.  

Storage Queue polling frequency and servicebus queue settings for the function app runtime can be set in **host.json**, see https://docs.microsoft.com/en-us/azure/azure-functions/functions-host-json#queues

Please note you must create the queues/topics yourself (you can do it through the portal or use Azure Storage Explorer with storage Queues).

### Handler signatures

Short examples on handler signatures:

```
(defn api-handler
    "req contains the incoming http request, return value is passed as the response"
    [req]
    "Hello World!")

(defn timer-handler
    "timer contains the scheduled trigger timestamp, return can be passed to function output i.e. other wired service"
    [timer]
    "Hello World!")

(defn queue-handler
    "message contains the message payload, return can be passed to function output i.e. other wired service"
    [message]
    "Hello World!)
```

See examples for more usage patterns.

### Testing

Testing your function (unit testing) requires a JavaScript runtime, ie. Node.js (or PhantomJS) because ClojureScript is compiled to JavaScript and needs a platform to run on.

To run unit tests in test/ -folder

    boot test

To run unit tests when files change in your project (watch project)

    boot watch test

### Deploying To Azure

To deploy, Hedge requires that you create your Resource Group. Hedge can create a Storage Account and Function App for you, but we recommend that you create these by your self so that you can choose where you host your serverless function code. One Function App can contain multiple serverless functions.

When you create these by your self you can choose location of data center, storage options and service plan.

Storage account is required to store your function code and logs that your functions create and other settings.

You can do it from the [Azure Portal](https://portal.azure.com) or use Azure CLI (Instructions below).

Example create the Resource Group in northeurope data center:

    az group create --name NameOfResourceGroup --location northeurope

To create your storage account with simplest storage option in north europe:

    az storage account create --name NameOfStorageAccount --location northeurope --resource-group NameOfResourceGroup --sku Standard_LRS

To create your function app with consumption plan (Windows Server backed serverless runtime with dynamic resource scaling, no dedicated VM):

    az functionapp create --name NameOfFunctionApp --storage-account NameOfStorageAccount --resource-group NameOfResourceGroup --consumption-plan-location northeurope

To compile and deploy your function to Azure:

    boot azure/deploy-azure -a NameOfFunctionApp -r NameOfResourceGroup

If your authentication file is correctly generated and found in the environment, your function should deploy to Azure and can be reached with HTTP.

### Deploying To AWS

To compile and deploy your project to AWS:

    boot deploy-aws -n <STACK_NAME>

Command checks that `STACK_NAME` name is free. If it is free project
is deployed into Cloudformation stack with given name. If name
is reserved error message is shown. After build and deployment steps command print API endpoint base URL.

Visible HTTP endpoints of the project are in base URL.

Technical note: command check if S3 bucket with name
`hedge-<STACK_NAME>` is free. This might be changed
in the future.

### Other Usage Examples

    # Get information about the Azure Publishing Profile
    boot azure/azure-publish-profile -a functionapp -r resourcegroup

    # Deploy to Azure and Persist the compiled artifacts in **target/** directory (index.js and function.json)
    boot azure/deploy-azure -a functionapp -r resourcegroup target

    # Persist the compiled output. Given no options, defaults to Optimizations=simple and directory=target
    boot azure/deploy-to-directory -O <optimization level> -f <function name> -d <directory>

    # Deploy compiled artifacts from target directory (index.js and function.json)
    boot azure/deploy-azure-from-directory -a functionapp -r resourcegroup -d <directory>

    # Get more help of task, i.e. commandline options
    boot <task-name> -h
