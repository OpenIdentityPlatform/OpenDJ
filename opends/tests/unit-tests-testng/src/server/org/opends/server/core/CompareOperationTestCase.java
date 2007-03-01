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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */

package org.opends.server.core;

import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Reader;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.protocols.ldap.*;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.Control;
import org.opends.server.types.ResultCode;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.LockManager;
import org.opends.server.TestCaseUtils;
import org.opends.server.util.ServerConstants;
import org.opends.server.controls.LDAPAssertionRequestControl;
import org.opends.server.controls.ProxiedAuthV1Control;
import org.opends.server.controls.ProxiedAuthV2Control;
import org.opends.server.plugins.InvocationCounterPlugin;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import static org.testng.Assert.*;
import static org.testng.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.net.Socket;

public class CompareOperationTestCase extends OperationTestCase
{
  private Entry entry;
  private InternalClientConnection proxyUserConn;


  @BeforeClass
  public void setUp() throws Exception
  {
    TestCaseUtils.startServer();
    TestCaseUtils.initializeTestBackend(true);

    InternalClientConnection connection =
         InternalClientConnection.getRootConnection();

    // Add a test entry.
    entry = TestCaseUtils.makeEntry(
         "dn: uid=rogasawara,o=test",
         "userpassword: password",
         "objectclass: top",
         "objectclass: person",
         "objectclass: organizationalPerson",
         "objectclass: inetOrgPerson",
         "uid: rogasawara",
         "mail: rogasawara@airius.co.jp",
         "givenname;lang-ja:: 44Ot44OJ44OL44O8",
         "sn;lang-ja:: 5bCP56yg5Y6f",
         "cn;lang-ja:: 5bCP56yg5Y6fIOODreODieODi+ODvA==",
         "title;lang-ja:: 5Za25qWt6YOoIOmDqOmVtw==",
         "preferredlanguage: ja",
         "givenname:: 44Ot44OJ44OL44O8",
         "sn:: 5bCP56yg5Y6f",
         "cn:: 5bCP56yg5Y6fIOODreODieODi+ODvA==",
         "title:: 5Za25qWt6YOoIOmDqOmVtw==",
         "givenname;lang-ja;phonetic:: 44KN44Gp44Gr44O8",
         "sn;lang-ja;phonetic:: 44GK44GM44GV44KP44KJ",
         "cn;lang-ja;phonetic:: 44GK44GM44GV44KP44KJIOOCjeOBqeOBq+ODvA==",
         "title;lang-ja;phonetic:: " +
              "44GI44GE44GO44KH44GG44G2IOOBtuOBoeOCh+OBhg==",
         "givenname;lang-en: Rodney",
         "sn;lang-en: Ogasawara",
         "cn;lang-en: Rodney Ogasawara",
         "title;lang-en: Sales, Director"
    );
    AddOperation addOperation =
         connection.processAdd(entry.getDN(),
                               entry.getObjectClasses(),
                               entry.getUserAttributes(),
                               entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
    assertNotNull(DirectoryServer.getEntry(entry.getDN()));

    // Add a user capable of using the proxied authorization control.
    TestCaseUtils.addEntry(
         "dn: uid=proxy.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: proxy.user",
         "givenName: Proxy",
         "sn: User",
         "cn: Proxy User",
         "userPassword: password",
         "ds-privilege-name: bypass-acl",
         "ds-privilege-name: proxied-auth");

    Entry proxyUserEntry =
               DirectoryServer.getEntry(DN.decode("uid=proxy.user,o=test"));
    AuthenticationInfo authInfo = new AuthenticationInfo(proxyUserEntry, false);
    proxyUserConn = new InternalClientConnection(authInfo);
  }


  public Operation[] createTestOperations() throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    return new Operation[]
    {
      new CompareOperation(conn, InternalClientConnection.nextOperationID(),
                           InternalClientConnection.nextMessageID(),
                           new ArrayList<Control>(),
                           new ASN1OctetString(entry.getDN().toString()),
                           "uid", new ASN1OctetString("rogasawara"))
    };
  }

  /**
   * Invokes a number of operation methods on the provided compare operation
   * for which all processing has been completed.
   *
   * @param  compareOperation  The operation to be tested.
   */
  private void examineCompletedOperation(CompareOperation compareOperation)
  {
    assertTrue(compareOperation.getProcessingStartTime() > 0);
    assertTrue(compareOperation.getProcessingStopTime() > 0);
    assertTrue(compareOperation.getProcessingTime() >= 0);
    assertNotNull(compareOperation.getResponseLogElements());

    assertEquals(InvocationCounterPlugin.getPreParseCount(), 1);
    assertEquals(InvocationCounterPlugin.getPreOperationCount(), 1);
    assertEquals(InvocationCounterPlugin.getPostOperationCount(), 1);
    assertEquals(InvocationCounterPlugin.getPostResponseCount(), 1);
  }

