package dev.flang.lsp.server;

import java.util.Comparator;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentPositionParams;

import dev.flang.util.SourcePosition;
import dev.flang.ast.Call;
import dev.flang.ast.Case;
import dev.flang.ast.Feature;
import dev.flang.ast.FeatureVisitor;
import dev.flang.ast.Generic;
import dev.flang.ast.Impl;
import dev.flang.ast.Stmnt;
import dev.flang.ast.Type;
import dev.flang.ast.Types;
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
   * given a TextDocumentPosition return all ASTItems
   * in the given file on the given line and before given character position.
   * @param params
   * @return
   */
  public static TreeSet<Object> getASTItemsOnLine(TextDocumentPositionParams params)
  {
    var uri = Util.getUri(params);
    var position = params.getPosition();

    var baseFeature = getBaseFeature(uri);
    if (baseFeature.isEmpty())
      {
        Log.write("no matching feature found for: " + uri);
        return new TreeSet<>();
      }

    var astItems = doVisitation(baseFeature.get(), uri, position);

    if (astItems.isEmpty())
      {
        Log.write("no matching AST items found");
        return astItems;
      }

    var maxColumn = astItems.stream().map(x -> getPosition(x)._column).max(Integer::compare).get();
    return astItems.stream()
      .filter(obj -> getPosition(obj).isBuiltIn() || getPosition(obj)._column == maxColumn)
      .map(astItem -> {
        Log.write("found: " + getPosition(astItem).toString() + ":" + astItem.getClass());
        return astItem;
      })
      .collect(Collectors.toCollection(() -> new TreeSet<>(FuzionHelpers.compareASTItems)));
  }

  private static TreeSet<Object> doVisitation(Feature baseFeature, String uri, Position position)
  {
    var astItems = new TreeSet<>(FuzionHelpers.compareASTItems);
    var visitor = new HeirsVisitor(astItems, IsItemInFileAndOnLineAndBeforeCharacter(uri, position));
    Log.write("starting visitation at: " + baseFeature.qualifiedName());
    baseFeature.visit(visitor, baseFeature.outer());
    return astItems;
  }

  /**
   * returns the outermost feature found in uri
   * @param uri
   * @return
   */
  private static Optional<Feature> getBaseFeature(String uri)
  {
    return getAllFeatures(uri).findFirst();
  }

  public static Stream<Feature> getAllFeatures(String uri)
  {
    var universe = Memory.Main.universe();
    var allFeatures = getAllFeatures(universe);

    return allFeatures.stream().filter(IsFeatureInFile(uri));
  }

  public static Stream<Feature> getParentFeatures(TextDocumentPositionParams params)
  {
    return getAllFeatures(params.getTextDocument().getUri()).filter(IsParentFeature(params.getPosition()));
  }

  /**
   * NYI make more precise
   * @param position
   * @return
   */
  private static Predicate<? super Feature> IsParentFeature(Position position)
  {
    return feature -> {
      var positionOfFeature = ToPosition(feature.pos);
      // feature has to be in same line or before and before cursor position
      return (position.getLine() >= positionOfFeature.getLine()
        && position.getCharacter() > positionOfFeature.getCharacter());
    };
  }

  private static Predicate<? super Feature> IsFeatureInFile(String uri)
  {
    return feature -> {
      return uri.equals(ParserHelper.getUri(feature.pos()));
    };
  }

  /**
   * @param baseFeature
   * @return all descending features of base feature
   */
  private static TreeSet<Feature> getAllFeatures(Feature baseFeature)
  {
    var allFeatures = new TreeSet<Feature>(CompareBySourcePosition);
    baseFeature.visit(new FeatureVisitor() {
      @Override
      public Stmnt action(Feature f, Feature outer)
      {
        allFeatures.add(f);
        f.declaredFeatures().forEach((n, df) -> df.visit(this, f));
        return super.action(f, outer);
      }
    }, baseFeature.outer());
    return allFeatures;
  }

  private static Comparator<? super Feature> CompareBySourcePosition =
    Comparator.comparing(feature -> feature.pos, (position1, position2) -> {
      return position1.compareTo(position2);
    });

  private static Predicate<? super Object> IsItemInFileAndOnLineAndBeforeCharacter(String uri, Position position)
  {
    return astItem -> {
      var sourcePosition = getPosition(astItem);
      // Log.write("visiting: " + sourcePosition.toString() + ":" +
      // astItem.getClass());

      // NYI what can we do with built in stuff?
      if (sourcePosition.isBuiltIn())
        {
          return false;
        }
      if (position.getLine() != sourcePosition._line - 1 || !uri.equals(ParserHelper.getUri(sourcePosition)))
        {
          return false;
        }

      return sourcePosition._column - 1 <= position.getCharacter();
    };
  }

  private static Comparator<? super Object> compareASTItems = Comparator.comparing(obj -> obj, (astItem1, astItem2) -> {
    // we don't care about order thus always return 1 if not same
    return astItem1.equals(astItem2) ? 0: 1;
  });

  public static boolean IsRoutineOrRoutineDef(Feature feature)
  {
    return IsRoutineOrRoutineDef(feature.impl);
  }

  public static boolean IsRoutineOrRoutineDef(Impl impl)
  {
    return Util.HashSetOf(Kind.Routine, Kind.RoutineDef).contains(impl.kind_);
  }

  public static boolean IsIntrinsic(Feature feature)
  {
    return IsIntrinsic(feature.impl);
  }

  public static boolean IsIntrinsic(Impl impl)
  {
    return impl.kind_ == Kind.Intrinsic;
  }

  public static Stream<Feature> getCalledFeatures(TextDocumentPositionParams params)
  {
    var suitableItems = getASTItemsOnLine(params).stream();
    // NYI what do we actually want to/can do here?
    Stream<Feature> calledFeatures = suitableItems
      .map(astItem -> {
        if (astItem instanceof Call)
          {
            var calledFeature = ((Call) astItem).calledFeature();
            if (calledFeature != Types.f_ERROR)
              {
                return calledFeature;
              }
          }
        return null;
      })
      .filter(o -> o != null);
    return calledFeatures;
  }

}
