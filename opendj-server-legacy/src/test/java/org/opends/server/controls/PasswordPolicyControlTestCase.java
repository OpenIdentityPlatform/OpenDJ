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
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.controls;

import java.util.List;

import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.controls.PasswordPolicyRequestControl;
import org.forgerock.opendj.ldap.controls.ProxiedAuthV2RequestControl;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.requests.SimpleBindRequest;
import org.opends.server.TestCaseUtils;
import org.opends.server.protocols.ldap.AddResponseProtocolOp;
import org.opends.server.protocols.ldap.CompareResponseProtocolOp;
import org.opends.server.protocols.ldap.DeleteResponseProtocolOp;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.protocols.ldap.ModifyDNResponseProtocolOp;
import org.opends.server.protocols.ldap.ModifyResponseProtocolOp;
import org.opends.server.protocols.ldap.SearchResultDoneProtocolOp;
import org.opends.server.tools.RemoteConnection;
import org.opends.server.types.Control;
import org.opends.server.types.DirectoryException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.*;
import static org.forgerock.opendj.ldap.ModificationType.*;
import static org.forgerock.opendj.ldap.requests.Requests.*;
import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.controls.PasswordPolicyErrorType.*;
import static org.opends.server.util.ServerConstants.*;
import static org.testng.Assert.*;

/**
 * This class contains test cases that verify the appropriate handling of the
 * password policy control as defined in draft-behera-ldap-password-policy.
 */
