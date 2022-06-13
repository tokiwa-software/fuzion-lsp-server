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

import org.eclipse.lsp4j.PrepareRenameResult;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
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
import dev.flang.shared.LexerTool;
import dev.flang.shared.ParserTool;
import dev.flang.shared.Util;
import dev.flang.util.ANY;
import dev.flang.util.SourcePosition;

/**
 * for rename request return all appropriate TextEdits
 * https://microsoft.github.io/language-server-protocol/specification#textDocument_rename
 * https://microsoft.github.io/language-server-protocol/specification#textDocument_prepareRename
 */
public class Rename extends ANY
{

  public static WorkspaceEdit getWorkspaceEdit(RenameParams params) throws ResponseErrorException
  {
    var res = getWorkspaceEditsOrError(params, params.getNewName());
    if (res.isLeft())
      {
        return res.getLeft();
      }
    throw res.getRight();
  }


  // NYI check for name collisions?
  public static Either<WorkspaceEdit, ResponseErrorException> getWorkspaceEditsOrError(TextDocumentPositionParams params, String newName)
  {
    if (!LexerTool.IsValidIdentifier(newName))
      {
        var responseError = new ResponseError(ResponseErrorCode.InvalidParams, "new name no valid identifier.", null);
        return Either.forRight(new ResponseErrorException(responseError));
      }

    var feature = QueryAST.FeatureAt(params);
    if (feature.isEmpty())
      {
        var responseError = new ResponseError(ResponseErrorCode.InvalidRequest, "nothing found for renaming.", null);
        return Either.forRight(new ResponseErrorException(responseError));
      }

    Stream<SourcePosition> renamePositions = getRenamePositions(params, feature.get());

    var changes = renamePositions
      .map(start -> {
        var end =
          new SourcePosition(start._sourceFile, start._line, start._column + LengthOfFeatureIdentifier(feature.get()));
        return Bridge.ToLocation(start, end);
      })
      .map(location -> new SimpleEntry<String, TextEdit>(location.getUri(),
        new TextEdit(location.getRange(), newName)))
      .collect(Collectors.groupingBy(e -> e.getKey(), Collectors.mapping(e -> e.getValue(), Collectors.toList())));

    return Either.forLeft(new WorkspaceEdit(changes));
  }


  /**
   *
   * @param params
   * @param featureToRename
   * @param featureIdentifier
   * @return stream of sourcepositions where renamings must be done
   */
  private static Stream<SourcePosition> getRenamePositions(TextDocumentPositionParams params, AbstractFeature featureToRename)
  {
    var callsSourcePositions = FeatureTool
      .CallsTo(featureToRename)
      .map(entry -> entry.getKey().pos())
      .map(pos -> {
        if (IsAtFunKeyword(pos))
          {
            var whitespace =
              new SourcePosition(pos._sourceFile, pos._line, pos._column + Lexer.Token.t_fun.toString().length());
            pos = LexerTool.NextTokenOfType(whitespace, Util.ArrayToSet(new Token[]
              {
                  Token.t_ident
              })).start();
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
        var whitespace = LexerTool.TokensAt(LexerTool.EndOfToken(f.pos()), true).right();
        if (CHECKS)
          check(whitespace.token() == Token.t_ws);
        return new SourcePosition(f.pos()._sourceFile, f.pos()._line,
          f.pos()._column + f.featureName().baseName().length() + whitespace.text().length());
      });

    // NYI we don't have correct position of args of fun in the AST yet
    // special case for renaming lamdba args
    if (featureToRename.outer() != null &&
      featureToRename.outer().outer() != null &&
      featureToRename.outer().outer().featureName().baseName().startsWith("#fun"))
      {
        // last Token before start of #fun.call where tokenText matches baseName
        var tokenPos = LexerTool
          .TokensFrom(new SourcePosition(pos._sourceFile, 1, 1), false)
          .filter(x -> featureToRename.featureName().baseName().equals(x.text()))
          .filter(x -> {
            return x.start()
              .compareTo(new SourcePosition(x.start()._sourceFile,
                featureToRename.pos()._line, featureToRename.pos()._column)) < 0;
          })
          .reduce(null, (r, x) -> x)
          .start();

        pos =
          new SourcePosition(pos._sourceFile, tokenPos._line, tokenPos._column);
      }

    var assignmentPositions = ASTWalker
      .Assignments(featureToRename.outer(), featureToRename)
      .map(x -> x.getKey().pos())
      .filter(x -> !x.pos().equals(featureToRename.pos()))
      .map(x -> {
        // NYI better we be if we had the needed and more correct info directly
        // in the AST
        var set =
          LexerTool.NextTokenOfType(new SourcePosition(x._sourceFile, x._line, 1), Util.ArrayToSet(new Token[]
          {
              Token.t_set
          }));
        var whitespace =
          LexerTool.TokensAt(new SourcePosition(x._sourceFile, set.end()._line, set.end()._column), true).right();
        if (CHECKS)
          check(whitespace.token() == Token.t_ws);
        return whitespace.end();
      });


    var choiceGenerics = ASTWalker
      .Traverse(ParserTool.TopLevelFeatures(LSP4jUtils.getUri(params)))
      .filter(entry -> entry.getKey() instanceof AbstractFeature)
      .map(entry -> (AbstractFeature) entry.getKey())
      .filter(f -> f.resultType().isChoice())
      .filter(f -> {
        return f.resultType().choiceGenerics().stream().anyMatch(t -> {
          return t.name().equals(featureToRename.featureName().baseName());
        });
      })
      .map(f -> PositionOfChoiceGeneric(featureToRename.featureName().baseName(), f));

    return Stream.of(callsSourcePositions, typePositions, Stream.of(pos), assignmentPositions, choiceGenerics)
      .reduce(Stream::concat)
      .orElseGet(Stream::empty);
  }

  private static SourcePosition PositionOfChoiceGeneric(String name, AbstractFeature f)
  {
    return LexerTool
      .TokensFrom(new SourcePosition(f.pos()._sourceFile, 1, 1), false)
      .filter(token -> name.equals(token.text()))
      .filter(token -> {
        return token.start()
          .compareTo(new SourcePosition(token.start()._sourceFile, f.pos()._line, f.pos()._column)) > 0;
      })
      .findFirst()
      .get()
      .start();
  }

  private static int LengthOfFeatureIdentifier(AbstractFeature feature)
  {
    return Arrays.stream(feature.featureName().baseName().split(" "))
      .map(str -> str.length())
      .reduce(0, (acc, item) -> item);
  }

  // NYI disallow renaming of stdlib
  public static PrepareRenameResult getPrepareRenameResult(TextDocumentPositionParams params)
  {
    var featureAt = QueryAST.FeatureAt(params);
    if (featureAt.isEmpty())
      {
        return new PrepareRenameResult();
      }

    return LexerTool.IdentOrOperatorTokenAt(Bridge.ToSourcePosition(params))
      .map(token -> {
        return new PrepareRenameResult(LSP4jUtils.Range(token), token.text());
      })
      .orElse(new PrepareRenameResult());
  }


  private static boolean IsAtFunKeyword(SourcePosition params)
  {
    return LexerTool.TokensAt(params, false).right().token() == Lexer.Token.t_fun;
  }

}
