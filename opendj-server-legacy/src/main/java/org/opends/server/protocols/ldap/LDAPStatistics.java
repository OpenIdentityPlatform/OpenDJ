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
 */
package org.opends.server.protocols.ldap;

import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.util.ServerConstants.*;

import java.util.concurrent.atomic.AtomicLong;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.forgerock.opendj.server.config.server.MonitorProviderCfg;
import org.opends.server.api.MonitorData;
import org.opends.server.api.MonitorProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.OperationType;

/**
 * This class defines a data structure that will be used to keep track
 * of various metrics related to LDAP communication that the server has
 * conducted. The statistics that will be tracked include:
 * <UL>
 * <LI>The total number of LDAP client connections accepted by the
 * server.</LI>
 * <LI>The total number of LDAP client connections that have been
 * closed.</LI>
 * <LI>The total number of LDAP messages read, both overall and broken
 * down by message type.</LI>
 * <LI>The total number of LDAP messages written, both overall and
 * broken down by message type.</LI>
 * <LI>The total number of bytes read from LDAP clients.</LI>
 * <LI>The total number of bytes written to LDAP clients.</LI>
 * </UL>
 * <BR>
 * <BR>
 * This class may also be used in a hierarchical form if it is desirable
 * to get specific and general statistics at the same time (e.g.,
 * information about the interaction with a specific client or
 * aggregated for all clients).
 */
public class LDAPStatistics extends MonitorProvider<MonitorProviderCfg>
{

  // The statistics maintained by this class.
  private AtomicLong abandonRequests = new AtomicLong(0);
  private AtomicLong addRequests = new AtomicLong(0);
  private AtomicLong bindRequests = new AtomicLong(0);
  private AtomicLong addResponses = new AtomicLong(0);
  private AtomicLong bindResponses = new AtomicLong(0);
  private AtomicLong bytesRead = new AtomicLong(0);
  private AtomicLong bytesWritten = new AtomicLong(0);
  private AtomicLong compareRequests = new AtomicLong(0);
  private AtomicLong compareResponses = new AtomicLong(0);
  private AtomicLong connectionsClosed = new AtomicLong(0);
  private AtomicLong connectionsEstablished = new AtomicLong(0);
  private AtomicLong deleteRequests = new AtomicLong(0);
  private AtomicLong deleteResponses = new AtomicLong(0);
  private AtomicLong extendedRequests = new AtomicLong(0);
  private AtomicLong extendedResponses = new AtomicLong(0);
  private AtomicLong messagesRead = new AtomicLong(0);
  private AtomicLong messagesWritten = new AtomicLong(0);
  private AtomicLong modifyRequests = new AtomicLong(0);
  private AtomicLong modifyResponses = new AtomicLong(0);
  private AtomicLong modifyDNRequests = new AtomicLong(0);
  private AtomicLong modifyDNResponses = new AtomicLong(0);
  private AtomicLong operationsAbandoned = new AtomicLong(0);
  private AtomicLong operationsCompleted = new AtomicLong(0);
  private AtomicLong operationsInitiated = new AtomicLong(0);
  private AtomicLong searchRequests = new AtomicLong(0);
  private AtomicLong searchOneRequests = new AtomicLong(0);
  private AtomicLong searchSubRequests = new AtomicLong(0);
  private AtomicLong searchResultEntries = new AtomicLong(0);
  private AtomicLong searchResultReferences = new AtomicLong(0);
  private AtomicLong searchResultsDone = new AtomicLong(0);
  private AtomicLong unbindRequests = new AtomicLong(0);


  /** The instance name for this monitor provider instance. */
  private final String instanceName;

