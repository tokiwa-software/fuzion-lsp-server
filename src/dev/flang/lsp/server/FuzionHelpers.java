package dev.flang.lsp.server;

import java.io.*;

import dev.flang.util.Errors;
import dev.flang.ast.FeatureName;
import dev.flang.ast.Types;
import dev.flang.fe.FrontEnd;
import dev.flang.fe.FrontEndOptions;

public class FuzionHelpers {

  public static void Parse(String uri){
    // NYI remove once we can create MIR multiple times
    Errors.clear();
    Types.clear();
    FeatureName.clear();

    // NYI don't read from filesystem but newest version from FuzionTextDocumentService->getText()
    File tempFile = Util.toFile(uri);

    Util.WithRedirectedStdOut(() -> {
      // NYI parsing works only once for now
      if (Memory.Universe != null)
        {
          return;
        }
      var frontEndOptions =
          new FrontEndOptions(0, new dev.flang.util.List<>(), 0, false, false, tempFile.getAbsolutePath());
      var universe = new FrontEnd(frontEndOptions).createMIR().main().universe();
      Memory.Universe = universe;
    });
  }

}
