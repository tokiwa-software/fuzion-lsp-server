package dev.flang.lsp.server;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.ExecuteCommandOptions;
import org.eclipse.lsp4j.HoverOptions;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.RenameOptions;
import org.eclipse.lsp4j.ServerCapabilities;
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

    capabilities.setTextDocumentSync(TextDocumentSyncKind.Incremental);
    return CompletableFuture.supplyAsync(() -> res);
  }

  private void initializeDocumentSymbol(ServerCapabilities capabilities)
  {
    capabilities.setDocumentSymbolProvider(true);
  }

  private void initializeCodeActions(ServerCapabilities capabilities)
  {
    capabilities.setCodeActionProvider(true);
    var commands = Arrays.stream(Commands.values())
      .map(c -> c.toString())
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
