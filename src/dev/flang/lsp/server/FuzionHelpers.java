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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
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
import dev.flang.parser.Lexer.Token;
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
      .filter(IsItemInFile(Util.getUri(params)))
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
      var cursorPosition = Util.getPosition(params);
      var sourcePositionOption = FuzionHelpers.sourcePosition(astItem);
      if (sourcePositionOption.isEmpty())
        {
          return false;
        }
      return cursorPosition.getLine() == Converters.ToPosition(sourcePositionOption.get()).getLine();
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

  private static Predicate<? super Entry<Object, AbstractFeature>> IsItemInFile(URI uri)
  {
    return (entry) -> {
      var sourcePositionOption = FuzionHelpers.sourcePosition(entry.getKey());
      if (sourcePositionOption.isEmpty())
        {
          return false;
        }
      return uri.equals(ParserHelper.getUri(sourcePositionOption.get()));
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
      var cursorPosition = Util.getPosition(params);

      var sourcePositionOption = FuzionHelpers.sourcePosition(astItem);
      if (sourcePositionOption.isEmpty())
        {
          return false;
        }

      boolean BuiltInOrEndAfterCursor = outer.pos().isBuiltIn()
        || Util.ComparePosition(cursorPosition,
          Converters.ToPosition(FuzionHelpers.endOfFeature(outer))) <= 0;
      boolean ItemPositionIsBeforeOrAtCursorPosition =
        Util.ComparePosition(cursorPosition, Converters.ToPosition(sourcePositionOption.get())) >= 0;

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
    return baseFeature(Util.getUri(params));
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
      return uri.equals(ParserHelper.getUri(feature.pos()));
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
      .filter(IsItemInFile(Util.getUri(params)))
      .filter(entry -> entry.getKey() instanceof Call)
      .map(entry -> new SimpleEntry<Call, AbstractFeature>((Call) entry.getKey(), entry.getValue()))
      .filter(entry -> PositionIsAfterOrAtCursor(params, endOfFeature(entry.getValue())))
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
    return Util.ComparePosition(Util.getPosition(params), Converters.ToPosition(sourcePosition)) <= 0;
  }

  private static boolean PositionIsBeforeCursor(TextDocumentPositionParams params, SourcePosition sourcePosition)
  {
    return Util.ComparePosition(Util.getPosition(params), Converters.ToPosition(sourcePosition)) > 0;
  }

  /**
   * NYI replace by real end of feature once we have this information in the AST
   * !!!CACHED via Memory.EndOfFeature!!!
   * @param feature
   * @return
   */
  public static SourcePosition endOfFeature(AbstractFeature feature)
  {
    var uri = ParserHelper.getUri(feature.pos());
    if (!Memory.EndOfFeature.containsKey(feature))
      {
        SourcePosition endOfFeature = ASTWalker.Traverse(feature)
          .filter(entry -> entry.getValue() != null)
          .filter(IsItemInFile(uri))
          .filter(entry -> entry.getValue().compareTo(feature) == 0)
          .map(entry -> sourcePosition(entry.getKey()))
          .filter(sourcePositionOption -> sourcePositionOption.isPresent())
          .map(sourcePosition -> sourcePosition.get())
          .sorted((Comparator<SourcePosition>) Comparator.<SourcePosition>reverseOrder())
          .map(position -> {
            var start = LexerUtil.endOfToken(uri, Converters.ToPosition(position));
            var line = FuzionHelpers.restOfLine(uri, start);
            // NYI maybe use inverse hashset here? i.e. state which tokens can
            // be skipped
            var token = LexerUtil.nextTokenOfType(line, Util.HashSetOf(Token.t_eof, Token.t_ident, Token.t_semicolon,
              Token.t_rbrace, Token.t_rcrochet, Token.t_rparen));
            return new SourcePosition(position._sourceFile, position._line, start.getCharacter() + token.end()._column);
          })
          .findFirst()
          .orElse(feature.pos());

        Memory.EndOfFeature.put(feature, endOfFeature);
      }

    return Memory.EndOfFeature.get(feature);
  }

  private static String restOfLine(URI uri, Position start)
  {
    var line = sourceText(uri)
      .lines()
      .skip(start.getLine())
      .findFirst()
      .orElseThrow();
    return line.length() <= start.getCharacter() ? "": line.substring(start.getCharacter());
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
    return allOf(ParserHelper.getUri(feature.pos()), AbstractFeature.class)
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
    var universe = ParserHelper.universe(uri);

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

  public static String sourceText(TextDocumentPositionParams params)
  {
    return sourceText(Util.getUri(params));
  }

  static String sourceText(URI uri)
  {
    var sourceText = FuzionTextDocumentService.getText(uri);
    if (sourceText.isPresent())
      {
        return sourceText.get();
      }
    try
      {
        return String.join(System.lineSeparator(),
          Files.readAllLines(Util.PathOf(uri), StandardCharsets.UTF_8));
      }
    catch (IOException e)
      {
        Util.WriteStackTraceAndExit(1, e);
        return null;
      }
  }

  /**
   * extract range of source
   * @param uri
   * @param range
   * @return
   */
  public static String stringAt(URI uri, Range range)
  {
    var lines = sourceText(uri)
      .lines()
      .skip(range.getStart().getLine())
      .limit(range.getEnd().getLine() - range.getStart().getLine() + 1)
      .toList();
    if (lines.size() == 1)
      {
        return lines.get(0).substring(range.getStart().getCharacter(), range.getEnd().getCharacter());
      }
    var result = "";
    for(int i = 0; i < lines.size(); i++)
      {
        // first line
        if (i == 0)
          {
            result += lines.get(i).substring(range.getStart().getCharacter()) + System.lineSeparator();
          }
        // last line
        else if (i + 1 == lines.size())
          {
            result += lines.get(i).substring(0, range.getEnd().getCharacter());
          }
        // middle line
        else
          {
            result += lines.get(i) + System.lineSeparator();
          }
      }
    return result;
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
    var token = LexerUtil.rawTokenAt(params);
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
    var token = LexerUtil.rawTokenAt(params);
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
    var mainFeature = ParserHelper.getMainFeature(Util.getUri(params));
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
      var interpreter = new Interpreter(ParserHelper.FUIR(uri));
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
      ParserHelper.DeclaredFeatures(feature).flatMap(f -> DeclaredFeaturesRecursive(f)));
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
    var uri = ParserHelper.getUri(call.pos);
    return Features(uri)
      .filter(contains(call))
      .findFirst()
      .orElseThrow();
  }

  private static String LineAt(TextDocumentPositionParams param)
  {
    return FuzionHelpers.sourceText(param)
      .split("\n")[param.getPosition().getLine()];
  }

  public static String CommentOf(AbstractFeature feature)
  {
    var textDocumentPosition = Converters.ToTextDocumentPosition(feature.pos());
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
        if (LexerUtil.isCommentLine(textDocumentPosition))
          {
            commentLines.add(LineAt(textDocumentPosition));
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
    new SourcePosition(Converters.ToSourceFile(Util.toURI("file:///--none--")), 0, 0);

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
          + item.getClass().getSimpleName() + ":" + Converters.ToLabel(item);
      }, String::concat);
    return ast;
  }

  public static boolean IsAbstractFeature(Object o)
  {
    return AbstractFeature.class.isAssignableFrom(o.getClass());
  }
}
