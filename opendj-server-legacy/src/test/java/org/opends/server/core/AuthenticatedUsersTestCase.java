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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.core;

import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import java.net.InetAddress;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.TestCaseUtils;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.CancelRequest;
import org.opends.server.types.CancelResult;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.Entry;
import org.opends.server.types.IntermediateResponse;
import org.opends.server.types.Operation;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;
import org.opends.server.types.operation.PostResponseModifyOperation;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/** Tests for the {@link AuthenticatedUsers} plugin. */
public class AuthenticatedUsersTestCase extends CoreTestCase
{
  @BeforeClass
  public void startServer() throws Exception
  {
    TestCaseUtils.startServer();
  }

  /**
   * A minimal client connection whose hash code is constant, so that every
   * instance lands in the same hash bin of the per-user connection set.
   * {@link ClientConnection#setAuthenticationInfo} deregisters and re-registers
   * the connection, appending it back to the tail of that bin's node chain:
   * a weakly-consistent iterator positioned in the bin then meets the
   * connection again, and with two such connections
   * {@link AuthenticatedUsers#doPostResponse(PostResponseModifyOperation)}
   * ping-pongs between them forever (issue #857).
   */
  private static final class CollidingClientConnection extends ClientConnection
  {
    private final AtomicInteger authInfoUpdates = new AtomicInteger();

    @Override
    public void setAuthenticationInfo(AuthenticationInfo authenticationInfo)
    {
      authInfoUpdates.incrementAndGet();
      super.setAuthenticationInfo(authenticationInfo);
    }

    @Override
    public int hashCode()
    {
      return 42;
    }

    @Override
    public long getConnectionID()
    {
      return -1;
    }

    @Override
    public ConnectionHandler<?> getConnectionHandler()
    {
      return null;
    }

    @Override
    public String getProtocol()
    {
      return "internal";
    }

    @Override
    public String getClientAddress()
    {
      return null;
    }

    @Override
    public int getClientPort()
    {
      return 0;
    }

    @Override
    public String getServerAddress()
    {
      return null;
    }

    @Override
    public int getServerPort()
    {
      return 0;
    }

    @Override
    public InetAddress getRemoteAddress()
    {
      return null;
    }

    @Override
    public InetAddress getLocalAddress()
    {
      return null;
    }

    @Override
    public boolean isConnectionValid()
    {
      return true;
    }

    @Override
    public boolean isSecure()
    {
      return false;
    }

    @Override
    public long getNumberOfOperations()
    {
      return 0;
    }

    @Override
    public void sendResponse(Operation operation)
    {
    }

    @Override
    public void sendSearchEntry(SearchOperation searchOperation, SearchResultEntry searchEntry)
        throws DirectoryException
    {
    }

    @Override
    public boolean sendSearchReference(SearchOperation searchOperation, SearchResultReference searchReference)
        throws DirectoryException
    {
      return false;
    }

    @Override
    protected boolean sendIntermediateResponseMessage(IntermediateResponse intermediateResponse)
    {
      return false;
    }

    @Override
    public void disconnect(DisconnectReason disconnectReason, boolean sendNotification, LocalizableMessage message)
    {
    }

    @Override
    public Collection<Operation> getOperationsInProgress()
    {
      return null;
    }

    @Override
    public Operation getOperationInProgress(int messageID)
    {
      return null;
    }

    @Override
    public boolean removeOperationInProgress(int messageID)
    {
      return false;
    }

    @Override
    public CancelResult cancelOperation(int messageID, CancelRequest cancelRequest)
    {
      return null;
    }

    @Override
    public void cancelAllOperations(CancelRequest cancelRequest)
    {
    }

    @Override
    public void cancelAllOperationsExcept(CancelRequest cancelRequest, int messageID)
    {
    }

    @Override
    public String getMonitorSummary()
    {
      return "";
    }

    @Override
    public void toString(StringBuilder buffer)
    {
      buffer.append("CollidingClientConnection");
    }

    @Override
    public int getSSF()
    {
      return 0;
    }
  }

  /**
   * Modifying an entry two or more connections are authenticated as must
   * terminate and update every connection exactly once, even though each
   * update re-registers the connection in the set being iterated.
   */
  @Test(timeOut = 60000)
  public void testDoPostResponseModifyTerminatesWhenConnectionsReRegister() throws Exception
  {
    Entry oldEntry = TestCaseUtils.makeEntry(
        "dn: uid=issue857.user,o=test",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: issue857.user",
        "givenName: Issue857",
        "sn: User",
        "cn: Issue857 User");
    Entry newEntry = TestCaseUtils.makeEntry(
        "dn: uid=issue857.user,o=test",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: issue857.user",
        "givenName: Issue857",
        "sn: User",
        "cn: Issue857 User",
        "description: updated");

    AuthenticatedUsers users = DirectoryServer.getAuthenticatedUsers();
    CollidingClientConnection conn1 = new CollidingClientConnection();
    CollidingClientConnection conn2 = new CollidingClientConnection();
    conn1.setAuthenticationInfo(new AuthenticationInfo(oldEntry, false));
    conn2.setAuthenticationInfo(new AuthenticationInfo(oldEntry, false));
    try
    {
      assertEquals(users.get(oldEntry.getName()).size(), 2);
      conn1.authInfoUpdates.set(0);
      conn2.authInfoUpdates.set(0);

      PostResponseModifyOperation op = mock(PostResponseModifyOperation.class);
      when(op.getResultCode()).thenReturn(ResultCode.SUCCESS);
      when(op.getCurrentEntry()).thenReturn(oldEntry);
      when(op.getModifiedEntry()).thenReturn(newEntry);

      users.doPostResponse(op);

      assertEquals(conn1.authInfoUpdates.get(), 1);
      assertEquals(conn2.authInfoUpdates.get(), 1);

      Set<ClientConnection> registered = users.get(oldEntry.getName());
      assertNotNull(registered);
      assertTrue(registered.contains(conn1));
      assertTrue(registered.contains(conn2));

      assertEquals(conn1.getAuthenticationInfo().getAuthenticationEntry()
          .parseAttribute("description").asString(), "updated");
      assertEquals(conn2.getAuthenticationInfo().getAuthenticationEntry()
          .parseAttribute("description").asString(), "updated");
    }
    finally
    {
      conn1.setAuthenticationInfo(new AuthenticationInfo());
      conn2.setAuthenticationInfo(new AuthenticationInfo());
    }
  }
}
