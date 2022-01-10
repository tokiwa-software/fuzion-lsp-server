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

package dev.flang.shared;

import java.io.File;
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

import dev.flang.air.Clazzes;
import dev.flang.ast.AbstractFeature;
import dev.flang.ast.FeatureName;
import dev.flang.ast.Types;
import dev.flang.be.interpreter.ChoiceIdAsRef;
import dev.flang.be.interpreter.Instance;
import dev.flang.be.interpreter.Interpreter;
import dev.flang.fe.FrontEnd;
import dev.flang.fe.FrontEndOptions;
import dev.flang.fuir.FUIR;
import dev.flang.me.MiddleEnd;
import dev.flang.opt.Optimizer;
import dev.flang.parser.Lexer.Token;
import dev.flang.shared.records.ParserCacheRecord;
import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.FuzionOptions;
import dev.flang.util.List;
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

  private static final int END_OF_FEATURE_CACHE_MAX_SIZE = 100;

  private static List<String> JavaModules = new List<String>();

  public static void SetJavaModules(List<String> javaModules)
  {
    JavaModules = javaModules;
  }

  private static ParserCache parserCache = new ParserCache();

  /**
   * LRU-Cache holding end of feature calculations
   */
  private static final Map<AbstractFeature, SourcePosition> EndOfFeatureCache = Collections
    .synchronizedMap(new LinkedHashMap<AbstractFeature, SourcePosition>(END_OF_FEATURE_CACHE_MAX_SIZE + 1, .75F, true) {
      public boolean removeEldestEntry(Map.Entry<AbstractFeature, SourcePosition> eldest)
      {
        return size() > END_OF_FEATURE_CACHE_MAX_SIZE;
      }
    });


  /**
   * @param uri
   * @return main feature in source text
   */
  public static AbstractFeature MainOrUniverse(URI uri)
  {
    if (IsStdLib(uri))
      {
        return getParserCacheRecord(uri).mir().universe();
      }
    return getParserCacheRecord(uri).mir().main();
  }

  /**
   * NYI in the case of uri to stdlib  we need context
   * @param uri
   * @return ParserCacheRecord, empty if user starts in stdlib file and no record present yet.
   */
  private synchronized static ParserCacheRecord getParserCacheRecord(URI uri)
  {
    var sourceText = SourceText.getText(uri);

    var result = parserCache.computeIfAbsent(sourceText, st -> computeParserCache(uri, true));
    // NYI remove this. restores Types.resolved
    Types.resolved = result.resolved();
    return result;
  }

  private static ParserCacheRecord computeParserCache(URI uri, boolean clearAfterParsing)
  {
    var parserCacheRecord = createParserCacheRecord(uri);
    // NYI
    if (clearAfterParsing)
      {
        ClearStaticallyHeldStuffInFuzionCompiler();
      }
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
    Types.reset();
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
        parserCacheRecord.frontEnd().module())
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
      new FrontEndOptions(0, fuzionHome, null, JavaModules, 0, false, false, tempFile.getAbsolutePath());
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
    return FuzionLexer.toURI(sourcePosition);
  }

  private static File toTempFile(URI uri)
  {
    var sourceText = IsStdLib(uri) ? "dummyFeature is": SourceText.getText(uri);
    File sourceFile = IO.writeToTempFile(sourceText);
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

  public static Stream<AbstractFeature> DeclaredFeatures(AbstractFeature f)
  {
    return DeclaredFeatures(f, false);
  }

  public static Stream<AbstractFeature> DeclaredOrInheritedFeatures(AbstractFeature f)
  {
    return FeatureTool.universe(f).map(universe -> {
      return parserCache.SourceModule(universe)
        .declaredOrInheritedFeatures(f)
        .values()
        .stream();
    })
      .orElse(Stream.empty());
  }

  public static Stream<AbstractFeature> DeclaredFeatures(AbstractFeature f, boolean IncludeAnonymousInnerFeatures)
  {
    return FeatureTool.universe(f).map(universe -> {
      return parserCache.SourceModule(universe)
        .declaredFeatures(f)
        .values()
        .stream();
    })
      .orElse(Stream.empty())
      .filter(feat -> IncludeAnonymousInnerFeatures ||
        !FeatureTool.IsAnonymousInnerFeature(feat));
  }


  /**
   * NYI replace by real end of feature once we have this information in the AST
   * NOTE: since this is a very expensive calculation and frequently used we cache this
   * @param feature
   * @return
   */
  public static SourcePosition endOfFeature(AbstractFeature feature)
  {
    return EndOfFeatureCache.computeIfAbsent(feature, f -> {
      if (FeatureTool.IsArgument(f))
        {
          // NYI make this more idiomatic?
          return new SourcePosition(f.pos()._sourceFile, 1, 1);
        }
      if (!f.isUniverse() && FeatureTool.IsOfLastFeature(f))
        {
          var sourceText = SourceText.getText(FuzionParser.getUri(f.pos()));
          var lines = sourceText.split("\n").length;
          return new SourcePosition(f.pos()._sourceFile, lines + 1, 1);
        }
      var uri = getUri(f.pos());
      return ASTWalker.Traverse(f)
        .filter(entry -> entry.getValue() != null)
        .filter(ASTItem.IsItemInFile(uri))
        .filter(entry -> entry.getValue().compareTo(f) == 0)
        .map(entry -> ASTItem.sourcePosition(entry.getKey()))
        .filter(sourcePositionOption -> sourcePositionOption.isPresent())
        .map(sourcePosition -> sourcePosition.get())
        .sorted((Comparator<SourcePosition>) Comparator.<SourcePosition>reverseOrder())
        .map(position -> {
          var start =
            FuzionLexer.endOfToken(position);
          var line = SourceText.RestOfLine(start);
          // NYI maybe use inverse hashset here? i.e. state which tokens can
          // be skipped
          var token = FuzionLexer.nextTokenOfType(line, Util.HashSetOf(Token.t_eof, Token.t_ident, Token.t_semicolon,
            Token.t_rbrace, Token.t_rcrochet, Token.t_rparen));
          return new SourcePosition(position._sourceFile, position._line, start._column - 1 + token.end()._column);
        })
        .findFirst()
        .orElse(f.pos());
    });
  }

  private static Optional<Interpreter> Interpreter(URI uri)
  {
    // NYI get fuzionoptions from client
    return FuzionParser.FUIR(uri).map(f -> new Interpreter(new FuzionOptions(0, 0, false), f));
  }

  public static String Run(URI uri)
    throws Exception
  {
    return Run(uri, 10000);
  }

  public synchronized static String Run(URI uri, int timeout)
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
    return result.result();
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
