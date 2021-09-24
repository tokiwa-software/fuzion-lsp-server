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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentPositionParams;

/**
 * utils which are independent of fuzion
 */
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
        Util.PrintStackTraceAndExit(1);
      }
    return byteArray;
  }

  public static File writeToTempFile(String text)
  {
    return writeToTempFile(text, Util.randomString(), ".fz");
  }

  private static File writeToTempFile(String text, String prefix, String extension)
  {
    return writeToTempFile(text, prefix, extension, true);
  }

  private static File writeToTempFile(String text, String prefix, String extension, boolean deleteOnExit)
  {
    try
      {
        File tempFile = File.createTempFile(prefix, extension);
        if (deleteOnExit)
          {
            tempFile.deleteOnExit();
          }

        FileWriter writer = new FileWriter(tempFile);
        writer.write(text);
        writer.close();
        return tempFile;
      }
    catch (IOException e)
      {
        Util.PrintStackTraceAndExit(1);
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

    return random.ints(leftLimit, rightLimit + 1)
      .limit(targetStringLength)
      .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
      .toString();
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

  public static void WithRedirectedStdErr(Runnable runnable)
  {
    var err = System.err;
    try
      {
        System.setErr(DEV_NULL);
        runnable.run();
      } finally
      {
        System.setErr(err);
      }
  }

  public static void WithTextInputStream(String text, Runnable runnable)
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

  public static URI toURI(String uri)
  {
    try
      {
        return new URI(uri);
      }
    catch (URISyntaxException e)
      {
        Util.PrintStackTraceAndExit(1);
        return null;
      }
  }

  public static <T> HashSet<T> HashSetOf(T... values)
  {
    return Stream.of(values).collect(Collectors.toCollection(HashSet::new));
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
        Util.PrintStackTraceAndExit(1);
        return null;
      }
  }

  public static String getUri(TextDocumentPositionParams params)
  {
    return getUri(params.getTextDocument());
  }

  public static String getUri(TextDocumentIdentifier params)
  {
    return params.getUri();
  }

  public static Position getPosition(TextDocumentPositionParams params)
  {
    return params.getPosition();
  }

  public static int ComparePosition(Position position1, Position position2)
  {
    var result = position1.getLine() < position2.getLine() ? -1: position1.getLine() > position2.getLine() ? +1: 0;
    if (result == 0)
      {
        result = position1.getCharacter() < position2.getCharacter() ? -1
                          : position1.getCharacter() > position2.getCharacter() ? +1: 0;
      }
    return result;
  }

  static void PrintStackTraceAndExit(int status)
  {
    PrintStackTraceAndExit(status, Thread.currentThread().getStackTrace());
  }

  static void PrintStackTraceAndExit(int status, StackTraceElement[] stackTrace)
  {
    var stackTraceString = Arrays.stream(stackTrace)
      .map(st -> st.toString())
      .collect(Collectors.joining(System.lineSeparator()));
    var file = Util.writeToTempFile(stackTraceString, "fuzion-lsp-crash", ".log", false);
    Main.getLanguageClient()
      .showMessage(new MessageParams(MessageType.Error,
        "fuzion language server crashed." + System.lineSeparator() + " Log: " + file.getAbsolutePath()));
    System.exit(1);
  }

}
