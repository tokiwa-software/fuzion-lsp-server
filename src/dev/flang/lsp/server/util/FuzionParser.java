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
 * Source of class FuzionParser
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.server.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.eclipse.lsp4j.MessageParams;
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
import dev.flang.lsp.server.ASTWalker;
import dev.flang.lsp.server.SourceText;
import dev.flang.lsp.server.Util;
import dev.flang.lsp.server.records.ParserCacheRecord;
import dev.flang.me.MiddleEnd;
import dev.flang.opt.Optimizer;
import dev.flang.parser.Lexer.Token;
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
    // NYI
    if (uri.toString().contains("/lib/"))
      {
        if (sourceText2ParserCache.isEmpty())
          {
            return Optional.empty();
          }
        return Optional.of(sourceText2ParserCache.firstEntry().getValue());
      }

    var sourceText = SourceText.getText(uri);
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

  private static ParserCacheRecord createMIRandCache(URI uri)
  {
    synchronized (PARSER_LOCK)
      {
        var sourceText = SourceText.getText(uri).orElseThrow();
        var result = parserCacheRecord(uri);
        sourceText2ParserCache.put(sourceText, result);
        // NYI get rid of this
        currentResolution = getParserCacheRecord(uri).map(x -> x.frontEnd().res()).get();
        return result;
      }
  }

  private static ParserCacheRecord parserCacheRecord(URI uri)
  {
    return IO.WithRedirectedStdOut(() -> {
      return IO.WithRedirectedStdErr(() -> {
        ClearStaticallyHeldStuffInFuzionCompiler();
        var frontEndOptions = FrontEndOptions(uri);
        var frontEnd = new FrontEnd(frontEndOptions);
        var mir = frontEnd.createMIR();
        return new ParserCacheRecord(mir, frontEndOptions, frontEnd);
      });
    });
  }

  /**
   * NYI remove once we can create MIR multiple times
  */
  private static void ClearStaticallyHeldStuffInFuzionCompiler()
  {
    Errors.clear();
    Types.clear();
    FeatureName.clear();
    Clazzes.clear();
  }

  private static Optional<FUIR> FUIR(URI uri)
  {
    // NYI remove this once unnecessary
    Interpreter.clear();
    Instance.universe = null;
    ChoiceIdAsRef.preallocated_.clear();

    // NYI remove recreation of MIR
    var parserCacheRecord = createMIRandCache(uri);

    if (Errors.count() > 0)
      {
        return Optional.empty();
      }

    var air =
      new MiddleEnd(parserCacheRecord.frontEndOptions(), parserCacheRecord.mir(), parserCacheRecord.frontEnd().res())
        .air();

    // NYI remove this once unnecessary
    Instance.universe = new Instance(Clazzes.universe.get());

    var fuir = new Optimizer(parserCacheRecord.frontEndOptions(), air).fuir();
    return Optional.of(fuir);
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
    File sourceFile = IO.writeToTempFile(SourceText.getText(uri).orElseThrow());
    try
      {
        tempFile2Uri.put(sourceFile.toPath().toString(), uri);
      }
    catch (Exception e)
      {
        ErrorHandling.WriteStackTraceAndExit(1, e);
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
    return universe(LSP4jUtils.getUri(params));
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
      .filter(feat -> !FeatureTool.IsAnonymousInnerFeature(feat));
  }

  private static final TreeMap<AbstractFeature, SourcePosition> EndOfFeature = new TreeMap<>();

  private static void afterParsing(URI uri, AbstractFeature mainFeature)
  {
    // NYI make this less bad
    EndOfFeature.clear();
  }

  /**
   * NYI replace by real end of feature once we have this information in the AST
   * @param feature
   * @return
   */
  public static SourcePosition endOfFeature(AbstractFeature feature)
  {
    var uri = getUri(feature.pos());
    if (!EndOfFeature.containsKey(feature))
      {
        SourcePosition endOfFeature = ASTWalker.Traverse(feature)
          .filter(entry -> entry.getValue() != null)
          .filter(ASTItem.IsItemInFile(uri))
          .filter(entry -> entry.getValue().compareTo(feature) == 0)
          .map(entry -> ASTItem.sourcePosition(entry.getKey()))
          .filter(sourcePositionOption -> sourcePositionOption.isPresent())
          .map(sourcePosition -> sourcePosition.get())
          .sorted((Comparator<SourcePosition>) Comparator.<SourcePosition>reverseOrder())
          .map(position -> {
            var start = FuzionLexer.endOfToken(uri, Bridge.ToPosition(position));
            var line = SourceText.RestOfLine(LSP4jUtils.TextDocumentPositionParams(uri, start));
            // NYI maybe use inverse hashset here? i.e. state which tokens can
            // be skipped
            var token = FuzionLexer.nextTokenOfType(line, Util.HashSetOf(Token.t_eof, Token.t_ident, Token.t_semicolon,
              Token.t_rbrace, Token.t_rcrochet, Token.t_rparen));
            return new SourcePosition(position._sourceFile, position._line, start.getCharacter() + token.end()._column);
          })
          .findFirst()
          .orElse(feature.pos());

        EndOfFeature.put(feature, endOfFeature);
      }

    return EndOfFeature.get(feature);
  }

  private static Optional<Interpreter> Interpreter(URI uri)
  {
    return FuzionParser.FUIR(uri).map(f -> new Interpreter(f));
  }

  public static MessageParams Run(URI uri)
    throws Exception
  {
    return Run(uri, 10000);
  }

  public static MessageParams Run(URI uri, int timeout)
    throws Exception
  {
    var result = Concurrency.RunWithPeriodicCancelCheck(null, IO.WithCapturedStdOutErr(() -> {
      var interpreter = FuzionParser.Interpreter(uri);
      interpreter.ifPresent(i -> i.run());
      if (interpreter.isEmpty())
        {
          throw new RuntimeException("Interpreter could not be created.");
        }
    }), timeout, timeout);
    return new MessageParams(MessageType.Info, result);
  }

}
