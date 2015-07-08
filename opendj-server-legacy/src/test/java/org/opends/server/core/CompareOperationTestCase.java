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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS.
 */

package org.opends.server.core;

import java.util.ArrayList;
import java.util.List;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.TestCaseUtils;
import org.opends.server.controls.LDAPAssertionRequestControl;
import org.opends.server.controls.ProxiedAuthV1Control;
import org.opends.server.controls.ProxiedAuthV2Control;
import org.opends.server.plugins.InvocationCounterPlugin;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.ldap.*;
import org.opends.server.types.*;
import org.opends.server.util.ServerConstants;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

@SuppressWarnings("javadoc")
public class CompareOperationTestCase extends OperationTestCase
{
  private Entry entry;
  private InternalClientConnection proxyUserConn;


  @BeforeClass
  public void setUp() throws Exception
  {
    TestCaseUtils.startServer();
    TestCaseUtils.initializeTestBackend(true);

    // Add a test entry.
    entry = TestCaseUtils.addEntry(
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
         "title;lang-en: Sales, Director",
         "aci: (targetattr=\"*\")(version 3.0; acl \"Proxy Rights\"; " +
              "allow(proxy) userdn=\"ldap:///uid=proxy.user,o=test\";)"
    );

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

    proxyUserConn =
         new InternalClientConnection(DN.valueOf("uid=proxy.user,o=test"));
  }


  @Override
  protected Operation[] createTestOperations() throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    return new Operation[]
    {
      new CompareOperationBasis(
                           conn, InternalClientConnection.nextOperationID(),
                           InternalClientConnection.nextMessageID(),
                           new ArrayList<Control>(),
                           ByteString.valueOf(entry.getName().toString()),
                           "uid", ByteString.valueOf("rogasawara"))
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

//    assertEquals(InvocationCounterPlugin.getPreParseCount(), 1);
//    assertEquals(InvocationCounterPlugin.getPreOperationCount(), 1);
//    assertEquals(InvocationCounterPlugin.getPostOperationCount(), 1);
    ensurePostReponseHasRun();
//    assertEquals(InvocationCounterPlugin.getPostResponseCount(), 1);
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
    assertTrue(compareOperation.getErrorMessage().length() > 0);

//    assertEquals(InvocationCounterPlugin.getPreParseCount(), 1);
//    assertEquals(InvocationCounterPlugin.getPreOperationCount(), 0);
//    assertEquals(InvocationCounterPlugin.getPostOperationCount(), 1);
    ensurePostReponseHasRun();
//    assertEquals(InvocationCounterPlugin.getPostResponseCount(), 1);
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
    assertTrue(compareOperation.getErrorMessage().length() > 0);

//    assertEquals(InvocationCounterPlugin.getPreParseCount(), 1);
//    assertEquals(InvocationCounterPlugin.getPreOperationCount(), 0);
//    assertEquals(InvocationCounterPlugin.getPostOperationCount(), 0);
    ensurePostReponseHasRun();
//    assertEquals(InvocationCounterPlugin.getPostResponseCount(), 1);
  }

