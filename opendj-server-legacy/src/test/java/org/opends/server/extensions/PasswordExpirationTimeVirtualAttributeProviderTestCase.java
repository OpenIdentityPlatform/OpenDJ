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
 *      Copyright 2012 profiq, s.r.o.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.extensions;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.TestCaseUtils;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.schema.GeneralizedTimeSyntax;
import org.opends.server.types.Attribute;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.Entry;
import org.opends.server.types.SearchResultEntry;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.testng.Assert.*;

@SuppressWarnings("javadoc")
public class PasswordExpirationTimeVirtualAttributeProviderTestCase
  extends ExtensionsTestCase
{

  private Entry notExpired;
  private Entry expired;

  @BeforeClass
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

  @BeforeMethod
  public void environmentSetup() throws Exception
  {
    TestCaseUtils.initializeMemoryBackend(TestCaseUtils.TEST_BACKEND_ID,
                                          TestCaseUtils.TEST_ROOT_DN_STRING,
                                          true);
  }

  @AfterMethod
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
    // Process the search request
    SearchRequest request = newSearchRequest(notExpired.getName(), SearchScope.BASE_OBJECT)
        .addAttribute(attributeName, "pwdpolicysubentry");
    InternalSearchOperation search = getRootConnection().processSearch(request);
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

    Iterator<ByteString> it = attr.iterator();
    assertTrue(it.hasNext());

    ByteString val = it.next();

    getRootConnection().disconnect(DisconnectReason.UNBIND, true, LocalizableMessage.EMPTY);

    return GeneralizedTimeSyntax.decodeGeneralizedTimeValue(val);
  }

}
