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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.tasks;



import java.net.Socket;

import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;

import org.opends.server.TestCaseUtils;
import org.opends.messages.Message;
import org.opends.server.backends.task.Task;
import org.opends.server.backends.task.TaskBackend;
import org.opends.server.backends.task.TaskState;
import org.opends.server.core.DirectoryServer;
import org.opends.server.extensions.GetConnectionIDExtendedOperation;
import org.opends.server.protocols.asn1.*;
import org.opends.server.protocols.ldap.*;
import org.opends.server.types.DN;

import static org.testng.Assert.*;

import static org.opends.server.util.ServerConstants.*;



/**
 * Tests the disconnect client task.
 */
public class DisconnectClientTaskTestCase
       extends TasksTestCase
{
  /**
   * Make sure that the Directory Server is running.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass()
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
  @Test()
  public void testDisconnectWithNotification()
         throws Exception
  {
    // Establish a connection to the server, bind, and get the connection ID.
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(new ASN1OctetString("cn=Directory Manager"),
                                   3, new ASN1OctetString("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeElement(message.encode());

    message = LDAPMessage.decode(r.readElement().decodeAsSequence());
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), LDAPResultCode.SUCCESS);


    ExtendedRequestProtocolOp extendedRequest =
         new ExtendedRequestProtocolOp(OID_GET_CONNECTION_ID_EXTOP);
    message = new LDAPMessage(2, extendedRequest);
    w.writeElement(message.encode());

    message = LDAPMessage.decode(r.readElement().decodeAsSequence());
    ExtendedResponseProtocolOp extendedResponse =
         message.getExtendedResponseProtocolOp();
    assertEquals(extendedResponse.getResultCode(), LDAPResultCode.SUCCESS);
    assertEquals(extendedResponse.getOID(), OID_GET_CONNECTION_ID_EXTOP);
    long connectionID = GetConnectionIDExtendedOperation.decodeResponseValue(
                             extendedResponse.getValue());


    // Invoke the disconnect client task.
    String taskID = "Disconnect Client " + connectionID;
    Message disconnectMessage = Message.raw("testDisconnectWithNotification");
    DN taskDN = DN.decode("ds-task-id=" + taskID +
                          ",cn=Scheduled Tasks,cn=Tasks");
    TestCaseUtils.addEntry(
      "dn: " + taskDN.toString(),
      "objectClass: top",
      "objectClass: ds-task",
      "objectClass: ds-task-disconnect",
      "ds-task-id: " + taskID,
      "ds-task-class-name: org.opends.server.tasks.DisconnectClientTask",
      "ds-task-disconnect-connection-id: " + connectionID,
      "ds-task-disconnect-notify-client: true",
      "ds-task-disconnect-message: " + disconnectMessage);

    Task task = getCompletedTask(taskDN);
    assertNotNull(task);
    assertEquals(task.getTaskState(), TaskState.COMPLETED_SUCCESSFULLY);


    // Make sure that we get a notice of disconnection on the initial
    // connection.
    message = LDAPMessage.decode(r.readElement().decodeAsSequence());
    extendedResponse = message.getExtendedResponseProtocolOp();
    assertEquals(extendedResponse.getOID(),
                 LDAPConstants.OID_NOTICE_OF_DISCONNECTION);
    assertEquals(extendedResponse.getErrorMessage(), disconnectMessage);

    try
    {
      s.close();
    } catch (Exception e) {}
  }



  /**
   * Tests the ability of the server to disconnect an arbitrary client
   * connection without a notice of disconnection.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDisconnectWithoutNotification()
         throws Exception
  {
    // Establish a connection to the server, bind, and get the connection ID.
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(new ASN1OctetString("cn=Directory Manager"),
                                   3, new ASN1OctetString("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeElement(message.encode());

    message = LDAPMessage.decode(r.readElement().decodeAsSequence());
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), LDAPResultCode.SUCCESS);


    ExtendedRequestProtocolOp extendedRequest =
         new ExtendedRequestProtocolOp(OID_GET_CONNECTION_ID_EXTOP);
    message = new LDAPMessage(2, extendedRequest);
    w.writeElement(message.encode());

    message = LDAPMessage.decode(r.readElement().decodeAsSequence());
    ExtendedResponseProtocolOp extendedResponse =
         message.getExtendedResponseProtocolOp();
    assertEquals(extendedResponse.getResultCode(), LDAPResultCode.SUCCESS);
    assertEquals(extendedResponse.getOID(), OID_GET_CONNECTION_ID_EXTOP);
    long connectionID = GetConnectionIDExtendedOperation.decodeResponseValue(
                             extendedResponse.getValue());


    // Invoke the disconnect client task.
    String taskID = "Disconnect Client " + connectionID;
    DN taskDN = DN.decode("ds-task-id=" + taskID +
                          ",cn=Scheduled Tasks,cn=Tasks");
    TestCaseUtils.addEntry(
      "dn: " + taskDN.toString(),
      "objectClass: top",
      "objectClass: ds-task",
      "objectClass: ds-task-disconnect",
      "ds-task-id: " + taskID,
      "ds-task-class-name: org.opends.server.tasks.DisconnectClientTask",
      "ds-task-disconnect-connection-id: " + connectionID,
      "ds-task-disconnect-notify-client: false");

    Task task = getCompletedTask(taskDN);
    assertNotNull(task);
    assertEquals(task.getTaskState(), TaskState.COMPLETED_SUCCESSFULLY);


    // Make sure that the client connection has been closed with no notice of
    // disconnection.
    assertNull(r.readElement());

    try
    {
      s.close();
    } catch (Exception e) {}
  }
}

