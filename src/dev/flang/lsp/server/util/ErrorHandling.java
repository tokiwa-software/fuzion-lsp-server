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
 * Source of class ErrorHandling
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.server.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;

import dev.flang.lsp.server.Config;

public class ErrorHandling {

  public static void WriteStackTraceAndExit(int status)
  {
    var throwable = CurrentStacktrace();
    WriteStackTraceAndExit(status, throwable);
  }

  public static Throwable CurrentStacktrace()
  {
    var throwable = new Throwable();
    throwable.fillInStackTrace();
    return throwable;
  }

  public static void WriteStackTraceAndExit(int status, Throwable e)
  {
    var filePath = WriteStackTrace(e);
    if (Config.DEBUG())
      {
        Config.languageClient()
          .showMessage(new MessageParams(MessageType.Error,
            "fuzion language server crashed." + System.lineSeparator() + " Log: " + filePath));
      }
    System.exit(status);
  }

  private static String toString(Throwable th)
  {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    th.printStackTrace(pw);
    return th.getMessage() + System.lineSeparator() + sw.toString();
  }

  public static String WriteStackTrace(Throwable e)
  {
    var stackTrace = toString(e) + System.lineSeparator()
      + "======" + System.lineSeparator()
      + Thread.getAllStackTraces()
        .entrySet()
        .stream()
        .map(entry -> "Thread: " + entry.getKey().getName() + System.lineSeparator() + String(entry.getValue()))
        .collect(Collectors.joining(System.lineSeparator()));

    return IO
      .writeToTempFile(stackTrace, "fuzion-lsp-crash", ".log", false)
      .getAbsolutePath();
  }

  private static String String(StackTraceElement[] stackTrace)
  {
    var sb = new StringBuilder();
    for(int i = 1; i < stackTrace.length; i++)
      sb.append("\tat " + stackTrace[i] + System.lineSeparator());
    return sb.toString();
  }

}
