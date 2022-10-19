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
 * Source of class QueryAST
 *
 *---------------------------------------------------------------------*/

package dev.flang.shared;

import java.net.URI;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import dev.flang.ast.AbstractCall;
import dev.flang.ast.AbstractConstant;
import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AbstractType;
import dev.flang.ast.Call;
import dev.flang.ast.StrConst;
import dev.flang.ast.Types;
import dev.flang.parser.Lexer.Token;
import dev.flang.util.ANY;
import dev.flang.util.HasSourcePosition;
import dev.flang.util.SourcePosition;

public class QueryAST extends ANY
{
  public static Optional<AbstractFeature> CalledFeature(SourcePosition params)
  {
    if (PRECONDITIONS)
      require(!Util.IsStdLib(SourceText.UriOf(params)));

    return CalledFeatureInAST(params)
      .or(() -> {
        // NYI this is a (bad) hack to handle incomplete source code
        // that contains something like `expr. ` where the parser than assumes
        // the dot to be a full stop but is actually just incomplete source code
        var tokens = LexerTool.TokensAt(params);
        if (tokens.left().text().equals(".") && tokens.right().token().equals(Token.t_ws))
          {
            return CalledFeatureInASTDefusedFullStop(params);
          }
        return Optional.empty();
      })
      .or(() -> Constant(params));
  }

  /**
   * try to find a called feature in AST
   * but insert dummy character after dot first
   * so parser does not parse it as full stop.
   *
   * @param params
   * @return
   */
  private static Optional<? extends AbstractFeature> CalledFeatureInASTDefusedFullStop(SourcePosition params)
  {
    var uri = SourceText.UriOf(params);
    var text = SourceText.getText(params);
    SourceText.setText(uri, InsertDummyCharacter(text, params));
    var result = CalledFeatureInAST(params);
    SourceText.setText(uri, text);
    return result;
  }

  /*
   * insert dummy character � at pos
   */
  private static String InsertDummyCharacter(String text, SourcePosition pos)
  {
    var lines = text.lines().toList();
    return IntStream
      .range(0, (int) lines.size())
      .mapToObj(x -> {
        if (x + 1 != pos._line)
          {
            return lines.get(x);
          }
        return lines.get(x).substring(0, pos._column - 1) + "�"
          + lines.get(x).substring(pos._column - 1, lines.get(x).length());
      })
      .collect(Collectors.joining(System.lineSeparator()));
  }

  // NYI motivate/explain this heuristic
  private static Optional<AbstractFeature> CalledFeatureInAST(SourcePosition params)
  {
    var leftToken = LexerTool.TokensAt(LexerTool.GoLeft(params)).left();
    return ASTWalker
      .Traverse(params)
      .filter(entry -> entry.getKey() instanceof AbstractCall)
      .filter(entry -> !entry.getValue().pos().isBuiltIn()
        && SourcePositionTool.PositionIsAfterOrAtCursor(params, ParserTool.endOfFeature(entry.getValue())))
      .filter(entry -> SourcePositionTool.PositionIsBeforeCursor(params, ((AbstractCall) entry.getKey()).pos()))
      .map(entry -> (AbstractCall) entry.getKey())
      .filter(ac -> SourcePositionTool.Compare(ExprTool.EndOfExpr(ac), params) <= 0)
      .sorted(ExprTool.CompareByEndOfExpr.reversed())
      .filter(ac -> ac.calledFeature() != null)
      .filter(CallTool.CalledFeatureNotInternal)
      // if left token is identifier, filter none matching calls by name
      .filter(ac -> !leftToken.token().equals(Token.t_ident) || !(ac instanceof Call)
        || leftToken.text().equals(((Call) ac).name))
      .map(ac -> {
        // try use infered type
        if (ac.typeForGenericsTypeInfereing() != null && !TypeTool.ContainsError(ac.typeForGenericsTypeInfereing()))
          {
            return ac.typeForGenericsTypeInfereing();
          }
        // fall back to result type
        return ac
          .calledFeature()
          .resultType();
      })
      .map(at -> {
        if (HasConstraint(at))
          {
            return at.genericArgument().constraint().featureOfType();
          }
        return at.featureOfType();
      })
      .filter(f -> !FeatureTool.IsInternal(f))
      .findFirst();
  }

  private static boolean HasConstraint(AbstractType at)
  {
    return at.isGenericArgument() && !at.genericArgument().constraint().equals(Types.resolved.t_object);
  }

