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
package org.opends.server.replication.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opends.messages.ReplicationMessages.ERR_DS_BADLY_DISCONNECTED;
import static org.opends.server.TestCaseUtils.TEST_ROOT_DN_STRING;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.net.ssl.SSLSocket;

import org.forgerock.opendj.ldap.DN;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.protocol.ProtocolVersion;
import org.opends.server.replication.protocol.Session;
import org.opends.server.replication.protocol.StopMsg;
import org.testng.annotations.Test;

/** Checks the records emitted when a replication server reader loses its peer. */
@SuppressWarnings("javadoc")
public class ServerReaderDisconnectTest extends ReplicationTestCase
{
  @Test
  public void eofFromModernPeerIsReported() throws Exception
  {
    assertThat(records(new byte[0], ProtocolVersion.REPLICATION_PROTOCOL_V4, false, false))
        .filteredOn(s -> s.contains("category=SYNC severity=ERROR msgID=211 "))
        .hasSize(1);
  }

  @Test
  public void truncatedFrameFromModernPeerIsReported() throws Exception
  {
    assertThat(records("00000004x".getBytes(StandardCharsets.US_ASCII),
        ProtocolVersion.REPLICATION_PROTOCOL_V4, false, false))
        .filteredOn(s -> s.contains("category=SYNC severity=ERROR msgID=211 "))
        .hasSize(1);
  }

  @Test
  public void eofFromOldPeerKeepsNotice() throws Exception
  {
    final List<String> logged = records(new byte[0], ProtocolVersion.REPLICATION_PROTOCOL_V3, false, false);
    assertThat(logged).filteredOn(s -> s.contains("category=SYNC severity=ERROR msgID=211 ")).isEmpty();
    assertThat(logged).filteredOn(s -> s.contains("category=SYNC severity=NOTICE msgID=53 ")).hasSize(1);
  }

  @Test
  public void eofDuringShutdownIsNotReportedAsBadDisconnection() throws Exception
  {
    assertThat(records(new byte[0], ProtocolVersion.REPLICATION_PROTOCOL_V4, true, false))
        .filteredOn(s -> s.contains("category=SYNC severity=ERROR msgID=211 ")).isEmpty();
  }

  @Test
  public void eofAfterLocalCloseIsNotReportedAsBadDisconnection() throws Exception
  {
    assertThat(records(new byte[0], ProtocolVersion.REPLICATION_PROTOCOL_V4, false, true))
        .filteredOn(s -> s.contains("category=SYNC severity=ERROR msgID=211 ")).isEmpty();
  }

  @Test
  public void stopMessageIsNotReportedAsBadDisconnection() throws Exception
  {
    final byte[] message = new StopMsg().getBytes(ProtocolVersion.REPLICATION_PROTOCOL_V4);
    final ByteArrayOutputStream frame = new ByteArrayOutputStream();
    frame.write(String.format("%08x", message.length).getBytes(StandardCharsets.US_ASCII));
    frame.write(message);
    assertThat(records(frame.toByteArray(), ProtocolVersion.REPLICATION_PROTOCOL_V4, false, false))
        .filteredOn(s -> s.contains("category=SYNC severity=ERROR msgID=211 ")).isEmpty();
  }

  private List<String> records(final byte[] input, final short version, final boolean shuttingDown,
      final boolean closeFirst) throws Exception
  {
    try (ServerSocket listener = new ServerSocket(0);
         Socket peer = new Socket("127.0.0.1", listener.getLocalPort());
         Socket socket = listener.accept())
    {
      final SSLSocket secure = new TestSocket(input);
      final Session session = new Session(socket, secure);
      session.setProtocolVersion(version);
      final DataServerHandler handler = mock(DataServerHandler.class);
      final ReplicationServer server = mock(ReplicationServer.class);
      when(server.getServerId()).thenReturn(9301);
      final java.lang.reflect.Field field = MessageHandler.class.getDeclaredField("replicationServer");
      field.setAccessible(true);
      field.set(handler, server);
      when(handler.shuttingDown()).thenReturn(shuttingDown);
      when(handler.getBadlyDisconnectedErrorMessage()).thenReturn(ERR_DS_BADLY_DISCONNECTED.get(
          9301, 9311, session.getReadableRemoteAddress(), DN.valueOf(TEST_ROOT_DN_STRING)));
      if (closeFirst)
      {
        session.close();
      }
      try
      {
        return errorLogRecordsOf(() -> {
          new ServerReader(session, handler).run();
          return null;
        });
      }
      finally
      {
        session.close();
      }
    }
  }

  private static final class TestSocket extends SSLSocket
  {
    private final InputStream input;
    private final OutputStream output = new ByteArrayOutputStream();

    TestSocket(final byte[] bytes) { input = new ByteArrayInputStream(bytes); }
    @Override public InputStream getInputStream() { return input; }
    @Override public OutputStream getOutputStream() { return output; }
    @Override public String[] getSupportedCipherSuites() { return new String[0]; }
    @Override public String[] getEnabledCipherSuites() { return new String[0]; }
    @Override public void setEnabledCipherSuites(String[] suites) { }
    @Override public String[] getSupportedProtocols() { return new String[0]; }
    @Override public String[] getEnabledProtocols() { return new String[0]; }
    @Override public void setEnabledProtocols(String[] protocols) { }
    @Override public javax.net.ssl.SSLSession getSession() { return null; }
    @Override public void addHandshakeCompletedListener(javax.net.ssl.HandshakeCompletedListener listener) { }
    @Override public void removeHandshakeCompletedListener(javax.net.ssl.HandshakeCompletedListener listener) { }
    @Override public void startHandshake() throws IOException { }
    @Override public void setUseClientMode(boolean mode) { }
    @Override public boolean getUseClientMode() { return false; }
    @Override public void setNeedClientAuth(boolean need) { }
    @Override public boolean getNeedClientAuth() { return false; }
    @Override public void setWantClientAuth(boolean want) { }
    @Override public boolean getWantClientAuth() { return false; }
    @Override public void setEnableSessionCreation(boolean flag) { }
    @Override public boolean getEnableSessionCreation() { return false; }
  }
}
