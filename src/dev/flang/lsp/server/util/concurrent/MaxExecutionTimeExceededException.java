package dev.flang.lsp.server.util.concurrent;

public class MaxExecutionTimeExceededException extends Exception
{
  public MaxExecutionTimeExceededException(String message, Throwable cause)
  {
    super(message, cause);
  }
}