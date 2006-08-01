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



import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.opends.server.api.ConnectionSecurityProvider;
import org.opends.server.api.DirectoryThread;
import org.opends.server.api.ServerShutdownListener;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.InitializationException;

import static org.opends.server.loggers.Debug.*;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ProtocolMessages.*;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines an LDAP request handler, which is associated with an LDAP
 * connection handler and is responsible for reading and decoding any requests
 * that LDAP clients may send to the server.  Multiple request handlers may be
 * used in conjunction with a single connection handler for better performance
 * and scalability.
 */
public class LDAPRequestHandler
       extends DirectoryThread
       implements ServerShutdownListener
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.protocols.ldap.LDAPRequestHandler";



  /**
   * The buffer size in bytes to use when reading data from a client.
   */
  public static final int BUFFER_SIZE = 8192;



  // Indicates whether the Directory Server is in the process of shutting down.
  private boolean shutdownRequested;

  // The queue that will be used to hold the set of pending connections that
  // need to be registered with the selector.
  private ConcurrentLinkedQueue<LDAPClientConnection> pendingConnections;

  // The connection handler with which this request handler is associated.
  private LDAPConnectionHandler connectionHandler;

  // The selector that will be used to monitor the client connections.
  private Selector selector;

  // The name to use for this request handler.
  private String handlerName;



  /**
   * Creates a new LDAP request handler that will be associated with the
   * provided connection handler.
   *
   * @param  connectionHandler  The LDAP connection handler with which this
   *                            request handler is associated.
   * @param  requestHandlerID   The integer value that may be used to distingush
   *                            this request handler from others associated with
   *                            the same connection handler.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   this request handler.
   */
  public LDAPRequestHandler(LDAPConnectionHandler connectionHandler,
                            int requestHandlerID)
         throws InitializationException
  {
    super("LDAP Request Handler " + requestHandlerID +
          " for connection handler " + connectionHandler.toString());

    assert debugConstructor(CLASS_NAME, String.valueOf(connectionHandler));

    this.connectionHandler = connectionHandler;

    handlerName         = getName();
    pendingConnections = new ConcurrentLinkedQueue<LDAPClientConnection>();

    try
    {
      selector = Selector.open();
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "<init>", e);

      int msgID = MSGID_LDAP_REQHANDLER_OPEN_SELECTOR_FAILED;
      String message = getMessage(msgID, handlerName, String.valueOf(e));
      throw new InitializationException(msgID, message, e);
    }
  }



  /**
   * Operates in a loop, waiting for client requests to arrive and ensuring that
   * they are processed properly.
   */
  public void run()
  {
    assert debugEnter(CLASS_NAME, "run");


    // Operate in a loop until the server shuts down.  Each time through the
    // loop, check for new requests, then check for new connections.
    while (! shutdownRequested)
    {
      int selectedKeys = 0;

      try
      {
        selectedKeys = selector.select();
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "run", e);

        // FIXME -- Should we do something else with this?
      }

      if (selectedKeys > 0)
      {
        Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
        while (iterator.hasNext())
        {
          SelectionKey key = iterator.next();

          try
          {
            if (key.isReadable())
            {
              LDAPClientConnection clientConnection = null;

              try
              {
                clientConnection = (LDAPClientConnection) key.attachment();

                try
                {
                  ConnectionSecurityProvider securityProvider =
                       clientConnection.getConnectionSecurityProvider();
                  if (! securityProvider.readData())
                  {
                    key.cancel();
                  }
                }
                catch (Exception e)
                {
                  assert debugException(CLASS_NAME, "run", e);

                  // Some other error occurred while we were trying to read data
                  // from the client.
                  // FIXME -- Should we log this?
                  key.cancel();
                  clientConnection.disconnect(DisconnectReason.SERVER_ERROR,
                                              false, String.valueOf(e), -1);
                }
              }
              catch (Exception e)
              {
                assert debugException(CLASS_NAME, "run", e);

                // We got some other kind of error.  If nothing else, cancel the
                // key, but if the client connection is available then
                // disconnect it as well.
                key.cancel();

                if (clientConnection != null)
                {
                  clientConnection.disconnect(DisconnectReason.SERVER_ERROR,
                                              false, String.valueOf(e), -1);
                }
              }
            }
            else if (! key.isValid())
            {
              key.cancel();
            }
          }
          catch (CancelledKeyException cke)
          {
            assert debugException(CLASS_NAME, "run", cke);

            // This could happen if a connection was closed between the time
            // that select returned and the time that we try to access the
            // associated channel.  If that was the case, we don't need to do
            // anything.
          }
          catch (Exception e)
          {
            assert debugException(CLASS_NAME, "run", e);

            // This should not happen, and it would have caused our reader
            // thread to die.  Log a severe error.
            int    msgID   = MSGID_LDAP_REQHANDLER_UNEXPECTED_SELECT_EXCEPTION;
            String message = getMessage(msgID, getName(),
                                        stackTraceToSingleLineString(e));
            logError(ErrorLogCategory.REQUEST_HANDLING,
                     ErrorLogSeverity.SEVERE_ERROR, message, msgID);
          }
          finally
          {
            iterator.remove();
          }
        }
      }


      // Check to see if we have any pending connections that need to be
      // registered with the selector.
      while (! pendingConnections.isEmpty())
      {
        LDAPClientConnection c = pendingConnections.remove();

        try
        {
          SocketChannel socketChannel = c.getSocketChannel();
          socketChannel.configureBlocking(false);
          socketChannel.register(selector, SelectionKey.OP_READ, c);
        }
        catch (Exception e)
        {
          assert debugException(CLASS_NAME, "run", e);

          c.disconnect(DisconnectReason.SERVER_ERROR, true,
                       MSGID_LDAP_REQHANDLER_CANNOT_REGISTER, handlerName,
                       String.valueOf(e));
        }
      }
    }
  }



  /**
   * Registers the provided client connection with this request handler so that
   * any requests received from that client will be processed.
   *
   * @param  clientConnection  The client connection to be registered with this
   *                           request handler.
   *
   * @return  <CODE>true</CODE> if the client connection was properly registered
   *          with this request handler, or <CODE>false</CODE> if not.
   */
  public boolean registerClient(LDAPClientConnection clientConnection)
  {
    assert debugEnter(CLASS_NAME, "registerClient",
                      String.valueOf(clientConnection));

    // FIXME -- Need to check if the maximum client limit has been reached.


    // If the server is in the process of shutting down, then we don't want to
    // accept it.
    if (shutdownRequested)
    {
      clientConnection.disconnect(DisconnectReason.SERVER_SHUTDOWN, true,
           MSGID_LDAP_REQHANDLER_REJECT_DUE_TO_SHUTDOWN);
      return false;
    }


    // Try to add the new connection to the queue.  If it succeeds, then wake
    // up the selector so it will be picked up right away.  Otherwise,
    // disconnect the client.
    if (pendingConnections.offer(clientConnection))
    {
      selector.wakeup();
      return true;
    }
    else
    {
      clientConnection.disconnect(DisconnectReason.ADMIN_LIMIT_EXCEEDED, true,
           MSGID_LDAP_REQHANDLER_REJECT_DUE_TO_QUEUE_FULL, handlerName);
      return false;
    }
  }



  /**
   * Deregisters the provided client connection from this request handler so it
   * will no longer look for requests from that client.
   *
   * @param  clientConnection  The client connection to deregister from this
   *                           request handler.
   */
  public void deregisterClient(LDAPClientConnection clientConnection)
  {
    assert debugEnter(CLASS_NAME, "deregisterClient",
                      String.valueOf(clientConnection));

    SelectionKey[] keyArray = selector.keys().toArray(new SelectionKey[0]);
    for (SelectionKey key : keyArray)
    {
      LDAPClientConnection conn = (LDAPClientConnection) key.attachment();
      if (clientConnection.equals(conn))
      {
        try
        {
          key.channel().close();
        }
        catch (Exception e)
        {
          assert debugException(CLASS_NAME, "deregisterClient", e);
        }

        try
        {
          key.cancel();
        }
        catch (Exception e)
        {
          assert debugException(CLASS_NAME, "deregisterClient", e);
        }
      }
    }
  }



  /**
   * Deregisters all clients associated with this request handler.
   */
  public void deregisterAllClients()
  {
    assert debugEnter(CLASS_NAME, "deregisterAllClients");

    SelectionKey[] keyArray = selector.keys().toArray(new SelectionKey[0]);
    for (SelectionKey key : keyArray)
    {
      try
      {
        key.channel().close();
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "deregisterAllClients", e);
      }

      try
      {
        key.cancel();
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "deregisterAllClients", e);
      }
    }
  }



  /**
   * Retrieves the set of all client connections that are currently registered
   * with this request handler.
   *
   * @return  The set of all client connections that are currently registered
   *          with this request handler.
   */
  public Collection<LDAPClientConnection> getClientConnections()
  {
    assert debugEnter(CLASS_NAME, "getClientConnections");

    SelectionKey[] keyArray = selector.keys().toArray(new SelectionKey[0]);

    ArrayList<LDAPClientConnection> connList =
         new ArrayList<LDAPClientConnection>(keyArray.length);
    for (SelectionKey key : keyArray)
    {
      connList.add((LDAPClientConnection) key.attachment());
    }

    return connList;
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
   * Causes this request handler to register itself as a shutdown listener with
   * the Directory Server.  This must be called if the connection handler is
   * shut down without closing all associated connections, otherwise the thread
   * would not be stopped by the server.
   */
  public void registerShutdownListener()
  {
    assert debugEnter(CLASS_NAME, "registerShutdownListener");

    DirectoryServer.registerShutdownListener(this);
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
    assert debugEnter(CLASS_NAME, "processServerShutdown",
                      String.valueOf(reason));

    shutdownRequested = true;

    Collection<LDAPClientConnection> clientConnections = getClientConnections();
    deregisterAllClients();

    if (clientConnections != null)
    {
      for (LDAPClientConnection c : clientConnections)
      {
        try
        {
          c.disconnect(DisconnectReason.SERVER_SHUTDOWN, true,
                       MSGID_LDAP_REQHANDLER_DEREGISTER_DUE_TO_SHUTDOWN,
                       reason);
        }
        catch (Exception e)
        {
          assert debugException(CLASS_NAME, "processServerShutdown", e);
        }
      }
    }

    try
    {
      if (selector != null)
      {
        selector.wakeup();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "processServerShutdown", e);
    }
  }
}

