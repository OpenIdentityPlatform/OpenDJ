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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.core;

import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertFalse;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Reader;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.protocols.ldap.*;
import org.opends.server.types.*;
import org.opends.server.TestCaseUtils;
import org.opends.server.controls.ProxiedAuthV1Control;
import org.opends.server.controls.ProxiedAuthV2Control;
import org.opends.server.controls.LDAPAssertionRequestControl;
import org.opends.server.plugins.InvocationCounterPlugin;
import org.opends.server.plugins.ShortCircuitPlugin;
import org.opends.server.tools.LDAPModify;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.net.Socket;

public class TestModifyDNOperation extends OperationTestCase
{

  private Entry entry;

  @BeforeClass
  public void setUp() throws Exception
  {
    TestCaseUtils.startServer();
    TestCaseUtils.clearJEBackend(false, "userRoot", "dc=example,dc=com");

    InternalClientConnection connection =
         InternalClientConnection.getRootConnection();

    // Add the example.com entry
    Entry exampleCom = TestCaseUtils.makeEntry(
      "dn: dc=example,dc=com",
      "objectclass: top",
      "objectclass: domain",
      "dc: example"
    );

    // Add the people entry
    Entry people = TestCaseUtils.makeEntry(
      "dn: ou=People,dc=example,dc=com",
      "objectclass: top",
      "objectclass: organizationalUnit",
      "ou: People"
    );

    // Add a test entry.
    entry = TestCaseUtils.makeEntry(
      "dn: uid=user.0,ou=People,dc=example,dc=com",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "givenName: Aaccf",
      "sn: Amar",
      "cn: Aaccf Amar",
      "initials: AQA",
      "employeeNumber: 0",
      "uid: user.0",
      "mail: user.0@example.com",
      "userPassword: password",
      "telephoneNumber: 380-535-2354",
      "homePhone: 707-626-3913",
      "pager: 456-345-7750",
      "mobile: 366-674-7274",
      "street: 99262 Eleventh Street",
      "l: Salem",
      "st: NM",
      "postalCode: 36530",
      "postalAddress: Aaccf Amar$99262 Eleventh Street$Salem, NM  36530",
      "description: This is the description for Aaccf Amar."
    );

    AddOperation addOperation =
         connection.processAdd(exampleCom.getDN(),
                               exampleCom.getObjectClasses(),
                               exampleCom.getUserAttributes(),
                               exampleCom.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
    assertNotNull(DirectoryServer.getEntry(exampleCom.getDN()));

    addOperation =
         connection.processAdd(people.getDN(),
                               people.getObjectClasses(),
                               people.getUserAttributes(),
                               people.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
    assertNotNull(DirectoryServer.getEntry(people.getDN()));

    addOperation =
         connection.processAdd(entry.getDN(),
                               entry.getObjectClasses(),
                               entry.getUserAttributes(),
                               entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
    assertNotNull(DirectoryServer.getEntry(entry.getDN()));
  }

  /**
   * Invokes a number of operation methods on the provided modify operation
   * for which all processing has been completed.
   *
   * @param  modifyDNOperation  The operation to be tested.
   */
  private void examineCompletedOperation(ModifyDNOperation modifyDNOperation)
  {
    assertTrue(modifyDNOperation.getProcessingStartTime() > 0);
    assertTrue(modifyDNOperation.getProcessingStopTime() > 0);
    assertTrue(modifyDNOperation.getProcessingTime() >= 0);
    assertNotNull(modifyDNOperation.getResponseLogElements());

    assertEquals(InvocationCounterPlugin.getPreParseCount(), 1);
    assertEquals(InvocationCounterPlugin.getPreOperationCount(), 1);
    assertEquals(InvocationCounterPlugin.getPostOperationCount(), 1);
    assertEquals(InvocationCounterPlugin.getPostResponseCount(), 1);
  }

  /**
   * Invokes a number of operation methods on the provided modify operation
   * for which the pre-operation plugin was not called.
   *
   * @param  modifyDNOperation  The operation to be tested.
   */
  private void examineIncompleteOperation(ModifyDNOperation modifyDNOperation)
  {
    assertTrue(modifyDNOperation.getProcessingStartTime() > 0);
    assertTrue(modifyDNOperation.getProcessingStopTime() > 0);
    assertTrue(modifyDNOperation.getProcessingTime() >= 0);
    assertNotNull(modifyDNOperation.getResponseLogElements());
    assertTrue(modifyDNOperation.getErrorMessage().length() > 0);

    assertEquals(InvocationCounterPlugin.getPreParseCount(), 1);
    assertEquals(InvocationCounterPlugin.getPreOperationCount(), 0);
    assertEquals(InvocationCounterPlugin.getPostOperationCount(), 1);
    assertEquals(InvocationCounterPlugin.getPostResponseCount(), 1);
  }

  /**
   * Invokes a number of operation methods on the provided modify operation
   * for which an error was found during parsing.
   *
   * @param  modifyDNOperation  The operation to be tested.
   */
  private void examineUnparsedOperation(ModifyDNOperation modifyDNOperation)
  {
    assertTrue(modifyDNOperation.getProcessingStartTime() > 0);
    assertTrue(modifyDNOperation.getProcessingStopTime() > 0);
    assertTrue(modifyDNOperation.getProcessingTime() >= 0);
    assertNotNull(modifyDNOperation.getResponseLogElements());
    assertTrue(modifyDNOperation.getErrorMessage().length() > 0);

    assertEquals(InvocationCounterPlugin.getPreParseCount(), 1);
    assertEquals(InvocationCounterPlugin.getPreOperationCount(), 0);
    assertEquals(InvocationCounterPlugin.getPostOperationCount(), 0);
    assertEquals(InvocationCounterPlugin.getPostResponseCount(), 1);
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public Operation[] createTestOperations()
      throws Exception
  {
    ArrayList<Control> noControls = new ArrayList<Control>(0);
    InternalClientConnection conn =
      InternalClientConnection.getRootConnection();

    ModifyDNOperation[] modifies = new ModifyDNOperation[]
    {
      new ModifyDNOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                            noControls, new ASN1OctetString("cn=test,ou=test"),
                            new ASN1OctetString("cn=test2"), true,
                            new ASN1OctetString("dc=example,dc=com")),
      new ModifyDNOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                            noControls, DN.decode("cn=test,ou=test"),
                            RDN.decode("cn=test2"), true,
                            DN.decode("dc=example,dc=com"))
    };

    return modifies;
  }

  @Test
  public void testRawModify() throws Exception
  {
    ArrayList<Control> noControls = new ArrayList<Control>(0);
    InvocationCounterPlugin.resetAllCounters();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ModifyDNOperation modifyDNOperation =
         new ModifyDNOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                               noControls,
                               new ASN1OctetString("uid=user.0,ou=People,dc=example,dc=com"),
                               new ASN1OctetString("uid=user.test0"), false,
                               null);

    modifyDNOperation.run();
    assertEquals(modifyDNOperation.getResultCode(),
                 ResultCode.SUCCESS);
    assertEquals(modifyDNOperation.getErrorMessage().length(), 0);
    Entry newEntry = DirectoryServer.getEntry(DN.decode(
        "uid=user.test0,ou=People,dc=example,dc=com"));
    assertNotNull(newEntry);
    RDN rdn = newEntry.getDN().getRDN();
    for (int i = 0; i < rdn.getNumValues(); i++)
    {
      AttributeType attribute = rdn.getAttributeType(i);
      assertTrue(newEntry.hasValue(attribute, null, new AttributeValue(attribute, "user.0")));
      assertTrue(newEntry.hasValue(attribute, null, new AttributeValue(attribute, "user.test0")));
    }

    examineCompletedOperation(modifyDNOperation);
    InvocationCounterPlugin.resetAllCounters();

    modifyDNOperation =
         new ModifyDNOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                               noControls,
                               new ASN1OctetString("uid=user.test0,ou=People,dc=example,dc=com"),
                               new ASN1OctetString("uid=user.0"), true,
                               null);

    modifyDNOperation.run();
    assertEquals(modifyDNOperation.getResultCode(),
                 ResultCode.SUCCESS);
    assertEquals(modifyDNOperation.getErrorMessage().length(), 0);
    newEntry = DirectoryServer.getEntry(DN.decode(
        "uid=user.0,ou=People,dc=example,dc=com"));
    assertNotNull(newEntry);
    rdn = newEntry.getDN().getRDN();
    for (int i = 0; i < rdn.getNumValues(); i++)
    {
      AttributeType attribute = rdn.getAttributeType(i);
      assertTrue(newEntry.hasValue(attribute, null, new AttributeValue(attribute, "user.0")));
      assertFalse(newEntry.hasValue(attribute, null, new AttributeValue(attribute, "user.test0")));
    }

    examineCompletedOperation(modifyDNOperation);
  }

