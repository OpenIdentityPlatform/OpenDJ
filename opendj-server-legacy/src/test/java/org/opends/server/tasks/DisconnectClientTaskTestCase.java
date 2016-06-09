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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.tasks;

import static org.opends.server.util.ServerConstants.*;
import static org.testng.Assert.*;

import java.io.IOException;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.LdapException;
import org.opends.server.TestCaseUtils;
import org.opends.server.extensions.GetConnectionIDExtendedOperation;
import org.opends.server.protocols.ldap.ExtendedResponseProtocolOp;
import org.opends.server.protocols.ldap.LDAPConstants;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.tools.RemoteConnection;
import org.opends.server.types.LDAPException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/** Tests the disconnect client task. */
public class DisconnectClientTaskTestCase
       extends TasksTestCase
{
  /**
   * Make sure that the Directory Server is running.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();
  }



  /**
   * Tests the ability of the server to disconnect an arbitrary client
   * connection with a notice of disconnection.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDisconnectWithNotification()
         throws Exception
  {
    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      conn.bind("cn=Directory Manager", "password");

      String disconnectMessage = "testDisconnectWithNotification";
      DN taskDN = invokeClientDisconnectTask(conn, disconnectMessage);
      waitTaskCompletedSuccessfully(taskDN);

      // Make sure that we get a notice of disconnection on the initial connection.
      LDAPMessage message = conn.readMessage();
      ExtendedResponseProtocolOp extendedResponse = message.getExtendedResponseProtocolOp();
      assertEquals(extendedResponse.getOID(), LDAPConstants.OID_NOTICE_OF_DISCONNECTION);
      assertEquals(extendedResponse.getErrorMessage(), LocalizableMessage.raw(disconnectMessage));
    }
  }

  /**
   * Tests the ability of the server to disconnect an arbitrary client
   * connection without a notice of disconnection.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDisconnectWithoutNotification()
         throws Exception
  {
    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      conn.bind("cn=Directory Manager", "password");

      DN taskDN = invokeClientDisconnectTask(conn, null);
      waitTaskCompletedSuccessfully(taskDN);

      // Make sure that the client connection has been closed with no notice of disconnection.
      try
      {
        conn.readMessage();
        fail("Expected IOException");
      }
      catch (IOException expected) { /* nothing to do */ }
    }
  }

  private DN invokeClientDisconnectTask(RemoteConnection conn, String disconnectMessage) throws Exception
  {
    long connectionID = getConnectionID(conn);
    String taskID = "Disconnect Client " + connectionID;
    DN taskDN = DN.valueOf("ds-task-id=" + taskID + ",cn=Scheduled Tasks,cn=Tasks");
    if (disconnectMessage != null)
    {
      TestCaseUtils.addEntry(
          "dn: " + taskDN,
          "objectClass: top",
          "objectClass: ds-task",
          "objectClass: ds-task-disconnect",
          "ds-task-id: " + taskID,
          "ds-task-class-name: org.opends.server.tasks.DisconnectClientTask",
          "ds-task-disconnect-connection-id: " + connectionID,
          "ds-task-disconnect-notify-client: true",
          "ds-task-disconnect-message: " + disconnectMessage);
    }
    else
    {
      TestCaseUtils.addEntry(
          "dn: " + taskDN,
          "objectClass: top",
          "objectClass: ds-task",
          "objectClass: ds-task-disconnect",
          "ds-task-id: " + taskID,
          "ds-task-class-name: org.opends.server.tasks.DisconnectClientTask",
          "ds-task-disconnect-connection-id: " + connectionID,
          "ds-task-disconnect-notify-client: false");
    }
    return taskDN;
  }

  private long getConnectionID(RemoteConnection conn) throws IOException, LDAPException, LdapException, DecodeException
  {
    LDAPMessage message = conn.extendedRequest(OID_GET_CONNECTION_ID_EXTOP);

    ExtendedResponseProtocolOp extendedResponse = message.getExtendedResponseProtocolOp();
    assertEquals(extendedResponse.getResultCode(), LDAPResultCode.SUCCESS);
    assertEquals(extendedResponse.getOID(), OID_GET_CONNECTION_ID_EXTOP);
    return GetConnectionIDExtendedOperation.decodeResponseValue(extendedResponse.getValue());
  }
}
