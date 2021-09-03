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
import java.util.AbstractMap.SimpleEntry;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentPositionParams;

import dev.flang.ast.*;
import dev.flang.ast.Impl.Kind;
import dev.flang.util.SourcePosition;

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

  public static Position ToPosition(SourcePosition sourcePosition)
  {
    return new Position(sourcePosition._line - 1, sourcePosition._column - 1);
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

  public static Location ToLocation(SourcePosition sourcePosition)
  {
    var position = Util.ToPosition(sourcePosition);
    return new Location("file://" + sourcePosition._sourceFile._fileName, new Range(position, position));
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

    Predicate<? super Feature> isInURI =
        feature -> Util.toURI("file://" + feature.pos()._sourceFile._fileName).equals(uri);
    Predicate<? super Feature> isRoutine =
        feature -> HashSetOf(Kind.Routine, Kind.RoutineDef).contains(feature.impl.kind_);
    Predicate<? super Feature> isBefore = feature -> feature.pos()._line - 1 <= position.getLine()
        && feature.pos()._column - 1 <= position.getCharacter();
    Predicate<? super Feature> hasASTItemOnSameLineOrAfter = feature -> getAllAstItems(feature).stream()
        .anyMatch(entry -> entry.getKey()._line - 1 >= position.getLine());

    // NYI needs a better way
    Optional<Feature> enclosingFeature = allFeatures.filter(isInURI).filter(isRoutine).filter(isBefore).filter(hasASTItemOnSameLineOrAfter)
        .sorted(Comparator.comparing(f -> -f.pos()._line)).findFirst();

    if (Main.DEBUG())
      {
        if (enclosingFeature.isEmpty())
          {
            System.err.println("no feature found");
          }
        if (enclosingFeature.isPresent())
          {
            System.out.println("CLOSEST FEATURE:" + enclosingFeature.get().featureName());
            System.out.println("CLOSEST FEATURE pos: " + enclosingFeature.get().pos());
          }
      }

    return enclosingFeature.isPresent() ? enclosingFeature.get(): Memory.Universe;
  }

  /**
   * NYI
   * @param params
   * @return
   */
  public static ArrayList<SimpleEntry<SourcePosition, Object>> getPossibleASTItems(TextDocumentPositionParams params, Predicate<? super SimpleEntry<SourcePosition, Object>> filter)
  {
    var enclosingFeature = Util.getEnclosingFeature(params);
    var astItems = getAllAstItems(enclosingFeature);
    astItems
    .sort(Comparator.comparing(SimpleEntry::getKey));
    if (Main.DEBUG())
      {
        System.out.println("ast items found: ");
        astItems.forEach(item -> {
          System.out.println(item.getValue().getClass() + ":" + item.getKey());
        });
        System.out.println("===");
      }

    Predicate<? super SimpleEntry<SourcePosition, Object>> isOnSameLine =
        entry -> params.getPosition().getLine() == (entry.getKey()._line - 1);
    Predicate<? super SimpleEntry<SourcePosition, Object>> startsBeforeOrAtCharacter =
        entry -> params.getPosition().getCharacter() - (entry.getKey()._column - 1) >= 0;

    var filteredASTItems = astItems.stream().filter(isOnSameLine).filter(startsBeforeOrAtCharacter).filter(filter)
        .collect(Collectors.toCollection(ArrayList<SimpleEntry<SourcePosition, Object>>::new));

    if (Main.DEBUG())
      {
        System.out.println("Items considered: ");
        filteredASTItems.forEach(item -> {
          System.out.println(item.getValue().getClass() + ":" + item.getKey());
        });
        System.out.println("===");
      }

    return filteredASTItems;
  }

  private static ArrayList<SimpleEntry<SourcePosition, Object>> getAllAstItems(Feature feature)
  {
    var result = new ArrayList<SimpleEntry<SourcePosition, Object>>();
    feature.visit(EverythingVisitor(result));
    return result;
  }

  private static FeatureVisitor EverythingVisitor(ArrayList<SimpleEntry<SourcePosition, Object>> visitedASTItems)
  {
    return new FeatureVisitor() {
      @Override
      public void action(Unbox u, Feature outer)
      {
        visitedASTItems.add(new SimpleEntry<SourcePosition, Object>(u.pos(),u));
      }

      @Override
      public void action(Assign a, Feature outer)
      {
        visitedASTItems.add(new SimpleEntry<SourcePosition, Object>(a.pos(),a));
        a.value.visit(this, outer);
      }

      @Override
      public void actionBefore(Block b, Feature outer)
      {
        visitedASTItems.add(new SimpleEntry<SourcePosition, Object>(b.pos(),b));
        b.statements_.forEach(s -> {
          s.visit(this, outer);
        });

      }

      @Override
      public void actionAfter(Block b, Feature outer)
      {
        visitedASTItems.add(new SimpleEntry<SourcePosition, Object>(b.pos(),b));
        b.statements_.forEach(s -> {
          s.visit(this, outer);
        });
      }

      @Override
      public void action(Box b, Feature outer)
      {
        b._value.visit(this, outer);
        visitedASTItems.add(new SimpleEntry<SourcePosition, Object>(b.pos(),b));
      }

      @Override
      public Expr action(Call c, Feature outer)
      {
        visitedASTItems.add(new SimpleEntry<SourcePosition, Object>(c.pos(),c));
        return c;
      }

      @Override
      public void actionBefore(Case c, Feature outer)
      {
        visitedASTItems.add(new SimpleEntry<SourcePosition, Object>(c.pos,c));
        c.code.visit(this, outer);
      }

      @Override
      public void actionAfter(Case c, Feature outer)
      {
        visitedASTItems.add(new SimpleEntry<SourcePosition, Object>(c.pos,c));
        c.code.visit(this, outer);
      }

      @Override
      public void action(Cond c, Feature outer)
      {
        visitedASTItems.add(new SimpleEntry<SourcePosition, Object>(c.cond.pos(),c));
        c.cond.visit(this, outer);
      }

      @Override
      public Expr action(Current c, Feature outer)
      {
        visitedASTItems.add(new SimpleEntry<SourcePosition, Object>(c.pos(),c));
        return c;
      }

      @Override
      public Stmnt action(Destructure d, Feature outer)
      {
        visitedASTItems.add(new SimpleEntry<SourcePosition, Object>(d.pos(),d));
        return d;
      }

      @Override
      public Stmnt action(Feature f, Feature outer)
      {
        visitedASTItems.add(new SimpleEntry<SourcePosition, Object>(f.pos(),f));
        f.visit(this, outer);
        return f;
      }

      @Override
      public Expr action(Function f, Feature outer)
      {
        visitedASTItems.add(new SimpleEntry<SourcePosition, Object>(f.pos(),f));
        return f;
      }

      @Override
      public void action(Generic g, Feature outer)
      {
        visitedASTItems.add(new SimpleEntry<SourcePosition, Object>(g._pos,g));
      }

      @Override
      public void action(If i, Feature outer)
      {
        visitedASTItems.add(new SimpleEntry<SourcePosition, Object>(i.pos(),i));
        i.cond.visit(this, outer);
        i.block.visit(this, outer);
        if(i.elseIf != null){
          i.elseIf.visit(this, outer);
        }
        if(i.elseBlock != null){
          i.elseBlock.visit(this, outer);
        }
      }

      @Override
      public void action(Impl i, Feature outer)
      {
        visitedASTItems.add(new SimpleEntry<SourcePosition, Object>(i.pos,i));
        i._code.visit(this, outer);
      }

      @Override
      public Expr action(InitArray i, Feature outer)
      {
        visitedASTItems.add(new SimpleEntry<SourcePosition, Object>(i.pos(),i));
        return i;
      }

      @Override
      public void action(Match m, Feature outer)
      {
        visitedASTItems.add(new SimpleEntry<SourcePosition, Object>(m.pos(),m));
      }

      @Override
      public void action(Tag b, Feature outer)
      {
        visitedASTItems.add(new SimpleEntry<SourcePosition, Object>(b.pos(),b));
      }

      @Override
      public Expr action(This t, Feature outer)
      {
        visitedASTItems.add(new SimpleEntry<SourcePosition, Object>(t.pos(),t));
        return t;
      }

      @Override
      public Type action(Type t, Feature outer)
      {
        visitedASTItems.add(new SimpleEntry<SourcePosition, Object>(t.pos,t));
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
