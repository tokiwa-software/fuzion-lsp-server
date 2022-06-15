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
 * Source of class CodeLenses
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.server.feature;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import dev.flang.lsp.server.util.Bridge;
import dev.flang.lsp.server.util.LSP4jUtils;
import dev.flang.lsp.server.util.QueryAST;
import dev.flang.shared.FeatureTool;

public class CodeLenses
{
  public static List<CodeLens> getCodeLenses(CodeLensParams params)
  {
    var uri = LSP4jUtils.getUri(params.getTextDocument());
    return Stream.concat(Stream.of(codeLensEvaluateFile(uri), codeLensShowSyntaxTree(uri)), codeLensesCallGraph(uri))
      .collect(Collectors.toList());
  }

  private static Stream<CodeLens> codeLensesCallGraph(URI uri)
  {
    return QueryAST.SelfAndDescendants(uri)
      .filter(f -> !(f.isField() || FeatureTool.IsArgument(f)))
      .map(f -> {
        var command =
          Commands.Create(Commands.callGraph, uri, List.of(FeatureTool.UniqueIdentifier(f)));
        return new CodeLens(Bridge.ToRange(f), command, null);
      });
  }

  private static CodeLens codeLensShowSyntaxTree(URI uri)
  {
    var command = Commands.Create(Commands.showSyntaxTree, uri, List.of());
    return new CodeLens(new Range(new Position(0, 0), new Position(0, 1)), command, null);
  }

  private static CodeLens codeLensEvaluateFile(URI uri)
  {
    Command command = Commands.Create(Commands.run, uri, List.of());
    return new CodeLens(new Range(new Position(0, 0), new Position(0, 1)),
      command,
      null);
  }

}
