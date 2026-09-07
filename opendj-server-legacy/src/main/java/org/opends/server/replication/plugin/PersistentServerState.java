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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2012-2016 ForgeRock AS.
 * Portions Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.replication.plugin;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.opends.server.replication.plugin.EntryHistorical.*;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.core.ModifyOperationBasis;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.ServerState;
import org.opends.server.types.Attribute;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.RawModification;
import org.opends.server.types.SearchResultEntry;

/**
 * This class implements a ServerState that is stored in the backend
 * used to store the synchronized data and that is therefore persistent
 * across server reboot.
 */
class PersistentServerState
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

   private final DN baseDN;
   private final int serverId;
   private final ServerState state;
   /**
    * Serialises the saves. Kept apart from the monitor of this object, which
    * {@link #checkAndUpdateServerState()} holds, so that loading the state does
    * not queue behind the backend write of a save.
    */
   private final Object saveLock = new Object();

   /**
    * The attribute name used to store the state in the backend.
    */
   private static final String REPLICATION_STATE = "ds-sync-state";

  /**
   * Create a new PersistentServerState based on an already existing
   * ServerState.
   *
   * @param baseDN    The baseDN for which the ServerState is created.
   * @param serverId  The serverId.
   * @param state     The serverState.
   */
  PersistentServerState(DN baseDN, int serverId, ServerState state)
  {
    this.baseDN = baseDN;
    this.serverId = serverId;
    this.state = state;
    loadState();
  }

  /**
   * Checks that the CSN given as a parameter is in this ServerState.
   *
   * @param   covered The CSN that should be checked.
   * @return  A boolean indicating if this ServerState contains the CSN
   *          given in parameter.
   */
  boolean cover(CSN covered)
  {
    return state.cover(covered);
  }

  /**
   * Update the Server State with a CSN. All operations with smaller CSN and the
   * same serverID must be committed before calling this method.
   *
   * @param csn
   *          The committed CSN.
   * @return a boolean indicating if the update was meaningful.
   */
  boolean update(CSN csn)
  {
    return state.update(csn);
  }

  /**
   * Save this object to persistent storage.
   * <p>
   * Saves exclude each other: two of them would otherwise each take their own
   * snapshot, and the write of the older one landing last would leave a state
   * on disk that is both stale and marked as saved.
   */
  public void save()
  {
    if (state.isSaved())
    {
      // Nothing to write: stay out of the queue of whoever is writing.
      return;
    }

    synchronized (saveLock)
    {
      if (!state.isSaved())
      {
        /*
         * Mark the state as saved before the snapshot that goes to the backend
         * is taken, so that an update landing while the write is in flight
         * clears the flag again and gets written by the next save. Marking it
         * afterwards would swallow such an update: it is not part of the write
         * it raced with, yet the state would look saved. The persisted state
         * would then stay stale until some later update happened to dirty it
         * again - which, on a domain as quiet as cn=schema, may never happen.
         */
        state.setSaved(true);
        boolean written = false;
        try
        {
          written = updateStateEntry();
        }
        finally
        {
          if (!written)
          {
            // The write reported a failure, or blew up on its way to the
            // backend: the state is not on disk, so leave it to the next save.
            state.setSaved(false);
          }
        }
      }
    }
  }

  /**
   * Load the ServerState from the backing entry in database to memory.
   */
  public void loadState()
  {
    // Whatever the state holds on the way in has, as far as this object knows,
    // never been written: what follows only merges in what the backend holds.
    final boolean hadCSNs = state.iterator().hasNext();

    // try to load the state from the base entry.
    SearchResultEntry stateEntry = searchBaseEntry();
    if (stateEntry == null)
    {
      /*
      The base entry does not exist yet in the database or was deleted.
      Try to read the ServerState from the configuration instead.
      */
      stateEntry = searchConfigEntry();
    }

    if (stateEntry != null)
    {
      updateStateFromEntry(stateEntry);
    }

    /*
     * In order to make sure that the replication never looses changes,
     * the server needs to search all the entries that have been
     * updated after the last write of the ServerState.
     * Inconsistencies may append after a crash.
     */
    checkAndUpdateServerState();

    if (hadCSNs)
    {
      state.setSaved(false);
    }
  }

  /**
   * Run a search operation to find the base entry
   * of the replication domain for which this ServerState was created.
   *
   * @return The base entry or null if no entry was found;
   */
  private SearchResultEntry searchBaseEntry()
  {
    // Search the database entry that is used to periodically save the ServerState
    final SearchRequest request = newSearchRequest(baseDN, SearchScope.BASE_OBJECT).addAttribute(REPLICATION_STATE);
    final InternalSearchOperation search = getRootConnection().processSearch(request);
    final ResultCode resultCode = search.getResultCode();
    if (resultCode != ResultCode.SUCCESS
        && resultCode != ResultCode.NO_SUCH_OBJECT)
    {
      logger.error(ERR_ERROR_SEARCHING_RUV, search.getResultCode().getName(), search, search.getErrorMessage(), baseDN);
      return null;
    }
    return getFirstResult(search);
  }

  /**
   * Run a search operation to find the entry with the configuration
   * of the replication domain for which this ServerState was created.
   *
   * @return The configuration Entry or null if no entry was found;
   */
  private SearchResultEntry searchConfigEntry()
  {
    try
    {
      String filter = "(&(objectclass=ds-cfg-replication-domain)" + "(ds-cfg-base-dn=" + baseDN + "))";
      final SearchRequest request = newSearchRequest("cn=config", SearchScope.SUBORDINATES, filter)
          .setSizeLimit(1)
          .addAttribute(REPLICATION_STATE);
      final InternalSearchOperation op = getRootConnection().processSearch(request);
      return getFirstResult(op);
    }
    catch (DirectoryException e)
    {
      // can not happen
      return null;
    }
  }

  private SearchResultEntry getFirstResult(InternalSearchOperation search)
  {
    if (search.getResultCode() == ResultCode.SUCCESS)
    {
      final LinkedList<SearchResultEntry> results = search.getSearchEntries();
      if (!results.isEmpty())
      {
        return results.getFirst();
      }
    }
    return null;
  }

  /**
   * Update this ServerState from the provided entry.
   *
   * @param resultEntry
   *          The entry that should be used to update this ServerState.
   */
  private void updateStateFromEntry(SearchResultEntry resultEntry)
  {
    Iterator<Attribute> attrs = resultEntry.getAllAttributes(REPLICATION_STATE).iterator();
    if (attrs.hasNext())
    {
      for (ByteString value : attrs.next())
      {
        update(new CSN(value.toString()));
      }
    }
  }

  /**
   * Save the current values of this PersistentState object
   * in the appropriate entry of the database.
   *
   * @return a boolean indicating if the method was successful.
   */
  private boolean updateStateEntry()
  {
    // Generate a modify operation on the Server State baseDN Entry.
    ResultCode result = runUpdateStateEntry(baseDN);
    if (result == ResultCode.NO_SUCH_OBJECT)
    {
      // The base entry does not exist yet in the database or has been deleted,
      // save the state to the config entry instead.
      SearchResultEntry configEntry = searchConfigEntry();
      if (configEntry != null)
      {
        result = runUpdateStateEntry(configEntry.getName());
      }
    }
    return result == ResultCode.SUCCESS;
  }

  /**
   * Run a modify operation to update the entry whose DN is given as
   * a parameter with the serverState information.
   *
   * @param serverStateEntryDN The DN of the entry to be updated.
   *
   * @return A ResultCode indicating if the operation was successful.
   */
  private ResultCode runUpdateStateEntry(DN serverStateEntryDN)
  {
    LDAPAttribute attr = new LDAPAttribute(REPLICATION_STATE, state.toASN1ArrayList());
    RawModification mod = new LDAPModification(ModificationType.REPLACE, attr);

    ModifyOperationBasis op = new ModifyOperationBasis(getRootConnection(),
          nextOperationID(), nextMessageID(), null,
          ByteString.valueOfUtf8(serverStateEntryDN.toString()),
          Collections.singletonList(mod));
    op.setInternalOperation(true);
    op.setSynchronizationOperation(true);
    op.setDontSynchronize(true);

    final ResultCode resultCode = runModify(op);
    if (resultCode != ResultCode.SUCCESS
        && !(resultCode == ResultCode.NO_SUCH_OBJECT && serverStateEntryDN.equals(baseDN)))
    {
      logger.error(DEBUG_ERROR_UPDATING_RUV, resultCode.getName(), op, op.getErrorMessage(), serverStateEntryDN);
    }
    return resultCode;
  }

  /**
   * Runs the modify operation that writes the state to the backend.
   * <p>
   * Kept separate, and overridable, so that a test can reach the point where
   * the snapshot has been taken but the write has not gone through yet: see
   * {@code PersistentServerStateTest}. Do not inline it.
   *
   * @param op The modify operation carrying the state to be written.
   * @return A ResultCode indicating if the operation was successful.
   */
  ResultCode runModify(ModifyOperationBasis op)
  {
    op.run();
    return op.getResultCode();
  }

  /**
   * Empty the ServerState in memory.
   * <p>
   * The emptied state is marked as not saved, so the next save writes the empty
   * state out - which is what {@link #clear()} is after. A caller that only
   * means to drop the in-memory copy, and expects the backend to keep what it
   * holds, has to keep saves away until it has loaded the state back.
   */
  public void clearInMemory()
  {
    state.clear();
  }

  /**
   * Empty the ServerState and write the emptied state to persistent storage.
   */
  void clear()
  {
    clearInMemory();
    save();
  }

  /**
   * The ServerState is saved to the database periodically,
   * therefore in case of crash it is possible that is does not contain
   * the latest changes that have been processed and saved to the
   * database.
   * In order to make sure that we don't loose them, search all the entries
   * that have been updated after this entry.
   * This is done by using the HistoricalCsnOrderingMatchingRule
   * and an ordering index for historical attribute
   */
  private final void checkAndUpdateServerState()
  {
    // Retrieves the entries that have changed since the
    // maxCsn stored in the serverState
    synchronized (this)
    {
      CSN serverStateMaxCSN = state.getCSN(serverId);
      if (serverStateMaxCSN == null)
      {
        return;
      }

      InternalSearchOperation op;
      try
      {
        op = LDAPReplicationDomain.searchForChangedEntries(baseDN,
                serverStateMaxCSN, null);
      }
      catch (Exception  e)
      {
        return;
      }

      if (op.getResultCode() != ResultCode.SUCCESS)
      {
        // An error happened trying to search for the updates
        // Log an error
        logger.error(ERR_CANNOT_RECOVER_CHANGES, baseDN);
        return;
      }

      CSN dbMaxCSN = serverStateMaxCSN;
      for (SearchResultEntry resEntry : op.getSearchEntries())
      {
        for (ByteString attrValue : resEntry.getAllAttributes(HISTORICAL_ATTRIBUTE_NAME).iterator().next())
        {
          HistoricalAttributeValue histVal =
              new HistoricalAttributeValue(attrValue.toString());
          CSN csn = histVal.getCSN();
          if (csn != null
              && csn.getServerId() == serverId
              && dbMaxCSN.isOlderThan(csn))
          {
            dbMaxCSN = csn;
          }
        }
      }

      if (dbMaxCSN.isNewerThan(serverStateMaxCSN))
      {
        // Update the serverState with the new maxCsn present in the database
        update(dbMaxCSN);
        logger.info(NOTE_SERVER_STATE_RECOVERY, baseDN, dbMaxCSN);
      }
    }
  }

  /**
   * Get the largest CSN seen for a given LDAP server ID.
   *
   * @param serverId
   *          The serverId
   * @return The largest CSN seen
   */
  public CSN getMaxCSN(int serverId)
  {
    return state.getCSN(serverId);
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    return getClass().getSimpleName()
        + " baseDN=" + baseDN
        + " serverId=" + serverId
        + " " + REPLICATION_STATE + "=" + state;
  }
}