  @Test
  public void testProcessedModify() throws Exception
  {
    ArrayList<Control> noControls = new ArrayList<Control>(0);
    InvocationCounterPlugin.resetAllCounters();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ModifyDNOperation modifyDNOperation =
         new ModifyDNOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                               noControls,
                               DN.decode("uid=user.0,ou=People,dc=example,dc=com"),
                               RDN.decode("uid=user.test0"), false,
                               null);

    modifyDNOperation.run();
    assertEquals(modifyDNOperation.getResultCode(),
                 ResultCode.SUCCESS);
    assertEquals(modifyDNOperation.getErrorMessage().length(), 0);
    Entry newEntry = DirectoryServer.getEntry(DN.decode(
        "uid=user.test0,ou=People,dc=example,dc=com"));
    assertNotNull(newEntry);
    RDN rdn = newEntry.getDN().getRDN();
    for (int i = 0; i < rdn.getNumValues(); i++)
    {
      AttributeType attribute = rdn.getAttributeType(i);
      assertTrue(newEntry.hasValue(attribute, null, new AttributeValue(attribute, "user.0")));
      assertTrue(newEntry.hasValue(attribute, null, new AttributeValue(attribute, "user.test0")));
    }

    examineCompletedOperation(modifyDNOperation);
    InvocationCounterPlugin.resetAllCounters();

