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
import org.eclipse.lsp4j.Range;

import dev.flang.ast.AbstractFeature;
import dev.flang.lsp.server.Config;
import dev.flang.lsp.server.util.Bridge;
import dev.flang.lsp.server.util.LSP4jUtils;
import dev.flang.shared.ASTWalker;
import dev.flang.shared.FeatureTool;
import dev.flang.shared.LexerTool;
import dev.flang.shared.ParserTool;
import dev.flang.shared.QueryAST;
import dev.flang.shared.Util;

/**
 * provide diagnostics for a given uri
 * https://microsoft.github.io/language-server-protocol/specification#textDocument_publishDiagnostics
 */
public enum Diagnostics
{
  nameingRefs, nameingFeatures, nameingTypeParams, duplicateName, errors, warnings, unusedFeatures;

  public static void publishDiagnostics(URI uri)
  {
    var diagnostics =
      new PublishDiagnosticsParams(uri.toString(), getDiagnostics(uri).collect(Collectors.toList()));
    Config.languageClient().publishDiagnostics(diagnostics);
  }

  public static Stream<Diagnostic> getDiagnostics(URI uri)
  {
    // NYI check names of type arguments
    return Util.ConcatStreams(
      Errors(uri),
      Warnings(uri),
      Unused(uri),
      NamingFeatures(uri),
      NamingRefs(uri),
      NamingTypeParams(uri),
      DuplicateName(uri));
  }

  private static Stream<Diagnostic> DuplicateName(URI uri)
  {
    var usedNames = new HashMap<String, String>();
    var featuresReusingNames = new HashSet<AbstractFeature>();
    QueryAST.SelfAndDescendants(uri)
      .forEach(x -> {
        var ident = x.qualifiedName() + x.featureName().argCount();
        if (usedNames.containsKey(ident) &&
          x.outer().qualifiedName().contains(usedNames.get(ident)) &&
          !FeatureTool.IsArgument(x))
          {
            featuresReusingNames.add(x);
          }
        else
          {
            usedNames.put(ident, x.outer().qualifiedName());
          }
      });
    return featuresReusingNames.stream().map(f -> {
      return Create(Bridge.ToRangeBaseName(f),
        "name reuse detected.",
        DiagnosticSeverity.Information, duplicateName);
    });
  }

  private static Stream<Diagnostic> Errors(URI uri)
  {
    var errorDiagnostics =
      ParserTool.Errors(uri).filter(error -> ParserTool.getUri(error.pos).equals(uri)).map((error) -> {
        var message = error.msg + System.lineSeparator() + error.detail;
        return Create(LSP4jUtils.Range(LexerTool.TokensAt(error.pos).right()), message,
          DiagnosticSeverity.Error,
          errors);
      });
    return errorDiagnostics;
  }

  private static Stream<Diagnostic> Warnings(URI uri)
  {
    var warningDiagnostics =
      ParserTool.Warnings(uri).filter(warning -> ParserTool.getUri(warning.pos).equals(uri)).map((warning) -> {
        var message = warning.msg + System.lineSeparator() + warning.detail;
        return Create(LSP4jUtils.Range(LexerTool.TokensAt(warning.pos).right()), message,
          DiagnosticSeverity.Warning, warnings);
      });
    return warningDiagnostics;
  }

  private static Stream<Diagnostic> NamingRefs(URI uri)
  {
    return QueryAST.SelfAndDescendants(uri)
      .filter(f -> !f.isTypeParameter())
      .filter(f -> (f.isOuterRef() || f.isThisRef()) && !f.isField())
      .filter(f -> {
        var basename = f.featureName().baseName();
        var splittedBaseName = basename.split("_");
        return
        // any lowercase after _
        Arrays.stream(splittedBaseName).anyMatch(str -> Character.isLowerCase(str.codePointAt(0)))
          // any uppercase after first char
          || Arrays.stream(splittedBaseName)
            .anyMatch(str -> !str.isEmpty()
              && str.substring(1).codePoints().anyMatch(c -> Character.isUpperCase(c)));
      })
      .map(f -> {
        return Create(Bridge.ToRangeBaseName(f),
          "use Snake_Pascal_Case for refs, check: https://flang.dev/design/identifiers",
          DiagnosticSeverity.Information, nameingRefs);
      });
  }

  private static Stream<Diagnostic> NamingFeatures(URI uri)
  {
    var snakeCase = QueryAST.SelfAndDescendants(uri)
      .filter(f -> !f.isTypeParameter())
      .filter(f -> !(f.isOuterRef() || f.isThisRef()) || f.isField())
      .filter(f -> {
        var basename = f.featureName().baseName();
        return
        // any uppercase
        basename.codePoints().anyMatch(c -> Character.isUpperCase(c));
      })
      .map(f -> {
        return Create(Bridge.ToRangeBaseName(f),
          "use snake_case for features and value types, check: https://flang.dev/design/identifiers",
          DiagnosticSeverity.Information, nameingFeatures);
      });
    return snakeCase;
  }

  private static Stream<Diagnostic> NamingTypeParams(URI uri)
  {
    var uppercase = QueryAST.SelfAndDescendants(uri)
      .filter(f -> f.isTypeParameter())
      .filter(f -> {
        var basename = f.featureName().baseName();
        return basename.codePoints().anyMatch(c -> Character.isLowerCase(c));
      })
      .map(f -> {
        return Create(Bridge.ToRangeBaseName(f),
          "use UPPERCASE for type parameters, check: https://flang.dev/design/identifiers",
          DiagnosticSeverity.Information, Diagnostics.nameingTypeParams);
      });
    return uppercase;
  }


  private static Diagnostic Create(Range range, String msg, DiagnosticSeverity diagnosticSeverity, Diagnostics d)
  {
    var diagnostic = new Diagnostic(range, msg,
      diagnosticSeverity, "fuzion language server");
    diagnostic.setCode(d.ordinal());
    return diagnostic;
  }


  /**
   * diagnostics for unused features
   * @param uri
   * @return
   */
  private static Stream<Diagnostic> Unused(URI uri)
  {
    if (Util.IsStdLib(uri))
      {
        return Stream.empty();
      }
    return ParserTool.TopLevelFeatures(uri)
      .flatMap(main -> {
        var calledFeatures = ASTWalker.Calls(main)
          .map(x -> x.getKey().calledFeature())
          .collect(Collectors.toSet());

        var unused = FeatureTool
          .SelfAndDescendants(main)
          .filter(f -> !f.isTypeParameter() // NYI unused type parameter
            && !f.isAbstract()
            && !calledFeatures.contains(f)
            && !f.equals(main)
            && !f.featureName().baseName().equals("result")
        // NYI in this case we would need to do more work to
        // know if feature is used.
            && f.redefines().isEmpty())
          .collect(Collectors.toList());

        var unusedDiagnostics = unused.stream().map(f -> {
          var diagnostic =
            Create(Bridge.ToRangeBaseName(f), "unused", DiagnosticSeverity.Hint, unusedFeatures);
          diagnostic.setTags(List.of(DiagnosticTag.Unnecessary));
          return diagnostic;
        });

        return unusedDiagnostics;
      });
  }
}
