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



import java.util.ArrayList;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.types.Control;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.LDAPException;

import static org.testng.Assert.*;

import static org.opends.server.util.ServerConstants.*;



/**
 * This class contains a number of test cases for the proxied authorization v1
 * control.
 */
public class ProxiedAuthV1ControlTestCase
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
   * Tests the first constructor, which creates an instance of the control using
   * a raw, unprocessed DN.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testConstructor1()
         throws Exception
  {
    // Try a DN of "null", which is not valid and will fail on the attempt to
    // create the control
    ProxiedAuthV1Control proxyControl;
    try
    {
      proxyControl = new ProxiedAuthV1Control((ASN1OctetString) null);
      throw new AssertionError("Expected a failure when creating a proxied " +
                               "auth V1 control with a null octet string.");
    } catch (Throwable t) {}


    // Try an empty DN, which is acceptable.
    proxyControl = new ProxiedAuthV1Control(new ASN1OctetString(""));
    assertTrue(proxyControl.getOID().equals(OID_PROXIED_AUTH_V1));
    assertTrue(proxyControl.isCritical());
    assertTrue(proxyControl.hasValue());
    assertTrue(proxyControl.getAuthorizationDN().isNullDN());


    // Try a valid DN, which is acceptable.
    proxyControl =
         new ProxiedAuthV1Control(new ASN1OctetString("uid=test,o=test"));
    assertTrue(proxyControl.getOID().equals(OID_PROXIED_AUTH_V1));
    assertTrue(proxyControl.isCritical());
    assertTrue(proxyControl.hasValue());
    assertEquals(proxyControl.getAuthorizationDN(),
                 DN.decode("uid=test,o=test"));


    // Try an invalid DN, which will be initally accepted but will fail when
    // attempting to get the authorization DN.
    proxyControl = new ProxiedAuthV1Control(new ASN1OctetString("invalid"));
    assertTrue(proxyControl.getOID().equals(OID_PROXIED_AUTH_V1));
    assertTrue(proxyControl.isCritical());
    assertTrue(proxyControl.hasValue());
    try
    {
      proxyControl.getAuthorizationDN();
      throw new AssertionError("Expected a failure when creating a proxied " +
                               "auth V1 control with an invalid DN string.");
    } catch (Exception e) {}
  }



  /**
   * Tests the second constructor, which creates an instance of the control
   * using a processed DN.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testConstructor2()
         throws Exception
  {
    // Try a DN of "null", which is not valid and will fail on the attempt to
    // create the control
    ProxiedAuthV1Control proxyControl;
    try
    {
      proxyControl = new ProxiedAuthV1Control((DN) null);
      throw new AssertionError("Expected a failure when creating a proxied " +
                               "auth V1 control with a null octet string.");
    } catch (Throwable t) {}


    // Try an empty DN, which is acceptable.
    proxyControl = new ProxiedAuthV1Control(DN.nullDN());
    assertTrue(proxyControl.getOID().equals(OID_PROXIED_AUTH_V1));
    assertTrue(proxyControl.isCritical());
    assertTrue(proxyControl.hasValue());
    assertTrue(proxyControl.getAuthorizationDN().isNullDN());


    // Try a valid DN, which is acceptable.
    proxyControl =
         new ProxiedAuthV1Control(DN.decode("uid=test,o=test"));
    assertTrue(proxyControl.getOID().equals(OID_PROXIED_AUTH_V1));
    assertTrue(proxyControl.isCritical());
    assertTrue(proxyControl.hasValue());
    assertEquals(proxyControl.getAuthorizationDN(),
                 DN.decode("uid=test,o=test"));
  }



  /**
   * Tests the {@code decodeControl} method when the provided control is
   * {@code null}.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { AssertionError.class })
  public void testDecodeControlNull()
         throws Exception
  {
    ProxiedAuthV1Control.decodeControl(null);
  }



  /**
   * Tests the {@code decodeControl} method when the provided control has a
   * criticality of "false".
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { LDAPException.class })
  public void testDecodeControlNotCritical()
         throws Exception
  {
    ArrayList<ASN1Element> elements = new ArrayList<ASN1Element>(1);
    elements.add(new ASN1OctetString("uid=test,o=test"));
    ASN1Sequence valueSequence = new ASN1Sequence(elements);
    ASN1OctetString value = new ASN1OctetString(valueSequence.encode());
    Control c = new Control(OID_PROXIED_AUTH_V1, false, value);

    ProxiedAuthV1Control.decodeControl(c);
  }



  /**
   * Tests the {@code decodeControl} method when the provided control does not
   * have a value.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { LDAPException.class })
  public void testDecodeControlNoValue()
         throws Exception
  {
    Control c = new Control(OID_PROXIED_AUTH_V1, true, null);

    ProxiedAuthV1Control.decodeControl(c);
  }



  /**
   * Tests the {@code decodeControl} method when the control value is not a
   * sequence.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { LDAPException.class })
  public void testDecodeControlValueNotSequence()
         throws Exception
  {
    ASN1OctetString value = new ASN1OctetString("uid=test,o=test");
    Control c = new Control(OID_PROXIED_AUTH_V1, true, value);

    ProxiedAuthV1Control.decodeControl(c);
  }



  /**
   * Tests the {@code decodeControl} method when the control value is a sequence
   * with zero elements.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { LDAPException.class })
  public void testDecodeControlValueEmptySequence()
         throws Exception
  {
    ArrayList<ASN1Element> elements = new ArrayList<ASN1Element>(0);
    ASN1Sequence valueSequence = new ASN1Sequence(elements);
    ASN1OctetString value = new ASN1OctetString(valueSequence.encode());
    Control c = new Control(OID_PROXIED_AUTH_V1, true, value);

    ProxiedAuthV1Control.decodeControl(c);
  }



  /**
   * Tests the {@code decodeControl} method when the control value is a sequence
   * with multiple elements.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { LDAPException.class })
  public void testDecodeControlValueMultiElementSequence()
         throws Exception
  {
    ArrayList<ASN1Element> elements = new ArrayList<ASN1Element>(2);
    elements.add(new ASN1OctetString("uid=element1,o=test"));
    elements.add(new ASN1OctetString("uid=element2,o=test"));
    ASN1Sequence valueSequence = new ASN1Sequence(elements);
    ASN1OctetString value = new ASN1OctetString(valueSequence.encode());
    Control c = new Control(OID_PROXIED_AUTH_V1, true, value);

    ProxiedAuthV1Control.decodeControl(c);
  }



  /**
   * Tests the {@code decodeControl} method when the control value is a valid
   * octet string that contains an invalid DN.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { DirectoryException.class })
  public void testDecodeControlValueInvalidDN()
         throws Exception
  {
    ArrayList<ASN1Element> elements = new ArrayList<ASN1Element>(1);
    elements.add(new ASN1OctetString("invaliddn"));
    ASN1Sequence valueSequence = new ASN1Sequence(elements);
    ASN1OctetString value = new ASN1OctetString(valueSequence.encode());
    Control c = new Control(OID_PROXIED_AUTH_V1, true, value);

    ProxiedAuthV1Control proxyControl = ProxiedAuthV1Control.decodeControl(c);
    proxyControl.getAuthorizationDN();
  }



  /**
   * Tests the {@code decodeControl} method when the control value is a valid
   * octet string that contains an valid empty DN.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDecodeControlValueEmptyDN()
         throws Exception
  {
    ArrayList<ASN1Element> elements = new ArrayList<ASN1Element>(1);
    elements.add(new ASN1OctetString(""));
    ASN1Sequence valueSequence = new ASN1Sequence(elements);
    ASN1OctetString value = new ASN1OctetString(valueSequence.encode());
    Control c = new Control(OID_PROXIED_AUTH_V1, true, value);

    ProxiedAuthV1Control proxyControl = ProxiedAuthV1Control.decodeControl(c);
    assertTrue(proxyControl.getAuthorizationDN().isNullDN());
  }



  /**
   * Tests the {@code decodeControl} method when the control value is a valid
   * octet string that contains an valid non-empty DN.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDecodeControlValueNonEmptyDN()
         throws Exception
  {
    ArrayList<ASN1Element> elements = new ArrayList<ASN1Element>(1);
    elements.add(new ASN1OctetString("uid=test,o=test"));
    ASN1Sequence valueSequence = new ASN1Sequence(elements);
    ASN1OctetString value = new ASN1OctetString(valueSequence.encode());
    Control c = new Control(OID_PROXIED_AUTH_V1, true, value);

    ProxiedAuthV1Control proxyControl = ProxiedAuthV1Control.decodeControl(c);
    assertEquals(proxyControl.getAuthorizationDN(),
                 DN.decode("uid=test,o=test"));
  }



  /**
   * Tests the {@code getRawAuthorizationDN} and {@code setRawAuthorizationDN}
   * methods.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetAndSetRawAuthorizationDN()
         throws Exception
  {
    ProxiedAuthV1Control proxyControl =
         new ProxiedAuthV1Control(new ASN1OctetString(""));
    assertEquals(proxyControl.getRawAuthorizationDN(), new ASN1OctetString(""));

    proxyControl.setRawAuthorizationDN(new ASN1OctetString("uid=test,o=test"));
    assertEquals(proxyControl.getRawAuthorizationDN(),
                 new ASN1OctetString("uid=test,o=test"));
  }



  /**
   * Tests the {@code getAuthorizationDN} and {@code setRawAuthorizationDN}
   * methods.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetAndSetAuthorizationDN()
         throws Exception
  {
    ProxiedAuthV1Control proxyControl =
         new ProxiedAuthV1Control(DN.nullDN());
    assertEquals(proxyControl.getRawAuthorizationDN(), new ASN1OctetString(""));
    assertEquals(proxyControl.getAuthorizationDN(), DN.nullDN());

    proxyControl.setAuthorizationDN(DN.decode("uid=test,o=test"));
    assertEquals(proxyControl.getRawAuthorizationDN(),
                 new ASN1OctetString("uid=test,o=test"));
    assertEquals(proxyControl.getAuthorizationDN(),
                 DN.decode("uid=test,o=test"));
  }



  /**
   * Tests the {@code setRawAuthorizationDN} method when providing an
   * authorization DN that is {@code null}.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { AssertionError.class })
  public void testSetNullAuthorizationDN()
         throws Exception
  {
    ProxiedAuthV1Control proxyControl =
         new ProxiedAuthV1Control(DN.nullDN());
    proxyControl.setAuthorizationDN(null);
  }



  /**
   * Tests the {@code getValidatedAuthorizationDN} method for the null DN.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetValidatedAuthorizationDNNullDN()
         throws Exception
  {
    ProxiedAuthV1Control proxyControl =
         new ProxiedAuthV1Control(DN.nullDN());

    assertNull(proxyControl.getAuthorizationEntry());
  }



  /**
   * Tests the {@code getValidatedAuthorizationDN} method for a normal user
   * that exists in the directory data and doesn't have any restrictions on its
   * use.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetValidatedAuthorizationExistingNormalUser()
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

    ProxiedAuthV1Control proxyControl =
         new ProxiedAuthV1Control(DN.decode("uid=test,o=test"));

    assertEquals(proxyControl.getAuthorizationEntry().getDN(),
                 DN.decode("uid=test,o=test"));
  }



  /**
   * Tests the {@code getValidatedAuthorizationDN} method for a user that
   * doesn't exist in the directory data.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { DirectoryException.class })
  public void testGetValidatedAuthorizationNonExistingNormalUser()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    ProxiedAuthV1Control proxyControl =
         new ProxiedAuthV1Control(DN.decode("uid=test,o=test"));

    proxyControl.getAuthorizationEntry();
  }



  /**
   * Tests the {@code getValidatedAuthorizationDN} method for a disabled user.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { DirectoryException.class })
  public void testGetValidatedAuthorizationDisabledUser()
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

    ProxiedAuthV1Control proxyControl =
         new ProxiedAuthV1Control(DN.decode("uid=test,o=test"));

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
    ProxiedAuthV1Control proxyControl =
         new ProxiedAuthV1Control(new ASN1OctetString("uid=test,o=test"));
    proxyControl.toString();

    proxyControl = new ProxiedAuthV1Control(DN.decode("uid=test,o=test"));
    proxyControl.toString();
  }
}

