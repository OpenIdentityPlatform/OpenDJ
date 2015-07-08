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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.authorization.dseecompat;

import java.util.*;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.api.AlertGenerator;
import org.opends.server.api.Backend;
import org.opends.server.api.BackendInitializationListener;
import org.opends.server.api.plugin.InternalDirectoryServerPlugin;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.api.plugin.PluginResult.PostOperation;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.types.*;
import org.opends.server.types.operation.*;
import org.opends.server.workflowelement.localbackend.LocalBackendSearchOperation;

import static org.opends.messages.AccessControlMessages.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.opends.server.util.ServerConstants.*;

/**
 * The AciListenerManager updates an ACI list after each modification
 * operation. Also, updates ACI list when backends are initialized and
 * finalized.
 */
public class AciListenerManager implements
    BackendInitializationListener, AlertGenerator
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * The fully-qualified name of this class.
   */
  private static final String CLASS_NAME =
      "org.opends.server.authorization.dseecompat.AciListenerManager";



  /**
   * Internal plugin used for updating the cache before a response is
   * sent to the client.
   */
  private final class AciChangeListenerPlugin extends
      InternalDirectoryServerPlugin
  {
    private AciChangeListenerPlugin()
    {
      super(configurationDN, EnumSet.of(
          PluginType.POST_SYNCHRONIZATION_ADD,
          PluginType.POST_SYNCHRONIZATION_DELETE,
          PluginType.POST_SYNCHRONIZATION_MODIFY,
          PluginType.POST_SYNCHRONIZATION_MODIFY_DN,
          PluginType.POST_OPERATION_ADD,
          PluginType.POST_OPERATION_DELETE,
          PluginType.POST_OPERATION_MODIFY,
          PluginType.POST_OPERATION_MODIFY_DN), true);
    }



    /** {@inheritDoc} */
    @Override
    public void doPostSynchronization(
        PostSynchronizationAddOperation addOperation)
    {
      Entry entry = addOperation.getEntryToAdd();
      if (entry != null)
      {
        doPostAdd(entry);
      }
    }



    /** {@inheritDoc} */
    @Override
    public void doPostSynchronization(
        PostSynchronizationDeleteOperation deleteOperation)
    {
      Entry entry = deleteOperation.getEntryToDelete();
      if (entry != null)
      {
        doPostDelete(entry);
      }
    }



    /** {@inheritDoc} */
    @Override
    public void doPostSynchronization(
        PostSynchronizationModifyDNOperation modifyDNOperation)
    {
      Entry entry = modifyDNOperation.getUpdatedEntry();
      if (entry != null)
      {
        doPostModifyDN(entry.getName(), entry.getName());
      }
    }



    /** {@inheritDoc} */
    @Override
    public void doPostSynchronization(
        PostSynchronizationModifyOperation modifyOperation)
    {
      Entry entry = modifyOperation.getCurrentEntry();
      Entry modEntry = modifyOperation.getModifiedEntry();
      if ((entry != null) && (modEntry != null))
      {
        doPostModify(modifyOperation.getModifications(), entry, modEntry);
      }
    }



    /** {@inheritDoc} */
    @Override
    public PostOperation doPostOperation(
        PostOperationAddOperation addOperation)
    {
      // Only do something if the operation is successful, meaning there
      // has been a change.
      if (addOperation.getResultCode() == ResultCode.SUCCESS)
      {
        doPostAdd(addOperation.getEntryToAdd());
      }

      // If we've gotten here, then everything is acceptable.
      return PluginResult.PostOperation.continueOperationProcessing();
    }



    /** {@inheritDoc} */
    @Override
    public PostOperation doPostOperation(
        PostOperationDeleteOperation deleteOperation)
    {
      // Only do something if the operation is successful, meaning there
      // has been a change.
      if (deleteOperation.getResultCode() == ResultCode.SUCCESS)
      {
        doPostDelete(deleteOperation.getEntryToDelete());
      }

      // If we've gotten here, then everything is acceptable.
      return PluginResult.PostOperation.continueOperationProcessing();
    }



    /** {@inheritDoc} */
    @Override
    public PostOperation doPostOperation(
        PostOperationModifyDNOperation modifyDNOperation)
    {
      // Only do something if the operation is successful, meaning there
      // has been a change.
      if (modifyDNOperation.getResultCode() == ResultCode.SUCCESS)
      {
        doPostModifyDN(modifyDNOperation.getOriginalEntry().getName(),
          modifyDNOperation.getUpdatedEntry().getName());
      }

      // If we've gotten here, then everything is acceptable.
      return PluginResult.PostOperation.continueOperationProcessing();
    }



    /** {@inheritDoc} */
    @Override
    public PostOperation doPostOperation(
        PostOperationModifyOperation modifyOperation)
    {
      // Only do something if the operation is successful, meaning there
      // has been a change.
      if (modifyOperation.getResultCode() == ResultCode.SUCCESS)
      {
        doPostModify(modifyOperation.getModifications(), modifyOperation
          .getCurrentEntry(), modifyOperation.getModifiedEntry());
      }

      // If we've gotten here, then everything is acceptable.
      return PluginResult.PostOperation.continueOperationProcessing();
    }



    private void doPostAdd(Entry addedEntry)
    {
      // This entry might have both global and aci attribute types.
      boolean hasAci = addedEntry.hasOperationalAttribute(AciHandler.aciType);
      boolean hasGlobalAci = addedEntry.hasAttribute(AciHandler.globalAciType);
      if (hasAci || hasGlobalAci)
      {
        // Ignore this list, the ACI syntax has already passed and it
        // should be empty.
        List<LocalizableMessage> failedACIMsgs = new LinkedList<>();

        aciList.addAci(addedEntry, hasAci, hasGlobalAci, failedACIMsgs);
      }
    }



    private void doPostDelete(Entry deletedEntry)
    {
      // This entry might have both global and aci attribute types.
      boolean hasAci = deletedEntry.hasOperationalAttribute(
              AciHandler.aciType);
      boolean hasGlobalAci = deletedEntry.hasAttribute(
              AciHandler.globalAciType);
      aciList.removeAci(deletedEntry, hasAci, hasGlobalAci);
    }



    private void doPostModifyDN(DN fromDN, DN toDN)
    {
      aciList.renameAci(fromDN, toDN);
    }



    private void doPostModify(List<Modification> mods, Entry oldEntry,
        Entry newEntry)
    {
      // A change to the ACI list is expensive so let's first make sure
      // that the modification included changes to the ACI. We'll check
      // for both "aci" attribute types and global "ds-cfg-global-aci"
      // attribute types.
      boolean hasAci = false, hasGlobalAci = false;
      for (Modification mod : mods)
      {
        AttributeType attributeType = mod.getAttribute()
            .getAttributeType();
        if (attributeType.equals(AciHandler.aciType))
        {
          hasAci = true;
        }
        else if (attributeType.equals(AciHandler.globalAciType))
        {
          hasGlobalAci = true;
        }

        if (hasAci && hasGlobalAci)
        {
          break;
        }
      }

      if (hasAci || hasGlobalAci)
      {
        aciList.modAciOldNewEntry(oldEntry, newEntry, hasAci,
            hasGlobalAci);
      }
    }

  }



  /** The configuration DN. */
  private DN configurationDN;

  /** True if the server is in lockdown mode. */
  private boolean inLockDownMode;

  /** The AciList caches the ACIs. */
  private AciList aciList;

  /** Search filter used in context search for "aci" attribute types. */
  private static SearchFilter aciFilter;

  /**
   * Internal plugin used for updating the cache before a response is
   * sent to the client.
   */
  private final AciChangeListenerPlugin plugin;

  /** The aci attribute type is operational so we need to specify it to be returned. */
  private static LinkedHashSet<String> attrs = new LinkedHashSet<>();
  static
  {
    // Set up the filter used to search private and public contexts.
    try
    {
      aciFilter = SearchFilter.createFilterFromString("(aci=*)");
    }
    catch (DirectoryException ex)
    {
      // TODO should never happen, error message?
    }
    attrs.add("aci");
  }



  /**
   * Save the list created by the AciHandler routine. Registers as an
   * Alert Generator that can send alerts when the server is being put
   * in lockdown mode. Registers as backend initialization listener that
   * is used to manage the ACI list cache when backends are
   * initialized/finalized. Registers as a change notification listener
   * that is used to manage the ACI list cache after ACI modifications
   * have been performed.
   *
   * @param aciList
   *          The list object created and loaded by the handler.
   * @param cfgDN
   *          The DN of the access control configuration entry.
   */
  public AciListenerManager(AciList aciList, DN cfgDN)
  {
    this.aciList = aciList;
    this.configurationDN = cfgDN;
    this.plugin = new AciChangeListenerPlugin();

    // Process ACI from already registered backends.
    Map<String, Backend> backendMap = DirectoryServer.getBackends();
    if (backendMap != null) {
      for (Backend backend : backendMap.values()) {
        performBackendInitializationProcessing(backend);
      }
    }

    DirectoryServer.registerInternalPlugin(plugin);
    DirectoryServer.registerBackendInitializationListener(this);
    DirectoryServer.registerAlertGenerator(this);
  }



  /**
   * Deregister from the change notification listener, the backend
   * initialization listener and the alert generator.
   */
  public void finalizeListenerManager()
  {
    DirectoryServer.deregisterInternalPlugin(plugin);
    DirectoryServer.deregisterBackendInitializationListener(this);
    DirectoryServer.deregisterAlertGenerator(this);
  }



  /**
   * {@inheritDoc} In this case, the server will search the backend to
   * find all aci attribute type values that it may contain and add them
   * to the ACI list.
   */
  @Override
  public void performBackendInitializationProcessing(Backend<?> backend)
  {
    // Check to make sure that the backend has a presence index defined
    // for the ACI attribute. If it does not, then log a warning message
    // because this processing could be very expensive.
    AttributeType aciType =
        DirectoryServer.getAttributeType("aci", true);
    if (backend.getEntryCount() > 0
        && !backend.isIndexed(aciType, IndexType.PRESENCE))
    {
      logger.warn(WARN_ACI_ATTRIBUTE_NOT_INDEXED, backend.getBackendID(), "aci");
    }

    LinkedList<LocalizableMessage> failedACIMsgs = new LinkedList<>();

    InternalClientConnection conn = getRootConnection();
    // Add manageDsaIT control so any ACIs in referral entries will be
    // picked up.
    LDAPControl c1 = new LDAPControl(OID_MANAGE_DSAIT_CONTROL, true);
    // Add group membership control to let a backend look for it and
    // decide if it would abort searches.
    LDAPControl c2 = new LDAPControl(OID_INTERNAL_GROUP_MEMBERSHIP_UPDATE, false);

    for (DN baseDN : backend.getBaseDNs())
    {
      try
      {
        if (!backend.entryExists(baseDN))
        {
          continue;
        }
      }
      catch (Exception e)
      {
        logger.traceException(e);
        continue;
      }
      SearchRequest request = newSearchRequest(baseDN, SearchScope.WHOLE_SUBTREE, aciFilter)
          .addControl(c1)
          .addControl(c2)
          .addAttribute(attrs);
      InternalSearchOperation internalSearch =
          new InternalSearchOperation(conn, nextOperationID(), nextMessageID(), request);
      LocalBackendSearchOperation localInternalSearch =
          new LocalBackendSearchOperation(internalSearch);
      try
      {
        backend.search(localInternalSearch);
      }
      catch (Exception e)
      {
        logger.traceException(e);
        continue;
      }
      if (!internalSearch.getSearchEntries().isEmpty())
      {
        int validAcis = aciList.addAci(internalSearch.getSearchEntries(), failedACIMsgs);
        if (!failedACIMsgs.isEmpty())
        {
          logMsgsSetLockDownMode(failedACIMsgs);
        }
        logger.debug(INFO_ACI_ADD_LIST_ACIS, validAcis, baseDN);
      }
    }
  }



  /**
   * {@inheritDoc} In this case, the server will remove all aci
   * attribute type values associated with entries in the provided
   * backend.
   */
  @Override
  public void performBackendFinalizationProcessing(Backend<?> backend)
  {
    aciList.removeAci(backend);
  }



  /**
   * Retrieves the fully-qualified name of the Java class for this alert
   * generator implementation.
   *
   * @return The fully-qualified name of the Java class for this alert
   *         generator implementation.
   */
  @Override
  public String getClassName()
  {
    return CLASS_NAME;
  }



  /**
   * Retrieves the DN of the configuration entry used to configure the
   * handler.
   *
   * @return The DN of the configuration entry containing the Access
   *         Control configuration information.
   */
  @Override
  public DN getComponentEntryDN()
  {
    return this.configurationDN;
  }



  /**
   * Retrieves information about the set of alerts that this generator
   * may produce. The map returned should be between the notification
   * type for a particular notification and the human-readable
   * description for that notification. This alert generator must not
   * generate any alerts with types that are not contained in this list.
   *
   * @return Information about the set of alerts that this generator may
   *         produce.
   */
  @Override
  public LinkedHashMap<String, String> getAlerts()
  {
    LinkedHashMap<String, String> alerts = new LinkedHashMap<>();
    alerts.put(ALERT_TYPE_ACCESS_CONTROL_PARSE_FAILED,
        ALERT_DESCRIPTION_ACCESS_CONTROL_PARSE_FAILED);
    return alerts;
  }

  /**
   * Log the exception messages from the failed ACI decode and then put
   * the server in lockdown mode -- if needed.
   *
   * @param failedACIMsgs
   *          List of exception messages from failed ACI decodes.
   */
  public void logMsgsSetLockDownMode(LinkedList<LocalizableMessage> failedACIMsgs)
  {
    for (LocalizableMessage msg : failedACIMsgs)
    {
      logger.warn(WARN_ACI_SERVER_DECODE_FAILED, msg);
    }
    if (!inLockDownMode)
    {
      setLockDownMode();
    }
  }



  /**
   * Send an WARN_ACI_ENTER_LOCKDOWN_MODE alert notification and put the
   * server in lockdown mode.
   */
  private void setLockDownMode()
  {
    if (!inLockDownMode)
    {
      inLockDownMode = true;
      // Send ALERT_TYPE_ACCESS_CONTROL_PARSE_FAILED alert that
      // lockdown is about to be entered.
      LocalizableMessage lockDownMsg = WARN_ACI_ENTER_LOCKDOWN_MODE.get();
      DirectoryServer.sendAlertNotification(this,
          ALERT_TYPE_ACCESS_CONTROL_PARSE_FAILED, lockDownMsg);
      // Enter lockdown mode.
      DirectoryServer.setLockdownMode(true);

    }
  }
}
