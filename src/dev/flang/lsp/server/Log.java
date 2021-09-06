package dev.flang.lsp.server;

import dev.flang.lsp.server.Main.Transport;

public class Log {
  private static int indentation = 0;

  public static void increaseIndentation(){
    indentation++;
  }

  public static void decreaseIndentation(){
    indentation--;
  }

  public static void write(String str){
    if(Main.DEBUG() && Main.transport == Transport.tcp){
      System.out.println(" ".repeat(indentation * 2) + str);
    }
  }
}
