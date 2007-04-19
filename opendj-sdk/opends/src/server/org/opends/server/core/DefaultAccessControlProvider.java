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
package org.opends.server.core;

import org.opends.server.admin.std.server.AccessControlHandlerCfg;
import org.opends.server.api.AccessControlHandler;
import org.opends.server.api.AccessControlProvider;
import org.opends.server.config.ConfigException;
import org.opends.server.types.InitializationException;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;
import org.opends.server.types.Entry;

/**
 * This class implements a default access control provider for the
 * Directory Server.
 * <p>
 * This class provides and access control handler which is used when
 * access control is disabled and implements a default access control
 * decision function which grants access to everything and anyone.
 */
class DefaultAccessControlProvider
  implements AccessControlProvider <AccessControlHandlerCfg> {

  /**
   * The single handler instance.
   */
  private static Handler instance = null;

  /**
   * Create a new default access control handler.
   */
  public DefaultAccessControlProvider() {
    super();


    // No implementation required.
  }

  /**
   * {@inheritDoc}
   */
  public void initializeAccessControlHandler(
      AccessControlHandlerCfg configuration)
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
      instance = new Handler();
    }
    return instance;
  }

  /**
   * This class provides the implementation of the default access
   * control handler.
   */
  private static class Handler extends AccessControlHandler {

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAllowed(AddOperation addOperation) {

      return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAllowed(BindOperation bindOperation) {

      return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAllowed(CompareOperation compareOperation) {

      return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAllowed(DeleteOperation deleteOperation) {

      return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAllowed(ExtendedOperation extendedOperation) {

      return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAllowed(ModifyOperation modifyOperation) {

      return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAllowed(ModifyDNOperation modifyDNOperation) {

      return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAllowed(SearchOperation searchOperation) {

      return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean maySend(SearchOperation searchOperation,
        SearchResultEntry searchEntry) {

      return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SearchResultEntry filterEntry(
        SearchOperation searchOperation, SearchResultEntry searchEntry) {

      // No implementation required.
      return searchEntry;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean maySend(SearchOperation searchOperation,
        SearchResultReference searchReference) {

      return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isProxiedAuthAllowed(Operation operation, Entry entry) {
     return true;
    }
  }
}
