package dev.flang.lsp.server;

import java.util.stream.Collectors;

import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentPositionParams;

import dev.flang.ast.Feature;
import dev.flang.util.SourcePosition;

/**
 * collection of static methods converting dev.flang objects to lsp4j
 */
public final class Converters {

  public static Position ToPosition(SourcePosition sourcePosition)
  {
    return new Position(sourcePosition._line - 1, sourcePosition._column - 1);
  }

  public static Location ToLocation(SourcePosition sourcePosition)
  {
    var position = ToPosition(sourcePosition);
    return new Location(ParserHelper.getUri(sourcePosition), new Range(position, position));
  }

  public static Range ToRange(Feature feature)
  {
    return new Range(ToPosition(feature.pos()), ToPosition(FuzionHelpers.getEndOfFeature(feature)));
  }

  public static Range ToRange(TextDocumentPositionParams params)
  {
    var tokenIdent = FuzionHelpers.nextToken(params);
    var line = params.getPosition().getLine();
    var characterStart = tokenIdent.start._column - 1;
    return new Range(new Position(line, characterStart), new Position(line, characterStart + tokenIdent.text.length()));
  }

  public static DocumentSymbol ToDocumentSymbol(Feature feature)
  {
    return new DocumentSymbol(Converters.ToLabel(feature), SymbolKind.Key, ToRange(feature), ToRange(feature));
  }

  /**
   * @param feature
   * @return example: array<T>(length i32, init Function<array.T, i32>) => array<array.T>
   */
  public static String ToLabel(Feature feature)
  {
    if (!FuzionHelpers.IsRoutineOrRoutineDef(feature))
      {
        return feature.featureName().baseName();
      }
    var arguments = "(" + feature.arguments.stream()
      .map(a -> a.thisType().featureOfType().featureName().baseName() + " " + a.thisType().featureOfType().resultType())
      .collect(Collectors.joining(", ")) + ")";
    return feature.featureName().baseName() + feature.generics + arguments + " => " + feature.resultType();
  }

}