  @Test
  public void testCompareTrue()
  {
    InvocationCounterPlugin.resetAllCounters();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    CompareOperationBasis compareOperation =
         new CompareOperationBasis(
                              conn, InternalClientConnection.nextOperationID(),
                              InternalClientConnection.nextMessageID(),
                              new ArrayList<Control>(),
                              ByteString.valueOf(entry.getName().toString()),
                              "uid", ByteString.valueOf("rogasawara"));

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

    CompareOperationBasis compareOperation =
         new CompareOperationBasis(
                              conn, InternalClientConnection.nextOperationID(),
                              InternalClientConnection.nextMessageID(),
                              new ArrayList<Control>(),
                              ByteString.valueOf(entry.getName().toString()),
                              "uid", ByteString.valueOf("rogasawala"));

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

    CompareOperationBasis compareOperation =
         new CompareOperationBasis(
                              conn, InternalClientConnection.nextOperationID(),
                              InternalClientConnection.nextMessageID(),
                              new ArrayList<Control>(),
                              ByteString.valueOf("o=nonexistent,o=test"),
                              "o", ByteString.valueOf("nonexistent"));

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

    CompareOperationBasis compareOperation =
         new CompareOperationBasis(
                              conn, InternalClientConnection.nextOperationID(),
                              InternalClientConnection.nextMessageID(),
                              new ArrayList<Control>(),
                              ByteString.valueOf("rogasawara,o=test"),
                              "uid", ByteString.valueOf("rogasawara"));

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

    CompareOperationBasis compareOperation =
         new CompareOperationBasis(
                              conn, InternalClientConnection.nextOperationID(),
                              InternalClientConnection.nextMessageID(),
                              new ArrayList<Control>(),
                              ByteString.valueOf(entry.getName().toString()),
                              "description", ByteString.valueOf("rogasawara"));

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

    CompareOperationBasis compareOperation =
         new CompareOperationBasis(
                              conn, InternalClientConnection.nextOperationID(),
                              InternalClientConnection.nextMessageID(),
                              new ArrayList<Control>(),
                              ByteString.valueOf(entry.getName().toString()),
                              "NotAnAttribute",
                              ByteString.valueOf("rogasawara"));

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

    CompareOperationBasis compareOperation =
         new CompareOperationBasis(
                              conn, InternalClientConnection.nextOperationID(),
                              InternalClientConnection.nextMessageID(),
                              new ArrayList<Control>(),
                              ByteString.valueOf(entry.getName().toString()),
                              "name",
                              ByteString.valueOf("Ogasawara"));

    compareOperation.run();
    assertEquals(compareOperation.getResultCode(),
                 ResultCode.COMPARE_TRUE);
  }

  @Test
  public void testCompareOptions1()
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    CompareOperationBasis compareOperation =
         new CompareOperationBasis(
                              conn, InternalClientConnection.nextOperationID(),
                              InternalClientConnection.nextMessageID(),
                              new ArrayList<Control>(),
                              ByteString.valueOf(entry.getName().toString()),
                              "sn",
                              ByteString.valueOf("Ogasawara"));

