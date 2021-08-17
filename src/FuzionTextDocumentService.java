import java.util.List;
import java.util.Random;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

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

  private static final PrintStream DEV_NULL = new PrintStream(OutputStream.nullOutputStream());

  /**
   * currently open text documents and their contents
   */
  private HashMap<String, String> textDocuments = new HashMap<String, String>();

  private FuzionLanguageServer _fuzionLanguageServer;

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

  private void WithTextInputStream(String text, Runnable runnable) {
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

  private void WithRedirectedStdOut(Runnable runnable) {
    var out = System.out;
    try {
      System.setOut(DEV_NULL);
      runnable.run();
    } finally {
      System.setOut(out);
    }
  }

  // https://www.baeldung.com/java-random-string
  public String randomString() {
    int leftLimit = 97; // letter 'a'
    int rightLimit = 122; // letter 'z'
    int targetStringLength = 10;
    Random random = new Random();

    return random.ints(leftLimit, rightLimit + 1).limit(targetStringLength)
        .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
  }

  public void sendDiagnostics(String uri, String text) {
    // TODO move this
    this.textDocuments.put(uri, text);

    Errors.clear();

    File tempFile = writeToTempFile(text);

    WithRedirectedStdOut(() -> {
      var frontEndOptions = new FrontEndOptions(0, new dev.flang.util.List<>(), 0, false, false,
          tempFile.getAbsolutePath());
      System.out.println(tempFile.getAbsolutePath());
      var mir = new FrontEnd(frontEndOptions).createMIR();
    });

    if (Errors.count() > 0) {
      var diagnostics = getDiagnostics(text);

      var param = new PublishDiagnosticsParams(uri, diagnostics);
      _fuzionLanguageServer.getClient().publishDiagnostics(param);
    }
  }

  private File writeToTempFile(String text) {
    try {
      File tempFile = File.createTempFile(randomString(), ".fz");
      tempFile.deleteOnExit();

      FileWriter writer = new FileWriter(tempFile);
      writer.write(text);
      writer.close();
      return tempFile;
    } catch (IOException e) {
      System.exit(1);
      return null;
    }
  }

  private List<Diagnostic> getDiagnostics(String text) {
    return Errors.get().stream().map((error) -> {
      var start_line = error.pos._line - 1;
      var start_character = error.pos._column - 1;
      var start_Position = new Position(start_line, start_character);
      var end_Position = new Position(start_line, getEndCharaterPositioin(text, start_line, start_character));
      var message = error.msg + System.lineSeparator() + error.detail;
      return new Diagnostic(new Range(start_Position, end_Position), message);
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
