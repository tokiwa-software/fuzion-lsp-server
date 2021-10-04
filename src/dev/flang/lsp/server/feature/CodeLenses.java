package dev.flang.lsp.server.feature;

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.Command;

import dev.flang.ast.Feature;
import dev.flang.lsp.server.Commands;
import dev.flang.lsp.server.Converters;
import dev.flang.lsp.server.FuzionHelpers;
import dev.flang.lsp.server.Util;

public class CodeLenses
{

  public static List<CodeLens> getCodeLenses(CodeLensParams params)
  {
    var uri = Util.getUri(params.getTextDocument());
    return FuzionHelpers.allOf(uri, Feature.class)
      .filter(FuzionHelpers.IsFeatureInFile(uri))
      .filter(f -> FuzionHelpers.IsRoutineOrRoutineDef(f))
      .filter(f -> !FuzionHelpers.IsAnonymousInnerFeature(f))
      // NYI can we support features with primite args?
      .filter(f -> f.arguments.isEmpty())
      // NYI range should only spawn one line
      .map(f -> new CodeLens(Converters.ToRange(f),
        new Command(Commands.evaluate.toString(), Commands.evaluate.toString(),
          List.of(FuzionHelpers.getString(uri, Converters.ToRange(f)))),
        null))
      .collect(Collectors.toList());
  }

}
