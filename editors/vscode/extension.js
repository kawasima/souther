// The VS Code client for the Souther language server. It launches the self-contained
// souther-lsp.jar and talks to it over stdio; all language features (diagnostics, outline, hover,
// go-to-definition, semantic tokens) come from the server.
const path = require('path');
const { workspace, window } = require('vscode');
const { LanguageClient, TransportKind } = require('vscode-languageclient/node');

let client;

function activate(context) {
  const config = workspace.getConfiguration('souther');
  const configuredJar = config.get('server.jar');
  const jar = configuredJar && configuredJar.length > 0
    ? configuredJar
    : context.asAbsolutePath(path.join('server', 'souther-lsp.jar'));
  const java = config.get('server.java') || 'java';

  const run = { command: java, args: ['-jar', jar], transport: TransportKind.stdio };
  const serverOptions = { run, debug: run };

  const clientOptions = {
    documentSelector: [{ scheme: 'file', language: 'souther' }],
    synchronize: { fileEvents: workspace.createFileSystemWatcher('**/*.sou') },
  };

  client = new LanguageClient('souther', 'Souther Language Server', serverOptions, clientOptions);
  client.start().catch((err) => {
    window.showErrorMessage(`Souther: could not start the language server (${jar}): ${err.message}`);
  });
}

function deactivate() {
  return client ? client.stop() : undefined;
}

module.exports = { activate, deactivate };
