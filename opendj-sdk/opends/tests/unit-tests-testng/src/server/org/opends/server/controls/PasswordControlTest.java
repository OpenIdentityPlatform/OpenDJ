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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.controls;

import static org.opends.server.util.ServerConstants.*;

import java.util.HashMap;
import java.util.Set;

import org.opends.server.protocols.asn1.ASN1Boolean;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.ldap.LDAPException;
import org.opends.server.types.Control;
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
   * Create values for PasswordControl
   */
  @DataProvider(name = "passwordControlData")
  public Object[][] createPasswordExpiredControlData()
  {

    return new Object[][] {
     { OID_NS_PASSWORD_EXPIRED, true , -1},
     { OID_NS_PASSWORD_EXPIRED, false , 0},
     { OID_NS_PASSWORD_EXPIRING, true,  1},
     { OID_NS_PASSWORD_EXPIRING, false, 2},
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
  @Test(dataProvider = "passwordControlData")
  public void passwordExpiredControlTest(
      String oid, boolean isCritical, int sec) throws Exception
  {
    // Check default constructor
    PasswordExpiredControl pec = new PasswordExpiredControl();
    assertNotNull(pec);
    assertEquals("PasswordExpiredControl()", pec.toString());
    assertEquals(pec.getOID(),OID_NS_PASSWORD_EXPIRED);

    // Check constructor with oid and boolean
    pec = new PasswordExpiredControl(oid, isCritical);
    assertNotNull(pec);
    assertEquals(pec.isCritical(),isCritical);
    assertEquals(pec.getOID(),oid);

    // Check the decode
    Control control = new Control(oid,isCritical);
    pec = PasswordExpiredControl.decodeControl(control);
    assertNotNull(pec);
    assertEquals(pec.isCritical(),isCritical);
    assertEquals(pec.getOID(),oid);
    
    control.setValue(new ASN1Boolean(true).decodeAsOctetString());
    try
    {
      pec = PasswordExpiredControl.decodeControl(control);
      assertTrue(false,
          "should be allow to create a passwordExpiredControl with value");
    }
    catch (LDAPException e)
    {
      // Normal case
      assertTrue(true,
          "should be allow to create a passwordExpiredControl with value");
    }

    // Check toString
    assertEquals("PasswordExpiredControl()", pec.toString());  
  }
  
  /**
   * Test "Netscape password expired control" implementation
   */
  @Test(dataProvider = "passwordControlData")
  public void passwordExpiringControlTest(
      String oid, boolean isCritical, int sec) throws Exception
  {
    // Check constructor with int
    PasswordExpiringControl pec = new PasswordExpiringControl(sec);
    assertNotNull(pec);
    String toString = "PasswordExpiringControl(secondsUntilExpiration=" + sec +")" ;
    assertEquals(toString, pec.toString());
    assertEquals(pec.getOID(),OID_NS_PASSWORD_EXPIRING);
    assertEquals(pec.getSecondsUntilExpiration(), sec);

    // Check constructor with oid, boolean and int
    pec = new PasswordExpiringControl(oid, isCritical, sec);
    assertNotNull(pec);
    assertEquals(pec.isCritical(),isCritical);
    assertEquals(pec.getOID(),oid);
    assertEquals(pec.getSecondsUntilExpiration(), sec);

    // Check the decode
    Control control = new Control(oid,isCritical);
    try
    {
      pec = PasswordExpiringControl.decodeControl(control);
      assertTrue(false,
          "shouldn't be allow to create PasswordExpiringControl without value");
    }
    catch (LDAPException e)
    {
      // Normal case
      assertTrue(true,
          "shouldn't be allow to create PasswordExpiringControl without value");
    }
    
    control.setValue(new ASN1OctetString("invalid value"));
    try
    {
      pec = PasswordExpiringControl.decodeControl(control);
      assertTrue(false,
      "shouldn't be allow to create PasswordExpiringControl with a wrong value");
    }
    catch (LDAPException e)
    {
      // Normal case
      assertTrue(true,
      "shouldn't be allow to create PasswordExpiringControl with a wrong value");
    }

    pec = new PasswordExpiringControl(oid, isCritical, sec);
    control= new Control(oid,isCritical,pec.getValue());
    PasswordExpiringControl newPec = PasswordExpiringControl.decodeControl(control);
    assertNotNull(newPec);
    assertEquals(newPec.isCritical(), isCritical);
    assertEquals(newPec.getOID(), oid);
    assertEquals(newPec.getSecondsUntilExpiration(), sec);
  }
  
  /**
   * Test PasswordPolicyRequestControl
   */
  @Test(dataProvider = "passwordControlData")
  public void passwordPolicyRequestControlTest(
      String oid, boolean isCritical, int sec) throws Exception
  {
    // Check default constructor
    PasswordPolicyRequestControl pec = new PasswordPolicyRequestControl();
    assertNotNull(pec);
    assertEquals("PasswordPolicyRequestControl()", pec.toString());
    assertEquals(pec.getOID(),OID_PASSWORD_POLICY_CONTROL);

    // Check constructor with oid and boolean
    pec = new PasswordPolicyRequestControl(oid, isCritical);
    assertNotNull(pec);
    assertEquals(pec.isCritical(),isCritical);
    assertEquals(pec.getOID(),oid);

    // Check the decode
    Control control = new Control(oid,isCritical);
    pec = PasswordPolicyRequestControl.decodeControl(control);
    assertNotNull(pec);
    assertEquals(pec.isCritical(),isCritical);
    assertEquals(pec.getOID(),oid);
    
    control.setValue(new ASN1Boolean(true).decodeAsOctetString());
    try
    {
      pec = PasswordPolicyRequestControl.decodeControl(control);
      assertTrue(false,
          "should be allow to create a PasswordPolicyRequestControl with value");
    }
    catch (LDAPException e)
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
     { OID_PASSWORD_POLICY_CONTROL, true , -1},
     { OID_PASSWORD_POLICY_CONTROL, false , -1},
     { OID_PASSWORD_POLICY_CONTROL, true , 0},
     { OID_PASSWORD_POLICY_CONTROL, false , 0},
     { OID_NS_PASSWORD_EXPIRING, true,      1},
     { OID_NS_PASSWORD_EXPIRING, false,     2}
    };
  }
  
  /**
   * Test PasswordPolicyResponseControl
   */
   @Test(dataProvider = "passwordPolicyResponseControl")
  public void passwordPolicyResponseControlTest(
      String oid, boolean isCritical, int warningValue)
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
        pprc = new PasswordPolicyResponseControl(oid, isCritical,
            warningType, warningValue, errorType);
        assertNotNull(pprc);
        assertEquals(warningType, pprc.getWarningType());
        assertEquals(errorType, pprc.getErrorType());
        assertEquals(pprc.getWarningValue(), warningValue);
      }
    }
    
    
    // check decode
    Control control ;
    for (PasswordPolicyErrorType errorType : PasswordPolicyErrorType.values())
    {
      for (PasswordPolicyWarningType warningType : PasswordPolicyWarningType
          .values())
      {
        control = new PasswordPolicyResponseControl(oid, isCritical,
            warningType, warningValue, errorType);
        pprc = PasswordPolicyResponseControl.decodeControl(control);
        assertNotNull(pprc);
        assertEquals(warningType, pprc.getWarningType());
        assertEquals(errorType, pprc.getErrorType());
        assertEquals(pprc.getWarningValue(), warningValue);
        
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
          control.setValue(null);
          pprc = PasswordPolicyResponseControl.decodeControl(control);
          assertTrue(false,"the control should have a value");
        }
        catch (LDAPException e)
        {
          // normal case
          assertTrue(true,"the control should have a value");
        }
        
        
        // check null warning type
        control = new PasswordPolicyResponseControl(oid, isCritical,
            null, warningValue, errorType);
        try
        {
          pprc = PasswordPolicyResponseControl.decodeControl(control);
          assertNull(pprc.getWarningType());
        }
        catch (LDAPException e)
        {
          assertTrue(false,"We should be able to decode the control");
        }
        
        // check null error type
        control = new PasswordPolicyResponseControl(oid, isCritical,
            warningType, warningValue, null);
        try
        {
          pprc = PasswordPolicyResponseControl.decodeControl(control);
          assertNull(pprc.getErrorType());
        }
        catch (LDAPException e)
        {
          assertTrue(false,"We should be able to decode the control");
        }
      }
    }
  }
}
