package dev.flang.lsp.server;

import dev.flang.lsp.server.Main.Transport;

public class Log {
  private static final int MAX_INDENT = 10;
  private static int indentation = 0;

  public static void increaseIndentation(){
    indentation++;
  }

  public static void decreaseIndentation(){
    indentation--;
  }

  public static void write(String str){

    if(Main.DEBUG() && Main.transport == Transport.tcp && indentation < MAX_INDENT){
      System.out.println(" ".repeat(indentation * 2) + str);
      if(indentation == MAX_INDENT - 1){
        System.out.println("...");
      }
    }
  }
}
