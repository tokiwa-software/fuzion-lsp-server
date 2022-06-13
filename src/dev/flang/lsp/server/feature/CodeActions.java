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
 * Source of class CodeActions
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.server.feature;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import dev.flang.lsp.server.util.LSP4jUtils;
import dev.flang.lsp.server.util.QueryAST;

public class CodeActions
{

  public static List<Either<Command, CodeAction>> getCodeActions(CodeActionParams params)
  {
    var uri = LSP4jUtils.getUri(params.getTextDocument());

    // NYI codeaction for NamingRefs, NamingTypeParams
    return Diagnostics.NamingFeatures(uri)
      .map(d -> CodeActionForDiagnostic(uri, d, oldName -> ToSnakeCase(oldName)).orElse(null))
      .filter(ca -> ca != null)
      .<Either<Command, CodeAction>>map(
        ca -> Either.forRight(ca))
      .collect(Collectors.toList());
  }

  private static String ToSnakeCase(String oldName)
  {
    var a = oldName.codePoints().mapToObj(ch -> {
      if (Character.isUpperCase(ch))
        {
          return "_" + Character.toString(Character.toLowerCase(ch));
        }
      return Character.toString(ch);
    }).collect(Collectors.joining());
    if (oldName.startsWith("_"))
      {
        return a;
      }
    // strip leading underscore
    return a.replaceAll("^_", "");
  }

  private static Optional<CodeAction> CodeActionForDiagnostic(URI uri, Diagnostic d, Function<String, String> newName)
  {
    var textDocumentPosition =
      new TextDocumentPositionParams(LSP4jUtils.TextDocumentIdentifier(uri), d.getRange().getStart());
    var oldName = QueryAST
      .FeatureAt(textDocumentPosition)
      .get()
      .featureName()
      .baseName();

    return Rename
      .getWorkspaceEditsOrError(
        new TextDocumentPositionParams(textDocumentPosition.getTextDocument(), d.getRange().getStart()),
        newName.apply(oldName))
      .<Optional<CodeAction>>map(edit -> {
        var res = new CodeAction();
        res.setTitle("fix name");
        res.setKind(CodeActionKind.QuickFix);
        res.setDiagnostics(List.of(d));
        res.setEdit(edit);
        return Optional.of(res);
      }, err -> Optional.empty());

  }

}