  // Monitor Objects : for Operations (count and time)
  private AtomicLong addOperationCount = new AtomicLong(0);
  private AtomicLong addOperationTime = new AtomicLong(0);
  private AtomicLong searchOperationCount = new AtomicLong(0);
  private AtomicLong searchOperationTime = new AtomicLong(0);
  private AtomicLong delOperationCount = new AtomicLong(0);
  private AtomicLong delOperationTime = new AtomicLong(0);
  private AtomicLong bindOperationCount = new AtomicLong(0);
  private AtomicLong bindOperationTime = new AtomicLong(0);
  private AtomicLong unbindOperationCount = new AtomicLong(0);
  private AtomicLong unbindOperationTime = new AtomicLong(0);
  private AtomicLong compOperationCount = new AtomicLong(0);
  private AtomicLong compOperationTime = new AtomicLong(0);
  private AtomicLong modOperationCount = new AtomicLong(0);
  private AtomicLong modOperationTime = new AtomicLong(0);
  private AtomicLong moddnOperationCount = new AtomicLong(0);
  private AtomicLong moddnOperationTime = new AtomicLong(0);
  private AtomicLong abandonOperationCount = new AtomicLong(0);
  private AtomicLong abandonOperationTime = new AtomicLong(0);
  private AtomicLong extOperationCount = new AtomicLong(0);
  private AtomicLong extOperationTime = new AtomicLong(0);

  /**
   * Creates a new instance of this class with the specified parent.
   *
   * @param instanceName
   *          The name for this monitor provider instance.
   */
  public LDAPStatistics(String instanceName)
  {
    this.instanceName = instanceName;
  }

  @Override
  public void initializeMonitorProvider(MonitorProviderCfg configuration)
      throws ConfigException
  {
    // Throw an exception, because this monitor is not intended to be
    // dynamically loaded from the configuration. Rather, it should be
    // explicitly created and registered by the LDAP connection handler
    // or an LDAP client connection.
    LocalizableMessage message =
        ERR_LDAP_STATS_INVALID_MONITOR_INITIALIZATION.get(configuration.dn());
    throw new ConfigException(message);
  }

  /**
   * Retrieves the name of this monitor provider. It should be unique
   * among all monitor providers, including all instances of the same
   * monitor provider.
   *
   * @return The name of this monitor provider.
   */
  @Override
  public String getMonitorInstanceName()
  {
    return instanceName;
  }

  @Override
  public ObjectClass getMonitorObjectClass()
  {
    return DirectoryServer.getSchema().getObjectClass(OC_MONITOR_CONNHANDLERSTATS);
  }

  @Override
  public MonitorData getMonitorData()
  {
    // Construct the list of attributes to return.
    /* TODO : the attribute names should be constant (in ServerConstants.java
     *        and associated with their objectclass
     *        OC_MONITOR_CONNHANDLERSTATS
     */
    final MonitorData attrs = new MonitorData(31 + 10 * 2);
    attrs.add("connectionsEstablished", connectionsEstablished);
    attrs.add("connectionsClosed", connectionsClosed);
    attrs.add("bytesRead", bytesRead);
    attrs.add("bytesWritten", bytesWritten);
    attrs.add("ldapMessagesRead", messagesRead);
    attrs.add("ldapMessagesWritten", messagesWritten);
    attrs.add("operationsAbandoned", operationsAbandoned);
    attrs.add("operationsInitiated", operationsInitiated);
    attrs.add("operationsCompleted", operationsCompleted);
    attrs.add("abandonRequests", abandonRequests);
    attrs.add("addRequests", addRequests);
    attrs.add("addResponses", addResponses);
    attrs.add("bindRequests", bindRequests);
    attrs.add("bindResponses", bindResponses);
    attrs.add("compareRequests", compareRequests);
    attrs.add("compareResponses", compareResponses);
    attrs.add("deleteRequests", deleteRequests);
    attrs.add("deleteResponses", deleteResponses);
    attrs.add("extendedRequests", extendedRequests);
    attrs.add("extendedResponses", extendedResponses);
    attrs.add("modifyRequests", modifyRequests);
    attrs.add("modifyResponses", modifyResponses);
    attrs.add("modifyDNRequests", modifyDNRequests);
    attrs.add("modifyDNResponses", modifyDNResponses);
    attrs.add("searchRequests", searchRequests);
    attrs.add("searchOneRequests", searchOneRequests);
    attrs.add("searchSubRequests", searchSubRequests);
    attrs.add("searchResultEntries", searchResultEntries);
    attrs.add("searchResultReferences", searchResultReferences);
    attrs.add("searchResultsDone", searchResultsDone);
    attrs.add("unbindRequests", unbindRequests);

    // adds
    attrs.add("ds-mon-add-operations-total-count", addOperationCount);
    attrs.add("ds-mon-resident-time-add-operations-total-time", addOperationTime);

    // search
    attrs.add("ds-mon-search-operations-total-count", searchOperationCount);
    attrs.add("ds-mon-resident-time-search-operations-total-time", searchOperationTime);

    // bind
    attrs.add("ds-mon-bind-operations-total-count", bindOperationCount);
    attrs.add("ds-mon-resident-time-bind-operations-total-time", bindOperationTime);

    // unbind
    attrs.add("ds-mon-unbind-operations-total-count", unbindOperationCount);
    attrs.add("ds-mon-resident-time-unbind-operations-total-time", unbindOperationTime);

    // compare
    attrs.add("ds-mon-compare-operations-total-count", compOperationCount);
    attrs.add("ds-mon-resident-time-compare-operations-total-time", compOperationTime);

    // del
    attrs.add("ds-mon-delete-operations-total-count", delOperationCount);
    attrs.add("ds-mon-resident-time-delete-operations-total-time", delOperationTime);

    // mod
    attrs.add("ds-mon-mod-operations-total-count", modOperationCount);
    attrs.add("ds-mon-resident-time-mod-operations-total-time", modOperationTime);

    // moddn
    attrs.add("ds-mon-moddn-operations-total-count", moddnOperationCount);
    attrs.add("ds-mon-resident-time-moddn-operations-total-time", moddnOperationTime);

    // abandon
    attrs.add("ds-mon-abandon-operations-total-count", abandonOperationCount);
    attrs.add("ds-mon-resident-time-abandon-operations-total-time", abandonOperationTime);

    // extended
    attrs.add("ds-mon-extended-operations-total-count", extOperationCount);
    attrs.add("ds-mon-resident-time-extended-operations-total-time", extOperationTime);

    return attrs;
  }

