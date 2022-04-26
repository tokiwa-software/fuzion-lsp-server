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

import java.net.URI;
import java.nio.file.Path;

import org.junit.Assert;
import org.junit.jupiter.api.BeforeAll;

import dev.flang.ast.AbstractFeature;
import dev.flang.shared.ParserTool;
import dev.flang.shared.IO;
import dev.flang.util.SourceFile;
import dev.flang.util.SourcePosition;

public abstract class BaseTest extends Assert
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

  protected static final String ManOrBoy = """
    man_or_boy is

      a(k i32, x1, x2, x3, x4, x5 () -> i32) i32 is
        b => set k := k - 1; a k (() -> b) x1 x2 x3 x4
        if k <= 0 x4() + x5() else b

      K(n i32) () -> i32 is () -> n

      (0..10) | n ->
        say \"manorboy a($n) = {a n (K 1) (K -1) (K -1) (K 1) (K 0)}\"
                """;

  protected static final String Mandelbrot = """
    mandelbrotexample is
      isInMandelbrotSet(c complex<f64>, maxEscapeIterations i32, z complex<f64>) bool is
        maxEscapeIterations = 0 || z.abs² <= 4 && isInMandelbrotSet c maxEscapeIterations-1 z*z+c

      steps(start, step f64, numPixels i32) =>
        array<f64> numPixels (i -> start + i.as_f64 * step)

      mandelbrotImage(yStart, yStep, xStart, xStep f64, height, width i32) =>
        for y in steps yStart yStep height do
          for x in steps xStart xStep width do
            if isInMandelbrotSet (complex x y) 50 (complex 0.0 0.0)
              yak "⬤"p
            else
              yak " "
          say ""

      mandelbrotImage 1 -0.05 -2 0.0315 40 80

                    """;

  protected static final String HelloWorld = """
    HelloWorld is
      say "Hello World!"
      """;

  protected static final String PythagoreanTriple = """
      pythagoreanTriple is
        cₘₐₓ := 100    # max length of hypothenuse

        # iterate over all interesting real/imag pairs while c<max
        for real in 1..cₘₐₓ do
          for
            # imag >= real is not interesting, v².real or v².imag would be negative
            # so we end imag at real-1
            imag in 1..real-1

            v := complex real imag
            v² := v * v
            f := v².real.gcd v².imag  # 1 or 2 (if real+imag is even)
            a := v².real / f
            b := v².imag / f
            c := v .abs² / f
          while c < cₘₐₓ
            if real.gcd imag = 1  # filter duplicates
              say "{a}² + {b}² = {c}² = {a*a} + {b*b} = {c*c}"
    """;

  protected static final String UnknownCall = """
    ex is
      (1..10).
          """;

  protected static SourcePosition CursorPosition(URI uri, int line, int column)
  {
    return new SourcePosition(new SourceFile(Path.of(uri)), line, column);
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
    IO.Init((line) -> {
    }, (line) -> {
    });
  }
}
