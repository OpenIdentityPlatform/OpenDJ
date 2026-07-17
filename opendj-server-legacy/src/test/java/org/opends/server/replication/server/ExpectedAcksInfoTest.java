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
 * Portions Copyright 2026 3A Systems, LLC
 */
package org.opends.server.replication.server;

import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.opends.server.DirectoryServerTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.protocol.AckMsg;
import org.testng.annotations.Test;

/**
 * Test the handling of acks received from servers that were not expected to
 * acknowledge an assured update.
 * <p>
 * A peer served through the changelog catch-up path receives the update with
 * the assured flag of the original sender (see issue #710), so it may send
 * back an ack for a server id that is not part of the expected servers of the
 * matching {@link ExpectedAcksInfo}. That ack must be ignored instead of
 * raising a {@link NullPointerException} while auto-unboxing the missing map
 * entry, which would close the connection to the acking peer.
 */
@SuppressWarnings("javadoc")
public class ExpectedAcksInfoTest extends DirectoryServerTestCase
{
  private static final CSN CSN1 = new CSN(1, 1, 10);

  private static ServerHandler mockServerHandler(int serverId, boolean isDataServer)
  {
    ServerHandler handler = mock(ServerHandler.class);
    when(handler.getServerId()).thenReturn(serverId);
    when(handler.isDataServer()).thenReturn(isDataServer);
    return handler;
  }

  @Test
  public void safeReadAckFromNotExpectedServerIsIgnored()
  {
    ServerHandler requester = mockServerHandler(1, true);
    List<Integer> expectedServers = Arrays.asList(2, 3);
    SafeReadExpectedAcksInfo acksInfo = new SafeReadExpectedAcksInfo(
        CSN1, requester, expectedServers, Collections.<Integer> emptyList());

    // Server id 99 is not in the expected servers list
    ServerHandler notExpected = mockServerHandler(99, false);
    assertFalse(acksInfo.processReceivedAck(notExpected, new AckMsg(CSN1)),
        "An ack from a not expected server must not complete the ack info");
    assertFalse(acksInfo.isExpectedServer(99));
    assertTrue(acksInfo.isExpectedServer(2));
  }

  @Test
  public void safeDataAckFromNotExpectedServerIsIgnored()
  {
    ServerHandler requester = mockServerHandler(1, true);
    List<Integer> expectedServers = Arrays.asList(2, 3);
    SafeDataExpectedAcksInfo acksInfo = new SafeDataExpectedAcksInfo(
        CSN1, requester, (byte) 3, expectedServers);

    // Server id 99 is a RS not in the expected servers list
    ServerHandler notExpected = mockServerHandler(99, false);
    assertFalse(acksInfo.processReceivedAck(notExpected, new AckMsg(CSN1)),
        "An ack from a not expected server must not complete the ack info");
    assertFalse(acksInfo.isExpectedServer(99));
    assertTrue(acksInfo.isExpectedServer(3));
  }
}