  /** Clears any statistical information collected to this point. */
  public void clearStatistics()
  {
      abandonRequests.set(0);
      addRequests.set(0);
      addResponses.set(0);
      bindRequests.set(0);
      bindResponses.set(0);
      bytesRead.set(0);
      bytesWritten.set(0);
      compareRequests.set(0);
      compareResponses.set(0);
      connectionsClosed.set(0);
      connectionsEstablished.set(0);
      deleteRequests.set(0);
      deleteResponses.set(0);
      extendedRequests.set(0);
      extendedResponses.set(0);
      messagesRead.set(0);
      messagesWritten.set(0);
      modifyRequests.set(0);
      modifyResponses.set(0);
      modifyDNRequests.set(0);
      modifyDNResponses.set(0);
      operationsAbandoned.set(0);
      operationsCompleted.set(0);
      operationsInitiated.set(0);
      searchRequests.set(0);
      searchOneRequests.set(0);
      searchSubRequests.set(0);
      searchResultEntries.set(0);
      searchResultReferences.set(0);
      searchResultsDone.set(0);
      unbindRequests.set(0);

      addOperationCount.set(0);
      addOperationTime.set(0);
      searchOperationCount.set(0);
      searchOperationTime.set(0);
      delOperationCount.set(0);
      delOperationTime.set(0);
      bindOperationCount.set(0);
      bindOperationTime.set(0);
      unbindOperationCount.set(0);
      unbindOperationTime.set(0);
      compOperationCount.set(0);
      compOperationTime.set(0);
      modOperationCount.set(0);
      modOperationTime.set(0);
      moddnOperationCount.set(0);
      moddnOperationTime.set(0);
      abandonOperationCount.set(0);
      abandonOperationTime.set(0);
      extOperationCount.set(0);
      extOperationTime.set(0);
  }

  /**
   * Updates the appropriate set of counters to indicate that a new
   * connection has been established.
   */
  public void updateConnect()
  {
    connectionsEstablished.getAndIncrement();
  }

  /** Updates the appropriate set of counters to indicate that a connection has been closed. */
  public void updateDisconnect()
  {
      connectionsClosed.getAndIncrement();
  }

  /**
   * Updates the appropriate set of counters to indicate that the
   * specified number of bytes have been read by the client.
   *
   * @param bytesRead
   *          The number of bytes read by the client.
   */
  public void updateBytesRead(int bytesRead)
  {
     this.bytesRead.getAndAdd(bytesRead);
  }

