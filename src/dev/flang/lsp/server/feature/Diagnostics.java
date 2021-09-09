package dev.flang.lsp.server.feature;

import java.util.stream.Collectors;
import java.util.List;
import java.util.ArrayList;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.Position;

import dev.flang.lsp.server.FuzionHelpers;
import dev.flang.lsp.server.FuzionTextDocumentService;
import dev.flang.util.Errors;

public class Diagnostics
{

  // NYI can we use tokenizer to get end?
  private static Position getEndPosition(String text, Position start)
  {
    var end = new Position(start.getLine(),start.getCharacter());
    var line_text = text.split("\n")[start.getLine()];
    while (line_text.length() > end.getCharacter() && line_text.charAt(end.getCharacter()) != ' ')
      {
        end.setCharacter(end.getCharacter() + 1);
      }
    return end;
  }

  public static PublishDiagnosticsParams getPublishDiagnosticsParams(String uri)
  {
    if (Errors.count() > 0)
      {
        var diagnostics = getDiagnostics(uri);

        return new PublishDiagnosticsParams(uri, diagnostics);
      }

    return new PublishDiagnosticsParams(uri, new ArrayList<Diagnostic>());
  }

  private static List<Diagnostic> getDiagnostics(String uri)
  {
    return Errors.get().stream().filter(error -> FuzionHelpers.toUriString(error.pos) == uri).map((error) -> {
      var start = FuzionHelpers.ToPosition(error.pos);
      var text = FuzionTextDocumentService.getText(uri);
      var end = getEndPosition(text, start);
      var message = error.msg + System.lineSeparator() + error.detail;
      return new Diagnostic(new Range(start, end), message);
    }).collect(Collectors.toList());
  }
}
