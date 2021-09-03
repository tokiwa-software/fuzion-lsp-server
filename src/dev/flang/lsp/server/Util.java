package dev.flang.lsp.server;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Optional;
import java.util.Random;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentPositionParams;

import dev.flang.ast.*;
import dev.flang.ast.Impl.Kind;

public class Util
{
  static final PrintStream DEV_NULL = new PrintStream(OutputStream.nullOutputStream());

  static byte[] getBytes(String text)
  {
    byte[] byteArray = new byte[0];
    try
      {
        byteArray = text.getBytes("UTF-8");
      }
    catch (UnsupportedEncodingException e)
      {
        e.printStackTrace();
        System.exit(1);
      }
    return byteArray;
  }

  public static File writeToTempFile(String text)
  {
    try
      {
        File tempFile = File.createTempFile(Util.randomString(), ".fz");
        tempFile.deleteOnExit();

        FileWriter writer = new FileWriter(tempFile);
        writer.write(text);
        writer.close();
        return tempFile;
      }
    catch (IOException e)
      {
        e.printStackTrace();
        System.exit(1);
        return null;
      }
  }

  // https://www.baeldung.com/java-random-string
  static String randomString()
  {
    int leftLimit = 97; // letter 'a'
    int rightLimit = 122; // letter 'z'
    int targetStringLength = 10;
    Random random = new Random();

    return random.ints(leftLimit, rightLimit + 1).limit(targetStringLength)
        .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
  }

  public static void WithRedirectedStdOut(Runnable runnable)
  {
    var out = System.out;
    try
      {
        System.setOut(DEV_NULL);
        runnable.run();
      } finally
      {
        System.setOut(out);
      }
  }

  private static void WithTextInputStream(String text, Runnable runnable)
  {
    byte[] byteArray = getBytes(text);

    InputStream testInput = new ByteArrayInputStream(byteArray);
    InputStream old = System.in;
    try
      {
        System.setIn(testInput);
        runnable.run();
      } finally
      {
        System.setIn(old);
      }
  }

  public static Position ToPosition(Stmnt stmnt)
  {
    return new Position(stmnt.pos()._line - 1, stmnt.pos()._column - 1);
  }

  public static URI toURI(String uri)
  {
    try
      {
        return new URI(uri);
      }
    catch (URISyntaxException e)
      {
        e.printStackTrace();
        System.exit(1);
        return null;
      }
  }

  public static Location ToLocation(Stmnt stmnt)
  {
    var position = Util.ToPosition(stmnt);
    return new Location("file://" + stmnt.pos()._sourceFile._fileName, new Range(position, position));
  }

  private static Stream<Feature> flatten(Stream<Feature> features)
  {
    var featureList = features.toList();
    if (featureList.stream().count() == 0)
      {
        return Stream.of();
      }
    return Stream.concat(featureList.stream(),
        featureList.stream().flatMap(feature -> flatten(feature.declaredFeatures().values().stream())));
  }

  public static <T> HashSet<T> HashSetOf(T... values)
  {
    return Stream.of(values).collect(Collectors.toCollection(HashSet::new));
  }

  /**
   * Return enclosing feature for given params
   * @param uriString
   * @param position
   * @return
   */
  public static Feature getEnclosingFeature(TextDocumentPositionParams params)
  {
    var uriString = params.getTextDocument().getUri();
    var position = params.getPosition();

    var uri = Util.toURI(uriString);

    var universeFeatures = Memory.Universe.declaredFeatures().values().stream();
    var allFeatures = flatten(universeFeatures);

    Predicate<? super Feature> isRoutine = feature -> HashSetOf(Kind.Routine, Kind.RoutineDef).contains(feature.impl.kind_);

    // NYI needs a better way
    Optional<Feature> enclosingFeature = allFeatures.filter((feature) -> {
      var featureUri = Util.toURI("file://" + feature.pos()._sourceFile._fileName);
      return featureUri.equals(uri);
    })
    .filter(isRoutine)
    .filter(feature -> {
      var line_zero_based = feature.pos()._line - 1;
      var character_zero_based = feature.pos()._column - 1;
      // NYI HACK remove once we get end position of stmnts
      if (getIndentationLevel(FuzionTextDocumentService.getText(uriString),
      position.getLine()) <= feature.pos()._column - 1)
        {
          System.out.println("filtering indent: " + feature.featureName());
          return false;
        }
        if(!(line_zero_based <= position.getLine() && character_zero_based <= position.getCharacter())){
          System.out.println("filtering pos: " + feature.featureName());
          return false;
        }
        return true;
    }).sorted(Comparator.comparing(f -> -f.pos()._line)).findFirst();

    if (Main.DEBUG())
      {
        if (enclosingFeature.isEmpty())
          {
            System.err.println("no feature found");
          }
        if (enclosingFeature.isPresent())
          {
            System.out.println("CLOSEST FEATURE:" + enclosingFeature.get().featureName());
            System.out.println("pos: " + enclosingFeature.get().pos());
          }
      }

    return enclosingFeature.isPresent() ? enclosingFeature.get(): Memory.Universe;
  }

