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
import java.util.AbstractMap.SimpleEntry;
import java.util.Comparator;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentPositionParams;

import dev.flang.ast.AbstractCall;
import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AbstractType;
import dev.flang.ast.Types;
import dev.flang.shared.ASTItem;
import dev.flang.shared.ASTWalker;
import dev.flang.shared.ErrorHandling;
import dev.flang.shared.FeatureTool;
import dev.flang.shared.FuzionLexer;
import dev.flang.shared.FuzionParser;
import dev.flang.shared.Util;
import dev.flang.util.SourcePosition;

public class QueryAST
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
    var universe = FuzionParser.Universe(LSP4jUtils.getUri(params));
    return ASTWalker.Traverse(universe)
      .filter(ASTItem.IsItemInFile(LSP4jUtils.getUri(params)))
      .filter(entry -> entry.getKey() instanceof AbstractCall)
      .map(entry -> new SimpleEntry<AbstractCall, AbstractFeature>((AbstractCall) entry.getKey(), entry.getValue()))
      .filter(entry -> PositionIsAfterOrAtCursor(params, FuzionParser.endOfFeature(entry.getValue())))
      .filter(entry -> PositionIsBeforeCursor(params, entry.getKey().pos()))
      .map(entry -> entry.getKey())
      .filter(c -> LSP4jUtils.ComparePosition(Bridge.ToPosition(CallTool.endOfCall(c)), params.getPosition()) <= 0)
      .sorted(CompareBySourcePosition.reversed())
      .filter(c -> c.calledFeature() != null)
      .map(c -> c.calledFeature())
      .filter(f -> !FeatureTool.IsAnonymousInnerFeature(f))
      // NYI in this case we could try to find possibly called features?
      .filter(f -> f.resultType() != Types.t_ERROR)
      .findFirst();
  }

  public static Stream<AbstractFeature> CallCompletionsAt(TextDocumentPositionParams params)
  {
    return CalledFeature(params)
      .map(x -> x.resultType())
      .filter(x -> !x.isGenericArgument())
      .map(x -> {
        return x.featureOfType();
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
        // NYI what to do with infix, prefix, postfix?
        // NYI do we need to filter abstract features?
        return declaredFeatures
          .stream()
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
      .filter(IsItemNotBuiltIn(params))
      .filter(ASTItem.IsItemInFile(LSP4jUtils.getUri(params)))
      .filter(IsItemOnSameLineAsCursor(params))
      .filter(IsItemInScope(params))
      .map(entry -> entry.getKey())
      .sorted(CompareBySourcePosition.reversed());

    return astItems;
  }

  private static Predicate<? super Entry<Object, AbstractFeature>> IsItemNotBuiltIn(TextDocumentPositionParams params)
  {
    return (entry) -> {
      var astItem = entry.getKey();
      var sourcePositionOption = ASTItem.sourcePosition(astItem);
      if (sourcePositionOption.isEmpty())
        {
          return false;
        }
      return !sourcePositionOption.get().isBuiltIn();
    };
  }

  private static Predicate<? super Entry<Object, AbstractFeature>> IsItemOnSameLineAsCursor(
    TextDocumentPositionParams params)
  {
    return (entry) -> {
      var astItem = entry.getKey();
      var cursorPosition = LSP4jUtils.getPosition(params);
      var sourcePositionOption = ASTItem.sourcePosition(astItem);
      if (sourcePositionOption.isEmpty())
        {
          return false;
        }
      return cursorPosition.getLine() == Bridge.ToPosition(sourcePositionOption.get()).getLine();
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
      if (sourcePositionOption.isEmpty())
        {
          return false;
        }

      boolean BuiltInOrEndAfterCursor = outer.pos().isBuiltIn()
        || LSP4jUtils.ComparePosition(cursorPosition,
          Bridge.ToPosition(FuzionParser.endOfFeature(outer))) <= 0;
      boolean ItemPositionIsBeforeOrAtCursorPosition =
        LSP4jUtils.ComparePosition(cursorPosition, Bridge.ToPosition(sourcePositionOption.get())) >= 0;

      return ItemPositionIsBeforeOrAtCursorPosition && BuiltInOrEndAfterCursor;
    };
  }


  private static Comparator<? super Object> CompareBySourcePosition =
    Comparator.comparing(obj -> ASTItem.sourcePosition(obj), (sourcePosition1, sourcePosition2) -> {
      if (sourcePosition1.isEmpty() || sourcePosition2.isEmpty())
        {
          if (sourcePosition1.isEmpty() && sourcePosition2.isEmpty())
            {
              return 0;
            }
          if (sourcePosition1.isEmpty())
            {
              return -1;
            }
          return 1;
        }
      return sourcePosition1.get().compareTo(sourcePosition2.get());
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
      .filter(item -> Util.HashSetOf(AbstractCall.class).stream().anyMatch(cl -> cl.isAssignableFrom(item.getClass())))
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
        if (astItem instanceof AbstractType t)
          {
            return t.featureOfType();
          }
        return null;
      })
      .filter(f -> f != null)
      .filter(f -> !FeatureTool.IsAnonymousInnerFeature(f))
      .map(f -> {
        if (f.resultType().isChoice())
          {
            return f;
          }
        if (FeatureTool.IsArgument(f))
          {
            return f.resultType().featureOfType();
          }
        return f;
      })
      .filter(f -> !FeatureTool.IsInternal(f))
      .filter(f -> !f.pos().isBuiltIn())
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
          .filter(f -> f.qualifiedName().endsWith(text))
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

}
