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

    if (this.serverless.service.provider.name === 'aws') {
      this.hooks = {
        // just build. Not really useful
        'build:execute': this.build.bind(this),

        // called by package and deploy
        'before:package:createDeploymentArtifacts': this.package.bind(this),
        'after:package:createDeploymentArtifacts': this.restore.bind(this),

        // called by deploy function
        'before:deploy:function:packageFunction': this.deployFunction.bind(this),
        'after:deploy:function:packageFunction': this.restore.bind(this),
      };
    } else if (this.serverless.service.provider.name === 'azure') {
      this.hooks = {
        // just build. Not really useful
        'build:execute': this.build.bind(this),

        // called by package and ???
        'before:deploy:deploy': this.package.bind(this),
        //'after:deploy:deploy': this.restore.bind(this), // FIXME: enable?

      };
    } else {
      throw new Error("Unsupported provider. Please file a bug report or PR");
    }
  }

  restore() {
    this.serverless.cli.log('Restoring ServicePath!');
    fs.copySync(
      // if running package command:
      //   copy target/.serverless into .serverless
      //   directory has only zip files which contains all functions
      // other use cases: TODO!
      path.join(this.originalServicePath, buildFolder, serverlessFolder),
      path.join(this.originalServicePath, serverlessFolder),
    );
    // restore path
    this.serverless.config.servicePath = this.originalServicePath;
    // FIXME: unset originalServicePath for azure

    // FIXME: delete buildFolder/.serverless
    // FIXME: clean output of copyExtras()
    // FIXME: cleanup mus be done in some other function.
  }

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
    // this.changeServicePath();
    this.buildWithBoot();
  }

  // package & deploy
  package() {
    this.serverless.cli.log('Running hedge-plugin package');
    this.createEdnFile();
    this.setupFns();
    this.changeServicePath();
    this.buildWithBoot();
    this.copyExtras();
  }

  // FIXME? deploy function?
  deployFunction() {
    this.serverless.cli.log('Starting deployFunction()...');
    this.createEdnFile();
    this.setupFns();
    this.changeServicePath();
    this.buildWithBoot();
    this.copyExtras();
  }

  createEdnFile() {
    this.serverless.cli.log('Creating EDN file for Hedge');
    const output = edn.encode(this.convertFunctionsToEdn(this.serverless.service.functions));
    fs.writeFileSync(path.resolve(path.join('resources', 'hedge.edn')), output);
  }

  changeServicePath() {
    if (!this.originalServicePath) {
      this.originalServicePath = this.serverless.config.servicePath;
      this.serverless.config.servicePath = path.join(this.originalServicePath, buildFolder);
    }
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
      if (value.handler && !value.hedge) {
        this.serverless.cli.log('WARNING: Functions with handler are not deployed');
        this.serverless.cli.log(`         Ignored function name: ${key}`);
      }
      if (value.hedge) {
        value.handler = `${this.generateCloudName(value.hedge)}/index.handler`;
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
