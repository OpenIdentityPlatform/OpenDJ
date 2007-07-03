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
package org.opends.server.core;


import java.util.ArrayList;
import java.util.Set;

import org.opends.server.controls.EntryChangeNotificationControl;
import org.opends.server.controls.PersistentSearchChangeType;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchScope;
import org.opends.server.workflowelement.localbackend.*;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;



/**
 * This class defines a data structure that will be used to hold the information
 * necessary for processing a persistent search.
 */
public class PersistentSearch
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // Indicates whether entries returned should include the entry change
  // notification control.
  private boolean returnECs;

  // The base DN for the search operation.
  private DN baseDN;

  // The set of change types we want to see.
  private Set<PersistentSearchChangeType> changeTypes;

  // The scope for the search operation.
  private SearchScope scope;

  // The filter for the search operation.
  private SearchFilter filter;

  // The reference to the associated search operation.
  private SearchOperation searchOperation;



  /**
   * Creates a new persistent search object with the provided information.
   *
   * @param  searchOperation  The search operation for this persistent search.
   * @param  changeTypes      The change types for which changes should be
   *                          examined.
   * @param  returnECs        Indicates whether to include entry change
   *                          notification controls in search result entries
   *                          sent to the client.
   */
  public PersistentSearch(SearchOperation searchOperation,
                          Set<PersistentSearchChangeType> changeTypes,
                          boolean returnECs)
  {
    this.searchOperation = searchOperation;
    this.changeTypes     = changeTypes;
    this.returnECs       = returnECs;

    baseDN = searchOperation.getBaseDN();
    scope  = searchOperation.getScope();
    filter = searchOperation.getFilter();
  }



  /**
   * Retrieves the search operation for this persistent search.
   *
   * @return  The search operation for this persistent search.
   */
  public SearchOperation getSearchOperation()
  {
    return searchOperation;
  }



  /**
   * Retrieves the set of change types for this persistent search.
   *
   * @return  The set of change types for this persistent search.
   */
  public Set<PersistentSearchChangeType> getChangeTypes()
  {
    return changeTypes;
  }



  /**
   * Retrieves the returnECs flag for this persistent search.
   *
   * @return  The return ECs flag for this persistent search.
   */
  public boolean getReturnECs()
  {
    return returnECs;
  }



  /**
   * Retrieves the base DN for this persistent search.
   *
   * @return  The base DN for this persistent search.
   */
  public DN getBaseDN()
  {
    return baseDN;
  }



  /**
   * Retrieves the scope for this persistent search.
   *
   * @return  The scope for this persistent search.
   */
  public SearchScope getScope()
  {
    return scope;
  }



  /**
   * Retrieves the filter for this persistent search.
   *
   * @return  The filter for this persistent search.
   */
  public SearchFilter getFilter()
  {
    return filter;
  }



  /**
   * Performs any necessary processing for the provided add operation.
   *
   * @param  addOperation  The add operation that has been processed.
   * @param  entry         The entry that was added.
   */
  public void processAdd(LocalBackendAddOperation addOperation, Entry entry)
  {
    // See if we care about add operations.
    if (! changeTypes.contains(PersistentSearchChangeType.ADD))
    {
      return;
    }


    // Make sure that the entry is within our target scope.
    switch (scope)
    {
      case BASE_OBJECT:
        if (! baseDN.equals(entry.getDN()))
        {
          return;
        }
        break;
      case SINGLE_LEVEL:
        if (! baseDN.equals(entry.getDN().getParentDNInSuffix()))
        {
          return;
        }
        break;
      case WHOLE_SUBTREE:
        if (! baseDN.isAncestorOf(entry.getDN()))
        {
          return;
        }
        break;
      case SUBORDINATE_SUBTREE:
        if (baseDN.equals(entry.getDN()) ||
            (! baseDN.isAncestorOf(entry.getDN())))
        {
          return;
        }
        break;
      default:
        return;
    }


    // Make sure that the entry matches the target filter.
    try
    {
      if (! filter.matchesEntry(entry))
      {
        return;
      }
    }
    catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }

      // FIXME -- Do we need to do anything here?

      return;
    }


    // The entry is one that should be sent to the client.  See if we also need
    // to construct an entry change notification control.
    ArrayList<Control> entryControls = new ArrayList<Control>(1);
    if (returnECs)
    {
      entryControls.add(new EntryChangeNotificationControl(
                                 PersistentSearchChangeType.ADD,
                                 addOperation.getChangeNumber()));
    }


    // Send the entry and see if we should continue processing.  If not, then
    // deregister this persistent search.
    try
    {
      if (! searchOperation.returnEntry(entry, entryControls))
      {
        DirectoryServer.deregisterPersistentSearch(this);
        searchOperation.sendSearchResultDone();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      DirectoryServer.deregisterPersistentSearch(this);

      try
      {
        searchOperation.sendSearchResultDone();
      }
      catch (Exception e2)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e2);
        }
      }
    }
  }



  /**
   * Performs any necessary processing for the provided delete operation.
   *
   * @param  deleteOperation  The delete operation that has been processed.
   * @param  entry            The entry that was removed.
   */
  public void processDelete(LocalBackendDeleteOperation deleteOperation,
      Entry entry)
  {
    // See if we care about delete operations.
    if (! changeTypes.contains(PersistentSearchChangeType.DELETE))
    {
      return;
    }


    // Make sure that the entry is within our target scope.
    switch (scope)
    {
      case BASE_OBJECT:
        if (! baseDN.equals(entry.getDN()))
        {
          return;
        }
        break;
      case SINGLE_LEVEL:
        if (! baseDN.equals(entry.getDN().getParentDNInSuffix()))
        {
          return;
        }
        break;
      case WHOLE_SUBTREE:
        if (! baseDN.isAncestorOf(entry.getDN()))
        {
          return;
        }
        break;
      case SUBORDINATE_SUBTREE:
        if (baseDN.equals(entry.getDN()) ||
            (! baseDN.isAncestorOf(entry.getDN())))
        {
          return;
        }
        break;
      default:
        return;
    }


    // Make sure that the entry matches the target filter.
    try
    {
      if (! filter.matchesEntry(entry))
      {
        return;
      }
    }
    catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }

      // FIXME -- Do we need to do anything here?

      return;
    }


    // The entry is one that should be sent to the client.  See if we also need
    // to construct an entry change notification control.
    ArrayList<Control> entryControls = new ArrayList<Control>(1);
    if (returnECs)
    {
      entryControls.add(new EntryChangeNotificationControl(
                                 PersistentSearchChangeType.DELETE,
                                 deleteOperation.getChangeNumber()));
    }


    // Send the entry and see if we should continue processing.  If not, then
    // deregister this persistent search.
    try
    {
      if (! searchOperation.returnEntry(entry, entryControls))
      {
        DirectoryServer.deregisterPersistentSearch(this);
        searchOperation.sendSearchResultDone();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      DirectoryServer.deregisterPersistentSearch(this);

      try
      {
        searchOperation.sendSearchResultDone();
      }
      catch (Exception e2)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e2);
        }
      }
    }
  }



  /**
   * Performs any necessary processing for the provided modify operation.
   *
   * @param  modifyOperation  The modify operation that has been processed.
   * @param  oldEntry         The entry before the modification was applied.
   * @param  newEntry         The entry after the modification was applied.
   */
  public void processModify(LocalBackendModifyOperation modifyOperation,
                            Entry oldEntry,
                            Entry newEntry)
  {
    // See if we care about modify operations.
    if (! changeTypes.contains(PersistentSearchChangeType.MODIFY))
    {
      return;
    }


    // Make sure that the entry is within our target scope.
    switch (scope)
    {
      case BASE_OBJECT:
        if (! baseDN.equals(oldEntry.getDN()))
        {
          return;
        }
        break;
      case SINGLE_LEVEL:
        if (! baseDN.equals(oldEntry.getDN().getParentDNInSuffix()))
        {
          return;
        }
        break;
      case WHOLE_SUBTREE:
        if (! baseDN.isAncestorOf(oldEntry.getDN()))
        {
          return;
        }
        break;
      case SUBORDINATE_SUBTREE:
        if (baseDN.equals(oldEntry.getDN()) ||
            (! baseDN.isAncestorOf(oldEntry.getDN())))
        {
          return;
        }
        break;
      default:
        return;
    }


    // Make sure that the entry matches the target filter.
    try
    {
      if ((! filter.matchesEntry(oldEntry)) &&
          (! filter.matchesEntry(newEntry)))
      {
        return;
      }
    }
    catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }

      // FIXME -- Do we need to do anything here?

      return;
    }


    // The entry is one that should be sent to the client.  See if we also need
    // to construct an entry change notification control.
    ArrayList<Control> entryControls = new ArrayList<Control>(1);
    if (returnECs)
    {
      entryControls.add(new EntryChangeNotificationControl(
                                 PersistentSearchChangeType.MODIFY,
                                 modifyOperation.getChangeNumber()));
    }


    // Send the entry and see if we should continue processing.  If not, then
    // deregister this persistent search.
    try
    {
      if (! searchOperation.returnEntry(newEntry, entryControls))
      {
        DirectoryServer.deregisterPersistentSearch(this);
        searchOperation.sendSearchResultDone();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      DirectoryServer.deregisterPersistentSearch(this);

      try
      {
        searchOperation.sendSearchResultDone();
      }
      catch (Exception e2)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e2);
        }
      }
    }
  }



  /**
   * Performs any necessary processing for the provided modify DN operation.
   *
   * @param  modifyDNOperation  The modify DN operation that has been processed.
   * @param  oldEntry           The entry before the modify DN.
   * @param  newEntry           The entry after the modify DN.
   */
  public void processModifyDN(ModifyDNOperation modifyDNOperation,
                              Entry oldEntry, Entry newEntry)
  {
    // See if we care about modify DN operations.
    if (! changeTypes.contains(PersistentSearchChangeType.MODIFY_DN))
    {
      return;
    }


    // Make sure that the old or new entry is within our target scope.  In this
    // case, we need to check the DNs of both the old and new entry so we know
    // which one(s) should be compared against the filter.
    boolean oldMatches = false;
    boolean newMatches = false;

    switch (scope)
    {
      case BASE_OBJECT:
        oldMatches = baseDN.equals(oldEntry.getDN());
        newMatches = baseDN.equals(newEntry.getDN());

        if (! (oldMatches || newMatches))
        {
          return;
        }

        break;
      case SINGLE_LEVEL:
        oldMatches = baseDN.equals(oldEntry.getDN().getParentDNInSuffix());
        newMatches = baseDN.equals(newEntry.getDN().getParentDNInSuffix());

        if (! (oldMatches || newMatches))
        {
          return;
        }

        break;
      case WHOLE_SUBTREE:
        oldMatches = baseDN.isAncestorOf(oldEntry.getDN());
        newMatches = baseDN.isAncestorOf(newEntry.getDN());

        if (! (oldMatches || newMatches))
        {
          return;
        }

        break;
      case SUBORDINATE_SUBTREE:
        oldMatches = ((! baseDN.equals(oldEntry.getDN())) &&
                      baseDN.isAncestorOf(oldEntry.getDN()));
        newMatches = ((! baseDN.equals(newEntry.getDN())) &&
                      baseDN.isAncestorOf(newEntry.getDN()));

        if (! (oldMatches || newMatches))
        {
          return;
        }

        break;
      default:
        return;
    }


    // Make sure that the entry matches the target filter.
    try
    {
      if (((! oldMatches) || (! filter.matchesEntry(oldEntry))) &&
          ((! newMatches) && (! filter.matchesEntry(newEntry))))
      {
        return;
      }
    }
    catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }

      // FIXME -- Do we need to do anything here?

      return;
    }


    // The entry is one that should be sent to the client.  See if we also need
    // to construct an entry change notification control.
    ArrayList<Control> entryControls = new ArrayList<Control>(1);
    if (returnECs)
    {
      entryControls.add(new EntryChangeNotificationControl(
                                 PersistentSearchChangeType.MODIFY_DN,
                                 oldEntry.getDN(),
                                 modifyDNOperation.getChangeNumber()));
    }


    // Send the entry and see if we should continue processing.  If not, then
    // deregister this persistent search.
    try
    {
      if (! searchOperation.returnEntry(newEntry, entryControls))
      {
        DirectoryServer.deregisterPersistentSearch(this);
        searchOperation.sendSearchResultDone();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      DirectoryServer.deregisterPersistentSearch(this);

      try
      {
        searchOperation.sendSearchResultDone();
      }
      catch (Exception e2)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e2);
        }
      }
    }
  }



  /**
   * Retrieves a string representation of this persistent search.
   *
   * @return  A string representation of this persistent search.
   */
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this persistent search to the provided
   * buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append("PersistentSearch(connID=");
    buffer.append(searchOperation.getConnectionID());
    buffer.append(",opID=");
    buffer.append(searchOperation.getOperationID());
    buffer.append(",baseDN=\"");
    searchOperation.getBaseDN().toString(buffer);
    buffer.append("\",scope=");
    buffer.append(scope.toString());
    buffer.append(",filter=\"");
    filter.toString(buffer);
    buffer.append("\")");
  }
}

