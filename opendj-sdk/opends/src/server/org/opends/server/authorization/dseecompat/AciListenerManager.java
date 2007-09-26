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

package org.opends.server.authorization.dseecompat;
import org.opends.messages.Message;

import org.opends.server.workflowelement.localbackend.*;
import org.opends.server.api.ChangeNotificationListener;
import org.opends.server.api.BackendInitializationListener;
import org.opends.server.api.Backend;
import org.opends.server.api.AlertGenerator;
import org.opends.server.types.operation.PostResponseAddOperation;
import org.opends.server.types.operation.PostResponseDeleteOperation;
import org.opends.server.types.operation.PostResponseModifyOperation;
import org.opends.server.types.operation.PostResponseModifyDNOperation;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.*;
import static org.opends.messages.AccessControlMessages.*;
import org.opends.server.core.DirectoryServer;
import static org.opends.server.util.ServerConstants.*;

import java.util.*;

/**
 * The AciListenerManager updates an ACI list after each
 * modification operation. Also, updates ACI list when backends are initialized
 * and finalized.
 */
public class AciListenerManager
        implements ChangeNotificationListener, BackendInitializationListener,
                   AlertGenerator {
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();


    /**
     * The fully-qualified name of this class.
     */
    private static final String CLASS_NAME =
         "org.opends.server.authorization.dseecompat.AciListenerManager";

    /*
     *  The configuration DN.
     */
    private DN configurationDN;


    /*
     *  True if the server is in lockdown mode.
     */
    private boolean inLockDownMode=false;

    /*
     * The AciList caches the ACIs.
     */
    private AciList aciList;

    /*
     * Search filter used in context search for "aci" attribute types.
     */
    private static SearchFilter aciFilter;

    /*
     * The aci attribute type is operational so we need to specify it to be
     * returned.
     */
    private static LinkedHashSet<String> attrs = new LinkedHashSet<String>();

    static {
        /*
         * Set up the filter used to search private and public contexts.
         */
        try {
            aciFilter=SearchFilter.createFilterFromString("(aci=*)");
        } catch (DirectoryException ex) {
            //TODO should never happen, error message?
        }
        attrs.add("aci");
    }

    /**
     * Save the list created by the AciHandler routine. Registers as an
     * Alert Generator that can send alerts when the server is being put
     * in lockdown  mode. Registers as backend initialization listener that is
     * used to manage the ACI list cache when backends are
     * initialized/finalized. Registers as a change notification listener that
     * is used to manage the ACI list cache after ACI modifications have been
     * performed.
     *
     * @param aciList The list object created and loaded by the handler.
     * @param cfgDN The DN of the access control configuration entry.
     */
    public AciListenerManager(AciList aciList, DN cfgDN) {
        this.aciList=aciList;
        this.configurationDN=cfgDN;
        DirectoryServer.registerChangeNotificationListener(this);
        DirectoryServer.registerBackendInitializationListener(this);
        DirectoryServer.registerAlertGenerator(this);
    }

    /**
     * A delete operation succeeded. Remove any ACIs associated with the
     * entry deleted.
     * @param deleteOperation The delete operation.
     * @param entry The entry being deleted.
     */
    public void handleDeleteOperation(PostResponseDeleteOperation
            deleteOperation, Entry entry) {
        boolean hasAci,  hasGlobalAci=false;
        //This entry might have both global and aci attribute types.
        if((hasAci=entry.hasOperationalAttribute(AciHandler.aciType)) ||
                (hasGlobalAci=entry.hasAttribute(AciHandler.globalAciType)))
            aciList.removeAci(entry, hasAci, hasGlobalAci);
    }

    /**
     * An Add operation succeeded. Add any ACIs associated with the
     * entry being added.
     * @param addOperation  The add operation.
     * @param entry   The entry being added.
     */
    public void handleAddOperation(PostResponseAddOperation addOperation,
                                   Entry entry) {
        boolean hasAci, hasGlobalAci=false;
        //Ignore this list, the ACI syntax has already passed and it should be
        //empty.
        LinkedList<Message>failedACIMsgs=new LinkedList<Message>();
        //This entry might have both global and aci attribute types.
        if((hasAci=entry.hasOperationalAttribute(AciHandler.aciType)) ||
                (hasGlobalAci=entry.hasAttribute(AciHandler.globalAciType)))
            aciList.addAci(entry, hasAci, hasGlobalAci, failedACIMsgs);
    }

    /**
     * A modify operation succeeded. Adjust the ACIs by removing
     * ACIs based on the oldEntry and then adding ACIs based on the new
     * entry.
     * @param modOperation  the modify operation.
     * @param oldEntry The old entry to examine.
     * @param newEntry  The new entry to examine.
     */
    public void handleModifyOperation(PostResponseModifyOperation modOperation,
                                      Entry oldEntry, Entry newEntry)
    {
        // A change to the ACI list is expensive so let's first make sure that
        // the modification included changes to the ACI. We'll check for
        //both "aci" attribute types and global "ds-cfg-global-aci" attribute
        //types.
        boolean hasAci = false, hasGlobalAci=false;
        List<Modification> mods = modOperation.getModifications();
        for (Modification mod : mods) {
            AttributeType attributeType=mod.getAttribute().getAttributeType();
            if (attributeType.equals(AciHandler.aciType))
                hasAci = true;
           else if(attributeType.equals(AciHandler.globalAciType))
                hasGlobalAci=true;
            if(hasAci && hasGlobalAci)
               break;
        }
        if (hasAci || hasGlobalAci)
            aciList.modAciOldNewEntry(oldEntry, newEntry, hasAci, hasGlobalAci);
    }

    /**
     * A modify DN operation has succeeded. Adjust the ACIs by moving ACIs
     * under the old entry DN to the new entry DN.
     * @param modifyDNOperation  The LDAP modify DN operation.
     * @param oldEntry  The old entry.
     * @param newEntry The new entry.
     */
    public void handleModifyDNOperation(
            PostResponseModifyDNOperation modifyDNOperation,
            Entry oldEntry, Entry newEntry)
    {
        aciList.renameAci(oldEntry.getDN(), newEntry.getDN());
    }

    /**
     * {@inheritDoc}  In this case, the server will search the backend to find
     * all aci attribute type values that it may contain and add them to the
     * ACI list.
     */
    public void performBackendInitializationProcessing(Backend backend) {
      // Check to make sure that the backend has a presence index defined for
      // the ACI attribute.  If it does not, then log a warning message because
      // this processing could be very expensive.
      AttributeType aciType = DirectoryServer.getAttributeType("aci", true);
      if (! backend.isIndexed(aciType, IndexType.PRESENCE))
      {
        logError(WARN_ACI_ATTRIBUTE_NOT_INDEXED.get(backend.getBackendID(),
                                                    "aci"));
      }


      InternalClientConnection conn =
           InternalClientConnection.getRootConnection();
      LinkedList<Message>failedACIMsgs=new LinkedList<Message>();
      //Add manageDsaIT control so any ACIs in referral entries will be
      //picked up.
      ArrayList<Control> controls = new ArrayList<Control>(1);
      controls.add(new Control(OID_MANAGE_DSAIT_CONTROL, true));
      for (DN baseDN : backend.getBaseDNs()) {
        try {
          if (! backend.entryExists(baseDN))  {
            continue;
          }
        } catch (Exception e) {
            if (debugEnabled())
            {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }
            continue;
        }
        InternalSearchOperation internalSearch =
             new InternalSearchOperation(
                  conn,
                  InternalClientConnection.nextOperationID(),
                  InternalClientConnection.nextMessageID(),
                  controls, baseDN, SearchScope.WHOLE_SUBTREE,
                  DereferencePolicy.NEVER_DEREF_ALIASES,
                  0, 0, false, aciFilter, attrs, null);
        LocalBackendSearchOperation localInternalSearch =
          new LocalBackendSearchOperation(internalSearch);
        try  {
          backend.search(localInternalSearch);
        } catch (Exception e) {
            if (debugEnabled())
            {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }
            continue;
        }
        if(internalSearch.getSearchEntries().isEmpty()) {
          Message message =
              INFO_ACI_ADD_LIST_NO_ACIS.get(String.valueOf(baseDN));
          logError(message);
        } else {
          int validAcis = aciList.addAci(
               internalSearch.getSearchEntries(), failedACIMsgs);
          if(!failedACIMsgs.isEmpty())
                    logMsgsSetLockDownMode(failedACIMsgs);
          Message message = INFO_ACI_ADD_LIST_ACIS.get(
              Integer.toString(validAcis), String.valueOf(baseDN));
          logError(message);
        }
      }
    }

    /**
     * {@inheritDoc}  In this case, the server will remove all aci attribute
     * type values associated with entries in the provided backend.
     */
    public void performBackendFinalizationProcessing(Backend backend) {
        aciList.removeAci(backend);
    }



    /**
     * Retrieves the fully-qualified name of the Java class for this alert
     * generator implementation.
     *
     * @return  The fully-qualified name of the Java class for this alert
     *          generator implementation.
     */
    public String getClassName()
    {
        return CLASS_NAME;
    }


    /**
     * Retrieves the DN of the configuration entry used to configure the
     * handler.
     *
     * @return  The DN of the configuration entry containing the Access Control
     *          configuration information.
     */
    public DN getComponentEntryDN()
    {
      return this.configurationDN;
    }


    /**
     * Retrieves information about the set of alerts that this generator may
     * produce.  The map returned should be between the notification type for a
     * particular notification and the human-readable description for that
     * notification.  This alert generator must not generate any alerts with
     * types that are not contained in this list.
     *
     * @return  Information about the set of alerts that this generator may
     *          produce.
     */
    public LinkedHashMap<String,String> getAlerts()
    {
        LinkedHashMap<String,String> alerts =
                new LinkedHashMap<String,String>();
        alerts.put(ALERT_TYPE_ACCESS_CONTROL_PARSE_FAILED,
                ALERT_DESCRIPTION_ACCESS_CONTROL_PARSE_FAILED);
        return alerts;

    }

    /**
     * Log the exception messages from the failed ACI decode and then put the
     * server in lockdown mode -- if needed.
     *
     * @param failedACIMsgs  List of exception messages from failed ACI decodes.
     */
    public  void logMsgsSetLockDownMode(LinkedList<Message> failedACIMsgs) {

        for(Message msg : failedACIMsgs) {
            Message message=WARN_ACI_SERVER_DECODE_FAILED.get(msg);
            logError(message);
        }
        if(!inLockDownMode)
            setLockDownMode();
    }


    /**
     * Send an WARN_ACI_ENTER_LOCKDOWN_MODE alert notification and put the
     * server in lockdown mode.
     *
     */
    private void setLockDownMode() {
        if(!inLockDownMode) {
            inLockDownMode=true;
            //Send ALERT_TYPE_ACCESS_CONTROL_PARSE_FAILED alert that
            //lockdown is about to be entered.
            Message lockDownMsg=WARN_ACI_ENTER_LOCKDOWN_MODE.get();
            DirectoryServer.sendAlertNotification(this,
                    ALERT_TYPE_ACCESS_CONTROL_PARSE_FAILED,
                    lockDownMsg );
            //Enter lockdown mode.
            DirectoryServer.setLockdownMode(true);

        }
    }
}
