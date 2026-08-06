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
 * Portions Copyright 2026 3A Systems, LLC
 */
package org.opends.server.protocols.ldap;

import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.util.ServerConstants.*;

import java.util.concurrent.atomic.LongAdder;

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

  // The statistics maintained by this class. These counters are updated for
  // every LDAP message on every connection of the handler, so they use
  // LongAdder: under concurrency striped cells avoid all worker threads
  // CAS-ing the same cache line, and reads (monitoring only) pay the cost.
  private final LongAdder abandonRequests = new LongAdder();
  private final LongAdder addRequests = new LongAdder();
  private final LongAdder bindRequests = new LongAdder();
  private final LongAdder addResponses = new LongAdder();
  private final LongAdder bindResponses = new LongAdder();
  private final LongAdder bytesRead = new LongAdder();
  private final LongAdder bytesWritten = new LongAdder();
  private final LongAdder compareRequests = new LongAdder();
  private final LongAdder compareResponses = new LongAdder();
  private final LongAdder connectionsClosed = new LongAdder();
  private final LongAdder connectionsEstablished = new LongAdder();
  private final LongAdder deleteRequests = new LongAdder();
  private final LongAdder deleteResponses = new LongAdder();
  private final LongAdder extendedRequests = new LongAdder();
  private final LongAdder extendedResponses = new LongAdder();
  private final LongAdder messagesRead = new LongAdder();
  private final LongAdder messagesWritten = new LongAdder();
  private final LongAdder modifyRequests = new LongAdder();
  private final LongAdder modifyResponses = new LongAdder();
  private final LongAdder modifyDNRequests = new LongAdder();
  private final LongAdder modifyDNResponses = new LongAdder();
  private final LongAdder operationsAbandoned = new LongAdder();
  private final LongAdder operationsCompleted = new LongAdder();
  private final LongAdder operationsInitiated = new LongAdder();
  private final LongAdder searchRequests = new LongAdder();
  private final LongAdder searchOneRequests = new LongAdder();
  private final LongAdder searchSubRequests = new LongAdder();
  private final LongAdder searchResultEntries = new LongAdder();
  private final LongAdder searchResultReferences = new LongAdder();
  private final LongAdder searchResultsDone = new LongAdder();
  private final LongAdder unbindRequests = new LongAdder();


  /** The instance name for this monitor provider instance. */
  private final String instanceName;

  // Monitor Objects : for Operations (count and time)
  private final LongAdder addOperationCount = new LongAdder();
  private final LongAdder addOperationTime = new LongAdder();
  private final LongAdder searchOperationCount = new LongAdder();
  private final LongAdder searchOperationTime = new LongAdder();
  private final LongAdder delOperationCount = new LongAdder();
  private final LongAdder delOperationTime = new LongAdder();
  private final LongAdder bindOperationCount = new LongAdder();
  private final LongAdder bindOperationTime = new LongAdder();
  private final LongAdder unbindOperationCount = new LongAdder();
  private final LongAdder unbindOperationTime = new LongAdder();
  private final LongAdder compOperationCount = new LongAdder();
  private final LongAdder compOperationTime = new LongAdder();
  private final LongAdder modOperationCount = new LongAdder();
  private final LongAdder modOperationTime = new LongAdder();
  private final LongAdder moddnOperationCount = new LongAdder();
  private final LongAdder moddnOperationTime = new LongAdder();
  private final LongAdder abandonOperationCount = new LongAdder();
  private final LongAdder abandonOperationTime = new LongAdder();
  private final LongAdder extOperationCount = new LongAdder();
  private final LongAdder extOperationTime = new LongAdder();

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
    return DirectoryServer.getInstance().getServerContext().getSchema().getObjectClass(OC_MONITOR_CONNHANDLERSTATS);
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
    attrs.add("connectionsEstablished", connectionsEstablished.sum());
    attrs.add("connectionsClosed", connectionsClosed.sum());
    attrs.add("bytesRead", bytesRead.sum());
    attrs.add("bytesWritten", bytesWritten.sum());
    attrs.add("ldapMessagesRead", messagesRead.sum());
    attrs.add("ldapMessagesWritten", messagesWritten.sum());
    attrs.add("operationsAbandoned", operationsAbandoned.sum());
    attrs.add("operationsInitiated", operationsInitiated.sum());
    attrs.add("operationsCompleted", operationsCompleted.sum());
    attrs.add("abandonRequests", abandonRequests.sum());
    attrs.add("addRequests", addRequests.sum());
    attrs.add("addResponses", addResponses.sum());
    attrs.add("bindRequests", bindRequests.sum());
    attrs.add("bindResponses", bindResponses.sum());
    attrs.add("compareRequests", compareRequests.sum());
    attrs.add("compareResponses", compareResponses.sum());
    attrs.add("deleteRequests", deleteRequests.sum());
    attrs.add("deleteResponses", deleteResponses.sum());
    attrs.add("extendedRequests", extendedRequests.sum());
    attrs.add("extendedResponses", extendedResponses.sum());
    attrs.add("modifyRequests", modifyRequests.sum());
    attrs.add("modifyResponses", modifyResponses.sum());
    attrs.add("modifyDNRequests", modifyDNRequests.sum());
    attrs.add("modifyDNResponses", modifyDNResponses.sum());
    attrs.add("searchRequests", searchRequests.sum());
    attrs.add("searchOneRequests", searchOneRequests.sum());
    attrs.add("searchSubRequests", searchSubRequests.sum());
    attrs.add("searchResultEntries", searchResultEntries.sum());
    attrs.add("searchResultReferences", searchResultReferences.sum());
    attrs.add("searchResultsDone", searchResultsDone.sum());
    attrs.add("unbindRequests", unbindRequests.sum());

    // adds
    attrs.add("ds-mon-add-operations-total-count", addOperationCount.sum());
    attrs.add("ds-mon-resident-time-add-operations-total-time", addOperationTime.sum());

    // search
    attrs.add("ds-mon-search-operations-total-count", searchOperationCount.sum());
    attrs.add("ds-mon-resident-time-search-operations-total-time", searchOperationTime.sum());

    // bind
    attrs.add("ds-mon-bind-operations-total-count", bindOperationCount.sum());
    attrs.add("ds-mon-resident-time-bind-operations-total-time", bindOperationTime.sum());

    // unbind
    attrs.add("ds-mon-unbind-operations-total-count", unbindOperationCount.sum());
    attrs.add("ds-mon-resident-time-unbind-operations-total-time", unbindOperationTime.sum());

    // compare
    attrs.add("ds-mon-compare-operations-total-count", compOperationCount.sum());
    attrs.add("ds-mon-resident-time-compare-operations-total-time", compOperationTime.sum());

    // del
    attrs.add("ds-mon-delete-operations-total-count", delOperationCount.sum());
    attrs.add("ds-mon-resident-time-delete-operations-total-time", delOperationTime.sum());

    // mod
    attrs.add("ds-mon-mod-operations-total-count", modOperationCount.sum());
    attrs.add("ds-mon-resident-time-mod-operations-total-time", modOperationTime.sum());

    // moddn
    attrs.add("ds-mon-moddn-operations-total-count", moddnOperationCount.sum());
    attrs.add("ds-mon-resident-time-moddn-operations-total-time", moddnOperationTime.sum());

    // abandon
    attrs.add("ds-mon-abandon-operations-total-count", abandonOperationCount.sum());
    attrs.add("ds-mon-resident-time-abandon-operations-total-time", abandonOperationTime.sum());

    // extended
    attrs.add("ds-mon-extended-operations-total-count", extOperationCount.sum());
    attrs.add("ds-mon-resident-time-extended-operations-total-time", extOperationTime.sum());

    return attrs;
  }

  /** Clears any statistical information collected to this point. */
  public void clearStatistics()
  {
      abandonRequests.reset();
      addRequests.reset();
      addResponses.reset();
      bindRequests.reset();
      bindResponses.reset();
      bytesRead.reset();
      bytesWritten.reset();
      compareRequests.reset();
      compareResponses.reset();
      connectionsClosed.reset();
      connectionsEstablished.reset();
      deleteRequests.reset();
      deleteResponses.reset();
      extendedRequests.reset();
      extendedResponses.reset();
      messagesRead.reset();
      messagesWritten.reset();
      modifyRequests.reset();
      modifyResponses.reset();
      modifyDNRequests.reset();
      modifyDNResponses.reset();
      operationsAbandoned.reset();
      operationsCompleted.reset();
      operationsInitiated.reset();
      searchRequests.reset();
      searchOneRequests.reset();
      searchSubRequests.reset();
      searchResultEntries.reset();
      searchResultReferences.reset();
      searchResultsDone.reset();
      unbindRequests.reset();

      addOperationCount.reset();
      addOperationTime.reset();
      searchOperationCount.reset();
      searchOperationTime.reset();
      delOperationCount.reset();
      delOperationTime.reset();
      bindOperationCount.reset();
      bindOperationTime.reset();
      unbindOperationCount.reset();
      unbindOperationTime.reset();
      compOperationCount.reset();
      compOperationTime.reset();
      modOperationCount.reset();
      modOperationTime.reset();
      moddnOperationCount.reset();
      moddnOperationTime.reset();
      abandonOperationCount.reset();
      abandonOperationTime.reset();
      extOperationCount.reset();
      extOperationTime.reset();
  }

  /**
   * Updates the appropriate set of counters to indicate that a new
   * connection has been established.
   */
  public void updateConnect()
  {
    connectionsEstablished.increment();
  }

  /** Updates the appropriate set of counters to indicate that a connection has been closed. */
  public void updateDisconnect()
  {
      connectionsClosed.increment();
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
     this.bytesRead.add(bytesRead);
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
     this.bytesWritten.add(bytesWritten);
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
      messagesRead.increment();
      operationsInitiated.increment();

      switch (message.getProtocolOp().getType())
      {
      case OP_TYPE_ABANDON_REQUEST:
        abandonRequests.increment();
        break;
      case OP_TYPE_ADD_REQUEST:
        addRequests.increment();
        break;
      case OP_TYPE_BIND_REQUEST:
        bindRequests.increment();
        break;
      case OP_TYPE_COMPARE_REQUEST:
        compareRequests.increment();
        break;
      case OP_TYPE_DELETE_REQUEST:
        deleteRequests.increment();
        break;
      case OP_TYPE_EXTENDED_REQUEST:
        extendedRequests.increment();
        break;
      case OP_TYPE_MODIFY_REQUEST:
        modifyRequests.increment();
        break;
      case OP_TYPE_MODIFY_DN_REQUEST:
        modifyDNRequests.increment();
        break;
      case OP_TYPE_SEARCH_REQUEST:
        searchRequests.increment();
        SearchRequestProtocolOp s = (SearchRequestProtocolOp)message
            .getProtocolOp();
        switch (s.getScope().asEnum())
        {
        case BASE_OBJECT:
            // we don't count base object searches as
            // this value can be derived from the others
            break;
        case SINGLE_LEVEL:
            searchOneRequests.increment();
            break;
        case WHOLE_SUBTREE:
            searchSubRequests.increment();
            break;
        default:
            break;
        }
        break;
      case OP_TYPE_UNBIND_REQUEST:
        unbindRequests.increment();
        break;
      }
  }

  /**
   * Updates the appropriate set of counters based on the provided
   * message that has been written to the client.
   *
   * @param messageType
   *          The message type that was written to the client.
   * @param messageId
   *          The message id that was written to the client
   */
  public void updateMessageWritten(byte messageType, int messageId) {
      messagesWritten.increment();
      switch (messageType)
      {
      case OP_TYPE_ADD_RESPONSE:
        addResponses.increment();
        operationsCompleted.increment();
        break;
      case OP_TYPE_BIND_RESPONSE:
        bindResponses.increment();
        operationsCompleted.increment();
        break;
      case OP_TYPE_COMPARE_RESPONSE:
        compareResponses.increment();
        operationsCompleted.increment();
        break;
      case OP_TYPE_DELETE_RESPONSE:
        deleteResponses.increment();
        operationsCompleted.increment();
        break;
      case OP_TYPE_EXTENDED_RESPONSE:
        extendedResponses.increment();

        // We don't want to include unsolicited notifications as
        // "completed" operations.
        if (messageId > 0)
        {
          operationsCompleted.increment();
        }
        break;
      case OP_TYPE_MODIFY_RESPONSE:
        modifyResponses.increment();
        operationsCompleted.increment();
        break;
      case OP_TYPE_MODIFY_DN_RESPONSE:
        modifyDNResponses.increment();
        operationsCompleted.increment();
        break;
      case OP_TYPE_SEARCH_RESULT_ENTRY:
        searchResultEntries.increment();
        break;
      case OP_TYPE_SEARCH_RESULT_REFERENCE:
        searchResultReferences.increment();
        break;
      case OP_TYPE_SEARCH_RESULT_DONE:
        searchResultsDone.increment();
        operationsCompleted.increment();
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
      updateMessageWritten(message.getProtocolOp().getType(), message.getMessageID());
  }

  /**
   * Updates the appropriate set of counters to indicate that an
   * operation was abandoned without sending a response to the client.
   */
  public void updateAbandonedOperation()
  {
      operationsAbandoned.increment();
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
    return connectionsEstablished.sum();
  }

  /**
   * Retrieves the number of client connections that have been closed.
   *
   * @return The number of client connections that have been closed.
   */
  public long getConnectionsClosed()
  {
    return connectionsClosed.sum();
  }

  /**
   * Retrieves the number of bytes that have been received from clients.
   *
   * @return The number of bytes that have been received from clients.
   */
  public long getBytesRead()
  {
      return bytesRead.sum();
  }

  /**
   * Retrieves the number of bytes that have been written to clients.
   *
   * @return The number of bytes that have been written to clients.
   */
  public long getBytesWritten()
  {
      return bytesWritten.sum();
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
    return messagesRead.sum();
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
   return messagesWritten.sum();
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
    return operationsInitiated.sum();
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
      return operationsCompleted.sum();
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
      return operationsAbandoned.sum();
  }

  /**
   * Retrieves the number of abandon requests that have been received.
   *
   * @return The number of abandon requests that have been received.
   */
  public long getAbandonRequests()
  {
      return abandonRequests.sum();
  }

  /**
   * Retrieves the number of add requests that have been received.
   *
   * @return The number of add requests that have been received.
   */
  public long getAddRequests()
  {
      return addRequests.sum();
  }

  /**
   * Retrieves the number of add responses that have been sent.
   *
   * @return The number of add responses that have been sent.
   */
  public long getAddResponses()
  {
      return addResponses.sum();
  }

  /**
   * Retrieves the number of bind requests that have been received.
   *
   * @return The number of bind requests that have been received.
   */
  public long getBindRequests()
  {
      return bindRequests.sum();
  }

  /**
   * Retrieves the number of bind responses that have been sent.
   *
   * @return The number of bind responses that have been sent.
   */
  public long getBindResponses()
  {
      return bindResponses.sum();
  }

  /**
   * Retrieves the number of compare requests that have been received.
   *
   * @return The number of compare requests that have been received.
   */
  public long getCompareRequests()
  {
      return compareRequests.sum();
  }

  /**
   * Retrieves the number of compare responses that have been sent.
   *
   * @return The number of compare responses that have been sent.
   */
  public long getCompareResponses()
  {
      return compareResponses.sum();
  }

  /**
   * Retrieves the number of delete requests that have been received.
   *
   * @return The number of delete requests that have been received.
   */
  public long getDeleteRequests()
  {
      return deleteRequests.sum();
  }

  /**
   * Retrieves the number of delete responses that have been sent.
   *
   * @return The number of delete responses that have been sent.
   */
  public long getDeleteResponses()
  {
      return deleteResponses.sum();
  }

  /**
   * Retrieves the number of extended requests that have been received.
   *
   * @return The number of extended requests that have been received.
   */
  public long getExtendedRequests()
  {
      return extendedRequests.sum();
  }

  /**
   * Retrieves the number of extended responses that have been sent.
   *
   * @return The number of extended responses that have been sent.
   */
  public long getExtendedResponses()
  {
      return extendedResponses.sum();
  }

  /**
   * Retrieves the number of modify requests that have been received.
   *
   * @return The number of modify requests that have been received.
   */
  public long getModifyRequests()
  {
      return modifyRequests.sum();
  }

  /**
   * Retrieves the number of modify responses that have been sent.
   *
   * @return The number of modify responses that have been sent.
   */
  public long getModifyResponses()
  {
      return modifyResponses.sum();
  }

  /**
   * Retrieves the number of modify DN requests that have been received.
   *
   * @return The number of modify DN requests that have been received.
   */
  public long getModifyDNRequests()
  {
      return modifyDNRequests.sum();
  }

  /**
   * Retrieves the number of modify DN responses that have been sent.
   *
   * @return The number of modify DN responses that have been sent.
   */
  public long getModifyDNResponses()
  {
      return modifyDNResponses.sum();
  }

  /**
   * Retrieves the number of search requests that have been received.
   *
   * @return The number of search requests that have been received.
   */
  public long getSearchRequests()
  {
      return searchRequests.sum();
  }

  /**
   * Retrieves the number of one-level search requests that have been received.
   *
   * @return The number of one-level search requests that have been received.
   */
  public long getSearchOneRequests()
  {
      return searchOneRequests.sum();
  }

  /**
   * Retrieves the number of subtree search requests that have been received.
   *
   * @return The number of subtree search requests that have been received.
   */
  public long getSearchSubRequests()
  {
      return searchSubRequests.sum();
  }

  /**
   * Retrieves the number of search result entries that have been sent.
   *
   * @return The number of search result entries that have been sent.
   */
  public long getSearchResultEntries()
  {
      return searchResultEntries.sum();
  }

  /**
   * Retrieves the number of search result references that have been
   * sent.
   *
   * @return The number of search result references that have been sent.
   */
  public long getSearchResultReferences()
  {
      return searchResultReferences.sum();
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
      return searchResultsDone.sum();
  }

  /**
   * Retrieves the number of unbind requests that have been received.
   *
   * @return The number of unbind requests that have been received.
   */
  public long getUnbindRequests()
  {
      return unbindRequests.sum();
  }

  /**
   * Update the operation counters and times depending on the OperationType.
   * @param type of the operation.
   * @param time of the operation execution.
   */

  public void updateOperationMonitoringData(OperationType type, long time) {
      if (type.equals(OperationType.ADD)) {
          addOperationCount.increment();
          addOperationTime.add(time);
      }
      else if (type.equals(OperationType.SEARCH)) {
          searchOperationCount.increment();
          searchOperationTime.add(time);
      }
      else if (type.equals(OperationType.ABANDON)) {
          abandonOperationCount.increment();
          abandonOperationTime.add(time);
      }
      else if (type.equals(OperationType.BIND)) {
          bindOperationCount.increment();
          bindOperationTime.add(time);
      }
      else if (type.equals(OperationType.UNBIND)) {
          unbindOperationCount.increment();
          unbindOperationTime.add(time);
      }
      else if (type.equals(OperationType.COMPARE)) {
          compOperationCount.increment();
          compOperationTime.add(time);
      }
      else if (type.equals(OperationType.DELETE)) {
          delOperationCount.increment();
          delOperationTime.add(time);
      }
      else if (type.equals(OperationType.EXTENDED)) {
          extOperationCount.increment();
          extOperationTime.add(time);
      }
      else if (type.equals(OperationType.MODIFY)) {
          modOperationCount.increment();
          modOperationTime.add(time);
      }
      else if (type.equals(OperationType.MODIFY_DN)) {
          moddnOperationCount.increment();
          moddnOperationTime.add(time);
      }
  }
}
