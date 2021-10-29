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
 * Source of class ParserHelperTest
 *
 *---------------------------------------------------------------------*/

package test.flang.lsp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import dev.flang.lsp.server.Converters;
import dev.flang.lsp.server.FuzionTextDocumentService;
import dev.flang.lsp.server.ParserHelper;
import dev.flang.lsp.server.Util;
import dev.flang.util.Errors;
import dev.flang.util.SourceFile;
import dev.flang.util.SourcePosition;

public class ParserHelperTest extends BaseTest
{
  @Test
  void getMainFeatureTest()
  {
    FuzionTextDocumentService.setText(uri1, """
      HelloWorld is
        say "Hello World!"
                  """);
    var mainFeature = ParserHelper.getMainFeature(uri1);
    assertEquals(0, Errors.count());
    assertEquals(true, mainFeature.isPresent());
    assertEquals("HelloWorld", mainFeature.get().featureName().baseName());
    assertEquals(uri1, ParserHelper.getUri(mainFeature.get().pos()));
  }

  @Test
  void getMainFeatureBrokenSourceCodeTest()
  {
    FuzionTextDocumentService.setText(uri1, """
      factors1 is

        (1..10).forAll(x -> say "sadf")
          (1..n) & (x -> n %% x) | fun print
        say

        (1..n) | m ->
          say("factors of $m: " +
              ((1..m) & (x ->  m %% x)))


                  """);
    var mainFeature = ParserHelper.getMainFeature(uri1);
    assertEquals(true, Errors.count() > 0);
    assertEquals(true, mainFeature.isPresent());
  }

  @Test
  void getUriStdLibFile()
  {
    var uri = ParserHelper.getUri(new SourcePosition(new SourceFile(Path.of("fuzion/build/lib/yak.fz")), 0, 0));
    assertEquals(Util.toURI("file://" + Path.of("./").normalize().toAbsolutePath() + "/fuzion/build/lib/yak.fz"), uri);
  }

  @Test
  void getUriUnix()
  {
    var sourceFile = Converters.ToSourceFile(Util.toURI("file:///tmp/myfile"));
    assertEquals(Util.toURI("file:///tmp/myfile"), ParserHelper.getUri(new SourcePosition(sourceFile, 0, 0)));
  }

  @Test
  void getUriWindows()
  {
    var sourceFile = Converters.ToSourceFile(Util.toURI("file:///c%3A/temp.fz"));
    assertEquals(Util.toURI("file:///c%3A/temp.fz"), ParserHelper.getUri(new SourcePosition(sourceFile, 0, 0)));
  }

}
