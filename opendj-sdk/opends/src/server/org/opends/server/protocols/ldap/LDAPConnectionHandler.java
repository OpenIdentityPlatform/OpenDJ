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
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.protocols.ldap;



import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;

import org.opends.server.api.AlertGenerator;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConfigurableComponent;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.api.ConnectionSecurityProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.InitializationException;
import org.opends.server.config.BooleanConfigAttribute;
import org.opends.server.config.ConfigAttribute;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.IntegerConfigAttribute;
import org.opends.server.config.IntegerWithUnitConfigAttribute;
import org.opends.server.config.MultiChoiceConfigAttribute;
import org.opends.server.config.StringConfigAttribute;
import org.opends.server.extensions.NullConnectionSecurityProvider;
import org.opends.server.extensions.TLSConnectionSecurityProvider;
import org.opends.server.types.AddressMask;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.DN;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SSLClientAuthPolicy;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.Access.*;
import static org.opends.server.loggers.Debug.*;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ProtocolMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a connection handler that will be used for communicating
 * with clients over LDAP.  It is actually implemented in two parts:  as a
 * connection handler and one or more request handlers.  The connection handler
 * is responsible for accepting new connections and registering each of them
 * with a request handler.  The request handlers then are responsible for
 * reading requests from the clients and parsing them as operations.  A single
 * request handler may be used, but having multiple handlers might provide
 * better performance in a multi-CPU system.
 */
