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
import java.util.Optional;
import java.util.Random;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentPositionParams;

import dev.flang.ast.Feature;
import dev.flang.ast.Call;
import dev.flang.ast.Stmnt;
import dev.flang.ast.FeatureVisitor;
import dev.flang.ast.Expr;

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
    if(featureList.stream().count() == 0){
      return Stream.of();
    }
    return Stream.concat(featureList.stream(), featureList.stream().flatMap(feature -> flatten(feature.declaredFeatures().values().stream())));
  }

  /**
   * Return innermost feature for given params
   * @param uriString
   * @param position
   * @return
   */
  public static Feature getClosestFeature(TextDocumentPositionParams params)
  {
    var uriString = params.getTextDocument().getUri();
    var position = params.getPosition();

    var uri = Util.toURI(uriString);

    var universeFeatures = Memory.Universe.declaredFeatures().values().stream();
    var allFeatures = flatten(universeFeatures);

    //NYI needs a better way
    Optional<Feature> closestFeature = allFeatures.filter((feature) -> {
      var featureUri = Util.toURI("file://" + feature.pos()._sourceFile._fileName);
      return featureUri.equals(uri);
    }).filter(feature -> {
      var line_zero_based = feature.pos()._line - 1;
      var character_zero_based = feature.pos()._column - 1;
      // NYI HACK remove once we get end position of stmnts
      if(getIndentationLevel(FuzionTextDocumentService.getText(uriString), position.getLine()) <= feature.pos()._column){
        return false;
      }

      return line_zero_based <= position.getLine() && character_zero_based <= position.getCharacter();
    }).sorted(Comparator.comparing(f -> -f.pos()._line)).findFirst();

    return closestFeature.isPresent() ?  closestFeature.get(): Memory.Universe;
  }

  private static int getIndentationLevel(String text, int line)
  {
    var lineText = text.split("\n")[line];
    return lineText.length() - lineText.stripLeading().length();
  }

  /**
   * NYI
   * @param params
   * @return
   */
  public static Optional<Call> getClosestCall(TextDocumentPositionParams params)
  {
    var closestFeature = Util.getClosestFeature(params);
    var visitedCalls = new ArrayList<Call>();
    closestFeature.visit(new FeatureVisitor() {
      @Override
      public Expr action(Call c, Feature outer)
      {
        visitedCalls.add(c);
        return c;
      }
    });

    Predicate<? super Call> isSameLine = statement -> statement.pos()._line - 1 == params.getPosition().getLine();
    Predicate<? super Call> beginsBeforeOrAtPosition = statement -> params.getPosition().getCharacter() - (statement.pos()._column - 1) >= 0;

    var closestCall = visitedCalls.stream()
      .filter(isSameLine)
      .filter(beginsBeforeOrAtPosition)
      .sorted(Comparator.comparing(statement -> {
        return -statement.pos()._column;
      }))
      .findFirst();
    return closestCall;
  }

}
