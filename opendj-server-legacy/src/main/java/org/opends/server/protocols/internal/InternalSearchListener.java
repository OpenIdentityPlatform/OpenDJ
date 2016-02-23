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
 * Portions Copyright 2015 ForgeRock AS.
 */
package org.opends.server.protocols.internal;



import org.opends.server.types.DirectoryException;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;



/**
 * This interface defines the methods that must be implemented by a
 * class that wishes to perform an internal search operation and be
 * notified of matching entries and referrals as they arrive rather
 * than altogether when the search has completed.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=true,
     mayInvoke=false)
public interface InternalSearchListener
{
  /**
   * Performs any processing necessary for the provided search result
   * entry.
   *
   * @param  searchOperation  The internal search operation being
   *                          processed.
   * @param  searchEntry      The matching search result entry to be
   *                          processed.
   *
   * @throws  DirectoryException  If a problem occurred while handling
   *                              the provided entry.  Search
   *                              processing will be terminated, and
   *                              the search operation will result
   *                              will be set based on this exception.
   */
  void handleInternalSearchEntry(
                   InternalSearchOperation searchOperation,
                   SearchResultEntry searchEntry)
         throws DirectoryException;



  /**
   * Performs any processing necessary for the provided search result
   * reference.
   *
   * @param  searchOperation  The internal search operation being
   *                          processed.
   * @param  searchReference  The search result reference to be
   *                          processed.
   *
   * @throws  DirectoryException  If a problem occurred while handling
   *                              the provided entry.  Search
   *                              processing will be terminated, and
   *                              the search operation will result
   *                              will be set based on this exception.
   */
  void handleInternalSearchReference(
                   InternalSearchOperation searchOperation,
                   SearchResultReference searchReference)
         throws DirectoryException;
}

