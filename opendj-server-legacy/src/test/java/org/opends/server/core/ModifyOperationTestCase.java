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
 * Copyright 2006-2011 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.core;

import static org.assertj.core.api.Assertions.*;
import static org.forgerock.opendj.ldap.ModificationType.*;
import static org.forgerock.opendj.ldap.controls.GenericControl.*;
import static org.forgerock.opendj.ldap.requests.Requests.*;
import static org.forgerock.opendj.ldap.schema.CoreSchema.*;
import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.newSearchRequest;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.opends.server.util.ServerConstants.*;
import static org.testng.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.opends.server.TestCaseUtils;
import org.opends.server.api.Backend;
import org.opends.server.plugins.DisconnectClientPlugin;
import org.opends.server.plugins.ShortCircuitPlugin;
import org.opends.server.plugins.UpdatePreOpPlugin;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.protocols.ldap.ModifyRequestProtocolOp;
import org.opends.server.protocols.ldap.ModifyResponseProtocolOp;
import org.opends.server.tools.LDAPModify;
import org.opends.server.tools.RemoteConnection;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.CancelRequest;
import org.opends.server.types.CancelResult;
import org.opends.server.types.Control;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.LockManager.DNLock;
import org.opends.server.types.Modification;
import org.opends.server.types.Operation;
import org.opends.server.types.RawModification;
import org.opends.server.types.WritabilityMode;
import org.opends.server.util.Base64;
import org.opends.server.workflowelement.localbackend.LocalBackendModifyOperation;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * A set of test cases for modify operations.
 */
