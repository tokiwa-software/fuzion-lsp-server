package dev.flang.lsp.server;

import dev.flang.lsp.server.Main.Transport;

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

  public static void write(String str)
  {

    if (Main.DEBUG() && Main.transport == Transport.tcp && indentation < MAX_INDENT)
      {
        var lines = str.split("\n");
        for(String line : lines)
          {
            System.out.println(" ".repeat(indentation * 2) + line);
            if (indentation == MAX_INDENT - 1)
              {
                System.out.println("...");
              }
          }
      }
  }
}
