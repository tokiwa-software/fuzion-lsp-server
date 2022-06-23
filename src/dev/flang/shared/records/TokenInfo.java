/*

This file is part of the Fuzion language server protocol implementation.

The Fuzion language server protocol implementation is free software: you can redistribute it
and/or modify it under the terms of the GNU General Public License as published
by the Free Software Foundation, version 3 of the License.

The Fuzion language server protocol implementation is distributed in the hope that it will be
useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public
License for more details.

You should have received a copy of the GNU General Public License along with The
Fuzion language implementation.  If not, see <https://www.gnu.org/licenses/>.

*/

/*-----------------------------------------------------------------------
 *
 * Tokiwa Software GmbH, Germany
 *
 * Source of class TokenInfo
 *
 *---------------------------------------------------------------------*/

package dev.flang.shared.records;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import dev.flang.ast.AbstractCall;
import dev.flang.ast.AbstractFeature;
import dev.flang.lsp.server.enums.TokenModifier;
import dev.flang.lsp.server.enums.TokenType;
import dev.flang.lsp.server.util.CallTool;
import dev.flang.parser.Lexer.Token;
import dev.flang.shared.FeatureTool;
import dev.flang.util.HasSourcePosition;
import dev.flang.util.SourcePosition;

/**
 * holds text of lexer token and the start position of the token
 */
public record TokenInfo(SourcePosition start, String text, Token token)
{
  public SourcePosition end()
  {
    return new SourcePosition(start._sourceFile, start._line, start._column + text.length());
  }

  /*
   * starting line of token, zero based
   */
  private Integer line()
  {
    return start._line - 1;
  }

  /*
  * starting column of token, zero based
  */
  private Integer startChar()
  {
    switch (token)
      {
      case t_StringDD :
      case t_StringDB :
      case t_stringBD :
      case t_stringBB :
        return start._column - 1 + 1;
      default:
        return start._column - 1;
      }
  }

  private Integer length()
  {
    switch (token)
      {
      case t_stringQD :
      case t_stringQB :
        return text.length() - 1;
      case t_StringDD :
      case t_StringDB :
      case t_stringBD :
      case t_stringBB :
        return text.length() - 2;
      default:
        return text.length();
      }
  }

  public Stream<Integer> SemanticTokenData(Optional<TokenInfo> previousToken,
    Map<Integer, HashSet<HasSourcePosition>> pos2Item)
  {
    var tokenType = TokenType(pos2Item);
    if (length() < 1)
      {
        return Stream.empty();
      }

    return previousToken.map(x -> {
      var IsSameLine = line() == x.line();
      return Stream.of(line() - x.line(), startChar() - (IsSameLine ? x.startChar(): 0),
        length(), tokenType.get().num, Modifiers(pos2Item));
    })
      .orElse(Stream.of(line(), startChar(), length(), tokenType.get().num, Modifiers(pos2Item)));
  }

  // NYI
  private Integer Modifiers(Map<Integer, HashSet<HasSourcePosition>> pos2Item)
  {
    switch (token)
      {
      case t_ident :
        var modifiers = new HashSet<TokenModifier>();
        GetItem(pos2Item)
          .ifPresent(item -> {
            if (item instanceof AbstractFeature af)
              {
                if (af.isAbstract())
                  {
                    modifiers.add(TokenModifier.Abstract);
                  }
                if (af.isField())
                  {
                    modifiers.add(TokenModifier.Readonly);
                  }
              }
          });
        return TokenModifier.DataOf(modifiers);
      default:
        return 0;
      }
  }

  public boolean IsSemanticToken(Map<Integer, HashSet<HasSourcePosition>> pos2Item)
  {
    return TokenType(pos2Item).isPresent();
  }

  private Optional<TokenType> TokenType(Map<Integer, HashSet<HasSourcePosition>> pos2Item)
  {
    if (token.isKeyword())
      {
        return Optional.of(TokenType.Keyword);
      }
    switch (token)
      {
      case t_comment :
        return Optional.of(TokenType.Comment);
      case t_numliteral :
        return Optional.of(TokenType.Number);
      case t_stringQQ :
      case t_stringQD :
      case t_stringQB :
      case t_StringDQ :
      case t_StringDD :
      case t_StringDB :
      case t_stringBQ :
      case t_stringBD :
      case t_stringBB :
        return Optional.of(TokenType.String);
      case t_op :
        if (text.equals("=>")
          || text.equals("->")
          || text.equals(":="))
          {
            return Optional.of(TokenType.Keyword);
          }
        return Optional.of(TokenType.Operator);
      case t_ident :
        return GetItem(pos2Item)
          .map(TokenInfo::ItemToToken)
          // NYI
          .orElse(Optional.of(TokenType.Type));
      case t_error :
      case t_ws :
      case t_comma :
      case t_lparen :
      case t_rparen :
      case t_lbrace :
      case t_rbrace :
      case t_lcrochet :
      case t_rcrochet :
      case t_semicolon :
      case t_eof :
      case t_indentationLimit :
      case t_lineLimit :
      case t_spaceLimit :
      case t_undefined :
        return Optional.empty();
      default:
        throw new RuntimeException("not implemented.");
      }
  }

  private static Optional<TokenType> ItemToToken(HasSourcePosition item)
  {
    if (item instanceof AbstractFeature af)
      {
        switch (af.kind())
          {
          case Field :
            if (FeatureTool.IsArgument(af))
              {
                return Optional.of(TokenType.Parameter);
              }
            return Optional.of(TokenType.Property);
          case OpenTypeParameter :
          case TypeParameter :
            return Optional.of(TokenType.TypeParameter);
          case Choice :
            return Optional.of(TokenType.Enum);
          case Intrinsic :
          case Abstract :
          case Routine :
            if (FeatureTool.IsNamespaceLike(af))
              {
                return Optional.of(TokenType.Namespace);
              }
            // NYI
            return Optional.of(TokenType.Class);
          }
      }
    var ac = (AbstractCall) item;
    if (ac.isInheritanceCall())
      {
        return Optional.of(TokenType.Interface);
      }
    if (CallTool.IsFixLikeCall(ac))
      {
        return Optional.of(TokenType.Operator);
      }
    // NYI
    return Optional.<TokenType>empty();
  }

  private Optional<HasSourcePosition> GetItem(Map<Integer, HashSet<HasSourcePosition>> pos2Item)
  {
    var key = KeyOf(start);
    if (!pos2Item.containsKey(key))
      {
        return Optional.empty();
      }
    return pos2Item
      .get(key)
      .stream()
      .filter(x -> {
        if (x instanceof AbstractFeature af)
          {
            return af.featureName().baseName().equals(text);
          }
        return ((AbstractCall) x).calledFeature().featureName().baseName().equals(text);
      })
      .findFirst();
  }

  // NYI move this somewhere better
  public static Integer KeyOf(SourcePosition pos)
  {
    // NYI better key
    return pos._line * 1000 + pos._column;
  }

}
