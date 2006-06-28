/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server;

import org.opends.server.core.DirectoryServer;

/**
 * This dependency makes sure that a minimal directory server instance
 * is available. Only one initialization task is performed: the
 * directory server instance is created and has its
 * {@link org.opends.server.core.DirectoryServer#bootstrapClient()}
 * method invoked.
 *
 * @author Matthew Swift
 */
public final class InitialDirectoryServerTestCaseDependency extends
    TestCaseDependency {

  // Flag used to prevent multiple initialization.
  private boolean isInitialized = false;

  /**
   * Create a dependency which will make sure that a minimal directory
   * server instance is available.
   */
  public InitialDirectoryServerTestCaseDependency() {
    // No implementation required.
  }

  /**
   * {@inheritDoc}
   */
  public void setUp() throws Exception {
    if (isInitialized == false) {
      // Make sure a new instance is created.
      //
      // This is effectively a no-op at the moment, but may do lazy
      // initialization at some point.
      DirectoryServer.getInstance();

      // Initialize minimal features such as key syntaxes.
      DirectoryServer.bootstrapClient();

      // Prevent multiple initialization.
      isInitialized = true;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void tearDown() throws Exception {
    // TODO: finalize the server instance?

    isInitialized = false;
  }
}