public class LDAPConnectionHandler
       extends ConnectionHandler
       implements ConfigurableComponent, AlertGenerator
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.protocols.ldap.LDAPConnectionHandler";



  /**
   * The unit string that will be used to designate that a value is in bytes.
   */
  public static final String UNIT_BYTES = "B";



  /**
   * The unit string that will be used to designate that a value is in
   * kilobytes.
   */
  public static final String UNIT_KILOBYTES = "KB";



  /**
   * The unit string that will be used to designate that a value is in
   * kibibytes.
   */
  public static final String UNIT_KIBIBYTES = "KiB";



  /**
   * The unit string that will be used to designate that a value is in
   * megabytes.
   */
  public static final String UNIT_MEGABYTES = "MB";



  /**
   * The unit string that will be used to designate that a value is in
   * mebibytes.
   */
  public static final String UNIT_MEBIBYTES = "MiB";



  /**
   * The hash map that holds the units that may be provided in conjunction with
   * the maximum request size.
   */
  private static final HashMap<String,Double> SIZE_UNITS =
       new HashMap<String,Double>();

  static
  {
    SIZE_UNITS.put(UNIT_BYTES, 1.0);
    SIZE_UNITS.put(UNIT_KILOBYTES, 1000.0);
    SIZE_UNITS.put(UNIT_KIBIBYTES, 1024.0);
    SIZE_UNITS.put(UNIT_MEGABYTES, 1000000.0);
    SIZE_UNITS.put(UNIT_MEBIBYTES, 1048576.0);
  }



  /**
   * The maximum value that may be specified for the max request size
   * configuration attribute.
   */
  private static final int MAX_REQUEST_SIZE_LIMIT = 2147483647;



  // The set of clients that are explicitly allowed access to the server.
  private AddressMask[] allowedClients;

  // The set of clients that have been explicitly denied access to the server.
  private AddressMask[] deniedClients;

  // Indicates whether to allow LDAPv2 clients.
  private boolean allowLDAPv2;

  // Indicates whether to allow the reuse address socket option.
  private boolean allowReuseAddress;

  // Indicates whether to allow startTLS extended operations on this connection.
  private boolean allowStartTLS;

  // Indicates whether this connection handler is enabled.
  private boolean enabled;

  // Indicates whether usage statistics should be maintained.
  private boolean keepStats;

  // Indicates whether the server should send an LDAP notice of disconnection
  // message to a client if a connection is rejected.
  private boolean sendRejectionNotice;

  // Indicates whether the Directory Server is in the process of shutting down.
  private boolean shutdownRequested;

  // Indicates whether to use TCP keepalive messages for new connections.
  private boolean useKeepAlive;

  // Indicates whether to use SSL to communicate with the clients.
  private boolean useSSL;

  // Indicates whether to use TCP_NODELAY for new connections.
  private boolean useTCPNoDelay;

  // The connection security provider that will be used by default for new
  // client connections.
  private ConnectionSecurityProvider securityProvider;

  // The DN of the configuration entry for this connection handler.
  private DN configEntryDN;

  // The set of addresses on which to listen for new connections.
  private HashSet<InetAddress> listenAddresses;

  // The backlog that will be used for the accept queue.
  private int backlog;

  // The port on which this connection handler should listen for requests.
  private int listenPort;

  // The maximum ASN.1 element value length that will be allowed when processing
  // requests for this connection handler.
  private int maxRequestSize;

  // The number of request handlers that should be used for this connection
  // handler.
  private int numRequestHandlers;

  // The index to the request handler that will be used for the next connection
  // accepted by the server.
  private int requestHandlerIndex;

  // The set of request handlers that are associated with this connection
  // handler.
  private LDAPRequestHandler[] requestHandlers;

  // The set of statistics collected for this connection handler.
  private LDAPStatistics statTracker;

  // The selector that will be used to multiplex connection acceptance across
  // multiple sockets by a single thread.
  private Selector selector;

  // The SSL client auth policy used by this connection handler.
  private SSLClientAuthPolicy sslClientAuthPolicy;

  // The unique name assigned to this connection handler.
  private String handlerName;

  // The security mechanism used for connections accepted by this connection
  // handler.
  private String securityMechanism;

  // The nickname of the SSL certificate that should be used if SSL is enabled.
  private String sslServerCertNickname;

  // The set of SSL cipher suites that should be allowed.
  private String[] enabledSSLCipherSuites;

  // The set of SSL protocols that should be allowed.
  private String[] enabledSSLProtocols;

  // The thread being used to run this connection handler.
  private Thread connHandlerThread;



  /**
   * Creates a new instance of this LDAP connection handler.  It must be
   * initialized before it may be used.
   */
  public LDAPConnectionHandler()
  {
    super("LDAP Connection Handler Thread");

    assert debugConstructor(CLASS_NAME);

    // No real implementation is required.  Do all the work in the
    // initializeConnectionHandler method.
  }



  /**
   * Initializes this connection handler based on the information in the
   * provided configuration entry.
   *
   * @param  configEntry  The configuration entry that contains the information
   *                      to use to initialize this connection handler.
   *
   * @throws  ConfigException  If there is a problem with the configuration for
   *                           this connection handler.
   *
   * @throws  InitializationException  If a problem occurs while attempting to
   *                                   initialize this connection handler.
   */
  public void initializeConnectionHandler(ConfigEntry configEntry)
         throws ConfigException, InitializationException
  {
    assert debugEnter(CLASS_NAME, "initializeConnectionHandler",
                      String.valueOf(configEntry));


    enabled = true;

    configEntryDN = configEntry.getDN();

    // Determine the set of addresses on which to listen.  There can be
    // multiple addresses specified.
    int msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_LISTEN_ADDRESS;
    listenAddresses = new HashSet<InetAddress>();
    StringConfigAttribute addrStub =
         new StringConfigAttribute(ATTR_LISTEN_ADDRESS, getMessage(msgID),
                                   true, true, true);
    try
    {
      StringConfigAttribute addrAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(addrStub);
      if ((addrAttr == null) || addrAttr.activeValues().isEmpty())
      {
        // This is fine -- we'll just listen on all IPv4 addresses.
        listenAddresses.add(InetAddress.getByName("0.0.0.0"));
      }
      else
      {
        for (String s : addrAttr.activeValues())
        {
          try
          {
            listenAddresses.add(InetAddress.getByName(s));
          }
          catch (UnknownHostException uhe)
          {
            assert debugException(CLASS_NAME, "initializeConnectionHandler",
                                  uhe);

            msgID = MSGID_LDAP_CONNHANDLER_UNKNOWN_LISTEN_ADDRESS;
            String message = getMessage(msgID, s,
                                        stackTraceToSingleLineString(uhe));
            throw new ConfigException(msgID, message, uhe);
          }
        }
      }
    }
    catch (ConfigException ce)
    {
      assert debugException(CLASS_NAME, "initializeConnectionHandler", ce);

      throw ce;
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializeConnectionHandler", e);

      msgID = MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_LISTEN_ADDRESS;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Determine the port on which to listen.  There may only be a single port
    // specified.
    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_LISTEN_PORT;
    IntegerConfigAttribute portStub =
         new IntegerConfigAttribute(ATTR_LISTEN_PORT, getMessage(msgID), true,
                                    false, true, true, 1, true, 65535);
    try
    {
      IntegerConfigAttribute portAttr =
           (IntegerConfigAttribute) configEntry.getConfigAttribute(portStub);
      if (portAttr == null)
      {
        msgID = MSGID_LDAP_CONNHANDLER_NO_LISTEN_PORT;
        String message = getMessage(msgID, String.valueOf(configEntryDN));
        throw new ConfigException(msgID, message);
      }

      listenPort = portAttr.activeIntValue();
    }
    catch (ConfigException ce)
    {
      assert debugException(CLASS_NAME, "initializeConnectionHandler", ce);

      throw ce;
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializeConnectionHandler", e);

      msgID = MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_LISTEN_PORT;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Determine the accept backlog.
    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_BACKLOG;
    IntegerConfigAttribute backlogStub =
         new IntegerConfigAttribute(ATTR_ACCEPT_BACKLOG, getMessage(msgID),
                                 true, false, true, true, 1, true,
                                 Integer.MAX_VALUE);
    try
    {
      IntegerConfigAttribute backlogAttr =
           (IntegerConfigAttribute) configEntry.getConfigAttribute(backlogStub);
      if (backlogAttr == null)
      {
        // This is fine -- just use the default value.
        backlog = DEFAULT_ACCEPT_BACKLOG;
      }
      else
      {
        backlog = backlogAttr.activeIntValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializeConnectionHandler", e);

      msgID = MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_BACKLOG;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Determine the set of allowed clients.
    allowedClients = null;
    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_ALLOWED_CLIENTS;
    StringConfigAttribute allowedStub =
         new StringConfigAttribute(ATTR_ALLOWED_CLIENT, getMessage(msgID),
                                   false, true, false);
    try
    {
      StringConfigAttribute allowedAttr =
           (StringConfigAttribute)
           configEntry.getConfigAttribute(allowedStub);
      if (allowedAttr != null)
      {
        List<String> maskStrings = allowedAttr.activeValues();
        allowedClients = new AddressMask[maskStrings.size()];
        for (int i=0; i < allowedClients.length; i++)
        {
          try
          {
            allowedClients[i] = AddressMask.decode(maskStrings.get(i));
          }
          catch (ConfigException ce)
          {
            assert debugException(CLASS_NAME, "initializeConnectionHandler",
                                  ce);

            msgID = MSGID_LDAP_CONNHANDLER_INVALID_ADDRESS_MASK;
            String message = getMessage(msgID, maskStrings.get(i),
                                        ATTR_ALLOWED_CLIENT,
                                        String.valueOf(configEntryDN),
                                        stackTraceToSingleLineString(ce));
            throw new ConfigException(msgID, message, ce);
          }
        }
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializeConnectionHandler", e);

      msgID = MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_ALLOWED_CLIENTS;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Determine the set of denied clients.
    deniedClients = null;
    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_DENIED_CLIENTS;
    StringConfigAttribute deniedStub =
         new StringConfigAttribute(ATTR_DENIED_CLIENT, getMessage(msgID),
                                   false, true, false);
    try
    {
      StringConfigAttribute deniedAttr =
           (StringConfigAttribute)
           configEntry.getConfigAttribute(deniedStub);
      if (deniedAttr != null)
      {
        List<String> maskStrings = deniedAttr.activeValues();
        deniedClients = new AddressMask[maskStrings.size()];
        for (int i=0; i < deniedClients.length; i++)
        {
          try
          {
            deniedClients[i] = AddressMask.decode(maskStrings.get(i));
          }
          catch (ConfigException ce)
          {
            assert debugException(CLASS_NAME, "initializeConnectionHandler",
                                  ce);

            msgID = MSGID_LDAP_CONNHANDLER_INVALID_ADDRESS_MASK;
            String message = getMessage(msgID, maskStrings.get(i),
                                        ATTR_ALLOWED_CLIENT,
                                        String.valueOf(configEntryDN),
                                        stackTraceToSingleLineString(ce));
            throw new ConfigException(msgID, message, ce);
          }
        }
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializeConnectionHandler", e);

      msgID = MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_DENIED_CLIENTS;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Determine whether to allow LDAPv2 clients.
    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_ALLOW_LDAPV2;
    BooleanConfigAttribute allowLDAPv2Stub =
         new BooleanConfigAttribute(ATTR_ALLOW_LDAPV2, getMessage(msgID),
                                    false);
    try
    {
      BooleanConfigAttribute allowLDAPv2Attr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(allowLDAPv2Stub);
      if (allowLDAPv2Attr == null)
      {
        // This is fine -- we'll just use the default behavior, which is to
        // allow these clients.
        allowLDAPv2 = DEFAULT_ALLOW_LDAPV2;
      }
      else
      {
        allowLDAPv2 = allowLDAPv2Attr.activeValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializeConnectionHandler", e);

      msgID = MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_ALLOW_LDAPV2;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Determine whether to keep LDAP statistics.
    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_KEEP_STATS;
    BooleanConfigAttribute keepStatsStub =
         new BooleanConfigAttribute(ATTR_KEEP_LDAP_STATS, getMessage(msgID),
                                    false);
    try
    {
      BooleanConfigAttribute keepStatsAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(keepStatsStub);
      if (keepStatsAttr == null)
      {
        // This is fine -- we'll just use the default behavior, which is to
        // allow these clients.
        keepStats = DEFAULT_KEEP_LDAP_STATS;
      }
      else
      {
        keepStats = keepStatsAttr.activeValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializeConnectionHandler", e);

      msgID = MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_KEEP_STATS;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Determine the number of request handlers to maintain.
    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_NUM_REQUEST_HANDLERS;
    IntegerConfigAttribute reqHandlerStub =
         new IntegerConfigAttribute(ATTR_NUM_REQUEST_HANDLERS,
                                    getMessage(msgID), true, false, true,
                                    true, 1, false, 0);
    try
    {
      IntegerConfigAttribute reqHandlerAttr =
           (IntegerConfigAttribute)
           configEntry.getConfigAttribute(reqHandlerStub);
      if (reqHandlerAttr == null)
      {
        // This is fine -- we'll just use the default value.
        numRequestHandlers = DEFAULT_NUM_REQUEST_HANDLERS;
      }
      else
      {
        numRequestHandlers = reqHandlerAttr.activeIntValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializeConnectionHandler", e);

      msgID = MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_NUM_REQUEST_HANDLERS;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Determine whether to send a notice to clients on rejection.
    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_SEND_REJECTION_NOTICE;
    BooleanConfigAttribute notifyRejectsStub =
         new BooleanConfigAttribute(ATTR_SEND_REJECTION_NOTICE,
                                    getMessage(msgID), false);
    try
    {
      BooleanConfigAttribute notifyRejectsAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(notifyRejectsStub);
      if (notifyRejectsAttr == null)
      {
        // This is fine -- we'll just use the default value.
        sendRejectionNotice = DEFAULT_SEND_REJECTION_NOTICE;
      }
      else
      {
        sendRejectionNotice = notifyRejectsAttr.activeValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializeConnectionHandler", e);

      msgID = MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_SEND_REJECTION_NOTICE;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Determine whether to use TCP keepalive.
    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_USE_TCP_KEEPALIVE;
    BooleanConfigAttribute keepAliveStub =
         new BooleanConfigAttribute(ATTR_USE_TCP_KEEPALIVE, getMessage(msgID),
                                    false);
    try
    {
      BooleanConfigAttribute keepAliveAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(keepAliveStub);
      if (keepAliveAttr == null)
      {
        // This is fine -- we'll just use the default.
        useKeepAlive = DEFAULT_USE_TCP_KEEPALIVE;
      }
      else
      {
        useKeepAlive = keepAliveAttr.activeValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializeConnectionHandler", e);

      msgID = MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_USE_TCP_KEEPALIVE;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Determine whether to use TCP nodelay.
    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_USE_TCP_NODELAY;
    BooleanConfigAttribute noDelayStub =
         new BooleanConfigAttribute(ATTR_USE_TCP_NODELAY, getMessage(msgID),
                                    false);
    try
    {
      BooleanConfigAttribute noDelayAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(noDelayStub);
      if (noDelayAttr == null)
      {
        // This is fine -- we'll just use the default.
        useTCPNoDelay = DEFAULT_USE_TCP_NODELAY;
      }
      else
      {
        useTCPNoDelay = noDelayAttr.activeValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializeConnectionHandler", e);

      msgID = MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_USE_TCP_NODELAY;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Determine whether to allow reuse of address/port combinations.
    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_ALLOW_REUSE_ADDRESS;
    BooleanConfigAttribute reuseAddrStub =
         new BooleanConfigAttribute(ATTR_ALLOW_REUSE_ADDRESS,
                                    getMessage(msgID), true);
    try
    {
      BooleanConfigAttribute reuseAddrAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(reuseAddrStub);
      if (reuseAddrAttr == null)
      {
        // This is fine -- we'll just use the default.
        allowReuseAddress = DEFAULT_ALLOW_REUSE_ADDRESS;
      }
      else
      {
        allowReuseAddress = reuseAddrAttr.activeValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializeConnectionHandler", e);

      msgID = MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_ALLOW_REUSE_ADDRESS;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Determine the maximum allowed request size.
    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_MAX_REQUEST_SIZE;
    IntegerWithUnitConfigAttribute maxReqSizeStub =
         new IntegerWithUnitConfigAttribute(ATTR_MAX_REQUEST_SIZE,
                                            getMessage(msgID), false,
                                            SIZE_UNITS, true, 0, true,
                                            MAX_REQUEST_SIZE_LIMIT);
    try
    {
      IntegerWithUnitConfigAttribute maxReqSizeAttr =
           (IntegerWithUnitConfigAttribute)
           configEntry.getConfigAttribute(maxReqSizeStub);
      if (maxReqSizeAttr == null)
      {
        // This is fine -- we'll just use the default value.
        maxRequestSize = DEFAULT_MAX_REQUEST_SIZE;
      }
      else
      {
        maxRequestSize = (int) maxReqSizeAttr.activeCalculatedValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializeConnectionHandler", e);

      msgID = MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_MAX_REQUEST_SIZE;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Determine whether to use SSL.
    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_USE_SSL;
    BooleanConfigAttribute useSSLStub =
         new BooleanConfigAttribute(ATTR_USE_SSL, getMessage(msgID), true);
    try
    {
      BooleanConfigAttribute useSSLAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(useSSLStub);
      if (useSSLAttr == null)
      {
        // This is fine -- we'll just use the default value.
        useSSL = DEFAULT_USE_SSL;
      }
      else
      {
        useSSL = useSSLAttr.activeValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializeConnectionHandler", e);

      msgID = MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_USE_SSL;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Determine whether to allow the StartTLS extended operation.
    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_ALLOW_STARTTLS;
    BooleanConfigAttribute startTLSStub =
         new BooleanConfigAttribute(ATTR_ALLOW_STARTTLS, getMessage(msgID),
                                    false);
    try
    {
      BooleanConfigAttribute startTLSAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(startTLSStub);
      if (startTLSAttr == null)
      {
        // This is fine -- we'll just use the default.
        allowStartTLS = DEFAULT_ALLOW_STARTTLS;
      }
      else
      {
        allowStartTLS = startTLSAttr.activeValue();
      }


      // See if both SSL and startTLS are configured.  If so, we'll have to
      // disable startTLS because they can't both be used concurrently.
      if (useSSL && allowStartTLS)
      {
        msgID = MSGID_LDAP_CONNHANDLER_CANNOT_HAVE_SSL_AND_STARTTLS;
        String message = getMessage(msgID, String.valueOf(configEntryDN));
        logError(ErrorLogCategory.CONFIGURATION,
                 ErrorLogSeverity.SEVERE_WARNING, message, msgID);

        allowStartTLS = false;
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializeConnectionHandler", e);

      msgID = MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_ALLOW_STARTTLS;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Determine how to handle SSL client authentication.
    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_SSL_CLIENT_AUTH_POLICY;
    HashSet<String> allowedValues = new HashSet<String>(3);
    allowedValues.add(toLowerCase(SSLClientAuthPolicy.DISABLED.toString()));
    allowedValues.add(toLowerCase(SSLClientAuthPolicy.OPTIONAL.toString()));
    allowedValues.add(toLowerCase(SSLClientAuthPolicy.REQUIRED.toString()));
    MultiChoiceConfigAttribute sslAuthPolicyStub =
         new MultiChoiceConfigAttribute(ATTR_SSL_CLIENT_AUTH_POLICY,
                                        getMessage(msgID), false, false, true,
                                        allowedValues);
    try
    {
      MultiChoiceConfigAttribute sslAuthPolicyAttr =
           (MultiChoiceConfigAttribute)
           configEntry.getConfigAttribute(sslAuthPolicyStub);
      if (sslAuthPolicyAttr == null)
      {
        // This is fine -- We'll just use the default.
        sslClientAuthPolicy = DEFAULT_SSL_CLIENT_AUTH_POLICY;
      }
      else
      {
        sslClientAuthPolicy = SSLClientAuthPolicy.policyForName(
                                   sslAuthPolicyAttr.activeValue());
        if (sslClientAuthPolicy == null)
        {
          msgID = MSGID_LDAP_CONNHANDLER_INVALID_SSL_CLIENT_AUTH_POLICY;
          String message = getMessage(msgID, sslAuthPolicyAttr.activeValue(),
                                      String.valueOf(configEntryDN));
          throw new ConfigException(msgID, message);
        }
      }
    }
    catch (ConfigException ce)
    {
      assert debugException(CLASS_NAME, "initializeConnectionHandler", ce);

      throw ce;
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializeConnectionHandler", e);

      msgID = MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_SSL_CLIENT_AUTH_POLICY;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Determine which SSL certificate to use.
    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_SSL_CERT_NICKNAME;
    StringConfigAttribute certNameStub =
         new StringConfigAttribute(ATTR_SSL_CERT_NICKNAME, getMessage(msgID),
                                   false, false, true);
    try
    {
      StringConfigAttribute certNameAttr =
           (StringConfigAttribute)
           configEntry.getConfigAttribute(certNameStub);
      if (certNameAttr == null)
      {
        // This is fine -- we'll just use the default.
        sslServerCertNickname = DEFAULT_SSL_CERT_NICKNAME;
      }
      else
      {
        sslServerCertNickname = certNameAttr.activeValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializeConnectionHandler", e);

      msgID = MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_SSL_CERT_NICKNAME;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Determine the set of SSL protocols to allow.
    enabledSSLProtocols = null;
    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_SSL_ENABLED_PROTOCOLS;
    StringConfigAttribute sslProtocolsStub =
         new StringConfigAttribute(ATTR_SSL_PROTOCOLS, getMessage(msgID), false,
                                   true, false);
    try
    {
      StringConfigAttribute sslProtocolsAttr =
           (StringConfigAttribute)
           configEntry.getConfigAttribute(sslProtocolsStub);
      if (sslProtocolsAttr != null)
      {
        enabledSSLProtocols = listToArray(sslProtocolsAttr.activeValues());
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializeConnectionHandler", e);

      msgID = MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_SSL_PROTOCOLS;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Determine the set of SSL cipher suites to allow.
    enabledSSLCipherSuites = null;
    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_SSL_ENABLED_CIPHERS;
    StringConfigAttribute sslCiphersStub =
         new StringConfigAttribute(ATTR_SSL_CIPHERS, getMessage(msgID), false,
                                   true, false);
    try
    {
      StringConfigAttribute sslCiphersAttr =
           (StringConfigAttribute)
           configEntry.getConfigAttribute(sslCiphersStub);
      if (sslCiphersAttr != null)
      {
        enabledSSLCipherSuites = listToArray(sslCiphersAttr.activeValues());
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializeConnectionHandler", e);

      msgID = MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_SSL_CIPHERS;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    if (useSSL)
    {
      TLSConnectionSecurityProvider tlsProvider =
           new TLSConnectionSecurityProvider();
      tlsProvider.initializeConnectionSecurityProvider(null);
      tlsProvider.setSSLClientAuthPolicy(sslClientAuthPolicy);
      tlsProvider.setEnabledProtocols(enabledSSLProtocols);
      tlsProvider.setEnabledCipherSuites(enabledSSLCipherSuites);

      // FIXME -- Need to do something with the requested cert nickname.

      securityProvider = tlsProvider;
    }
    else
    {
      securityProvider = new NullConnectionSecurityProvider();
      securityProvider.initializeConnectionSecurityProvider(null);
    }


    DirectoryServer.registerConfigurableComponent(this);


    try
    {
      selector = Selector.open();
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializeConnectionHandler", e);

      msgID = MSGID_LDAP_CONNHANDLER_OPEN_SELECTOR_FAILED;
      String message = getMessage(msgID, configEntryDN,
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Construct a unique name for this connection handler.
    StringBuilder nameBuffer = new StringBuilder();
    nameBuffer.append("LDAP Connection Handler");
    for (InetAddress a : listenAddresses)
    {
      nameBuffer.append(" ");
      nameBuffer.append(a.getHostAddress());
    }
    nameBuffer.append(" port ");
    nameBuffer.append(listenPort);
    handlerName = nameBuffer.toString();


    // Set the security mechanism for this connection handler.
    if (useSSL)
    {
      securityMechanism = SECURITY_MECHANISM_SSL;
    }
    else
    {
      securityMechanism = null;
    }


    // Perform any additional initialization that might be required.
    connHandlerThread = null;
    statTracker = new LDAPStatistics(handlerName);


    // Create and start the request handlers.
    requestHandlers = new LDAPRequestHandler[numRequestHandlers];
    for (int i=0; i < numRequestHandlers; i++)
    {
      requestHandlers[i] = new LDAPRequestHandler(this, i);
    }
    for (int i=0; i < numRequestHandlers; i++)
    {
      requestHandlers[i].start();
    }
  }



  /**
   * Closes this connection handler so that it will no longer accept new client
   * connections.  It may or may not disconnect existing client connections
   * based on the provided flag.  Note, however, that some connection handler
   * implementations may not have any way to continue processing requests from
   * existing connections, in which case they should always be closed regardless
   * of the value of the <CODE>closeConnections</CODE> flag.
   *
   * @param  finalizeReason    The reason that this connection handler should be
   *                           finalized.
   * @param  closeConnections  Indicates whether any established client
   *                           connections associated with the connection
   *                           handler should also be closed.
   */
  public void finalizeConnectionHandler(String finalizeReason,
                                       boolean closeConnections)
  {
    assert debugEnter(CLASS_NAME, "finalizeConnectionHandler");

    shutdownRequested = true;

    DirectoryServer.deregisterConfigurableComponent(this);

    try
    {
      selector.wakeup();
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "finalizeConnectionHandler", e);
    }


    if (closeConnections)
    {
      for (LDAPRequestHandler requestHandler : requestHandlers)
      {
        requestHandler.processServerShutdown(finalizeReason);
      }
    }
    else
    {
      for (LDAPRequestHandler requestHandler : requestHandlers)
      {
        requestHandler.registerShutdownListener();
      }
    }
  }



  /**
   * Retrieves the set of active client connections that have been established
   * through this connection handler.
   *
   * @return  The set of active client connections that have been established
   *          through this connection handler.
   */
  public Collection<ClientConnection> getClientConnections()
  {
    assert debugEnter(CLASS_NAME, "getClientConnections");

    LinkedList<ClientConnection> connectionList =
         new LinkedList<ClientConnection>();
    for (LDAPRequestHandler requestHandler : requestHandlers)
    {
      connectionList.addAll(requestHandler.getClientConnections());
    }

    return connectionList;
  }



  /**
   * Operates in a loop, accepting new connections and ensuring that requests on
   * those connections are handled properly.
   */
  public void run()
  {
    assert debugEnter(CLASS_NAME, "run");

    connHandlerThread = Thread.currentThread();
    setName(handlerName);

    boolean listening = false;

    while (! shutdownRequested)
    {
      // If this connection handler is not enabled, then just sleep for a bit
      // and check again.
      if (! enabled)
      {
        if (listening)
        {
          cleanUpSelector();
          listening = false;
          enabled   = false;

          logError(ErrorLogCategory.CONNECTION_HANDLING,
                   ErrorLogSeverity.NOTICE,
                   MSGID_LDAP_CONNHANDLER_STOPPED_LISTENING, handlerName);
        }

        try
        {
          Thread.sleep(1000);
        } catch (Exception e) {}

        continue;
      }


      // If we have gotten here, then we are about to start listening for the
      // first time since startup or since we were previously disabled.  Make
      // sure to start with a clean selector and then create all the listeners.
      try
      {
        cleanUpSelector();

        int numRegistered = 0;
        for (InetAddress a : listenAddresses)
        {
          try
          {
            ServerSocketChannel channel = ServerSocketChannel.open();
            channel.socket().setReuseAddress(allowReuseAddress);
            channel.socket().bind(new InetSocketAddress(a, listenPort),
                                  backlog);
            channel.configureBlocking(false);
            channel.register(selector, SelectionKey.OP_ACCEPT);
            numRegistered++;

            logError(ErrorLogCategory.CONNECTION_HANDLING,
                     ErrorLogSeverity.NOTICE,
                     MSGID_LDAP_CONNHANDLER_STARTED_LISTENING, handlerName);
          }
          catch (Exception e)
          {
            assert debugException(CLASS_NAME, "run", e);

            logError(ErrorLogCategory.CONNECTION_HANDLING,
                     ErrorLogSeverity.SEVERE_ERROR,
                     MSGID_LDAP_CONNHANDLER_CREATE_CHANNEL_FAILED,
                     configEntryDN, a.getHostAddress(), listenPort,
                     stackTraceToSingleLineString(e));
          }
        }


        // If none of the listeners were created successfully, then consider the
        // connection handler disabled and require administrative action before
        // trying again.
        if (numRegistered == 0)
        {
          logError(ErrorLogCategory.CONNECTION_HANDLING,
                   ErrorLogSeverity.FATAL_ERROR,
                   MSGID_LDAP_CONNHANDLER_NO_ACCEPTORS, configEntryDN);

          enabled = false;
          continue;
        }

        listening = true;


        // Enter a loop, waiting for new connections to arrive and then
        // accepting them as they come in.
        boolean lastIterationFailed = false;
        while (enabled && (! shutdownRequested))
        {
          try
          {
            if (selector.select() > 0)
            {
              Iterator<SelectionKey> iterator =
                   selector.selectedKeys().iterator();

              while (iterator.hasNext())
              {
                SelectionKey key = iterator.next();
                if (key.isAcceptable())
                {
                  // Accept the new client connection.
                  ServerSocketChannel serverChannel =
                       (ServerSocketChannel) key.channel();
                  SocketChannel clientChannel = serverChannel.accept();
                  clientChannel.socket().setKeepAlive(useKeepAlive);
                  clientChannel.socket().setTcpNoDelay(useTCPNoDelay);

                  LDAPClientConnection clientConnection =
                       new LDAPClientConnection(this, clientChannel);

                  ConnectionSecurityProvider connectionSecurityProvider =
                       securityProvider.newInstance(clientConnection,
                                                    clientChannel);
                  clientConnection.setConnectionSecurityProvider(
                       connectionSecurityProvider);


                  // Check to see if the core server rejected the connection
                  // (e.g., already too many connections established).
                  if (clientConnection.getConnectionID() < 0)
                  {
                    // The connection will have already been closed.
                    key.cancel();
                    iterator.remove();
                    continue;
                  }


                  // Check to see if the client is on the denied list.  If so,
                  // then reject it immediately.
                  if ((deniedClients != null) &&
                      AddressMask.maskListContains(clientConnection,
                                                   deniedClients))
                  {
                    clientConnection.disconnect(
                         DisconnectReason.CONNECTION_REJECTED,
                         sendRejectionNotice,
                         MSGID_LDAP_CONNHANDLER_DENIED_CLIENT,
                         clientConnection.getClientHostPort(),
                         clientConnection.getServerHostPort());

                    key.cancel();
                    iterator.remove();
                    continue;
                  }


                  // Check to see if there is an allowed list and if there is
                  // whether the client is on that list.  If not, then reject
                  // the connection.
                  if ((allowedClients != null) && (allowedClients.length > 0) &&
                      (! AddressMask.maskListContains(clientConnection,
                                                      allowedClients)))
                  {
                    clientConnection.disconnect(
                         DisconnectReason.CONNECTION_REJECTED,
                         sendRejectionNotice,
                         MSGID_LDAP_CONNHANDLER_DISALLOWED_CLIENT,
                         clientConnection.getClientHostPort(),
                         clientConnection.getServerHostPort());

                    key.cancel();
                    iterator.remove();
                    continue;
                  }


                  // If we've gotten here, then we'll take the connection so
                  // choose a request handler and register the client with it.
                  try
                  {
                    LDAPRequestHandler requestHandler =
                         requestHandlers[requestHandlerIndex++];
                    if (requestHandlerIndex >= numRequestHandlers)
                    {
                      requestHandlerIndex = 0;
                    }

                    if (requestHandler.registerClient(clientConnection))
                    {
                      logConnect(clientConnection);
                    }
                    else
                    {
                      key.cancel();
                      iterator.remove();
                      continue;
                    }
                  }
                  catch (Exception e)
                  {
                    assert debugException(CLASS_NAME, "run", e);

                    int msgID =
                             MSGID_LDAP_CONNHANDLER_UNABLE_TO_REGISTER_CLIENT;
                    String message =
                                getMessage(msgID,
                                           clientConnection.getClientHostPort(),
                                           clientConnection.getServerHostPort(),
                                           stackTraceToSingleLineString(e));

                    logError(ErrorLogCategory.CONNECTION_HANDLING,
                             ErrorLogSeverity.SEVERE_ERROR, message, msgID);

                    clientConnection.disconnect(DisconnectReason.SERVER_ERROR,
                                                sendRejectionNotice, message,
                                                msgID);

                    key.cancel();
                    iterator.remove();
                    continue;
                  }
                }

                iterator.remove();
              }
            }
            else
            {
              if (shutdownRequested)
              {
                cleanUpSelector();
                listening = false;
                enabled   = false;
                continue;
              }
            }

            lastIterationFailed = false;
          }
          catch (Exception e)
          {
            assert debugException(CLASS_NAME, "run", e);

            logError(ErrorLogCategory.CONNECTION_HANDLING,
                     ErrorLogSeverity.SEVERE_WARNING,
                     MSGID_LDAP_CONNHANDLER_CANNOT_ACCEPT_CONNECTION,
                     configEntryDN, stackTraceToSingleLineString(e));

            if (lastIterationFailed)
            {
              // The last time through the accept loop we also encountered a
              // failure.  Rather than enter a potential infinite loop of
              // failures, disable this acceptor and log an error.
              int msgID = MSGID_LDAP_CONNHANDLER_CONSECUTIVE_ACCEPT_FAILURES;
              String message = getMessage(msgID, String.valueOf(configEntryDN),
                                          stackTraceToSingleLineString(e));

              logError(ErrorLogCategory.CONNECTION_HANDLING,
                       ErrorLogSeverity.FATAL_ERROR, message, msgID);

              DirectoryServer.sendAlertNotification(this,
                   ALERT_TYPE_LDAP_CONNECTION_HANDLER_CONSECUTIVE_FAILURES,
                   msgID, message);

              enabled = false;

              try
              {
                cleanUpSelector();
              } catch (Exception e2) {}
            }
            else
            {
              lastIterationFailed = true;
            }
          }
        }
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "run", e);

        // This is very bad because we failed outside the loop.  The only
        // thing we can do here is log a message, send an alert, and disable the
        // selector until an administrator can figure out what's going on.
        int msgID = MSGID_LDAP_CONNHANDLER_UNCAUGHT_ERROR;
        String message = getMessage(msgID, String.valueOf(configEntryDN),
                                    stackTraceToSingleLineString(e));

        logError(ErrorLogCategory.CONNECTION_HANDLING,
                 ErrorLogSeverity.SEVERE_ERROR, message, msgID);

        DirectoryServer.sendAlertNotification(this,
             ALERT_TYPE_LDAP_CONNECTION_HANDLER_UNCAUGHT_ERROR, msgID, message);

        try
        {
          cleanUpSelector();
        } catch (Exception e2) {}

        enabled = false;
      }
    }
  }



  /**
   * Cleans up the contents of the selector, closing any server socket channels
   * that might be associated with it.  Any connections that might have been
   * established through those channels should not be impacted.
   */
  private void cleanUpSelector()
  {
    assert debugEnter(CLASS_NAME, "cleanUpSelector");

    try
    {
      Iterator<SelectionKey> iterator = selector.keys().iterator();
      while (iterator.hasNext())
      {
        SelectionKey key = iterator.next();

        try
        {
          key.cancel();
        }
        catch (Exception e)
        {
          assert debugException(CLASS_NAME, "cleanUpSelector", e);
        }

        try
        {
          key.channel().close();
        }
        catch (Exception e)
        {
          assert debugException(CLASS_NAME, "cleanUpSelector", e);
        }
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "cleanUpSelector", e);
    }
  }



  /**
   * Indicates whether this connection handler should maintain usage statistics.
   *
   * @return  <CODE>true</CODE> if this connection handler should maintain usage
   *          statistics, or <CODE>false</CODE> if not.
   */
  public boolean keepStats()
  {
    assert debugEnter(CLASS_NAME, "keepStats");

    return keepStats;
  }



  /**
   * Specifies whether this connection handler should maintain usage statistics.
   *
   * @param  keepStats  Specifies whether this connection handler should
   *                    maintain usage statistics.
   */
  public void setKeepStats(boolean keepStats)
  {
    assert debugEnter(CLASS_NAME, "setKeepStats", String.valueOf(keepStats));

    this.keepStats = keepStats;
  }



  /**
   * Retrieves the set of statistics maintained by this connection handler.
   *
   * @return  The set of statistics maintained by this connection handler.
   */
  public LDAPStatistics getStatTracker()
  {
    assert debugEnter(CLASS_NAME, "getStatTracker");

    return statTracker;
  }



  /**
   * Indicates whether this connection handler should allow interaction with
   * LDAPv2 clients.
   *
   * @return  <CODE>true</CODE> if LDAPv2 is allowed, or <CODE>false</CODE> if
   *          not.
   */
  public boolean allowLDAPv2()
  {
    assert debugEnter(CLASS_NAME, "allowLDAPv2");

    return allowLDAPv2;
  }



  /**
   * Indicates whether this connection handler should allow the use of the
   * StartTLS extended operation.
   *
   * @return  <CODE>true</CODE> if StartTLS is allowed, or <CODE>false</CODE> if
   *          not.
   */
  public boolean allowStartTLS()
  {
    assert debugEnter(CLASS_NAME, "allowStartTLS");

    return allowStartTLS;
  }



  /**
   * Retrieves the SSL client authentication policy for this connection handler.
   *
   * @return  The SSL client authentication policy for this connection handler.
   */
  public SSLClientAuthPolicy getSSLClientAuthPolicy()
  {
    assert debugEnter(CLASS_NAME, "getSSLClientAuthPolicy");

    return sslClientAuthPolicy;
  }



  /**
   * Retrieves the set of enabled SSL protocols configured for this connection
   * handler.
   *
   * @return  The set of enabled SSL protocols configured for this connection
   *          handler.
   */
  public String[] getEnabledSSLProtocols()
  {
    assert debugEnter(CLASS_NAME, "getEnabledSSLProtocols");

    return enabledSSLProtocols;
  }



  /**
   * Retrieves the set of enabled SSL cipher suites configured for this
   * connection handler.
   *
   * @return  The set of enabled SSL cipher suites configured for this
   *          connection handler.
   */
  public String[] getEnabledSSLCipherSuites()
  {
    assert debugEnter(CLASS_NAME, "getEnabledSSLCipherSuites");

    return enabledSSLCipherSuites;
  }



  /**
   * Retrieves the maximum ASN.1 element value length that will be allowed by
   * this connection handler.
   *
   * @return  The maximum ASN.1 element value length that will be allowed by
   *          this connection handler.
   */
  public int getMaxRequestSize()
  {
    assert debugEnter(CLASS_NAME, "getMaxRequestSize");

    return maxRequestSize;
  }



  /**
   * Retrieves the human-readable name for this shutdown listener.
   *
   * @return  The human-readable name for this shutdown listener.
   */
  public String getShutdownListenerName()
  {
    assert debugEnter(CLASS_NAME, "getShutdownListenerName");

    return handlerName;
  }



  /**
   * Indicates that the Directory Server has received a request to stop running
   * and that this shutdown listener should take any action necessary to prepare
   * for it.
   *
   * @param  reason  The human-readable reason for the shutdown.
   */
  public void processServerShutdown(String reason)
  {
    assert debugEnter(CLASS_NAME, "processServerShutdown");

    shutdownRequested = true;

    try
    {
      for (LDAPRequestHandler requestHandler : requestHandlers)
      {
        try
        {
          requestHandler.processServerShutdown(reason);
        } catch (Exception e) {}
      }
    } catch (Exception e) {}
  }



  /**
   * Retrieves the DN of the configuration entry with which this component is
   * associated.
   *
   * @return  The DN of the configuration entry with which this component is
   *          associated.
   */
  public DN getConfigurableComponentEntryDN()
  {
    assert debugEnter(CLASS_NAME, "getConfigurableComponentEntryDN");

    return configEntryDN;
  }



  /**
   * Retrieves the set of configuration attributes that are associated with this
   * configurable component.
   *
   * @return  The set of configuration attributes that are associated with this
   *          configurable component.
   */
  public List<ConfigAttribute> getConfigurationAttributes()
  {
    assert debugEnter(CLASS_NAME, "getConfigurationAttributes");

    LinkedList<ConfigAttribute> configAttrs = new LinkedList<ConfigAttribute>();

    int msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_LISTEN_ADDRESS;
    ArrayList<String> listenAddressStrings =
         new ArrayList<String>(listenAddresses.size());
    for (InetAddress a : listenAddresses)
    {
      listenAddressStrings.add(a.getHostAddress());
    }
    configAttrs.add(new StringConfigAttribute(ATTR_LISTEN_ADDRESS,
                                              getMessage(msgID), true, true,
                                              true, listenAddressStrings));


    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_LISTEN_PORT;
    configAttrs.add(new IntegerConfigAttribute(ATTR_LISTEN_PORT,
                                               getMessage(msgID), true, false,
                                               true, true, 1, true, 65535,
                                               listenPort));


    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_BACKLOG;
    configAttrs.add(new IntegerConfigAttribute(ATTR_ACCEPT_BACKLOG,
                                               getMessage(msgID), true, false,
                                               true, true, 1, true,
                                               Integer.MAX_VALUE, backlog));


    if (allowedClients == null)
    {
      msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_ALLOWED_CLIENTS;
      ArrayList<String> allowedMasks = new ArrayList<String>(0);
      configAttrs.add(new StringConfigAttribute(ATTR_ALLOWED_CLIENT,
                                                getMessage(msgID), false, true,
                                                false, allowedMasks));
    }
    else
    {
      msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_ALLOWED_CLIENTS;
      ArrayList<String> allowedMasks =
           new ArrayList<String>(allowedClients.length);
      for (AddressMask m : allowedClients)
      {
        allowedMasks.add(m.toString());
      }
      configAttrs.add(new StringConfigAttribute(ATTR_ALLOWED_CLIENT,
                                                getMessage(msgID), false, true,
                                                false, allowedMasks));
    }


    if (deniedClients == null)
    {
      msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_DENIED_CLIENTS;
      ArrayList<String> deniedMasks = new ArrayList<String>(0);
      configAttrs.add(new StringConfigAttribute(ATTR_DENIED_CLIENT,
                                                getMessage(msgID), false, true,
                                                false, deniedMasks));
    }
    else
    {
      msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_DENIED_CLIENTS;
      ArrayList<String> deniedMasks =
           new ArrayList<String>(deniedClients.length);
      for (AddressMask m : deniedClients)
      {
        deniedMasks.add(m.toString());
      }
      configAttrs.add(new StringConfigAttribute(ATTR_DENIED_CLIENT,
                                                getMessage(msgID), false, true,
                                                false, deniedMasks));
    }


    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_ALLOW_LDAPV2;
    configAttrs.add(new BooleanConfigAttribute(ATTR_ALLOW_LDAPV2,
                                               getMessage(msgID), false,
                                               allowLDAPv2));


    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_KEEP_STATS;
    configAttrs.add(new BooleanConfigAttribute(ATTR_KEEP_LDAP_STATS,
                                               getMessage(msgID), false,
                                               keepStats));


    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_NUM_REQUEST_HANDLERS;
    configAttrs.add(new IntegerConfigAttribute(ATTR_NUM_REQUEST_HANDLERS,
                                               getMessage(msgID), true, false,
                                               true, true, 1, false, 0,
                                               numRequestHandlers));


    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_SEND_REJECTION_NOTICE;
    configAttrs.add(new BooleanConfigAttribute(ATTR_SEND_REJECTION_NOTICE,
                                               getMessage(msgID), false,
                                               sendRejectionNotice));


    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_USE_TCP_KEEPALIVE;
    configAttrs.add(new BooleanConfigAttribute(ATTR_USE_TCP_KEEPALIVE,
                                               getMessage(msgID), false,
                                               useKeepAlive));


    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_USE_TCP_NODELAY;
    configAttrs.add(new BooleanConfigAttribute(ATTR_USE_TCP_NODELAY,
                                               getMessage(msgID), false,
                                               useTCPNoDelay));


    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_ALLOW_REUSE_ADDRESS;
    configAttrs.add(new BooleanConfigAttribute(ATTR_ALLOW_REUSE_ADDRESS,
                                               getMessage(msgID), true,
                                               allowReuseAddress));


    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_MAX_REQUEST_SIZE;
    configAttrs.add(new IntegerWithUnitConfigAttribute(ATTR_MAX_REQUEST_SIZE,
                                                       getMessage(msgID), false,
                                                       SIZE_UNITS, true, 0,
                                                       true,
                                                       MAX_REQUEST_SIZE_LIMIT,
                                                       maxRequestSize,
                                                       UNIT_BYTES));


    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_USE_SSL;
    configAttrs.add(new BooleanConfigAttribute(ATTR_USE_SSL, getMessage(msgID),
                                               true, useSSL));


    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_ALLOW_STARTTLS;
    configAttrs.add(new BooleanConfigAttribute(ATTR_ALLOW_STARTTLS,
                                               getMessage(msgID), false,
                                               allowStartTLS));


    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_SSL_CLIENT_AUTH_POLICY;
    HashSet<String> allowedValues = new HashSet<String>(3);
    allowedValues.add(toLowerCase(SSLClientAuthPolicy.DISABLED.toString()));
    allowedValues.add(toLowerCase(SSLClientAuthPolicy.OPTIONAL.toString()));
    allowedValues.add(toLowerCase(SSLClientAuthPolicy.REQUIRED.toString()));
    configAttrs.add(new MultiChoiceConfigAttribute(ATTR_SSL_CLIENT_AUTH_POLICY,
                             getMessage(msgID), false, false, true,
                             allowedValues, sslClientAuthPolicy.toString()));


    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_SSL_CERT_NICKNAME;
    configAttrs.add(new StringConfigAttribute(ATTR_SSL_CERT_NICKNAME,
                                              getMessage(msgID), false, false,
                                              true, sslServerCertNickname));

    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_SSL_ENABLED_PROTOCOLS;
    configAttrs.add(new StringConfigAttribute(ATTR_SSL_PROTOCOLS,
                             getMessage(msgID), false, true, false,
                             arrayToList(enabledSSLProtocols)));

    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_SSL_ENABLED_CIPHERS;
    configAttrs.add(new StringConfigAttribute(ATTR_SSL_CIPHERS,
                             getMessage(msgID), false, true, false,
                             arrayToList(enabledSSLCipherSuites)));


    return configAttrs;
  }



  /**
   * Indicates whether the provided configuration entry has an acceptable
   * configuration for this component.  If it does not, then detailed
   * information about the problem(s) should be added to the provided list.
   *
   * @param  configEntry          The configuration entry for which to make the
   *                              determination.
   * @param  unacceptableReasons  A list that can be used to hold messages about
   *                              why the provided entry does not have an
   *                              acceptable configuration.
   *
   * @return  <CODE>true</CODE> if the provided entry has an acceptable
   *          configuration for this component, or <CODE>false</CODE> if not.
   */
  public boolean hasAcceptableConfiguration(ConfigEntry configEntry,
                                            List<String> unacceptableReasons)
  {
    assert debugEnter(CLASS_NAME, "hasAcceptableConfiguration",
                      String.valueOf(configEntry), "java.util.List<String>");


    boolean configValid = true;


    // Determine the set of addresses on which to listen.  There can be
    // multiple addresses specified.
    int msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_LISTEN_ADDRESS;
    StringConfigAttribute addrStub =
         new StringConfigAttribute(ATTR_LISTEN_ADDRESS, getMessage(msgID),
                                   true, true, false);
    try
    {
      StringConfigAttribute addrAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(addrStub);
      if ((addrAttr == null) || addrAttr.activeValues().isEmpty())
      {
        // This is fine -- we'll just listen on all IPv4 addresses.
      }
      else
      {
        for (String s : addrAttr.activeValues())
        {
          try
          {
            InetAddress.getByName(s);
          }
          catch (UnknownHostException uhe)
          {
            assert debugException(CLASS_NAME, "hasAcceptableConfiguration",
                                  uhe);

            msgID = MSGID_LDAP_CONNHANDLER_UNKNOWN_LISTEN_ADDRESS;
            unacceptableReasons.add(getMessage(msgID, s,
                                         stackTraceToSingleLineString(uhe)));
            configValid = false;
          }
        }
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", e);

      msgID = MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_LISTEN_ADDRESS;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(configEntryDN),
                                         stackTraceToSingleLineString(e)));
      configValid = false;
    }


    // Determine the port on which to listen.  There may only be a single port
    // specified.
    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_LISTEN_PORT;
    IntegerConfigAttribute portStub =
         new IntegerConfigAttribute(ATTR_LISTEN_PORT, getMessage(msgID), true,
                                    false, false, true, 1, true, 65535);
    try
    {
      IntegerConfigAttribute portAttr =
           (IntegerConfigAttribute) configEntry.getConfigAttribute(portStub);
      if (portAttr == null)
      {
        msgID = MSGID_LDAP_CONNHANDLER_NO_LISTEN_PORT;
        unacceptableReasons.add(getMessage(msgID,
                                           String.valueOf(configEntryDN)));
        configValid = false;
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", e);

      msgID = MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_LISTEN_PORT;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(configEntryDN),
                                         stackTraceToSingleLineString(e)));
      configValid = false;
    }


    // Determine the accept backlog to use.
    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_BACKLOG;
    IntegerConfigAttribute backlogStub =
         new IntegerConfigAttribute(ATTR_ACCEPT_BACKLOG, getMessage(msgID),
                                    true, false, true, true, 1, true,
                                    Integer.MAX_VALUE);
    try
    {
      IntegerConfigAttribute backlogAttr =
           (IntegerConfigAttribute) configEntry.getConfigAttribute(backlogStub);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", e);

      msgID = MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_BACKLOG;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(configEntryDN),
                                         stackTraceToSingleLineString(e)));
      configValid = false;
    }


    // Determine the set of allowed clients.
    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_ALLOWED_CLIENTS;
    StringConfigAttribute allowedStub =
         new StringConfigAttribute(ATTR_ALLOWED_CLIENT, getMessage(msgID),
                                   false, true, false);
    try
    {
      StringConfigAttribute allowedAttr =
           (StringConfigAttribute)
           configEntry.getConfigAttribute(allowedStub);
      if (allowedAttr != null)
      {
        for (String s : allowedAttr.activeValues())
        {
          try
          {
            AddressMask.decode(s);
          }
          catch (ConfigException ce)
          {
            assert debugException(CLASS_NAME, "hasAcceptableConfiguration", ce);

            msgID = MSGID_LDAP_CONNHANDLER_INVALID_ADDRESS_MASK;
            unacceptableReasons.add(getMessage(msgID, s, ATTR_ALLOWED_CLIENT,
                                         String.valueOf(configEntryDN),
                                         stackTraceToSingleLineString(ce)));
            configValid = false;
          }
        }
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", e);

      msgID = MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_ALLOWED_CLIENTS;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(configEntryDN),
                                         stackTraceToSingleLineString(e)));
      configValid = false;
    }


    // Determine the set of denied clients.
    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_DENIED_CLIENTS;
    StringConfigAttribute deniedStub =
         new StringConfigAttribute(ATTR_DENIED_CLIENT, getMessage(msgID),
                                   false, true, false);
    try
    {
      StringConfigAttribute deniedAttr =
           (StringConfigAttribute)
           configEntry.getConfigAttribute(deniedStub);
      if (deniedAttr != null)
      {
        for (String s : deniedAttr.activeValues())
        {
          try
          {
            AddressMask.decode(s);
          }
          catch (ConfigException ce)
          {
            assert debugException(CLASS_NAME, "hasAcceptableConfiguration", ce);

            msgID = MSGID_LDAP_CONNHANDLER_INVALID_ADDRESS_MASK;
            unacceptableReasons.add(getMessage(msgID, s, ATTR_DENIED_CLIENT,
                                         String.valueOf(configEntryDN),
                                         stackTraceToSingleLineString(ce)));
            configValid = false;
          }
        }
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", e);

      msgID = MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_DENIED_CLIENTS;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(configEntryDN),
                                         stackTraceToSingleLineString(e)));
      configValid = false;
    }


    // Determine whether to allow LDAPv2 clients.
    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_ALLOW_LDAPV2;
    BooleanConfigAttribute allowLDAPv2Stub =
         new BooleanConfigAttribute(ATTR_ALLOW_LDAPV2, getMessage(msgID),
                                    false);
    try
    {
      BooleanConfigAttribute allowLDAPv2Attr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(allowLDAPv2Stub);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", e);

      msgID = MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_ALLOW_LDAPV2;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(configEntryDN),
                                         stackTraceToSingleLineString(e)));
      configValid = false;
    }


    // Determine whether to keep LDAP statistics.
    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_KEEP_STATS;
    BooleanConfigAttribute keepStatsStub =
         new BooleanConfigAttribute(ATTR_KEEP_LDAP_STATS, getMessage(msgID),
                                    false);
    try
    {
      BooleanConfigAttribute keepStatsAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(keepStatsStub);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", e);

      msgID = MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_KEEP_STATS;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(configEntryDN),
                                         stackTraceToSingleLineString(e)));
      configValid = false;
    }


    // Determine the number of request handlers to maintain.
    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_NUM_REQUEST_HANDLERS;
    IntegerConfigAttribute reqHandlerStub =
         new IntegerConfigAttribute(ATTR_NUM_REQUEST_HANDLERS,
                                    getMessage(msgID), true, false, true,
                                    true, 1, false, 0);
    try
    {
      IntegerConfigAttribute reqHandlerAttr =
           (IntegerConfigAttribute)
           configEntry.getConfigAttribute(reqHandlerStub);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", e);

      msgID = MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_NUM_REQUEST_HANDLERS;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(configEntryDN),
                                         stackTraceToSingleLineString(e)));
      configValid = false;
    }


    // Determine whether to send a notice to clients on rejection.
    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_SEND_REJECTION_NOTICE;
    BooleanConfigAttribute notifyRejectsStub =
         new BooleanConfigAttribute(ATTR_SEND_REJECTION_NOTICE,
                                    getMessage(msgID), false);
    try
    {
      BooleanConfigAttribute notifyRejectsAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(notifyRejectsStub);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", e);

      msgID = MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_SEND_REJECTION_NOTICE;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(configEntryDN),
                                         stackTraceToSingleLineString(e)));
      configValid = false;
    }


    // Determine whether to use TCP keepalive.
    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_USE_TCP_KEEPALIVE;
    BooleanConfigAttribute keepAliveStub =
         new BooleanConfigAttribute(ATTR_USE_TCP_KEEPALIVE, getMessage(msgID),
                                    false);
    try
    {
      BooleanConfigAttribute keepAliveAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(keepAliveStub);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", e);

      msgID = MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_USE_TCP_KEEPALIVE;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(configEntryDN),
                                         stackTraceToSingleLineString(e)));
      configValid = false;
    }


    // Determine whether to use TCP nodelay.
    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_USE_TCP_NODELAY;
    BooleanConfigAttribute noDelayStub =
         new BooleanConfigAttribute(ATTR_USE_TCP_NODELAY, getMessage(msgID),
                                    false);
    try
    {
      BooleanConfigAttribute noDelayAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(noDelayStub);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", e);

      msgID = MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_USE_TCP_NODELAY;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(configEntryDN),
                                         stackTraceToSingleLineString(e)));
      configValid = false;
    }


    // Determine whether to allow reuse of address/port combinations.
    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_ALLOW_REUSE_ADDRESS;
    BooleanConfigAttribute reuseAddrStub =
         new BooleanConfigAttribute(ATTR_ALLOW_REUSE_ADDRESS,
                                    getMessage(msgID), true);
    try
    {
      BooleanConfigAttribute reuseAddrAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(reuseAddrStub);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", e);

      msgID = MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_ALLOW_REUSE_ADDRESS;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(configEntryDN),
                                         stackTraceToSingleLineString(e)));
      configValid = false;
    }


    // Determine the maximum allowed request size.
    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_MAX_REQUEST_SIZE;
    IntegerWithUnitConfigAttribute maxReqSizeStub =
         new IntegerWithUnitConfigAttribute(ATTR_MAX_REQUEST_SIZE,
                                            getMessage(msgID), false,
                                            SIZE_UNITS, true, 0, true,
                                            MAX_REQUEST_SIZE_LIMIT);
    try
    {
      IntegerWithUnitConfigAttribute maxReqSizeAttr =
           (IntegerWithUnitConfigAttribute)
           configEntry.getConfigAttribute(maxReqSizeStub);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", e);

      msgID = MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_MAX_REQUEST_SIZE;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(configEntryDN),
                                         stackTraceToSingleLineString(e)));
      configValid = false;
    }


    // Determine whether to use SSL.
    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_USE_SSL;
    boolean tmpUseSSL;
    BooleanConfigAttribute useSSLStub =
         new BooleanConfigAttribute(ATTR_USE_SSL, getMessage(msgID), true);
    try
    {
      BooleanConfigAttribute useSSLAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(useSSLStub);
      if (useSSLAttr == null)
      {
        tmpUseSSL = DEFAULT_USE_SSL;
      }
      else
      {
        tmpUseSSL = useSSLAttr.pendingValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", e);

      msgID = MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_USE_SSL;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(configEntryDN),
                                         stackTraceToSingleLineString(e)));
      configValid = false;
      tmpUseSSL   = DEFAULT_USE_SSL;
    }


    // Determine whether to allow the StartTLS extended operation.
    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_ALLOW_STARTTLS;
    BooleanConfigAttribute startTLSStub =
         new BooleanConfigAttribute(ATTR_ALLOW_STARTTLS, getMessage(msgID),
                                    false);
    try
    {
      BooleanConfigAttribute startTLSAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(startTLSStub);
      boolean tmpAllowStartTLS;
      if (startTLSAttr == null)
      {
        // This is fine -- we'll just use the default.
        tmpAllowStartTLS = DEFAULT_ALLOW_STARTTLS;
      }
      else
      {
        tmpAllowStartTLS = startTLSAttr.activeValue();
      }


      // See if both SSL and startTLS are configured.  If so, we'll have to
      // disable startTLS because they can't both be used concurrently.
      if (tmpUseSSL && tmpAllowStartTLS)
      {
        msgID = MSGID_LDAP_CONNHANDLER_CANNOT_HAVE_SSL_AND_STARTTLS;
        unacceptableReasons.add(getMessage(msgID,
                                           String.valueOf(configEntryDN)));
        configValid = false;
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", e);

      msgID = MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_ALLOW_STARTTLS;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(configEntryDN),
                                         stackTraceToSingleLineString(e)));
      configValid = false;
    }


    // Determine how to handle SSL client authentication.
    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_SSL_CLIENT_AUTH_POLICY;
    HashSet<String> allowedValues = new HashSet<String>(3);
    allowedValues.add(toLowerCase(SSLClientAuthPolicy.DISABLED.toString()));
    allowedValues.add(toLowerCase(SSLClientAuthPolicy.OPTIONAL.toString()));
    allowedValues.add(toLowerCase(SSLClientAuthPolicy.REQUIRED.toString()));
    MultiChoiceConfigAttribute sslAuthPolicyStub =
         new MultiChoiceConfigAttribute(ATTR_SSL_CLIENT_AUTH_POLICY,
                                        getMessage(msgID), false, false, true,
                                        allowedValues);
    try
    {
      MultiChoiceConfigAttribute sslAuthPolicyAttr =
           (MultiChoiceConfigAttribute)
           configEntry.getConfigAttribute(sslAuthPolicyStub);
      if (sslAuthPolicyAttr == null)
      {
        // This is fine -- We'll just use the default.
      }
      else
      {
        SSLClientAuthPolicy tmpPolicy = SSLClientAuthPolicy.policyForName(
                                             sslAuthPolicyAttr.activeValue());
        if (tmpPolicy == null)
        {
          msgID = MSGID_LDAP_CONNHANDLER_INVALID_SSL_CLIENT_AUTH_POLICY;
          unacceptableReasons.add(getMessage(msgID,
                                             sslAuthPolicyAttr.activeValue(),
                                             String.valueOf(configEntryDN)));
          configValid = false;
        }
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", e);

      msgID = MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_SSL_CLIENT_AUTH_POLICY;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(configEntryDN),
                                         stackTraceToSingleLineString(e)));
      configValid = false;
    }


    // Determine which SSL certificate to use.
    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_SSL_CERT_NICKNAME;
    StringConfigAttribute certNameStub =
         new StringConfigAttribute(ATTR_SSL_CERT_NICKNAME, getMessage(msgID),
                                   false, false, true);
    try
    {
      StringConfigAttribute certNameAttr =
           (StringConfigAttribute)
           configEntry.getConfigAttribute(certNameStub);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", e);

      msgID = MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_SSL_CERT_NICKNAME;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(configEntryDN),
                                         stackTraceToSingleLineString(e)));
      configValid = false;
    }


    // Determine the set of SSL protocols to allow.
    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_SSL_ENABLED_PROTOCOLS;
    StringConfigAttribute sslProtocolsStub =
         new StringConfigAttribute(ATTR_SSL_PROTOCOLS, getMessage(msgID),
                                   false, true, false);
    try
    {
      StringConfigAttribute sslProtocolsAttr =
           (StringConfigAttribute)
           configEntry.getConfigAttribute(sslProtocolsStub);
      // FIXME -- Is there a good way to determine the set of supported
      //          protocols to validate what the user has provided?
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", e);

      msgID = MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_SSL_PROTOCOLS;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(configEntryDN),
                                         stackTraceToSingleLineString(e)));
      configValid = false;
    }


    // Determine the set of SSL cipher suites to allow.
    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_SSL_ENABLED_CIPHERS;
    StringConfigAttribute sslCiphersStub =
         new StringConfigAttribute(ATTR_SSL_CIPHERS, getMessage(msgID),
                                   false, true, false);
    try
    {
      StringConfigAttribute sslCiphersAttr =
           (StringConfigAttribute)
           configEntry.getConfigAttribute(sslCiphersStub);
      // FIXME -- Is there a good way to determine the set of supported cipher
      //          suites to validate what the user has provided?
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", e);

      msgID = MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_SSL_CIPHERS;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(configEntryDN),
                                         stackTraceToSingleLineString(e)));
      configValid = false;
    }


    return configValid;
  }



  /**
   * Makes a best-effort attempt to apply the configuration contained in the
   * provided entry.  Information about the result of this processing should be
   * added to the provided message list.  Information should always be added to
   * this list if a configuration change could not be applied.  If detailed
   * results are requested, then information about the changes applied
   * successfully (and optionally about parameters that were not changed) should
   * also be included.
   *
   * @param  configEntry      The entry containing the new configuration to
   *                          apply for this component.
   * @param  detailedResults  Indicates whether detailed information about the
   *                          processing should be added to the list.
   *
   * @return  Information about the result of the configuration update.
   */
  public ConfigChangeResult applyNewConfiguration(ConfigEntry configEntry,
                                                  boolean detailedResults)
  {
    assert debugEnter(CLASS_NAME, "applyNewConfiguration",
                      String.valueOf(configEntry),
                      String.valueOf(detailedResults));


    // Create variables to include in the response.
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();


    // The set of addresses and the port on which to listen are not dynamically
    // reconfigurable, so we can skip them.


    // The backlog is not dynamically reconfigurable, so we can skip it.


    // Determine the set of allowed clients.
    HashSet<AddressMask> newAllowedClients = null;
    int msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_ALLOWED_CLIENTS;
    StringConfigAttribute allowedStub =
         new StringConfigAttribute(ATTR_ALLOWED_CLIENT, getMessage(msgID),
                                   false, true, false);
    try
    {
      StringConfigAttribute allowedAttr =
           (StringConfigAttribute)
           configEntry.getConfigAttribute(allowedStub);
      if (allowedAttr != null)
      {
        newAllowedClients = new HashSet<AddressMask>();

        for (String s : allowedAttr.pendingValues())
        {
          try
          {
            newAllowedClients.add(AddressMask.decode(s));
          }
          catch (ConfigException ce)
          {
            assert debugException(CLASS_NAME, "applyNewConfiguration", ce);

            msgID = MSGID_LDAP_CONNHANDLER_INVALID_ADDRESS_MASK;
            messages.add(getMessage(msgID, s, ATTR_ALLOWED_CLIENT,
                                    String.valueOf(configEntryDN),
                                    stackTraceToSingleLineString(ce)));
            if (resultCode == ResultCode.SUCCESS)
            {
              resultCode = ResultCode.INVALID_ATTRIBUTE_SYNTAX;
            }
          }
        }
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyNewConfiguration", e);

      msgID = MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_ALLOWED_CLIENTS;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              stackTraceToSingleLineString(e)));
      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = DirectoryServer.getServerErrorResultCode();
      }
    }

    boolean allowedClientsChanged = false;
    if (resultCode == ResultCode.SUCCESS)
    {
      if ((allowedClients == null) || (allowedClients.length == 0))
      {
        allowedClientsChanged = (! ((newAllowedClients == null) ||
                                    newAllowedClients.isEmpty()));
      }
      else if ((newAllowedClients == null) || newAllowedClients.isEmpty())
      {
        allowedClientsChanged = true;
      }
      else if (allowedClients.length != newAllowedClients.size())
      {
        allowedClientsChanged = true;
      }
      else
      {
        for (AddressMask m : allowedClients)
        {
          if (! newAllowedClients.contains(m))
          {
            allowedClientsChanged = true;
            break;
          }
        }
      }
    }


    // Determine the set of denied clients.
    HashSet<AddressMask> newDeniedClients = null;
    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_DENIED_CLIENTS;
    StringConfigAttribute deniedStub =
         new StringConfigAttribute(ATTR_DENIED_CLIENT, getMessage(msgID),
                                   false, true, false);
    try
    {
      StringConfigAttribute deniedAttr =
           (StringConfigAttribute)
           configEntry.getConfigAttribute(deniedStub);
      if (deniedAttr != null)
      {
        newDeniedClients = new HashSet<AddressMask>();

        for (String s : deniedAttr.pendingValues())
        {
          try
          {
            newDeniedClients.add(AddressMask.decode(s));
          }
          catch (ConfigException ce)
          {
            assert debugException(CLASS_NAME, "applyNewConfiguration", ce);

            msgID = MSGID_LDAP_CONNHANDLER_INVALID_ADDRESS_MASK;
            messages.add(getMessage(msgID, s, ATTR_DENIED_CLIENT,
                                    String.valueOf(configEntryDN),
                                    stackTraceToSingleLineString(ce)));
            if (resultCode == ResultCode.SUCCESS)
            {
              resultCode = ResultCode.INVALID_ATTRIBUTE_SYNTAX;
            }
          }
        }
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyNewConfiguration", e);

      msgID = MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_DENIED_CLIENTS;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              stackTraceToSingleLineString(e)));
      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = DirectoryServer.getServerErrorResultCode();
      }
    }

    boolean deniedClientsChanged = false;
    if (resultCode == ResultCode.SUCCESS)
    {
      if ((deniedClients == null) || (deniedClients.length == 0))
      {
        deniedClientsChanged = (! ((newDeniedClients == null) ||
                                    newDeniedClients.isEmpty()));
      }
      else if ((newDeniedClients == null) || newDeniedClients.isEmpty())
      {
        deniedClientsChanged = true;
      }
      else if (deniedClients.length != newDeniedClients.size())
      {
        deniedClientsChanged = true;
      }
      else
      {
        for (AddressMask m : deniedClients)
        {
          if (! newDeniedClients.contains(m))
          {
            deniedClientsChanged = true;
            break;
          }
        }
      }
    }


    // Determine whether to allow LDAPv2 clients.
    boolean newAllowLDAPv2;
    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_ALLOW_LDAPV2;
    BooleanConfigAttribute allowLDAPv2Stub =
         new BooleanConfigAttribute(ATTR_ALLOW_LDAPV2, getMessage(msgID),
                                    false);
    try
    {
      BooleanConfigAttribute allowLDAPv2Attr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(allowLDAPv2Stub);
      if (allowLDAPv2Attr == null)
      {
        newAllowLDAPv2 = DEFAULT_ALLOW_LDAPV2;
      }
      else
      {
        newAllowLDAPv2 = allowLDAPv2Attr.pendingValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyNewConfiguration", e);

      msgID = MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_ALLOW_LDAPV2;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              stackTraceToSingleLineString(e)));
      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = DirectoryServer.getServerErrorResultCode();
      }

      newAllowLDAPv2 = DEFAULT_ALLOW_LDAPV2;
    }


    // Determine whether to keep LDAP statistics.
    boolean newKeepStats;
    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_KEEP_STATS;
    BooleanConfigAttribute keepStatsStub =
         new BooleanConfigAttribute(ATTR_KEEP_LDAP_STATS, getMessage(msgID),
                                    false);
    try
    {
      BooleanConfigAttribute keepStatsAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(keepStatsStub);
      if (keepStatsAttr == null)
      {
        newKeepStats = DEFAULT_KEEP_LDAP_STATS;
      }
      else
      {
        newKeepStats = keepStatsAttr.pendingValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyNewConfiguration", e);

      msgID = MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_KEEP_STATS;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              stackTraceToSingleLineString(e)));
      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = DirectoryServer.getServerErrorResultCode();
      }

      newKeepStats = DEFAULT_KEEP_LDAP_STATS;
    }


    // The number of request handlers to maintain is not dynamically
    // reconfigurable, so we can skip it.


    // Determine whether to send a notice to clients on rejection.
    boolean newSendRejectionNotice;
    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_SEND_REJECTION_NOTICE;
    BooleanConfigAttribute notifyRejectsStub =
         new BooleanConfigAttribute(ATTR_SEND_REJECTION_NOTICE,
                                    getMessage(msgID), false);
    try
    {
      BooleanConfigAttribute notifyRejectsAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(notifyRejectsStub);
      if (notifyRejectsAttr == null)
      {
        newSendRejectionNotice = DEFAULT_SEND_REJECTION_NOTICE;
      }
      else
      {
        newSendRejectionNotice = notifyRejectsAttr.pendingValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyNewConfiguration", e);

      msgID = MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_SEND_REJECTION_NOTICE;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              stackTraceToSingleLineString(e)));
      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = DirectoryServer.getServerErrorResultCode();
      }

      newSendRejectionNotice = DEFAULT_SEND_REJECTION_NOTICE;
    }


    // Determine whether to use TCP keepalive.
    boolean newUseKeepAlive;
    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_USE_TCP_KEEPALIVE;
    BooleanConfigAttribute keepAliveStub =
         new BooleanConfigAttribute(ATTR_USE_TCP_KEEPALIVE, getMessage(msgID),
                                    false);
    try
    {
      BooleanConfigAttribute keepAliveAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(keepAliveStub);
      if (keepAliveAttr == null)
      {
        newUseKeepAlive = DEFAULT_USE_TCP_KEEPALIVE;
      }
      else
      {
        newUseKeepAlive = keepAliveAttr.pendingValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyNewConfiguration", e);

      msgID = MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_USE_TCP_KEEPALIVE;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              stackTraceToSingleLineString(e)));
      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = DirectoryServer.getServerErrorResultCode();
      }

      newUseKeepAlive = DEFAULT_USE_TCP_KEEPALIVE;
    }


    // Determine whether to use TCP nodelay.
    boolean newUseTCPNoDelay;
    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_USE_TCP_NODELAY;
    BooleanConfigAttribute noDelayStub =
         new BooleanConfigAttribute(ATTR_USE_TCP_NODELAY, getMessage(msgID),
                                    false);
    try
    {
      BooleanConfigAttribute noDelayAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(noDelayStub);
      if (noDelayAttr == null)
      {
        newUseTCPNoDelay = DEFAULT_USE_TCP_NODELAY;
      }
      else
      {
        newUseTCPNoDelay = noDelayAttr.pendingValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyNewConfiguration", e);

      msgID = MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_USE_TCP_NODELAY;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              stackTraceToSingleLineString(e)));
      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = DirectoryServer.getServerErrorResultCode();
      }

      newUseTCPNoDelay = DEFAULT_USE_TCP_NODELAY;
    }


    // The reuse address option isn't dynamically reconfigurable, so we can skip
    // it.


    // Determine the maximum allowed request size.
    int newMaxRequestSize;
    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_MAX_REQUEST_SIZE;
    IntegerWithUnitConfigAttribute maxReqSizeStub =
         new IntegerWithUnitConfigAttribute(ATTR_MAX_REQUEST_SIZE,
                                            getMessage(msgID), false,
                                            SIZE_UNITS, true, 0, true,
                                            MAX_REQUEST_SIZE_LIMIT);
    try
    {
      IntegerWithUnitConfigAttribute maxReqSizeAttr =
           (IntegerWithUnitConfigAttribute)
           configEntry.getConfigAttribute(maxReqSizeStub);
      if (maxReqSizeAttr == null)
      {
        newMaxRequestSize = DEFAULT_MAX_REQUEST_SIZE;
      }
      else
      {
        newMaxRequestSize = (int) maxReqSizeAttr.pendingCalculatedValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyNewConfiguration", e);

      msgID = MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_MAX_REQUEST_SIZE;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              stackTraceToSingleLineString(e)));
      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = DirectoryServer.getServerErrorResultCode();
      }

      newMaxRequestSize = DEFAULT_MAX_REQUEST_SIZE;
    }


    // The flag specifying whether to use SSL is not dynamically reconfigurable,
    // so we can skip it.


    // Determine whether to allow the StartTLS extended operation.
    boolean newAllowStartTLS;
    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_ALLOW_STARTTLS;
    BooleanConfigAttribute startTLSStub =
         new BooleanConfigAttribute(ATTR_ALLOW_STARTTLS, getMessage(msgID),
                                    false);
    try
    {
      BooleanConfigAttribute startTLSAttr =
           (BooleanConfigAttribute)
           configEntry.getConfigAttribute(startTLSStub);
      if (startTLSAttr == null)
      {
        // This is fine -- we'll just use the default.
        newAllowStartTLS = DEFAULT_ALLOW_STARTTLS;
      }
      else
      {
        newAllowStartTLS = startTLSAttr.pendingValue();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyNewConfiguration", e);

      msgID = MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_ALLOW_STARTTLS;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              stackTraceToSingleLineString(e)));
      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = DirectoryServer.getServerErrorResultCode();
      }

      newAllowStartTLS = DEFAULT_ALLOW_STARTTLS;
    }


    // The SSL client authentication policy and SSL certificate nickname are not
    // dynamically reconfigurable, so we can skip them.


    // Determine the set of SSL protocols to allow.
    String[] newSSLProtocols;
    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_SSL_ENABLED_PROTOCOLS;
    StringConfigAttribute sslProtocolsStub =
         new StringConfigAttribute(ATTR_SSL_PROTOCOLS, getMessage(msgID), false,
                                   true, false);
    try
    {
      StringConfigAttribute sslProtocolsAttr =
           (StringConfigAttribute)
           configEntry.getConfigAttribute(sslProtocolsStub);
      if (sslProtocolsAttr == null)
      {
        newSSLProtocols = null;
      }
      else
      {
        // FIXME -- Is there a good way to validate the provided set of values?
        newSSLProtocols = listToArray(sslProtocolsAttr.pendingValues());
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyNewConfiguration", e);

      msgID = MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_SSL_PROTOCOLS;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              stackTraceToSingleLineString(e)));
      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = DirectoryServer.getServerErrorResultCode();
      }

      newSSLProtocols = null;
    }



    // Determine the set of SSL cipher suites to allow.
    String[] newSSLCiphers;
    msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_SSL_ENABLED_CIPHERS;
    StringConfigAttribute sslCiphersStub =
         new StringConfigAttribute(ATTR_SSL_CIPHERS, getMessage(msgID), false,
                                   true, false);
    try
    {
      StringConfigAttribute sslCiphersAttr =
           (StringConfigAttribute)
           configEntry.getConfigAttribute(sslCiphersStub);
      if (sslCiphersAttr == null)
      {
        newSSLCiphers = null;
      }
      else
      {
        // FIXME -- Is there a good way to validate the provided set of values?
        newSSLCiphers = listToArray(sslCiphersAttr.pendingValues());
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyNewConfiguration", e);

      msgID = MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_SSL_CIPHERS;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              stackTraceToSingleLineString(e)));
      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = DirectoryServer.getServerErrorResultCode();
      }

      newSSLCiphers = null;
    }


    // If the provided configuration is acceptable, then apply it.
    if (resultCode == ResultCode.SUCCESS)
    {
      if (allowedClientsChanged)
      {
        AddressMask[] newAllowedArray;
        if ((newAllowedClients == null) || newAllowedClients.isEmpty())
        {
          newAllowedArray = null;
        }
        else
        {
          newAllowedArray = new AddressMask[newAllowedClients.size()];
          newAllowedClients.toArray(newAllowedArray);
        }

        allowedClients = newAllowedArray;
        if (detailedResults)
        {
          messages.add(getMessage(MSGID_LDAP_CONNHANDLER_NEW_ALLOWED_CLIENTS,
                                  String.valueOf(configEntryDN)));
        }
      }


      if (deniedClientsChanged)
      {
        AddressMask[] newDeniedArray;
        if ((newDeniedClients == null) || newDeniedClients.isEmpty())
        {
          newDeniedArray = null;
        }
        else
        {
          newDeniedArray = new AddressMask[newDeniedClients.size()];
          newDeniedClients.toArray(newDeniedArray);
        }

        deniedClients = newDeniedArray;
        if (detailedResults)
        {
          messages.add(getMessage(MSGID_LDAP_CONNHANDLER_NEW_DENIED_CLIENTS,
                                  String.valueOf(configEntryDN)));
        }
      }


      if (allowLDAPv2 != newAllowLDAPv2)
      {
        allowLDAPv2 = newAllowLDAPv2;
        if (allowLDAPv2)
        {
          if (statTracker == null)
          {
            statTracker = new LDAPStatistics(handlerName);
          }
          else
          {
            statTracker.clearStatistics();
          }
        }

        if (detailedResults)
        {
          messages.add(getMessage(MSGID_LDAP_CONNHANDLER_NEW_ALLOW_LDAPV2,
                                  String.valueOf(newAllowLDAPv2),
                                  String.valueOf(configEntryDN)));
        }
      }


      if (keepStats != newKeepStats)
      {
        keepStats = newKeepStats;
        if (detailedResults)
        {
          messages.add(getMessage(MSGID_LDAP_CONNHANDLER_NEW_KEEP_STATS,
                                  String.valueOf(newKeepStats),
                                  String.valueOf(configEntryDN)));
        }
      }


      if (sendRejectionNotice != newSendRejectionNotice)
      {
        sendRejectionNotice = newSendRejectionNotice;
        if (detailedResults)
        {
          messages.add(getMessage(
                            MSGID_LDAP_CONNHANDLER_NEW_SEND_REJECTION_NOTICE,
                            String.valueOf(newSendRejectionNotice),
                            String.valueOf(configEntryDN)));
        }
      }


      if (useKeepAlive != newUseKeepAlive)
      {
        useKeepAlive = newUseKeepAlive;
        if (detailedResults)
        {
          messages.add(getMessage(MSGID_LDAP_CONNHANDLER_NEW_USE_KEEPALIVE,
                                  String.valueOf(newUseKeepAlive),
                                  String.valueOf(configEntryDN)));
        }
      }


      if (useTCPNoDelay != newUseTCPNoDelay)
      {
        useTCPNoDelay = newUseTCPNoDelay;
        if (detailedResults)
        {
          messages.add(getMessage(MSGID_LDAP_CONNHANDLER_NEW_USE_TCP_NODELAY,
                                  String.valueOf(newUseTCPNoDelay),
                                  String.valueOf(configEntryDN)));
        }
      }


      if (maxRequestSize != newMaxRequestSize)
      {
        maxRequestSize = newMaxRequestSize;
        if (detailedResults)
        {
          messages.add(getMessage(MSGID_LDAP_CONNHANDLER_NEW_MAX_REQUEST_SIZE,
                                  String.valueOf(newMaxRequestSize),
                                  String.valueOf(configEntryDN)));
        }
      }


      if (allowStartTLS != newAllowStartTLS)
      {
        allowStartTLS = newAllowStartTLS;
        if (detailedResults)
        {
          messages.add(getMessage(MSGID_LDAP_CONNHANDLER_NEW_ALLOW_STARTTLS,
                                  String.valueOf(newAllowStartTLS),
                                  String.valueOf(configEntryDN)));
        }
      }


      // Update enabled SSL protocols.
      if (! Arrays.equals(enabledSSLProtocols, newSSLProtocols))
      {
        enabledSSLProtocols = newSSLProtocols;
        if (detailedResults)
        {
          messages.add(getMessage(MSGID_LDAP_CONNHANDLER_NEW_SSL_PROTOCOLS,
                                  Arrays.toString(newSSLProtocols),
                                  String.valueOf(configEntryDN)));
        }
      }


      // Update enabled SSL cipher suites.
      if (! Arrays.equals(enabledSSLCipherSuites, newSSLCiphers))
      {
        enabledSSLCipherSuites = newSSLCiphers;
        if (detailedResults)
        {
          messages.add(getMessage(MSGID_LDAP_CONNHANDLER_NEW_SSL_CIPHERS,
                                  Arrays.toString(newSSLCiphers),
                                  String.valueOf(configEntryDN)));
        }
      }
    }


    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }


  /**
   * Appends a string representation of this connection handler to the provided
   * buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    assert debugEnter(CLASS_NAME, "toString", "java.lang.StringBuilder");

    buffer.append(handlerName);
  }



  /**
   * Retrieves the DN of the configuration entry with which this alert generator
   * is associated.
   *
   * @return  The DN of the configuration entry with which this alert generator
   *          is associated.
   */
  public DN getComponentEntryDN()
  {
    assert debugEnter(CLASS_NAME, "getComponentEntryDN");

    return configEntryDN;
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
    assert debugEnter(CLASS_NAME, "getClassName");

    return CLASS_NAME;
  }



  /**
   * Retrieves information about the set of alerts that this generator may
   * produce.  The map returned should be between the notification type for a
   * particular notification and the human-readable description for that
   * notification.  This alert generator must not generate any alerts with types
   * that are not contained in this list.
   *
   * @return  Information about the set of alerts that this generator may
   *          produce.
   */
  public LinkedHashMap<String,String> getAlerts()
  {
    assert debugEnter(CLASS_NAME, "getAlerts");

    LinkedHashMap<String,String> alerts = new LinkedHashMap<String,String>();

    alerts.put(ALERT_TYPE_LDAP_CONNECTION_HANDLER_CONSECUTIVE_FAILURES,
               ALERT_DESCRIPTION_LDAP_CONNECTION_HANDLER_CONSECUTIVE_FAILURES);
    alerts.put(ALERT_TYPE_LDAP_CONNECTION_HANDLER_UNCAUGHT_ERROR,
               ALERT_DESCRIPTION_LDAP_CONNECTION_HANDLER_UNCAUGHT_ERROR);

    return alerts;
  }
}

