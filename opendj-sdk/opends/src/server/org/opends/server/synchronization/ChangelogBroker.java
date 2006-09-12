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
package org.opends.server.synchronization;

import static org.opends.server.loggers.Error.logError;
import static org.opends.server.messages.MessageHandler.getMessage;
import static org.opends.server.synchronization.SynchMessages.*;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.TreeSet;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

import org.opends.server.changelog.ProtocolSession;
import org.opends.server.changelog.SocketSession;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchListener;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;
import org.opends.server.types.SearchScope;


/**
 * The broker for Multimaster Synchronization.
 */
public class ChangelogBroker implements InternalSearchListener
{
  private boolean shutdown = false;
  private List<String> servers;
  private Short identifier;
  private boolean connected = false;
  private SynchronizationDomain domain;
  private final Object lock = new Object();
  private String changelogServer = "Not connected";
  private TreeSet<FakeOperation> replayOperations;
  private ProtocolSession session = null;

  /**
   * Creates a new Changelog Broker for a particular SynchronizationDomain.
   *
   * @param domain The SynchronizationDomain for which the borker is created.
   */
  public ChangelogBroker(SynchronizationDomain domain)
  {
    this.domain = domain;
    replayOperations =
      new TreeSet<FakeOperation>(new FakeOperationComparator());
  }


  /**
   * Start the ChangelogBroker.
   *
   * @param identifier identifier of the changelog
   * @param servers list of servers used
   * @throws Exception : in case of errors
   */
  public void start(Short identifier,
                    List<String> servers)
                    throws Exception
  {
    /*
     * Open Socket to the Changelog
     * Send the Start message
     */
    this.identifier = identifier;
    this.servers = servers;
    if (servers.size() < 1)
    {
      int    msgID   = MSGID_NEED_MORE_THAN_ONE_CHANGELOG_SERVER;
      String message = getMessage(msgID);
      logError(ErrorLogCategory.SYNCHRONIZATION,
               ErrorLogSeverity.NOTICE,
               message, msgID);
    }

    this.connect();
  }


  /**
   * Connect the Changelog server to other servers.
   *
   * @throws NumberFormatException address was invalid
   * @throws IOException error during connection phase
   */
  private void connect() throws NumberFormatException, IOException
  {
    ChangelogStartMessage startMsg;

    boolean checkState = true;
    while( !connected)
    {
      for (String server : servers)
      {
        String token[] = server.split(":");
        String hostname = token[0];
        String port = token[1];

        try
        {
          /*
           * Open a socket connection to the next candidate.
           */
          InetSocketAddress ServerAddr = new InetSocketAddress(
              InetAddress.getByName(hostname), Integer.parseInt(port));
          Socket socket = new Socket();
          socket.connect(ServerAddr, 500);
          session = new SocketSession(socket);

          /*
           * Send our ServerStartMessage.
           */
          ServerStartMessage msg = domain.newServerStartMessage();
          session.publish(msg);


          /*
           * Read the ChangelogStartMessage that should come back.
           * TODO : should have a timeout here.
           */
          startMsg = (ChangelogStartMessage) session.receive();

          /*
           * We must not publish changes to a changelog that has not
           * seen all our previous changes because this could cause some
           * other ldap servers to miss those changes.
           * Check that the Changelog has seen all our previous changes.
           * If not, try another changelog server.
           * If no other changelog server has seen all our changes, recover
           * those changes and send them again to any changelog server.
           */
          ChangeNumber changelogMaxChangeNumber =
            startMsg.getServerState().getMaxChangeNumber(identifier);
          if (changelogMaxChangeNumber == null)
            changelogMaxChangeNumber = new ChangeNumber(0, 0, identifier);
          ChangeNumber ourMaxChangeNumber =  domain.getMaxChangeNumber();
          if ((ourMaxChangeNumber == null) ||
              (ourMaxChangeNumber.olderOrEqual(changelogMaxChangeNumber)))
          {
            changelogServer = ServerAddr.toString();
            connected = true;
            break;
          }
          else
          {
            if (checkState == true)
            {
              /* This changelog server is missing some
               * of our changes, we are going to try another server
               * but before log a notice message
               */
              int    msgID   = MSGID_CHANGELOG_MISSING_CHANGES;
              String message = getMessage(msgID, server);
              logError(ErrorLogCategory.SYNCHRONIZATION,
                       ErrorLogSeverity.NOTICE,
                       message, msgID);
            }
            else
            {
              replayOperations.clear();
              /*
               * Get all the changes that have not been seen by this changelog
               * server and update it
               */
              InternalClientConnection conn = new InternalClientConnection();
              LDAPFilter filter = LDAPFilter.decode(
                  "("+ Historical.HISTORICALATTRIBUTENAME +
                  ">=dummy:" + changelogMaxChangeNumber + ")");
              LinkedHashSet<String> attrs = new LinkedHashSet<String>(1);
              attrs.add(Historical.HISTORICALATTRIBUTENAME);
              InternalSearchOperation op = conn.processSearch(
                  new ASN1OctetString(domain.getBaseDN().toString()),
                  SearchScope.WHOLE_SUBTREE,
                  DereferencePolicy.NEVER_DEREF_ALIASES,
                  0, 0, false, filter,
                  attrs, this);
              if (op.getResultCode() != ResultCode.SUCCESS)
              {
                /*
                 * An error happened trying to search for the updates
                 * This server therefore can't start acepting new updates.
                 * TODO : should stop the LDAP server (how to ?)
                 */
                int    msgID   = MSGID_CANNOT_RECOVER_CHANGES;
                String message = getMessage(msgID);
                logError(ErrorLogCategory.SYNCHRONIZATION,
                         ErrorLogSeverity.FATAL_ERROR,
                         message, msgID);
              }
              else
              {
                changelogServer = ServerAddr.toString();
                connected = true;
                for (FakeOperation replayOp : replayOperations)
                {
                  publish(replayOp.generateMessage());
                }
                break;
              }
            }
          }
        }
        catch (ConnectException e)
        {
          /*
           * There was no server waiting on this host:port
           * Log a notice and try the next changelog server in the list
           */
          int    msgID   = MSGID_NO_CHANGELOG_SERVER_LISTENING;
          String message = getMessage(msgID, server);
          logError(ErrorLogCategory.SYNCHRONIZATION,
                   ErrorLogSeverity.NOTICE,
                   message, msgID);
        }
        catch (Exception e)
        {
          int    msgID   = MSGID_EXCEPTION_STARTING_SESSION;
          String message = getMessage(msgID)  + stackTraceToSingleLineString(e);
          logError(ErrorLogCategory.SYNCHRONIZATION,
                   ErrorLogSeverity.SEVERE_ERROR,
                   message, msgID);
        }
        finally
        {
          if (connected == false)
          {
            if (session != null)
            {
              session.close();
              session = null;
            }
          }
        }
      }

      if (!connected)
      {
        if (checkState == true)
        {
          /*
           * We could not find a changelog server that has seen all the
           * changes that this server has already processed, start again
           * the loop looking for any changelog server.
           */
          checkState = false;
          int    msgID   = MSGID_COULD_NOT_FIND_CHANGELOG_WITH_MY_CHANGES;
          String message = getMessage(msgID);
          logError(ErrorLogCategory.SYNCHRONIZATION,
              ErrorLogSeverity.NOTICE,
              message, msgID);
        }
        else
        {
          /*
           * This server could not find any changelog server
           * Let's wait a little and try again.
           */
          synchronized (this)
          {
            checkState = false;
            int    msgID   = MSGID_COULD_NOT_FIND_CHANGELOG;
            String message = getMessage(msgID);
            logError(ErrorLogCategory.SYNCHRONIZATION,
                ErrorLogSeverity.NOTICE,
                message, msgID);
            try
            {
              this.wait(1000);
            } catch (InterruptedException e)
            {
            }
          }
        }
      }
    }
  }


