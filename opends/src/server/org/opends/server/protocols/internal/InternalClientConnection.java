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

import org.opends.messages.Message;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.api.ConnectionSecurityProvider;
import org.opends.server.core.*;
import org.opends.server.core.networkgroups.NetworkGroup;
import org.opends.server.extensions.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.AbstractOperation;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.Attributes;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.ByteString;
import org.opends.server.types.CancelRequest;
import org.opends.server.types.CancelResult;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.Entry;
import org.opends.server.types.IntermediateResponse;
import org.opends.server.types.LDAPException;
import org.opends.server.types.Modification;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.Operation;
import org.opends.server.types.Privilege;
import org.opends.server.types.RDN;
import org.opends.server.types.RawAttribute;
import org.opends.server.types.RawFilter;
import org.opends.server.types.RawModification;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;
import org.opends.server.types.SearchScope;
import org.opends.server.util.AddChangeRecordEntry;
import org.opends.server.util.DeleteChangeRecordEntry;
import org.opends.server.util.ModifyChangeRecordEntry;
import org.opends.server.util.ModifyDNChangeRecordEntry;

import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a pseudo-connection object that can be used for
 * performing internal operations.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public final class InternalClientConnection
       extends ClientConnection
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * The protocol verison string that will be used for internal bind
   * operations.  Since this is modeled after LDAPv3 binds, it will
   * use a version number string of "3".
   */
  public static final String PROTOCOL_VERSION = "3";



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
  }



  /**
   * Creates a new internal client connection that will be
   * authenticated as a root user for which access control will not be
   * enforced.
   */
  private InternalClientConnection()
  {
    super();


    this.setNetworkGroup(NetworkGroup.getInternalNetworkGroup());

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
      attrList.add(Attributes.create(ATTR_COMMON_NAME,
          commonName));
      userAttrs.put(cnAT, attrList);

      attrList = new LinkedList<Attribute>();
      attrList.add(Attributes.create(ATTR_SN, commonName));
      userAttrs.put(snAT, attrList);

      attrList = new LinkedList<Attribute>();
      attrList.add(Attributes.create(
          ATTR_ROOTDN_ALTERNATE_BIND_DN,
          shortDNString));
      userAttrs.put(altDNAT, attrList);


      LinkedHashMap<AttributeType,List<Attribute>> operationalAttrs =
           new LinkedHashMap<AttributeType,List<Attribute>>();

      AttributeType privType =
           DirectoryServer.getAttributeType(OP_ATTR_PRIVILEGE_NAME,
                                            true);

      AttributeBuilder builder = new AttributeBuilder(privType);
      for (Privilege p : Privilege.getDefaultRootPrivileges())
      {
        builder.add(new AttributeValue(privType, p.getName()));
      }
      attrList = new LinkedList<Attribute>();
      attrList.add(builder.toAttribute());

      operationalAttrs.put(privType, attrList);


      DN internalUserDN = DN.decode(fullDNString);
      Entry internalUserEntry =
                 new Entry(internalUserDN, objectClasses, userAttrs,
                           operationalAttrs);

      this.authenticationInfo =
           new AuthenticationInfo(internalUserEntry, true);
      super.setAuthenticationInfo(authenticationInfo);
      super.setSizeLimit(0);
      super.setTimeLimit(0);
      super.setIdleTimeLimit(0);
      super.setLookthroughLimit(0);
    }
    catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }

      logError(ERR_INTERNAL_CANNOT_DECODE_DN.get(
          fullDNString, getExceptionMessage(de)));
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
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
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


    this.setNetworkGroup(NetworkGroup.getInternalNetworkGroup());

    this.authenticationInfo = authInfo;
    super.setAuthenticationInfo(authInfo);
    super.setSizeLimit(0);
    super.setTimeLimit(0);
    super.setIdleTimeLimit(0);
    super.setLookthroughLimit(0);

    connectionID  = nextConnectionID.getAndDecrement();
    operationList = new LinkedList<Operation>();

    try
    {
      securityProvider = new InternalConnectionSecurityProvider();
      securityProvider.initializeConnectionSecurityProvider(null);
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
   * Creates a new internal client connection that will be
   * authenticated as the specified user.
   *
   * @param  userDN  The DN of the entry to use as the
   *                 authentication and authorization identity.
   *
   * @throws  DirectoryException  If a problem occurs while trying to
   *                              get the entry for the provided user
   *                              DN.
   */
  public InternalClientConnection(DN userDN)
         throws DirectoryException
  {
    this(getAuthInfoForDN(userDN));
  }



  /**
   * Creates an authentication information object for the user with
   * the specified DN.
   *
   * @param  userDN  The DN of the user for whom to create an
   *                 authentication information object.
   *
   * @return  The appropriate authentication information object.
   *
   * @throws  DirectoryException  If a problem occurs while trying to
   *                              create the authentication
   *                              information object, or there is no
   *                              such user in the directory.
   */
  private static AuthenticationInfo getAuthInfoForDN(DN userDN)
          throws DirectoryException
  {
    if ((userDN == null) || userDN.isNullDN())
    {
      return new AuthenticationInfo();
    }

    DN rootUserDN = DirectoryServer.getActualRootBindDN(userDN);
    if (rootUserDN != null)
    {
      userDN = rootUserDN;
    }

    Entry userEntry = DirectoryServer.getEntry(userDN);
    if (userEntry == null)
    {
      Message m =
           ERR_INTERNALCONN_NO_SUCH_USER.get(String.valueOf(userDN));
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, m);
    }

    boolean isRoot = DirectoryServer.isRootDN(userDN);
    return new AuthenticationInfo(userEntry, isRoot);
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
    if (rootConnection == null)
    {
      rootConnection = new InternalClientConnection();
    }

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
  @Override()
  public long getConnectionID()
  {
    return connectionID;
  }



  /**
   * Retrieves the connection handler that accepted this client
   * connection.
   *
   * @return  The connection handler that accepted this client
   *          connection.
   */
  @org.opends.server.types.PublicAPI(
       stability=org.opends.server.types.StabilityLevel.PRIVATE,
       mayInstantiate=false,
       mayExtend=false,
       mayInvoke=false)
  @Override()
  public ConnectionHandler<?> getConnectionHandler()
  {
    return InternalConnectionHandler.getInstance();
  }



  /**
   * Retrieves the protocol that the client is using to communicate
   * with the Directory Server.
   *
   * @return  The protocol that the client is using to communicate
   *          with the Directory Server.
   */
  @Override()
  public String getProtocol()
  {
    return "internal";
  }



  /**
   * Retrieves a string representation of the address of the client.
   *
   * @return  A string representation of the address of the client.
   */
  @Override()
  public String getClientAddress()
  {
    return "internal";
  }



  /**
   * Retrieves the port number for this connection on the client
   * system.
   *
   * @return The port number for this connection on the client system.
   */
  public int getClientPort()
  {
    return -1;
  }



  /**
   * Retrieves a string representation of the address on the server to
   * which the client connected.
   *
   * @return  A string representation of the address on the server to
   *          which the client connected.
   */
  @Override()
  public String getServerAddress()
  {
    return "internal";
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
   * Retrieves the <CODE>java.net.InetAddress</CODE> associated with
   * the remote client system.
   *
   * @return  The <CODE>java.net.InetAddress</CODE> associated with
   *          the remote client system.  It may be <CODE>null</CODE>
   *          if the client is not connected over an IP-based
   *          connection.
   */
  @Override()
  public InetAddress getRemoteAddress()
  {
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
  @Override()
  public InetAddress getLocalAddress()
  {
    return null;
  }



  /**
   * Specifies the size limit that will be enforced for searches
   * performed using this client connection.  This method does nothing
   * because connection-level size limits will never be enforced for
   * internal client connections.
   *
   * @param  sizeLimit  The size limit that will be enforced for
   *                    searches performed using this client
   *                    connection.
   */
  @Override()
  public void setSizeLimit(int sizeLimit)
  {
    // No implementation required.  We never want to set a nonzero
    // size limit for internal client connections.
  }



  /**
   * Specifies the default maximum number of entries that should
   * be checked for matches during a search.  This method does nothing
   * because connection-level lookthrough limits will never be
   * enforced for internal client connections
   *
   * @param  lookthroughLimit  The default maximum number of
   *                           entries that should be check for
   *                           matches during a search.
   */
  @Override()
  public void setLookthroughLimit(int lookthroughLimit)
  {
    // No implementation required.  We never want to set a nonzero
    // lookthrough limit for internal client connections.
  }



  /**
   * Specifies the maximum length of time in milliseconds that this
   * client connection will be allowed to remain idle before it should
   * be disconnected.  This method does nothing because internal
   * client connections will not be terminated due to an idle time
   * limit.
   *
   * @param  idleTimeLimit  The maximum length of time in milliseconds
   *                        that this client connection will be
   *                        allowed to remain idle before it should be
   *                        disconnected.
   */
  @Override()
  public void setIdleTimeLimit(long idleTimeLimit)
  {
    // No implementation rqeuired.  We never want to set a nonzero
    // idle time limit for internal client connections.
  }



  /**
   * Specifies the time limit that will be enforced for searches
   * performed using this client connection.  This method does nothing
   * because connection-level tim elimits will never be enforced for
   * internal client connections.
   *
   * @param  timeLimit  The time limit that will be enforced for
   *                    searches performed using this client
   *                    connection.
   */
  @Override()
  public void setTimeLimit(int timeLimit)
  {
    // No implementation required.  We never want to set a nonzero
    // time limit for internal client connections.
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
  @Override()
  public boolean isSecure()
  {
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
  @Override()
  public ConnectionSecurityProvider getConnectionSecurityProvider()
  {
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
  @org.opends.server.types.PublicAPI(
       stability=org.opends.server.types.StabilityLevel.PRIVATE,
       mayInstantiate=false,
       mayExtend=false,
       mayInvoke=false)
  @Override()
  public void setConnectionSecurityProvider(ConnectionSecurityProvider
                                                 securityProvider)
  {
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
  @Override()
  public String getSecurityMechanism()
  {
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
  @org.opends.server.types.PublicAPI(
       stability=org.opends.server.types.StabilityLevel.PRIVATE,
       mayInstantiate=false,
       mayExtend=false,
       mayInvoke=false)
  @Override()
  public boolean processDataRead(ByteBuffer buffer)
  {
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
  @org.opends.server.types.PublicAPI(
       stability=org.opends.server.types.StabilityLevel.PRIVATE,
       mayInstantiate=false,
       mayExtend=false,
       mayInvoke=false)
  @Override()
  public void sendResponse(Operation operation)
  {
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
  @Override()
  public AuthenticationInfo getAuthenticationInfo()
  {
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
  @org.opends.server.types.PublicAPI(
       stability=org.opends.server.types.StabilityLevel.PRIVATE,
       mayInstantiate=false,
       mayExtend=false,
       mayInvoke=false)
  @Override()
  public void setAuthenticationInfo(AuthenticationInfo
                                         authenticationInfo)
  {
    // No implementation required.
  }



  /**
   * This method has no effect, as the authentication info for
   * internal client connections is set when the connection is created
   * and cannot be changed after the fact.
   */
  @org.opends.server.types.PublicAPI(
       stability=org.opends.server.types.StabilityLevel.PRIVATE,
       mayInstantiate=false,
       mayExtend=false,
       mayInvoke=false)
  @Override()
  public void setUnauthenticated()
  {
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
  public AddOperation processAdd(String rawEntryDN,
                                 List<RawAttribute> rawAttributes)
  {
    return processAdd(new ASN1OctetString(rawEntryDN), rawAttributes,
                      null);
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
                                 List<RawAttribute> rawAttributes)
  {
    return processAdd(rawEntryDN, rawAttributes, null);
  }



  /**
   * Processes an internal add operation with the provided
   * information.
   *
   * @param  rawEntryDN     The DN to use for the entry to add.
   * @param  rawAttributes  The set of attributes to include in the
   *                        entry to add.
   * @param  controls       The set of controls to include in the
   *                        request.
   *
   * @return  A reference to the add operation that was processed and
   *          contains information about the result of the processing.
   */
  public AddOperation processAdd(ByteString rawEntryDN,
                                 List<RawAttribute> rawAttributes,
                                 List<Control> controls)
  {
    if (controls == null)
    {
      controls = new ArrayList<Control>(0);
    }

    AddOperationBasis addOperation =
         new AddOperationBasis(this, nextOperationID(),
                          nextMessageID(), controls, rawEntryDN,
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
    return processAdd(entryDN, objectClasses, userAttributes,
                      operationalAttributes, null);
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
   * @param  controls               The set of controls to include in
   *                                the request.
   *
   * @return  A reference to the add operation that was processed and
   *          contains information about the result of the processing.
   */
  public AddOperation processAdd(DN entryDN,
                           Map<ObjectClass,String> objectClasses,
                           Map<AttributeType,List<Attribute>>
                                userAttributes,
                           Map<AttributeType,List<Attribute>>
                                operationalAttributes,
                           List<Control> controls)
  {
    if (controls == null)
    {
      controls = new ArrayList<Control>(0);
    }

    AddOperationBasis addOperation =
         new AddOperationBasis(this, nextOperationID(),
                          nextMessageID(), controls, entryDN,
                          objectClasses, userAttributes,
                          operationalAttributes);
    addOperation.setInternalOperation(true);

    addOperation.run();
    return addOperation;
  }



  /**
   * Processes an internal add operation with the provided
   * information.
   *
   * @param  entry  The entry to be added.
   *
   * @return  A reference to the add operation that was processed and
   *          contains information about the result of the processing.
   */
  public AddOperation processAdd(Entry entry)
  {
    return processAdd(entry, null);
  }



  /**
   * Processes an internal add operation with the provided
   * information.
   *
   * @param  entry     The entry to be added.
   * @param  controls  The set of controls to include in the request.
   *
   * @return  A reference to the add operation that was processed and
   *          contains information about the result of the processing.
   */
  public AddOperation processAdd(Entry entry, List<Control> controls)
  {
    if (controls == null)
    {
      controls = new ArrayList<Control>(0);
    }

    return processAdd(entry.getDN(), entry.getObjectClasses(),
                      entry.getUserAttributes(),
                      entry.getOperationalAttributes());
  }



  /**
   * Processes an internal add operation based on the provided add
   * change record entry.
   *
   * @param  addRecord  The add change record entry to be processed.
   *
   * @return  A reference to the add operation that was processed and
   *          contains information about the result of the processing.
   */
  public AddOperation processAdd(AddChangeRecordEntry addRecord)
  {
    LinkedHashMap<ObjectClass,String> objectClasses =
         new LinkedHashMap<ObjectClass,String>();
    LinkedHashMap<AttributeType,List<Attribute>> userAttrs =
         new LinkedHashMap<AttributeType,List<Attribute>>();
    LinkedHashMap<AttributeType,List<Attribute>> opAttrs =
         new LinkedHashMap<AttributeType,List<Attribute>>();

    Entry e = new Entry(addRecord.getDN(), objectClasses, userAttrs,
                        opAttrs);

    ArrayList<AttributeValue> duplicateValues =
         new ArrayList<AttributeValue>();
    for (Attribute a : addRecord.getAttributes())
    {
      if (a.getAttributeType().isObjectClassType())
      {
        for (AttributeValue v : a)
        {
          String ocName = v.getStringValue();
          String lowerName = toLowerCase(ocName);
          ObjectClass oc = DirectoryServer.getObjectClass(lowerName,
                                                          true);
          objectClasses.put(oc, ocName);
        }
      }
      else
      {
        e.addAttribute(a, duplicateValues);
      }
    }

    return processAdd(addRecord.getDN(), objectClasses, userAttrs,
                      opAttrs);
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
  public BindOperation processSimpleBind(String rawBindDN,
                                         String password)
  {
    return processSimpleBind(new ASN1OctetString(rawBindDN),
                             new ASN1OctetString(password),
                             null);
  }



  /**
   * Processes an internal bind operation with the provided
   * information.  Note that regardless of whether the bind is
   * successful, the authentication state for this internal connection
   * will not be altered in any way.
   *
   * @param  rawBindDN  The bind DN for the operation.
   * @param  password   The bind password for the operation.
   * @param  controls   The set of controls to include in the
   *                    request.
   *
   * @return  A reference to the bind operation that was processed and
   *          contains information about the result of the processing.
   */
  public BindOperation processSimpleBind(String rawBindDN,
                                         String password,
                                         List<Control> controls)
  {
    return processSimpleBind(new ASN1OctetString(rawBindDN),
                             new ASN1OctetString(password), controls);
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
    return processSimpleBind(rawBindDN, password, null);
  }



  /**
   * Processes an internal bind operation with the provided
   * information.  Note that regardless of whether the bind is
   * successful, the authentication state for this internal connection
   * will not be altered in any way.
   *
   * @param  rawBindDN  The bind DN for the operation.
   * @param  password   The bind password for the operation.
   * @param  controls   The set of controls to include in the request.
   *
   * @return  A reference to the bind operation that was processed and
   *          contains information about the result of the processing.
   */
  public BindOperation processSimpleBind(ByteString rawBindDN,
                                         ByteString password,
                                         List<Control> controls)
  {
    if (controls == null)
    {
      controls = new ArrayList<Control>(0);
    }

    BindOperationBasis bindOperation =
         new BindOperationBasis(this, nextOperationID(),
                           nextMessageID(), controls,
                           PROTOCOL_VERSION, rawBindDN, password);
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
    return processSimpleBind(bindDN, password, null);
  }



  /**
   * Processes an internal bind operation with the provided
   * information.  Note that regardless of whether the bind is
   * successful, the authentication state for this internal connection
   * will not be altered in any way.
   *
   * @param  bindDN    The bind DN for the operation.
   * @param  password  The bind password for the operation.
   * @param  controls  The set of controls to include in the request.
   *
   * @return  A reference to the bind operation that was processed and
   *          contains information about the result of the processing.
   */
  public BindOperation processSimpleBind(DN bindDN,
                                         ByteString password,
                                         List<Control> controls)
  {
    if (controls == null)
    {
      controls = new ArrayList<Control>(0);
    }

    BindOperationBasis bindOperation =
         new BindOperationBasis(this, nextOperationID(),
                           nextMessageID(), controls,
                           PROTOCOL_VERSION, bindDN, password);
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
    return processSASLBind(rawBindDN, saslMechanism, saslCredentials,
                           null);
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
   * @param  controls         The set of controls to include in the
   *                          request.
   *
   * @return  A reference to the bind operation that was processed and
   *          contains information about the result of the processing.
   */
  public BindOperation processSASLBind(ByteString rawBindDN,
                            String saslMechanism,
                            ASN1OctetString saslCredentials,
                            List<Control> controls)
  {
    if (controls == null)
    {
      controls = new ArrayList<Control>(0);
    }

    BindOperationBasis bindOperation =
         new BindOperationBasis(this, nextOperationID(),
                           nextMessageID(), controls,
                           PROTOCOL_VERSION, rawBindDN, saslMechanism,
                           saslCredentials);
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
    return processSASLBind(bindDN, saslMechanism, saslCredentials,
                           null);
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
   * @param  controls         The set of controls to include in the
   *                          request.
   *
   * @return  A reference to the bind operation that was processed and
   *          contains information about the result of the processing.
   */
  public BindOperation processSASLBind(DN bindDN,
                            String saslMechanism,
                            ASN1OctetString saslCredentials,
                            List<Control> controls)
  {
    if (controls == null)
    {
      controls = new ArrayList<Control>(0);
    }

    BindOperationBasis bindOperation =
         new BindOperationBasis(this, nextOperationID(),
                           nextMessageID(), controls,
                           PROTOCOL_VERSION, bindDN, saslMechanism,
                           saslCredentials);
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
  public CompareOperation processCompare(String rawEntryDN,
                                         String attributeType,
                                         String assertionValue)
  {
    return processCompare(new ASN1OctetString(rawEntryDN),
                          attributeType,
                          new ASN1OctetString(assertionValue), null);
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
   * @param  controls        The set of controls to include in the
   *                         request.
   *
   * @return  A reference to the compare operation that was processed
   *          and contains information about the result of the
   *          processing.
   */
  public CompareOperation processCompare(String rawEntryDN,
                                         String attributeType,
                                         String assertionValue,
                                         List<Control> controls)
  {
    return processCompare(new ASN1OctetString(rawEntryDN),
                          attributeType,
                          new ASN1OctetString(assertionValue),
                          controls);
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
    return processCompare(rawEntryDN, attributeType, assertionValue,
                          null);
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
   * @param  controls        The set of controls to include in the
   *                         request.
   *
   * @return  A reference to the compare operation that was processed
   *          and contains information about the result of the
   *          processing.
   */
  public CompareOperation processCompare(ByteString rawEntryDN,
                                         String attributeType,
                                         ByteString assertionValue,
                                         List<Control> controls)
  {
    if (controls == null)
    {
      controls = new ArrayList<Control>(0);
    }

    CompareOperationBasis compareOperation =
         new CompareOperationBasis(this, nextOperationID(),
                              nextMessageID(), controls, rawEntryDN,
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
    return processCompare(entryDN, attributeType, assertionValue,
                          null);
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
   * @param  controls        The set of controls to include in the
   *                         request.
   *
   * @return  A reference to the compare operation that was processed
   *          and contains information about the result of the
   *          processing.
   */
  public CompareOperation processCompare(DN entryDN,
                                         AttributeType attributeType,
                                         ByteString assertionValue,
                                         List<Control> controls)
  {
    if (controls == null)
    {
      controls = new ArrayList<Control>(0);
    }

    CompareOperationBasis compareOperation =
         new CompareOperationBasis(this, nextOperationID(),
                              nextMessageID(), controls, entryDN,
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
  public DeleteOperation processDelete(String rawEntryDN)
  {
    return processDelete(new ASN1OctetString(rawEntryDN), null);
  }



  /**
   * Processes an internal delete operation with the provided
   * information.
   *
   * @param  rawEntryDN  The entry DN for the delete operation.
   * @param  controls    The set of controls to include in the
   *                     request.
   *
   * @return  A reference to the delete operation that was processed
   *          and contains information about the result of the
   *          processing.
   */
  public DeleteOperation processDelete(String rawEntryDN,
                                       List<Control> controls)
  {
    return processDelete(new ASN1OctetString(rawEntryDN), controls);
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
    return processDelete(rawEntryDN, null);
  }



  /**
   * Processes an internal delete operation with the provided
   * information.
   *
   * @param  rawEntryDN  The entry DN for the delete operation.
   * @param  controls    The set of controls to include in the
   *                     request.
   *
   * @return  A reference to the delete operation that was processed
   *          and contains information about the result of the
   *          processing.
   */
  public DeleteOperation processDelete(ByteString rawEntryDN,
                                       List<Control> controls)
  {
    if (controls == null)
    {
      controls = new ArrayList<Control>(0);
    }

    DeleteOperationBasis deleteOperation =
         new DeleteOperationBasis(this, nextOperationID(),
                             nextMessageID(), controls, rawEntryDN);
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
    return processDelete(entryDN, null);
  }



  /**
   * Processes an internal delete operation with the provided
   * information.
   *
   * @param  entryDN   The entry DN for the delete operation.
   * @param  controls  The set of controls to include in the request.
   *
   * @return  A reference to the delete operation that was processed
   *          and contains information about the result of the
   *          processing.
   */
  public DeleteOperation processDelete(DN entryDN,
                                       List<Control> controls)
  {
    if (controls == null)
    {
      controls = new ArrayList<Control>(0);
    }

    DeleteOperationBasis deleteOperation =
         new DeleteOperationBasis(this, nextOperationID(),
                             nextMessageID(), controls, entryDN);
    deleteOperation.setInternalOperation(true);

    deleteOperation.run();
    return deleteOperation;
  }



  /**
   * Processes an internal delete operation with the provided
   * information.
   *
   * @param  deleteRecord  The delete change record entry to be
   *                       processed.
   *
   * @return  A reference to the delete operation that was processed
   *          and contains information about the result of the
   *          processing.
   */
  public DeleteOperation processDelete(
                              DeleteChangeRecordEntry deleteRecord)
  {
    return processDelete(deleteRecord.getDN());
  }



  /**
   * Processes an internal extended operation with the provided
   * information.
   *
   * @param  requestOID    The OID for the extended request.
   * @param  requestValue  The encoded value for the extended
   *                       operation, or <CODE>null</CODE> if there is
   *                       no value.
   *
   * @return  A reference to the extended operation that was processed
   *          and contains information about the result of the
   *          processing.
   */
  public ExtendedOperation processExtendedOperation(
                                String requestOID,
                                ASN1OctetString requestValue)
  {
    return processExtendedOperation(requestOID, requestValue, null);
  }



  /**
   * Processes an internal extended operation with the provided
   * information.
   *
   * @param  requestOID    The OID for the extended request.
   * @param  requestValue  The encoded value for the extended
   *                       operation, or <CODE>null</CODE> if there is
   *                       no value.
   * @param  controls      The set of controls to include in the
   *                       request.
   *
   * @return  A reference to the extended operation that was processed
   *          and contains information about the result of the
   *          processing.
   */
  public ExtendedOperation processExtendedOperation(
                                String requestOID,
                                ASN1OctetString requestValue,
                                List<Control> controls)
  {
    if (controls == null)
    {
      controls = new ArrayList<Control>(0);
    }

    ExtendedOperationBasis extendedOperation =
         new ExtendedOperationBasis(this, nextOperationID(),
                               nextMessageID(), controls, requestOID,
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
  public ModifyOperation processModify(String rawEntryDN,
                              List<RawModification> rawModifications)
  {
    return processModify(new ASN1OctetString(rawEntryDN),
                         rawModifications, null);
  }



  /**
   * Processes an internal modify operation with the provided
   * information.
   *
   * @param  rawEntryDN        The raw entry DN for this modify
   *                           operation.
   * @param  rawModifications  The set of modifications for this
   *                           modify operation.
   * @param  controls          The set of controls to include in the
   *                           request.
   *
   * @return  A reference to the modify operation that was processed
   *          and contains information about the result of the
   *          processing.
   */
  public ModifyOperation processModify(String rawEntryDN,
                              List<RawModification> rawModifications,
                              List<Control> controls)
  {
    return processModify(new ASN1OctetString(rawEntryDN),
                         rawModifications, controls);
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
                              List<RawModification> rawModifications)
  {
    return processModify(rawEntryDN, rawModifications, null);
  }



  /**
   * Processes an internal modify operation with the provided
   * information.
   *
   * @param  rawEntryDN        The raw entry DN for this modify
   *                           operation.
   * @param  rawModifications  The set of modifications for this
   *                           modify operation.
   * @param  controls          The set of controls to include in the
   *                           request.
   *
   * @return  A reference to the modify operation that was processed
   *          and contains information about the result of the
   *          processing.
   */
  public ModifyOperation processModify(ByteString rawEntryDN,
                              List<RawModification> rawModifications,
                              List<Control> controls)
  {
    if (controls == null)
    {
      controls = new ArrayList<Control>(0);
    }

    ModifyOperationBasis modifyOperation =
         new ModifyOperationBasis(this, nextOperationID(),
                             nextMessageID(), controls, rawEntryDN,
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
    return processModify(entryDN, modifications, null);
  }



  /**
   * Processes an internal modify operation with the provided
   * information.
   *
   * @param  entryDN        The entry DN for this modify operation.
   * @param  modifications  The set of modifications for this modify
   *                        operation.
   * @param  controls       The set of controls to include in the
   *                        request.
   *
   * @return  A reference to the modify operation that was processed
   *          and contains information about the result of the
   *          processing.
   */
  public ModifyOperation processModify(DN entryDN,
                              List<Modification> modifications,
                              List<Control> controls)
  {
    if (controls == null)
    {
      controls = new ArrayList<Control>(0);
    }

    ModifyOperationBasis modifyOperation =
         new ModifyOperationBasis(this, nextOperationID(),
                             nextMessageID(), controls, entryDN,
                             modifications);
    modifyOperation.setInternalOperation(true);
    modifyOperation.run();

    return modifyOperation;
  }



  /**
   * Processes an internal modify operation with the provided
   * information.
   *
   * @param  modifyRecord  The modify change record entry with
   *                       information about the changes to perform.
   *
   * @return  A reference to the modify operation that was processed
   *          and contains information about the result of the
   *          processing.
   */
  public ModifyOperation processModify(
                              ModifyChangeRecordEntry modifyRecord)
  {
    return processModify(modifyRecord.getDN().toString(),
                         modifyRecord.getModifications());
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
  public ModifyDNOperation processModifyDN(String rawEntryDN,
                                           String rawNewRDN,
                                           boolean deleteOldRDN)
  {
    return processModifyDN(new ASN1OctetString(rawEntryDN),
                           new ASN1OctetString(rawNewRDN),
                           deleteOldRDN, null, null);
  }



  /**
   * Processes an internal modify DN operation with the provided
   * information.
   *
   * @param  rawEntryDN    The current DN of the entry to rename.
   * @param  rawNewRDN     The new RDN to use for the entry.
   * @param  deleteOldRDN  The flag indicating whether the old RDN
   *                       value is to be removed from the entry.
   * @param  controls      The set of controls to include in the
   *                       request.
   *
   * @return  A reference to the modify DN operation that was
   *          processed and contains information about the result of
   *          the processing.
   */
  public ModifyDNOperation processModifyDN(String rawEntryDN,
                                           String rawNewRDN,
                                           boolean deleteOldRDN,
                                           List<Control> controls)
  {
    return processModifyDN(new ASN1OctetString(rawEntryDN),
                           new ASN1OctetString(rawNewRDN),
                           deleteOldRDN, null, controls);
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
    return processModifyDN(rawEntryDN, rawNewRDN, deleteOldRDN, null,
                           null);
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
  public ModifyDNOperation processModifyDN(String rawEntryDN,
                                           String rawNewRDN,
                                           boolean deleteOldRDN,
                                           String rawNewSuperior)
  {
    return processModifyDN(new ASN1OctetString(rawEntryDN),
                           new ASN1OctetString(rawNewRDN),
                           deleteOldRDN,
                           new ASN1OctetString(rawNewSuperior), null);
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
   * @param  controls        The set of controls to include in the
   *                         request.
   *
   * @return  A reference to the modify DN operation that was
   *          processed and contains information about the result of
   *          the processing.
   */
  public ModifyDNOperation processModifyDN(String rawEntryDN,
                                           String rawNewRDN,
                                           boolean deleteOldRDN,
                                           String rawNewSuperior,
                                           List<Control> controls)
  {
    return processModifyDN(new ASN1OctetString(rawEntryDN),
                           new ASN1OctetString(rawNewRDN),
                           deleteOldRDN,
                           new ASN1OctetString(rawNewSuperior),
                           controls);
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
    return processModifyDN(rawEntryDN, rawNewRDN, deleteOldRDN,
                           rawNewSuperior, null);
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
   * @param  controls        The set of controls to include in the
   *                         request.
   *
   * @return  A reference to the modify DN operation that was
   *          processed and contains information about the result of
   *          the processing.
   */
  public ModifyDNOperation processModifyDN(ByteString rawEntryDN,
                                           ByteString rawNewRDN,
                                           boolean deleteOldRDN,
                                           ByteString rawNewSuperior,
                                           List<Control> controls)
  {
    if (controls == null)
    {
      controls = new ArrayList<Control>(0);
    }

    ModifyDNOperationBasis modifyDNOperation =
         new ModifyDNOperationBasis(this, nextOperationID(),
                               nextMessageID(), controls, rawEntryDN,
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
  public ModifyDNOperation processModifyDN(DN entryDN,
                                           RDN newRDN,
                                           boolean deleteOldRDN)
  {
    return processModifyDN(entryDN, newRDN, deleteOldRDN, null, null);
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
  public ModifyDNOperation processModifyDN(DN entryDN,
                                           RDN newRDN,
                                           boolean deleteOldRDN,
                                           DN newSuperior)
  {
    return processModifyDN(entryDN, newRDN, deleteOldRDN, newSuperior,
                           null);
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
   * @param  controls      The set of controls to include in the
   *                       request.
   *
   * @return  A reference to the modify DN operation that was
   *          processed and contains information about the result of
   *          the processing.
   */
  public ModifyDNOperation processModifyDN(DN entryDN,
                                           RDN newRDN,
                                           boolean deleteOldRDN,
                                           DN newSuperior,
                                           List<Control> controls)
  {
    if (controls == null)
    {
      controls = new ArrayList<Control>(0);
    }

    ModifyDNOperationBasis modifyDNOperation =
         new ModifyDNOperationBasis(this, nextOperationID(),
                               nextMessageID(), controls, entryDN,
                               newRDN, deleteOldRDN, newSuperior);
    modifyDNOperation.setInternalOperation(true);

    modifyDNOperation.run();
    return modifyDNOperation;
  }



  /**
   * Processes an internal modify DN operation with the provided
   * information.
   *
   * @param  modifyDNRecord  The modify DN change record entry with
   *                         information about the processing to
   *                         perform.
   *
   * @return  A reference to the modify DN operation that was
   *          processed and contains information about the result of
   *          the processing.
   */
  public ModifyDNOperation processModifyDN(
              ModifyDNChangeRecordEntry modifyDNRecord)
  {
    return processModifyDN(modifyDNRecord.getDN(),
                           modifyDNRecord.getNewRDN(),
                           modifyDNRecord.deleteOldRDN(),
                           modifyDNRecord.getNewSuperiorDN());
  }



  /**
   * Processes an internal search operation with the provided
   * information.  It will not dereference any aliases, will not
   * request a size or time limit, and will retrieve all user
   * attributes.
   *
   * @param  rawBaseDN     The base DN for the search.
   * @param  scope         The scope for the search.
   * @param  filterString  The string representation of the filter for
   *                       the search.
   *
   * @return  A reference to the internal search operation that was
   *          processed and contains information about the result of
   *          the processing as well as lists of the matching entries
   *          and search references.
   *
   * @throws  DirectoryException  If the provided filter string cannot
   *                              be decoded as a search filter.
   */
  public InternalSearchOperation processSearch(String rawBaseDN,
                                      SearchScope scope,
                                      String filterString)
         throws DirectoryException
  {
    RawFilter rawFilter;
    try
    {
      rawFilter = RawFilter.create(filterString);
    }
    catch (LDAPException le)
    {
      throw new DirectoryException(
                     ResultCode.valueOf(le.getResultCode()),
                     le.getErrorMessage(), le);
    }

    return processSearch(new ASN1OctetString(rawBaseDN), scope,
                         rawFilter);
  }



  /**
   * Processes an internal search operation with the provided
   * information.
   *
   * @param  rawBaseDN     The base DN for the search.
   * @param  scope         The scope for the search.
   * @param  derefPolicy   The alias dereferencing policy for the
   *                       search.
   * @param  sizeLimit     The size limit for the search.
   * @param  timeLimit     The time limit for the search.
   * @param  typesOnly     The typesOnly flag for the search.
   * @param  filterString  The string representation of the filter for
   *                       the search.
   * @param  attributes    The set of requested attributes for the
   *                       search.
   *
   * @return  A reference to the internal search operation that was
   *          processed and contains information about the result of
   *          the processing as well as lists of the matching entries
   *          and search references.
   *
   * @throws  DirectoryException  If the provided filter string cannot
   *                              be decoded as a search filter.
   */
  public InternalSearchOperation
              processSearch(String rawBaseDN, SearchScope scope,
                            DereferencePolicy derefPolicy,
                            int sizeLimit, int timeLimit,
                            boolean typesOnly, String filterString,
                            LinkedHashSet<String> attributes)
         throws DirectoryException
  {
    return processSearch(rawBaseDN, scope, derefPolicy, sizeLimit,
                         timeLimit, typesOnly, filterString,
                         attributes, null, null);
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
   * @param  filterString    The string representation of the filter
   *                         for the search.
   * @param  attributes      The set of requested attributes for the
   *                         search.
   * @param  searchListener  The internal search listener that should
   *                         be used to handle the matching entries
   *                         and references.
   *
   * @return  A reference to the internal search operation that was
   *          processed and contains information about the result of
   *          the processing as well as lists of the matching entries
   *          and search references.
   *
   * @throws  DirectoryException  If the provided filter string cannot
   *                              be decoded as a search filter.
   */
  public InternalSearchOperation
              processSearch(String rawBaseDN, SearchScope scope,
                            DereferencePolicy derefPolicy,
                            int sizeLimit, int timeLimit,
                            boolean typesOnly, String filterString,
                            LinkedHashSet<String> attributes,
                            InternalSearchListener searchListener)
         throws DirectoryException
  {
    return processSearch(rawBaseDN, scope, derefPolicy, sizeLimit,
                         timeLimit, typesOnly, filterString,
                         attributes, null, searchListener);
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
   * @param  filterString    The string representation of the filter
   *                         for the search.
   * @param  attributes      The set of requested attributes for the
   *                         search.
   * @param  controls        The set of controls to include in the
   *                         request.
   * @param  searchListener  The internal search listener that should
   *                         be used to handle the matching entries
   *                         and references.
   *
   * @return  A reference to the internal search operation that was
   *          processed and contains information about the result of
   *          the processing as well as lists of the matching entries
   *          and search references.
   *
   * @throws  DirectoryException  If the provided filter string cannot
   *                              be decoded as a search filter.
   */
  public InternalSearchOperation
              processSearch(String rawBaseDN, SearchScope scope,
                            DereferencePolicy derefPolicy,
                            int sizeLimit, int timeLimit,
                            boolean typesOnly, String filterString,
                            LinkedHashSet<String> attributes,
                            List<Control> controls,
                            InternalSearchListener searchListener)
         throws DirectoryException
  {
    RawFilter rawFilter;
    try
    {
      rawFilter = RawFilter.create(filterString);
    }
    catch (LDAPException le)
    {
      throw new DirectoryException(
                     ResultCode.valueOf(le.getResultCode()),
                     le.getErrorMessage(), le);
    }

    return processSearch(new ASN1OctetString(rawBaseDN), scope,
                         derefPolicy, sizeLimit, timeLimit, typesOnly,
                         rawFilter, attributes, controls,
                         searchListener);
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
                                      RawFilter filter)
  {
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
                            boolean typesOnly, RawFilter filter,
                            LinkedHashSet<String> attributes)
  {
    return processSearch(rawBaseDN, scope, derefPolicy, sizeLimit,
                         timeLimit, typesOnly, filter, attributes,
                         null, null);
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
                            boolean typesOnly, RawFilter filter,
                            LinkedHashSet<String> attributes,
                            InternalSearchListener searchListener)
  {
    return processSearch(rawBaseDN, scope, derefPolicy, sizeLimit,
                         timeLimit, typesOnly, filter, attributes,
                         null, searchListener);
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
   * @param  controls        The set of controls to include in the
   *                         request.
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
                            boolean typesOnly, RawFilter filter,
                            LinkedHashSet<String> attributes,
                            List<Control> controls,
                            InternalSearchListener searchListener)
  {
    if (controls == null)
    {
      controls = new ArrayList<Control>(0);
    }

    InternalSearchOperation searchOperation =
         new InternalSearchOperation(this, nextOperationID(),
                                     nextMessageID(), controls,
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
    return processSearch(baseDN, scope, derefPolicy, sizeLimit,
                         timeLimit, typesOnly, filter, attributes,
                         null, null);
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
    return processSearch(baseDN, scope, derefPolicy, sizeLimit,
                         timeLimit, typesOnly, filter, attributes,
                         null, searchListener);
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
   * @param  controls        The set of controls to include in the
   *                         request.
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
                            List<Control> controls,
                            InternalSearchListener searchListener)
  {
    if (controls == null)
    {
      controls = new ArrayList<Control>(0);
    }

    InternalSearchOperation searchOperation =
         new InternalSearchOperation(this, nextOperationID(),
                                     nextMessageID(), controls,
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
   *
   * @throws  DirectoryException  If a problem occurs while processing
   *                              the entry and the search should be
   *                              terminated.
   */
  @org.opends.server.types.PublicAPI(
       stability=org.opends.server.types.StabilityLevel.PRIVATE,
       mayInstantiate=false,
       mayExtend=false,
       mayInvoke=false)
  @Override()
  public void sendSearchEntry(SearchOperation searchOperation,
                              SearchResultEntry searchEntry)
         throws DirectoryException
  {
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
   *
   * @throws  DirectoryException  If a problem occurs while processing
   *                              the entry and the search should be
   *                              terminated.
   */
  @org.opends.server.types.PublicAPI(
       stability=org.opends.server.types.StabilityLevel.PRIVATE,
       mayInstantiate=false,
       mayExtend=false,
       mayInvoke=false)
  @Override()
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
   * @param  intermediateResponse  The intermediate response message
   *                               to be sent.
   *
   * @return  <CODE>true</CODE> if processing on the associated
   *          operation should continue, or <CODE>false</CODE> if not.
   */
  @org.opends.server.types.PublicAPI(
       stability=org.opends.server.types.StabilityLevel.PRIVATE,
       mayInstantiate=false,
       mayExtend=false,
       mayInvoke=false)
  @Override()
  protected boolean sendIntermediateResponseMessage(
                         IntermediateResponse intermediateResponse)
  {
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
   */
  @org.opends.server.types.PublicAPI(
       stability=org.opends.server.types.StabilityLevel.PRIVATE,
       mayInstantiate=false,
       mayExtend=false,
       mayInvoke=false)
  @Override()
  public void disconnect(DisconnectReason disconnectReason,
                         boolean sendNotification,
                         Message message)
  {
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
  @Override()
  public boolean bindInProgress()
  {
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
  @org.opends.server.types.PublicAPI(
       stability=org.opends.server.types.StabilityLevel.PRIVATE,
       mayInstantiate=false,
       mayExtend=false,
       mayInvoke=false)
  @Override()
  public void setBindInProgress(boolean bindInProgress)
  {
    // No implementation is required.
  }



  /**
   * Retrieves the set of operations in progress for this client
   * connection.  This list must not be altered by any caller.
   *
   * @return  The set of operations in progress for this client
   *          connection.
   */
  @org.opends.server.types.PublicAPI(
       stability=org.opends.server.types.StabilityLevel.PRIVATE,
       mayInstantiate=false,
       mayExtend=false,
       mayInvoke=false)
  @Override()
  public Collection<Operation> getOperationsInProgress()
  {
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
  @org.opends.server.types.PublicAPI(
       stability=org.opends.server.types.StabilityLevel.PRIVATE,
       mayInstantiate=false,
       mayExtend=false,
       mayInvoke=false)
  @Override()
  public AbstractOperation getOperationInProgress(int messageID)
  {
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
  @org.opends.server.types.PublicAPI(
       stability=org.opends.server.types.StabilityLevel.PRIVATE,
       mayInstantiate=false,
       mayExtend=false,
       mayInvoke=false)
  @Override()
  public boolean removeOperationInProgress(int messageID)
  {
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
  @org.opends.server.types.PublicAPI(
       stability=org.opends.server.types.StabilityLevel.PRIVATE,
       mayInstantiate=false,
       mayExtend=false,
       mayInvoke=false)
  @Override()
  public CancelResult cancelOperation(int messageID,
                                      CancelRequest cancelRequest)
  {
    // Internal operations cannot be cancelled.
    // TODO: i18n
    return new CancelResult(ResultCode.CANNOT_CANCEL,
        Message.raw("Internal operations cannot be cancelled"));
  }



  /**
   * Attempts to cancel all operations in progress on this connection.
   *
   * @param  cancelRequest  An object providing additional information
   *                        about how the cancel should be processed.
   */
  @org.opends.server.types.PublicAPI(
       stability=org.opends.server.types.StabilityLevel.PRIVATE,
       mayInstantiate=false,
       mayExtend=false,
       mayInvoke=false)
  @Override()
  public void cancelAllOperations(CancelRequest cancelRequest)
  {
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
  @org.opends.server.types.PublicAPI(
       stability=org.opends.server.types.StabilityLevel.PRIVATE,
       mayInstantiate=false,
       mayExtend=false,
       mayInvoke=false)
  @Override()
  public void cancelAllOperationsExcept(CancelRequest cancelRequest,
                                        int messageID)
  {
    // No implementation is required since internal operations cannot
    // be cancelled.
  }



  /**
   * Retrieves a one-line summary of this client connection in a form
   * that is suitable for including in the monitor entry for the
   * associated connection handler.  It should be in a format that is
   * both humand readable and machine parseable (e.g., a
   * space-delimited name-value list, with quotes around the values).
   *
   * @return  A one-line summary of this client connection in a form
   *          that is suitable for including in the monitor entry for
   *          the associated connection handler.
   */
  @Override()
  public String getMonitorSummary()
  {
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
  @Override()
  public void toString(StringBuilder buffer)
  {
    buffer.append("InternalClientConnection(connID=");
    buffer.append(connectionID);
    buffer.append(", authDN=\"");

    if (getAuthenticationInfo() != null)
    {
      buffer.append(getAuthenticationInfo().getAuthenticationDN());
    }

    buffer.append("\")");
  }

  /**
   * Called near the end of server shutdown.  This ensures that a new
   * InternalClientConnection is created if the server is immediately
   * restarted as part of an in-core restart.
   */
  static void clearRootClientConnectionAtShutdown()
  {
    rootConnection = null;
  }

  /**
   * To be implemented.
   *
   * @return number of operations performed on this connection
   */
  @Override
  public long getNumberOfOperations() {
    // Internal operations will not be limited.
    return 0;
  }
}

