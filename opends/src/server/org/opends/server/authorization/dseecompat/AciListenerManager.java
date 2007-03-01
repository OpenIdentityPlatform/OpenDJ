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

import org.opends.server.api.ChangeNotificationListener;
import org.opends.server.api.BackendInitializationListener;
import org.opends.server.api.Backend;
import org.opends.server.types.operation.PostResponseAddOperation;
import org.opends.server.types.operation.PostResponseDeleteOperation;
import org.opends.server.types.operation.PostResponseModifyOperation;
import org.opends.server.types.operation.PostResponseModifyDNOperation;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import static org.opends.server.loggers.debug.DebugLogger.debugCought;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.Error.logError;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DN;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchScope;
import static org.opends.server.authorization.dseecompat.AciMessages.*;
import static org.opends.server.messages.MessageHandler.getMessage;

import java.util.LinkedHashSet;

/**
 * The AciListenerManager updates an ACI list after each
 * modification operation. Also, updates ACI list when backends are initialized
 * and finalized.
 */
public class AciListenerManager
        implements ChangeNotificationListener, BackendInitializationListener {


    private AciList aciList;
       /*
     * Search filter used in context search for "aci" attribute types.
     */
    private static SearchFilter aciFilter;
    /**
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
     * Save the list created by the AciHandler routine.
     * @param aciList The list object created and loaded by the handler.
     */
    public AciListenerManager(AciList aciList) {
        this.aciList=aciList;
    }

    /**
     * A delete operation succeeded. Remove any ACIs associated with the
     * entry deleted.
     * @param deleteOperation The delete operation.
     * @param entry The entry being deleted.
     */
    public void handleDeleteOperation(PostResponseDeleteOperation
                                      deleteOperation, Entry entry) {
       if(entry.hasOperationalAttribute(AciHandler.aciType)) {
            aciList.removeAci(entry);
       }
    }

    /**
     * An Add operation succeeded. Add any ACIs associated with the
     * entry being added.
     * @param addOperation  The add operation.
     * @param entry   The entry being added.
     */
    public void handleAddOperation(PostResponseAddOperation addOperation,
            Entry entry) {
        if(entry.hasOperationalAttribute(AciHandler.aciType))
        {
            aciList.addAci(entry);
        }
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
        aciList.modAciOldNewEntry(oldEntry, newEntry);
    }

    /**
     * Not implemented.
     * @param modifyDNOperation  The LDAP modify DN operation.
     * @param oldEntry  The old entry.
     * @param newEntry The new entry.
     */
    public void handleModifyDNOperation(
            PostResponseModifyDNOperation modifyDNOperation,
            Entry oldEntry, Entry newEntry)
    {
        /*
         * TODO Not yet implemented.
         */
    }

    /**
     * {@inheritDoc}  In this case, the server will search the backend to find
     * all aci attribute type values that it may contain and add them to the
     * ACI list.
     */
    public void performBackendInitializationProcessing(Backend backend) {
        InternalClientConnection conn =
                InternalClientConnection.getRootConnection();
        for (DN baseDN : backend.getBaseDNs()) {
            try {
                if (! backend.entryExists(baseDN))  {
                    continue;
                }
            } catch (Exception e) {
              if (debugEnabled())
              {
                debugCought(DebugLogLevel.ERROR, e);
              }
                //TODO log message
                continue;
            }
            InternalSearchOperation internalSearch =
                    new InternalSearchOperation(conn,
                            InternalClientConnection.nextOperationID(),
                            InternalClientConnection.nextMessageID(),
                            null, baseDN, SearchScope.WHOLE_SUBTREE,
                            DereferencePolicy.NEVER_DEREF_ALIASES,
                            0, 0, false, aciFilter, attrs, null);
            try
            {
                backend.search(internalSearch);
            } catch (Exception e) {
              if (debugEnabled())
              {
                debugCought(DebugLogLevel.ERROR, e);
              }
                //TODO log message
                continue;
            }
            if(internalSearch.getSearchEntries().isEmpty()) {
                int    msgID  = MSGID_ACI_ADD_LIST_NO_ACIS;
                String message = getMessage(msgID, String.valueOf(baseDN));
                logError(ErrorLogCategory.ACCESS_CONTROL,
                        ErrorLogSeverity.NOTICE, message, msgID);
            } else {
                int validAcis=0;
                for (SearchResultEntry entry :
                        internalSearch.getSearchEntries()) {
                    validAcis += aciList.addAci(entry);
                }
                int    msgID  = MSGID_ACI_ADD_LIST_ACIS;
                String message = getMessage(msgID, Integer.toString(validAcis),
                        String.valueOf(baseDN));
                logError(ErrorLogCategory.ACCESS_CONTROL,
                        ErrorLogSeverity.NOTICE,
                        message, msgID);
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
}
