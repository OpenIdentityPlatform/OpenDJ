/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.core;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.DITCacheMap;
import org.opends.server.api.plugin.InternalDirectoryServerPlugin;
import org.opends.server.api.plugin.PluginResult.PostResponse;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.Entry;
import org.opends.server.types.operation.PostResponseDeleteOperation;
import org.opends.server.types.operation.PostResponseModifyDNOperation;
import org.opends.server.types.operation.PostResponseModifyOperation;

import static org.opends.messages.CoreMessages.*;
import static org.opends.server.api.plugin.PluginType.*;

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
public class AuthenticatedUsers extends InternalDirectoryServerPlugin
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * The mapping between authenticated user DNs and the associated client
   * connection objects.
   */
  private final DITCacheMap<CopyOnWriteArraySet<ClientConnection>> userMap;

  /** Lock to protect internal data structures. */
  private final ReentrantReadWriteLock lock;

  /** Dummy configuration DN. */
  private static final String CONFIG_DN = "cn=Authenticated Users,cn=config";

  /**
   * Creates a new instance of this authenticated users object.
   */
  public AuthenticatedUsers()
  {
    super(DN.valueOf(CONFIG_DN), EnumSet.of(
        // No implementation is required for add operations, since a connection
        // can not be authenticated as a user that does not exist yet.
        POST_RESPONSE_MODIFY, POST_RESPONSE_MODIFY_DN, POST_RESPONSE_DELETE),
        true);
    userMap = new DITCacheMap<>();
    lock = new ReentrantReadWriteLock();

    DirectoryServer.registerInternalPlugin(this);
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
      CopyOnWriteArraySet<ClientConnection> connectionSet = userMap.get(userDN);
      if (connectionSet == null)
      {
        connectionSet = new CopyOnWriteArraySet<>();
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

  @Override
  public PostResponse doPostResponse(PostResponseDeleteOperation op)
  {
    final DN entryDN = op.getEntryDN();
    if (op.getResultCode() != ResultCode.SUCCESS || operationDoesNotTargetAuthenticatedUser(entryDN))
    {
      return PostResponse.continueOperationProcessing();
    }

    // Identify any client connections that may be authenticated
    // or authorized as the user whose entry has been deleted and terminate them
    Set<CopyOnWriteArraySet<ClientConnection>> arraySet = new HashSet<>();
    lock.writeLock().lock();
    try
    {
      userMap.removeSubtree(entryDN, arraySet);
    }
    finally
    {
      lock.writeLock().unlock();
    }

    for (CopyOnWriteArraySet<ClientConnection> connectionSet : arraySet)
    {
      for (ClientConnection conn : connectionSet)
      {
        LocalizableMessage message = WARN_CLIENTCONNECTION_DISCONNECT_DUE_TO_DELETE.get(entryDN);
        conn.disconnect(DisconnectReason.INVALID_CREDENTIALS, true, message);
      }
    }
    return PostResponse.continueOperationProcessing();
  }

  private boolean operationDoesNotTargetAuthenticatedUser(final DN entryDN)
  {
    lock.readLock().lock();
    try
    {
      return !userMap.containsSubtree(entryDN);
    }
    finally
    {
      lock.readLock().unlock();
    }
  }

  @Override
  public PostResponse doPostResponse(PostResponseModifyOperation op)
  {
    final Entry oldEntry = op.getCurrentEntry();
    if (op.getResultCode() != ResultCode.SUCCESS || oldEntry == null
            ||  operationDoesNotTargetAuthenticatedUser(oldEntry.getName()))
    {
      return PostResponse.continueOperationProcessing();
    }

    // Identify any client connections that may be authenticated
    // or authorized as the user whose entry has been modified
    // and update them with the latest version of the entry
    // including any virtual attributes.
    lock.writeLock().lock();
    try
    {
      CopyOnWriteArraySet<ClientConnection> connectionSet = userMap.get(oldEntry.getName());
      if (connectionSet != null)
      {
        Entry newEntry = null;
        for (ClientConnection conn : connectionSet)
        {
          if (newEntry == null)
          {
            newEntry = op.getModifiedEntry().duplicate(true);
          }
          conn.updateAuthenticationInfo(oldEntry, newEntry);
        }
      }
    }
    finally
    {
      lock.writeLock().unlock();
    }
    return PostResponse.continueOperationProcessing();
  }

  @Override
  public PostResponse doPostResponse(PostResponseModifyDNOperation op)
  {
    final Entry oldEntry = op.getOriginalEntry();
    final Entry newEntry = op.getUpdatedEntry();
    if (op.getResultCode() != ResultCode.SUCCESS || oldEntry == null || newEntry == null
            || operationDoesNotTargetAuthenticatedUser(oldEntry.getName()))
    {
      return PostResponse.continueOperationProcessing();
    }

    final DN oldDN = oldEntry.getName();
    final DN newDN = newEntry.getName();

    // Identify any client connections that may be authenticated
    // or authorized as the user whose entry has been modified
    // and update them with the latest version of the entry.
    lock.writeLock().lock();
    try
    {
      final Set<CopyOnWriteArraySet<ClientConnection>> arraySet = new HashSet<>();
      userMap.removeSubtree(oldEntry.getName(), arraySet);
      for (CopyOnWriteArraySet<ClientConnection> connectionSet : arraySet)
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
              newAuthNDN = authNDN.rename(oldDN, newDN);
            }
            catch (Exception e)
            {
              // Should not happen.
              logger.traceException(e);
            }
          }
          if (authZDN == null)
          {
            authZDN = conn.getAuthenticationInfo().getAuthorizationDN();
            try
            {
              newAuthZDN = authZDN.rename(oldDN, newDN);
            }
            catch (Exception e)
            {
              // Should not happen.
              logger.traceException(e);
            }
          }
          if (newAuthNDN != null && authNDN != null && authNDN.isSubordinateOrEqualTo(oldEntry.getName()))
          {
            if (newAuthNSet == null)
            {
              newAuthNSet = new CopyOnWriteArraySet<>();
            }
            conn.getAuthenticationInfo().setAuthenticationDN(newAuthNDN);
            newAuthNSet.add(conn);
          }
          if (newAuthZDN != null && authZDN != null && authZDN.isSubordinateOrEqualTo(oldEntry.getName()))
          {
            if (newAuthZSet == null)
            {
              newAuthZSet = new CopyOnWriteArraySet<>();
            }
            conn.getAuthenticationInfo().setAuthorizationDN(newAuthZDN);
            newAuthZSet.add(conn);
          }
        }
        if (newAuthNDN != null && newAuthNSet != null)
        {
          userMap.put(newAuthNDN, newAuthNSet);
        }
        if (newAuthZDN != null && newAuthZSet != null)
        {
          userMap.put(newAuthZDN, newAuthZSet);
        }
      }
    }
    finally
    {
      lock.writeLock().unlock();
    }
    return PostResponse.continueOperationProcessing();
  }
}

