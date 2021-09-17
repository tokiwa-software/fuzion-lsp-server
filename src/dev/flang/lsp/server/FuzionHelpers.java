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

import dev.flang.util.SourceFile;
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
  public static TreeSet<Object> getASTItemsOnLine(TextDocumentPositionParams params)
  {
    var baseFeature = getBaseFeature(params);
    if (baseFeature.isEmpty())
      {
        return new TreeSet<>();
      }

    var astItems = doVisitation(baseFeature.get(), params);

    if (astItems.isEmpty())
      {
        Log.write("no matching AST items found");
        return astItems;
      }

    return astItems.stream()
      .map(astItem -> {
        Log.write("found: " + getPosition(astItem).toString() + ":" + astItem.getClass());
        return astItem;
      })
      .collect(Collectors.toCollection(() -> new TreeSet<>(FuzionHelpers.CompareBySourcePositionDesc)));
  }

  private static TreeSet<Object> doVisitation(Feature baseFeature, TextDocumentPositionParams params)
  {
    var astItems = new TreeSet<>(FuzionHelpers.compareASTItems);
    var visitor = new HeirsVisitor(astItems, params);
    baseFeature.visit(visitor, baseFeature.outer());
    return astItems;
  }

  /**
   * returns the outermost feature found in uri
   * @param uri
   * @return
   */
  private static Optional<Feature> getBaseFeature(TextDocumentPositionParams params)
  {
    var universe = Memory.getMain().universe();
    var baseFeature = getAllFeatures(universe).stream()
      .filter(IsFeatureInFile(Util.getUri(params)))
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

  public static Comparator<? super Object> CompareBySourcePositionDesc =
    Comparator.comparing(obj -> obj, (obj1, obj2) -> {
      var result = getPosition(obj1).compareTo(getPosition(obj2));
      if (result != 0)
        {
          return result;
        }
      return obj1.equals(obj2) ? 0: 1;
    }).reversed();

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

  public static Stream<Feature> calledFeaturesSortedDesc(TextDocumentPositionParams params)
  {
    var baseFeature = getBaseFeature(params);
    if (baseFeature.isEmpty())
      {
        return Stream.empty();
      }

    var calls = callsSortedDesc(baseFeature.get(), params);

    return calls.stream()
      .map(c -> c.calledFeature());
  }

  /**
   * NYI replace by real end of feature once we have this information in the AST
   * !!!CACHED via Memory.EndOfFeature!!!
   * @param baseFeature
   * @return
   */
  public static SourcePosition getEndOfFeature(Feature baseFeature)
  {
    if (Memory.EndOfFeature.containsKey(baseFeature))
      {
        return Memory.EndOfFeature.get(baseFeature);
      }
    var positions = new TreeSet<SourcePosition>();
    baseFeature.visit(new FeatureVisitor() {
      @Override
      public Stmnt action(Feature f, Feature outer)
      {
        positions.add(getPosition(f));
        f.visit(this);
        f.declaredFeatures().forEach((n, df) -> df.visit(this, f));
        return super.action(f, outer);
      }

      @Override
      public void action(Unbox u, Feature outer)
      {
        positions.add(getPosition(u));
      }

      @Override
      public void action(Assign a, Feature outer)
      {
        positions.add(getPosition(a));
      }

      @Override
      public void actionBefore(Block b, Feature outer)
      {
        positions.add(getPosition(b));
      }

      @Override
      public void actionAfter(Block b, Feature outer)
      {
        positions.add(getPosition(b));
      }

      @Override
      public void action(Box b, Feature outer)
      {

        positions.add(getPosition(b));
      }

      @Override
      public Expr action(Call c, Feature outer)
      {
        positions.add(getPosition(c));
        return c;
      }

      @Override
      public void actionBefore(Case c, Feature outer)
      {
        positions.add(getPosition(c));
      }

      @Override
      public void actionAfter(Case c, Feature outer)
      {
        positions.add(getPosition(c));
      }

      @Override
      public void action(Cond c, Feature outer)
      {
        // Cond has no SourcePosition, not including
      }

      @Override
      public Expr action(Current c, Feature outer)
      {
        positions.add(getPosition(c));
        return c;
      }

      @Override
      public Stmnt action(Destructure d, Feature outer)
      {
        positions.add(getPosition(d));
        return d;
      }

      @Override
      public Expr action(Function f, Feature outer)
      {
        positions.add(getPosition(f));
        return f;
      }

      @Override
      public void action(Generic g, Feature outer)
      {
        positions.add(getPosition(g));
      }

      @Override
      public void action(If i, Feature outer)
      {
        positions.add(getPosition(i));
      }

      @Override
      public void action(Impl i, Feature outer)
      {
        positions.add(getPosition(i));
      }

      @Override
      public Expr action(InitArray i, Feature outer)
      {
        positions.add(getPosition(i));
        return i;
      }

      @Override
      public void action(Match m, Feature outer)
      {
        positions.add(getPosition(m));
      }

      @Override
      public void action(Tag b, Feature outer)
      {
        positions.add(getPosition(b));
      }

      @Override
      public Expr action(This t, Feature outer)
      {
        positions.add(getPosition(t));
        return t;
      }

      @Override
      public Type action(Type t, Feature outer)
      {
        positions.add(getPosition(t));
        return t;
      }
    }, baseFeature.outer());

    System.out.println(
      "end of feature: " + getLabel(baseFeature) + ":" + positions.last()._line + ":" + positions.last()._column);

    Memory.EndOfFeature.put(baseFeature, positions.last());

    return positions.last();
  }

  private static TreeSet<Call> callsSortedDesc(Feature baseFeature, TextDocumentPositionParams params)
  {
    var allCalls = new TreeSet<Call>(CompareBySourcePositionDesc);
    baseFeature.visit(new FeatureVisitor() {
      @Override
      public Expr action(Call c, Feature outer)
      {
        // NYI consider begin as well
        if (Util.ComparePosition(Util.getPosition(params), ToPosition(getEndOfFeature(outer))) <= 0)
          {
            allCalls.add(c);
          }
        return super.action(c, outer);
      }

      @Override
      public Stmnt action(Feature f, Feature outer)
      {
        f.visit(this);
        f.declaredFeatures().forEach((n, df) -> df.visit(this, f));
        return super.action(f, outer);
      }
    }, baseFeature.outer());
    return allCalls;
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

  public static Stream<Feature> getFeatures(TextDocumentPositionParams params)
  {
    var result = getASTItemsOnLine(params)
      .stream()
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
      .filter(f -> IsRoutineOrRoutineDef(f))
      .filter(f -> !IsAnonymousInnerFeature(f))
      // NYI maybe there is a better way?
      .filter(f -> !Util.HashSetOf("Object", "Function", "call").contains(f.featureName().baseName()));

    return result;
  }

}
