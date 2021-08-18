import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;

public class FuzionTextDocumentService implements TextDocumentService {
  /**
   * currently open text documents and their contents
   */
  private HashMap<String, String> textDocuments = new HashMap<String, String>();

  private LanguageClient _languageClient;

  public FuzionTextDocumentService(LanguageClient languageClient) {
    _languageClient = languageClient;

  }

  @Override
  public void didOpen(DidOpenTextDocumentParams params) {
    var textDocument = params.getTextDocument();
    var uri = textDocument.getUri();
    var text = textDocument.getText();

    this.textDocuments.put(uri, text);

    _languageClient.publishDiagnostics(Diagnostics.getPublishDiagnosticsParams(uri, text));

  }

  @Override
  public void didChange(DidChangeTextDocumentParams params) {
    var uri = params.getTextDocument().getUri();
    var text = params.getContentChanges().get(0).getText();

    this.textDocuments.put(uri, text);

    _languageClient.publishDiagnostics(Diagnostics.getPublishDiagnosticsParams(uri, text));
  }

  @Override
  public void didClose(DidCloseTextDocumentParams params) {
    textDocuments.remove(params.getTextDocument().getUri());
  }

  @Override
  public void didSave(DidSaveTextDocumentParams params) {
    var uri = params.getTextDocument().getUri();
    var text = params.getText();
    this.textDocuments.put(uri, text);
  }

  @Override
  public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams position) {
    return CompletableFuture.completedFuture(Completion.getCompletions(position));
  }

  @Override
  public CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem unresolved) {
    return CompletableFuture.completedFuture(unresolved);
  }

  @Override
	public CompletableFuture<Hover> hover(HoverParams params) {
    return CompletableFuture.completedFuture(Hovering.getHover(params));
	}

}
