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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.server.api;



/**
 * This interface defines a set of methods that may be used by server
 * components to perform any processing that they might find necessary
 * whenever a local backend is initialized and/or finalized.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=true,
     mayInvoke=false)
public interface LocalBackendInitializationListener
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
  void performBackendPreInitializationProcessing(LocalBackend<?> backend);

  /**
   * Performs any processing that may be required
   * after the Initialisation cycle has been completed, that is
   * all listeners have received the initialisation event, and the
   * backend has been put into service,.
   *
   * @param  backend  The backend that has been initialized and has been
   *                  put into service.
   */
  void performBackendPostInitializationProcessing(LocalBackend<?> backend);

  /**
   * Performs any processing that may be required before starting
   * the finalisation cycle, that is invoked before any listener receive
   * the Finalization event.
   *
   * @param  backend  The backend that is about to be finalized.
   */
  void performBackendPreFinalizationProcessing(LocalBackend<?> backend);

  /**
   * Performs any processing that may be required whenever a backend
   * is finalized.  This method will be invoked after the backend has
   * been taken out of service but before it has been finalized.
   *
   * @param  backend  The backend that has been taken out of service
   *                  and is about to be finalized.
   */
  void performBackendPostFinalizationProcessing(LocalBackend<?> backend);

}

