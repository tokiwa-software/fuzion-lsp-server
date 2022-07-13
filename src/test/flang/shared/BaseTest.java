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
 * Source of class BaseTest
 *
 *---------------------------------------------------------------------*/

package test.flang.shared;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;

import dev.flang.ast.AbstractFeature;
import dev.flang.shared.IO;
import dev.flang.shared.ParserTool;
import dev.flang.util.Errors;
import dev.flang.util.SourceFile;
import dev.flang.util.SourcePosition;

// NYI all tests should not need more than 100ms, currently 500
// Timeout Disabled because it is too flakey. First test frequently fails even though it then
// only takes way less than 500ms.
@Nested // @Timeout(value = 500, unit = TimeUnit.MILLISECONDS)
public abstract class BaseTest extends Assertions
{
  protected static final URI uri1 = IO.writeToTempFile("").toURI();
  protected static final URI uri2 = IO.writeToTempFile("").toURI();
  protected static final URI uri3 = IO.writeToTempFile("").toURI();
  protected static final URI uri4 = IO.writeToTempFile("").toURI();

  protected static final String LoremIpsum =
    """
      Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.
      Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.
      Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur.
      Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum
          """;

  protected static final String ManOrBoy = Read("test_data/man_or_boy.fz");

  protected static final String Faulhaber = Read("test_data/faulhaber.fz");

  protected static final String Mandelbrot = Read("test_data/mandelbrot.fz");

  protected static final String PythagoreanTriple = Read("test_data/pythagorean_triple.fz");

  protected static final String HelloWorld = """
    HelloWorld is
      say "Hello World!"
      """;

  protected static final String UnknownCall = """
    ex is
      (1..10).
          """;

  /**
  * @param uri
  * @param line (zero based)
  * @param character (zero based)
  * @return
  */
  protected static SourcePosition Cursor(URI uri, int line, int character)
  {
    return new SourcePosition(new SourceFile(Path.of(uri)), line + 1, character + 1);
  }

  protected static AbstractFeature Universe()
  {
    return ParserTool.Universe(Path.of("fuzion/build/lib/unit.fz").toUri());
  }

  protected static AbstractFeature DeclaredInUniverse(String name, int argCount)
  {
    return ParserTool
      .DeclaredFeatures(Universe())
      .filter(f -> f.featureName().baseName().equals(name))
      .filter(f -> f.arguments().size() == argCount)
      .findFirst()
      .get();
  }

  @BeforeAll
  public static void setup()
  {
    Errors.MAX_ERROR_MESSAGES = Integer.MAX_VALUE;
    IO.Init((line) -> {
    }, (line) -> {
    });
  }

  protected static String Read(String path)
  {
    return Read(Path.of(path));
  }

  protected static String Read(Path of)
  {
    try
      {
        return Files.readString(of);
      }
    catch (IOException e)
      {
        assertTrue(false);
        return "";
      }
  }

  protected Stream<Path> StdLibAndAllTestFiles() throws IOException
  {
    return Stream.concat(StdLibFiles(), TestFiles(true));
  }

  protected Stream<Path> StdLibFiles() throws IOException
  {
    return Files.find(Paths.get("fuzion/lib"), 5, (p, bfa) -> bfa.isRegularFile());
  }

  protected Stream<Path> TestFiles(boolean includeNegative) throws IOException
  {
    return Files
      .find(Paths.get("fuzion/tests"), 5, (p, bfa) -> bfa.isRegularFile()
        && p.toString().endsWith(".fz")
        && (includeNegative || !p.toString().contains("_negative")));
  }

}
