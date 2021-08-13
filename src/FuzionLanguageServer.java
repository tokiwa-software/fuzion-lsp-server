import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

public class FuzionLanguageServer implements LanguageServer {

  @Override
  public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
    final InitializeResult res = new InitializeResult(new ServerCapabilities());
    CompletionOptions completionProvider = new CompletionOptions();
    completionProvider.setResolveProvider(Boolean.TRUE);
    res.getCapabilities().setCompletionProvider(completionProvider);
    res.getCapabilities().setTextDocumentSync(TextDocumentSyncKind.Full);
    return CompletableFuture.supplyAsync(() -> res);
  }

  @Override
  public CompletableFuture<Object> shutdown() {
    return CompletableFuture.supplyAsync(() -> Boolean.TRUE);
  }

  @Override
  public void exit() {
    System.exit(0);
  }

  @Override
  public TextDocumentService getTextDocumentService() {
    return new FuzionTextDocumentService();
  }

  @Override
  public WorkspaceService getWorkspaceService() {
    return new FuzionWorkspaceService();
  }
}
