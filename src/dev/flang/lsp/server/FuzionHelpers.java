package dev.flang.lsp.server;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Comparator;
import java.util.Optional;
import java.util.ArrayList;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentPositionParams;

import dev.flang.util.Errors;
import dev.flang.util.SourcePosition;
import dev.flang.ast.Case;
import dev.flang.ast.Feature;
import dev.flang.ast.FeatureName;
import dev.flang.ast.FeatureVisitor;
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
    // NYI don't read from filesystem but newest version from
    // FuzionTextDocumentService->getText()
    File sourceFile = uri.contains("/lib/") ? getDummyFile(): Util.toFile(uri);

    Util.WithRedirectedStdOut(() -> {
      // NYI parsing works only once for now
      if (Memory.Main != null)
        {
          return;
        }

      // // NYI remove once we can create MIR multiple times
      // Errors.clear();
      // Types.clear();
      // FeatureName.clear();

      var frontEndOptions =
          new FrontEndOptions(0, new dev.flang.util.List<>(), 0, false, false, sourceFile.getAbsolutePath());
      var main = new FrontEnd(frontEndOptions).createMIR().main();
      Memory.Main = main;
    });

  }

  private static File getDummyFile()
  {
    try
      {
        var file = File.createTempFile("000000", ".fz");
        FileWriter writer = new FileWriter(file);
        writer.write("nothing is");
        writer.close();
        return file;
      }
    catch (IOException e)
      {
        Log.write("parsing failed");
        Log.write(e.getStackTrace().toString());
        return null;
      }
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
    Log.write("no src pos found for: " + entry.getClass());
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
   * given a TextDocumentPosition return all ASTItems
   * in the given file on the given line and before given character position.
   * @param params
   * @return
   */
  public static TreeSet<Object> getSuitableASTItems(TextDocumentPositionParams params)
  {
    var uri = params.getTextDocument().getUri();
    var position = params.getPosition();

    var baseFeature = getBaseFeature(uri);
    if (baseFeature.isEmpty())
      {
        Log.write("no matching feature found for: " + uri);
        return new TreeSet<>();
      }

    var astItems = doVisitation(baseFeature.get(), uri, position);

    if (astItems.isEmpty())
      {
        Log.write("no matching AST items found");
        return astItems;
      }

    var maxColumn = astItems.stream().map(x -> getPosition(x)._column).max(Integer::compare).get();
    return astItems.stream().filter(obj -> getPosition(obj).isBuiltIn() || getPosition(obj)._column == maxColumn)
        .map(astItem -> {
          Log.write("found: " + getPosition(astItem).toString() + ":" + astItem.getClass());
          return astItem;
        }).collect(Collectors.toCollection(() -> new TreeSet<>(FuzionHelpers.compareASTItems)));
  }

  private static TreeSet<Object> doVisitation(Feature baseFeature, String uri, Position position)
  {
    var astItems = new TreeSet<>(FuzionHelpers.compareASTItems);
    var visitor = new HeirsVisitor(astItems, filterIrrelevantItems(uri, position), uri);
    Log.write("starting visitation at: " + baseFeature.qualifiedName());
    baseFeature.visit(visitor, baseFeature.outer());
    return astItems;
  }

  private static Optional<Feature> getBaseFeature(String uri)
  {
    var universe = Memory.Main.universe();
    var allFeatures = new ArrayList<Feature>();
    universe.visit(new FeatureVisitor() {
      @Override
      public Stmnt action(Feature f, Feature outer)
      {
        allFeatures.add(f);
        f.declaredFeatures().forEach((n, df) -> df.visit(this, f));
        return super.action(f, outer);
      }
    }, universe.outer());

    return allFeatures.stream().filter(feature -> {
      return uri.equals(toUriString(feature.pos()));
    }).findFirst();
  }

  private static Predicate<? super Object> filterIrrelevantItems(String uri, Position position)
  {
    return astItem -> {
      var sourcePosition = getPosition(astItem);
      Log.write("visiting: " + sourcePosition.toString() + ":" + astItem.getClass());

      // NYI what can we do with built in stuff?
      if (sourcePosition.isBuiltIn())
        {
          return false;
        }
      if (position.getLine() != sourcePosition._line - 1 || !uri.equals(toUriString(sourcePosition)))
        {
          return false;
        }

      return sourcePosition._column - 1 <= position.getCharacter();
    };
  }

  public static String toUriString(SourcePosition sourcePosition)
  {
    return "file://" + sourcePosition._sourceFile._fileName.toString();
  }

  private static Comparator<? super Object> compareASTItems = Comparator.comparing(obj -> obj, (astItem1, astItem2) -> {
    // we don't care about order thus always return 1 if not same
    return astItem1.equals(astItem2) ? 0: 1;
  });

  static boolean IsRoutineOrRoutineDef(Feature feature)
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

}
