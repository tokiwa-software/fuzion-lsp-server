package dev.flang.lsp.server;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Optional;
import java.util.TreeMap;

import org.eclipse.lsp4j.MessageType;

import dev.flang.ast.Feature;
import dev.flang.ast.FeatureName;
import dev.flang.ast.Types;
import dev.flang.be.interpreter.Interpreter;
import dev.flang.fe.FrontEnd;
import dev.flang.fe.FrontEndOptions;
import dev.flang.fuir.FUIR;
import dev.flang.ir.Clazzes;
import dev.flang.lsp.server.records.ParserCache;
import dev.flang.me.MiddleEnd;
import dev.flang.mir.MIR;
import dev.flang.opt.Optimizer;
import dev.flang.util.Errors;
import dev.flang.util.SourcePosition;

/**
 * - does the parsing of a given URI
 * - caches parsing results.
 * - provides a function to get the original URI of a SourcePosition
 */
public class ParserHelper
{

  /**
   * maps temporary files which are fed to the parser to their original uri.
   */
  private static TreeMap<String, String> tempFile2Uri = new TreeMap<>();
  private static TreeMap<String, ParserCache> uri2ParserCache = new TreeMap<>();
  private static TreeMap<String, String> uri2SourceText = new TreeMap<>();

  /**
   * @param uri
   */
  public static Optional<Feature> getMainFeature(String uri)
  {
    synchronized (tempFile2Uri)
      {

        // NYI
        if (uri.contains("/lib/"))
          {
            if (uri2ParserCache.isEmpty())
              {
                return Optional.empty();
              }
            return Optional.of(uri2ParserCache.firstEntry().getValue().mir().main());
          }

        if (cacheContains(uri))
          {
            return Optional.of(uri2ParserCache.get(uri).mir().main());
          }

        createMIRandCache(uri);

        var result = getMainFeature(uri).get();

        afterParsing(uri, result);

        return Optional.of(result);
      }
  }

  private static boolean cacheContains(String uri)
  {
    var sourceText = FuzionTextDocumentService.getText(uri).orElseThrow();
    return uri2ParserCache.containsKey(uri) && sourceText.equals(uri2SourceText.get(uri));
  }

  private static void createMIRandCache(String uri)
  {
    var sourceText = FuzionTextDocumentService.getText(uri).orElseThrow();
    uri2ParserCache.put(uri, parserCache(uri));
    uri2SourceText.put(uri, sourceText);
  }

  private static ParserCache parserCache(String uri)
  {
    var frontEndOptions = FrontEndOptions(uri);
    var mir = MIR(frontEndOptions);
    return new ParserCache(mir, frontEndOptions);
  }

  private static void afterParsing(String uri, Feature mainFeature)
  {
    // NYI make this less bad
    Memory.EndOfFeature.clear();

    if (Config.DEBUG())
      {
        ASTtoHTML.printAST(mainFeature);
      }
  }

  public static FUIR FUIR(String uri)
  {
    // NYI remove recreation of MIR
    createMIRandCache(uri);

    var frontEndOptions = uri2ParserCache.get(uri).frontEndOptions();
    var air = new MiddleEnd(frontEndOptions, uri2ParserCache.get(uri).mir()).air();
    return new Optimizer(frontEndOptions, air).fuir(false);
  }

  private static MIR MIR(FrontEndOptions frontEndOptions)
  {
    var mir = Util.WithRedirectedStdOut(() -> {
      return Util.WithRedirectedStdErr(() -> {
        // NYI remove once we can create MIR multiple times
        Errors.clear();
        Types.clear();
        FeatureName.clear();
        Clazzes.clear();
        Interpreter.clear();
        return new FrontEnd(frontEndOptions).createMIR();
      });
    });
    return mir;
  }

  private static FrontEndOptions FrontEndOptions(String uri)
  {
    File tempFile = ParserHelper.toTempFile(uri);
    var frontEndOptions = FrontEndOptions(tempFile);
    return frontEndOptions;
  }

  private static FrontEndOptions FrontEndOptions(File tempFile)
  {
    var frontEndOptions =
      new FrontEndOptions(0, new dev.flang.util.List<>(), 0, false, false, tempFile.getAbsolutePath());
    return frontEndOptions;
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
    File sourceFile = Util.writeToTempFile(FuzionTextDocumentService.getText(uri).orElseThrow());
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
