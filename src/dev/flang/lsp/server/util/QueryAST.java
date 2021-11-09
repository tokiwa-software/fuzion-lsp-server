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
import java.util.stream.Stream;

import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentPositionParams;

import dev.flang.ast.AbstractFeature;
import dev.flang.ast.Call;
import dev.flang.ast.Type;
import dev.flang.ast.Types;
import dev.flang.lsp.server.ASTWalker;
import dev.flang.lsp.server.Util;
import dev.flang.lsp.server.records.TokenInfo;
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

  public static Stream<AbstractFeature> CalledFeaturesSortedDesc(TextDocumentPositionParams params)
  {
    var baseFeature = FuzionParser.main(params.getTextDocument());
    if (baseFeature.isEmpty())
      {
        return Stream.empty();
      }

    return ASTWalker.Traverse(baseFeature.get())
      .filter(ASTItem.IsItemInFile(LSP4jUtils.getUri(params)))
      .filter(entry -> entry.getKey() instanceof Call)
      .map(entry -> new SimpleEntry<Call, AbstractFeature>((Call) entry.getKey(), entry.getValue()))
      .filter(entry -> PositionIsAfterOrAtCursor(params, FuzionParser.endOfFeature(entry.getValue())))
      .filter(entry -> PositionIsBeforeCursor(params, entry.getKey().pos()))
      .map(entry -> entry.getKey())
      .filter(c -> !FeatureTool.IsAnonymousInnerFeature(c.calledFeature()))
      .filter(c -> c.calledFeature().resultType() != Types.t_ERROR)
      .sorted(CompareByEndOfCall.reversed())
      .map(c -> {
        Log.message("call: " + c.pos().toString());
        return c.calledFeature();
      });
  }


  /**
   * example: allOf(uri, Call.class) returns all Calls
   * @param <T>
   * @param classOfT
   * @return
   */
  public static <T extends Object> Stream<T> AllOf(URI uri, Class<T> classOfT)
  {
    var universe = FuzionParser.universe(uri);

    return ASTWalker.Traverse(universe)
      .map(e -> e.getKey())
      .filter(obj -> classOfT.isAssignableFrom(obj.getClass()))
      .map(obj -> (T) obj);
  }

  /**
   * @param feature
   * @return all calls to this feature
   */
  public static Stream<Call> CallsTo(URI uri, AbstractFeature feature)
  {
    return AllOf(uri, Call.class)
      .filter(call -> call.calledFeature().equals(feature));
  }

  private static Stream<Object> CallsAndFeaturesAt(TextDocumentPositionParams params)
  {
    return ASTItemsBeforeOrAtCursor(params)
      .filter(item -> Util.HashSetOf(AbstractFeature.class, Call.class)
        .stream()
        .anyMatch(cl -> cl.isAssignableFrom(item.getClass())));
  }

  /**
   * @param params
   * @return feature at textdocumentposition or empty
   */
  public static Optional<AbstractFeature> Feature(TextDocumentPositionParams params)
  {
    var token = FuzionLexer.rawTokenAt(params);
    return CallsAndFeaturesAt(params).map(callOrFeature -> {
      if (callOrFeature instanceof Call)
        {
          return ((Call) callOrFeature).calledFeature();
        }
      return (AbstractFeature) callOrFeature;
    })
      .filter(x -> x.featureName().baseName().equals(token.text()))
      .findFirst();
  }

  public static Optional<TokenInfo> CallOrFeatureToken(TextDocumentPositionParams params)
  {
    var token = FuzionLexer.rawTokenAt(params);
    if (token == null)
      {
        return Optional.empty();
      }
    var column = token.start()._column;
    var isCallOrFeature = CallsAndFeaturesAt(params)
      .map(obj -> ASTItem.sourcePosition(obj).get())
      .filter(pos -> column == pos._column)
      .findFirst()
      .isPresent();
    if (!isCallOrFeature)
      {
        return Optional.empty();
      }
    return Optional.of(token);
  }

  // NYI test this
  public static Stream<AbstractFeature> FeaturesIncludingInheritedFeatures(TextDocumentPositionParams params)
  {
    var mainFeature = FuzionParser.main(LSP4jUtils.getUri(params));
    if (mainFeature.isEmpty())
      {
        return Stream.empty();
      }

    var feature = CalledFeaturesSortedDesc(params)
      .map(x -> {
        return x.resultType().featureOfType();
      })
      .findFirst();

    if (feature.isEmpty())
      {
        return Stream.empty();
      }
    return Stream.concat(Stream.of(feature.get()), FeatureTool.InheritedFeatures(feature.get()));
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
    var baseFeature = FuzionParser.main(params.getTextDocument());
    if (baseFeature.isEmpty())
      {
        return Stream.empty();
      }

    var astItems = ASTWalker.Traverse(baseFeature.get())
      .filter(IsItemNotBuiltIn(params))
      .filter(ASTItem.IsItemInFile(LSP4jUtils.getUri(params)))
      .filter(IsItemOnSameLineAsCursor(params))
      .filter(IsItemInScope(params))
      .map(entry -> entry.getKey())
      .sorted(CompareBySourcePosition.reversed());

    return astItems;
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
    Comparator.comparing(obj -> obj, (obj1, obj2) -> {
      var sourcePositionOption1 = ASTItem.sourcePosition(obj1);
      var sourcePositionOption2 = ASTItem.sourcePosition(obj2);
      if (sourcePositionOption1.isEmpty() || sourcePositionOption2.isEmpty())
        {
          if (sourcePositionOption1.isEmpty() && sourcePositionOption2.isEmpty())
            {
              return 0;
            }
          if (sourcePositionOption1.isEmpty())
            {
              return -1;
            }
          return 1;
        }
      return sourcePositionOption1.get().compareTo(sourcePositionOption2.get());
    });

  private static Comparator<? super Call> CompareByEndOfCall =
    Comparator.comparing(obj -> obj, (obj1, obj2) -> {
      if (obj1.equals(obj2))
        {
          return 0;
        }
      return CallTool.endOfCall(obj1).compareTo(CallTool.endOfCall(obj2));
    });

  /**
   * returns all features declared in uri
   * @param uri
   * @return
   */
  public static Stream<AbstractFeature> DeclaredFeaturesRecursive(URI uri)
  {
    var baseFeature = FuzionParser.main(uri);
    if (baseFeature.isEmpty())
      {
        return Stream.empty();
      }
    return FeatureTool.DeclaredFeaturesRecursive(baseFeature.get());
  }

  public static Optional<Call> callAt(TextDocumentPositionParams params)
  {
    Optional<Call> call = ASTItemsBeforeOrAtCursor(params)
      .filter(item -> Util.HashSetOf(Call.class).stream().anyMatch(cl -> cl.isAssignableFrom(item.getClass())))
      .map(c -> (Call) c)
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
    return ASTItemsBeforeOrAtCursor(params)
      .map(astItem -> {
        if (astItem instanceof AbstractFeature)
          {
            return (AbstractFeature) astItem;
          }
        if (astItem instanceof Call)
          {
            return ((Call) astItem).calledFeature();
          }
        if (astItem instanceof Type)
          {
            return ((Type) astItem).featureOfType();
          }
        return null;
      })
      .filter(f -> f != null)
      .filter(f -> !FeatureTool.IsAnonymousInnerFeature(f))
      .map(f -> {
        if (FeatureTool.IsArgument(f))
          {
            return f.resultType().featureOfType();
          }
        return f;
      })
      .filter(f -> !FeatureTool.IsFieldLike(f))
      // NYI maybe there is a better way?
      .filter(f -> !Util.HashSetOf("Object", "Function", "call").contains(f.featureName().baseName()))
      .findFirst();
  }


}
