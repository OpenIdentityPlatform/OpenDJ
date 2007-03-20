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
  public void handleInternalSearchEntry(
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
  public void handleInternalSearchReference(
                   InternalSearchOperation searchOperation,
                   SearchResultReference searchReference)
         throws DirectoryException;
}

