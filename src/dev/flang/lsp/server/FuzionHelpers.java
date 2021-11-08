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
 * Source of class FuzionHelpers
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.server;

import java.io.IOException;
import java.net.URI;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentPositionParams;

import dev.flang.ast.AbstractFeature;
import dev.flang.ast.Call;
import dev.flang.ast.Case;
import dev.flang.ast.Cond;
import dev.flang.ast.Contract;
import dev.flang.ast.Expr;
import dev.flang.ast.Feature;
import dev.flang.ast.FeatureVisitor;
import dev.flang.ast.FormalGenerics;
import dev.flang.ast.Generic;
import dev.flang.ast.Impl;
import dev.flang.ast.Impl.Kind;
import dev.flang.ast.InlineArray;
import dev.flang.ast.ReturnType;
import dev.flang.ast.Stmnt;
import dev.flang.ast.Type;
import dev.flang.ast.Types;
import dev.flang.be.interpreter.Interpreter;
import dev.flang.lsp.server.records.TokenInfo;
import dev.flang.lsp.server.util.ASTItem;
import dev.flang.lsp.server.util.Bridge;
import dev.flang.lsp.server.util.FuzionLexer;
import dev.flang.lsp.server.util.FuzionParser;
import dev.flang.lsp.server.util.LSP4jUtils;
import dev.flang.lsp.server.util.Log;
import dev.flang.util.SourcePosition;

/**
 * wild mixture of
 * shared helpers which are useful in more than one language server feature
 */
public final class FuzionHelpers
{

  // NYI remove once we have ISourcePosition interface
  /**
   * getPosition of ASTItem
   * @param entry
   * @return
   */
  public static Optional<SourcePosition> sourcePosition(Object entry)
  {
    if (entry instanceof Stmnt)
      {
        return Optional.ofNullable(((Stmnt) entry).pos());
      }
    if (entry instanceof Type)
      {
        return Optional.ofNullable(((Type) entry).pos);
      }
    if (entry instanceof Impl)
      {
        return Optional.ofNullable(((Impl) entry).pos);
      }
    if (entry instanceof Generic)
      {
        return Optional.ofNullable(((Generic) entry)._pos);
      }
    if (entry instanceof Case)
      {
        return Optional.ofNullable(((Case) entry).pos);
      }
    if (entry instanceof InlineArray)
      {
        return Optional.ofNullable(((InlineArray) entry).pos());
      }
    if (entry instanceof Expr)
      {
        return Optional.ofNullable(((Expr) entry).pos());
      }
    if (entry instanceof ReturnType)
      {
        return Optional.empty();
      }
    if (entry instanceof Cond)
      {
        return Optional.empty();
      }
    if (entry instanceof FormalGenerics)
      {
        return Optional.empty();
      }
    if (entry instanceof Contract)
      {
        return Optional.empty();
      }

    System.err.println(entry.getClass());
    Util.WriteStackTraceAndExit(1);
    return Optional.empty();
  }

  /**
   * given a TextDocumentPosition return all matching ASTItems
   * in the given file on the given line.
   * sorted by position descending.
   * @param params
   * @return
   */
  public static Stream<Object> ASTItemsBeforeOrAtCursor(TextDocumentPositionParams params)
  {
    var baseFeature = baseFeature(params.getTextDocument());
    if (baseFeature.isEmpty())
      {
        return Stream.empty();
      }

    var astItems = ASTWalker.Traverse(baseFeature.get())
      .filter(IsItemNotBuiltIn(params))
      .filter(IsItemInFile(LSP4jUtils.getUri(params)))
      .filter(IsItemOnSameLineAsCursor(params))
      .filter(IsItemInScope(params))
      .map(entry -> entry.getKey())
      .sorted(FuzionHelpers.CompareBySourcePosition.reversed());

    return astItems;
  }

