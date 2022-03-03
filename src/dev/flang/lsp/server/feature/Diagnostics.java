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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DiagnosticTag;
import org.eclipse.lsp4j.PublishDiagnosticsParams;

import dev.flang.ast.AbstractFeature;
import dev.flang.lsp.server.Config;
import dev.flang.lsp.server.util.Bridge;
import dev.flang.lsp.server.util.LSP4jUtils;
import dev.flang.lsp.server.util.QueryAST;
import dev.flang.shared.ASTWalker;
import dev.flang.shared.FeatureTool;
import dev.flang.shared.FuzionLexer;
import dev.flang.shared.FuzionParser;
import dev.flang.shared.Util;

/**
 * provide diagnostics for a given uri
 * https://microsoft.github.io/language-server-protocol/specification#textDocument_publishDiagnostics
 */
public class Diagnostics
{

  public static void publishDiagnostics(URI uri)
  {
    var diagnostics =
      new PublishDiagnosticsParams(uri.toString(), getDiagnostics(uri).collect(Collectors.toList()));
    Config.languageClient().publishDiagnostics(diagnostics);
  }

  public static Stream<Diagnostic> getDiagnostics(URI uri)
  {
    // NYI check names of type arguments
    return Stream.of(Errors(uri), Warnings(uri), Unused(uri), NamingFeatures(uri), NamingRefs(uri), DuplicateName(uri))
      .reduce(Stream::concat)
      .orElseGet(Stream::empty);
  }

  private static Stream<Diagnostic> DuplicateName(URI uri)
  {
    var usedNames = new HashMap<String, String>();
    var featuresReusingNames = new HashSet<AbstractFeature>();
    QueryAST.SelfAndDescendants(uri)
      .forEach(x -> {
        var ident = x.featureName().baseName() + x.featureName().argCount();
        if (
          usedNames.containsKey(ident) &&
          x.outer().qualifiedName().contains(usedNames.get(ident)) &&
          x.outer().isRoutine()
        )
          {
            featuresReusingNames.add(x);
          }
        else
          {
            usedNames.put(ident, x.outer().qualifiedName());
          }
      });
    return featuresReusingNames.stream().map(f -> {
      return new Diagnostic(Bridge.ToRange(f, true),
        "name reuse detected, you probably want to use `set`?",
        DiagnosticSeverity.Information, "fuzion language server");
    });
  }

  private static Stream<Diagnostic> Errors(URI uri)
  {
    var errorDiagnostics =
      FuzionParser.Errors(uri).filter(error -> FuzionParser.getUri(error.pos).equals(uri)).map((error) -> {
        var message = error.msg + System.lineSeparator() + error.detail;
        return new Diagnostic(LSP4jUtils.Range(FuzionLexer.rawTokenAt(error.pos)), message, DiagnosticSeverity.Error,
          "fuzion language server");
      });
    return errorDiagnostics;
  }

  private static Stream<Diagnostic> Warnings(URI uri)
  {
    var warningDiagnostics =
      FuzionParser.Warnings(uri).filter(warning -> FuzionParser.getUri(warning.pos).equals(uri)).map((warning) -> {
        var message = warning.msg + System.lineSeparator() + warning.detail;
        return new Diagnostic(LSP4jUtils.Range(FuzionLexer.rawTokenAt(warning.pos)), message,
          DiagnosticSeverity.Warning, "fuzion language server");
      });
    return warningDiagnostics;
  }

  private static Stream<Diagnostic> NamingRefs(URI uri)
  {
    return QueryAST.SelfAndDescendants(uri)
      .filter(f -> f.resultType().isRef() && !f.isField())
      .filter(f -> {
        var basename = f.featureName().baseName();
        var splittedBaseName = basename.split("_");
        return
        // any lowercase after _
        Arrays.stream(splittedBaseName).anyMatch(str -> Character.isLowerCase(str.codePointAt(0)))
          // any uppercase after first char
          || Arrays.stream(splittedBaseName)
            .anyMatch(str -> !str.isEmpty()
              && str.substring(1).chars().mapToObj(i -> (char) i).anyMatch(c -> Character.isUpperCase(c)));
      })
      .map(f -> {
        return new Diagnostic(Bridge.ToRange(f, true),
          "use Snake_Pascal_Case for refs, check: https://flang.dev/design/identifiers",
          DiagnosticSeverity.Information, "fuzion language server");
      });
  }

  private static Stream<Diagnostic> NamingFeatures(URI uri)
  {
    var snakeCase = QueryAST.SelfAndDescendants(uri)
      .filter(f -> !f.resultType().isRef() || f.isField())
      .filter(f -> {
        var basename = f.featureName().baseName();
        return
        // any uppercase
        basename.chars().mapToObj(i -> (char) i).anyMatch(c -> Character.isUpperCase(c));
      })
      .map(f -> {
        return new Diagnostic(Bridge.ToRange(f, true), "use snake_case, check: https://flang.dev/design/identifiers",
          DiagnosticSeverity.Information, "fuzion language server");
      });
    return snakeCase;
  }

  /**
   * diagnostics for unused features
   * @param uri
   * @return
   */
  private static Stream<Diagnostic> Unused(URI uri)
  {
    if(Util.IsStdLib(uri)){
      return Stream.empty();
    }
    var main = FuzionParser.Main(uri);
    var calledFeatures = ASTWalker.Calls(main)
      .map(x -> x.getKey().calledFeature())
      .collect(Collectors.toSet());

    var unusedFeatures = FeatureTool
      .SelfAndDescendants(main)
      // NYI: workaround for: #result is only used for features using type
      // inference
      // for features using is the name is result even when unused
      .filter(f -> !FeatureTool.IsInternal(f))
      .filter(f -> !calledFeatures.contains(f)
        && !f.equals(main))
      .collect(Collectors.toList());

    var unusedDiagnostics = unusedFeatures.stream().map(f -> {
      var diagnostic =
        new Diagnostic(Bridge.ToRange(f, true), "unused", DiagnosticSeverity.Hint, "fuzion language server");
      diagnostic.setTags(List.of(DiagnosticTag.Unnecessary));
      return diagnostic;
    });

    return unusedDiagnostics;
  }
}
