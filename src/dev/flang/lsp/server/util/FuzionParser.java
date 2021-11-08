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

package dev.flang.lsp.server.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.TextDocumentPositionParams;

import dev.flang.air.Clazzes;
import dev.flang.ast.AbstractFeature;
import dev.flang.ast.FeatureName;
import dev.flang.ast.Resolution;
import dev.flang.ast.Types;
import dev.flang.be.interpreter.ChoiceIdAsRef;
import dev.flang.be.interpreter.Instance;
import dev.flang.be.interpreter.Interpreter;
import dev.flang.fe.FrontEnd;
import dev.flang.fe.FrontEndOptions;
import dev.flang.fuir.FUIR;
import dev.flang.lsp.server.FuzionHelpers;
import dev.flang.lsp.server.FuzionTextDocumentService;
import dev.flang.lsp.server.Log;
import dev.flang.lsp.server.Memory;
import dev.flang.lsp.server.Util;
import dev.flang.lsp.server.records.ParserCacheRecord;
import dev.flang.me.MiddleEnd;
import dev.flang.opt.Optimizer;
import dev.flang.util.Errors;
import dev.flang.util.SourcePosition;

/**
 * - does the parsing of a given URI
 * - caches parsing results.
 * - provides a function to get the original URI of a SourcePosition
 */
public class FuzionParser
{

  private static final String PARSER_LOCK = "";
  /**
   * maps temporary files which are fed to the parser to their original uri.
   */
  private static TreeMap<String, URI> tempFile2Uri = new TreeMap<>();
  private static TreeMap<String, ParserCacheRecord> sourceText2ParserCache = new TreeMap<>();
  // NYI get rid of this
  private static Resolution currentResolution;

  /**
   * @param uri
   * @return main feature in source text, may return universe
   */
  public static Optional<AbstractFeature> getMainFeature(URI uri)
  {
    // NYI get rid of this
    currentResolution = getParserCacheRecord(uri).map(x -> x.frontEnd().res()).get();
    return getParserCacheRecord(uri).map(x -> x.mir().main());
  }

  /**
   * @param uri
   */
  private static Optional<ParserCacheRecord> getParserCacheRecord(URI uri)
  {
    synchronized (tempFile2Uri)
      {
        // NYI
        if (uri.toString().contains("/lib/"))
          {
            if (sourceText2ParserCache.isEmpty())
              {
                return Optional.empty();
              }
            return Optional.of(sourceText2ParserCache.firstEntry().getValue());
          }

        var sourceText = FuzionTextDocumentService.getText(uri);
        if (sourceText.isEmpty())
          {
            return Optional.empty();
          }

        if (sourceText2ParserCache.containsKey(sourceText.get()))
          {
            return Optional.of(sourceText2ParserCache.get(sourceText.get()));
          }

        createMIRandCache(uri);

        var result = getParserCacheRecord(uri).get();

        afterParsing(uri, result.mir().main());

        return Optional.of(result);
      }
  }

  private static ParserCacheRecord createMIRandCache(URI uri)
  {
    synchronized (PARSER_LOCK)
      {
        var sourceText = FuzionTextDocumentService.getText(uri).orElseThrow();
        var result = parserCacheRecord(uri);
        sourceText2ParserCache.put(sourceText, result);
        // NYI get rid of this
        currentResolution = getParserCacheRecord(uri).map(x -> x.frontEnd().res()).get();
        return result;
      }
  }

  private static ParserCacheRecord parserCacheRecord(URI uri)
  {
    return Util.WithRedirectedStdOut(() -> {
      return Util.WithRedirectedStdErr(() -> {
        // NYI remove once we can create MIR multiple times
        Errors.clear();
        Types.clear();
        FeatureName.clear();
        Clazzes.clear();
        var frontEndOptions = FrontEndOptions(uri);
        var frontEnd = new FrontEnd(frontEndOptions);
        var mir = frontEnd.createMIR();
        return new ParserCacheRecord(mir, frontEndOptions, frontEnd);
      });
    });
  }

  private static void afterParsing(URI uri, AbstractFeature mainFeature)
  {
    // NYI make this less bad
    Memory.EndOfFeature.clear();
  }

  public static FUIR FUIR(URI uri)
  {
    // NYI remove this once unnecessary
    Interpreter.clear();
    Instance.universe = null;
    ChoiceIdAsRef.preallocated_.clear();

    // NYI remove recreation of MIR
    var parserCacheRecord = createMIRandCache(uri);

    var air =
      new MiddleEnd(parserCacheRecord.frontEndOptions(), parserCacheRecord.mir(), parserCacheRecord.frontEnd().res())
        .air();

    // NYI remove this once unnecessary
    Instance.universe = new Instance(Clazzes.universe.get());

    var fuir = new Optimizer(parserCacheRecord.frontEndOptions(), air).fuir();
    return fuir;
  }

  private static FrontEndOptions FrontEndOptions(URI uri)
  {
    File tempFile = FuzionParser.toTempFile(uri);
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
   * get original URI of given sourcePosition if present
   * necessary because we are feeding the parser temporary files
   * @param sourcePosition
   * @return
   */
  public static URI getUri(SourcePosition sourcePosition)
  {
    var result = tempFile2Uri.get(sourcePosition._sourceFile._fileName.toString());
    if (result != null)
      {
        return result;
      }
    return sourcePosition._sourceFile._fileName.toUri();
  }

  private static File toTempFile(URI uri)
  {
    File sourceFile = Util.writeToTempFile(FuzionTextDocumentService.getText(uri).orElseThrow());
    try
      {
        tempFile2Uri.put(sourceFile.toPath().toString(), uri);
      }
    catch (Exception e)
      {
        Util.WriteStackTraceAndExit(1, e);
      }
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

  public static AbstractFeature universe(URI uri)
  {
    // NYI get rid of this
    currentResolution = getParserCacheRecord(uri).map(x -> x.frontEnd().res()).get();
    return getParserCacheRecord(uri).map(x -> x.mir().universe()).orElseThrow();
  }

  public static AbstractFeature universe(TextDocumentPositionParams params)
  {
    return universe(Util.getUri(params));
  }

  public static Stream<AbstractFeature> AllDeclaredFeatures(AbstractFeature f)
  {
    // NYI get rid of this
    return currentResolution._module.declaredFeatures(f)
      .values()
      .stream();
  }

  public static Stream<AbstractFeature> DeclaredOrInheritedFeatures(AbstractFeature f)
  {
    // NYI get rid of this
    return currentResolution._module.declaredOrInheritedFeatures(f)
      .values()
      .stream();
  }

  public static Stream<AbstractFeature> DeclaredFeatures(AbstractFeature f)
  {
    return AllDeclaredFeatures(f)
      .filter(feat -> !FuzionHelpers.IsAnonymousInnerFeature(feat));
  }


}
