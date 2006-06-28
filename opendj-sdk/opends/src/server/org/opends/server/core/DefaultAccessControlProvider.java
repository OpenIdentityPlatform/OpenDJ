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
package org.opends.server.core;

import static org.opends.server.loggers.Debug.debugConstructor;
import static org.opends.server.loggers.Debug.debugEnter;

import org.opends.server.api.AccessControlHandler;
import org.opends.server.api.AccessControlProvider;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;

/**
 * This class implements a default access control provider for the
 * Directory Server.
 * <p>
 * This class provides and access control handler which is used when
 * access control is disabled and implements a default access control
 * decision function which grants access to everything and anyone.
 */
class DefaultAccessControlProvider implements AccessControlProvider {
  // Fully qualified class name for debugging purposes.
  private static final String CLASS_NAME =
    "org.opends.server.core.DefaultAccessControlProvider";

  /**
   * The single handler instance.
   */
  private static Handler instance = null;

  /**
   * Create a new default access control handler.
   */
  public DefaultAccessControlProvider() {
    super();

    assert debugConstructor(CLASS_NAME);

    // No implementation required.
  }

  /**
   * {@inheritDoc}
   */
  public void initializeAccessControlHandler(ConfigEntry configEntry)
      throws ConfigException, InitializationException {
    assert debugEnter(CLASS_NAME, "initializeAccessControlHandler");

    // Avoid potential race conditions constructing the handler instance
    // and create it here.
    getInstance();
  }

  /**
   * {@inheritDoc}
   */
  public void finalizeAccessControlHandler() {
    assert debugEnter(CLASS_NAME, "finalizeAccessControlHandler");

    // No implementation required.
  }

  /**
   * {@inheritDoc}
   */
  public AccessControlHandler getInstance() {
    assert debugEnter(CLASS_NAME, "getInstance");

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
    // Fully qualified class name for debugging purposes.
    private static final String CLASS_NAME =
      "org.opends.server.core.DefaultAccessControlProvider.Handler";

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAllowed(AddOperation addOperation) {
      assert debugEnter(CLASS_NAME, "isAllowed");

      return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAllowed(BindOperation bindOperation) {
      assert debugEnter(CLASS_NAME, "isAllowed");

      return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAllowed(CompareOperation compareOperation) {
      assert debugEnter(CLASS_NAME, "isAllowed");

      return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAllowed(DeleteOperation deleteOperation) {
      assert debugEnter(CLASS_NAME, "isAllowed");

      return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAllowed(ExtendedOperation extendedOperation) {
      assert debugEnter(CLASS_NAME, "isAllowed");

      return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAllowed(ModifyOperation modifyOperation) {
      assert debugEnter(CLASS_NAME, "isAllowed");

      return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAllowed(ModifyDNOperation modifyDNOperation) {
      assert debugEnter(CLASS_NAME, "isAllowed");

      return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAllowed(SearchOperation searchOperation) {
      assert debugEnter(CLASS_NAME, "isAllowed");

      return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean maySend(SearchOperation searchOperation,
        SearchResultEntry searchEntry) {
      assert debugEnter(CLASS_NAME, "maySend");

      return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SearchResultEntry filterEntry(
        SearchOperation searchOperation, SearchResultEntry searchEntry) {
      assert debugEnter(CLASS_NAME, "filterEntry");

      // No implementation required.
      return searchEntry;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean maySend(SearchOperation searchOperation,
        SearchResultReference searchReference) {
      assert debugEnter(CLASS_NAME, "maySend");

      return true;
    }
  }
}