@SuppressWarnings("javadoc")
public class ModifyOperationTestCase
       extends OperationTestCase
{

  @BeforeClass
  public void restartServer() throws Exception {
    TestCaseUtils.restartServer();
  }

  /** Some of the tests disable the backends, so we reenable them here. */
  @AfterMethod(alwaysRun=true)
  public void reenableBackend() throws DirectoryException {
    for (Object[] backendBaseDN2 : getBaseDNs())
    {
      final DN baseDN = DN.valueOf(backendBaseDN2[0].toString());
      Backend<?> b = DirectoryServer.getBackend(baseDN);
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
  public Object[][] getModifyOperations() throws Exception
  {
    List<ModifyOperation> opList = new ArrayList<>();

    List<Control> noControls = new ArrayList<>();

    List<RawModification> ldapMods = newRawModifications(ADD, "description", "foo");

    opList.add(newModifyOperation(null, ByteString.empty(), ldapMods));
    opList.add(newModifyOperation(noControls, ByteString.empty(), ldapMods));
    opList.add(newModifyOperation(null, ByteString.valueOfUtf8("o=test"), ldapMods));
    opList.add(newModifyOperation(noControls, ByteString.valueOfUtf8("o=test"), ldapMods));

    ldapMods = newRawModifications(DELETE, "description", "foo");

    opList.add(newModifyOperation(null, ByteString.empty(), ldapMods));
    opList.add(newModifyOperation(noControls, ByteString.empty(), ldapMods));
    opList.add(newModifyOperation(null, ByteString.valueOfUtf8("o=test"), ldapMods));
    opList.add(newModifyOperation(noControls, ByteString.valueOfUtf8("o=test"), ldapMods));

    ldapMods = newRawModifications(REPLACE, "description", "foo");

    opList.add(newModifyOperation(null, ByteString.empty(), ldapMods));
    opList.add(newModifyOperation(noControls, ByteString.empty(), ldapMods));
    opList.add(newModifyOperation(null, ByteString.valueOfUtf8("o=test"), ldapMods));
    opList.add(newModifyOperation(noControls, ByteString.valueOfUtf8("o=test"), ldapMods));

    ldapMods = newArrayList(
        newRawModification(DELETE, "description", "foo"),
        newRawModification(ADD, "description", "bar"));

    opList.add(newModifyOperation(null, ByteString.empty(), ldapMods));
    opList.add(newModifyOperation(noControls, ByteString.empty(), ldapMods));
    opList.add(newModifyOperation(null, ByteString.valueOfUtf8("o=test"), ldapMods));
    opList.add(newModifyOperation(noControls, ByteString.valueOfUtf8("o=test"), ldapMods));

    ldapMods = newArrayList(
        newRawModification(REPLACE, "description", "foo"),
        newRawModification(REPLACE, "cn", "bar"));

    opList.add(newModifyOperation(null, ByteString.empty(), ldapMods));
    opList.add(newModifyOperation(noControls, ByteString.empty(), ldapMods));
    opList.add(newModifyOperation(null, ByteString.valueOfUtf8("o=test"), ldapMods));
    opList.add(newModifyOperation(noControls, ByteString.valueOfUtf8("o=test"), ldapMods));



    List<Modification> mods = newModifications(ADD, "description", "foo");

    opList.add(newModifyOperation(null, DN.rootDN(), mods));
    opList.add(newModifyOperation(noControls, DN.rootDN(), mods));
    opList.add(newModifyOperation(null, DN.valueOf("o=test"), mods));
    opList.add(newModifyOperation(noControls, DN.valueOf("o=test"), mods));

    mods = newModifications(DELETE, "description", "foo");

    opList.add(newModifyOperation(null, DN.rootDN(), mods));
    opList.add(newModifyOperation(noControls, DN.rootDN(), mods));
    opList.add(newModifyOperation(null, DN.valueOf("o=test"), mods));
    opList.add(newModifyOperation(noControls, DN.valueOf("o=test"), mods));

    mods = newModifications(REPLACE, "description", "foo");

    opList.add(newModifyOperation(null, DN.rootDN(), mods));
    opList.add(newModifyOperation(noControls, DN.rootDN(), mods));
    opList.add(newModifyOperation(null, DN.valueOf("o=test"), mods));
    opList.add(newModifyOperation(noControls, DN.valueOf("o=test"), mods));

    mods = newArrayList(
        newModification(DELETE, "description", "foo"),
        newModification(ADD, "description", "bar"));

    opList.add(newModifyOperation(null, DN.rootDN(), mods));
    opList.add(newModifyOperation(noControls, DN.rootDN(), mods));
    opList.add(newModifyOperation(null, DN.valueOf("o=test"), mods));
    opList.add(newModifyOperation(noControls, DN.valueOf("o=test"), mods));

    mods = newArrayList(
        newModification(REPLACE, "description", "foo"),
        newModification(REPLACE, "cn", "bar"));

    opList.add(newModifyOperation(null, DN.rootDN(), mods));
    opList.add(newModifyOperation(noControls, DN.rootDN(), mods));
    opList.add(newModifyOperation(null, DN.valueOf("o=test"), mods));
    opList.add(newModifyOperation(noControls, DN.valueOf("o=test"), mods));



    Object[][] objArray = new Object[opList.size()][1];
    for (int i=0; i < objArray.length; i++)
    {
      objArray[i][0] = opList.get(i);
    }

    return objArray;
  }

  private ModifyOperation newModifyOperation(List<Control> requestControls,
      DN entryDn, List<Modification> modifications)
  {
    return new ModifyOperationBasis(
        getRootConnection(), nextOperationID(), nextMessageID(),
        requestControls, entryDn, modifications);
  }

  private ModifyOperation newModifyOperation(List<Control> requestControls,
      ByteString rawEntryDn, List<RawModification> rawModifications)
  {
    return new ModifyOperationBasis(
        getRootConnection(), nextOperationID(), nextMessageID(),
        requestControls, rawEntryDn, rawModifications);
  }

  @DataProvider(name = "baseDNs")
  public Object[][] getBaseDNs()
  {
    return new Object[][] {
         { "o=test"}
    };
  }

  @BeforeMethod
  public void clearTestBackend() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
  }


  /** {@inheritDoc} */
  @Override
  protected Operation[] createTestOperations() throws Exception
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

    modifyOperation.setRawEntryDN(ByteString.valueOfUtf8("uid=test,o=test"));
    assertNotNull(modifyOperation.getRawEntryDN());
    assertEquals(modifyOperation.getRawEntryDN(),
                 ByteString.valueOfUtf8("uid=test,o=test"));

    modifyOperation.setRawEntryDN(originalDN);
    assertNotNull(modifyOperation.getRawEntryDN());
    assertEquals(modifyOperation.getRawEntryDN(), originalDN);
  }



  /**
   * Tests the <CODE>getEntryDN</CODE> method that should decode
   * the raw entry dn and return a non-null DN.
   */
  @Test
  public void testGetEntryDNInitiallyNull()
  {
    List<RawModification> mods = newRawModifications(REPLACE, "description", "foo");
    ModifyOperation modifyOperation = newModifyOperation(null, ByteString.empty(), mods);
    assertNotNull(modifyOperation.getEntryDN());
  }

  /**
   * Tests the <CODE>getEntryDN</CODE> method for the case in which we expect
   * the DN to be initially non-null.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testGetEntryDNInitiallyNonNull() throws Exception
  {
    List<Modification> mods = newModifications(REPLACE, "description", "foo");
    ModifyOperation modifyOperation = newModifyOperation(null, DN.rootDN(), mods);
    assertNotNull(modifyOperation.getEntryDN());
  }



  /**
   * Tests the <CODE>getEntryDN</CODE> method for the case in which we expect
   * the DN to be initially non-null, then is null after the raw DN is
   * changed, but becomes non-null after the call to <CODE>getEntryDN</CODE>.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testGetEntryDNNonNullChangedToNull() throws Exception
  {
    List<Modification> mods = newModifications(REPLACE, "description", "foo");
    ModifyOperation modifyOperation = newModifyOperation(null, DN.rootDN(), mods);
    assertNotNull(modifyOperation.getEntryDN());

    modifyOperation.setRawEntryDN(ByteString.valueOfUtf8("ou=Users,o=test"));
    assertNotNull(modifyOperation.getEntryDN());
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
    List<RawModification> rawMods = modifyOperation.getRawModifications();
    List<RawModification> clonedMods = new ArrayList<>(rawMods);
    modifyOperation.setRawModifications(clonedMods);

    modifyOperation.addRawModification(newRawModification(REPLACE, "test", "test"));

    assertEquals(modifyOperation.getRawModifications().size(), rawMods.size() + 1);

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
    assertTrue(modifyOperation.getProcessingStartTime() > 0);
    assertTrue(modifyOperation.getProcessingStopTime() >=
               modifyOperation.getProcessingStartTime());
    assertTrue(modifyOperation.getProcessingTime() >= 0);

    @SuppressWarnings("unchecked")
    List<LocalBackendModifyOperation> localOps =
        (List<LocalBackendModifyOperation>) modifyOperation.getAttachment(Operation.LOCALBACKENDOPERATIONS);
    assertNotNull(localOps);
    for (LocalBackendModifyOperation curOp : localOps)
    {
      curOp.getNewPasswords();
      curOp.getCurrentPasswords();
      assertNotNull(curOp.getCurrentEntry());
      assertNotNull(curOp.getModifiedEntry());
    }
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
  }



  /**
   * Tests the <CODE>getModifications</CODE> and <CODE>addModification</CODE>
   * methods.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testGetAndAddModifications() throws Exception
  {
    Entry e = DirectoryServer.getEntry(DN.valueOf("o=test"));
    assertThat(e.getAttribute(getDescriptionAttributeType())).isEmpty();

    UpdatePreOpPlugin.reset();
    UpdatePreOpPlugin.addModification(
         new Modification(ModificationType.REPLACE,
             Attributes.create("description", "foo")));


    List<Modification> mods = newModifications(REPLACE, "l", "Austin");
    ModifyOperation modifyOperation =
        getRootConnection().processModify(DN.valueOf("o=test"), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveSuccessfulOperationElements(modifyOperation);

    e = DirectoryServer.getEntry(DN.valueOf("o=test"));
    assertThat(e.getAttribute(getDescriptionAttributeType())).isNotEmpty();

    UpdatePreOpPlugin.reset();
  }



  /**
   * Tests to ensure that a modify attempt fails if an invalid DN is provided.
   */
  @Test
  public void testFailInvalidDN()
  {
    RawModification mod = newRawModification(REPLACE, "description", "foo");
    ModifyOperation modifyOperation = processModify("invaliddn", mod);
    assertNotEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveFailedOperationElements(modifyOperation);
  }



  /**
   * Tests to ensure that a modify attempt fails if the target DN is a suffix
   * that doesn't exist.
   */
  @Test
  public void testFailNoSuchSuffix()
  {
    RawModification mod = newRawModification(REPLACE, "description", "foo");
    ModifyOperation modifyOperation = processModify("o=nonexistent", mod);
    assertNotEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
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
    RawModification mod = newRawModification(REPLACE, "description", "foo");
    ModifyOperation modifyOperation = processModify("cn=test,ou=nosuchparent," + baseDN, mod);
    assertNotEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
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
    RawModification mod = newRawModification(REPLACE, "description", "foo");
    ModifyOperation modifyOperation = processModify("cn=nosuchentry," + baseDN, mod);
    assertNotEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
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
    ModifyOperation modifyOperation = processModify(baseDN);
    assertNotEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveFailedOperationElements(modifyOperation);
  }



  /**
   * Tests the ability to perform a modification that adds a new attribute to an
   * entry.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testSuccessAddAttribute() throws Exception
  {
    Entry e = DirectoryServer.getEntry(DN.valueOf("o=test"));
    assertThat(e.getAttribute(getDescriptionAttributeType())).isEmpty();

    RawModification mod = newRawModification(REPLACE, "description", "foo");
    ModifyOperation modifyOperation = processModify("o=test", mod);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveSuccessfulOperationElements(modifyOperation);

    e = DirectoryServer.getEntry(DN.valueOf("o=test"));
    assertThat(e.getAttribute(getDescriptionAttributeType())).isNotEmpty();
  }



  /**
   * Tests the ability to perform a modification that adds a new value to an
   * existing attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testSuccessAddAttributeValue() throws Exception
  {
    Entry e = DirectoryServer.getEntry(DN.valueOf("o=test"));

    List<Attribute> attrList = e.getAttribute(getOAttributeType());
    assertEquals(countValues(attrList), 1);

    ModifyOperation modifyOperation = processModify("o=test", newRawModification(ADD, "o", "test2"));
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveSuccessfulOperationElements(modifyOperation);

    e = DirectoryServer.getEntry(DN.valueOf("o=test"));
    attrList = e.getAttribute(getOAttributeType());
    assertEquals(countValues(attrList), 2);
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
    Entry e = DirectoryServer.getEntry(DN.valueOf(baseDN));

    List<Attribute> attrList = e.getAttribute(getOAttributeType());
    assertEquals(countValues(attrList), 1);

    RawModification mod = newRawModification(ADD, "o;lang-en-us", "test");
    ModifyOperation modifyOperation = processModify(baseDN, mod);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveSuccessfulOperationElements(modifyOperation);

    e = DirectoryServer.getEntry(DN.valueOf(baseDN));
    attrList = e.getAttribute(getOAttributeType());
    assertEquals(countValues(attrList), 2);
  }

  private int countValues(List<Attribute> attrList)
  {
    int count = 0;
    for (Attribute a : attrList)
    {
      count += a.size();
    }
    return count;
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
    TestCaseUtils.addEntry(
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

    RawModification mod = newRawModification(ADD, "displayName", "foo");
    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN, mod);
    assertNotEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
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
    TestCaseUtils.addEntry(
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

    RawModification mod = newRawModification(ADD, "ds-pwp-account-disabled", "false");
    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN, mod);
    assertNotEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
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
    TestCaseUtils.addEntry(
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

    RawModification mod = newRawModification(REPLACE, "displayName", "foo", "bar");
    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN, mod);
    assertNotEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
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
    TestCaseUtils.addEntry(
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

    RawModification mod = newRawModification(REPLACE, "ds-pwp-account-disabled", "true", "false");
    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN, mod);
    assertNotEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveFailedOperationElements(modifyOperation);
  }

  private ModifyOperation processModify(String entryDN, RawModification... mods)
  {
    return getRootConnection().processModify(ByteString.valueOfUtf8(entryDN), Arrays.asList(mods));
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
    TestCaseUtils.addEntry(
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

    RawModification mod = newRawModification(ADD, "givenName", "Test");
    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN, mod);
    assertNotEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
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
    TestCaseUtils.addEntry(
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

    RawModification mod = newRawModification(REPLACE, "description", "Foo", "Foo");
    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN, mod);
    assertNotEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
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
    TestCaseUtils.addEntry(
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

    RawModification mod = newRawModification(REPLACE, "manager", "invaliddn");
    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN, mod);
    assertNotEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
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
    TestCaseUtils.addEntry(
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

    RawModification mod = newRawModification(ADD, "manager", "invaliddn");
    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN, mod);
    assertNotEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
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
    TestCaseUtils.addEntry(
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

    RawModification mod = newRawModification(ADD, "dc", "foo");
    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN, mod);
    assertEquals(modifyOperation.getResultCode(), ResultCode.OBJECTCLASS_VIOLATION);
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
    TestCaseUtils.addEntry(
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

    RawModification mod = newRawModification(ADD, "objectClass", "extensibleObject");
    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN, mod);
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
    TestCaseUtils.addEntry(
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

    RawModification mod = newRawModification(REPLACE, "uid", "foo");
    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN, mod);
    assertNotEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
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
    TestCaseUtils.addEntry(
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

    RawModification mod = newRawModification(DELETE, "uid");
    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN, mod);
    assertNotEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
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
    TestCaseUtils.addEntry(
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

    RawModification mod = newRawModification(DELETE, "uid", "test.user");
    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN, mod);
    assertNotEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
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
    TestCaseUtils.addEntry(
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

    RawModification mod = newRawModification(REPLACE, "givenName", "Foo");
    ModifyOperation modifyOperation = processModify("givenName=Test,sn=User," + baseDN, mod);
    assertNotEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
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
    TestCaseUtils.addEntry(
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

    RawModification mod = newRawModification(DELETE, "givenName");
    ModifyOperation modifyOperation = processModify("givenName=Test,sn=User," + baseDN, mod);
    assertNotEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
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
    TestCaseUtils.addEntry(
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

    RawModification mod = newRawModification(DELETE, "displayName");
    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN, mod);
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
    TestCaseUtils.addEntry(
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

    RawModification mod = newRawModification(DELETE, "mail", "foo");
    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN, mod);
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
    TestCaseUtils.addEntry(
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

    RawModification mod = newRawModification(DELETE, "mail", "foo");
    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN, mod);
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
    TestCaseUtils.addEntry(
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

    RawModification mod = newRawModification(DELETE, "mail", "foo", "bar");
    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN, mod);
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
    TestCaseUtils.addEntry(
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

    RawModification mod = newRawModification(DELETE, "sn");
    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN, mod);
    assertNotEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
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
    TestCaseUtils.addEntry(
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

    RawModification mod = newRawModification(DELETE, "sn", "User");
    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN, mod);
    assertNotEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveFailedOperationElements(modifyOperation);
  }



  /**
   * Tests the ability to perform a modification that replaces an existing
   * attribute with something else.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testSuccessReplaceExistingWithNew(String baseDN) throws Exception
  {
    TestCaseUtils.addEntry(
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

    RawModification mod = newRawModification(REPLACE, "description", "bar");
    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN, mod);
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
    TestCaseUtils.addEntry(
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

    String dn = "uid=test.user," + baseDN;
    ModifyOperation modifyOperation = processModify(dn, newRawModification(REPLACE, "uid", "test.user"));
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveSuccessfulOperationElements(modifyOperation);

    SearchRequest request = newSearchRequest(dn, SearchScope.WHOLE_SUBTREE, "(uid=test.user)");
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);

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
    TestCaseUtils.addEntry(
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

    RawModification delete = newRawModification(DELETE, "cn", "Test User");
    RawModification add = newRawModification(ADD, "cn", "Test User");
    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN, delete, add);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveSuccessfulOperationElements(modifyOperation);

    SearchRequest request = newSearchRequest(baseDN, SearchScope.WHOLE_SUBTREE, "(cn=Test User)");
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);

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
  public void testSuccessDeleteAttributeWithOption(String baseDN) throws Exception
  {
    TestCaseUtils.addEntry(
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

    RawModification mod = newRawModification(DELETE, "givenName;lang-fr", "X");
    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN, mod);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveSuccessfulOperationElements(modifyOperation);

    SearchRequest request = newSearchRequest(baseDN, SearchScope.WHOLE_SUBTREE, "(givenName;lang-de=X)");
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);

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
    TestCaseUtils.addEntry(
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

    RawModification mod = newRawModification(REPLACE, "description");
    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN, mod);
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
    TestCaseUtils.addEntry(
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

    RawModification mod = newRawModification(REPLACE, "description");
    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN, mod);
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
    TestCaseUtils.addEntry(
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

    RawModification mod = newRawModification(REPLACE, "description", "foo");
    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN, mod);
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
    TestCaseUtils.addEntry(
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

    RawModification delete = newRawModification(DELETE, "mail", "foo");
    RawModification add = newRawModification(ADD, "mail", "bar");
    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN, delete, add);
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
    TestCaseUtils.addEntry(
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

    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN,
        newRawModification(DELETE, "mail", "foo"),
        newRawModification(ADD, "mail", "baz"));
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
    TestCaseUtils.addEntry(
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

    RawModification delete = newRawModification(DELETE, "mail", "foo");
    RawModification add = newRawModification(ADD, "mail", "bar", "baz");
    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN, delete, add);
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
    TestCaseUtils.addEntry(
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

    RawModification mod = newRawModification(DELETE, "displayName");
    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN, mod);
    assertNotEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
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
    TestCaseUtils.addEntry(
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

    RawModification mod = newRawModification(DELETE, "displayName", "Foo");
    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN, mod);
    assertNotEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
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
    TestCaseUtils.addEntry(
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

    RawModification mod = newRawModification(DELETE, "objectClass");
    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN, mod);
    assertNotEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
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
    TestCaseUtils.addEntry(
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

    RawModification mod = newRawModification(REPLACE, "objectClass");
    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN, mod);
    assertNotEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
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
    TestCaseUtils.addEntry(
         "dn: ou=People," + baseDN,
         "objectClass: top",
         "objectClass: organizationalUnit",
         "objectClass: extensibleObject",
         "ou: People");

    RawModification mod = newRawModification(DELETE, "objectClass", "organizationalUnit");
    ModifyOperation modifyOperation = processModify("ou=People," + baseDN, mod);
    assertNotEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
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
    TestCaseUtils.addEntry(
         "dn: ou=People," + baseDN,
         "objectClass: top",
         "objectClass: organizationalUnit",
         "objectClass: extensibleObject",
         "ou: People");

    RawModification mod = newRawModification(ADD, "objectClass", "organization");
    ModifyOperation modifyOperation = processModify("ou=People," + baseDN, mod);
    assertNotEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
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
    TestCaseUtils.addEntry(
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

    RawModification mod = newRawModification(INCREMENT, "employeeNumber", "1");
    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN, mod);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveSuccessfulOperationElements(modifyOperation);

    Entry e = DirectoryServer.getEntry(DN.valueOf("uid=test.user," + baseDN));
    List<Attribute> attrList = e.getAttribute(getEmployeeNumberAttributeType());
    assertIntegerValueExists(attrList, 2);
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
    TestCaseUtils.addEntry(
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

    RawModification mod = newRawModification(INCREMENT, "employeeNumber", "10");
    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN, mod);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveSuccessfulOperationElements(modifyOperation);

    Entry e = DirectoryServer.getEntry(DN.valueOf("uid=test.user," + baseDN));
    List<Attribute> attrList = e.getAttribute(getEmployeeNumberAttributeType());
    assertIntegerValueExists(attrList, 11);
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
    TestCaseUtils.addEntry(
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

    RawModification mod = newRawModification(INCREMENT, "employeeNumber", "-1");
    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN, mod);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveSuccessfulOperationElements(modifyOperation);

    Entry e = DirectoryServer.getEntry(DN.valueOf("uid=test.user," + baseDN));
    List<Attribute> attrList = e.getAttribute(getEmployeeNumberAttributeType());
    assertIntegerValueExists(attrList, 0);
  }

  private AttributeType getEmployeeNumberAttributeType()
  {
    return DirectoryServer.getSchema().getAttributeType("employeenumber");
  }

  private void assertIntegerValueExists(List<Attribute> attrList, int expectedValue)
  {
    boolean found = false;
    for (Attribute a : attrList)
    {
      for (ByteString v : a)
      {
        assertEquals(Integer.parseInt(v.toString()), expectedValue);
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
    TestCaseUtils.addEntry(
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

    RawModification mod = newRawModification(INCREMENT, "displayName", "1");
    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN, mod);
    assertNotEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
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
    TestCaseUtils.addEntry(
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

    RawModification mod = newRawModification(INCREMENT, "description", "notnumeric");
    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN, mod);
    assertNotEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
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
    TestCaseUtils.addEntry(
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

    RawModification mod = newRawModification(INCREMENT, "roomNumber", "1");
    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN, mod);
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
    TestCaseUtils.addEntry(
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

    RawModification mod = newRawModification(INCREMENT, "roomNumber");
    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN, mod);
    assertNotEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
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
    TestCaseUtils.addEntry(
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

    RawModification mod = newRawModification(INCREMENT, "roomNumber", "1", "2");
    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN, mod);
    assertNotEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveFailedOperationElements(modifyOperation);
  }



  /**
   * Tests to ensure that a modify attempt fails if an attempt is made to
   * increment a non existing attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testFailIncrementNonExisting(String baseDN)
         throws Exception
  {
    TestCaseUtils.addEntry(
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

    RawModification mod = newRawModification(INCREMENT, "employeeNumber", "1");
    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN, mod);
    assertNotEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
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
    TestCaseUtils.addEntry(
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

    RawModification mod = newRawModification(DELETE, "objectClass", "extensibleObject");
    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN, mod);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveSuccessfulOperationElements(modifyOperation);

    Entry e = DirectoryServer.getEntry(DN.valueOf("uid=test.user," + baseDN));
    assertFalse(e.hasObjectClass(getExtensibleObjectObjectClass()));
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
    TestCaseUtils.addEntry(
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

    RawModification mod = newRawModification(ADD, "objectClass", "extensibleObject");
    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN, mod);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveSuccessfulOperationElements(modifyOperation);

    Entry e = DirectoryServer.getEntry(DN.valueOf("uid=test.user," + baseDN));
    assertTrue(e.hasObjectClass(getExtensibleObjectObjectClass()));
    assertTrue(e.hasObjectClass(DirectoryServer.getSchema().getObjectClass("inetOrgPerson")));
    assertTrue(e.hasObjectClass(getOrganizationalPersonObjectClass()));
    assertTrue(e.hasObjectClass(getPersonObjectClass()));
    assertTrue(e.hasObjectClass(getTopObjectClass()));
    assertEquals(e.getUserAttributes().size(), 8, "Incorrect number of user attributes");
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
    TestCaseUtils.addEntry(
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

    RawModification mod = newRawModification(ADD, "objectClass", "inetOrgPerson");
    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN, mod);
    assertNotEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveFailedOperationElements(modifyOperation);
  }



  /**
   * Tests that an attempt to remove an objectclass that does not exist will fail.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testFailRemoveNonExistingObjectClass(String baseDN)
         throws Exception
  {
    TestCaseUtils.addEntry(
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

    RawModification mod = newRawModification(DELETE, "objectClass", "organizationalUnit");
    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN, mod);
    assertNotEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
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
    TestCaseUtils.addEntry(
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

    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      conn.bind("cn=Directory Manager", "password");

      long modifyRequests = ldapStatistics.getModifyRequests();
      long modifyResponses = ldapStatistics.getModifyResponses();

      ModifyRequest modifyRequest = Requests.newModifyRequest("uid=test.user," + baseDN)
          .addModification(REPLACE, "entryUUID", "12345678-1234-1234-1234-1234567890ab");
      LDAPMessage message = conn.modify(modifyRequest, false);
      ModifyResponseProtocolOp modifyResponse = message.getModifyResponseProtocolOp();
      assertFalse(modifyResponse.getResultCode() == 0);

      assertEquals(ldapStatistics.getModifyRequests(), modifyRequests + 1);
      waitForModifyResponsesStat(modifyResponses + 1);
    }
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
    TestCaseUtils.addEntry(
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

    DirectoryServer.setWritabilityMode(WritabilityMode.DISABLED);

    RawModification mod = newRawModification(ADD, "objectClass", "extensibleObject");
    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN, mod);
    assertNotEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
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
    TestCaseUtils.addEntry(
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

    DirectoryServer.setWritabilityMode(WritabilityMode.INTERNAL_ONLY);

    RawModification mod = newRawModification(ADD, "objectClass", "extensibleObject");
    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN, mod);
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
    TestCaseUtils.addEntry(
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

    DirectoryServer.setWritabilityMode(WritabilityMode.INTERNAL_ONLY);

    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      conn.bind("cn=Directory Manager", "password");

      long modifyRequests = ldapStatistics.getModifyRequests();
      long modifyResponses = ldapStatistics.getModifyResponses();

      ModifyRequest modifyRequest =
          newModifyRequest("uid=test.user," + baseDN)
          .addModification(ADD, "objectClass", "extensibleObject");
      LDAPMessage message = conn.modify(modifyRequest, false);
      ModifyResponseProtocolOp modifyResponse = message.getModifyResponseProtocolOp();
      assertFalse(modifyResponse.getResultCode() == 0);

      assertEquals(ldapStatistics.getModifyRequests(), modifyRequests + 1);
      waitForModifyResponsesStat(modifyResponses + 1);

      DirectoryServer.setWritabilityMode(WritabilityMode.ENABLED);
    }
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
    TestCaseUtils.addEntry(
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

    Backend<?> b = DirectoryServer.getBackend(DN.valueOf(baseDN));
    b.setWritabilityMode(WritabilityMode.DISABLED);

    RawModification mod = newRawModification(ADD, "objectClass", "extensibleObject");
    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN, mod);
    assertNotEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
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
    TestCaseUtils.addEntry(
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

    Backend<?> b = DirectoryServer.getBackend(DN.valueOf(baseDN));
    b.setWritabilityMode(WritabilityMode.INTERNAL_ONLY);

    RawModification mod = newRawModification(ADD, "objectClass", "extensibleObject");
    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN, mod);
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
    TestCaseUtils.addEntry(
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

    Backend<?> b = DirectoryServer.getBackend(DN.valueOf(baseDN));
    b.setWritabilityMode(WritabilityMode.INTERNAL_ONLY);

    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      conn.bind("cn=Directory Manager", "password");

      long modifyRequests = ldapStatistics.getModifyRequests();
      long modifyResponses = ldapStatistics.getModifyResponses();

      ModifyRequest modifyRequest =
          newModifyRequest("uid=test.user," + baseDN)
          .addModification(ADD, "objectClass", "extensibleObject");
      LDAPMessage message = conn.modify(modifyRequest, false);
      ModifyResponseProtocolOp modifyResponse = message.getModifyResponseProtocolOp();
      assertFalse(modifyResponse.getResultCode() == 0);

      assertEquals(ldapStatistics.getModifyRequests(), modifyRequests + 1);
      waitForModifyResponsesStat(modifyResponses + 1);

      b.setWritabilityMode(WritabilityMode.ENABLED);
    }
  }



  /**
   * Tests to ensure that change listeners are properly notified for a
   * successful modify operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testSuccessNotifyChangeListeners() throws Exception
  {
    TestChangeNotificationListener changeListener =
         new TestChangeNotificationListener();
    DirectoryServer.registerInternalPlugin(changeListener);
    try
    {
      assertEquals(changeListener.getModifyCount(), 0);

      RawModification mod = newRawModification(REPLACE, "description", "foo");
      ModifyOperation modifyOperation = processModify("o=test", mod);
      assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
      retrieveSuccessfulOperationElements(modifyOperation);

      assertEquals(changeListener.getModifyCount(), 1);
    }
    finally
    {
      DirectoryServer.deregisterInternalPlugin(changeListener);
    }
  }



  /**
   * Tests to ensure that change listeners are not notified for a failed modify
   * modify operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testFailDoNotNotifyChangeListeners(String baseDN) throws Exception
  {
    TestChangeNotificationListener changeListener =
         new TestChangeNotificationListener();
    DirectoryServer.registerInternalPlugin(changeListener);
    try
    {
      assertEquals(changeListener.getModifyCount(), 0);

      RawModification mod = newRawModification(REPLACE, "dc", "foo");
      ModifyOperation modifyOperation = processModify(baseDN, mod);
      assertNotEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
      retrieveFailedOperationElements(modifyOperation);

      assertEquals(changeListener.getModifyCount(), 0);
    }
    finally
    {
      DirectoryServer.deregisterInternalPlugin(changeListener);
    }
  }



  /**
   * Tests a modify operation that gets canceled before startup.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testCancelBeforeStartup(String baseDN)
         throws Exception
  {
    List<RawModification> mods = newRawModifications(REPLACE, "description", "foo");

    ModifyOperation modifyOperation =
        newModifyOperation(null, ByteString.valueOfUtf8(baseDN), mods);

    CancelRequest cancelRequest = new CancelRequest(false,
                                                    LocalizableMessage.raw("testCancelBeforeStartup"));
    modifyOperation.abort(cancelRequest);
    modifyOperation.run();
    assertEquals(modifyOperation.getResultCode(), ResultCode.CANCELLED);
  }



  /**
   * Tests a modify operation that gets canceled before startup.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testCancelAfterOperation(String baseDN)
         throws Exception
  {
    List<RawModification> mods = newRawModifications(REPLACE, "description", "foo");

    ModifyOperation modifyOperation =
        newModifyOperation(null, ByteString.valueOfUtf8(baseDN), mods);
    modifyOperation.run();

    CancelRequest cancelRequest = new CancelRequest(false,
                                                    LocalizableMessage.raw("testCancelBeforeStartup"));
    CancelResult cancelResponse = modifyOperation.cancel(cancelRequest);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    assertEquals(cancelResponse.getResultCode(), ResultCode.TOO_LATE);
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
    final DNLock entryLock = DirectoryServer.getLockManager().tryReadLockEntry(DN.valueOf(baseDN));
    try
    {
      RawModification mod = newRawModification(REPLACE, "description", "foo");
      ModifyOperation modifyOperation = processModify(baseDN, mod);
      assertEquals(modifyOperation.getResultCode(), ResultCode.BUSY);
    }
    finally
    {
      entryLock.unlock();
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
    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      conn.bind("cn=Directory Manager", "password");

      List<RawModification> mods = newRawModifications(REPLACE, "description", "foo");
      ModifyRequestProtocolOp modifyRequest = new ModifyRequestProtocolOp(ByteString.valueOfUtf8(baseDN), mods);
      conn.writeMessage(modifyRequest, DisconnectClientPlugin.createDisconnectControlList("PreParse"));

      LDAPMessage message = conn.readMessage();
      if (message != null)
      {
        // If we got an element back, then it must be a notice of disconnect
        // unsolicited notification.
        assertEquals(message.getProtocolOpType(), OP_TYPE_EXTENDED_RESPONSE);
      }
    }
  }



  /**
   * Tests a modify operation that should be disconnected in a pre-operation
   * plugin.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDisconnectInPreOperationModify() throws Exception
  {
    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      conn.bind("cn=Directory Manager", "password");

      List<RawModification> mods = newRawModifications(REPLACE, "description", "foo");
      ModifyRequestProtocolOp modifyRequest = new ModifyRequestProtocolOp(ByteString.valueOfUtf8("o=test"), mods);
      conn.writeMessage(modifyRequest, DisconnectClientPlugin.createDisconnectControlList("PreOperation"));

      LDAPMessage message = conn.readMessage();
      if (message != null)
      {
        // If we got an element back, then it must be a notice of disconnect
        // unsolicited notification.
        assertEquals(message.getProtocolOpType(), OP_TYPE_EXTENDED_RESPONSE);
      }
    }
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
    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      conn.bind("cn=Directory Manager", "password");

      List<RawModification> mods = newRawModifications(REPLACE, "description", "foo");

      ModifyRequestProtocolOp modifyRequest = new ModifyRequestProtocolOp(ByteString.valueOfUtf8(baseDN), mods);
      conn.writeMessage(modifyRequest, DisconnectClientPlugin.createDisconnectControlList("PostOperation"));

      // The operation should NOT be aborted at the post operation stage. While
      // the plugin can disconnect the client, the modify should have already
      // been committed to the backend and a SUCCESS COULD get back to the client.
      waitForResponse(conn, "testDisconnectInPostOperationModify");
    }
  }

  private void waitForResponse(RemoteConnection conn, String string) throws Exception
  {
    responseLoop:
    while (true)
    {
      LDAPMessage message = conn.readMessage();
      if (message == null)
      {
        // The connection has been closed.
        break responseLoop;
      }

      switch (message.getProtocolOpType())
      {
      case OP_TYPE_MODIFY_RESPONSE:
        // This was expected. The disconnect didn't happen until after the
        // response was sent.
        break;
      case OP_TYPE_EXTENDED_RESPONSE:
        // The server is notifying us that it will be closing the connection.
        break responseLoop;
      default:
        // This is a problem. It's an unexpected response.
        throw new Exception("Unexpected response message " + message + " encountered in " + string);
      }
    }
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
    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      conn.bind("cn=Directory Manager", "password");

      List<RawModification> mods = newRawModifications(REPLACE, "description", "foo");
      ModifyRequestProtocolOp modifyRequest = new ModifyRequestProtocolOp(ByteString.valueOfUtf8(baseDN), mods);
      conn.writeMessage(modifyRequest, DisconnectClientPlugin.createDisconnectControlList("PostResponse"));

      waitForResponse(conn, "testDisconnectInPostResponseModify");
    }
  }

  private List<Modification> newModifications(ModificationType modType, String attrType, String attrValue)
  {
    return newArrayList(newModification(modType, attrType, attrValue));
  }

  private Modification newModification(ModificationType modType, String attrType, String attrValue)
  {
    return new Modification(modType, Attributes.create(attrType, attrValue));
  }

  private RawModification newRawModification(ModificationType modType, String attributeType, String... attributeValues)
  {
    return new LDAPModification(modType, new LDAPAttribute(attributeType, newArrayList(attributeValues)));
  }

  private List<RawModification> newRawModifications(ModificationType modType, String attributeType,
      String... attributeValues)
  {
    return newArrayList(newRawModification(modType, attributeType, attributeValues));
  }

  /**
   * Tests a modify operation that attempts to set a value for an attribute type
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



  /**
   * Tests the behavior of the server when short-circuiting out of a modify
   * operation in the pre-parse phase with a success result code.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testShortCircuitInPreParse() throws Exception
  {
    List<Control> controls =
         ShortCircuitPlugin.createShortCircuitControlList(0, "PreParse");

    List<RawModification> mods = newRawModifications(REPLACE, "description", "foo");

    ModifyOperation modifyOperation =
        newModifyOperation(controls, ByteString.valueOfUtf8("o=test"), mods);
    modifyOperation.run();
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    assertTrue(DirectoryServer.entryExists(DN.valueOf("o=test")));
    assertFalse(DirectoryServer.getEntry(DN.valueOf("o=test")).hasAttribute(getDescriptionAttributeType()));
  }


  /**
   * Tests modify operation with the Permissive Modify control.
   */

  /**
   * Test to ensure that a modify operation with the Permissive Modify control
   * succeeds when an attempt is made to add a value that matches one
   * that already exists.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testSuccessPermissiveModifyControlAddDuplicateValue(String baseDN)
         throws Exception
  {
    TestCaseUtils.addEntry(
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

    ModifyRequest modifyRequest = Requests.newModifyRequest("uid=test.user," + baseDN)
        .addModification(ADD, "givenName", "Test")
        .addControl(newControl(OID_PERMISSIVE_MODIFY_CONTROL));
    ModifyOperation modifyOperation = getRootConnection().processModify(modifyRequest);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveSuccessfulOperationElements(modifyOperation);
  }

  /**
   * Test to ensure that a modify operation with the Permissive Modify control
   * succeeds when an attempt is made to delete a non existent value.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testSuccessPermissiveModifyControlRemoveNonExistentValue(String baseDN)
         throws Exception
  {
    TestCaseUtils.addEntry(
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

    ModifyRequest modifyRequest = Requests.newModifyRequest("uid=test.user," + baseDN)
        .addModification(DELETE, "givenName", "Foo")
        .addControl(newControl(OID_PERMISSIVE_MODIFY_CONTROL));
    ModifyOperation modifyOperation = getRootConnection().processModify(modifyRequest);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveSuccessfulOperationElements(modifyOperation);
  }

  /**
   * Test to ensure that a modify operation with the Permissive Modify control
   * succeeds when an attempt is made to delete a non existent attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testSuccessPermissiveModifyControlRemoveNonExistentAttribute(String baseDN)
         throws Exception
  {
    TestCaseUtils.addEntry(
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

    ModifyRequest modifyRequest = Requests.newModifyRequest("uid=test.user," + baseDN)
        .addModification(DELETE, "description")
        .addControl(newControl(OID_PERMISSIVE_MODIFY_CONTROL));
    ModifyOperation modifyOperation = getRootConnection().processModify(modifyRequest);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveSuccessfulOperationElements(modifyOperation);
  }

  /**
   * Tests a modify operation that attempts change the user password doing
   * a delete of all values followed of an add of a new value.
   *
   * @param  baseDN  The base DN for the test backend.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testModifyDelAddPasswordAttribute(String baseDN)
         throws Exception
  {
    TestCaseUtils.addEntry(
         "dn: uid=testPassword01.user," + baseDN,
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

    String path = TestCaseUtils.createTempFile(
         "dn: uid=testPassword01.user," + baseDN,
         "changetype: modify",
         "delete: userPassword",
         "-",
         "add: userPassword",
         "userPassword: aNewPassword");

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
  }

   /**
   * Tests a modify operation that attempts change the user password doing
   * a delete of a clear text value followed of an add of a new value.
   *
   * @param  baseDN  The base DN for the test backend.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testModifyDelOneAddOnePasswordAttribute(String baseDN)
         throws Exception
  {
    TestCaseUtils.addEntry(
         "dn: uid=testPassword02.user," + baseDN,
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

    String path = TestCaseUtils.createTempFile(
         "dn: uid=testPassword02.user," + baseDN,
         "changetype: modify",
         "delete: userPassword",
         "userPassword: password",
         "-",
         "add: userPassword",
         "userPassword: aNewPassword");

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
  }

   /**
   * Tests a modify operation that attempts change the user password doing
   * a delete of an encrypted value followed of an add of a new value.
   *
   * @param  baseDN  The base DN for the test backend.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testModifyDelEncryptedAddOnePasswordAttribute(String baseDN)
         throws Exception
  {
    String dn = "uid=testPassword03.user," + baseDN;
    Entry e = TestCaseUtils.addEntry(
         "dn: " + dn,
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

    String passwd = firstValue(e.getAttribute(getUserPasswordAttributeType()));
    assertNotNull(passwd);

    String path = TestCaseUtils.createTempFile(
         "dn: " + dn,
         "changetype: modify",
         "delete: userPassword",
         "userPassword: " + passwd,
         "-",
         "add: userPassword",
         "userPassword: aNewPassword");

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
  }

  private String firstValue(List<Attribute> attrs)
  {
    for (Attribute a : attrs)
    {
      for (ByteString v : a)
      {
        return v.toString();
      }
    }
    return null;
  }

  /**
   * Tests that it is possible to delete userPassword attributes which have
   * options. Options are not allowed for passwords, but we should allow users
   * to clean them up, for example, after an import of legacy data.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test
  public void testModifyDelPasswordAttributeWithOption() throws Exception
  {
    // @formatter:off
    Entry e = TestCaseUtils.makeEntry(
        "dn: cn=Test User,o=test",
        "objectClass: top",
        "objectClass: person",
        "sn: User",
        "cn: Test User",
        "userPassword: password",
        "userPassword;deleted: oldpassword");
    Backend<?> backend = DirectoryServer.getBackend(TEST_BACKEND_ID);
    backend.addEntry(e, null); // Don't use add operation.

    // Constraint violation.
    assertEquals(TestCaseUtils.applyModifications(false,
        "dn: cn=Test User,o=test",
        "changetype: modify",
        "delete: userPassword;deleted",
        "-"
    ), 0);
    // @formatter:on

    e = DirectoryServer.getEntry(DN.valueOf("cn=Test User,o=test"));
    List<Attribute> attrList = e.getAttribute("userpassword");
    assertThat(attrList).hasSize(1);
    assertFalse(attrList.get(0).getAttributeDescription().hasOptions());
    assertThat(attrList.get(0)).hasSize(1);
  }

  /**
   * Tests that it is possible to delete userPassword attributes using an empty
   * replace which have options. Options are not allowed for passwords, but we
   * should allow users to clean them up, for example, after an import of legacy
   * data.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test
  public void testModifyReplaceEmptyPasswordAttributeWithOption() throws Exception
  {
    // @formatter:off
        Entry e = TestCaseUtils.makeEntry(
        "dn: cn=Test User,o=test",
        "objectClass: top",
        "objectClass: person",
        "sn: User",
        "cn: Test User",
        "userPassword: password",
        "userPassword;deleted: oldpassword");
    Backend<?> backend = DirectoryServer.getBackend(TEST_BACKEND_ID);
    backend.addEntry(e, null); // Don't use add operation.

    // Constraint violation.
    assertEquals(TestCaseUtils.applyModifications(false,
        "dn: cn=Test User,o=test",
        "changetype: modify",
        "replace: userPassword;deleted",
        "-"
    ), 0);
    // @formatter:on

    e = DirectoryServer.getEntry(DN.valueOf("cn=Test User,o=test"));
    List<Attribute> attrList = e.getAttribute("userpassword");
    assertThat(attrList).hasSize(1);
    assertFalse(attrList.get(0).getAttributeDescription().hasOptions());
    assertThat(attrList.get(0)).hasSize(1);
  }

  /**
   * Tests that it is not possible to add userPassword attributes which have
   * options. Options are not allowed for passwords.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test
  public void testModifyAddPasswordAttributeWithOption() throws Exception
  {
    // @formatter:off
        TestCaseUtils.addEntry(
        "dn: cn=Test User,o=test",
        "objectClass: top",
        "objectClass: person",
        "sn: User",
        "cn: Test User",
        "userPassword: password");

    // Constraint violation.
    assertEquals(TestCaseUtils.applyModifications(false,
        "dn: cn=Test User,o=test",
        "changetype: modify",
        "add: userPassword;added",
        "userPassword;added: newpassword",
        "-"
    ), 19);
    // @formatter:on

    Entry e = DirectoryServer.getEntry(DN.valueOf("cn=Test User,o=test"));
    List<Attribute> attrList = e.getAttribute("userpassword");
    assertThat(attrList).hasSize(1);
    assertFalse(attrList.get(0).getAttributeDescription().hasOptions());
    assertThat(attrList.get(0)).hasSize(1);
  }

  /**
   * Tests that it is not possible to add userPassword attributes which have
   * options. Options are not allowed for passwords.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test
  public void testModifyReplaceWithValuesPasswordAttributeWithOption() throws Exception
  {
    // @formatter:off
        TestCaseUtils.addEntry(
        "dn: cn=Test User,o=test",
        "objectClass: top",
        "objectClass: person",
        "sn: User",
        "cn: Test User",
        "userPassword: password");

    // Constraint violation.
    assertEquals(TestCaseUtils.applyModifications(false,
        "dn: cn=Test User,o=test",
        "changetype: modify",
        "replace: userPassword;added",
        "userPassword;added: newpassword",
        "-"
    ), 19);
    // @formatter:on

    Entry e = DirectoryServer.getEntry(DN.valueOf("cn=Test User,o=test"));
    List<Attribute> attrList = e.getAttribute("userpassword");
    assertThat(attrList).hasSize(1);
    assertFalse(attrList.get(0).getAttributeDescription().hasOptions());
    assertThat(attrList.get(0)).hasSize(1);
  }

  /**
   * Tests that the binary option is automatically added to modifications if it
   * is missing and required.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test(dataProvider = "baseDNs")
  public void testAddCertificateWithoutBinaryOption(String baseDN) throws Exception
  {
    TestCaseUtils.addEntry(
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

    String certificateValue =
      "MIICpTCCAg6gAwIBAgIJALeoA6I3ZC/cMA0GCSqGSIb3DQEBBQUAMFYxCzAJBgNV" +
      "BAYTAlVTMRMwEQYDVQQHEwpDdXBlcnRpb25lMRwwGgYDVQQLExNQcm9kdWN0IERl" +
      "dmVsb3BtZW50MRQwEgYDVQQDEwtCYWJzIEplbnNlbjAeFw0xMjA1MDIxNjM0MzVa" +
      "Fw0xMjEyMjExNjM0MzVaMFYxCzAJBgNVBAYTAlVTMRMwEQYDVQQHEwpDdXBlcnRp" +
      "b25lMRwwGgYDVQQLExNQcm9kdWN0IERldmVsb3BtZW50MRQwEgYDVQQDEwtCYWJz" +
      "IEplbnNlbjCBnzANBgkqhkiG9w0BAQEFAAOBjQAwgYkCgYEApysa0c9qc8FB8gIJ" +
      "8zAb1pbJ4HzC7iRlVGhRJjFORkGhyvU4P5o2wL0iz/uko6rL9/pFhIlIMbwbV8sm" +
      "mKeNUPitwiKOjoFDmtimcZ4bx5UTAYLbbHMpEdwSpMC5iF2UioM7qdiwpAfZBd6Z" +
      "69vqNxuUJ6tP+hxtr/aSgMH2i8ECAwEAAaN7MHkwCQYDVR0TBAIwADAsBglghkgB" +
      "hvhCAQ0EHxYdT3BlblNTTCBHZW5lcmF0ZWQgQ2VydGlmaWNhdGUwHQYDVR0OBBYE" +
      "FLlZD3aKDa8jdhzoByOFMAJDs2osMB8GA1UdIwQYMBaAFLlZD3aKDa8jdhzoByOF" +
      "MAJDs2osMA0GCSqGSIb3DQEBBQUAA4GBAE5vccY8Ydd7by2bbwiDKgQqVyoKrkUg" +
      "6CD0WRmc2pBeYX2z94/PWO5L3Fx+eIZh2wTxScF+FdRWJzLbUaBuClrxuy0Y5ifj" +
      "axuJ8LFNbZtsp1ldW3i84+F5+SYT+xI67ZcoAtwx/VFVI9s5I/Gkmu9f9nxjPpK7" +
      "1AIUXiE3Qcck";

    ByteString value = ByteString.wrap(Base64.decode(certificateValue));
    LDAPAttribute attr = new LDAPAttribute("usercertificate", value);
    ModifyOperation modifyOperation = processModify("uid=test.user," + baseDN, new LDAPModification(ADD, attr));
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveSuccessfulOperationElements(modifyOperation);

    Entry e = DirectoryServer.getEntry(DN.valueOf("uid=test.user," + baseDN));
    List<Attribute> attrList = e.getAttribute(getUserCertificateAttributeType());
    assertThat(attrList).hasSize(1);
    Attribute a = attrList.get(0);
    assertTrue(a.getAttributeDescription().hasOption("binary"));
    assertEquals(a.size(), 1);
    assertEquals(Base64.encode(a.iterator().next()), certificateValue);
  }



  /**
   * Tests to ensure that the compressed schema is refreshed after an object
   * class is changed (OPENDJ-169).
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test
  public void testCompressedSchemaRefresh() throws Exception
  {
    String baseDN = "dc=example,dc=com";
    TestCaseUtils.clearBackend("userRoot", baseDN);
    TestCaseUtils.addEntry("dn: cn=Test User," + baseDN,
        "objectClass: top", "objectClass: person",
        "objectClass: organizationalPerson", "sn: User", "cn: Test User");

    // First check that adding "dc" fails because it is not allowed by inetOrgPerson.
    RawModification mod = newRawModification(ADD, "dc", "foo");
    ModifyOperation modifyOperation = processModify("cn=Test User," + baseDN, mod);
    assertEquals(modifyOperation.getResultCode(), ResultCode.OBJECTCLASS_VIOLATION);

    int res = applyModifications(
        false,
        "dn: cn=schema",
        "changetype: modify",
        "delete: objectclasses",
        "objectClasses: ( 2.5.6.6 NAME 'person' SUP top STRUCTURAL MUST ( sn $ cn )"
            + "  MAY ( userPassword $ telephoneNumber $ seeAlso $ description )"
            + "  X-ORIGIN 'RFC 4519' )",
        "-",
        "add: objectclasses",
        "objectClasses: ( 2.5.6.6 NAME 'person' SUP top STRUCTURAL MUST ( sn $ cn )"
            + "  MAY ( dc $ userPassword $ telephoneNumber $ seeAlso $ description )"
            + "  X-ORIGIN 'RFC 4519' )");
    assertEquals(res, 0, "Schema update failed");
    try
    {
      // Modify existing entry.
      modifyOperation = processModify("cn=Test User," + baseDN, mod);
      assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);

      // Add new entry and modify.
      TestCaseUtils.addEntry("dn: cn=Test User2," + baseDN,
          "objectClass: top", "objectClass: person",
          "objectClass: organizationalPerson", "sn: User2", "cn: Test User2");

      modifyOperation = processModify("cn=Test User2," + baseDN, mod);
      assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    }
    finally
    {
      int result = applyModifications(
          false,
          "dn: cn=schema",
          "changetype: modify",
          "delete: objectclasses",
          "objectClasses: ( 2.5.6.6 NAME 'person' SUP top STRUCTURAL MUST ( sn $ cn )"
              + "  MAY ( dc $ userPassword $ telephoneNumber $ seeAlso $ description )"
              + "  X-ORIGIN 'RFC 4519' )",
          "-",
          "add: objectclasses",
          "objectClasses: ( 2.5.6.6 NAME 'person' SUP top STRUCTURAL MUST ( sn $ cn )"
              + "  MAY ( userPassword $ telephoneNumber $ seeAlso $ description )"
              + "  X-ORIGIN 'RFC 4519' )");
      assertEquals(result, 0, "Schema update failed");

      // Add new entry and modify (this time it should fail).
      TestCaseUtils.addEntry("dn: cn=Test User3," + baseDN,
          "objectClass: top", "objectClass: person",
          "objectClass: organizationalPerson", "sn: User3", "cn: Test User3");

      modifyOperation = processModify("cn=Test User3," + baseDN, mod);
      assertEquals(modifyOperation.getResultCode(), ResultCode.OBJECTCLASS_VIOLATION);
    }
  }
}
