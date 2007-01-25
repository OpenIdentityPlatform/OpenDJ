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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.core;



import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.annotations.AfterMethod;

import org.opends.server.TestCaseUtils;
import org.opends.server.api.Backend;
import org.opends.server.plugins.DisconnectClientPlugin;
import org.opends.server.plugins.UpdatePreOpPlugin;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Reader;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.ldap.BindRequestProtocolOp;
import org.opends.server.protocols.ldap.BindResponseProtocolOp;
import org.opends.server.protocols.ldap.ModifyRequestProtocolOp;
import org.opends.server.protocols.ldap.ModifyResponseProtocolOp;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.tools.LDAPModify;
import org.opends.server.types.*;

import static org.testng.Assert.*;
import static org.testng.Assert.assertEquals;

import static org.opends.server.protocols.ldap.LDAPConstants.*;



/**
 * A set of test cases for modify operations
 */
public class ModifyOperationTestCase
       extends OperationTestCase
{

  // Some of the tests disable the backends, so we reenable them here.
  @AfterMethod(alwaysRun=true)
  public void reenableBackend() throws DirectoryException {
    Object[][] backendBaseDNs = getBaseDNs();
    for (int i = 0; i < backendBaseDNs.length; i++) {
      String backendBaseDN = backendBaseDNs[i][0].toString();
      Backend b = DirectoryServer.getBackend(DN.decode(backendBaseDN));
      b.setWritabilityMode(WritabilityMode.ENABLED);
    }
  }

  /**
   * Retrieves a set of modify operations that may be used for testing.
   *
   * @return  A set of modify operations that may be used for testing.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "modifyOperations")
  public Object[][] getModifyOperations()
         throws Exception
  {
    ArrayList<ModifyOperation> opList = new ArrayList<ModifyOperation>();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ArrayList<Control> noControls = new ArrayList<Control>();


    ArrayList<LDAPModification> ldapMods = new ArrayList<LDAPModification>();
    ArrayList<ASN1OctetString> ldapValues = new ArrayList<ASN1OctetString>();
    ldapValues.add(new ASN1OctetString("foo"));
    LDAPAttribute ldapAttr = new LDAPAttribute("description", ldapValues);
    ldapMods.add(new LDAPModification(ModificationType.ADD, ldapAttr));

    opList.add(new ModifyOperation(conn, conn.nextOperationID(),
                                   conn.nextMessageID(), null,
                                   new ASN1OctetString(), ldapMods));
    opList.add(new ModifyOperation(conn, conn.nextOperationID(),
                                   conn.nextMessageID(), noControls,
                                   new ASN1OctetString(), ldapMods));
    opList.add(new ModifyOperation(conn, conn.nextOperationID(),
                                   conn.nextMessageID(), null,
                                   new ASN1OctetString("o=test"), ldapMods));
    opList.add(new ModifyOperation(conn, conn.nextOperationID(),
                                   conn.nextMessageID(), noControls,
                                   new ASN1OctetString("o=test"), ldapMods));

    ldapMods = new ArrayList<LDAPModification>();
    ldapMods.add(new LDAPModification(ModificationType.DELETE, ldapAttr));

    opList.add(new ModifyOperation(conn, conn.nextOperationID(),
                                   conn.nextMessageID(), null,
                                   new ASN1OctetString(), ldapMods));
    opList.add(new ModifyOperation(conn, conn.nextOperationID(),
                                   conn.nextMessageID(), noControls,
                                   new ASN1OctetString(), ldapMods));
    opList.add(new ModifyOperation(conn, conn.nextOperationID(),
                                   conn.nextMessageID(), null,
                                   new ASN1OctetString("o=test"), ldapMods));
    opList.add(new ModifyOperation(conn, conn.nextOperationID(),
                                   conn.nextMessageID(), noControls,
                                   new ASN1OctetString("o=test"), ldapMods));

    ldapMods = new ArrayList<LDAPModification>();
    ldapMods.add(new LDAPModification(ModificationType.REPLACE, ldapAttr));

    opList.add(new ModifyOperation(conn, conn.nextOperationID(),
                                   conn.nextMessageID(), null,
                                   new ASN1OctetString(), ldapMods));
    opList.add(new ModifyOperation(conn, conn.nextOperationID(),
                                   conn.nextMessageID(), noControls,
                                   new ASN1OctetString(), ldapMods));
    opList.add(new ModifyOperation(conn, conn.nextOperationID(),
                                   conn.nextMessageID(), null,
                                   new ASN1OctetString("o=test"), ldapMods));
    opList.add(new ModifyOperation(conn, conn.nextOperationID(),
                                   conn.nextMessageID(), noControls,
                                   new ASN1OctetString("o=test"), ldapMods));

    ldapMods = new ArrayList<LDAPModification>();
    ArrayList<ASN1OctetString> values2 = new ArrayList<ASN1OctetString>();
    values2.add(new ASN1OctetString("bar"));
    LDAPAttribute ldapAttr2 = new LDAPAttribute("description", values2);
    ldapMods.add(new LDAPModification(ModificationType.DELETE, ldapAttr));
    ldapMods.add(new LDAPModification(ModificationType.ADD, ldapAttr2));

    opList.add(new ModifyOperation(conn, conn.nextOperationID(),
                                   conn.nextMessageID(), null,
                                   new ASN1OctetString(), ldapMods));
    opList.add(new ModifyOperation(conn, conn.nextOperationID(),
                                   conn.nextMessageID(), noControls,
                                   new ASN1OctetString(), ldapMods));
    opList.add(new ModifyOperation(conn, conn.nextOperationID(),
                                   conn.nextMessageID(), null,
                                   new ASN1OctetString("o=test"), ldapMods));
    opList.add(new ModifyOperation(conn, conn.nextOperationID(),
                                   conn.nextMessageID(), noControls,
                                   new ASN1OctetString("o=test"), ldapMods));

    ldapMods = new ArrayList<LDAPModification>();
    ldapAttr2 = new LDAPAttribute("cn", values2);
    ldapMods.add(new LDAPModification(ModificationType.REPLACE, ldapAttr));
    ldapMods.add(new LDAPModification(ModificationType.REPLACE, ldapAttr2));

    opList.add(new ModifyOperation(conn, conn.nextOperationID(),
                                   conn.nextMessageID(), null,
                                   new ASN1OctetString(), ldapMods));
    opList.add(new ModifyOperation(conn, conn.nextOperationID(),
                                   conn.nextMessageID(), noControls,
                                   new ASN1OctetString(), ldapMods));
    opList.add(new ModifyOperation(conn, conn.nextOperationID(),
                                   conn.nextMessageID(), null,
                                   new ASN1OctetString("o=test"), ldapMods));
    opList.add(new ModifyOperation(conn, conn.nextOperationID(),
                                   conn.nextMessageID(), noControls,
                                   new ASN1OctetString("o=test"), ldapMods));



    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.ADD,
                              new Attribute("description", "foo")));

    opList.add(new ModifyOperation(conn, conn.nextOperationID(),
                                   conn.nextMessageID(), null, DN.nullDN(), mods));
    opList.add(new ModifyOperation(conn, conn.nextOperationID(),
                                   conn.nextMessageID(), noControls, DN.nullDN(),
                                   mods));
    opList.add(new ModifyOperation(conn, conn.nextOperationID(),
                                   conn.nextMessageID(), null,
                                   DN.decode("o=test"), mods));
    opList.add(new ModifyOperation(conn, conn.nextOperationID(),
                                   conn.nextMessageID(), noControls,
                                   DN.decode("o=test"), mods));

    mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.DELETE,
                              new Attribute("description", "foo")));

    opList.add(new ModifyOperation(conn, conn.nextOperationID(),
                                   conn.nextMessageID(), null, DN.nullDN(), mods));
    opList.add(new ModifyOperation(conn, conn.nextOperationID(),
                                   conn.nextMessageID(), noControls, DN.nullDN(),
                                   mods));
    opList.add(new ModifyOperation(conn, conn.nextOperationID(),
                                   conn.nextMessageID(), null,
                                   DN.decode("o=test"), mods));
    opList.add(new ModifyOperation(conn, conn.nextOperationID(),
                                   conn.nextMessageID(), noControls,
                                   DN.decode("o=test"), mods));

    mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
                              new Attribute("description", "foo")));

    opList.add(new ModifyOperation(conn, conn.nextOperationID(),
                                   conn.nextMessageID(), null, DN.nullDN(), mods));
    opList.add(new ModifyOperation(conn, conn.nextOperationID(),
                                   conn.nextMessageID(), noControls, DN.nullDN(),
                                   mods));
    opList.add(new ModifyOperation(conn, conn.nextOperationID(),
                                   conn.nextMessageID(), null,
                                   DN.decode("o=test"), mods));
    opList.add(new ModifyOperation(conn, conn.nextOperationID(),
                                   conn.nextMessageID(), noControls,
                                   DN.decode("o=test"), mods));

    mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.DELETE,
                              new Attribute("description", "foo")));
    mods.add(new Modification(ModificationType.ADD,
                              new Attribute("description", "bar")));

    opList.add(new ModifyOperation(conn, conn.nextOperationID(),
                                   conn.nextMessageID(), null, DN.nullDN(), mods));
    opList.add(new ModifyOperation(conn, conn.nextOperationID(),
                                   conn.nextMessageID(), noControls, DN.nullDN(),
                                   mods));
    opList.add(new ModifyOperation(conn, conn.nextOperationID(),
                                   conn.nextMessageID(), null,
                                   DN.decode("o=test"), mods));
    opList.add(new ModifyOperation(conn, conn.nextOperationID(),
                                   conn.nextMessageID(), noControls,
                                   DN.decode("o=test"), mods));

    mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
                              new Attribute("description", "foo")));
    mods.add(new Modification(ModificationType.REPLACE,
                              new Attribute("cn", "bar")));

    opList.add(new ModifyOperation(conn, conn.nextOperationID(),
                                   conn.nextMessageID(), null, DN.nullDN(), mods));
    opList.add(new ModifyOperation(conn, conn.nextOperationID(),
                                   conn.nextMessageID(), noControls, DN.nullDN(),
                                   mods));
    opList.add(new ModifyOperation(conn, conn.nextOperationID(),
                                   conn.nextMessageID(), null,
                                   DN.decode("o=test"), mods));
    opList.add(new ModifyOperation(conn, conn.nextOperationID(),
                                   conn.nextMessageID(), noControls,
                                   DN.decode("o=test"), mods));



    Object[][] objArray = new Object[opList.size()][1];
    for (int i=0; i < objArray.length; i++)
    {
      objArray[i][0] = opList.get(i);
    }

    return objArray;
  }




  @DataProvider(name = "baseDNs")
  public Object[][] getBaseDNs()
  {
    return new Object[][] {
         { "dc=example,dc=com"},
// FIXME Waiting on issue 1080.
//         { "o=test"},
    };
  }




  /**
   * {@inheritDoc}
   */
  @Override()
  public Operation[] createTestOperations()
         throws Exception
  {
    Object[][]  objs = getModifyOperations();
    Operation[] ops  = new Operation[objs.length];
    for (int i=0; i < objs.length; i++)
    {
      ops[i] = (Operation) objs[i][0];
    }

    return ops;
  }



  /**
   * Tests the <CODE>getRawEntryDN</CODE> and <CODE>setRawEntryDN</CODE>
   * methods.
   *
   * @param  modifyOperation  The modify operation to be tested.
   */
  @Test(dataProvider = "modifyOperations")
  public void testGetAndSetRawEntryDN(ModifyOperation modifyOperation)
  {
    ByteString originalDN = modifyOperation.getRawEntryDN();
    assertNotNull(originalDN);

    modifyOperation.setRawEntryDN(new ASN1OctetString("uid=test,o=test"));
    assertNotNull(modifyOperation.getRawEntryDN());
    assertEquals(modifyOperation.getRawEntryDN(),
                 new ASN1OctetString("uid=test,o=test"));

    modifyOperation.setRawEntryDN(originalDN);
    assertNotNull(modifyOperation.getRawEntryDN());
    assertEquals(modifyOperation.getRawEntryDN(), originalDN);
  }



  /**
   * Tests the <CODE>getEntryDN</CODE> method for the case in which we expect
   * the DN to be initially null.
   */
  @Test()
  public void testGetEntryDNInitiallyNull()
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("foo"));
    LDAPAttribute attr = new LDAPAttribute("description", values);

    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.REPLACE, attr));

    ModifyOperation modifyOperation =
         new ModifyOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                             null, new ASN1OctetString(), mods);
    assertNull(modifyOperation.getEntryDN());
  }



  /**
   * Tests the <CODE>getEntryDN</CODE> method for the case in which we expect
   * the DN to be initially non-null.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetEntryDNInitiallyNonNull()
         throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
                              new Attribute("description", "foo")));
    ModifyOperation modifyOperation =
         new ModifyOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                             null, DN.nullDN(), mods);
    assertNotNull(modifyOperation.getEntryDN());
  }



  /**
   * Tests the <CODE>getEntryDN</CODE> method for the case in which we expect
   * the DN to be initially non-null but then becomes null after the raw DN is
   * changed.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetEntryDNNonNullChangedToNull()
         throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
                              new Attribute("description", "foo")));
    ModifyOperation modifyOperation =
         new ModifyOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                             null, DN.nullDN(), mods);
    assertNotNull(modifyOperation.getEntryDN());

    modifyOperation.setRawEntryDN(new ASN1OctetString("ou=Users,o=test"));
    assertNull(modifyOperation.getEntryDN());
  }



  /**
   * Tests the <CODE>getRawModifications</CODE>,
   * <CODE>addRawModification</CODE>, and <CODE>setRawModifications</CODE>
   * methods.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "modifyOperations")
  public void testGetAndSetRawModifications(ModifyOperation modifyOperation)
         throws Exception
  {
    List<LDAPModification> rawMods = modifyOperation.getRawModifications();

    List<LDAPModification> clonedMods =
         new ArrayList<LDAPModification>(rawMods);
    modifyOperation.setRawModifications(clonedMods);

    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("test"));
    LDAPAttribute attr = new LDAPAttribute("test", values);

    LDAPModification mod = new LDAPModification(ModificationType.REPLACE, attr);
    modifyOperation.addRawModification(mod);

    assertEquals(modifyOperation.getRawModifications().size(),
                 (rawMods.size() + 1));

    modifyOperation.setRawModifications(rawMods);
    assertEquals(modifyOperation.getRawModifications().size(), rawMods.size());
  }



  /**
   * Invokes methods to retrieve members of a modify operation after it has
   * completed successfully.
   *
   * @param  modifyOperation  The modify operation to examine.  It should have
   *                       completed successfully.
   */
  private void retrieveSuccessfulOperationElements(
                    ModifyOperation modifyOperation)
  {
    assertNotNull(modifyOperation.getCurrentEntry());
    assertNotNull(modifyOperation.getModifiedEntry());
    assertTrue(modifyOperation.getProcessingStartTime() > 0);
    assertTrue(modifyOperation.getProcessingStopTime() >=
               modifyOperation.getProcessingStartTime());
    assertTrue(modifyOperation.getProcessingTime() >= 0);
    assertNotNull(modifyOperation.getResponseLogElements());

    modifyOperation.getNewPasswords();
    modifyOperation.getCurrentPasswords();

    long changeNumber = modifyOperation.getChangeNumber();
    modifyOperation.setChangeNumber(changeNumber);
  }



  /**
   * Invokes methods to retrieve members of a modify operation after it has
   * completed unsuccessfully.
   *
   * @param  modifyOperation  The modify operation to examine.  It should have
   *                       completed failed.
   */
  private void retrieveFailedOperationElements(
                    ModifyOperation modifyOperation)
  {
    assertTrue(modifyOperation.getProcessingStartTime() > 0);
    assertTrue(modifyOperation.getProcessingStopTime() >=
               modifyOperation.getProcessingStartTime());
    assertTrue(modifyOperation.getProcessingTime() >= 0);
    assertNotNull(modifyOperation.getResponseLogElements());

    modifyOperation.getCurrentEntry();
    modifyOperation.getModifiedEntry();
    modifyOperation.getNewPasswords();
    modifyOperation.getCurrentPasswords();

    long changeNumber = modifyOperation.getChangeNumber();
    modifyOperation.setChangeNumber(changeNumber);
  }



  /**
   * Tests the <CODE>getModifications</CODE> and <CODE>addModification</CODE>
   * methods.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetAndAddModifications()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = DirectoryServer.getEntry(DN.decode("o=test"));
    assertNull(e.getAttribute(DirectoryServer.getAttributeType(
                                                   "description", true)));

    UpdatePreOpPlugin.reset();
    UpdatePreOpPlugin.addModification(
         new Modification(ModificationType.REPLACE,
                          new Attribute("description", "foo")));


    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
                              new Attribute("l", "Austin")));

    ModifyOperation modifyOperation =
         conn.processModify(DN.decode("o=test"), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveSuccessfulOperationElements(modifyOperation);

    e = DirectoryServer.getEntry(DN.decode("o=test"));
    assertNotNull(e.getAttribute(DirectoryServer.getAttributeType(
                                                      "description", true)));

    UpdatePreOpPlugin.reset();
  }



  /**
   * Tests to ensure that a modify attempt fails if an invalid DN is provided.
   */
  @Test()
  public void testFailInvalidDN()
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("foo"));
    LDAPAttribute attr = new LDAPAttribute("description", values);

    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.REPLACE, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("invaliddn"), mods);
    assertFalse(modifyOperation.getResultCode() == ResultCode.SUCCESS);
    retrieveFailedOperationElements(modifyOperation);
  }



  /**
   * Tests to ensure that a modify attempt fails if the target DN is a suffix
   * that doesn't exist.
   */
  @Test()
  public void testFailNoSuchSuffix()
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("foo"));
    LDAPAttribute attr = new LDAPAttribute("description", values);

    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.REPLACE, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("o=nonexistent"), mods);
    assertFalse(modifyOperation.getResultCode() == ResultCode.SUCCESS);
    retrieveFailedOperationElements(modifyOperation);
  }



  /**
   * Tests to ensure that a modify attempt fails if the target DN doesn't have a
   * parent.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testFailNoSuchParent(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("foo"));
    LDAPAttribute attr = new LDAPAttribute("mail", values);

    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.REPLACE, attr));

    ModifyOperation modifyOperation =
         conn.processModify(
              new ASN1OctetString("cn=test,ou=nosuchparent," + baseDN), mods);
    assertFalse(modifyOperation.getResultCode() == ResultCode.SUCCESS);
    retrieveFailedOperationElements(modifyOperation);
  }



  /**
   * Tests to ensure that a modify attempt fails if the target entry doesn't
   * exist.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testFailNoSuchEntry(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("foo"));
    LDAPAttribute attr = new LDAPAttribute("mail", values);

    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.REPLACE, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("cn=nosuchentry," + baseDN),
                            mods);
    assertFalse(modifyOperation.getResultCode() == ResultCode.SUCCESS);
    retrieveFailedOperationElements(modifyOperation);
  }



  /**
   * Tests to ensure that a modify attempt fails if the modification doesn't
   * contain any changes.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testFailNoModifications(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString(baseDN), mods);
    assertFalse(modifyOperation.getResultCode() == ResultCode.SUCCESS);
    retrieveFailedOperationElements(modifyOperation);
  }



  /**
   * Tests the ability to perform a modification that adds a new attribute to an
   * entry.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSuccessAddAttribute()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = DirectoryServer.getEntry(DN.decode("o=test"));
    assertNull(e.getAttribute(DirectoryServer.getAttributeType("description",
                                                               true)));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("foo"));
    LDAPAttribute attr = new LDAPAttribute("description", values);

    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.REPLACE, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("o=test"), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveSuccessfulOperationElements(modifyOperation);

    e = DirectoryServer.getEntry(DN.decode("o=test"));
    assertNotNull(e.getAttribute(DirectoryServer.getAttributeType("description",
                                                                  true)));
  }



  /**
   * Tests the ability to perform a modification that adds a new value to an
   * existing attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSuccessAddAttributeValue()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = DirectoryServer.getEntry(DN.decode("o=test"));

    int numValues = 0;
    List<Attribute> attrList =
         e.getAttribute(DirectoryServer.getAttributeType("o", true));
    for (Attribute a : attrList)
    {
      numValues += a.getValues().size();
    }
    assertEquals(numValues, 1);

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("test2"));
    LDAPAttribute attr = new LDAPAttribute("o", values);

    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.ADD, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("o=test"), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveSuccessfulOperationElements(modifyOperation);

    e = DirectoryServer.getEntry(DN.decode("o=test"));
    numValues = 0;
    attrList = e.getAttribute(DirectoryServer.getAttributeType("o", true));
    for (Attribute a : attrList)
    {
      numValues += a.getValues().size();
    }
    assertEquals(numValues, 2);
  }



  /**
   * Tests the ability to perform a modification that adds a new attribute with
   * options to an entry.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testSuccessAddAttributeWithOptions(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry e = DirectoryServer.getEntry(DN.decode(baseDN));

    int numValues = 0;
    List<Attribute> attrList =
         e.getAttribute(DirectoryServer.getAttributeType("dc", true));
    for (Attribute a : attrList)
    {
      numValues += a.getValues().size();
    }
    assertEquals(numValues, 1);

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("test"));
    LDAPAttribute attr = new LDAPAttribute("dc;lang-en-us", values);

    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.ADD, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString(baseDN), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveSuccessfulOperationElements(modifyOperation);

    e = DirectoryServer.getEntry(DN.decode(baseDN));
    numValues = 0;
    attrList = e.getAttribute(DirectoryServer.getAttributeType("dc", true));
    for (Attribute a : attrList)
    {
      numValues += a.getValues().size();
    }
    assertEquals(numValues, 2);
  }



  /**
   * Tests to ensure that a modify attempt fails if an attempt is made to add a
   * second value to a single-valued attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testFailAddToSingleValuedAttribute(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("foo"));
    LDAPAttribute attr = new LDAPAttribute("displayName", values);

    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.ADD, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertFalse(modifyOperation.getResultCode() == ResultCode.SUCCESS);
    retrieveFailedOperationElements(modifyOperation);
  }



  /**
   * Tests to ensure that a modify attempt fails if an attempt is made to add a
   * second value to a single-valued operational attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testFailAddToSingleValuedOperationalAttribute(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password",
         "ds-pwp-account-disabled: true");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("false"));
    LDAPAttribute attr = new LDAPAttribute("ds-pwp-account-disabled", values);

    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.ADD, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertFalse(modifyOperation.getResultCode() == ResultCode.SUCCESS);
    retrieveFailedOperationElements(modifyOperation);
  }



  /**
   * Tests to ensure that a modify attempt fails if an attempt is made to
   * replace a single-valued attribute with multiple values.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testFailReplaceSingleValuedWithMultipleValues(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("foo"));
    values.add(new ASN1OctetString("bar"));
    LDAPAttribute attr = new LDAPAttribute("displayName", values);

    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.REPLACE, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertFalse(modifyOperation.getResultCode() == ResultCode.SUCCESS);
    retrieveFailedOperationElements(modifyOperation);
  }



  /**
   * Tests to ensure that a modify attempt fails if an attempt is made to
   * replace a single-valued operational attribute with multiple values.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testFailReplaceSingleValuedOperationalAttrWithMultipleValues(
       String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("true"));
    values.add(new ASN1OctetString("false"));
    LDAPAttribute attr = new LDAPAttribute("ds-pwp-account-disabled", values);

    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.REPLACE, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertFalse(modifyOperation.getResultCode() == ResultCode.SUCCESS);
    retrieveFailedOperationElements(modifyOperation);
  }



  /**
   * Tests to ensure that a modify attempt fails if an attempt is made to add a
   * value that matches one that already exists.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testFailAddDuplicateValue(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("Test"));
    LDAPAttribute attr = new LDAPAttribute("givenName", values);

    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.ADD, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertFalse(modifyOperation.getResultCode() == ResultCode.SUCCESS);
    retrieveFailedOperationElements(modifyOperation);
  }



  /**
   * Tests to ensure that a modify attempt fails if an attempt is made to
   * replace an attribute with a set of values that contains a duplicate.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testFailReplaceWithDuplicates(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("Foo"));
    values.add(new ASN1OctetString("Foo"));
    LDAPAttribute attr = new LDAPAttribute("mail", values);

    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.REPLACE, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertFalse(modifyOperation.getResultCode() == ResultCode.SUCCESS);
    retrieveFailedOperationElements(modifyOperation);
  }



  /**
   * Tests to ensure that a modify attempt fails if an attempt is made to
   * replace with a value that violates the attribute syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testFailReplaceWithSyntaxViolation(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password",
         "manager: cn=boss," + baseDN);

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("invaliddn"));
    LDAPAttribute attr = new LDAPAttribute("manager", values);

    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.REPLACE, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertFalse(modifyOperation.getResultCode() == ResultCode.SUCCESS);
    retrieveFailedOperationElements(modifyOperation);
  }



  /**
   * Tests to ensure that a modify attempt fails if an attempt is made to add a
   * value that violates the attribute syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testFailAddSyntaxViolation(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("invaliddn"));
    LDAPAttribute attr = new LDAPAttribute("manager", values);

    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.ADD, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertFalse(modifyOperation.getResultCode() == ResultCode.SUCCESS);
    retrieveFailedOperationElements(modifyOperation);
  }



  /**
   * Tests to ensure that a modify attempt fails if an attempt is made to add an
   * attribute that is not allowed by any objectclass.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testFailAddDisallowedAttribute(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("foo"));
    LDAPAttribute attr = new LDAPAttribute("dc", values);

    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.ADD, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertFalse(modifyOperation.getResultCode() == ResultCode.SUCCESS);
    retrieveFailedOperationElements(modifyOperation);
  }



  /**
   * Tests to ensure that a modify attempt succeeds if an attempt is made to add
   * an attribute that is not allowed by any objectclass but the
   * extensibleObject objectclass is also added.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testSuccessAddDisallowedAttributeWithExtensibleObject(
       String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("foo"));
    LDAPAttribute attr = new LDAPAttribute("dc", values);

    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.ADD, attr));

    values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("extensibleObject"));
    attr = new LDAPAttribute("objectClass", values);
    mods.add(new LDAPModification(ModificationType.ADD, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveSuccessfulOperationElements(modifyOperation);
  }



  /**
   * Tests to ensure that a modify attempt fails if an attempt is made to
   * replace the RDN attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testFailReplaceRDNAttribute(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("foo"));
    LDAPAttribute attr = new LDAPAttribute("uid", values);

    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.REPLACE, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertFalse(modifyOperation.getResultCode() == ResultCode.SUCCESS);
    retrieveFailedOperationElements(modifyOperation);
  }



  /**
   * Tests to ensure that a modify attempt fails if an attempt is made to
   * remove the RDN attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testFailRemoveRDNAttribute(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    LDAPAttribute attr = new LDAPAttribute("uid");
    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.DELETE, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertFalse(modifyOperation.getResultCode() == ResultCode.SUCCESS);
    retrieveFailedOperationElements(modifyOperation);
  }



  /**
   * Tests to ensure that a modify attempt fails if an attempt is made to
   * remove the RDN attribute value.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testFailRemoveRDNValue(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("test.user"));
    LDAPAttribute attr = new LDAPAttribute("uid", values);

    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.DELETE, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertFalse(modifyOperation.getResultCode() == ResultCode.SUCCESS);
    retrieveFailedOperationElements(modifyOperation);
  }



  /**
   * Tests to ensure that a modify attempt fails if an attempt is made to
   * replace an RDN attribute in a multivalued RDN.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testFailReplaceOneOfMultipleRDNAttributes(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: givenName=Test+sn=User," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("Foo"));
    LDAPAttribute attr = new LDAPAttribute("givenName", values);

    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.REPLACE, attr));

    ModifyOperation modifyOperation =
         conn.processModify(
              new ASN1OctetString("givenName=Test,sn=User," + baseDN), mods);
    assertFalse(modifyOperation.getResultCode() == ResultCode.SUCCESS);
    retrieveFailedOperationElements(modifyOperation);
  }



  /**
   * Tests to ensure that a modify attempt fails if an attempt is made to
   * remove an RDN attribute value from a multivalued RDN.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testFailRemoveOneOfMultipleRDNValues(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: givenName=Test+sn=User," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    LDAPAttribute attr = new LDAPAttribute("givenName");
    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.DELETE, attr));

    ModifyOperation modifyOperation =
         conn.processModify(
              new ASN1OctetString("givenName=Test,sn=User," + baseDN), mods);
    assertFalse(modifyOperation.getResultCode() == ResultCode.SUCCESS);
    retrieveFailedOperationElements(modifyOperation);
  }



  /**
   * Tests the ability to perform a modification that removes a complete
   * attribute from an entry.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testSuccessRemoveCompleteAttribute(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    LDAPAttribute attr = new LDAPAttribute("displayName");
    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.DELETE, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveSuccessfulOperationElements(modifyOperation);
  }



  /**
   * Tests the ability to perform a modification that removes one of multiple
   * values from an entry.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testSuccessRemoveOneOfManyValues(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password",
         "mail: foo",
         "mail: bar");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("foo"));
    LDAPAttribute attr = new LDAPAttribute("mail", values);

    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.DELETE, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveSuccessfulOperationElements(modifyOperation);
  }



  /**
   * Tests the ability to perform a modification that removes the only value of
   * an existing attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testSuccessRemoveOnlyValue(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password",
         "mail: foo");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("foo"));
    LDAPAttribute attr = new LDAPAttribute("mail", values);

    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.DELETE, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveSuccessfulOperationElements(modifyOperation);
  }



  /**
   * Tests the ability to perform a modification that removes all of multiple
   * values from an existing attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testSuccessRemoveAllOfManyValues(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password",
         "mail: foo",
         "mail: bar");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("foo"));
    values.add(new ASN1OctetString("bar"));
    LDAPAttribute attr = new LDAPAttribute("mail", values);

    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.DELETE, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveSuccessfulOperationElements(modifyOperation);
  }



  /**
   * Tests to ensure that a modify attempt fails if an attempt is made to remove
   * a required attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testFailRemoveRequiredAttribute(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    LDAPAttribute attr = new LDAPAttribute("sn");
    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.DELETE, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertFalse(modifyOperation.getResultCode() == ResultCode.SUCCESS);
    retrieveFailedOperationElements(modifyOperation);
  }



  /**
   * Tests to ensure that a modify attempt fails if an attempt is made to remove
   * the only value for a required attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testFailRemoveRequiredAttributeValue(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("User"));
    LDAPAttribute attr = new LDAPAttribute("sn", values);

    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.DELETE, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertFalse(modifyOperation.getResultCode() == ResultCode.SUCCESS);
    retrieveFailedOperationElements(modifyOperation);
  }



  /**
   * Tests the ability to perform a modification that replaces an existing
   * attribute with something else.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testSuccessReplaceExistingWithNew(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password",
         "mail: foo");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("bar"));
    LDAPAttribute attr = new LDAPAttribute("mail", values);

    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.REPLACE, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveSuccessfulOperationElements(modifyOperation);
  }



  /**
   * Tests the ability to perform a modification that replaces an existing
   * attribute with the same value.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testSuccessReplaceExistingWithSame(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password",
         "mail: foo");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("test.user"));
    LDAPAttribute attr = new LDAPAttribute("uid", values);

    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.REPLACE, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveSuccessfulOperationElements(modifyOperation);

    InternalSearchOperation searchOperation =
         new InternalSearchOperation(
              conn,
              InternalClientConnection.nextOperationID(),
              InternalClientConnection.nextMessageID(),
              new ArrayList<Control>(),
              new ASN1OctetString(baseDN),
              SearchScope.WHOLE_SUBTREE,
              DereferencePolicy.NEVER_DEREF_ALIASES,
              Integer.MAX_VALUE,
              Integer.MAX_VALUE,
              false,
              LDAPFilter.decode("(uid=test.user)"),
              null, null);

    searchOperation.run();
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    assertEquals(searchOperation.getEntriesSent(), 1);
    assertEquals(searchOperation.getErrorMessage().length(), 0);
  }



  /**
   * Tests the ability to perform a modification that deletes a value then
   * adds the same value in a single operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testSuccessDeleteAndAddSameValue(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password",
         "mail: foo");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("Test User"));
    LDAPAttribute attr = new LDAPAttribute("cn", values);

    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.DELETE, attr));
    mods.add(new LDAPModification(ModificationType.ADD, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveSuccessfulOperationElements(modifyOperation);

    InternalSearchOperation searchOperation =
         new InternalSearchOperation(
              conn,
              InternalClientConnection.nextOperationID(),
              InternalClientConnection.nextMessageID(),
              new ArrayList<Control>(),
              new ASN1OctetString(baseDN),
              SearchScope.WHOLE_SUBTREE,
              DereferencePolicy.NEVER_DEREF_ALIASES,
              Integer.MAX_VALUE,
              Integer.MAX_VALUE,
              false,
              LDAPFilter.decode("(cn=Test User)"),
              null, null);

    searchOperation.run();
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    assertEquals(searchOperation.getEntriesSent(), 1);
    assertEquals(searchOperation.getErrorMessage().length(), 0);
  }



  /**
   * Tests the ability to perform a modification that deletes one value of an
   * attribute containing two values, the values are the same but the attribute
   * options differ.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testSuccessDeleteAttributeWithOption(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "givenName;lang-de: X",
         "givenName;lang-fr: X",
         "displayName: Test User",
         "userPassword: password",
         "mail: foo");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("X"));
    LDAPAttribute attr = new LDAPAttribute("givenName;lang-fr", values);

    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.DELETE, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveSuccessfulOperationElements(modifyOperation);

    InternalSearchOperation searchOperation =
         new InternalSearchOperation(
              conn,
              InternalClientConnection.nextOperationID(),
              InternalClientConnection.nextMessageID(),
              new ArrayList<Control>(),
              new ASN1OctetString(baseDN),
              SearchScope.WHOLE_SUBTREE,
              DereferencePolicy.NEVER_DEREF_ALIASES,
              Integer.MAX_VALUE,
              Integer.MAX_VALUE,
              false,
              LDAPFilter.decode("(givenName;lang-de=X)"),
              null, null);

    searchOperation.run();
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    assertEquals(searchOperation.getEntriesSent(), 1);
    assertEquals(searchOperation.getErrorMessage().length(), 0);
  }



  /**
   * Tests the ability to perform a modification that replaces an existing
   * attribute with nothing.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testSuccessReplaceExistingWithNothing(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password",
         "mail: foo");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    LDAPAttribute attr = new LDAPAttribute("mail");
    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.REPLACE, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveSuccessfulOperationElements(modifyOperation);
  }



  /**
   * Tests the ability to perform a modification that replaces a nonexistent
   * attribute with nothing.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testSuccessReplaceNonExistingWithNothing(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    LDAPAttribute attr = new LDAPAttribute("mail");
    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.REPLACE, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveSuccessfulOperationElements(modifyOperation);
  }



  /**
   * Tests the ability to perform a modification that replaces a nonexistent
   * attribute with a new attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testSuccessReplaceNonExistingWithNew(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("foo"));
    LDAPAttribute attr = new LDAPAttribute("mail", values);

    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.REPLACE, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveSuccessfulOperationElements(modifyOperation);
  }



  /**
   * Tests the ability to perform a modification that removes the only existing
   * value and adds a new value.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testSuccessRemoveOnlyExistingAndAddNew(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password",
         "mail: foo");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("foo"));
    LDAPAttribute attr = new LDAPAttribute("mail", values);
    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.DELETE, attr));

    values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("bar"));
    attr = new LDAPAttribute("mail", values);
    mods.add(new LDAPModification(ModificationType.ADD, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveSuccessfulOperationElements(modifyOperation);
  }



  /**
   * Tests the ability to perform a modification that removes one of many values
   * and adds a new value.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testSuccessRemoveOneExistingAndAddNew(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password",
         "mail: foo",
         "mail: bar");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("foo"));
    LDAPAttribute attr = new LDAPAttribute("mail", values);
    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.DELETE, attr));

    values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("baz"));
    attr = new LDAPAttribute("mail", values);
    mods.add(new LDAPModification(ModificationType.ADD, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveSuccessfulOperationElements(modifyOperation);
  }



  /**
   * Tests the ability to perform a modification that removes one of many values
   * existing value and adds multiple new values.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testSuccessRemoveOneExistingAndAddMultipleNew(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password",
         "mail: foo");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("foo"));
    LDAPAttribute attr = new LDAPAttribute("mail", values);
    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.DELETE, attr));

    values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("bar"));
    values.add(new ASN1OctetString("baz"));
    attr = new LDAPAttribute("mail", values);
    mods.add(new LDAPModification(ModificationType.ADD, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveSuccessfulOperationElements(modifyOperation);
  }



  /**
   * Tests to ensure that a modify attempt fails if an attempt is made to remove
   * a nonexistent attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testFailRemoveNonExistentAttribute(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "userPassword: password");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    LDAPAttribute attr = new LDAPAttribute("displayName");
    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.DELETE, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertFalse(modifyOperation.getResultCode() == ResultCode.SUCCESS);
    retrieveFailedOperationElements(modifyOperation);
  }



  /**
   * Tests to ensure that a modify attempt fails if an attempt is made to remove
   * a nonexistent attribute value.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testFailRemoveNonExistentValue(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("Foo"));
    LDAPAttribute attr = new LDAPAttribute("displayName", values);

    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.DELETE, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertFalse(modifyOperation.getResultCode() == ResultCode.SUCCESS);
    retrieveFailedOperationElements(modifyOperation);
  }



  /**
   * Tests to ensure that a modify attempt fails if an attempt is made to remove
   * all objectclasses from an entry.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testFailRemoveAllObjectClasses(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    LDAPAttribute attr = new LDAPAttribute("objectClass");
    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.DELETE, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertFalse(modifyOperation.getResultCode() == ResultCode.SUCCESS);
    retrieveFailedOperationElements(modifyOperation);
  }



  /**
   * Tests to ensure that a modify attempt fails if an attempt is made to
   * replace all objectclasses in an entry with nothing.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testFailReplaceObjectClassesWithNothing(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    LDAPAttribute attr = new LDAPAttribute("objectClass");
    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.REPLACE, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertFalse(modifyOperation.getResultCode() == ResultCode.SUCCESS);
    retrieveFailedOperationElements(modifyOperation);
  }



  /**
   * Tests to ensure that a modify attempt fails if an attempt is made to remove
   * the structural objectclass from an entry.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testFailRemoveStructuralObjectclass(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: ou=People," + baseDN,
         "objectClass: top",
         "objectClass: organizationalUnit",
         "objectClass: extensibleObject",
         "ou: People");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("organizationalUnit"));
    LDAPAttribute attr = new LDAPAttribute("objectClass", values);

    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.DELETE, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("ou=People," + baseDN), mods);
    assertFalse(modifyOperation.getResultCode() == ResultCode.SUCCESS);
    retrieveFailedOperationElements(modifyOperation);
  }



  /**
   * Tests to ensure that a modify attempt fails if an attempt is made to add a
   * second structural objectclass.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testFailAddSecondStructuralObjectClass(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: ou=People," + baseDN,
         "objectClass: top",
         "objectClass: organizationalUnit",
         "objectClass: extensibleObject",
         "ou: People");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("organization"));
    LDAPAttribute attr = new LDAPAttribute("objectClass", values);

    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.ADD, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("ou=People," + baseDN), mods);
    assertFalse(modifyOperation.getResultCode() == ResultCode.SUCCESS);
    retrieveFailedOperationElements(modifyOperation);
  }



  /**
   * Tests the ability to perform a modification that increments a single-valued
   * integer attribute by one.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testSuccessIncrementByOne(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password",
         "mail: foo",
         "employeeNumber: 1");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("1"));
    LDAPAttribute attr = new LDAPAttribute("employeeNumber", values);
    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.INCREMENT, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveSuccessfulOperationElements(modifyOperation);

    Entry e = DirectoryServer.getEntry(DN.decode("uid=test.user," + baseDN));
    List<Attribute> attrList =
         e.getAttribute(DirectoryServer.getAttributeType("employeenumber",
                                                         true));
    assertNotNull(attrList);

    boolean found = false;
    for (Attribute a : attrList)
    {
      for (AttributeValue v : a.getValues())
      {
        assertEquals(Integer.parseInt(v.getStringValue()), 2);
        found = true;
      }
    }
    assertTrue(found);
  }



  /**
   * Tests the ability to perform a modification that increments a single-valued
   * integer attribute by ten.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testSuccessIncrementByTen(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password",
         "mail: foo",
         "employeeNumber: 1");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("10"));
    LDAPAttribute attr = new LDAPAttribute("employeeNumber", values);
    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.INCREMENT, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveSuccessfulOperationElements(modifyOperation);

    Entry e = DirectoryServer.getEntry(DN.decode("uid=test.user," + baseDN));
    List<Attribute> attrList =
         e.getAttribute(DirectoryServer.getAttributeType("employeenumber",
                                                         true));
    assertNotNull(attrList);

    boolean found = false;
    for (Attribute a : attrList)
    {
      for (AttributeValue v : a.getValues())
      {
        assertEquals(Integer.parseInt(v.getStringValue()), 11);
        found = true;
      }
    }
    assertTrue(found);
  }



  /**
   * Tests the ability to perform a modification that increments a single-valued
   * integer attribute by negative one.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testSuccessIncrementByNegativeOne(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password",
         "mail: foo",
         "employeeNumber: 1");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("-1"));
    LDAPAttribute attr = new LDAPAttribute("employeeNumber", values);
    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.INCREMENT, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveSuccessfulOperationElements(modifyOperation);

    Entry e = DirectoryServer.getEntry(DN.decode("uid=test.user," + baseDN));
    List<Attribute> attrList =
         e.getAttribute(DirectoryServer.getAttributeType("employeenumber",
                                                         true));
    assertNotNull(attrList);

    boolean found = false;
    for (Attribute a : attrList)
    {
      for (AttributeValue v : a.getValues())
      {
        assertEquals(Integer.parseInt(v.getStringValue()), 0);
        found = true;
      }
    }
    assertTrue(found);
  }



  /**
   * Tests to ensure that a modify attempt fails if an attempt is made to
   * increment a non-numeric attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testFailIncrementNonNumeric(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("1"));
    LDAPAttribute attr = new LDAPAttribute("displayName", values);
    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.INCREMENT, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertFalse(modifyOperation.getResultCode() == ResultCode.SUCCESS);
    retrieveFailedOperationElements(modifyOperation);
  }



  /**
   * Tests to ensure that a modify attempt fails if an attempt is made to
   * increment a non-numeric attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testFailIncrementValueNonNumeric(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password",
         "mail: 1");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("notnumeric"));
    LDAPAttribute attr = new LDAPAttribute("mail", values);
    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.INCREMENT, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertFalse(modifyOperation.getResultCode() == ResultCode.SUCCESS);
    retrieveFailedOperationElements(modifyOperation);
  }



  /**
   * Tests the ability to perform a modification that increments a multivalued
   * integer attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testSuccessIncrementMultiValued(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password",
         "roomNumber: 1",
         "roomNumber: 2");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("1"));
    LDAPAttribute attr = new LDAPAttribute("roomNumber", values);
    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.INCREMENT, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveSuccessfulOperationElements(modifyOperation);
  }



  /**
   * Tests to ensure that a modify attempt fails if an attempt is made to
   * perform an increment with no increment values in the request.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testFailIncrementNoIncrementValues(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password",
         "roomNumber: 1");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    LDAPAttribute attr = new LDAPAttribute("roomNumber");
    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.INCREMENT, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertFalse(modifyOperation.getResultCode() == ResultCode.SUCCESS);
    retrieveFailedOperationElements(modifyOperation);
  }



  /**
   * Tests to ensure that a modify attempt fails if an attempt is made to
   * perform an increment with multiple increment values in the request.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testFailIncrementMultipleIncrementValues(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password",
         "roomNumber: 1");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("1"));
    values.add(new ASN1OctetString("2"));
    LDAPAttribute attr = new LDAPAttribute("roomNumber", values);
    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.INCREMENT, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertFalse(modifyOperation.getResultCode() == ResultCode.SUCCESS);
    retrieveFailedOperationElements(modifyOperation);
  }



  /**
   * Tests to ensure that a modify attempt fails if an attempt is made to
   * increment a nonexisting attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testFailIncrementNonExisting(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("1"));
    LDAPAttribute attr = new LDAPAttribute("employeeNumber", values);
    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.INCREMENT, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertFalse(modifyOperation.getResultCode() == ResultCode.SUCCESS);
    retrieveFailedOperationElements(modifyOperation);
  }



  /**
   * Tests the ability to perform a modification that removes an unneeded
   * auxiliary objectclass.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testSuccessRemoveUnneededAuxiliaryObjectClass(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "objectClass: extensibleObject",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password",
         "mail: foo",
         "employeeNumber: 1");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("extensibleObject"));
    LDAPAttribute attr = new LDAPAttribute("objectClass", values);
    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.DELETE, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveSuccessfulOperationElements(modifyOperation);

    Entry e = DirectoryServer.getEntry(DN.decode("uid=test.user," + baseDN));
    assertFalse(e.hasObjectClass(
         DirectoryServer.getObjectClass("extensibleobject", true)));
  }



  /**
   * Tests the ability to perform a modification that adds an auxiliary
   * objectclass.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testSuccessAddAuxiliaryObjectClass(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password",
         "mail: foo",
         "employeeNumber: 1");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("extensibleObject"));
    LDAPAttribute attr = new LDAPAttribute("objectClass", values);
    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.ADD, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveSuccessfulOperationElements(modifyOperation);

    Entry e = DirectoryServer.getEntry(DN.decode("uid=test.user," + baseDN));
    assertTrue(e.hasObjectClass(
         DirectoryServer.getObjectClass("extensibleobject", true)));
  }



  /**
   * Tests that an attempt to add an objectclass that already exists will fail.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testFailAddDuplicateObjectClass(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password",
         "mail: foo",
         "employeeNumber: 1");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("inetOrgPerson"));
    LDAPAttribute attr = new LDAPAttribute("objectClass", values);
    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.ADD, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertFalse(modifyOperation.getResultCode() == ResultCode.SUCCESS);
    retrieveFailedOperationElements(modifyOperation);
  }



  /**
   * Tests that an attempt to remove an objectclass that doesn't exist will
   * fail.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testFailRemoveNonExistingObjectClass(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password",
         "mail: foo",
         "employeeNumber: 1");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("organizationalUnit"));
    LDAPAttribute attr = new LDAPAttribute("objectClass", values);
    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.DELETE, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertFalse(modifyOperation.getResultCode() == ResultCode.SUCCESS);
    retrieveFailedOperationElements(modifyOperation);
  }



  /**
   * Tests to ensure that a modify attempt fails if an attempt is made to
   * alter an attribute marked NO-USER-MODIFICATION.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testFailReplaceNoUserModification(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    Socket s = new Socket("127.0.0.1", (int) TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);
    r.setIOTimeout(3000);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(new ASN1OctetString("cn=Directory Manager"),
                                   3, new ASN1OctetString("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeElement(message.encode());

    message = LDAPMessage.decode(r.readElement().decodeAsSequence());
    BindResponseProtocolOp bindResponse =
         message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), 0);

    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("12345678-1234-1234-1234-1234567890ab"));
    LDAPAttribute attr = new LDAPAttribute("entryUUID", values);
    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.REPLACE, attr));

    long modifyRequests  = ldapStatistics.getModifyRequests();
    long modifyResponses = ldapStatistics.getModifyResponses();

    ModifyRequestProtocolOp modifyRequest =
         new ModifyRequestProtocolOp(
                  new ASN1OctetString("uid=test.user," + baseDN), mods);
    message = new LDAPMessage(2, modifyRequest);
    w.writeElement(message.encode());

    message = LDAPMessage.decode(r.readElement().decodeAsSequence());
    ModifyResponseProtocolOp modifyResponse =
         message.getModifyResponseProtocolOp();
    assertFalse(modifyResponse.getResultCode() == 0);

    assertEquals(ldapStatistics.getModifyRequests(), modifyRequests+1);
    waitForModifyResponsesStat(modifyResponses+1);
  }



  /**
   * Tests to ensure that a modify attempt fails if the server is completely
   * read-only.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testFailServerCompletelyReadOnly(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password",
         "mail: foo",
         "employeeNumber: 1");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    DirectoryServer.setWritabilityMode(WritabilityMode.DISABLED);

    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("extensibleObject"));
    LDAPAttribute attr = new LDAPAttribute("objectClass", values);
    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.ADD, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertFalse(modifyOperation.getResultCode() == ResultCode.SUCCESS);
    retrieveFailedOperationElements(modifyOperation);

    DirectoryServer.setWritabilityMode(WritabilityMode.ENABLED);
  }



  /**
   * Tests to ensure that an internal modify attempt succeeds when the server is
   * in an internal-only writability mode.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testSucceedServerInternalOnlyWritability(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password",
         "mail: foo",
         "employeeNumber: 1");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    DirectoryServer.setWritabilityMode(WritabilityMode.INTERNAL_ONLY);

    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("extensibleObject"));
    LDAPAttribute attr = new LDAPAttribute("objectClass", values);
    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.ADD, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveSuccessfulOperationElements(modifyOperation);

    DirectoryServer.setWritabilityMode(WritabilityMode.ENABLED);
  }



  /**
   * Tests to ensure that an external modify attempt fails when the server is in
   * an internal-only writability mode.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testFailServerInternalOnlyWritability(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password",
         "mail: foo",
         "employeeNumber: 1");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    DirectoryServer.setWritabilityMode(WritabilityMode.INTERNAL_ONLY);


    Socket s = new Socket("127.0.0.1", (int) TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);
    r.setIOTimeout(3000);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(new ASN1OctetString("cn=Directory Manager"),
                                   3, new ASN1OctetString("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeElement(message.encode());

    message = LDAPMessage.decode(r.readElement().decodeAsSequence());
    BindResponseProtocolOp bindResponse =
         message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), 0);

    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("extensibleObject"));
    LDAPAttribute attr = new LDAPAttribute("objectClass", values);
    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.ADD, attr));

    long modifyRequests  = ldapStatistics.getModifyRequests();
    long modifyResponses = ldapStatistics.getModifyResponses();

    ModifyRequestProtocolOp modifyRequest =
         new ModifyRequestProtocolOp(
                  new ASN1OctetString("uid=test.user," + baseDN), mods);
    message = new LDAPMessage(2, modifyRequest);
    w.writeElement(message.encode());

    message = LDAPMessage.decode(r.readElement().decodeAsSequence());
    ModifyResponseProtocolOp modifyResponse =
         message.getModifyResponseProtocolOp();
    assertFalse(modifyResponse.getResultCode() == 0);

    assertEquals(ldapStatistics.getModifyRequests(), modifyRequests+1);
    waitForModifyResponsesStat(modifyResponses+1);

    DirectoryServer.setWritabilityMode(WritabilityMode.ENABLED);
  }



  /**
   * Tests to ensure that a modify attempt fails if the backend is completely
   * read-only.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testFailBackendCompletelyReadOnly(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password",
         "mail: foo",
         "employeeNumber: 1");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    Backend b = DirectoryServer.getBackend(DN.decode(baseDN));
    b.setWritabilityMode(WritabilityMode.DISABLED);

    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("extensibleObject"));
    LDAPAttribute attr = new LDAPAttribute("objectClass", values);
    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.ADD, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertFalse(modifyOperation.getResultCode() == ResultCode.SUCCESS);
    retrieveFailedOperationElements(modifyOperation);

    b.setWritabilityMode(WritabilityMode.ENABLED);
  }



  /**
   * Tests to ensure that an internal modify attempt succeeds when the backend
   * is in an internal-only writability mode.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testSucceedBackendInternalOnlyWritability(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password",
         "mail: foo",
         "employeeNumber: 1");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    Backend b = DirectoryServer.getBackend(DN.decode(baseDN));
    b.setWritabilityMode(WritabilityMode.INTERNAL_ONLY);

    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("extensibleObject"));
    LDAPAttribute attr = new LDAPAttribute("objectClass", values);
    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.ADD, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("uid=test.user," + baseDN),
                            mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveSuccessfulOperationElements(modifyOperation);

    b.setWritabilityMode(WritabilityMode.ENABLED);
  }



  /**
   * Tests to ensure that an external modify attempt fails when the backend is
   * in an internal-only writability mode.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testFailBackendInternalOnlyWritability(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user," + baseDN,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "displayName: Test User",
         "userPassword: password",
         "mail: foo",
         "employeeNumber: 1");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    Backend b = DirectoryServer.getBackend(DN.decode(baseDN));
    b.setWritabilityMode(WritabilityMode.INTERNAL_ONLY);


    Socket s = new Socket("127.0.0.1", (int) TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);
    r.setIOTimeout(3000);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(new ASN1OctetString("cn=Directory Manager"),
                                   3, new ASN1OctetString("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeElement(message.encode());

    message = LDAPMessage.decode(r.readElement().decodeAsSequence());
    BindResponseProtocolOp bindResponse =
         message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), 0);

    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("extensibleObject"));
    LDAPAttribute attr = new LDAPAttribute("objectClass", values);
    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.ADD, attr));

    long modifyRequests  = ldapStatistics.getModifyRequests();
    long modifyResponses = ldapStatistics.getModifyResponses();

    ModifyRequestProtocolOp modifyRequest =
         new ModifyRequestProtocolOp(
                  new ASN1OctetString("uid=test.user," + baseDN), mods);
    message = new LDAPMessage(2, modifyRequest);
    w.writeElement(message.encode());

    message = LDAPMessage.decode(r.readElement().decodeAsSequence());
    ModifyResponseProtocolOp modifyResponse =
         message.getModifyResponseProtocolOp();
    assertFalse(modifyResponse.getResultCode() == 0);

    assertEquals(ldapStatistics.getModifyRequests(), modifyRequests+1);
    waitForModifyResponsesStat(modifyResponses+1);

    b.setWritabilityMode(WritabilityMode.ENABLED);
  }



  /**
   * Tests to ensure that change listeners are properly notified for a
   * successful modify operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSuccessNotifyChangeListeners()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    TestChangeNotificationListener changeListener =
         new TestChangeNotificationListener();
    DirectoryServer.registerChangeNotificationListener(changeListener);
    assertEquals(changeListener.getModifyCount(), 0);

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("foo"));
    LDAPAttribute attr = new LDAPAttribute("description", values);

    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.REPLACE, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString("o=test"), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveSuccessfulOperationElements(modifyOperation);

    assertEquals(changeListener.getModifyCount(), 1);
    DirectoryServer.deregisterChangeNotificationListener(changeListener);
  }



  /**
   * Tests to ensure that change listeners are not notified for a failed modify
   * modify operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testFailDoNotNotifyChangeListeners(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    TestChangeNotificationListener changeListener =
         new TestChangeNotificationListener();
    DirectoryServer.registerChangeNotificationListener(changeListener);
    assertEquals(changeListener.getModifyCount(), 0);

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("foo"));
    LDAPAttribute attr = new LDAPAttribute("dc", values);

    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.REPLACE, attr));

    ModifyOperation modifyOperation =
         conn.processModify(new ASN1OctetString(baseDN), mods);
    assertFalse(modifyOperation.getResultCode() == ResultCode.SUCCESS);
    retrieveFailedOperationElements(modifyOperation);

    assertEquals(changeListener.getModifyCount(), 0);
    DirectoryServer.deregisterChangeNotificationListener(changeListener);
  }



  /**
   * Tests a modify operation that gets canceled before startup.
   *
   * @throws  Exception  If an unexpected probem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testCancelBeforeStartup(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("foo"));
    LDAPAttribute attr = new LDAPAttribute("mail", values);

    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.REPLACE, attr));

    ModifyOperation modifyOperation =
         new ModifyOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                             null, new ASN1OctetString(baseDN), mods);

    CancelRequest cancelRequest = new CancelRequest(false,
                                                    "testCancelBeforeStartup");
    modifyOperation.setCancelRequest(cancelRequest);
    modifyOperation.run();
    assertEquals(modifyOperation.getResultCode(), ResultCode.CANCELED);
  }



  /**
   * Tests a modify operation in which the server cannot obtain a lock on the
   * target entry because there is already a read lock held on it.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs", groups = { "slow" })
  public void testCannotLockEntry(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Lock entryLock = LockManager.lockRead(DN.decode(baseDN));

    try
    {
      InternalClientConnection conn =
           InternalClientConnection.getRootConnection();

      ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
      values.add(new ASN1OctetString("foo"));
      LDAPAttribute attr = new LDAPAttribute("mail", values);

      ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
      mods.add(new LDAPModification(ModificationType.REPLACE, attr));

      ModifyOperation modifyOperation =
           conn.processModify(new ASN1OctetString(baseDN), mods);
      assertFalse(modifyOperation.getResultCode() == ResultCode.SUCCESS);
    }
    finally
    {
      LockManager.unlock(DN.decode(baseDN), entryLock);
    }
  }



  /**
   * Tests a modify operation that should be disconnected in a pre-parse plugin.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testDisconnectInPreParseModify(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Socket s = new Socket("127.0.0.1", (int) TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);
    r.setIOTimeout(5000);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(new ASN1OctetString("cn=Directory Manager"),
                                   3, new ASN1OctetString("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeElement(message.encode());

    message = LDAPMessage.decode(r.readElement().decodeAsSequence());
    BindResponseProtocolOp bindResponse =
         message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), 0);


    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("foo"));
    LDAPAttribute attr = new LDAPAttribute("mail", values);

    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.REPLACE, attr));

    ModifyRequestProtocolOp modifyRequest =
         new ModifyRequestProtocolOp(new ASN1OctetString(baseDN), mods);
    message = new LDAPMessage(2, modifyRequest,
         DisconnectClientPlugin.createDisconnectLDAPControlList("PreParse"));
    w.writeElement(message.encode());

    ASN1Element element = r.readElement();
    if (element != null)
    {
      // If we got an element back, then it must be a notice of disconnect
      // unsolicited notification.
      message = LDAPMessage.decode(element.decodeAsSequence());
      assertEquals(message.getProtocolOpType(), OP_TYPE_EXTENDED_RESPONSE);
    }

    try
    {
      s.close();
    } catch (Exception e) {}
  }



  /**
   * Tests a modify operation that should be disconnected in a pre-operation
   * plugin.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDisconnectInPreOperationModify()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Socket s = new Socket("127.0.0.1", (int) TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);
    r.setIOTimeout(5000);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(new ASN1OctetString("cn=Directory Manager"),
                                   3, new ASN1OctetString("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeElement(message.encode());

    message = LDAPMessage.decode(r.readElement().decodeAsSequence());
    BindResponseProtocolOp bindResponse =
         message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), 0);


    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("foo"));
    LDAPAttribute attr = new LDAPAttribute("description", values);

    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.REPLACE, attr));

    ModifyRequestProtocolOp modifyRequest =
         new ModifyRequestProtocolOp(new ASN1OctetString("o=test"), mods);
    message = new LDAPMessage(2, modifyRequest,
         DisconnectClientPlugin.createDisconnectLDAPControlList(
              "PreOperation"));
    w.writeElement(message.encode());

    ASN1Element element = r.readElement();
    if (element != null)
    {
      // If we got an element back, then it must be a notice of disconnect
      // unsolicited notification.
      message = LDAPMessage.decode(element.decodeAsSequence());
      assertEquals(message.getProtocolOpType(), OP_TYPE_EXTENDED_RESPONSE);
    }

    try
    {
      s.close();
    } catch (Exception e) {}
  }



  /**
   * Tests a modify operation that should be disconnected in a post-operation
   * plugin.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testDisconnectInPostOperationModify(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Socket s = new Socket("127.0.0.1", (int) TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);
    r.setIOTimeout(5000);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(new ASN1OctetString("cn=Directory Manager"),
                                   3, new ASN1OctetString("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeElement(message.encode());

    message = LDAPMessage.decode(r.readElement().decodeAsSequence());
    BindResponseProtocolOp bindResponse =
         message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), 0);


    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("foo"));
    LDAPAttribute attr = new LDAPAttribute("mail", values);

    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.REPLACE, attr));

    ModifyRequestProtocolOp modifyRequest =
         new ModifyRequestProtocolOp(new ASN1OctetString(baseDN), mods);
    message = new LDAPMessage(2, modifyRequest,
         DisconnectClientPlugin.createDisconnectLDAPControlList(
              "PostOperation"));
    w.writeElement(message.encode());

    ASN1Element element = r.readElement();
    if (element != null)
    {
      // If we got an element back, then it must be a notice of disconnect
      // unsolicited notification.
      message = LDAPMessage.decode(element.decodeAsSequence());
      assertEquals(message.getProtocolOpType(), OP_TYPE_EXTENDED_RESPONSE);
    }

    try
    {
      s.close();
    } catch (Exception e) {}
  }



  /**
   * Tests a modify operation that should be disconnected in a post-response
   * plugin.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testDisconnectInPostResponseModify(String baseDN)
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    Socket s = new Socket("127.0.0.1", (int) TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);
    r.setIOTimeout(5000);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(new ASN1OctetString("cn=Directory Manager"),
                                   3, new ASN1OctetString("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeElement(message.encode());

    message = LDAPMessage.decode(r.readElement().decodeAsSequence());
    BindResponseProtocolOp bindResponse =
         message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), 0);


    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("foo"));
    LDAPAttribute attr = new LDAPAttribute("mail", values);

    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    mods.add(new LDAPModification(ModificationType.REPLACE, attr));

    ModifyRequestProtocolOp modifyRequest =
         new ModifyRequestProtocolOp(new ASN1OctetString(baseDN), mods);
    message = new LDAPMessage(2, modifyRequest,
         DisconnectClientPlugin.createDisconnectLDAPControlList(
              "PostResponse"));
    w.writeElement(message.encode());

responseLoop:
    while (true)
    {
      ASN1Element element = r.readElement();
      if (element == null)
      {
        // The connection has been closed.
        break responseLoop;
      }

      message = LDAPMessage.decode(element.decodeAsSequence());
      switch (message.getProtocolOpType())
      {
        case OP_TYPE_MODIFY_RESPONSE:
          // This was expected.  The disconnect didn't happen until after the
          // response was sent.
          break;
        case OP_TYPE_EXTENDED_RESPONSE:
          // The server is notifying us that it will be closing the connection.
          break responseLoop;
        default:
          // This is a problem.  It's an unexpected response.
          try
          {
            s.close();
          } catch (Exception e) {}

          throw new Exception("Unexpected response message " + message +
                              " encountered in " +
                              "testDisconnectInPostResponseModify");
      }
    }

    try
    {
      s.close();
    } catch (Exception e) {}
  }



  /**
   * Tests a modify operation that attemtps to set a value for an attribute type
   * that is marked OBSOLETE in the server schema.
   *
   * @param  baseDN  The base DN for the test backend.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testModifyObsoleteAttribute(String baseDN)
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: attributeTypes",
         "attributeTypes: ( testmodifyobsoleteattribute-oid " +
              "NAME 'testModifyObsoleteAttribute' OBSOLETE " +
              "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE " +
              "X-ORGIN 'SchemaBackendTestCase' )");

    String attrName = "testmodifyobsoleteattribute";
    assertFalse(DirectoryServer.getSchema().hasAttributeType(attrName));

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
    assertTrue(DirectoryServer.getSchema().hasAttributeType(attrName));

    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    path = TestCaseUtils.createTempFile(
         "dn: " + baseDN,
         "changetype: modify",
         "add: objectClass",
         "objectClass: extensibleObject",
         "-",
         "replace: testModifyObsoleteAttribute",
         "testModifyObsoleteAttribute: foo");

    args = new String[]
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertFalse(LDAPModify.mainModify(args, false, null, null) == 0);
  }



  /**
   * Tests a modify operation that attemtps to add an OBSOLETE object class to
   * an entry.
   *
   * @param  baseDN  The base DN for the test backend.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testModifyAddObsoleteObjectClass(String baseDN)
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses: ( testmodifyaddobsoleteobjectclass-oid " +
              "NAME 'testModifyAddObsoleteObjectClass' OBSOLETE " +
              "AUXILIARY MAY description X-ORGIN 'SchemaBackendTestCase' )");

    String ocName = "testmodifyaddobsoleteobjectclass";
    assertFalse(DirectoryServer.getSchema().hasObjectClass(ocName));

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
    assertTrue(DirectoryServer.getSchema().hasObjectClass(ocName));

    TestCaseUtils.clearJEBackend(true,"userRoot",baseDN);

    path = TestCaseUtils.createTempFile(
         "dn: " + baseDN,
         "changetype: modify",
         "add: objectClass",
         "objectClass: testModifyAddObsoleteObjectClass");

    args = new String[]
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertFalse(LDAPModify.mainModify(args, false, null, null) == 0);
  }
}

