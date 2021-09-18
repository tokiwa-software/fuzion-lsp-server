package dev.flang.lsp.server;

import java.util.Comparator;
import java.util.Optional;
import java.util.TreeSet;
import java.util.Map.Entry;
import java.util.stream.Stream;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentPositionParams;

import dev.flang.util.SourcePosition;
import dev.flang.ast.*;
import dev.flang.ast.Impl.Kind;

public class FuzionHelpers
{

  public static Position ToPosition(SourcePosition sourcePosition)
  {
    return new Position(sourcePosition._line - 1, sourcePosition._column - 1);
  }

  public static Location ToLocation(SourcePosition sourcePosition)
  {
    var position = ToPosition(sourcePosition);
    return new Location(ParserHelper.getUri(sourcePosition), new Range(position, position));
  }

  // NYI remove once we have ISourcePosition interface
  /**
   * getPosition of ASTItem
   * @param entry
   * @return
   */
  public static SourcePosition getPosition(Object entry)
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

    System.out.println("not implemented: " + entry.getClass());
    System.exit(1);
    return null;
  }

  /**
   * given a TextDocumentPosition return all matching ASTItems
   * in the given file on the given line.
   * sorted by position descending.
   * @param params
   * @return
   */
  public static Stream<Object> getASTItemsOnLine(TextDocumentPositionParams params)
  {
    var baseFeature = getBaseFeature(params);
    if (baseFeature.isEmpty())
      {
        return Stream.empty();
      }

    var astItems = HeirsVisitor.visit(baseFeature.get())
      .entrySet()
      .stream()
      .filter(IsItemNotBuiltIn(params))
      .filter(IsItemInFile(params))
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
      var sourcePosition = FuzionHelpers.getPosition(astItem);
      return cursorPosition.getLine() == FuzionHelpers.ToPosition(sourcePosition).getLine();
    };
  }

  private static Predicate<? super Entry<Object, Feature>> IsItemNotBuiltIn(TextDocumentPositionParams params)
  {
    return (entry) -> {
      var astItem = entry.getKey();
      var sourcePosition = FuzionHelpers.getPosition(astItem);
      return !sourcePosition.isBuiltIn();
    };
  }

  private static Predicate<? super Entry<Object, Feature>> IsItemInFile(TextDocumentPositionParams params)
  {
    return (entry) -> {
      var uri = Util.getUri(params);
      var sourcePosition = FuzionHelpers.getPosition(entry.getKey());
      return uri.equals(ParserHelper.getUri(sourcePosition));
    };
  }

  private static Predicate<? super Entry<Object, Feature>> IsItemInScope(TextDocumentPositionParams params)
  {
    return (entry) -> {
      var astItem = entry.getKey();
      var outer = entry.getValue();
      var cursorPosition = Util.getPosition(params);

      var sourcePosition = FuzionHelpers.getPosition(astItem);

      boolean EndOfOuterFeatureIsAfterCursorPosition = Util.ComparePosition(cursorPosition,
        FuzionHelpers.ToPosition(FuzionHelpers.getEndOfFeature(outer))) <= 0;
      boolean ItemPositionIsBeforeOrAtCursorPosition =
        Util.ComparePosition(cursorPosition, FuzionHelpers.ToPosition(sourcePosition)) >= 0;

      return ItemPositionIsBeforeOrAtCursorPosition && EndOfOuterFeatureIsAfterCursorPosition;
    };
  }

  /**
   * returns the outermost feature found in uri
   * @param uri
   * @return
   */
  private static Optional<Feature> getBaseFeature(TextDocumentPositionParams params)
  {
    var universe = Memory.getMain().universe();
    var baseFeature = HeirsVisitor.visit(universe)
      .keySet()
      .stream()
      .filter(obj -> obj instanceof Feature)
      .map(obj -> (Feature) obj)
      .filter(IsFeatureInFile(Util.getUri(params)))
      .sorted(CompareBySourcePosition)
      .findFirst();
    if (baseFeature.isPresent())
      {
        Log.write("baseFeature: " + baseFeature.get().qualifiedName());
      }
    return baseFeature;
  }

  public static Stream<Feature> getParentFeatures(TextDocumentPositionParams params)
  {
    // find innermost then outer() until at universe
    return Stream.empty();
  }

  private static Predicate<? super Feature> IsFeatureInFile(String uri)
  {
    return feature -> {
      return uri.equals(ParserHelper.getUri(feature.pos()));
    };
  }

  private static Comparator<? super Object> CompareBySourcePosition =
    Comparator.comparing(obj -> obj, (obj1, obj2) -> {
      var result = getPosition(obj1).compareTo(getPosition(obj2));
      if (result != 0)
        {
          return result;
        }
      return obj1.equals(obj2) ? 0: 1;
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
    var baseFeature = getBaseFeature(params);
    if (baseFeature.isEmpty())
      {
        return Stream.empty();
      }

    return callsSortedDesc(baseFeature.get(), params)
      .stream()
      .filter(c -> !IsAnonymousInnerFeature(c.calledFeature()))
      .filter(c -> c.calledFeature().resultType() != Types.t_ERROR)
      .map(c -> {
        Log.write("call: " + c.pos().toString());
        return c.calledFeature();
      });
  }

  /**
   * NYI replace by real end of feature once we have this information in the AST
   * !!!CACHED via Memory.EndOfFeature!!!
   * @param baseFeature
   * @return
   */
  public static SourcePosition getEndOfFeature(Feature baseFeature)
  {
    if (!Memory.EndOfFeature.containsKey(baseFeature))
      {
        SourcePosition endOfFeature = HeirsVisitor
          .visit(baseFeature)
          .entrySet()
          .stream()
          .filter(entry -> entry.getValue() != null)
          .filter(entry -> entry.getValue().compareTo(baseFeature) == 0)
          .map(entry -> getPosition(entry.getKey()))
          .sorted((Comparator<SourcePosition>) Comparator.<SourcePosition>reverseOrder())
          .map(position -> {
            return new SourcePosition(position._sourceFile, position._line, getEndColumn(position));
          })
          .findFirst()
          .orElse(baseFeature.pos());

        Memory.EndOfFeature.put(baseFeature, endOfFeature);
      }

    return Memory.EndOfFeature.get(baseFeature);
  }

  private static int getEndColumn(SourcePosition start)
  {
    var uri = ParserHelper.getUri(start);
    var line_text = FuzionTextDocumentService.getText(uri).split("\\R", -1)[start._line - 1];
    var column = start._column;
    while (line_text.length() > column && !Util.HashSetOf(')', '.', ' ').contains(line_text.charAt(column - 1)))
      {
        column++;
      }
    return column;
  }

  private static TreeSet<Call> callsSortedDesc(Feature baseFeature, TextDocumentPositionParams params)
  {
    var result = new TreeSet<Call>(CompareBySourcePosition.reversed());
    baseFeature.visit(new FeatureVisitor() {
      @Override
      public Expr action(Call c, Feature outer)
      {
        if (ItemIsAfterCursor(params, c.pos()))
          {
            return super.action(c, outer);
          }
        if (ItemIsBeforeCursor(params, getEndOfFeature(outer)))
          {
            return super.action(c, outer);
          }
        result.add(c);
        return super.action(c, outer);
      }

      private boolean ItemIsAfterCursor(TextDocumentPositionParams params, SourcePosition sourcePosition)
      {
        return Util.ComparePosition(Util.getPosition(params), ToPosition(sourcePosition)) < 0;
      }

      private boolean ItemIsBeforeCursor(TextDocumentPositionParams params, SourcePosition sourcePosition)
      {
        return Util.ComparePosition(Util.getPosition(params), ToPosition(sourcePosition)) > 0;
      }

      @Override
      public Stmnt action(Feature f, Feature outer)
      {
        f.visit(this);
        f.declaredFeatures().forEach((n, df) -> df.visit(this, f));
        return super.action(f, outer);
      }
    }, baseFeature.outer());
    return result;
  }

  /**
   * @param feature
   * @return example: array<T>(length i32, init Function<array.T, i32>) => array<array.T>
   */
  public static String getLabel(Feature feature)
  {
    if (!IsRoutineOrRoutineDef(feature))
      {
        return feature.featureName().baseName();
      }
    var arguments = "(" + feature.arguments.stream()
      .map(a -> a.thisType().featureOfType().featureName().baseName() + " " + a.thisType().featureOfType().resultType())
      .collect(Collectors.joining(", ")) + ")";
    return feature.featureName().baseName() + feature.generics + arguments + " => " + feature.resultType();
  }

  public static boolean IsAnonymousInnerFeature(Feature f)
  {
    return f.featureName().baseName().startsWith("#");
  }

  public static Stream<Feature> getFeaturesDesc(TextDocumentPositionParams params)
  {
    var result = getASTItemsOnLine(params)
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
      .filter(f -> !IsAnonymousInnerFeature(f))
      // NYI maybe there is a better way?
      .filter(f -> !Util.HashSetOf("Object", "Function", "call").contains(f.featureName().baseName()));

    return result;
  }

}
