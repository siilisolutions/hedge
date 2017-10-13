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

## License

Copyright © 2016-2017 [Siili Solutions Plc.](http://www.siili.com)

Distributed under the [Eclipse Public License 1.0.](https://www.eclipse.org/legal/epl-v10.html)
