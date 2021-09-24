package dev.flang.lsp.server;

import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;

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
    if (Main.DEBUG())
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
        Main.getLanguageClient().logMessage(new MessageParams(messageType, result));
      }
  }
}
