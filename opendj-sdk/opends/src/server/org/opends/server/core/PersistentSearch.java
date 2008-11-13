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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.core;



import static org.opends.server.loggers.debug.DebugLogger.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.opends.server.controls.EntryChangeNotificationControl;
import org.opends.server.controls.PersistentSearchChangeType;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.CancelResult;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchScope;



/**
 * This class defines a data structure that will be used to hold the
 * information necessary for processing a persistent search.
 * <p>
 * Work flow element implementations are responsible for managing the
 * persistent searches that they are currently handling.
 * <p>
 * Typically, a work flow element search operation will first decode
 * the persistent search control and construct a new {@code
 * PersistentSearch}.
 * <p>
 * Once the initial search result set has been returned and no errors
 * encountered, the work flow element implementation should register a
 * cancellation callback which will be invoked when the persistent
 * search is cancelled. This is achieved using
 * {@link #registerCancellationCallback(CancellationCallback)}. The
 * callback should make sure that any resources associated with the
 * {@code PersistentSearch} are released. This may included removing
 * the {@code PersistentSearch} from a list, or abandoning a
 * persistent search operation that has been sent to a remote server.
 * <p>
 * Finally, the {@code PersistentSearch} should be enabled using
 * {@link #enable()}. This method will register the {@code
 * PersistentSearch} with the client connection and notify the
 * underlying search operation that no result should be sent to the
 * client.
 * <p>
 * Work flow element implementations should {@link #cancel()} active
 * persistent searches when the work flow element fails or is shut
 * down.
 */
public final class PersistentSearch
{

  /**
   * A cancellation call-back which can be used by work-flow element
   * implementations in order to register for resource cleanup when a
   * persistent search is cancelled.
   */
  public static interface CancellationCallback
  {

    /**
     * The provided persistent search has been cancelled. Any
     * resources associated with the persistent search should be
     * released.
     *
     * @param psearch
     *          The persistent search which has just been cancelled.
     */
    void persistentSearchCancelled(PersistentSearch psearch);
  }

  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



  // Cancel a persistent search.
  private static synchronized void cancel(PersistentSearch psearch)
  {
    if (!psearch.isCancelled)
    {
      psearch.isCancelled = true;

      // The persistent search can no longer be cancelled.
      psearch.searchOperation.getClientConnection().deregisterPersistentSearch(
          psearch);

      // Notify any cancellation callbacks.
      for (CancellationCallback callback : psearch.cancellationCallbacks)
      {
        try
        {
          callback.persistentSearchCancelled(psearch);
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
        }
      }
    }
  }

  // The base DN for the search operation.
  private final DN baseDN;

  // Cancellation callbacks which should be run when this persistent
  // search is cancelled.
  private final List<CancellationCallback> cancellationCallbacks =
    new CopyOnWriteArrayList<CancellationCallback>();

  // The set of change types we want to see.
  private final Set<PersistentSearchChangeType> changeTypes;

  // The filter for the search operation.
  private final SearchFilter filter;

  // Indicates whether or not this persistent search has already been
  // aborted.
  private boolean isCancelled = false;

  // Indicates whether entries returned should include the entry
  // change notification control.
  private final boolean returnECs;

  // The scope for the search operation.
  private final SearchScope scope;

  // The reference to the associated search operation.
  private final SearchOperation searchOperation;



  /**
   * Creates a new persistent search object with the provided
   * information.
   *
   * @param searchOperation
   *          The search operation for this persistent search.
   * @param changeTypes
   *          The change types for which changes should be examined.
   * @param returnECs
   *          Indicates whether to include entry change notification
   *          controls in search result entries sent to the client.
   */
  public PersistentSearch(SearchOperation searchOperation,
      Set<PersistentSearchChangeType> changeTypes, boolean returnECs)
  {
    this.searchOperation = searchOperation;
    this.changeTypes = changeTypes;
    this.returnECs = returnECs;

    this.baseDN = searchOperation.getBaseDN();
    this.scope = searchOperation.getScope();
    this.filter = searchOperation.getFilter();
  }



  /**
   * Cancels this persistent search operation. On exit this persistent
   * search will no longer be valid and any resources associated with
   * it will have been released. In addition, any other persistent
   * searches that are associated with this persistent search will
   * also be canceled.
   *
   * @return The result of the cancellation.
   */
  public synchronized CancelResult cancel()
  {
    if (!isCancelled)
    {
      // Cancel this persistent search.
      cancel(this);

      // Cancel any other persistent searches which are associated
      // with this one. For example, a persistent search may be
      // distributed across multiple proxies.
      for (PersistentSearch psearch : searchOperation.getClientConnection()
          .getPersistentSearches())
      {
        if (psearch.getMessageID() == getMessageID())
        {
          cancel(psearch);
        }
      }
    }

    return new CancelResult(ResultCode.CANCELED, null);
  }



