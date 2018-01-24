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

Copyright © 2016-2017 [Siili Solutions Plc.](http://www.siili.com)

Distributed under the [Eclipse Public License 1.0.](https://www.eclipse.org/legal/epl-v10.html)

## Table of Contents
1. Forewords
1. Preparations and Required Software
1. How Hedge Works
1. Preparing Hedge Authentication Against Azure
1. Authentication for AWS (tba)
1. Supported Handler Types
1. Basic Serverless Function Project Structure
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

Currently supported handler types are :
* HTTP Request In - HTTP Response Out Handler (Function triggered by incoming HTTP request)

Other handler types are planned.

### Basic Serverless Project Structure

A basic structure can be found in one of the examples in the **(Example TBA)** folder.

* src/ - This directory contains your CLJS source files
* target/ - Optionally persisted compiled output directory
* resources/hedge.edn - Includes configuration information that maps your program function to the serverless function entry point. You can configure here if a function is protected with authentication code or available without authentication.
* test/ - This directory contains your unit tests
* boot.properties - Boot properties
* build.boot - Your build configuration file
* package.json - Optionally, if you use external Node modules
* node_modules - Optionally, if you have installed Node modules

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

    boot deploy-azure -a NameOfFunctionApp -r NameOfResourceGroup

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
    boot azure-publish-profile -a functionapp -r resourcegroup

    # Deploy to Azure and Persist the compiled artifacts in **target/** directory (index.js and function.json)
    boot deploy-azure -a functionapp -r resourcegroup target

    # Persist the compiled output to target/ without deploy
    boot deploy-to-target

    # Persist the compiled output to <directory>
    boot deploy-to-directory -O <optimization level> -f <function name> -d <directory>

    # Deploy compiled artifacts from target directory (index.js and function.json)
    boot deploy-azure-from-directory -a functionapp -r resourcegroup -d <directory>
