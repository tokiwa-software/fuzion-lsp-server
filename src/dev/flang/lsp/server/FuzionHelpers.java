package dev.flang.lsp.server;

import java.io.File;
import java.util.Comparator;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentPositionParams;

import dev.flang.util.Errors;
import dev.flang.util.SourcePosition;
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
import dev.flang.ast.FeatureName;
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
import dev.flang.ast.Types;
import dev.flang.ast.Unbox;
import dev.flang.fe.FrontEnd;
import dev.flang.fe.FrontEndOptions;

public class FuzionHelpers
{

  /**
   * create MIR and store main feature in Memory.Main
   * for future use
   * @param uri
   */
  public static void Parse(String uri)
  {
    // NYI remove once we can create MIR multiple times
    Errors.clear();
    Types.clear();
    FeatureName.clear();

    // NYI don't read from filesystem but newest version from
    // FuzionTextDocumentService->getText()
    File tempFile = Util.toFile(uri);

    Util.WithRedirectedStdOut(() -> {
      // NYI parsing works only once for now
      if (Memory.Main != null)
        {
          return;
        }
      var frontEndOptions =
          new FrontEndOptions(0, new dev.flang.util.List<>(), 0, false, false, tempFile.getAbsolutePath());
      var main = new FrontEnd(frontEndOptions).createMIR().main();
      Memory.Main = main;
    });
  }

  /**
   * visit everything in feature
   * add to result if predicate is true
   * @param result
   * @param addToResult
   * @return
   */
  static FeatureVisitor EverythingVisitor(TreeSet<Object> result, Predicate<? super Object> addToResult)
  {
    if (result.comparator() == null)
      {
        System.err.println("no comparator");
        System.exit(1);
      }
    return new FeatureVisitor() {
      @Override
      public void action(Unbox u, Feature outer)
      {
        if (addToResult.test(u))
          {
            result.add(u);
          }
        u.adr_.visit(this, outer);
      }

      @Override
      public void action(Assign a, Feature outer)
      {
        if (addToResult.test(a))
          {
            result.add(a);
          }
        a.value.visit(this, outer);
      }

      @Override
      public void actionBefore(Block b, Feature outer)
      {
        if (addToResult.test(b))
          {
            result.add(b);
          }
        b.statements_.forEach(s -> {
          s.visit(this, outer);
        });

      }

      @Override
      public void actionAfter(Block b, Feature outer)
      {
        if (addToResult.test(b))
          {
            result.add(b);
          }
        b.statements_.forEach(s -> {
          s.visit(this, outer);
        });
      }

      @Override
      public void action(Box b, Feature outer)
      {
        b._value.visit(this, outer);
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

        c._actuals.forEach(x -> {
          if (!result.contains(x))
            {
              x.visit(this, outer);
            }
        });

        c.generics.forEach(x -> {
          if (!result.contains(x))
            {
              x.visit(this, outer);
            }
        });

        c.target.visit(this, outer);
        return c;
      }

      @Override
      public void actionBefore(Case c, Feature outer)
      {
        if (addToResult.test(c))
          {
            result.add(c);
          }
        c.code.visit(this, outer);
      }

      @Override
      public void actionAfter(Case c, Feature outer)
      {
        if (addToResult.test(c))
          {
            result.add(c);
          }
        c.code.visit(this, outer);
      }

      @Override
      public void action(Cond c, Feature outer)
      {
        if (addToResult.test(c))
          {
            result.add(c);
          }
        c.cond.visit(this, outer);
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
        if (addToResult.test(f))
          {
            result.add(f);
          }
        f.impl.visit(this, outer);
        return f;
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
        i.cond.visit(this, outer);
        i.block.visit(this, outer);
        if (i.elseIf != null)
          {
            i.elseIf.visit(this, outer);
          }
        if (i.elseBlock != null)
          {
            i.elseBlock.visit(this, outer);
          }
      }

      @Override
      public void action(Impl i, Feature outer)
      {
        if (addToResult.test(i))
          {
            result.add(i);
          }
        i._code.visit(this, outer);
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
        b._value.visit(this, outer);
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
        t._generics.forEach(generic -> generic.visit(this, outer));
        return t;
      }
    };
  }

  public static Position ToPosition(SourcePosition sourcePosition)
  {
    return new Position(sourcePosition._line - 1, sourcePosition._column - 1);
  }

  public static Location ToLocation(SourcePosition sourcePosition)
  {
    var position = ToPosition(sourcePosition);
    return new Location("file://" + sourcePosition._sourceFile._fileName, new Range(position, position));
  }

  /**
   * getPosition of ASTItem
   * @param entry
   * @return
   */
  public static SourcePosition getPosition(Object entry)
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
  public static TreeSet<Object> getSuitableASTItems(TextDocumentPositionParams params)
  {
    var astItems = new TreeSet<>(FuzionHelpers.compareASTItems);
    var visitor = EverythingVisitor(astItems, astItem -> {
      var sourcePosition = getPosition(astItem);
      if (params.getPosition().getLine() != sourcePosition._line - 1)
        {
          return false;
        }
      var result = sourcePosition._column - 1 <= params.getPosition().getCharacter();
      if (result && Main.DEBUG())
        {
          System.out.println("considering: " + getPosition(astItem).toString() + ":" + astItem.getClass());
        }
      return result;
    });
    Memory.Main.visit(visitor);
    Memory.Main.universe().visit(visitor);
    if (astItems.isEmpty())
      {
        return astItems;
      }

    var maxColumn = astItems.stream().map(x -> getPosition(x)._column).max(Integer::compare).get();
    return astItems.stream().filter(obj -> getPosition(obj)._column == maxColumn)
        .collect(Collectors.toCollection(() -> new TreeSet<>(FuzionHelpers.compareASTItems)));
  }

  /*
   * tries to figure out if two AST Items are the same.
   * compares position then classname.
   */
  static Comparator<? super Object> compareASTItems = Comparator.comparing(obj -> obj, (astItem1, astItem2) -> {
    var positionComparisonResult = getPosition(astItem1).compareTo(getPosition(astItem2));
    if (positionComparisonResult == 0)
      {
        return astItem1.getClass().getCanonicalName().compareTo(astItem2.getClass().getCanonicalName());
      }
    return positionComparisonResult;
  });

}
