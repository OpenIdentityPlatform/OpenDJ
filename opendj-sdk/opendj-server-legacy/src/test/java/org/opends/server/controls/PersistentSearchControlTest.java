/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.controls;

import static org.assertj.core.api.Assertions.*;
import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.controls.PersistentSearchChangeType.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.opends.server.util.ServerConstants.*;
import static org.testng.Assert.*;

import java.util.*;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.util.Utils;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.ModifyOperation;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.protocols.ldap.LDAPReader;
import org.opends.server.tools.LDAPSearch;
import org.opends.server.types.*;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class PersistentSearchControlTest extends ControlsTestCase
{

  private static final String CANNOT_DECODE_CHANGE_NOTIF_CONTROL_NO_VALUE =
      "Cannot decode the provided entry change notification control because it "
          + "does not have a value";

  private static final String CANNOT_DECODE_PERSISTENT_SEARCH_CONTROL_NO_VALUE =
      "Cannot decode the provided persistent search control because it does not have a value";

  /**
   * Create correct values.
   */
  @DataProvider(name = "persistentSearchChangeTypeData")
  public Object[][] createPersistentSearchChangeTypeData()
  {
    Map<Integer, String> values = new HashMap<>();
    values.put(1, "add");
    values.put(2, "delete");
    values.put(4, "modify");
    values.put(8, "modDN");
    return new Object[][] { { values } };
  }

  /**
   * Test if int value are ok.
   */
  @Test(dataProvider = "persistentSearchChangeTypeData")
  public void checkIntValueTest(Map<Integer, String> expectedValues)
      throws Exception
  {
    for (Integer i : expectedValues.keySet())
    {
      PersistentSearchChangeType val = PersistentSearchChangeType.valueOf(i);
      String expected = expectedValues.get(i);
      assertEquals(val.toString(), expected);
    }
  }


  /**
   * Test If we have only the required values.
   */
  @Test(dataProvider = "persistentSearchChangeTypeData")
  public void checkRequiredValuesTest(Map<Integer, String> exceptedValues)
      throws Exception
  {
    // Retrieve the values
    PersistentSearchChangeType[] vals = PersistentSearchChangeType.values();

    // Check if we have the correct number
    assertEquals(vals.length, exceptedValues.size());

    // Check if we have the correct int value
    for (PersistentSearchChangeType val : vals)
    {
      assertTrue(exceptedValues.containsKey(val.intValue()));
    }
  }

  /**
   * Test invalid int values.
   */
  @Test(dataProvider = "persistentSearchChangeTypeData")
  public void checkInvalidIntTest(Map<Integer, String> exceptedValues)
      throws Exception
  {
    Set<Integer> keys = exceptedValues.keySet() ;
    for (int i=-10 ; i< 10 ; i++)
    {
      if (keys.contains(i)) continue ;
      try
      {
        PersistentSearchChangeType.valueOf(i);
        fail();
      }
      catch (LDAPException e)
      {
        assertThat(e.getMessage()).contains(
            "The provided integer value " + i
                + " does not correspond to any persistent search change type");
      }
    }
  }

  /**
   * Test int to type.
   */
  @Test(dataProvider = "persistentSearchChangeTypeData")
  public void checkIntToTypeTest(Map<Integer, String> exceptedValues)
      throws Exception
  {
    Set<Integer> keys = exceptedValues.keySet() ;

    Set<PersistentSearchChangeType> expectedTypes = new HashSet<>(4);

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
      Set<PersistentSearchChangeType> returnTypes = PersistentSearchChangeType.intToTypes(i);
      assertEquals(expectedTypes.size(), returnTypes.size());
      for (PersistentSearchChangeType type: expectedTypes)
      {
        assertTrue(returnTypes.contains(type));
      }
    }

    // We should have an exception
    try
    {
      PersistentSearchChangeType.intToTypes(0);
      fail();
    }
    catch (LDAPException expected)
    {
      assertEquals(
          expected.getMessage(),
          "The provided integer value indicated that there were no persistent search change types, which is not allowed");
    }

    // We should have an exception
    int i = 16;
    try
    {
      PersistentSearchChangeType.intToTypes(i);
      fail();
    }
    catch (LDAPException expected)
    {
      assertEquals(
          expected.getMessage(),
          "The provided integer value " + i
              + " was outside the range of acceptable values for an encoded change type set");
    }
  }

  /**
   * Test type to int.
   */
  @Test(dataProvider = "persistentSearchChangeTypeData", dependsOnMethods= {"checkIntToTypeTest"})
  public void checkTypesToIntTest(Map<Integer, String> exceptedValues)
      throws Exception
  {
    for (int i = 1; i <= 15; i++)
    {
      Set<PersistentSearchChangeType> returnTypes = PersistentSearchChangeType.intToTypes(i);
      int ret = PersistentSearchChangeType.changeTypesToInt(returnTypes);
      assertEquals(ret, i);
    }
  }

  @Test(dataProvider = "persistentSearchChangeTypeData", dependsOnMethods= {"checkIntToTypeTest"})
  public void checkChangeTypesToStringTest(Map<Integer, String> exceptedValues)
      throws Exception
  {
    for (int i = 1; i <= 15; i++)
    {
      Set<PersistentSearchChangeType> returnTypes = PersistentSearchChangeType.intToTypes(i);
      String ret = PersistentSearchChangeType.changeTypesToString(returnTypes);
      assertEquals(ret, Utils.joinAsString("|", returnTypes));
    }
  }

  /**
   * Create values for PersistentSearchControl.
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
   * Test PersistentSearchControl.
   */
  @Test(dataProvider = "persistentSearchControl")
  public void checkPersistentSearchControlTest(
      boolean isCritical, boolean changesOnly, boolean returnECs)
      throws Exception
  {
    // Test constructor
    // CheclPersistentSearchControlTest(Set<PersistentSearchChangeType>
    // changeTypes, boolean changesOnly, boolean returnECs
    for (int i = 1; i <= 15; i++)
    {
      Set<PersistentSearchChangeType> returnTypes = PersistentSearchChangeType.intToTypes(i);
      PersistentSearchControl psc = new PersistentSearchControl(returnTypes, changesOnly, returnECs);
      assertNotNull(psc);
      assertEquals(changesOnly, psc.getChangesOnly());
      assertEquals(returnECs, psc.getReturnECs());
      assertEquals(returnTypes.size(), psc.getChangeTypes().size());
      assertEquals(OID_PERSISTENT_SEARCH, psc.getOID());
    }

    // Test constructor
    // CString oid, boolean isCritical,
    // Set<PersistentSearchChangeType> changeTypes,
    //    boolean changesOnly, boolean returnECs
    for (int i = 1; i <= 15; i++)
    {
      Set<PersistentSearchChangeType> returnTypes = PersistentSearchChangeType.intToTypes(i);
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
      Set<PersistentSearchChangeType> returnTypes = PersistentSearchChangeType.intToTypes(i);
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
      String changeTypes =
          PersistentSearchChangeType.changeTypesToString(psc.getChangeTypes());
      String toString =
          "PersistentSearchControl(changeTypes=\"" + changeTypes
              + "\",changesOnly=" + psc.getChangesOnly() + ",returnECs="
              + psc.getReturnECs() + ")";
      assertEquals(psc.toString(), toString);


      // check null value for the control
      try
      {
        control = new LDAPControl(OID_PERSISTENT_SEARCH, isCritical);
        psc = PersistentSearchControl.DECODER.decode(control.isCritical(), control.getValue());
        fail();
      }
      catch (DirectoryException expected)
      {
        assertEquals(expected.getMessage(),
            CANNOT_DECODE_PERSISTENT_SEARCH_CONTROL_NO_VALUE);
      }

      // check invalid value for the control
      try
      {
        control = new LDAPControl(OID_PERSISTENT_SEARCH, isCritical,
            ByteString.valueOf("invalid value"));
        psc = PersistentSearchControl.DECODER.decode(control.isCritical(), control.getValue());
        fail();
      }
      catch (DirectoryException expected)
      {
        assertThat(expected.getMessage()).contains(
            "Cannot decode the provided persistent search control");
      }
    }
  }


  /**
   * Create values for EntryChangeNotificationControl.
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
   * Test EntryChangeNotificationControl.
   */
  @Test(dataProvider = "entryChangeNotificationControl")
  public void checkEntryChangeNotificationControlTest(
      boolean isCritical, long changeNumber, String dnString)
      throws Exception
  {
    // Test constructor EntryChangeNotificationControl
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
        fail();
      }
    }

    // Test constructor EntryChangeNotificationControl
    // (PersistentSearchChangeType changeType, DN previousDN, long
    // changeNumber)
    DN dn = DN.valueOf(dnString);
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
        assertNotEquals(type.compareTo(MODIFY_DN), 0,
            "couldn't decode a control with previousDN not null and type=modDN");
      }
    }


    // Test constructor EntryChangeNotificationControl(boolean
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
        assertNotEquals(type.compareTo(PersistentSearchChangeType.MODIFY_DN), 0,
            "couldn't decode a control with previousDN not null and type=modDN");
      }
    }

    // Check error on decode
    try
    {
      LDAPControl control =
          new LDAPControl(OID_ENTRY_CHANGE_NOTIFICATION, isCritical);
      newEcnc = EntryChangeNotificationControl.DECODER.decode(control.isCritical(), control.getValue());
      fail();
    }
    catch (DirectoryException expected)
    {
      assertEquals(expected.getMessage(),
          CANNOT_DECODE_CHANGE_NOTIF_CONTROL_NO_VALUE);
    }
  }

  private void checkEntryChangeNotificationControlToString(EntryChangeNotificationControl ecnc)
  {
    String toString =
        "EntryChangeNotificationControl(changeType=" + ecnc.getChangeType();
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
  @Test
  public void testMaxPSearch() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    //Modify the configuration to allow only 1 concurrent persistent search.
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ArrayList<ByteString> values = new ArrayList<>();
    values.add(ByteString.valueOf("1"));
    LDAPAttribute attr = new LDAPAttribute("ds-cfg-max-psearches", values);

    ArrayList<RawModification> mods = new ArrayList<>();
    mods.add(new LDAPModification(ModificationType.REPLACE, attr));

    ModifyOperation modifyOperation =
         conn.processModify(ByteString.valueOf("cn=config"), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);

    //Create a persistent search request.
    Set<PersistentSearchChangeType> changeTypes = EnumSet.of(ADD, DELETE, MODIFY, MODIFY_DN);
    SearchRequest request = newSearchRequest(DN.valueOf("o=test"), SearchScope.BASE_OBJECT)
        .setTypesOnly(true)
        .addAttribute("cn")
        .addControl(new PersistentSearchControl(changeTypes, true, true));
    final InternalSearchOperation search = conn.processSearch(request);

    Thread t = new Thread(new Runnable() {
      @Override
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
    search.cancel(new CancelRequest(true,LocalizableMessage.EMPTY));
  }
}
