package dev.flang.lsp.server.records;

import dev.flang.util.SourcePosition;

/**
 * holds text of lexer token and the start position of the token
 */
public record TokenInfo(SourcePosition start, String text)
{
  public SourcePosition end(){
    return new SourcePosition(start._sourceFile, start._line, start._column + text.length());
  }
}
