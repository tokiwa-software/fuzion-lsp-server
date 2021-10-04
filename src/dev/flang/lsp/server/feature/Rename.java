package dev.flang.lsp.server.feature;

import java.util.AbstractMap.SimpleEntry;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PrepareRenameParams;
import org.eclipse.lsp4j.PrepareRenameResult;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;

import dev.flang.ast.Call;
import dev.flang.ast.Feature;
import dev.flang.lsp.server.Converters;
import dev.flang.lsp.server.FuzionHelpers;
import dev.flang.lsp.server.TokenInfo;
import dev.flang.lsp.server.Util;
import dev.flang.parser.Lexer.Token;
import dev.flang.util.SourcePosition;

/**
 * for rename request return all appropriate TextEdits
 * https://microsoft.github.io/language-server-protocol/specification#textDocument_rename
 * https://microsoft.github.io/language-server-protocol/specification#textDocument_prepareRename
 */
public class Rename
{

  public static WorkspaceEdit getWorkspaceEdit(RenameParams params)
  {
    if (!FuzionHelpers.IsValidIdentifier(params.getNewName()))
      {
        var responseError = new ResponseError(ResponseErrorCode.InvalidParams, "new name no valid identifier.", null);
        throw new ResponseErrorException(responseError);
      }

    var feature = getFeature(params);

    var featureIdentifier = FuzionHelpers.nextTokenOfType(feature.featureName().baseName(), Util.HashSetOf(Token.t_ident, Token.t_op));

    Stream<SourcePosition> renamePositions = getRenamePositions(Util.getUri(params), feature, featureIdentifier);

    var changes = renamePositions
      .map(sourcePosition -> Converters.ToLocation(sourcePosition))
      .map(location -> new SimpleEntry<String, TextEdit>(location.getUri(),
        getTextEdit(location, featureIdentifier.text.length(), params.getNewName())))
      .collect(Collectors.groupingBy(e -> e.getKey(), Collectors.mapping(e -> e.getValue(), Collectors.toList())));

    return new WorkspaceEdit(changes);
  }

  /**
   * get the feature that is to be renamed
   * @param params
   * @return
   */
  private static Feature getFeature(RenameParams params)
  {
    Optional<Object> itemToRename = CallsAndFeatures(params)
      .findFirst();

    if (itemToRename.isEmpty())
      {
        var responseError = new ResponseError(ResponseErrorCode.InvalidRequest, "nothing found for renaming.", null);
        throw new ResponseErrorException(responseError);
      }

    var featureToRename = getFeature(itemToRename.get());
    return featureToRename;
  }

  /**
   *
   * @param featureToRename
   * @param featureIdentifier
   * @return stream of sourcepositions where renamings must be done
   */
  private static Stream<SourcePosition> getRenamePositions(String uri, Feature featureToRename, TokenInfo featureIdentifier)
  {
    var callsSourcePositions = FuzionHelpers.callsTo(uri, featureToRename).map(c -> c.pos());
    var tokenPosition = new SourcePosition(featureToRename.pos()._sourceFile, featureToRename.pos()._line, featureToRename.pos()._column + featureIdentifier.start._column - 1);
    Stream<SourcePosition> renamePositions = Stream.concat(callsSourcePositions, Stream.of(tokenPosition));
    return renamePositions;
  }

  private static TextEdit getTextEdit(Location location, int lengthOfOldToken, String newText)
  {
    var startPos = location.getRange().getStart();
    var endPos = new Position(startPos.getLine(), startPos.getCharacter() + lengthOfOldToken);
    var result = new TextEdit(new Range(startPos, endPos), newText);
    return result;
  }

  private static Feature getFeature(Object item)
  {
    if (item instanceof Call)
      {
        return ((Call) item).calledFeature();
      }
    return (Feature) item;
  }

  // NYI disallow renaming of stdlib
  // NYI should we disallow renaming in case of source code errors?
  public static PrepareRenameResult getPrepareRenameResult(PrepareRenameParams params)
  {
    var tokenPosition = FuzionHelpers.nextToken(params);
    if (tokenPosition == null || !IsAtCallOrFeature(params, tokenPosition.start._column))
      {
        var responseError = new ResponseError(ResponseErrorCode.InvalidParams, "no valid identifier.", null);
        throw new ResponseErrorException(responseError);
      }
    return new PrepareRenameResult(Converters.ToRange(params), tokenPosition.text);
  }

  private static boolean IsAtCallOrFeature(PrepareRenameParams params, int column)
  {
    return CallsAndFeatures(params).map(obj -> FuzionHelpers.getPosition(obj))
      .filter(pos -> column == pos._column)
      .findFirst()
      .isPresent();
  }

  private static Stream<Object> CallsAndFeatures(TextDocumentPositionParams params)
  {
    return FuzionHelpers.getASTItemsOnLine(params)
      .filter(item -> Util.HashSetOf(Feature.class, Call.class).contains(item.getClass()));
  }

}
