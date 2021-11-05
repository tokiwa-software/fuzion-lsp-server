/*

This file is part of the Fuzion language server protocol implementation.

The Fuzion language server protocol implementation is free software: you can redistribute it
and/or modify it under the terms of the GNU General Public License as published
by the Free Software Foundation, version 3 of the License.

The Fuzion language server protocol implementation is distributed in the hope that it will be
useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public
License for more details.

You should have received a copy of the GNU General Public License along with The
Fuzion language implementation.  If not, see <https://www.gnu.org/licenses/>.

*/

/*-----------------------------------------------------------------------
 *
 * Tokiwa Software GmbH, Germany
 *
 * Source of class Util
 *
 *---------------------------------------------------------------------*/

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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
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
import org.eclipse.lsp4j.jsonrpc.CancelChecker;

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

  public static Comparator<? super Object> CompareByHashCode =
    Comparator.comparing(obj -> obj, (obj1, obj2) -> {
      return obj1.hashCode() - obj2.hashCode();
    });

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

  private static final PrintStream StdOut = System.out;

  // for now we have to run most things more or less sequentially
  private static ExecutorService executor = Executors.newSingleThreadExecutor();

  public static MessageParams WithCapturedStdOutErr(Runnable runnable, long timeOutInMilliSeconds)
    throws IOException, InterruptedException, ExecutionException, TimeoutException
  {
    Future<String> future = executor.submit(WithCapturedStdOutErr(runnable));
    try
      {
        var result = future.get(timeOutInMilliSeconds, TimeUnit.MILLISECONDS);
        return new MessageParams(MessageType.Info, result);
      } finally
      {
        if (!future.isCancelled() || !future.isDone())
          {
            future.cancel(true);
          }
      }
  }

  /**
   * @param runnable
   * @return callable to be run on an executor.
   * The result of the callable is everything that is written to stdout/stderr by the runnable.
   */
  private static Callable<String> WithCapturedStdOutErr(Runnable runnable)
  {
    return () -> {
      if (StdOut.hashCode() != System.out.hashCode())
        {
          throw new RuntimeException(
            "System.out compromised. Expected the same stdout as to when StdOut was initialized but found different.");
        }
      var out = System.out;
      var err = System.err;
      var inputStream = new PipedInputStream();
      var outputStream = new PrintStream(new PipedOutputStream(inputStream));
      try
        {
          System.setOut(outputStream);
          System.setErr(outputStream);
          runnable.run();
          // close outputstream so that reading of inputstream does not run
          // inifinitly.
          outputStream.close();
          return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } finally
        {
          outputStream.close();
          inputStream.close();
          System.setOut(out);
          System.setErr(err);
        }
    };
  }

  /**
   * run callable on single thread executor.
   * periodically check if callable meanwhile has been cancelled
   * and/or maximum execution time has been reached
   * @param <T>
   * @param cancelToken
   * @param callable
   * @param periodInMs
   * @param maxExecutionTimeInMs
   * @return
   * @throws InterruptedException
   * @throws ExecutionException
   * @throws TimeoutException
   */
  public static <T> T RunWithPeriodicCancelCheck(
    CancelChecker cancelToken, Callable<T> callable, int periodInMs, int maxExecutionTimeInMs)
    throws InterruptedException, ExecutionException, TimeoutException
  {
    Future<T> future = executor.submit(callable);
    try
      {
        var timeElapsedInMs = 0;
        var completed = false;
        while (!completed)
          {
            try
              {
                future.get(periodInMs, TimeUnit.MILLISECONDS);
                completed = true;
              }
            // when timeout occurs we check
            // if maxExecutionTime has been reached
            // or if cancelToken wants to cancel execution
            catch (TimeoutException e)
              {
                timeElapsedInMs += periodInMs;
                if (timeElapsedInMs >= maxExecutionTimeInMs)
                  {
                    throw e;
                  }
                cancelToken.checkCanceled();
              }
          }
      } finally
      {
        if (!future.isCancelled() || !future.isDone())
          {
            future.cancel(true);
          }
      }
    return future.get();
  }

  public static void RunInBackground(Runnable runnable)
  {
    executor.submit(runnable);
  }

  private static <T> T callOrPanic(Callable<T> callable)
  {
    try
      {
        return callable.call();
      }
    catch (Exception e)
      {
        Util.WriteStackTraceAndExit(1, e);
        return null;
      }
  }

  public static <T> T WithRedirectedStdOut(Callable<T> callable)
  {
    if (Config.transport() == Transport.tcp)
      {
        return callOrPanic(callable);
      }
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
    if (Config.transport() == Transport.tcp)
      {
        return callOrPanic(callable);
      }
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
        return new URI(URLDecoder.decode(uri, StandardCharsets.UTF_8.toString()));
      }
    catch (Exception e)
      {
        Util.WriteStackTraceAndExit(1);
        return null;
      }
  }

  public static <T> HashSet<T> HashSetOf(T... values)
  {
    return Stream.of(values).collect(Collectors.toCollection(HashSet::new));
  }

  public static URI getUri(TextDocumentPositionParams params)
  {
    return getUri(params.getTextDocument());
  }

  public static URI getUri(TextDocumentIdentifier params)
  {
    return toURI(params.getUri());
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
    WriteStackTraceAndExit(status, throwable);
  }

  public static void WriteStackTraceAndExit(int status, Throwable e)
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
    var stackTrace = String(e) + System.lineSeparator()
      + "======" + System.lineSeparator()
      + Thread.getAllStackTraces()
        .entrySet()
        .stream()
        .map(entry -> "Thread: " + entry.getKey().getName() + System.lineSeparator() + String(entry.getValue()))
        .collect(Collectors.joining(System.lineSeparator()));

    var file =
      Util.writeToTempFile(stackTrace, "fuzion-lsp-crash", ".log", false);
    if (Config.DEBUG())
      {
        Config.languageClient()
          .showMessage(new MessageParams(MessageType.Error,
            "fuzion language server crashed." + System.lineSeparator() + " Log: " + file.getAbsolutePath()));
      }
  }

  private static String String(StackTraceElement[] stackTrace)
  {
    var sb = new StringBuilder();
    for(int i = 1; i < stackTrace.length; i++)
      sb.append("\tat " + stackTrace[i] + System.lineSeparator());
    return sb.toString();
  }

  private static String String(Throwable e)
  {
    return e.getMessage() + System.lineSeparator() + toString(e);
  }

  static Path PathOf(URI uri)
  {
    return Path.of(uri);
  }

  /**
   * example: TextDocumentPositionParams("file://uri", 0, 0)
   */
  public static TextDocumentPositionParams TextDocumentPositionParams(URI uri, int line, int character)
  {
    return TextDocumentPositionParams(uri, new Position(line, character));
  }

  public static org.eclipse.lsp4j.TextDocumentPositionParams TextDocumentPositionParams(URI uri, Position position)
  {
    return new TextDocumentPositionParams(TextDocumentIdentifier(uri), position);
  }

  public static TextDocumentIdentifier TextDocumentIdentifier(URI uri)
  {
    return new TextDocumentIdentifier(uri.toString());
  }

}
