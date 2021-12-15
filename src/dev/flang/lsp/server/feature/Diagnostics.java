/*

This file is part of the Fuzion language server protocol implementation.

The Fuzion language server protocol implementation is free software: you can redistribute it
and/or modify it under the terms of the GNU General Public License as published
by the Free Software Foundation, version 3 of the License.

The Fuzion language server protocol implementation is distributed in the hope that it will be
useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public
License for more details.

You should have received a copy of the GNU General Public License along with The
Fuzion language implementation.  If not, see <https://www.gnu.org/licenses/>.

*/

/*-----------------------------------------------------------------------
 *
 * Tokiwa Software GmbH, Germany
 *
 * Source of class Diagnostics
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.server.feature;

import java.net.URI;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.PublishDiagnosticsParams;

import dev.flang.lsp.server.Config;
import dev.flang.lsp.server.util.FuzionLexer;
import dev.flang.lsp.server.util.FuzionParser;
import dev.flang.lsp.server.util.Log;

/**
 * provide diagnostics for a given uri
 * https://microsoft.github.io/language-server-protocol/specification#textDocument_publishDiagnostics
 */
public class Diagnostics
{

  public static void publishDiagnostics(URI uri)
  {
    var diagnostics = new PublishDiagnosticsParams(uri.toString(), getDiagnostics(uri).collect(Collectors.toList()));
    Log.message("publishing diagnostics: " + diagnostics.getDiagnostics().size());
    Config.languageClient().publishDiagnostics(diagnostics);
  }

  private static Stream<Diagnostic> getDiagnostics(URI uri)
  {
    var errorDiagnostics = FuzionParser.Errors(uri).filter(error -> FuzionParser.getUri(error.pos).equals(uri)).map((error) -> {
        var message = error.msg + System.lineSeparator() + error.detail;
        return new Diagnostic(FuzionLexer.rawTokenAt(error.pos).toRange(), message, DiagnosticSeverity.Error, "fuzion language server");
      });

    var warningDiagnostics = FuzionParser.Warnings(uri).filter(warning -> FuzionParser.getUri(warning.pos).equals(uri)).map((warning) -> {
        var message = warning.msg + System.lineSeparator() + warning.detail;
        return new Diagnostic(FuzionLexer.rawTokenAt(warning.pos).toRange(), message, DiagnosticSeverity.Warning, "fuzion language server");
      });

    return Stream.concat(errorDiagnostics, warningDiagnostics);
  }
}
