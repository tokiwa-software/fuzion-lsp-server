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
 * Source of class ASTtoHTML
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

public class ASTtoHTML extends FeatureVisitor
{
  private ASTtoHTML(){
  }
  /**
  * example: ASTPrinter.printAST(feature)
  * @return
  <script>
    function toggle(that){
      this.event.stopPropagation();
      Array.from(that.children).forEach(child => child.tagName ==='UL' ? child.classList.toggle('d-none') : 0);
    }
  </script>

  <ul>
    <li onclick="toggle(this)">Feature:1:1:myfeat
    </li>
    ...
  </ul>
  */
  public static void printAST(Feature baseFeature)
  {
    var visitor = new ASTtoHTML();
    Log.message("""
      <script>
      function toggle(that){
        this.event.stopPropagation();
        Array.from(that.children).forEach(child => child.tagName ==='UL' ? child.classList.toggle('d-none') : 0);
      }
      </script>
      """);
    Log.message("<ul>");
    baseFeature.visit(visitor, baseFeature.outer());
    Log.message("</ul>");
  }

  private void Print(String type, String position, String name)
  {
    Print(type, position, name, null);
  }

  private void Print(String type, String position, String name, Runnable inner)
  {
    // NYI sanitize html
    // NYI add some useful css-classes
    Log.message(
      "<li onclick=\"toggle(this)\">" + type + ":" + position + ":" + name.replace("<", "&lt;").replace(">", "&gt;"));
    Log.message("<ul class=\"d-none\">");
    if (inner != null)
      {
        inner.run();
      }
    Log.message("</ul>");
    Log.message("</li>");
  }

  @Override
  public void action(Unbox u, Feature outer)
  {
    Print("Unbox", PosToString(FuzionHelpers.sourcePositionOrNone(u)), u.toString());
  }

  @Override
  public void action(Assign a, Feature outer)
  {
    Print("Assign", PosToString(FuzionHelpers.sourcePositionOrNone(a)), a._assignedField.qualifiedName());
  }

  @Override
  public void actionBefore(Block b, Feature outer)
  {
    Print("Block", PosToString(FuzionHelpers.sourcePositionOrNone(b)), "");
  }

  @Override
  public void actionAfter(Block b, Feature outer)
  {
  }

  @Override
  public void action(Box b, Feature outer)
  {
    Print("Box", PosToString(FuzionHelpers.sourcePositionOrNone(b)), b.toString());
  }

  private String PosToString(SourcePosition position)
  {
    return position._line + ":" + position._column;
  }

  @Override
  public Expr action(Call c, Feature outer)
  {
    Print("Call", PosToString(FuzionHelpers.sourcePositionOrNone(c)), c.calledFeature().qualifiedName());
    return c;
  }


  @Override
  public void actionBefore(Case c, Feature outer)
  {
    Print("Case", PosToString(FuzionHelpers.sourcePositionOrNone(c)), c.toString());
  }

  @Override
  public void actionAfter(Case c, Feature outer)
  {
  }

  @Override
  public void action(Cond c, Feature outer)
  {
    Print("Cond", PosToString(FuzionHelpers.sourcePositionOrNone(c)), c.toString());
  }

  @Override
  public Expr action(Current c, Feature outer)
  {
    Print("Current", PosToString(FuzionHelpers.sourcePositionOrNone(c)), c.toString());
    return c;
  }

  @Override
  public Stmnt action(Destructure d, Feature outer)
  {
    Print("Destructure", PosToString(FuzionHelpers.sourcePositionOrNone(d)), d.toString());
    return d;
  }

  @Override
  public Stmnt action(Feature f, Feature outer)
  {
    Print("Feature", PosToString(FuzionHelpers.sourcePositionOrNone(f)), f.qualifiedName(), () -> {
      var visitations = new TreeMap<Object, Feature>(FuzionHelpers.CompareBySourcePosition.thenComparing(Util.CompareByHashCode));

      Log.increaseIndentation();

      for(Feature argument : f.arguments)
      {
        visitations.put(argument, f);
      }

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

  private void doVisit(Object astItem, ASTtoHTML visitor, Feature outer)
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
    Print("Function", PosToString(FuzionHelpers.sourcePositionOrNone(f)), "");
    return f;
  }

  @Override
  public void action(Generic g, Feature outer)
  {
    Print("Generic", PosToString(FuzionHelpers.sourcePositionOrNone(g)), g.toString());
  }

  @Override
  public void action(If i, Feature outer)
  {
    Print("If", PosToString(FuzionHelpers.sourcePositionOrNone(i)), "");
  }

  @Override
  public void action(Impl i, Feature outer)
  {
    Print("Impl", PosToString(FuzionHelpers.sourcePositionOrNone(i)), "");
  }

  @Override
  public Expr action(InlineArray i, Feature outer)
  {
    Print("InlineArray", PosToString(FuzionHelpers.sourcePositionOrNone(i)), i.toString());
    return i;
  }

  @Override
  public void action(Match m, Feature outer)
  {
    Print("Match", PosToString(FuzionHelpers.sourcePositionOrNone(m)), m.toString());
  }

  @Override
  public void action(Tag b, Feature outer)
  {
    Print("Tag", PosToString(FuzionHelpers.sourcePositionOrNone(b)), b.toString());
  }

  @Override
  public Expr action(This t, Feature outer)
  {
    Print("This", PosToString(FuzionHelpers.sourcePositionOrNone(t)), t.toString());
    return t;
  }

  @Override
  public Type action(Type t, Feature outer)
  {
    Print("Type", PosToString(FuzionHelpers.sourcePositionOrNone(t)), t.toString());
    return t;
  }
}