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
 * Source of class ParserCacheItem
 *
 *---------------------------------------------------------------------*/

package dev.flang.shared;

import java.net.URI;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Stream;

import dev.flang.air.AIR;
import dev.flang.air.Clazzes;
import dev.flang.ast.AbstractFeature;
import dev.flang.ast.Types;
import dev.flang.ast.Types.Resolved;
import dev.flang.be.effects.LanguageServerEffects;
import dev.flang.fe.FrontEnd;
import dev.flang.fe.FrontEndOptions;
import dev.flang.fuir.FUIR;
import dev.flang.me.MiddleEnd;
import dev.flang.mir.MIR;
import dev.flang.opt.Optimizer;
import dev.flang.util.Errors;
import dev.flang.util.FuzionOptions;

/**
 * holds all artifacts of parsing that we later need
 */
public class ParserCacheItem
{

  private final URI uri;
  private final MIR mir;
  private final FrontEndOptions frontEndOptions;
  private final FrontEnd frontEnd;
  private final TreeSet<Errors.Error> errors;
  private final TreeSet<Errors.Error> warnings;
  private final Resolved resolved;

  public ParserCacheItem(URI uri, MIR mir, FrontEndOptions frontEndOptions, FrontEnd frontEnd,
    TreeSet<Errors.Error> errors, TreeSet<Errors.Error> warnings, Resolved resolved)
  {
    this.uri = uri;
    this.mir = mir;
    this.frontEndOptions = frontEndOptions;
    this.frontEnd = frontEnd;
    this.errors = errors;
    this.warnings = warnings;
    this.resolved = resolved;
  }

  public AIR air()
  {
    RestoreStaticArtifacts();
    return new MiddleEnd(frontEndOptions, mir, frontEnd.module())
      .air();
  }

  private void RestoreStaticArtifacts()
  {
    Types.resolved = resolved();
  }

  /**
   * @param uri
   * @return top level feature in source text
   */
  public Stream<AbstractFeature> TopLevelFeatures()
  {
    return ParserTool.DeclaredFeatures(mir.universe())
      // feature is in file
      .filter(f -> ParserTool.getUri(f.pos()).equals(uri));
  }

  public TreeSet<Errors.Error> warnings()
  {
    return warnings;
  }

  public TreeSet<Errors.Error> errors()
  {
    return errors;
  }

  public MIR mir()
  {
    return mir;
  }

  public FuzionOptions frontEndOptions()
  {
    return frontEndOptions;
  }

  public Resolved resolved()
  {
    return resolved;
  }

  public FrontEnd frontEnd()
  {
    return frontEnd;
  }

  public Stream<String> effects(AbstractFeature af)
  {
    return fuir().map(f -> {
      try
        {
          return new LanguageServerEffects(frontEndOptions(), f).effects()
            .successors(Clazzes.clazz(af.selfType())._idInFUIR)
            .stream()
            .map(x -> {
              try
                {
                  return f.clazzAsString(x);
                }
              catch (Exception e)
                {
                  return "?";
                }
            });
        }
      catch (Exception e)
        {
          return Stream.of("error evaluating effects");
        }
    }).orElse(Stream.empty());
  }

  public Optional<FUIR> fuir()
  {
    if (!Context.MiddleEndEnabled)
      {
        return Optional.empty();
      }
    try
      {
        return Optional.of(new Optimizer(frontEndOptions, air())
          .fuir());
      }
    catch (Exception e)
      {
        return Optional.empty();
      }
  }

}
