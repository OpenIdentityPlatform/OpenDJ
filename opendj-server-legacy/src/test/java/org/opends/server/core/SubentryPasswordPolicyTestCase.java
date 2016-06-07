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
 * Copyright 2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.core;

import java.util.Collection;
import java.util.List;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.opends.server.TestCaseUtils;
import org.opends.server.api.AuthenticationPolicy;
import org.opends.server.api.PasswordValidator;
import org.opends.server.types.Entry;
import org.opends.server.util.StaticUtils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.testng.Assert.*;

/**
 * A set of test cases for the Directory Server subentry password policy.
 */
@SuppressWarnings("javadoc")
public class SubentryPasswordPolicyTestCase
       extends CoreTestCase
{
  private static final String SUFFIX = "dc=example,dc=com";
  private static final String BASE = "ou=People," + SUFFIX;

  @BeforeClass
  public void setUp() throws Exception
  {
    TestCaseUtils.startServer();
    TestCaseUtils.clearBackend("userRoot");

    // Add suffix entry.
    DN suffixDN = DN.valueOf(SUFFIX);
    if (DirectoryServer.getEntry(suffixDN) == null)
    {
      createEntry(suffixDN);
    }

    // Add base entry.
    DN baseDN = DN.valueOf(BASE);
    if (DirectoryServer.getEntry(baseDN) == null)
    {
      createEntry(baseDN);
    }

    TestCaseUtils.addEntry(
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
  }

  private void createEntry(DN suffixDN) throws Exception
  {
    Entry e = StaticUtils.createEntry(suffixDN);
    AddOperation addOperation = getRootConnection().processAdd(e);
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
    assertNotNull(DirectoryServer.getEntry(e.getName()));
  }

  @AfterClass
  public void cleanUp() throws Exception
  {
    TestCaseUtils.clearBackend("userRoot");
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
         "pwdAttribute: userPassword",
         "",
         "dn: cn=Temp Policy 6," + SUFFIX,
         "objectClass: top",
         "objectClass: pwdPolicy",
         "objectClass: pwdValidatorPolicy",
         "objectClass: subentry",
         "cn: Temp Policy 6",
         "subtreeSpecification: { base \"ou=people\" }",
         "pwdLockoutDuration: 300",
         "pwdMaxFailure: 3",
         "pwdMustChange: TRUE",
         "pwdAttribute: userPassword",
         "",
         "dn: cn=Temp Policy 7," + SUFFIX,
         "objectClass: top",
         "objectClass: pwdPolicy",
         "objectClass: pwdValidatorPolicy",
         "objectClass: subentry",
         "cn: Temp Policy 7",
         "subtreeSpecification: { base \"ou=people\" }",
         "pwdLockoutDuration: 300",
         "pwdMaxFailure: 3",
         "pwdMustChange: TRUE",
         "pwdAttribute: userPassword",
         "ds-cfg-password-validator: Not_A_DN",
         "",
         "dn: cn=Temp Policy 8," + SUFFIX,
         "objectClass: top",
         "objectClass: pwdPolicy",
         "objectClass: pwdValidatorPolicy",
         "objectClass: subentry",
         "cn: Temp Policy 8",
         "subtreeSpecification: { base \"ou=people\" }",
         "pwdLockoutDuration: 300",
         "pwdMaxFailure: 3",
         "pwdMustChange: TRUE",
         "pwdAttribute: userPassword",
         "ds-cfg-password-validator: cn=Unique Characters Inexistant,cn=Password Validators,cn=config"

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
    AddOperation addOperation = getRootConnection().processAdd(e);
    assertNotEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
    assertNull(DirectoryServer.getEntry(e.getName()));
  }

  /**
   * Ensures that password policy constructed from subentry
   * is active and has a valid configuration.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testValidConfiguration()
         throws Exception
  {
    PasswordPolicy defaultPolicy =
            DirectoryServer.getDefaultPasswordPolicy();
    assertNotNull(defaultPolicy);

    // The values are selected on a basis that they
    // should differ from default password policy.
    Entry policyEntry = TestCaseUtils.addEntry(
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

    PasswordPolicy policy = (PasswordPolicy) DirectoryServer.getAuthenticationPolicy(
            DN.valueOf("cn=Temp Policy," + SUFFIX));
    assertNotNull(policy);

    // Check all pwp attributes for correct values.
    assertEquals(policy.getLockoutDuration(), 300);
    assertEquals(policy.getLockoutFailureCount(), 3);
    assertEquals(policy.isForceChangeOnReset(), true);
    assertTrue(policy.getPasswordAttribute().getNameOrOID().equalsIgnoreCase("authPassword"));
    assertEquals(policy.getMinPasswordAge(), 600);
    assertEquals(policy.getMaxPasswordAge(), 2147483647);
    assertEquals(policy.getPasswordHistoryCount(), 5);
    assertEquals(policy.getPasswordExpirationWarningInterval(), 864000);
    assertEquals(policy.getGraceLoginCount(), 3);
    assertEquals(policy.getLockoutFailureExpirationInterval(), 3600);
    assertEquals(policy.isAllowUserPasswordChanges(), false);
    assertEquals(policy.isPasswordChangeRequiresCurrentPassword(), true);

    /* Check the password validator attributes for correct values.
     * The default unit-test config has a single Password validator which is
     * enabled for the default password policy.
     */
    Collection<PasswordValidator<?>> validators = policy.getPasswordValidators();
    assertEquals(validators.size(), 1);
    for (PasswordValidator<?> validator : validators)
    {
      assertTrue(validator.toString().startsWith("org.opends.server.extensions.TestPasswordValidator"));
    }

    // Make sure this policy applies to the test entry
    // its supposed to target and that its the same
    // policy object as previously tested.
    Entry testEntry = DirectoryServer.getEntry(DN.valueOf(
            "uid=rogasawara," + BASE));
    assertNotNull(testEntry);

    AuthenticationPolicy statePolicy = AuthenticationPolicy.forUser(testEntry,
        false);
    assertNotNull(statePolicy);
    assertEquals(policy, statePolicy);

    // Make sure this policy is gone and default
    // policy is in effect instead.
    TestCaseUtils.deleteEntry(policyEntry.getName());
    statePolicy = AuthenticationPolicy.forUser(testEntry, false);
    assertNotNull(statePolicy);
    assertEquals(defaultPolicy, statePolicy);
  }

  /**
   * Ensures that password policy constructed from subentry,
   * containing a password validator reference,
   * is active and has a valid configuration.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testValidConfigurationWithValidator()
      throws Exception
  {
    PasswordPolicy defaultPolicy =
        DirectoryServer.getDefaultPasswordPolicy();
    assertNotNull(defaultPolicy);

    // The values are selected on a basis that they
    // should differ from default password policy.
    Entry policyEntry = TestCaseUtils.addEntry(
        "dn: cn=Temp Validator Policy," + SUFFIX,
        "objectClass: top",
        "objectClass: pwdPolicy",
        "objectClass: pwdValidatorPolicy",
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
        "pwdSafeModify: TRUE",
        "ds-cfg-password-validator: cn=Unique Characters,cn=Password Validators,cn=config",
        "ds-cfg-password-validator: cn=Length-Based Password Validator,cn=Password Validators,cn=config"
    );

    PasswordPolicy policy = (PasswordPolicy) DirectoryServer.getAuthenticationPolicy(
        DN.valueOf("cn=Temp Validator Policy," + SUFFIX));
    assertNotNull(policy);

    // Check the password validator attributes for correct values.
    Collection<PasswordValidator<?>> validators = policy.getPasswordValidators();
    assertFalse(validators.isEmpty());
    assertEquals(validators.size(), 2);

    // Make sure this policy applies to the test entry
    // its supposed to target and that its the same
    // policy object as previously tested.
    Entry testEntry = DirectoryServer.getEntry(DN.valueOf("uid=rogasawara," + BASE));
    assertNotNull(testEntry);

    AuthenticationPolicy statePolicy = AuthenticationPolicy.forUser(testEntry, false);
    assertNotNull(statePolicy);
    assertEquals(policy, statePolicy);

    // Make sure this policy is gone and default
    // policy is in effect instead.
    TestCaseUtils.deleteEntry(policyEntry.getName());
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
  @Test
  public void testPasswordPolicySubentryAttribute()
         throws Exception
  {
    PasswordPolicy defaultPolicy = DirectoryServer.getDefaultPasswordPolicy();
    assertNotNull(defaultPolicy);

    Entry testEntry = DirectoryServer.getEntry(DN.valueOf("uid=rogasawara," + BASE));
    assertNotNull(testEntry);

    AttributeType attrType = DirectoryServer.getSchema().getAttributeType("pwdpolicysubentry");

    // Make sure that default policy is in effect
    // for the user entry.
    assertTrue(testEntry.hasAttribute(attrType));
    assertTrue(testEntry.hasValue(attrType,
            ByteString.valueOfUtf8(defaultPolicy.getDN().toString())));

    // Add new subentry policy with the
    // scope to apply to the user entry.
    Entry policyEntry = TestCaseUtils.addEntry(
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

    // Make sure just added policy is in effect.
    testEntry = DirectoryServer.getEntry(DN.valueOf("uid=rogasawara," + BASE));
    assertNotNull(testEntry);

    assertTrue(testEntry.hasAttribute(attrType));
    assertTrue(testEntry.hasValue(attrType, ByteString.valueOfUtf8("cn=Temp Policy," + SUFFIX)));

    // Remove subentry policy and make sure
    // default policy is in effect again.
    TestCaseUtils.deleteEntry(policyEntry.getName());

    testEntry = DirectoryServer.getEntry(DN.valueOf(
            "uid=rogasawara," + BASE));
    assertNotNull(testEntry);

    assertTrue(testEntry.hasAttribute(attrType));
    assertTrue(testEntry.hasValue(attrType,
            ByteString.valueOfUtf8(defaultPolicy.getDN().toString())));
  }
}
