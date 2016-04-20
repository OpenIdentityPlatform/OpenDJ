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
 * Portions Copyright 2015-2016 ForgeRock AS.
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

  /** Creates a new instance of this test internal search listener. */
  public TestInternalSearchListener()
  {
    searchEntries    = new ArrayList<>();
    searchReferences = new ArrayList<>();
  }

  @Override
  public void handleInternalSearchEntry(InternalSearchOperation searchOperation,
                                        SearchResultEntry searchEntry)
  {
    searchEntries.add(searchEntry);
  }

  @Override
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
