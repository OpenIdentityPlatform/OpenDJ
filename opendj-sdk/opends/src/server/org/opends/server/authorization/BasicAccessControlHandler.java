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
package org.opends.server.authorization;

import static org.opends.server.loggers.Debug.debugEnter;

import org.opends.server.api.AccessControlHandler;
import org.opends.server.core.AddOperation;
import org.opends.server.core.BindOperation;
import org.opends.server.core.CompareOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.ExtendedOperation;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;

/**
 * This class provides the implementation of the basic access control
 * handler. It is responsible for handling authorization decision
 * requests made by the core directory server.
 */
final class BasicAccessControlHandler extends AccessControlHandler {
  // Fully qualified class name for debugging purposes.
  private static final String CLASS_NAME =
    "org.opends.server.authorization.BasicAccessControlHandler";

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isAllowed(AddOperation addOperation) {
    assert debugEnter(CLASS_NAME, "isAllowed");

    // TODO: not yet implemented.

    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isAllowed(BindOperation bindOperation) {
    assert debugEnter(CLASS_NAME, "isAllowed");

    // TODO: not yet implemented.

    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isAllowed(CompareOperation compareOperation) {
    assert debugEnter(CLASS_NAME, "isAllowed");

    // TODO: not yet implemented.

    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isAllowed(DeleteOperation deleteOperation) {
    assert debugEnter(CLASS_NAME, "isAllowed");

    // TODO: not yet implemented.

    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isAllowed(ExtendedOperation extendedOperation) {
    assert debugEnter(CLASS_NAME, "isAllowed");

    // TODO: not yet implemented.

    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isAllowed(ModifyOperation modifyOperation) {
    assert debugEnter(CLASS_NAME, "isAllowed");

    // TODO: not yet implemented.

    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isAllowed(ModifyDNOperation modifyDNOperation) {
    assert debugEnter(CLASS_NAME, "isAllowed");

    // TODO: not yet implemented.

    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isAllowed(SearchOperation searchOperation) {
    assert debugEnter(CLASS_NAME, "isAllowed");

    // TODO: not yet implemented.

    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean maySend(SearchOperation searchOperation,
      SearchResultEntry searchEntry) {
    assert debugEnter(CLASS_NAME, "maySend");

    // TODO: not yet implemented.

    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SearchResultEntry filterEntry(SearchOperation searchOperation,
      SearchResultEntry searchEntry) {
    assert debugEnter(CLASS_NAME, "filterEntry");

    // TODO: not yet implemented.

    return searchEntry;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean maySend(SearchOperation searchOperation,
      SearchResultReference searchReference) {
    assert debugEnter(CLASS_NAME, "maySend");

    // TODO: not yet implemented.

    return true;
  }
}
