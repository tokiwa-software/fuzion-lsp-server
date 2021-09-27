package dev.flang.lsp.server.feature;

import java.util.stream.Stream;
import java.util.Optional;
import java.util.AbstractMap.SimpleEntry;
import java.util.stream.Collectors;

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
import dev.flang.lsp.server.FuzionHelpers;
import dev.flang.lsp.server.Util;
import dev.flang.util.SourcePosition;

public class Rename
{

  public static WorkspaceEdit getWorkspaceEdit(RenameParams params)
  {
    if (!FuzionHelpers.IsValidIdentifier(params.getNewName()))
      {
        var responseError = new ResponseError(ResponseErrorCode.InvalidParams, "new name no valid identifier.", null);
        throw new ResponseErrorException(responseError);
      }

    Optional<Object> itemToRename = CallsAndFeatures(params)
      .findFirst();

    if (itemToRename.isEmpty())
      {
        var responseError = new ResponseError(ResponseErrorCode.InvalidRequest, "nothing found for renaming.", null);
        throw new ResponseErrorException(responseError);
      }

    var featureToRename = getFeature(itemToRename.get());
    var callPositions = FuzionHelpers.callsTo(featureToRename).map(c -> c.pos());

    //NYI support e.g. infix features
    Stream<SourcePosition> renamePositions = Stream.concat(callPositions, Stream.of(featureToRename.pos()));

    var changes = renamePositions
      .map(sourcePosition -> FuzionHelpers.ToLocation(sourcePosition))
      .map(location -> new SimpleEntry<String, TextEdit>(location.getUri(),
        getEdit(location, featureToRename.featureName().baseName(), params.getNewName())))
      .collect(Collectors.groupingBy(e -> e.getKey(), Collectors.mapping(e -> e.getValue(), Collectors.toList())));

    return new WorkspaceEdit(changes);
  }

  private static TextEdit getEdit(Location location, String oldName, String newText)
  {
    var startPos = location.getRange().getStart();
    var endPos = new Position(startPos.getLine(), startPos.getCharacter() + oldName.length());
    return new TextEdit(new Range(startPos, endPos), newText);
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
  public static PrepareRenameResult getPrepareRenameResult(PrepareRenameParams params)
  {
    var tokenPosition = FuzionHelpers.getTokenIdentifier(params);
    if (tokenPosition == null || !IsAtCallOrFeature(params, tokenPosition.start._column))
      {
        var responseError = new ResponseError(ResponseErrorCode.InvalidParams, "no valid identifier.", null);
        throw new ResponseErrorException(responseError);
      }
    var end = new SourcePosition(tokenPosition.start._sourceFile, tokenPosition.start._line,
      tokenPosition.start._column + tokenPosition.text.length());
    return new PrepareRenameResult(FuzionHelpers.ToRange(tokenPosition.start, end), tokenPosition.text);
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
