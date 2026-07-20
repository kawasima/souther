# Souther for VS Code

Syntax highlighting for the Souther language (`.sou`).

The TextMate grammar in `syntaxes/souther.tmLanguage.json` is generated from the compiler's
lexer, so it stays in step with the language. Regenerate it with:

```sh
mvn -q -pl souther-compiler exec:java \
  -Dexec.mainClass=net.unit8.souther.compiler.highlight.TmLanguageGenerator \
  -Dexec.args="editors/vscode/syntaxes/souther.tmLanguage.json"
```

A test (`TmLanguageGeneratorTest`) fails if the committed grammar drifts from the generator, so a
keyword added to the lexer forces the grammar — and this file — to be regenerated.

## Language server

The Souther language server is a self-contained jar that speaks LSP over stdio. Build it with:

```sh
mvn -q -pl souther-lsp -am package
# → souther-lsp/target/souther-lsp.jar
```

It provides diagnostics (all syntax errors plus the first semantic error), the document outline,
hover, go-to-definition, and semantic tokens — the last read the CST, so a type name and a value are
coloured differently even though Souther identifiers are not capitalised.

## Running the client in VS Code

`extension.js` launches the jar over stdio via `vscode-languageclient`. To try it from source:

```sh
mvn -q -pl souther-lsp -am package                 # build the server jar
mkdir -p editors/vscode/server
cp souther-lsp/target/souther-lsp.jar editors/vscode/server/
cd editors/vscode && npm install                   # fetch vscode-languageclient
```

Then open `editors/vscode` in VS Code and press F5 (Extension Development Host), or package it with
`vsce package`. Point `souther.server.jar` at another jar, or `souther.server.java` at a specific
`java`, through the settings if the defaults do not fit.
