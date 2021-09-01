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
import java.util.Comparator;
import java.util.Optional;
import java.util.Random;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import dev.flang.ast.Feature;
import dev.flang.ast.Stmnt;

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
        System.exit(1);
        return null;
      }
  }

  public static Location ToLocation(Stmnt stmnt)
  {
    var position = Util.ToPosition(stmnt);
    return new Location("file://" + stmnt.pos()._sourceFile._fileName, new Range(position, position));
  }

  /**
   * Return innermost feature for given params
   * @param uriString
   * @param position
   * @return
   */
  public static Optional<Feature> getClosestFeature(String uriString, Position position)
  {
    var uri = Util.toURI(uriString);
    var universe = Memory.Uri2Universe.get(uriString);

    return universe.declaredFeatures().values().stream().filter((feature) -> {
      var featureUri = Util.toURI("file://" + feature.pos()._sourceFile._fileName);
      return featureUri.equals(uri);
    }).filter(feature -> {
      var line_zero_based = feature.pos()._line - 1;
      var character_zero_based = feature.pos()._column - 1;
      return line_zero_based <= position.getLine() && character_zero_based <= position.getCharacter();
    }).sorted(Comparator.comparing(f -> -f.pos()._line)).findFirst();
  }

}
