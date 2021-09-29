package dev.flang.lsp.server;

public enum Commands
{
  showSyntaxTree;

  public String toString()
  {
    switch (this)
      {
        case showSyntaxTree :
          return "Show Syntax Tree";
        default:
          return "not implemented";
      }
  }
}
