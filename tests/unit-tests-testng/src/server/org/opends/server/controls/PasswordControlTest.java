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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.controls;

import static org.opends.server.util.ServerConstants.*;

import java.util.HashMap;
import java.util.Set;

import org.opends.server.types.*;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.protocols.ldap.LDAPReader;
import org.opends.server.protocols.asn1.ASN1;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Test password control
 */
public class PasswordControlTest
    extends ControlsTestCase
{

  /**
   * Create values for PasswordPolicyErrorType
   */
  @DataProvider(name = "passwordPolicyErrorTypeData")
  public Object[][] createPasswordPolicyErrorTypeData()
  {

    HashMap<Integer, String> values = new HashMap<Integer, String>();
    values.put(0, "passwordExpired");
    values.put(1, "accountLocked");
    values.put(2, "changeAfterReset");
    values.put(3, "passwordModNotAllowed");
    values.put(4, "mustSupplyOldPassword");
    values.put(5, "insufficientPasswordQuality");
    values.put(6, "passwordTooShort");
    values.put(7, "passwordTooYoung");
    values.put(8, "passwordInHistory");
    return new Object[][]
    {
    { values } };
  }

  /**
   * Test if int value are ok
   */
  @Test(dataProvider = "passwordPolicyErrorTypeData")
  public void checkIntValuePasswordPolicyErrorTypeTest(
      HashMap<Integer, String> expectedValues) throws Exception
  {
    for (Integer i : expectedValues.keySet())
    {
      PasswordPolicyErrorType val = PasswordPolicyErrorType.valueOf(i);
      String expected = expectedValues.get(i);
      assertEquals(val.toString(), expected);
    }
  }


  /**
   * Test If we have only the required values
   */
  @Test(dataProvider = "passwordPolicyErrorTypeData")
  public void checkRequiredValuesPasswordPolicyErrorTypeTest(
      HashMap<Integer, String> exceptedValues) throws Exception
  {
    // Retrieve the values
    PasswordPolicyErrorType[] vals = PasswordPolicyErrorType.values();

    // Check if we have the correct munber
    assertEquals(vals.length, exceptedValues.size());

    // Check if we have the correct int value
    for (PasswordPolicyErrorType val : vals)
    {
      assertTrue(exceptedValues.containsKey(val.intValue()));
    }
  }

  /**
   * Test invalid int values
   */
  @Test(dataProvider = "passwordPolicyErrorTypeData")
  public void checkInvalidIntPasswordPolicyErrorTypeTest(
      HashMap<Integer, String> exceptedValues) throws Exception
  {
    Set<Integer> keys = exceptedValues.keySet() ;
    for (int i=-10 ; i< 10 ; i++)
    {
      if (keys.contains(i)) continue ;
      assertNull(PasswordPolicyErrorType.valueOf(i));
    }
  }

  /**
   * Create correct values
   */
  @DataProvider(name = "passwordPolicyWarningTypeData")
  public Object[][] createPasswordPolicyWarningTypeData()
  {

    HashMap<Byte, String> values = new HashMap<Byte, String>();
    values.put((byte)0x80, "timeBeforeExpiration");
    values.put((byte)0x81, "graceAuthNsRemaining");
    return new Object[][]
    {
    { values } };
  }

  /**
   * Test if byte values are ok
   */
  @Test(dataProvider = "passwordPolicyWarningTypeData")
  public void checkIntValuePasswordPolicyWarningTypeTest(
      HashMap<Byte, String> expectedValues) throws Exception
  {
    for (byte i : expectedValues.keySet())
    {
      PasswordPolicyWarningType val = PasswordPolicyWarningType.valueOf(i);
      String expected = expectedValues.get(i);

      assertEquals(val.toString(), expected);
      assertEquals(i, val.getType());
    }
  }


  /**
   * Test If we have only the required values
   */
  @Test(dataProvider = "passwordPolicyWarningTypeData")
  public void checkRequiredValuesPasswordPolicyWarningTypeTest(
      HashMap<Byte, String> exceptedValues) throws Exception
  {
    // Retrieve the values
    PasswordPolicyWarningType[] vals = PasswordPolicyWarningType.values();

    // Check if we have the correct munber
    assertEquals(vals.length, exceptedValues.size());

    // Check if we have the correct byte value
    for (PasswordPolicyWarningType val : vals)
    {
      assertTrue(exceptedValues.containsValue(val.toString()));
    }
  }

  /**
   * Test invalid int values
   */
  @Test(dataProvider = "passwordPolicyWarningTypeData")
  public void checkInvalidIntPasswordPolicyWarningTypeTest(
      HashMap<Integer, String> exceptedValues) throws Exception
  {
    Set<Integer> keys = exceptedValues.keySet();
    for (int i = 0x70; i < 0x90; i++)
    {
      byte b = new Integer(i).byteValue();
      if (keys.contains(b))
      {
        continue;
      }
      else
      {
        assertNull(PasswordPolicyWarningType.valueOf(b));
        PasswordPolicyWarningType val = PasswordPolicyWarningType.valueOf(b);
        assertNull(val);
      }

    }
  }

  /**
   * Create values for PasswordExpiredControl
   */
  @DataProvider(name = "passwordExpiredControlData")
  public Object[][] createPasswordExpiredControlData()
  {

    return new Object[][] {
     { true },
     { false },
    };
  }

  /**
   * Test OID
   */
  @Test()
  public void checkPasswordOID() throws Exception
  {
    assertEquals(OID_NS_PASSWORD_EXPIRED,     "2.16.840.1.113730.3.4.4");
    assertEquals(OID_NS_PASSWORD_EXPIRING,    "2.16.840.1.113730.3.4.5");
    //assertEquals(OID_PASSWORD_POLICY_CONTROL, "");
  }

  /**
   * Test "Netscape password expired control" implementation
   */
  @Test(dataProvider = "passwordExpiredControlData")
  public void passwordExpiredControlTest(
      boolean isCritical) throws Exception
  {
    // Check default constructor
    PasswordExpiredControl pec = new PasswordExpiredControl();
    assertNotNull(pec);
    assertEquals("PasswordExpiredControl()", pec.toString());
    assertEquals(pec.getOID(),OID_NS_PASSWORD_EXPIRED);

    // Check constructor with oid and boolean
    pec = new PasswordExpiredControl(isCritical);
    assertNotNull(pec);
    assertEquals(pec.isCritical(),isCritical);
    assertEquals(pec.getOID(),OID_NS_PASSWORD_EXPIRED);

    // Check the decode
    LDAPControl control = new LDAPControl(OID_NS_PASSWORD_EXPIRED,isCritical);
    pec = PasswordExpiredControl.DECODER.decode(control.isCritical(), control.getValue());
    assertNotNull(pec);
    assertEquals(pec.isCritical(),isCritical);
    assertEquals(pec.getOID(),OID_NS_PASSWORD_EXPIRED);

    control = new LDAPControl(OID_NS_PASSWORD_EXPIRED, isCritical,
        ByteString.valueOf("value"));
    try
    {
      pec = PasswordExpiredControl.DECODER.decode(control.isCritical(), control.getValue());
      assertTrue(false,
          "should be allow to create a passwordExpiredControl with value");
    }
    catch (DirectoryException e)
    {
      // Normal case
      assertTrue(true,
          "should be allow to create a passwordExpiredControl with value");
    }

    // Check toString
    assertEquals("PasswordExpiredControl()", pec.toString());

    // Check encode
    ByteStringBuilder bsb = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(bsb);
    pec = new PasswordExpiredControl(isCritical);
    pec.write(writer);
    control = LDAPReader.readControl(ASN1.getReader(bsb));
    PasswordExpiredControl newPec =
        PasswordExpiredControl.DECODER.decode(control.isCritical(), control.getValue());
    assertNotNull(newPec);
    assertEquals(newPec.isCritical(), isCritical);
    assertEquals(pec.getOID(),OID_NS_PASSWORD_EXPIRED);
  }

  /**
   * Create values for PasswordControl
   */
  @DataProvider(name = "passwordExpiringControlData")
  public Object[][] createPasswordExpiringControlData()
  {

    return new Object[][] {
     { true,  1},
     { false, 2},
    };
  }

  /**
   * Test "Netscape password expired control" implementation
   */
  @Test(dataProvider = "passwordExpiringControlData")
  public void passwordExpiringControlTest(
      boolean isCritical, int sec) throws Exception
  {
    // Check constructor with int
    PasswordExpiringControl pec = new PasswordExpiringControl(sec);
    assertNotNull(pec);
    String toString = "PasswordExpiringControl(secondsUntilExpiration=" + sec +")" ;
    assertEquals(toString, pec.toString());
    assertEquals(pec.getOID(),OID_NS_PASSWORD_EXPIRING);
    assertEquals(pec.getSecondsUntilExpiration(), sec);

    // Check constructor with oid, boolean and int
    pec = new PasswordExpiringControl(isCritical, sec);
    assertNotNull(pec);
    assertEquals(pec.isCritical(),isCritical);
    assertEquals(pec.getOID(),OID_NS_PASSWORD_EXPIRING);
    assertEquals(pec.getSecondsUntilExpiration(), sec);

    // Check the decode
    LDAPControl control = new LDAPControl(OID_NS_PASSWORD_EXPIRING,isCritical);
    try
    {
      pec = PasswordExpiringControl.DECODER.decode(control.isCritical(), control.getValue());
      assertTrue(false,
          "shouldn't be allow to create PasswordExpiringControl without value");
    }
    catch (DirectoryException e)
    {
      // Normal case
      assertTrue(true,
          "shouldn't be allow to create PasswordExpiringControl without value");
    }

    control = new LDAPControl(OID_NS_PASSWORD_EXPIRING, isCritical,
        ByteString.valueOf("Wrong value"));
    try
    {
      pec = PasswordExpiringControl.DECODER.decode(control.isCritical(), control.getValue());
      assertTrue(false,
      "shouldn't be allow to create PasswordExpiringControl with a wrong value");
    }
    catch (DirectoryException e)
    {
      // Normal case
      assertTrue(true,
      "shouldn't be allow to create PasswordExpiringControl with a wrong value");
    }

    // Check encode/decode
    ByteStringBuilder bsb = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(bsb);
    pec = new PasswordExpiringControl(isCritical, sec);
    pec.write(writer);
    control = LDAPReader.readControl(ASN1.getReader(bsb));
    pec = PasswordExpiringControl.DECODER.decode(control.isCritical(), control.getValue());
    assertNotNull(pec);
    assertEquals(pec.isCritical(), isCritical);
    assertEquals(pec.getOID(),OID_NS_PASSWORD_EXPIRING);
    assertEquals(pec.getSecondsUntilExpiration(), sec);
  }

  /**
   * Create values for PasswordControl
   */
  @DataProvider(name = "passwordPolicyRequestControlData")
  public Object[][] createPasswordPolicyRequestControlData()
  {

    return new Object[][] {
     { true},
     { false},
    };
  }

  /**
   * Test PasswordPolicyRequestControl
   */
  @Test(dataProvider = "passwordPolicyRequestControlData")
  public void passwordPolicyRequestControlTest(
      boolean isCritical) throws Exception
  {
    // Check default constructor
    PasswordPolicyRequestControl pec = new PasswordPolicyRequestControl();
    assertNotNull(pec);
    assertEquals("PasswordPolicyRequestControl()", pec.toString());
    assertEquals(pec.getOID(),OID_PASSWORD_POLICY_CONTROL);

    // Check constructor with oid and boolean
    pec = new PasswordPolicyRequestControl(isCritical);
    assertNotNull(pec);
    assertEquals(pec.isCritical(),isCritical);
    assertEquals(pec.getOID(),OID_PASSWORD_POLICY_CONTROL);

    // Check the encode/decode
    ByteStringBuilder bsb = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(bsb);
    pec = new PasswordPolicyRequestControl(isCritical);
    pec.write(writer);
    LDAPControl control = LDAPReader
        .readControl(ASN1.getReader(bsb));
    pec = PasswordPolicyRequestControl.DECODER.decode(control.isCritical(), control.getValue());
    assertNotNull(pec);
    assertEquals(pec.isCritical(),isCritical);
    assertEquals(pec.getOID(),OID_PASSWORD_POLICY_CONTROL);

    control = new LDAPControl(OID_PASSWORD_POLICY_CONTROL,
        isCritical, ByteString.valueOf("value"));
    try
    {
      pec = PasswordPolicyRequestControl.DECODER.decode(control.isCritical(), control.getValue());
      assertTrue(false,
          "should be allow to create a PasswordPolicyRequestControl with value");
    }
    catch (DirectoryException e)
    {
      // Normal case
      assertTrue(true,
          "should be allow to create a PasswordPolicyRequestControl with value");
    }

    // Check toString
    assertEquals("PasswordPolicyRequestControl()", pec.toString());
  }


  /**
   * Create values for PasswordControl
   */
  @DataProvider(name = "passwordPolicyResponseControl")
  public Object[][] createPasswordPolicyResponseControlData()
  {

    return new Object[][] {
     { true , -1},
     { false , -1},
     { true , 0},
     { false , 0}
    };
  }

  /**
   * Test PasswordPolicyResponseControl
   */
   @Test(dataProvider = "passwordPolicyResponseControl")
  public void passwordPolicyResponseControlTest(
      boolean isCritical, int warningValue)
      throws Exception
  {
    // Check default constructor
    PasswordPolicyResponseControl pprc = new PasswordPolicyResponseControl();
    assertNotNull(pprc);
    assertEquals("PasswordPolicyResponseControl()", pprc.toString());
    assertEquals(pprc.getOID(), OID_PASSWORD_POLICY_CONTROL);
    assertNull(pprc.getWarningType());
    assertNull(pprc.getErrorType());


    // check constructor PasswordPolicyResponseControl
    // (PasswordPolicyWarningType warningType,
    //    int warningValue,
    //    PasswordPolicyErrorType errorType)
    for (PasswordPolicyErrorType errorType : PasswordPolicyErrorType.values())
    {
      for (PasswordPolicyWarningType warningType : PasswordPolicyWarningType.values())
      {
        pprc = new PasswordPolicyResponseControl(warningType,warningValue,errorType);
        assertNotNull(pprc) ;
        assertEquals(warningType, pprc.getWarningType());
        assertEquals(errorType, pprc.getErrorType());
        assertEquals(pprc.getWarningValue(),warningValue);
        assertEquals(pprc.getOID(), OID_PASSWORD_POLICY_CONTROL);
      }
    }

    // check constructor PasswordPolicyResponseControl
    // (PString oid, boolean isCritical,
    //     PasswordPolicyWarningType warningType,
    //   warningValue,
    // PasswordPolicyErrorType errorType)
    for (PasswordPolicyErrorType errorType : PasswordPolicyErrorType.values())
    {
      for (PasswordPolicyWarningType warningType : PasswordPolicyWarningType
          .values())
      {
        pprc = new PasswordPolicyResponseControl(isCritical,
            warningType, warningValue, errorType);
        assertNotNull(pprc);
        assertEquals(warningType, pprc.getWarningType());
        assertEquals(errorType, pprc.getErrorType());
        assertEquals(pprc.getWarningValue(), warningValue);
        assertEquals(pprc.getOID(), OID_PASSWORD_POLICY_CONTROL);
      }
    }


    // check encode/decode
    PasswordPolicyResponseControl control ;
    ByteStringBuilder bsb = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(bsb);
    for (PasswordPolicyErrorType errorType : PasswordPolicyErrorType.values())
    {
      for (PasswordPolicyWarningType warningType : PasswordPolicyWarningType
          .values())
      {
        bsb.clear();
        control = new PasswordPolicyResponseControl(isCritical,
            warningType, warningValue, errorType);
        control.write(writer);
        LDAPControl c = LDAPReader.readControl(ASN1.getReader(bsb));
        pprc = PasswordPolicyResponseControl.DECODER.decode(c.isCritical(), c.getValue());
        assertNotNull(pprc);
        assertEquals(warningType, pprc.getWarningType());
        assertEquals(errorType, pprc.getErrorType());
        assertEquals(pprc.getWarningValue(), warningValue);
        assertEquals(pprc.getOID(), OID_PASSWORD_POLICY_CONTROL);

        // check to String
        String toString =
          "PasswordPolicyResponseControl(" +
          warningType.toString() +
          "=" +
          warningValue +
          ", " +
          errorType.toString() +
          ")" ;
        assertEquals(toString, pprc.toString()) ;


        // check null value for the control
        try
        {
          c = new LDAPControl(OID_PASSWORD_POLICY_CONTROL, isCritical);
          pprc = PasswordPolicyResponseControl.DECODER.decode(c.isCritical(), c.getValue());
          assertTrue(false,"the control should have a value");
        }
        catch (DirectoryException e)
        {
          // normal case
          assertTrue(true,"the control should have a value");
        }


        // check null warning type
        bsb.clear();
        control = new PasswordPolicyResponseControl(isCritical,
            null, warningValue, errorType);
        control.write(writer);
        c = LDAPReader.readControl(ASN1.getReader(bsb));
        try
        {
          pprc = PasswordPolicyResponseControl.DECODER.decode(c.isCritical(), c.getValue());
          assertNull(pprc.getWarningType());
        }
        catch (DirectoryException e)
        {
          assertTrue(false,"We should be able to decode the control");
        }

        // check null error type
        bsb.clear();
        control = new PasswordPolicyResponseControl(isCritical,
            warningType, warningValue, null);
        control.write(writer);
        c = LDAPReader.readControl(ASN1.getReader(bsb));
        try
        {
          pprc = PasswordPolicyResponseControl.DECODER.decode(c.isCritical(), c.getValue());
          assertNull(pprc.getErrorType());
        }
        catch (DirectoryException e)
        {
          assertTrue(false,"We should be able to decode the control");
        }
      }
    }
  }
}
