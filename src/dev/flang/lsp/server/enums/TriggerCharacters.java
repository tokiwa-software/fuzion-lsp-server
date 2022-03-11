package dev.flang.lsp.server.enums;

public enum TriggerCharacters
{
  Dot("."), // calls
  Space(" "), // infix, postfix
  LessThan("<"), // types NYI
  r("r"), // for
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
