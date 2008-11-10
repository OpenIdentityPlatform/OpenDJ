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
package org.opends.server.protocols.jmx;
import org.opends.messages.Message;



import java.net.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.remote.JMXConnectionNotification;
import org.opends.server.api.*;
import org.opends.server.core.*;
import org.opends.server.extensions.*;
import org.opends.server.protocols.asn1.*;
import org.opends.server.protocols.ldap.*;
import org.opends.server.protocols.internal.InternalSearchOperation ;
import org.opends.server.protocols.internal.InternalSearchListener;

import org.opends.server.types.AbstractOperation;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.CancelRequest;
import org.opends.server.types.CancelResult;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.IntermediateResponse;
import org.opends.server.types.Modification;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.Operation;
import org.opends.server.types.Privilege;
import org.opends.server.types.RDN;
import org.opends.server.types.RawAttribute;
import org.opends.server.types.RawModification;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;
import org.opends.server.types.SearchScope;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.messages.ProtocolMessages.*;

import org.opends.messages.MessageBuilder;
import org.opends.server.core.networkgroups.NetworkGroup;


/**
 * This class defines the set of methods and structures that must be implemented
 * by a Directory Server client connection.
 *
 */
public class JmxClientConnection
       extends ClientConnection implements NotificationListener
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // The message ID counter to use for jmx connections.
  private AtomicInteger nextMessageID;

  // The operation ID counter to use for operations on this connection.
  private AtomicLong nextOperationID;

  // The connection security provider for this client connection.
  private ConnectionSecurityProvider securityProvider;

  // The empty operation list for this connection.
  private LinkedList<Operation> operationList;

  // The connection ID for this client connection.
  private long connectionID;

  /**
   * The JMX connection ID for this client connection.
   */
  protected String jmxConnectionID = null;

  /**
   * The reference to the connection handler that accepted this connection.
   */
  private JmxConnectionHandler jmxConnectionHandler;

  /**
   * Indicate that the disconnect process is started.
   */
  private Boolean disconnectStarted = new Boolean(false);


  /**
   * Creates a new Jmx client connection that will be authenticated as
   * as the specified user.
   *
   * @param jmxConnectionHandler
   *        The connection handler on which we should be registered
   * @param authInfo
   *        the User authentication info
   */
  public JmxClientConnection(JmxConnectionHandler jmxConnectionHandler,
      AuthenticationInfo authInfo)
  {
    super();

    this.setNetworkGroup(NetworkGroup.getAdminNetworkGroup());

    nextMessageID    = new AtomicInteger(1);
    nextOperationID  = new AtomicLong(0);

    this.jmxConnectionHandler = jmxConnectionHandler;
    jmxConnectionHandler.registerClientConnection(this);

    setAuthenticationInfo(authInfo);

    connectionID = DirectoryServer.newConnectionAccepted(this);
    if (connectionID < 0)
    {
      //
      // TODO Change Message to be JMX specific
      disconnect(
          DisconnectReason.ADMIN_LIMIT_EXCEEDED,
          true,
          ERR_LDAP_CONNHANDLER_REJECTED_BY_SERVER.get());
    }
    operationList = new LinkedList<Operation>();

    try
    {
      securityProvider = new NullConnectionSecurityProvider();
      securityProvider.initializeConnectionSecurityProvider(null);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
    }

    //
    // Register the Jmx Notification listener (this)
    jmxConnectionHandler.getRMIConnector().jmxRmiConnectorNoClientCertificate
        .addNotificationListener(this, null, null);
  }


  /**
   * {@inheritDoc}
   */
  public void handleNotification(Notification notif, Object handback)
  {
    JMXConnectionNotification jcn ;

    //
    // We don't have the expected notification
    if ( ! (notif instanceof JMXConnectionNotification))
    {
      return ;
    }
    else
    {
      jcn = (JMXConnectionNotification) notif ;
    }

    //
    // The only handled notifications are CLOSED and FAILED
    if (! (
        (jcn.getType().equals(JMXConnectionNotification.CLOSED))
        ||
        (jcn.getType().equals(JMXConnectionNotification.FAILED))
        ))
    {
      return;
    }

    //
    // Check if the closed connection corresponds to the current connection
    if (!(jcn.getConnectionId().equals(jmxConnectionID)))
    {
      return;
    }

    //
    // Ok, we can perform the unbind: call finalize
    disconnect(DisconnectReason.CLIENT_DISCONNECT, false, null);
  }


  /**
   * Retrieves the operation ID that should be used for the next Jmx
   * operation.
   *
   * @return  The operation ID that should be used for the next Jmx
   *          operation.
   */
  public long nextOperationID()
  {
    long opID = nextOperationID.getAndIncrement();
    if (opID < 0)
    {
      synchronized (nextOperationID)
      {
        if (nextOperationID.get() < 0)
        {
          nextOperationID.set(1);
          return 0;
        }
        else
        {
          return nextOperationID.getAndIncrement();
        }
      }
    }

    return opID;
  }



  /**
   * Retrieves the message ID that should be used for the next Jmx
   * operation.
   *
   * @return  The message ID that should be used for the next Jmx
   *          operation.
   */
  public int nextMessageID()
  {
    int msgID = nextMessageID.getAndIncrement();
    if (msgID < 0)
    {
      synchronized (nextMessageID)
      {
        if (nextMessageID.get() < 0)
        {
          nextMessageID.set(2);
          return 1;
        }
        else
        {
          return nextMessageID.getAndIncrement();
        }
      }
    }

    return msgID;
  }



  /**
   * Retrieves the unique identifier that has been assigned to this connection.
   *
   * @return  The unique identifier that has been assigned to this connection.
   */
  public long getConnectionID()
  {
    return connectionID;
  }

  /**
   * Retrieves the connection handler that accepted this client connection.
   *
   * @return  The connection handler that accepted this client connection.
   */
  public ConnectionHandler getConnectionHandler()
  {
    return jmxConnectionHandler;
  }

  /**
   * Retrieves the protocol that the client is using to communicate with the
   * Directory Server.
   *
   * @return  The protocol that the client is using to communicate with the
   *          Directory Server.
   */
  public String getProtocol()
  {
    return "jmx";
  }



  /**
   * Retrieves a string representation of the address of the client.
   *
   * @return  A string representation of the address of the client.
   */
  public String getClientAddress()
  {
    return "jmx";
  }



  /**
   * Retrieves the port number for this connection on the client system.
   *
   * @return  The port number for this connection on the client system.
   */
  public int getClientPort()
  {
    return -1;
  }



  /**
   * Retrieves a string representation of the address on the server to which the
   * client connected.
   *
   * @return  A string representation of the address on the server to which the
   *          client connected.
   */
  public String getServerAddress()
  {
    return "jmx";
  }



  /**
   * Retrieves the port number for this connection on the server
   * system if available.
   *
   * @return The port number for this connection on the server system
   *         or -1 if there is no server port associated with this
   *         connection (e.g. internal client).
   */
  public int getServerPort()
  {
    return -1;
  }



  /**
   * Retrieves the <CODE>java.net.InetAddress</CODE> associated with the remote
   * client system.
   *
   * @return  The <CODE>java.net.InetAddress</CODE> associated with the remote
   *          client system.  It may be <CODE>null</CODE> if the client is not
   *          connected over an IP-based connection.
   */
  public InetAddress getRemoteAddress()
  {
    return null;
  }



  /**
   * Retrieves the <CODE>java.net.InetAddress</CODE> for the Directory Server
   * system to which the client has established the connection.
   *
   * @return  The <CODE>java.net.InetAddress</CODE> for the Directory Server
   *          system to which the client has established the connection.  It may
   *          be <CODE>null</CODE> if the client is not connected over an
   *          IP-based connection.
   */
  public InetAddress getLocalAddress()
  {
    return null;
  }



  /**
   * Indicates whether this client connection is currently using a secure
   * mechanism to communicate with the server.  Note that this may change over
   * time based on operations performed by the client or server (e.g., it may go
   * from <CODE>false</CODE> to <CODE>true</CODE> if the client uses the
   * StartTLS extended operation).
   *
   * @return  <CODE>true</CODE> if the client connection is currently using a
   *          secure mechanism to communicate with the server, or
   *          <CODE>false</CODE> if not.
   */
  public boolean isSecure()
  {
    return securityProvider.isSecure();
  }



  /**
   * Retrieves the connection security provider for this client connection.
   *
   * @return  The connection security provider for this client connection.
   */
  public ConnectionSecurityProvider getConnectionSecurityProvider()
  {
    return securityProvider;
  }



  /**
   * Specifies the connection security provider for this client connection.
   *
   * @param  securityProvider  The connection security provider to use for
   *                           communication on this client connection.
   */
  public void setConnectionSecurityProvider(ConnectionSecurityProvider
                                                 securityProvider)
  {
    this.securityProvider = securityProvider;
  }



  /**
   * Retrieves the human-readable name of the security mechanism that is used to
   * protect communication with this client.
   *
   * @return  The human-readable name of the security mechanism that is used to
   *          protect communication with this client, or <CODE>null</CODE> if no
   *          security is in place.
   */
  public String getSecurityMechanism()
  {
    return securityProvider.getSecurityMechanismName();
  }



  /**
   * Indicates that the data in the provided buffer has been read from the
   * client and should be processed.  The contents of the provided buffer will
   * be in clear-text (the data may have been passed through a connection
   * security provider to obtain the clear-text version), and may contain part
   * or all of one or more client requests.
   *
   * @param  buffer  The byte buffer containing the data available for reading.
   *
   * @return  <CODE>true</CODE> if all the data in the provided buffer was
   *          processed and the client connection can remain established, or
   *          <CODE>false</CODE> if a decoding error occurred and requests from
   *          this client should no longer be processed.  Note that if this
   *          method does return <CODE>false</CODE>, then it must have already
   *          disconnected the client.
   */
  public boolean processDataRead(ByteBuffer buffer)
  {
    // This method will not do anything with the data because there is no
    // actual "connection" from which information can be read, nor any protocol
    // to use to read it.
    return false;
  }



  /**
   * Sends a response to the client based on the information in the provided
   * operation.
   *
   * @param  operation  The operation for which to send the response.
   */
  public void sendResponse(Operation operation)
  {
    // There will not be any response sent by this method, since there is not an
    // actual connection.
  }



  /**
   * Processes an Jmx add operation with the provided information.
   *
   * @param  rawEntryDN     The DN to use for the entry to add.
   * @param  rawAttributes  The set of attributes to include in the entry to
   *                        add.
   *
   * @return  A reference to the add operation that was processed and contains
   *          information about the result of the processing.
   */
  public AddOperation processAdd(ASN1OctetString rawEntryDN,
                                 ArrayList<RawAttribute> rawAttributes)
  {
    AddOperationBasis addOperation =
         new AddOperationBasis(this, nextOperationID(), nextMessageID(),
                          new ArrayList<Control>(0), rawEntryDN, rawAttributes);

    // Check if we have enough privilege
    if (! hasPrivilege(Privilege.JMX_WRITE, null))
    {
      Message message = ERR_JMX_ADD_INSUFFICIENT_PRIVILEGES.get();
      addOperation.setErrorMessage(new MessageBuilder(message));
      addOperation.setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS) ;
    }
    else
    {
      addOperation.run();
    }
    return addOperation;
  }

  /**
   * Processes an internal add operation with the provided
   * information.
   *
   * @param  entryDN                The entry DN for the add
   *                                operation.
   * @param  objectClasses          The set of objectclasses for the
   *                                add operation.
   * @param  userAttributes         The set of user attributes for the
   *                                add operation.
   * @param  operationalAttributes  The set of operational attributes
   *                                for the add operation.
   *
   * @return  A reference to the add operation that was processed and
   *          contains information about the result of the processing.
   */
  public AddOperation processAdd(DN entryDN,
                           Map<ObjectClass,String> objectClasses,
                           Map<AttributeType,List<Attribute>>
                                userAttributes,
                           Map<AttributeType,List<Attribute>>
                                operationalAttributes)
  {
    AddOperationBasis addOperation =
         new AddOperationBasis(this, nextOperationID(),
                          nextMessageID(),
                          new ArrayList<Control>(0), entryDN,
                          objectClasses, userAttributes,
                          operationalAttributes);
    // Check if we have enough privilege
    if (! hasPrivilege(Privilege.JMX_WRITE, null))
    {
      Message message = ERR_JMX_ADD_INSUFFICIENT_PRIVILEGES.get();
      addOperation.setErrorMessage(new MessageBuilder(message));
      addOperation.setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS) ;
    }
    else
    {
      addOperation.run();
    }
    return addOperation;
  }

  /**
   * Processes an internal delete operation with the provided
   * information.
   *
   * @param  entryDN  The entry DN for the delete operation.
   *
   * @return  A reference to the delete operation that was processed
   *          and contains information about the result of the
   *          processing.
   */
  public DeleteOperation processDelete(DN entryDN)
  {
    DeleteOperationBasis deleteOperation =
         new DeleteOperationBasis(this, nextOperationID(),
                             nextMessageID(),
                             new ArrayList<Control>(0), entryDN);
    // Check if we have enough privilege
    if (! hasPrivilege(Privilege.JMX_WRITE, null))
    {
      Message message = ERR_JMX_DELETE_INSUFFICIENT_PRIVILEGES.get();
      deleteOperation.setErrorMessage(new MessageBuilder(message));
      deleteOperation.setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS) ;
    }
    else
    {
      deleteOperation.run();
    }
    return deleteOperation;
  }


  /**
   * Processes an Jmx compare operation with the provided information.
   *
   * @param  rawEntryDN      The entry DN for the compare operation.
   * @param  attributeType   The attribute type for the compare operation.
   * @param  assertionValue  The assertion value for the compare operation.
   *
   * @return  A reference to the compare operation that was processed and
   *          contains information about the result of the processing.
   */
  public CompareOperation processCompare(ASN1OctetString rawEntryDN,
                                        String attributeType,
                                        ASN1OctetString assertionValue)
  {
    CompareOperationBasis compareOperation =
         new CompareOperationBasis(this, nextOperationID(), nextMessageID(),
                              new ArrayList<Control>(0), rawEntryDN,
                              attributeType, assertionValue);

    // Check if we have enough privilege
    if (! hasPrivilege(Privilege.JMX_READ, null))
    {
      Message message = ERR_JMX_SEARCH_INSUFFICIENT_PRIVILEGES.get();
      compareOperation.setErrorMessage(new MessageBuilder(message));
      compareOperation.setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS) ;
    }
    else
    {
      compareOperation.run();
    }
    return compareOperation;
  }



  /**
   * Processes an Jmx delete operation with the provided information.
   *
   * @param  rawEntryDN  The entry DN for the delete operation.
   *
   * @return  A reference to the delete operation that was processed and
   *          contains information about the result of the processing.
   */
  public DeleteOperation processDelete(ASN1OctetString rawEntryDN)
  {
    DeleteOperationBasis deleteOperation =
         new DeleteOperationBasis(this, nextOperationID(), nextMessageID(),
                             new ArrayList<Control>(0), rawEntryDN);

    // Check if we have enough privilege
    if (! hasPrivilege(Privilege.JMX_WRITE, null))
    {
      Message message = ERR_JMX_DELETE_INSUFFICIENT_PRIVILEGES.get();
      deleteOperation.setErrorMessage(new MessageBuilder(message));
      deleteOperation.setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS) ;
    }
    else
    {
      deleteOperation.run();
    }
    return deleteOperation;
  }



  /**
   * Processes an Jmx extended operation with the provided information.
   *
   * @param  requestOID    The OID for the extended request.
   * @param  requestValue  The encoded +value for the extended operation, or
   *                       <CODE>null</CODE> if there is no value.
   *
   * @return  A reference to the extended operation that was processed and
   *          contains information about the result of the processing.
   */
  public ExtendedOperation processExtendedOperation(String requestOID,
                                ASN1OctetString requestValue)
  {
    ExtendedOperationBasis extendedOperation =
         new ExtendedOperationBasis(this, nextOperationID(), nextMessageID(),
                               new ArrayList<Control>(0), requestOID,
                               requestValue);

    extendedOperation.run();
    return extendedOperation;
  }



  /**
   * Processes an Jmx modify operation with the provided information.
   *
   * @param  rawEntryDN        The raw entry DN for this modify operation.
   * @param  rawModifications  The set of modifications for this modify
   *                           operation.
   *
   * @return  A reference to the modify operation that was processed and
   *          contains information about the result of the processing
   */
  public ModifyOperation processModify(ASN1OctetString rawEntryDN,
                              ArrayList<RawModification> rawModifications)
  {
    ModifyOperationBasis modifyOperation =
         new ModifyOperationBasis(this, nextOperationID(), nextMessageID(),
                             new ArrayList<Control>(0), rawEntryDN,
                             rawModifications);

    if (! hasPrivilege(Privilege.JMX_WRITE, null))
    {
      Message message = ERR_JMX_MODIFY_INSUFFICIENT_PRIVILEGES.get();
      modifyOperation.setErrorMessage(new MessageBuilder(message));
      modifyOperation.setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS) ;
    }
    else
    {
      modifyOperation.run();
    }
    return modifyOperation;
  }


  /**
   * Processes an internal modify operation with the provided
   * information.
   *
   * @param  entryDN        The entry DN for this modify operation.
   * @param  modifications  The set of modifications for this modify
   *                        operation.
   *
   * @return  A reference to the modify operation that was processed
   *          and contains information about the result of the
   *          processing.
   */
  public ModifyOperation processModify(DN entryDN,
                              List<Modification> modifications)
  {
    ModifyOperationBasis modifyOperation =
         new ModifyOperationBasis(this, nextOperationID(),
                             nextMessageID(),
                             new ArrayList<Control>(0), entryDN,
                             modifications);
    if (! hasPrivilege(Privilege.JMX_WRITE, null))
    {
      Message message = ERR_JMX_MODIFY_INSUFFICIENT_PRIVILEGES.get();
      modifyOperation.setErrorMessage(new MessageBuilder(message));
      modifyOperation.setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS) ;
    }
    else
    {
      modifyOperation.run();
    }
    return modifyOperation;
  }

  /**
   * Processes an Jmx modify DN operation with the provided information.
   *
   * @param  rawEntryDN    The current DN of the entry to rename.
   * @param  rawNewRDN     The new RDN to use for the entry.
   * @param  deleteOldRDN  The flag indicating whether the old RDN value is to
   *                       be removed from the entry.
   *
   * @return  A reference to the modify DN operation that was processed and
   *          contains information about the result of the processing.
   */
  public ModifyDNOperation processModifyDN(ASN1OctetString rawEntryDN,
                                           ASN1OctetString rawNewRDN,
                                           boolean deleteOldRDN)
  {
    return processModifyDN(rawEntryDN, rawNewRDN, deleteOldRDN, null);
  }



  /**
   * Processes an Jmx modify DN operation with the provided information.
   *
   * @param  rawEntryDN      The current DN of the entry to rename.
   * @param  rawNewRDN       The new RDN to use for the entry.
   * @param  deleteOldRDN    The flag indicating whether the old RDN value is to
   *                         be removed from the entry.
   * @param  rawNewSuperior  The new superior for the modify DN operation, or
   *                         <CODE>null</CODE> if the entry will remain below
   *                         the same parent.
   *
   * @return  A reference to the modify DN operation that was processed and
   *          contains information about the result of the processing.
   */
  public ModifyDNOperation processModifyDN(ASN1OctetString rawEntryDN,
                                           ASN1OctetString rawNewRDN,
                                           boolean deleteOldRDN,
                                           ASN1OctetString rawNewSuperior)
  {
    ModifyDNOperationBasis modifyDNOperation =
         new ModifyDNOperationBasis(this, nextOperationID(), nextMessageID(),
                               new ArrayList<Control>(0), rawEntryDN, rawNewRDN,
                               deleteOldRDN, rawNewSuperior);

    if (! hasPrivilege(Privilege.JMX_WRITE, null))
    {
      Message message = ERR_JMX_MODDN_INSUFFICIENT_PRIVILEGES.get();
      modifyDNOperation.setErrorMessage(new MessageBuilder(message));
      modifyDNOperation.setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS) ;
    }
    else
    {
      modifyDNOperation.run();
    }
    return modifyDNOperation;
  }

  /**
   * Processes an internal modify DN operation with the provided
   * information.
   *
   * @param  entryDN       The current DN of the entry to rename.
   * @param  newRDN        The new RDN to use for the entry.
   * @param  deleteOldRDN  The flag indicating whether the old RDN
   *                       value is to be removed from the entry.
   * @param  newSuperior   The new superior for the modify DN
   *                       operation, or <CODE>null</CODE> if the
   *                       entry will remain below the same parent.
   *
   * @return  A reference to the modify DN operation that was
   *          processed and contains information about the result of
   *          the processing.
   */
  public ModifyDNOperation processModifyDN(DN entryDN, RDN newRDN,
                                           boolean deleteOldRDN,
                                           DN newSuperior)
  {
    ModifyDNOperationBasis modifyDNOperation =
         new ModifyDNOperationBasis(this, nextOperationID(),
                               nextMessageID(),
                               new ArrayList<Control>(0), entryDN,
                               newRDN, deleteOldRDN, newSuperior);

    if (! hasPrivilege(Privilege.JMX_WRITE, null))
    {
      Message message = ERR_JMX_MODDN_INSUFFICIENT_PRIVILEGES.get();
      modifyDNOperation.setErrorMessage(new MessageBuilder(message));
      modifyDNOperation.setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS) ;
    }
    else
    {
      modifyDNOperation.run();
    }
    return modifyDNOperation;
  }

  /**
   * Processes an Jmx search operation with the provided information.
   * It will not dereference any aliases, will not request a size or time limit,
   * and will retrieve all user attributes.
   *
   * @param  rawBaseDN  The base DN for the search.
   * @param  scope      The scope for the search.
   * @param  filter     The filter for the search.
   *
   * @return  A reference to the internal search operation that was processed
   *          and contains information about the result of the processing as
   *          well as lists of the matching entries and search references.
   */
  public InternalSearchOperation processSearch(ASN1OctetString rawBaseDN,
                                      SearchScope scope, LDAPFilter filter)
  {
    return processSearch(rawBaseDN, scope,
                         DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                         filter, new LinkedHashSet<String>(0));
  }



  /**
   * Processes an Jmx search operation with the provided information.
   *
   * @param  rawBaseDN    The base DN for the search.
   * @param  scope        The scope for the search.
   * @param  derefPolicy  The alias dereferencing policy for the search.
   * @param  sizeLimit    The size limit for the search.
   * @param  timeLimit    The time limit for the search.
   * @param  typesOnly    The typesOnly flag for the search.
   * @param  filter       The filter for the search.
   * @param  attributes   The set of requested attributes for the search.
   *
   * @return  A reference to the internal search operation that was processed
   *          and contains information about the result of the processing as
   *          well as lists of the matching entries and search references.
   */
  public InternalSearchOperation processSearch(ASN1OctetString rawBaseDN,
                                      SearchScope scope,
                                      DereferencePolicy derefPolicy,
                                      int sizeLimit, int timeLimit,
                                      boolean typesOnly, LDAPFilter filter,
                                      LinkedHashSet<String> attributes)
  {
    InternalSearchOperation searchOperation =
         new InternalSearchOperation(this, nextOperationID(), nextMessageID(),
                                     new ArrayList<Control>(0), rawBaseDN,
                                     scope, derefPolicy, sizeLimit, timeLimit,
                                     typesOnly, filter, attributes, null);

    if (! hasPrivilege(Privilege.JMX_READ, null))
    {
      Message message = ERR_JMX_SEARCH_INSUFFICIENT_PRIVILEGES.get();
      searchOperation.setErrorMessage(new MessageBuilder(message));
      searchOperation.setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS) ;
    }
    else
    {
      searchOperation.run();
    }
    return searchOperation;
  }



  /**
   * Processes an Jmx search operation with the provided information.
   *
   * @param  rawBaseDN       The base DN for the search.
   * @param  scope           The scope for the search.
   * @param  derefPolicy     The alias dereferencing policy for the search.
   * @param  sizeLimit       The size limit for the search.
   * @param  timeLimit       The time limit for the search.
   * @param  typesOnly       The typesOnly flag for the search.
   * @param  filter          The filter for the search.
   * @param  attributes      The set of requested attributes for the search.
   * @param  searchListener  The internal search listener that should be used to
   *                         handle the matching entries and references.
   *
   * @return  A reference to the internal search operation that was processed
   *          and contains information about the result of the processing.
   */
  public InternalSearchOperation processSearch(ASN1OctetString rawBaseDN,
                                      SearchScope scope,
                                      DereferencePolicy derefPolicy,
                                      int sizeLimit, int timeLimit,
                                      boolean typesOnly, LDAPFilter filter,
                                      LinkedHashSet<String> attributes,
                                      InternalSearchListener searchListener)
  {
    InternalSearchOperation searchOperation =
         new InternalSearchOperation(this, nextOperationID(), nextMessageID(),
                                     new ArrayList<Control>(0), rawBaseDN,
                                     scope, derefPolicy, sizeLimit, timeLimit,
                                     typesOnly, filter, attributes,
                                     searchListener);

    searchOperation.run();
    return searchOperation;
  }



  /**
   * Sends the provided search result entry to the client.
   *
   * @param  searchOperation  The search operation with which the entry is
   *                          associated.
   * @param  searchEntry      The search result entry to be sent to the client.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to send
   *                              the entry to the client and the search should
   *                              be terminated.
   */
  public void sendSearchEntry(SearchOperation searchOperation,
                              SearchResultEntry searchEntry)
         throws DirectoryException
  {
    ((InternalSearchOperation) searchOperation).addSearchEntry(searchEntry);
  }



  /**
   * Sends the provided search result reference to the client.
   *
   * @param  searchOperation  The search operation with which the reference is
   *                          associated.
   * @param  searchReference  The search result reference to be sent to the
   *                          client.
   *
   * @return  <CODE>true</CODE> if the client is able to accept referrals, or
   *          <CODE>false</CODE> if the client cannot handle referrals and no
   *          more attempts should be made to send them for the associated
   *          search operation.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to send
   *                              the reference to the client and the search
   *                              should be terminated.
   */
  public boolean sendSearchReference(SearchOperation searchOperation,
                                     SearchResultReference searchReference)
         throws DirectoryException
  {
    ((InternalSearchOperation)
     searchOperation).addSearchReference(searchReference);
    return true;
  }




  /**
   * Sends the provided intermediate response message to the client.
   *
   * @param  intermediateResponse  The intermediate response message to be sent.
   *
   * @return  <CODE>true</CODE> if processing on the associated operation should
   *          continue, or <CODE>false</CODE> if not.
   */
  protected boolean sendIntermediateResponseMessage(
                         IntermediateResponse intermediateResponse)
  {
    // FIXME -- Do we need to support Jmx intermediate responses?  If so,
    // then implement this.
    return false;
  }




  /**
   * Closes the connection to the client, optionally sending it a message
   * indicating the reason for the closure.  Note that the ability to send a
   * notice of disconnection may not be available for all protocols or under all
   * circumstances.
   *
   * @param  disconnectReason  The disconnect reason that provides the generic
   *                           cause for the disconnect.
   * @param  sendNotification  Indicates whether to try to provide notification
   *                           to the client that the connection will be closed.
   * @param  message           The message to send to the client.  It may be
   *                           <CODE>null</CODE> if no notification is to be
   *                           sent.
   */
  public void disconnect(DisconnectReason disconnectReason,
                         boolean sendNotification,
                         Message message)
  {
    // we are already performing a disconnect
    if (disconnectStarted)
    {
      return;
    }
    disconnectStarted = true ;
    finalizeConnectionInternal();



    // unbind the underlying connection
    try
    {
      UnbindOperationBasis unbindOp = new UnbindOperationBasis(
          (ClientConnection) this,
          this.nextOperationID(),
          this.nextMessageID(), null);

      unbindOp.run();
    }
    catch (Exception e)
    {
      // TODO print a message ?
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
    }

    // Call postDisconnectPlugins
    try
    {
      PluginConfigManager pluginManager =
           DirectoryServer.getPluginConfigManager();
      pluginManager.invokePostDisconnectPlugins(this, disconnectReason,
                                                message);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
    }
  }



  /**
   * Indicates whether a bind operation is in progress on this client
   * connection.  If so, then no new operations should be allowed until the bind
   * has completed.
   *
   * @return  <CODE>true</CODE> if a bind operation is in progress on this
   *          connection, or <CODE>false</CODE> if not.
   */
  public boolean bindInProgress()
  {
    // For Jmx operations, we don't care if there are any binds in
    // progress.
    return false;
  }



  /**
   * Specifies whether a bind operation is in progress on this client
   * connection.  If so, then no new operations should be allowed until the bind
   * has completed.
   *
   * @param  bindInProgress  Specifies whether a bind operation is in progress
   *                         on this client connection.
   */
  public void setBindInProgress(boolean bindInProgress)
  {
    // No implementation is required.
  }



  /**
   * Retrieves the set of operations in progress for this client connection.
   * This list must not be altered by any caller.
   *
   * @return  The set of operations in progress for this client connection.
   */
  public Collection<Operation> getOperationsInProgress()
  {
    return operationList;
  }



  /**
   * Retrieves the operation in progress with the specified message ID.
   *
   * @param  messageID  The message ID of the operation to retrieve.
   *
   * @return  The operation in progress with the specified message ID, or
   *          <CODE>null</CODE> if no such operation could be found.
   */
  public AbstractOperation getOperationInProgress(int messageID)
  {
    // Jmx operations will not be tracked.
    return null;
  }



  /**
   * Removes the provided operation from the set of operations in progress for
   * this client connection.  Note that this does not make any attempt to
   * cancel any processing that may already be in progress for the operation.
   *
   * @param  messageID  The message ID of the operation to remove from the set
   *                    of operations in progress.
   *
   * @return  <CODE>true</CODE> if the operation was found and removed from the
   *          set of operations in progress, or <CODE>false</CODE> if not.
   */
  public boolean removeOperationInProgress(int messageID)
  {
    // No implementation is required, since Jmx operations will not be
    // tracked.
    return false;
  }



  /**
   * Attempts to cancel the specified operation.
   *
   * @param  messageID      The message ID of the operation to cancel.
   * @param  cancelRequest  An object providing additional information about how
   *                        the cancel should be processed.
   *
   * @return  A cancel result that either indicates that the cancel was
   *          successful or provides a reason that it was not.
   */
  public CancelResult cancelOperation(int messageID,
                                      CancelRequest cancelRequest)
  {
    // Jmx operations cannot be cancelled.
    // TODO: i18n
    return new CancelResult(ResultCode.CANNOT_CANCEL,
        Message.raw("Jmx operations cannot be cancelled"));
  }



  /**
   * Attempts to cancel all operations in progress on this connection.
   *
   * @param  cancelRequest  An object providing additional information about how
   *                        the cancel should be processed.
   */
  public void cancelAllOperations(CancelRequest cancelRequest)
  {
    // No implementation is required since Jmx operations cannot be
    // cancelled.
  }



  /**
   * Attempts to cancel all operations in progress on this connection except the
   * operation with the specified message ID.
   *
   * @param  cancelRequest  An object providing additional information about how
   *                        the cancel should be processed.
   * @param  messageID      The message ID of the operation that should not be
   *                        canceled.
   */
  public void cancelAllOperationsExcept(CancelRequest cancelRequest,
                                        int messageID)
  {
    // No implementation is required since Jmx operations cannot be
    // cancelled.
  }



  /**
   * {@inheritDoc}
   */
  public String getMonitorSummary()
  {
    StringBuilder buffer = new StringBuilder();
    buffer.append("connID=\"");
    buffer.append(connectionID);
    buffer.append("\" connectTime=\"");
    buffer.append(getConnectTimeString());
    buffer.append("\" jmxConnID=\"");
    buffer.append(jmxConnectionID);
    buffer.append("\" authDN=\"");

    DN authDN = getAuthenticationInfo().getAuthenticationDN();
    if (authDN != null)
    {
      authDN.toString(buffer);
    }

    buffer.append("\" security=\"");
    if (securityProvider.isSecure())
    {
      buffer.append(securityProvider.getSecurityMechanismName());
    }
    else
    {
      buffer.append("none");
    }

    buffer.append("\"");

    return buffer.toString();
  }



  /**
   * Appends a string representation of this client connection to the provided
   * buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append("JmxClientConnection(connID=");
    buffer.append(connectionID);
    buffer.append(", authDN=\"");
    buffer.append(getAuthenticationInfo().getAuthenticationDN());
    buffer.append("\")");
  }

  /**
   * Called by the Gc when the object is garbage collected
   * Release the cursor in case the iterator was badly used and releaseCursor
   * was never called.
   */
  protected void finalize()
  {
    super.finalize();
    disconnect(DisconnectReason.OTHER, false, null);
  }

  /**
   * To be implemented.
   *
   * @return number of operations performed on this connection
   */
  @Override
  public long getNumberOfOperations() {
    // JMX connections will not be limited.
    return 0;
  }
}

