package dev.flang.lsp.server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.AbstractMap.SimpleEntry;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentPositionParams;

import dev.flang.ast.Call;
import dev.flang.ast.Case;
import dev.flang.ast.Cond;
import dev.flang.ast.Contract;
import dev.flang.ast.Feature;
import dev.flang.ast.FormalGenerics;
import dev.flang.ast.Generic;
import dev.flang.ast.Impl;
import dev.flang.ast.Impl.Kind;
import dev.flang.ast.ReturnType;
import dev.flang.ast.Stmnt;
import dev.flang.ast.Type;
import dev.flang.ast.Types;
import dev.flang.be.interpreter.Interpreter;
import dev.flang.lsp.server.records.TokenInfo;
import dev.flang.parser.Lexer;
import dev.flang.parser.Lexer.Token;
import dev.flang.util.SourceFile;
import dev.flang.util.SourcePosition;

/**
 * wild mixture of
 * shared helpers which are useful in more than one language server feature
 */
public final class FuzionHelpers
{

  // NYI remove once we have ISourcePosition interface
  // NYI return Optional<SourcePosition>
  /**
   * getPosition of ASTItem
   * @param entry
   * @return
   */
  public static SourcePosition position(Object entry)
  {
    var result = getPositionOrNull(entry);
    if (result != null)
      {
        return result;
      }
    // Log.write("no src pos found for: " + entry.getClass());
    // NYI what to return?
    return SourcePosition.builtIn;
  }

  private static SourcePosition getPositionOrNull(Object entry)
  {
    if (entry instanceof Stmnt)
      {
        return ((Stmnt) entry).pos();
      }
    if (entry instanceof Type)
      {
        return ((Type) entry).pos;
      }
    if (entry instanceof Impl)
      {
        return ((Impl) entry).pos;
      }
    if (entry instanceof Generic)
      {
        return ((Generic) entry)._pos;
      }
    if (entry instanceof Case)
      {
        return ((Case) entry).pos;
      }
    // NYI
    if (entry instanceof ReturnType)
      {
        return SourcePosition.builtIn;
      }
    // NYI
    if (entry instanceof Cond)
      {
        return SourcePosition.builtIn;
      }
    // NYI
    if (entry instanceof FormalGenerics)
      {
        return SourcePosition.builtIn;
      }
    // NYI
    if (entry instanceof Contract)
      {
        return SourcePosition.builtIn;
      }

    System.err.println(entry.getClass());
    Util.WriteStackTraceAndExit(1);
    return null;
  }

  /**
   * given a TextDocumentPosition return all matching ASTItems
   * in the given file on the given line.
   * sorted by position descending.
   * @param params
   * @return
   */
  public static Stream<Object> ASTItemsOnLine(TextDocumentPositionParams params)
  {
    var baseFeature = baseFeature(params.getTextDocument());
    if (baseFeature.isEmpty())
      {
        return Stream.empty();
      }

    var astItems = HeirsVisitor.visit(baseFeature.get())
      .entrySet()
      .stream()
      .filter(IsItemNotBuiltIn(params))
      .filter(IsItemInFile(Util.getUri(params)))
      .filter(IsItemOnSameLineAsCursor(params))
      .filter(IsItemInScope(params))
      .map(entry -> entry.getKey())
      .sorted(FuzionHelpers.CompareBySourcePosition.reversed());

    return astItems;
  }

  private static Predicate<? super Entry<Object, Feature>> IsItemOnSameLineAsCursor(TextDocumentPositionParams params)
  {
    return (entry) -> {
      var astItem = entry.getKey();
      var cursorPosition = Util.getPosition(params);
      var sourcePosition = FuzionHelpers.position(astItem);
      return cursorPosition.getLine() == Converters.ToPosition(sourcePosition).getLine();
    };
  }

  private static Predicate<? super Entry<Object, Feature>> IsItemNotBuiltIn(TextDocumentPositionParams params)
  {
    return (entry) -> {
      var astItem = entry.getKey();
      var sourcePosition = FuzionHelpers.position(astItem);
      return !sourcePosition.isBuiltIn();
    };
  }

  private static Predicate<? super Entry<Object, Feature>> IsItemInFile(String uri)
  {
    return (entry) -> {
      var sourcePosition = FuzionHelpers.position(entry.getKey());
      return uri.equals(ParserHelper.getUri(sourcePosition));
    };
  }

  /**
   * tries figuring out if an item is "reachable" from a given textdocumentposition
   * @param params
   * @return
   */
  private static Predicate<? super Entry<Object, Feature>> IsItemInScope(TextDocumentPositionParams params)
  {
    return (entry) -> {
      var astItem = entry.getKey();
      var outer = entry.getValue();
      var cursorPosition = Util.getPosition(params);

      var sourcePosition = FuzionHelpers.position(astItem);

      boolean BuiltInOrEndAfterCursor = outer.pos().isBuiltIn()
        || Util.ComparePosition(cursorPosition,
          Converters.ToPosition(FuzionHelpers.endOfFeature(outer))) <= 0;
      boolean ItemPositionIsBeforeOrAtCursorPosition =
        Util.ComparePosition(cursorPosition, Converters.ToPosition(sourcePosition)) >= 0;

      return ItemPositionIsBeforeOrAtCursorPosition && BuiltInOrEndAfterCursor;
    };
  }

