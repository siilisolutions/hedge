'use strict';

const BbPromise = require('bluebird');
const childProcess = require('child_process');
const edn = require("jsedn");
const path = require('path');
const fs = require('fs-extra');
const _ = require('lodash');

const buildFolder = 'target';
const serverlessFolder = '.serverless';

class HedgePlugin {
  constructor(serverless, options) {
    this.serverless = serverless;
    this.options = options;

    this.commands = {
      build: {
        usage: 'Builds your Clojurescript project with boot',
        lifecycleEvents: ['execute'],
        options: {
          usage: 'Specify functions to build',
          required: false,
        },
      },
    };

    this.hooks = {
      // just build. Not really useful
      'build:execute': this.build.bind(this),

      // for package command
      'before:package:createDeploymentArtifacts': this.package.bind(this),
      'after:package:createDeploymentArtifacts': this.restore.bind(this),

      'before:deploy:function:packageFunction': this.foo2.bind(this),
      'after:deploy:function:packageFunction': this.restore.bind(this),
    };
  }

  restore() {
    this.serverless.cli.log("Restoring ServicePath!");
    fs.copySync(
      // if running package command:
      //   copy target/.serverless into .serverless
      //   directory has only zip files which contains all functions
      // other use cases: TODO!
      path.join(this.originalServicePath, buildFolder, serverlessFolder),
      path.join(this.originalServicePath, serverlessFolder)
    );
    // restore path
    this.serverless.config.servicePath = this.originalServicePath
    // FIXME delete buildFolder/.serverless
  }

  // FIXME: do we need this? Boot/cljs finds node_modules from root
  copyExtras() {
    if (!fs.existsSync(path.resolve(path.join(buildFolder, 'node_modules')))) {
      fs.symlinkSync(path.resolve('node_modules'), path.resolve(path.join(buildFolder, 'node_modules')));
    }

    if (!fs.existsSync(path.resolve(path.join(buildFolder, 'package.json')))) {
      fs.symlinkSync(path.resolve('package.json'), path.resolve(path.join(buildFolder, 'package.json')));
    }
  }

  build() {
    this.createEdnFile();
    this.buildWithBoot();
  }

  package() {
    this.serverless.cli.log('Running hedge-plugin package');
    this.createEdnFile();
    this.setupFns();
    this.buildWithBoot();
  }

  foo2() {
    this.serverless.cli.log('foo2');
    this.createEdnFile();
    this.setupFns();
    this.buildWithBoot();
    this.copyExtras();
  }

  createEdnFile() {
    this.serverless.cli.log('Creating EDN file for Hedge');
    var output = edn.encode(this.convertFunctionsToEdn(this.serverless.service.functions));
    fs.writeFileSync(path.resolve(path.join('resources', 'hedge.edn')), output);
  }

  changeServicePath() {
    if (!this.originalServicePath) {
      this.originalServicePath = this.serverless.config.servicePath;
      this.serverless.config.servicePath = path.join(this.originalServicePath, buildFolder);
    }
  }

  buildWithBoot() {
    this.changeServicePath();

    if (this.options.function) {
      // TODO: build only one function
    } else {
      // build all functions
      this.serverless.cli.log('Building with Hedge...');
      childProcess.execSync('echo boot deploy-to-target', { stdio: 'inherit' });
      this.serverless.cli.log('Build done!');
    }
  }

  setupFns() {
    this.serverless.cli.log('setupFns');
    var functions = this.serverless.service.functions;
    var options = this.options;
    _.forIn(functions, (value, key) => {
      if (value["hedge"]) {
        value["handler"] = `${this.generateCloudName(value["hedge"])}/index.handler`;
      }
    });
  }

  dashedAlphaNumeric(s) {
    return s.replace('[^A-Za-z0-9\-]', '_');
  }

  generateCloudName(s) {
    var splitted = s.split('/');
    return this.dashedAlphaNumeric(splitted[0]).replace('.', '_') +
     '__' + this.dashedAlphaNumeric(splitted[1]);
  }

  convertFunctionsToEdn(fns) {
    var result = new edn.Map();
    _.forIn(fns, (value, key) => {
      if (value["hedge"]) {
        let inner = new edn.Map([edn.kw(':handler'), edn.sym(value["hedge"])]);
        result.set(key, inner);
      }
    })
    return new edn.Map([edn.kw(':api'), result]);
  }
}

module.exports = HedgePlugin;
