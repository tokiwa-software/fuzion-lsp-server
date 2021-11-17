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
 * Source of class IO
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.server.util;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;

import dev.flang.lsp.server.Config;

public class IO
{
  public static final PrintStream SYS_OUT = System.out;
  public static final PrintStream SYS_ERR = System.err;
  public static final InputStream SYS_IN = System.in;
  private static final PrintStream CLIENT_OUT = createCaptureStream(MessageType.Log);
  private static final PrintStream CLIENT_ERR = createCaptureStream(MessageType.Error);

  static byte[] getBytes(String text)
  {
    byte[] byteArray = new byte[0];
    try
      {
        byteArray = text.getBytes("UTF-8");
      }
    catch (UnsupportedEncodingException e)
      {
        ErrorHandling.WriteStackTraceAndExit(1);
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

  public static File writeToTempFile(String text, String prefix, String extension, boolean deleteOnExit)
  {
    try
      {
        File tempFile = File.createTempFile(prefix + String.valueOf(System.currentTimeMillis()), extension);
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
        ErrorHandling.WriteStackTraceAndExit(1);
        return null;
      }
  }

  public synchronized static <T> T WithTextInputStream(String text, Callable<T> callable)
  {
    byte[] byteArray = getBytes(text);
    try
      {
        System.setIn(new ByteArrayInputStream(byteArray));
        return callable.call();
      }
    catch (Exception e)
      {
        ErrorHandling.WriteStackTraceAndExit(1, e);
        return null;
      } finally
      {
        IO.RedirectErrOutToClientLog();
      }
  }

  public static Path PathOf(URI uri)
  {
    return Path.of(uri);
  }

  /**
   * @param runnable
   * @return callable to be run on an executor.
   * The result of the callable is everything that is written to stdout/stderr by the runnable.
   */
  public synchronized static Callable<String> WithCapturedStdOutErr(Runnable runnable)
  {
    return () -> {
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
          IO.RedirectErrOutToClientLog();
        }
    };
  }

  public static void RedirectErrOutToClientLog()
  {
    System.setOut(CLIENT_OUT);
    System.setErr(CLIENT_ERR);
    System.setIn(new PipedInputStream());
  }

  private static PrintStream createCaptureStream(MessageType messageType)
  {
    try
      {

        var inputStream = new PipedInputStream();
        var reader = new BufferedReader(new InputStreamReader(inputStream));
        var result = new PrintStream(new PipedOutputStream(inputStream));
        Concurrency.RunInBackground(
          () -> {
            try
              {
                while (true)
                  {
                    var line = "io: " + reader.readLine();
                    if (Config.languageClient() != null)
                      Config.languageClient().logMessage(new MessageParams(messageType, line));
                  }
              }
            catch (IOException e)
              {
                System.exit(1);
              }
          });
        return result;
      }
    catch (IOException e)
      {
        System.exit(1);
        return null;
      }
  }

}