    modifyDNOperation =
         new ModifyDNOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                               noControls,
                               DN.decode("uid=user.test0,ou=People,dc=example,dc=com"),
                               RDN.decode("uid=user.0"), true,
                               null);

    modifyDNOperation.run();
    assertEquals(modifyDNOperation.getResultCode(),
                 ResultCode.SUCCESS);
    assertEquals(modifyDNOperation.getErrorMessage().length(), 0);
    newEntry = DirectoryServer.getEntry(DN.decode(
        "uid=user.0,ou=People,dc=example,dc=com"));
    assertNotNull(newEntry);
    rdn = newEntry.getDN().getRDN();
    for (int i = 0; i < rdn.getNumValues(); i++)
    {
      AttributeType attribute = rdn.getAttributeType(i);
      assertTrue(newEntry.hasValue(attribute, null, new AttributeValue(attribute, "user.0")));
      assertFalse(newEntry.hasValue(attribute, null, new AttributeValue(attribute, "user.test0")));
    }

    examineCompletedOperation(modifyDNOperation);
  }

  @Test
  public void testRawDeleteOldRDNModify() throws Exception
  {
    ArrayList<Control> noControls = new ArrayList<Control>(0);
    InvocationCounterPlugin.resetAllCounters();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ModifyDNOperation modifyDNOperation =
         new ModifyDNOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                               noControls,
                               new ASN1OctetString("uid=user.0,ou=People,dc=example,dc=com"),
                               new ASN1OctetString("uid=user.test0"), true,
                               null);

    modifyDNOperation.run();
    assertEquals(modifyDNOperation.getResultCode(),
                 ResultCode.SUCCESS);
    assertEquals(modifyDNOperation.getErrorMessage().length(), 0);
    Entry newEntry = DirectoryServer.getEntry(DN.decode(
        "uid=user.test0,ou=People,dc=example,dc=com"));
    assertNotNull(newEntry);
    RDN rdn = newEntry.getDN().getRDN();
    for (int i = 0; i < rdn.getNumValues(); i++)
    {
      AttributeType attribute = rdn.getAttributeType(i);
      assertFalse(newEntry.hasValue(attribute, null, new AttributeValue(attribute, "user.0")));
      assertTrue(newEntry.hasValue(attribute, null, new AttributeValue(attribute, "user.test0")));
    }

    examineCompletedOperation(modifyDNOperation);
    InvocationCounterPlugin.resetAllCounters();

    modifyDNOperation =
         new ModifyDNOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                               noControls,
                               new ASN1OctetString("uid=user.test0,ou=People,dc=example,dc=com"),
                               new ASN1OctetString("uid=user.0"), true,
                               null);

    modifyDNOperation.run();
    assertEquals(modifyDNOperation.getResultCode(),
                 ResultCode.SUCCESS);
    assertEquals(modifyDNOperation.getErrorMessage().length(), 0);
    newEntry = DirectoryServer.getEntry(DN.decode(
        "uid=user.0,ou=People,dc=example,dc=com"));
    assertNotNull(newEntry);
    rdn = newEntry.getDN().getRDN();
    for (int i = 0; i < rdn.getNumValues(); i++)
    {
      AttributeType attribute = rdn.getAttributeType(i);
      assertTrue(newEntry.hasValue(attribute, null, new AttributeValue(attribute, "user.0")));
      assertFalse(newEntry.hasValue(attribute, null, new AttributeValue(attribute, "user.test0")));
    }

    examineCompletedOperation(modifyDNOperation);
  }

  @Test
  public void testProcessedDeleteOldRDNModify() throws Exception
  {
    ArrayList<Control> noControls = new ArrayList<Control>(0);
    InvocationCounterPlugin.resetAllCounters();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ModifyDNOperation modifyDNOperation =
         new ModifyDNOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                               noControls,
                               DN.decode("uid=user.0,ou=People,dc=example,dc=com"),
                               RDN.decode("uid=user.test0"), true,
                               null);

    modifyDNOperation.run();
    assertEquals(modifyDNOperation.getResultCode(),
                 ResultCode.SUCCESS);
    assertEquals(modifyDNOperation.getErrorMessage().length(), 0);
    Entry newEntry = DirectoryServer.getEntry(DN.decode(
        "uid=user.test0,ou=People,dc=example,dc=com"));
    assertNotNull(newEntry);
    RDN rdn = newEntry.getDN().getRDN();
    for (int i = 0; i < rdn.getNumValues(); i++)
    {
      AttributeType attribute = rdn.getAttributeType(i);
      assertFalse(newEntry.hasValue(attribute, null, new AttributeValue(attribute, "user.0")));
      assertTrue(newEntry.hasValue(attribute, null, new AttributeValue(attribute, "user.test0")));
    }

    examineCompletedOperation(modifyDNOperation);
    InvocationCounterPlugin.resetAllCounters();

    modifyDNOperation =
         new ModifyDNOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                               noControls,
                               DN.decode("uid=user.test0,ou=People,dc=example,dc=com"),
                               RDN.decode("uid=user.0"), true,
                               null);

    modifyDNOperation.run();
    assertEquals(modifyDNOperation.getResultCode(),
                 ResultCode.SUCCESS);
    assertEquals(modifyDNOperation.getErrorMessage().length(), 0);
    newEntry = DirectoryServer.getEntry(DN.decode(
        "uid=user.0,ou=People,dc=example,dc=com"));
    assertNotNull(newEntry);
    rdn = newEntry.getDN().getRDN();
    for (int i = 0; i < rdn.getNumValues(); i++)
    {
      AttributeType attribute = rdn.getAttributeType(i);
      assertTrue(newEntry.hasValue(attribute, null, new AttributeValue(attribute, "user.0")));
      assertFalse(newEntry.hasValue(attribute, null, new AttributeValue(attribute, "user.test0")));
    }

    examineCompletedOperation(modifyDNOperation);
  }

  @Test
  public void testRawNewSuperiorModify() throws Exception
  {
    ArrayList<Control> noControls = new ArrayList<Control>(0);
    InvocationCounterPlugin.resetAllCounters();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ModifyDNOperation modifyDNOperation =
         new ModifyDNOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                               noControls,
                               new ASN1OctetString("uid=user.0,ou=People,dc=example,dc=com"),
                               new ASN1OctetString("uid=user.test0"), true,
                               new ASN1OctetString("dc=example,dc=com"));

    modifyDNOperation.run();
    assertEquals(modifyDNOperation.getResultCode(),
                 ResultCode.SUCCESS);
    assertEquals(modifyDNOperation.getErrorMessage().length(), 0);
    Entry newEntry = DirectoryServer.getEntry(DN.decode(
        "uid=user.test0,dc=example,dc=com"));
    assertNotNull(newEntry);
    RDN rdn = newEntry.getDN().getRDN();
    for (int i = 0; i < rdn.getNumValues(); i++)
    {
      AttributeType attribute = rdn.getAttributeType(i);
      assertFalse(newEntry.hasValue(attribute, null, new AttributeValue(attribute, "user.0")));
      assertTrue(newEntry.hasValue(attribute, null, new AttributeValue(attribute, "user.test0")));
    }

    examineCompletedOperation(modifyDNOperation);
    InvocationCounterPlugin.resetAllCounters();

    modifyDNOperation =
         new ModifyDNOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                               noControls,
                               new ASN1OctetString("uid=user.test0,dc=example,dc=com"),
                               new ASN1OctetString("uid=user.0"), true,
                               new ASN1OctetString("ou=People,dc=example,dc=com"));

    modifyDNOperation.run();
    assertEquals(modifyDNOperation.getResultCode(),
                 ResultCode.SUCCESS);
    assertEquals(modifyDNOperation.getErrorMessage().length(), 0);
    newEntry = DirectoryServer.getEntry(DN.decode(
        "uid=user.0,ou=People,dc=example,dc=com"));
    assertNotNull(newEntry);
    rdn = newEntry.getDN().getRDN();
    for (int i = 0; i < rdn.getNumValues(); i++)
    {
      AttributeType attribute = rdn.getAttributeType(i);
      assertTrue(newEntry.hasValue(attribute, null, new AttributeValue(attribute, "user.0")));
      assertFalse(newEntry.hasValue(attribute, null, new AttributeValue(attribute, "user.test0")));
    }

    examineCompletedOperation(modifyDNOperation);
  }

  @Test
  public void testProcessedNewSuperiorModify() throws Exception
  {
    ArrayList<Control> noControls = new ArrayList<Control>(0);
    InvocationCounterPlugin.resetAllCounters();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ModifyDNOperation modifyDNOperation =
         new ModifyDNOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                               noControls,
                               DN.decode("uid=user.0,ou=People,dc=example,dc=com"),
                               RDN.decode("uid=user.test0"), true,
                               DN.decode("dc=example,dc=com"));

    modifyDNOperation.run();
    assertEquals(modifyDNOperation.getResultCode(),
                 ResultCode.SUCCESS);
    assertEquals(modifyDNOperation.getErrorMessage().length(), 0);
    Entry newEntry = DirectoryServer.getEntry(DN.decode(
        "uid=user.test0,dc=example,dc=com"));
    assertNotNull(newEntry);
    RDN rdn = newEntry.getDN().getRDN();
    for (int i = 0; i < rdn.getNumValues(); i++)
    {
      AttributeType attribute = rdn.getAttributeType(i);
      assertFalse(newEntry.hasValue(attribute, null, new AttributeValue(attribute, "user.0")));
      assertTrue(newEntry.hasValue(attribute, null, new AttributeValue(attribute, "user.test0")));
    }

    examineCompletedOperation(modifyDNOperation);
    InvocationCounterPlugin.resetAllCounters();

    modifyDNOperation =
         new ModifyDNOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                               noControls,
                               DN.decode("uid=user.test0,dc=example,dc=com"),
                               RDN.decode("uid=user.0"), true,
                               DN.decode("ou=People,dc=example,dc=com"));

    modifyDNOperation.run();
    assertEquals(modifyDNOperation.getResultCode(),
                 ResultCode.SUCCESS);
    assertEquals(modifyDNOperation.getErrorMessage().length(), 0);
    newEntry = DirectoryServer.getEntry(DN.decode(
        "uid=user.0,ou=People,dc=example,dc=com"));
    assertNotNull(newEntry);
    rdn = newEntry.getDN().getRDN();
    for (int i = 0; i < rdn.getNumValues(); i++)
    {
      AttributeType attribute = rdn.getAttributeType(i);
      assertTrue(newEntry.hasValue(attribute, null, new AttributeValue(attribute, "user.0")));
      assertFalse(newEntry.hasValue(attribute, null, new AttributeValue(attribute, "user.test0")));
    }

    examineCompletedOperation(modifyDNOperation);
  }

  @Test
  public void testRawRDNModify() throws Exception
  {
    ArrayList<Control> noControls = new ArrayList<Control>(0);
    InvocationCounterPlugin.resetAllCounters();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ModifyDNOperation modifyDNOperation =
         new ModifyDNOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                               noControls,
                               DN.decode("uid=user.0,ou=People,dc=example,dc=com"),
                               RDN.decode("cn=Aaccf Amar Test"), true,
                               DN.decode("dc=example,dc=com"));

    modifyDNOperation.run();
    assertEquals(modifyDNOperation.getResultCode(),
                 ResultCode.SUCCESS);
    assertEquals(modifyDNOperation.getErrorMessage().length(), 0);
    Entry newEntry = DirectoryServer.getEntry(DN.decode(
        "cn=Aaccf Amar Test,dc=example,dc=com"));
    assertNotNull(newEntry);
    assertNull(DirectoryServer.getEntry(DN.decode("uid=user.0,ou=People,dc=example,dc=com")));
    assertNull(newEntry.getAttribute("uid"));

    for(Attribute attribute : newEntry.getAttribute("cn"))
    {
      assertTrue(attribute.hasValue(new AttributeValue(attribute.getAttributeType(), "Aaccf Amar Test")));
      assertTrue(attribute.hasValue(new AttributeValue(attribute.getAttributeType(), "Aaccf Amar")));
    }

    examineCompletedOperation(modifyDNOperation);
    InvocationCounterPlugin.resetAllCounters();

    modifyDNOperation =
         new ModifyDNOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                               noControls,
                               DN.decode("cn=Aaccf Amar Test,dc=example,dc=com"),
                               RDN.decode("uid=user.0"), false,
                               DN.decode("ou=People,dc=example,dc=com"));

    modifyDNOperation.run();
    assertEquals(modifyDNOperation.getResultCode(),
                 ResultCode.SUCCESS);
    assertEquals(modifyDNOperation.getErrorMessage().length(), 0);
    Entry newOldEntry = DirectoryServer.getEntry(DN.decode(
        "uid=user.0,ou=People,dc=example,dc=com"));
    assertNotNull(newOldEntry);
    assertNull(DirectoryServer.getEntry(DN.decode("cn=Aaccf Amar Test,dc=example,dc=com")));
    for(Attribute attribute : newOldEntry.getAttribute("cn"))
    {
      assertTrue(attribute.hasValue(new AttributeValue(attribute.getAttributeType(), "Aaccf Amar Test")));
      assertTrue(attribute.hasValue(new AttributeValue(attribute.getAttributeType(), "Aaccf Amar")));
    }
    for(Attribute attribute : newOldEntry.getAttribute("uid"))
    {
      assertTrue(attribute.hasValue(new AttributeValue(attribute.getAttributeType(), "user.0")));
    }

    examineCompletedOperation(modifyDNOperation);
  }

  @Test
  public void testInvalidEntryModify() throws Exception
  {
    ArrayList<Control> noControls = new ArrayList<Control>(0);
    InvocationCounterPlugin.resetAllCounters();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ModifyDNOperation modifyDNOperation =
         new ModifyDNOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                               noControls,
                               DN.decode("uid=user.invalid,ou=People,dc=example,dc=com"),
                               RDN.decode("uid=user.test0"), true,
                               DN.decode("dc=example,dc=com"));

    modifyDNOperation.run();
    assertEquals(modifyDNOperation.getResultCode(),
                 ResultCode.NO_SUCH_OBJECT);
    assertTrue(modifyDNOperation.getErrorMessage().length() > 0);
    examineIncompleteOperation(modifyDNOperation);
  }

  @Test
  public void testInvalidRDNModify() throws Exception
  {
    ArrayList<Control> noControls = new ArrayList<Control>(0);
    InvocationCounterPlugin.resetAllCounters();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ModifyDNOperation modifyDNOperation =
         new ModifyDNOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                               noControls,
                               DN.decode("uid=user.0,ou=People,dc=example,dc=com"),
                               RDN.decode("invalid=invalid"), true,
                               DN.decode("dc=example,dc=com"));

    modifyDNOperation.run();
    assertEquals(modifyDNOperation.getResultCode(),
                 ResultCode.OBJECTCLASS_VIOLATION);
    assertTrue(modifyDNOperation.getErrorMessage().length() > 0);
    examineIncompleteOperation(modifyDNOperation);
  }

  @Test
  public void testInvalidSuperiorModify() throws Exception
  {
    ArrayList<Control> noControls = new ArrayList<Control>(0);
    InvocationCounterPlugin.resetAllCounters();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ModifyDNOperation modifyDNOperation =
         new ModifyDNOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                               noControls,
                               DN.decode("uid=user.0,ou=People,dc=example,dc=com"),
                               RDN.decode("uid=user.test0"), true,
                               DN.decode("dc=invalid,dc=com"));

    modifyDNOperation.run();
    assertEquals(modifyDNOperation.getResultCode(),
                 ResultCode.NO_SUCH_OBJECT);
    assertTrue(modifyDNOperation.getErrorMessage().length() > 0);
    examineIncompleteOperation(modifyDNOperation);
  }

  @Test
  public void testRawNoSuchDNModify() throws Exception
  {
    ArrayList<Control> noControls = new ArrayList<Control>(0);
    InvocationCounterPlugin.resetAllCounters();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ModifyDNOperation modifyDNOperation =
         new ModifyDNOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                               noControls,
                               new ASN1OctetString("invalid DN"),
                               new ASN1OctetString("uid=user.test0"), true,
                               new ASN1OctetString("dc=example,dc=com"));

    modifyDNOperation.run();
    assertEquals(modifyDNOperation.getResultCode(),
                 ResultCode.INVALID_DN_SYNTAX);
    assertTrue(modifyDNOperation.getErrorMessage().length() > 0);
    examineUnparsedOperation(modifyDNOperation);
  }

  @Test
  public void testRawNoSuchRDNModify() throws Exception
  {
    ArrayList<Control> noControls = new ArrayList<Control>(0);
    InvocationCounterPlugin.resetAllCounters();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ModifyDNOperation modifyDNOperation =
         new ModifyDNOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                               noControls,
                               new ASN1OctetString("uid=user.0,ou=People,dc=example,dc=com"),
                               new ASN1OctetString("invalid RDN"), true,
                               new ASN1OctetString("dc=example,dc=com"));

    modifyDNOperation.run();
    assertEquals(modifyDNOperation.getResultCode(),
                 ResultCode.INVALID_DN_SYNTAX);
    assertTrue(modifyDNOperation.getErrorMessage().length() > 0);
    examineUnparsedOperation(modifyDNOperation);
  }

  @Test
  public void testRawInvalidSuperiorModify() throws Exception
  {
    ArrayList<Control> noControls = new ArrayList<Control>(0);
    InvocationCounterPlugin.resetAllCounters();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ModifyDNOperation modifyDNOperation =
         new ModifyDNOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                               noControls,
                               new ASN1OctetString("uid=user.0,ou=People,dc=example,dc=com"),
                               new ASN1OctetString("uid=user.test0"), true,
                               new ASN1OctetString("invalid superior"));

    modifyDNOperation.run();
    assertEquals(modifyDNOperation.getResultCode(),
                 ResultCode.INVALID_DN_SYNTAX);
    assertTrue(modifyDNOperation.getErrorMessage().length() > 0);
    examineUnparsedOperation(modifyDNOperation);
  }

  @Test
  public void testModifySuffix() throws Exception
  {
    ArrayList<Control> noControls = new ArrayList<Control>(0);
    InvocationCounterPlugin.resetAllCounters();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ModifyDNOperation modifyDNOperation =
         new ModifyDNOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                               noControls,
                               DN.decode("dc=example,dc=com"),
                               RDN.decode("dc=exampletest"), true,
                               null);

    modifyDNOperation.run();
    assertEquals(modifyDNOperation.getResultCode(),
                 ResultCode.UNWILLING_TO_PERFORM);
    assertTrue(modifyDNOperation.getErrorMessage().length() > 0);
    examineIncompleteOperation(modifyDNOperation);
  }

  @Test
  public void testRawProxyAuthV1Modify() throws Exception
  {
    ProxiedAuthV1Control authV1Control =
         new ProxiedAuthV1Control(new ASN1OctetString());
    List<Control> controls = new ArrayList<Control>();
    controls.add(authV1Control);
    InvocationCounterPlugin.resetAllCounters();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ModifyDNOperation modifyDNOperation =
         new ModifyDNOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                               controls,
                               new ASN1OctetString("uid=user.0,ou=People,dc=example,dc=com"),
                               new ASN1OctetString("uid=user.test0"), false,
                               null);

    modifyDNOperation.run();
    assertEquals(modifyDNOperation.getResultCode(),
                 ResultCode.SUCCESS);
    assertEquals(modifyDNOperation.getErrorMessage().length(), 0);
    Entry newEntry = DirectoryServer.getEntry(DN.decode(
        "uid=user.test0,ou=People,dc=example,dc=com"));
    assertNotNull(newEntry);
    RDN rdn = newEntry.getDN().getRDN();
    for (int i = 0; i < rdn.getNumValues(); i++)
    {
      AttributeType attribute = rdn.getAttributeType(i);
      assertTrue(newEntry.hasValue(attribute, null, new AttributeValue(attribute, "user.0")));
      assertTrue(newEntry.hasValue(attribute, null, new AttributeValue(attribute, "user.test0")));
    }

    examineCompletedOperation(modifyDNOperation);
    InvocationCounterPlugin.resetAllCounters();

    modifyDNOperation =
         new ModifyDNOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                               controls,
                               new ASN1OctetString("uid=user.test0,ou=People,dc=example,dc=com"),
                               new ASN1OctetString("uid=user.0"), true,
                               null);

    modifyDNOperation.run();
    assertEquals(modifyDNOperation.getResultCode(),
                 ResultCode.SUCCESS);
    assertEquals(modifyDNOperation.getErrorMessage().length(), 0);
    newEntry = DirectoryServer.getEntry(DN.decode(
        "uid=user.0,ou=People,dc=example,dc=com"));
    assertNotNull(newEntry);
    rdn = newEntry.getDN().getRDN();
    for (int i = 0; i < rdn.getNumValues(); i++)
    {
      AttributeType attribute = rdn.getAttributeType(i);
      assertTrue(newEntry.hasValue(attribute, null, new AttributeValue(attribute, "user.0")));
      assertFalse(newEntry.hasValue(attribute, null, new AttributeValue(attribute, "user.test0")));
    }

    examineCompletedOperation(modifyDNOperation);
  }

  @Test
  public void testProcessedProxyAuthV1Modify() throws Exception
  {
    ProxiedAuthV1Control authV1Control =
         new ProxiedAuthV1Control(new ASN1OctetString());
    List<Control> controls = new ArrayList<Control>();
    controls.add(authV1Control);
    InvocationCounterPlugin.resetAllCounters();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ModifyDNOperation modifyDNOperation =
         new ModifyDNOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                               controls,
                               DN.decode("uid=user.0,ou=People,dc=example,dc=com"),
                               RDN.decode("uid=user.test0"), false,
                               null);

    modifyDNOperation.run();
    assertEquals(modifyDNOperation.getResultCode(),
                 ResultCode.SUCCESS);
    assertEquals(modifyDNOperation.getErrorMessage().length(), 0);
    Entry newEntry = DirectoryServer.getEntry(DN.decode(
        "uid=user.test0,ou=People,dc=example,dc=com"));
    assertNotNull(newEntry);
    RDN rdn = newEntry.getDN().getRDN();
    for (int i = 0; i < rdn.getNumValues(); i++)
    {
      AttributeType attribute = rdn.getAttributeType(i);
      assertTrue(newEntry.hasValue(attribute, null, new AttributeValue(attribute, "user.0")));
      assertTrue(newEntry.hasValue(attribute, null, new AttributeValue(attribute, "user.test0")));
    }

    examineCompletedOperation(modifyDNOperation);
    InvocationCounterPlugin.resetAllCounters();

    modifyDNOperation =
         new ModifyDNOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                               controls,
                               DN.decode("uid=user.test0,ou=People,dc=example,dc=com"),
                               RDN.decode("uid=user.0"), true,
                               null);

    modifyDNOperation.run();
    assertEquals(modifyDNOperation.getResultCode(),
                 ResultCode.SUCCESS);
    assertEquals(modifyDNOperation.getErrorMessage().length(), 0);
    newEntry = DirectoryServer.getEntry(DN.decode(
        "uid=user.0,ou=People,dc=example,dc=com"));
    assertNotNull(newEntry);
    rdn = newEntry.getDN().getRDN();
    for (int i = 0; i < rdn.getNumValues(); i++)
    {
      AttributeType attribute = rdn.getAttributeType(i);
      assertTrue(newEntry.hasValue(attribute, null, new AttributeValue(attribute, "user.0")));
      assertFalse(newEntry.hasValue(attribute, null, new AttributeValue(attribute, "user.test0")));
    }

    examineCompletedOperation(modifyDNOperation);
  }

  @Test
  public void testProcessedProxyAuthV1DeniedModify() throws Exception
  {
    ProxiedAuthV1Control authV1Control =
         new ProxiedAuthV1Control(new ASN1OctetString("cn=nonexistent,o=test"));
    List<Control> controls = new ArrayList<Control>();
    controls.add(authV1Control);
    InvocationCounterPlugin.resetAllCounters();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ModifyDNOperation modifyDNOperation =
         new ModifyDNOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                               controls,
                               DN.decode("uid=user.0,ou=People,dc=example,dc=com"),
                               RDN.decode("uid=user.test0"), false,
                               null);

    modifyDNOperation.run();
    assertEquals(modifyDNOperation.getResultCode(),
                 ResultCode.AUTHORIZATION_DENIED);
    assertTrue(modifyDNOperation.getErrorMessage().length() > 0);

    examineIncompleteOperation(modifyDNOperation);
  }

  @Test
  public void testProcessedProxyAuthV2Modify() throws Exception
  {
    ProxiedAuthV2Control authV2Control =
         new ProxiedAuthV2Control(new ASN1OctetString("dn:"));
    List<Control> controls = new ArrayList<Control>();
    controls.add(authV2Control);
    InvocationCounterPlugin.resetAllCounters();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ModifyDNOperation modifyDNOperation =
         new ModifyDNOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                               controls,
                               DN.decode("uid=user.0,ou=People,dc=example,dc=com"),
                               RDN.decode("uid=user.test0"), false,
                               null);

    modifyDNOperation.run();
    assertEquals(modifyDNOperation.getResultCode(),
                 ResultCode.SUCCESS);
    assertEquals(modifyDNOperation.getErrorMessage().length(), 0);
    Entry newEntry = DirectoryServer.getEntry(DN.decode(
        "uid=user.test0,ou=People,dc=example,dc=com"));
    assertNotNull(newEntry);
    RDN rdn = newEntry.getDN().getRDN();
    for (int i = 0; i < rdn.getNumValues(); i++)
    {
      AttributeType attribute = rdn.getAttributeType(i);
      assertTrue(newEntry.hasValue(attribute, null, new AttributeValue(attribute, "user.0")));
      assertTrue(newEntry.hasValue(attribute, null, new AttributeValue(attribute, "user.test0")));
    }

    examineCompletedOperation(modifyDNOperation);
    InvocationCounterPlugin.resetAllCounters();

    modifyDNOperation =
         new ModifyDNOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                               controls,
                               DN.decode("uid=user.test0,ou=People,dc=example,dc=com"),
                               RDN.decode("uid=user.0"), true,
                               null);

    modifyDNOperation.run();
    assertEquals(modifyDNOperation.getResultCode(),
                 ResultCode.SUCCESS);
    assertEquals(modifyDNOperation.getErrorMessage().length(), 0);
    newEntry = DirectoryServer.getEntry(DN.decode(
        "uid=user.0,ou=People,dc=example,dc=com"));
    assertNotNull(newEntry);
    rdn = newEntry.getDN().getRDN();
    for (int i = 0; i < rdn.getNumValues(); i++)
    {
      AttributeType attribute = rdn.getAttributeType(i);
      assertTrue(newEntry.hasValue(attribute, null, new AttributeValue(attribute, "user.0")));
      assertFalse(newEntry.hasValue(attribute, null, new AttributeValue(attribute, "user.test0")));
    }

    examineCompletedOperation(modifyDNOperation);
  }

  @Test
  public void testProcessedProxyAuthV2DeniedModify() throws Exception
  {
    ProxiedAuthV2Control authV2Control = new ProxiedAuthV2Control(
         new ASN1OctetString("dn:cn=nonexistent,o=test"));
    List<Control> controls = new ArrayList<Control>();
    controls.add(authV2Control);
    InvocationCounterPlugin.resetAllCounters();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ModifyDNOperation modifyDNOperation =
         new ModifyDNOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                               controls,
                               DN.decode("uid=user.0,ou=People,dc=example,dc=com"),
                               RDN.decode("uid=user.test0"), false,
                               null);

    modifyDNOperation.run();
    assertEquals(modifyDNOperation.getResultCode(),
                 ResultCode.AUTHORIZATION_DENIED);
    assertTrue(modifyDNOperation.getErrorMessage().length() > 0);

    examineIncompleteOperation(modifyDNOperation);
  }

  @Test(enabled = false) //FIXME: Issue 741
  public void testProcessedProxyAuthV2CriticalityModify() throws Exception
  {
    ProxiedAuthV2Control authV2Control = new ProxiedAuthV2Control(
         new ASN1OctetString("dn:cn=nonexistent,o=test"));
    List<Control> controls = new ArrayList<Control>();
    authV2Control.setCritical(false);
    controls.add(authV2Control);
    InvocationCounterPlugin.resetAllCounters();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ModifyDNOperation modifyDNOperation =
         new ModifyDNOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                               controls,
                               DN.decode("uid=user.0,ou=People,dc=example,dc=com"),
                               RDN.decode("uid=user.test0"), false,
                               null);

    modifyDNOperation.run();
    assertEquals(modifyDNOperation.getResultCode(),
                 ResultCode.PROTOCOL_ERROR);
    assertTrue(modifyDNOperation.getErrorMessage().length() > 0);

    examineIncompleteOperation(modifyDNOperation);
  }

  @Test
  public void testProcessedUnsupportedControlModify() throws Exception
  {
    LDAPFilter ldapFilter = LDAPFilter.decode("(preferredlanguage=ja)");
    LDAPAssertionRequestControl assertControl =
         new LDAPAssertionRequestControl("1.1.1.1.1.1", true, ldapFilter);
    List<Control> controls = new ArrayList<Control>();
    controls.add(assertControl);
    InvocationCounterPlugin.resetAllCounters();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ModifyDNOperation modifyDNOperation =
         new ModifyDNOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                               controls,
                               DN.decode("uid=user.0,ou=People,dc=example,dc=com"),
                               RDN.decode("uid=user.test0"), false,
                               null);

    modifyDNOperation.run();
    assertEquals(modifyDNOperation.getResultCode(),
                 ResultCode.UNAVAILABLE_CRITICAL_EXTENSION);
    assertTrue(modifyDNOperation.getErrorMessage().length() > 0);

    examineIncompleteOperation(modifyDNOperation);
  }

  @Test
  public void testShortCircuitModify() throws Exception
  {
    // Since we are going to be watching the post-response count, we need to
    // wait for the server to become idle before kicking off the next request to
    // ensure that any remaining post-response processing from the previous
    // operation has completed.
    assertTrue(DirectoryServer.getWorkQueue().waitUntilIdle(10000));


    // Establish a connection to the server.
    Socket s = new Socket("127.0.0.1", (int) TestCaseUtils.getServerLdapPort());

    InvocationCounterPlugin.resetAllCounters();

    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);
    r.setIOTimeout(6000);

    ModifyDNRequestProtocolOp modifyRequest =
        new ModifyDNRequestProtocolOp(
            new ASN1OctetString(entry.getDN().toString()),
            new ASN1OctetString("uid=user.test0"), false);
    LDAPMessage message = new LDAPMessage(2, modifyRequest,
                                          ShortCircuitPlugin.createShortCircuitLDAPControlList(80, "PreOperation"));
    w.writeElement(message.encode());

    message = LDAPMessage.decode(r.readElement().decodeAsSequence());
    ModifyDNResponseProtocolOp modifyResponse =
        message.getModifyDNResponseProtocolOp();

    assertEquals(modifyResponse.getResultCode(), 80);
    assertEquals(InvocationCounterPlugin.waitForPostResponse(), 1);

    try
    {
      s.close();
    } catch (Exception e) {}
  }

  @Test(groups = "slow")
  public void testWriteLockModify() throws Exception
  {
    // We need the operation to be run in a separate thread because we are going
    // to write lock the entry in the test case thread and check that the
    // modify DN operation does not proceed.

    // Establish a connection to the server.
    Socket s = new Socket("127.0.0.1", (int) TestCaseUtils.getServerLdapPort());
    try
    {
      ASN1Reader r = new ASN1Reader(s);
      ASN1Writer w = new ASN1Writer(s);
      r.setIOTimeout(15000);

      BindRequestProtocolOp bindRequest =
           new BindRequestProtocolOp(
                new ASN1OctetString("cn=Directory Manager"),
                3, new ASN1OctetString("password"));
      LDAPMessage message = new LDAPMessage(1, bindRequest);
      w.writeElement(message.encode());

      message = LDAPMessage.decode(r.readElement().decodeAsSequence());
      BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
      assertEquals(bindResponse.getResultCode(), LDAPResultCode.SUCCESS);

      // Since we are going to be watching the post-response count, we need to
      // wait for the server to become idle before kicking off the next request
      // to ensure that any remaining post-response processing from the previous
      // operation has completed.
      assertTrue(DirectoryServer.getWorkQueue().waitUntilIdle(10000));


      Lock writeLock = LockManager.lockWrite(entry.getDN());
      assertNotNull(writeLock);

      try
      {
        InvocationCounterPlugin.resetAllCounters();

        long modifyDNRequests  = ldapStatistics.getModifyDNRequests();
        long modifyDNResponses = ldapStatistics.getModifyDNResponses();

        ModifyDNRequestProtocolOp modifyRequest =
          new ModifyDNRequestProtocolOp(
               new ASN1OctetString(entry.getDN().toString()),
               new ASN1OctetString("uid=user.test0"), false);
        message = new LDAPMessage(2, modifyRequest);
        w.writeElement(message.encode());

        message = LDAPMessage.decode(r.readElement().decodeAsSequence());
        ModifyDNResponseProtocolOp modifyResponse =
             message.getModifyDNResponseProtocolOp();

        assertEquals(modifyResponse.getResultCode(),
                     DirectoryServer.getServerErrorResultCode().getIntValue());

        assertEquals(InvocationCounterPlugin.getPreParseCount(), 1);
        assertEquals(InvocationCounterPlugin.getPreOperationCount(), 0);
        assertEquals(InvocationCounterPlugin.getPostOperationCount(), 0);
        // The post response might not have been called yet.
        assertEquals(InvocationCounterPlugin.waitForPostResponse(), 1);

        assertEquals(ldapStatistics.getModifyDNRequests(), modifyDNRequests+1);
        assertEquals(ldapStatistics.getModifyDNResponses(),
                     modifyDNResponses+1);
      } finally
      {
        LockManager.unlock(entry.getDN(), writeLock);
      }
    } finally
    {
      s.close();
    }

  }



  /**
   * Tests performing a modify DN operation in which the new RDN contains an
   * attribute type marked OBSOLETE in the server schema.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testModifyDNWithObsoleteAttribute()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: attributeTypes",
         "attributeTypes: ( testmodifydnwithobsoleteattribute-oid " +
              "NAME 'testModifyDNWithObsoleteAttribute' OBSOLETE " +
              "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE " +
              "X-ORGIN 'SchemaBackendTestCase' )");

    String attrName = "testmodifydnwithobsoleteattribute";
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
         "dn: cn=oldrdn,o=test",
         "changetype: add",
         "objectClass: top",
         "objectClass: device",
         "objectClass: extensibleObject",
         "cn: oldrdn",
         "",
         "dn: cn=oldrdn,o=test",
         "changetype: moddn",
         "newRDN: testModifyDNWithObsoleteAttribute=foo",
         "deleteOldRDN: 0"
    );

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