public class PasswordPolicyControlTestCase
    extends ControlsTestCase
{
  /**
   * Make sure that the server is running.
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
   * Tests that an appropriate password policy response control is returned for
   * an add operation when the user's password is in a "must change" state.
   * This test will also ensure that the bind response is also capable of
   * including the password policy response control with the "change after
   * reset" error type set.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddMustChange()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    setPasswordPolicyProp("--set", "force-change-on-add:true");

    TestCaseUtils.addEntry(
        "dn: uid=test.user,o=test",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: test.user",
        "givenName: Test",
        "sn: User",
        "cn: Test User",
        "userPassword: password",
        "ds-privilege-name: bypass-acl");

    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      SimpleBindRequest bindRequest = newSimpleBindRequest("uid=test.user,o=test", "password".toCharArray())
          .addControl(newPasswordPolicyControl());
      LDAPMessage bindMessage = conn.bind(bindRequest, false);
      assertTrue(passwordPolicyControlExists(bindMessage.getControls(), CHANGE_AFTER_RESET));

      AddRequest addRequest = newAddRequest("ou=People,o=test")
          .addAttribute("objectClass", "organizationalUnit")
          .addAttribute("ou", "People")
          .addControl(newPasswordPolicyControl());
      LDAPMessage message = conn.add(addRequest, false);

      AddResponseProtocolOp addResponse = message.getAddResponseProtocolOp();
      assertNotEquals(addResponse.getResultCode(), LDAPResultCode.SUCCESS);
      assertTrue(passwordPolicyControlExists(message.getControls(), CHANGE_AFTER_RESET));
    }
    finally
    {
      setPasswordPolicyProp("--set", "force-change-on-add:false");
    }
  }

  /**
   * Tests that an appropriate password policy response control is returned for
   * an add operation in which the proposed password is pre-encoded.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddPreEncodedPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    try (RemoteConnection c = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      c.bind("cn=Directory Manager", "password", newPasswordPolicyControl());

      AddRequest addRequest = newAddRequest("ou=uid=test.user,o=test")
          .addAttribute("objectClass", "inetOrgPerson")
          .addAttribute("uid", "test.user")
          .addAttribute("givenName", "Test")
          .addAttribute("sn", "User")
          .addAttribute("cn", "Test User")
          .addAttribute("userPassword", "{SSHA}0pZPpMIm6xSBIW4hGvR/72fjO4M9p3Ff1g7QFw==")
          .addControl(newPasswordPolicyControl());
      LDAPMessage message = c.add(addRequest, false);
      AddResponseProtocolOp addResponse = message.getAddResponseProtocolOp();
      assertNotEquals(addResponse.getResultCode(), LDAPResultCode.SUCCESS);
      assertTrue(passwordPolicyControlExists(message.getControls(), INSUFFICIENT_PASSWORD_QUALITY));
    }
  }

  private boolean passwordPolicyControlExists(List<Control> controls, PasswordPolicyErrorType expectedErrorType)
      throws DirectoryException
  {
    assertThat(controls).isNotEmpty();

    for(Control c : controls)
    {
      if (c.getOID().equals(OID_PASSWORD_POLICY_CONTROL))
      {
        PasswordPolicyResponseControl pwpControl;
        if(c instanceof LDAPControl)
        {
          pwpControl = PasswordPolicyResponseControl.DECODER.decode(c.isCritical(), ((LDAPControl) c).getValue());
        }
        else
        {
          pwpControl = (PasswordPolicyResponseControl)c;
        }
        assertEquals(pwpControl.getErrorType(), expectedErrorType);
        return true;
      }
    }
    return false;
  }


  /**
   * Tests that an appropriate password policy response control is returned for
   * an add operation in which the proposed password fails validation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddPasswordFailsValidation()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    setPasswordPolicyProp("--add", "password-validator:Length-Based Password Validator");

    try (RemoteConnection c = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      c.bind("cn=Directory Manager", "password", newPasswordPolicyControl());

      AddRequest addRequest = newAddRequest("ou=uid=test.user,o=test")
          .addAttribute("objectClass", "inetOrgPerson")
          .addAttribute("uid", "test.user")
          .addAttribute("givenName", "Test")
          .addAttribute("sn", "User")
          .addAttribute("cn", "Test User")
          .addAttribute("userPassword", "short")
          .addControl(newPasswordPolicyControl());
      LDAPMessage message = c.add(addRequest, false);
      AddResponseProtocolOp addResponse = message.getAddResponseProtocolOp();
      assertNotEquals(addResponse.getResultCode(), LDAPResultCode.SUCCESS);
      assertTrue(passwordPolicyControlExists(message.getControls(), INSUFFICIENT_PASSWORD_QUALITY));
    }
    finally
    {
      setPasswordPolicyProp("--remove", "password-validator:Length-Based Password Validator");
    }
  }



  /**
   * Tests that an appropriate password policy response control is returned for
   * a bind operation in which the user's account is locked due to
   * authentication failures.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testBindLockedDueToFailures()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    setPasswordPolicyProp("--set", "lockout-failure-count:3");

    TestCaseUtils.addEntry(
        "dn: uid=test.user,o=test",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: test.user",
        "givenName: Test",
        "sn: User",
        "cn: Test User",
        "userPassword: password",
        "ds-privilege-name: bypass-acl");

    try (RemoteConnection c = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      for (int i=1; i <= 3; i++)
      {
        SimpleBindRequest request =
            newSimpleBindRequest("uid=test.user,o=test", "wrong".getBytes())
            .addControl(newPasswordPolicyControl());
        LDAPMessage message = c.bind(request, false);
        assertNotEquals(message.getBindResponseProtocolOp().getResultCode(), LDAPResultCode.SUCCESS);
      }

      SimpleBindRequest request =
          newSimpleBindRequest("uid=test.user,o=test", "password".getBytes())
          .addControl(newPasswordPolicyControl());

      LDAPMessage message = c.bind(request, false);
      assertNotEquals(message.getBindResponseProtocolOp().getResultCode(), LDAPResultCode.SUCCESS);
      assertTrue(passwordPolicyControlExists(message.getControls(), ACCOUNT_LOCKED));
    }
    finally
    {
      setPasswordPolicyProp("--set", "lockout-failure-count:0");
    }
  }


  /**
   * Tests that an appropriate password policy response control is returned for
   * a compare operation when the user's password is in a "must change" state.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testCompareMustChange()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    setPasswordPolicyProp("--set", "force-change-on-add:true");

    TestCaseUtils.addEntry(
        "dn: uid=test.user,o=test",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: test.user",
        "givenName: Test",
        "sn: User",
        "cn: Test User",
        "userPassword: password",
        "ds-privilege-name: bypass-acl");

    try (RemoteConnection c = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      c.bind("uid=test.user,o=test", "password");

      CompareRequest request = newCompareRequest("o=test", "o", "test").addControl(newPasswordPolicyControl());
      LDAPMessage message = c.compare(request, false);
      CompareResponseProtocolOp compareResponse = message.getCompareResponseProtocolOp();
      assertNotEquals(compareResponse.getResultCode(), LDAPResultCode.SUCCESS);

      assertTrue(passwordPolicyControlExists(message.getControls(), CHANGE_AFTER_RESET));
    }
    finally
    {
      setPasswordPolicyProp("--set", "force-change-on-add:false");
    }
  }

  private PasswordPolicyRequestControl newPasswordPolicyControl()
  {
    return PasswordPolicyRequestControl.newControl(true);
  }



  /**
   * Tests that an appropriate password policy response control is returned for
   * a delete operation when the user's password is in a "must change" state.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDeleteMustChange()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    setPasswordPolicyProp("--set", "force-change-on-add:true");

    TestCaseUtils.addEntries(
        "dn: uid=test.user,o=test",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: test.user",
        "givenName: Test",
        "sn: User",
        "cn: Test User",
        "userPassword: password",
        "ds-privilege-name: bypass-acl",
        "",
        "dn: ou=People,o=test",
        "objectClass: top",
        "objectClass: organizationalUnit",
        "ou: People");

    try (RemoteConnection c = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      c.bind("uid=test.user,o=test", "password");

      DeleteRequest deleteRequest = newDeleteRequest("ou=People,o=test").addControl(newPasswordPolicyControl());
      LDAPMessage message = c.delete(deleteRequest, false);
      DeleteResponseProtocolOp deleteResponse = message.getDeleteResponseProtocolOp();
      assertNotEquals(deleteResponse.getResultCode(), LDAPResultCode.SUCCESS);

      assertTrue(passwordPolicyControlExists(message.getControls(), CHANGE_AFTER_RESET));
    }
    finally
    {
      setPasswordPolicyProp("--set", "force-change-on-add:false");
    }
  }




  /**
   * Creates test data for testModifyMustChange.
   *
   * Fields:
   *   <userDN> <entryDN> <changeAfterReset>
   *
   * @return Returns test data for testModifyMustChange.
   */
  @DataProvider(name = "testModifyMustChange")
  public Object[][] createTestModifyMustChange() {
    return new Object[][] {
        // User does not need to change their password.
        { "uid=test.admin,o=test", "uid=test.admin,o=test", false },
        { "uid=test.admin,o=test", "uid=test.user,o=test",  false },
        { "uid=test.admin,o=test", "o=test",                false },

        // User does need to change their password.
        { "uid=test.user,o=test",  "uid=test.admin,o=test", true },
        { "uid=test.user,o=test",  "uid=test.user,o=test",  true },
        { "uid=test.user,o=test",  "o=test",                true }
    };
  }



  /**
   * Tests that an appropriate password policy response control is
   * returned for a modify operation when the user's password is in a
   * "must change" state.
   *
   * @param userDN
   *          The name of the user to bind as.
   * @param entryDN
   *          The name of the entry to modify.
   * @param changeAfterReset
   *          {@code true} if change after reset is expected.
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test(dataProvider="testModifyMustChange")
  public void testModifyMustChange(String userDN, String entryDN, boolean changeAfterReset)
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    TestCaseUtils.addEntry(
        "dn: uid=test.admin,o=test",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: test.admin",
        "givenName: Test Admin",
        "sn: Admin",
        "cn: Test Admin",
        "userPassword: password",
        "ds-privilege-name: bypass-acl");

    setPasswordPolicyProp("--set", "force-change-on-add:true");

    TestCaseUtils.addEntry(
        "dn: uid=test.user,o=test",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: test.user",
        "givenName: Test",
        "sn: User",
        "cn: Test User",
        "userPassword: password",
        "ds-privilege-name: bypass-acl");

    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      conn.bind(userDN, "password");

      ModifyRequest modifyRequest =
          newModifyRequest(entryDN).addModification(REPLACE, "description", "foo")
          .addControl(newPasswordPolicyControl());
      LDAPMessage message = conn.modify(modifyRequest, false);
      ModifyResponseProtocolOp modifyResponse = message.getModifyResponseProtocolOp();
      if (changeAfterReset)
      {
        assertEquals(modifyResponse.getResultCode(), LDAPResultCode.CONSTRAINT_VIOLATION);
      }
      else
      {
        assertEquals(modifyResponse.getResultCode(), LDAPResultCode.SUCCESS);
      }

      List<Control> controls = message.getControls();
      assertThat(controls).isNotEmpty();

      boolean found = false;
      for(Control c : controls)
      {
        if (c.getOID().equals(OID_PASSWORD_POLICY_CONTROL))
        {
          PasswordPolicyResponseControl pwpControl;
          if(c instanceof LDAPControl)
          {
            pwpControl =
                PasswordPolicyResponseControl.DECODER.decode(c.isCritical(), ((LDAPControl)c).getValue());
          }
          else
          {
            pwpControl = (PasswordPolicyResponseControl)c;
          }
          if (changeAfterReset) {
            assertEquals(pwpControl.getErrorType(), CHANGE_AFTER_RESET);
          } else {
            assertNull(pwpControl.getErrorType());
          }
          found = true;
        }
      }
      assertTrue(found);
    }
    finally
    {
      setPasswordPolicyProp("--set", "force-change-on-add:false");
    }
  }



  /**
   * Tests that an appropriate password policy response control is
   * returned for a modify operation when the authorized user is forced to
   * change their own password before changing a different entry.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test
  public void testAuthzModifyMustChange()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String userDN = "uid=test.admin,o=test";
    String entryDN = "uid=test.user,o=test";
    String authzDN = "uid=authz.user,o=test";

    TestCaseUtils.addEntry(
        "dn: uid=test.admin,o=test",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: test.admin",
        "givenName: Test Admin",
        "sn: Admin",
        "cn: Test Admin",
        "userPassword: password",
        "ds-privilege-name: bypass-acl",
        "ds-privilege-name: proxied-auth");

    TestCaseUtils.addEntry(
        "dn: uid=test.user,o=test",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: test.user",
        "givenName: Test",
        "sn: User",
        "cn: Test User",
        "userPassword: password",
        "ds-privilege-name: bypass-acl");

    setPasswordPolicyProp("--set", "force-change-on-add:true");

    TestCaseUtils.addEntry(
        "dn: uid=authz.user,o=test",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: authz.user",
        "givenName: Authz",
        "sn: User",
        "cn: Authz User",
        "userPassword: password",
        "ds-privilege-name: bypass-acl");

    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      conn.bind(userDN, "password");

      ModifyRequest modifyRequest = newModifyRequest(entryDN)
          .addModification(REPLACE, "description", "foo")
          .addControl(newPasswordPolicyControl())
          .addControl(ProxiedAuthV2RequestControl.newControl("dn:" + authzDN));
      LDAPMessage message = conn.modify(modifyRequest, false);
      ModifyResponseProtocolOp modifyResponse = message.getModifyResponseProtocolOp();
      assertEquals(modifyResponse.getResultCode(), LDAPResultCode.CONSTRAINT_VIOLATION);

      assertTrue(passwordPolicyControlExists(message.getControls(), CHANGE_AFTER_RESET));
    }
    finally
    {
      setPasswordPolicyProp("--set", "force-change-on-add:false");
    }
  }



  /**
   * Tests that an appropriate password policy response control is returned for
   * a modify operation when users do not have permission to change their own
   * passwords.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testModifyCannotChange()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    setPasswordPolicyProp("--set", "allow-user-password-changes:false");

    TestCaseUtils.addEntry(
        "dn: uid=test.user,o=test",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: test.user",
        "givenName: Test",
        "sn: User",
        "cn: Test User",
        "userPassword: password",
        "ds-privilege-name: bypass-acl");

    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      conn.bind("uid=test.user,o=test", "password");

      ModifyRequest modifyRequest = newModifyRequest("uid=test.user,o=test")
          .addModification(REPLACE, "userPassword", "newpassword")
          .addControl(newPasswordPolicyControl());
      LDAPMessage message = conn.modify(modifyRequest, false);
      ModifyResponseProtocolOp modifyResponse = message.getModifyResponseProtocolOp();
      assertNotEquals(modifyResponse.getResultCode(), LDAPResultCode.SUCCESS);

      assertTrue(passwordPolicyControlExists(message.getControls(), PASSWORD_MOD_NOT_ALLOWED));
    }
    finally
    {
      setPasswordPolicyProp("--set", "allow-user-password-changes:true");
    }
  }

  /**
   * Tests that an appropriate password policy response control is returned for
   * a modify operation when the proposed password is in the user's password
   * history.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testModifyPasswordInHistory()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    setPasswordPolicyProp("--set", "password-history-count:5");

    TestCaseUtils.addEntry(
        "dn: uid=test.user,o=test",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: test.user",
        "givenName: Test",
        "sn: User",
        "cn: Test User",
        "userPassword: password",
        "ds-privilege-name: bypass-acl");

    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      conn.bind("uid=test.user,o=test", "password");

      ModifyRequest modifyRequest = newModifyRequest("uid=test.user,o=test")
          .addModification(REPLACE, "userPassword", "password")
          .addControl(newPasswordPolicyControl());
      LDAPMessage message = conn.modify(modifyRequest, false);
      ModifyResponseProtocolOp modifyResponse = message.getModifyResponseProtocolOp();
      assertNotEquals(modifyResponse.getResultCode(), LDAPResultCode.SUCCESS);

      assertTrue(passwordPolicyControlExists(message.getControls(), PASSWORD_IN_HISTORY));
    }
    finally
    {
      setPasswordPolicyProp("--set", "password-history-count:0");
    }
  }



  /**
   * Tests that an appropriate password policy response control is returned for
   * a modify operation when the user didn't provide their current password when
   * it was required.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testModifyMissingCurrentPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    setPasswordPolicyProp("--set", "password-change-requires-current-password:true");

    TestCaseUtils.addEntry(
        "dn: uid=test.user,o=test",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: test.user",
        "givenName: Test",
        "sn: User",
        "cn: Test User",
        "userPassword: password",
        "ds-privilege-name: bypass-acl");

    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      conn.bind("uid=test.user,o=test", "password");

      ModifyRequest modifyRequest = newModifyRequest("uid=test.user,o=test")
          .addModification(REPLACE, "userPassword", "newpassword")
          .addControl(newPasswordPolicyControl());
      LDAPMessage message = conn.modify(modifyRequest, false);
      ModifyResponseProtocolOp modifyResponse = message.getModifyResponseProtocolOp();
      assertNotEquals(modifyResponse.getResultCode(), LDAPResultCode.SUCCESS);

      assertTrue(passwordPolicyControlExists(message.getControls(), MUST_SUPPLY_OLD_PASSWORD));
    }
    finally
    {
      setPasswordPolicyProp("--set", "password-change-requires-current-password:false");
    }
  }



  /**
   * Tests that an appropriate password policy response control is returned for
   * a modify operation when the user tried to perform multiple password changes
   * without respecting the minimum age.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testModifyMinimumPasswordAge()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    setPasswordPolicyProp("--set", "min-password-age:24 hours");

    TestCaseUtils.addEntry(
        "dn: uid=test.user,o=test",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: test.user",
        "givenName: Test",
        "sn: User",
        "cn: Test User",
        "userPassword: password",
        "ds-privilege-name: bypass-acl");

    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      conn.bind("uid=test.user,o=test", "password");

      ModifyRequest modifyRequest = newModifyRequest("uid=test.user,o=test")
          .addModification(REPLACE, "userPassword", "newpassword")
          .addControl(newPasswordPolicyControl());
      LDAPMessage message = conn.modify(modifyRequest, false);
      ModifyResponseProtocolOp modifyResponse = message.getModifyResponseProtocolOp();
      assertNotEquals(modifyResponse.getResultCode(), LDAPResultCode.SUCCESS);

      assertTrue(passwordPolicyControlExists(message.getControls(), PASSWORD_TOO_YOUNG));
    }
    finally
    {
      setPasswordPolicyProp("--set", "min-password-age:0 seconds");
    }
  }



  /**
   * Tests that an appropriate password policy response control is returned for
   * a modify DN operation when the user's password is in a "must change" state.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testModifyDNMustChange()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    setPasswordPolicyProp("--set", "force-change-on-add:true");

    TestCaseUtils.addEntries(
        "dn: uid=test.user,o=test",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: test.user",
        "givenName: Test",
        "sn: User",
        "cn: Test User",
        "userPassword: password",
        "ds-privilege-name: bypass-acl",
        "",
        "dn: ou=People,o=test",
        "objectClass: top",
        "objectClass: organizationalUnit",
        "ou: People");

    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      conn.bind("uid=test.user,o=test", "password");

      ModifyDNRequest modifyDNRequest = newModifyDNRequest("ou=People,o=test", "ou=Users")
          .setDeleteOldRDN(true)
          .addControl(newPasswordPolicyControl());
      LDAPMessage message = conn.modifyDN(modifyDNRequest, false);
      ModifyDNResponseProtocolOp modifyDNResponse = message.getModifyDNResponseProtocolOp();
      assertNotEquals(modifyDNResponse.getResultCode(), LDAPResultCode.SUCCESS);

      assertTrue(passwordPolicyControlExists(message.getControls(), CHANGE_AFTER_RESET));
    }
    finally
    {
      setPasswordPolicyProp("--set", "force-change-on-add:false");
    }
  }

  /**
   * Tests that an appropriate password policy response control is returned for
   * a search operation when the user's password is in a "must change" state.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testSearchMustChange()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    setPasswordPolicyProp("--set", "force-change-on-add:true");

    TestCaseUtils.addEntry(
        "dn: uid=test.user,o=test",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: test.user",
        "givenName: Test",
        "sn: User",
        "cn: Test User",
        "userPassword: password",
        "ds-privilege-name: bypass-acl");

    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      conn.bind("uid=test.user,o=test", "password");

      SearchRequest searchRequest = newSearchRequest("o=test", SearchScope.BASE_OBJECT, "(objectclass=*)")
          .addControl(newPasswordPolicyControl());
      conn.search(searchRequest);
      LDAPMessage message = conn.readMessage();
      SearchResultDoneProtocolOp searchDone = message.getSearchResultDoneProtocolOp();
      assertNotEquals(searchDone.getResultCode(), LDAPResultCode.SUCCESS);

      assertTrue(passwordPolicyControlExists(message.getControls(), CHANGE_AFTER_RESET));
    }
    finally
    {
      setPasswordPolicyProp("--set", "force-change-on-add:false");
    }
  }

  private void setPasswordPolicyProp(String arg, String value)
  {
    TestCaseUtils.dsconfig("set-password-policy-prop", "--policy-name", "Default Password Policy", arg, value);
  }
}
