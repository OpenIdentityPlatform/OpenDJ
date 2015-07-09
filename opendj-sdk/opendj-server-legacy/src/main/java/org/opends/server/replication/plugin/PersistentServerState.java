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
 *      Portions Copyright 2012-2015 ForgeRock AS.
 */
package org.opends.server.replication.plugin;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperationBasis;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.ServerState;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.RawModification;
import org.opends.server.types.SearchResultEntry;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;

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
   */
  public void save()
  {
    if (!state.isSaved())
    {
      state.setSaved(updateStateEntry());
    }
  }

  /**
   * Load the ServerState from the backing entry in database to memory.
   */
  public void loadState()
  {
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
   * @param resultEntry The entry that should be used to update this
   *                    ServerState.
   */
  private void updateStateFromEntry(SearchResultEntry resultEntry)
  {
    AttributeType synchronizationStateType =
      DirectoryServer.getAttributeType(REPLICATION_STATE);
    List<Attribute> attrs = resultEntry.getAttribute(synchronizationStateType);
    if (attrs != null)
    {
      for (ByteString value : attrs.get(0))
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
          ByteString.valueOf(serverStateEntryDN.toString()),
          Collections.singletonList(mod));
    op.setInternalOperation(true);
    op.setSynchronizationOperation(true);
    op.setDontSynchronize(true);
    op.run();
    if (op.getResultCode() != ResultCode.SUCCESS)
    {
      logger.error(DEBUG_ERROR_UPDATING_RUV,
          op.getResultCode().getName(), op, op.getErrorMessage(), baseDN);
    }
    return op.getResultCode();
  }

  /**
   * Empty the ServerState.
   * After this call the Server State will be in the same state
   * as if it was just created.
   */
  public void clearInMemory()
  {
    state.clear();
    state.setSaved(false);
  }

  /**
   * Empty the ServerState.
   * After this call the Server State will be in the same state
   * as if it was just created.
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
    final AttributeType histType = DirectoryServer.getAttributeType(
        EntryHistorical.HISTORICAL_ATTRIBUTE_NAME);

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
        for (ByteString attrValue : resEntry.getAttribute(histType).get(0))
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
