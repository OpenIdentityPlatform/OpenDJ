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

import org.opends.server.types.LDIFExportConfig;

/**
 * This interface defines a set of methods that may be used to notify
 * various Directory Server components whenever an LDIF export task is
 * about to begin or has just completed.  Note that these methods will
 * only be invoked for the LDIF export task and not for offline LDIF
 * export processing.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=true,
     mayInvoke=false)
public interface ExportTaskListener
{
  /**
   * Performs any processing that might be necessary just before the
   * server begins processing on an LDIF export task.  This may
   * include flushing any outstanding writes to disk so they are
   * included in the export and/or pausing interaction with the
   * provided backend while the export is in progress.
   *
   * @param  backend  The backend to be exported.
   * @param  config   Configuration information about the LDIF export
   *                  to be performed.
   */
  void processExportBegin(Backend<?> backend, LDIFExportConfig config);

  /**
   * Performs any processing that might be necessary after the server
   * has completed processing on an LDIF export task.  Note that this
   * will always be called when export processing completes,
   * regardless of whether it was successful.
   *
   * @param  backend     The backend that was exported.
   * @param  config      Configuration information about the LDIF
   *                     export that was performed.
   * @param  successful  Indicates whether the export operation
   *                     completed successfully.
   */
  void processExportEnd(Backend<?> backend, LDIFExportConfig config, boolean successful);
}