  private static Predicate<? super Entry<Object, AbstractFeature>> IsItemOnSameLineAsCursor(
    TextDocumentPositionParams params)
  {
    return (entry) -> {
      var astItem = entry.getKey();
      var cursorPosition = LSP4jUtils.getPosition(params);
      var sourcePositionOption = FuzionHelpers.sourcePosition(astItem);
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
      var sourcePositionOption = FuzionHelpers.sourcePosition(astItem);
      if (sourcePositionOption.isEmpty())
        {
          return false;
        }
      return !sourcePositionOption.get().isBuiltIn();
    };
  }

  public static Predicate<? super Entry<Object, AbstractFeature>> IsItemInFile(URI uri)
  {
    return (entry) -> {
      var sourcePositionOption = FuzionHelpers.sourcePosition(entry.getKey());
      if (sourcePositionOption.isEmpty())
        {
          return false;
        }
      return uri.equals(FuzionParser.getUri(sourcePositionOption.get()));
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

      var sourcePositionOption = FuzionHelpers.sourcePosition(astItem);
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

  /**
   * returns the outermost feature found in uri
   * @param params
   * @return
   */
  private static Optional<AbstractFeature> baseFeature(TextDocumentIdentifier params)
  {
    return baseFeature(LSP4jUtils.getUri(params));
  }

  /**
  * returns the outermost feature found in uri
  * @param uri
  * @return
  */
  public static Optional<AbstractFeature> baseFeature(URI uri)
  {
    var baseFeature = allOf(uri, AbstractFeature.class)
      .filter(IsFeatureInFile(uri))
      .findFirst();
    return baseFeature;
  }

  public static Predicate<? super AbstractFeature> IsFeatureInFile(URI uri)
  {
    return feature -> {
      return uri.equals(FuzionParser.getUri(feature.pos()));
    };
  }

  public static Comparator<? super Object> CompareBySourcePosition =
    Comparator.comparing(obj -> obj, (obj1, obj2) -> {
      var sourcePositionOption1 = sourcePosition(obj1);
      var sourcePositionOption2 = sourcePosition(obj2);
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
      return endOfCall(obj1).compareTo(endOfCall(obj2));
    });

  public static boolean IsRoutineOrRoutineDef(AbstractFeature feature)
  {
    return Util.HashSetOf(Kind.Routine, Kind.RoutineDef).contains(feature.implKind());
  }

  public static boolean IsFieldLike(AbstractFeature feature)
  {
    return Util.HashSetOf(Kind.Field, Kind.FieldActual, Kind.FieldDef, Kind.FieldInit, Kind.FieldIter)
      .contains(feature.implKind());
  }

  public static boolean IsIntrinsic(AbstractFeature feature)
  {
    return feature.implKind() == Kind.Intrinsic;
  }

  public static Stream<AbstractFeature> calledFeaturesSortedDesc(TextDocumentPositionParams params)
  {
    var baseFeature = baseFeature(params.getTextDocument());
    if (baseFeature.isEmpty())
      {
        return Stream.empty();
      }

    return ASTWalker.Traverse(baseFeature.get())
      .filter(IsItemInFile(LSP4jUtils.getUri(params)))
      .filter(entry -> entry.getKey() instanceof Call)
      .map(entry -> new SimpleEntry<Call, AbstractFeature>((Call) entry.getKey(), entry.getValue()))
      .filter(entry -> PositionIsAfterOrAtCursor(params, FuzionParser.endOfFeature(entry.getValue())))
      .filter(entry -> PositionIsBeforeCursor(params, entry.getKey().pos()))
      .map(entry -> entry.getKey())
      .filter(c -> !IsAnonymousInnerFeature(c.calledFeature()))
      .filter(c -> c.calledFeature().resultType() != Types.t_ERROR)
      .sorted(CompareByEndOfCall.reversed())
      .map(c -> {
        Log.message("call: " + c.pos().toString());
        return c.calledFeature();
      });
  }

  /**
   * tries to figure out the end of a call in terms of a sourceposition
   * @param call
   * @return
  */
  private static SourcePosition endOfCall(Call call)
  {
    var result = call._actuals
      .stream()
      .map(expression -> expression.pos())
      .sorted(Comparator.reverseOrder())
      .findFirst();
    if (result.isEmpty())
      {
        return call.pos();
      }

    return new SourcePosition(result.get()._sourceFile, result.get()._line, result.get()._column + 1);
  }

  private static boolean PositionIsAfterOrAtCursor(TextDocumentPositionParams params, SourcePosition sourcePosition)
  {
    return LSP4jUtils.ComparePosition(LSP4jUtils.getPosition(params), Bridge.ToPosition(sourcePosition)) <= 0;
  }

  private static boolean PositionIsBeforeCursor(TextDocumentPositionParams params, SourcePosition sourcePosition)
  {
    return LSP4jUtils.ComparePosition(LSP4jUtils.getPosition(params), Bridge.ToPosition(sourcePosition)) > 0;
  }

  public static boolean IsAnonymousInnerFeature(AbstractFeature f)
  {
    return f.featureName().baseName().startsWith("#");
  }

  /**
   * tries to find the closest feature at given
   * position that is declared, called or used by a type
   * @param params
   */
  public static Optional<AbstractFeature> featureAt(TextDocumentPositionParams params)
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
      .filter(f -> !IsAnonymousInnerFeature(f))
      .map(f -> {
        if (IsArgument(f))
          {
            return f.resultType().featureOfType();
          }
        return f;
      })
      .filter(f -> !IsFieldLike(f))
      // NYI maybe there is a better way?
      .filter(f -> !Util.HashSetOf("Object", "Function", "call").contains(f.featureName().baseName()))
      .findFirst();
  }

  private static boolean IsArgument(AbstractFeature feature)
  {
    if (feature.pos().isBuiltIn())
      {
        return false;
      }
    return allOf(FuzionParser.getUri(feature.pos()), AbstractFeature.class)
      .anyMatch(f -> f.arguments().contains(feature));
  }

  /**
   * example: allOf(uri, Call.class) returns all Calls
   * @param <T>
   * @param classOfT
   * @return
   */
  public static <T extends Object> Stream<T> allOf(URI uri, Class<T> classOfT)
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
  public static Stream<Call> callsTo(URI uri, AbstractFeature feature)
  {
    return allOf(uri, Call.class)
      .filter(call -> call.calledFeature().equals(feature));
  }

  private static Stream<Object> callsAndFeaturesAt(TextDocumentPositionParams params)
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
  public static Optional<AbstractFeature> feature(TextDocumentPositionParams params)
  {
    var token = FuzionLexer.rawTokenAt(params);
    return callsAndFeaturesAt(params).map(callOrFeature -> {
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
    var isCallOrFeature = FuzionHelpers.callsAndFeaturesAt(params)
      .map(obj -> sourcePosition(obj).get())
      .filter(pos -> column == pos._column)
      .findFirst()
      .isPresent();
    if (!isCallOrFeature)
      {
        return Optional.empty();
      }
    return Optional.of(token);
  }

  private static Stream<AbstractFeature> InheritedFeatures(AbstractFeature feature)
  {
    return feature.inherits().stream().flatMap(c -> {
      return Stream.concat(Stream.of(c.calledFeature()), InheritedFeatures(c.calledFeature()));
    });
  }

  public static Stream<AbstractFeature> featuresIncludingInheritedFeatures(TextDocumentPositionParams params)
  {
    var mainFeature = FuzionParser.getMainFeature(LSP4jUtils.getUri(params));
    if (mainFeature.isEmpty())
      {
        return Stream.empty();
      }

    var feature = calledFeaturesSortedDesc(params)
      .map(x -> {
        return x.resultType().featureOfType();
      })
      .findFirst();

    if (feature.isEmpty())
      {
        return Stream.empty();
      }
    return Stream.concat(Stream.of(feature.get()), InheritedFeatures(feature.get()));
  }

  public static MessageParams Run(URI uri)
    throws IOException, InterruptedException, ExecutionException, TimeoutException
  {
    return Run(uri, 10000);
  }

  public static MessageParams Run(URI uri, int timeout)
    throws IOException, InterruptedException, ExecutionException, TimeoutException
  {
    var result = Util.WithCapturedStdOutErr(() -> {
      var interpreter = new Interpreter(FuzionParser.FUIR(uri));
      interpreter.run();
    }, timeout);
    return result;
  }

  public static Stream<AbstractFeature> outerFeatures(AbstractFeature feature)
  {
    if (feature.outer() == null)
      {
        return Stream.of(feature.outer());
      }
    return Stream.concat(Stream.of(feature.outer()), outerFeatures(feature.outer()))
      .filter(f -> f != null);
  }

  /**
   * returns all features declared in uri
   * @param uri
   * @return
   */
  public static Stream<AbstractFeature> Features(URI uri)
  {
    var baseFeature = baseFeature(uri);
    if (baseFeature.isEmpty())
      {
        return Stream.empty();
      }
    return DeclaredFeaturesRecursive(baseFeature.get());
  }

  private static Stream<AbstractFeature> DeclaredFeaturesRecursive(AbstractFeature feature)
  {
    return Stream.concat(Stream.of(feature),
      FuzionParser.DeclaredFeatures(feature).flatMap(f -> DeclaredFeaturesRecursive(f)));
  }

  /**
   * check if a feature contains a given call
   * @param call
   * @return
   */
  private static Predicate<? super AbstractFeature> contains(Call call)
  {
    return f -> {
      var calls = new TreeSet<Call>(Util.CompareByHashCode);
      // NYI replace visitor
      f.visit(new FeatureVisitor() {
        public Expr action(Call c, Feature outer)
        {
          calls.add(c);
          return super.action(c, outer);
        }
      });
      return calls.contains(call);
    };
  }

  /**
   * @param call
   * @return
   */
  public static AbstractFeature featureOf(Call call)
  {
    var uri = FuzionParser.getUri(call.pos);
    return Features(uri)
      .filter(contains(call))
      .findFirst()
      .orElseThrow();
  }

  public static String CommentOf(AbstractFeature feature)
  {
    var textDocumentPosition = Bridge.ToTextDocumentPosition(feature.pos());
    var commentLines = new ArrayList<String>();
    while (true)
      {
        if (textDocumentPosition.getPosition().getLine() != 0)
          {
            var position = textDocumentPosition.getPosition();
            position.setLine(textDocumentPosition.getPosition().getLine() - 1);
            textDocumentPosition.setPosition(position);
          }
        else
          {
            break;
          }
        if (FuzionLexer.isCommentLine(textDocumentPosition))
          {
            commentLines.add(SourceText.LineAt(textDocumentPosition));
          }
        else
          {
            break;
          }
      }
    Collections.reverse(commentLines);
    return commentLines.stream().map(line -> line.trim()).collect(Collectors.joining(System.lineSeparator()));
  }

  private static final SourcePosition None =
    new SourcePosition(Bridge.ToSourceFile(Util.toURI("file:///--none--")), 0, 0);

  public static SourcePosition sourcePositionOrNone(Object obj)
  {
    return sourcePosition(obj).orElse(None);
  }

  public static Optional<Call> callAt(TextDocumentPositionParams params)
  {
    Optional<Call> call = ASTItemsBeforeOrAtCursor(params)
      .filter(item -> Util.HashSetOf(Call.class).stream().anyMatch(cl -> cl.isAssignableFrom(item.getClass())))
      .map(c -> (Call) c)
      .findFirst();
    return call;
  }

  public static String AST(AbstractFeature feature)
  {
    var ast = ASTWalker.Traverse(feature)
      .reduce("", (a, b) -> {
        var item = b.getKey();
        var position = sourcePosition(item);
        // NYI
        var indent = 0;
        if (position.isEmpty())
          {
            return a;
          }
        return a + System.lineSeparator()
          + " ".repeat(indent * 2) + position.get()._line + ":" + position.get()._column + ":"
          + item.getClass().getSimpleName() + ":" + ASTItem.ToLabel(item);
      }, String::concat);
    return ast;
  }

  public static boolean IsAbstractFeature(Object o)
  {
    return AbstractFeature.class.isAssignableFrom(o.getClass());
  }
}
