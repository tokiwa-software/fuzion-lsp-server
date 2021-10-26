/*

This file is part of the Fuzion language server protocol implementation.

The Fuzion language server protocol implementation is free software: you can redistribute it
and/or modify it under the terms of the GNU General Public License as published
by the Free Software Foundation, version 3 of the License.

The Fuzion language server protocol implementation is distributed in the hope that it will be
useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public
License for more details.

You should have received a copy of the GNU General Public License along with The
Fuzion language implementation.  If not, see <https://www.gnu.org/licenses/>.

*/

/*-----------------------------------------------------------------------
 *
 * Tokiwa Software GmbH, Germany
 *
 * Source of class FuzionLanguageServer
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.server;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.CodeLensOptions;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.ExecuteCommandOptions;
import org.eclipse.lsp4j.HoverOptions;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.RenameOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.SignatureHelpOptions;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

/**
 * does the initialization of language server features
 */
public class FuzionLanguageServer implements LanguageServer
{

  @Override
  public CompletableFuture<InitializeResult> initialize(InitializeParams params)
  {
    final InitializeResult res = new InitializeResult(new ServerCapabilities());
    var capabilities = res.getCapabilities();

    initializeCompletion(capabilities);
    initializeHover(capabilities);
    initializeDefinition(capabilities);
    initializeReferences(capabilities);
    initializeRename(capabilities);
    initializeCodeActions(capabilities);
    initializeDocumentSymbol(capabilities);
    initializeCodeLens(capabilities);
    initializeSignatureHelp(capabilities);

    capabilities.setTextDocumentSync(TextDocumentSyncKind.Incremental);
    return CompletableFuture.supplyAsync(() -> res);
  }

  private void initializeSignatureHelp(ServerCapabilities capabilities)
  {
    // NYI which characters should trigger?
    capabilities.setSignatureHelpProvider(new SignatureHelpOptions(List.of(" ", "(", ",")));
  }

  private void initializeCodeLens(ServerCapabilities capabilities)
  {
    // NYI implement code lens resolve
    capabilities.setCodeLensProvider(new CodeLensOptions(false));
  }

  private void initializeDocumentSymbol(ServerCapabilities capabilities)
  {
    capabilities.setDocumentSymbolProvider(true);
  }

  private void initializeCodeActions(ServerCapabilities capabilities)
  {
    capabilities.setCodeActionProvider(true);
    var commands = Arrays.stream(Commands.values())
      .map(c -> c.name())
      .collect(Collectors.toList());
    capabilities.setExecuteCommandProvider(new ExecuteCommandOptions(commands));
  }

  private void initializeRename(ServerCapabilities capabilities)
  {
    capabilities.setRenameProvider(new RenameOptions(true));
  }

  private void initializeReferences(ServerCapabilities capabilities)
  {
    capabilities.setReferencesProvider(true);
  }

  private void initializeDefinition(ServerCapabilities serverCapabilities)
  {
    serverCapabilities.setDefinitionProvider(true);
  }

  private void initializeHover(ServerCapabilities serverCapabilities)
  {
    var hoverOptions = new HoverOptions();
    hoverOptions.setWorkDoneProgress(Boolean.FALSE);
    serverCapabilities.setHoverProvider(hoverOptions);
  }

  private void initializeCompletion(ServerCapabilities serverCapabilities)
  {
    CompletionOptions completionOptions = new CompletionOptions();
    completionOptions.setResolveProvider(Boolean.FALSE);
    completionOptions.setTriggerCharacters(List.of(".", "<"));
    serverCapabilities.setCompletionProvider(completionOptions);
  }

  @Override
  public CompletableFuture<Object> shutdown()
  {
    return CompletableFuture.supplyAsync(() -> Boolean.TRUE);
  }

  @Override
  public void exit()
  {
    System.exit(0);
  }

  @Override
  public TextDocumentService getTextDocumentService()
  {
    return new FuzionTextDocumentService();
  }

  @Override
  public WorkspaceService getWorkspaceService()
  {
    return new FuzionWorkspaceService();
  }
}
