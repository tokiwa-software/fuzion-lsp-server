package dev.flang.lsp.server;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.TreeMap;

import org.eclipse.lsp4j.MessageType;

import dev.flang.ast.Feature;
import dev.flang.ast.FeatureName;
import dev.flang.ast.Types;
import dev.flang.fe.FrontEnd;
import dev.flang.fe.FrontEndOptions;
import dev.flang.lsp.server.feature.Diagnostics;
import dev.flang.util.Errors;
import dev.flang.util.SourcePosition;

public class ParserHelper
{

  /**
   * maps temporary files which are fed to the parser to their original uri.
   */
  private static TreeMap<String, String> tempFile2Uri = new TreeMap<>();
  private static TreeMap<String, Feature> parserCache = new TreeMap<>();

  /**
   * @param uri
   */
  public static Feature getMainFeature(String uri)
  {
    synchronized (tempFile2Uri)
      {

        // NYI
        if (uri.contains("/lib/"))
          {
            return null;
          }

        var sourceText = FuzionTextDocumentService.getText(uri);
        if (parserCache.containsKey(sourceText))
          {
            return parserCache.get(sourceText);
          }
        var mainFeature = Parse(uri);
        parserCache.put(sourceText, mainFeature);

        var result = getMainFeature(uri);

        afterParsing(uri, result);

        return result;
      }
  }

  private static void afterParsing(String uri, Feature mainFeature)
  {
    Memory.EndOfFeature.clear();

    //NYI publish diagnostics throttled after change of text document
    Diagnostics.publishDiagnostics(uri);

    if (Main.DEBUG())
      {
        ASTPrinter.printAST(mainFeature);
      }
  }

  private static Feature Parse(String uri)
  {
    File tempFile = ParserHelper.toTempFile(uri);

    var mainFeature = Util.WithRedirectedStdOut(() -> {
      return Util.WithRedirectedStdErr(() -> {
        // NYI remove once we can create MIR multiple times
        Errors.clear();
        Types.clear();
        FeatureName.clear();

        var frontEndOptions =
          new FrontEndOptions(0, new dev.flang.util.List<>(), 0, false, false, tempFile.getAbsolutePath());
        return new FrontEnd(frontEndOptions).createMIR().main();
      });
    });
    return mainFeature;
  }

  /**
   * get original URI of given sourcePosition
   * necessary because we are feeding the parser temporary files
   * @param sourcePosition
   * @return
   */
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
        Log.message("parsing failed", MessageType.Error);
        Log.message(e.getStackTrace().toString(), MessageType.Error);
        return null;
      }
  }

}