  /**
   * Restart the Changelog broker after a failure.
   *
   * @param failingSession the socket which failed
   */
  private void reStart(ProtocolSession failingSession)
  {
    try
    {
      failingSession.close();
    } catch (IOException e1)
    {
      // ignore
    }

    if (failingSession == session)
    {
      this.connected = false;
    }
    while (!this.connected && (!this.shutdown))
    {
      try
      {
        this.connect();
      } catch (Exception e)
      {
        int    msgID   = MSGID_EXCEPTION_STARTING_SESSION;
        String message = getMessage(msgID) + stackTraceToSingleLineString(e);
        logError(ErrorLogCategory.SYNCHRONIZATION,
                 ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
      }
    }
  }


  /**
   * Publish a message to the other servers.
   * @param msg the message to publish
   */
  public void publish(SynchronizationMessage msg)
  {
    boolean done = false;
    ProtocolSession failingSession = session;

    while (!done)
    {
      synchronized (lock)
      {
        try
        {
          if (this.connected == false)
            this.reStart(failingSession);

          session.publish(msg);
          done = true;
        } catch (IOException e)
        {
          this.reStart(failingSession);
        }
      }
    }
  }


  /**
   * Receive a message.
   * @return the received message
   */
  public SynchronizationMessage receive()
  {
    while (shutdown == false)
    {
      ProtocolSession failingSession = session;
      try
      {
        return session.receive();
      } catch (Exception e)
      {
        if (shutdown == false)
        {
          synchronized (lock)
          {
            this.reStart(failingSession);
          }
        }
      }
    }
    return null;
  }


  /**
   * stop the server.
   */
  public void stop()
  {
    shutdown = true;
    try
    {
      session.close();
    } catch (IOException e)
    {}
  }

  /**
   * restart the server after a suspension.
   * @throws Exception in case of errors.
   */
  public void restartReceive() throws Exception
  {
    // TODO Auto-generated method stub

  }

  /**
   * Suspend message reception.
   * @throws Exception in case of errors.
   */
  public void suspendReceive() throws Exception
  {
    // TODO Auto-generated method stub

  }

  /**
   * Get the name of the changelog server to which this broker is currently
   * connected.
   *
   * @return the name of the changelog server to which this domain
   *         is currently connected.
   */
  public String getChangelogServer()
  {
    return changelogServer;
  }
  /**
   * {@inheritDoc}
   */
  public void handleInternalSearchEntry(
      InternalSearchOperation searchOperation,
      SearchResultEntry searchEntry)
  {
    /*
     * Only deal with modify operation so far
     * TODO : implement code for ADD, DEL, MODDN operation
     *
     * Parse all ds-sync-hist attribute values
     *   - for each Changenumber>changelogMaxChangeNumber :
     *          build an attribute mod
     *
     */
    Iterable<FakeOperation> updates =
      Historical.generateFakeOperations(searchEntry);
    for (FakeOperation op : updates)
    {
      replayOperations.add(op);
    }
  }

  /**
   * {@inheritDoc}
   */
  public void handleInternalSearchReference(
      InternalSearchOperation searchOperation,
      SearchResultReference searchReference)
  {
    // TODO to be implemented
  }
}
