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
 * Source of class Rename
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.server.feature;

import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PrepareRenameResult;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;

import dev.flang.ast.AbstractFeature;
import dev.flang.lsp.server.util.Bridge;
import dev.flang.lsp.server.util.LSP4jUtils;
import dev.flang.lsp.server.util.QueryAST;
import dev.flang.parser.Lexer;
import dev.flang.parser.Lexer.Token;
import dev.flang.shared.ASTWalker;
import dev.flang.shared.FeatureTool;
import dev.flang.shared.FuzionLexer;
import dev.flang.shared.Util;
import dev.flang.util.SourcePosition;

/**
 * for rename request return all appropriate TextEdits
 * https://microsoft.github.io/language-server-protocol/specification#textDocument_rename
 * https://microsoft.github.io/language-server-protocol/specification#textDocument_prepareRename
 */
public class Rename
{

  // NYI check for name collisions?
  public static WorkspaceEdit getWorkspaceEdit(RenameParams params)
  {
    if (!FuzionLexer.IsValidIdentifier(params.getNewName()))
      {
        var responseError = new ResponseError(ResponseErrorCode.InvalidParams, "new name no valid identifier.", null);
        throw new ResponseErrorException(responseError);
      }

    var feature = QueryAST.FeatureAt(params);
    if (feature.isEmpty())
      {
        var responseError = new ResponseError(ResponseErrorCode.InvalidRequest, "nothing found for renaming.", null);
        throw new ResponseErrorException(responseError);
      }

    Stream<SourcePosition> renamePositions = getRenamePositions(feature.get());

    var changes = renamePositions
      .map(sourcePosition -> Bridge.ToLocation(sourcePosition))
      .map(location -> new SimpleEntry<String, TextEdit>(location.getUri(),
        getTextEdit(location, LengthOfFeatureIdentifier(feature.get()), params.getNewName())))
      .collect(Collectors.groupingBy(e -> e.getKey(), Collectors.mapping(e -> e.getValue(), Collectors.toList())));

    return new WorkspaceEdit(changes);
  }

  /**
   *
   * @param featureToRename
   * @param featureIdentifier
   * @return stream of sourcepositions where renamings must be done
   */
  private static Stream<SourcePosition> getRenamePositions(AbstractFeature featureToRename)
  {
    var callsSourcePositions = FeatureTool
      .CallsTo(featureToRename)
      .map(entry -> entry.getKey().pos())
      .map(pos -> {
        if (IsAtFunKeyword(pos))
          {
            var nextPosition =
              new SourcePosition(pos._sourceFile, pos._line, pos._column + Lexer.Token.t_fun.toString().length());
            pos = FuzionLexer.tokenAt(nextPosition).start();
          }
        return pos;
      });
    var pos = featureToRename.pos();

    // positions where feature is used as type
    var typePositions = FeatureTool.SelfAndDescendants(featureToRename.universe())
      .filter(f -> !f.equals(featureToRename) && !f.resultType().isGenericArgument()
        && f.resultType().featureOfType().equals(featureToRename))
      .map(f -> {
        // NYI we need correct position of type here
        var whitespace = FuzionLexer.rawTokenAt(FuzionLexer.endOfToken(f.pos()));
        return new SourcePosition(f.pos()._sourceFile, f.pos()._line,
          f.pos()._column + f.featureName().baseName().length() + whitespace.text().length());
      });

    // special case for renaming lamdba args
    if (featureToRename.outer() != null &&
      featureToRename.outer().outer() != null &&
      featureToRename.outer().outer().featureName().baseName().startsWith("#fun"))
      {
        pos =
          new SourcePosition(pos._sourceFile, pos._line, pos._column - LengthOfFeatureIdentifier(featureToRename) - 1);
      }

    var assignmentPositions = ASTWalker
      .Assignments(featureToRename.outer(), featureToRename)
      .map(x -> x.getKey().pos())
      .filter(x -> !x.pos().equals(featureToRename.pos()))
      .map(x -> {
        // NYI better we be if we had the needed and more correct info directly
        // in the AST
        var set =
          FuzionLexer.nextTokenOfType(new SourcePosition(x._sourceFile, x._line, 1), Util.HashSetOf(Token.t_set));
        var whitespace =
          FuzionLexer.rawTokenAt(new SourcePosition(x._sourceFile, set.end()._line, set.end()._column));
        return whitespace.end();
      });

    return Stream.of(callsSourcePositions, typePositions, Stream.of(pos), assignmentPositions)
      .reduce(Stream::concat)
      .orElseGet(Stream::empty);
  }

  private static int LengthOfFeatureIdentifier(AbstractFeature feature)
  {
    return Arrays.stream(feature.featureName().baseName().split(" "))
      .map(str -> str.length())
      .reduce(0, (acc, item) -> item);
  }

  private static TextEdit getTextEdit(Location location, int lengthOfOldToken, String newText)
  {
    var startPos = location.getRange().getStart();
    var endPos = new Position(startPos.getLine(), startPos.getCharacter() + lengthOfOldToken);
    var result = new TextEdit(new Range(startPos, endPos), newText);
    return result;
  }

  // NYI disallow renaming of stdlib
  public static PrepareRenameResult getPrepareRenameResult(TextDocumentPositionParams params)
  {
    var featureAt = QueryAST.FeatureAt(params);
    if (featureAt.isEmpty())
      {
        return new PrepareRenameResult();
      }

    return FuzionLexer.IdentifierTokenAt(params)
      .map(token -> {
        return new PrepareRenameResult(LSP4jUtils.Range(token), token.text());
      })
      .orElse(new PrepareRenameResult());
  }


  private static boolean IsAtFunKeyword(SourcePosition params)
  {
    return FuzionLexer.tokenAt(params).token() == Lexer.Token.t_fun;
  }

}
