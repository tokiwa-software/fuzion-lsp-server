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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import dev.flang.lsp.server.Config;
import dev.flang.lsp.server.enums.Transport;

public class IO
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

  public static <T> T WithRedirectedStdOut(Callable<T> callable)
  {
    if (Config.transport() == Transport.tcp)
      {
        return callOrPanic(callable);
      }
    synchronized (DEV_NULL)
      {
        var out = System.out;
        try
          {
            System.setOut(DEV_NULL);
            return callable.call();
          }
        catch (Exception e)
          {
            ErrorHandling.WriteStackTraceAndExit(1, e);
            return null;
          } finally
          {
            System.setOut(out);
          }
      }
  }

  public static <T> T WithRedirectedStdErr(Callable<T> callable)
  {
    if (Config.transport() == Transport.tcp)
      {
        return callOrPanic(callable);
      }
    synchronized (DEV_NULL)
      {
        var err = System.err;
        try
          {
            System.setErr(DEV_NULL);
            return callable.call();
          }
        catch (Exception e)
          {
            ErrorHandling.WriteStackTraceAndExit(1, e);
            return null;
          } finally
          {
            System.setErr(err);
          }
      }
  }

  public static <T> T WithTextInputStream(String text, Callable<T> callable)
  {
    byte[] byteArray = getBytes(text);

    InputStream testInput = new ByteArrayInputStream(byteArray);
    synchronized (DEV_NULL)
      {
        InputStream old = System.in;
        try
          {
            System.setIn(testInput);
            return callable.call();
          }
        catch (Exception e)
          {
            ErrorHandling.WriteStackTraceAndExit(1, e);
            return null;
          } finally
          {
            System.setIn(old);
          }
      }
  }

  private static <T> T callOrPanic(Callable<T> callable)
  {
    try
      {
        return callable.call();
      }
    catch (Exception e)
      {
        ErrorHandling.WriteStackTrace(e);
        return null;
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
  public static Callable<String> WithCapturedStdOutErr(Runnable runnable)
  {
    return () -> {
      synchronized (DEV_NULL)
        {
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
        }
    };
  }

}