  // NYI motivate/explain this heuristic
  private static Optional<? extends AbstractFeature> Constant(SourcePosition params)
  {
    return ASTWalker.Traverse(params)
      .filter(entry -> entry.getKey() instanceof AbstractConstant)
      .filter(entry -> !entry.getValue().pos().isBuiltIn()
        && SourcePositionTool.PositionIsAfterOrAtCursor(params, ParserTool.endOfFeature(entry.getValue())))
      .filter(entry -> SourcePositionTool.PositionIsBeforeCursor(params, ((AbstractConstant) entry.getKey()).pos()))
      .map(entry -> ((AbstractConstant) entry.getKey()))
      .filter(HasSourcePositionTool.IsItemOnSameLineAsCursor(params))
      .sorted(HasSourcePositionTool.CompareBySourcePosition.reversed())
      .map(x -> x.type().featureOfType())
      .findFirst();
  }

  public static Stream<AbstractFeature> CallCompletionsAt(SourcePosition params)
  {
    return CalledFeature(params)
      .map(feature -> {
        var declaredFeaturesOfInheritedFeatures =
          InheritedRecursive(feature).flatMap(c -> ParserTool.DeclaredFeatures(c.calledFeature()));

        var declaredFeatures = Stream.concat(ParserTool
          .DeclaredFeatures(feature), declaredFeaturesOfInheritedFeatures)
          .collect(Collectors.toList());

        var redefinedFeatures =
          declaredFeatures.stream().flatMap(x -> x.redefines().stream()).collect(Collectors.toSet());

        // subtract redefined features from result
        return declaredFeatures
          .stream()
          // filter infix, prefix, postfix features
          .filter(x -> !x.featureName().baseName().contains(" "))
          .filter(x -> !redefinedFeatures.contains(x));
      })
      .orElse(Stream.empty());
  }

  public static Stream<AbstractFeature> InfixPostfixCompletionsAt(SourcePosition params)
  {
    return CalledFeature(params)
      .map(feature -> {
        var declaredFeaturesOfInheritedFeatures =
          InheritedRecursive(feature).flatMap(c -> ParserTool.DeclaredFeatures(c.calledFeature()));

        var declaredFeatures = Stream.concat(ParserTool
          .DeclaredFeatures(feature), declaredFeaturesOfInheritedFeatures)
          .collect(Collectors.toList());

        var redefinedFeatures =
          declaredFeatures.stream().flatMap(x -> x.redefines().stream()).collect(Collectors.toSet());

        // subtract redefined features from result
        return declaredFeatures
          .stream()
          .filter(x -> x.featureName().baseName().startsWith("infix")
            || x.featureName().baseName().startsWith("postfix"))
          .filter(x -> !redefinedFeatures.contains(x));
      })
      .orElse(Stream.empty());
  }

  private static Stream<AbstractCall> InheritedRecursive(AbstractFeature feature)
  {
    return Stream.concat(feature.inherits().stream(),
      feature.inherits().stream().flatMap(c -> InheritedRecursive(c.calledFeature())));
  }

  public static Stream<AbstractFeature> CompletionsAt(SourcePosition params)
  {
    var tokens = LexerTool.TokensAt(params);
    return QueryAST.FeaturesInScope(params)
      .filter(f -> {
        if (tokens.left().token().equals(Token.t_ws))
          {
            return true;
          }
        if (tokens.left().token().equals(Token.t_ident))
          {
            return f.featureName().baseName().startsWith(tokens.left().text());
          }
        return false;
      });
  }

  /**
   * given a TextDocumentPosition return all matching ASTItems
   * in the given file on the given line.
   * sorted by position descending.
   * @param params
   * @return
   */
  private static Stream<HasSourcePosition> ASTItemsBeforeOrAtCursor(SourcePosition params)
  {
    return ASTWalker.Traverse(params)
      .filter(HasSourcePositionTool.IsItemInScope(params))
      .map(entry -> entry.getKey())
      .filter(HasSourcePositionTool.IsItemNotBuiltIn(params))
      .filter(HasSourcePositionTool.IsItemOnSameLineAsCursor(params))
      .sorted(HasSourcePositionTool.CompareBySourcePosition.reversed());
  }

  /**
   * returns all features declared in uri
   * @param uri
   * @return
   */
  public static Stream<AbstractFeature> SelfAndDescendants(URI uri)
  {
    return ParserTool
      .TopLevelFeatures(uri)
      .flatMap(f -> FeatureTool.SelfAndDescendants(f));
  }

  public static Optional<AbstractCall> callAt(SourcePosition params)
  {
    Optional<AbstractCall> call = ASTItemsBeforeOrAtCursor(params)
      .filter(item -> item instanceof AbstractCall)
      .map(c -> (AbstractCall) c)
      .findFirst();
    return call;
  }


