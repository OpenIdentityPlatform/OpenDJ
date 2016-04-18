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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.server.api;

import org.opends.server.types.RestoreConfig;

/**
 * This interface defines a set of methods that may be used to notify
 * various Directory Server components whenever a backend restore task
 * is about to begin or has just completed.  Note that these methods
 * will only be invoked for the restore task and not for offline
 * restore processing.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=true,
     mayInvoke=false)
public interface RestoreTaskListener
{
  /**
   * Performs any processing that might be necessary just before the
   * server begins processing on a restore task.  This should include
   * pausing interaction with the provided backend while the restore
   * is in progress.
   *
   * @param  backend  The backend to be restored.
   * @param  config   Configuration information about the restore to
   *                  be performed.
   */
  void processRestoreBegin(Backend<?> backend, RestoreConfig config);

  /**
   * Performs any processing that might be necessary after the server
   * has completed processing on a restore task.  Note that this will
   * always be called when restore processing completes, regardless of
   * whether it was successful.
   *
   * @param  backend     The backend that was restored.
   * @param  config      Configuration information about the restore
   *                     that was performed.
   * @param  successful  Indicates whether the restore operation
   *                     completed successfully.
   */
  void processRestoreEnd(Backend<?> backend, RestoreConfig config, boolean successful);
}
