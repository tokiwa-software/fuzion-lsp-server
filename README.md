# A language server implementation for Fuzion

## Requirements
- java version 16 or higher
- GNU-Make
- wget

## Run
- make

## Debug
- make debug

## Usage
### Transport socket
- run `make`
- connect the client to the (random) port the server prints to stdout.
### Transport stdio
- run `make stdio`

## Clients
|Client|Repository|
|---|---|
|vscode||
|vim||
|emacs||
|eclipse (theia)||

## TODO

|Language Feature|Status|
|---|---|
|completion|☐|
|completion resolve|☐|
|hover|☐|
|signatureHelp|☐|
|declaration|☐|
|definition|☐|
|typeDefinition|☐|
|implementation|☐|
|references|☐|
|documentHighlight|☐|
|documentSymbol|☐|
|codeAction|☐|
|codeAction resolve|☐|
|codeLens|☐|
|codeLens resolve|☐|
|codeLens refresh|☐|
|documentLink|☐|
|documentLink resolve|☐|
|documentColor|☐|
|colorPresentation|☐|
|formatting|☐|
|rangeFormatting|☐|
|onTypeFormatting|☐|
|rename|☐|
|prepareRename|☐|
|foldingRange|☐|
|selectionRange|☐|
|prepareCallHierarchy|☐|
|callHierarchy incoming|☐|
|callHierarchy outgoing|☐|
|semantic tokens|☐|
|linkedEditingRange|☐|
|moniker|☐|
