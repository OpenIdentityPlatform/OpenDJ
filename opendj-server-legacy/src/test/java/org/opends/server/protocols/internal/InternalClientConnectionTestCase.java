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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.protocols.internal;

import java.util.ArrayList;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.AddOperation;
import org.opends.server.core.BindOperation;
import org.opends.server.core.CompareOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ExtendedOperation;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.types.Attributes;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.CancelRequest;
import org.opends.server.types.CancelResult;
import org.opends.server.types.DN;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;
import org.opends.server.types.RDN;
import org.opends.server.types.RawAttribute;
import org.opends.server.types.RawModification;
import org.opends.server.types.SearchResultReference;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.opends.server.util.ServerConstants.*;
import static org.testng.Assert.*;

/**
 * This class defines a set of tests for the
 * org.opends.server.protocols.internal.InternalClientConnection class.
 */
public class InternalClientConnectionTestCase
       extends InternalTestCase
{
  /**
   * Ensures that the Directory Server is running.
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
   * Retrieves a set of internal client connections that may be used for
   * testing purposes.
   *
   * @return  A set of internal client connections that may be used for
   *          testing purposes.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "internalConns")
  public Object[][] getInternalConnections()
         throws Exception
  {
    DN dmDN = DN.valueOf("cn=Directory Manager,cn=Root DNs,cn=config");
    Entry dmEntry = DirectoryServer.getEntry(dmDN);

    TestCaseUtils.initializeTestBackend(true);
    Entry userEntry = TestCaseUtils.makeEntry(
      "dn: uid=test.user,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test.user",
      "givenName: Test",
      "sn: User",
      "cn: Test User",
      "userPassword: password");
    TestCaseUtils.addEntry(userEntry);

    return new Object[][]
    {
      new Object[] { InternalClientConnection.getRootConnection() },
      new Object[] { new InternalClientConnection(new AuthenticationInfo()) },
      new Object[] { new InternalClientConnection(
           new AuthenticationInfo(dmEntry, true)) },
      new Object[] { new InternalClientConnection(
           new AuthenticationInfo(userEntry, false)) },
      new Object[] { new InternalClientConnection(dmDN) },
      new Object[] { new InternalClientConnection(DN.rootDN()) },
      new Object[] { new InternalClientConnection((DN) null) }
    };
  }



  /**
   * Tests the <CODE>nextOperationID</CODE> method.
   *
   * @param  conn  The internal client connection to use for the test.
   */
  @Test(dataProvider = "internalConns")
  public void testNextOperationID(InternalClientConnection conn)
  {
    long opID1 = InternalClientConnection.nextOperationID();
    long opID2 = InternalClientConnection.nextOperationID();
    assertEquals(opID2, opID1 + 1);
  }



  /**
   * Tests the <CODE>nextMessageID</CODE> method.
   *
   * @param  conn  The internal client connection to use for the test.
   */
  @Test(dataProvider = "internalConns")
  public void testNextMessageID(InternalClientConnection conn)
  {
    int msgID1 = InternalClientConnection.nextMessageID();
    int msgID2 = InternalClientConnection.nextMessageID();
    assertEquals(msgID2, msgID1 + 1);
  }



  /**
   * Tests the <CODE>getConnectionID</CODE> method.
   *
   * @param  conn  The internal client connection to use for the test.
   */
  @Test(dataProvider = "internalConns")
  public void testGetConnectionID(InternalClientConnection conn)
  {
    assertTrue(conn.getConnectionID() < 0);
  }



  /**
   * Tests the <CODE>getConnectionHandler</CODE> method.
   *
   * @param  conn  The internal client connection to use for the test.
   */
  @Test(dataProvider = "internalConns")
  public void testGetConnectionHandler(InternalClientConnection conn)
  {
    assertEquals(conn.getConnectionHandler(),
                 InternalConnectionHandler.getInstance());
  }



  /**
   * Tests the <CODE>getProtocol</CODE> method.
   *
   * @param  conn  The internal client connection to use for the test.
   */
  @Test(dataProvider = "internalConns")
  public void testGetProtocol(InternalClientConnection conn)
  {
    assertEquals(conn.getProtocol(), "internal");
  }



  /**
   * Tests the <CODE>getClientAddress</CODE> method.
   *
   * @param  conn  The internal client connection to use for the test.
   */
  @Test(dataProvider = "internalConns")
  public void testGetClientAddress(InternalClientConnection conn)
  {
    assertEquals(conn.getClientAddress(), "internal");
  }



  /**
   * Tests the <CODE>getServerAddress</CODE> method.
   *
   * @param  conn  The internal client connection to use for the test.
   */
  @Test(dataProvider = "internalConns")
  public void testGetServerAddress(InternalClientConnection conn)
  {
    assertEquals(conn.getServerAddress(), "internal");
  }



  /**
   * Tests the <CODE>getRemoteAddress</CODE> method.
   *
   * @param  conn  The internal client connection to use for the test.
   */
  @Test(dataProvider = "internalConns")
  public void testGetRemoteAddress(InternalClientConnection conn)
  {
    assertNull(conn.getRemoteAddress());
  }



  /**
   * Tests the <CODE>getLocalAddress</CODE> method.
   *
   * @param  conn  The internal client connection to use for the test.
   */
  @Test(dataProvider = "internalConns")
  public void testGetLocalAddress(InternalClientConnection conn)
  {
    assertNull(conn.getLocalAddress());
  }



  /**
   * Tests the <CODE>isSecure</CODE> method.
   *
   * @param  conn  The internal client connection to use for the test.
   */
  @Test(dataProvider = "internalConns")
  public void testIsSecure(InternalClientConnection conn)
  {
    assertTrue(conn.isSecure());
  }



  /**
   * Tests the <CODE>getConnectionSecurityProvider</CODE> method.
   *
   * @param  conn  The internal client connection to use for the test.
   */
 /*MPD
  *  @Test(dataProvider = "internalConns")

  public void testGetConnectionSecurityProvider(InternalClientConnection conn)
  {
    assertNotNull(conn.getConnectionSecurityProvider());
  }
*/


  /**
   * Tests the <CODE>setConnectionSecurityProvider</CODE> method.
   *
   * @param  conn  The internal client connection to use for the test.
   */
  /* MPD
  @Test(dataProvider = "internalConns")
  public void testSetConnectionSecurityProvider(InternalClientConnection conn)
  {
    ConnectionSecurityProvider securityProvider =
         conn.getConnectionSecurityProvider();
    assertNotNull(securityProvider);
    conn.setConnectionSecurityProvider(securityProvider);
  }
*/


  /**
   * Tests the first <CODE>processAdd</CODE> method, which takes raw arguments.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testProcessAdd1()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    ByteString dn = ByteString.valueOf("cn=test,o=test");

    ArrayList<RawAttribute> attrs = new ArrayList<>();
    attrs.add(new LDAPAttribute("objectClass", newArrayList("top", "device")));
    attrs.add(new LDAPAttribute("cn", "test"));

    AddOperation addOperation = getRootConnection().processAdd(dn, attrs);
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the second <CODE>processAdd</CODE> method, which takes processed
   * arguments.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testProcessAdd2()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry("dn: cn=test,o=test",
                                      "objectClass: top",
                                      "objectClass: device",
                                      "cn: test");
  }



  /**
   * Tests the first <CODE>processSimpleBind</CODE> method, which takes raw
   * arguments.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testProcessSimpleBind1()
         throws Exception
  {
    InternalClientConnection conn = getRootConnection();
    BindOperation bindOperation =
         conn.processSimpleBind(ByteString.valueOf("cn=Directory Manager"),
                                ByteString.valueOf("password"));
    assertEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the second <CODE>processSimpleBind</CODE> method, which takes
   * processed arguments.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testProcessSimpleBind2()
         throws Exception
  {
    InternalClientConnection conn = getRootConnection();
    BindOperation bindOperation =
         conn.processSimpleBind(DN.valueOf("cn=Directory Manager"),
                                ByteString.valueOf("password"));
    assertEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the first <CODE>processSASLBind</CODE> method, which takes raw
   * arguments.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testProcessSASLBind1()
         throws Exception
  {
    ByteString creds =
         ByteString.valueOf("\u0000dn:cn=Directory Manager\u0000password");

    InternalClientConnection conn = getRootConnection();
    BindOperation bindOperation =
         conn.processSASLBind(ByteString.empty(), "PLAIN", creds);
    assertEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the second <CODE>processSASLBind</CODE> method, which takes processed
   * arguments.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testProcessSASLBind2()
         throws Exception
  {
    ByteString creds =
         ByteString.valueOf("\u0000dn:cn=Directory Manager\u0000password");

    InternalClientConnection conn = getRootConnection();
    BindOperation bindOperation =
         conn.processSASLBind(DN.rootDN(), "PLAIN", creds);
    assertEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the first <CODE>processCompare</CODE> method, which takes raw
   * arguments.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testProcessCompare1()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry("dn: cn=test,o=test",
                                      "objectClass: top",
                                      "objectClass: device",
                                      "cn: test");


    InternalClientConnection conn = getRootConnection();
    CompareOperation compareOperation =
         conn.processCompare(ByteString.valueOf("cn=test,o=test"), "cn",
                             ByteString.valueOf("test"));
    assertEquals(compareOperation.getResultCode(), ResultCode.COMPARE_TRUE);
  }



  /**
   * Tests the second <CODE>processCompare</CODE> method, which takes processed
   * arguments.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testProcessCompare2()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry("dn: cn=test,o=test",
                                      "objectClass: top",
                                      "objectClass: device",
                                      "cn: test");

    InternalClientConnection conn = getRootConnection();
    CompareOperation compareOperation =
         conn.processCompare(DN.valueOf("cn=test,o=test"),
                             DirectoryServer.getAttributeTypeOrDefault("cn"),
                             ByteString.valueOf("test"));
    assertEquals(compareOperation.getResultCode(), ResultCode.COMPARE_TRUE);
  }



  /**
   * Tests the first <CODE>processDelete</CODE> method, which takes raw
   * arguments.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testProcessDelete1()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry("dn: cn=test,o=test",
                                      "objectClass: top",
                                      "objectClass: device",
                                      "cn: test");

    DeleteOperation deleteOperation =
         getRootConnection().processDelete(ByteString.valueOf("cn=test,o=test"));
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the second <CODE>processDelete</CODE> method, which takes processed
   * arguments.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testProcessDelete2()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry("dn: cn=test,o=test",
                                      "objectClass: top",
                                      "objectClass: device",
                                      "cn: test");

    DeleteOperation deleteOperation =
         getRootConnection().processDelete(DN.valueOf("cn=test,o=test"));
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the <CODE>processExtendedOperation</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testProcessExtendedOperation()
         throws Exception
  {
    InternalClientConnection conn = getRootConnection();
    ExtendedOperation extendedOperation =
         conn.processExtendedOperation(OID_WHO_AM_I_REQUEST, null);
    assertEquals(extendedOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the first <CODE>processModify</CODE> method, which takes raw
   * arguments.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testProcessModify1()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry("dn: cn=test,o=test",
                                      "objectClass: top",
                                      "objectClass: device",
                                      "cn: test");

    ArrayList<RawModification> mods = new ArrayList<>();
    mods.add(new LDAPModification(ModificationType.REPLACE,
        new LDAPAttribute("description", "This is a test")));

    InternalClientConnection conn = getRootConnection();
    ModifyOperation modifyOperation =
         conn.processModify(ByteString.valueOf("cn=test,o=test"), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the second <CODE>processModify</CODE> method, which takes processed
   * arguments.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testProcessModify2()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry("dn: cn=test,o=test",
                                      "objectClass: top",
                                      "objectClass: device",
                                      "cn: test");

    ArrayList<Modification> mods = new ArrayList<>();
    mods.add(new Modification(ModificationType.REPLACE,
        Attributes.create("description", "This is a test")));

    InternalClientConnection conn = getRootConnection();
    ModifyOperation modifyOperation =
         conn.processModify(DN.valueOf("cn=test,o=test"), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the first <CODE>processModifyDN</CODE> method, which takes raw
   * arguments and no newSuperior option.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testProcessModifyDN1()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry("dn: cn=test,o=test",
                                      "objectClass: top",
                                      "objectClass: device",
                                      "cn: test");

    InternalClientConnection conn = getRootConnection();
    ModifyDNOperation modifyDNOperation =
         conn.processModifyDN(ByteString.valueOf("cn=test,o=test"),
                              ByteString.valueOf("cn=test2"), true);
    assertEquals(modifyDNOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the second <CODE>processModifyDN</CODE> method, which takes raw
   * arguments and allows newSuperior option.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testProcessModifyDN2()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry("dn: cn=test,o=test",
                                      "objectClass: top",
                                      "objectClass: device",
                                      "cn: test");

    InternalClientConnection conn = getRootConnection();
    ModifyDNOperation modifyDNOperation =
         conn.processModifyDN(ByteString.valueOf("cn=test,o=test"),
                              ByteString.valueOf("cn=test2"), true,
                              ByteString.valueOf("dc=example,dc=com"));
    assertEquals(modifyDNOperation.getResultCode(),
                 ResultCode.UNWILLING_TO_PERFORM);
  }



  /**
   * Tests the third <CODE>processModifyDN</CODE> method, which takes processed
   * arguments and no newSuperior option.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testProcessModifyDN3()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry("dn: cn=test,o=test",
                                      "objectClass: top",
                                      "objectClass: device",
                                      "cn: test");

    InternalClientConnection conn = getRootConnection();
    ModifyDNOperation modifyDNOperation =
         conn.processModifyDN(DN.valueOf("cn=test,o=test"),
                              RDN.decode("cn=test2"), true);
    assertEquals(modifyDNOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the fourth <CODE>processModifyDN</CODE> method, which takes processed
   * arguments and allows newSuperior option.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testProcessModifyDN4()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry("dn: cn=test,o=test",
                                      "objectClass: top",
                                      "objectClass: device",
                                      "cn: test");

    InternalClientConnection conn = getRootConnection();
    ModifyDNOperation modifyDNOperation =
         conn.processModifyDN(DN.valueOf("cn=test,o=test"),
                              RDN.decode("cn=test2"), true,
                              DN.valueOf("dc=example,dc=com"));
    assertEquals(modifyDNOperation.getResultCode(),
                 ResultCode.UNWILLING_TO_PERFORM);
  }



  /**
   * Tests the first <CODE>processSearch</CODE> method, which takes a minimal
   * set of raw arguments.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testProcessSearch1() throws Exception
  {
    SearchRequest request = newSearchRequest(DN.rootDN(), SearchScope.BASE_OBJECT);
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    assertFalse(searchOperation.getSearchEntries().isEmpty());
    assertTrue(searchOperation.getSearchReferences().isEmpty());
  }



  /**
   * Tests the second <CODE>processSearch</CODE> method, which takes a full set
   * of raw arguments.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testProcessSearch2() throws Exception
  {
    SearchRequest request = newSearchRequest(DN.rootDN(), SearchScope.BASE_OBJECT);
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    assertFalse(searchOperation.getSearchEntries().isEmpty());
    assertTrue(searchOperation.getSearchReferences().isEmpty());
  }



  /**
   * Tests the third <CODE>processSearch</CODE> method, which takes a full set
   * of raw arguments and uses an internal search listener to handle the
   * results.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testProcessSearch3() throws Exception
  {
    TestInternalSearchListener searchListener = new TestInternalSearchListener();

    SearchRequest request = newSearchRequest(DN.rootDN(), SearchScope.BASE_OBJECT);
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request, searchListener);
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    assertFalse(searchListener.getSearchEntries().isEmpty());
    assertTrue(searchListener.getSearchReferences().isEmpty());
  }



  /**
   * Tests the fourth <CODE>processSearch</CODE> method, which takes a minimal
   * set of processed arguments.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testProcessSearch4() throws Exception
  {
    final SearchRequest request = newSearchRequest(DN.rootDN(), SearchScope.BASE_OBJECT);
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    assertFalse(searchOperation.getSearchEntries().isEmpty());
    assertTrue(searchOperation.getSearchReferences().isEmpty());
  }



  /**
   * Tests the fifth <CODE>processSearch</CODE> method, which takes a full set
   * of processed arguments.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testProcessSearch5() throws Exception
  {
    final SearchRequest request = newSearchRequest(DN.rootDN(), SearchScope.BASE_OBJECT);
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    assertFalse(searchOperation.getSearchEntries().isEmpty());
    assertTrue(searchOperation.getSearchReferences().isEmpty());
  }



  /**
   * Tests the sixth <CODE>processSearch</CODE> method, which takes a full set
   * of processed arguments and uses an internal search listener to handle the
   * results.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testProcessSearch6() throws Exception
  {
    TestInternalSearchListener searchListener =
         new TestInternalSearchListener();

    final SearchRequest request = newSearchRequest(DN.rootDN(), SearchScope.BASE_OBJECT);
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request, searchListener);
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    assertFalse(searchListener.getSearchEntries().isEmpty());
    assertTrue(searchListener.getSearchReferences().isEmpty());
  }



  /**
   * Tests the <CODE>sendSearchReference</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testSendSearchReference()
         throws Exception
  {
    InternalClientConnection conn = getRootConnection();
    InternalSearchOperation searchOperation =
        conn.processSearch(newSearchRequest(DN.rootDN(), SearchScope.BASE_OBJECT));
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    assertFalse(searchOperation.getSearchEntries().isEmpty());
    assertTrue(searchOperation.getSearchReferences().isEmpty());

    SearchResultReference reference =
         new SearchResultReference("ldap://server.example.com:389/");
    conn.sendSearchReference(searchOperation, reference);
    assertFalse(searchOperation.getSearchReferences().isEmpty());
  }



  /**
   * Tests the <CODE>sendIntermediateResponseMessage</CODE> method.
   */
  @Test
  public void testSendIntermediateResponseMessage()
  {
    assertFalse(getRootConnection().sendIntermediateResponseMessage(null));
  }



  /**
   * Tests the <CODE>disconnect</CODE> method.
   */
  @Test
  public void testDisconnect()
  {
    getRootConnection().disconnect(DisconnectReason.OTHER, false,
            LocalizableMessage.raw("testDisconnect"));
  }





  /**
   * Tests the <CODE>removeOperationInProgress</CODE> method.
   */
  @Test
  public void testRemoveOperationInProgress()
  {
    assertFalse(getRootConnection().removeOperationInProgress(1));
  }



  /**
   * Tests the <CODE>cancelOperation</CODE> method.
   */
  @Test
  public void testCancelOperation()
  {
    CancelResult cancelResult =
         getRootConnection().cancelOperation(1,
              new CancelRequest(true, LocalizableMessage.raw("testCancelOperation")));
    assertEquals(cancelResult.getResultCode(), ResultCode.CANNOT_CANCEL);
  }



  /**
   * Tests the <CODE>cancelAllOperations</CODE> method.
   */
  @Test
  public void testCancelAllOperations()
  {
    getRootConnection().cancelAllOperations(new CancelRequest(true,
            LocalizableMessage.raw("testCancelOperation")));
  }



  /**
   * Tests the <CODE>cancelAllOperationsExcept</CODE> method.
   */
  @Test
  public void testCancelAllOperationsExcept()
  {
    getRootConnection().cancelAllOperationsExcept(
            new CancelRequest(true, LocalizableMessage.raw("testCancelOperation")), 1);
  }



  /**
   * Tests the <CODE>toString</CODE> method.
   */
  @Test
  public void testToString()
  {
    assertFalse(getRootConnection().toString().equals(""));
  }
}

