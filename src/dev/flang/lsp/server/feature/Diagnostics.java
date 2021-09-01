package dev.flang.lsp.server.feature;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.stream.Collectors;
import java.util.List;
import java.util.ArrayList;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.Position;

import dev.flang.util.Errors;
import dev.flang.ast.FeatureName;
import dev.flang.ast.Types;
import dev.flang.fe.FrontEnd;
import dev.flang.fe.FrontEndOptions;
import dev.flang.lsp.server.Memory;
import dev.flang.lsp.server.Util;

public class Diagnostics
{

  private static int getEndCharaterPositioin(String text, int line_number, int character_position)
  {
    var line_text = text.split("\n")[line_number];
    while (line_text.length() > character_position && line_text.charAt(character_position) != ' ')
      {
        character_position++;
      }
    return character_position;
  }

  public static PublishDiagnosticsParams getPublishDiagnosticsParams(String uri, String text)
  {
    // NYI remove once we can create MIR multiple times
    Errors.clear();
    Types.clear();
    FeatureName.clear();

    File tempFile;
    try
      {
        tempFile = new File(new URI(uri));
      }
    catch (URISyntaxException e)
      {
        System.exit(1);
        return new PublishDiagnosticsParams(uri, new ArrayList<Diagnostic>());
      }

    Util.WithRedirectedStdOut(() -> {
      var frontEndOptions =
          new FrontEndOptions(0, new dev.flang.util.List<>(), 0, false, false, tempFile.getAbsolutePath());
      var universe = new FrontEnd(frontEndOptions).createMIR().main();
      Memory.Uri2Universe.put(uri, universe);
    });

    if (Errors.count() > 0)
      {
        var diagnostics = getDiagnostics(text);

        return new PublishDiagnosticsParams(uri, diagnostics);
      }

    return new PublishDiagnosticsParams(uri, new ArrayList<Diagnostic>());
  }

  private static List<Diagnostic> getDiagnostics(String text)
  {
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
