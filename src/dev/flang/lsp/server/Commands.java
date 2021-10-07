package dev.flang.lsp.server;

public enum Commands
{
  showSyntaxTree,
  evaluate;

  public String toString()
  {
    switch (this)
      {
        case showSyntaxTree :
          return "Show Syntax Tree";
        case evaluate :
          return "evaluate file";
        default:
          return "not implemented";
      }
  }
}
