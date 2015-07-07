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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.core;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.controls.EntryChangeNotificationControl;
import org.opends.server.controls.PersistentSearchChangeType;
import org.opends.server.types.CancelResult;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;

import static org.opends.server.controls.PersistentSearchChangeType.*;

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
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();



  /** Cancel a persistent search. */
  private static synchronized void cancel(PersistentSearch psearch)
  {
    if (!psearch.isCancelled)
    {
      psearch.isCancelled = true;

      // The persistent search can no longer be cancelled.
      psearch.searchOperation.getClientConnection().deregisterPersistentSearch(psearch);

      DirectoryServer.deregisterPersistentSearch();

      // Notify any cancellation callbacks.
      for (CancellationCallback callback : psearch.cancellationCallbacks)
      {
        try
        {
          callback.persistentSearchCancelled(psearch);
        }
        catch (Exception e)
        {
          logger.traceException(e);
        }
      }
    }
  }

  /** Cancellation callbacks which should be run when this persistent search is cancelled. */
  private final List<CancellationCallback> cancellationCallbacks = new CopyOnWriteArrayList<>();

  /** The set of change types to send to the client. */
  private final Set<PersistentSearchChangeType> changeTypes;

  /** Indicates whether or not this persistent search has already been aborted. */
  private boolean isCancelled;

  /**
   * Indicates whether entries returned should include the entry change
   * notification control.
   */
  private final boolean returnECs;

  /** The reference to the associated search operation. */
  private final SearchOperation searchOperation;

  /**
   * Indicates whether to only return entries that have been updated since the
   * beginning of the search.
   */
  private final boolean changesOnly;

  /**
   * Creates a new persistent search object with the provided information.
   *
   * @param searchOperation
   *          The search operation for this persistent search.
   * @param changeTypes
   *          The change types for which changes should be examined.
   * @param changesOnly
   *          whether to only return entries that have been updated since the
   *          beginning of the search
   * @param returnECs
   *          Indicates whether to include entry change notification controls in
   *          search result entries sent to the client.
   */
  public PersistentSearch(SearchOperation searchOperation,
      Set<PersistentSearchChangeType> changeTypes, boolean changesOnly,
      boolean returnECs)
  {
    this.searchOperation = searchOperation;
    this.changeTypes = changeTypes;
    this.changesOnly = changesOnly;
    this.returnECs = returnECs;
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

    return new CancelResult(ResultCode.CANCELLED, null);
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
   * Get the search operation associated with this persistent search.
   *
   * @return The search operation associated with this persistent search.
   */
  public SearchOperation getSearchOperation()
  {
    return searchOperation;
  }

  /**
   * Returns whether only entries updated after the beginning of this persistent
   * search should be returned.
   *
   * @return true if only entries updated after the beginning of this search
   *         should be returned, false otherwise
   */
  public boolean isChangesOnly()
  {
    return changesOnly;
  }

  /**
   * Notifies the persistent searches that an entry has been added.
   *
   * @param entry
   *          The entry that was added.
   */
  public void processAdd(Entry entry)
  {
    if (changeTypes.contains(ADD)
        && isInScope(entry.getName())
        && matchesFilter(entry))
    {
      sendEntry(entry, createControls(ADD, null));
    }
  }

  private boolean isInScope(final DN dn)
  {
    final DN baseDN = searchOperation.getBaseDN();
    switch (searchOperation.getScope().asEnum())
    {
    case BASE_OBJECT:
      return baseDN.equals(dn);
    case SINGLE_LEVEL:
      return baseDN.equals(dn.getParentDNInSuffix());
    case WHOLE_SUBTREE:
      return baseDN.isAncestorOf(dn);
    case SUBORDINATES:
      return !baseDN.equals(dn) && baseDN.isAncestorOf(dn);
    default:
      return false;
    }
  }

  private boolean matchesFilter(Entry entry)
  {
    try
    {
      final boolean filterMatchesEntry = searchOperation.getFilter().matchesEntry(entry);
      if (logger.isTraceEnabled())
      {
        logger.trace(this + " " + entry + " filter=" + filterMatchesEntry);
      }
      return filterMatchesEntry;
    }
    catch (DirectoryException de)
    {
      logger.traceException(de);

      // FIXME -- Do we need to do anything here?
      return false;
    }
  }

  /**
   * Notifies the persistent searches that an entry has been deleted.
   *
   * @param entry
   *          The entry that was deleted.
   */
  public void processDelete(Entry entry)
  {
    if (changeTypes.contains(DELETE)
        && isInScope(entry.getName())
        && matchesFilter(entry))
    {
      sendEntry(entry, createControls(DELETE, null));
    }
  }



  /**
   * Notifies the persistent searches that an entry has been modified.
   *
   * @param entry
   *          The entry after it was modified.
   */
  public void processModify(Entry entry)
  {
    processModify(entry, entry);
  }



  /**
   * Notifies persistent searches that an entry has been modified.
   *
   * @param entry
   *          The entry after it was modified.
   * @param oldEntry
   *          The entry before it was modified.
   */
  public void processModify(Entry entry, Entry oldEntry)
  {
    if (changeTypes.contains(MODIFY)
        && isInScopeForModify(oldEntry.getName())
        && anyMatchesFilter(entry, oldEntry))
    {
      sendEntry(entry, createControls(MODIFY, null));
    }
  }

  private boolean isInScopeForModify(final DN dn)
  {
    final DN baseDN = searchOperation.getBaseDN();
    switch (searchOperation.getScope().asEnum())
    {
    case BASE_OBJECT:
      return baseDN.equals(dn);
    case SINGLE_LEVEL:
      return baseDN.equals(dn.parent());
    case WHOLE_SUBTREE:
      return baseDN.isAncestorOf(dn);
    case SUBORDINATES:
      return !baseDN.equals(dn) && baseDN.isAncestorOf(dn);
    default:
      return false;
    }
  }

  private boolean anyMatchesFilter(Entry entry, Entry oldEntry)
  {
    return matchesFilter(oldEntry) || matchesFilter(entry);
  }

  /**
   * Notifies the persistent searches that an entry has been renamed.
   *
   * @param entry
   *          The entry after it was modified.
   * @param oldDN
   *          The DN of the entry before it was renamed.
   */
  public void processModifyDN(Entry entry, DN oldDN)
  {
    if (changeTypes.contains(MODIFY_DN)
        && isAnyInScopeForModify(entry, oldDN)
        && matchesFilter(entry))
    {
      sendEntry(entry, createControls(MODIFY_DN, oldDN));
    }
  }

  private boolean isAnyInScopeForModify(Entry entry, DN oldDN)
  {
    return isInScopeForModify(oldDN) || isInScopeForModify(entry.getName());
  }

  /**
   * The entry is one that should be sent to the client. See if we also need to
   * construct an entry change notification control.
   */
  private List<Control> createControls(PersistentSearchChangeType changeType,
      DN previousDN)
  {
    if (returnECs)
    {
      final Control c = previousDN != null
          ? new EntryChangeNotificationControl(changeType, previousDN, -1)
          : new EntryChangeNotificationControl(changeType, -1);
      return Collections.singletonList(c);
    }
    return Collections.emptyList();
  }

  private void sendEntry(Entry entry, List<Control> entryControls)
  {
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
      logger.traceException(e);

      cancel();

      try
      {
        searchOperation.sendSearchResultDone();
      }
      catch (Exception e2)
      {
        logger.traceException(e2);
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
    //Register itself with the Core.
    DirectoryServer.registerPersistentSearch();
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
    buffer.append(searchOperation.getScope());
    buffer.append(",filter=\"");
    searchOperation.getFilter().toString(buffer);
    buffer.append("\")");
  }
}
