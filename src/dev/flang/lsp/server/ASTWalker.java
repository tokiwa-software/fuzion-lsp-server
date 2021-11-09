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

package dev.flang.lsp.server;

import java.util.Comparator;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import dev.flang.ast.AbstractFeature;
import dev.flang.ast.Assign;
import dev.flang.ast.Block;
import dev.flang.ast.BoolConst;
import dev.flang.ast.Box;
import dev.flang.ast.Call;
import dev.flang.ast.Case;
import dev.flang.ast.Check;
import dev.flang.ast.Current;
import dev.flang.ast.Expr;
import dev.flang.ast.Function;
import dev.flang.ast.If;
import dev.flang.ast.Match;
import dev.flang.ast.Nop;
import dev.flang.ast.NumLiteral;
import dev.flang.ast.ReturnType;
import dev.flang.ast.Stmnt;
import dev.flang.ast.StrConst;
import dev.flang.ast.Tag;
import dev.flang.ast.Type;
import dev.flang.ast.Unbox;
import dev.flang.ast.Universe;
import dev.flang.lsp.server.util.ASTItem;
import dev.flang.lsp.server.util.FeatureTool;
import dev.flang.lsp.server.util.FuzionParser;

public class ASTWalker
{

  public static Stream<Entry<Object, AbstractFeature>> Traverse(AbstractFeature start)
  {
    var result = new TreeMap<Object, AbstractFeature>(new Comparator<Object>() {
      public int compare(Object o1, Object o2)
      {
        return o1.equals(o2) ? 0: 1;
      }
    });
    TraverseFeature(start, (item, outer) -> {
      var isAlreadyPresent = result.containsKey(item);
      result.put(item, outer);
      return !isAlreadyPresent;
    });
    return result.entrySet().stream();
  }

  private static void TraverseFeature(AbstractFeature feature, BiFunction<Object, AbstractFeature, Boolean> callback)
  {
    if (!callback.apply(feature, feature.outer()))
      {
        return;
      }
    feature.arguments()
      .stream()
      .forEach(f -> TraverseFeature(f, callback));

    TraverseReturnType(feature.returnType(), feature, callback);
    TraverseType(feature.resultType(), feature, callback);

    if (FeatureTool.IsRoutineOrRoutineDef(feature))
      {
        TraverseExpression(feature.code(), feature, callback);
      }

    FuzionParser.AllDeclaredFeatures(feature)
      .forEach(f -> TraverseFeature(f, callback));
  }

  private static void TraverseReturnType(ReturnType returnType, AbstractFeature outer,
    BiFunction<Object, AbstractFeature, Boolean> callback)
  {
    if (!callback.apply(returnType, outer))
      {
        return;
      }
  }

  private static void TraverseCase(Case c, AbstractFeature outer, BiFunction<Object, AbstractFeature, Boolean> callback)
  {
    if (!callback.apply(c, outer))
      {
        return;
      }
    TraverseBlock(c.code, outer, callback);
  }

  private static void TraverseStatement(Stmnt s, AbstractFeature outer,
    BiFunction<Object, AbstractFeature, Boolean> callback)
  {
    if (ASTItem.IsAbstractFeature(s))
      {
        TraverseFeature((AbstractFeature) s, callback);
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
    if (s instanceof Assign a)
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

    throw new RuntimeException("TraverseStatement NYI: " + s.getClass());
  }

  private static void TraverseBlock(Block b, AbstractFeature outer,
    BiFunction<Object, AbstractFeature, Boolean> callback)
  {
    if (!callback.apply(b, outer))
      {
        return;
      }
    b.statements_.forEach(s -> TraverseStatement(s, outer, callback));
  }

  private static void TraverseType(Type t, AbstractFeature outer, BiFunction<Object, AbstractFeature, Boolean> callback)
  {
    if (!callback.apply(t, outer))
      {
        return;
      }
  }

  private static void TraverseExpression(Expr expr, AbstractFeature outer,
    BiFunction<Object, AbstractFeature, Boolean> callback)
  {
    if (expr == null)
      {
        return;
      }
    if (expr instanceof Block b)
      {
        TraverseBlock(b, outer, callback);
        return;
      }
    if (expr instanceof Match m)
      {
        TraverseExpression(m.subject, outer, callback);
        m.cases.forEach(c -> TraverseCase(c, outer, callback));
        return;
      }
    if (expr instanceof Call c)
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
    if (expr instanceof Function f)
      {
        TraverseType(f.type(), outer, callback);
        return;
      }

    if (expr instanceof Current || expr instanceof NumLiteral || expr instanceof Unbox || expr instanceof BoolConst
      || expr instanceof StrConst || expr instanceof Universe)
      {
        return;
      }
    throw new RuntimeException("TraverseExpression NYI: " + expr.getClass());
  }

  private static void TraverseCall(Call c, AbstractFeature outer, BiFunction<Object, AbstractFeature, Boolean> callback)
  {
    if (!callback.apply(c, outer))
      {
        return;
      }
    c.generics.forEach(g -> TraverseType(g, outer, callback));
    c._actuals.forEach(a -> TraverseExpression(a, outer, callback));
    // this should be enough to not run into an infinite recursion...
    if (!IsSameSourceFile(c.target, outer))
      {
        return;
      }
    TraverseExpression(c.target, outer, callback);
  }

  private static boolean IsSameSourceFile(Expr e, AbstractFeature outer)
  {
    return e.pos()._sourceFile.equals(outer.pos()._sourceFile);
  }

}
