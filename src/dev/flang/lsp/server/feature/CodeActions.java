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
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import dev.flang.lsp.server.util.LSP4jUtils;
import dev.flang.lsp.server.util.QueryAST;
import dev.flang.shared.Converter;

public class CodeActions
{

  public static List<Either<Command, CodeAction>> getCodeActions(CodeActionParams params)
  {
    return Stream.of(
      NameingFixes(params, Diagnostics.nameingFeatures, oldName -> Converter.ToSnakeCase(oldName)),
      NameingFixes(params, Diagnostics.nameingRefs, oldName -> Converter.ToSnakePascalCase(oldName)),
      NameingFixes(params, Diagnostics.nameingTypeParams, oldName -> oldName.toUpperCase()))
      .reduce(Stream::concat)
      .orElseGet(Stream::empty)
      .collect(Collectors.toList());
  }

  private static Stream<Diagnostic> getDiagnostics(CodeActionParams params, Diagnostics diag)
  {
    return params
      .getContext()
      .getDiagnostics()
      .stream()
      .filter(x -> x.getCode().isRight() && x.getCode().getRight().equals(diag.ordinal()));
  }

  private static Stream<Either<Command, CodeAction>> NameingFixes(CodeActionParams params, Diagnostics diag,
    Function<String, String> fix)
  {
    var uri = LSP4jUtils.getUri(params.getTextDocument());
    return getDiagnostics(params, diag)
      .map(d -> CodeActionForNameingIssue(uri, d, fix))
      .<Either<Command, CodeAction>>map(
        ca -> Either.forRight(ca));
  }

  /**
   * if renameing of identifier is possible
   * return code action for fixing identifier name
   *
   * @param uri
   * @param d
   * @param convertIdentifier
   * @return
   */
  private static CodeAction CodeActionForNameingIssue(URI uri, Diagnostic d, Function<String, String> convertIdentifier)
  {
    var textDocumentPosition =
      new TextDocumentPositionParams(LSP4jUtils.TextDocumentIdentifier(uri), d.getRange().getStart());
    var oldName = QueryAST
      .FeatureAt(textDocumentPosition)
      .get()
      .featureName()
      .baseName();

    var res = new CodeAction();
    res.setTitle("fix identifier");
    res.setKind(CodeActionKind.QuickFix);
    res.setDiagnostics(List.of(d));
    res.setCommand(Commands.Create(Commands.codeActionFixIdentifier, uri,
      List.of(d.getRange().getStart().getLine(), d.getRange().getStart().getCharacter(),
        convertIdentifier.apply(oldName))));

    return res;
  }

}
