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

package dev.flang.lsp.server.util;

import java.net.URI;
import java.util.Comparator;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.lsp4j.TextDocumentPositionParams;

import dev.flang.ast.AbstractCall;
import dev.flang.ast.AbstractConstant;
import dev.flang.ast.AbstractFeature;
import dev.flang.ast.StrConst;
import dev.flang.ast.Types;
import dev.flang.shared.ASTItem;
import dev.flang.shared.ASTWalker;
import dev.flang.shared.ErrorHandling;
import dev.flang.shared.FeatureTool;
import dev.flang.shared.FuzionLexer;
import dev.flang.shared.FuzionParser;
import dev.flang.shared.SourceText;
import dev.flang.shared.Util;
import dev.flang.util.ANY;
import dev.flang.util.SourcePosition;

public class QueryAST extends ANY
{
  private static boolean PositionIsAfterOrAtCursor(TextDocumentPositionParams params, SourcePosition sourcePosition)
  {
    return LSP4jUtils.ComparePosition(LSP4jUtils.getPosition(params), Bridge.ToPosition(sourcePosition)) <= 0;
  }

  private static boolean PositionIsBeforeCursor(TextDocumentPositionParams params, SourcePosition sourcePosition)
  {
    return LSP4jUtils.ComparePosition(LSP4jUtils.getPosition(params), Bridge.ToPosition(sourcePosition)) > 0;
  }

  public static Optional<AbstractFeature> CalledFeature(TextDocumentPositionParams params)
  {
    if (PRECONDITIONS)
      require(!Util.IsStdLib(LSP4jUtils.getUri(params)));

    var universe = FuzionParser.Universe(LSP4jUtils.getUri(params));
    return ASTWalker.Traverse(universe)
      .filter(ASTItem.IsItemInFile(LSP4jUtils.getUri(params)))
      .filter(entry -> entry.getKey() instanceof AbstractCall)
      .filter(entry -> PositionIsAfterOrAtCursor(params, FuzionParser.endOfFeature(entry.getValue())))
      .filter(entry -> PositionIsBeforeCursor(params, ((AbstractCall) entry.getKey()).pos()))
      .map(entry -> (AbstractCall) entry.getKey())
      .filter(c -> LSP4jUtils.ComparePosition(Bridge.ToPosition(CallTool.endOfCall(c)), params.getPosition()) <= 0)
      .sorted(CompareBySourcePosition.reversed())
      .filter(c -> c.calledFeature() != null)
      // filter calls generated by syntactic sugaring
      .filter(c -> !c.calledFeature().qualifiedName().equals("sys"))
      .filter(c -> !c.calledFeature().qualifiedName().equals("sys.array"))
      .filter(c -> !c.calledFeature().qualifiedName().equals("sys.array.index [ ] ="))
      .map(c -> c.calledFeature())
      .filter(f -> !FeatureTool.IsInternal(f))
      // NYI in this case we could try to find possibly called features?
      .filter(f -> f.resultType() != Types.t_ERROR)
      .findFirst()
      .or(() -> Constant(params, universe));
  }

  private static Optional<? extends AbstractFeature> Constant(TextDocumentPositionParams params,
    AbstractFeature universe)
  {
    var pos = FuzionLexer.GoBackInLine(Bridge.ToSourcePosition(params), 2);
    if (pos.isEmpty())
      {
        return Optional.empty();
      }
    return ASTWalker.Traverse(universe)
      .filter(ASTItem.IsItemInFile(LSP4jUtils.getUri(params)))
      .filter(entry -> entry.getKey() instanceof AbstractConstant)
      .filter(entry -> PositionIsAfterOrAtCursor(params, FuzionParser.endOfFeature(entry.getValue())))
      .filter(entry -> PositionIsBeforeCursor(params, ((AbstractConstant) entry.getKey()).pos()))
      .map(entry -> ((AbstractConstant) entry.getKey()))
      .sorted(CompareBySourcePosition.reversed())
      .map(x -> x.type().featureOfType())
      .findFirst();
  }

