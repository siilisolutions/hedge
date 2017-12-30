This plugin is is companion plugin for Hedge. Hedge is a platform agnostic
Clojurescript Framework for ring compatible serverless function handlers.

This plugin makes usage of Hedge easier. Serverless Framework is used to handle
deployment and this plugin creates build instructions for Hegde and invokes
Hedge to build requested handler when requested by user.

Secondary target of the plugin is to improve development agility by enabling
local function testing.

# Requirements

This plugin and Hedge is currently under heave development process.

Full deployment with `deploy` command should work with unreleased v0.0.2 Hedge.
AWS and `deploy function` commands require unreleased v0.0.3 Hedge or custom
build from aws branch

# Usage

See example repositories TBD and TBD for AWS and Azure examples

Note. This plugin picks only functions with are marked with `hedge` key. Keys'
values are Clojure namespaces and plugin automatically generates working handler
keys from the value.

# Known limitations

 - Deployment to Azure is tested only with serverless-azure-functions v0.4.0.
   Newer version might break this plugin,
 - Only HTTP trigger/input/output are supported by Hedge
 - Plugin version should follow Hedge version?

# License

Copyright © 2016-2017 [Siili Solutions Plc.](http://www.siili.com)

Distributed under the [Eclipse Public License 1.0.](https://www.eclipse.org/legal/epl-v10.html)
