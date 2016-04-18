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

import org.opends.server.types.LDIFImportConfig;

/**
 * This interface defines a set of methods that may be used to notify
 * various Directory Server components whenever an LDIF import task is
 * about to begin or has just completed.  Note that these methods will
 * only be invoked for the LDIF import task and not for offline LDIF
 * import processing.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=true,
     mayInvoke=false)
public interface ImportTaskListener
{
  /**
   * Performs any processing that might be necessary just before the
   * server begins processing on an LDIF import task.  This should
   * include pausing interaction with the provided backend while the
   * import is in progress.
   *
   * @param  backend  The backend to be imported.
   * @param  config   Configuration information about the LDIF import
   *                  to be performed.
   */
  void processImportBegin(Backend<?> backend, LDIFImportConfig config);

  /**
   * Performs any processing that might be necessary after the server
   * has completed processing on an LDIF import task.  Note that this
   * will always be called when import processing completes,
   * regardless of whether it was successful.
   *
   * @param  backend     The backend that was imported.
   * @param  config      Configuration information about the LDIF
   *                     import that was performed.
   * @param  successful  Indicates whether the import operation
   *                     completed successfully.
   */
  void processImportEnd(Backend<?> backend, LDIFImportConfig config, boolean successful);
}