  public static Stream<AbstractFeature> CallCompletionsAt(TextDocumentPositionParams params)
  {
    return CalledFeature(params)
      .map(x -> x.resultType())
      .flatMap(x -> {
        if (!x.isGenericArgument())
          {
            return Optional.of(x.featureOfType());
          }
        if (x.isGenericArgument() && !x.genericArgument().constraint().equals(Types.resolved.t_object))
          {
            return Optional.of(x.genericArgument().constraint().featureOfType());
          }
        return Optional.empty();
      })
      .map(feature -> {
        var declaredFeaturesOfInheritedFeatures =
          InheritedRecursive(feature).flatMap(c -> FuzionParser.DeclaredFeatures(c.calledFeature()));

        var declaredFeatures = Stream.concat(FuzionParser
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

  public static Stream<AbstractFeature> InfixPostfixCompletionsAt(TextDocumentPositionParams params)
  {
    return CalledFeature(params)
      .map(x -> x.resultType())
      .flatMap(x -> {
        if (!x.isGenericArgument())
          {
            return Optional.of(x.featureOfType());
          }
        if (x.isGenericArgument() && !x.genericArgument().constraint().equals(Types.resolved.t_object))
          {
            return Optional.of(x.genericArgument().constraint().featureOfType());
          }
        return Optional.empty();
      })
      .map(feature -> {
        var declaredFeaturesOfInheritedFeatures =
          InheritedRecursive(feature).flatMap(c -> FuzionParser.DeclaredFeatures(c.calledFeature()));

        var declaredFeatures = Stream.concat(FuzionParser
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

  public static Stream<AbstractFeature> CompletionsAt(TextDocumentPositionParams params)
  {
    return InFeature(params)
      .map(feature -> FeatureTool.FeaturesInScope(feature))
      .orElse(Stream.empty());
  }

  /**
   * given a TextDocumentPosition return all matching ASTItems
   * in the given file on the given line.
   * sorted by position descending.
   * @param params
   * @return
   */
  private static Stream<Object> ASTItemsBeforeOrAtCursor(TextDocumentPositionParams params)
  {
    var baseFeature = FuzionParser.Universe(LSP4jUtils.getUri(params));
    var astItems = ASTWalker.Traverse(baseFeature)
      .filter(ASTItem.IsItemInFile(LSP4jUtils.getUri(params)))
      .filter(IsItemInScope(params))
      .map(entry -> entry.getKey())
      .filter(IsItemNotBuiltIn(params))
      .filter(IsItemOnSameLineAsCursor(params))
      .sorted(CompareBySourcePosition.reversed());
    return astItems;
  }

  private static Predicate<Object> IsItemNotBuiltIn(TextDocumentPositionParams params)
  {
    return (astItem) -> {
      var sourcePositionOption = ASTItem.sourcePosition(astItem);
      return !sourcePositionOption.isBuiltIn();
    };
  }

  private static Predicate<Object> IsItemOnSameLineAsCursor(
    TextDocumentPositionParams params)
  {
    return (astItem) -> {
      var cursorPosition = LSP4jUtils.getPosition(params);
      var sourcePositionOption = ASTItem.sourcePosition(astItem);
      return cursorPosition.getLine() == Bridge.ToPosition(sourcePositionOption).getLine();
    };
  }

  /**
   * tries figuring out if an item is "reachable" from a given textdocumentposition
   * @param params
   * @return
   */
  private static Predicate<? super Entry<Object, AbstractFeature>> IsItemInScope(TextDocumentPositionParams params)
  {
    return (entry) -> {
      var astItem = entry.getKey();
      var outer = entry.getValue();
      var cursorPosition = LSP4jUtils.getPosition(params);

      var sourcePositionOption = ASTItem.sourcePosition(astItem);
      if (sourcePositionOption.isBuiltIn())
        {
          return false;
        }

      boolean BuiltInOrEndAfterCursor = outer.pos().isBuiltIn()
        || LSP4jUtils.ComparePosition(cursorPosition,
          Bridge.ToPosition(FuzionParser.endOfFeature(outer))) <= 0;
      boolean ItemPositionIsBeforeOrAtCursorPosition =
        LSP4jUtils.ComparePosition(cursorPosition, Bridge.ToPosition(sourcePositionOption)) >= 0;

      return ItemPositionIsBeforeOrAtCursorPosition && BuiltInOrEndAfterCursor;
    };
  }


  private static Comparator<? super Object> CompareBySourcePosition =
    Comparator.comparing(obj -> ASTItem.sourcePosition(obj), (sourcePosition1, sourcePosition2) -> {
      return sourcePosition1.compareTo(sourcePosition2);
    });

  /**
   * returns all features declared in uri
   * @param uri
   * @return
   */
  public static Stream<AbstractFeature> SelfAndDescendants(URI uri)
  {
    var baseFeature = FuzionParser.Main(uri);
    return FeatureTool.SelfAndDescendants(baseFeature);
  }

  public static Optional<AbstractCall> callAt(TextDocumentPositionParams params)
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
  public static Optional<AbstractFeature> FeatureAt(TextDocumentPositionParams params)
  {
    var token = FuzionLexer.IdentifierTokenAt(Bridge.ToSourcePosition(params));
    return ASTItemsBeforeOrAtCursor(params)
      .map(astItem -> {
        if (astItem instanceof AbstractFeature f
          && token.<Boolean>map(x -> x.text().equals(f.featureName().baseName())).orElse(false))
          {
            return f;
          }
        if (astItem instanceof AbstractCall c)
          {
            return ErrorHandling.ResultOrDefault(() -> c.calledFeature(), null);
          }
        return null;
      })
      .filter(f -> f != null)
      .findFirst()
      // NYI workaround for not having positions of all types in
      // the AST currently
      .or(() -> FindFeatureByName(params, token.map(t -> t.text()).orElse("")));
  }

  public static Optional<AbstractFeature> FindFeatureByName(TextDocumentPositionParams params, String text)
  {
    return QueryAST.InFeature(params)
      .map(contextFeature -> {
        return FeatureTool.FeaturesInScope(contextFeature)
          .filter(f -> f.featureName().baseName().equals(text))
          .findFirst()
          .orElse(null);
      });
  }

  /**
   * @param params
   * @return the most inner feature at the cursor position
   */
  public static Optional<AbstractFeature> InFeature(TextDocumentPositionParams params)
  {
    return SelfAndDescendants(LSP4jUtils.getUri(params))
      .filter(f -> {
        var cursorPosition = LSP4jUtils.getPosition(params);
        var startOfFeature = Bridge.ToPosition(f.pos());
        var endOfFeature = Bridge.ToPosition(FuzionParser.endOfFeature(f));
        return LSP4jUtils.ComparePosition(cursorPosition, endOfFeature) <= 0 &&
          LSP4jUtils.ComparePosition(cursorPosition, startOfFeature) > 0;
      })
      .filter(f -> {
        return f.pos()._column < params.getPosition().getCharacter() + 1;
      })
      .sorted(CompareBySourcePosition.reversed())
      .findFirst();
  }

  /**
   * @param params
   * @return if text document position is inside of string
   */
  public static boolean InString(TextDocumentPositionParams params)
  {
    return ASTWalker.Traverse(FuzionParser.Main(LSP4jUtils.getUri(params)))
      .filter(x -> x.getKey() instanceof StrConst)
      .map(x -> (StrConst) x.getKey())
      .anyMatch(x -> {
        if (x.pos()._line - 1 != params.getPosition().getLine())
          {
            return false;
          }
        var start = x.pos()._column;
        var end = x.pos()._column + x.str.length();
        if (SourceText.LineAt(x.pos()).charAt(x.pos()._column - 1) == '\"')
          {
            return start <= params.getPosition().getCharacter()
              && params.getPosition().getCharacter() <= end;
          }
        return start <= params.getPosition().getCharacter()
          && params.getPosition().getCharacter() < end;
      });
  }

}
