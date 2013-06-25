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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */
package org.opends.server.controls;



import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.protocols.asn1.ASN1;
import org.opends.server.types.*;

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
      proxyControl = new ProxiedAuthV1Control((ByteString) null);
      throw new AssertionError("Expected a failure when creating a proxied " +
                               "auth V1 control with a null octet string.");
    } catch (Throwable t) {}


    // Try an empty DN, which is acceptable.
    proxyControl = new ProxiedAuthV1Control(ByteString.valueOf(""));
    assertTrue(proxyControl.getOID().equals(OID_PROXIED_AUTH_V1));
    assertTrue(proxyControl.isCritical());
    assertTrue(proxyControl.getAuthorizationDN().isNullDN());


    // Try a valid DN, which is acceptable.
    proxyControl =
         new ProxiedAuthV1Control(ByteString.valueOf("uid=test,o=test"));
    assertTrue(proxyControl.getOID().equals(OID_PROXIED_AUTH_V1));
    assertTrue(proxyControl.isCritical());
    assertEquals(proxyControl.getAuthorizationDN(),
                 DN.decode("uid=test,o=test"));


    // Try an invalid DN, which will be initally accepted but will fail when
    // attempting to get the authorization DN.
    proxyControl = new ProxiedAuthV1Control(ByteString.valueOf("invalid"));
    assertTrue(proxyControl.getOID().equals(OID_PROXIED_AUTH_V1));
    assertTrue(proxyControl.isCritical());
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
    assertTrue(proxyControl.getAuthorizationDN().isNullDN());


    // Try a valid DN, which is acceptable.
    proxyControl =
         new ProxiedAuthV1Control(DN.decode("uid=test,o=test"));
    assertTrue(proxyControl.getOID().equals(OID_PROXIED_AUTH_V1));
    assertTrue(proxyControl.isCritical());
    assertEquals(proxyControl.getAuthorizationDN(),
                 DN.decode("uid=test,o=test"));
  }



  /**
   * Tests the {@code decodeControl} method when the provided control has a
   * criticality of "false".
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { DirectoryException.class })
  public void testDecodeControlNotCritical()
         throws Exception
  {
    ByteStringBuilder bsb = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(bsb);
    writer.writeStartSequence();
    writer.writeOctetString("uid=test,o=test");
    writer.writeEndSequence();
    LDAPControl c =
        new LDAPControl(OID_PROXIED_AUTH_V1, false, bsb.toByteString());

    ProxiedAuthV1Control.DECODER.decode(c.isCritical(), c.getValue());
  }



  /**
   * Tests the {@code decodeControl} method when the provided control does not
   * have a value.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { DirectoryException.class })
  public void testDecodeControlNoValue()
         throws Exception
  {
    LDAPControl c = new LDAPControl(OID_PROXIED_AUTH_V1, true);

    ProxiedAuthV1Control.DECODER.decode(c.isCritical(), c.getValue());
  }



  /**
   * Tests the {@code decodeControl} method when the control value is not a
   * sequence.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { DirectoryException.class })
  public void testDecodeControlValueNotSequence()
         throws Exception
  {
    LDAPControl c =
        new LDAPControl(OID_PROXIED_AUTH_V1, true,
            ByteString.valueOf("uid=test,o=test"));

    ProxiedAuthV1Control.DECODER.decode(c.isCritical(), c.getValue());
  }



  /**
   * Tests the {@code decodeControl} method when the control value is a sequence
   * with zero elements.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { DirectoryException.class })
  public void testDecodeControlValueEmptySequence()
         throws Exception
  {
    ByteStringBuilder bsb = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(bsb);
    writer.writeStartSequence();
    writer.writeEndSequence();
    LDAPControl c =
        new LDAPControl(OID_PROXIED_AUTH_V1, true, bsb.toByteString());

    ProxiedAuthV1Control.DECODER.decode(c.isCritical(), c.getValue());
  }



  /**
   * Tests the {@code decodeControl} method when the control value is a sequence
   * with multiple elements.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDecodeControlValueMultiElementSequence()
         throws Exception
  {
    ByteStringBuilder bsb = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(bsb);
    writer.writeStartSequence();
    writer.writeOctetString("uid=element1,o=test");
    writer.writeOctetString("uid=element2,o=test");
    writer.writeEndSequence();
    LDAPControl c =
        new LDAPControl(OID_PROXIED_AUTH_V1, true, bsb.toByteString());

    assertEquals(ByteString.valueOf("uid=element1,o=test"),
        ProxiedAuthV1Control.DECODER.decode(c.isCritical(),
            c.getValue()).getRawAuthorizationDN());
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
    ByteStringBuilder bsb = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(bsb);
    writer.writeStartSequence();
    writer.writeOctetString("invaliddn");
    writer.writeEndSequence();
    LDAPControl c =
        new LDAPControl(OID_PROXIED_AUTH_V1, true, bsb.toByteString());

    ProxiedAuthV1Control.DECODER.decode(c.isCritical(), c.getValue());
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
    ByteStringBuilder bsb = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(bsb);
    writer.writeStartSequence();
    writer.writeOctetString("");
    writer.writeEndSequence();
    LDAPControl c =
        new LDAPControl(OID_PROXIED_AUTH_V1, true, bsb.toByteString());

    ProxiedAuthV1Control proxyControl = ProxiedAuthV1Control.DECODER.decode(c.isCritical(), c.getValue());
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
    ByteStringBuilder bsb = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(bsb);
    writer.writeStartSequence();
    writer.writeOctetString("uid=test,o=test");
    writer.writeEndSequence();
    LDAPControl c =
        new LDAPControl(OID_PROXIED_AUTH_V1, true, bsb.toByteString());

    ProxiedAuthV1Control proxyControl = ProxiedAuthV1Control.DECODER.decode(c.isCritical(), c.getValue());
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
         new ProxiedAuthV1Control(ByteString.valueOf(""));
    assertEquals(proxyControl.getRawAuthorizationDN(), ByteString.valueOf(""));

    proxyControl =
         new ProxiedAuthV1Control(ByteString.valueOf("uid=test,o=test"));
    assertEquals(proxyControl.getRawAuthorizationDN(),
        ByteString.valueOf("uid=test,o=test"));
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
    assertEquals(proxyControl.getRawAuthorizationDN(), ByteString.valueOf(""));
    assertEquals(proxyControl.getAuthorizationDN(), DN.nullDN());

    proxyControl =
         new ProxiedAuthV1Control(DN.decode("uid=test,o=test"));
    assertEquals(proxyControl.getRawAuthorizationDN(),
                 ByteString.valueOf("uid=test,o=test"));
    assertEquals(proxyControl.getAuthorizationDN(),
                 DN.decode("uid=test,o=test"));
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
         new ProxiedAuthV1Control(ByteString.valueOf("uid=test,o=test"));
    proxyControl.toString();

    proxyControl = new ProxiedAuthV1Control(DN.decode("uid=test,o=test"));
    proxyControl.toString();
  }
}