  /**
   * tries to find the closest feature at given
   * position that is declared, called or used by a type
   * @param params
   */
  public static Optional<AbstractFeature> FeatureAt(SourcePosition params)
  {
    return FeatureDefinedAt(params)
      .or(() -> FindFeatureInAST(params))
      // NYI workaround for not having positions of all types in
      // the AST currently
      .or(() -> FeatureAtFuzzy(params));
  }

  private static Optional<? extends AbstractFeature> FindFeatureInAST(SourcePosition params)
  {
    var token = LexerTool.IdentOrOperatorTokenAt(params);
    return ASTItemsBeforeOrAtCursor(params)
      .map(astItem -> {
        if (astItem instanceof AbstractFeature f
          && token.<Boolean>map(x -> x.text().equals(f.featureName().baseName())).orElse(false))
          {
            return f;
          }
        if (astItem instanceof AbstractCall c)
          {
            return ErrorHandling.ResultOrDefault(() -> {
              if (token.map(t -> c.pos()._column + Util.CodepointCount(t.text()) >= params._column)
                .orElse(false)
                && !c.calledFeature().equals(Types.f_ERROR))
                {
                  return c.calledFeature();
                }
              return null;
            }, null);
          }
        return null;
      })
      .filter(f -> f != null)
      .findFirst();
  }

  /**
   * if we are somewhere here:
   *
   * infix * ...
   * ^^^^^^^
   *
   * or somewhere here:
   *
   * some_feat ... is
   * ^^^^^^^^^
   *
   * return the matching feature
   */
  private static Optional<AbstractFeature> FeatureDefinedAt(SourcePosition params)
  {
    return ASTWalker.Features(SourceText.UriOf(params))
      .filter(af -> !FeatureTool.IsArgument(af))
      // line
      .filter(x -> params._line == x.pos()._line)
      .filter(x -> {
        var start = x.pos()._column;
        // NYI should work most of the time but there might be additional
        // whitespace?
        var end = start + Util.CodepointCount(x.featureName().baseName());
        return start <= params._column && params._column <= end;
      })
      .findAny();
  }

  /**
   * Try to find feature by matching the ident token text at given position.
   * @param params
   * @return
   */
  private static Optional<AbstractFeature> FeatureAtFuzzy(SourcePosition params)
  {
    return LexerTool.IdentOrOperatorTokenAt(params)
      .flatMap(token -> QueryAST.FeaturesInScope(params)
        .filter(f -> f.featureName().baseName().equals(token.text()))
        // NYI we could be better here if we considered approximate
        // argcount
        .findFirst());
  }

  /**
   * @param params
   * @return the most inner feature at the cursor position
   */
  public static Optional<AbstractFeature> InFeature(SourcePosition params)
  {
    return SelfAndDescendants(SourceText.UriOf(params))
      .filter(f -> {
        var startOfFeature = f.pos();
        var endOfFeature = ParserTool.endOfFeature(f);
        return SourcePositionTool.Compare(params, endOfFeature) <= 0 &&
          SourcePositionTool.Compare(params, startOfFeature) > 0;
      })
      .filter(f -> f.pos()._column < params._column)
      .sorted(HasSourcePositionTool.CompareBySourcePosition.reversed())
      .findFirst();
  }

  /**
   * @param params
   * @return if text document position is inside of string
   */
  public static boolean InString(SourcePosition params)
  {
    return ASTWalker.Traverse(params)
      .filter(x -> x.getKey() instanceof StrConst)
      .map(x -> (StrConst) x.getKey())
      .anyMatch(x -> {
        if (x.pos()._line != params._line)
          {
            return false;
          }
        var start = x.pos()._column;
        var end = x.pos()._column + Util.CharCount(x._str);
        if (SourceText.LineAt(x.pos()).charAt(x.pos()._column - 1) == '\"')
          {
            return start < params._column
              && params._column - 1 <= end;
          }
        return start < params._column
          && params._column <= end;
      });
  }

  /**
   * @param feature
   * @return all features which are accessible (callable) at pos
   */
  public static Stream<AbstractFeature> FeaturesInScope(SourcePosition pos)
  {
    return InFeature(pos)
      .map(feature -> {
        return Util.ConcatStreams(
          Stream.of(feature),
          FeatureTool.OuterFeatures(feature),
          feature.inherits().stream().map(c -> c.calledFeature()))
          .filter(f -> !TypeTool.ContainsError(f.thisType()))
          .flatMap(f -> {
            return ParserTool.DeclaredFeatures(f);
          })
          .distinct();
      })
      .orElse(Stream.empty());
  }

}
