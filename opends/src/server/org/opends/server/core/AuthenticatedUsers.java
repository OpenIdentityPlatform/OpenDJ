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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.core;



import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.opends.server.api.ChangeNotificationListener;
import org.opends.server.api.ClientConnection;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.operation.PostResponseAddOperation;
import org.opends.server.types.operation.PostResponseDeleteOperation;
import org.opends.server.types.operation.PostResponseModifyOperation;
import org.opends.server.types.operation.PostResponseModifyDNOperation;

import static org.opends.server.messages.CoreMessages.*;
import static org.opends.server.messages.MessageHandler.*;



/**
 * This class provides a data structure which maps an authenticated user DN to
 * the set of client connections authenticated as that user.  Note that a single
 * client connection may be registered with two different user DNs if the client
 * has different authentication and authorization identities.
 * <BR><BR>
 * This class also provides a mechanism for detecting changes to authenticated
 * user entries and notifying the corresponding client connections so that they
 * can update their cached versions.
 */
public class AuthenticatedUsers
       implements ChangeNotificationListener
{



  // The mapping between authenticated user DNs and the associated client
  // connection objects.
  private ConcurrentHashMap<DN,CopyOnWriteArraySet<ClientConnection>>
               userMap;



  /**
   * Creates a new instance of this authenticated users object.
   */
  public AuthenticatedUsers()
  {

    userMap = new ConcurrentHashMap<DN,CopyOnWriteArraySet<ClientConnection>>();

    DirectoryServer.registerChangeNotificationListener(this);
  }



  /**
   * Registers the provided user DN and client connection with this object.
   *
   * @param  userDN            The DN of the user associated with the provided
   *                           client connection.
   * @param  clientConnection  The client connection over which the user is
   *                           authenticated.
   */
  public synchronized void put(DN userDN, ClientConnection clientConnection)
  {

    CopyOnWriteArraySet<ClientConnection> connectionSet = userMap.get(userDN);
    if (connectionSet == null)
    {
      connectionSet = new CopyOnWriteArraySet<ClientConnection>();
      connectionSet.add(clientConnection);
      userMap.put(userDN, connectionSet);
    }
    else
    {
      connectionSet.add(clientConnection);
    }
  }



  /**
   * Deregisters the provided user DN and client connection with this object.
   *
   * @param  userDN            The DN of the user associated with the provided
   *                           client connection.
   * @param  clientConnection  The client connection over which the user is
   *                           authenticated.
   */
  public synchronized void remove(DN userDN, ClientConnection clientConnection)
  {

    CopyOnWriteArraySet<ClientConnection> connectionSet = userMap.get(userDN);
    if (connectionSet != null)
    {
      connectionSet.remove(clientConnection);
      if (connectionSet.isEmpty())
      {
        userMap.remove(userDN);
      }
    }
  }



  /**
   * Performs any processing that may be required after an add
   * operation.
   *
   * @param  addOperation  The add operation that was performed in the
   *                       server.
   * @param  entry         The entry that was added to the server.
   */
  public void handleAddOperation(
                   PostResponseAddOperation addOperation,
                   Entry entry)
  {
    // No implementation is required for add operations, since a connection
    // can't be authenticated as a user that doesn't exist yet.
  }



  /**
   * Performs any processing that may be required after a delete
   * operation.
   *
   * @param  deleteOperation  The delete operation that was performed
   *                          in the server.
   * @param  entry            The entry that was removed from the
   *                          server.
   */
  public void handleDeleteOperation(
                   PostResponseDeleteOperation deleteOperation,
                   Entry entry)
  {
    // Identify any client connections that may be authenticated or
    // authorized as the user whose entry has been deleted and terminate them.
    CopyOnWriteArraySet<ClientConnection> connectionSet =
         userMap.remove(entry.getDN());
    if (connectionSet != null)
    {
      for (ClientConnection conn : connectionSet)
      {
        int    msgID   = MSGID_CLIENTCONNECTION_DISCONNECT_DUE_TO_DELETE;
        String message = getMessage(msgID, String.valueOf(entry.getDN()));

        conn.disconnect(DisconnectReason.OTHER, true, message, msgID);
      }
    }
  }



  /**
   * Performs any processing that may be required after a modify
   * operation.
   *
   * @param  modifyOperation  The modify operation that was performed
   *                          in the server.
   * @param  oldEntry         The entry before it was updated.
   * @param  newEntry         The entry after it was updated.
   */
  public void handleModifyOperation(
                   PostResponseModifyOperation modifyOperation,
                   Entry oldEntry, Entry newEntry)
  {
    // Identify any client connections that may be authenticated or authorized
    // as the user whose entry has been modified and update them with the latest
    // version of the entry.
    CopyOnWriteArraySet<ClientConnection> connectionSet =
         userMap.get(oldEntry.getDN());
    if (connectionSet != null)
    {
      for (ClientConnection conn : connectionSet)
      {
        conn.updateAuthenticationInfo(oldEntry, newEntry);
      }
    }
  }



  /**
   * Performs any processing that may be required after a modify DN
   * operation.
   *
   * @param  modifyDNOperation  The modify DN operation that was
   *                            performed in the server.
   * @param  oldEntry           The entry before it was updated.
   * @param  newEntry           The entry after it was updated.
   */
  public void handleModifyDNOperation(
                   PostResponseModifyDNOperation modifyDNOperation,
                   Entry oldEntry, Entry newEntry)
  {
    // Identify any client connections that may be authenticated or authorized
    // as the user whose entry has been modified and update them with the latest
    // version of the entry.
    CopyOnWriteArraySet<ClientConnection> connectionSet =
         userMap.remove(oldEntry.getDN());
    if (connectionSet != null)
    {
      synchronized (this)
      {
        CopyOnWriteArraySet<ClientConnection> existingNewSet =
             userMap.get(newEntry.getDN());
        if (existingNewSet == null)
        {
          userMap.put(newEntry.getDN(), connectionSet);
        }
        else
        {
          existingNewSet.addAll(connectionSet);
        }
      }

      for (ClientConnection conn : connectionSet)
      {
        conn.updateAuthenticationInfo(oldEntry, newEntry);
      }
    }
  }
}