  /**
   * Invokes a number of operation methods on the provided compare operation
   * for which the pre-operation plugin was not called.
   *
   * @param  compareOperation  The operation to be tested.
   */
  private void examineIncompleteOperation(CompareOperation compareOperation)
  {
    assertTrue(compareOperation.getProcessingStartTime() > 0);
    assertTrue(compareOperation.getProcessingStopTime() > 0);
    assertTrue(compareOperation.getProcessingTime() >= 0);
    assertNotNull(compareOperation.getResponseLogElements());
    assertTrue(compareOperation.getErrorMessage().length() > 0);

    assertEquals(InvocationCounterPlugin.getPreParseCount(), 1);
    assertEquals(InvocationCounterPlugin.getPreOperationCount(), 0);
    assertEquals(InvocationCounterPlugin.getPostOperationCount(), 1);
    assertEquals(InvocationCounterPlugin.getPostResponseCount(), 1);
  }

  /**
   * Invokes a number of operation methods on the provided compare operation
   * for which an error was found during parsing.
   *
   * @param  compareOperation  The operation to be tested.
   */
  private void examineUnparsedOperation(CompareOperation compareOperation)
  {
    assertTrue(compareOperation.getProcessingStartTime() > 0);
    assertTrue(compareOperation.getProcessingStopTime() > 0);
    assertTrue(compareOperation.getProcessingTime() >= 0);
    assertNotNull(compareOperation.getResponseLogElements());
    assertTrue(compareOperation.getErrorMessage().length() > 0);

    assertEquals(InvocationCounterPlugin.getPreParseCount(), 1);
    assertEquals(InvocationCounterPlugin.getPreOperationCount(), 0);
    assertEquals(InvocationCounterPlugin.getPostOperationCount(), 0);
    assertEquals(InvocationCounterPlugin.getPostResponseCount(), 1);
  }

  @Test
  public void testCompareTrue()
  {
    InvocationCounterPlugin.resetAllCounters();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    CompareOperation compareOperation =
         new CompareOperation(conn, InternalClientConnection.nextOperationID(),
                              InternalClientConnection.nextMessageID(),
                              new ArrayList<Control>(),
                              new ASN1OctetString(entry.getDN().toString()),
                              "uid", new ASN1OctetString("rogasawara"));

    compareOperation.run();
    assertEquals(compareOperation.getResultCode(),
                 ResultCode.COMPARE_TRUE);
    assertEquals(compareOperation.getErrorMessage().length(), 0);

    examineCompletedOperation(compareOperation);
  }


  @Test
  public void testCompareFalse()
  {
    InvocationCounterPlugin.resetAllCounters();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    CompareOperation compareOperation =
         new CompareOperation(conn, InternalClientConnection.nextOperationID(),
                              InternalClientConnection.nextMessageID(),
                              new ArrayList<Control>(),
                              new ASN1OctetString(entry.getDN().toString()),
                              "uid", new ASN1OctetString("rogasawala"));

    compareOperation.run();
    assertEquals(compareOperation.getResultCode(),
                 ResultCode.COMPARE_FALSE);

    assertEquals(compareOperation.getErrorMessage().length(), 0);
    examineCompletedOperation(compareOperation);
  }

  @Test
  public void testCompareEntryNonexistent()
  {
    InvocationCounterPlugin.resetAllCounters();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    CompareOperation compareOperation =
         new CompareOperation(conn, InternalClientConnection.nextOperationID(),
                              InternalClientConnection.nextMessageID(),
                              new ArrayList<Control>(),
                              new ASN1OctetString("o=nonexistent,o=test"),
                              "o", new ASN1OctetString("nonexistent"));

    compareOperation.run();
    assertEquals(compareOperation.getResultCode(),
                 ResultCode.NO_SUCH_OBJECT);

    examineIncompleteOperation(compareOperation);
  }

