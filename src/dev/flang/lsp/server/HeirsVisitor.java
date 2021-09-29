package dev.flang.lsp.server;

import java.util.Comparator;
import java.util.TreeMap;
import java.util.TreeSet;

import dev.flang.ast.Assign;
import dev.flang.ast.Block;
import dev.flang.ast.Box;
import dev.flang.ast.Call;
import dev.flang.ast.Case;
import dev.flang.ast.Cond;
import dev.flang.ast.Current;
import dev.flang.ast.Destructure;
import dev.flang.ast.Expr;
import dev.flang.ast.Feature;
import dev.flang.ast.FeatureVisitor;
import dev.flang.ast.Function;
import dev.flang.ast.Generic;
import dev.flang.ast.If;
import dev.flang.ast.Impl;
import dev.flang.ast.InitArray;
import dev.flang.ast.Match;
import dev.flang.ast.Stmnt;
import dev.flang.ast.Tag;
import dev.flang.ast.This;
import dev.flang.ast.Type;
import dev.flang.ast.Unbox;

/**
* visit every AST-Item in feature including heirs
* @param result
* @return
*/
public class HeirsVisitor extends FeatureVisitor
{
  // memorize already visited features
  private final TreeSet<Feature> VisitedFeatures = new TreeSet<>();
  private final TreeMap<Object, Feature> result;

  private HeirsVisitor()
  {
    this.result = new TreeMap<Object, Feature>(new Comparator<Object>() {
      public int compare(Object o1, Object o2)
      {
        return o1.equals(o2) ? 0: 1;
      }
    });
  }

  public static TreeMap<Object, Feature> visit(Feature baseFeature)
  {
    var visitor = new HeirsVisitor();
    baseFeature.visit(visitor, baseFeature.outer());
    return visitor.result;
  }

  @Override
  public void action(Unbox u, Feature outer)
  {
    result.put(u, outer);
  }

  @Override
  public void action(Assign a, Feature outer)
  {
    result.put(a, outer);
  }

  @Override
  public void actionBefore(Block b, Feature outer)
  {
    result.put(b, outer);
  }

  @Override
  public void actionAfter(Block b, Feature outer)
  {
    result.put(b, outer);
  }

  @Override
  public void action(Box b, Feature outer)
  {

    result.put(b, outer);
  }

  @Override
  public Expr action(Call c, Feature outer)
  {
    result.put(c, outer);
    if (this.VisitedFeatures.contains(c.calledFeature()) || FuzionHelpers.IsIntrinsic(c.calledFeature()))
      {
        return c;
      }
    Log.increaseIndentation();
    c.calledFeature().visit(this, c.calledFeature().outer());
    Log.decreaseIndentation();
    return c;
  }

  @Override
  public void actionBefore(Case c, Feature outer)
  {
    result.put(c, outer);
  }

  @Override
  public void actionAfter(Case c, Feature outer)
  {
    result.put(c, outer);
  }

  @Override
  public void action(Cond c, Feature outer)
  {
    // Cond has no SourcePosition, not including
  }

  @Override
  public Expr action(Current c, Feature outer)
  {
    result.put(c, outer);
    return c;
  }

  @Override
  public Stmnt action(Destructure d, Feature outer)
  {
    result.put(d, outer);
    return d;
  }

  @Override
  public Stmnt action(Feature f, Feature outer)
  {
    this.VisitedFeatures.add(f);

    result.put(f, outer);
    Log.increaseIndentation();

    // NYI their is also resultType and resultField?
    f.returnType.visit(this, outer);
    f.resultType().visit(this, outer);

    f.visit(this);

    // NYI declaredFeatures is correct/good?
    f.declaredFeatures().forEach((n, feature) -> {
      if (this.VisitedFeatures.contains(feature) || FuzionHelpers.IsIntrinsic(feature))
        {
          return;
        }
      feature.visit(this, feature.outer());
    });

    Log.decreaseIndentation();

    return f;
  }

  @Override
  public Expr action(Function f, Feature outer)
  {
    result.put(f, outer);
    return f;
  }

  @Override
  public void action(Generic g, Feature outer)
  {
    result.put(g, outer);
  }

  @Override
  public void action(If i, Feature outer)
  {
    result.put(i, outer);
  }

  @Override
  public void action(Impl i, Feature outer)
  {
    result.put(i, outer);
  }

  @Override
  public Expr action(InitArray i, Feature outer)
  {
    result.put(i, outer);
    return i;
  }

  @Override
  public void action(Match m, Feature outer)
  {
    result.put(m, outer);
  }

  @Override
  public void action(Tag b, Feature outer)
  {
    result.put(b, outer);
  }

  @Override
  public Expr action(This t, Feature outer)
  {
    result.put(t, outer);
    return t;
  }

  @Override
  public Type action(Type t, Feature outer)
  {
    result.put(t, outer);
    return t;
  }
}