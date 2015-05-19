/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2015 ForgeRock AS
 */
package org.opends.server.api;



/**
 * This interface defines a set of methods that may be used by server
 * components to perform any processing that they might find necessary
 * whenever a backend is initialized and/or finalized.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=true,
     mayInvoke=false)
public interface BackendInitializationListener
{
  /**
   * Performs any processing that may be required whenever a backend
   * is initialized for use in the Directory Server.  This method will
   * be invoked after the backend has been initialized but before it
   * has been put into service.
   *
   * @param  backend  The backend that has been initialized and is
   *                  about to be put into service.
   */
  void performBackendInitializationProcessing(Backend<?> backend);

  /**
   * Performs any processing that may be required whenever a backend
   * is finalized.  This method will be invoked after the backend has
   * been taken out of service but before it has been finalized.
   *
   * @param  backend  The backend that has been taken out of service
   *                  and is about to be finalized.
   */
  void performBackendFinalizationProcessing(Backend<?> backend);
}