  @Test
  public void testCompareInvalidDn()
  {
    InvocationCounterPlugin.resetAllCounters();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    CompareOperation compareOperation =
         new CompareOperation(conn, InternalClientConnection.nextOperationID(),
                              InternalClientConnection.nextMessageID(),
                              new ArrayList<Control>(),
                              new ASN1OctetString("rogasawara,o=test"),
                              "uid", new ASN1OctetString("rogasawara"));

    compareOperation.run();
    assertEquals(compareOperation.getResultCode(),
                 ResultCode.INVALID_DN_SYNTAX);

    examineUnparsedOperation(compareOperation);
  }

  @Test
  public void testCompareNoSuchAttribute()
  {
    InvocationCounterPlugin.resetAllCounters();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    CompareOperation compareOperation =
         new CompareOperation(conn, InternalClientConnection.nextOperationID(),
                              InternalClientConnection.nextMessageID(),
                              new ArrayList<Control>(),
                              new ASN1OctetString(entry.getDN().toString()),
                              "description", new ASN1OctetString("rogasawara"));

    compareOperation.run();
    assertEquals(compareOperation.getResultCode(),
                 ResultCode.NO_SUCH_ATTRIBUTE);

    assertTrue(compareOperation.getErrorMessage().length() > 0);
    examineCompletedOperation(compareOperation);
  }

  @Test
  public void testCompareUndefinedAttribute()
  {
    InvocationCounterPlugin.resetAllCounters();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    CompareOperation compareOperation =
         new CompareOperation(conn, InternalClientConnection.nextOperationID(),
                              InternalClientConnection.nextMessageID(),
                              new ArrayList<Control>(),
                              new ASN1OctetString(entry.getDN().toString()),
                              "NotAnAttribute",
                              new ASN1OctetString("rogasawara"));

    compareOperation.run();
    assertEquals(compareOperation.getResultCode(),
                 ResultCode.NO_SUCH_ATTRIBUTE);

    assertTrue(compareOperation.getErrorMessage().length() > 0);
    examineCompletedOperation(compareOperation);
  }

  @Test
  public void testCompareSubtype()
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    CompareOperation compareOperation =
         new CompareOperation(conn, InternalClientConnection.nextOperationID(),
                              InternalClientConnection.nextMessageID(),
                              new ArrayList<Control>(),
                              new ASN1OctetString(entry.getDN().toString()),
                              "name",
                              new ASN1OctetString("Ogasawara"));

