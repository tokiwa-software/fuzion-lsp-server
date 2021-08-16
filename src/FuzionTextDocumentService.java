import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.io.ByteArrayInputStream;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentItem;

import dev.flang.parser.Parser;
import dev.flang.util.SourceFile;
import dev.flang.util.Errors;
import dev.flang.util.Errors.Error;
import dev.flang.ast.Block;
import dev.flang.ast.Feature;
import dev.flang.ast.Resolution;
import dev.flang.util.SourcePosition;
import dev.flang.fe.FrontEnd;
import dev.flang.fe.FrontEndOptions;

public class FuzionTextDocumentService implements TextDocumentService {

  /**
   * currently open text documents and their contents
   */
  private HashMap<String, String> textDocuments = new HashMap<String, String>();

  private FuzionLanguageServer _fuzionLanguageServer;

  private FrontEndOptions frontEndOptions = new FrontEndOptions(0, new dev.flang.util.List<>(), 0, false, true, null);

  public FuzionTextDocumentService(FuzionLanguageServer fuzionLanguageServer) {
    _fuzionLanguageServer = fuzionLanguageServer;
    System.setProperty("FUZION_DISABLE_ANSI_ESCAPES", "true");
  }

  private int getEndCharaterPositioin(String text, int line_number, int character_position) {
    var line_text = text.split("\n")[line_number];
    while (line_text.length() > character_position && line_text.charAt(character_position) != ' ') {
      character_position++;
    }
    return character_position;
  }

  private void WithTextInputStream(String text, Runnable runnable){
    byte[] byteArray = getBytes(text);

    InputStream testInput = new ByteArrayInputStream(byteArray);
    InputStream old = System.in;
    try {
      System.setIn(testInput);
      runnable.run();
    } finally {
      System.setIn(old);
    }
  }

  public void sendDiagnostics(String uri, String text) {
    // TODO move this
    this.textDocuments.put(uri, text);

    WithTextInputStream(text,() -> {
        Errors.clear();

        var mir = new FrontEnd(frontEndOptions).createMIR();

        if (Errors.count() > 0) {
          var diagnostics = getDiagnostics(text);

          var param = new PublishDiagnosticsParams(uri, diagnostics);
          _fuzionLanguageServer.getClient().publishDiagnostics(param);
        }

    });

  }

  private List<Diagnostic> getDiagnostics(String text) {
    return Errors.get().stream().map((error) -> {
      var start_line = error.pos._line - 1;
      var start_character = error.pos._column - 1;
      var start = new Position(start_line, start_character);
      var end = new Position(start_line, getEndCharaterPositioin(text, start_line, start_character));
      var message = error.msg + "\n" + error.detail;
      return new Diagnostic(new Range(start, end), message);
    }).collect(Collectors.toList());
  }

  private byte[] getBytes(String text) {
    byte[] byteArray = new byte[0];
    try {
      byteArray = text.getBytes("UTF-8");
    } catch (UnsupportedEncodingException e) {
      System.exit(1);
    }
    return byteArray;
  }

  @Override
  public void didOpen(DidOpenTextDocumentParams params) {
    var textDocument = params.getTextDocument();
    var uri = textDocument.getUri();
    var text = textDocument.getText();
    sendDiagnostics(uri, text);
  }

  @Override
  public void didChange(DidChangeTextDocumentParams params) {
    var uri = params.getTextDocument().getUri();
    var text = params.getContentChanges().get(0).getText();
    System.out.println(text);
    sendDiagnostics(uri, text);
  }

  @Override
  public void didClose(DidCloseTextDocumentParams params) {
    textDocuments.remove(params.getTextDocument().getUri());
  }

  @Override
  public void didSave(DidSaveTextDocumentParams params) {
  }

  protected static CompletionItem createTextCompletionItem(String label, Object data) {
    var item = new CompletionItem(label);
    item.setKind(CompletionItemKind.Text);
    item.setData(data);
    return item;
  }

  @Override
  public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams position) {
    List<CompletionItem> completionItems = Arrays.asList(createTextCompletionItem("test1", 1));
    return CompletableFuture.completedFuture(Either.forLeft(completionItems));
  }

  @Override
  public CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem unresolved) {
    return CompletableFuture.completedFuture(unresolved);
  }

}