  /**
   * Gets the message ID associated with this persistent search.
   *
   * @return The message ID associated with this persistent search.
   */
  public int getMessageID()
  {
    return searchOperation.getMessageID();
  }



  /**
   * Notifies the persistent searches that an entry has been added.
   *
   * @param entry
   *          The entry that was added.
   * @param changeNumber
   *          The change number associated with the operation that
   *          added the entry, or {@code -1} if there is no change
   *          number.
   */
  public void processAdd(Entry entry, long changeNumber)
  {
    // See if we care about add operations.
    if (!changeTypes.contains(PersistentSearchChangeType.ADD))
    {
      return;
    }

    // Make sure that the entry is within our target scope.
    switch (scope)
    {
    case BASE_OBJECT:
      if (!baseDN.equals(entry.getDN()))
      {
        return;
      }
      break;
    case SINGLE_LEVEL:
      if (!baseDN.equals(entry.getDN().getParentDNInSuffix()))
      {
        return;
      }
      break;
    case WHOLE_SUBTREE:
      if (!baseDN.isAncestorOf(entry.getDN()))
      {
        return;
      }
      break;
    case SUBORDINATE_SUBTREE:
      if (baseDN.equals(entry.getDN()) || (!baseDN.isAncestorOf(entry.getDN())))
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
      if (!filter.matchesEntry(entry))
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

    // The entry is one that should be sent to the client. See if we
    // also need to construct an entry change notification control.
    ArrayList<Control> entryControls = new ArrayList<Control>(1);
    if (returnECs)
    {
      entryControls.add(new EntryChangeNotificationControl(
          PersistentSearchChangeType.ADD, changeNumber));
    }

    // Send the entry and see if we should continue processing. If
    // not, then deregister this persistent search.
    try
    {
      if (!searchOperation.returnEntry(entry, entryControls))
      {
        cancel();
        searchOperation.sendSearchResultDone();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      cancel();

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
   * Notifies the persistent searches that an entry has been deleted.
   *
   * @param entry
   *          The entry that was deleted.
   * @param changeNumber
   *          The change number associated with the operation that
   *          deleted the entry, or {@code -1} if there is no change
   *          number.
   */
  public void processDelete(Entry entry, long changeNumber)
  {
    // See if we care about delete operations.
    if (!changeTypes.contains(PersistentSearchChangeType.DELETE))
    {
      return;
    }

    // Make sure that the entry is within our target scope.
    switch (scope)
    {
    case BASE_OBJECT:
      if (!baseDN.equals(entry.getDN()))
      {
        return;
      }
      break;
    case SINGLE_LEVEL:
      if (!baseDN.equals(entry.getDN().getParentDNInSuffix()))
      {
        return;
      }
      break;
    case WHOLE_SUBTREE:
      if (!baseDN.isAncestorOf(entry.getDN()))
      {
        return;
      }
      break;
    case SUBORDINATE_SUBTREE:
      if (baseDN.equals(entry.getDN()) || (!baseDN.isAncestorOf(entry.getDN())))
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
      if (!filter.matchesEntry(entry))
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

    // The entry is one that should be sent to the client. See if we
    // also need to construct an entry change notification control.
    ArrayList<Control> entryControls = new ArrayList<Control>(1);
    if (returnECs)
    {
      entryControls.add(new EntryChangeNotificationControl(
          PersistentSearchChangeType.DELETE, changeNumber));
    }

    // Send the entry and see if we should continue processing. If
    // not, then deregister this persistent search.
    try
    {
      if (!searchOperation.returnEntry(entry, entryControls))
      {
        cancel();
        searchOperation.sendSearchResultDone();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      cancel();

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
   * Notifies the persistent searches that an entry has been modified.
   *
   * @param entry
   *          The entry after it was modified.
   * @param changeNumber
   *          The change number associated with the operation that
   *          modified the entry, or {@code -1} if there is no change
   *          number.
   */
  public void processModify(Entry entry, long changeNumber)
  {
    processModify(entry, changeNumber, entry);
  }



  /**
   * Notifies persistent searches that an entry has been modified.
   *
   * @param entry
   *          The entry after it was modified.
   * @param changeNumber
   *          The change number associated with the operation that
   *          modified the entry, or {@code -1} if there is no change
   *          number.
   * @param oldEntry
   *          The entry before it was modified.
   */
  public void processModify(Entry entry, long changeNumber, Entry oldEntry)
  {
    // See if we care about modify operations.
    if (!changeTypes.contains(PersistentSearchChangeType.MODIFY))
    {
      return;
    }

    // Make sure that the entry is within our target scope.
    switch (scope)
    {
    case BASE_OBJECT:
      if (!baseDN.equals(oldEntry.getDN()))
      {
        return;
      }
      break;
    case SINGLE_LEVEL:
      if (!baseDN.equals(oldEntry.getDN().getParent()))
      {
        return;
      }
      break;
    case WHOLE_SUBTREE:
      if (!baseDN.isAncestorOf(oldEntry.getDN()))
      {
        return;
      }
      break;
    case SUBORDINATE_SUBTREE:
      if (baseDN.equals(oldEntry.getDN())
          || (!baseDN.isAncestorOf(oldEntry.getDN())))
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
      if ((!filter.matchesEntry(oldEntry)) && (!filter.matchesEntry(entry)))
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

    // The entry is one that should be sent to the client. See if we
    // also need to construct an entry change notification control.
    ArrayList<Control> entryControls = new ArrayList<Control>(1);
    if (returnECs)
    {
      entryControls.add(new EntryChangeNotificationControl(
          PersistentSearchChangeType.MODIFY, changeNumber));
    }

    // Send the entry and see if we should continue processing. If
    // not, then deregister this persistent search.
    try
    {
      if (!searchOperation.returnEntry(entry, entryControls))
      {
        cancel();
        searchOperation.sendSearchResultDone();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      cancel();

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
   * Notifies the persistent searches that an entry has been renamed.
   *
   * @param entry
   *          The entry after it was modified.
   * @param changeNumber
   *          The change number associated with the operation that
   *          modified the entry, or {@code -1} if there is no change
   *          number.
   * @param oldDN
   *          The DN of the entry before it was renamed.
   */
  public void processModifyDN(Entry entry, long changeNumber, DN oldDN)
  {
    // See if we care about modify DN operations.
    if (!changeTypes.contains(PersistentSearchChangeType.MODIFY_DN))
    {
      return;
    }

    // Make sure that the old or new entry is within our target scope.
    // In this case, we need to check the DNs of both the old and new
    // entry so we know which one(s) should be compared against the
    // filter.
    boolean oldMatches = false;
    boolean newMatches = false;

    switch (scope)
    {
    case BASE_OBJECT:
      oldMatches = baseDN.equals(oldDN);
      newMatches = baseDN.equals(entry.getDN());

      if (!(oldMatches || newMatches))
      {
        return;
      }

      break;
    case SINGLE_LEVEL:
      oldMatches = baseDN.equals(oldDN.getParent());
      newMatches = baseDN.equals(entry.getDN().getParent());

      if (!(oldMatches || newMatches))
      {
        return;
      }

      break;
    case WHOLE_SUBTREE:
      oldMatches = baseDN.isAncestorOf(oldDN);
      newMatches = baseDN.isAncestorOf(entry.getDN());

      if (!(oldMatches || newMatches))
      {
        return;
      }

      break;
    case SUBORDINATE_SUBTREE:
      oldMatches = ((!baseDN.equals(oldDN)) && baseDN.isAncestorOf(oldDN));
      newMatches = ((!baseDN.equals(entry.getDN())) && baseDN
          .isAncestorOf(entry.getDN()));

      if (!(oldMatches || newMatches))
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
      if (!oldMatches && !newMatches && !filter.matchesEntry(entry))
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

    // The entry is one that should be sent to the client. See if we
    // also need to construct an entry change notification control.
    ArrayList<Control> entryControls = new ArrayList<Control>(1);
    if (returnECs)
    {
      entryControls.add(new EntryChangeNotificationControl(
          PersistentSearchChangeType.MODIFY_DN, oldDN, changeNumber));
    }

    // Send the entry and see if we should continue processing. If
    // not, then deregister this persistent search.
    try
    {
      if (!searchOperation.returnEntry(entry, entryControls))
      {
        cancel();
        searchOperation.sendSearchResultDone();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      cancel();

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
   * Registers a cancellation callback with this persistent search.
   * The cancellation callback will be notified when this persistent
   * search has been cancelled.
   *
   * @param callback
   *          The cancellation callback.
   */
  public void registerCancellationCallback(CancellationCallback callback)
  {
    cancellationCallbacks.add(callback);
  }



  /**
   * Enable this persistent search. The persistent search will be
   * registered with the client connection and will be prevented from
   * sending responses to the client.
   */
  public void enable()
  {
    searchOperation.getClientConnection().registerPersistentSearch(this);
    searchOperation.setSendResponse(false);
  }



  /**
   * Retrieves a string representation of this persistent search.
   *
   * @return A string representation of this persistent search.
   */
  @Override
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this persistent search to the
   * provided buffer.
   *
   * @param buffer
   *          The buffer to which the information should be appended.
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