  /**
   * returns the outermost feature found in uri
   * @param uri
   * @return
   */
  private static Optional<Feature> baseFeature(TextDocumentIdentifier params)
  {
    return baseFeature(Util.getUri(params));
  }

  public static Optional<Feature> baseFeature(String uri)
  {
    var baseFeature = allOf(uri, Feature.class)
      .filter(IsFeatureInFile(uri))
      .findFirst();
    if (baseFeature.isPresent())
      {
        Log.message("baseFeature: " + baseFeature.get().qualifiedName());
      }
    return baseFeature;
  }

  public static Predicate<? super Feature> IsFeatureInFile(String uri)
  {
    return feature -> {
      return uri.equals(ParserHelper.getUri(feature.pos()));
    };
  }

  public static Comparator<? super Object> CompareBySourcePosition =
    Comparator.comparing(obj -> obj, (obj1, obj2) -> {
      if (obj1.equals(obj2))
        {
          return 0;
        }
      return position(obj1).compareTo(position(obj2));
    });

  private static Comparator<? super Call> CompareByEndOfCall =
    Comparator.comparing(obj -> obj, (obj1, obj2) -> {
      if (obj1.equals(obj2))
        {
          return 0;
        }
      return endOfCall(obj1).compareTo(endOfCall(obj2));
    });

  public static boolean IsRoutineOrRoutineDef(Feature feature)
  {
    return IsRoutineOrRoutineDef(feature.impl);
  }

  public static boolean IsRoutineOrRoutineDef(Impl impl)
  {
    return Util.HashSetOf(Kind.Routine, Kind.RoutineDef).contains(impl.kind_);
  }

  public static boolean IsFieldLike(Feature feature)
  {
    return IsFieldLike(feature.impl);
  }

  public static boolean IsFieldLike(Impl impl)
  {
    return Util.HashSetOf(Kind.Field, Kind.FieldActual, Kind.FieldDef, Kind.FieldInit, Kind.FieldIter)
      .contains(impl.kind_);
  }

  public static boolean IsIntrinsic(Feature feature)
  {
    return IsIntrinsic(feature.impl);
  }

  public static boolean IsIntrinsic(Impl impl)
  {
    return impl.kind_ == Kind.Intrinsic;
  }

