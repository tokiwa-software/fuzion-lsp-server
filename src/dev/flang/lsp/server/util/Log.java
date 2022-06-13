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
 * Source of class Log
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.server.util;

import java.time.Duration;
import java.util.concurrent.Callable;

import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;

import dev.flang.lsp.server.Config;
import dev.flang.util.ANY;

public class Log extends ANY
{
  private static final int MAX_INDENT = 10;
  private static int indentation = 0;

  public static void increaseIndentation()
  {
    indentation++;
  }

  public static void decreaseIndentation()
  {
    indentation--;
  }

  public static void message(String str)
  {
    message(str, MessageType.Log);
  }

  public static void message(String str, MessageType messageType)
  {
    String result = "";
    if (Config.DEBUG())
      {
        var lines = str.split("\n");
        for(String line : lines)
          {
            result += " ".repeat(indentation * 2) + line + System.lineSeparator();
            if (indentation == MAX_INDENT - 1)
              {
                result += "..." + System.lineSeparator();
              }
          }
        Config.languageClient().logMessage(new MessageParams(messageType, result));
      }
  }


  /**
   * Log if computation of task takes longer than maxTime
   * @param <T>
   * @param c
   * @param maxTime
   * @param taskDescription
   * @return
   */
  public static <T> T taskExceedsMaxTime(Callable<T> c, Duration maxTime, String taskDescription)
  {
    long startTime = System.nanoTime();
    try
      {
        var result = c.call();
        long stopTime = System.nanoTime();
        var elapsedTime = Duration.ofNanos(Math.round((stopTime - startTime) / 1E6));
        if (maxTime.minus(elapsedTime).isNegative())
          {
            message(taskDescription + " took " + elapsedTime + "ms", MessageType.Warning);
          }
        return result;
      }
    catch (Exception e)
      {
        // c must not throw exception
        check(false);
        return null;
      }
  }


}