  /**
   * Updates the appropriate set of counters to indicate that the
   * specified number of bytes have been written to the client.
   *
   * @param bytesWritten
   *          The number of bytes written to the client.
   */
  public void updateBytesWritten(int bytesWritten)
  {
     this.bytesWritten.getAndAdd(bytesWritten);
  }

  /**
   * Updates the appropriate set of counters based on the provided
   * message that has been read from the client.
   *
   * @param message
   *          The message that was read from the client.
   */
  public void updateMessageRead(LDAPMessage message)
  {
      messagesRead.getAndIncrement();
      operationsInitiated.getAndIncrement();

      switch (message.getProtocolOp().getType())
      {
      case OP_TYPE_ABANDON_REQUEST:
        abandonRequests.getAndIncrement();
        break;
      case OP_TYPE_ADD_REQUEST:
        addRequests.getAndIncrement();
        break;
      case OP_TYPE_BIND_REQUEST:
        bindRequests.getAndIncrement();
        break;
      case OP_TYPE_COMPARE_REQUEST:
        compareRequests.getAndIncrement();
        break;
      case OP_TYPE_DELETE_REQUEST:
        deleteRequests.getAndIncrement();
        break;
      case OP_TYPE_EXTENDED_REQUEST:
        extendedRequests.getAndIncrement();
        break;
      case OP_TYPE_MODIFY_REQUEST:
        modifyRequests.getAndIncrement();
        break;
      case OP_TYPE_MODIFY_DN_REQUEST:
        modifyDNRequests.getAndIncrement();
        break;
      case OP_TYPE_SEARCH_REQUEST:
        searchRequests.getAndIncrement();
        SearchRequestProtocolOp s = (SearchRequestProtocolOp)message
            .getProtocolOp();
        switch (s.getScope().asEnum())
        {
        case BASE_OBJECT:
            // we don't count base object searches as
            // this value can be derived from the others
            break;
        case SINGLE_LEVEL:
            searchOneRequests.getAndIncrement();
            break;
        case WHOLE_SUBTREE:
            searchSubRequests.getAndIncrement();
            break;
        default:
            break;
        }
        break;
      case OP_TYPE_UNBIND_REQUEST:
        unbindRequests.getAndIncrement();
        break;
      }
  }

  /**
   * Updates the appropriate set of counters based on the provided
   * message that has been written to the client.
   *
   * @param message
   *          The message that was written to the client.
   */
  public void updateMessageWritten(LDAPMessage message)
  {
      messagesWritten.getAndIncrement();

      switch (message.getProtocolOp().getType())
      {
      case OP_TYPE_ADD_RESPONSE:
        addResponses.getAndIncrement();
        operationsCompleted.getAndIncrement();
        break;
      case OP_TYPE_BIND_RESPONSE:
        bindResponses.getAndIncrement();
        operationsCompleted.getAndIncrement();
        break;
      case OP_TYPE_COMPARE_RESPONSE:
        compareResponses.getAndIncrement();
        operationsCompleted.getAndIncrement();
        break;
      case OP_TYPE_DELETE_RESPONSE:
        deleteResponses.getAndIncrement();
        operationsCompleted.getAndIncrement();
        break;
      case OP_TYPE_EXTENDED_RESPONSE:
        extendedResponses.getAndIncrement();

        // We don't want to include unsolicited notifications as
        // "completed" operations.
        if (message.getMessageID() > 0)
        {
          operationsCompleted.getAndIncrement();
        }
        break;
      case OP_TYPE_MODIFY_RESPONSE:
        modifyResponses.getAndIncrement();
        operationsCompleted.getAndIncrement();
        break;
      case OP_TYPE_MODIFY_DN_RESPONSE:
        modifyDNResponses.getAndIncrement();
        operationsCompleted.getAndIncrement();
        break;
      case OP_TYPE_SEARCH_RESULT_ENTRY:
        searchResultEntries.getAndIncrement();
        break;
      case OP_TYPE_SEARCH_RESULT_REFERENCE:
        searchResultReferences.getAndIncrement();
        break;
      case OP_TYPE_SEARCH_RESULT_DONE:
        searchResultsDone.getAndIncrement();
        operationsCompleted.getAndIncrement();
        break;
      }
  }

