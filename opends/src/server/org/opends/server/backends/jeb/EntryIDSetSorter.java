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



import java.util.Map;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.TreeMap;

import org.opends.server.controls.VLVRequestControl;
import org.opends.server.controls.VLVResponseControl;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.SearchOperation;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ResultCode;
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
   * @param  vlvRequest       The VLV request control included in the search
   *                          request, or {@code null} if there was none.
   *
   * @return  A new entry ID set which is a sorted representation of the
   *          provided set using the given sort order.
   *
   * @throws  DirectoryException  If an error occurs while performing the sort.
   */
  public static EntryIDSet sort(EntryContainer entryContainer,
                                EntryIDSet entryIDSet,
                                SearchOperation searchOperation,
                                SortOrder sortOrder,
                                VLVRequestControl vlvRequest)
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


    // See if there is a VLV request to further pare down the set of results,
    // and if there is where it should be processed by offset or assertion
    // value.
    long[] sortedIDs;
    if (vlvRequest != null)
    {
      int beforeCount = vlvRequest.getBeforeCount();
      int afterCount  = vlvRequest.getAfterCount();

      if (vlvRequest.getTargetType() == VLVRequestControl.TYPE_TARGET_BYOFFSET)
      {
        int targetOffset = vlvRequest.getOffset();
        int listOffset = targetOffset - 1; // VLV offsets start at 1, not 0.
        int startPos = listOffset - beforeCount;
        if (startPos < 0)
        {
          searchOperation.addResponseControl(
               new VLVResponseControl(targetOffset, sortMap.size(),
                                      LDAPResultCode.OFFSET_RANGE_ERROR));

          int    msgID   = MSGID_ENTRYIDSORTER_NEGATIVE_START_POS;
          String message = getMessage(msgID);
          throw new DirectoryException(ResultCode.VIRTUAL_LIST_VIEW_ERROR,
                                       message, msgID);
        }
        else if (startPos >= sortMap.size())
        {
          searchOperation.addResponseControl(
               new VLVResponseControl(targetOffset, sortMap.size(),
                                      LDAPResultCode.OFFSET_RANGE_ERROR));

          int    msgID   = MSGID_ENTRYIDSORTER_OFFSET_TOO_LARGE;
          String message = getMessage(msgID, vlvRequest.getOffset(),
                                      sortMap.size());
          throw new DirectoryException(ResultCode.VIRTUAL_LIST_VIEW_ERROR,
                                       message, msgID);
        }

        int count = 1 + beforeCount + afterCount;
        sortedIDs = new long[count];

        int treePos = 0;
        int arrayPos = 0;
        Iterator<EntryID> idIterator = sortMap.values().iterator();
        while (idIterator.hasNext())
        {
          EntryID id = idIterator.next();
          if (treePos++ < startPos)
          {
            continue;
          }

          sortedIDs[arrayPos++] = id.longValue();
          if (arrayPos >= count)
          {
            break;
          }
        }

        if (arrayPos < count)
        {
          // We don't have enough entries in the set to meet the requested
          // page size, so we'll need to shorten the array.
          long[] newIDArray = new long[arrayPos];
          System.arraycopy(sortedIDs, 0, newIDArray, 0, arrayPos);
          sortedIDs = newIDArray;
        }

        searchOperation.addResponseControl(
             new VLVResponseControl(targetOffset, sortMap.size(),
                                    LDAPResultCode.SUCCESS));
      }
      else
      {
        AttributeValue assertionValue = new
             AttributeValue(sortOrder.getSortKeys()[0].getAttributeType(),
                            vlvRequest.getGreaterThanOrEqualAssertion());

        boolean targetFound     = false;
        int targetOffset        = 0;
        int includedBeforeCount = 0;
        int includedAfterCount  = 0;
        int listSize            = 0;
        LinkedList<EntryID> idList = new LinkedList<EntryID>();
        Iterator<Map.Entry<SortValues,EntryID>> mapIterator =
             sortMap.entrySet().iterator();
        while (mapIterator.hasNext())
        {
          Map.Entry<SortValues,EntryID> entry = mapIterator.next();
          SortValues sortValues = entry.getKey();
          EntryID id = entry.getValue();

          if (targetFound)
          {
            idList.add(id);
            listSize++;
            includedAfterCount++;
            if (includedAfterCount >= afterCount)
            {
              break;
            }
          }
          else
          {
            targetFound = (sortValues.compareTo(assertionValue) >= 0);
            targetOffset++;

            if (targetFound)
            {
              idList.add(id);
              listSize++;
            }
            else if (beforeCount > 0)
            {
              if (beforeCount > 0)
              {
                idList.add(id);
                includedBeforeCount++;
                if (includedBeforeCount > beforeCount)
                {
                  idList.removeFirst();
                  includedBeforeCount--;
                }
                else
                {
                  listSize++;
                }
              }
            }
          }
        }

        if (! targetFound)
        {
          searchOperation.addResponseControl(
               new VLVResponseControl(sortMap.size(), sortMap.size(),
                                      LDAPResultCode.OFFSET_RANGE_ERROR));

          int    msgID   = MSGID_ENTRYIDSORTER_TARGET_VALUE_NOT_FOUND;
          String message = getMessage(msgID);
          throw new DirectoryException(ResultCode.VIRTUAL_LIST_VIEW_ERROR,
                                       message, msgID);
        }

        sortedIDs = new long[listSize];
        Iterator<EntryID> idIterator = idList.iterator();
        for (int i=0; i < listSize; i++)
        {
          sortedIDs[i] = idIterator.next().longValue();
        }

        searchOperation.addResponseControl(
             new VLVResponseControl(targetOffset, sortMap.size(),
                                    LDAPResultCode.SUCCESS));
      }
    }
    else
    {
      sortedIDs = new long[sortMap.size()];
      int i=0;
      for (EntryID id : sortMap.values())
      {
        sortedIDs[i++] = id.longValue();
      }
    }

    return new EntryIDSet(sortedIDs, 0, sortedIDs.length);
  }
}

