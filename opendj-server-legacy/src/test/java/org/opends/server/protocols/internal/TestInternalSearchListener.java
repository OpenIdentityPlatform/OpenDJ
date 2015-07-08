/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2015 ForgeRock AS.
 */
package org.opends.server.protocols.internal;



import java.util.ArrayList;

import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;




/**
 * This class provides an internal search listener that may be used for testing
 * purposes.  It merely stores the entries and referrals in an internal list.
 */
public class TestInternalSearchListener
       implements InternalSearchListener
{
  /** The list of search result entries returned. */
  private ArrayList<SearchResultEntry> searchEntries;

  /** The list of search result references returned. */
  private ArrayList<SearchResultReference> searchReferences;



  /**
   * Creates a new instance of this test internal search listener.
   */
  public TestInternalSearchListener()
  {
    searchEntries    = new ArrayList<>();
    searchReferences = new ArrayList<>();
  }



  /** {@inheritDoc} */
  public void handleInternalSearchEntry(InternalSearchOperation searchOperation,
                                        SearchResultEntry searchEntry)
  {
    searchEntries.add(searchEntry);
  }



  /** {@inheritDoc} */
  public void handleInternalSearchReference(
                   InternalSearchOperation searchOperation,
                   SearchResultReference searchReference)
  {
    searchReferences.add(searchReference);
  }



  /**
   * Retrieves the set of search result entries returned for the search.
   *
   * @return  The set of search result entries returned for the search.
   */
  public ArrayList<SearchResultEntry> getSearchEntries()
  {
    return searchEntries;
  }



  /**
   * Retrieves the set of search result references returned for the search.
   *
   * @return  The set of search result references returned for the search.
   */
  public ArrayList<SearchResultReference> getSearchReferences()
  {
    return searchReferences;
  }
}