  private static int getIndentationLevel(String text, int line)
  {
    var lineText = text.split("\n")[line];
    var indent = lineText.length() - lineText.stripLeading().length();
    return indent;
  }

  /**
   * NYI
   * @param params
   * @return
   */
  public static Optional<Stmnt> getClosestStmnt(TextDocumentPositionParams params)
  {
    var enclosingFeature = Util.getEnclosingFeature(params);
    var visitedStmnts = new ArrayList<Stmnt>();
    enclosingFeature.visit(StmntVisitor(visitedStmnts));

    Predicate<? super Stmnt> beginsBeforeOrAtLine =
        statement -> params.getPosition().getLine() - (statement.pos()._line - 1) >= 0;
    Predicate<? super Stmnt> beginsBeforeOrAtCharacter =
        statement -> params.getPosition().getCharacter() - (statement.pos()._column - 1) >= 0;

    var filteredStmnts = visitedStmnts.stream().filter(beginsBeforeOrAtLine).filter(beginsBeforeOrAtCharacter)
        .sorted(Comparator.comparing(statement -> -((Stmnt) statement).pos()._line)
            .thenComparing(statement -> -((Stmnt) statement).pos()._column))
        .toList();

    var closestStmnt = filteredStmnts.stream().findFirst();

    if (Main.DEBUG() && closestStmnt.isPresent())
      {
        System.out.println("cs: " + closestStmnt.get().getClass());
      }
    if (Main.DEBUG() && closestStmnt.isEmpty())
      {
        System.out.println("cs: found nothing");
      }

    return closestStmnt;
  }

  private static FeatureVisitor StmntVisitor(ArrayList<Stmnt> visitedStmnts)
  {
    return new FeatureVisitor() {
      @Override
      public void action(Unbox u, Feature outer)
      {
        visitedStmnts.add(u);
      }

      @Override
      public void action(Assign a, Feature outer)
      {
        visitedStmnts.add(a);
      }

      @Override
      public void actionBefore(Block b, Feature outer)
      {
        visitedStmnts.add(b);
      }

      @Override
      public void actionAfter(Block b, Feature outer)
      {
        visitedStmnts.add(b);
      }

      @Override
      public void action(Box b, Feature outer)
      {
        visitedStmnts.add(b);
      }

      @Override
      public Expr action(Call c, Feature outer)
      {
        visitedStmnts.add(c);
        return c;
      }

      @Override
      public void actionBefore(Case c, Feature outer)
      {
      }

      @Override
      public void actionAfter(Case c, Feature outer)
      {
      }

      @Override
      public void action(Cond c, Feature outer)
      {
      }

      @Override
      public Expr action(Current c, Feature outer)
      {
        visitedStmnts.add(c);
        return c;
      }

      @Override
      public Stmnt action(Destructure d, Feature outer)
      {
        visitedStmnts.add(d);
        return d;
      }

      @Override
      public Stmnt action(Feature f, Feature outer)
      {
        visitedStmnts.add(f);
        return f;
      }

      @Override
      public Expr action(Function f, Feature outer)
      {
        visitedStmnts.add(f);
        return f;
      }

      @Override
      public void action(Generic g, Feature outer)
      {
      }

      @Override
      public void action(If i, Feature outer)
      {
        visitedStmnts.add(i);
      }

      @Override
      public void action(Impl i, Feature outer)
      {
      }

      @Override
      public Expr action(InitArray i, Feature outer)
      {
        visitedStmnts.add(i);
        return i;
      }

      @Override
      public void action(Match m, Feature outer)
      {
        visitedStmnts.add(m);
      }

      @Override
      public void action(Tag b, Feature outer)
      {
        visitedStmnts.add(b);
      }

      @Override
      public Expr action(This t, Feature outer)
      {
        visitedStmnts.add(t);
        return t;
      }

      @Override
      public Type action(Type t, Feature outer)
      {
        return t;
      }
    };
  }

  public static Range toRange(Position position)
  {
    var line = position.getLine();
    var character = position.getCharacter();
    return new Range(new Position(line, character), new Position(line, character));
  }

  static File toFile(String uri)
  {
    try
      {
        return new File(new URI(uri));
      }
    catch (URISyntaxException e)
      {
        System.exit(1);
        return null;
      }
  }

}
