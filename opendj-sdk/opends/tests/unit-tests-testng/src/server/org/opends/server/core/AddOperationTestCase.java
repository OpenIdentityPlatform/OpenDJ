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

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.api.Backend;
import org.opends.server.plugins.UpdatePreOpPlugin;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Reader;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.ldap.AddRequestProtocolOp;
import org.opends.server.protocols.ldap.AddResponseProtocolOp;
import org.opends.server.protocols.ldap.BindRequestProtocolOp;
import org.opends.server.protocols.ldap.BindResponseProtocolOp;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ByteString;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.ResultCode;
import org.opends.server.types.WritabilityMode;

import static org.testng.Assert.*;

import static org.opends.server.util.ServerConstants.*;



/**
 * A set of test cases for add operations
 */
public class AddOperationTestCase
       extends OperationTestCase
{
  /**
   * Retrieves a set of add operations that may be used for testing.
   *
   * @return  A set of add operations that may be used for testing.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "addOperations")
  public Object[][] getAddOperations()
         throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ArrayList<Control> noControls = new ArrayList<Control>();

    ArrayList<LDAPAttribute> ldapAttrList = new ArrayList<LDAPAttribute>();

    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("top"));
    values.add(new ASN1OctetString("organizationalUnit"));
    ldapAttrList.add(new LDAPAttribute("objectclass", values));

    values.clear();
    values.add(new ASN1OctetString("People"));
    ldapAttrList.add(new LDAPAttribute("ou", values));

    Entry entry = TestCaseUtils.makeEntry(
         "dn: ou=People,o=test",
         "objectClass: top",
         "objectClass: organizationalUnit",
         "ou: People");

    Operation[] opArray = new Operation[]
    {
      new AddOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                       null, new ASN1OctetString("ou=People,o=test"),
                       ldapAttrList),
      new AddOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                       noControls, new ASN1OctetString("ou=People,o=test"),
                       ldapAttrList),
      new AddOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                       null, entry.getDN(), entry.getObjectClasses(),
                       entry.getUserAttributes(),
                       entry.getOperationalAttributes()),
      new AddOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                       noControls, entry.getDN(), entry.getObjectClasses(),
                       entry.getUserAttributes(),
                       entry.getOperationalAttributes()),
    };

    Object[][] objArray = new Object[opArray.length][1];
    for (int i=0; i < opArray.length; i++)
    {
      objArray[i][0] = opArray[i];
    }

    return objArray;
  }




  /**
   * {@inheritDoc}
   */
  @Override()
  public Operation[] createTestOperations()
         throws Exception
  {
    Object[][]  objs = getAddOperations();
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
   * @param  addOperation  The add operation to be tested.
   */
  @Test(dataProvider = "addOperations")
  public void testGetAndSetRawEntryDN(AddOperation addOperation)
  {
    ByteString originalDN = addOperation.getRawEntryDN();
    assertNotNull(originalDN);

    addOperation.setRawEntryDN(new ASN1OctetString("uid=test,o=test"));
    assertNotNull(addOperation.getRawEntryDN());
    assertEquals(addOperation.getRawEntryDN(),
                 new ASN1OctetString("uid=test,o=test"));

    addOperation.setRawEntryDN(originalDN);
    assertNotNull(addOperation.getRawEntryDN());
    assertEquals(addOperation.getRawEntryDN(), originalDN);
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

    ArrayList<LDAPAttribute> ldapAttrList = new ArrayList<LDAPAttribute>();

    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("top"));
    values.add(new ASN1OctetString("organizationalUnit"));
    ldapAttrList.add(new LDAPAttribute("objectclass", values));

    values.clear();
    values.add(new ASN1OctetString("People"));
    ldapAttrList.add(new LDAPAttribute("ou", values));

    AddOperation addOperation =
         new AddOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                          null, new ASN1OctetString("ou=People,o=test"),
                          ldapAttrList);
    assertNull(addOperation.getEntryDN());
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

    Entry entry = TestCaseUtils.makeEntry(
         "dn: ou=People,o=test",
         "objectClass: top",
         "objectClass: organizationalUnit",
         "ou: People");

    AddOperation addOperation =
         new AddOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                          null, entry.getDN(), entry.getObjectClasses(),
                          entry.getUserAttributes(),
                          entry.getOperationalAttributes());
    assertNotNull(addOperation.getEntryDN());
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

    Entry entry = TestCaseUtils.makeEntry(
         "dn: ou=People,o=test",
         "objectClass: top",
         "objectClass: organizationalUnit",
         "ou: People");

    AddOperation addOperation =
         new AddOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                          null, entry.getDN(), entry.getObjectClasses(),
                          entry.getUserAttributes(),
                          entry.getOperationalAttributes());
    assertNotNull(addOperation.getEntryDN());

    addOperation.setRawEntryDN(new ASN1OctetString("ou=Users,o=test"));
    assertNull(addOperation.getEntryDN());
  }



  /**
   * Tests the <CODE>getRawAttributes</CODE>, <CODE>addRawAttribute</CODE>, and
   * <CODE>setRawAttributes</CODE> methods.
   *
   * @param  addOperation  The add operation to be tested.
   */
  @Test(dataProvider = "addOperations")
  public void testGetAndSetRawAttributes(AddOperation addOperation)
  {
    List<LDAPAttribute> rawAttrs = addOperation.getRawAttributes();
    assertNotNull(rawAttrs);
    assertFalse(rawAttrs.isEmpty());

    ArrayList<LDAPAttribute> copiedAttrs =
      new ArrayList<LDAPAttribute>(rawAttrs);
    addOperation.setRawAttributes(copiedAttrs);

    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("foo"));
    addOperation.addRawAttribute(new LDAPAttribute("description", values));

    boolean found = false;
    for (LDAPAttribute a : addOperation.getRawAttributes())
    {
      if (a.getAttributeType().equalsIgnoreCase("description"))
      {
        found = true;
        break;
      }
    }
    assertTrue(found);

    addOperation.setRawAttributes(rawAttrs);

    found = false;
    for (LDAPAttribute a : addOperation.getRawAttributes())
    {
      if (a.getAttributeType().equalsIgnoreCase("description"))
      {
        found = true;
        break;
      }
    }
    assertFalse(found);
  }



  /**
   * Tests the <CODE>addObjectClass</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddObjectClass()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: ou=People,o=test",
         "objectClass: top",
         "objectClass: organizationalUnit",
         "ou: People");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    UpdatePreOpPlugin.reset();

    ObjectClass oc = DirectoryServer.getObjectClass("extensibleobject", true);
    UpdatePreOpPlugin.addObjectClassToAdd(oc);

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveCompletedOperationElements(addOperation);

    Entry e = DirectoryServer.getEntry(DN.decode("ou=People,o=test"));
    assertTrue(e.hasObjectClass(oc));

    UpdatePreOpPlugin.reset();
  }



  /**
   * Tests the <CODE>removeObjectClass</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRemoveObjectClass()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: ou=People,o=test",
         "objectClass: top",
         "objectClass: organizationalUnit",
         "objectClass: extensibleObject",
         "ou: People");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    UpdatePreOpPlugin.reset();

    ObjectClass oc = DirectoryServer.getObjectClass("extensibleobject", true);
    UpdatePreOpPlugin.addObjectClassToRemove(oc);

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveCompletedOperationElements(addOperation);

    Entry e = DirectoryServer.getEntry(DN.decode("ou=People,o=test"));
    assertFalse(e.hasObjectClass(oc));

    UpdatePreOpPlugin.reset();
  }



  /**
   * Tests the <CODE>setAttribute</CODE> method for an attribute that already
   * exists.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSetAttributeOverwrite()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: ou=People,o=test",
         "objectClass: top",
         "objectClass: organizationalUnit",
         "ou: People",
         "description: foo");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    UpdatePreOpPlugin.reset();

    Attribute a = new Attribute("description", "bar");
    UpdatePreOpPlugin.addAttributeToSet(a);

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveCompletedOperationElements(addOperation);

    Entry e = DirectoryServer.getEntry(DN.decode("ou=People,o=test"));
    List<Attribute> attrList = e.getAttribute(a.getAttributeType());
    assertNotNull(attrList);
    assertFalse(attrList.isEmpty());

    boolean foundFoo = false;
    boolean foundBar = false;
    for (Attribute attr : attrList)
    {
      if (attr.hasValue(new AttributeValue(a.getAttributeType(),
                                           new ASN1OctetString("foo"))))
      {
        foundFoo = true;
      }

      if (attr.hasValue(new AttributeValue(a.getAttributeType(),
                                                new ASN1OctetString("bar"))))
      {
        foundBar = true;
      }
    }

    assertFalse(foundFoo);
    assertTrue(foundBar);

    UpdatePreOpPlugin.reset();
  }



  /**
   * Tests the <CODE>setAttribute</CODE> method for an attribute that doesn't
   * exist.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSetAttributeAdd()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: ou=People,o=test",
         "objectClass: top",
         "objectClass: organizationalUnit",
         "ou: People");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    UpdatePreOpPlugin.reset();

    Attribute a = new Attribute("description", "foo");
    UpdatePreOpPlugin.addAttributeToSet(a);

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveCompletedOperationElements(addOperation);

    Entry e = DirectoryServer.getEntry(DN.decode("ou=People,o=test"));
    List<Attribute> attrList = e.getAttribute(a.getAttributeType());
    assertNotNull(attrList);
    assertFalse(attrList.isEmpty());

    UpdatePreOpPlugin.reset();
  }



  /**
   * Tests the <CODE>removeAttribute</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSetAttributeRemove()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: ou=People,o=test",
         "objectClass: top",
         "objectClass: organizationalUnit",
         "ou: People",
         "description: foo");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    UpdatePreOpPlugin.reset();

    AttributeType attrType = DirectoryServer.getAttributeType("description",
                                                              true);
    UpdatePreOpPlugin.addAttributeToRemove(attrType);

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveCompletedOperationElements(addOperation);

    Entry e = DirectoryServer.getEntry(DN.decode("ou=People,o=test"));
    List<Attribute> attrList = e.getAttribute(attrType);
    assertNull(attrList);

    UpdatePreOpPlugin.reset();
  }



  /**
   * Invokes methods to retrieve members of an add operation after it has
   * completed.
   *
   * @param  addOperation  The add operation to examine.  It should have
   *                       completed successfully.
   */
  private void retrieveCompletedOperationElements(AddOperation addOperation)
  {
    assertNotNull(addOperation.getEntryToAdd());
    assertTrue(addOperation.getProcessingStartTime() > 0);
    assertTrue(addOperation.getProcessingStopTime() >=
               addOperation.getProcessingStartTime());
    assertTrue(addOperation.getProcessingTime() >= 0);
    assertNotNull(addOperation.getResponseLogElements());

    long changeNumber = addOperation.getChangeNumber();
    addOperation.setChangeNumber(changeNumber);
  }



  /**
   * Tests an internal add operation that should be successful using raw
   * arguments.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testIntenalAddSuccessRaw()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    ArrayList<LDAPAttribute> attrs = new ArrayList<LDAPAttribute>();

    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("top"));
    values.add(new ASN1OctetString("organizationalUnit"));
    attrs.add(new LDAPAttribute("objectClass", values));

    values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("People"));
    attrs.add(new LDAPAttribute("ou", values));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(new ASN1OctetString("ou=People,o=test"), attrs);
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveCompletedOperationElements(addOperation);
  }



  /**
   * Tests an internal add operation that should be successful using processed
   * arguments.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testIntenalAddSuccessProcessed()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: ou=People,o=test",
         "objectClass: top",
         "objectClass: organizationalUnit",
         "ou: People");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveCompletedOperationElements(addOperation);
  }



  /**
   * Tests an internal add operation that fails because it contains a malformed
   * DN.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testIntenalAddFailureMalformedDN()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    ArrayList<LDAPAttribute> attrs = new ArrayList<LDAPAttribute>();

    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("top"));
    values.add(new ASN1OctetString("organizationalUnit"));
    attrs.add(new LDAPAttribute("objectClass", values));

    values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("People"));
    attrs.add(new LDAPAttribute("ou", values));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(new ASN1OctetString("invalid"), attrs);
    assertFalse(addOperation.getResultCode() == ResultCode.SUCCESS);
  }



  /**
   * Tests an internal add operation that fails because it contains the DN of
   * an entry that already exists.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testIntenalAddFailureAlreadyExists()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    ArrayList<LDAPAttribute> attrs = new ArrayList<LDAPAttribute>();

    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("top"));
    values.add(new ASN1OctetString("organization"));
    attrs.add(new LDAPAttribute("objectClass", values));

    values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("test"));
    attrs.add(new LDAPAttribute("o", values));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(new ASN1OctetString("o=test"), attrs);
    assertFalse(addOperation.getResultCode() == ResultCode.SUCCESS);
  }



  /**
   * Tests an internal add operation that fails because it is a suffix that
   * doesn't exist.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testIntenalAddFailureNoSuchSuffix()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    ArrayList<LDAPAttribute> attrs = new ArrayList<LDAPAttribute>();

    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("top"));
    values.add(new ASN1OctetString("organization"));
    attrs.add(new LDAPAttribute("objectClass", values));

    values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("undefined"));
    attrs.add(new LDAPAttribute("o", values));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(new ASN1OctetString("o=undefined"), attrs);
    assertFalse(addOperation.getResultCode() == ResultCode.SUCCESS);
  }



  /**
   * Tests an internal add operation that fails because it is below a suffix
   * that doesn't exist.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testIntenalAddFailureNoSuchSuffixParent()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    ArrayList<LDAPAttribute> attrs = new ArrayList<LDAPAttribute>();

    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("top"));
    values.add(new ASN1OctetString("organizationalUnit"));
    attrs.add(new LDAPAttribute("objectClass", values));

    values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("People"));
    attrs.add(new LDAPAttribute("ou", values));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(new ASN1OctetString("ou=People,o=undefined"), attrs);
    assertFalse(addOperation.getResultCode() == ResultCode.SUCCESS);
  }



  /**
   * Tests an internal add operation that fails because its parent doesn't
   * exist.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testIntenalAddFailureNoSuchParent()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    ArrayList<LDAPAttribute> attrs = new ArrayList<LDAPAttribute>();

    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("top"));
    values.add(new ASN1OctetString("organizationalUnit"));
    attrs.add(new LDAPAttribute("objectClass", values));

    values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("People"));
    attrs.add(new LDAPAttribute("ou", values));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(new ASN1OctetString("ou=People,o=missing,o=test"),
                         attrs);
    assertFalse(addOperation.getResultCode() == ResultCode.SUCCESS);
  }



  /**
   * Tests an external add operation that fails because it contains an attribute
   * that is marked no-user-modification.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testExternalAddFailureNoUserModification()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

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


    ArrayList<LDAPAttribute> attrs = new ArrayList<LDAPAttribute>();
    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("top"));
    values.add(new ASN1OctetString("organizationalUnit"));
    attrs.add(new LDAPAttribute("objectClass", values));

    values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("People"));
    attrs.add(new LDAPAttribute("ou", values));

    values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("cn=Directory Manager"));
    attrs.add(new LDAPAttribute("creatorsName", values));

    values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("20060101000000Z"));
    attrs.add(new LDAPAttribute("createTimestamp", values));

    AddRequestProtocolOp addRequest =
         new AddRequestProtocolOp(new ASN1OctetString("ou=People,o=test"),
                                  attrs);
    message = new LDAPMessage(2, addRequest);
    w.writeElement(message.encode());

    message = LDAPMessage.decode(r.readElement().decodeAsSequence());
    AddResponseProtocolOp addResponse =
         message.getAddResponseProtocolOp();
    assertFalse(addResponse.getResultCode() == 0);

    try
    {
      s.close();
    } catch (Exception e) {}
  }



  /**
   * Tests an internal add operation that fails because it has an undefined
   * objectclass.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testIntenalAddFailureUndefinedObjectClass()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    ArrayList<LDAPAttribute> attrs = new ArrayList<LDAPAttribute>();

    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("top"));
    values.add(new ASN1OctetString("undefined"));
    attrs.add(new LDAPAttribute("objectClass", values));

    values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("People"));
    attrs.add(new LDAPAttribute("ou", values));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(new ASN1OctetString("ou=People,o=test"),
                         attrs);
    assertFalse(addOperation.getResultCode() == ResultCode.SUCCESS);
  }



  /**
   * Tests a successful internal add operation that contains a user-modifiable
   * operational attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testIntenalAddSuccessfulWithOperationalAttribute()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "userPassword: password",
         "pwdPolicySubentry: cn=Clear UserPassword Policy," +
              "cn=Password Policies,cn=config");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveCompletedOperationElements(addOperation);
  }



  /**
   * Tests a successful internal add operation that contains an attribute with
   * multiple values where the values are spread throughout the entry.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testIntenalAddSuccessfulDisjointAttribute()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    ArrayList<LDAPAttribute> attrs = new ArrayList<LDAPAttribute>();

    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("top"));
    values.add(new ASN1OctetString("organizationalUnit"));
    attrs.add(new LDAPAttribute("objectClass", values));

    values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("foo"));
    attrs.add(new LDAPAttribute("description", values));

    values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("People"));
    attrs.add(new LDAPAttribute("ou", values));

    values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("bar"));
    attrs.add(new LDAPAttribute("description", values));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(new ASN1OctetString("ou=People,o=test"),
                         attrs);
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests a successful internal add operation that contains raw attributes with
   * options.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testIntenalAddSuccessfulWithRawAttributeOptions()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    ArrayList<LDAPAttribute> attrs = new ArrayList<LDAPAttribute>();

    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("top"));
    values.add(new ASN1OctetString("organizationalUnit"));
    attrs.add(new LDAPAttribute("objectClass", values));

    values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("foo"));
    attrs.add(new LDAPAttribute("description", values));

    values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("People"));
    attrs.add(new LDAPAttribute("ou", values));

    values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("foo"));
    attrs.add(new LDAPAttribute("description;lang-en-us", values));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(new ASN1OctetString("ou=People,o=test"),
                         attrs);
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests a successful internal add operation that contains raw attributes with
   * options and an attribute that doesn't have any values without options.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testIntenalAddSuccessfulWithRawAttributeOptionsOnlyOptions()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    ArrayList<LDAPAttribute> attrs = new ArrayList<LDAPAttribute>();

    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("top"));
    values.add(new ASN1OctetString("organizationalUnit"));
    attrs.add(new LDAPAttribute("objectClass", values));

    values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("People"));
    attrs.add(new LDAPAttribute("ou", values));

    values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("foo"));
    attrs.add(new LDAPAttribute("description;lang-en-us", values));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(new ASN1OctetString("ou=People,o=test"),
                         attrs);
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests a successful internal add operation that contains attributes with
   * options.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testIntenalAddSuccessfulWithAttributeOptions()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "uid;lang-en-us: test.user",
         "givenName: Test",
         "givenName;lang-en-us: Test",
         "sn: User",
         "sn;lang-en-us: User",
         "cn: Test User",
         "cn;lang-en-us: Test User",
         "userPassword: password");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveCompletedOperationElements(addOperation);
  }



  /**
   * Tests an internal add operation that fails because it attempts to add the
   * root DSE.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testIntenalAddFailureRootDSE()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    ArrayList<LDAPAttribute> attrs = new ArrayList<LDAPAttribute>();

    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("top"));
    values.add(new ASN1OctetString("ds-root-dse"));
    values.add(new ASN1OctetString("extensibleObject"));
    attrs.add(new LDAPAttribute("objectClass", values));

    values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("Root DSE"));
    attrs.add(new LDAPAttribute("cn", values));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(new ASN1OctetString(), attrs);
    assertFalse(addOperation.getResultCode() == ResultCode.SUCCESS);
  }



  /**
   * Tests a successful internal add operation that is missing RDN attributes.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testIntenalAddSuccessfulWithMissingRDNAttributes()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: ou=People,o=test",
         "objectClass: top",
         "objectClass: organizationalUnit");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveCompletedOperationElements(addOperation);

    Entry e = DirectoryServer.getEntry(DN.decode("ou=People,o=test"));
    List<Attribute> attrList = e.getAttribute("ou");
    assertNotNull(attrList);
  }



  /**
   * Tests a failed internal add operation that is missing RDN attributes.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testIntenalAddFailureWithMissingRDNAttributes()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    DirectoryServer.setAddMissingRDNAttributes(false);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: ou=People,o=test",
         "objectClass: top",
         "objectClass: organizationalUnit");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertFalse(addOperation.getResultCode() == ResultCode.SUCCESS);

    DirectoryServer.setAddMissingRDNAttributes(true);
  }



  /**
   * Tests a successful internal add operation that is missing an objectclass
   * in the hierarchical chain.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testIntenalAddSuccessfulWithMissingParentObjectClass()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
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
    retrieveCompletedOperationElements(addOperation);

    Entry e = DirectoryServer.getEntry(DN.decode("uid=test.user,o=test"));
    List<Attribute> attrList =
         e.getAttribute(DirectoryServer.getObjectClassAttributeType());
    assertNotNull(attrList);

    boolean found = false;
    for (Attribute a : attrList)
    {
      for (AttributeValue v : a.getValues())
      {
        if (v.getStringValue().equalsIgnoreCase("top"))
        {
          found = true;
          break;
        }
      }
    }
    assertTrue(found);
  }



  /**
   * Tests a failed internal add operation that doesn't have any objectclasses.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testIntenalAddFailureNoObjectClasses()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: ou=People,o=test",
         "ou: People");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertFalse(addOperation.getResultCode() == ResultCode.SUCCESS);
  }



  /**
   * Tests a failed internal add operation that only has an abstract
   * objectclass.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testIntenalAddFailureOnlyAbstractObjectClass()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: ou=People,o=test",
         "objectClass: top",
         "ou: People");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertFalse(addOperation.getResultCode() == ResultCode.SUCCESS);
  }



  /**
   * Tests a failed internal add operation that doesn't have any structural
   * objectclass (only abstract and auxiliary).
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testIntenalAddFailureNoStructuralObjectClass()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: ou=People,o=test",
         "objectClass: top",
         "objectClass: extensibleObject",
         "ou: People");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertFalse(addOperation.getResultCode() == ResultCode.SUCCESS);
  }



  /**
   * Tests a failed internal add operation that has multiple structural
   * objectclasses.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testIntenalAddFailureMultipleStructuralObjectClasses()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: ou=People,o=test",
         "objectClass: top",
         "objectClass: organizationalUnit",
         "objectClass: person",
         "ou: People",
         "cn: Test User",
         "sn: User");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertFalse(addOperation.getResultCode() == ResultCode.SUCCESS);
  }



  /**
   * Tests a failed internal add operation that is missing a required attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testIntenalAddFailureMissingRequiredAttribute()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "userPassword: password"); // Missing cn

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertFalse(addOperation.getResultCode() == ResultCode.SUCCESS);
  }



  /**
   * Tests a failed internal add operation that is missing a required attribute
   * but has the extensibleObject objectClass (which shouldn't change anything).
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testIntenalAddFailureMissingRequiredAttributeExtensibleObject()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "objectClass: extensibleObject",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "userPassword: password"); // Missing cn

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertFalse(addOperation.getResultCode() == ResultCode.SUCCESS);
  }



  /**
   * Tests a failed internal add operation that contains an attribute not
   * allowed by any objectclass.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testIntenalAddFailureDisallowedAttribute()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "userPassword: password",
         "dc: Not allowed by inetOrgPerson");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertFalse(addOperation.getResultCode() == ResultCode.SUCCESS);
  }



  /**
   * Tests a successful internal add operation that contains an attribute not
   * allowed by any standard objectclass in the entry but is allowed by
   * extensibleObject.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testIntenalAddSuccessfulDisallowedAttributeExtensibleObject()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "objectClass: extensibleObject",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "userPassword: password",
         "dc: Not allowed by inetOrgPerson but allowed by extensibleObject");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveCompletedOperationElements(addOperation);
  }



  /**
   * Tests a failed internal add operation with the server in complete read-only
   * mode.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testIntenalAddFailureServerCompletelyReadOnly()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
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

    DirectoryServer.setWritabilityMode(WritabilityMode.DISABLED);

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertFalse(addOperation.getResultCode() == ResultCode.SUCCESS);

    DirectoryServer.setWritabilityMode(WritabilityMode.ENABLED);
  }



  /**
   * Tests a successful internal add operation with the server in read-only mode
   * for external operations but allowed for internal operations.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testIntenalAddSuccessServerExternallyReadOnly()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
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

    DirectoryServer.setWritabilityMode(WritabilityMode.INTERNAL_ONLY);

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveCompletedOperationElements(addOperation);

    DirectoryServer.setWritabilityMode(WritabilityMode.ENABLED);
  }



  /**
   * Tests a failed external add operation with the server in read-only mode
   * for external operations but allowed for internal operations.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testExternalAddFailureServerExternallyReadOnly()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

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


    ArrayList<LDAPAttribute> attrs = new ArrayList<LDAPAttribute>();
    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("top"));
    values.add(new ASN1OctetString("organizationalUnit"));
    attrs.add(new LDAPAttribute("objectClass", values));

    values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("People"));
    attrs.add(new LDAPAttribute("ou", values));

    DirectoryServer.setWritabilityMode(WritabilityMode.INTERNAL_ONLY);

    AddRequestProtocolOp addRequest =
         new AddRequestProtocolOp(new ASN1OctetString("ou=People,o=test"),
                                  attrs);
    message = new LDAPMessage(2, addRequest);
    w.writeElement(message.encode());

    message = LDAPMessage.decode(r.readElement().decodeAsSequence());
    AddResponseProtocolOp addResponse =
         message.getAddResponseProtocolOp();
    assertFalse(addResponse.getResultCode() == 0);

    try
    {
      s.close();
    } catch (Exception e) {}

    DirectoryServer.setWritabilityMode(WritabilityMode.ENABLED);
  }



  /**
   * Tests a failed internal add operation with the backend in complete
   * read-only mode.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testIntenalAddFailureBackendCompletelyReadOnly()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
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

    Backend b = DirectoryServer.getBackend(DN.decode("o=test"));
    b.setWritabilityMode(WritabilityMode.DISABLED);

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertFalse(addOperation.getResultCode() == ResultCode.SUCCESS);

    b.setWritabilityMode(WritabilityMode.ENABLED);
  }



  /**
   * Tests a successful internal add operation with the backend in read-only
   * mode for external operations but allowed for internal operations.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testIntenalAddSuccessBackendExternallyReadOnly()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
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

    Backend b = DirectoryServer.getBackend(DN.decode("o=test"));
    b.setWritabilityMode(WritabilityMode.INTERNAL_ONLY);

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveCompletedOperationElements(addOperation);

    b.setWritabilityMode(WritabilityMode.ENABLED);
  }



  /**
   * Tests a failed external add operation with the backend in read-only mode
   * for external operations but allowed for internal operations.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testExternalAddFailureBackendExternallyReadOnly()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

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


    ArrayList<LDAPAttribute> attrs = new ArrayList<LDAPAttribute>();
    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("top"));
    values.add(new ASN1OctetString("organizationalUnit"));
    attrs.add(new LDAPAttribute("objectClass", values));

    values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("People"));
    attrs.add(new LDAPAttribute("ou", values));

    Backend b = DirectoryServer.getBackend(DN.decode("o=test"));
    b.setWritabilityMode(WritabilityMode.INTERNAL_ONLY);

    AddRequestProtocolOp addRequest =
         new AddRequestProtocolOp(new ASN1OctetString("ou=People,o=test"),
                                  attrs);
    message = new LDAPMessage(2, addRequest);
    w.writeElement(message.encode());

    message = LDAPMessage.decode(r.readElement().decodeAsSequence());
    AddResponseProtocolOp addResponse =
         message.getAddResponseProtocolOp();
    assertFalse(addResponse.getResultCode() == 0);

    try
    {
      s.close();
    } catch (Exception e) {}

    b.setWritabilityMode(WritabilityMode.ENABLED);
  }



  /**
   * Tests to ensure that any registered add notification listeners are invoked
   * for a successful add operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSuccessWithNotificationListener()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    TestChangeNotificationListener changeListener =
         new TestChangeNotificationListener();
    DirectoryServer.registerChangeNotificationListener(changeListener);
    assertEquals(changeListener.getAddCount(), 0);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: ou=People,o=test",
         "objectClass: top",
         "objectClass: organizationalUnit",
         "ou: People");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveCompletedOperationElements(addOperation);

    assertEquals(changeListener.getAddCount(), 1);
    DirectoryServer.deregisterChangeNotificationListener(changeListener);
  }



  /**
   * Tests to ensure that any registered add notification listeners are not
   * invoked for a failed add operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testFailureWithNotificationListener()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    TestChangeNotificationListener changeListener =
         new TestChangeNotificationListener();
    DirectoryServer.registerChangeNotificationListener(changeListener);
    assertEquals(changeListener.getAddCount(), 0);

    Entry entry = TestCaseUtils.makeEntry(
         "dn: ou=People,ou=nonexistent,o=test",
         "objectClass: top",
         "objectClass: organizationalUnit",
         "ou: People");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    assertFalse(addOperation.getResultCode() == ResultCode.SUCCESS);

    assertEquals(changeListener.getAddCount(), 0);
    DirectoryServer.deregisterChangeNotificationListener(changeListener);
  }
}

