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
package org.opends.server.backends;



import java.util.LinkedHashSet;
import java.util.List;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.tools.LDAPModify;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ByteStringFactory;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.RDN;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchScope;

import static org.testng.Assert.*;

import static org.opends.server.util.StaticUtils.*;



/**
 * A set of test cases for the schema backend.
 */
public class SchemaBackendTestCase
       extends BackendTestCase
{
  // A reference to the schema backend.
  private SchemaBackend schemaBackend;



  /**
   * Ensures that the Directory Server is running and gets a reference to the
   * schema backend.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass()
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();

    schemaBackend = (SchemaBackend) DirectoryServer.getBackend("schema");
    assertNotNull(schemaBackend);
  }



  /**
   * Tests the {@code isLocal} method to ensure that it is considered local.
   */
  @Test()
  public void testIsLocal()
  {
    assertTrue(schemaBackend.isLocal());
  }



  /**
   * Tests the {@code getEntry} method to ensure that it is able to retrieve
   * the schema entry if it is given a valid entry DN.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetValidEntry()
         throws Exception
  {
    DN    schemaDN    = DN.decode("cn=schema");
    Entry schemaEntry = schemaBackend.getEntry(schemaDN);
    assertNotNull(schemaEntry);
    assertEquals(schemaEntry.getDN(), schemaDN);

    AttributeType t = DirectoryServer.getAttributeType("attributetypes");
    assertTrue(schemaEntry.hasAttribute(t));

    t = DirectoryServer.getAttributeType("objectclasses");
    assertTrue(schemaEntry.hasAttribute(t));

    t = DirectoryServer.getAttributeType("ldapsyntaxes");
    assertTrue(schemaEntry.hasAttribute(t));

    t = DirectoryServer.getAttributeType("matchingrules");
    assertTrue(schemaEntry.hasAttribute(t));
  }



  /**
   * Tests the {@code getEntry} method to ensure that it is not able to retrieve
   * anything when given an inappropriate DN.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetInvalidEntry()
         throws Exception
  {
    DN    schemaDN    = DN.decode("cn=notschema");
    Entry schemaEntry = schemaBackend.getEntry(schemaDN);
    assertNull(schemaEntry);

    schemaDN    = DN.decode("cn=child,cn=schema");
    schemaEntry = schemaBackend.getEntry(schemaDN);
    assertNull(schemaEntry);
  }



  /**
   * Tests the {@code getSchemaEntry} method to ensure that it is able to
   * retrieve the appropriate information with different DNs.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetSchemaEntry()
         throws Exception
  {
    DN    schemaDN    = DN.decode("cn=schema");
    Entry schemaEntry = schemaBackend.getSchemaEntry(schemaDN);
    assertNotNull(schemaEntry);
    assertEquals(schemaEntry.getDN(), schemaDN);

    AttributeType t = DirectoryServer.getAttributeType("attributetypes");
    assertTrue(schemaEntry.hasAttribute(t));

    t = DirectoryServer.getAttributeType("objectclasses");
    assertTrue(schemaEntry.hasAttribute(t));

    t = DirectoryServer.getAttributeType("ldapsyntaxes");
    assertTrue(schemaEntry.hasAttribute(t));

    t = DirectoryServer.getAttributeType("matchingrules");
    assertTrue(schemaEntry.hasAttribute(t));


    schemaDN    = DN.decode("cn=subschema");
    schemaEntry = schemaBackend.getSchemaEntry(schemaDN);
    assertNotNull(schemaEntry);
    assertEquals(schemaEntry.getDN(), schemaDN);

    t = DirectoryServer.getAttributeType("attributetypes");
    assertTrue(schemaEntry.hasAttribute(t));

    t = DirectoryServer.getAttributeType("objectclasses");
    assertTrue(schemaEntry.hasAttribute(t));

    t = DirectoryServer.getAttributeType("ldapsyntaxes");
    assertTrue(schemaEntry.hasAttribute(t));

    t = DirectoryServer.getAttributeType("matchingrules");
    assertTrue(schemaEntry.hasAttribute(t));
  }



  /**
   * Tests the {@code entryExists} method with a valid schema DN.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testEntryExistsValidDN()
         throws Exception
  {
    DN schemaDN = DN.decode("cn=schema");
    assertTrue(schemaBackend.entryExists(schemaDN));
  }



  /**
   * Tests the {@code entryExists} method with an invalid schema DN.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testEntryExistsInvalidDN()
         throws Exception
  {
    DN schemaDN = DN.decode("cn=notschema");
    assertFalse(schemaBackend.entryExists(schemaDN));
  }



  /**
   * Tests to ensure that the {@code addEntry} method always throws an
   * exception.
   */
  @Test(expectedExceptions = { DirectoryException.class })
  public void testAddEntry()
         throws Exception
  {
    Entry entry = createEntry(DN.decode("cn=schema"));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         new AddOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                          null, entry.getDN(), entry.getObjectClasses(),
                          entry.getUserAttributes(),
                          entry.getOperationalAttributes());

    schemaBackend.addEntry(entry, addOperation);
  }



  /**
   * Tests to ensure that the {@code deleteEntry} method always throws an
   * exception.
   */
  @Test(expectedExceptions = { DirectoryException.class })
  public void testDeleteEntry()
         throws Exception
  {
    DN schemaDN = DN.decode("cn=schema");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    DeleteOperation deleteOperation =
         new DeleteOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                             null, schemaDN);

    schemaBackend.deleteEntry(schemaDN, deleteOperation);
  }



  /**
   * Tests to ensure that the {@code renameEntry} method always throws an
   * exception.
   */
  @Test(expectedExceptions = { DirectoryException.class })
  public void testRenameEntry()
         throws Exception
  {
    DN currentSchemaDN = DN.decode("cn=schema");
    DN newSchemaDN     = DN.decode("cn=newschema");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    ModifyDNOperation modifyDNOperation =
         new ModifyDNOperation(conn, conn.nextOperationID(),
                               conn.nextMessageID(), null,
                               currentSchemaDN, newSchemaDN.getRDN(),
                               true, null);

    schemaBackend.renameEntry(currentSchemaDN,
                              schemaBackend.getSchemaEntry(newSchemaDN),
                              modifyDNOperation);
  }



  /**
   * Performs a simple base-level search to verify that the schema entry is
   * returned.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSimpleBaseSearch()
         throws Exception
  {
    String filterString = "(|(objectClass=*)(objectClass=ldapSubentry))";

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         conn.processSearch(DN.decode("cn=schema"), SearchScope.BASE_OBJECT,
              SearchFilter.createFilterFromString(filterString));
    assertNotNull(searchOperation);
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    assertFalse(searchOperation.getSearchEntries().isEmpty());
  }



  /**
   * Performs a simple single-level search to verify that nothing is returned.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSimpleOneLevelSearch()
         throws Exception
  {
    String filterString = "(|(objectClass=*)(objectClass=ldapSubentry))";

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         conn.processSearch(DN.decode("cn=schema"), SearchScope.SINGLE_LEVEL,
              SearchFilter.createFilterFromString(filterString));
    assertNotNull(searchOperation);
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    assertTrue(searchOperation.getSearchEntries().isEmpty());
  }



  /**
   * Performs a simple subtree search to verify that the schema entry is
   * returned.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSimpleSubtreeSearch()
         throws Exception
  {
    String filterString = "(|(objectClass=*)(objectClass=ldapSubentry))";

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         conn.processSearch(DN.decode("cn=schema"), SearchScope.WHOLE_SUBTREE,
              SearchFilter.createFilterFromString(filterString));
    assertNotNull(searchOperation);
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    assertFalse(searchOperation.getSearchEntries().isEmpty());
  }



  /**
   * Performs a simple subordinate subtree search to verify that nothing is
   * returned.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSimpleSubordinateSubtreeSearch()
         throws Exception
  {
    String filterString = "(|(objectClass=*)(objectClass=ldapSubentry))";

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         conn.processSearch(DN.decode("cn=schema"),
              SearchScope.SUBORDINATE_SUBTREE,
              SearchFilter.createFilterFromString(filterString));
    assertNotNull(searchOperation);
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    assertTrue(searchOperation.getSearchEntries().isEmpty());
  }



  /**
   * Tests the behavior of the schema backend with regard to the
   * ds-cfg-show-all-attributes configuration.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test()
  public void testTreatAsUserAttrs()
         throws Exception
  {
    DN schemaDN = DN.decode("cn=schema");
    AttributeType a = DirectoryServer.getAttributeType("attributetypes");
    AttributeType o = DirectoryServer.getAttributeType("objectclasses");
    AttributeType m = DirectoryServer.getAttributeType("matchingrules");
    AttributeType s = DirectoryServer.getAttributeType("ldapsyntaxes");

    assertFalse(schemaBackend.showAllAttributes());
    Entry schemaEntry = schemaBackend.getSchemaEntry(schemaDN);
    assertTrue(schemaEntry.hasOperationalAttribute(a));
    assertTrue(schemaEntry.hasOperationalAttribute(o));
    assertTrue(schemaEntry.hasOperationalAttribute(m));
    assertTrue(schemaEntry.hasOperationalAttribute(s));

    schemaBackend.setShowAllAttributes(true);
    assertTrue(schemaBackend.showAllAttributes());
    schemaEntry = schemaBackend.getSchemaEntry(schemaDN);
    assertFalse(schemaEntry.hasOperationalAttribute(a));
    assertFalse(schemaEntry.hasOperationalAttribute(o));
    assertFalse(schemaEntry.hasOperationalAttribute(m));
    assertTrue(schemaEntry.hasOperationalAttribute(s));

    schemaBackend.setShowAllAttributes(false);
    assertFalse(schemaBackend.showAllAttributes());
    schemaEntry = schemaBackend.getSchemaEntry(schemaDN);
    assertTrue(schemaEntry.hasOperationalAttribute(a));
    assertTrue(schemaEntry.hasOperationalAttribute(o));
    assertTrue(schemaEntry.hasOperationalAttribute(m));
    assertTrue(schemaEntry.hasOperationalAttribute(s));
  }



  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * attribute type with a valid syntax and that isn't already defined.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddAttributeTypeSuccessful()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: attributeTypes",
         "attributeTypes: ( 1.3.6.1.4.1.26027.1.999.4 NAME " +
              "'testAddAttributeTypeSuccessful' SYNTAX " +
              "1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE X-ORIGIN " +
              "'SchemaBackendTestCase' )");

    String attrName = "testaddattributetypesuccessful";
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
  }



  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * attribute type with a valid syntax (but using a textual OID rather than
   * numeric) and that isn't already defined.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddAttributeTypeSuccessfulNoOID()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: attributeTypes",
         "attributeTypes: ( testaddattributetypesuccessfulnooid-oid NAME " +
              "'testAddAttributeTypeSuccessfulNoOID' SYNTAX " +
              "1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE X-ORIGIN " +
              "'SchemaBackendTestCase' )");

    String attrName = "testaddattributetypesuccessfulnooid";
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
  }



  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * attribute type definition that can't be parsed.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddAttributeTypeInvalidSyntax()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: attributeTypes",
         "attributeTypes: invalidsyntax");

    String[] args =
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
   * Tests the behavior of the schema backend when attempting to add a new
   * objectclass that doesn't already exist, that has a valid superior class,
   * and for which all attributes contained in it are already defined.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddObjectClassSuccessful()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses: ( 1.3.6.1.4.1.26027.1.999.5 NAME " +
              "'testAddObjectClassSuccessful' SUP top STRUCTURAL MUST cn " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    String ocName = "testaddobjectclasssuccessful";
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
  }



  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * objectclass that doesn't already exist, that has a textual OID rather than
   * numeric, has a valid superior class, and for which all attributes contained
   * in it are already defined.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddObjectClassSuccessfulNoOID()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses: ( testaddobjectclasssuccessfulnooid-oid NAME " +
              "'testAddObjectClassSuccessfulNoOID' SUP top STRUCTURAL " +
              "MUST cn X-ORIGIN 'SchemaBackendTestCase' )");

    String ocName = "testaddobjectclasssuccessfulnooid";
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
  }



  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * objectclass definition that can't be parsed.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddObjectClassInvalidSyntax()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses: invalidsyntax");

    String[] args =
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
   * Tests the behavior of the schema backend when attempting to add a new
   * objectclass that references an undefined superior class.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddObjectClassUndefinedSuperiorClass()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses: ( testaddocundefinedsuperior-oid NAME " +
              "'testAddOCUndefinedSuperior' SUP undefined STRUCTURAL " +
              "MUST cn X-ORIGIN 'SchemaBackendTestCase' )");

    String[] args =
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
   * Tests the behavior of the schema backend when attempting to add a new
   * objectclass that references an undefined required attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddObjectClassUndefinedRequiredAttribute()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses: ( testaddocundefinedrequired-oid NAME " +
              "'testAddOCUndefinedRequired' SUP top STRUCTURAL " +
              "MUST undefined X-ORIGIN 'SchemaBackendTestCase' )");

    String[] args =
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
   * Tests the behavior of the schema backend when attempting to add a new
   * objectclass that references an undefined optional attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddObjectClassUndefinedOptionalAttribute()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses: ( testaddocundefinedoptional-oid NAME " +
              "'testAddOCUndefinedOptional' SUP top STRUCTURAL " +
              "MAY undefined X-ORIGIN 'SchemaBackendTestCase' )");

    String[] args =
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

