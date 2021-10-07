package dev.flang.lsp.server;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.Position;
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
        Util.WriteStackTraceAndExit(1);
      }
    return byteArray;
  }

  public static File writeToTempFile(String text)
  {
    return writeToTempFile(text, String.valueOf(System.currentTimeMillis()), ".fz");
  }

  public static File writeToTempFile(String text, String prefix, String extension)
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
        Util.WriteStackTraceAndExit(1);
        return null;
      }
  }

  /**
   * this ugly method executes a runnable within a given time and
   * captures both stdout and stderr along the way.
   * @param runnable
   * @param timeOutInMilliSeconds
   * @return
   */
  public static MessageParams WithCapturedStdOutErr(Runnable runnable, long timeOutInMilliSeconds)
  {
    var out = System.out;
    var err = System.err;
    try
      {
        var inputStream = new PipedInputStream();
        PrintStream outputStream = new PrintStream(new PipedOutputStream(inputStream));
        System.setOut(outputStream);
        System.setErr(outputStream);

        return TryRunWithTimeout(runnable, timeOutInMilliSeconds, inputStream, outputStream);
      }
    catch (Exception e)
      {
        Util.WriteStackTraceAndExit(1, e);
        return null;
      } finally
      {
        System.setOut(out);
        System.setErr(err);
      }
  }

  private static MessageParams TryRunWithTimeout(Runnable runnable, long timeOutInMilliSeconds,
    PipedInputStream inputStream,
    PrintStream outputStream) throws IOException
  {
    ExecutorService executor = Executors.newSingleThreadExecutor();

    Future<?> future = executor.submit(runnable);
    try
      {
        future.get(timeOutInMilliSeconds, TimeUnit.MILLISECONDS);
        outputStream.close();
        return new MessageParams(MessageType.Info, new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
      }
    catch (TimeoutException e)
      {
        future.cancel(true);
        outputStream.close();
        return new MessageParams(MessageType.Warning, "Execution timed out: " + System.lineSeparator()
          + new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
      }
    catch (Exception e)
      {
        Util.WriteStackTrace(e);
        return new MessageParams(MessageType.Error, "Execution failed: " + System.lineSeparator() +
          e.getMessage());
      } finally
      {
        executor.shutdown();
        outputStream.close();
        inputStream.close();
      }
  }

  public static <T> T WithRedirectedStdOut(Callable<T> callable)
  {
    var out = System.out;
    try
      {
        System.setOut(DEV_NULL);
        return callable.call();
      }
    catch (Exception e)
      {
        Util.WriteStackTraceAndExit(1, e);
        return null;
      } finally
      {
        System.setOut(out);
      }
  }

  public static <T> T WithRedirectedStdErr(Callable<T> callable)
  {
    var err = System.err;
    try
      {
        System.setErr(DEV_NULL);
        return callable.call();
      }
    catch (Exception e)
      {
        Util.WriteStackTraceAndExit(1, e);
        return null;
      } finally
      {
        System.setErr(err);
      }
  }

  public static <T> T WithTextInputStream(String text, Callable<T> callable)
  {
    byte[] byteArray = getBytes(text);

    InputStream testInput = new ByteArrayInputStream(byteArray);
    InputStream old = System.in;
    try
      {
        System.setIn(testInput);
        return callable.call();
      }
    catch (Exception e)
      {
        Util.WriteStackTraceAndExit(1, e);
        return null;
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
        Util.WriteStackTraceAndExit(1);
        return null;
      }
  }

  public static <T> HashSet<T> HashSetOf(T... values)
  {
    return Stream.of(values).collect(Collectors.toCollection(HashSet::new));
  }

  static File toFile(String uri)
  {
    try
      {
        return new File(new URI(uri));
      }
    catch (URISyntaxException e)
      {
        Util.WriteStackTraceAndExit(1);
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

  static void WriteStackTraceAndExit(int status)
  {
    var throwable = new Throwable();
    throwable.fillInStackTrace();
    WriteStackTrace(throwable);
    System.exit(status);
  }

  private static void WriteStackTraceAndExit(int status, Throwable e)
  {
    WriteStackTrace(e);
    System.exit(status);
  }

  public static String toString(Throwable th)
  {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    th.printStackTrace(pw);
    return sw.toString();
  }

  public static void WriteStackTrace(Throwable e)
  {
    var file =
      Util.writeToTempFile(e.getMessage() + System.lineSeparator() + toString(e), "fuzion-lsp-crash", ".log", false);
    if (Main.DEBUG())
      {
        Main.getLanguageClient()
          .showMessage(new MessageParams(MessageType.Error,
            "fuzion language server crashed." + System.lineSeparator() + " Log: " + file.getAbsolutePath()));
      }
  }

}
