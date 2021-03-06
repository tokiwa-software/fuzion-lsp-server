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
 * Source of class ParserTool
 *
 *---------------------------------------------------------------------*/

package dev.flang.shared;

import java.io.File;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

import dev.flang.air.Clazzes;
import dev.flang.ast.AbstractFeature;
import dev.flang.ast.Feature;
import dev.flang.ast.Types;
import dev.flang.be.interpreter.Interpreter;
import dev.flang.fe.FrontEnd;
import dev.flang.fe.FrontEndOptions;
import dev.flang.fuir.FUIR;
import dev.flang.opt.Optimizer;
import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.SourcePosition;

/**
 * - does the parsing of a given URI
 * - caches parsing results.
 * - provides a function to get the original URI of a SourcePosition
 */
public class ParserTool extends ANY
{

  /**
   * maps temporary files which are fed to the parser to their original uri.
   */
  private static TreeMap<String, URI> tempFile2Uri = new TreeMap<>();

  private static final int END_OF_FEATURE_CACHE_MAX_SIZE = 100;

  private static List<String> JavaModules = List.<String>of();

  public static void SetJavaModules(List<String> javaModules)
  {
    JavaModules = javaModules;
  }

  private static ParserCache parserCache = new ParserCache();

  /**
   * LRU-Cache holding end of feature calculations
   */
  private static final Map<AbstractFeature, SourcePosition> EndOfFeatureCache =
    Util.ThreadSafeLRUMap(END_OF_FEATURE_CACHE_MAX_SIZE, null);

  /**
   * NYI in the case of uri to stdlib  we need context
   * @param uri
   * @return ParserCacheItem, empty if user starts in stdlib file and no record present yet.
   */
  private synchronized static ParserCacheItem getParserCacheItem(URI uri)
  {
    var sourceText = SourceText.getText(uri);
    var result = parserCache.computeIfAbsent(uri, sourceText, key -> createParserCacheItem(uri));
    // NYI hack! Without this the test RegressionRenameMandelbrotImage fails
    // when running all tests
    Types.resolved = result.resolved();
    return result;
  }

  private static ParserCacheItem createParserCacheItem(URI uri)
  {
    // NYI
    Clazzes.clear();

    var frontEndOptions = FrontEndOptions(uri);
    var frontEnd = new FrontEnd(frontEndOptions);
    var mir = frontEnd.createMIR();
    var errors = Errors.errors();
    var warnings = Errors.warnings();

    return new ParserCacheItem(uri, mir, frontEndOptions, frontEnd, errors, warnings, Types.resolved);
  }


  private static Optional<FUIR> FUIR(URI uri)
  {
    // NYI remove recreation of MIR
    var parserCacheItem = createParserCacheItem(uri);

    if (Errors.count() > 0)
      {
        return Optional.empty();
      }

    var fuir = new Optimizer(parserCacheItem.frontEndOptions(), parserCacheItem.air())
      .fuir();
    return Optional.of(fuir);
  }

  private static FrontEndOptions FrontEndOptions(URI uri)
  {
    File tempFile = ParserTool.toTempFile(uri);
    var frontEndOptions = FrontEndOptions(tempFile);
    return frontEndOptions;
  }

  private static FrontEndOptions FrontEndOptions(File tempFile)
  {
    var frontEndOptions =
      new FrontEndOptions(0, SourceText.FuzionHome, null, true, new dev.flang.util.List<String>(JavaModules.iterator()),
        0,
        false, false,
        tempFile.getAbsolutePath(), true);
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
    return SourceText.UriOf(sourcePosition);
  }

