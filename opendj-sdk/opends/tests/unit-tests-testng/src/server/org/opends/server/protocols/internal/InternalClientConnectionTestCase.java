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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.protocols.internal;



import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConnectionSecurityProvider;
import org.opends.server.core.AddOperation;
import org.opends.server.core.BindOperation;
import org.opends.server.core.CompareOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ExtendedOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.Operation;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.types.Attribute;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.CancelRequest;
import org.opends.server.types.CancelResult;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.RDN;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchResultReference;
import org.opends.server.types.SearchScope;

import static org.testng.Assert.*;

import static org.opends.server.util.ServerConstants.*;



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
  @BeforeClass()
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
    return new Object[][]
    {
      new Object[] { new InternalClientConnection() },
      new Object[] { InternalClientConnection.getRootConnection() },
      new Object[] { new InternalClientConnection(new AuthenticationInfo()) },
      new Object[] { new InternalClientConnection(
           new AuthenticationInfo(DN.decode("cn=Directory Manager"), true)) },
      new Object[] { new InternalClientConnection(
           new AuthenticationInfo(DN.decode("uid=test,o=test"), false)) },
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
    long opID1 = conn.nextOperationID();
    long opID2 = conn.nextOperationID();
    assertEquals(opID2, (opID1 + 1));
  }



  /**
   * Tests the <CODE>nextMessageID</CODE> method.
   *
   * @param  conn  The internal client connection to use for the test.
   */
  @Test(dataProvider = "internalConns")
  public void testNextMessageID(InternalClientConnection conn)
  {
    int msgID1 = conn.nextMessageID();
    int msgID2 = conn.nextMessageID();
    assertEquals(msgID2, (msgID1 + 1));
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
  @Test(dataProvider = "internalConns")
  public void testGetConnectionSecurityProvider(InternalClientConnection conn)
  {
    assertNotNull(conn.getConnectionSecurityProvider());
  }



  /**
   * Tests the <CODE>setConnectionSecurityProvider</CODE> method.
   *
   * @param  conn  The internal client connection to use for the test.
   */
  @Test(dataProvider = "internalConns")
  public void testSetConnectionSecurityProvider(InternalClientConnection conn)
  {
    ConnectionSecurityProvider securityProvider =
         conn.getConnectionSecurityProvider();
    assertNotNull(securityProvider);
    conn.setConnectionSecurityProvider(securityProvider);
  }



  /**
   * Tests the <CODE>getSecurityMechanism</CODE> method.
   *
   * @param  conn  The internal client connection to use for the test.
   */
  @Test(dataProvider = "internalConns")
  public void testGetSecurityMechanism(InternalClientConnection conn)
  {
    assertNotNull(conn.getSecurityMechanism());
  }



  /**
   * Tests the <CODE>processDataRead</CODE> method.
   *
   * @param  conn  The internal client connection to use for the test.
   */
  @Test(dataProvider = "internalConns")
  public void testProcessDataRead(InternalClientConnection conn)
  {
    assertFalse(conn.processDataRead(null));
  }



  /**
   * Tests the first <CODE>processAdd</CODE> method, which takes raw arguments.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testProcessAdd1()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    ASN1OctetString dn = new ASN1OctetString("cn=test,o=test");

    ArrayList<LDAPAttribute> attrs = new ArrayList<LDAPAttribute>();

    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("top"));
    values.add(new ASN1OctetString("device"));
    attrs.add(new LDAPAttribute("objectClass", values));

    values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("test"));
    attrs.add(new LDAPAttribute("cn", values));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation = conn.processAdd(dn, attrs);
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the second <CODE>processAdd</CODE> method, which takes processed
   * arguments.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testProcessAdd2()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry("dn: cn=test,o=test",
                                      "objectClass: top",
                                      "objectClass: device",
                                      "cn: test");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation = conn.processAdd(e.getDN(), e.getObjectClasses(),
                                                e.getUserAttributes(),
                                                e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the first <CODE>processSimpleBind</CODE> method, which takes raw
   * arguments.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testProcessSimpleBind1()
         throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    BindOperation bindOperation =
         conn.processSimpleBind(new ASN1OctetString("cn=Directory Manager"),
                                new ASN1OctetString("password"));
    assertEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the second <CODE>processSimpleBind</CODE> method, which takes
   * processed arguments.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testProcessSimpleBind2()
         throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    BindOperation bindOperation =
         conn.processSimpleBind(DN.decode("cn=Directory Manager"),
                                new ASN1OctetString("password"));
    assertEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the first <CODE>processSASLBind</CODE> method, which takes raw
   * arguments.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testProcessSASLBind1()
         throws Exception
  {
    ASN1OctetString creds =
         new ASN1OctetString("\u0000dn:cn=Directory Manager\u0000password");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    BindOperation bindOperation =
         conn.processSASLBind(new ASN1OctetString(), "PLAIN", creds);
    assertEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the second <CODE>processSASLBind</CODE> method, which takes processed
   * arguments.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testProcessSASLBind2()
         throws Exception
  {
    ASN1OctetString creds =
         new ASN1OctetString("\u0000dn:cn=Directory Manager\u0000password");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    BindOperation bindOperation =
         conn.processSASLBind(DN.nullDN(), "PLAIN", creds);
    assertEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the first <CODE>processCompare</CODE> method, which takes raw
   * arguments.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testProcessCompare1()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry("dn: cn=test,o=test",
                                      "objectClass: top",
                                      "objectClass: device",
                                      "cn: test");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation = conn.processAdd(e.getDN(), e.getObjectClasses(),
                                                e.getUserAttributes(),
                                                e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    CompareOperation compareOperation =
         conn.processCompare(new ASN1OctetString("cn=test,o=test"), "cn",
                             new ASN1OctetString("test"));
    assertEquals(compareOperation.getResultCode(), ResultCode.COMPARE_TRUE);
  }



  /**
   * Tests the second <CODE>processCompare</CODE> method, which takes processed
   * arguments.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testProcessCompare2()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry("dn: cn=test,o=test",
                                      "objectClass: top",
                                      "objectClass: device",
                                      "cn: test");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation = conn.processAdd(e.getDN(), e.getObjectClasses(),
                                                e.getUserAttributes(),
                                                e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    CompareOperation compareOperation =
         conn.processCompare(DN.decode("cn=test,o=test"),
                             DirectoryServer.getAttributeType("cn", true),
                             new ASN1OctetString("test"));
    assertEquals(compareOperation.getResultCode(), ResultCode.COMPARE_TRUE);
  }



  /**
   * Tests the first <CODE>processDelete</CODE> method, which takes raw
   * arguments.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testProcessDelete1()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry("dn: cn=test,o=test",
                                      "objectClass: top",
                                      "objectClass: device",
                                      "cn: test");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation = conn.processAdd(e.getDN(), e.getObjectClasses(),
                                                e.getUserAttributes(),
                                                e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    DeleteOperation deleteOperation =
         conn.processDelete(new ASN1OctetString("cn=test,o=test"));
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the second <CODE>processDelete</CODE> method, which takes processed
   * arguments.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testProcessDelete2()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry("dn: cn=test,o=test",
                                      "objectClass: top",
                                      "objectClass: device",
                                      "cn: test");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation = conn.processAdd(e.getDN(), e.getObjectClasses(),
                                                e.getUserAttributes(),
                                                e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    DeleteOperation deleteOperation =
         conn.processDelete(DN.decode("cn=test,o=test"));
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the <CODE>processExtendedOperation</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testProcessExtendedOperation()
         throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
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
  @Test()
  public void testProcessModify1()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry("dn: cn=test,o=test",
                                      "objectClass: top",
                                      "objectClass: device",
                                      "cn: test");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation = conn.processAdd(e.getDN(), e.getObjectClasses(),
                                                e.getUserAttributes(),
                                                e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("This is a test"));

    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.REPLACE,
                                  new LDAPAttribute("description", values)));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("cn=test,o=test"), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the second <CODE>processModify</CODE> method, which takes processed
   * arguments.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testProcessModify2()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry("dn: cn=test,o=test",
                                      "objectClass: top",
                                      "objectClass: device",
                                      "cn: test");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation = conn.processAdd(e.getDN(), e.getObjectClasses(),
                                                e.getUserAttributes(),
                                                e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
                              new Attribute("description", "This is a test")));

    ModifyOperation modifyOperation =
         conn.processModify(DN.decode("cn=test,o=test"), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the first <CODE>processModifyDN</CODE> method, which takes raw
   * arguments and no newSuperior option.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testProcessModifyDN1()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry("dn: cn=test,o=test",
                                      "objectClass: top",
                                      "objectClass: device",
                                      "cn: test");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation = conn.processAdd(e.getDN(), e.getObjectClasses(),
                                                e.getUserAttributes(),
                                                e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ModifyDNOperation modifyDNOperation =
         conn.processModifyDN(new ASN1OctetString("cn=test,o=test"),
                              new ASN1OctetString("cn=test2"), true);
    assertEquals(modifyDNOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the second <CODE>processModifyDN</CODE> method, which takes raw
   * arguments and allows newSuperior option.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testProcessModifyDN2()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry("dn: cn=test,o=test",
                                      "objectClass: top",
                                      "objectClass: device",
                                      "cn: test");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation = conn.processAdd(e.getDN(), e.getObjectClasses(),
                                                e.getUserAttributes(),
                                                e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ModifyDNOperation modifyDNOperation =
         conn.processModifyDN(new ASN1OctetString("cn=test,o=test"),
                              new ASN1OctetString("cn=test2"), true,
                              new ASN1OctetString("dc=example,dc=com"));
    assertEquals(modifyDNOperation.getResultCode(),
                 ResultCode.UNWILLING_TO_PERFORM);
  }



  /**
   * Tests the third <CODE>processModifyDN</CODE> method, which takes processed
   * arguments and no newSuperior option.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testProcessModifyDN3()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry("dn: cn=test,o=test",
                                      "objectClass: top",
                                      "objectClass: device",
                                      "cn: test");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation = conn.processAdd(e.getDN(), e.getObjectClasses(),
                                                e.getUserAttributes(),
                                                e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ModifyDNOperation modifyDNOperation =
         conn.processModifyDN(DN.decode("cn=test,o=test"),
                              RDN.decode("cn=test2"), true);
    assertEquals(modifyDNOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the fourth <CODE>processModifyDN</CODE> method, which takes processed
   * arguments and allows newSuperior option.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testProcessModifyDN4()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry("dn: cn=test,o=test",
                                      "objectClass: top",
                                      "objectClass: device",
                                      "cn: test");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation = conn.processAdd(e.getDN(), e.getObjectClasses(),
                                                e.getUserAttributes(),
                                                e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ModifyDNOperation modifyDNOperation =
         conn.processModifyDN(DN.decode("cn=test,o=test"),
                              RDN.decode("cn=test2"), true,
                              DN.decode("dc=example,dc=com"));
    assertEquals(modifyDNOperation.getResultCode(),
                 ResultCode.UNWILLING_TO_PERFORM);
  }



  /**
   * Tests the first <CODE>processSearch</CODE> method, which takes a minimal
   * set of raw arguments.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testProcessSearch1()
         throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         conn.processSearch(new ASN1OctetString(""), SearchScope.BASE_OBJECT,
                            LDAPFilter.decode("(objectClass=*)"));
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
  @Test()
  public void testProcessSearch2()
         throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         conn.processSearch(new ASN1OctetString(""), SearchScope.BASE_OBJECT,
                            DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                            LDAPFilter.decode("(objectClass=*)"),
                            new LinkedHashSet<String>());
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
  @Test()
  public void testProcessSearch3()
         throws Exception
  {
    TestInternalSearchListener searchListener =
         new TestInternalSearchListener();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         conn.processSearch(new ASN1OctetString(""), SearchScope.BASE_OBJECT,
                            DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                            LDAPFilter.decode("(objectClass=*)"),
                            new LinkedHashSet<String>(), searchListener);
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
  @Test()
  public void testProcessSearch4()
         throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         conn.processSearch(DN.nullDN(), SearchScope.BASE_OBJECT,
              SearchFilter.createFilterFromString("(objectClass=*)"));
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
  @Test()
  public void testProcessSearch5()
         throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         conn.processSearch(DN.nullDN(), SearchScope.BASE_OBJECT,
              DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
              SearchFilter.createFilterFromString("(objectClass=*)"),
              new LinkedHashSet<String>());
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
  @Test()
  public void testProcessSearch6()
         throws Exception
  {
    TestInternalSearchListener searchListener =
         new TestInternalSearchListener();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         conn.processSearch(DN.nullDN(), SearchScope.BASE_OBJECT,
              DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
              SearchFilter.createFilterFromString("(objectClass=*)"),
              new LinkedHashSet<String>(), searchListener);
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    assertFalse(searchListener.getSearchEntries().isEmpty());
    assertTrue(searchListener.getSearchReferences().isEmpty());
  }



  /**
   * Tests the <CODE>sendSearchReference</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSendSearchReference()
         throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         conn.processSearch(new ASN1OctetString(""), SearchScope.BASE_OBJECT,
                            LDAPFilter.decode("(objectClass=*)"));
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
  @Test()
  public void testSendIntermediateResponseMessage()
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    assertFalse(conn.sendIntermediateResponseMessage(null));
  }



  /**
   * Tests the <CODE>disconnect</CODE> method.
   */
  @Test()
  public void testDisconnect()
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    conn.disconnect(DisconnectReason.OTHER, false, "testDisconnect", -1);
  }



  /**
   * Tests the <CODE>bindInProgress</CODE> method.
   */
  @Test()
  public void testBindInProgress()
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    assertFalse(conn.bindInProgress());
  }



  /**
   * Tests the <CODE>setBindInProgress</CODE> method.
   */
  @Test()
  public void testSetBindInProgress()
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    conn.setBindInProgress(true);
    assertFalse(conn.bindInProgress());
  }



  /**
   * Tests the <CODE>getOperationsInProgress</CODE> method.
   */
  @Test()
  public void testGetOperationsInProgress()
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    Collection<Operation> opList = conn.getOperationsInProgress();
    assertNotNull(opList);
    assertTrue(opList.isEmpty());
  }



  /**
   * Tests the <CODE>getOperationInProgress</CODE> method.
   */
  @Test()
  public void testGetOperationInProgress()
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    assertNull(conn.getOperationInProgress(0));
  }



  /**
   * Tests the <CODE>removeOperationInProgress</CODE> method.
   */
  @Test()
  public void testRemoveOperationInProgress()
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    assertFalse(conn.removeOperationInProgress(1));
  }



  /**
   * Tests the <CODE>cancelOperation</CODE> method.
   */
  @Test()
  public void testCancelOperation()
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    CancelResult cancelResult =
         conn.cancelOperation(1,
              new CancelRequest(true, "testCancelOperation"));
    assertEquals(cancelResult, CancelResult.CANNOT_CANCEL);
  }



  /**
   * Tests the <CODE>cancelAllOperations</CODE> method.
   */
  @Test()
  public void testCancelAllOperations()
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    conn.cancelAllOperations(new CancelRequest(true, "testCancelOperation"));
  }



  /**
   * Tests the <CODE>cancelAllOperationsExcept</CODE> method.
   */
  @Test()
  public void testCancelAllOperationsExcept()
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    conn.cancelAllOperationsExcept(
         new CancelRequest(true, "testCancelOperation"), 1);
  }



  /**
   * Tests the <CODE>toString</CODE> method.
   */
  @Test()
  public void testToString()
  {
    StringBuilder buffer = new StringBuilder();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    conn.toString(buffer);

    assertFalse(buffer.toString().equals(""));
  }
}

