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
 * Source of class LSPSecurityManager
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.server;

import java.security.Permission;

/**
 * Ignore most calls to System.exit()
 */
public class LSPSecurityManager extends SecurityManager
{
  public static boolean IgnoreExit = true;

  @Override
  public void checkExit(int status)
  {
    if (status != 0 || !IgnoreExit)
      {
        throw new SecurityException("Unhandled error (called exit).");
      }
    super.checkExit(status);
  }

  @Override
  public void checkPermission(Permission perm)
  {
    // Allow other activities by default
  }
}
