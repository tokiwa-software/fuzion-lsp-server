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
import dev.flang.ast.Feature;
import dev.flang.ast.Feature.State;
import dev.flang.lsp.server.enums.TokenModifier;
import dev.flang.lsp.server.enums.TokenType;
import dev.flang.parser.Lexer.Token;
import dev.flang.shared.CallTool;
import dev.flang.shared.FeatureTool;
import dev.flang.shared.ParserTool;
import dev.flang.shared.SourceText;
import dev.flang.shared.Util;
import dev.flang.util.ANY;
import dev.flang.util.HasSourcePosition;
import dev.flang.util.SourcePosition;

/**
 * holds text of lexer token and the start position of the token
 */
public record TokenInfo(SourcePosition start, String text, Token token)
{
  //NYI this is too expensive
  public SourcePosition end()
  {
    var lines = (int) text.lines().count();
    return new SourcePosition(start._sourceFile, start._line + lines - 1,
      lines == 1 ? start._column + length(): Util.CharCount(Util.LastOrDefault(text.lines(), "")) + 1);
  }

  /*
   * starting line of token, zero based
   */
  private Integer line()
  {
    return start._line == 0 ? 0: start._line - 1;
  }

  /*
  * startChar of token, zero based
  */
  private Integer startChar()
  {
    if (start._column == 0)
      {
        return 0;
      }
    return SourceText
      .LineAt(start)
      .codePoints()
      .limit(start._column - 1)
      .map(cp -> Character.charCount(cp))
      .sum();
  }

  private Integer length()
  {
    return Util.CharCount(text());
  }

  public Stream<Integer> SemanticTokenData(TokenInfo previousToken,
    Map<Integer, HasSourcePosition> pos2Item)
  {
    var tokenType = TokenType(pos2Item);
    if (length() < 1)
      {
        return Stream.empty();
      }

    int relativeLine = line() - previousToken.line();
    int relativeChar = startChar() - (IsSameLine(previousToken) ? previousToken.startChar(): 0);
    Integer tokenTypeNum = tokenType.get().num;

    if (ANY.CHECKS)
      ANY.check(relativeLine != 0 || relativeChar >= previousToken.length());

    return Stream.of(
      relativeLine,
      relativeChar,
      length(),
      tokenTypeNum,
      Modifiers(pos2Item));
  }

  private boolean IsSameLine(TokenInfo previousToken)
  {
    return line().equals(previousToken.line());
  }

  // NYI
  private Integer Modifiers(Map<Integer, HasSourcePosition> pos2Item)
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

  public boolean IsSemanticToken(Map<Integer, HasSourcePosition> pos2Item)
  {
    return TokenType(pos2Item).isPresent();
  }

  private Optional<TokenType> TokenType(Map<Integer, HasSourcePosition> pos2Item)
  {
    if (token.isKeyword())
      {
        switch (token)
          {
          case t_lazy :
          case t_synchronized :
          case t_const :
          case t_leaf :
          case t_infix :
          case t_prefix :
          case t_postfix :
          case t_export :
          case t_private :
          case t_protected :
          case t_public :
            return Optional.of(TokenType.Modifier);
          default:
            return Optional.of(TokenType.Keyword);
          }
      }
    switch (token)
      {
      case t_comment :
        return Optional.of(TokenType.Comment);
      case t_numliteral :
        return Optional.of(TokenType.Number);
      case t_stringQQ :
      case t_StringDQ :
        return Optional.of(TokenType.String);
      case t_stringQD :
      case t_stringQB :
      case t_StringDD :
      case t_StringDB :
      case t_stringBQ :
      case t_stringBD :
      case t_stringBB :
        if (ANY.PRECONDITIONS)
          ANY.check(false);
        return Optional.empty();
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
          .map(TokenInfo::TokenType)
          // NYI check if all cases are considered
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

  private static Optional<TokenType> TokenType(HasSourcePosition item)
  {
    if (item instanceof AbstractFeature af)
      {
        if (af instanceof Feature f && f.state().equals(State.ERROR))
          {
            return Optional.empty();
          }
        switch (af.kind())
          {
          case OpenTypeParameter :
          case TypeParameter :
            return Optional.of(TokenType.TypeParameter);
          case Field :
            if (FeatureTool.IsArgument(af))
              {
                return Optional.of(TokenType.Parameter);
              }
            return Optional.of(TokenType.Property);
          case Choice :
            return Optional.of(TokenType.Enum);
          case Intrinsic :
          case Abstract :
          case Routine :
            if (af.isConstructor()
              && af.valueArguments().size() == 0
              && af.code().containsOnlyDeclarations()
              && !FeatureTool.DoesInherit(af))
              {
                if (ParserTool.DeclaredFeatures(af).count() > 0)
                  {
                    return Optional.of(TokenType.Namespace);
                  }
                if (FeatureTool.IsUsedInChoice(af))
                  {
                    return Optional.of(TokenType.EnumMember);
                  }
                return Optional.of(TokenType.Type);
              }
            if (af.isConstructor())
              {
                return Optional.of(TokenType.Class);
              }
            if (FeatureTool.outerFeatures(af).allMatch(x -> x.valueArguments().size() == 0))
              {
                return Optional.of(TokenType.Function);
              }
            return Optional.of(TokenType.Method);
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
    // "normal" call
    return TokenType(ac.calledFeature());
  }

  private Optional<HasSourcePosition> GetItem(Map<Integer, HasSourcePosition> pos2Item)
  {
    var key = KeyOf(start);
    if (!pos2Item.containsKey(key))
      {
        return Optional.empty();
      }
    return Optional.of(pos2Item
      .get(key));
  }

  // NYI move this somewhere better
  public static Integer KeyOf(SourcePosition pos)
  {
    // NYI better key
    return pos._line * 1000 + pos._column;
  }

}
