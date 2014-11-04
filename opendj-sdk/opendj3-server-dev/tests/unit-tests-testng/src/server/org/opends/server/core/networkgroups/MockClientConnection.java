/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2014 ForgeRock AS
 */
package org.opends.server.core.networkgroups;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.SearchOperation;
import org.opends.server.types.*;

/**
 * A mock connection for connection criteria testing.
 */
@SuppressWarnings("javadoc")
public final class MockClientConnection extends ClientConnection
{
  private final int clientPort;
  private final boolean isSecure;
  private final AuthenticationInfo authInfo;

  /**
   * Creates a new mock client connection.
   *
   * @param clientPort
   *          The client port.
   * @param isSecure
   *          Is the client using a secure connection.
   * @param bindDN
   *          The client bind DN.
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  public MockClientConnection(int clientPort, boolean isSecure, DN bindDN) throws Exception
  {
    this.clientPort = clientPort;
    this.isSecure = isSecure;
    if (bindDN != null)
    {
      Entry simpleUser = DirectoryServer.getEntry(bindDN);
      this.authInfo = new AuthenticationInfo(simpleUser, bindDN, true);
    }
    else
    {
      this.authInfo = new AuthenticationInfo();
    }
  }



  @Override
  public AuthenticationInfo getAuthenticationInfo()
  {
    return authInfo;
  }



  @Override
  public void cancelAllOperations(CancelRequest cancelRequest)
  {
    // Stub.
  }



  @Override
  public void cancelAllOperationsExcept(CancelRequest cancelRequest,
      int messageID)
  {
    // Stub.
  }



  @Override
  public CancelResult cancelOperation(int messageID,
      CancelRequest cancelRequest)
  {
    // Stub.
    return null;
  }



  @Override
  public void disconnect(DisconnectReason disconnectReason,
      boolean sendNotification, LocalizableMessage message)
  {
    // Stub.
  }



  @Override
  public String getClientAddress()
  {
    return "127.0.0.1";
  }



  @Override
  public int getClientPort()
  {
    return clientPort;
  }



  @Override
  public ConnectionHandler<?> getConnectionHandler()
  {
    // Stub.
    return null;
  }



  @Override
  public long getConnectionID()
  {
    // Stub.
    return 0;
  }



  @Override
  public InetAddress getLocalAddress()
  {
    // Stub.
    return null;
  }



  @Override
  public String getMonitorSummary()
  {
    // Stub.
    return null;
  }



  @Override
  public long getNumberOfOperations()
  {
    // Stub.
    return 0;
  }



  @Override
  public Operation getOperationInProgress(int messageID)
  {
    // Stub.
    return null;
  }



  @Override
  public Collection<Operation> getOperationsInProgress()
  {
    // Stub.
    return null;
  }



  @Override
  public String getProtocol()
  {
    // Stub.
    return null;
  }



  @Override
  public InetAddress getRemoteAddress()
  {
    try
    {
      return InetAddress.getByAddress(new byte[] { 127, 0, 0, 1 });
    }
    catch (UnknownHostException e)
    {
      throw new RuntimeException(e);
    }
  }



  @Override
  public String getServerAddress()
  {
    // Stub.
    return null;
  }



  @Override
  public int getServerPort()
  {
    // Stub.
    return 0;
  }

  @Override
  public boolean isConnectionValid()
  {
    // This connection is always valid
    return true;
  }

  @Override
  public boolean isSecure()
  {
    return isSecure;
  }



  @Override
  public boolean removeOperationInProgress(int messageID)
  {
    // Stub.
    return false;
  }



  @Override
  protected boolean sendIntermediateResponseMessage(
      IntermediateResponse intermediateResponse)
  {
    // Stub.
    return false;
  }



  @Override
  public void sendResponse(Operation operation)
  {
    // Stub.
  }



  @Override
  public void sendSearchEntry(SearchOperation searchOperation,
      SearchResultEntry searchEntry) throws DirectoryException
  {
    // Stub.
  }



  @Override
  public boolean sendSearchReference(SearchOperation searchOperation,
      SearchResultReference searchReference) throws DirectoryException
  {
    // Stub.
    return false;
  }



  @Override
  public void toString(StringBuilder buffer)
  {
    // Stub.
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public int getSSF()
  {
    // Stub.
    return 0;
  }
}
