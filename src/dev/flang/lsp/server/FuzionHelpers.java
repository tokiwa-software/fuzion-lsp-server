package dev.flang.lsp.server;

import java.io.File;
import java.util.Comparator;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentPositionParams;

import dev.flang.util.Errors;
import dev.flang.util.SourcePosition;
import dev.flang.ast.Case;
import dev.flang.ast.Cond;
import dev.flang.ast.Feature;
import dev.flang.ast.FeatureName;
import dev.flang.ast.Generic;
import dev.flang.ast.Impl;
import dev.flang.ast.Stmnt;
import dev.flang.ast.Type;
import dev.flang.ast.Types;
import dev.flang.ast.Impl.Kind;
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
   * @return can be null
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
    if (entry instanceof Generic)
      {
        return ((Generic) entry)._pos;
      }
    if (entry instanceof Cond)
      {
        return null;
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
  public static TreeSet<Object> getSuitableASTItems(TextDocumentPositionParams params)
  {
    var astItems = new TreeSet<>(FuzionHelpers.compareASTItems);
    var visitor = new EverythingVisitor(astItems, astItem -> {
      var sourcePosition = getPosition(astItem);
      if (sourcePosition == null)
        {
          Log.write("no src pos: " + astItem.getClass());
          return false;
        }
      Log.write("visiting: " + getPosition(astItem).toString() + ":" + astItem.getClass());
      if (params.getPosition().getLine() != sourcePosition._line - 1)
        {
          return false;
        }
      var result = sourcePosition._column - 1 <= params.getPosition().getCharacter();
      if (result)
        {
          Log.write("found: " + getPosition(astItem).toString() + ":" + astItem.getClass());
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
        return astItem1.getClass().getName().compareTo(astItem2.getClass().getName());
      }
    return positionComparisonResult;
  });

  static boolean IsRoutineOrRoutineDef(Feature feature)
  {
    return Util.HashSetOf(Kind.Routine, Kind.RoutineDef).contains(feature.impl.kind_);
  }

  public static boolean IsRoutineOrRoutineDef(Impl impl)
  {
    return Util.HashSetOf(Kind.Routine, Kind.RoutineDef).contains(impl.kind_);
  }

}
