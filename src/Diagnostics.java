import java.io.File;
import java.util.stream.Collectors;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Consumer;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.Hover;
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

public class Diagnostics {

  private static int getEndCharaterPositioin(String text, int line_number, int character_position) {
    var line_text = text.split("\n")[line_number];
    while (line_text.length() > character_position && line_text.charAt(character_position) != ' ') {
      character_position++;
    }
    return character_position;
  }

  public static PublishDiagnosticsParams getPublishDiagnosticsParams(String uri, String text) {
    Errors.clear();

    File tempFile = Util.writeToTempFile(text);

    Util.WithRedirectedStdOut(() -> {
      var frontEndOptions = new FrontEndOptions(0, new dev.flang.util.List<>(), 0, false, false,
          tempFile.getAbsolutePath());
      System.out.println(tempFile.getAbsolutePath());
      var mir = new FrontEnd(frontEndOptions).createMIR();
    });

    if (Errors.count() > 0) {
      var diagnostics = getDiagnostics(text);

      return new PublishDiagnosticsParams(uri, diagnostics);
    }
    return new PublishDiagnosticsParams(uri, new ArrayList<Diagnostic>());
  }

  private static List<Diagnostic> getDiagnostics(String text) {
    return Errors.get().stream().map((error) -> {
      var start_line = error.pos._line - 1;
      var start_character = error.pos._column - 1;
      var start_Position = new Position(start_line, start_character);
      var end_Position = new Position(start_line, getEndCharaterPositioin(text, start_line, start_character));
      var message = error.msg + System.lineSeparator() + error.detail;
      return new Diagnostic(new Range(start_Position, end_Position), message);
    }).collect(Collectors.toList());
  }
}
