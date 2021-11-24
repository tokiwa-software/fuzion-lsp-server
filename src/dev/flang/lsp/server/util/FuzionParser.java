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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.TextDocumentIdentifier;
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
import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.SourcePosition;

/**
 * - does the parsing of a given URI
 * - caches parsing results.
 * - provides a function to get the original URI of a SourcePosition
 */
public class FuzionParser extends ANY
{

  /**
   * maps temporary files which are fed to the parser to their original uri.
   */
  private static TreeMap<String, URI> tempFile2Uri = new TreeMap<>();

  static final int MAX_ENTRIES = 20;
  // LRU-Cache holding the most recent results of parser
  private static Map<String, ParserCacheRecord> sourceText2ParserCache = Collections.synchronizedMap(
    new LinkedHashMap<String, ParserCacheRecord>(MAX_ENTRIES + 1, .75F, true) {
      public boolean removeEldestEntry(Map.Entry<String, ParserCacheRecord> eldest)
      {
        var removeEldestEntry = size() > MAX_ENTRIES;
        if (removeEldestEntry)
          {
            universe2ResolutionMap.remove(eldest.getValue().mir().universe());
          }
        return removeEldestEntry;
      }
    });
  private static HashMap<AbstractFeature, Resolution> universe2ResolutionMap = new HashMap<>();

  /**
   * @param uri
   * @return main feature in source text, may return universe
   */
  public static AbstractFeature main(URI uri)
  {
    if (IsStdLib(uri))
      {
        return getParserCacheRecord(uri).mir().universe();
      }
    return getParserCacheRecord(uri).mir().main();
  }

  public static AbstractFeature main(TextDocumentIdentifier params)
  {
    return main(LSP4jUtils.getUri(params));
  }

  /**
   * NYI in the case of uri to stdlib  we need context
   * @param uri
   * @return ParserCacheRecord, empty if user starts in stdlib file and no record present yet.
   */
  private synchronized static ParserCacheRecord getParserCacheRecord(URI uri)
  {
    var sourceText = IsStdLib(uri) ? "dummyFeat is": SourceText.getText(uri).orElseThrow();

    var result = sourceText2ParserCache.computeIfAbsent(sourceText, st -> computeParserCache(uri, true));
    // NYI remove this. restores Types.resolved
    Types.resolved = result.resolved();
    return result;
  }

  private static ParserCacheRecord computeParserCache(URI uri, boolean clearAfterParsing)
  {
    var parserCacheRecord = createParserCacheRecord(uri);
    universe2ResolutionMap.put(parserCacheRecord.mir().universe(), parserCacheRecord.frontEnd().res());
    // NYI
    if (clearAfterParsing)
      {
        ClearStaticallyHeldStuffInFuzionCompiler();
      }
    afterParsing();
    return parserCacheRecord;
  }

  /**
  * NYI need more reliable way than string comparision
  * @param uri
  * @return
  */
  private static boolean IsStdLib(URI uri)
  {
    return uri.toString().contains("/lib/");
  }

  private static ParserCacheRecord createParserCacheRecord(URI uri)
  {
    // NYI
    ClearStaticallyHeldStuffInFuzionCompiler();

    var frontEndOptions = FrontEndOptions(uri);
    var frontEnd = new FrontEnd(frontEndOptions);
    var mir = frontEnd.createMIR();
    var errors = Errors.errors();
    var warnings = Errors.warnings();

    return new ParserCacheRecord(mir, frontEndOptions, frontEnd, errors, warnings, Types.resolved);
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
    var parserCacheRecord = computeParserCache(uri, false);

    if (Errors.count() > 0)
      {
        return Optional.empty();
      }

    var air =
      new MiddleEnd(parserCacheRecord.frontEndOptions(), parserCacheRecord.mir(),
        parserCacheRecord.frontEnd().res()._module)
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

  public static AbstractFeature universe(URI uri)
  {
    return getParserCacheRecord(uri).mir().universe();
  }

  public static AbstractFeature universe(TextDocumentPositionParams params)
  {
    return universe(LSP4jUtils.getUri(params));
  }

  public static Stream<AbstractFeature> DeclaredFeatures(AbstractFeature f)
  {
    return DeclaredFeatures(f, false);
  }

  public static Stream<AbstractFeature> DeclaredOrInheritedFeatures(AbstractFeature f)
  {
    return FeatureTool.universe(f).map(universe -> {
      return universe2ResolutionMap.get(universe)._module.declaredOrInheritedFeatures(f)
        .values()
        .stream();
    }).orElse(Stream.empty());
  }

  public static Stream<AbstractFeature> DeclaredFeatures(AbstractFeature f, boolean IncludeAnonymousInnerFeatures)
  {
    return FeatureTool.universe(f).map(universe -> {
      return universe2ResolutionMap.get(universe)._module
        .declaredFeatures(f)
        .values()
        .stream();
    })
      .orElse(Stream.empty())
      .filter(feat -> IncludeAnonymousInnerFeatures || !FeatureTool.IsAnonymousInnerFeature(feat));
  }

  private static final TreeMap<AbstractFeature, SourcePosition> EndOfFeature = new TreeMap<>();

  private static void afterParsing()
  {
    // NYI make this less bad
    EndOfFeature.clear();
  }

  /**
   * NYI replace by real end of feature once we have this information in the AST
   * NOTE: since this is a very expensive calculation and frequently used we cache this
   * @param feature
   * @return
   */
  public static SourcePosition endOfFeature(AbstractFeature feature)
  {
    if (!EndOfFeature.containsKey(feature))
      {
        if (FeatureTool.IsArgument(feature))
          {
            // NYI make this more idiomatic?
            return new SourcePosition(feature.pos()._sourceFile, 1, 1);
          }
        if (!feature.isUniverse() && FeatureTool.IsOfLastFeature(feature))
          {
            var sourceText = SourceText.getText(FuzionParser.getUri(feature.pos())).get();
            var lines = sourceText.split("\n").length;
            return new SourcePosition(feature.pos()._sourceFile, lines + 1, 1);
          }
        var uri = getUri(feature.pos());
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
    return new MessageParams(MessageType.Info, result.result());
  }

  public static Stream<Errors.Error> Warnings(URI uri)
  {
    return getParserCacheRecord(uri).warnings().stream();
  }

  public static Stream<Errors.Error> Errors(URI uri)
  {
    return getParserCacheRecord(uri).errors().stream();
  }


}
