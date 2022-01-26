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
 * Source of class ASTWalker
 *
 *---------------------------------------------------------------------*/

package dev.flang.shared;

import java.util.AbstractMap.SimpleEntry;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import dev.flang.ast.AbstractAssign;
import dev.flang.ast.AbstractBlock;
import dev.flang.ast.AbstractCall;
import dev.flang.ast.AbstractCase;
import dev.flang.ast.AbstractConstant;
import dev.flang.ast.AbstractCurrent;
import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AbstractMatch;
import dev.flang.ast.Box;
import dev.flang.ast.Check;
import dev.flang.ast.Expr;
import dev.flang.ast.Function;
import dev.flang.ast.If;
import dev.flang.ast.Nop;
import dev.flang.ast.Stmnt;
import dev.flang.ast.Tag;
import dev.flang.ast.Unbox;
import dev.flang.ast.Universe;

public class ASTWalker
{

  /**
   * depth first traversal, starting at feature
   * collects calls and features (=key) as well as their outer features (=value).
   * @param start
   * @return
   */
  public static Stream<Entry<Object, AbstractFeature>> Traverse(AbstractFeature start)
  {
    return Traverse(start, true);
  }

  public static Stream<Entry<Object, AbstractFeature>> Traverse(AbstractFeature start, boolean descend)
  {
    var result = new HashMap<Object, AbstractFeature>();
    TraverseFeature(start, (item, outer) -> {
      var isAlreadyPresent = result.containsKey(item);
      result.put(item, outer);
      return !isAlreadyPresent;
    }, descend);
    return result.entrySet().stream();
  }

  private static void TraverseFeature(AbstractFeature feature, BiFunction<Object, AbstractFeature, Boolean> callback,
    boolean descend)
  {
    if (!callback.apply(feature, feature.outer()))
      {
        return;
      }
    feature.arguments()
      .stream()
      .forEach(f -> TraverseFeature(f, callback, false));

    // feature.isRoutine() sometimes throws because it depends on
    // statically held Types.resolved.f_choice which may have been cleared
    // already.
    // We may remove wrapper ResultOrDefault in the future if this changes.
    if (ErrorHandling.ResultOrDefault(() -> feature.isRoutine(), true))
      {
        TraverseExpression(feature.code(), feature, callback);
      }

    feature.contract().req.forEach(x -> TraverseExpression(x.cond, feature.outer(), callback));
    feature.contract().ens.forEach(x -> TraverseExpression(x.cond, feature.outer(), callback));
    feature.contract().inv.forEach(x -> TraverseExpression(x.cond, feature.outer(), callback));

    // NYI do we need to traverse choicetag?

    if (descend)
      {
        FuzionParser.DeclaredFeatures(feature, true)
          .forEach(f -> TraverseFeature(f, callback, descend));
      }
  }

  private static void TraverseCase(AbstractCase c, AbstractFeature outer,
    BiFunction<Object, AbstractFeature, Boolean> callback)
  {
    TraverseBlock(c.code(), outer, callback);
  }

  private static void TraverseStatement(Stmnt s, AbstractFeature outer,
    BiFunction<Object, AbstractFeature, Boolean> callback)
  {
    if (ASTItem.IsAbstractFeature(s))
      {
        TraverseFeature((AbstractFeature) s, callback, false);
        return;
      }
    if (s instanceof Expr expr)
      {
        TraverseExpression(expr, outer, callback);
        return;
      }
    if (s instanceof Nop)
      {
        return;
      }
    if (s instanceof AbstractAssign a)
      {
        TraverseExpression(a._value, outer, callback);
        if (a._target != null)
          {
            TraverseExpression(a._target, outer, callback);
          }
        return;
      }
    if (s instanceof Check c)
      {
        return;
      }

    throw new RuntimeException("TraverseStatement not implemented for: " + s.getClass());
  }

  private static void TraverseBlock(AbstractBlock b, AbstractFeature outer,
    BiFunction<Object, AbstractFeature, Boolean> callback)
  {
    b.statements_.forEach(s -> TraverseStatement(s, outer, callback));
  }

  private static void TraverseExpression(Expr expr, AbstractFeature outer,
    BiFunction<Object, AbstractFeature, Boolean> callback)
  {
    if (expr == null)
      {
        return;
      }
    if (expr instanceof AbstractBlock b)
      {
        TraverseBlock(b, outer, callback);
        return;
      }
    if (expr instanceof AbstractMatch m)
      {
        TraverseExpression(m.subject(), outer, callback);
        m.cases().forEach(c -> TraverseCase(c, outer, callback));
        return;
      }
    if (expr instanceof AbstractCall c)
      {
        TraverseCall(c, outer, callback);
        return;
      }
    if (expr instanceof Tag t)
      {
        TraverseExpression(t._value, outer, callback);
        return;
      }
    if (expr instanceof Box b)
      {
        TraverseExpression(b._value, outer, callback);
        return;
      }
    if (expr instanceof If i)
      {
        TraverseExpression(i.cond, outer, callback);
        TraverseBlock(i.block, outer, callback);
        if (i.elseBlock != null)
          {
            TraverseBlock(i.elseBlock, outer, callback);
          }
        if (i.elseIf != null)
          {
            TraverseExpression(i.elseIf, outer, callback);
          }
        return;
      }
    if (expr instanceof AbstractCurrent
      || expr instanceof Unbox
      || expr instanceof AbstractConstant
      || expr instanceof Universe
      || expr instanceof Function)
      {
        return;
      }
    throw new RuntimeException("TraverseExpression not implemented for: " + expr.getClass());
  }

  private static void TraverseCall(AbstractCall c, AbstractFeature outer,
    BiFunction<Object, AbstractFeature, Boolean> callback)
  {
    if (!callback.apply(c, outer))
      {
        return;
      }
    c.actuals().forEach(a -> TraverseExpression(a, outer, callback));
    // this should be enough to not run into an infinite recursion...
    if (c.target() == null || !IsSameSourceFile(c.target(), outer))
      {
        return;
      }
    TraverseExpression(c.target(), outer, callback);
  }

  private static boolean IsSameSourceFile(Expr e, AbstractFeature outer)
  {
    return e.pos()._sourceFile.equals(outer.pos()._sourceFile);
  }

  /**
   * @param start
   * @return any calls - and their outer features - happening in feature start or descending features of start
   */
  public static Stream<SimpleEntry<AbstractCall, AbstractFeature>> Calls(AbstractFeature start)
  {
    return Traverse(start)
      .filter(entry -> {
        return AbstractCall.class.isAssignableFrom(entry.getKey().getClass());
      })
      .map(obj -> new SimpleEntry<>((AbstractCall) obj.getKey(), obj.getValue()));
  }

}
