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
 * Source of class Memory
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.server;

import java.util.TreeMap;

import dev.flang.ast.Feature;
import dev.flang.util.SourcePosition;

/**
 * Memory is used as a cache for some calculations
 */
public final class Memory
{
  public static final TreeMap<Feature, SourcePosition> EndOfFeature = new TreeMap<>();
}