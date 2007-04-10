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
package org.opends.server.authorization;


import org.opends.server.api.AccessControlHandler;
import org.opends.server.core.*;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;
import org.opends.server.types.Entry;

/**
 * This class provides the implementation of the basic access control
 * handler. It is responsible for handling authorization decision
 * requests made by the core directory server.
 */
final class BasicAccessControlHandler extends AccessControlHandler {

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isAllowed(AddOperation addOperation) {

    // TODO: not yet implemented.

    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isAllowed(BindOperation bindOperation) {

    // TODO: not yet implemented.

    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isAllowed(CompareOperation compareOperation) {

    // TODO: not yet implemented.

    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isAllowed(DeleteOperation deleteOperation) {

    // TODO: not yet implemented.

    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isAllowed(ExtendedOperation extendedOperation) {

    // TODO: not yet implemented.

    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isAllowed(ModifyOperation modifyOperation) {

    // TODO: not yet implemented.

    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isAllowed(ModifyDNOperation modifyDNOperation) {

    // TODO: not yet implemented.

    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isAllowed(SearchOperation searchOperation) {

    // TODO: not yet implemented.

    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean maySend(SearchOperation searchOperation,
      SearchResultEntry searchEntry) {

    // TODO: not yet implemented.

    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SearchResultEntry filterEntry(SearchOperation searchOperation,
      SearchResultEntry searchEntry) {

    // TODO: not yet implemented.

    return searchEntry;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean maySend(SearchOperation searchOperation,
      SearchResultReference searchReference) {

    // TODO: not yet implemented.

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
