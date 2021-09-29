package dev.flang.lsp.server;

import dev.flang.util.ANY;
import dev.flang.util.SourcePosition;

/**
 * holds text of lexer token and the start position of the token
 */
public class TokenInfo extends ANY
{
  public TokenInfo(SourcePosition start, String text)
  {
    this.start = start;
    this.text = text;
  }

  public final SourcePosition start;
  public final String text;
}
