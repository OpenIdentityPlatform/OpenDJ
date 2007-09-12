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
package org.opends.server.protocols.ldap;



import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.api.ConnectionSecurityProvider;
import org.opends.server.core.AbandonOperationBasis;
import org.opends.server.core.AddOperationBasis;
import org.opends.server.core.BindOperationBasis;
import org.opends.server.core.CompareOperationBasis;
import org.opends.server.core.DeleteOperationBasis;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ExtendedOperationBasis;
import org.opends.server.core.ModifyDNOperationBasis;
import org.opends.server.core.ModifyOperationBasis;
import org.opends.server.core.PersistentSearch;
import org.opends.server.core.PluginConfigManager;
import org.opends.server.core.SearchOperation;
import org.opends.server.core.SearchOperationBasis;
import org.opends.server.core.UnbindOperationBasis;
import org.opends.server.extensions.NullConnectionSecurityProvider;
import org.opends.server.extensions.TLSCapableConnection;
import org.opends.server.extensions.TLSConnectionSecurityProvider;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.types.AbstractOperation;
import org.opends.server.types.CancelRequest;
import org.opends.server.types.CancelResult;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.IntermediateResponse;
import org.opends.server.types.Operation;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;
import org.opends.server.util.TimeThread;

import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.loggers.AccessLogger.logDisconnect;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.util.StaticUtils.getExceptionMessage;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;



/**
 * This class defines an LDAP client connection, which is a type of client
 * connection that will be accepted by an instance of the LDAP connection
 * handler and have its requests decoded by an LDAP request handler.
 */
