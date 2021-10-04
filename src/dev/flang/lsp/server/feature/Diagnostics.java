package dev.flang.lsp.server.feature;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;

import dev.flang.lsp.server.Converters;
import dev.flang.lsp.server.FuzionHelpers;
import dev.flang.lsp.server.Log;
import dev.flang.lsp.server.Main;
import dev.flang.lsp.server.ParserHelper;
import dev.flang.util.Errors;

/**
 * provide diagnostics for a given uri
 * https://microsoft.github.io/language-server-protocol/specification#textDocument_publishDiagnostics
 */
public class Diagnostics
{

  public static void publishDiagnostics(String uri)
  {
    var diagnostics = new PublishDiagnosticsParams(uri, getDiagnostics(uri).collect(Collectors.toList()));
    Log.message("publishing diagnostics: " + diagnostics.getDiagnostics().size());
    Main.getLanguageClient().publishDiagnostics(diagnostics);
  }

  private static Stream<Diagnostic> getDiagnostics(String uri)
  {
    var errorDiagnostics =
      Errors.errors().stream().filter(error -> ParserHelper.getUri(error.pos).equals(uri)).map((error) -> {
        var start = Converters.ToPosition(error.pos);
        var end = FuzionHelpers.endOfToken(uri, start);
        var message = error.msg + System.lineSeparator() + error.detail;
        return new Diagnostic(new Range(start, end), message, DiagnosticSeverity.Error, "fuzion language server");
      });

    var warningDiagnostics =
      Errors.warnings().stream().filter(error -> ParserHelper.getUri(error.pos).equals(uri)).map((error) -> {
        var start = Converters.ToPosition(error.pos);
        var end = FuzionHelpers.endOfToken(uri, start);
        var message = error.msg + System.lineSeparator() + error.detail;
        return new Diagnostic(new Range(start, end), message, DiagnosticSeverity.Warning, "fuzion language server");
      });

    return Stream.concat(errorDiagnostics, warningDiagnostics);
  }
}
