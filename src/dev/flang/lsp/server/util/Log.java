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

import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;

import dev.flang.lsp.server.Config;

public class Log
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
}
