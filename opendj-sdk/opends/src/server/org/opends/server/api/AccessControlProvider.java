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
import org.opends.server.types.InitializationException;

/**
 * This class defines an interface for managing the life-cycle of an
 * access control handler. The access control handler configuration
 * should specify the name of a class implementing this interface.
 */
public interface AccessControlProvider {

  /**
   * Initializes the access control handler implementation based on
   * the information in the provided configuration entry.
   *
   * @param configEntry
   *          The configuration entry that contains the information to
   *          use to initialize this access control handler.
   * @throws ConfigException
   *           If an unrecoverable problem arises in the process of
   *           performing the initialization.
   * @throws InitializationException
   *           If a problem occurs during initialization that is not
   *           related to the server configuration.
   */
  void initializeAccessControlHandler(ConfigEntry configEntry)
      throws ConfigException, InitializationException;

  /**
   * Performs any necessary finalization for the access control
   * handler implementation. This will be called just after the
   * handler has been deregistered with the server but before it has
   * been unloaded.
   */
  void finalizeAccessControlHandler();

  /**
   * Get the access control handler responsible for making access
   * control decisions. This method is called each time an access
   * control decision is needed.
   *
   * @return Returns the access control handler.
   */
  AccessControlHandler getInstance();

}

