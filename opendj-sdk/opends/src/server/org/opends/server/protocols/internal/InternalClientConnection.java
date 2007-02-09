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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.protocols.internal;



import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.api.ConnectionSecurityProvider;
import org.opends.server.core.AddOperation;
import org.opends.server.core.BindOperation;
import org.opends.server.core.CompareOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ExtendedOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.Operation;
import org.opends.server.core.SearchOperation;
import org.opends.server.extensions.
            InternalConnectionSecurityProvider;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.ByteString;
import org.opends.server.types.CancelRequest;
import org.opends.server.types.CancelResult;
import org.opends.server.types.Control;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.IntermediateResponse;
import org.opends.server.types.Modification;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.RDN;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;
import org.opends.server.types.SearchScope;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.Debug.*;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ProtocolMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a pseudo-connection object that can be used for
 * performing internal operations.
 */
public class InternalClientConnection
       extends ClientConnection
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.protocols.internal." +
       "InternalClientConnection";


  // The message ID counter to use for internal connections.
  private static AtomicInteger nextMessageID;

  // The connection ID counter to use for internal connections.
  private static AtomicLong nextConnectionID;

  // The operation ID counter to use for operations on this
  // connection.
  private static AtomicLong nextOperationID;

  // The connection security provider for this client connection.
  private ConnectionSecurityProvider securityProvider;

  // The static connection for root-based connections.
  private static InternalClientConnection rootConnection;

  // The authentication info for this connection.
  private AuthenticationInfo authenticationInfo;

  // The empty operation list for this connection.
  private LinkedList<Operation> operationList;

  // The connection ID for this client connection.
  private long connectionID;



  static
  {
    nextMessageID    = new AtomicInteger(1);
    nextConnectionID = new AtomicLong(-1);
    nextOperationID  = new AtomicLong(0);
    rootConnection   = new InternalClientConnection();
  }



  /**
   * Creates a new internal client connection that will be
   * authenticated as a root user for which access control will not be
   * enforced.
   */
  private InternalClientConnection()
  {
    super();

    assert debugConstructor(CLASS_NAME);

    // This connection will be authenticated as a root user so that no
    // access control will be enforced.
    String commonName    = "Internal Client";
    String shortDNString = "cn=" + commonName;
    String fullDNString  = shortDNString + ",cn=Root DNs,cn=config";
    try
    {
      LinkedHashMap<ObjectClass,String> objectClasses =
           new LinkedHashMap<ObjectClass,String>();
      ObjectClass topOC = DirectoryServer.getTopObjectClass();
      ObjectClass personOC = DirectoryServer.getObjectClass(OC_PERSON,
                                                            true);
      ObjectClass rootOC = DirectoryServer.getObjectClass(OC_ROOT_DN,
                                                          true);

      objectClasses.put(topOC, topOC.getPrimaryName());
      objectClasses.put(personOC, personOC.getPrimaryName());
      objectClasses.put(rootOC, rootOC.getPrimaryName());


      LinkedHashMap<AttributeType,List<Attribute>> userAttrs =
           new LinkedHashMap<AttributeType,List<Attribute>>();
      AttributeType cnAT =
           DirectoryServer.getAttributeType(ATTR_COMMON_NAME, true);
      AttributeType snAT = DirectoryServer.getAttributeType(ATTR_SN,
                                                            true);
      AttributeType altDNAT =
           DirectoryServer.getAttributeType(
                ATTR_ROOTDN_ALTERNATE_BIND_DN, true);

      LinkedList<Attribute> attrList = new LinkedList<Attribute>();
      attrList.add(new Attribute(ATTR_COMMON_NAME, commonName));
      userAttrs.put(cnAT, attrList);

      attrList = new LinkedList<Attribute>();
      attrList.add(new Attribute(ATTR_SN, commonName));
      userAttrs.put(snAT, attrList);

      attrList = new LinkedList<Attribute>();
      attrList.add(new Attribute(ATTR_ROOTDN_ALTERNATE_BIND_DN,
                                 shortDNString));
      userAttrs.put(altDNAT, attrList);


      LinkedHashMap<AttributeType,List<Attribute>> operationalAttrs =
           new LinkedHashMap<AttributeType,List<Attribute>>();


      DN internalUserDN = DN.decode(fullDNString);
      Entry internalUserEntry =
                 new Entry(internalUserDN, objectClasses, userAttrs,
                           operationalAttrs);

      this.authenticationInfo =
           new AuthenticationInfo(internalUserEntry, true);
      super.setAuthenticationInfo(authenticationInfo);
    }
    catch (DirectoryException de)
    {
      assert debugException(CLASS_NAME, "<init>", de);

      logError(ErrorLogCategory.CONNECTION_HANDLING,
               ErrorLogSeverity.SEVERE_ERROR,
               MSGID_INTERNAL_CANNOT_DECODE_DN, fullDNString,
               stackTraceToSingleLineString(de));
    }

    connectionID  = nextConnectionID.getAndDecrement();
    operationList = new LinkedList<Operation>();

    try
    {
      securityProvider = new InternalConnectionSecurityProvider();
      securityProvider.initializeConnectionSecurityProvider(null);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "<init>", e);
    }
  }



  /**
   * Creates a new internal client connection that will be
   * authenticated as the specified user.
   *
   * @param  authInfo  The authentication information to use for the
   *                   connection.
   */
  public InternalClientConnection(AuthenticationInfo authInfo)
  {
    super();

    assert debugConstructor(CLASS_NAME, String.valueOf(authInfo));

    this.authenticationInfo = authInfo;
    super.setAuthenticationInfo(authInfo);

    connectionID  = nextConnectionID.getAndDecrement();
    operationList = new LinkedList<Operation>();

    try
    {
      securityProvider = new InternalConnectionSecurityProvider();
      securityProvider.initializeConnectionSecurityProvider(null);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "<init>", e);
    }
  }



  /**
   * Retrieves a shared internal client connection that is
   * authenticated as a root user.
   *
   * @return  A shared internal client connection that is
   *          authenticated as a root user.
   */
  public static InternalClientConnection getRootConnection()
  {
    assert debugEnter(CLASS_NAME, "getRootConnection");

    return rootConnection;
  }



  /**
   * Retrieves the operation ID that should be used for the next
   * internal operation.
   *
   * @return  The operation ID that should be used for the next
   *          internal operation.
   */
  public static long nextOperationID()
  {
    assert debugEnter(CLASS_NAME, "nextOperationID");

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
   * Retrieves the message ID that should be used for the next
   * internal operation.
   *
   * @return  The message ID that should be used for the next internal
   *          operation.
   */
  public static int nextMessageID()
  {
    assert debugEnter(CLASS_NAME, "nextMessageID");

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
   * Retrieves the unique identifier that has been assigned to this
   * connection.
   *
   * @return  The unique identifier that has been assigned to this
   *          connection.
   */
  public long getConnectionID()
  {
    assert debugEnter(CLASS_NAME, "getConnectionID");

    return connectionID;
  }



  /**
   * Retrieves the connection handler that accepted this client
   * connection.
   *
   * @return  The connection handler that accepted this client
   *          connection.
   */
  public ConnectionHandler getConnectionHandler()
  {
    assert debugEnter(CLASS_NAME, "getConnectionHandler");

    return InternalConnectionHandler.getInstance();
  }



  /**
   * Retrieves the protocol that the client is using to communicate
   * with the Directory Server.
   *
   * @return  The protocol that the client is using to communicate
   *          with the Directory Server.
   */
  public String getProtocol()
  {
    assert debugEnter(CLASS_NAME, "getProtocol");

    return "internal";
  }



  /**
   * Retrieves a string representation of the address of the client.
   *
   * @return  A string representation of the address of the client.
   */
  public String getClientAddress()
  {
    assert debugEnter(CLASS_NAME, "getClientAddress");

    return "internal";
  }



  /**
   * Retrieves a string representation of the address on the server to
   * which the client connected.
   *
   * @return  A string representation of the address on the server to
   *          which the client connected.
   */
  public String getServerAddress()
  {
    assert debugEnter(CLASS_NAME, "getServerAddress");

    return "internal";
  }



  /**
   * Retrieves the <CODE>java.net.InetAddress</CODE> associated with
   * the remote client system.
   *
   * @return  The <CODE>java.net.InetAddress</CODE> associated with
   *          the remote client system.  It may be <CODE>null</CODE>
   *          if the client is not connected over an IP-based
   *          connection.
   */
  public InetAddress getRemoteAddress()
  {
    assert debugEnter(CLASS_NAME, "getRemoteAddress");

    return null;
  }



  /**
   * Retrieves the <CODE>java.net.InetAddress</CODE> for the Directory
   * Server system to which the client has established the connection.
   *
   * @return  The <CODE>java.net.InetAddress</CODE> for the Directory
   *          Server system to which the client has established the
   *          connection.  It may be <CODE>null</CODE> if the client
   *          is not connected over an IP-based connection.
   */
  public InetAddress getLocalAddress()
  {
    assert debugEnter(CLASS_NAME, "getLocalAddress");

    return null;
  }



  /**
   * Indicates whether this client connection is currently using a
   * secure mechanism to communicate with the server.  Note that this
   * may change over time based on operations performed by the client
   * or server (e.g., it may go from <CODE>false</CODE> to
   * <CODE>true</CODE> if the client uses the StartTLS extended
   * operation).
   *
   * @return  <CODE>true</CODE> if the client connection is currently
   *          using a secure mechanism to communicate with the server,
   *          or <CODE>false</CODE> if not.
   */
  public boolean isSecure()
  {
    assert debugEnter(CLASS_NAME, "isSecure");

    // Internal connections will generally be considered secure, but
    // they may be declared insecure if they are accessed through some
    // external mechanism (e.g., a DSML handler that runs the server
    // in a Servlet engine and using internal operations for
    // processing requests).
    return securityProvider.isSecure();
  }



  /**
   * Retrieves the connection security provider for this client
   * connection.
   *
   * @return  The connection security provider for this client
   *          connection.
   */
  public ConnectionSecurityProvider getConnectionSecurityProvider()
  {
    assert debugEnter(CLASS_NAME, "getConnectionSecurityProvider");

    return securityProvider;
  }



  /**
   * Specifies the connection security provider for this client
   * connection.
   *
   * @param  securityProvider  The connection security provider to use
   *                           for communication on this client
   *                           connection.
   */
  public void setConnectionSecurityProvider(ConnectionSecurityProvider
                                                 securityProvider)
  {
    assert debugEnter(CLASS_NAME, "setConnectionSecurityProvider",
                      String.valueOf(securityProvider));

    this.securityProvider = securityProvider;
  }



  /**
   * Retrieves the human-readable name of the security mechanism that
   * is used to protect communication with this client.
   *
   * @return  The human-readable name of the security mechanism that
   *          is used to protect communication with this client, or
   *          <CODE>null</CODE> if no security is in place.
   */
  public String getSecurityMechanism()
  {
    assert debugEnter(CLASS_NAME, "getSecurityMechanism");

    return securityProvider.getSecurityMechanismName();
  }



  /**
   * Indicates that the data in the provided buffer has been read from
   * the client and should be processed.  The contents of the provided
   * buffer will be in clear-text (the data may have been passed
   * through a connection security provider to obtain the clear-text
   * version), and may contain part or all of one or more client
   * requests.
   *
   * @param  buffer  The byte buffer containing the data available for
   *                 reading.
   *
   * @return  <CODE>true</CODE> if all the data in the provided buffer
   *          was processed and the client connection can remain
   *          established, or <CODE>false</CODE> if a decoding error
   *          occurred and requests from this client should no longer
   *          be processed.  Note that if this method does return
   *          <CODE>false</CODE>, then it must have already
   *          disconnected the client.
   */
  public boolean processDataRead(ByteBuffer buffer)
  {
    assert debugEnter(CLASS_NAME, "processDataRead");

    // This method will not do anything with the data because there is
    // no actual "connection" from which information can be read, nor
    // any protocol to use to read it.
    return false;
  }



  /**
   * Sends a response to the client based on the information in the
   * provided operation.
   *
   * @param  operation  The operation for which to send the response.
   */
  public void sendResponse(Operation operation)
  {
    assert debugEnter(CLASS_NAME, "sendResponse",
                      String.valueOf(operation));

    // There will not be any response sent by this method, since there
    // is not an actual connection.
  }



  /**
   * Retrieves information about the authentication that has been
   * performed for this connection.
   *
   * @return  Information about the user that is currently
   *          authenticated on this connection.
   */
  public AuthenticationInfo getAuthenticationInfo()
  {
    assert debugEnter(CLASS_NAME, "getAuthenticationInfo");

    return authenticationInfo;
  }



  /**
   * This method has no effect, as the authentication info for
   * internal client connections is set when the connection is created
   * and cannot be changed after the fact.
   *
   * @param  authenticationInfo  Information about the authentication
   *                             that has been performed for this
   *                             connection.  It should not be
   *                             <CODE>null</CODE>.
   */
  public void setAuthenticationInfo(AuthenticationInfo
                                         authenticationInfo)
  {
    assert debugEnter(CLASS_NAME, "setAuthenticationInfo",
                      String.valueOf(authenticationInfo));

    // No implementation required.
  }



  /**
   * This method has no effect, as the authentication info for
   * internal client connections is set when the connection is created
   * and cannot be changed after the fact.
   */
  public void setUnauthenticated()
  {
    assert debugEnter(CLASS_NAME, "setUnauthenticated");

    // No implementation required.
  }



  /**
   * Processes an internal add operation with the provided
   * information.
   *
   * @param  rawEntryDN     The DN to use for the entry to add.
   * @param  rawAttributes  The set of attributes to include in the
   *                        entry to add.
   *
   * @return  A reference to the add operation that was processed and
   *          contains information about the result of the processing.
   */
  public AddOperation processAdd(ByteString rawEntryDN,
                                 List<LDAPAttribute> rawAttributes)
  {
    assert debugEnter(CLASS_NAME, "processAdd",
                      String.valueOf(rawEntryDN),
                      String.valueOf(rawAttributes));

    AddOperation addOperation =
         new AddOperation(this, nextOperationID(), nextMessageID(),
                          new ArrayList<Control>(0), rawEntryDN,
                       rawAttributes);
    addOperation.setInternalOperation(true);

    addOperation.run();
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
    assert debugEnter(CLASS_NAME, "processAdd",
                      String.valueOf(entryDN),
                      String.valueOf(objectClasses),
                      String.valueOf(userAttributes),
                      String.valueOf(operationalAttributes));

    AddOperation addOperation =
         new AddOperation(this, nextOperationID(), nextMessageID(),
                          new ArrayList<Control>(0), entryDN,
                          objectClasses, userAttributes,
                          operationalAttributes);
    addOperation.setInternalOperation(true);

    addOperation.run();
    return addOperation;
  }



  /**
   * Processes an internal bind operation with the provided
   * information.  Note that regardless of whether the bind is
   * successful, the authentication state for this internal connection
   * will not be altered in any way.
   *
   * @param  rawBindDN  The bind DN for the operation.
   * @param  password   The bind password for the operation.
   *
   * @return  A reference to the bind operation that was processed and
   *          contains information about the result of the processing.
   */
  public BindOperation processSimpleBind(ByteString rawBindDN,
                                         ByteString password)
  {
    assert debugEnter(CLASS_NAME, "processSimpleBind",
                      String.valueOf(rawBindDN), "ByteString");

    BindOperation bindOperation =
         new BindOperation(this, nextOperationID(), nextMessageID(),
                           new ArrayList<Control>(0), rawBindDN,
                           password);
    bindOperation.setInternalOperation(true);

    bindOperation.run();
    return bindOperation;
  }



  /**
   * Processes an internal bind operation with the provided
   * information.  Note that regardless of whether the bind is
   * successful, the authentication state for this internal connection
   * will not be altered in any way.
   *
   * @param  bindDN    The bind DN for the operation.
   * @param  password  The bind password for the operation.
   *
   * @return  A reference to the bind operation that was processed and
   *          contains information about the result of the processing.
   */
  public BindOperation processSimpleBind(DN bindDN,
                                         ByteString password)
  {
    assert debugEnter(CLASS_NAME, "processSimpleBind",
                      String.valueOf(bindDN), "ByteString");

    BindOperation bindOperation =
         new BindOperation(this, nextOperationID(), nextMessageID(),
                           new ArrayList<Control>(0), bindDN,
                           password);
    bindOperation.setInternalOperation(true);

    bindOperation.run();
    return bindOperation;
  }



  /**
   * Processes an internal bind operation with the provided
   * information.  Note that regardless of whether the bind is
   * successful, the authentication state for this internal connection
   * will not be altered in any way.
   *
   * @param  rawBindDN        The bind DN for the operation.
   * @param  saslMechanism    The SASL mechanism for the operation.
   * @param  saslCredentials  The SASL credentials for the operation.
   *
   * @return  A reference to the bind operation that was processed and
   *          contains information about the result of the processing.
   */
  public BindOperation processSASLBind(ByteString rawBindDN,
                            String saslMechanism,
                            ASN1OctetString saslCredentials)
  {
    assert debugEnter(CLASS_NAME, "processSASLBind",
                      String.valueOf(rawBindDN),
                      String.valueOf(saslMechanism),
                      "ASN1OctetString");

    BindOperation bindOperation =
         new BindOperation(this, nextOperationID(), nextMessageID(),
                           new ArrayList<Control>(0), rawBindDN,
                           saslMechanism, saslCredentials);
    bindOperation.setInternalOperation(true);

    bindOperation.run();
    return bindOperation;
  }



  /**
   * Processes an internal bind operation with the provided
   * information.  Note that regardless of whether the bind is
   * successful, the authentication state for this internal connection
   * will not be altered in any way.
   *
   * @param  bindDN           The bind DN for the operation.
   * @param  saslMechanism    The SASL mechanism for the operation.
   * @param  saslCredentials  The SASL credentials for the operation.
   *
   * @return  A reference to the bind operation that was processed and
   *          contains information about the result of the processing.
   */
  public BindOperation processSASLBind(DN bindDN,
                            String saslMechanism,
                            ASN1OctetString saslCredentials)
  {
    assert debugEnter(CLASS_NAME, "processSASLBind",
                      String.valueOf(bindDN), "ByteString");

    BindOperation bindOperation =
         new BindOperation(this, nextOperationID(), nextMessageID(),
                           new ArrayList<Control>(0), bindDN,
                           saslMechanism, saslCredentials);
    bindOperation.setInternalOperation(true);

    bindOperation.run();
    return bindOperation;
  }



  /**
   * Processes an internal compare operation with the provided
   * information.
   *
   * @param  rawEntryDN      The entry DN for the compare operation.
   * @param  attributeType   The attribute type for the compare
   *                         operation.
   * @param  assertionValue  The assertion value for the compare
   *                         operation.
   *
   * @return  A reference to the compare operation that was processed
   *          and contains information about the result of the
   *          processing.
   */
  public CompareOperation processCompare(ByteString rawEntryDN,
                                         String attributeType,
                                         ByteString assertionValue)
  {
    assert debugEnter(CLASS_NAME, "processCompare",
                      String.valueOf(rawEntryDN),
                      String.valueOf(attributeType),
                      String.valueOf(assertionValue));

    CompareOperation compareOperation =
         new CompareOperation(this, nextOperationID(),
                              nextMessageID(),
                              new ArrayList<Control>(0), rawEntryDN,
                              attributeType, assertionValue);
    compareOperation.setInternalOperation(true);

    compareOperation.run();
    return compareOperation;
  }



  /**
   * Processes an internal compare operation with the provided
   * information.
   *
   * @param  entryDN         The entry DN for the compare operation.
   * @param  attributeType   The attribute type for the compare
   *                         operation.
   * @param  assertionValue  The assertion value for the compare
   *                         operation.
   *
   * @return  A reference to the compare operation that was processed
   *          and contains information about the result of the
   *          processing.
   */
  public CompareOperation processCompare(DN entryDN,
                                         AttributeType attributeType,
                                         ByteString assertionValue)
  {
    assert debugEnter(CLASS_NAME, "processCompare",
                      String.valueOf(entryDN),
                      String.valueOf(attributeType),
                      String.valueOf(assertionValue));

    CompareOperation compareOperation =
         new CompareOperation(this, nextOperationID(),
                              nextMessageID(),
                              new ArrayList<Control>(0), entryDN,
                              attributeType, assertionValue);
    compareOperation.setInternalOperation(true);

    compareOperation.run();
    return compareOperation;
  }



  /**
   * Processes an internal delete operation with the provided
   * information.
   *
   * @param  rawEntryDN  The entry DN for the delete operation.
   *
   * @return  A reference to the delete operation that was processed
   *          and contains information about the result of the
   *          processing.
   */
  public DeleteOperation processDelete(ByteString rawEntryDN)
  {
    assert debugEnter(CLASS_NAME, "processDelete",
                      String.valueOf(rawEntryDN));

    DeleteOperation deleteOperation =
         new DeleteOperation(this, nextOperationID(), nextMessageID(),
                             new ArrayList<Control>(0), rawEntryDN);
    deleteOperation.setInternalOperation(true);

    deleteOperation.run();
    return deleteOperation;
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
    assert debugEnter(CLASS_NAME, "processDelete",
                      String.valueOf(entryDN));

    DeleteOperation deleteOperation =
         new DeleteOperation(this, nextOperationID(), nextMessageID(),
                             new ArrayList<Control>(0), entryDN);
    deleteOperation.setInternalOperation(true);

    deleteOperation.run();
    return deleteOperation;
  }



  /**
   * Processes an internal extended operation with the provided
   * information.
   *
   * @param  requestOID    The OID for the extended request.
   * @param  requestValue  The encoded +value for the extended
   *                       operation, or <CODE>null</CODE> if there is
   *                       no value.
   *
   * @return  A reference to the extended operation that was processed
   *          and contains information about the result of the
   *          processing.
   */
  public ExtendedOperation processExtendedOperation(String requestOID,
                                ASN1OctetString requestValue)
  {
    assert debugEnter(CLASS_NAME, "processExtendedOperation",
                      String.valueOf(requestOID),
                      String.valueOf(requestValue));

    ExtendedOperation extendedOperation =
         new ExtendedOperation(this, nextOperationID(),
                               nextMessageID(),
                               new ArrayList<Control>(0), requestOID,
                               requestValue);
    extendedOperation.setInternalOperation(true);

    extendedOperation.run();
    return extendedOperation;
  }



  /**
   * Processes an internal modify operation with the provided
   * information.
   *
   * @param  rawEntryDN        The raw entry DN for this modify
   *                           operation.
   * @param  rawModifications  The set of modifications for this
   *                           modify operation.
   *
   * @return  A reference to the modify operation that was processed
   *          and contains information about the result of the
   *          processing.
   */
  public ModifyOperation processModify(ByteString rawEntryDN,
                              List<LDAPModification> rawModifications)
  {
    assert debugEnter(CLASS_NAME, "processModify",
                      String.valueOf(rawEntryDN),
                      String.valueOf(rawModifications));

    ModifyOperation modifyOperation =
         new ModifyOperation(this, nextOperationID(), nextMessageID(),
                             new ArrayList<Control>(0), rawEntryDN,
                             rawModifications);
    modifyOperation.setInternalOperation(true);

    modifyOperation.run();
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
    assert debugEnter(CLASS_NAME, "processModify",
                      String.valueOf(entryDN),
                      String.valueOf(modifications));

    ModifyOperation modifyOperation =
         new ModifyOperation(this, nextOperationID(), nextMessageID(),
                             new ArrayList<Control>(0), entryDN,
                             modifications);
    modifyOperation.setInternalOperation(true);

    modifyOperation.run();
    return modifyOperation;
  }



  /**
   * Processes an internal modify DN operation with the provided
   * information.
   *
   * @param  rawEntryDN    The current DN of the entry to rename.
   * @param  rawNewRDN     The new RDN to use for the entry.
   * @param  deleteOldRDN  The flag indicating whether the old RDN
   *                       value is to be removed from the entry.
   *
   * @return  A reference to the modify DN operation that was
   *          processed and contains information about the result of
   *          the processing.
   */
  public ModifyDNOperation processModifyDN(ByteString rawEntryDN,
                                           ByteString rawNewRDN,
                                           boolean deleteOldRDN)
  {
    assert debugEnter(CLASS_NAME, "processModifyDN",
                      String.valueOf(rawEntryDN),
                      String.valueOf(rawNewRDN),
                      String.valueOf(deleteOldRDN));

    return processModifyDN(rawEntryDN, rawNewRDN, deleteOldRDN, null);
  }



  /**
   * Processes an internal modify DN operation with the provided
   * information.
   *
   * @param  rawEntryDN      The current DN of the entry to rename.
   * @param  rawNewRDN       The new RDN to use for the entry.
   * @param  deleteOldRDN    The flag indicating whether the old RDN
   *                         value is to be removed from the entry.
   * @param  rawNewSuperior  The new superior for the modify DN
   *                         operation, or <CODE>null</CODE> if the
   *                         entry will remain below the same parent.
   *
   * @return  A reference to the modify DN operation that was
   *          processed and contains information about the result of
   *          the processing.
   */
  public ModifyDNOperation processModifyDN(ByteString rawEntryDN,
                                           ByteString rawNewRDN,
                                           boolean deleteOldRDN,
                                           ByteString rawNewSuperior)
  {
    assert debugEnter(CLASS_NAME, "processModifyDN",
                      String.valueOf(rawEntryDN),
                      String.valueOf(rawNewRDN),
                      String.valueOf(deleteOldRDN),
                      String.valueOf(rawNewSuperior));

    ModifyDNOperation modifyDNOperation =
         new ModifyDNOperation(this, nextOperationID(),
                               nextMessageID(),
                               new ArrayList<Control>(0), rawEntryDN,
                               rawNewRDN, deleteOldRDN,
                               rawNewSuperior);
    modifyDNOperation.setInternalOperation(true);

    modifyDNOperation.run();
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
   *
   * @return  A reference to the modify DN operation that was
   *          processed and contains information about the result of
   *          the processing.
   */
  public ModifyDNOperation processModifyDN(DN entryDN, RDN newRDN,
                                           boolean deleteOldRDN)
  {
    assert debugEnter(CLASS_NAME, "processModifyDN",
                      String.valueOf(entryDN),
                      String.valueOf(newRDN),
                      String.valueOf(deleteOldRDN));

    return processModifyDN(entryDN, newRDN, deleteOldRDN, null);
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
    assert debugEnter(CLASS_NAME, "processModifyDN",
                      String.valueOf(entryDN), String.valueOf(newRDN),
                      String.valueOf(deleteOldRDN),
                      String.valueOf(newSuperior));

    ModifyDNOperation modifyDNOperation =
         new ModifyDNOperation(this, nextOperationID(),
                               nextMessageID(),
                               new ArrayList<Control>(0), entryDN,
                               newRDN, deleteOldRDN, newSuperior);
    modifyDNOperation.setInternalOperation(true);

    modifyDNOperation.run();
    return modifyDNOperation;
  }



  /**
   * Processes an internal search operation with the provided
   * information.  It will not dereference any aliases, will not
   * request a size or time limit, and will retrieve all user
   * attributes.
   *
   * @param  rawBaseDN  The base DN for the search.
   * @param  scope      The scope for the search.
   * @param  filter     The filter for the search.
   *
   * @return  A reference to the internal search operation that was
   *          processed and contains information about the result of
   *          the processing as well as lists of the matching entries
   *          and search references.
   */
  public InternalSearchOperation processSearch(ByteString rawBaseDN,
                                      SearchScope scope,
                                      LDAPFilter filter)
  {
    assert debugEnter(CLASS_NAME, "processSearch",
                      String.valueOf(rawBaseDN),
                      String.valueOf(scope), String.valueOf(filter));

    return processSearch(rawBaseDN, scope,
                         DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0,
                         false, filter, new LinkedHashSet<String>(0));
  }



  /**
   * Processes an internal search operation with the provided
   * information.
   *
   * @param  rawBaseDN    The base DN for the search.
   * @param  scope        The scope for the search.
   * @param  derefPolicy  The alias dereferencing policy for the
   *                      search.
   * @param  sizeLimit    The size limit for the search.
   * @param  timeLimit    The time limit for the search.
   * @param  typesOnly    The typesOnly flag for the search.
   * @param  filter       The filter for the search.
   * @param  attributes   The set of requested attributes for the
   *                      search.
   *
   * @return  A reference to the internal search operation that was
   *          processed and contains information about the result of
   *          the processing as well as lists of the matching entries
   *          and search references.
   */
  public InternalSearchOperation
              processSearch(ByteString rawBaseDN,
                            SearchScope scope,
                            DereferencePolicy derefPolicy,
                            int sizeLimit, int timeLimit,
                            boolean typesOnly, LDAPFilter filter,
                            LinkedHashSet<String> attributes)
  {
    assert debugEnter(CLASS_NAME, "processSearch",
                      new String[]
                      {
                        String.valueOf(rawBaseDN),
                        String.valueOf(scope),
                        String.valueOf(derefPolicy),
                        String.valueOf(sizeLimit),
                        String.valueOf(timeLimit),
                        String.valueOf(typesOnly),
                        String.valueOf(filter),
                        String.valueOf(attributes)
                      });

    InternalSearchOperation searchOperation =
         new InternalSearchOperation(this, nextOperationID(),
                                     nextMessageID(),
                                     new ArrayList<Control>(0),
                                     rawBaseDN, scope, derefPolicy,
                                     sizeLimit, timeLimit,
                                     typesOnly, filter, attributes,
                                     null);

    searchOperation.run();
    return searchOperation;
  }



  /**
   * Processes an internal search operation with the provided
   * information.
   *
   * @param  rawBaseDN       The base DN for the search.
   * @param  scope           The scope for the search.
   * @param  derefPolicy     The alias dereferencing policy for the
   *                         search.
   * @param  sizeLimit       The size limit for the search.
   * @param  timeLimit       The time limit for the search.
   * @param  typesOnly       The typesOnly flag for the search.
   * @param  filter          The filter for the search.
   * @param  attributes      The set of requested attributes for the
   *                         search.
   * @param  searchListener  The internal search listener that should
   *                         be used to handle the matching entries
   *                         and references.
   *
   * @return  A reference to the internal search operation that was
   *          processed and contains information about the result of
   *          the processing.
   */
  public InternalSearchOperation
              processSearch(ByteString rawBaseDN,
                            SearchScope scope,
                            DereferencePolicy derefPolicy,
                            int sizeLimit, int timeLimit,
                            boolean typesOnly, LDAPFilter filter,
                            LinkedHashSet<String> attributes,
                            InternalSearchListener searchListener)
  {
    assert debugEnter(CLASS_NAME, "processSearch",
                      new String[]
                      {
                        String.valueOf(rawBaseDN),
                        String.valueOf(scope),
                        String.valueOf(derefPolicy),
                        String.valueOf(sizeLimit),
                        String.valueOf(timeLimit),
                        String.valueOf(typesOnly),
                        String.valueOf(filter),
                        String.valueOf(attributes),
                        String.valueOf(searchListener)
                      });

    InternalSearchOperation searchOperation =
         new InternalSearchOperation(this, nextOperationID(),
                                     nextMessageID(),
                                     new ArrayList<Control>(0),
                                     rawBaseDN, scope, derefPolicy,
                                     sizeLimit, timeLimit,
                                     typesOnly, filter, attributes,
                                     searchListener);

    searchOperation.run();
    return searchOperation;
  }



  /**
   * Processes an internal search operation with the provided
   * information.  It will not dereference any aliases, will not
   * request a size or time limit, and will retrieve all user
   * attributes.
   *
   * @param  baseDN  The base DN for the search.
   * @param  scope   The scope for the search.
   * @param  filter  The filter for the search.
   *
   * @return  A reference to the internal search operation that was
   *          processed and contains information about the result of
   *          the processing as well as lists of the matching entries
   *          and search references.
   */
  public InternalSearchOperation processSearch(DN baseDN,
                                      SearchScope scope,
                                      SearchFilter filter)
  {
    assert debugEnter(CLASS_NAME, "processSearch",
                      String.valueOf(baseDN),
                      String.valueOf(scope), String.valueOf(filter));

    return processSearch(baseDN, scope,
                         DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0,
                         false, filter, new LinkedHashSet<String>(0));
  }



  /**
   * Processes an internal search operation with the provided
   * information.
   *
   * @param  baseDN       The base DN for the search.
   * @param  scope        The scope for the search.
   * @param  derefPolicy  The alias dereferencing policy for the
   *                      search.
   * @param  sizeLimit    The size limit for the search.
   * @param  timeLimit    The time limit for the search.
   * @param  typesOnly    The typesOnly flag for the search.
   * @param  filter       The filter for the search.
   * @param  attributes   The set of requested attributes for the
   *                      search.
   *
   * @return  A reference to the internal search operation that was
   *          processed and contains information about the result of
   *          the processing as well as lists of the matching entries
   *          and search references.
   */
  public InternalSearchOperation
              processSearch(DN baseDN, SearchScope scope,
                            DereferencePolicy derefPolicy,
                            int sizeLimit, int timeLimit,
                            boolean typesOnly, SearchFilter filter,
                            LinkedHashSet<String> attributes)
  {
    assert debugEnter(CLASS_NAME, "processSearch",
                      new String[]
                      {
                        String.valueOf(baseDN),
                        String.valueOf(scope),
                        String.valueOf(derefPolicy),
                        String.valueOf(sizeLimit),
                        String.valueOf(timeLimit),
                        String.valueOf(typesOnly),
                        String.valueOf(filter),
                        String.valueOf(attributes)
                      });

    InternalSearchOperation searchOperation =
         new InternalSearchOperation(this, nextOperationID(),
                                     nextMessageID(),
                                     new ArrayList<Control>(0),
                                     baseDN, scope, derefPolicy,
                                     sizeLimit, timeLimit,
                                     typesOnly, filter, attributes,
                                     null);

    searchOperation.run();
    return searchOperation;
  }



  /**
   * Processes an internal search operation with the provided
   * information.
   *
   * @param  baseDN          The base DN for the search.
   * @param  scope           The scope for the search.
   * @param  derefPolicy     The alias dereferencing policy for the
   *                         search.
   * @param  sizeLimit       The size limit for the search.
   * @param  timeLimit       The time limit for the search.
   * @param  typesOnly       The typesOnly flag for the search.
   * @param  filter          The filter for the search.
   * @param  attributes      The set of requested attributes for the
   *                         search.
   * @param  searchListener  The internal search listener that should
   *                         be used to handle the matching entries
   *                         and references.
   *
   * @return  A reference to the internal search operation that was
   *          processed and contains information about the result of
   *          the processing.
   */
  public InternalSearchOperation
              processSearch(DN baseDN, SearchScope scope,
                            DereferencePolicy derefPolicy,
                            int sizeLimit, int timeLimit,
                            boolean typesOnly, SearchFilter filter,
                            LinkedHashSet<String> attributes,
                            InternalSearchListener searchListener)
  {
    assert debugEnter(CLASS_NAME, "processSearch",
                      new String[]
                      {
                        String.valueOf(baseDN),
                        String.valueOf(scope),
                        String.valueOf(derefPolicy),
                        String.valueOf(sizeLimit),
                        String.valueOf(timeLimit),
                        String.valueOf(typesOnly),
                        String.valueOf(filter),
                        String.valueOf(attributes),
                        String.valueOf(searchListener)
                      });

    InternalSearchOperation searchOperation =
         new InternalSearchOperation(this, nextOperationID(),
                                     nextMessageID(),
                                     new ArrayList<Control>(0),
                                     baseDN, scope, derefPolicy,
                                     sizeLimit, timeLimit,
                                     typesOnly, filter, attributes,
                                     searchListener);

    searchOperation.run();
    return searchOperation;
  }



  /**
   * Sends the provided search result entry to the client.
   *
   * @param  searchOperation  The search operation with which the
   *                          entry is associated.
   * @param  searchEntry      The search result entry to be sent to
   *                          the client.
   */
  public void sendSearchEntry(SearchOperation searchOperation,
                              SearchResultEntry searchEntry)
  {
    assert debugEnter(CLASS_NAME, "sendSearchEntry",
                      String.valueOf(searchOperation),
                      String.valueOf(searchEntry));

    ((InternalSearchOperation) searchOperation).
         addSearchEntry(searchEntry);
  }



  /**
   * Sends the provided search result reference to the client.
   *
   * @param  searchOperation  The search operation with which the
   *                          reference is associated.
   * @param  searchReference  The search result reference to be sent
   *                          to the client.
   *
   * @return  <CODE>true</CODE> if the client is able to accept
   *          referrals, or <CODE>false</CODE> if the client cannot
   *          handle referrals and no more attempts should be made to
   *          send them for the associated search operation.
   */
  public boolean sendSearchReference(SearchOperation searchOperation,
                      SearchResultReference searchReference)
  {
    assert debugEnter(CLASS_NAME, "sendSearchReference",
                      String.valueOf(searchOperation),
                      String.valueOf(searchReference));


    ((InternalSearchOperation)
     searchOperation).addSearchReference(searchReference);
    return true;
  }




  /**
   * Sends the provided intermediate response message to the client.
   *
   * @param  intermediateResponse  The intermediate response message
   *                               to be sent.
   *
   * @return  <CODE>true</CODE> if processing on the associated
   *          operation should continue, or <CODE>false</CODE> if not.
   */
  protected boolean sendIntermediateResponseMessage(
                         IntermediateResponse intermediateResponse)
  {
    assert debugEnter(CLASS_NAME, "sendIntermediateResponseMessage",
                      String.valueOf(intermediateResponse));


    // FIXME -- Do we need to support internal intermediate responses?
    //          If so, then implement this.
    return false;
  }




  /**
   * Closes the connection to the client, optionally sending it a
   * message indicating the reason for the closure.  Note that the
   * ability to send a notice of disconnection may not be available
   * for all protocols or under all circumstances.
   *
   * @param  disconnectReason  The disconnect reason that provides the
   *                           generic cause for the disconnect.
   * @param  sendNotification  Indicates whether to try to provide
   *                           notification to the client that the
   *                           connection will be closed.
   * @param  message           The message to send to the client.  It
   *                           may be <CODE>null</CODE> if no
   *                           notification is to be sent.
   * @param  messageID         The unique identifier associated with
   *                           the message to send to the client.  It
   *                           may be -1 if no notification is to be
   *                           sent.
   */
  public void disconnect(DisconnectReason disconnectReason,
                         boolean sendNotification, String message,
                         int messageID)
  {
    assert debugEnter(CLASS_NAME, "disconnect",
                      String.valueOf(disconnectReason),
                      String.valueOf(sendNotification),
                      String.valueOf(message),
                      String.valueOf(messageID));

    // No implementation is required since there is nothing to
    // disconnect.  Further, since there is no real disconnect, we can
    // wait to have the garbage collector call
    // finalizeConnectionInternal whenever this internal connection is
    // garbage collected.
  }



  /**
   * Indicates whether a bind operation is in progress on this client
   * connection.  If so, then no new operations should be allowed
   * until the bind has completed.
   *
   * @return  <CODE>true</CODE> if a bind operation is in progress on
   *          this connection, or <CODE>false</CODE> if not.
   */
  public boolean bindInProgress()
  {
    assert debugEnter(CLASS_NAME, "bindInProgress");

    // For internal operations, we don't care if there are any binds
    // in progress.
    return false;
  }



  /**
   * Specifies whether a bind operation is in progress on this client
   * connection.  If so, then no new operations should be allowed
   * until the bind has completed.
   *
   * @param  bindInProgress  Specifies whether a bind operation is in
   *                         progress on this client connection.
   */
  public void setBindInProgress(boolean bindInProgress)
  {
    assert debugEnter(CLASS_NAME, "setBindInProgress",
                      String.valueOf(bindInProgress));

    // No implementation is required.
  }



  /**
   * Retrieves the set of operations in progress for this client
   * connection.  This list must not be altered by any caller.
   *
   * @return  The set of operations in progress for this client
   *          connection.
   */
  public Collection<Operation> getOperationsInProgress()
  {
    assert debugEnter(CLASS_NAME, "getOperationsInProgress");

    return operationList;
  }



  /**
   * Retrieves the operation in progress with the specified message
   * ID.
   *
   * @param  messageID  The message ID of the operation to retrieve.
   *
   * @return  The operation in progress with the specified message ID,
   *          or <CODE>null</CODE> if no such operation could be
   *          found.
   */
  public Operation getOperationInProgress(int messageID)
  {
    assert debugEnter(CLASS_NAME, "getOperationInProgress",
                      String.valueOf(messageID));

    // Internal operations will not be tracked.
    return null;
  }



  /**
   * Removes the provided operation from the set of operations in
   * progress for this client connection.  Note that this does not
   * make any attempt to cancel any processing that may already be in
   * progress for the operation.
   *
   * @param  messageID  The message ID of the operation to remove from
   *                    the set of operations in progress.
   *
   * @return  <CODE>true</CODE> if the operation was found and removed
   *          from the set of operations in progress, or
   *          <CODE>false</CODE> if not.
   */
  public boolean removeOperationInProgress(int messageID)
  {
    assert debugEnter(CLASS_NAME, "removeOperationInProgress",
                      String.valueOf(messageID));

    // No implementation is required, since internal operations will
    // not be tracked.
    return false;
  }



  /**
   * Attempts to cancel the specified operation.
   *
   * @param  messageID      The message ID of the operation to cancel.
   * @param  cancelRequest  An object providing additional information
   *                        about how the cancel should be processed.
   *
   * @return  A cancel result that either indicates that the cancel
   *          was successful or provides a reason that it was not.
   */
  public CancelResult cancelOperation(int messageID,
                                      CancelRequest cancelRequest)
  {
    assert debugEnter(CLASS_NAME, "cancelOperation",
                      String.valueOf(messageID),
                      String.valueOf(cancelRequest));

    // Internal operations cannot be cancelled.
    return CancelResult.CANNOT_CANCEL;
  }



  /**
   * Attempts to cancel all operations in progress on this connection.
   *
   * @param  cancelRequest  An object providing additional information
   *                        about how the cancel should be processed.
   */
  public void cancelAllOperations(CancelRequest cancelRequest)
  {
    assert debugEnter(CLASS_NAME, "cancelAllOperations",
                      String.valueOf(cancelRequest));

    // No implementation is required since internal operations cannot
    // be cancelled.
  }



  /**
   * Attempts to cancel all operations in progress on this connection
   * except the operation with the specified message ID.
   *
   * @param  cancelRequest  An object providing additional information
   *                        about how the cancel should be processed.
   * @param  messageID      The message ID of the operation that
   *                        should not be canceled.
   */
  public void cancelAllOperationsExcept(CancelRequest cancelRequest,
                                        int messageID)
  {
    assert debugEnter(CLASS_NAME, "cancelAllOperationsExcept",
                      String.valueOf(cancelRequest),
                      String.valueOf(messageID));

    // No implementation is required since internal operations cannot
    // be cancelled.
  }



  /**
   * {@inheritDoc}
   */
  public String getMonitorSummary()
  {
    assert debugEnter(CLASS_NAME, "getMonitorSummary");

    StringBuilder buffer = new StringBuilder();
    buffer.append("connID=\"");
    buffer.append(connectionID);
    buffer.append("\" authDN=\"");
    buffer.append(getAuthenticationInfo().getAuthenticationDN());
    buffer.append("\"");

    return buffer.toString();
  }



  /**
   * Appends a string representation of this client connection to the
   * provided buffer.
   *
   * @param  buffer  The buffer to which the information should be
   *                 appended.
   */
  public void toString(StringBuilder buffer)
  {
    assert debugEnter(CLASS_NAME, "toString",
                      "java.lang.StringBuilder");

    buffer.append("InternalClientConnection(connID=");
    buffer.append(connectionID);
    buffer.append(", authDN=\"");

    if (getAuthenticationInfo() != null)
    {
      buffer.append(getAuthenticationInfo().getAuthenticationDN());
    }

    buffer.append("\")");
  }
}

