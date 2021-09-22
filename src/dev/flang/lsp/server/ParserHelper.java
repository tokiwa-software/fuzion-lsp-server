package dev.flang.lsp.server;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.TreeMap;

import dev.flang.ast.FeatureName;
import dev.flang.ast.Types;
import dev.flang.fe.FrontEnd;
import dev.flang.fe.FrontEndOptions;
import dev.flang.util.Errors;
import dev.flang.util.SourcePosition;

public class ParserHelper
{

  /**
   * maps temporary files which are fed to the parser to their original uri.
   */
  private static TreeMap<String, String> tempFile2Uri = new TreeMap<>();

  /**
   * create MIR and store main feature in Memory.Main
   * for future use
   * @param uri
   */
  public static void Parse(String uri)
  {
    synchronized (tempFile2Uri)
      {

        // NYI
        if (uri.contains("/lib/"))
          {
            return;
          }

        File tempFile = ParserHelper.toTempFile(uri);

        Util.WithRedirectedStdOut(() -> {
          Util.WithRedirectedStdErr(() -> {
            // NYI remove once we can create MIR multiple times
            Errors.clear();
            Types.clear();
            FeatureName.clear();

            var frontEndOptions =
              new FrontEndOptions(0, new dev.flang.util.List<>(), 0, false, false, tempFile.getAbsolutePath());
            var main = new FrontEnd(frontEndOptions).createMIR().main();
            Memory.setMain(main);
          });
        });
      }
    if(Main.DEBUG()){
      ASTPrinter.printAST(Memory.getMain());
    }
  }

  public static String getUri(SourcePosition sourcePosition)
  {
    var result = tempFile2Uri.get("file:" + sourcePosition._sourceFile._fileName.toString());
    if (result != null)
      {
        return result;
      }
    return "file://" + sourcePosition._sourceFile._fileName.toString();
  }

  private static File toTempFile(String uri)
  {
    File sourceFile = Util.writeToTempFile(FuzionTextDocumentService.getText(uri));
    tempFile2Uri.put(sourceFile.toURI().toString(), uri);
    return sourceFile;
  }

  private static File getDummyFile()
  {
    try
      {
        var file = File.createTempFile("000000", ".fz");
        FileWriter writer = new FileWriter(file);
        writer.write("nothing is");
        writer.close();
        return file;
      }
    catch (IOException e)
      {
        Log.write("parsing failed");
        Log.write(e.getStackTrace().toString());
        return null;
      }
  }

}
