/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 *  Copyright 2016 ForgeRock AS.
 */
package org.opends.server.tools;

import org.opends.quicksetup.util.IncompatibleVersionException;
import org.opends.quicksetup.util.Utils;

/** Class used by script to ensure the running java version is compatible with OpenDJ software. */
public class CheckJVMVersion
{
  /** Incompatible java version return code. */
  private static final int JAVA_VERSION_INCOMPATIBLE = 8;

  /**
   * Ensure that running Java version is supported.
   *
   * @param args
   *         unused
   */
  public static void main(final String[] args)
  {
    try
    {
      Utils.checkJavaVersion();
      System.exit(0);
    }
    catch (final IncompatibleVersionException ive)
    {
      System.out.println(ive.getMessageObject());
      System.exit(JAVA_VERSION_INCOMPATIBLE);
    }
  }
}
