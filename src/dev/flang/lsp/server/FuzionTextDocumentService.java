package dev.flang.lsp.server;

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
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;

public class FuzionTextDocumentService implements TextDocumentService {
  /**
   * currently open text documents and their contents
   */
  private static HashMap<String, String> textDocuments = new HashMap<String, String>();

  public static String getText(String uri) {
    return textDocuments.get(uri);
  }

  @Override
  public void didOpen(DidOpenTextDocumentParams params) {
    var textDocument = params.getTextDocument();
    var uri = textDocument.getUri();
    var text = textDocument.getText();

    textDocuments.put(uri, text);

    var diagnostics = Diagnostics.getPublishDiagnosticsParams(uri, text);
    if (diagnostics.getDiagnostics().size() > 0) {
      Main.getLanguageClient().publishDiagnostics(diagnostics);
    }
  }

  // taken from apache commons
  public static int ordinalIndexOf(String str, String substr, int n) {
    if (n == 0) {
      return -1;
    }
    int pos = str.indexOf(substr);
    while (--n > 0 && pos != -1)
      pos = str.indexOf(substr, pos + 1);
    return pos;
  }

  @Override
  public void didChange(DidChangeTextDocumentParams params) {
    var uri = params.getTextDocument().getUri();
    var text = textDocuments.get(uri);
    var contentChanges = params.getContentChanges();

    // TODO test if this works correctly
    reverseSort(contentChanges);
    text = applyContentChanges(text, contentChanges);

    textDocuments.put(uri, text);

    var diagnostics = Diagnostics.getPublishDiagnosticsParams(uri, text);
    if (diagnostics.getDiagnostics().size() > 0) {
      Main.getLanguageClient().publishDiagnostics(diagnostics);
    }
  }

  /**
   * sort descending by line then descending by character
   *
   * @param contentChanges
   */
  private void reverseSort(List<TextDocumentContentChangeEvent> contentChanges) {
    contentChanges.sort((left, right) -> {
      if (right.getRange().getStart().getLine() == left.getRange().getStart().getLine()) {
        return Integer.compare(right.getRange().getStart().getCharacter(), left.getRange().getStart().getCharacter());
      } else {
        return Integer.compare(right.getRange().getStart().getLine(), left.getRange().getStart().getLine());
      }
    });
  }

  private String applyContentChanges(String text, List<TextDocumentContentChangeEvent> contentChanges) {
    return contentChanges.stream().reduce(text, (_text, contentChange) -> {
      var start = ordinalIndexOf(_text, System.lineSeparator(), contentChange.getRange().getStart().getLine()) + 1
          + contentChange.getRange().getStart().getCharacter();
      var end = ordinalIndexOf(_text, System.lineSeparator(), contentChange.getRange().getEnd().getLine()) + 1
          + contentChange.getRange().getEnd().getCharacter();
      return _text.substring(0, start) + contentChange.getText() + _text.substring(end, _text.length());
    }, String::concat);
  }

  @Override
  public void didClose(DidCloseTextDocumentParams params) {
    textDocuments.remove(params.getTextDocument().getUri());
  }

  @Override
  public void didSave(DidSaveTextDocumentParams params) {
    var uri = params.getTextDocument().getUri();
    var text = params.getText();
    textDocuments.put(uri, text);
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