    compareOperation.run();
    assertEquals(compareOperation.getResultCode(),
                 ResultCode.COMPARE_TRUE);
  }

  @Test
  public void testCompareOptions2()
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    CompareOperationBasis compareOperation =
         new CompareOperationBasis(
                              conn, InternalClientConnection.nextOperationID(),
                              InternalClientConnection.nextMessageID(),
                              new ArrayList<Control>(),
                              ByteString.valueOf(entry.getName().toString()),
                              "sn;lang-ja",
                              ByteString.valueOf("Ogasawara"));

    compareOperation.run();
    assertEquals(compareOperation.getResultCode(),
                 ResultCode.COMPARE_FALSE);
  }

  @Test
  public void testCompareOptions3()
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    CompareOperationBasis compareOperation =
         new CompareOperationBasis(
                              conn, InternalClientConnection.nextOperationID(),
                              InternalClientConnection.nextMessageID(),
                              new ArrayList<Control>(),
                              ByteString.valueOf(entry.getName().toString()),
                              "givenName;lAnG-En",
                              ByteString.valueOf("Rodney"));

    compareOperation.run();
    assertEquals(compareOperation.getResultCode(),
                 ResultCode.COMPARE_TRUE);
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
    List<Control> controls = new ArrayList<>();
    controls.add(assertControl);

    CompareOperationBasis compareOperation =
         new CompareOperationBasis(
                              conn, InternalClientConnection.nextOperationID(),
                              InternalClientConnection.nextMessageID(),
                              controls,
                              ByteString.valueOf(entry.getName().toString()),
                              "uid", ByteString.valueOf("rogasawara"));

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
    List<Control> controls = new ArrayList<>();
    controls.add(assertControl);

    CompareOperationBasis compareOperation =
         new CompareOperationBasis(
                              conn, InternalClientConnection.nextOperationID(),
                              InternalClientConnection.nextMessageID(),
                              controls,
                              ByteString.valueOf(entry.getName().toString()),
                              "uid", ByteString.valueOf("rogasawara"));

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
         new ProxiedAuthV1Control(ByteString.valueOf(
              "cn=Directory Manager,cn=Root DNs,cn=config"));
    List<Control> controls = new ArrayList<>();
    controls.add(authV1Control);

    CompareOperationBasis compareOperation =
         new CompareOperationBasis(
                              proxyUserConn,
                              InternalClientConnection.nextOperationID(),
                              InternalClientConnection.nextMessageID(),
                              controls,
                              ByteString.valueOf(entry.getName().toString()),
                              "uid", ByteString.valueOf("rogasawara"));

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
         new ProxiedAuthV1Control(ByteString.valueOf("cn=nonexistent,o=test"));
    List<Control> controls = new ArrayList<>();
    controls.add(authV1Control);

    CompareOperationBasis compareOperation =
         new CompareOperationBasis(
                              proxyUserConn,
                              InternalClientConnection.nextOperationID(),
                              InternalClientConnection.nextMessageID(),
                              controls,
                              ByteString.valueOf(entry.getName().toString()),
                              "uid", ByteString.valueOf("rogasawara"));

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
         new ProxiedAuthV2Control(ByteString.valueOf(
                  "dn:cn=Directory Manager,cn=Root DNs,cn=config"));
    List<Control> controls = new ArrayList<>();
    controls.add(authV2Control);

    CompareOperationBasis compareOperation =
         new CompareOperationBasis(
                              proxyUserConn,
                              InternalClientConnection.nextOperationID(),
                              InternalClientConnection.nextMessageID(),
                              controls,
                              ByteString.valueOf(entry.getName().toString()),
                              "uid", ByteString.valueOf("rogasawara"));

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
         ByteString.valueOf("dn:cn=nonexistent,o=test"));
    List<Control> controls = new ArrayList<>();
    controls.add(authV2Control);

    CompareOperationBasis compareOperation =
         new CompareOperationBasis(
                              proxyUserConn,
                              InternalClientConnection.nextOperationID(),
                              InternalClientConnection.nextMessageID(),
                              controls,
                              ByteString.valueOf(entry.getName().toString()),
                              "uid", ByteString.valueOf("rogasawara"));

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
         new LDAPControl(ServerConstants.OID_PROXIED_AUTH_V2, false,
                     ByteString.empty());

    List<Control> controls = new ArrayList<>();
    controls.add(authV2Control);

    CompareOperationBasis compareOperation =
         new CompareOperationBasis(
                              proxyUserConn,
                              InternalClientConnection.nextOperationID(),
                              InternalClientConnection.nextMessageID(),
                              controls,
                              ByteString.valueOf(entry.getName().toString()),
                              "uid", ByteString.valueOf("rogasawara"));

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

    LDAPFilter.decode("(preferredlanguage=ja)");
    LDAPControl assertControl =
         new LDAPControl("1.1.1.1.1.1", true);
    List<Control> controls = new ArrayList<>();
    controls.add(assertControl);
    CompareOperationBasis compareOperation =
         new CompareOperationBasis(
                              conn, InternalClientConnection.nextOperationID(),
                              InternalClientConnection.nextMessageID(),
                              controls,
                              ByteString.valueOf(entry.getName().toString()),
                              "uid", ByteString.valueOf("rogasawara"));

    compareOperation.run();
    assertEquals(compareOperation.getResultCode(),
                 ResultCode.UNAVAILABLE_CRITICAL_EXTENSION);

    examineIncompleteOperation(compareOperation);
  }

}
