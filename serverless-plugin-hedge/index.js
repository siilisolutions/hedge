'use strict';

const BbPromise = require('bluebird');
const childProcess = require('child_process');
const edn = require("jsedn");
const path = require('path');
const fs = require('fs');
const fs_extra = require('fs-extra');
const _ = require('lodash');

const buildFolder = 'target';

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
      'build:execute': this.build.bind(this),
      'before:package:createDeploymentArtifacts': this.foo.bind(this),
      'before:deploy:function:packageFunction': this.foo2.bind(this),
    };
  }

  copyExtras() {
    if (!fs_extra.existsSync(path.resolve(path.join(buildFolder, 'node_modules')))) {
      fs_extra.symlinkSync(path.resolve('node_modules'), path.resolve(path.join(buildFolder, 'node_modules')));
    }

    if (!fs_extra.existsSync(path.resolve(path.join(buildFolder, 'package.json')))) {
      fs_extra.symlinkSync(path.resolve('package.json'), path.resolve(path.join(buildFolder, 'package.json')));
    }
  }

  build() {
    this.serverless.cli.log('build');
    console.log(this.serverless);
    this.createEdnFile();
    this.buildCljs();
  }

  dashedAlphaNumeric(s) {
    return s.replace('[^A-Za-z0-9\-]', '_');
  }

  // use this to map handler to target's files
  generateCloudName(s) {
    // ; hedge-test.core/hello => hedge-test_core__hello
    // output file will be in hedge-test_core__hello/index.js with function handler
    // e.g. target/handler_core__hello/index.handler
    var splitted = s.split('/');
    return dashedAlphaNumeric(splitted[0]) + '__' + dashedAlphaNumeric(splitted[1]);
  }

  wrapperAndStripFunctions(fns) {
    var result = new edn.Map();
    _.forIn(fns, (value, key) => {
      let inner = new edn.Map([edn.kw(':handler'), edn.sym(value["handler"])]);
                               // authorization is temp fix for hedge. Remove after hedge is fixed
                               //edn.kw(':authorization'), edn.kw(':anonymous')]);
      result.set(key, inner);
    })
    return new edn.Map([edn.kw(':api'), result]);
  }

  // TODO: this always create edn/json with all function. use command line
  // boot parameters to select function to build
  createEdnFile() {
    this.serverless.cli.log('createEdnFile');
    console.log('this.options.function: ', this.options.function);
    console.log('this.serverless.service.functions: ', this.serverless.service.functions);

    var output = edn.encode(this.wrapperAndStripFunctions(this.serverless.service.functions));
    fs.writeFileSync(path.resolve(path.join('resources', 'hedge.edn')), output);
  }

  buildCljs() {
    if (!this.originalServicePath) {
      this.originalServicePath = this.serverless.config.servicePath;
      this.serverless.config.servicePath = path.join(this.originalServicePath, buildFolder);
    }

    if (this.options.function) {
      // TODO: build only one function
    } else {
      // build all functions
      this.serverless.cli.log('Starting build with boot');
      childProcess.execSync('boot deploy-to-target', { stdio: 'inherit' });
      this.serverless.cli.log('Build done');
    }
  }

  zz() {
    _.forIn(this.serverless.service.functions, (value, key) => {
      if (value.hedge) {
        value.handler =
      }
    });
  }

  setupFns() {
    this.serverless.cli.log('setupFns');
    var functions = this.serverless.service.functions;
    var options = this.options;
    console.log('functions: ', functions);
    console.log('options: ', options);
  }

  foo() {
    this.serverless.cli.log('foo');
    this.createEdnFile();
    this.setupFns();
    this.buildCljs();
    this.copyExtras();
  }

  foo2() {
    this.serverless.cli.log('foo2');
    this.createEdnFile();
    this.setupFns();
    this.buildCljs();
    this.copyExtras();
  }
}

module.exports = HedgePlugin;
