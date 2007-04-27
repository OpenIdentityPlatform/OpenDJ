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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.backends.jeb;



import java.util.TreeMap;

import org.opends.server.core.DirectoryServer;
import org.opends.server.core.SearchOperation;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchScope;
import org.opends.server.types.SortOrder;

import static org.opends.server.messages.JebMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class provides a mechanism for sorting the contents of an entry ID set
 * based on a given sort order.
 */
public class EntryIDSetSorter
{
  /**
   * Creates a new entry ID set which is a sorted representation of the provided
   * set using the given sort order.
   *
   * @param  entryContainer   The entry container with which the ID list is
   *                          associated.
   * @param  entryIDSet       The entry ID set to be sorted.
   * @param  searchOperation  The search operation being processed.
   * @param  sortOrder        The sort order to use for the entry ID set.
   *
   * @return  A new entry ID set which is a sorted representation of the
   *          provided set using the given sort order.
   *
   * @throws  DirectoryException  If an error occurs while performing the sort.
   */
  public static EntryIDSet sort(EntryContainer entryContainer,
                                EntryIDSet entryIDSet,
                                SearchOperation searchOperation,
                                SortOrder sortOrder)
         throws DirectoryException
  {
    if (! entryIDSet.isDefined())
    {
      return new EntryIDSet();
    }

    ID2Entry id2Entry = entryContainer.getID2Entry();
    DN baseDN = searchOperation.getBaseDN();
    SearchScope scope = searchOperation.getScope();
    SearchFilter filter = searchOperation.getFilter();

    TreeMap<SortValues,EntryID> sortMap = new TreeMap<SortValues,EntryID>();
    for (EntryID id : entryIDSet)
    {
      try
      {
        Entry e = id2Entry.get(null, id);

        if ((! e.matchesBaseAndScope(baseDN, scope)) ||
            (! filter.matchesEntry(e)))
        {
          continue;
        }

        sortMap.put(new SortValues(id, e, sortOrder), id);
      }
      catch (Exception e)
      {
        int msgID = MSGID_ENTRYIDSORTER_CANNOT_EXAMINE_ENTRY;
        String message = getMessage(msgID, String.valueOf(id),
                                    getExceptionMessage(e));
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, msgID, e);
      }
    }

    long[] sortedIDs = new long[sortMap.size()];
    int i=0;
    for (EntryID id : sortMap.values())
    {
      sortedIDs[i++] = id.longValue();
    }

    return new EntryIDSet(sortedIDs, 0, sortedIDs.length);
  }
}

