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
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions copyright 2011 ForgeRock AS.
 */
package org.opends.server.core;



import java.util.List;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.api.AuthenticationPolicy;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValues;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ResultCode;
import org.opends.server.util.StaticUtils;
import org.testng.annotations.AfterClass;

import static org.testng.Assert.*;


/**
 * A set of test cases for the Directory Server subentry password policy.
 */
public class SubentryPasswordPolicyTestCase
       extends CoreTestCase
{
  private static final String SUFFIX = "dc=example,dc=com";
  private static final String BASE = "ou=People," + SUFFIX;

  @BeforeClass()
  public void setUp() throws Exception
  {
    TestCaseUtils.startServer();
    TestCaseUtils.clearJEBackend(false, "userRoot", SUFFIX);

    InternalClientConnection connection =
         InternalClientConnection.getRootConnection();

    // Add suffix entry.
    DN suffixDN = DN.decode(SUFFIX);
    if (DirectoryServer.getEntry(suffixDN) == null)
    {
      Entry suffixEntry = StaticUtils.createEntry(suffixDN);
      AddOperation addOperation =
           connection.processAdd(suffixEntry.getDN(),
                                 suffixEntry.getObjectClasses(),
                                 suffixEntry.getUserAttributes(),
                                 suffixEntry.getOperationalAttributes());
      assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
      assertNotNull(DirectoryServer.getEntry(suffixEntry.getDN()));
    }

    // Add base entry.
    DN baseDN = DN.decode(BASE);
    if (DirectoryServer.getEntry(baseDN) == null)
    {
      Entry baseEntry = StaticUtils.createEntry(baseDN);
      AddOperation addOperation =
           connection.processAdd(baseEntry.getDN(),
                                 baseEntry.getObjectClasses(),
                                 baseEntry.getUserAttributes(),
                                 baseEntry.getOperationalAttributes());
      assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
      assertNotNull(DirectoryServer.getEntry(baseEntry.getDN()));
    }

    // Add test entry.
    Entry testEntry = TestCaseUtils.makeEntry(
         "dn: uid=rogasawara," + BASE,
         "objectclass: top",
         "objectclass: person",
         "objectclass: organizationalPerson",
         "objectclass: inetOrgPerson",
         "uid: rogasawara",
         "userpassword: password",
         "mail: rogasawara@example.com",
         "givenname: Rodney",
         "sn: Ogasawara",
         "cn: Rodney Ogasawara",
         "title: Sales, Director"
    );
    AddOperation addOperation =
         connection.processAdd(testEntry.getDN(),
                               testEntry.getObjectClasses(),
                               testEntry.getUserAttributes(),
                               testEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
    assertNotNull(DirectoryServer.getEntry(testEntry.getDN()));
  }

  @AfterClass
  public void cleanUp() throws Exception
  {
    TestCaseUtils.clearJEBackend(false, "userRoot", SUFFIX);
  }

  /**
   * Retrieves a set of invalid configurations that cannot be used to
   * initialize a password policy.
   *
   * @return  A set of invalid configurations that cannot be used to
   *          initialize a password policy.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "invalidConfigs")
  public Object[][] getInvalidConfigurations()
         throws Exception
  {
    List<Entry> entries = TestCaseUtils.makeEntries(
         "dn: cn=Temp Policy 1," + SUFFIX,
         "objectClass: top",
         "objectClass: pwdPolicy",
         "objectClass: subentry",
         "cn: Temp Policy 1",
         "subtreeSpecification: { base \"ou=people\" }",
         "pwdLockoutDuration: 300",
         "pwdMaxFailure: 3",
         "pwdMustChange: 1",
         "pwdAttribute: userPassword",
         "",
         "dn: cn=Temp Policy 2," + SUFFIX,
         "objectClass: top",
         "objectClass: pwdPolicy",
         "objectClass: subentry",
         "cn: Temp Policy 2",
         "subtreeSpecification: { base \"ou=people\" }",
         "pwdLockoutDuration: 300 seconds",
         "pwdMaxFailure: 3",
         "pwdMustChange: TRUE",
         "pwdAttribute: userPassword",
         "",
         "dn: cn=Temp Policy 3," + SUFFIX,
         "objectClass: top",
         "objectClass: pwdPolicy",
         "objectClass: subentry",
         "cn: Temp Policy 3",
         "subtreeSpecification: { base \"ou=people\" }",
         "pwdLockoutDuration: 300",
         "pwdMaxFailure: 3",
         "pwdMustChange: TRUE",
         "pwdAttribute: noSuchAttribute",
         "",
         "dn: cn=Temp Policy 4," + SUFFIX,
         "objectClass: top",
         "objectClass: pwdPolicy",
         "objectClass: subentry",
         "cn: Temp Policy 4",
         "subtreeSpecification: { base \"ou=people\" }",
         "pwdLockoutDuration: 300",
         "pwdMaxFailure: -3",
         "pwdMustChange: TRUE",
         "pwdAttribute: userPassword",
         "",
         "dn: cn=Temp Policy 5," + SUFFIX,
         "objectClass: top",
         "objectClass: pwdPolicy",
         "objectClass: subentry",
         "cn: Temp Policy 5",
         "subtreeSpecification: { base \"ou=people\" }",
         "pwdLockoutDuration: 2147483648",
         "pwdMaxFailure: 3",
         "pwdMustChange: TRUE",
         "pwdAttribute: userPassword"
         );

    Object[][] configEntries = new Object[entries.size()][1];
    for (int i=0; i < configEntries.length; i++)
    {
      configEntries[i] = new Object[] { entries.get(i) };
    }

    return configEntries;
  }

  /**
   * Ensures that password policy creation will fail when given
   * an invalid configuration.
   *
   * @param  e  The entry containing an invalid password policy
   *            configuration.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "invalidConfigs")
  public void testInvalidConfigurations(Entry e)
         throws Exception
  {
    InternalClientConnection connection =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         connection.processAdd(e.getDN(),
                               e.getObjectClasses(),
                               e.getUserAttributes(),
                               e.getOperationalAttributes());
    assertTrue(addOperation.getResultCode() != ResultCode.SUCCESS);
    assertNull(DirectoryServer.getEntry(e.getDN()));
  }

  /**
   * Ensures that password policy constructed from subentry
   * is active and has a valid configuration.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testValidConfiguration()
         throws Exception
  {
    PasswordPolicy defaultPolicy =
            DirectoryServer.getDefaultPasswordPolicy();
    assertNotNull(defaultPolicy);

    // The values are selected on a basis that they
    // should differ from default password policy.
    Entry policyEntry = TestCaseUtils.makeEntry(
         "dn: cn=Temp Policy," + SUFFIX,
         "objectClass: top",
         "objectClass: pwdPolicy",
         "objectClass: subentry",
         "cn: Temp Policy",
         "subtreeSpecification: { base \"ou=people\" }",
         "pwdLockoutDuration: 300",
         "pwdMaxFailure: 3",
         "pwdMustChange: TRUE",
         "pwdAttribute: authPassword",
         "pwdMinAge: 600",
         "pwdMaxAge: 2147483647",
         "pwdInHistory: 5",
         "pwdExpireWarning: 864000",
         "pwdGraceAuthNLimit: 3",
         "pwdFailureCountInterval: 3600",
         "pwdAllowUserChange: FALSE",
         "pwdSafeModify: TRUE"
    );

    InternalClientConnection connection =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         connection.processAdd(policyEntry.getDN(),
                               policyEntry.getObjectClasses(),
                               policyEntry.getUserAttributes(),
                               policyEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
    assertNotNull(DirectoryServer.getEntry(policyEntry.getDN()));

    PasswordPolicy policy = (PasswordPolicy) DirectoryServer.getAuthenticationPolicy(
            DN.decode("cn=Temp Policy," + SUFFIX));
    assertNotNull(policy);

    // Check all pwp attributes for correct values.
    assertEquals(policy.getLockoutDuration(), 300);
    assertEquals(policy.getLockoutFailureCount(), 3);
    assertEquals(policy.isForceChangeOnReset(), true);
    assertTrue(policy.getPasswordAttribute(
            ).getPrimaryName().equalsIgnoreCase(
            "authPassword"));
    assertEquals(policy.getMinPasswordAge(), 600);
    assertEquals(policy.getMaxPasswordAge(), 2147483647);
    assertEquals(policy.getPasswordHistoryCount(), 5);
    assertEquals(policy.getPasswordExpirationWarningInterval(), 864000);
    assertEquals(policy.getGraceLoginCount(), 3);
    assertEquals(policy.getLockoutFailureExpirationInterval(),
            3600);
    assertEquals(policy.isAllowUserPasswordChanges(), false);
    assertEquals(policy.isPasswordChangeRequiresCurrentPassword(), true);

    // Make sure this policy applies to the test entry
    // its supposed to target and that its the same
    // policy object as previously tested.
    Entry testEntry = DirectoryServer.getEntry(DN.decode(
            "uid=rogasawara," + BASE));
    assertNotNull(testEntry);

    AuthenticationPolicy statePolicy = AuthenticationPolicy.forUser(testEntry,
        false);
    assertNotNull(statePolicy);
    assertEquals(policy, statePolicy);

    // Make sure this policy is gone and default
    // policy is in effect instead.
    TestCaseUtils.deleteEntry(policyEntry.getDN());
    statePolicy = AuthenticationPolicy.forUser(testEntry, false);
    assertNotNull(statePolicy);
    assertEquals(defaultPolicy, statePolicy);
  }

  /**
   * Ensures that password policy pwdPolicySubentry
   * operational attribute reflects active password
   * policy for a given user entry.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testPasswordPolicySubentryAttribute()
         throws Exception
  {
    PasswordPolicy defaultPolicy =
            DirectoryServer.getDefaultPasswordPolicy();
    assertNotNull(defaultPolicy);

    Entry testEntry = DirectoryServer.getEntry(DN.decode(
            "uid=rogasawara," + BASE));
    assertNotNull(testEntry);

    AttributeType attrType = DirectoryServer.getAttributeType(
            "pwdpolicysubentry");

    // Make sure that default policy is in effect
    // for the user entry.
    assertTrue(testEntry.hasAttribute(attrType));
    assertTrue(testEntry.hasValue(attrType, null,
            AttributeValues.create(attrType,
            defaultPolicy.getDN(
            ).toString())));

    // Add new subentry policy with the
    // scope to apply to the user entry.
    Entry policyEntry = TestCaseUtils.makeEntry(
         "dn: cn=Temp Policy," + SUFFIX,
         "objectClass: top",
         "objectClass: pwdPolicy",
         "objectClass: subentry",
         "cn: Temp Policy",
         "subtreeSpecification: { base \"ou=people\" }",
         "pwdLockoutDuration: 300",
         "pwdMaxFailure: 3",
         "pwdMustChange: true",
         "pwdAttribute: userPassword"
        );

    InternalClientConnection connection =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         connection.processAdd(policyEntry.getDN(),
                               policyEntry.getObjectClasses(),
                               policyEntry.getUserAttributes(),
                               policyEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
    assertNotNull(DirectoryServer.getEntry(policyEntry.getDN()));

    // Make sure just added policy is in effect.
    testEntry = DirectoryServer.getEntry(DN.decode(
            "uid=rogasawara," + BASE));
    assertNotNull(testEntry);

    assertTrue(testEntry.hasAttribute(attrType));
    assertTrue(testEntry.hasValue(attrType, null,
            AttributeValues.create(attrType,
            "cn=Temp Policy," + SUFFIX)));

    // Remove subentry policy and make sure
    // default policy is in effect again.
    TestCaseUtils.deleteEntry(policyEntry.getDN());

    testEntry = DirectoryServer.getEntry(DN.decode(
            "uid=rogasawara," + BASE));
    assertNotNull(testEntry);

    assertTrue(testEntry.hasAttribute(attrType));
    assertTrue(testEntry.hasValue(attrType, null,
            AttributeValues.create(attrType,
            defaultPolicy.getDN(
            ).toString())));
  }
}
