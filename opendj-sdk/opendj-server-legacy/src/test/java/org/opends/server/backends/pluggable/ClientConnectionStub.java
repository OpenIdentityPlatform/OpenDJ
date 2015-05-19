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
 *      Copyright 2015 ForgeRock AS
 */
package org.opends.server.backends.pluggable;

import java.net.InetAddress;
import java.util.Collection;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.core.SearchOperation;
import org.opends.server.types.CancelRequest;
import org.opends.server.types.CancelResult;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.IntermediateResponse;
import org.opends.server.types.Operation;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;

class ClientConnectionStub extends ClientConnection
{

  @Override
  public long getConnectionID()
  {
    return 0;
  }

  @Override
  public ConnectionHandler<?> getConnectionHandler()
  {
    return null;
  }

  @Override
  public String getProtocol()
  {
    return null;
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
    return false;
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
  public void sendSearchEntry(SearchOperation searchOperation, SearchResultEntry searchEntry) throws DirectoryException
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
    return null;
  }

  @Override
  public void toString(StringBuilder buffer)
  {
  }

  @Override
  public int getSSF()
  {
    return 0;
  }

}
