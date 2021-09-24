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
import dev.flang.ast.InitArray;
import dev.flang.ast.Match;
import dev.flang.ast.ReturnType;
import dev.flang.ast.Stmnt;
import dev.flang.ast.Tag;
import dev.flang.ast.This;
import dev.flang.ast.Type;
import dev.flang.ast.Unbox;
import dev.flang.util.SourcePosition;

/**
* visit everything in feature including heirs
* @param result
* @return
*/
public class ASTPrinter extends FeatureVisitor
{
  public static void printAST(Feature baseFeature)
  {
    var visitor = new ASTPrinter();
    Log.message("""
    <script>
    function toggle(that){
      this.event.stopPropagation();
      Array.from(that.children).forEach(child => child.tagName =='UL' ? child.classList.toggle('d-none') : 0);
    }
    </script>
    """);
    Log.message("<ul onclick=\"toggle(this)\">");
    baseFeature.visit(visitor, baseFeature.outer());
    Log.message("</ul>");
  }

  private void Print(String type, String position, String name)
  {
    Print(type, position, name, null);
  }

  private void Print(String type, String position, String name, Runnable inner)
  {
    Log.message("<li>" + type + ":" + position + ":" + name + "</li>");
    Log.message("<ul class=\"d-none\" onclick=\"toggle(this)\">");
    if (inner != null)
      {
        inner.run();
      }
    Log.message("</ul>");
  }

  @Override
  public void action(Unbox u, Feature outer)
  {
    Print("Unbox", PosToString(FuzionHelpers.getPosition(u)), u.toString());
  }

  @Override
  public void action(Assign a, Feature outer)
  {
    Print("Assign", PosToString(FuzionHelpers.getPosition(a)), a._assignedField.qualifiedName());
  }

  @Override
  public void actionBefore(Block b, Feature outer)
  {
    Print("Block", PosToString(FuzionHelpers.getPosition(b)), "");
  }

  @Override
  public void actionAfter(Block b, Feature outer)
  {
  }

  @Override
  public void action(Box b, Feature outer)
  {
    Print("Box", PosToString(FuzionHelpers.getPosition(b)), b.toString());
  }

  private String PosToString(SourcePosition position)
  {
    return position._line + ":" + position._column;
  }

  @Override
  public Expr action(Call c, Feature outer)
  {
    Print("Call", PosToString(FuzionHelpers.getPosition(c)), c.calledFeature().qualifiedName());
    return c;
  }


  @Override
  public void actionBefore(Case c, Feature outer)
  {
    Print("Case", PosToString(FuzionHelpers.getPosition(c)), c.toString());
  }

  @Override
  public void actionAfter(Case c, Feature outer)
  {
  }

  @Override
  public void action(Cond c, Feature outer)
  {
    Print("Cond", PosToString(FuzionHelpers.getPosition(c)), c.toString());
  }

  @Override
  public Expr action(Current c, Feature outer)
  {
    Print("Current", PosToString(FuzionHelpers.getPosition(c)), c.toString());
    return c;
  }

  @Override
  public Stmnt action(Destructure d, Feature outer)
  {
    Print("Destructure", PosToString(FuzionHelpers.getPosition(d)), d.toString());
    return d;
  }

  @Override
  public Stmnt action(Feature f, Feature outer)
  {
    Print("Feature", PosToString(FuzionHelpers.getPosition(f)), f.qualifiedName(), () -> {
      var visitations = new TreeMap<Object, Feature>(FuzionHelpers.CompareBySourcePosition);

      Log.increaseIndentation();

      visitations.put(f.resultType(), outer);

      visitations.put(f.generics, outer);
      for(Call c : f.inherits)
        {
          visitations.put(c, outer);
        }
      if (f.contract != null)
        {
          visitations.put(f.contract, outer);
        }
      visitations.put(f.impl, outer);
      visitations.put(f.returnType, outer);

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
    Util.PrintStackTraceAndExit(1);
  }

  @Override
  public Expr action(Function f, Feature outer)
  {
    Print("Function", PosToString(FuzionHelpers.getPosition(f)), "");
    return f;
  }

  @Override
  public void action(Generic g, Feature outer)
  {
    Print("Generic", PosToString(FuzionHelpers.getPosition(g)), g.toString());
  }

  @Override
  public void action(If i, Feature outer)
  {
    Print("If", PosToString(FuzionHelpers.getPosition(i)), "");
  }

  @Override
  public void action(Impl i, Feature outer)
  {
    Print("Impl", PosToString(FuzionHelpers.getPosition(i)), "");
  }

  @Override
  public Expr action(InitArray i, Feature outer)
  {
    Print("InitArray", PosToString(FuzionHelpers.getPosition(i)), i.toString());
    return i;
  }

  @Override
  public void action(Match m, Feature outer)
  {
    Print("Match", PosToString(FuzionHelpers.getPosition(m)), m.toString());
  }

  @Override
  public void action(Tag b, Feature outer)
  {
    Print("Tag", PosToString(FuzionHelpers.getPosition(b)), b.toString());
  }

  @Override
  public Expr action(This t, Feature outer)
  {
    Print("This", PosToString(FuzionHelpers.getPosition(t)), t.toString());
    return t;
  }

  @Override
  public Type action(Type t, Feature outer)
  {
    Print("Type", PosToString(FuzionHelpers.getPosition(t)), t.toString());
    return t;
  }
}