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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.opends.sdk;



import org.opends.sdk.responses.SearchResultEntry;
import org.opends.sdk.responses.SearchResultReference;



/**
 * A completion handler for consuming the results of an asynchronous
 * Search operation.
 * <p>
 * {@link Connection} objects allow a search result completion handler
 * to be specified when sending Search operation requests to a Directory
 * Server. The {@link #handleEntry} method is invoked each time a Search
 * Result Entry is returned from the Directory Server. The
 * {@link #handleReference} method is invoked for each Search Result
 * Reference returned from the Directory Server.
 * <p>
 * Implementations of these methods should complete in a timely manner
 * so as to avoid keeping the invoking thread from dispatching to other
 * completion handlers.
 */
public interface SearchResultHandler
{
  /**
   * Invoked each time a search result entry is returned from an
   * asynchronous search operation.
   *
   * @param entry
   *          The search result entry.
   */
  void handleEntry(SearchResultEntry entry);



  /**
   * Invoked each time a search result reference is returned from an
   * asynchronous search operation.
   *
   * @param reference
   *          The search result reference.
   */
  void handleReference(SearchResultReference reference);
}