  /**
   * Updates the appropriate set of counters to indicate that an
   * operation was abandoned without sending a response to the client.
   */
  public void updateAbandonedOperation()
  {
      operationsAbandoned.getAndIncrement();
  }

  /**
   * Retrieves the number of client connections that have been
   * established.
   *
   * @return The number of client connections that have been
   *         established.
   */
  public long getConnectionsEstablished()
  {
    return connectionsEstablished.get();
  }

  /**
   * Retrieves the number of client connections that have been closed.
   *
   * @return The number of client connections that have been closed.
   */
  public long getConnectionsClosed()
  {
    return connectionsClosed.get();
  }

  /**
   * Retrieves the number of bytes that have been received from clients.
   *
   * @return The number of bytes that have been received from clients.
   */
  public long getBytesRead()
  {
      return bytesRead.get();
  }

  /**
   * Retrieves the number of bytes that have been written to clients.
   *
   * @return The number of bytes that have been written to clients.
   */
  public long getBytesWritten()
  {
      return bytesWritten.get();
  }

  /**
   * Retrieves the number of LDAP messages that have been received from
   * clients.
   *
   * @return The number of LDAP messages that have been received from
   *         clients.
   */
  public long getMessagesRead()
  {
    return messagesRead.get();
  }

  /**
   * Retrieves the number of LDAP messages that have been written to
   * clients.
   *
   * @return The number of LDAP messages that have been written to
   *         clients.
   */
  public long getMessagesWritten()
  {
   return messagesWritten.get();
  }

  /**
   * Retrieves the number of operations that have been initiated by
   * clients.
   *
   * @return The number of operations that have been initiated by
   *         clients.
   */
  public long getOperationsInitiated()
  {
    return operationsInitiated.get();
  }

  /**
   * Retrieves the number of operations for which the server has
   * completed processing.
   *
   * @return The number of operations for which the server has completed
   *         processing.
   */
  public long getOperationsCompleted()
  {
      return operationsCompleted.get();
  }

  /**
   * Retrieves the number of operations that have been abandoned by
   * clients.
   *
   * @return The number of operations that have been abandoned by
   *         clients.
   */
  public long getOperationsAbandoned()
  {
      return operationsAbandoned.get();
  }

  /**
   * Retrieves the number of abandon requests that have been received.
   *
   * @return The number of abandon requests that have been received.
   */
  public long getAbandonRequests()
  {
      return abandonRequests.get();
  }

  /**
   * Retrieves the number of add requests that have been received.
   *
   * @return The number of add requests that have been received.
   */
  public long getAddRequests()
  {
      return addRequests.get();
  }

  /**
   * Retrieves the number of add responses that have been sent.
   *
   * @return The number of add responses that have been sent.
   */
  public long getAddResponses()
  {
      return addResponses.get();
  }

  /**
   * Retrieves the number of bind requests that have been received.
   *
   * @return The number of bind requests that have been received.
   */
  public long getBindRequests()
  {
      return bindRequests.get();
  }

  /**
   * Retrieves the number of bind responses that have been sent.
   *
   * @return The number of bind responses that have been sent.
   */
  public long getBindResponses()
  {
      return bindResponses.get();
  }

  /**
   * Retrieves the number of compare requests that have been received.
   *
   * @return The number of compare requests that have been received.
   */
  public long getCompareRequests()
  {
      return compareRequests.get();
  }

  /**
   * Retrieves the number of compare responses that have been sent.
   *
   * @return The number of compare responses that have been sent.
   */
  public long getCompareResponses()
  {
      return compareResponses.get();
  }

  /**
   * Retrieves the number of delete requests that have been received.
   *
   * @return The number of delete requests that have been received.
   */
  public long getDeleteRequests()
  {
      return deleteRequests.get();
  }

  /**
   * Retrieves the number of delete responses that have been sent.
   *
   * @return The number of delete responses that have been sent.
   */
  public long getDeleteResponses()
  {
      return deleteResponses.get();
  }

  /**
   * Retrieves the number of extended requests that have been received.
   *
   * @return The number of extended requests that have been received.
   */
  public long getExtendedRequests()
  {
      return extendedRequests.get();
  }

