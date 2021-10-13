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
    return Stream.of(codeLensEvaluateFile(uri), codeLensShowSyntaxTree(params))
      .collect(Collectors.toList());
  }

  private static CodeLens codeLensShowSyntaxTree(CodeLensParams params)
  {
    var command = new Command(Commands.showSyntaxTree.toString(), Commands.showSyntaxTree.name(),
      List.of(params.getTextDocument().getUri()));
    return new CodeLens(new Range(new Position(0, 0), new Position(0, 1)), command, null);
  }

  private static CodeLens codeLensEvaluateFile(String uri)
  {
    Command command = new Command(Commands.evaluate.toString(), Commands.evaluate.name(),
      List.of(uri));
    return new CodeLens(new Range(new Position(0, 0), new Position(0, 1)),
      command,
      null);
  }

}
