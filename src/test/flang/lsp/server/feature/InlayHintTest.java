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
 * Source of class InlayHintTest
 *
 *---------------------------------------------------------------------*/


package test.flang.lsp.server.feature;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.flang.lsp.server.feature.InlayHints;
import dev.flang.shared.IO;
import dev.flang.shared.SourceText;
import test.flang.lsp.server.ExtendedBaseTest;

public class InlayHintTest extends ExtendedBaseTest
{

  @Test
  public void InlayHintsComposedArg()
  {
    SourceText.setText(uri1, Mandelbrot);

    var inlayHints = InlayHints
      .getInlayHints(Params());

    // 20 actuals, 2 result types, 1 effects
    assertEquals(20 + 2 + 1, inlayHints.size());

    InlayHint maxEscapeIter = inlayHints.stream().filter(x -> x.getPosition().getLine() == 2).findFirst().get();
    assertEquals("maxEscapeIterations:", maxEscapeIter.getLabel().getLeft());
    assertEquals(2, maxEscapeIter.getPosition().getLine());
    assertEquals(66, maxEscapeIter.getPosition().getCharacter());
  }


  @Test
  public void InlayHintsChainedCallSimple()
  {
    SourceText.setText(uri1, """
      ex =>
        a is
          b := "asdf"

        my_say(arg string) is
          say arg

        my_say a.b
              """);

    var inlayHints = InlayHints
      .getInlayHints(Params());

    assertEquals(3, inlayHints.size());
    assertEquals("arg:", inlayHints.get(0).getLabel().getLeft());
    assertEquals(7, inlayHints.get(0).getPosition().getLine());
    assertEquals(9, inlayHints.get(0).getPosition().getCharacter());
  }


  @Test
  public void InlayHintsArrayParam()
  {
    SourceText.setText(uri1, """
      ex =>
        my_feat (list_arg list string) is
        my_feat ["hello"].asList
              """);

    var inlayHints = InlayHints
      .getInlayHints(Params());

    assertEquals(2, inlayHints.size());
    assertEquals("list_arg:", inlayHints.get(0).getLabel().getLeft());
    assertEquals(2, inlayHints.get(0).getPosition().getLine());
    assertEquals(10, inlayHints.get(0).getPosition().getCharacter());
  }


  private void PrintDebug(List<InlayHint> inlayHints)
  {
    inlayHints.stream().forEach(
      x -> IO.SYS_OUT.println(x.getLabel()+  ":" +  x.getPosition())
    );
  }


  @Test
  public void InlayHintsLambdaArg()
  {
    SourceText.setText(uri1, """
      ex =>
        feat(mylmbd (i32) -> bool) is
        feat (i -> false)
              """);

    var inlayHints = InlayHints
      .getInlayHints(Params())
      .stream()
      .sorted((a, b) -> a.getPosition().getLine() - b.getPosition().getLine())
      .collect(Collectors.toList());

    assertEquals(2, inlayHints.size());
    assertEquals("mylmbd:", inlayHints.get(1).getLabel().getLeft());
    assertEquals(2, inlayHints.get(1).getPosition().getLine());
    assertEquals(7, inlayHints.get(1).getPosition().getCharacter());
  }

  @Test
  public void InlayHintsBoxedValue()
  {
    SourceText.setText(uri1, """
      ex =>
        tmp : string is
          redef utf8 Sequence u8 is
            [u8 8]
                                                                # asArray, since we don't want this to be lazy
        strings.fromCodepoints (tmp.asCodepoints.asStream.take 1).asArray
                    """);

    var inlayHints = InlayHints
      .getInlayHints(Params())
      .stream()
      .sorted((a, b) -> a.getPosition().getLine() - b.getPosition().getLine())
      .collect(Collectors.toList());

    assertEquals("codePoints:", inlayHints.get(1).getLabel().getLeft());
    assertEquals(5, inlayHints.get(1).getPosition().getLine());
    assertEquals(25, inlayHints.get(1).getPosition().getCharacter());
  }


  @Test
  public void InlayHintsChainedCallComplex()
  {
    SourceText.setText(uri1, """
      ex =>
        my_say(arg string) is
          say arg

        get_strings(cond bool) =>
          ["a string"]

        arr := ["a string"]

        my_say (arr.filter ((x) -> true)).first
        my_say (["a string"]
          .filter ((x) -> true )).first
        my_say ((get_strings true).filter ((x) -> true)).first
                    """);

    var inlayHints = InlayHints
      .getInlayHints(Params());

    var inlayHintsArray = inlayHints
      .stream()
      .filter(x -> x.getLabel().getLeft().contains("array"))
      .sorted((a, b) -> a.getPosition().getLine() - b.getPosition().getLine())
      .collect(Collectors.toList());

    assertEquals(2, inlayHintsArray.size());
    assertEquals(7, inlayHintsArray.get(1).getPosition().getLine());
    assertEquals(6, inlayHintsArray.get(1).getPosition().getCharacter());

    var inlayHintsArg = inlayHints
      .stream()
      .filter(x -> x.getLabel().getLeft().equals("arg:"))
      .sorted((a, b) -> a.getPosition().getLine() - b.getPosition().getLine())
      .collect(Collectors.toList());


    assertEquals(3, inlayHintsArg.size());

    assertEquals("arg:", inlayHintsArg.get(0).getLabel().getLeft());
    assertEquals(9, inlayHintsArg.get(0).getPosition().getLine());
    assertEquals(9, inlayHintsArg.get(0).getPosition().getCharacter());

    assertEquals("arg:", inlayHintsArg.get(1).getLabel().getLeft());
    assertEquals(10, inlayHintsArg.get(1).getPosition().getLine());
    assertEquals(9, inlayHintsArg.get(1).getPosition().getCharacter());

    assertEquals("arg:", inlayHintsArg.get(2).getLabel().getLeft());
    assertEquals(12, inlayHintsArg.get(2).getPosition().getLine());
    assertEquals(9, inlayHintsArg.get(2).getPosition().getCharacter());
  }


  private InlayHintParams Params()
  {
    var cursor = Cursor(uri1, 1, 1);
    return new InlayHintParams(TextDocument(cursor), new Range(new Position(0, 0), new Position(100, 0)));
  }

  @Test @Timeout(value = 60, unit = TimeUnit.SECONDS) @Disabled // too slow
  public void InlayHints() throws IOException
  {
    StdLibAndAllTestFiles()
      .forEach(p -> {
        SourceText.setText(uri1, Read(p));
        assertDoesNotThrow(() -> InlayHints.getInlayHints(Params()));
      });
  }

}
