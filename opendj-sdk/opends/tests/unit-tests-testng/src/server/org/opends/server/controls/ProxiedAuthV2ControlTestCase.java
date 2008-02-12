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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.controls;



import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.Control;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.LDAPException;

import static org.testng.Assert.*;

import static org.opends.server.util.ServerConstants.*;



/**
 * This class contains a number of test cases for the proxied authorization v2
 * control.
 */
public class ProxiedAuthV2ControlTestCase
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
   * Tests the constructor with a {@code null} authorization ID.
   */
  @Test(expectedExceptions = { AssertionError.class })
  public void testConstructorNullAuthzID()
  {
    ProxiedAuthV2Control proxyControl = new ProxiedAuthV2Control(null);
  }



  /**
   * Tests the constructor with an empty authorization ID.
   */
  @Test()
  public void testConstructorEmptyAuthzID()
  {
    ProxiedAuthV2Control proxyControl =
         new ProxiedAuthV2Control(new ASN1OctetString(""));
    assertEquals(proxyControl.getAuthorizationID(), new ASN1OctetString(""));
  }



  /**
   * Tests the constructor with a non-empty authorization ID using the "dn:"
   * form.
   */
  @Test()
  public void testConstructorNonEmptyAuthzIDDN()
  {
    ProxiedAuthV2Control proxyControl =
         new ProxiedAuthV2Control(new ASN1OctetString("dn:uid=test,o=test"));
    assertEquals(proxyControl.getAuthorizationID(),
                 new ASN1OctetString("dn:uid=test,o=test"));
  }



  /**
   * Tests the constructor with a non-empty authorization ID using the "u:"
   * form.
   */
  @Test()
  public void testConstructorNonEmptyAuthzIDUsername()
  {
    ProxiedAuthV2Control proxyControl =
         new ProxiedAuthV2Control(new ASN1OctetString("u:test"));
    assertEquals(proxyControl.getAuthorizationID(),
                 new ASN1OctetString("u:test"));
  }



  /**
   * Tests the {@code decodeControl} method with a {@code null} argument.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { AssertionError.class })
  public void testDecodeControlNull()
         throws Exception
  {
    ProxiedAuthV2Control.decodeControl(null);
  }



  /**
   * Tests the {@code decodeControl} method with a control that is not marked
   * critical.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { LDAPException.class })
  public void testDecodeControlNotCritical()
         throws Exception
  {
    Control c = new Control(OID_PROXIED_AUTH_V2, false,
                            new ASN1OctetString("u:test"));
    ProxiedAuthV2Control.decodeControl(c);
  }



  /**
   * Tests the {@code decodeControl} method with a control that does not have a
   * value.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { LDAPException.class })
  public void testDecodeControlNoValue()
         throws Exception
  {
    Control c = new Control(OID_PROXIED_AUTH_V2, true);
    ProxiedAuthV2Control.decodeControl(c);
  }



  /**
   * Tests the {@code decodeControl} method with a control encoded in the
   * standard from with the "dn:"-style value.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDecodeControlDNValue()
         throws Exception
  {
    ASN1OctetString authzID = new ASN1OctetString("dn:uid=test,o=test");

    Control c = new Control(OID_PROXIED_AUTH_V2, true, authzID);
    ProxiedAuthV2Control proxyControl = ProxiedAuthV2Control.decodeControl(c);
    assertEquals(proxyControl.getAuthorizationID(), authzID);
  }



  /**
   * Tests the {@code decodeControl} method with a control encoded in the
   * standard from with the "u:"-style value.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDecodeControlUsernameValue()
         throws Exception
  {
    ASN1OctetString authzID = new ASN1OctetString("u:test");

    Control c = new Control(OID_PROXIED_AUTH_V2, true, authzID);
    ProxiedAuthV2Control proxyControl = ProxiedAuthV2Control.decodeControl(c);
    assertEquals(proxyControl.getAuthorizationID(), authzID);
  }



  /**
   * Tests the {@code decodeControl} method with an invalid value (which doesn't
   * start with either "dn:" or "u:").
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { LDAPException.class })
  public void testDecodeControlInvalidValue()
         throws Exception
  {
    ASN1OctetString authzID = new ASN1OctetString("invalid");

    Control c = new Control(OID_PROXIED_AUTH_V2, true, authzID);
    ProxiedAuthV2Control proxyControl = ProxiedAuthV2Control.decodeControl(c);
    assertEquals(proxyControl.getAuthorizationID(), authzID);
  }



  /**
   * Tests the {@code decodeControl} method with a control encoded in the legacy
   * form (in which the value is wrapped by an extra octet string) with the
   * "dn:"-style value.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDecodeControlLegacyDNValue()
         throws Exception
  {
    ASN1OctetString innerValue = new ASN1OctetString("dn:uid=test,o=test");
    ASN1OctetString outerValue = new ASN1OctetString(innerValue.encode());

    Control c = new Control(OID_PROXIED_AUTH_V2, true, outerValue);
    ProxiedAuthV2Control proxyControl = ProxiedAuthV2Control.decodeControl(c);
    assertEquals(proxyControl.getAuthorizationID(), innerValue);
  }



  /**
   * Tests the {@code decodeControl} method with a control encoded in the legacy
   * form (in which the value is wrapped by an extra octet string) with the
   * "u:"-style value.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDecodeControlLegacyUsernameValue()
         throws Exception
  {
    ASN1OctetString innerValue = new ASN1OctetString("u:test");
    ASN1OctetString outerValue = new ASN1OctetString(innerValue.encode());

    Control c = new Control(OID_PROXIED_AUTH_V2, true, outerValue);
    ProxiedAuthV2Control proxyControl = ProxiedAuthV2Control.decodeControl(c);
    assertEquals(proxyControl.getAuthorizationID(), innerValue);
  }



  /**
   * Tests the {@code getAuthorizationID} and {@code setAuthorizationID}
   * methods.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetAndSetAuthorizationID()
         throws Exception
  {
    ProxiedAuthV2Control proxyControl =
         new ProxiedAuthV2Control(new ASN1OctetString("dn:uid=test,o=test"));
    assertEquals(proxyControl.getAuthorizationID(),
                 new ASN1OctetString("dn:uid=test,o=test"));

    proxyControl.setAuthorizationID(null);
    assertEquals(proxyControl.getAuthorizationID(), new ASN1OctetString(""));

    proxyControl.setAuthorizationID(new ASN1OctetString(""));
    assertEquals(proxyControl.getAuthorizationID(), new ASN1OctetString(""));

    proxyControl.setAuthorizationID(new ASN1OctetString("u:test"));
    assertEquals(proxyControl.getAuthorizationID(),
                 new ASN1OctetString("u:test"));

    proxyControl.setAuthorizationID(new ASN1OctetString("dn:uid=test,o=test"));
    assertEquals(proxyControl.getAuthorizationID(),
                 new ASN1OctetString("dn:uid=test,o=test"));
  }



  /**
   * Tests the {@code getValidatedAuthorizationDN} method with an empty
   * authorization ID string.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetValidatedAuthorizationDNEmptyAuthzID()
         throws Exception
  {
    ProxiedAuthV2Control proxyControl =
         new ProxiedAuthV2Control(new ASN1OctetString(""));
    assertNull(proxyControl.getAuthorizationEntry());
  }



  /**
   * Tests the {@code getValidatedAuthorizationDN} method with an authorization
   * ID of "dn:".
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetValidatedAuthorizationDNEmptyAuthzIDDN()
         throws Exception
  {
    ProxiedAuthV2Control proxyControl =
         new ProxiedAuthV2Control(new ASN1OctetString("dn:"));
    assertNull(proxyControl.getAuthorizationEntry());
  }



  /**
   * Tests the {@code getValidatedAuthorizationDN} method with an authorization
   * ID in the "dn:" form that points to a valid user.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetValidatedAuthorizationDNExistingUserDN()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
      "dn: uid=test,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test",
      "givenName: Test",
      "sn: User",
      "cn: Test User");

    ProxiedAuthV2Control proxyControl =
         new ProxiedAuthV2Control(new ASN1OctetString("dn:uid=test,o=test"));
    assertEquals(proxyControl.getAuthorizationEntry().getDN(),
                 DN.decode("uid=test,o=test"));
  }



  /**
   * Tests the {@code getValidatedAuthorizationDN} method with an authorization
   * ID in the "dn:" form that points to a user that doesn't exist.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { DirectoryException.class })
  public void testGetValidatedAuthorizationDNNonExistingUserDN()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    ProxiedAuthV2Control proxyControl =
         new ProxiedAuthV2Control(new ASN1OctetString("dn:uid=test,o=test"));
    proxyControl.getAuthorizationEntry();
  }



  /**
   * Tests the {@code getValidatedAuthorizationDN} method with an authorization
   * ID in the "dn:" form that points to a valid user but whose account is
   * disabled.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { DirectoryException.class })
  public void testGetValidatedAuthorizationDNExistingDisabledUserDN()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
      "dn: uid=test,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test",
      "givenName: Test",
      "sn: User",
      "cn: Test User",
      "ds-pwp-account-disabled: true");

    ProxiedAuthV2Control proxyControl =
         new ProxiedAuthV2Control(new ASN1OctetString("dn:uid=test,o=test"));
    proxyControl.getAuthorizationEntry();
  }



  /**
   * Tests the {@code getValidatedAuthorizationDN} method with an authorization
   * ID of "u:".
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetValidatedAuthorizationDNEmptyAuthzIDUsername()
         throws Exception
  {
    ProxiedAuthV2Control proxyControl =
         new ProxiedAuthV2Control(new ASN1OctetString("u:"));
    assertNull(proxyControl.getAuthorizationEntry());
  }



  /**
   * Tests the {@code getValidatedAuthorizationDN} method with an authorization
   * ID in the "u:" form that points to a valid user.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetValidatedAuthorizationDNExistingUserUsername()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
      "dn: uid=test,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test",
      "givenName: Test",
      "sn: User",
      "cn: Test User");

    ProxiedAuthV2Control proxyControl =
         new ProxiedAuthV2Control(new ASN1OctetString("u:test"));
    assertEquals(proxyControl.getAuthorizationEntry().getDN(),
                 DN.decode("uid=test,o=test"));
  }



  /**
   * Tests the {@code getValidatedAuthorizationDN} method with an authorization
   * ID in the "u:" form that points to a user that doesn't exist.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { DirectoryException.class })
  public void testGetValidatedAuthorizationDNNonExistingUserUsername()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    ProxiedAuthV2Control proxyControl =
         new ProxiedAuthV2Control(new ASN1OctetString("u:test"));
    proxyControl.getAuthorizationEntry();
  }



  /**
   * Tests the {@code getValidatedAuthorizationDN} method with an authorization
   * ID in the "u:" form that points to a valid user but whose account is
   * disabled.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { DirectoryException.class })
  public void testGetValidatedAuthorizationDNExistingDisabledUserUsername()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
      "dn: uid=test,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test",
      "givenName: Test",
      "sn: User",
      "cn: Test User",
      "ds-pwp-account-disabled: true");

    ProxiedAuthV2Control proxyControl =
         new ProxiedAuthV2Control(new ASN1OctetString("u:test"));
    proxyControl.getAuthorizationEntry();
  }



  /**
   * Tests the {@code getValidatedAuthorizationDN} method with an invalid
   * authorization ID.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { DirectoryException.class })
  public void testGetValidatedAuthorizationDNInvalidAuthzID()
         throws Exception
  {
    ProxiedAuthV2Control proxyControl =
         new ProxiedAuthV2Control(new ASN1OctetString("invalid"));
    proxyControl.getAuthorizationEntry();
  }



  /**
   * Tests the {@code toString} methods.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testToString()
         throws Exception
  {
    // The default toString() calls the version that takes a string builder
    // argument, so we only need to use the default version to cover both cases.
    ProxiedAuthV2Control proxyControl =
         new ProxiedAuthV2Control(new ASN1OctetString("dn:uid=test,o=test"));
    proxyControl.toString();

    proxyControl = new ProxiedAuthV2Control(new ASN1OctetString("u:test"));
    proxyControl.toString();
  }
}

