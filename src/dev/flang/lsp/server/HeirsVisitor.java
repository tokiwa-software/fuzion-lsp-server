package dev.flang.lsp.server;

import java.util.TreeSet;
import java.util.function.Predicate;

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
* visit everything in feature including heirs
* add to result if predicate is true
* @param result
* @param addToResult
* @return
*/
public class HeirsVisitor extends FeatureVisitor
{
  // memorize already visited features
  private final TreeSet<Feature> VisitedFeatures = new TreeSet<>();
  // predicate to decide if AST item should be added to result set
  private final Predicate<? super Object> addToResult;
  private final TreeSet<Object> result;
  // for which uri this visitor was created
  private final String uri;

  public HeirsVisitor(TreeSet<Object> result, Predicate<? super Object> addToResult, String uri)
  {
    if (result.comparator() == null)
      {
        System.err.println("no comparator");
        System.exit(1);
      }
    this.result = result;
    this.addToResult = addToResult;
    this.uri = uri;
  }

  @Override
  public void action(Unbox u, Feature outer)
  {
    if (addToResult.test(u))
      {
        result.add(u);
      }
  }

  @Override
  public void action(Assign a, Feature outer)
  {
    if (addToResult.test(a))
      {
        result.add(a);
      }
  }

  @Override
  public void actionBefore(Block b, Feature outer)
  {
    if (addToResult.test(b))
      {
        result.add(b);
      }
  }

  @Override
  public void actionAfter(Block b, Feature outer)
  {
    if (addToResult.test(b))
      {
        result.add(b);
      }
  }

  @Override
  public void action(Box b, Feature outer)
  {

    if (addToResult.test(b))
      {
        result.add(b);
      }
  }

  @Override
  public Expr action(Call c, Feature outer)
  {
    if (addToResult.test(c))
      {
        result.add(c);
      }
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
    if (addToResult.test(c))
      {
        result.add(c);
      }
  }

  @Override
  public void actionAfter(Case c, Feature outer)
  {
    if (addToResult.test(c))
      {
        result.add(c);
      }
  }

  @Override
  public void action(Cond c, Feature outer)
  {
    // Cond has no SourcePosition, not including
  }

  @Override
  public Expr action(Current c, Feature outer)
  {
    if (addToResult.test(c))
      {
        result.add(c);
      }
    return c;
  }

  @Override
  public Stmnt action(Destructure d, Feature outer)
  {
    if (addToResult.test(d))
      {
        result.add(d);
      }
    return d;
  }

  @Override
  public Stmnt action(Feature f, Feature outer)
  {
    this.VisitedFeatures.add(f);
    if (addToResult.test(f))
      {
        result.add(f);
      }
    Log.increaseIndentation();

    // NYI their is also resultType and resultField?
    f.returnType.visit(this, outer);
    f.resultType().visit(this, outer);

    f.visit(this);

    // NYI declaredFeatures is correct/good?
    f.declaredFeatures().forEach((n, feature) -> {
      if (this.VisitedFeatures.contains(feature) || FuzionHelpers.IsIntrinsic(feature)
          || !IsFeatureInUri(feature))
        {
          return;
        }
      feature.visit(this, feature.outer());
    });

    Log.decreaseIndentation();
    return f;
  }

  private boolean IsFeatureInUri(Feature feature)
  {
    return uri.equals(ParserHelper.getUri(feature.pos()));
  }

  @Override
  public Expr action(Function f, Feature outer)
  {
    if (addToResult.test(f))
      {
        result.add(f);
      }
    return f;
  }

  @Override
  public void action(Generic g, Feature outer)
  {
    if (addToResult.test(g))
      {
        result.add(g);
      }
  }

  @Override
  public void action(If i, Feature outer)
  {
    if (addToResult.test(i))
      {
        result.add(i);
      }
  }

  @Override
  public void action(Impl i, Feature outer)
  {
    if (addToResult.test(i))
      {
        result.add(i);
      }
  }

  @Override
  public Expr action(InitArray i, Feature outer)
  {
    if (addToResult.test(i))
      {
        result.add(i);
      }
    return i;
  }

  @Override
  public void action(Match m, Feature outer)
  {
    if (addToResult.test(m))
      {
        result.add(m);
      }
  }

  @Override
  public void action(Tag b, Feature outer)
  {
    if (addToResult.test(b))
      {
        result.add(b);
      }
  }

  @Override
  public Expr action(This t, Feature outer)
  {
    if (addToResult.test(t))
      {
        result.add(t);
      }
    return t;
  }

  @Override
  public Type action(Type t, Feature outer)
  {
    if (addToResult.test(t))
      {
        result.add(t);
      }
    return t;
  }
}