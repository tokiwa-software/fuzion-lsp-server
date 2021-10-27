/*

This file is part of the Fuzion language server protocol implementation.

The Fuzion language server protocol implementation is free software: you can redistribute it
and/or modify it under the terms of the GNU General Public License as published
by the Free Software Foundation, version 3 of the License.

The Fuzion language server protocol implementation is distributed in the hope that it will be
useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public
License for more details.

You should have received a copy of the GNU General Public License along with The
Fuzion language implementation.  If not, see <https://www.gnu.org/licenses/>.

*/

/*-----------------------------------------------------------------------
 *
 * Tokiwa Software GmbH, Germany
 *
 * Source of class ParserHelper
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.server;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.TreeMap;

import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.TextDocumentPositionParams;

import dev.flang.ast.Feature;
import dev.flang.ast.FeatureName;
import dev.flang.ast.Types;
import dev.flang.be.interpreter.ChoiceIdAsRef;
import dev.flang.be.interpreter.Instance;
import dev.flang.be.interpreter.Interpreter;
import dev.flang.fe.FrontEnd;
import dev.flang.fe.FrontEndOptions;
import dev.flang.fuir.FUIR;
import dev.flang.ir.Clazzes;
import dev.flang.lsp.server.records.ParserCacheRecord;
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

  private static final String PARSER_LOCK = "";
  /**
   * maps temporary files which are fed to the parser to their original uri.
   */
  private static TreeMap<String, String> tempFile2Uri = new TreeMap<>();
  private static TreeMap<String, ParserCacheRecord> sourceText2ParserCache = new TreeMap<>();

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
            if (sourceText2ParserCache.isEmpty())
              {
                return Optional.empty();
              }
            return Optional.of(sourceText2ParserCache.firstEntry().getValue().mir().main());
          }

        var sourceText = FuzionTextDocumentService.getText(uri).orElseThrow();
        if (sourceText2ParserCache.containsKey(sourceText))
          {
            return Optional.of(sourceText2ParserCache.get(sourceText).mir().main());
          }

        createMIRandCache(uri);

        var result = getMainFeature(uri).get();

        afterParsing(uri, result);

        return Optional.of(result);
      }
  }

  private static ParserCacheRecord createMIRandCache(String uri)
  {
    synchronized (PARSER_LOCK)
      {
        var sourceText = FuzionTextDocumentService.getText(uri).orElseThrow();
        var result = parserCacheRecord(uri);
        sourceText2ParserCache.put(sourceText, result);
        return result;
      }
  }

  private static ParserCacheRecord parserCacheRecord(String uri)
  {
    var frontEndOptions = FrontEndOptions(uri);
    var mir = MIR(frontEndOptions);
    return new ParserCacheRecord(mir, frontEndOptions);
  }

  private static void afterParsing(String uri, Feature mainFeature)
  {
    // NYI make this less bad
    Memory.EndOfFeature.clear();
  }

  public static FUIR FUIR(String uri)
  {
    // NYI remove this once unnecessary
    Interpreter.clear();
    Instance.universe = null;
    ChoiceIdAsRef.preallocated_.clear();

    // NYI remove recreation of MIR
    var parserCacheRecord = createMIRandCache(uri);


    var air = new MiddleEnd(parserCacheRecord.frontEndOptions(), parserCacheRecord.mir()).air();

    // NYI remove this once unnecessary
    Instance.universe = new Instance(Clazzes.universe.get());

    var fuir = new Optimizer(parserCacheRecord.frontEndOptions(), air).fuir();
    return fuir;
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
        var result = new FrontEnd(frontEndOptions).createMIR();
        return result;
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
    var fuzionHome = Path.of(System.getProperty("fuzion.home"));
    var frontEndOptions =
      new FrontEndOptions(0, fuzionHome, new dev.flang.util.List<>(), 0, false, false, tempFile.getAbsolutePath());
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

  public static Feature universe(String uri)
  {
    return getMainFeature(uri).get().universe();
  }

  public static Feature universe(TextDocumentPositionParams params)
  {
    return universe(Util.getUri(params));
  }

}
