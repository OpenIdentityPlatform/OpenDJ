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
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
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
  public void processExportBegin(Backend backend,
                                 LDIFExportConfig config);



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
  public void processExportEnd(Backend backend,
                               LDIFExportConfig config,
                               boolean successful);
}

