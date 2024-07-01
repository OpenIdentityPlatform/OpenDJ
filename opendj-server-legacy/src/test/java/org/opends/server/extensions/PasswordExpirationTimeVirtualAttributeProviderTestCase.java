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
 * Copyright 2012 profiq, s.r.o.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import static org.assertj.core.api.Assertions.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.testng.Assert.*;

import java.util.Iterator;
import java.util.LinkedList;

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

    Iterable<Attribute> attrs = entry.getAllAttributes(attributeName);
    assertThat(attrs).hasSize(1);

    Attribute attr = attrs.iterator().next();
    assertNotNull(attr);

    Iterator<ByteString> it = attr.iterator();
    assertTrue(it.hasNext());

    ByteString val = it.next();

    getRootConnection().disconnect(DisconnectReason.UNBIND, true, LocalizableMessage.EMPTY);

    return GeneralizedTimeSyntax.decodeGeneralizedTimeValue(val);
  }

}
