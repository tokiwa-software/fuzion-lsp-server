package dev.flang.lsp.server.feature;

import java.util.stream.Collectors;
import java.util.List;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.Position;

import dev.flang.lsp.server.FuzionHelpers;
import dev.flang.lsp.server.FuzionTextDocumentService;
import dev.flang.lsp.server.Main;
import dev.flang.lsp.server.ParserHelper;
import dev.flang.util.Errors;

/**
 * provide diagnostics for a given uri
 * https://microsoft.github.io/language-server-protocol/specification#textDocument_publishDiagnostics
 */
public class Diagnostics
{

  // NYI can we use tokenizer to get end?
  private static Position getEndPosition(String text, Position start)
  {
    var end = new Position(start.getLine(), start.getCharacter());
    var line_text = text.split("\\R", -1)[start.getLine()];
    while (line_text.length() > end.getCharacter() && line_text.charAt(end.getCharacter()) != ' ')
      {
        end.setCharacter(end.getCharacter() + 1);
      }
    return end;
  }

  public static void publishDiagnostics(String uri){
    var diagnostics = new PublishDiagnosticsParams(uri, getDiagnostics(uri));
    Main.getLanguageClient().publishDiagnostics(diagnostics);
  }

  private static List<Diagnostic> getDiagnostics(String uri)
  {
    return Errors.get().stream().filter(error -> ParserHelper.getUri(error.pos).equals(uri)).map((error) -> {
      var start = FuzionHelpers.ToPosition(error.pos);
      var text = FuzionTextDocumentService.getText(uri);
      var end = getEndPosition(text, start);
      var message = error.msg + System.lineSeparator() + error.detail;
      return new Diagnostic(new Range(start, end), message);
    }).collect(Collectors.toList());
  }
}