public class LDAPClientConnection
       extends ClientConnection
       implements TLSCapableConnection
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



  // The time that the last operation was completed.
  private AtomicLong lastCompletionTime;

  // The next operation ID that should be used for this connection.
  private AtomicLong nextOperationID;

  // The selector that may be used for write operations.
  private AtomicReference<Selector> writeSelector;

  // Indicates whether the Directory Server believes this connection to be
  // valid and available for communication.
  private boolean connectionValid;

  // Indicates whether this connection is about to be closed.  This will be used
  // to prevent accepting new requests while a disconnect is in progress.
  private boolean disconnectRequested;

  // Indicates whether the connection should keep statistics regarding the
  // operations that it is performing.
  private boolean keepStats;

  // The BER type for the ASN.1 element that is in the process of being read.
  private byte elementType;

  // The encoded value for the ASN.1 element that is in the process of being
  // read.
  private byte[] elementValue;

  // The set of all operations currently in progress on this connection.
  private ConcurrentHashMap<Integer,AbstractOperation> operationsInProgress;

  // The connection security provider that was in use for the client connection
  // before switching to a TLS-based provider.
  private ConnectionSecurityProvider clearSecurityProvider;

  // The connection security provider for this client connection.
  private ConnectionSecurityProvider securityProvider;

  // The port on the client from which this connection originated.
  private int clientPort;

  // The number of bytes contained in the value for the ASN.1 element that is in
  // the process of being read.
  private int elementLength;

  // The number of bytes in the multi-byte length that are still needed to fully
  // decode the length of the ASN.1 element in process.
  private int elementLengthBytesNeeded;

  // The current state for the data read for the ASN.1 element in progress.
  private int elementReadState;

  // The number of bytes that have already been read for the ASN.1 element
  // value in progress.
  private int elementValueBytesRead;

  // The number of bytes that are still needed to fully decode the value of the
  // ASN.1 element in progress.
  private int elementValueBytesNeeded;

  // The LDAP version that the client is using to communicate with the server.
  private int ldapVersion;

  // The port on the server to which this client has connected.
  private int serverPort;

  // The reference to the connection handler that accepted this connection.
  private LDAPConnectionHandler connectionHandler;

  // The reference to the request handler with which this connection is
  // associated.
  private LDAPRequestHandler requestHandler;

  // The statistics tracker associated with this client connection.
  private LDAPStatistics statTracker;

  // The connection ID assigned to this connection.
  private long connectionID;

  // The lock used to provide threadsafe access to the set of operations in
  // progress.
  private Object opsInProgressLock;

  // The lock used to provide threadsafe access when sending data to the client.
  private Object transmitLock;

  // The socket channel with which this client connection is associated.
  private SocketChannel clientChannel;

  // The string representation of the address of the client.
  private String clientAddress;

  // The name of the protocol that the client is using to communicate with the
  // server.
  private String protocol;

  // The string representation of the address of the server to which the client
  // has connected.
  private String serverAddress;

  // The TLS connection security provider that may be used for this connection
  // if StartTLS is requested.
  private TLSConnectionSecurityProvider tlsSecurityProvider;



  /**
   * Creates a new LDAP client connection with the provided information.
   *
   * @param  connectionHandler  The connection handler that accepted this
   *                            connection.
   * @param  clientChannel      The socket channel that may be used to
   *                            communicate with the client.
   */
  public LDAPClientConnection(LDAPConnectionHandler connectionHandler,
                              SocketChannel clientChannel)
  {
    super();


    this.connectionHandler     = connectionHandler;
    this.clientChannel         = clientChannel;
    this.securityProvider      = null;
    this.clearSecurityProvider = null;

    opsInProgressLock = new Object();
    transmitLock      = new Object();

    elementReadState         = ELEMENT_READ_STATE_NEED_TYPE;
    elementType              = 0x00;
    elementLength            = 0;
    elementLengthBytesNeeded = 0;
    elementValue             = null;
    elementValueBytesRead    = 0;
    elementValueBytesNeeded  = 0;

    ldapVersion          = 3;
    requestHandler       = null;
    lastCompletionTime   = new AtomicLong(TimeThread.getTime());
    nextOperationID      = new AtomicLong(0);
    connectionValid      = true;
    disconnectRequested  = false;
    operationsInProgress = new ConcurrentHashMap<Integer,AbstractOperation>();
    keepStats            = connectionHandler.keepStats();
    protocol             = "LDAP";
    writeSelector        = new AtomicReference<Selector>();

    clientAddress = clientChannel.socket().getInetAddress().getHostAddress();
    clientPort    = clientChannel.socket().getPort();
    serverAddress = clientChannel.socket().getLocalAddress().getHostAddress();
    serverPort    = clientChannel.socket().getLocalPort();

    LDAPStatistics parentTracker = connectionHandler.getStatTracker();
    String         instanceName  = parentTracker.getMonitorInstanceName() +
                                   " for " + toString();
    statTracker = new LDAPStatistics(instanceName, parentTracker);

    if (keepStats)
    {
      statTracker.updateConnect();
    }

    connectionID = DirectoryServer.newConnectionAccepted(this);
    if (connectionID < 0)
    {
      disconnect(DisconnectReason.ADMIN_LIMIT_EXCEEDED, true,
                 ERR_LDAP_CONNHANDLER_REJECTED_BY_SERVER.get());
    }
  }



  /**
   * Retrieves the connection ID assigned to this connection.
   *
   * @return  The connection ID assigned to this connection.
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
    return connectionHandler;
  }



  /**
   * Retrieves the request handler that will read requests for this client
   * connection.
   *
   * @return  The request handler that will read requests for this client
   *          connection, or <CODE>null</CODE> if none has been assigned yet.
   */
  public LDAPRequestHandler getRequestHandler()
  {
    return requestHandler;
  }



  /**
   * Specifies the request handler that will read requests for this client
   * connection.
   *
   * @param  requestHandler  The request handler that will read requests for
   *                         this client connection.
   */
  public void setRequestHandler(LDAPRequestHandler requestHandler)
  {
    this.requestHandler = requestHandler;
  }



  /**
   * Retrieves the socket channel that can be used to communicate with the
   * client.
   *
   * @return  The socket channel that can be used to communicate with the
   *          client.
   */
  public SocketChannel getSocketChannel()
  {
    return clientChannel;
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
    return protocol;
  }



  /**
   * Retrieves a string representation of the address of the client.
   *
   * @return  A string representation of the address of the client.
   */
  public String getClientAddress()
  {
    return clientAddress;
  }



  /**
   * Retrieves the port number for this connection on the client system.
   *
   * @return  The port number for this connection on the client system.
   */
  public int getClientPort()
  {
    return clientPort;
  }



  /**
   * Retrieves the address and port of the client system, separated by a colon.
   *
   * @return  The address and port of the client system, separated by a colon.
   */
  public String getClientHostPort()
  {
    return clientAddress + ":" + clientPort;
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
    return serverAddress;
  }



  /**
   * Retrieves the port number for this connection on the server system.
   *
   * @return  The port number for this connection on the server system.
   */
  public int getServerPort()
  {
    return serverPort;
  }



  /**
   * Retrieves the address and port of the server system, separated by a colon.
   *
   * @return  The address and port of the server system, separated by a colon.
   */
  public String getServerHostPort()
  {
    return serverAddress + ":" + serverPort;
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
    return clientChannel.socket().getInetAddress();
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
    return clientChannel.socket().getLocalAddress();
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

    if (securityProvider.isSecure())
    {
      protocol = "LDAP+" + securityProvider.getSecurityMechanismName();
    }
    else
    {
      protocol = "LDAP";
    }
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
   * Retrieves the next operation ID that should be used for this connection.
   *
   * @return  The next operation ID that should be used for this connection.
   */
  public long nextOperationID()
  {
    return nextOperationID.getAndIncrement();
  }



  /**
   * Sends a response to the client based on the information in the provided
   * operation.
   *
   * @param  operation  The operation for which to send the response.
   */
  public void sendResponse(Operation operation)
  {
    // Since this is the final response for this operation, we can go ahead and
    // remove it from the "operations in progress" list.  It can't be canceled
    // after this point, and this will avoid potential race conditions in which
    // the client immediately sends another request with the same message ID as
    // was used for this operation.
    removeOperationInProgress(operation.getMessageID());

    LDAPMessage message = operationToResponseLDAPMessage(operation);
    if (message != null)
    {
      sendLDAPMessage(securityProvider, message);
    }
  }



  /**
   * Retrieves an LDAPMessage containing a response generated from the provided
   * operation.
   *
   * @param  operation  The operation to use to generate the response
   *                    LDAPMessage.
   *
   * @return  An LDAPMessage containing a response generated from the provided
   *          operation.
   */
  private LDAPMessage operationToResponseLDAPMessage(Operation operation)
  {
    ResultCode resultCode = operation.getResultCode();
    if (resultCode == null)
    {
      // This must mean that the operation has either not yet completed or that
      // it completed without a result for some reason.  In any case, log a
      // message and set the response to "operations error".
      logError(ERR_LDAP_CLIENT_SEND_RESPONSE_NO_RESULT_CODE.
          get(operation.getOperationType().toString(),
              operation.getConnectionID(), operation.getOperationID()));
      resultCode = DirectoryServer.getServerErrorResultCode();
    }


    MessageBuilder errorMessage = operation.getErrorMessage();
    DN             matchedDN    = operation.getMatchedDN();


    // Referrals are not allowed for LDAPv2 clients.
    List<String> referralURLs;
    if (ldapVersion == 2)
    {
      referralURLs = null;

      if (resultCode == ResultCode.REFERRAL)
      {
        resultCode = ResultCode.CONSTRAINT_VIOLATION;
        errorMessage.append(ERR_LDAPV2_REFERRAL_RESULT_CHANGED.get());
      }

      List<String> opReferrals = operation.getReferralURLs();
      if ((opReferrals != null) && (! opReferrals.isEmpty()))
      {
        StringBuilder referralsStr = new StringBuilder();
        Iterator<String> iterator = opReferrals.iterator();
        referralsStr.append(iterator.next());

        while (iterator.hasNext())
        {
          referralsStr.append(", ");
          referralsStr.append(iterator.next());
        }

        errorMessage.append(ERR_LDAPV2_REFERRALS_OMITTED.get(
                String.valueOf(referralsStr)));
      }
    }
    else
    {
      referralURLs = operation.getReferralURLs();
    }

    ProtocolOp protocolOp;
    switch (operation.getOperationType())
    {
      case ADD:
        protocolOp = new AddResponseProtocolOp(resultCode.getIntValue(),
                                               errorMessage.toMessage(),
                                               matchedDN, referralURLs);
        break;
      case BIND:
        ASN1OctetString serverSASLCredentials =
             ((BindOperationBasis) operation).getServerSASLCredentials();
        protocolOp = new BindResponseProtocolOp(resultCode.getIntValue(),
                              errorMessage.toMessage(), matchedDN,
                              referralURLs, serverSASLCredentials);
        break;
      case COMPARE:
        protocolOp = new CompareResponseProtocolOp(resultCode.getIntValue(),
                                                   errorMessage.toMessage(),
                                                   matchedDN, referralURLs);
        break;
      case DELETE:
        protocolOp = new DeleteResponseProtocolOp(resultCode.getIntValue(),
                                                  errorMessage.toMessage(),
                                                  matchedDN, referralURLs);
        break;
      case EXTENDED:
        // If this an LDAPv2 client, then we can't send this.
        if (ldapVersion == 2)
        {
          logError(ERR_LDAPV2_SKIPPING_EXTENDED_RESPONSE.get(
              getConnectionID(), operation.getOperationID(),
                  String.valueOf(operation)));
          return null;
        }

        ExtendedOperationBasis extOp = (ExtendedOperationBasis) operation;
        protocolOp = new ExtendedResponseProtocolOp(resultCode.getIntValue(),
                              errorMessage.toMessage(), matchedDN, referralURLs,
                              extOp.getResponseOID(), extOp.getResponseValue());
        break;
      case MODIFY:
        protocolOp = new ModifyResponseProtocolOp(resultCode.getIntValue(),
                                                  errorMessage.toMessage(),
                                                  matchedDN, referralURLs);
        break;
      case MODIFY_DN:
        protocolOp = new ModifyDNResponseProtocolOp(resultCode.getIntValue(),
                                                    errorMessage.toMessage(),
                                                    matchedDN, referralURLs);
        break;
      case SEARCH:
        protocolOp = new SearchResultDoneProtocolOp(resultCode.getIntValue(),
                                                    errorMessage.toMessage(),
                                                    matchedDN, referralURLs);
        break;
      default:
        // This must be a type of operation that doesn't have a response.  This
        // shouldn't happen, so log a message and return.
        logError(ERR_LDAP_CLIENT_SEND_RESPONSE_INVALID_OP.get(
                String.valueOf(operation.getOperationType()),
                getConnectionID(),
                operation.getOperationID(),
                String.valueOf(operation)));
        return null;
    }


    // Controls are not allowed for LDAPv2 clients.
    ArrayList<LDAPControl> controls;
    if (ldapVersion == 2)
    {
      controls = null;
    }
    else
    {
      List<Control> responseControls = operation.getResponseControls();
      if ((responseControls == null) || responseControls.isEmpty())
      {
        controls = null;
      }
      else
      {
        controls = new ArrayList<LDAPControl>(responseControls.size());
        for (Control c : responseControls)
        {
          controls.add(new LDAPControl(c));
        }
      }
    }

    return new LDAPMessage(operation.getMessageID(), protocolOp, controls);
  }



  /**
   * Sends the provided search result entry to the client.
   *
   * @param  searchOperation  The search operation with which the entry is
   *                          associated.
   * @param  searchEntry      The search result entry to be sent to the client.
   */
  public void sendSearchEntry(SearchOperation searchOperation,
                              SearchResultEntry searchEntry)
  {
    SearchResultEntryProtocolOp protocolOp =
         new SearchResultEntryProtocolOp(searchEntry);

    List<Control> entryControls = searchEntry.getControls();
    ArrayList<LDAPControl> controls;
    if ((entryControls == null) || entryControls.isEmpty())
    {
      controls = null;
    }
    else
    {
      controls = new ArrayList<LDAPControl>(entryControls.size());
      for (Control c : entryControls)
      {
        controls.add(new LDAPControl(c));
      }
    }

    sendLDAPMessage(securityProvider,
                    new LDAPMessage(searchOperation.getMessageID(), protocolOp,
                                    controls));
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
   */
  public boolean sendSearchReference(SearchOperation searchOperation,
                                     SearchResultReference searchReference)
  {
    // Make sure this is not an LDAPv2 client.  If it is, then they can't see
    // referrals so we'll not send anything.  Also, throw an exception so that
    // the core server will know not to try sending any more referrals to this
    // client for the rest of the operation.
    if (ldapVersion == 2)
    {
      Message message = ERR_LDAPV2_SKIPPING_SEARCH_REFERENCE.
          get(getConnectionID(), searchOperation.getOperationID(),
              String.valueOf(searchReference));
      logError(message);
      return false;
    }

    SearchResultReferenceProtocolOp protocolOp =
         new SearchResultReferenceProtocolOp(searchReference);

    List<Control> referenceControls = searchReference.getControls();
    ArrayList<LDAPControl> controls;
    if ((referenceControls == null) || referenceControls.isEmpty())
    {
      controls = null;
    }
    else
    {
      controls = new ArrayList<LDAPControl>(referenceControls.size());
      for (Control c : referenceControls)
      {
        controls.add(new LDAPControl(c));
      }
    }

    sendLDAPMessage(securityProvider,
                    new LDAPMessage(searchOperation.getMessageID(), protocolOp,
                                    controls));
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
    IntermediateResponseProtocolOp protocolOp =
         new IntermediateResponseProtocolOp(intermediateResponse.getOID(),
                                            intermediateResponse.getValue());

    Operation operation = intermediateResponse.getOperation();

    List<Control> controls = intermediateResponse.getControls();
    ArrayList<LDAPControl> ldapControls =
         new ArrayList<LDAPControl>(controls.size());
    for (Control c : controls)
    {
      ldapControls.add(new LDAPControl(c));
    }


    LDAPMessage message = new LDAPMessage(operation.getMessageID(), protocolOp,
                                          ldapControls);
    sendLDAPMessage(securityProvider, message);


    // The only reason we shouldn't continue processing is if the connection is
    // closed.
    return connectionValid;
  }



  /**
   * Sends the provided LDAP message to the client.
   *
   * @param  secProvider  The connection security provider to use to handle any
   *                      necessary security translation.
   * @param  message      The LDAP message to send to the client.
   */
  public void sendLDAPMessage(ConnectionSecurityProvider secProvider,
                              LDAPMessage message)
  {
    ASN1Element messageElement = message.encode();

    ByteBuffer messageBuffer = ByteBuffer.wrap(messageElement.encode());


    // Make sure that we can only send one message at a time.  This locking will
    // not have any impact on the ability to read requests from the client.
    synchronized (transmitLock)
    {
      try
      {
        try
        {
          int bytesWritten = messageBuffer.limit() - messageBuffer.position();
          if (! secProvider.writeData(messageBuffer))
          {
            return;
          }

          TRACER.debugProtocolElement(DebugLogLevel.VERBOSE, message);
          TRACER.debugProtocolElement(DebugLogLevel.VERBOSE, messageElement);

          messageBuffer.rewind();
          if (debugEnabled())
          {
            TRACER.debugData(DebugLogLevel.VERBOSE, messageBuffer);
          }

          if (keepStats)
          {
            statTracker.updateMessageWritten(message, bytesWritten);
          }
        }
        catch (@Deprecated Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          // We were unable to send the message due to some other internal
          // problem.  Disconnect from the client and return.
          disconnect(DisconnectReason.SERVER_ERROR, true, null);
          return;
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        // FIXME -- Log a message or something
        disconnect(DisconnectReason.SERVER_ERROR, true, null);
        return;
      }
    }
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
   * @param  message           The message to include in the disconnect
   *                           notification response.  It may be
   *                           <CODE>null</CODE> if no message is to be sent.
   */
  public void disconnect(DisconnectReason disconnectReason,
                         boolean sendNotification,
                         Message message)
  {
    // If we are already in the middle of a disconnect, then don't do anything.
    if (disconnectRequested)
    {
      return;
    }


    if (keepStats)
    {
      statTracker.updateDisconnect();
    }

    if (connectionID >= 0)
    {
      DirectoryServer.connectionClosed(this);
    }


    // Indicate that this connection is no longer valid.
    connectionValid = false;


    // Set a flag indicating that the connection is being terminated so that no
    // new requests will be accepted.  Also cancel all operations in progress.
    synchronized (opsInProgressLock)
    {
      try
      {
        disconnectRequested = true;
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }

    cancelAllOperations(new CancelRequest(true, message));
    finalizeConnectionInternal();


    // If there is a write selector for this connection, then close it.
    Selector selector = writeSelector.get();
    if (selector != null)
    {
      try
      {
        selector.close();
      } catch (Exception e) {}
    }


    // See if we should send a notification to the client.  If so, then
    // construct and send a notice of disconnection unsolicited response.
    // Note that we cannot send this notification to an LDAPv2 client.
    if (sendNotification && (ldapVersion != 2))
    {
      try
      {
        int resultCode;
        switch (disconnectReason)
        {
          case PROTOCOL_ERROR:
            resultCode = LDAPResultCode.PROTOCOL_ERROR;
            break;
          case SERVER_SHUTDOWN:
            resultCode = LDAPResultCode.UNAVAILABLE;
            break;
          case SERVER_ERROR:
            resultCode =
                 DirectoryServer.getServerErrorResultCode().getIntValue();
            break;
          case ADMIN_LIMIT_EXCEEDED:
          case IDLE_TIME_LIMIT_EXCEEDED:
          case MAX_REQUEST_SIZE_EXCEEDED:
          case IO_TIMEOUT:
            resultCode = LDAPResultCode.ADMIN_LIMIT_EXCEEDED;
            break;
          case CONNECTION_REJECTED:
            resultCode = LDAPResultCode.CONSTRAINT_VIOLATION;
            break;
          default:
            resultCode = LDAPResultCode.OTHER;
            break;
        }


        Message errMsg;
        if (message == null)
        {
          errMsg = INFO_LDAP_CLIENT_GENERIC_NOTICE_OF_DISCONNECTION.get();
        }
        else
        {
          errMsg = message;
        }


        ExtendedResponseProtocolOp notificationOp =
             new ExtendedResponseProtocolOp(resultCode, errMsg, null, null,
                                            OID_NOTICE_OF_DISCONNECTION, null);
        byte[] messageBytes =
                    new LDAPMessage(0, notificationOp, null).encode().encode();
        ByteBuffer buffer = ByteBuffer.wrap(messageBytes);
        try
        {
          securityProvider.writeData(buffer);
        } catch (Exception e) {}
      }
      catch (Exception e)
      {
        // NYI -- Log a message indicating that we couldn't send the notice of
        // disconnection.
      }
    }


    // Close the connection to the client.
    try
    {
      securityProvider.disconnect(sendNotification);
    }
    catch (Exception e)
    {
      // In general, we don't care about any exception that might be thrown
      // here.
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
    }

    try
    {
      clientChannel.close();
    }
    catch (Exception e)
    {
      // In general, we don't care about any exception that might be thrown
      // here.
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
    }


    // NYI -- Deregister the client connection from any server components that
    // might know about it.


    // Log a disconnect message.
    logDisconnect(this, disconnectReason, message);


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
   * Retrieves the set of operations in progress for this client connection.
   * This list must not be altered by any caller.
   *
   * @return  The set of operations in progress for this client connection.
   */
  public Collection<AbstractOperation> getOperationsInProgress()
  {
    return operationsInProgress.values();
  }



  /**
   * Retrieves the operation in progress with the specified message ID.
   *
   * @param  messageID  The message ID for the operation to retrieve.
   *
   * @return  The operation in progress with the specified message ID, or
   *          <CODE>null</CODE> if no such operation could be found.
   */
  public AbstractOperation getOperationInProgress(int messageID)
  {
    return operationsInProgress.get(messageID);
  }



  /**
   * Adds the provided operation to the set of operations in progress for this
   * client connection.
   *
   * @param  operation  The operation to add to the set of operations in
   *                    progress for this client connection.
   *
   * @throws  DirectoryException  If the operation is not added for some reason
   *                              (e.g., the client already has reached the
   *                              maximum allowed concurrent requests).
   */
  public void addOperationInProgress(AbstractOperation operation)
         throws DirectoryException
  {
    int messageID = operation.getMessageID();

    // We need to grab a lock to ensure that no one else can add operations to
    // the queue while we are performing some preliminary checks.
    synchronized (opsInProgressLock)
    {
      try
      {
        // If we're already in the process of disconnecting the client, then
        // reject the operation.
        if (disconnectRequested)
        {
          Message message = WARN_LDAP_CLIENT_DISCONNECT_IN_PROGRESS.get();
          throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                       message);
        }


        // See if there is already an operation in progress with the same
        // message ID.  If so, then we can't allow it.
        AbstractOperation op = operationsInProgress.get(messageID);
        if (op != null)
        {
          Message message =
               WARN_LDAP_CLIENT_DUPLICATE_MESSAGE_ID.get(messageID);
          throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
        }


        // Add the operation to the list of operations in progress for this
        // connection.
        operationsInProgress.put(messageID, operation);


        // Try to add the operation to the work queue.
        DirectoryServer.enqueueRequest(operation);
      }
      catch (DirectoryException de)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, de);
        }

        operationsInProgress.remove(messageID);
        lastCompletionTime.set(TimeThread.getTime());

        throw de;
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        Message message =
            WARN_LDAP_CLIENT_CANNOT_ENQUEUE.get(getExceptionMessage(e));
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, e);
      }
    }
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
    AbstractOperation operation = operationsInProgress.remove(messageID);
    if (operation == null)
    {
      return false;
    }
    else
    {
      lastCompletionTime.set(TimeThread.getTime());
      return true;
    }
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
    AbstractOperation op = operationsInProgress.get(messageID);
    if (op == null)
    {
      // See if the operation is in the list of persistent searches.
      for (PersistentSearch ps : getPersistentSearches())
      {
        if (ps.getSearchOperation().getMessageID() == messageID)
        {
          CancelResult cancelResult =
               ps.getSearchOperation().cancel(cancelRequest);

          if (keepStats && (cancelResult == CancelResult.CANCELED))
          {
            statTracker.updateAbandonedOperation();
          }

          return cancelResult;
        }
      }

      return CancelResult.NO_SUCH_OPERATION;
    }
    else
    {
      CancelResult cancelResult = op.cancel(cancelRequest);
      if (keepStats && (cancelResult == CancelResult.CANCELED))
      {
        statTracker.updateAbandonedOperation();
      }

      return op.cancel(cancelRequest);
    }
  }



  /**
   * Attempts to cancel all operations in progress on this connection.
   *
   * @param  cancelRequest  An object providing additional information about how
   *                        the cancel should be processed.
   */
  public void cancelAllOperations(CancelRequest cancelRequest)
  {
    // Make sure that no one can add any new operations.
    synchronized (opsInProgressLock)
    {
      try
      {
        for (AbstractOperation o : operationsInProgress.values())
        {
          try
          {
            CancelResult cancelResult = o.cancel(cancelRequest);
            if (keepStats && (cancelResult == CancelResult.CANCELED))
            {
              statTracker.updateAbandonedOperation();
            }
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }
          }
        }

        if (! (operationsInProgress.isEmpty() &&
               getPersistentSearches().isEmpty()))
        {
          lastCompletionTime.set(TimeThread.getTime());
        }

        operationsInProgress.clear();


        for (PersistentSearch persistentSearch : getPersistentSearches())
        {
          DirectoryServer.deregisterPersistentSearch(persistentSearch);
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }
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
    // Make sure that no one can add any new operations.
    synchronized (opsInProgressLock)
    {
      try
      {
        for (int msgID : operationsInProgress.keySet())
        {
          if (msgID == messageID)
          {
            continue;
          }

          AbstractOperation o = operationsInProgress.get(msgID);
          if (o != null)
          {
            try
            {
              CancelResult cancelResult = o.cancel(cancelRequest);
              if (keepStats && (cancelResult == CancelResult.CANCELED))
              {
                statTracker.updateAbandonedOperation();
              }
            }
            catch (Exception e)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);
              }
            }
          }

          operationsInProgress.remove(msgID);
          lastCompletionTime.set(TimeThread.getTime());
        }


        for (PersistentSearch persistentSearch : getPersistentSearches())
        {
          DirectoryServer.deregisterPersistentSearch(persistentSearch);
          lastCompletionTime.set(TimeThread.getTime());
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public Selector getWriteSelector()
  {
    Selector selector = writeSelector.get();
    if (selector == null)
    {
      try
      {
        selector = Selector.open();
        if (! writeSelector.compareAndSet(null, selector))
        {
          selector.close();
          selector = writeSelector.get();
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }

    return selector;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public long getMaxBlockedWriteTimeLimit()
  {
    return connectionHandler.getMaxBlockedWriteTimeLimit();
  }



  /**
   * Process the information contained in the provided byte buffer as an ASN.1
   * element.  It may take several calls to this method in order to get all the
   * information necessary to decode a single ASN.1 element, but it may also be
   * possible that there are multiple elements (or at least fragments of
   * multiple elements) in a single buffer.  This will fully process whatever
   * the client provided and set up the appropriate state information to make it
   * possible to pick up in the right place the next time around.
   *
   * @param  buffer  The buffer containing the data to be processed.  It must be
   *                 ready for reading (i.e., it should have been flipped by the
   *                 caller), and the data provided must be unencrypted (e.g.,
   *                 if the client is communicating over SSL, then the
   *                 decryption should happen before calling this method).
   *
   * @return  <CODE>true</CODE> if all the data in the provided buffer was
   *          processed and the client connection can remain established, or
   *          <CODE>false</CODE> if a decoding error occurred and requests from
   *          this client should no longer be processed.  Note that if this
   *          method does return <CODE>false</CODE>, then it must have already
   *          disconnected the client, and upon returning the request handler
   *          should remove it from the associated selector.
   */
  public boolean processDataRead(ByteBuffer buffer)
  {
    if (debugEnabled())
    {
      TRACER.debugData(DebugLogLevel.VERBOSE, buffer);
    }


    int bytesAvailable = buffer.limit() - buffer.position();

    if (keepStats)
    {
      statTracker.updateBytesRead(bytesAvailable);
    }

    while (bytesAvailable > 0)
    {
      switch (elementReadState)
      {
        case ELEMENT_READ_STATE_NEED_TYPE:
          // Read just the type and then loop again to see if there is more.
          elementType = buffer.get();
          bytesAvailable--;
          elementReadState = ELEMENT_READ_STATE_NEED_FIRST_LENGTH_BYTE;
          continue;


        case ELEMENT_READ_STATE_NEED_FIRST_LENGTH_BYTE:
          // Get the first length byte and see if it is a single-byte or
          // multi-byte length.
          byte firstLengthByte = buffer.get();
          bytesAvailable--;
          elementLengthBytesNeeded = (firstLengthByte & 0x7F);
          if (elementLengthBytesNeeded == firstLengthByte)
          {
            elementLength = firstLengthByte;

            // If the length is zero, then it cannot be a valid LDAP message.
            if (elementLength == 0)
            {
              disconnect(DisconnectReason.PROTOCOL_ERROR, true,
                         ERR_LDAP_CLIENT_DECODE_ZERO_BYTE_VALUE.get());
              return false;
            }

            // Make sure that the element is not larger than the maximum allowed
            // message size.
            if ((connectionHandler.getMaxRequestSize() > 0) &&
                (elementLength > connectionHandler.getMaxRequestSize()))
            {
              Message m = ERR_LDAP_CLIENT_DECODE_MAX_REQUEST_SIZE_EXCEEDED.get(
                elementLength, connectionHandler.getMaxRequestSize());
              disconnect(DisconnectReason.MAX_REQUEST_SIZE_EXCEEDED, true, m);
              return false;
            }

            elementValue            = new byte[elementLength];
            elementValueBytesRead   = 0;
            elementValueBytesNeeded = elementLength;
            elementReadState        = ELEMENT_READ_STATE_NEED_VALUE_BYTES;
            continue;
          }
          else
          {
            if (elementLengthBytesNeeded > 4)
            {
              // We cannot handle multi-byte lengths in which more than four
              // bytes are used to encode the length.
              Message m = ERR_LDAP_CLIENT_DECODE_INVALID_MULTIBYTE_LENGTH.get(
                elementLengthBytesNeeded);
              disconnect(DisconnectReason.PROTOCOL_ERROR, true, m);
              return false;
            }

            elementLength = 0x00;
            if (elementLengthBytesNeeded <= bytesAvailable)
            {
              // We can read the entire length, so do it.
              while (elementLengthBytesNeeded > 0)
              {
                elementLength = (elementLength << 8) | (buffer.get() & 0xFF);
                bytesAvailable--;
                elementLengthBytesNeeded--;
              }

              // If the length is zero, then it cannot be a valid LDAP message.
              if (elementLength == 0)
              {
                disconnect(DisconnectReason.PROTOCOL_ERROR, true,
                           ERR_LDAP_CLIENT_DECODE_ZERO_BYTE_VALUE.get());
                return false;
              }

              // Make sure that the element is not larger than the maximum
              // allowed message size.
              if ((connectionHandler.getMaxRequestSize() > 0) &&
                  (elementLength > connectionHandler.getMaxRequestSize()))
              {
                disconnect(DisconnectReason.MAX_REQUEST_SIZE_EXCEEDED, true,
                           ERR_LDAP_CLIENT_DECODE_MAX_REQUEST_SIZE_EXCEEDED.get(
                                   elementLength,
                                   connectionHandler.getMaxRequestSize()));
                return false;
              }

              elementValue            = new byte[elementLength];
              elementValueBytesRead   = 0;
              elementValueBytesNeeded = elementLength;
              elementReadState        = ELEMENT_READ_STATE_NEED_VALUE_BYTES;
              continue;
            }
            else
            {
              // We can't read the entire length, so just read what is
              // available.
              while (bytesAvailable > 0)
              {
                elementLength = (elementLength << 8) | (buffer.get() & 0xFF);
                bytesAvailable--;
                elementLengthBytesNeeded--;
              }

              return true;
            }
          }


        case ELEMENT_READ_STATE_NEED_ADDITIONAL_LENGTH_BYTES:
          if (bytesAvailable >= elementLengthBytesNeeded)
          {
            // We have enough data available to be able to read the entire
            // length.  Do so.
            while (elementLengthBytesNeeded > 0)
            {
              elementLength = (elementLength << 8) | (buffer.get() & 0xFF);
              bytesAvailable--;
              elementLengthBytesNeeded--;
            }

            // If the length is zero, then it cannot be a valid LDAP message.
            if (elementLength == 0)
            {
              disconnect(DisconnectReason.PROTOCOL_ERROR, true,
                         ERR_LDAP_CLIENT_DECODE_ZERO_BYTE_VALUE.get());
              return false;
            }

            // Make sure that the element is not larger than the maximum allowed
            // message size.
            if ((connectionHandler.getMaxRequestSize() > 0) &&
                (elementLength > connectionHandler.getMaxRequestSize()))
            {
              disconnect(DisconnectReason.MAX_REQUEST_SIZE_EXCEEDED, true,
                         ERR_LDAP_CLIENT_DECODE_MAX_REQUEST_SIZE_EXCEEDED.get(
                                 elementLength,
                                 connectionHandler.getMaxRequestSize()));
              return false;
            }

            elementValue            = new byte[elementLength];
            elementValueBytesRead   = 0;
            elementValueBytesNeeded = elementLength;
            elementReadState        = ELEMENT_READ_STATE_NEED_VALUE_BYTES;
            continue;
          }
          else
          {
            // We still don't have enough data to complete the length, so just
            // read as much as possible.
            while (bytesAvailable > 0)
            {
              elementLength = (elementLength << 8) | (buffer.get() & 0xFF);
              bytesAvailable--;
              elementLengthBytesNeeded--;
            }

            return true;
          }


        case ELEMENT_READ_STATE_NEED_VALUE_BYTES:
          if (bytesAvailable >= elementValueBytesNeeded)
          {
            // We have enough data available to fully read the value.  Finish
            // reading the information and convert it to an ASN.1 element.  Then
            // decode that as an LDAP message.
            buffer.get(elementValue, elementValueBytesRead,
                       elementValueBytesNeeded);
            elementValueBytesRead += elementValueBytesNeeded;
            bytesAvailable -= elementValueBytesNeeded;
            elementReadState = ELEMENT_READ_STATE_NEED_TYPE;

            ASN1Sequence requestSequence;
            try
            {
              requestSequence = ASN1Sequence.decodeAsSequence(elementType,
                                                              elementValue);
              TRACER.debugProtocolElement(DebugLogLevel.VERBOSE,
                                          requestSequence);
            }
            catch (Exception e)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);
              }
              Message m = ERR_LDAP_CLIENT_DECODE_ASN1_FAILED.get(
                String.valueOf(e));
              disconnect(DisconnectReason.PROTOCOL_ERROR, true, m);
              return false;
            }

            LDAPMessage requestMessage;
            try
            {
              requestMessage = LDAPMessage.decode(requestSequence);
              TRACER.debugProtocolElement(DebugLogLevel.VERBOSE,
                                          requestMessage);
            }
            catch (Exception e)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);
              }
              Message m = ERR_LDAP_CLIENT_DECODE_LDAP_MESSAGE_FAILED.get(
                String.valueOf(e));
              disconnect(DisconnectReason.PROTOCOL_ERROR, true, m);
              return false;
            }

            if (processLDAPMessage(requestMessage))
            {
              continue;
            }
            else
            {
              return false;
            }
          }
          else
          {
            // We can't read all the value, so just read as much as we have
            // available and pick it up again the next time around.
            buffer.get(elementValue, elementValueBytesRead, bytesAvailable);
            elementValueBytesRead   += bytesAvailable;
            elementValueBytesNeeded -= bytesAvailable;
            return true;
          }


        default:
          // This should never happen.  There is an invalid internal read state.
          // The only recourse that we have is to log a message and disconnect
          // the client.
          Message message =
              ERR_LDAP_CLIENT_INVALID_DECODE_STATE.get(elementReadState);
          logError(message);
          disconnect(DisconnectReason.SERVER_ERROR, true, message);
          return false;
      }
    }


    // If we've gotten here, then all of the data must have been processed
    // properly so we can return true.
    return true;
  }



  /**
   * Processes the provided LDAP message read from the client and takes
   * whatever action is appropriate.  For most requests, this will include
   * placing the operation in the work queue.  Certain requests (in particular,
   * abandons and unbinds) will be processed directly.
   *
   * @param  message  The LDAP message to process.
   *
   * @return  <CODE>true</CODE> if the appropriate action was taken for the
   *          request, or <CODE>false</CODE> if there was a fatal error and
   *          the client has been disconnected as a result, or if the client
   *          unbound from the server.
   */
  private boolean processLDAPMessage(LDAPMessage message)
  {
    if (keepStats)
    {
      statTracker.updateMessageRead(message);
    }

    ArrayList<Control> opControls;
    ArrayList<LDAPControl> ldapControls = message.getControls();
    if ((ldapControls == null) || ldapControls.isEmpty())
    {
      opControls = null;
    }
    else
    {
      opControls = new ArrayList<Control>(ldapControls.size());
      for (LDAPControl c : ldapControls)
      {
        opControls.add(c.getControl());
      }
    }


    // FIXME -- See if there is a bind in progress.  If so, then deny most
    // kinds of operations.


    // Figure out what type of operation we're dealing with based on the LDAP
    // message.  Abandon and unbind requests will be processed here.  All other
    // types of requests will be encapsulated into operations and put into the
    // work queue to be picked up by a worker thread.  Any other kinds of
    // LDAP messages (e.g., response messages) are illegal and will result in
    // the connection being terminated.
    try
    {
      switch (message.getProtocolOpType())
      {
        case OP_TYPE_ABANDON_REQUEST:
          return processAbandonRequest(message, opControls);
        case OP_TYPE_ADD_REQUEST:
          return processAddRequest(message, opControls);
        case OP_TYPE_BIND_REQUEST:
          return processBindRequest(message, opControls);
        case OP_TYPE_COMPARE_REQUEST:
          return processCompareRequest(message, opControls);
        case OP_TYPE_DELETE_REQUEST:
          return processDeleteRequest(message, opControls);
        case OP_TYPE_EXTENDED_REQUEST:
          return processExtendedRequest(message, opControls);
        case OP_TYPE_MODIFY_REQUEST:
          return processModifyRequest(message, opControls);
        case OP_TYPE_MODIFY_DN_REQUEST:
          return processModifyDNRequest(message, opControls);
        case OP_TYPE_SEARCH_REQUEST:
          return processSearchRequest(message, opControls);
        case OP_TYPE_UNBIND_REQUEST:
          return processUnbindRequest(message, opControls);
        default:
          Message msg = ERR_LDAP_DISCONNECT_DUE_TO_INVALID_REQUEST_TYPE.get(
                  message.getProtocolOpName(), message.getMessageID());
          disconnect(DisconnectReason.PROTOCOL_ERROR, true, msg);
          return false;
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message msg = ERR_LDAP_DISCONNECT_DUE_TO_PROCESSING_FAILURE.get(
              message.getProtocolOpName(),
              message.getMessageID(), String.valueOf(e));
      disconnect(DisconnectReason.SERVER_ERROR, true, msg);
      return false;
    }
  }



  /**
   * Processes the provided LDAP message as an abandon request.
   *
   * @param  message   The LDAP message containing the abandon request to
   *                   process.
   * @param  controls  The set of pre-decoded request controls contained in the
   *                   message.
   *
   * @return  <CODE>true</CODE> if the request was processed successfully, or
   *          <CODE>false</CODE> if not and the connection has been closed as a
   *          result (it is the responsibility of this method to close the
   *          connection).
   */
  private boolean processAbandonRequest(LDAPMessage message,
                                        ArrayList<Control> controls)
  {
    AbandonRequestProtocolOp protocolOp = message.getAbandonRequestProtocolOp();
    AbandonOperationBasis abandonOp =
         new AbandonOperationBasis(this, nextOperationID.getAndIncrement(),
                              message.getMessageID(), controls,
                              protocolOp.getIDToAbandon());

    abandonOp.run();
    if (keepStats && (abandonOp.getResultCode() == ResultCode.CANCELED))
    {
      statTracker.updateAbandonedOperation();
    }

    return connectionValid;
  }



  /**
   * Processes the provided LDAP message as an add request.
   *
   * @param  message   The LDAP message containing the add request to process.
   * @param  controls  The set of pre-decoded request controls contained in the
   *                   message.
   *
   * @return  <CODE>true</CODE> if the request was processed successfully, or
   *          <CODE>false</CODE> if not and the connection has been closed as a
   *          result (it is the responsibility of this method to close the
   *          connection).
   */
  private boolean processAddRequest(LDAPMessage message,
                                    ArrayList<Control> controls)
  {
    if ((ldapVersion == 2) && (controls != null) && (! controls.isEmpty()))
    {
      // LDAPv2 clients aren't allowed to send controls.
      AddResponseProtocolOp responseOp =
           new AddResponseProtocolOp(LDAPResultCode.PROTOCOL_ERROR,
                    ERR_LDAPV2_CONTROLS_NOT_ALLOWED.get());
      sendLDAPMessage(securityProvider,
                      new LDAPMessage(message.getMessageID(), responseOp));
      disconnect(DisconnectReason.PROTOCOL_ERROR, false,
                 ERR_LDAPV2_CONTROLS_NOT_ALLOWED.get());
      return false;
    }

    // Create the add operation and add it into the work queue.
    AddRequestProtocolOp protocolOp = message.getAddRequestProtocolOp();
    AddOperationBasis addOp =
         new AddOperationBasis(this, nextOperationID.getAndIncrement(),
                          message.getMessageID(), controls, protocolOp.getDN(),
                          protocolOp.getAttributes());

    try
    {
      addOperationInProgress(addOp);
    }
    catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }

      AddResponseProtocolOp responseOp =
           new AddResponseProtocolOp(de.getResultCode().getIntValue(),
                                     de.getMessageObject(), de.getMatchedDN(),
                                     de.getReferralURLs());

      List<Control> responseControls = addOp.getResponseControls();
      ArrayList<LDAPControl> responseLDAPControls =
           new ArrayList<LDAPControl>(responseControls.size());
      for (Control c : responseControls)
      {
        responseLDAPControls.add(new LDAPControl(c));
      }

      sendLDAPMessage(securityProvider,
                      new LDAPMessage(message.getMessageID(), responseOp,
                                      responseLDAPControls));
    }


    return connectionValid;
  }



  /**
   * Processes the provided LDAP message as a bind request.
   *
   * @param  message   The LDAP message containing the bind request to process.
   * @param  controls  The set of pre-decoded request controls contained in the
   *                   message.
   *
   * @return  <CODE>true</CODE> if the request was processed successfully, or
   *          <CODE>false</CODE> if not and the connection has been closed as a
   *          result (it is the responsibility of this method to close the
   *          connection).
   */
  private boolean processBindRequest(LDAPMessage message,
                                     ArrayList<Control> controls)
  {
    BindRequestProtocolOp protocolOp = message.getBindRequestProtocolOp();

    // See if this is an LDAPv2 bind request, and if so whether that should be
    // allowed.
    String versionString;
    switch (ldapVersion = protocolOp.getProtocolVersion())
    {
      case 2:
        versionString = "2";

        if (! connectionHandler.allowLDAPv2())
        {
          BindResponseProtocolOp responseOp =
               new BindResponseProtocolOp(
                        LDAPResultCode.INAPPROPRIATE_AUTHENTICATION,
                        ERR_LDAPV2_CLIENTS_NOT_ALLOWED.get());
          sendLDAPMessage(securityProvider,
                          new LDAPMessage(message.getMessageID(), responseOp));
          disconnect(DisconnectReason.PROTOCOL_ERROR, false,
                     ERR_LDAPV2_CLIENTS_NOT_ALLOWED.get());
          return false;
        }

        if ((controls != null) && (! controls.isEmpty()))
        {
          // LDAPv2 clients aren't allowed to send controls.
          BindResponseProtocolOp responseOp =
               new BindResponseProtocolOp(LDAPResultCode.PROTOCOL_ERROR,
                        ERR_LDAPV2_CONTROLS_NOT_ALLOWED.get());
          sendLDAPMessage(securityProvider,
                          new LDAPMessage(message.getMessageID(), responseOp));
          disconnect(DisconnectReason.PROTOCOL_ERROR, false,
                     ERR_LDAPV2_CONTROLS_NOT_ALLOWED.get());
          return false;
        }

        break;
      case 3:
        versionString = "3";
        break;
      default:
        versionString = String.valueOf(ldapVersion);
        break;
    }


    ASN1OctetString bindDN = protocolOp.getDN();

    BindOperationBasis bindOp;
    switch (protocolOp.getAuthenticationType())
    {
      case SIMPLE:
        bindOp = new BindOperationBasis(this, nextOperationID.getAndIncrement(),
                                   message.getMessageID(), controls,
                                   versionString, bindDN,
                                   protocolOp.getSimplePassword());
        break;
      case SASL:
        bindOp = new BindOperationBasis(this, nextOperationID.getAndIncrement(),
                                   message.getMessageID(), controls,
                                   versionString, bindDN,
                                   protocolOp.getSASLMechanism(),
                                   protocolOp.getSASLCredentials());
        break;
      default:
        // This is an invalid authentication type, and therefore a protocol
        // error.  As per RFC 2251, a protocol error in a bind request must
        // result in terminating the connection.
        Message msg =
                ERR_LDAP_INVALID_BIND_AUTH_TYPE.get(message.getMessageID(),
                          String.valueOf(protocolOp.getAuthenticationType()));
        disconnect(DisconnectReason.PROTOCOL_ERROR, true, msg);
        return false;
    }

    // Add the operation into the work queue.
    try
    {
      addOperationInProgress(bindOp);
    }
    catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }

      BindResponseProtocolOp responseOp =
           new BindResponseProtocolOp(de.getResultCode().getIntValue(),
                                      de.getMessageObject(), de.getMatchedDN(),
                                      de.getReferralURLs());

      List<Control> responseControls = bindOp.getResponseControls();
      ArrayList<LDAPControl> responseLDAPControls =
           new ArrayList<LDAPControl>(responseControls.size());
      for (Control c : responseControls)
      {
        responseLDAPControls.add(new LDAPControl(c));
      }

      sendLDAPMessage(securityProvider,
                      new LDAPMessage(message.getMessageID(), responseOp,
                                      responseLDAPControls));

      // If it was a protocol error, then terminate the connection.
      if (de.getResultCode() == ResultCode.PROTOCOL_ERROR)
      {
        Message msg = ERR_LDAP_DISCONNECT_DUE_TO_BIND_PROTOCOL_ERROR.get(
                message.getMessageID(), de.getMessageObject());
        disconnect(DisconnectReason.PROTOCOL_ERROR, true, msg);
      }
    }


    return connectionValid;
  }



  /**
   * Processes the provided LDAP message as a compare request.
   *
   * @param  message   The LDAP message containing the compare request to
   *                   process.
   * @param  controls  The set of pre-decoded request controls contained in the
   *                   message.
   *
   * @return  <CODE>true</CODE> if the request was processed successfully, or
   *          <CODE>false</CODE> if not and the connection has been closed as a
   *          result (it is the responsibility of this method to close the
   *          connection).
   */
  private boolean processCompareRequest(LDAPMessage message,
                                        ArrayList<Control> controls)
  {
    if ((ldapVersion == 2) && (controls != null) && (! controls.isEmpty()))
    {
      // LDAPv2 clients aren't allowed to send controls.
      CompareResponseProtocolOp responseOp =
           new CompareResponseProtocolOp(LDAPResultCode.PROTOCOL_ERROR,
                    ERR_LDAPV2_CONTROLS_NOT_ALLOWED.get());
      sendLDAPMessage(securityProvider,
                      new LDAPMessage(message.getMessageID(), responseOp));
      disconnect(DisconnectReason.PROTOCOL_ERROR, false,
                 ERR_LDAPV2_CONTROLS_NOT_ALLOWED.get());
      return false;
    }

    CompareRequestProtocolOp protocolOp = message.getCompareRequestProtocolOp();
    CompareOperationBasis compareOp =
         new CompareOperationBasis(this, nextOperationID.getAndIncrement(),
                              message.getMessageID(), controls,
                              protocolOp.getDN(), protocolOp.getAttributeType(),
                              protocolOp.getAssertionValue());

    // Add the operation into the work queue.
    try
    {
      addOperationInProgress(compareOp);
    }
    catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }

      CompareResponseProtocolOp responseOp =
           new CompareResponseProtocolOp(de.getResultCode().getIntValue(),
                                         de.getMessageObject(),
                                         de.getMatchedDN(),
                                         de.getReferralURLs());

      List<Control> responseControls = compareOp.getResponseControls();
      ArrayList<LDAPControl> responseLDAPControls =
           new ArrayList<LDAPControl>(responseControls.size());
      for (Control c : responseControls)
      {
        responseLDAPControls.add(new LDAPControl(c));
      }

      sendLDAPMessage(securityProvider,
                      new LDAPMessage(message.getMessageID(), responseOp,
                                      responseLDAPControls));
    }


    return connectionValid;
  }



  /**
   * Processes the provided LDAP message as a delete request.
   *
   * @param  message   The LDAP message containing the delete request to
   *                   process.
   * @param  controls  The set of pre-decoded request controls contained in the
   *                   message.
   *
   * @return  <CODE>true</CODE> if the request was processed successfully, or
   *          <CODE>false</CODE> if not and the connection has been closed as a
   *          result (it is the responsibility of this method to close the
   *          connection).
   */
  private boolean processDeleteRequest(LDAPMessage message,
                                       ArrayList<Control> controls)
  {
    if ((ldapVersion == 2) && (controls != null) && (! controls.isEmpty()))
    {
      // LDAPv2 clients aren't allowed to send controls.
      DeleteResponseProtocolOp responseOp =
           new DeleteResponseProtocolOp(LDAPResultCode.PROTOCOL_ERROR,
                    ERR_LDAPV2_CONTROLS_NOT_ALLOWED.get());
      sendLDAPMessage(securityProvider,
                      new LDAPMessage(message.getMessageID(), responseOp));
      disconnect(DisconnectReason.PROTOCOL_ERROR, false,
                 ERR_LDAPV2_CONTROLS_NOT_ALLOWED.get());
      return false;
    }

    DeleteRequestProtocolOp protocolOp = message.getDeleteRequestProtocolOp();
    DeleteOperationBasis deleteOp =
         new DeleteOperationBasis(this, nextOperationID.getAndIncrement(),
                             message.getMessageID(), controls,
                             protocolOp.getDN());

    // Add the operation into the work queue.
    try
    {
      addOperationInProgress(deleteOp);
    }
    catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }

      DeleteResponseProtocolOp responseOp =
           new DeleteResponseProtocolOp(de.getResultCode().getIntValue(),
                                        de.getMessageObject(),
                                        de.getMatchedDN(),
                                        de.getReferralURLs());

      List<Control> responseControls = deleteOp.getResponseControls();
      ArrayList<LDAPControl> responseLDAPControls =
           new ArrayList<LDAPControl>(responseControls.size());
      for (Control c : responseControls)
      {
        responseLDAPControls.add(new LDAPControl(c));
      }

      sendLDAPMessage(securityProvider,
                      new LDAPMessage(message.getMessageID(), responseOp,
                                      responseLDAPControls));
    }


    return connectionValid;
  }



  /**
   * Processes the provided LDAP message as an extended request.
   *
   * @param  message   The LDAP message containing the extended request to
   *                   process.
   * @param  controls  The set of pre-decoded request controls contained in the
   *                   message.
   *
   * @return  <CODE>true</CODE> if the request was processed successfully, or
   *          <CODE>false</CODE> if not and the connection has been closed as a
   *          result (it is the responsibility of this method to close the
   *          connection).
   */
  private boolean processExtendedRequest(LDAPMessage message,
                                         ArrayList<Control> controls)
  {
    // See if this is an LDAPv2 client.  If it is, then they should not be
    // issuing extended requests.  We can't send a response that we can be sure
    // they can understand, so we have no choice but to close the connection.
    if (ldapVersion == 2)
    {
      Message msg = ERR_LDAPV2_EXTENDED_REQUEST_NOT_ALLOWED.get(
          getConnectionID(), message.getMessageID());
      logError(msg);
      disconnect(DisconnectReason.PROTOCOL_ERROR, false, msg);
      return false;
    }


    // FIXME -- Do we need to handle certain types of request here?
    // -- StartTLS requests
    // -- Cancel requests


    ExtendedRequestProtocolOp protocolOp =
         message.getExtendedRequestProtocolOp();
    ExtendedOperationBasis extendedOp =
         new ExtendedOperationBasis(this, nextOperationID.getAndIncrement(),
                               message.getMessageID(), controls,
                               protocolOp.getOID(), protocolOp.getValue());

    // Add the operation into the work queue.
    try
    {
      addOperationInProgress(extendedOp);
    }
    catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }

      ExtendedResponseProtocolOp responseOp =
           new ExtendedResponseProtocolOp(de.getResultCode().getIntValue(),
                                          de.getMessageObject(),
                                          de.getMatchedDN(),
                                          de.getReferralURLs());

      List<Control> responseControls = extendedOp.getResponseControls();
      ArrayList<LDAPControl> responseLDAPControls =
           new ArrayList<LDAPControl>(responseControls.size());
      for (Control c : responseControls)
      {
        responseLDAPControls.add(new LDAPControl(c));
      }

      sendLDAPMessage(securityProvider,
                      new LDAPMessage(message.getMessageID(), responseOp,
                                      responseLDAPControls));
    }


    return connectionValid;
  }



  /**
   * Processes the provided LDAP message as a modify request.
   *
   * @param  message   The LDAP message containing the modify request to
   *                   process.
   * @param  controls  The set of pre-decoded request controls contained in the
   *                   message.
   *
   * @return  <CODE>true</CODE> if the request was processed successfully, or
   *          <CODE>false</CODE> if not and the connection has been closed as a
   *          result (it is the responsibility of this method to close the
   *          connection).
   */
  private boolean processModifyRequest(LDAPMessage message,
                                       ArrayList<Control> controls)
  {
    if ((ldapVersion == 2) && (controls != null) && (! controls.isEmpty()))
    {
      // LDAPv2 clients aren't allowed to send controls.
      ModifyResponseProtocolOp responseOp =
           new ModifyResponseProtocolOp(LDAPResultCode.PROTOCOL_ERROR,
                    ERR_LDAPV2_CONTROLS_NOT_ALLOWED.get());
      sendLDAPMessage(securityProvider,
                      new LDAPMessage(message.getMessageID(), responseOp));
      disconnect(DisconnectReason.PROTOCOL_ERROR, false,
                 ERR_LDAPV2_CONTROLS_NOT_ALLOWED.get());
      return false;
    }

    ModifyRequestProtocolOp protocolOp = message.getModifyRequestProtocolOp();
    ModifyOperationBasis modifyOp =
         new ModifyOperationBasis(this, nextOperationID.getAndIncrement(),
                             message.getMessageID(), controls,
                             protocolOp.getDN(), protocolOp.getModifications());

    // Add the operation into the work queue.
    try
    {
      addOperationInProgress(modifyOp);
    }
    catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }

      ModifyResponseProtocolOp responseOp =
           new ModifyResponseProtocolOp(de.getResultCode().getIntValue(),
                                        de.getMessageObject(),
                                        de.getMatchedDN(),
                                        de.getReferralURLs());

      List<Control> responseControls = modifyOp.getResponseControls();
      ArrayList<LDAPControl> responseLDAPControls =
           new ArrayList<LDAPControl>(responseControls.size());
      for (Control c : responseControls)
      {
        responseLDAPControls.add(new LDAPControl(c));
      }

      sendLDAPMessage(securityProvider,
                      new LDAPMessage(message.getMessageID(), responseOp,
                                      responseLDAPControls));
    }


    return connectionValid;
  }



  /**
   * Processes the provided LDAP message as a modify DN request.
   *
   * @param  message   The LDAP message containing the modify DN request to
   *                   process.
   * @param  controls  The set of pre-decoded request controls contained in the
   *                   message.
   *
   * @return  <CODE>true</CODE> if the request was processed successfully, or
   *          <CODE>false</CODE> if not and the connection has been closed as a
   *          result (it is the responsibility of this method to close the
   *          connection).
   */
  private boolean processModifyDNRequest(LDAPMessage message,
                                         ArrayList<Control> controls)
  {
    if ((ldapVersion == 2) && (controls != null) && (! controls.isEmpty()))
    {
      // LDAPv2 clients aren't allowed to send controls.
      ModifyDNResponseProtocolOp responseOp =
           new ModifyDNResponseProtocolOp(LDAPResultCode.PROTOCOL_ERROR,
                    ERR_LDAPV2_CONTROLS_NOT_ALLOWED.get());
      sendLDAPMessage(securityProvider,
                      new LDAPMessage(message.getMessageID(), responseOp));
      disconnect(DisconnectReason.PROTOCOL_ERROR, false,
                 ERR_LDAPV2_CONTROLS_NOT_ALLOWED.get());
      return false;
    }

    ModifyDNRequestProtocolOp protocolOp =
         message.getModifyDNRequestProtocolOp();
    ModifyDNOperationBasis modifyDNOp =
         new ModifyDNOperationBasis(this, nextOperationID.getAndIncrement(),
                               message.getMessageID(), controls,
                               protocolOp.getEntryDN(), protocolOp.getNewRDN(),
                               protocolOp.deleteOldRDN(),
                               protocolOp.getNewSuperior());

    // Add the operation into the work queue.
    try
    {
      addOperationInProgress(modifyDNOp);
    }
    catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }

      ModifyDNResponseProtocolOp responseOp =
           new ModifyDNResponseProtocolOp(de.getResultCode().getIntValue(),
                                          de.getMessageObject(),
                                          de.getMatchedDN(),
                                          de.getReferralURLs());

      List<Control> responseControls = modifyDNOp.getResponseControls();
      ArrayList<LDAPControl> responseLDAPControls =
           new ArrayList<LDAPControl>(responseControls.size());
      for (Control c : responseControls)
      {
        responseLDAPControls.add(new LDAPControl(c));
      }

      sendLDAPMessage(securityProvider,
                      new LDAPMessage(message.getMessageID(), responseOp,
                                      responseLDAPControls));
    }


    return connectionValid;
  }



  /**
   * Processes the provided LDAP message as a search request.
   *
   * @param  message   The LDAP message containing the search request to
   *                   process.
   * @param  controls  The set of pre-decoded request controls contained in the
   *                   message.
   *
   * @return  <CODE>true</CODE> if the request was processed successfully, or
   *          <CODE>false</CODE> if not and the connection has been closed as a
   *          result (it is the responsibility of this method to close the
   *          connection).
   */
  private boolean processSearchRequest(LDAPMessage message,
                                       ArrayList<Control> controls)
  {
    if ((ldapVersion == 2) && (controls != null) && (! controls.isEmpty()))
    {
      // LDAPv2 clients aren't allowed to send controls.
      SearchResultDoneProtocolOp responseOp =
           new SearchResultDoneProtocolOp(LDAPResultCode.PROTOCOL_ERROR,
                    ERR_LDAPV2_CONTROLS_NOT_ALLOWED.get());
      sendLDAPMessage(securityProvider,
                      new LDAPMessage(message.getMessageID(), responseOp));
      disconnect(DisconnectReason.PROTOCOL_ERROR, false,
                 ERR_LDAPV2_CONTROLS_NOT_ALLOWED.get());
      return false;
    }

    SearchRequestProtocolOp protocolOp = message.getSearchRequestProtocolOp();
    SearchOperationBasis searchOp =
         new SearchOperationBasis(this, nextOperationID.getAndIncrement(),
                             message.getMessageID(), controls,
                             protocolOp.getBaseDN(), protocolOp.getScope(),
                             protocolOp.getDereferencePolicy(),
                             protocolOp.getSizeLimit(),
                             protocolOp.getTimeLimit(),
                             protocolOp.getTypesOnly(), protocolOp.getFilter(),
                             protocolOp.getAttributes());

    // Add the operation into the work queue.
    try
    {
      addOperationInProgress(searchOp);
    }
    catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }

      SearchResultDoneProtocolOp responseOp =
           new SearchResultDoneProtocolOp(de.getResultCode().getIntValue(),
                                          de.getMessageObject(),
                                          de.getMatchedDN(),
                                          de.getReferralURLs());

      List<Control> responseControls = searchOp.getResponseControls();
      ArrayList<LDAPControl> responseLDAPControls =
           new ArrayList<LDAPControl>(responseControls.size());
      for (Control c : responseControls)
      {
        responseLDAPControls.add(new LDAPControl(c));
      }

      sendLDAPMessage(securityProvider,
                      new LDAPMessage(message.getMessageID(), responseOp,
                                      responseLDAPControls));
    }


    return connectionValid;
  }



  /**
   * Processes the provided LDAP message as an unbind request.
   *
   * @param  message   The LDAP message containing the unbind request to
   *                   process.
   * @param  controls  The set of pre-decoded request controls contained in the
   *                   message.
   *
   * @return  <CODE>true</CODE> if the request was processed successfully, or
   *          <CODE>false</CODE> if not and the connection has been closed as a
   *          result (it is the responsibility of this method to close the
   *          connection).
   */
  private boolean processUnbindRequest(LDAPMessage message,
                                       ArrayList<Control> controls)
  {
    UnbindOperationBasis unbindOp =
         new UnbindOperationBasis(this, nextOperationID.getAndIncrement(),
                              message.getMessageID(), controls);

    unbindOp.run();

    // The client connection will never be valid after an unbind.
    return false;
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
    buffer.append("\" source=\"");
    buffer.append(clientAddress);
    buffer.append(":");
    buffer.append(clientPort);
    buffer.append("\" destination=\"");
    buffer.append(serverAddress);
    buffer.append(":");
    buffer.append(connectionHandler.getListenPort());
    buffer.append("\" ldapVersion=\"");
    buffer.append(ldapVersion);
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

    buffer.append("\" opsInProgress=\"");
    buffer.append(operationsInProgress.size());
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
    buffer.append("LDAP client connection from ");
    buffer.append(clientAddress);
    buffer.append(":");
    buffer.append(clientPort);
    buffer.append(" to ");
    buffer.append(serverAddress);
    buffer.append(":");
    buffer.append(serverPort);
  }



  /**
   * Indicates whether TLS protection is actually available for the underlying
   * client connection.  If there is any reason that TLS protection cannot be
   * enabled on this client connection, then it should be appended to the
   * provided buffer.
   *
   * @param  unavailableReason  The buffer used to hold the reason that TLS is
   *                            not available on the underlying client
   *                            connection.
   *
   * @return  <CODE>true</CODE> if TLS is available on the underlying client
   *          connection, or <CODE>false</CODE> if it is not.
   */
  public boolean tlsProtectionAvailable(MessageBuilder unavailableReason)
  {
    // Make sure that this client connection does not already have some other
    // security provider enabled.
    if (! (securityProvider instanceof NullConnectionSecurityProvider))
    {

      unavailableReason.append(ERR_LDAP_TLS_EXISTING_SECURITY_PROVIDER.get(
              securityProvider.getSecurityMechanismName()));
      return false;
    }


    // Make sure that the connection handler allows the use of the StartTLS
    // operation.
    if (! connectionHandler.allowStartTLS())
    {

      unavailableReason.append(ERR_LDAP_TLS_STARTTLS_NOT_ALLOWED.get());
      return false;
    }


    // Make sure that the TLS security provider is available.
    if (tlsSecurityProvider == null)
    {
      try
      {
        TLSConnectionSecurityProvider tlsProvider =
             new TLSConnectionSecurityProvider();
        tlsProvider.initializeConnectionSecurityProvider(null);
        tlsProvider.setSSLClientAuthPolicy(
             connectionHandler.getSSLClientAuthPolicy());
        tlsProvider.setEnabledProtocols(
             connectionHandler.getEnabledSSLProtocols());
        tlsProvider.setEnabledCipherSuites(
             connectionHandler.getEnabledSSLCipherSuites());

        tlsSecurityProvider = (TLSConnectionSecurityProvider)
                              tlsProvider.newInstance(this, clientChannel);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        tlsSecurityProvider = null;


        unavailableReason.append(ERR_LDAP_TLS_CANNOT_CREATE_TLS_PROVIDER.get(
                stackTraceToSingleLineString(e)));
        return false;
      }
    }


    // If we've gotten here, then everything looks OK.
    return true;
  }



  /**
   * Installs the TLS connection security provider on this client connection.
   * If an error occurs in the process, then the underlying client connection
   * must be terminated and an exception must be thrown to indicate the
   * underlying cause.
   *
   * @throws  DirectoryException  If the TLS connection security provider could
   *                              not be enabled and the underlying connection
   *                              has been closed.
   */
  public void enableTLSConnectionSecurityProvider()
         throws DirectoryException
  {
    if (tlsSecurityProvider == null)
    {
      Message message = ERR_LDAP_TLS_NO_PROVIDER.get();

      disconnect(DisconnectReason.OTHER, false, message);
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message);
    }

    clearSecurityProvider = securityProvider;
    setConnectionSecurityProvider(tlsSecurityProvider);
  }



  /**
   * Disables the TLS connection security provider on this client connection.
   * This must also eliminate any authentication that had been performed on the
   * client connection so that it is in an anonymous state.  If a problem occurs
   * while attempting to revert the connection to a non-TLS-protected state,
   * then an exception must be thrown and the client connection must be
   * terminated.
   *
   * @throws  DirectoryException  If TLS protection cannot be reverted and the
   *                              underlying client connection has been closed.
   */
  public void disableTLSConnectionSecurityProvider()
         throws DirectoryException
  {
    Message message = ERR_LDAP_TLS_CLOSURE_NOT_ALLOWED.get();

    disconnect(DisconnectReason.OTHER, false, message);
    throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                 message);
  }



  /**
   * Sends a response to the client in the clear rather than through the
   * encrypted channel.  This should only be used when processing the StartTLS
   * extended operation to send the response in the clear after the TLS
   * negotiation has already been initiated.
   *
   * @param  operation  The operation for which to send the response in the
   *                    clear.
   *
   *
   * @throws  DirectoryException  If a problem occurs while sending the response
   *                              in the clear.
   */
  public void sendClearResponse(Operation operation)
         throws DirectoryException
  {
    if (clearSecurityProvider == null)
    {
      Message message = ERR_LDAP_NO_CLEAR_SECURITY_PROVIDER.get(toString());
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message);
    }

    sendLDAPMessage(clearSecurityProvider,
                    operationToResponseLDAPMessage(operation));
  }



  /**
   * {@inheritDoc}
   */
  public DN getKeyManagerProviderDN()
  {
    return connectionHandler.getKeyManagerProviderDN();
  }



  /**
   * {@inheritDoc}
   */
  public DN getTrustManagerProviderDN()
  {
    return connectionHandler.getTrustManagerProviderDN();
  }



  /**
   * Retrieves the alias of the server certificate that should be used
   * for operations requiring a server certificate.  The default
   * implementation returns {@code null} to indicate that any alias is
   * acceptable.
   *
   * @return  The alias of the server certificate that should be used
   *          for operations requring a server certificate, or
   *          {@code null} if any alias is acceptable.
   */
  public String getCertificateAlias()
  {
    return connectionHandler.getSSLServerCertNickname();
  }



  /**
   * Retrieves the length of time in milliseconds that this client
   * connection has been idle.
   * <BR><BR>
   * Note that the default implementation will always return zero.
   * Subclasses associated with connection handlers should override
   * this method if they wish to provided idle time limit
   * functionality.
   *
   * @return  The length of time in milliseconds that this client
   *          connection has been idle.
   */
  public long getIdleTime()
  {
    if (operationsInProgress.isEmpty() && getPersistentSearches().isEmpty())
    {
      return (TimeThread.getTime() - lastCompletionTime.get());
    }
    else
    {
      // There's at least one operation in progress, so it's not idle.
      return 0L;
    }
  }
}

