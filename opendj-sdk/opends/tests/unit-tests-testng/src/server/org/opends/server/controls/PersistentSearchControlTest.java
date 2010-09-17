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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 */
package org.opends.server.controls;

import java.util.ArrayList;
import static org.opends.server.util.ServerConstants.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.opends.messages.Message;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.ModifyOperation;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

import org.opends.server.types.*;
import org.opends.server.protocols.asn1.ASN1;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPReader;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.tools.LDAPSearch;

/**
 * Test ChangeNumber and ChangeNumberGenerator
 */
public class PersistentSearchControlTest
    extends ControlsTestCase
{

  /**
   * Create correct values
   */
  @DataProvider(name = "persistentSearchChangeTypeData")
  public Object[][] createPersistentSearchChangeTypeData()
  {

    HashMap<Integer, String> values = new HashMap<Integer, String>();
    values.put(1, "add");
    values.put(2, "delete");
    values.put(4, "modify");
    values.put(8, "modDN");
    return new Object[][]
    {
    { values } };
  }

  /**
   * Test if int value are ok
   */
  @Test(dataProvider = "persistentSearchChangeTypeData")
  public void checkIntValueTest(
      HashMap<Integer, String> expectedValues) throws Exception
  {
    for (Integer i : expectedValues.keySet())
    {
      PersistentSearchChangeType val = PersistentSearchChangeType.valueOf(i);
      String expected = expectedValues.get(i);
      assertEquals(val.toString(), expected);
    }
  }


  /**
   * Test If we have only the required values
   */
  @Test(dataProvider = "persistentSearchChangeTypeData")
  public void checkRequiredValuesTest(
      HashMap<Integer, String> exceptedValues) throws Exception
  {
    // Retrieve the values
    PersistentSearchChangeType[] vals = PersistentSearchChangeType.values();

    // Check if we have the correct munber
    assertEquals(vals.length, exceptedValues.size());

    // Check if we have the correct int value
    for (PersistentSearchChangeType val : vals)
    {
      assertTrue(exceptedValues.containsKey(val.intValue()));
    }
  }

  /**
   * Test invalid int values
   */
  @Test(dataProvider = "persistentSearchChangeTypeData")
  public void checkInvalidIntTest(
      HashMap<Integer, String> exceptedValues) throws Exception
  {
    Set<Integer> keys = exceptedValues.keySet() ;
    for (int i=-10 ; i< 10 ; i++)
    {
      if (keys.contains(i)) continue ;
      try
      {
        PersistentSearchChangeType.valueOf(i);
        assertTrue(false,"the int '" + i + "' is not a set of type - exception expected");
      }
      catch (LDAPException e)
      {
        assertTrue(true,"the int '" + i + "' is not a set of type - exception expected");
      }
    }
  }

  /**
   * Test int to type
   */
  @Test(dataProvider = "persistentSearchChangeTypeData")
  public void checkIntToTypeTest(
      HashMap<Integer, String> exceptedValues) throws Exception
  {
    Set<Integer> keys = exceptedValues.keySet() ;

    Set<PersistentSearchChangeType> returnTypes;
    HashSet<PersistentSearchChangeType> expectedTypes =
      new HashSet<PersistentSearchChangeType>(4);

    for (int i = 1; i <= 15; i++)
    {
      expectedTypes.clear();
      for (int key : keys)
      {
        if ((i & key) != 0)
        {
          expectedTypes.add(PersistentSearchChangeType.valueOf(key));
        }
      }
      returnTypes = PersistentSearchChangeType.intToTypes(i);
      assertEquals(expectedTypes.size(), returnTypes.size());
      for (PersistentSearchChangeType type: expectedTypes)
      {
        assertTrue(returnTypes.contains(type));
      }
    }

    // We should have and exception
    try
    {
      PersistentSearchChangeType.intToTypes(0);
      assertTrue(false,"the int '" + 0 + "' is not a set of type - exception expected");
    }
    catch (LDAPException e)
    {
      assertTrue(true,"the int is not a set of type - exception expected");
    }

    // We should have and exception
    try
    {
      int i = 16 ;
      PersistentSearchChangeType.intToTypes(i);
      assertTrue(false,"the int '" + i + "' is not a set of type - exception expected");
    }
    catch (LDAPException e)
    {
      assertTrue(true,"the int is not a set of type - exception expected");
    }
  }

  /**
   * Test type to int
   */
  @Test(dataProvider = "persistentSearchChangeTypeData", dependsOnMethods= {"checkIntToTypeTest"})
  public void checkTypesToIntTest(
      HashMap<Integer, String> exceptedValues) throws Exception
  {
    Set<PersistentSearchChangeType> returnTypes;
    for (int i = 1; i <= 15; i++)
    {
      returnTypes = PersistentSearchChangeType.intToTypes(i);
      int ret = PersistentSearchChangeType.changeTypesToInt(returnTypes);
      assertEquals(ret, i);
    }
  }

  @Test(dataProvider = "persistentSearchChangeTypeData", dependsOnMethods= {"checkIntToTypeTest"})
  public void checkChangeTypesToStringTest(
      HashMap<Integer, String> exceptedValues) throws Exception
  {
    Set<PersistentSearchChangeType> returnTypes;
    for (int i = 1; i <= 15; i++)
    {
      returnTypes = PersistentSearchChangeType.intToTypes(i);
      String ret = PersistentSearchChangeType.changeTypesToString(returnTypes);
      String exceptedRet = null ;
      for (PersistentSearchChangeType type : returnTypes)
      {
        if (exceptedRet == null)
        {
          exceptedRet = type.toString();
        }
        else
        {
          exceptedRet = exceptedRet + "|" + type.toString();
        }
      }
      assertEquals(ret, exceptedRet);
    }
  }

  /**
   * Create values for PersistentSearchControl
   */
  @DataProvider(name = "persistentSearchControl")
  public Object[][] createPasswordPolicyResponseControlData()
  {

    return new Object[][]
    {
    {true,  false, true },
    {false, false, false }, };
  }

  /**
   * Test PersistentSearchControl
   */
  @Test(dataProvider = "persistentSearchControl")
  public void checkPersistentSearchControlTest(
      boolean isCritical, boolean changesOnly, boolean returnECs)
      throws Exception
  {
    // Test contructor
    // CheclPersistentSearchControlTest(Set<PersistentSearchChangeType>
    // changeTypes,
    // boolean changesOnly, boolean returnECs
    Set<PersistentSearchChangeType> returnTypes;
    for (int i = 1; i <= 15; i++)
    {
      returnTypes = PersistentSearchChangeType.intToTypes(i);
      PersistentSearchControl psc = new PersistentSearchControl(returnTypes,
          changesOnly, returnECs);
      assertNotNull(psc);
      assertEquals(changesOnly, psc.getChangesOnly());
      assertEquals(returnECs, psc.getReturnECs());
      assertEquals(returnTypes.size(), psc.getChangeTypes().size());
      assertEquals(OID_PERSISTENT_SEARCH, psc.getOID());
    }

    // Test contructor
    // CString oid, boolean isCritical,
    // Set<PersistentSearchChangeType> changeTypes,
    //    boolean changesOnly, boolean returnECs
    for (int i = 1; i <= 15; i++)
    {
      returnTypes = PersistentSearchChangeType.intToTypes(i);
      PersistentSearchControl psc = new PersistentSearchControl(
          isCritical, returnTypes, changesOnly, returnECs);
      assertNotNull(psc);
      assertEquals(isCritical, psc.isCritical());
      assertEquals(OID_PERSISTENT_SEARCH, psc.getOID());
      assertEquals(changesOnly, psc.getChangesOnly());
      assertEquals(returnECs, psc.getReturnECs());
      assertEquals(returnTypes.size(), psc.getChangeTypes().size());
    }


    // Test encode/decode
    ByteStringBuilder bsb = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(bsb);
    for (int i = 1; i <= 15; i++)
    {
      bsb.clear();
      returnTypes = PersistentSearchChangeType.intToTypes(i);
      PersistentSearchControl psc = new PersistentSearchControl(
          isCritical, returnTypes, changesOnly, returnECs);
      psc.write(writer);
      LDAPControl control = LDAPReader.readControl(ASN1.getReader(bsb));
      psc = PersistentSearchControl.DECODER.decode(control.isCritical(), control.getValue());
      assertNotNull(psc);
      assertEquals(isCritical, psc.isCritical());
      assertEquals(OID_PERSISTENT_SEARCH, psc.getOID());
      assertEquals(changesOnly, psc.getChangesOnly());
      assertEquals(returnECs, psc.getReturnECs());
      assertEquals(returnTypes.size(), psc.getChangeTypes().size());

      // Check the toString
      String toString = "PersistentSearchControl(changeTypes=\"" +
      PersistentSearchChangeType.changeTypesToString(psc.getChangeTypes()) +
      "\",changesOnly=" + psc.getChangesOnly() +
      ",returnECs=" +psc.getReturnECs() +")" ;
      assertEquals(psc.toString(), toString);


      // check null value for the control
      try
      {
        control = new LDAPControl(OID_PERSISTENT_SEARCH, isCritical);
        psc = PersistentSearchControl.DECODER.decode(control.isCritical(), control.getValue());
        assertTrue(false,"the control should have a value");
      }
      catch (DirectoryException e)
      {
        // normal case
        assertTrue(true,"the control should have a value");
      }

      // check invalid value for the control
      try
      {
        control = new LDAPControl(OID_PERSISTENT_SEARCH, isCritical,
            ByteString.valueOf("invalid value"));
        psc = PersistentSearchControl.DECODER.decode(control.isCritical(), control.getValue());
        assertTrue(false, "the control should have a value");
      }
      catch (DirectoryException e)
      {
        // normal case
        assertTrue(true, "the control should have a value");
      }

    }
  }


  /**
   * Create values for EntryChangeNotificationControl
   */
  @DataProvider(name = "entryChangeNotificationControl")
  public Object[][] createEntryChangeNotificationControlData()
  {
    return new Object[][]
    {
    { true, 1, "cn=test" },
    { false, 2, "dc=example,dc=com" },
    { true, 3, "cn=test, dc=example,dc=com" },
    { false, 4, "cn= new test, dc=example,dc=com" } };
  }
  /**
   * Test EntryChangeNotificationControl
   */
  @Test(dataProvider = "entryChangeNotificationControl")
  public void checkEntryChangeNotificationControlTest(
      boolean isCritical, long changeNumber, String dnString)
      throws Exception
  {
    // Test contructor EntryChangeNotificationControl
    // (PersistentSearchChangeType changeType,long changeNumber)
    PersistentSearchChangeType[] types = PersistentSearchChangeType.values();
    EntryChangeNotificationControl ecnc = null ;
    EntryChangeNotificationControl newEcnc ;
    ByteStringBuilder bsb = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(bsb);
    for (PersistentSearchChangeType type : types)
    {
      ecnc = new EntryChangeNotificationControl(type, changeNumber);
      assertNotNull(ecnc);
      assertEquals(OID_ENTRY_CHANGE_NOTIFICATION, ecnc.getOID());
      assertEquals(changeNumber, ecnc.getChangeNumber());
      assertEquals(type, ecnc.getChangeType());
      assertNull(ecnc.getPreviousDN()) ;
      assertEquals(false, ecnc.isCritical()) ;
      checkEntryChangeNotificationControlToString(ecnc);

      // also check encode/decode
      try
      {
        bsb.clear();
        ecnc.write(writer);
        LDAPControl control = LDAPReader.readControl(ASN1.getReader(bsb));
        newEcnc = EntryChangeNotificationControl.DECODER.decode(control.isCritical(), control.getValue());
        assertNotNull(newEcnc);
        assertEquals(ecnc.getOID(), newEcnc.getOID());
        assertEquals(ecnc.getChangeNumber(), newEcnc.getChangeNumber());
        assertEquals(ecnc.getChangeType(), newEcnc.getChangeType());
        assertNull(newEcnc.getPreviousDN());
        assertEquals(ecnc.isCritical(), newEcnc.isCritical());
      }
      catch (DirectoryException e)
      {
        if (type.compareTo(PersistentSearchChangeType.MODIFY_DN) == 0)
        {
          assertTrue(true,"could decode a control with previousDN=null " +
              "and type=modDN");
        }
        else
        {
          assertTrue(false,"could decode a control with previousDN=null " +
          "and type=modDN");
        }
      }
    }

    // Test contructor EntryChangeNotificationControl
    // (PersistentSearchChangeType changeType, DN previousDN, long
    // changeNumber)
    DN dn = DN.decode(dnString);
    for (PersistentSearchChangeType type : types)
    {
      ecnc = new EntryChangeNotificationControl(type, dn, changeNumber);
      assertNotNull(ecnc);
      assertEquals(OID_ENTRY_CHANGE_NOTIFICATION, ecnc.getOID());
      assertEquals(changeNumber, ecnc.getChangeNumber());
      assertEquals(type, ecnc.getChangeType());
      assertEquals(dn, ecnc.getPreviousDN());
      assertEquals(false, ecnc.isCritical()) ;
      checkEntryChangeNotificationControlToString(ecnc);

      // also check encode/decode
      try
      {
        bsb.clear();
        ecnc.write(writer);
        LDAPControl control = LDAPReader.readControl(ASN1.getReader(bsb));
        newEcnc = EntryChangeNotificationControl.DECODER.decode(control.isCritical(), control.getValue());
        assertNotNull(newEcnc);
        assertEquals(ecnc.getOID(),newEcnc.getOID());
        assertEquals(ecnc.getChangeNumber(),newEcnc.getChangeNumber());
        assertEquals(ecnc.getChangeType(),newEcnc.getChangeType());
        assertEquals(ecnc.getPreviousDN(),newEcnc.getPreviousDN());
        assertEquals(ecnc.isCritical(),newEcnc.isCritical()) ;
      }
      catch (DirectoryException e)
      {
        if (type.compareTo(PersistentSearchChangeType.MODIFY_DN) == 0)
        {
          assertTrue(false,"couldn't decode a control with previousDN " +
              "not null and type=modDN");
        }
        else
        {
          assertTrue(true,"couldn't decode a control with previousDN " +
          "not null and type=modDN");
        }
      }
    }


    // Test contructor EntryChangeNotificationControl(boolean
    // isCritical, PersistentSearchChangeType changeType,
    // DN previousDN, long changeNumber)
    for (PersistentSearchChangeType type : types)
    {
      ecnc = new EntryChangeNotificationControl(isCritical, type, dn,
          changeNumber);
      assertNotNull(ecnc);
      assertEquals(OID_ENTRY_CHANGE_NOTIFICATION, ecnc.getOID());
      assertEquals(changeNumber, ecnc.getChangeNumber());
      assertEquals(type, ecnc.getChangeType());
      assertEquals(dn, ecnc.getPreviousDN());
      assertEquals(isCritical, ecnc.isCritical()) ;
      checkEntryChangeNotificationControlToString(ecnc);

      // also check encode/decode
      try
      {
        bsb.clear();
        ecnc.write(writer);
        LDAPControl control = LDAPReader.readControl(ASN1.getReader(bsb));
        newEcnc = EntryChangeNotificationControl.DECODER.decode(control.isCritical(), control.getValue());
        assertNotNull(newEcnc);
        assertEquals(ecnc.getOID(),newEcnc.getOID());
        assertEquals(ecnc.getChangeNumber(),newEcnc.getChangeNumber());
        assertEquals(ecnc.getChangeType(),newEcnc.getChangeType());
        assertEquals(ecnc.getPreviousDN(),newEcnc.getPreviousDN());
        assertEquals(ecnc.isCritical(),newEcnc.isCritical()) ;
      }
      catch (DirectoryException e)
      {
        if (type.compareTo(PersistentSearchChangeType.MODIFY_DN) == 0)
        {
          assertTrue(false,"couldn't decode a control with previousDN " +
              "not null and type=modDN");
        }
        else
        {
          assertTrue(true,"couldn't decode a control with previousDN " +
          "not null and type=modDN");
        }
      }
    }

    // Check error on decode
    try
    {
      LDAPControl control =
          new LDAPControl(OID_ENTRY_CHANGE_NOTIFICATION, isCritical);
      newEcnc = EntryChangeNotificationControl.DECODER.decode(control.isCritical(), control.getValue());
      assertTrue(false,"couldn't decode a control with null");
    }
    catch (DirectoryException e)
    {
      assertTrue(true,"couldn't decode a control with null");
    }
  }

  private void checkEntryChangeNotificationControlToString(EntryChangeNotificationControl ecnc)
  {
    String toString ="EntryChangeNotificationControl(changeType=";
    toString = toString + ecnc.getChangeType();

    if (ecnc.getPreviousDN() != null)
    {
      toString = toString + ",previousDN=\"" + ecnc.getPreviousDN() + "\"" ;
    }

    if (ecnc.getChangeNumber() > 0)
    {
      toString = toString + ",changeNumber=" + ecnc.getChangeNumber() ;
    }
    toString = toString +")";
    assertEquals(toString, ecnc.toString());
  }


  /**
   * Tests the maximum persistent search limit imposed by the server.
   */
  @Test()
  public void testMaxPSearch() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    //Modify the configuration to allow only 1 concurrent persistent search.
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ArrayList<ByteString> values = new ArrayList<ByteString>();
    values.add(ByteString.valueOf("1"));
    LDAPAttribute attr = new LDAPAttribute("ds-cfg-max-psearches", values);

    ArrayList<RawModification> mods = new ArrayList<RawModification>();
    mods.add(new LDAPModification(ModificationType.REPLACE, attr));

    ModifyOperation modifyOperation =
         conn.processModify(ByteString.valueOf("cn=config"), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);

    //Create a persistent search request.
    LinkedHashSet<String> attributes = new LinkedHashSet<String>();
    attributes.add("cn");
    List<Control> controls = new LinkedList<Control>();
          // Creates psearch control
    HashSet<PersistentSearchChangeType> changeTypes =
      new HashSet<PersistentSearchChangeType>();
    changeTypes.add(PersistentSearchChangeType.ADD);
    changeTypes.add(PersistentSearchChangeType.DELETE);
    changeTypes.add(PersistentSearchChangeType.MODIFY);
    changeTypes.add(PersistentSearchChangeType.MODIFY_DN);
    PersistentSearchControl persSearchControl = new PersistentSearchControl(
          changeTypes, true, true);
      controls.add(persSearchControl);
    final InternalSearchOperation search =
        conn.processSearch("o=test", SearchScope.BASE_OBJECT,
            DereferencePolicy.NEVER_DEREF_ALIASES, 0, // Size limit
            0, // Time limit
            true, // Types only
            "(objectClass=*)", attributes, controls, null);
  
    Thread t = new Thread(new Runnable() {
      public void run() {
        try {
          search.run();
        }
        catch(Exception ex) {}
      }
    },"Persistent Search Test");
    t.start();
    t.join(2000);
     //Create a persistent search request.
    final String[] args =
     {
     "-D", "cn=Directory Manager",
     "-w", "password",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-b", "o=test",
      "-s", "sub",
      "-C","ps:add:true:true",
      "--noPropertiesFile",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false,
                  true, null, System.err),11);
    //cancel the persisting persistent search.
    search.cancel(new CancelRequest(true,Message.EMPTY));
  }
}