  /**
   * Retrieves the number of extended responses that have been sent.
   *
   * @return The number of extended responses that have been sent.
   */
  public long getExtendedResponses()
  {
      return extendedResponses.get();
  }

  /**
   * Retrieves the number of modify requests that have been received.
   *
   * @return The number of modify requests that have been received.
   */
  public long getModifyRequests()
  {
      return modifyRequests.get();
  }

  /**
   * Retrieves the number of modify responses that have been sent.
   *
   * @return The number of modify responses that have been sent.
   */
  public long getModifyResponses()
  {
      return modifyResponses.get();
  }

  /**
   * Retrieves the number of modify DN requests that have been received.
   *
   * @return The number of modify DN requests that have been received.
   */
  public long getModifyDNRequests()
  {
      return modifyDNRequests.get();
  }

  /**
   * Retrieves the number of modify DN responses that have been sent.
   *
   * @return The number of modify DN responses that have been sent.
   */
  public long getModifyDNResponses()
  {
      return modifyDNResponses.get();
  }

  /**
   * Retrieves the number of search requests that have been received.
   *
   * @return The number of search requests that have been received.
   */
  public long getSearchRequests()
  {
      return searchRequests.get();
  }

  /**
   * Retrieves the number of one-level search requests that have been received.
   *
   * @return The number of one-level search requests that have been received.
   */
  public long getSearchOneRequests()
  {
      return searchOneRequests.get();
  }

  /**
   * Retrieves the number of subtree search requests that have been received.
   *
   * @return The number of subtree search requests that have been received.
   */
  public long getSearchSubRequests()
  {
      return searchSubRequests.get();
  }

  /**
   * Retrieves the number of search result entries that have been sent.
   *
   * @return The number of search result entries that have been sent.
   */
  public long getSearchResultEntries()
  {
      return searchResultEntries.get();
  }

  /**
   * Retrieves the number of search result references that have been
   * sent.
   *
   * @return The number of search result references that have been sent.
   */
  public long getSearchResultReferences()
  {
      return searchResultReferences.get();
  }

  /**
   * Retrieves the number of search result done messages that have been
   * sent.
   *
   * @return The number of search result done messages that have been
   *         sent.
   */
  public long getSearchResultsDone()
  {
      return searchResultsDone.get();
  }

  /**
   * Retrieves the number of unbind requests that have been received.
   *
   * @return The number of unbind requests that have been received.
   */
  public long getUnbindRequests()
  {
      return unbindRequests.get();
  }

  /**
   * Update the operation counters and times depending on the OperationType.
   * @param type of the operation.
   * @param time of the operation execution.
   */

  public void updateOperationMonitoringData(OperationType type, long time) {
      if (type.equals(OperationType.ADD)) {
          addOperationCount.getAndIncrement();
          addOperationTime.getAndAdd(time);
      }
      else if (type.equals(OperationType.SEARCH)) {
          searchOperationCount.getAndIncrement();
          searchOperationTime.getAndAdd(time);
      }
      else if (type.equals(OperationType.ABANDON)) {
          abandonOperationCount.getAndIncrement();
          abandonOperationTime.getAndAdd(time);
      }
      else if (type.equals(OperationType.BIND)) {
          bindOperationCount.getAndIncrement();
          bindOperationTime.getAndAdd(time);
      }
      else if (type.equals(OperationType.UNBIND)) {
          unbindOperationCount.getAndIncrement();
          unbindOperationTime.getAndAdd(time);
      }
      else if (type.equals(OperationType.COMPARE)) {
          compOperationCount.getAndIncrement();
          compOperationTime.getAndAdd(time);
      }
      else if (type.equals(OperationType.DELETE)) {
          delOperationCount.getAndIncrement();
          delOperationTime.getAndAdd(time);
      }
      else if (type.equals(OperationType.EXTENDED)) {
          extOperationCount.getAndIncrement();
          extOperationTime.getAndAdd(time);
      }
      else if (type.equals(OperationType.MODIFY)) {
          modOperationCount.getAndIncrement();
          modOperationTime.getAndAdd(time);
      }
      else if (type.equals(OperationType.MODIFY_DN)) {
          moddnOperationCount.getAndIncrement();
          moddnOperationTime.getAndAdd(time);
      }
  }
}
