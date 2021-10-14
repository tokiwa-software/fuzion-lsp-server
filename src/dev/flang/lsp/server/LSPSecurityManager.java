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