  public static Stream<Feature> calledFeaturesSortedDesc(TextDocumentPositionParams params)
  {
    var baseFeature = baseFeature(params.getTextDocument());
    if (baseFeature.isEmpty())
      {
        return Stream.empty();
      }

    return HeirsVisitor.visit(baseFeature.get())
      .entrySet()
      .stream()
      .filter(IsItemInFile(Util.getUri(params)))
      .filter(entry -> entry.getKey() instanceof Call)
      .map(entry -> new SimpleEntry<Call, Feature>((Call) entry.getKey(), entry.getValue()))
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
   * @param baseFeature
   * @return
   */
  public static SourcePosition endOfFeature(Feature baseFeature)
  {
    var uri = Converters.ToLocation(baseFeature.pos()).getUri();
    if (!Memory.EndOfFeature.containsKey(baseFeature))
      {
        SourcePosition endOfFeature = HeirsVisitor
          .visit(baseFeature)
          .entrySet()
          .stream()
          .filter(entry -> entry.getValue() != null)
          .filter(IsItemInFile(uri))
          .filter(entry -> entry.getValue().compareTo(baseFeature) == 0)
          .map(entry -> position(entry.getKey()))
          .sorted((Comparator<SourcePosition>) Comparator.<SourcePosition>reverseOrder())
          .map(position -> {
            return new SourcePosition(position._sourceFile, position._line,
              endOfToken(uri, Converters.ToPosition(position)).getCharacter() + 1);
          })
          .findFirst()
          .orElse(baseFeature.pos());

        Memory.EndOfFeature.put(baseFeature, endOfFeature);
      }

    return Memory.EndOfFeature.get(baseFeature);
  }

  public static boolean IsAnonymousInnerFeature(Feature f)
  {
    return f.featureName().baseName().startsWith("#");
  }

  /**
   * @param params
   * @return
   */
  public static Optional<Feature> featureAt(TextDocumentPositionParams params)
  {
    return ASTItemsOnLine(params)
      .map(astItem -> {
        if (astItem instanceof Feature)
          {
            return (Feature) astItem;
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
      .filter(f -> !IsFieldLike(f))
      // NYI maybe there is a better way?
      .filter(f -> !Util.HashSetOf("Object", "Function", "call").contains(f.featureName().baseName()))
      .findFirst();
  }

  /**
   * example: allOf(Call.class) returns all Calls
   * @param <T>
   * @param classOfT
   * @return
   */
  public static <T extends Object> Stream<T> allOf(String uri, Class<T> classOfT)
  {
    var mainFeature = ParserHelper.getMainFeature(uri);
    if (mainFeature.isEmpty())
      {
        return Stream.empty();
      }
    var universe = mainFeature.get().universe();
    return HeirsVisitor.visit(universe)
      .keySet()
      .stream()
      .filter(obj -> obj.getClass().equals(classOfT))
      .map(obj -> (T) obj);
  }

  /**
   * @param feature
   * @return all calls to this feature
   */
  public static Stream<Call> callsTo(String uri, Feature feature)
  {
    return allOf(uri, Call.class)
      .filter(call -> call.calledFeature().equals(feature));
  }

  public static Boolean IsValidIdentifier(String str)
  {
    var isIdentifier = Util.WithTextInputStream(str, () -> {
      var lexer = new Lexer(SourceFile.STDIN);
      return lexer.current() == Token.t_ident;
    });
    return isIdentifier;
  }

  /**
   * @param str example: "infix %%"
   * @return example: text: "%%", start: 7
   */
  public static TokenInfo nextTokenOfType(String str, HashSet<Token> tokens)
  {
    return Util.WithTextInputStream(str, () -> {
      var lexer = new Lexer(SourceFile.STDIN);

      while (lexer.current() != Token.t_eof && !tokens.contains(lexer.current()))
        {
          lexer.next();
        }
      return tokenInfo(lexer);
    });
  }

  // NYI rename this method to tokenAt
  public static TokenInfo nextToken(TextDocumentPositionParams params)
  {
    var sourceText = sourceText(params);
    return Util.WithTextInputStream(sourceText, () -> {

      var lexer = new Lexer(SourceFile.STDIN);
      lexer.setPos(lexer.lineStartPos(params.getPosition().getLine() + 1));

      while (lexer.current() != Token.t_eof
        && lexerEndPosIsBeforeOrAtTextDocumentPosition(params, lexer))
        {
          lexer.nextRaw();
        }
      return tokenInfo(lexer);
    });
  }

  private static String sourceText(TextDocumentPositionParams params)
  {
    String uri = params.getTextDocument().getUri();
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

  private static boolean lexerEndPosIsBeforeOrAtTextDocumentPosition(TextDocumentPositionParams params, Lexer lexer)
  {
    return (lexer.sourcePos()._column - 1) <= params.getPosition().getCharacter();
  }

  private static TokenInfo tokenInfo(Lexer lexer)
  {
    var start = lexer.sourcePos(lexer.pos());
    var tokenString = lexer.asString(lexer.pos(), lexer.bytePos());
    return new TokenInfo(start, tokenString);
  }

  public static String stringAt(String uri, Range range)
  {
    var optionalText = FuzionTextDocumentService.getText(uri);
    if (optionalText.isEmpty())
      {
        return "error";
      }
    var lines = optionalText.get()
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

  public static Position endOfToken(String uri, Position start)
  {
    var textDocumentPosition = new TextDocumentPositionParams(new TextDocumentIdentifier(uri), start);
    var token = nextToken(textDocumentPosition);
    return Converters.ToPosition(token.end());
  }

  private static Stream<Object> callsAndFeaturesAt(TextDocumentPositionParams params)
  {
    return ASTItemsOnLine(params)
      .filter(item -> Util.HashSetOf(Feature.class, Call.class).contains(item.getClass()));
  }

  /**
   * @param params
   * @return feature at textdocumentposition or empty
   */
  public static Optional<Feature> feature(TextDocumentPositionParams params)
  {
    Optional<Object> callOrFeature = callsAndFeaturesAt(params)
      .findFirst();

    if (callOrFeature.isEmpty())
      {
        return Optional.empty();
      }

    var item = callOrFeature.get();
    if (item instanceof Call)
      {
        return Optional.of(((Call) item).calledFeature());
      }
    return Optional.of((Feature) item);
  }

  public static Optional<TokenInfo> CallOrFeatureToken(TextDocumentPositionParams params)
  {
    var token = FuzionHelpers.nextToken(params);
    if (token == null)
      {
        return Optional.empty();
      }
    var column = token.start()._column;
    var isCallOrFeature = FuzionHelpers.callsAndFeaturesAt(params)
      .map(obj -> position(obj))
      .filter(pos -> column == pos._column)
      .findFirst()
      .isPresent();
    if (!isCallOrFeature)
      {
        return Optional.empty();
      }
    return Optional.of(token);
  }

  private static Stream<Feature> InheritedFeatures(Feature feature)
  {
    return feature.inherits.stream().flatMap(c -> {
      return Stream.concat(Stream.of(c.calledFeature()), InheritedFeatures(c.calledFeature()));
    });
  }

  public static Feature universe(TextDocumentPositionParams params)
  {
    return ParserHelper.getMainFeature(Util.getUri(params)).get().universe();
  }

  public static Stream<Feature> featuresIncludingInheritedFeatures(TextDocumentPositionParams params)
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

  public static MessageParams Run(String uri)
    throws IOException, InterruptedException, ExecutionException, TimeoutException
  {
    var result = Util.WithCapturedStdOutErr(() -> {
      var interpreter = new Interpreter(ParserHelper.FUIR(uri));
      interpreter.run();
    }, 10000);
    return result;
  }

}
