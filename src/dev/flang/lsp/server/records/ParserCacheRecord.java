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
 * Source of class ParserCacheRecord
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.server.records;

import java.util.TreeSet;

import dev.flang.ast.Types.Resolved;
import dev.flang.fe.FrontEnd;
import dev.flang.fe.FrontEndOptions;
import dev.flang.mir.MIR;
import dev.flang.util.Errors;

/**
 * holds all artifacts of parsing that we later need
 */
public record ParserCacheRecord(MIR mir, FrontEndOptions frontEndOptions, FrontEnd frontEnd,
  TreeSet<Errors.Error> errors, TreeSet<Errors.Error> warnings, Resolved resolved)
{

}
