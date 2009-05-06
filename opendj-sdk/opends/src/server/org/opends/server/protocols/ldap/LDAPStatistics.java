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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */
package org.opends.server.protocols.ldap;



import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.types.OperationType.*;

import java.util.ArrayList;

import org.opends.messages.Message;
import org.opends.server.admin.std.server.MonitorProviderCfg;
import org.opends.server.api.MonitorProvider;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.monitors.OperationMonitor;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValues;



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
  private long abandonRequests;
  private long addRequests;
  private long addResponses;
  private long bindRequests;
  private long bindResponses;
  private long bytesRead;
  private long bytesWritten;
  private long compareRequests;
  private long compareResponses;
  private long connectionsClosed;
  private long connectionsEstablished;
  private long deleteRequests;
  private long deleteResponses;
  private long extendedRequests;
  private long extendedResponses;
  private long messagesRead;
  private long messagesWritten;
  private long modifyRequests;
  private long modifyResponses;
  private long modifyDNRequests;
  private long modifyDNResponses;
  private long operationsAbandoned;
  private long operationsCompleted;
  private long operationsInitiated;
  private long searchRequests;
  private long searchResultEntries;
  private long searchResultReferences;
  private long searchResultsDone;
  private long unbindRequests;

  // The parent that should also be updated whenever statistics in this
  // object are updated.
  private final LDAPStatistics parent;

  // The locks used to provide threadsafe access to this class. In this
  // case, read and write refer to the type of LDAP communication, not
  // access to the protected data.
  private final Object abandonLock;
  private final Object connectLock;
  private final Object disconnectLock;
  private final Object readLock;
  private final Object writeLock;

  // The instance name for this monitor provider instance.
  private final String instanceName;

  // Monitor Objects : for Operations.
  private final OperationMonitor addRequestsMonitor =
      OperationMonitor.getOperationMonitor(ADD);
  private final OperationMonitor searchRequestsMonitor =
      OperationMonitor.getOperationMonitor(SEARCH);
  private final OperationMonitor delRequestsMonitor =
      OperationMonitor.getOperationMonitor(DELETE);
  private final OperationMonitor bindRequestsMonitor =
      OperationMonitor.getOperationMonitor(BIND);
  private final OperationMonitor unbindRequestsMonitor =
      OperationMonitor.getOperationMonitor(UNBIND);
  private final OperationMonitor compareRequestsMonitor =
      OperationMonitor.getOperationMonitor(COMPARE);
  private final OperationMonitor modRequestsMonitor =
      OperationMonitor.getOperationMonitor(MODIFY);
  private final OperationMonitor moddnRequestsMonitor =
      OperationMonitor.getOperationMonitor(MODIFY);
  private final OperationMonitor abandonRequestsMonitor =
      OperationMonitor.getOperationMonitor(ABANDON);
  private final OperationMonitor extendedRequestsMonitor =
      OperationMonitor.getOperationMonitor(EXTENDED);



  /**
   * Creates a new instance of this class with no parent.
   *
   * @param instanceName
   *          The name for this monitor provider instance.
   */
  public LDAPStatistics(String instanceName)
  {
    this(instanceName, null);
  }



  /**
   * Creates a new instance of this class with the specified parent.
   *
   * @param instanceName
   *          The name for this monitor provider instance.
   * @param parent
   *          The parent object that should also be updated whenever
   *          this class is updated. It may be null if there should not
   *          be a parent.
   */
  public LDAPStatistics(String instanceName, LDAPStatistics parent)
  {
    super("LDAP Statistics Monitor Provider");

    this.instanceName = instanceName;
    this.parent = parent;

    abandonLock = new Object();
    connectLock = new Object();
    disconnectLock = new Object();
    readLock = new Object();
    writeLock = new Object();

    abandonRequests = 0;
    addRequests = 0;
    addResponses = 0;
    bindRequests = 0;
    bindResponses = 0;
    bytesRead = 0;
    bytesWritten = 0;
    compareRequests = 0;
    compareResponses = 0;
    connectionsClosed = 0;
    connectionsEstablished = 0;
    deleteRequests = 0;
    deleteResponses = 0;
    extendedRequests = 0;
    extendedResponses = 0;
    messagesRead = 0;
    messagesWritten = 0;
    modifyRequests = 0;
    modifyResponses = 0;
    modifyDNRequests = 0;
    modifyDNResponses = 0;
    operationsAbandoned = 0;
    operationsCompleted = 0;
    operationsInitiated = 0;
    searchRequests = 0;
    searchResultEntries = 0;
    searchResultReferences = 0;
    searchResultsDone = 0;
    unbindRequests = 0;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void initializeMonitorProvider(MonitorProviderCfg configuration)
      throws ConfigException
  {
    // Throw an exception, because this monitor is not intended to be
    // dynamically loaded from the configuration. Rather, it should be
    // explicitly created and registered by the LDAP connection handler
    // or an LDAP client connection.
    Message message =
        ERR_LDAP_STATS_INVALID_MONITOR_INITIALIZATION.get(String
            .valueOf(configuration.dn()));
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



  /**
   * Retrieves the length of time in milliseconds that should elapse
   * between calls to the <CODE>updateMonitorData()</CODE> method. A
   * negative or zero return value indicates that the
   * <CODE>updateMonitorData()</CODE> method should not be periodically
   * invoked.
   *
   * @return The length of time in milliseconds that should elapse
   *         between calls to the <CODE>updateMonitorData()</CODE>
   *         method.
   */
  @Override
  public long getUpdateInterval()
  {
    // This monitor should not run periodically.
    return -1;
  }



  /**
   * Performs any processing periodic processing that may be desired to
   * update the information associated with this monitor. Note that
   * best-effort attempts will be made to ensure that calls to this
   * method come <CODE>getUpdateInterval()</CODE> milliseconds apart,
   * but no guarantees will be made.
   */
  @Override
  public void updateMonitorData()
  {
    // No implementation is required since this does not do periodic
    // updates.
  }



  /**
   * Retrieves a set of attributes containing monitor data that should
   * be returned to the client if the corresponding monitor entry is
   * requested.
   *
   * @return A set of attributes containing monitor data that should be
   *         returned to the client if the corresponding monitor entry
   *         is requested.
   */
  @Override
  public ArrayList<Attribute> getMonitorData()
  {
    ArrayList<Attribute> attrs = new ArrayList<Attribute>(29);

    long tmpAbandonRequests;
    long tmpAddRequests;
    long tmpAddResponses;
    long tmpBindRequests;
    long tmpBindResponses;
    long tmpBytesRead;
    long tmpBytesWritten;
    long tmpCompareRequests;
    long tmpCompareResponses;
    long tmpConnectionsClosed;
    long tmpConnectionsEstablished;
    long tmpDeleteRequests;
    long tmpDeleteResponses;
    long tmpExtendedRequests;
    long tmpExtendedResponses;
    long tmpMessagesRead;
    long tmpMessagesWritten;
    long tmpModifyRequests;
    long tmpModifyResponses;
    long tmpModifyDNRequests;
    long tmpModifyDNResponses;
    long tmpOperationsAbandoned;
    long tmpOperationsCompleted;
    long tmpOperationsInitiated;
    long tmpSearchRequests;
    long tmpSearchEntries;
    long tmpSearchReferences;
    long tmpSearchResultsDone;
    long tmpUnbindRequests;

    // Quickly grab the locks and store consistent copies of the
    // information. Note that when grabbing multiple locks, it is
    // essential that they are all acquired in the same order to prevent
    // deadlocks.
    synchronized (abandonLock)
    {
      synchronized (connectLock)
      {
        synchronized (disconnectLock)
        {
          synchronized (writeLock)
          {
            synchronized (readLock)
            {
              tmpAbandonRequests = abandonRequests;
              tmpAddRequests = addRequests;
              tmpAddResponses = addResponses;
              tmpBindRequests = bindRequests;
              tmpBindResponses = bindResponses;
              tmpBytesRead = bytesRead;
              tmpBytesWritten = bytesWritten;
              tmpCompareRequests = compareRequests;
              tmpCompareResponses = compareResponses;
              tmpConnectionsClosed = connectionsClosed;
              tmpConnectionsEstablished = connectionsEstablished;
              tmpDeleteRequests = deleteRequests;
              tmpDeleteResponses = deleteResponses;
              tmpExtendedRequests = extendedRequests;
              tmpExtendedResponses = extendedResponses;
              tmpMessagesRead = messagesRead;
              tmpMessagesWritten = messagesWritten;
              tmpModifyRequests = modifyRequests;
              tmpModifyResponses = modifyResponses;
              tmpModifyDNRequests = modifyDNRequests;
              tmpModifyDNResponses = modifyDNResponses;
              tmpOperationsAbandoned = operationsAbandoned;
              tmpOperationsCompleted = operationsCompleted;
              tmpOperationsInitiated = operationsInitiated;
              tmpSearchRequests = searchRequests;
              tmpSearchEntries = searchResultEntries;
              tmpSearchReferences = searchResultReferences;
              tmpSearchResultsDone = searchResultsDone;
              tmpUnbindRequests = unbindRequests;
            }
          }
        }
      }
    }

    // Construct the list of attributes to return.
    attrs.add(createAttribute("connectionsEstablished", String
        .valueOf(tmpConnectionsEstablished)));
    attrs.add(createAttribute("connectionsClosed", String
        .valueOf(tmpConnectionsClosed)));
    attrs
        .add(createAttribute("bytesRead", String.valueOf(tmpBytesRead)));
    attrs.add(createAttribute("bytesWritten", String
        .valueOf(tmpBytesWritten)));
    attrs.add(createAttribute("ldapMessagesRead", String
        .valueOf(tmpMessagesRead)));
    attrs.add(createAttribute("ldapMessagesWritten", String
        .valueOf(tmpMessagesWritten)));
    attrs.add(createAttribute("operationsAbandoned", String
        .valueOf(tmpOperationsAbandoned)));
    attrs.add(createAttribute("operationsInitiated", String
        .valueOf(tmpOperationsInitiated)));
    attrs.add(createAttribute("operationsCompleted", String
        .valueOf(tmpOperationsCompleted)));
    attrs.add(createAttribute("abandonRequests", String
        .valueOf(tmpAbandonRequests)));
    attrs.add(createAttribute("addRequests", String
        .valueOf(tmpAddRequests)));
    attrs.add(createAttribute("addResponses", String
        .valueOf(tmpAddResponses)));
    attrs.add(createAttribute("bindRequests", String
        .valueOf(tmpBindRequests)));
    attrs.add(createAttribute("bindResponses", String
        .valueOf(tmpBindResponses)));
    attrs.add(createAttribute("compareRequests", String
        .valueOf(tmpCompareRequests)));
    attrs.add(createAttribute("compareResponses", String
        .valueOf(tmpCompareResponses)));
    attrs.add(createAttribute("deleteRequests", String
        .valueOf(tmpDeleteRequests)));
    attrs.add(createAttribute("deleteResponses", String
        .valueOf(tmpDeleteResponses)));
    attrs.add(createAttribute("extendedRequests", String
        .valueOf(tmpExtendedRequests)));
    attrs.add(createAttribute("extendedResponses", String
        .valueOf(tmpExtendedResponses)));
    attrs.add(createAttribute("modifyRequests", String
        .valueOf(tmpModifyRequests)));
    attrs.add(createAttribute("modifyResponses", String
        .valueOf(tmpModifyResponses)));
    attrs.add(createAttribute("modifyDNRequests", String
        .valueOf(tmpModifyDNRequests)));
    attrs.add(createAttribute("modifyDNResponses", String
        .valueOf(tmpModifyDNResponses)));
    attrs.add(createAttribute("searchRequests", String
        .valueOf(tmpSearchRequests)));
    attrs.add(createAttribute("searchResultEntries", String
        .valueOf(tmpSearchEntries)));
    attrs.add(createAttribute("searchResultReferences", String
        .valueOf(tmpSearchReferences)));
    attrs.add(createAttribute("searchResultsDone", String
        .valueOf(tmpSearchResultsDone)));
    attrs.add(createAttribute("unbindRequests", String
        .valueOf(tmpUnbindRequests)));

    // adds
    attrs.add(createAttribute("ds-mon-add-operations-total-count",
        String.valueOf(addRequestsMonitor.getCounter().getCount())));
    attrs.add(createAttribute(
        "ds-mon-resident-time-add-operations-total-time", String
            .valueOf(addRequestsMonitor.getTotalTime())));

    // search
    attrs.add(createAttribute("ds-mon-search-operations-total-count",
        String.valueOf(searchRequestsMonitor.getCounter().getCount())));
    attrs.add(createAttribute(
        "ds-mon-resident-time-search-operations-total-time", String
            .valueOf(searchRequestsMonitor.getTotalTime())));

    // bind
    attrs.add(createAttribute("ds-mon-bind-operations-total-count",
        String.valueOf(bindRequestsMonitor.getCounter().getCount())));
    attrs.add(createAttribute(
        "ds-mon-resident-time-bind-operations-total-time", String
            .valueOf(bindRequestsMonitor.getTotalTime())));

    // unbind
    attrs.add(createAttribute("ds-mon-unbind-operations-total-count",
        String.valueOf(unbindRequestsMonitor.getCounter().getCount())));
    attrs.add(createAttribute(
        "ds-mon-resident-time-unbind-operations-total-time", String
            .valueOf(unbindRequestsMonitor.getTotalTime())));

    // compare
    attrs
        .add(createAttribute("ds-mon-compare-operations-total-count",
            String.valueOf(compareRequestsMonitor.getCounter()
                .getCount())));
    attrs.add(createAttribute(
        "ds-mon-resident-time-compare-operations-total-time", String
            .valueOf(compareRequestsMonitor.getTotalTime())));
    // del
    attrs.add(createAttribute("ds-mon-delete-operations-total-count",
        String.valueOf(delRequestsMonitor.getCounter().getCount())));
    attrs.add(createAttribute(
        "ds-mon-resident-time-delete-operations-total-time", String
            .valueOf(delRequestsMonitor.getTotalTime())));

    // mod
    attrs.add(createAttribute("ds-mon-mod-operations-total-count",
        String.valueOf(modRequestsMonitor.getCounter().getCount())));
    attrs.add(createAttribute(
        "ds-mon-resident-time-mod-operations-total-time", String
            .valueOf(modRequestsMonitor.getTotalTime())));

    // moddn
    attrs.add(createAttribute("ds-mon-moddn-operations-total-count",
        String.valueOf(moddnRequestsMonitor.getCounter().getCount())));
    attrs.add(createAttribute(
        "ds-mon-resident-time-moddn-operations-total-time", String
            .valueOf(moddnRequestsMonitor.getTotalTime())));

    // abandon
    attrs
        .add(createAttribute("ds-mon-abandon-operations-total-count",
            String.valueOf(abandonRequestsMonitor.getCounter()
                .getCount())));
    attrs.add(createAttribute(
        "ds-mon-resident-time-abandon-operations-total-time", String
            .valueOf(abandonRequestsMonitor.getTotalTime())));

    // extended
    attrs
        .add(createAttribute("ds-mon-extended-operations-total-count",
            String.valueOf(extendedRequestsMonitor.getCounter()
                .getCount())));
    attrs.add(createAttribute(
        "ds-mon-resident-time-extended-operations-total-time", String
            .valueOf(extendedRequestsMonitor.getTotalTime())));

    return attrs;
  }



  /**
   * Clears any statistical information collected to this point.
   */
  public void clearStatistics()
  {
    // Quickly grab the locks and store consistent copies of the
    // information. Note that when grabbing multiple locks, it is
    // essential that they are all acquired in the same order to prevent
    // deadlocks.
    synchronized (abandonLock)
    {
      synchronized (connectLock)
      {
        synchronized (disconnectLock)
        {
          synchronized (writeLock)
          {
            synchronized (readLock)
            {
              abandonRequests = 0;
              addRequests = 0;
              addResponses = 0;
              bindRequests = 0;
              bindResponses = 0;
              bytesRead = 0;
              bytesWritten = 0;
              compareRequests = 0;
              compareResponses = 0;
              connectionsClosed = 0;
              connectionsEstablished = 0;
              deleteRequests = 0;
              deleteResponses = 0;
              extendedRequests = 0;
              extendedResponses = 0;
              messagesRead = 0;
              messagesWritten = 0;
              modifyRequests = 0;
              modifyResponses = 0;
              modifyDNRequests = 0;
              modifyDNResponses = 0;
              operationsAbandoned = 0;
              operationsCompleted = 0;
              operationsInitiated = 0;
              searchRequests = 0;
              searchResultEntries = 0;
              searchResultReferences = 0;
              searchResultsDone = 0;
              unbindRequests = 0;
            }
          }
        }
      }
    }
  }



  /**
   * Updates the appropriate set of counters to indicate that a new
   * connection has been established.
   */
  public void updateConnect()
  {
    synchronized (connectLock)
    {
      connectionsEstablished++;
    }

    // Update the parent if there is one.
    if (parent != null)
    {
      parent.updateConnect();
    }
  }



  /**
   * Updates the appropriate set of counters to indicate that a
   * connection has been closed.
   */
  public void updateDisconnect()
  {
    synchronized (disconnectLock)
    {
      connectionsClosed++;
    }

    // Update the parent if there is one.
    if (parent != null)
    {
      parent.updateDisconnect();
    }
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
    synchronized (readLock)
    {
      this.bytesRead += bytesRead;
    }

    // Update the parent if there is one.
    if (parent != null)
    {
      parent.updateBytesRead(bytesRead);
    }
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
    synchronized (readLock)
    {
      messagesRead++;
      operationsInitiated++;

      switch (message.getProtocolOp().getType())
      {
      case OP_TYPE_ABANDON_REQUEST:
        abandonRequests++;
        break;
      case OP_TYPE_ADD_REQUEST:
        addRequests++;
        break;
      case OP_TYPE_BIND_REQUEST:
        bindRequests++;
        break;
      case OP_TYPE_COMPARE_REQUEST:
        compareRequests++;
        break;
      case OP_TYPE_DELETE_REQUEST:
        deleteRequests++;
        break;
      case OP_TYPE_EXTENDED_REQUEST:
        extendedRequests++;
        break;
      case OP_TYPE_MODIFY_REQUEST:
        modifyRequests++;
        break;
      case OP_TYPE_MODIFY_DN_REQUEST:
        modifyDNRequests++;
        break;
      case OP_TYPE_SEARCH_REQUEST:
        searchRequests++;
        break;
      case OP_TYPE_UNBIND_REQUEST:
        unbindRequests++;
        break;
      }
    }

    // Update the parent if there is one.
    if (parent != null)
    {
      parent.updateMessageRead(message);
    }
  }



  /**
   * Updates the appropriate set of counters based on the provided
   * message that has been written to the client.
   *
   * @param message
   *          The message that was written to the client.
   * @param bytesWritten
   *          The size of the message written in bytes.
   */
  public void updateMessageWritten(LDAPMessage message, int bytesWritten)
  {
    synchronized (writeLock)
    {
      this.bytesWritten += bytesWritten;
      messagesWritten++;

      switch (message.getProtocolOp().getType())
      {
      case OP_TYPE_ADD_RESPONSE:
        addResponses++;
        operationsCompleted++;
        break;
      case OP_TYPE_BIND_RESPONSE:
        bindResponses++;
        operationsCompleted++;
        break;
      case OP_TYPE_COMPARE_RESPONSE:
        compareResponses++;
        operationsCompleted++;
        break;
      case OP_TYPE_DELETE_RESPONSE:
        deleteResponses++;
        operationsCompleted++;
        break;
      case OP_TYPE_EXTENDED_RESPONSE:
        extendedResponses++;

        // We don't want to include unsolicited notifications as
        // "completed" operations.
        if (message.getMessageID() > 0)
        {
          operationsCompleted++;
        }
        break;
      case OP_TYPE_MODIFY_RESPONSE:
        modifyResponses++;
        operationsCompleted++;
        break;
      case OP_TYPE_MODIFY_DN_RESPONSE:
        modifyDNResponses++;
        operationsCompleted++;
        break;
      case OP_TYPE_SEARCH_RESULT_ENTRY:
        searchResultEntries++;
        break;
      case OP_TYPE_SEARCH_RESULT_REFERENCE:
        searchResultReferences++;
        break;
      case OP_TYPE_SEARCH_RESULT_DONE:
        searchResultsDone++;
        operationsCompleted++;
        break;
      }
    }

    // Update the parent if there is one.
    if (parent != null)
    {
      parent.updateMessageWritten(message, bytesWritten);
    }
  }



  /**
   * Updates the appropriate set of counters to indicate that an
   * operation was abandoned without sending a response to the client.
   */
  public void updateAbandonedOperation()
  {
    synchronized (abandonLock)
    {
      operationsAbandoned++;
    }

    // Update the parent if there is one.
    if (parent != null)
    {
      parent.updateAbandonedOperation();
    }
  }



  /**
   * Constructs an attribute using the provided information. It will
   * have the default syntax.
   *
   * @param name
   *          The name to use for the attribute.
   * @param value
   *          The value to use for the attribute.
   * @return the constructed attribute.
   */
  private Attribute createAttribute(String name, String value)
  {
    AttributeType attrType =
        DirectoryServer.getDefaultAttributeType(name);

    AttributeBuilder builder = new AttributeBuilder(attrType, name);
    builder.add(AttributeValues.create(attrType, value));

    return builder.toAttribute();
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
    synchronized (connectLock)
    {
      return connectionsEstablished;
    }
  }



  /**
   * Retrieves the number of client connections that have been closed.
   *
   * @return The number of client connections that have been closed.
   */
  public long getConnectionsClosed()
  {
    synchronized (disconnectLock)
    {
      return connectionsClosed;
    }
  }



  /**
   * Retrieves the number of bytes that have been received from clients.
   *
   * @return The number of bytes that have been received from clients.
   */
  public long getBytesRead()
  {
    synchronized (readLock)
    {
      return bytesRead;
    }
  }



  /**
   * Retrieves the number of bytes that have been written to clients.
   *
   * @return The number of bytes that have been written to clients.
   */
  public long getBytesWritten()
  {
    synchronized (writeLock)
    {
      return bytesWritten;
    }
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
    synchronized (readLock)
    {
      return messagesRead;
    }
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
    synchronized (writeLock)
    {
      return messagesWritten;
    }
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
    synchronized (readLock)
    {
      return operationsInitiated;
    }
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
    synchronized (writeLock)
    {
      return operationsCompleted;
    }
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
    synchronized (abandonLock)
    {
      return operationsAbandoned;
    }
  }



  /**
   * Retrieves the number of abandon requests that have been received.
   *
   * @return The number of abandon requests that have been received.
   */
  public long getAbandonRequests()
  {
    synchronized (readLock)
    {
      return abandonRequests;
    }
  }



  /**
   * Retrieves the number of add requests that have been received.
   *
   * @return The number of add requests that have been received.
   */
  public long getAddRequests()
  {
    synchronized (readLock)
    {
      return addRequests;
    }
  }



  /**
   * Retrieves the number of add responses that have been sent.
   *
   * @return The number of add responses that have been sent.
   */
  public long getAddResponses()
  {
    synchronized (writeLock)
    {
      return addResponses;
    }
  }



  /**
   * Retrieves the number of bind requests that have been received.
   *
   * @return The number of bind requests that have been received.
   */
  public long getBindRequests()
  {
    synchronized (readLock)
    {
      return bindRequests;
    }
  }



  /**
   * Retrieves the number of bind responses that have been sent.
   *
   * @return The number of bind responses that have been sent.
   */
  public long getBindResponses()
  {
    synchronized (writeLock)
    {
      return bindResponses;
    }
  }



  /**
   * Retrieves the number of compare requests that have been received.
   *
   * @return The number of compare requests that have been received.
   */
  public long getCompareRequests()
  {
    synchronized (readLock)
    {
      return compareRequests;
    }
  }



  /**
   * Retrieves the number of compare responses that have been sent.
   *
   * @return The number of compare responses that have been sent.
   */
  public long getCompareResponses()
  {
    synchronized (writeLock)
    {
      return compareResponses;
    }
  }



  /**
   * Retrieves the number of delete requests that have been received.
   *
   * @return The number of delete requests that have been received.
   */
  public long getDeleteRequests()
  {
    synchronized (readLock)
    {
      return deleteRequests;
    }
  }



  /**
   * Retrieves the number of delete responses that have been sent.
   *
   * @return The number of delete responses that have been sent.
   */
  public long getDeleteResponses()
  {
    synchronized (writeLock)
    {
      return deleteResponses;
    }
  }



  /**
   * Retrieves the number of extended requests that have been received.
   *
   * @return The number of extended requests that have been received.
   */
  public long getExtendedRequests()
  {
    synchronized (readLock)
    {
      return extendedRequests;
    }
  }



  /**
   * Retrieves the number of extended responses that have been sent.
   *
   * @return The number of extended responses that have been sent.
   */
  public long getExtendedResponses()
  {
    synchronized (writeLock)
    {
      return extendedResponses;
    }
  }



  /**
   * Retrieves the number of modify requests that have been received.
   *
   * @return The number of modify requests that have been received.
   */
  public long getModifyRequests()
  {
    synchronized (readLock)
    {
      return modifyRequests;
    }
  }



  /**
   * Retrieves the number of modify responses that have been sent.
   *
   * @return The number of modify responses that have been sent.
   */
  public long getModifyResponses()
  {
    synchronized (writeLock)
    {
      return modifyResponses;
    }
  }



  /**
   * Retrieves the number of modify DN requests that have been received.
   *
   * @return The number of modify DN requests that have been received.
   */
  public long getModifyDNRequests()
  {
    synchronized (readLock)
    {
      return modifyDNRequests;
    }
  }



  /**
   * Retrieves the number of modify DN responses that have been sent.
   *
   * @return The number of modify DN responses that have been sent.
   */
  public long getModifyDNResponses()
  {
    synchronized (writeLock)
    {
      return modifyDNResponses;
    }
  }



  /**
   * Retrieves the number of search requests that have been received.
   *
   * @return The number of search requests that have been received.
   */
  public long getSearchRequests()
  {
    synchronized (readLock)
    {
      return searchRequests;
    }
  }



  /**
   * Retrieves the number of search result entries that have been sent.
   *
   * @return The number of search result entries that have been sent.
   */
  public long getSearchResultEntries()
  {
    synchronized (writeLock)
    {
      return searchResultEntries;
    }
  }



  /**
   * Retrieves the number of search result references that have been
   * sent.
   *
   * @return The number of search result references that have been sent.
   */
  public long getSearchResultReferences()
  {
    synchronized (writeLock)
    {
      return searchResultReferences;
    }
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
    synchronized (writeLock)
    {
      return searchResultsDone;
    }
  }



  /**
   * Retrieves the number of unbind requests that have been received.
   *
   * @return The number of unbind requests that have been received.
   */
  public long getUnbindRequests()
  {
    synchronized (readLock)
    {
      return unbindRequests;
    }
  }



  /**
   * Retrieves the parent statistics tracker that will also be updated
   * whenever this tracker is updated.
   *
   * @return The parent statistics tracker, or {@code null} if there is
   *         none.
   */
  public LDAPStatistics getParent()
  {
    return parent;
  }



  /**
   * Updates the monitor object.
   *
   * @param opMonitor
   *          monitor object.
   */
  public void updateMonitor(OperationMonitor opMonitor)
  {
    synchronized (readLock)
    {
      switch (opMonitor.getType())
      {
      case ABANDON:
        this.abandonRequestsMonitor.add(opMonitor);
        break;
      case ADD:
        this.addRequestsMonitor.add(opMonitor);
        break;
      case BIND:
        this.bindRequestsMonitor.add(opMonitor);
        break;
      case COMPARE:
        this.compareRequestsMonitor.add(opMonitor);
        break;
      case DELETE:
        this.delRequestsMonitor.add(opMonitor);
        break;
      case EXTENDED:
        this.extendedRequestsMonitor.add(opMonitor);
        break;
      case MODIFY:
        this.modRequestsMonitor.add(opMonitor);
        break;
      case MODIFY_DN:
        this.moddnRequestsMonitor.add(opMonitor);
        break;
      case SEARCH:
        this.searchRequestsMonitor.add(opMonitor);
        break;
      case UNBIND:
        this.unbindRequestsMonitor.add(opMonitor);
        break;
      default:
      }
      if (parent != null)
      {
        parent.updateMonitor(opMonitor);
      }
      opMonitor.reset();
    }
  }

}
