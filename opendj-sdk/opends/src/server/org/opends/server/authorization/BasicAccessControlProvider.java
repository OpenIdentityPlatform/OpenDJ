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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.authorization;

import org.opends.server.api.AccessControlHandler;
import org.opends.server.api.AccessControlProvider;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.types.InitializationException;

/**
 * This class implements an access control handler for the Directory
 * Server All methods in this class take the entire request into account
 * when making the determination including any request controls that
 * might have been provided.
 */
public class BasicAccessControlProvider implements
    AccessControlProvider {

  /**
   * The single handler instance.
   */
  private static BasicAccessControlHandler instance = null;

  /**
   * Create a new local access control handler.
   */
  public BasicAccessControlProvider() {
    super();


    // No implementation required.
  }

  /**
   * {@inheritDoc}
   */
  public void initializeAccessControlHandler(ConfigEntry configEntry)
      throws ConfigException, InitializationException {

    // Avoid potential race conditions constructing the handler instance
    // and create it here.
    getInstance();
  }

  /**
   * {@inheritDoc}
   */
  public void finalizeAccessControlHandler() {

    // No implementation required.
  }

  /**
   * {@inheritDoc}
   */
  public AccessControlHandler getInstance() {

    if (instance == null) {
      instance = new BasicAccessControlHandler();
    }
    return instance;
  }
}
