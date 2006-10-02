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
package org.opends.server.api;



import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.ExtendedOperation;
import org.opends.server.types.InitializationException;

import static org.opends.server.loggers.Debug.*;



/**
 * This class defines the set of methods and structures that must be
 * implemented by a Directory Server module that implements the
 * functionality required for one or more types of extended
 * operations.
 */
public abstract class ExtendedOperationHandler
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.api.ExtendedOperationHandler";



  /**
   * Initializes this extended operation handler based on the
   * information in the provided configuration entry.  It should also
   * register itself with the Directory Server for the particular
   * kinds of extended operations that it will process.
   *
   * @param  configEntry  The configuration entry that contains the
   *                      information to use to initialize this
   *                      extended operation handler.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in
   *                           the process of performing the
   *                           initialization.
   *
   * @throws  InitializationException  If a problem occurs
   *                                   during initialization that is
   *                                   not related to the server
   *                                   configuration.
   */
  public abstract void
       initializeExtendedOperationHandler(ConfigEntry configEntry)
         throws ConfigException, InitializationException;



  /**
   * Performs any finalization that may be necessary for this extended
   * operation handler.  By default, no finalization is performed.
   */
  public void finalizeExtendedOperationHandler()
  {
    assert debugEnter(CLASS_NAME, "finalizeExtendedOperationHandler");

    // No implementation is required by default.
  }



  /**
   * Processes the provided extended operation.
   *
   * @param  operation  The extended operation to be processed.
   */
  public abstract void processExtendedOperation(ExtendedOperation
                                                     operation);
}

