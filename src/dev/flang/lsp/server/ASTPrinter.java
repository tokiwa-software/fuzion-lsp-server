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
 * Source of class ASTPrinter
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.server;

import java.util.TreeMap;

import org.eclipse.lsp4j.MessageType;

import dev.flang.ast.Assign;
import dev.flang.ast.Block;
import dev.flang.ast.Box;
import dev.flang.ast.Call;
import dev.flang.ast.Case;
import dev.flang.ast.Cond;
import dev.flang.ast.Contract;
import dev.flang.ast.Current;
import dev.flang.ast.Destructure;
import dev.flang.ast.Expr;
import dev.flang.ast.Feature;
import dev.flang.ast.FeatureVisitor;
import dev.flang.ast.FormalGenerics;
import dev.flang.ast.Function;
import dev.flang.ast.Generic;
import dev.flang.ast.If;
import dev.flang.ast.Impl;
import dev.flang.ast.InlineArray;
import dev.flang.ast.Match;
import dev.flang.ast.ReturnType;
import dev.flang.ast.Stmnt;
import dev.flang.ast.Tag;
import dev.flang.ast.This;
import dev.flang.ast.Type;
import dev.flang.ast.Unbox;
import dev.flang.util.SourcePosition;

// NYI sort AST by line and column
public class ASTPrinter extends FeatureVisitor
{
  private ASTPrinter()
  {
  }

  /**
  * @return
  */
  private StringBuilder sb = new StringBuilder();
  private int indent = 0;

  public static String getAST(Feature baseFeature)
  {
    var visitor = new ASTPrinter();
    visitor.sb.append("Abstract syntax tree of: " + baseFeature.qualifiedName() + System.lineSeparator());
    visitor.sb.append("Legend: line:column:type_of_item:identifier" + System.lineSeparator());
    visitor.sb.append(System.lineSeparator());
    baseFeature.visit(visitor, baseFeature.outer());
    return visitor.sb.toString();
  }

  private void Print(String type, SourcePosition position, String name)
  {
    Print(type, position, name, null);
  }

  private void Print(String type, SourcePosition position, String name, Runnable inner)
  {
    sb.append(" ".repeat(indent * 2) + position._line + ":" + position._column + ":" + type + ":" + name
      + System.lineSeparator());
    indent++;
    if (inner != null)
      {
        inner.run();
      }
    indent--;
  }

  @Override
  public void action(Unbox u, Feature outer)
  {
    Print("Unbox", FuzionHelpers.position(u), u.toString());
  }

  @Override
  public void action(Assign a, Feature outer)
  {
    Print("Assign", FuzionHelpers.position(a), a._assignedField.qualifiedName());
  }

  @Override
  public void actionBefore(Block b, Feature outer)
  {
    Print("Block", FuzionHelpers.position(b), "");
  }

  @Override
  public void actionAfter(Block b, Feature outer)
  {
  }

  @Override
  public void action(Box b, Feature outer)
  {
    Print("Box", FuzionHelpers.position(b), b.toString());
  }


  @Override
  public Expr action(Call c, Feature outer)
  {
    Print("Call", FuzionHelpers.position(c), c.calledFeature().qualifiedName());
    return c;
  }


  @Override
  public void actionBefore(Case c, Feature outer)
  {
    Print("Case", FuzionHelpers.position(c), c.toString());
  }

  @Override
  public void actionAfter(Case c, Feature outer)
  {
  }

  @Override
  public void action(Cond c, Feature outer)
  {
    Print("Cond", FuzionHelpers.position(c), c.toString());
  }

  @Override
  public Expr action(Current c, Feature outer)
  {
    Print("Current", FuzionHelpers.position(c), c.toString());
    return c;
  }

  @Override
  public Stmnt action(Destructure d, Feature outer)
  {
    Print("Destructure", FuzionHelpers.position(d), d.toString());
    return d;
  }

  @Override
  public Stmnt action(Feature f, Feature outer)
  {
    Print("Feature", FuzionHelpers.position(f), f.qualifiedName(), () -> {
      var visitations = new TreeMap<Object, Feature>(FuzionHelpers.CompareBySourcePosition);

      Log.increaseIndentation();

      visitations.put(f.resultType(), f);

      visitations.put(f.generics, f);
      for(Call c : f.inherits)
        {
          visitations.put(c, f);
        }
      if (f.contract != null)
        {
          visitations.put(f.contract, f);
        }
      visitations.put(f.impl, f);
      visitations.put(f.returnType, f);

      f.declaredFeatures().forEach((n, feature) -> {
        visitations.put(feature, feature.outer());
      });

      visitations.forEach((key, value) -> doVisit(key, this, value));

      Log.decreaseIndentation();

    });
    return f;
  }

  private void doVisit(Object astItem, ASTPrinter visitor, Feature outer)
  {
    if (astItem instanceof Type)
      {
        ((Type) astItem).visit(visitor, outer);
        return;
      }
    if (astItem instanceof FormalGenerics)
      {
        ((FormalGenerics) astItem).visit(visitor, outer);
        return;
      }
    if (astItem instanceof Contract)
      {
        ((Contract) astItem).visit(visitor, outer);
        return;
      }
    if (astItem instanceof Impl)
      {
        ((Impl) astItem).visit(visitor, outer);
        return;
      }
    if (astItem instanceof ReturnType)
      {
        ((ReturnType) astItem).visit(visitor, outer);
        return;
      }
    if (astItem instanceof ReturnType)
      {
        ((ReturnType) astItem).visit(visitor, outer);
        return;
      }
    if (astItem instanceof Feature)
      {
        ((Feature) astItem).visit(visitor, outer);
        return;
      }
    if (astItem instanceof Call)
      {
        ((Call) astItem).visit(visitor, outer);
        return;
      }
    Log.message(astItem.getClass().toString(), MessageType.Error);
    Util.WriteStackTraceAndExit(1);
  }

  @Override
  public Expr action(Function f, Feature outer)
  {
    Print("Function", FuzionHelpers.position(f), "");
    return f;
  }

  @Override
  public void action(Generic g, Feature outer)
  {
    Print("Generic", FuzionHelpers.position(g), g.toString());
  }

  @Override
  public void action(If i, Feature outer)
  {
    Print("If", FuzionHelpers.position(i), "");
  }

  @Override
  public void action(Impl i, Feature outer)
  {
    Print("Impl", FuzionHelpers.position(i), "");
  }

  @Override
  public Expr action(InlineArray i, Feature outer)
  {
    Print("InlineArray", FuzionHelpers.position(i), i.toString());
    return super.action(i, outer);
  }

  @Override
  public void action(Match m, Feature outer)
  {
    Print("Match", FuzionHelpers.position(m), m.toString());
  }

  @Override
  public void action(Tag b, Feature outer)
  {
    Print("Tag", FuzionHelpers.position(b), b.toString());
  }

  @Override
  public Expr action(This t, Feature outer)
  {
    Print("This", FuzionHelpers.position(t), t.toString());
    return t;
  }

  @Override
  public Type action(Type t, Feature outer)
  {
    Print("Type", FuzionHelpers.position(t), t.toString());
    return t;
  }
}