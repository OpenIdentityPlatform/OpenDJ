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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.controls;



import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedHashSet;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.protocols.asn1.*;
import org.opends.server.protocols.ldap.*;
import org.opends.server.types.*;
import org.opends.server.tools.dsconfig.DSConfig;

import static org.testng.Assert.*;

import static org.opends.server.util.ServerConstants.*;



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
  @BeforeClass()
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
  @Test()
  public void testAddMustChange()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "force-change-on-add:true");

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

    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    try
    {
      ArrayList<LDAPControl> controls = new ArrayList<LDAPControl>();
      controls.add(new LDAPControl(OID_PASSWORD_POLICY_CONTROL, true));

      BindRequestProtocolOp bindRequest = new BindRequestProtocolOp(
           new ASN1OctetString("uid=test.user,o=test"), 3,
           new ASN1OctetString("password"));
      LDAPMessage message = new LDAPMessage(1, bindRequest, controls);
      w.writeElement(message.encode());

      message = LDAPMessage.decode(r.readElement().decodeAsSequence());
      BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
      assertEquals(bindResponse.getResultCode(), LDAPResultCode.SUCCESS);

      controls = message.getControls();
      assertNotNull(controls);
      assertFalse(controls.isEmpty());

      boolean found = false;
      for (LDAPControl c : controls)
      {
        if (c.getOID().equals(OID_PASSWORD_POLICY_CONTROL))
        {
          PasswordPolicyResponseControl pwpControl =
               PasswordPolicyResponseControl.decodeControl(c.getControl());
          assertEquals(pwpControl.getErrorType(),
                       PasswordPolicyErrorType.CHANGE_AFTER_RESET);
          found = true;
        }
      }
      assertTrue(found);


      ArrayList<RawAttribute> rawAttrs = new ArrayList<RawAttribute>();
      rawAttrs.add(RawAttribute.create("objectClass", "organizationalUnit"));
      rawAttrs.add(RawAttribute.create("ou", "People"));

      AddRequestProtocolOp addRequest = new AddRequestProtocolOp(
           new ASN1OctetString("ou=People,o=test"), rawAttrs);

      controls = new ArrayList<LDAPControl>();
      controls.add(new LDAPControl(OID_PASSWORD_POLICY_CONTROL, true));

      message = new LDAPMessage(2, addRequest, controls);
      w.writeElement(message.encode());

      message = LDAPMessage.decode(r.readElement().decodeAsSequence());
      AddResponseProtocolOp addResponse = message.getAddResponseProtocolOp();
      assertFalse(addResponse.getResultCode() == LDAPResultCode.SUCCESS);

      controls = message.getControls();
      assertNotNull(controls);
      assertFalse(controls.isEmpty());

      found = false;
      for (LDAPControl c : controls)
      {
        if (c.getOID().equals(OID_PASSWORD_POLICY_CONTROL))
        {
          PasswordPolicyResponseControl pwpControl =
               PasswordPolicyResponseControl.decodeControl(c.getControl());
          assertEquals(pwpControl.getErrorType(),
                       PasswordPolicyErrorType.CHANGE_AFTER_RESET);
          found = true;
        }
      }
      assertTrue(found);
    }
    finally
    {
      TestCaseUtils.dsconfig(
        "set-password-policy-prop",
        "--policy-name", "Default Password Policy",
        "--set", "force-change-on-add:false");

      try
      {
        s.close();
      } catch (Exception e) {}
    }
  }



  /**
   * Tests that an appropriate password policy response control is returned for
   * an add operation in which the proposed password is pre-encoded.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddPreEncodedPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    try
    {
      BindRequestProtocolOp bindRequest = new BindRequestProtocolOp(
           new ASN1OctetString("cn=Directory Manager"), 3,
           new ASN1OctetString("password"));
      LDAPMessage message = new LDAPMessage(1, bindRequest);
      w.writeElement(message.encode());

      message = LDAPMessage.decode(r.readElement().decodeAsSequence());
      BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
      assertEquals(bindResponse.getResultCode(), LDAPResultCode.SUCCESS);


      ArrayList<RawAttribute> rawAttrs = new ArrayList<RawAttribute>();
      rawAttrs.add(RawAttribute.create("objectClass", "inetOrgPerson"));
      rawAttrs.add(RawAttribute.create("uid", "test.user"));
      rawAttrs.add(RawAttribute.create("givenName", "Test"));
      rawAttrs.add(RawAttribute.create("sn", "User"));
      rawAttrs.add(RawAttribute.create("cn", "Test User"));
      rawAttrs.add(RawAttribute.create("userPassword",
                        "{SSHA}0pZPpMIm6xSBIW4hGvR/72fjO4M9p3Ff1g7QFw=="));

      AddRequestProtocolOp addRequest = new AddRequestProtocolOp(
           new ASN1OctetString("ou=uid=test.user,o=test"), rawAttrs);

      ArrayList<LDAPControl> controls = new ArrayList<LDAPControl>();
      controls.add(new LDAPControl(OID_PASSWORD_POLICY_CONTROL, true));

      message = new LDAPMessage(2, addRequest, controls);
      w.writeElement(message.encode());

      message = LDAPMessage.decode(r.readElement().decodeAsSequence());
      AddResponseProtocolOp addResponse = message.getAddResponseProtocolOp();
      assertFalse(addResponse.getResultCode() == LDAPResultCode.SUCCESS);

      controls = message.getControls();
      assertNotNull(controls);
      assertFalse(controls.isEmpty());

      boolean found = false;
      for (LDAPControl c : controls)
      {
        if (c.getOID().equals(OID_PASSWORD_POLICY_CONTROL))
        {
          PasswordPolicyResponseControl pwpControl =
               PasswordPolicyResponseControl.decodeControl(c.getControl());
          assertEquals(pwpControl.getErrorType(),
                       PasswordPolicyErrorType.INSUFFICIENT_PASSWORD_QUALITY);
          found = true;
        }
      }
      assertTrue(found);
    }
    finally
    {
      try
      {
        s.close();
      } catch (Exception e) {}
    }
  }



  /**
   * Tests that an appropriate password policy response control is returned for
   * an add operation in which the proposed password fails validation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddPasswordFailsValidation()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--add", "password-validator-dn:cn=Length-Based Password Validator," +
           "cn=Password Validators,cn=config");

    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    try
    {
      BindRequestProtocolOp bindRequest = new BindRequestProtocolOp(
           new ASN1OctetString("cn=Directory Manager"), 3,
           new ASN1OctetString("password"));
      LDAPMessage message = new LDAPMessage(1, bindRequest);
      w.writeElement(message.encode());

      message = LDAPMessage.decode(r.readElement().decodeAsSequence());
      BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
      assertEquals(bindResponse.getResultCode(), LDAPResultCode.SUCCESS);


      ArrayList<RawAttribute> rawAttrs = new ArrayList<RawAttribute>();
      rawAttrs.add(RawAttribute.create("objectClass", "inetOrgPerson"));
      rawAttrs.add(RawAttribute.create("uid", "test.user"));
      rawAttrs.add(RawAttribute.create("givenName", "Test"));
      rawAttrs.add(RawAttribute.create("sn", "User"));
      rawAttrs.add(RawAttribute.create("cn", "Test User"));
      rawAttrs.add(RawAttribute.create("userPassword", "short"));

      AddRequestProtocolOp addRequest = new AddRequestProtocolOp(
           new ASN1OctetString("ou=uid=test.user,o=test"), rawAttrs);

      ArrayList<LDAPControl> controls = new ArrayList<LDAPControl>();
      controls.add(new LDAPControl(OID_PASSWORD_POLICY_CONTROL, true));

      message = new LDAPMessage(2, addRequest, controls);
      w.writeElement(message.encode());

      message = LDAPMessage.decode(r.readElement().decodeAsSequence());
      AddResponseProtocolOp addResponse = message.getAddResponseProtocolOp();
      assertFalse(addResponse.getResultCode() == LDAPResultCode.SUCCESS);

      controls = message.getControls();
      assertNotNull(controls);
      assertFalse(controls.isEmpty());

      boolean found = false;
      for (LDAPControl c : controls)
      {
        if (c.getOID().equals(OID_PASSWORD_POLICY_CONTROL))
        {
          PasswordPolicyResponseControl pwpControl =
               PasswordPolicyResponseControl.decodeControl(c.getControl());
          assertEquals(pwpControl.getErrorType(),
                       PasswordPolicyErrorType.INSUFFICIENT_PASSWORD_QUALITY);
          found = true;
        }
      }
      assertTrue(found);
    }
    finally
    {
      TestCaseUtils.dsconfig(
        "set-password-policy-prop",
        "--policy-name", "Default Password Policy",
        "--remove", "password-validator-dn:cn=Length-Based Password " +
             "Validator,cn=Password Validators,cn=config");

      try
      {
        s.close();
      } catch (Exception e) {}
    }
  }



  /**
   * Tests that an appropriate password policy response control is returned for
   * a bind operation in which the user's account is locked due to
   * authentication failures.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testBindLockedDueToFailures()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "lockout-failure-count:3");

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

    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    try
    {
      BindRequestProtocolOp bindRequest = new BindRequestProtocolOp(
           new ASN1OctetString("uid=test.user,o=test"), 3,
           new ASN1OctetString("wrong"));

      for (int i=1; i <= 3; i++)
      {
        LDAPMessage message = new LDAPMessage(1, bindRequest);
        w.writeElement(message.encode());

        message = LDAPMessage.decode(r.readElement().decodeAsSequence());
        BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
        assertFalse(bindResponse.getResultCode() == LDAPResultCode.SUCCESS);
      }

      bindRequest = new BindRequestProtocolOp(
           new ASN1OctetString("uid=test.user,o=test"), 3,
           new ASN1OctetString("password"));

      ArrayList<LDAPControl> controls = new ArrayList<LDAPControl>();
      controls.add(new LDAPControl(OID_PASSWORD_POLICY_CONTROL, true));

      LDAPMessage message = new LDAPMessage(4, bindRequest, controls);
      w.writeElement(message.encode());

      message = LDAPMessage.decode(r.readElement().decodeAsSequence());
      BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
      assertFalse(bindResponse.getResultCode() == LDAPResultCode.SUCCESS);

      controls = message.getControls();
      assertNotNull(controls);
      assertFalse(controls.isEmpty());

      boolean found = false;
      for (LDAPControl c : controls)
      {
        if (c.getOID().equals(OID_PASSWORD_POLICY_CONTROL))
        {
          PasswordPolicyResponseControl pwpControl =
               PasswordPolicyResponseControl.decodeControl(c.getControl());
          assertEquals(pwpControl.getErrorType(),
                       PasswordPolicyErrorType.ACCOUNT_LOCKED);
          found = true;
        }
      }
      assertTrue(found);
    }
    finally
    {
      TestCaseUtils.dsconfig(
        "set-password-policy-prop",
        "--policy-name", "Default Password Policy",
        "--set", "lockout-failure-count:0");

      try
      {
        s.close();
      } catch (Exception e) {}
    }
  }



  /**
   * Tests that an appropriate password policy response control is returned for
   * a compare operation when the user's password is in a "must change" state.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testCompareMustChange()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "force-change-on-add:true");

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

    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    try
    {
      BindRequestProtocolOp bindRequest = new BindRequestProtocolOp(
           new ASN1OctetString("uid=test.user,o=test"), 3,
           new ASN1OctetString("password"));
      LDAPMessage message = new LDAPMessage(1, bindRequest);
      w.writeElement(message.encode());

      message = LDAPMessage.decode(r.readElement().decodeAsSequence());
      BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
      assertEquals(bindResponse.getResultCode(), LDAPResultCode.SUCCESS);


      CompareRequestProtocolOp compareRequest =
           new CompareRequestProtocolOp(new ASN1OctetString("o=test"), "o",
                                        new ASN1OctetString("test"));

      ArrayList<LDAPControl> controls = new ArrayList<LDAPControl>();
      controls.add(new LDAPControl(OID_PASSWORD_POLICY_CONTROL, true));

      message = new LDAPMessage(2, compareRequest, controls);
      w.writeElement(message.encode());

      message = LDAPMessage.decode(r.readElement().decodeAsSequence());
      CompareResponseProtocolOp compareResponse =
           message.getCompareResponseProtocolOp();
      assertFalse(compareResponse.getResultCode() == LDAPResultCode.SUCCESS);

      controls = message.getControls();
      assertNotNull(controls);
      assertFalse(controls.isEmpty());

      boolean found = false;
      for (LDAPControl c : controls)
      {
        if (c.getOID().equals(OID_PASSWORD_POLICY_CONTROL))
        {
          PasswordPolicyResponseControl pwpControl =
               PasswordPolicyResponseControl.decodeControl(c.getControl());
          assertEquals(pwpControl.getErrorType(),
                       PasswordPolicyErrorType.CHANGE_AFTER_RESET);
          found = true;
        }
      }
      assertTrue(found);
    }
    finally
    {
      TestCaseUtils.dsconfig(
        "set-password-policy-prop",
        "--policy-name", "Default Password Policy",
        "--set", "force-change-on-add:false");

      try
      {
        s.close();
      } catch (Exception e) {}
    }
  }



  /**
   * Tests that an appropriate password policy response control is returned for
   * a delete operation when the user's password is in a "must change" state.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDeleteMustChange()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "force-change-on-add:true");

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

    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    try
    {
      BindRequestProtocolOp bindRequest = new BindRequestProtocolOp(
           new ASN1OctetString("uid=test.user,o=test"), 3,
           new ASN1OctetString("password"));
      LDAPMessage message = new LDAPMessage(1, bindRequest);
      w.writeElement(message.encode());

      message = LDAPMessage.decode(r.readElement().decodeAsSequence());
      BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
      assertEquals(bindResponse.getResultCode(), LDAPResultCode.SUCCESS);


      DeleteRequestProtocolOp deleteRequest =
           new DeleteRequestProtocolOp(new ASN1OctetString("ou=People,o=test"));

      ArrayList<LDAPControl> controls = new ArrayList<LDAPControl>();
      controls.add(new LDAPControl(OID_PASSWORD_POLICY_CONTROL, true));

      message = new LDAPMessage(2, deleteRequest, controls);
      w.writeElement(message.encode());

      message = LDAPMessage.decode(r.readElement().decodeAsSequence());
      DeleteResponseProtocolOp deleteResponse =
           message.getDeleteResponseProtocolOp();
      assertFalse(deleteResponse.getResultCode() == LDAPResultCode.SUCCESS);

      controls = message.getControls();
      assertNotNull(controls);
      assertFalse(controls.isEmpty());

      boolean found = false;
      for (LDAPControl c : controls)
      {
        if (c.getOID().equals(OID_PASSWORD_POLICY_CONTROL))
        {
          PasswordPolicyResponseControl pwpControl =
               PasswordPolicyResponseControl.decodeControl(c.getControl());
          assertEquals(pwpControl.getErrorType(),
                       PasswordPolicyErrorType.CHANGE_AFTER_RESET);
          found = true;
        }
      }
      assertTrue(found);
    }
    finally
    {
      TestCaseUtils.dsconfig(
        "set-password-policy-prop",
        "--policy-name", "Default Password Policy",
        "--set", "force-change-on-add:false");

      try
      {
        s.close();
      } catch (Exception e) {}
    }
  }



  /**
   * Tests that an appropriate password policy response control is returned for
   * a modify operation when the user's password is in a "must change" state.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testModifyMustChange()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "force-change-on-add:true");

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

    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    try
    {
      BindRequestProtocolOp bindRequest = new BindRequestProtocolOp(
           new ASN1OctetString("uid=test.user,o=test"), 3,
           new ASN1OctetString("password"));
      LDAPMessage message = new LDAPMessage(1, bindRequest);
      w.writeElement(message.encode());

      message = LDAPMessage.decode(r.readElement().decodeAsSequence());
      BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
      assertEquals(bindResponse.getResultCode(), LDAPResultCode.SUCCESS);


      ArrayList<RawModification> mods = new ArrayList<RawModification>();
      mods.add(RawModification.create(ModificationType.REPLACE, "description",
                                      "foo"));

      ModifyRequestProtocolOp modifyRequest =
           new ModifyRequestProtocolOp(new ASN1OctetString("o=test"), mods);

      ArrayList<LDAPControl> controls = new ArrayList<LDAPControl>();
      controls.add(new LDAPControl(OID_PASSWORD_POLICY_CONTROL, true));

      message = new LDAPMessage(2, modifyRequest, controls);
      w.writeElement(message.encode());

      message = LDAPMessage.decode(r.readElement().decodeAsSequence());
      ModifyResponseProtocolOp modifyResponse =
           message.getModifyResponseProtocolOp();
      assertFalse(modifyResponse.getResultCode() == LDAPResultCode.SUCCESS);

      controls = message.getControls();
      assertNotNull(controls);
      assertFalse(controls.isEmpty());

      boolean found = false;
      for (LDAPControl c : controls)
      {
        if (c.getOID().equals(OID_PASSWORD_POLICY_CONTROL))
        {
          PasswordPolicyResponseControl pwpControl =
               PasswordPolicyResponseControl.decodeControl(c.getControl());
          assertEquals(pwpControl.getErrorType(),
                       PasswordPolicyErrorType.CHANGE_AFTER_RESET);
          found = true;
        }
      }
      assertTrue(found);
    }
    finally
    {
      TestCaseUtils.dsconfig(
        "set-password-policy-prop",
        "--policy-name", "Default Password Policy",
        "--set", "force-change-on-add:false");

      try
      {
        s.close();
      } catch (Exception e) {}
    }
  }



  /**
   * Tests that an appropriate password policy response control is returned for
   * a modify operation when users do not have permission to change their own
   * passwords.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testModifyCannotChange()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "allow-user-password-changes:false");

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

    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    try
    {
      BindRequestProtocolOp bindRequest = new BindRequestProtocolOp(
           new ASN1OctetString("uid=test.user,o=test"), 3,
           new ASN1OctetString("password"));
      LDAPMessage message = new LDAPMessage(1, bindRequest);
      w.writeElement(message.encode());

      message = LDAPMessage.decode(r.readElement().decodeAsSequence());
      BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
      assertEquals(bindResponse.getResultCode(), LDAPResultCode.SUCCESS);


      ArrayList<RawModification> mods = new ArrayList<RawModification>();
      mods.add(RawModification.create(ModificationType.REPLACE, "userPassword",
                                      "newpassword"));

      ModifyRequestProtocolOp modifyRequest =
           new ModifyRequestProtocolOp(
                    new ASN1OctetString("uid=test.user,o=test"), mods);

      ArrayList<LDAPControl> controls = new ArrayList<LDAPControl>();
      controls.add(new LDAPControl(OID_PASSWORD_POLICY_CONTROL, true));

      message = new LDAPMessage(2, modifyRequest, controls);
      w.writeElement(message.encode());

      message = LDAPMessage.decode(r.readElement().decodeAsSequence());
      ModifyResponseProtocolOp modifyResponse =
           message.getModifyResponseProtocolOp();
      assertFalse(modifyResponse.getResultCode() == LDAPResultCode.SUCCESS);

      controls = message.getControls();
      assertNotNull(controls);
      assertFalse(controls.isEmpty());

      boolean found = false;
      for (LDAPControl c : controls)
      {
        if (c.getOID().equals(OID_PASSWORD_POLICY_CONTROL))
        {
          PasswordPolicyResponseControl pwpControl =
               PasswordPolicyResponseControl.decodeControl(c.getControl());
          assertEquals(pwpControl.getErrorType(),
                       PasswordPolicyErrorType.PASSWORD_MOD_NOT_ALLOWED);
          found = true;
        }
      }
      assertTrue(found);
    }
    finally
    {
      TestCaseUtils.dsconfig(
        "set-password-policy-prop",
        "--policy-name", "Default Password Policy",
        "--set", "allow-user-password-changes:true");

      try
      {
        s.close();
      } catch (Exception e) {}
    }
  }



  /**
   * Tests that an appropriate password policy response control is returned for
   * a modify operation when the proposed password is in the user's password
   * history.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testModifyPasswordInHistory()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "password-history-count:5");

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

    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    try
    {
      BindRequestProtocolOp bindRequest = new BindRequestProtocolOp(
           new ASN1OctetString("uid=test.user,o=test"), 3,
           new ASN1OctetString("password"));
      LDAPMessage message = new LDAPMessage(1, bindRequest);
      w.writeElement(message.encode());

      message = LDAPMessage.decode(r.readElement().decodeAsSequence());
      BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
      assertEquals(bindResponse.getResultCode(), LDAPResultCode.SUCCESS);


      ArrayList<RawModification> mods = new ArrayList<RawModification>();
      mods.add(RawModification.create(ModificationType.REPLACE, "userPassword",
                                      "password"));

      ModifyRequestProtocolOp modifyRequest =
           new ModifyRequestProtocolOp(
                    new ASN1OctetString("uid=test.user,o=test"), mods);

      ArrayList<LDAPControl> controls = new ArrayList<LDAPControl>();
      controls.add(new LDAPControl(OID_PASSWORD_POLICY_CONTROL, true));

      message = new LDAPMessage(2, modifyRequest, controls);
      w.writeElement(message.encode());

      message = LDAPMessage.decode(r.readElement().decodeAsSequence());
      ModifyResponseProtocolOp modifyResponse =
           message.getModifyResponseProtocolOp();
      assertFalse(modifyResponse.getResultCode() == LDAPResultCode.SUCCESS);

      controls = message.getControls();
      assertNotNull(controls);
      assertFalse(controls.isEmpty());

      boolean found = false;
      for (LDAPControl c : controls)
      {
        if (c.getOID().equals(OID_PASSWORD_POLICY_CONTROL))
        {
          PasswordPolicyResponseControl pwpControl =
               PasswordPolicyResponseControl.decodeControl(c.getControl());
          assertEquals(pwpControl.getErrorType(),
                       PasswordPolicyErrorType.PASSWORD_IN_HISTORY);
          found = true;
        }
      }
      assertTrue(found);
    }
    finally
    {
      TestCaseUtils.dsconfig(
        "set-password-policy-prop",
        "--policy-name", "Default Password Policy",
        "--set", "password-history-count:0");

      try
      {
        s.close();
      } catch (Exception e) {}
    }
  }



  /**
   * Tests that an appropriate password policy response control is returned for
   * a modify operation when the user didn't provide their current password when
   * it was required.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testModifyMissingCurrentPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "password-change-requires-current-password:true");

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

    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    try
    {
      BindRequestProtocolOp bindRequest = new BindRequestProtocolOp(
           new ASN1OctetString("uid=test.user,o=test"), 3,
           new ASN1OctetString("password"));
      LDAPMessage message = new LDAPMessage(1, bindRequest);
      w.writeElement(message.encode());

      message = LDAPMessage.decode(r.readElement().decodeAsSequence());
      BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
      assertEquals(bindResponse.getResultCode(), LDAPResultCode.SUCCESS);


      ArrayList<RawModification> mods = new ArrayList<RawModification>();
      mods.add(RawModification.create(ModificationType.REPLACE, "userPassword",
                                      "newpassword"));

      ModifyRequestProtocolOp modifyRequest =
           new ModifyRequestProtocolOp(
                    new ASN1OctetString("uid=test.user,o=test"), mods);

      ArrayList<LDAPControl> controls = new ArrayList<LDAPControl>();
      controls.add(new LDAPControl(OID_PASSWORD_POLICY_CONTROL, true));

      message = new LDAPMessage(2, modifyRequest, controls);
      w.writeElement(message.encode());

      message = LDAPMessage.decode(r.readElement().decodeAsSequence());
      ModifyResponseProtocolOp modifyResponse =
           message.getModifyResponseProtocolOp();
      assertFalse(modifyResponse.getResultCode() == LDAPResultCode.SUCCESS);

      controls = message.getControls();
      assertNotNull(controls);
      assertFalse(controls.isEmpty());

      boolean found = false;
      for (LDAPControl c : controls)
      {
        if (c.getOID().equals(OID_PASSWORD_POLICY_CONTROL))
        {
          PasswordPolicyResponseControl pwpControl =
               PasswordPolicyResponseControl.decodeControl(c.getControl());
          assertEquals(pwpControl.getErrorType(),
                       PasswordPolicyErrorType.MUST_SUPPLY_OLD_PASSWORD);
          found = true;
        }
      }
      assertTrue(found);
    }
    finally
    {
      TestCaseUtils.dsconfig(
        "set-password-policy-prop",
        "--policy-name", "Default Password Policy",
        "--set", "password-change-requires-current-password:false");

      try
      {
        s.close();
      } catch (Exception e) {}
    }
  }



  /**
   * Tests that an appropriate password policy response control is returned for
   * a modify operation when the user tried to perform multiple password changes
   * without respecting the minimum age.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testModifyMinimumPasswordAge()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "minimum-password-age:24 hours");

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

    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    try
    {
      BindRequestProtocolOp bindRequest = new BindRequestProtocolOp(
           new ASN1OctetString("uid=test.user,o=test"), 3,
           new ASN1OctetString("password"));
      LDAPMessage message = new LDAPMessage(1, bindRequest);
      w.writeElement(message.encode());

      message = LDAPMessage.decode(r.readElement().decodeAsSequence());
      BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
      assertEquals(bindResponse.getResultCode(), LDAPResultCode.SUCCESS);


      ArrayList<RawModification> mods = new ArrayList<RawModification>();
      mods.add(RawModification.create(ModificationType.REPLACE, "userPassword",
                                      "newpassword"));

      ModifyRequestProtocolOp modifyRequest =
           new ModifyRequestProtocolOp(
                    new ASN1OctetString("uid=test.user,o=test"), mods);

      ArrayList<LDAPControl> controls = new ArrayList<LDAPControl>();
      controls.add(new LDAPControl(OID_PASSWORD_POLICY_CONTROL, true));

      message = new LDAPMessage(2, modifyRequest, controls);
      w.writeElement(message.encode());

      message = LDAPMessage.decode(r.readElement().decodeAsSequence());
      ModifyResponseProtocolOp modifyResponse =
           message.getModifyResponseProtocolOp();
      assertFalse(modifyResponse.getResultCode() == LDAPResultCode.SUCCESS);

      controls = message.getControls();
      assertNotNull(controls);
      assertFalse(controls.isEmpty());

      boolean found = false;
      for (LDAPControl c : controls)
      {
        if (c.getOID().equals(OID_PASSWORD_POLICY_CONTROL))
        {
          PasswordPolicyResponseControl pwpControl =
               PasswordPolicyResponseControl.decodeControl(c.getControl());
          assertEquals(pwpControl.getErrorType(),
                       PasswordPolicyErrorType.PASSWORD_TOO_YOUNG);
          found = true;
        }
      }
      assertTrue(found);
    }
    finally
    {
      TestCaseUtils.dsconfig(
        "set-password-policy-prop",
        "--policy-name", "Default Password Policy",
        "--set", "minimum-password-age:0 seconds");

      try
      {
        s.close();
      } catch (Exception e) {}
    }
  }



  /**
   * Tests that an appropriate password policy response control is returned for
   * a modify DN operation when the user's password is in a "must change" state.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testModifyDNMustChange()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "force-change-on-add:true");

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

    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    try
    {
      BindRequestProtocolOp bindRequest = new BindRequestProtocolOp(
           new ASN1OctetString("uid=test.user,o=test"), 3,
           new ASN1OctetString("password"));
      LDAPMessage message = new LDAPMessage(1, bindRequest);
      w.writeElement(message.encode());

      message = LDAPMessage.decode(r.readElement().decodeAsSequence());
      BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
      assertEquals(bindResponse.getResultCode(), LDAPResultCode.SUCCESS);


      ModifyDNRequestProtocolOp modifyDNRequest =
           new ModifyDNRequestProtocolOp(
                    new ASN1OctetString("ou=People,o=test"),
                    new ASN1OctetString("ou=Users"), true);

      ArrayList<LDAPControl> controls = new ArrayList<LDAPControl>();
      controls.add(new LDAPControl(OID_PASSWORD_POLICY_CONTROL, true));

      message = new LDAPMessage(2, modifyDNRequest, controls);
      w.writeElement(message.encode());

      message = LDAPMessage.decode(r.readElement().decodeAsSequence());
      ModifyDNResponseProtocolOp modifyDNResponse =
           message.getModifyDNResponseProtocolOp();
      assertFalse(modifyDNResponse.getResultCode() == LDAPResultCode.SUCCESS);

      controls = message.getControls();
      assertNotNull(controls);
      assertFalse(controls.isEmpty());

      boolean found = false;
      for (LDAPControl c : controls)
      {
        if (c.getOID().equals(OID_PASSWORD_POLICY_CONTROL))
        {
          PasswordPolicyResponseControl pwpControl =
               PasswordPolicyResponseControl.decodeControl(c.getControl());
          assertEquals(pwpControl.getErrorType(),
                       PasswordPolicyErrorType.CHANGE_AFTER_RESET);
          found = true;
        }
      }
      assertTrue(found);
    }
    finally
    {
      TestCaseUtils.dsconfig(
        "set-password-policy-prop",
        "--policy-name", "Default Password Policy",
        "--set", "force-change-on-add:false");

      try
      {
        s.close();
      } catch (Exception e) {}
    }
  }



  /**
   * Tests that an appropriate password policy response control is returned for
   * a search operation when the user's password is in a "must change" state.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSearchMustChange()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "force-change-on-add:true");

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

    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    try
    {
      BindRequestProtocolOp bindRequest = new BindRequestProtocolOp(
           new ASN1OctetString("uid=test.user,o=test"), 3,
           new ASN1OctetString("password"));
      LDAPMessage message = new LDAPMessage(1, bindRequest);
      w.writeElement(message.encode());

      message = LDAPMessage.decode(r.readElement().decodeAsSequence());
      BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
      assertEquals(bindResponse.getResultCode(), LDAPResultCode.SUCCESS);


      SearchRequestProtocolOp searchRequest =
           new SearchRequestProtocolOp(new ASN1OctetString("o=test"),
                                       SearchScope.BASE_OBJECT,
                                       DereferencePolicy.NEVER_DEREF_ALIASES, 0,
                                       0, false,
                                       LDAPFilter.decode("(objectClass=*)"),
                                       new LinkedHashSet<String>());

      ArrayList<LDAPControl> controls = new ArrayList<LDAPControl>();
      controls.add(new LDAPControl(OID_PASSWORD_POLICY_CONTROL, true));

      message = new LDAPMessage(2, searchRequest, controls);
      w.writeElement(message.encode());

      message = LDAPMessage.decode(r.readElement().decodeAsSequence());
      SearchResultDoneProtocolOp searchDone =
           message.getSearchResultDoneProtocolOp();
      assertFalse(searchDone.getResultCode() == LDAPResultCode.SUCCESS);

      controls = message.getControls();
      assertNotNull(controls);
      assertFalse(controls.isEmpty());

      boolean found = false;
      for (LDAPControl c : controls)
      {
        if (c.getOID().equals(OID_PASSWORD_POLICY_CONTROL))
        {
          PasswordPolicyResponseControl pwpControl =
               PasswordPolicyResponseControl.decodeControl(c.getControl());
          assertEquals(pwpControl.getErrorType(),
                       PasswordPolicyErrorType.CHANGE_AFTER_RESET);
          found = true;
        }
      }
      assertTrue(found);
    }
    finally
    {
      TestCaseUtils.dsconfig(
        "set-password-policy-prop",
        "--policy-name", "Default Password Policy",
        "--set", "force-change-on-add:false");

      try
      {
        s.close();
      } catch (Exception e) {}
    }
  }
}

