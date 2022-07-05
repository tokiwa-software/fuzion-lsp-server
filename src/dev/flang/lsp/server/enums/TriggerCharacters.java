package dev.flang.lsp.server.enums;

public enum TriggerCharacters
{
  Dot("."), // calls
  Space(" "), // infix, postfix, types
  LessThan("<"), // types NYI
  None("");

  private final String triggerChar;

  private TriggerCharacters(String s)
  {
    triggerChar = s;
  }

  public String toString()
  {
    return this.triggerChar;
  }
}
