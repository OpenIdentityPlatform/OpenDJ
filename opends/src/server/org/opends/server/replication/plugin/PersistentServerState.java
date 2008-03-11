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
package org.opends.server.replication.plugin;
import org.opends.messages.Message;

import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.messages.ReplicationMessages.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Iterator;

import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperationBasis;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.common.ServerState;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.LDAPException;
import org.opends.server.types.ModificationType;
import org.opends.server.types.RawModification;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchScope;

/**
 * This class implements a ServerState that is stored on the backends
 * used to store the synchronized data and that is therefore persistent
 * accross server reboot.
 */
public class PersistentServerState extends ServerState
{
   private DN baseDn;
   private boolean savedStatus = true;
   private InternalClientConnection conn =
       InternalClientConnection.getRootConnection();
   private ASN1OctetString asn1BaseDn;
   private short serverId;

   /**
    * The attribute name used to store the state in the backend.
    */
   protected static final String REPLICATION_STATE = "ds-sync-state";

  /**
   * create a new ServerState.
   * @param baseDn The baseDN for which the ServerState is created
   *  @param serverId The serverId
   */
  public PersistentServerState(DN baseDn, short serverId)
  {
    this.baseDn = baseDn;
    this.serverId = serverId;
    asn1BaseDn = new ASN1OctetString(baseDn.toString());
    loadState();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean update(ChangeNumber changeNumber)
  {
    savedStatus = false;
    return super.update(changeNumber);
  }

  /**
   * Save this object to persistent storage.
   */
  public void save()
  {
    if (savedStatus)
      return;

    savedStatus = true;
    ResultCode resultCode = updateStateEntry();
    if (resultCode != ResultCode.SUCCESS)
    {
        savedStatus = false;
    }
  }

  /**
   * Load the ServerState from the backing entry in database to memory.
   */
  public void loadState()
  {
    SearchResultEntry stateEntry = null;

    // try to load the state from the base entry.
    stateEntry = searchBaseEntry();

    if (stateEntry == null)
    {
      // The base entry does not exist yet
      // in the database or was deleted. Try to read the ServerState
      // from the configuration instead.
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
   * @return Thebasen Entry or null if no entry was found;
   */
  private SearchResultEntry searchBaseEntry()
  {
    LDAPFilter filter;

    try
    {
      filter = LDAPFilter.decode("objectclass=*");
    } catch (LDAPException e)
    {
      // can not happen
      return null;
    }

    /*
     * Search the database entry that is used to periodically
     * save the ServerState
     */
    LinkedHashSet<String> attributes = new LinkedHashSet<String>(1);
    attributes.add(REPLICATION_STATE);
    InternalSearchOperation search = conn.processSearch(asn1BaseDn,
        SearchScope.BASE_OBJECT,
        DereferencePolicy.DEREF_ALWAYS, 0, 0, false,
        filter,attributes);
    if (((search.getResultCode() != ResultCode.SUCCESS)) &&
        ((search.getResultCode() != ResultCode.NO_SUCH_OBJECT)))
    {
      Message message = ERR_ERROR_SEARCHING_RUV.
          get(search.getResultCode().getResultCodeName(), search.toString(),
              search.getErrorMessage(), baseDn.toString());
      logError(message);
      return null;
    }

    SearchResultEntry stateEntry = null;
    if (search.getResultCode() == ResultCode.SUCCESS)
    {
      /*
       * Read the serverState from the REPLICATION_STATE attribute
       */
      LinkedList<SearchResultEntry> result = search.getSearchEntries();
      if (!result.isEmpty())
      {
        stateEntry = result.getFirst();
      }
    }
    return stateEntry;
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
      SearchFilter filter =
        SearchFilter.createFilterFromString(
            "(&(objectclass=ds-cfg-replication-domain)"
            +"(ds-cfg-base-dn="+baseDn+"))");

      LinkedHashSet<String> attributes = new LinkedHashSet<String>(1);
      attributes.add(REPLICATION_STATE);
      InternalSearchOperation op =
          conn.processSearch(DN.decode("cn=config"),
          SearchScope.SUBORDINATE_SUBTREE,
          DereferencePolicy.NEVER_DEREF_ALIASES,
          1, 0, false, filter, attributes);

      if (op.getResultCode() == ResultCode.SUCCESS)
      {
        /*
         * Read the serverState from the REPLICATION_STATE attribute
         */
        LinkedList<SearchResultEntry> resultEntries =
          op.getSearchEntries();
        if (!resultEntries.isEmpty())
        {
          SearchResultEntry resultEntry = resultEntries.getFirst();
          return resultEntry;
        }
      }
      return null;
    } catch (DirectoryException e)
    {
      // can not happen
      return null;
    }
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
    List<Attribute> attrs =
      resultEntry.getAttribute(synchronizationStateType);
    if (attrs != null)
    {
      Attribute attr = attrs.get(0);
      LinkedHashSet<AttributeValue> values = attr.getValues();
      for (AttributeValue value : values)
      {
        ChangeNumber changeNumber =
          new ChangeNumber(value.getStringValue());
        update(changeNumber);
      }
    }
  }

  /**
   * Save the current values of this PersistentState object
   * in the appropiate entry of the database.
   *
   * @return a ResultCode indicating if the method was successfull.
   */
  private ResultCode updateStateEntry()
  {
    /*
     * Generate a modify operation on the Server State baseD Entry.
     */
    ResultCode result = runUpdateStateEntry(baseDn);

    if (result == ResultCode.NO_SUCH_OBJECT)
    {
      // The base entry does not exist yet in the database or
      // has been deleted, save the state to the config entry instead.
      SearchResultEntry configEntry = searchConfigEntry();
      if (configEntry != null)
      {
        DN configDN = configEntry.getDN();
        result = runUpdateStateEntry(configDN);
      }
    }
    return result;
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
    ArrayList<ASN1OctetString> values = this.toASN1ArrayList();

    LDAPAttribute attr =
      new LDAPAttribute(REPLICATION_STATE, values);
    LDAPModification mod = new LDAPModification(ModificationType.REPLACE, attr);
    ArrayList<RawModification> mods = new ArrayList<RawModification>(1);
    mods.add(mod);

    ModifyOperationBasis op =
      new ModifyOperationBasis(conn, InternalClientConnection.nextOperationID(),
          InternalClientConnection.nextMessageID(),
          new ArrayList<Control>(0),
          new ASN1OctetString(serverStateEntryDN.toString()),
          mods);
    op.setInternalOperation(true);
    op.setSynchronizationOperation(true);
    op.setDontSynchronize(true);
    op.run();
    if (op.getResultCode() != ResultCode.SUCCESS)
    {
      Message message = DEBUG_ERROR_UPDATING_RUV.get(
              op.getResultCode().getResultCodeName().toString(),
              op.toString(),
              op.getErrorMessage().toString(),
              baseDn.toString());
      logError(message);
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
    super.clear();
    this.savedStatus = false;
  }

  /**
   * Empty the ServerState.
   * After this call the Server State will be in the same state
   * as if it was just created.
   */
  public void clear()
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
  public final void checkAndUpdateServerState() {
    Message message;
    InternalSearchOperation op;
    ChangeNumber serverStateMaxCn;
    ChangeNumber dbMaxCn;
    final AttributeType histType =
      DirectoryServer.getAttributeType(Historical.HISTORICALATTRIBUTENAME);

    // Retrieves the entries that have changed since the
    // maxCn stored in the serverState
    synchronized (this)
    {
      serverStateMaxCn = this.getMaxChangeNumber(serverId);

      if (serverStateMaxCn == null)
        return;

      try {
        op = ReplicationBroker.searchForChangedEntries(baseDn,
            serverStateMaxCn, null);
      }
      catch (Exception  e)
      {
        return;
      }
      if (op.getResultCode() != ResultCode.SUCCESS)
      {
        // An error happened trying to search for the updates
        // Log an error
        message = ERR_CANNOT_RECOVER_CHANGES.get(
            baseDn.toNormalizedString());
        logError(message);
      }
      else
      {
        dbMaxCn = serverStateMaxCn;
        for (SearchResultEntry resEntry : op.getSearchEntries())
        {
          List<Attribute> attrs = resEntry.getAttribute(histType);
          Iterator<AttributeValue> iav = attrs.get(0).getValues().iterator();
          try
          {
            while (true)
            {
              AttributeValue attrVal = iav.next();
              HistVal histVal = new HistVal(attrVal.getStringValue());
              ChangeNumber cn = histVal.getCn();

              if ((cn != null) && (cn.getServerId() == serverId))
              {
                // compare the csn regarding the maxCn we know and
                // store the biggest
                if (ChangeNumber.compare(dbMaxCn, cn) < 0)
                {
                  dbMaxCn = cn;
                }
              }
            }
          }
          catch(Exception e)
          {
          }
        }

        if (ChangeNumber.compare(dbMaxCn, serverStateMaxCn) > 0)
        {
          // Update the serverState with the new maxCn
          // present in the database
          this.update(dbMaxCn);
          message = NOTE_SERVER_STATE_RECOVERY.get(
              baseDn.toNormalizedString(), dbMaxCn.toString());
          logError(message);
        }
      }
    }
  }
}