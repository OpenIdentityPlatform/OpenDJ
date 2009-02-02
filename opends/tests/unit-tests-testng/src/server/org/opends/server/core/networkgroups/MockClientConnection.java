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
 *      Copyright 2009 Sun Microsystems, Inc.
 */
package org.opends.server.core.networkgroups;



import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Collection;

import org.opends.messages.Message;
import org.opends.server.admin.std.meta.NetworkGroupCfgDefn.AllowedAuthMethod;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.api.ConnectionSecurityProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.SearchOperation;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.ByteString;
import org.opends.server.types.CancelRequest;
import org.opends.server.types.CancelResult;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.Entry;
import org.opends.server.types.IntermediateResponse;
import org.opends.server.types.Operation;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;



/**
 * A mock connection for connection criteria testing.
 */
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
   * @param authMethod
   *          The client authentication mathod.
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  public MockClientConnection(int clientPort, boolean isSecure,
      DN bindDN, AllowedAuthMethod authMethod) throws Exception
  {
    this.clientPort = clientPort;
    this.isSecure = isSecure;

    switch (authMethod)
    {
    case ANONYMOUS:
      this.authInfo = new AuthenticationInfo();
      break;
    case SIMPLE:
      Entry simpleUser = DirectoryServer.getEntry(bindDN);
      ByteString password = new ASN1OctetString();
      password.setValue("password");
      this.authInfo =
          new AuthenticationInfo(simpleUser, password, true);
      break;
    default: // SASL
      Entry saslUser = DirectoryServer.getEntry(bindDN);
      this.authInfo =
          new AuthenticationInfo(saslUser, "external", true);
      break;
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
      boolean sendNotification, Message message)
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
  public ConnectionSecurityProvider getConnectionSecurityProvider()
  {
    // Stub.
    return null;
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
  public String getSecurityMechanism()
  {
    // Stub.
    return null;
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
  public boolean isSecure()
  {
    return isSecure;
  }



  @Override
  public boolean processDataRead(ByteBuffer buffer)
  {
    // Stub.
    return false;
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
  public void setConnectionSecurityProvider(
      ConnectionSecurityProvider securityProvider)
  {
    // Stub.
  }



  @Override
  public void toString(StringBuilder buffer)
  {
    // Stub.
  }
}