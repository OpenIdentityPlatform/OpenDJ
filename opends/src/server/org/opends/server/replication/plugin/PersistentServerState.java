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
package org.opends.server.replication.plugin;

import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.messages.MessageHandler.getMessage;
import static org.opends.server.messages.ReplicationMessages.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;

import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
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
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.LDAPException;
import org.opends.server.types.ModificationType;
import org.opends.server.types.RawModification;
import org.opends.server.types.ResultCode;
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

   /**
    * The attribute name used to store the state in the backend.
    */
   protected static final String REPLICATION_STATE = "ds-sync-state";

  /**
   * create a new ServerState.
   * @param baseDn The baseDN for which the ServerState is created
   */
  public PersistentServerState(DN baseDn)
  {
    this.baseDn = baseDn;
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
    /*
     * Read the serverState from the database,
     * If not there create empty entry
     */
    LDAPFilter filter;
    try
    {
      filter = LDAPFilter.decode("objectclass=*");
    } catch (LDAPException e)
    {
      // can not happen
      return;
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
      int msgID = MSGID_ERROR_SEARCHING_RUV;
      String message = getMessage(msgID,
          search.getResultCode().getResultCodeName(),
          search.toString(), search.getErrorMessage(),
          baseDn.toString());
      logError(ErrorLogCategory.SYNCHRONIZATION, ErrorLogSeverity.SEVERE_ERROR,
          message, msgID);
    }

    SearchResultEntry resultEntry = null;
    if (search.getResultCode() == ResultCode.SUCCESS)
    {
      /*
       * Read the serverState from the REPLICATION_STATE attribute
       */
      LinkedList<SearchResultEntry> result = search.getSearchEntries();
      resultEntry = result.getFirst();
      if (resultEntry != null)
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

      /*
       * TODO : The ServerState is saved to the database periodically,
       * therefore in case of crash it is possible that is does not contain
       * the latest changes that have been processed and saved to the
       * database.
       * In order to make sure that we don't loose them, search all the entries
       * that have been updated after this entry.
       * This is done by using the HistoricalCsnOrderingMatchingRule
       * and an ordering index for historical attribute
       */
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
    ArrayList<ASN1OctetString> values = this.toASN1ArrayList();

    if (values.size() == 0)
      return ResultCode.SUCCESS;

    LDAPAttribute attr =
      new LDAPAttribute(REPLICATION_STATE, values);
    LDAPModification mod = new LDAPModification(ModificationType.REPLACE, attr);
    ArrayList<RawModification> mods = new ArrayList<RawModification>(1);
    mods.add(mod);

    ModifyOperation op =
      new ModifyOperation(conn, InternalClientConnection.nextOperationID(),
          InternalClientConnection.nextMessageID(),
          new ArrayList<Control>(0), asn1BaseDn,
          mods);
    op.setInternalOperation(true);
    op.setSynchronizationOperation(true);
    op.setDontSynchronize(true);

    op.run();

    ResultCode result = op.getResultCode();
    if (result != ResultCode.SUCCESS)
    {
      int msgID = MSGID_ERROR_UPDATING_RUV;
      String message = getMessage(msgID, op.getResultCode().getResultCodeName(),
          op.toString(), op.getErrorMessage(), baseDn.toString(),
          Thread.currentThread().getStackTrace());
      logError(ErrorLogCategory.SYNCHRONIZATION, ErrorLogSeverity.SEVERE_ERROR,
          message, msgID);
    }
    return result;
  }
}