  private static File toTempFile(URI uri)
  {
    var sourceText = Util.IsStdLib(uri) ? "dummyFeature is": SourceText.getText(uri);
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

  public static AbstractFeature Universe(URI uri)
  {
    return getParserCacheItem(uri).mir().universe();
  }

  public static Stream<AbstractFeature> DeclaredFeatures(AbstractFeature f)
  {
    return DeclaredFeatures(f, false);
  }

  public static Stream<AbstractFeature> DeclaredOrInheritedFeatures(AbstractFeature f)
  {
    if (TypeTool.ContainsError(f.thisType()))
      {
        return Stream.empty();
      }
    return parserCache.SourceModule(f)
      .declaredOrInheritedFeatures(f)
      .values()
      .stream();
  }

  public static Stream<AbstractFeature> DeclaredFeatures(AbstractFeature f, boolean includeInternalFeatures)
  {
    if (TypeTool.ContainsError(f.thisType()))
      {
        return Stream.empty();
      }
    return parserCache.SourceModule(f)
      .declaredFeatures(f)
      .values()
      .stream()
      .filter(feat -> includeInternalFeatures
        || !FeatureTool.IsInternal(feat));
  }

  /*
   * @param feature
   * @return
   */
  public static SourcePosition endOfFeature(AbstractFeature feature)
  {
    if (PRECONDITIONS)
      require(!feature.pos().isBuiltIn());

    if (feature instanceof Feature f && !f.nextPos().equals(SourcePosition.notAvailable))
      {
        if (f.nextPos()._sourceFile.byteLength() <= f.nextPos().bytePos())
          {
            return new SourcePosition(f.nextPos()._sourceFile, f.nextPos()._sourceFile.byteLength());
          }
        return new SourcePosition(f.nextPos()._sourceFile, f.nextPos().bytePos() - 1);
      }
    if (feature.featureName().baseName().equals(FuzionConstants.RESULT_NAME))
      {
        return endOfFeature(feature.outer());
      }
    // NYI replace by real end of feature once we have this information in the
    // AST
    // NOTE: since this is a very expensive calculation and frequently used we
    // cache this
    return EndOfFeatureCache.computeIfAbsent(feature, f -> {
      if (FeatureTool.IsArgument(f))
        {
          return LexerTool.EndOfToken(f.pos());
        }
      if (!f.isUniverse() && FeatureTool.IsOfLastFeature(f))
        {
          var sourceText = SourceText.getText(ParserTool.getUri(f.pos()));
          var lines = sourceText.split("\n").length;
          return new SourcePosition(f.pos()._sourceFile, lines + 1, 1);
        }
      var uri = getUri(f.pos());
      var result = ASTWalker.Traverse(f)
        .filter(entry -> entry.getValue() != null)
        .filter(HasSourcePositionTool.IsItemInFile(uri))
        .filter(entry -> entry.getValue().compareTo(f) == 0)
        .map(entry -> entry.getKey().pos())
        .filter(sourcePositionOption -> !sourcePositionOption.isBuiltIn())
        .sorted((Comparator<SourcePosition>) Comparator.<SourcePosition>reverseOrder())
        .map(position -> LexerTool.EndOfToken(position))
        .findFirst()
        .orElse(LexerTool.EndOfToken(f.pos()));

      if (POSTCONDITIONS)
        ensure(f.pos()._line < result._line
          || (f.pos()._line == result._line && f.pos()._column < result._column));
      return result;
    });
  }

  private static Optional<Interpreter> Interpreter(URI uri)
  {
    return ParserTool.FUIR(uri).map(f -> new Interpreter(Context.FuzionOptions, f));
  }

  public static String Run(URI uri)
    throws Throwable
  {
    return Run(uri, 10000);
  }

  public synchronized static String Run(URI uri, int timeout)
    throws Throwable
  {
    var result = Concurrency.RunWithPeriodicCancelCheck(IO.WithCapturedStdOutErr(() -> {
      var interpreter = ParserTool.Interpreter(uri);
      interpreter.ifPresent(i -> i.run());
      if (interpreter.isEmpty())
        {
          throw new RuntimeException("Interpreter could not be created.");
        }
    }), () -> {
    }, timeout, timeout);
    return result.result();
  }

  public static Stream<Errors.Error> Warnings(URI uri)
  {
    return getParserCacheItem(uri).warnings().stream();
  }

  public static Stream<Errors.Error> Errors(URI uri)
  {
    return getParserCacheItem(uri).errors().stream();
  }

  public static Stream<AbstractFeature> TopLevelFeatures(URI uri)
  {
    return getParserCacheItem(uri).TopLevelFeatures();
  }

}
