package dev.flang.lsp.server.feature;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import dev.flang.lsp.server.Commands;
import dev.flang.lsp.server.Util;

public class CodeLenses
{

  public static List<CodeLens> getCodeLenses(CodeLensParams params)
  {
    var uri = Util.getUri(params.getTextDocument());
    return evaluateUri(uri)
      .collect(Collectors.toList());
  }

  private static Stream<CodeLens> evaluateUri(String uri)
  {
    return Stream.of(new CodeLens(new Range(new Position(0, 0), new Position(0, 1)),
      new Command(Commands.evaluate.toString(), Commands.evaluate.toString(),
        List.of(uri)),
      null));
  }

}
