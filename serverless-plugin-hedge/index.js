'use strict';

const BbPromise = require('bluebird');
const childProcess = require('child_process');
const edn = require('jsedn');
const path = require('path');
const fs = require('fs-extra');
const _ = require('lodash');

const buildFolder = 'target';
const serverlessFolder = '.serverless';
const bootCommand = process.env.HEDGE_BOOT_COMMAND ?
                    process.env.HEDGE_BOOT_COMMAND :
                    'boot';

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

    // TODO: add default exclude

    if (this.serverless.service.provider.name === 'aws') {
      this.hooks = {
        // just build. Not really useful
        'build:execute': this.build.bind(this),

        // called by `package` and `deploy`(call package internally) commands
        'before:package:createDeploymentArtifacts': this.package.bind(this),
        'after:package:createDeploymentArtifacts': this.cleanUp.bind(this),

        // called by `deploy function` command
        'before:deploy:function:packageFunction': this.deployFunction.bind(this),
        'after:deploy:function:packageFunction': this.cleanUp.bind(this),
      };
    } else if (this.serverless.service.provider.name === 'azure') {
      this.hooks = {
        'build:execute': this.build.bind(this),

        // called by deploy
        'before:deploy:deploy': this.package.bind(this),
        'after:deploy:deploy': this.cleanUp.bind(this),

        // called by deploy function
        'before:deploy:function:deploy': this.package.bind(this),
        'after:deploy:function:deploy': this.cleanUp.bind(this),

      };
    } else {
      throw new Error("Unsupported provider. Please file a bug report or PR");
    }
  }

  cleanUp() {

  }

  build() {
    this.createEdnFile();
    this.buildWithBoot();
  }

  // package & deploy
  package() {
    this.serverless.cli.log('Running hedge-plugin package');
    this.createEdnFile();
    this.setupFns();
    this.buildWithBoot();
  }

  deployFunction() {
    this.serverless.cli.log('Starting deployFunction()...');
    this.createEdnFile();
    this.setupFns();
    this.buildWithBoot();
  }

  createEdnFile() {
    this.serverless.cli.log('Creating EDN file for Hedge');
    const output = edn.encode(this.convertFunctionsToEdn(this.serverless.service.functions));
    fs.ensureDirSync('resources');
    fs.writeFileSync(path.resolve(path.join('resources', 'hedge.edn')), output);
  }

  buildWithBoot() {
    if (this.options.function) {
      const fn = this.options.function;
      const fns = this.serverless.service.functions;
      this.serverless.cli.log(`Building function ${this.options.function} with Hedge...`);
      childProcess.execSync(`${bootCommand} deploy-to-target -f ${fns[fn]["hedge"]}`, { stdio: 'inherit' });
      this.serverless.cli.log('Build done!');
    } else {
      // build all functions
      this.serverless.cli.log('Building with Hedge...');
      childProcess.execSync(`${bootCommand} deploy-to-target`, { stdio: 'inherit' });
      this.serverless.cli.log('Build done!');
    }
  }

  setupFns() {
    this.serverless.cli.log('setupFns');
    const functions = this.serverless.service.functions;
    const options = this.options;
    _.forIn(functions, (value, key) => {
      if (value.hedge) {
        value.handler = `target/${this.generateCloudName(value.hedge)}/index.handler`;
      }
    });
  }

  dashedAlphaNumeric(s) {
    return s.replace('[^A-Za-z0-9\-]', '_');
  }

  generateCloudName(s) {
    const splitted = s.split('/');
    return `${this.dashedAlphaNumeric(splitted[0]).replace('.', '_')
    }__${this.dashedAlphaNumeric(splitted[1])}`;
  }

  convertFunctionsToEdn(fns) {
    const result = new edn.Map();
    _.forIn(fns, (value, key) => {
      if (value.hedge) {
        const inner = new edn.Map([edn.kw(':handler'), edn.sym(value.hedge)]);
        result.set(key, inner);
      }
    });
    return new edn.Map([edn.kw(':api'), result]);
  }
}

module.exports = HedgePlugin;