    compareOperation.run();
    assertEquals(compareOperation.getResultCode(),
                 ResultCode.COMPARE_TRUE);
  }

  @Test
  public void testCompareOptions1()
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    CompareOperation compareOperation =
         new CompareOperation(conn, InternalClientConnection.nextOperationID(),
                              InternalClientConnection.nextMessageID(),
                              new ArrayList<Control>(),
                              new ASN1OctetString(entry.getDN().toString()),
                              "sn",
                              new ASN1OctetString("Ogasawara"));

    compareOperation.run();
    assertEquals(compareOperation.getResultCode(),
                 ResultCode.COMPARE_TRUE);
  }

  @Test
  public void testCompareOptions2()
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    CompareOperation compareOperation =
         new CompareOperation(conn, InternalClientConnection.nextOperationID(),
                              InternalClientConnection.nextMessageID(),
                              new ArrayList<Control>(),
                              new ASN1OctetString(entry.getDN().toString()),
                              "sn;lang-ja",
                              new ASN1OctetString("Ogasawara"));

    compareOperation.run();
    assertEquals(compareOperation.getResultCode(),
                 ResultCode.COMPARE_FALSE);
  }

  @Test
  public void testCompareTrueAssertion() throws Exception
  {
    InvocationCounterPlugin.resetAllCounters();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    LDAPFilter ldapFilter = LDAPFilter.decode("(preferredlanguage=ja)");
    LDAPAssertionRequestControl assertControl =
         new LDAPAssertionRequestControl(true, ldapFilter);
    List<Control> controls = new ArrayList<Control>();
    controls.add(assertControl);

    CompareOperation compareOperation =
         new CompareOperation(conn, InternalClientConnection.nextOperationID(),
                              InternalClientConnection.nextMessageID(),
                              controls,
                              new ASN1OctetString(entry.getDN().toString()),
                              "uid", new ASN1OctetString("rogasawara"));

    compareOperation.run();
    assertEquals(compareOperation.getResultCode(),
                 ResultCode.COMPARE_TRUE);

    assertEquals(compareOperation.getErrorMessage().length(), 0);
    examineCompletedOperation(compareOperation);
  }

  @Test
  public void testCompareAssertionFailed() throws Exception
  {
    InvocationCounterPlugin.resetAllCounters();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    LDAPFilter ldapFilter = LDAPFilter.decode("(preferredlanguage=en)");
    LDAPAssertionRequestControl assertControl =
         new LDAPAssertionRequestControl(true, ldapFilter);
    List<Control> controls = new ArrayList<Control>();
    controls.add(assertControl);

    CompareOperation compareOperation =
         new CompareOperation(conn, InternalClientConnection.nextOperationID(),
                              InternalClientConnection.nextMessageID(),
                              controls,
                              new ASN1OctetString(entry.getDN().toString()),
                              "uid", new ASN1OctetString("rogasawara"));

    compareOperation.run();
    assertEquals(compareOperation.getResultCode(),
                 ResultCode.ASSERTION_FAILED);

    examineIncompleteOperation(compareOperation);
  }

  @Test
  public void testCompareProxiedAuthV1() throws Exception
  {
    InvocationCounterPlugin.resetAllCounters();

    ProxiedAuthV1Control authV1Control =
         new ProxiedAuthV1Control(new ASN1OctetString());
    List<Control> controls = new ArrayList<Control>();
    controls.add(authV1Control);

    CompareOperation compareOperation =
         new CompareOperation(proxyUserConn,
                              InternalClientConnection.nextOperationID(),
                              InternalClientConnection.nextMessageID(),
                              controls,
                              new ASN1OctetString(entry.getDN().toString()),
                              "uid", new ASN1OctetString("rogasawara"));

    compareOperation.run();
    assertEquals(compareOperation.getResultCode(),
                 ResultCode.COMPARE_TRUE);

    assertEquals(compareOperation.getErrorMessage().length(), 0);
    examineCompletedOperation(compareOperation);
  }

  @Test
  public void testCompareProxiedAuthV1Denied() throws Exception
  {


    InvocationCounterPlugin.resetAllCounters();

    ProxiedAuthV1Control authV1Control =
         new ProxiedAuthV1Control(new ASN1OctetString("cn=nonexistent,o=test"));
    List<Control> controls = new ArrayList<Control>();
    controls.add(authV1Control);

    CompareOperation compareOperation =
         new CompareOperation(proxyUserConn,
                              InternalClientConnection.nextOperationID(),
                              InternalClientConnection.nextMessageID(),
                              controls,
                              new ASN1OctetString(entry.getDN().toString()),
                              "uid", new ASN1OctetString("rogasawara"));

    compareOperation.run();
    assertEquals(compareOperation.getResultCode(),
                 ResultCode.AUTHORIZATION_DENIED);

    examineIncompleteOperation(compareOperation);
  }

  @Test
  public void testCompareProxiedAuthV2() throws Exception
  {
    InvocationCounterPlugin.resetAllCounters();

    ProxiedAuthV2Control authV2Control =
         new ProxiedAuthV2Control(new ASN1OctetString("dn:"));
    List<Control> controls = new ArrayList<Control>();
    controls.add(authV2Control);

    CompareOperation compareOperation =
         new CompareOperation(proxyUserConn,
                              InternalClientConnection.nextOperationID(),
                              InternalClientConnection.nextMessageID(),
                              controls,
                              new ASN1OctetString(entry.getDN().toString()),
                              "uid", new ASN1OctetString("rogasawara"));

    compareOperation.run();
    assertEquals(compareOperation.getResultCode(),
                 ResultCode.COMPARE_TRUE);

    assertEquals(compareOperation.getErrorMessage().length(), 0);
    examineCompletedOperation(compareOperation);
  }

  @Test
  public void testCompareProxiedAuthV2Denied() throws Exception
  {
    InvocationCounterPlugin.resetAllCounters();

    ProxiedAuthV2Control authV2Control = new ProxiedAuthV2Control(
         new ASN1OctetString("dn:cn=nonexistent,o=test"));
    List<Control> controls = new ArrayList<Control>();
    controls.add(authV2Control);

    CompareOperation compareOperation =
         new CompareOperation(proxyUserConn,
                              InternalClientConnection.nextOperationID(),
                              InternalClientConnection.nextMessageID(),
                              controls,
                              new ASN1OctetString(entry.getDN().toString()),
                              "uid", new ASN1OctetString("rogasawara"));

    compareOperation.run();
    assertEquals(compareOperation.getResultCode(),
                 ResultCode.AUTHORIZATION_DENIED);

    examineIncompleteOperation(compareOperation);
  }

  @Test
  public void testCompareProxiedAuthV2Criticality() throws Exception
  {
    InvocationCounterPlugin.resetAllCounters();

    Control authV2Control =
         new Control(ServerConstants.OID_PROXIED_AUTH_V2, false,
                     new ASN1OctetString());

    List<Control> controls = new ArrayList<Control>();
    controls.add(authV2Control);

    CompareOperation compareOperation =
         new CompareOperation(proxyUserConn,
                              InternalClientConnection.nextOperationID(),
                              InternalClientConnection.nextMessageID(),
                              controls,
                              new ASN1OctetString(entry.getDN().toString()),
                              "uid", new ASN1OctetString("rogasawara"));

    compareOperation.run();
    assertEquals(compareOperation.getResultCode(),
                 ResultCode.PROTOCOL_ERROR);

    examineIncompleteOperation(compareOperation);
  }

  @Test
  public void testCompareUnsupportedControl() throws Exception
  {
    InvocationCounterPlugin.resetAllCounters();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    LDAPFilter ldapFilter = LDAPFilter.decode("(preferredlanguage=ja)");
    LDAPAssertionRequestControl assertControl =
         new LDAPAssertionRequestControl("1.1.1.1.1.1", true, ldapFilter);
    List<Control> controls = new ArrayList<Control>();
    controls.add(assertControl);
    CompareOperation compareOperation =
         new CompareOperation(conn, InternalClientConnection.nextOperationID(),
                              InternalClientConnection.nextMessageID(),
                              controls,
                              new ASN1OctetString(entry.getDN().toString()),
                              "uid", new ASN1OctetString("rogasawara"));

    compareOperation.run();
    assertEquals(compareOperation.getResultCode(),
                 ResultCode.UNAVAILABLE_CRITICAL_EXTENSION);

    examineIncompleteOperation(compareOperation);
  }

  @Test(groups = "slow")
  public void testCompareWriteLock() throws Exception
  {
    // We need the operation to be run in a separate thread because we are going
    // to write lock the entry in the test case thread and check that the
    // compare operation does not proceed.

    // Establish a connection to the server.
    Socket s = new Socket("127.0.0.1", (int) TestCaseUtils.getServerLdapPort());
    try
    {
      ASN1Reader r = new ASN1Reader(s);
      ASN1Writer w = new ASN1Writer(s);
      r.setIOTimeout(15000);

      BindRequestProtocolOp bindRequest =
           new BindRequestProtocolOp(
                new ASN1OctetString("cn=Directory Manager"),
                3, new ASN1OctetString("password"));
      LDAPMessage message = new LDAPMessage(1, bindRequest);
      w.writeElement(message.encode());

      message = LDAPMessage.decode(r.readElement().decodeAsSequence());
      BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
      assertEquals(bindResponse.getResultCode(), LDAPResultCode.SUCCESS);

      // Since we are going to be watching the post-response count, we need to
      // wait for the server to become idle before kicking off the next request
      // to ensure that any remaining post-response processing from the previous
      // operation has completed.
      assertTrue(DirectoryServer.getWorkQueue().waitUntilIdle(10000));


      Lock writeLock = LockManager.lockWrite(entry.getDN());
      assertNotNull(writeLock);

      try
      {
        InvocationCounterPlugin.resetAllCounters();

        long compareRequests  = ldapStatistics.getCompareRequests();
        long compareResponses = ldapStatistics.getCompareResponses();

        CompareRequestProtocolOp compareRequest =
          new CompareRequestProtocolOp(
               new ASN1OctetString(entry.getDN().toString()),
               "uid", new ASN1OctetString("rogasawara"));
        message = new LDAPMessage(2, compareRequest);
        w.writeElement(message.encode());

        message = LDAPMessage.decode(r.readElement().decodeAsSequence());
        CompareResponseProtocolOp compareResponse =
             message.getCompareResponseProtocolOp();

        assertEquals(compareResponse.getResultCode(),
                     DirectoryServer.getServerErrorResultCode().getIntValue());

        assertEquals(InvocationCounterPlugin.getPreParseCount(), 1);
        assertEquals(InvocationCounterPlugin.getPreOperationCount(), 0);
        assertEquals(InvocationCounterPlugin.getPostOperationCount(), 0);
        // The post response might not have been called yet.
        assertEquals(InvocationCounterPlugin.waitForPostResponse(), 1);

        assertEquals(ldapStatistics.getCompareRequests(), compareRequests+1);
        assertEquals(ldapStatistics.getCompareResponses(), compareResponses+1);
      } finally
      {
        LockManager.unlock(entry.getDN(), writeLock);
      }
    } finally
    {
      s.close();
    }

  }

}
