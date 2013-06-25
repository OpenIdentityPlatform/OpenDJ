/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
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
 *      Copyright 2012 profiq, s.r.o.
 */
package org.opends.server.extensions;

import org.testng.annotations.BeforeMethod;
import org.opends.server.schema.GeneralizedTimeSyntax;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;

import org.opends.messages.Message;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.types.*;

import static org.testng.Assert.*;

public class PasswordExpirationTimeVirtualAttributeProviderTestCase
  extends ExtensionsTestCase
{

  private Entry notExpired;
  private Entry expired;

  @BeforeClass()
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();
    notExpired = TestCaseUtils.makeEntry("dn: uid=tuser," + TestCaseUtils.TEST_ROOT_DN_STRING,
                                         "objectClass: top",
                                         "objectClass: person",
                                         "objectClass: organizationalPerson",
                                         "objectClass: inetOrgPerson",
                                         "uid: tuser",
                                         "cn: Test User",
                                         "sn: User",
                                         "userPassword: password",
                                         "createTimestamp: 20120315163235Z");
    
    expired = TestCaseUtils.makeEntry("dn: uid=tuser," + TestCaseUtils.TEST_ROOT_DN_STRING,
                                       "objectClass: top",
                                       "objectClass: person",
                                       "objectClass: organizationalPerson",
                                       "objectClass: inetOrgPerson",
                                       "uid: tuser",
                                       "cn: Test User",
                                       "sn: User",
                                       "userPassword: password",
                                       "createTimestamp: 20110315163235Z");
  }

  @Test
  public void test30dPwPolicy() throws Exception
  {
    TestCaseUtils.addEntry(notExpired);

    TestCaseUtils.addEntry("dn: cn=Subentry Password Policy,"  + TestCaseUtils.TEST_ROOT_DN_STRING,
                           "objectClass: top",
                           "objectClass: subentry",
                           "objectClass: pwdPolicy",
                           "cn: Subentry Password Policy",
                           "pwdAttribute: userPassword",
                           "pwdLockout: TRUE",
                           "pwdMaxFailure: 3",
                           "pwdFailureCountInterval: 300",
                           "pwdLockoutDuration: 300",
                           "pwdAllowUserChange: TRUE",
                           "pwdSafeModify: TRUE",
                           "pwdMaxAge: 2592000", // 30 days
                           "subtreeSpecification: {minimum 1, specificationFilter \"(objectclass=*)\"}");

    
    long expirationTime = getTimeValueFromAttribute("ds-pwp-password-expiration-time");
    long createTime = getTimeValueFromAttribute("pwdchangedtime");
    assertEquals(expirationTime, createTime + 2592000000L);
  }

  @BeforeMethod()
  public void environmentSetup() throws Exception
  {
    TestCaseUtils.initializeMemoryBackend(TestCaseUtils.TEST_BACKEND_ID,
                                          TestCaseUtils.TEST_ROOT_DN_STRING,
                                          true);
  }

  @AfterMethod()
  public void environmentCleanup() throws Exception
  {
    TestCaseUtils.clearMemoryBackend(TestCaseUtils.TEST_BACKEND_ID);
  }

  @Test(expectedExceptions = AssertionError.class)
  public void testNoExpiration() throws Exception
  {
    TestCaseUtils.addEntry(notExpired);
    TestCaseUtils.addEntry("dn: cn=Subentry Password Policy,"  + TestCaseUtils.TEST_ROOT_DN_STRING,
                           "objectClass: top",
                           "objectClass: subentry",
                           "objectClass: pwdPolicy",
                           "cn: Subentry Password Policy",
                           "pwdAttribute: userPassword",
                           "pwdLockout: TRUE",
                           "pwdMaxFailure: 3",
                           "pwdFailureCountInterval: 300",
                           "pwdLockoutDuration: 300",
                           "pwdAllowUserChange: TRUE",
                           "pwdSafeModify: TRUE",
                           "subtreeSpecification: {minimum 1, specificationFilter \"(objectclass=*)\"}");
    getTimeValueFromAttribute("ds-pwp-password-expiration-time");
  }

  @Test
  public void testPwPolicyExpiring() throws Exception
  {
    TestCaseUtils.addEntry(notExpired);
    
    TestCaseUtils.addEntry("dn: cn=Subentry Password Policy,"  + TestCaseUtils.TEST_ROOT_DN_STRING,
                           "objectClass: top",
                           "objectClass: subentry",
                           "objectClass: pwdPolicy",
                           "cn: Subentry Password Policy",
                           "pwdAttribute: userPassword",
                           "pwdLockout: TRUE",
                           "pwdMaxFailure: 3",
                           "pwdFailureCountInterval: 300",
                           "pwdLockoutDuration: 300",
                           "pwdAllowUserChange: TRUE",
                           "pwdSafeModify: TRUE",
                           "pwdMaxAge: 2592000", // 30 days
                           "subtreeSpecification: {minimum 1, specificationFilter \"(objectclass=*)\"}");

    long expirationTime = getTimeValueFromAttribute("ds-pwp-password-expiration-time");
    long createTime = getTimeValueFromAttribute("pwdchangedtime");
    assertEquals(expirationTime, createTime + 2592000000L);
  }

  @Test
  public void testPwPolicyExpired() throws Exception
  {
    TestCaseUtils.addEntry(expired);
    
    TestCaseUtils.addEntry("dn: cn=Subentry Password Policy,"  + TestCaseUtils.TEST_ROOT_DN_STRING,
                           "objectClass: top",
                           "objectClass: subentry",
                           "objectClass: pwdPolicy",
                           "cn: Subentry Password Policy",
                           "pwdAttribute: userPassword",
                           "pwdLockout: TRUE",
                           "pwdMaxFailure: 3",
                           "pwdFailureCountInterval: 300",
                           "pwdLockoutDuration: 300",
                           "pwdAllowUserChange: TRUE",
                           "pwdSafeModify: TRUE",
                           "pwdMaxAge: 648000", // 7.5 days
                           "subtreeSpecification: {minimum 1, specificationFilter \"(objectclass=*)\"}");


    long expirationTime = getTimeValueFromAttribute("ds-pwp-password-expiration-time");
    long createTime = getTimeValueFromAttribute("pwdchangedtime");
    assertEquals(expirationTime, createTime + 648000000L);
  }

  private long getTimeValueFromAttribute(String attributeName)
    throws Exception
  {
    // Establish the internal connection as root

    InternalClientConnection conn =
      InternalClientConnection.getRootConnection();

    assertNotNull(conn);

    // Define the attribute to be returned

    LinkedHashSet<String> retAttr = new  LinkedHashSet<String>();
    retAttr.add(attributeName);
    retAttr.add("pwdpolicysubentry");

    // Process the search request

    InternalSearchOperation search =
      conn.processSearch(notExpired.getDN().toString(),
                         SearchScope.BASE_OBJECT,
                         DereferencePolicy.DEREF_ALWAYS,
                         0,
                         0,
                         false,
                         "(objectclass=*)",
                         retAttr);
    assertEquals(search.getResultCode(), ResultCode.SUCCESS);

    LinkedList<SearchResultEntry> entries = search.getSearchEntries();
    assertNotNull(entries);
    assertEquals(entries.size(), 1);

    SearchResultEntry entry = entries.get(0);
    assertNotNull(entry);

    List<Attribute> attrs = entry.getAttribute(attributeName);
    assertNotNull(attrs);
    assertEquals(attrs.size(), 1);

    Attribute attr = attrs.get(0);
    assertNotNull(attr);

    Iterator<AttributeValue> it = attr.iterator();
    assertTrue(it.hasNext());

    AttributeValue val = it.next();

    conn.disconnect(DisconnectReason.UNBIND, true, Message.EMPTY);
    
    return
      GeneralizedTimeSyntax.decodeGeneralizedTimeValue(val.getValue());
  }

}
