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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions copyright 2011 ForgeRock AS
 */
package org.opends.server.core;
import java.util.HashSet;
import java.util.Set;
import org.opends.messages.Message;



import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.opends.server.api.ChangeNotificationListener;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.DITCacheMap;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.DN;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.Entry;
import org.opends.server.types.operation.PostResponseAddOperation;
import org.opends.server.types.operation.PostResponseDeleteOperation;
import org.opends.server.types.operation.PostResponseModifyOperation;
import org.opends.server.types.operation.PostResponseModifyDNOperation;

import static org.opends.messages.CoreMessages.*;
import static org.opends.server.loggers.debug.DebugLogger.*;

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
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // The mapping between authenticated user DNs and the associated client
  // connection objects.
  private DITCacheMap<CopyOnWriteArraySet<ClientConnection>> userMap;

  // Lock to protect internal data structures.
  private final ReentrantReadWriteLock lock;


  /**
   * Creates a new instance of this authenticated users object.
   */
  public AuthenticatedUsers()
  {
    userMap = new DITCacheMap<CopyOnWriteArraySet<ClientConnection>>();
    lock = new ReentrantReadWriteLock();

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
  public void put(DN userDN, ClientConnection clientConnection)
  {
    lock.writeLock().lock();
    try
    {
      CopyOnWriteArraySet<ClientConnection> connectionSet =
              userMap.get(userDN);
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
    finally
    {
      lock.writeLock().unlock();
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
  public void remove(DN userDN, ClientConnection clientConnection)
  {
    lock.writeLock().lock();
    try
    {
      CopyOnWriteArraySet<ClientConnection> connectionSet =
              userMap.get(userDN);
      if (connectionSet != null)
      {
        connectionSet.remove(clientConnection);
        if (connectionSet.isEmpty())
        {
          userMap.remove(userDN);
        }
      }
    }
    finally
    {
      lock.writeLock().unlock();
    }
  }



  /**
   * Retrieves the set of client connections authenticated as the specified
   * user.  This method is only intended for internal testing use and should not
   * be called for any other purpose.
   *
   * @param  userDN  The DN of the user for which to retrieve the corresponding
   *                 set of client connections.
   *
   * @return  The set of client connections authenticated as the specified user,
   *          or {@code null} if there are none.
   */
  public CopyOnWriteArraySet<ClientConnection> get(DN userDN)
  {
    lock.readLock().lock();
    try
    {
      return userMap.get(userDN);
    }
    finally
    {
      lock.readLock().unlock();
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
    // Identify any client connections that may be authenticated
    // or authorized as the user whose entry has been deleted and
    // terminate them.
    Set<CopyOnWriteArraySet<ClientConnection>> arraySet =
            new HashSet<CopyOnWriteArraySet<ClientConnection>>();
    lock.writeLock().lock();
    try
    {
      userMap.removeSubtree(entry.getDN(), arraySet);
    }
    finally
    {
      lock.writeLock().unlock();
    }
    for (CopyOnWriteArraySet<ClientConnection>
            connectionSet : arraySet)
    {
      for (ClientConnection conn : connectionSet)
      {
        Message message = WARN_CLIENTCONNECTION_DISCONNECT_DUE_TO_DELETE.get(
                String.valueOf(entry.getDN()));

        conn.disconnect(DisconnectReason.INVALID_CREDENTIALS, true, message);
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
    // Identify any client connections that may be authenticated
    // or authorized as the user whose entry has been modified
    // and update them with the latest version of the entry.
    lock.writeLock().lock();
    try
    {
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
    finally
    {
      lock.writeLock().unlock();
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
    String oldDNString = oldEntry.getDN().toNormalizedString();
    String newDNString = newEntry.getDN().toNormalizedString();

    // Identify any client connections that may be authenticated
    // or authorized as the user whose entry has been modified
    // and update them with the latest version of the entry.
    lock.writeLock().lock();
    try
    {
      Set<CopyOnWriteArraySet<ClientConnection>> arraySet =
        new HashSet<CopyOnWriteArraySet<ClientConnection>>();
      userMap.removeSubtree(oldEntry.getDN(), arraySet);
      for (CopyOnWriteArraySet<ClientConnection>
              connectionSet : arraySet)
      {
        DN authNDN = null;
        DN authZDN = null;
        DN newAuthNDN = null;
        DN newAuthZDN = null;
        CopyOnWriteArraySet<ClientConnection> newAuthNSet = null;
        CopyOnWriteArraySet<ClientConnection> newAuthZSet = null;
        for (ClientConnection conn : connectionSet)
        {
          if (authNDN == null)
          {
            authNDN = conn.getAuthenticationInfo().getAuthenticationDN();
            try
            {
              StringBuilder builder = new StringBuilder(
                  authNDN.toNormalizedString());
              int oldDNIndex = builder.lastIndexOf(oldDNString);
              builder.replace(oldDNIndex, builder.length(),
                      newDNString);
              String newAuthNDNString = builder.toString();
              newAuthNDN = DN.decode(newAuthNDNString);
            }
            catch (Exception e)
            {
              // Shouldnt happen.
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);
              }
            }
          }
          if (authZDN == null)
          {
            authZDN = conn.getAuthenticationInfo().getAuthorizationDN();
            try
            {
              StringBuilder builder = new StringBuilder(
                  authZDN.toNormalizedString());
              int oldDNIndex = builder.lastIndexOf(oldDNString);
              builder.replace(oldDNIndex, builder.length(),
                      newDNString);
              String newAuthZDNString = builder.toString();
              newAuthZDN = DN.decode(newAuthZDNString);
            }
            catch (Exception e)
            {
              // Shouldnt happen.
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);
              }
            }
          }
          if ((newAuthNDN != null) && (authNDN != null) &&
               authNDN.isDescendantOf(oldEntry.getDN()))
          {
            if (newAuthNSet == null)
            {
              newAuthNSet = new CopyOnWriteArraySet<ClientConnection>();
            }
            conn.getAuthenticationInfo().setAuthenticationDN(newAuthNDN);
            newAuthNSet.add(conn);
          }
          if ((newAuthZDN != null) && (authZDN != null) &&
               authZDN.isDescendantOf(oldEntry.getDN()))
          {
            if (newAuthZSet == null)
            {
              newAuthZSet = new CopyOnWriteArraySet<ClientConnection>();
            }
            conn.getAuthenticationInfo().setAuthorizationDN(newAuthZDN);
            newAuthZSet.add(conn);
          }
        }
        if ((newAuthNDN != null) && (newAuthNSet != null))
        {
          userMap.put(newAuthNDN, newAuthNSet);
        }
        if ((newAuthZDN != null) && (newAuthZSet != null))
        {
          userMap.put(newAuthZDN, newAuthZSet);
        }
      }
    }
    finally
    {
      lock.writeLock().unlock();
    }
  }
}

