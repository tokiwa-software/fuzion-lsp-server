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
 * Source of class LanguageServerEffects
 *
 *---------------------------------------------------------------------*/


package dev.flang.be.effects;

import dev.flang.fuir.FUIR;
import dev.flang.util.FuzionOptions;
import dev.flang.util.Graph;

/**
 * Helper class to make graph accessible by language server.
 */
public class LanguageServerEffects extends Effects
{

  public LanguageServerEffects(FuzionOptions options, FUIR fuir)
  {
    super(options, fuir);
  }

  public Graph<Integer> effects()
  {
    return this._effects;
  }

}
