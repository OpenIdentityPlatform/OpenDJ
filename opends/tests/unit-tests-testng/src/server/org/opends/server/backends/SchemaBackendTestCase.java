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
package org.opends.server.backends;



import java.io.File;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.SchemaConfigManager;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.tools.LDAPModify;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ByteStringFactory;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DITContentRule;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ExistingFileBehavior;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.MatchingRuleUse;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.ObjectClass;
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
   * Tests the {@code initializeBackend} method by providing a null
   * configuration entry.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ConfigException.class,
                               InitializationException.class })
  public void testInitializeWithNullEntry()
         throws Exception
  {
    SchemaBackend schemaBackend = new SchemaBackend();
    schemaBackend.initializeBackend(null, new DN[0]);
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
   * attribute type that is not allowed to be altered.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddUnsupportedAttr()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClass",
         "objectClass: extensibleObject");

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
   * Tests the behavior of the schema backend when attempting to remove an
   * attribute type that is not allowed to be altered.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRemoveUnsupportedAttr()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "delete: objectClass",
         "objectClass: subschema",
         "-",
         "add: objectClass",
         "objectClass: extensibleObject");

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
   * Tests the behavior of the schema backend when attempting to remove all
   * attribute type definitions.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRemoveAllAttributeTypes()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "delete: attributeTypes");

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
   * Tests the behavior of the schema backend when attempting to replace all
   * attribute types.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testReplaceAllAttributeTypes()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "replace: attributeTypes");

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
         "attributeTypes: ( 1.3.6.1.4.1.26027.1.999.4 " +
              "NAME 'testAddAttributeTypeSuccessful' " +
              "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE " +
              "X-ORGIN 'SchemaBackendTestCase' )");

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
         "attributeTypes: ( testaddattributetypesuccessfulnooid-oid " +
              "NAME 'testAddAttributeTypeSuccessfulNoOID' " +
              "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE " +
              "X-ORGIN 'SchemaBackendTestCase' )");

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
   * attribute type to a specific schema file.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  public void testAddAttributeTypeToAltSchemaFile()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: attributeTypes",
         "attributeTypes: ( testaddattributetypetoaltschemafile-oid " +
              "NAME 'testAddAttributeTypeToAltSchemaFile' " +
              "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE " +
              "X-ORIGIN 'SchemaBackendTestCase' " +
              "X-SCHEMA-FILE '98-schema-test-attrtype.ldif' )");

    String attrName = "testaddattributetypetoaltschemafile";
    assertFalse(DirectoryServer.getSchema().hasAttributeType(attrName));

    File schemaFile = new File(SchemaConfigManager.getSchemaDirectoryPath(),
                               "98-schema-test-attrtype.ldif");
    assertFalse(schemaFile.exists());

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
    assertTrue(schemaFile.exists());
  }



  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * attribute type in a manner that replaces an existing definition.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddAttributeTypeSuccessfulReplace()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: attributeTypes",
         "attributeTypes: ( testaddattributetypesuccessfulreplace-oid " +
              "NAME 'testAddAttributeTypeSuccessfulReplace' " +
              "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "",
         "dn: cn=schema",
         "changetype: modify",
         "add: attributeTypes",
         "attributeTypes: ( testaddattributetypesuccessfulreplace-oid " +
              "NAME 'testAddAttributeTypeSuccessfulReplace' " +
              "SYNTAX 1.3.6.1.4.1.1466.115.121.1.26 SINGLE-VALUE " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    String attrName = "testaddattributetypesuccessfulreplace";
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
   * Tests the behavior of the schema backend when attempting to replace an
   * attribute type definition in a custom schema file.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  public void testReplaceAttributeTypeInAltSchemaFile()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: attributeTypes",
         "attributeTypes: ( testreplaceattributetypeinaltschemafile-oid " +
              "NAME 'testReplaceAttributeTypeInAltSchemaFile' " +
              "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE " +
              "X-ORIGIN 'SchemaBackendTestCase' " +
              "X-SCHEMA-FILE '98-schema-test-replaceattrtype.ldif' )",
         "",
         "dn: cn=schema",
         "changetype: modify",
         "add: attributeTypes",
         "attributeTypes: ( testreplaceattributetypeinaltschemafile-oid " +
              "NAME 'testReplaceAttributeTypeInAltSchemaFile' " +
              "SYNTAX 1.3.6.1.4.1.1466.115.121.1.26 SINGLE-VALUE " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    String attrName = "testreplaceattributetypeinaltschemafile";
    assertFalse(DirectoryServer.getSchema().hasAttributeType(attrName));

    File schemaFile = new File(SchemaConfigManager.getSchemaDirectoryPath(),
                               "98-schema-test-replaceattrtype.ldif");
    assertFalse(schemaFile.exists());

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
    assertTrue(schemaFile.exists());
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
   * attribute type that conflicts with multiple existing types.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddAttributeTypeMultipleConflicts()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: attributeTypes",
         "attributeTypes: ( testaddattributetypemultipleconflicts-oid NAME " +
              "( 'testAddAttributeTypeMultipleConflicts' 'cn' 'uid' ) SYNTAX " +
              "1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE X-ORIGIN " +
              "'SchemaBackendTestCase' )");

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
   * attribute type that references an undefined superior attribute type.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddAttributeTypeUndefinedSuperior()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: attributeTypes",
         "attributeTypes: ( testaddattributetypeundefinedsuperior-oid NAME " +
              "'testAddAttributeTypeUndefinedSuperior' SUP undefined SYNTAX " +
              "1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE X-ORIGIN " +
              "'SchemaBackendTestCase' )");

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
   * Tests the behavior of the schema backend when attempting to remove an
   * attribute type that is defined in the server schema and does not have any
   * dependencies.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRemoveAttributeTypeSuccessful()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: attributeTypes",
         "attributeTypes: ( 1.3.6.1.4.1.26027.1.999.6 NAME " +
              "'testRemoveAttributeTypeSuccessful' SYNTAX " +
              "1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE X-ORIGIN " +
              "'SchemaBackendTestCase' )",
         "",
         "dn: cn=schema",
         "changetype: modify",
         "delete: attributeTypes",
         "attributeTypes: ( 1.3.6.1.4.1.26027.1.999.6 NAME " +
              "'testRemoveAttributeTypeSuccessful' SYNTAX " +
              "1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE X-ORIGIN " +
              "'SchemaBackendTestCase' )");

    String attrName = "testremoveattributetypesuccessful";
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
    assertFalse(DirectoryServer.getSchema().hasAttributeType(attrName));
  }



  /**
   * Tests the behavior of the schema backend when attempting to remove an
   * attribute type and add it back in the same modification.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRemoveThenAddAttributeTypeSuccessful()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: attributeTypes",
         "attributeTypes: ( testremovethenaddattributetypesuccessful-oid " +
              "NAME 'testRemoveThenAddAttributeTypeSuccessful' " +
              "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "",
         "dn: cn=schema",
         "changetype: modify",
         "delete: attributeTypes",
         "attributeTypes: ( testremovethenaddattributetypesuccessful-oid " +
              "NAME 'testRemoveThenAddAttributeTypeSuccessful' " +
              "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "-",
         "add: attributeTypes",
         "attributeTypes: ( testremovethenaddattributetypesuccessful-oid " +
              "NAME 'testRemoveThenAddAttributeTypeSuccessful' " +
              "SYNTAX 1.3.6.1.4.1.1466.115.121.1.26 SINGLE-VALUE " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    String attrName = "testremoveattributetypesuccessful";
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
    assertFalse(DirectoryServer.getSchema().hasAttributeType(attrName));
  }



  /**
   * Tests the behavior of the schema backend when attempting to remove an
   * attribute type that is not defined in the server schema.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRemoveAttributeTypeUndefined()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "delete: attributeTypes",
         "attributeTypes: ( testremoveattributetypeundefined-oid " +
              "NAME 'testRemoveAttributeTypeUndefined' " +
              "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    String attrName = "testremoveattributetypeundefined";
    assertFalse(DirectoryServer.getSchema().hasAttributeType(attrName));

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
   * Tests the behavior of the schema backend when attempting to remove an
   * attribute type that is referenced as the superior type for another
   * attribute type.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRemoveSuperiorAttributeType()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "delete: attributeTypes",
         "attributeTypes: ( 2.5.4.41 NAME 'name' EQUALITY caseIgnoreMatch " +
              "SUBSTR caseIgnoreSubstringsMatch " +
              "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15{32768} " +
              "X-ORIGIN 'RFC 2256' )");

    String attrName = "name";
    assertTrue(DirectoryServer.getSchema().hasAttributeType(attrName));

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertFalse(LDAPModify.mainModify(args, false, null, null) == 0);
    assertTrue(DirectoryServer.getSchema().hasAttributeType(attrName));
  }



  /**
   * Tests the behavior of the schema backend when attempting to remove an
   * attribute type that is referenced by an existing objectclass.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRemoveAttributeTypeReferencedByObjectClass()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "delete: attributeTypes",
         "attributeTypes: ( 0.9.2342.19200300.100.1.1 NAME 'uid' " +
              "EQUALITY caseIgnoreMatch SUBSTR caseIgnoreSubstringsMatch " +
              "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15{256} " +
              "X-ORIGIN 'RFC 1274' )");

    String attrName = "uid";
    assertTrue(DirectoryServer.getSchema().hasAttributeType(attrName));

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertFalse(LDAPModify.mainModify(args, false, null, null) == 0);
    assertTrue(DirectoryServer.getSchema().hasAttributeType(attrName));
  }



  /**
   * Tests the behavior of the schema backend when attempting to remove an
   * attribute type that is referenced by an existing name form.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRemoveAttributeTypeReferencedByNameForm()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: attributeTypes",
         "attributeTypes: ( testremoveattributetypereferencedbynf-oid " +
              "NAME 'testRemoveAttributeTypeReferencedByNF' " +
              "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "-",
         "add: objectClasses",
         "objectClasses:  ( testremoveattributetypereferencedbynfoc-oid " +
              "NAME 'testRemoveAttributeTypeReferencedByNFOC' SUP top " +
              "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: nameForms",
         "nameForms: ( testremoveattributetypereferencedbynfnf-oid " +
              "NAME 'testRemoveAttributeTypeReferencedByNFNF' " +
              "OC testRemoveAttributeTypeReferencedByNFOC " +
              "MUST testRemoveAttributeTypeReferencedByNF " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "",
         "dn: cn=schema",
         "changetype: modify",
         "delete: attributeTypes",
         "attributeTypes: ( testremoveattributetypereferencedbynf-oid " +
              "NAME 'testRemoveAttributeTypeReferencedByNF' " +
              "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    String attrName = "testremoveattributetypereferencedbynf";
    assertFalse(DirectoryServer.getSchema().hasAttributeType(attrName));

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertFalse(LDAPModify.mainModify(args, false, null, null) == 0);
    assertTrue(DirectoryServer.getSchema().hasAttributeType(attrName));
  }



  /**
   * Tests the behavior of the schema backend when attempting to remove an
   * attribute type that is referenced by an existing DIT content rule.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRemoveAttributeTypeReferencedByDCR()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: attributeTypes",
         "attributeTypes: ( testremoveattributetypereferencedbydcr-oid " +
              "NAME 'testRemoveAttributeTypeReferencedByDCR' " +
              "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "-",
         "add: objectClasses",
         "objectClasses:  ( testremoveattributetypereferencedbydcroc-oid " +
              "NAME 'testRemoveAttributeTypeReferencedByDCROC' SUP top " +
              "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: ditContentRules",
         "ditContentRules: ( testremoveattributetypereferencedbydcroc-oid " +
              "NAME 'testRemoveAttributeTypeReferencedByDCRDCR' " +
              "MAY testRemoveAttributeTypeReferencedByDCR " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "",
         "dn: cn=schema",
         "changetype: modify",
         "delete: attributeTypes",
         "attributeTypes: ( testremoveattributetypereferencedbydcr-oid " +
              "NAME 'testRemoveAttributeTypeReferencedByDCR' " +
              "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    String attrName = "testremoveattributetypereferencedbydcr";
    assertFalse(DirectoryServer.getSchema().hasAttributeType(attrName));

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertFalse(LDAPModify.mainModify(args, false, null, null) == 0);
    assertTrue(DirectoryServer.getSchema().hasAttributeType(attrName));
  }



  /**
   * Tests the behavior of the schema backend when attempting to remove an
   * attribute type that is referenced by an existing matching rule use.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRemoveAttributeTypeReferencedByMRU()
         throws Exception
  {
    SchemaTestMatchingRule matchingRule =
         new SchemaTestMatchingRule("testRemoveATRefByMRUMatch",
                                    "1.3.6.1.4.1.26027.1.999.17");
    DirectoryServer.registerMatchingRule(matchingRule, false);


    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: attributeTypes",
         "attributeTypes: ( testremoveatrefbymruat-oid " +
              "NAME 'testRemoveATRefByMRUAT' " +
              "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "-",
         "add: matchingRuleUse",
         "matchingRuleUse: ( 1.3.6.1.4.1.26027.1.999.17 " +
              "NAME 'testRemoveATRefByMRUMRU' APPLIES testRemoveATRefByMRUAT " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "",
         "dn: cn=schema",
         "changetype: modify",
         "delete: attributeTypes",
         "attributeTypes: ( testremoveatrefbymruat-oid " +
              "NAME 'testRemoveATRefByMRUAT' " +
              "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    String attrName = "testremoveatrefbymruat";
    assertFalse(DirectoryServer.getSchema().hasAttributeType(attrName));

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertFalse(LDAPModify.mainModify(args, false, null, null) == 0);

    MatchingRuleUse mru =
         DirectoryServer.getSchema().getMatchingRuleUse(matchingRule);
    assertNotNull(mru);
    assertTrue(mru.hasName("testremoveatrefbymrumru"));

    assertTrue(DirectoryServer.getSchema().hasAttributeType(attrName));
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
   * objectclass to a specific schema file.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddObjectClassToAltSchemaFile()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses: ( testaddobjectclasstoaltschemafile-oid NAME " +
              "'testAddObjectClassToAltSchemaFile' SUP top STRUCTURAL " +
              "MUST cn X-ORIGIN 'SchemaBackendTestCase' " +
              "X-SCHEMA-FILE '98-schema-test-oc.ldif' )");

    String ocName = "testaddobjectclasstoaltschemafile";
    assertFalse(DirectoryServer.getSchema().hasObjectClass(ocName));

    File schemaFile = new File(SchemaConfigManager.getSchemaDirectoryPath(),
                               "98-schema-test-oc.ldif");
    assertFalse(schemaFile.exists());

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
    assertTrue(schemaFile.exists());
  }



  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * objectclass that already exists (i.e., a replace)
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddObjectClassSuccessfulReplace()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses: ( testaddobjectclasssuccessfulreplace-oid " +
              "NAME 'testAddObjectClassSuccessfulReplace' SUP top STRUCTURAL " +
              "MUST cn X-ORIGIN 'SchemaBackendTestCase' )",
         "",
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses: ( testaddobjectclasssuccessfulreplace-oid " +
              "NAME 'testAddObjectClassSuccessfulReplace' SUP top STRUCTURAL " +
              "MUST cn MAY description X-ORIGIN 'SchemaBackendTestCase' )");

    String ocName = "testaddobjectclasssuccessfulreplace";
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
   * objectclass that conflicts with multiple existing objectclasses.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddObjectClassMultipleConflicts()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses: ( testaddobjectclassmultipleconflicts-oid " +
              "NAME ( 'testAddObjectClassMultipleConflicts' 'person' " +
              "'device' ) SUP top STRUCTURAL MUST cn " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    String ocName = "testaddobjectclassmultipleconflicts";
    assertFalse(DirectoryServer.getSchema().hasObjectClass(ocName));

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertFalse(LDAPModify.mainModify(args, false, null, null) == 0);
    assertFalse(DirectoryServer.getSchema().hasObjectClass(ocName));
  }



  /**
   * Tests the behavior of the schema backend when attempting to remove an
   * existing objectclass definition and then add it back in the same operation
   * with a different definition.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRemoveThenAddAddObjectClassSuccessful()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses: ( testremovethenaddobjectclasssuccessful-oid " +
              "NAME 'testRemoveThenAddObjectClassSuccessful' SUP top " +
              "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase' )",
         "",
         "dn: cn=schema",
         "changetype: modify",
         "delete: objectClasses",
         "objectClasses: ( testremovethenaddobjectclasssuccessful-oid " +
              "NAME 'testRemoveThenAddObjectClassSuccessful' SUP top " +
              "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase' )",
         "-",
         "add: objectClasses",
         "objectClasses: ( testremovethenaddobjectclasssuccessful-oid " +
              "NAME 'testRemoveThenAddObjectClassSuccessful' SUP top " +
              "STRUCTURAL MUST cn MAY description " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    String ocName = "testremovethenaddobjectclasssuccessful";
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



  /**
   * Tests the behavior of the schema backend when attempting to remove an
   * objectclass that exists and for which there are no dependencies.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRemoveObjectClassSuccessful()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses: ( 1.3.6.1.4.1.26027.1.999.7 NAME " +
              "'testRemoveObjectClassSuccessful' SUP top STRUCTURAL MUST cn " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "",
         "dn: cn=schema",
         "changetype: modify",
         "delete: objectClasses",
         "objectClasses: ( 1.3.6.1.4.1.26027.1.999.7 NAME " +
              "'testRemoveObjectClassSuccessful' SUP top STRUCTURAL MUST cn " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    String ocName = "testremoveobjectclasssuccessful";
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
    assertFalse(DirectoryServer.getSchema().hasObjectClass(ocName));
  }



  /**
   * Tests the behavior of the schema backend when attempting to remove an
   * objectclass that is the superior class for another objectclass.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRemoveSuperiorObjectClass()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "delete: objectClasses",
         "objectClasses: ( 2.5.6.6 NAME 'person' SUP top STRUCTURAL " +
              "MUST ( sn $ cn ) MAY ( userPassword $ telephoneNumber $ " +
              "seeAlso $ description ) X-ORIGIN 'RFC 2256' )");

    String ocName = "person";
    assertTrue(DirectoryServer.getSchema().hasObjectClass(ocName));

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertFalse(LDAPModify.mainModify(args, false, null, null) == 0);
    assertTrue(DirectoryServer.getSchema().hasObjectClass(ocName));
  }



  /**
   * Tests the behavior of the schema backend when attempting to remove an
   * objectclass that is referenced by an existing name form.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRemoveObjectClassReferencedByNameForm()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses:  ( testremoveobjectclassreferencedbynf-oid " +
              "NAME 'testRemoveObjectClassReferencedByNF' SUP top " +
              "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: nameForms",
         "nameForms: ( testremoveattributetypereferencedbynfnf-oid " +
              "NAME 'testRemoveObjectClassReferencedByNFNF' " +
              "OC testRemoveObjectClassReferencedByNF MUST cn " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "",
         "dn: cn=schema",
         "changetype: modify",
         "delete: objectClasses",
         "objectClasses:  ( testremoveobjectclassreferencedbynf-oid " +
              "NAME 'testRemoveObjectClassReferencedByNF' SUP top " +
              "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase')");

    String ocName = "testremoveobjectclassreferencedbynf";
    assertFalse(DirectoryServer.getSchema().hasObjectClass(ocName));

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertFalse(LDAPModify.mainModify(args, false, null, null) == 0);
    assertTrue(DirectoryServer.getSchema().hasObjectClass(ocName));
  }



  /**
   * Tests the behavior of the schema backend when attempting to remove an
   * objectclass that is referenced by an existing DIT content rule.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRemoveObjectClassReferencedByDCR()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses:  ( testremoveobjectclassreferencedbydcr-oid " +
              "NAME 'testRemoveObjectClassReferencedByDCR' SUP top " +
              "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: ditContentRules",
         "ditContentRules: ( testremoveobjectclassreferencedbydcr-oid " +
              "NAME 'testRemoveObjectClassReferencedByDCRDCR' " +
              "MAY description X-ORIGIN 'SchemaBackendTestCase' )",
         "",
         "dn: cn=schema",
         "changetype: modify",
         "delete: objectClasses",
         "objectClasses:  ( testremoveobjectclassreferencedbydcr-oid " +
              "NAME 'testRemoveObjectClassReferencedByDCR' SUP top " +
              "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase')");

    String ocName = "testremoveobjectclassreferencedbydcr";
    assertFalse(DirectoryServer.getSchema().hasObjectClass(ocName));

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertFalse(LDAPModify.mainModify(args, false, null, null) == 0);
    assertTrue(DirectoryServer.getSchema().hasObjectClass(ocName));
  }



  /**
   * Tests the behavior of the schema backend when attempting to add a new name
   * form that doesn't already exist.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddNameFormSuccessful()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses:  ( testaddnameformsuccessfuloc-oid " +
              "NAME 'testAddNameFormSuccessfulOC' SUP top STRUCTURAL MUST cn " +
              "X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: nameForms",
         "nameForms: ( 1.3.6.1.4.1.26027.1.999.8 " +
              "NAME 'testAddNameFormSuccessful' " +
              "OC testAddNameFormSuccessfulOC MUST cn " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    String nameFormName = "testaddnameformsuccessful";
    assertFalse(DirectoryServer.getSchema().hasNameForm(nameFormName));

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
    assertTrue(DirectoryServer.getSchema().hasNameForm(nameFormName));
  }



  /**
   * Tests the behavior of the schema backend when attempting to add a new name
   * form that doesn't already exist to an alternate schema file.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddNameFormToAltSchemaFile()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses:  ( testaddnameformtoaltschemafileoc-oid " +
              "NAME 'testAddNameFormToAltSchemaFileOC' SUP top STRUCTURAL " +
              "MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: nameForms",
         "nameForms: ( testaddnameformtoaltschemafile-oid " +
              "NAME 'testAddNameFormToAltSchemaFile' " +
              "OC testAddNameFormToAltSchemaFileOC MUST cn " +
              "X-ORIGIN 'SchemaBackendTestCase' " +
              "X-SCHEMA-FILE '98-schema-test-nameform.ldif' )");

    String nameFormName = "testaddnameformtoaltschemafile";
    assertFalse(DirectoryServer.getSchema().hasNameForm(nameFormName));

    File schemaFile = new File(SchemaConfigManager.getSchemaDirectoryPath(),
                               "98-schema-test-nameform.ldif");
    assertFalse(schemaFile.exists());

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
    assertTrue(DirectoryServer.getSchema().hasNameForm(nameFormName));
    assertTrue(schemaFile.exists());
  }



  /**
   * Tests the behavior of the schema backend when attempting to add a new name
   * form that references a required attribute type not defined in the server
   * schema.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddNameFormWithUndefinedReqAT()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses:  ( testaddnameformwithundefinedreqatoc-oid " +
              "NAME 'testAddNameFormWithUndefinedReqATOC' SUP top STRUCTURAL " +
              "MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: nameForms",
         "nameForms: ( testaddnameformwithundefinereqdat-oid " +
              "NAME 'testAddNameFormWithUndefinedReqAT' " +
              "OC testAddNameFormWithUndefinedReqATOC MUST xxxundefinedxxx " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    String nameFormName = "testaddnameformwithundefinedreqat";
    assertFalse(DirectoryServer.getSchema().hasNameForm(nameFormName));

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertFalse(LDAPModify.mainModify(args, false, null, null) == 0);
    assertFalse(DirectoryServer.getSchema().hasNameForm(nameFormName));
  }



  /**
   * Tests the behavior of the schema backend when attempting to add a new name
   * form that references an optional attribute type not defined in the server
   * schema.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddNameFormWithUndefinedOptAT()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses:  ( testaddnameformwithundefinedoptatoc-oid " +
              "NAME 'testAddNameFormWithUndefinedOptATOC' SUP top STRUCTURAL " +
              "MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: nameForms",
         "nameForms: ( testaddnameformwithundefineoptdat-oid " +
              "NAME 'testAddNameFormWithUndefinedOptAT' " +
              "OC testAddNameFormWithUndefinedOptATOC MUST cn " +
              "MAY xxxundefinedxxx X-ORIGIN 'SchemaBackendTestCase' )");

    String nameFormName = "testaddnameformwithundefinedoptat";
    assertFalse(DirectoryServer.getSchema().hasNameForm(nameFormName));

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertFalse(LDAPModify.mainModify(args, false, null, null) == 0);
    assertFalse(DirectoryServer.getSchema().hasNameForm(nameFormName));
  }



  /**
   * Tests the behavior of the schema backend when attempting to add a new name
   * form whose structural objectclass is not defined in the server schema.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddNameFormWithUndefinedOC()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: nameForms",
         "nameForms: ( testaddnameformwithundefinedoc-oid " +
              "NAME 'testAddNameFormWithUndefinedOC' " +
              "OC xxxundefinedxxx MUST cn X-ORIGIN 'SchemaBackendTestCase' )");

    String nameFormName = "testaddnameformwithundefinedoc";
    assertFalse(DirectoryServer.getSchema().hasNameForm(nameFormName));

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertFalse(LDAPModify.mainModify(args, false, null, null) == 0);
    assertFalse(DirectoryServer.getSchema().hasNameForm(nameFormName));
  }



  /**
   * Tests the behavior of the schema backend when attempting to add a new name
   * form whose objectclass auxiliary rather than structural.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddNameFormWithAuxiliaryOC()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses:  ( testaddnameformwithauxiliaryococ-oid " +
              "NAME 'testAddNameFormWithAuxiliaryOCOC' SUP top AUXILIARY " +
              "MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: nameForms",
         "nameForms: ( testaddnameformwithauxiliaryoc-oid " +
              "NAME 'testAddNameFormWithAuxiliaryOC' " +
              "OC testAddNameFormWithAuxiliaryOCOC MUST cn " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    String nameFormName = "testaddnameformwithauxiliaryoc";
    assertFalse(DirectoryServer.getSchema().hasNameForm(nameFormName));

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertFalse(LDAPModify.mainModify(args, false, null, null) == 0);
    assertFalse(DirectoryServer.getSchema().hasNameForm(nameFormName));
  }



  /**
   * Tests the behavior of the schema backend when attempting to add a new name
   * form that references a structural objectclass already referenced by another
   * name form.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddNameFormOCConflict()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses:  ( testaddnameformocconflictoc-oid " +
              "NAME 'testAddNameFormOCConflictOC' SUP top STRUCTURAL MUST cn " +
              "X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: nameForms",
         "nameForms: ( testaddnameformocconflict-oid " +
              "NAME 'testAddNameFormOCConflict' " +
              "OC testAddNameFormOCConflictOC MUST cn " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "",
         "dn: cn=schema",
         "changetype: modify",
         "add: nameForms",
         "nameForms: ( testaddnameformocconflict2-oid " +
              "NAME 'testAddNameFormOCConflict2' " +
              "OC testAddNameFormOCConflictOC MUST cn " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    String nameFormName = "testaddnameformocconflict2";
    assertFalse(DirectoryServer.getSchema().hasNameForm(nameFormName));

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertFalse(LDAPModify.mainModify(args, false, null, null) == 0);
    assertFalse(DirectoryServer.getSchema().hasNameForm(nameFormName));
  }



  /**
   * Tests the behavior of the schema backend when attempting to remove an
   * existing name form.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRemoveNameFormSuccessful()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses:  ( testremovenameformsuccessfuloc-oid " +
              "NAME 'testRemoveNameFormSuccessfulOC' SUP top STRUCTURAL " +
              "MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: nameForms",
         "nameForms: ( 1.3.6.1.4.1.26027.1.999.9 " +
              "NAME 'testRemoveNameFormSuccessful' " +
              "OC testRemoveNameFormSuccessfulOC MUST cn " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "",
         "dn: cn=schema",
         "changetype: modify",
         "delete: nameForms",
         "nameForms: ( 1.3.6.1.4.1.26027.1.999.9 " +
              "NAME 'testRemoveNameFormSuccessful' " +
              "OC testRemoveNameFormSuccessfulOC MUST cn " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    String nameFormName = "testremovenameformsuccessful";
    assertFalse(DirectoryServer.getSchema().hasNameForm(nameFormName));

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
    assertFalse(DirectoryServer.getSchema().hasNameForm(nameFormName));
  }



  /**
   * Tests the behavior of the schema backend when attempting to remove an
   * existing name form and then add it back in the same operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRemoveThenAddNameFormSuccessful()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses:  ( testremovethenaddnameformsuccessfuloc-oid " +
              "NAME 'testRemoveThenAddNameFormSuccessfulOC' SUP top " +
              "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase' )",
         "-",
         "add: nameForms",
         "nameForms: ( testremovethenaddnameformsuccessful-oid " +
              "NAME 'testRemoveThenAddNameFormSuccessful' " +
              "OC testRemoveThenAddNameFormSuccessfulOC MUST cn " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "",
         "dn: cn=schema",
         "changetype: modify",
         "delete: nameForms",
         "nameForms: ( testremovethenaddnameformsuccessful-oid " +
              "NAME 'testRemoveThenAddNameFormSuccessful' " +
              "OC testRemoveThenAddNameFormSuccessfulOC MUST cn " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "-",
         "add: nameForms",
         "nameForms: ( testremovethenaddnameformsuccessful-oid " +
              "NAME 'testRemoveThenAddNameFormSuccessful' " +
              "OC testRemoveThenAddNameFormSuccessfulOC MUST cn MAY sn " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    String nameFormName = "testremovethenaddnameformsuccessful";
    assertFalse(DirectoryServer.getSchema().hasNameForm(nameFormName));

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
    assertTrue(DirectoryServer.getSchema().hasNameForm(nameFormName));
  }



  /**
   * Tests the behavior of the schema backend when attempting to remove a name
   * form that is referenced by a DIT structure rule.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRemoveNameFormReferencedByDSR()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses:  ( testremovenameformreferencedbydsroc-oid " +
              "NAME 'testRemoveNameFormReferencedByDSROC' SUP top " +
              "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: nameForms",
         "nameForms: ( testremovenameformreferencedbydsrnf-oid " +
              "NAME 'testRemoveNameFormReferencedByDSRNF' " +
              "OC testRemoveNameFormReferencedByDSROC MUST cn " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "-",
         "add: ditStructureRules",
         "ditStructureRules: ( 999009 " +
              "NAME 'testRemoveNameFormReferencedByDSRDSR' " +
              "FORM testRemoveNameFormReferencedByDSRNF " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "",
         "dn: cn=schema",
         "changetype: modify",
         "delete: nameForms",
         "nameForms: ( testremovenameformreferencedbydsrnf-oid " +
              "NAME 'testRemoveNameFormReferencedByDSRNF' " +
              "OC testRemoveNameFormReferencedByDSROC MUST cn " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    String nameFormName = "testremovenameformreferencedbydsrnf";
    assertFalse(DirectoryServer.getSchema().hasNameForm(nameFormName));

    assertFalse(LDAPModify.mainModify(args, false, null, null) == 0);
    assertTrue(DirectoryServer.getSchema().hasNameForm(nameFormName));
  }



  /**
   * Tests the behavior of the schema backend when attempting to add a new DIT
   * content rule that doesn't already exist.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddDITContentRuleSuccessful()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses:  ( testaddditcontentrulesuccessfuloc-oid " +
              "NAME 'testAddDITContentRuleSuccessfulOC' SUP top STRUCTURAL " +
              "MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: ditContentRules",
         "ditContentRules: ( testaddditcontentrulesuccessfuloc-oid " +
              "NAME 'testAddDITContentRuleSuccessful' NOT description " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    String ocName = "testaddditcontentrulesuccessfuloc";
    assertFalse(DirectoryServer.getSchema().hasObjectClass(ocName));

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);

    ObjectClass oc = DirectoryServer.getSchema().getObjectClass(ocName);
    assertNotNull(oc);

    DITContentRule dcr = DirectoryServer.getSchema().getDITContentRule(oc);
    assertNotNull(dcr);
    assertTrue(dcr.hasName("testaddditcontentrulesuccessful"));
  }



  /**
   * Tests the behavior of the schema backend when attempting to replace an
   * existing DIT content rule.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testReplaceDITContentRuleSuccessful()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses:  ( testreplaceditcontentrulesuccessfuloc-oid " +
              "NAME 'testReplaceDITContentRuleSuccessfulOC' SUP top " +
              "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: ditContentRules",
         "ditContentRules: ( testreplaceditcontentrulesuccessfuloc-oid " +
              "NAME 'testReplaceDITContentRuleSuccessful' NOT description " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "",
         "dn: cn=schema",
         "changetype: modify",
         "add: ditContentRules",
         "ditContentRules: ( testreplaceditcontentrulesuccessfuloc-oid " +
              "NAME 'testReplaceDITContentRuleSuccessful' MAY sn " +
              "NOT description X-ORIGIN 'SchemaBackendTestCase' )");

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    String ocName = "testreplaceditcontentrulesuccessfuloc";
    assertFalse(DirectoryServer.getSchema().hasObjectClass(ocName));

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);

    ObjectClass oc = DirectoryServer.getSchema().getObjectClass(ocName);
    assertNotNull(oc);

    DITContentRule dcr = DirectoryServer.getSchema().getDITContentRule(oc);
    assertNotNull(dcr);
    assertTrue(dcr.hasName("testreplaceditcontentrulesuccessful"));
  }



  /**
   * Tests the behavior of the schema backend when attempting to add a new DIT
   * content rule to an alternate schema file.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddDITContentRuleToAltSchemaFile()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses:  ( testadddcrtoaltschemafileoc-oid " +
              "NAME 'testAddDCRToAltSchemaFileOC' SUP top STRUCTURAL " +
              "MUST cn X-SCHEMA-FILE '98-schema-test-dcr.ldif' " +
              "X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: ditContentRules",
         "ditContentRules: ( testadddcrtoaltschemafileoc-oid " +
              "NAME 'testAddDCRToAltSchemaFile' NOT description " +
              "X-SCHEMA-FILE '98-schema-test-dcr.ldif' " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    String ocName = "testadddcrtoaltschemafileoc";
    assertFalse(DirectoryServer.getSchema().hasObjectClass(ocName));

    File schemaFile = new File(SchemaConfigManager.getSchemaDirectoryPath(),
                               "98-schema-test-dcr.ldif");
    assertFalse(schemaFile.exists());

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);

    ObjectClass oc = DirectoryServer.getSchema().getObjectClass(ocName);
    assertNotNull(oc);

    DITContentRule dcr = DirectoryServer.getSchema().getDITContentRule(oc);
    assertNotNull(dcr);
    assertTrue(dcr.hasName("testadddcrtoaltschemafile"));

    assertTrue(schemaFile.exists());
  }



  /**
   * Tests the behavior of the schema backend when attempting to remove an
   * existing DIT content rule and add it back in the same operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRemoveThenAddDITContentRule()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses:  ( testremovethenaddditcontentruleoc-oid " +
              "NAME 'testRemoveThenAddDITContentRuleOC' SUP top " +
              "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: ditContentRules",
         "ditContentRules: ( testremovethenaddditcontentruleoc-oid " +
              "NAME 'testRemoveThenAddDITContentRule' NOT description " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "",
         "dn: cn=schema",
         "changetype: modify",
         "delete: ditContentRules",
         "ditContentRules: ( testremovethenaddditcontentruleoc-oid " +
              "NAME 'testRemoveThenAddDITContentRule' NOT description " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "-",
         "add: ditContentRules",
         "ditContentRules: ( testremovethenaddditcontentruleoc-oid " +
              "NAME 'testRemoveThenAddDITContentRule' MAY sn " +
              "NOT description X-ORIGIN 'SchemaBackendTestCase' )");

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    String ocName = "testremovethenaddditcontentruleoc";
    assertFalse(DirectoryServer.getSchema().hasObjectClass(ocName));

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);

    ObjectClass oc = DirectoryServer.getSchema().getObjectClass(ocName);
    assertNotNull(oc);

    DITContentRule dcr = DirectoryServer.getSchema().getDITContentRule(oc);
    assertNotNull(dcr);
    assertTrue(dcr.hasName("testremovethenaddditcontentrule"));
  }



  /**
   * Tests the behavior of the schema backend when attempting to add a new DIT
   * content rule whose structural objectclass is not defined in the schema.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddDITContentRuleUndefinedOC()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: ditContentRules",
         "ditContentRules: ( xxxundefinedxxx-oid " +
              "NAME 'testAddDITContentRuleUndefinedOC' NOT description " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

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
   * Tests the behavior of the schema backend when attempting to add a new DIT
   * content rule whose structural objectclass is not actually structural.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddDITContentRuleAuxiliaryOC()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses:  ( testaddditcontentruleauxiliaryococ-oid " +
              "NAME 'testAddDITContentRuleAuxiliaryOCOC' SUP top AUXILIARY " +
              "MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: ditContentRules",
         "ditContentRules: ( testaddditcontentruleauxiliaryococ-oid " +
              "NAME 'testAddDITContentRuleAuxiliaryOC' NOT description " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

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
   * Tests the behavior of the schema backend when attempting to add a new DIT
   * content rule whose structural objectclass is already referenced by an
   * existing DIT content rule.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddDITContentRuleConflictingOC()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses:  ( testaddditcontentruleconflictingococ-oid " +
              "NAME 'testAddDITContentRuleConflictingOCOC' SUP top " +
              "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: ditContentRules",
         "ditContentRules: ( testaddditcontentruleconflictingococ-oid " +
              "NAME 'testAddDITContentRuleConflictingOC' NOT description " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "",
         "dn: cn=schema",
         "changetype: modify",
         "add: ditContentRules",
         "ditContentRules: ( testaddditcontentruleconflictingococ-oid " +
              "NAME 'testAddDITContentRuleConflictingOC2' NOT description " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

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
   * Tests the behavior of the schema backend when attempting to add a new DIT
   * content rule with an undefined auxiliary objectclass.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddDITContentRuleUndefinedAuxOC()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses:  ( testaddditcontentruleundefinedauxococ-oid " +
              "NAME 'testAddDITContentRuleUndefinedAuxOCOC' SUP top " +
              "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: ditContentRules",
         "ditContentRules: ( testaddditcontentruleundefinedauxococ-oid " +
              "NAME 'testAddDITContentRuleUndefinedAuxOC' " +
              "AUX xxxundefinedxxx X-ORIGIN 'SchemaBackendTestCase' )");

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
   * Tests the behavior of the schema backend when attempting to add a new DIT
   * content rule that references an undefined required attribute type.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddDITContentRuleUndefinedReqAT()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses:  ( testaddditcontentruleundefinedreqatoc-oid " +
              "NAME 'testAddDITContentRuleAuxiliaryOCOC' SUP top STRUCTURAL " +
              "MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: ditContentRules",
         "ditContentRules: ( testaddditcontentruleundefinedreqatoc-oid " +
              "NAME 'testAddDITContentRuleUndefinedReqAT' " +
              "MUST xxxundefinedxxx X-ORIGIN 'SchemaBackendTestCase' )");

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
   * Tests the behavior of the schema backend when attempting to add a new DIT
   * content rule that references an undefined optional attribute type.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddDITContentRuleUndefinedOptAT()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses:  ( testaddditcontentruleundefinedoptatoc-oid " +
              "NAME 'testAddDITContentRuleAuxiliaryOCOC' SUP top STRUCTURAL " +
              "MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: ditContentRules",
         "ditContentRules: ( testaddditcontentruleundefinedoptatoc-oid " +
              "NAME 'testAddDITContentRuleUndefinedOptAT' " +
              "MAY xxxundefinedxxx X-ORIGIN 'SchemaBackendTestCase' )");

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
   * Tests the behavior of the schema backend when attempting to add a new DIT
   * content rule that references an undefined prohibited attribute type.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddDITContentRuleUndefinedNotAT()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses:  ( testaddditcontentruleundefinednotatoc-oid " +
              "NAME 'testAddDITContentRuleAuxiliaryOCOC' SUP top STRUCTURAL " +
              "MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: ditContentRules",
         "ditContentRules: ( testaddditcontentruleundefinednotatoc-oid " +
              "NAME 'testAddDITContentRuleUndefinedNotAT' " +
              "NOT xxxundefinedxxx X-ORIGIN 'SchemaBackendTestCase' )");

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
   * Tests the behavior of the schema backend when attempting to remove an
   * existing DIT content rule.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRemoveDITContentRuleSuccessful()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses:  ( testremoveditcontentrulesuccessfuloc-oid " +
              "NAME 'testRemoveDITContentRuleSuccessfulOC' SUP top " +
              "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: ditContentRules",
         "ditContentRules: ( testremoveditcontentrulesuccessfuloc-oid " +
              "NAME 'testRemoveDITContentRuleSuccessful' NOT description " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "",
         "dn: cn=schema",
         "changetype: modify",
         "delete: ditContentRules",
         "ditContentRules: ( testremoveditcontentrulesuccessfuloc-oid " +
              "NAME 'testRemoveDITContentRuleSuccessful' NOT description " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    String ocName = "testremoveditcontentrulesuccessfuloc";
    assertFalse(DirectoryServer.getSchema().hasObjectClass(ocName));

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);

    ObjectClass oc = DirectoryServer.getSchema().getObjectClass(ocName);
    assertNotNull(oc);

    DITContentRule dcr = DirectoryServer.getSchema().getDITContentRule(oc);
    assertNull(dcr);
  }



  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * DIT structure rule.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddDITStructureRuleSuccessful()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses:  ( testaddditstructurerulesuccessfuloc-oid " +
              "NAME 'testAddDITStructureRuleSuccessfulOC' SUP top " +
              "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: nameForms",
         "nameForms: ( testaddditstructurerulesuccessfulnf-oid " +
              "NAME 'testAddDITStructureRuleSuccessfulNF' " +
              "OC testAddDITStructureRuleSuccessfulOC MUST cn " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "-",
         "add: ditStructureRules",
         "ditStructureRules: ( 999001 " +
              "NAME 'testAddDITStructureRuleSuccessful' " +
              "FORM testAddDITStructureRuleSuccessfulNF " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    int ruleID = 999001;
    assertFalse(DirectoryServer.getSchema().hasDITStructureRule(ruleID));

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
    assertTrue(DirectoryServer.getSchema().hasDITStructureRule(ruleID));
  }



  /**
   * Tests the behavior of the schema backend when attempting to replace an
   * existing DIT structure rule definition.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testReplaceDITStructureRuleSuccessful()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses:  ( testreplaceditstructurerulesuccessfuloc-oid " +
              "NAME 'testReplaceDITStructureRuleSuccessfulOC' SUP top " +
              "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: nameForms",
         "nameForms: ( testreplaceditstructurerulesuccessfulnf-oid " +
              "NAME 'testReplaceDITStructureRuleSuccessfulNF' " +
              "OC testReplaceDITStructureRuleSuccessfulOC MUST cn " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "-",
         "add: ditStructureRules",
         "ditStructureRules: ( 999002 " +
              "NAME 'testReplaceDITStructureRuleSuccessful' " +
              "FORM testReplaceDITStructureRuleSuccessfulNF " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "",
         "dn: cn=schema",
         "changetype: modify",
         "add: ditStructureRules",
         "ditStructureRules: ( 999002 " +
              "NAME 'testReplaceDITStructureRuleSuccessful' " +
              "DESC 'Testing the replacement of an existing DSR' " +
              "FORM testReplaceDITStructureRuleSuccessfulNF " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    int ruleID = 999002;
    assertFalse(DirectoryServer.getSchema().hasDITStructureRule(ruleID));

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
    assertTrue(DirectoryServer.getSchema().hasDITStructureRule(ruleID));
  }



  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * DIT structure rule to an alternate schema file.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddDITStructureRuleToAltSchemaFile()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses:  ( testaddditstructureruletoaltschemafileoc-oid " +
              "NAME 'testAddDITStructureRuleToAltSchemaFileOC' SUP top " +
              "STRUCTURAL MUST cn X-SCHEMA-FILE '98-schema-test-dsr.ldif' " +
              "X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: nameForms",
         "nameForms: ( testaddditstructureruletoaltschemafilenf-oid " +
              "NAME 'testAddDITStructureRuleToAltSchemaFileNF' " +
              "OC testAddDITStructureRuleToAltSchemaFileOC MUST cn " +
              "X-SCHEMA-FILE '98-schema-test-dsr.ldif' " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "-",
         "add: ditStructureRules",
         "ditStructureRules: ( 999010 " +
              "NAME 'testAddDITStructureRuleToAltSchemaFile' " +
              "FORM testAddDITStructureRuleToAltSchemaFileNF " +
              "X-SCHEMA-FILE '98-schema-test-dsr.ldif' " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    int ruleID = 999010;
    assertFalse(DirectoryServer.getSchema().hasDITStructureRule(ruleID));

    File schemaFile = new File(SchemaConfigManager.getSchemaDirectoryPath(),
                               "98-schema-test-dsr.ldif");
    assertFalse(schemaFile.exists());

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
    assertTrue(DirectoryServer.getSchema().hasDITStructureRule(ruleID));

    assertTrue(schemaFile.exists());
  }



  /**
   * Tests the behavior of the schema backend when attempting to remove an
   * existing DIT structure rule definition and add it back in the same
   * operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRemoveAndAddDITStructureRuleSuccessful()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses:  ( testremoveandaddditstructurerulesuccessfuloc-oid " +
              "NAME 'testRemoveAndAddDITStructureRuleSuccessfulOC' SUP top " +
              "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: nameForms",
         "nameForms: ( testremoveandaddditstructurerulesuccessfulnf-oid " +
              "NAME 'testRemoveAndAddDITStructureRuleSuccessfulNF' " +
              "OC testRemoveAndAddDITStructureRuleSuccessfulOC MUST cn " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "-",
         "add: ditStructureRules",
         "ditStructureRules: ( 999003 " +
              "NAME 'testRemoveAndAddDITStructureRuleSuccessful' " +
              "FORM testRemoveAndAddDITStructureRuleSuccessfulNF " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "",
         "dn: cn=schema",
         "changetype: modify",
         "delete: ditStructureRules",
         "ditStructureRules: ( 999003 " +
              "NAME 'testRemoveAndAddDITStructureRuleSuccessful' " +
              "FORM testRemoveAndAddDITStructureRuleSuccessfulNF " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "-",
         "add: ditStructureRules",
         "ditStructureRules: ( 999003 " +
              "NAME 'testRemoveAndAddDITStructureRuleSuccessful' " +
              "DESC 'Testing removing and re-adding an existing DSR' " +
              "FORM testRemoveAndAddDITStructureRuleSuccessfulNF " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    int ruleID = 999003;
    assertFalse(DirectoryServer.getSchema().hasDITStructureRule(ruleID));

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
    assertTrue(DirectoryServer.getSchema().hasDITStructureRule(ruleID));
  }



  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * DIT structure rule with an undefined name form.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddDITStructureRuleUndefinedNameForm()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: ditStructureRules",
         "ditStructureRules: ( 999004 " +
              "NAME 'testAddDITStructureRuleUndefinedNameForm' " +
              "FORM xxxundefinedxxx " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    int ruleID = 999004;
    assertFalse(DirectoryServer.getSchema().hasDITStructureRule(ruleID));

    assertFalse(LDAPModify.mainModify(args, false, null, null) == 0);
    assertFalse(DirectoryServer.getSchema().hasDITStructureRule(ruleID));
  }



  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * DIT structure rule that references an undefined superior rule.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddDITStructureRuleUndefinedSuperior()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses:  ( testadddsrundefinedsuperioroc-oid " +
              "NAME 'testAddDSRUndefinedSuperiorOC' SUP top " +
              "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: nameForms",
         "nameForms: ( testadddsrundefinedsuperiornf-oid " +
              "NAME 'testAddDSRUndefinedSuperiorNF' " +
              "OC testAddDSRUndefinedSuperiorOC MUST cn " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "-",
         "add: ditStructureRules",
         "ditStructureRules: ( 999005 " +
              "NAME 'testAddDSRUndefinedSuperior' " +
              "FORM testAddDSRUndefinedSuperiorNF SUP 999000 " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    int ruleID = 999005;
    assertFalse(DirectoryServer.getSchema().hasDITStructureRule(ruleID));

    assertFalse(LDAPModify.mainModify(args, false, null, null) == 0);
    assertFalse(DirectoryServer.getSchema().hasDITStructureRule(ruleID));
  }



  /**
   * Tests the behavior of the schema backend when attempting to remove an
   * existing DIT structure rule definition.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRemoveDITStructureRuleSuccessful()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses:  ( testremoveditstructurerulesuccessfuloc-oid " +
              "NAME 'testRemoveDITStructureRuleSuccessfulOC' SUP top " +
              "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: nameForms",
         "nameForms: ( testremoveditstructurerulesuccessfulnf-oid " +
              "NAME 'testRemoveDITStructureRuleSuccessfulNF' " +
              "OC testRemoveDITStructureRuleSuccessfulOC MUST cn " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "-",
         "add: ditStructureRules",
         "ditStructureRules: ( 999006 " +
              "NAME 'testRemoveDITStructureRuleSuccessful' " +
              "FORM testRemoveDITStructureRuleSuccessfulNF " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "",
         "dn: cn=schema",
         "changetype: modify",
         "delete: ditStructureRules",
         "ditStructureRules: ( 999006 " +
              "NAME 'testRemoveDITStructureRuleSuccessful' " +
              "FORM testRemoveDITStructureRuleSuccessfulNF " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    int ruleID = 999006;
    assertFalse(DirectoryServer.getSchema().hasDITStructureRule(ruleID));

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
    assertFalse(DirectoryServer.getSchema().hasDITStructureRule(ruleID));
  }



  /**
   * Tests the behavior of the schema backend when attempting to remove an
   * existing DIT structure rule definition which is the superior rule for
   * another DIT structure rule.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRemoveSuperiorDITStructureRule()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses:  ( testremovesuperiorditstructureruleoc-oid " +
              "NAME 'testRemoveSuperiorDITStructureRuleOC' SUP top " +
              "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "objectClasses:  ( testremovesuperiorditstructureruleoc2-oid " +
              "NAME 'testRemoveSuperiorDITStructureRuleOC2' SUP top " +
              "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: nameForms",
         "nameForms: ( testremovesuperiorditstructurerulenf-oid " +
              "NAME 'testRemoveSuperiorDITStructureRuleNF' " +
              "OC testRemoveSuperiorDITStructureRuleOC MUST cn " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "nameForms: ( testremovesuperiorditstructurerulenf2-oid " +
              "NAME 'testRemoveSuperiorDITStructureRuleNF2' " +
              "OC testRemoveSuperiorDITStructureRuleOC2 MUST cn " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "-",
         "add: ditStructureRules",
         "ditStructureRules: ( 999007 " +
              "NAME 'testRemoveSuperiorDITStructureRule' " +
              "FORM testRemoveSuperiorDITStructureRuleNF " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "ditStructureRules: ( 999008 " +
              "NAME 'testRemoveSuperiorDITStructureRule2' " +
              "FORM testRemoveSuperiorDITStructureRuleNF2 SUP 999007 " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "",
         "dn: cn=schema",
         "changetype: modify",
         "delete: ditStructureRules",
         "ditStructureRules: ( 999007 " +
              "NAME 'testRemoveSuperiorDITStructureRule' " +
              "FORM testRemoveSuperiorDITStructureRuleNF " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    int ruleID = 999007;
    assertFalse(DirectoryServer.getSchema().hasDITStructureRule(ruleID));

    assertFalse(LDAPModify.mainModify(args, false, null, null) == 0);
    assertTrue(DirectoryServer.getSchema().hasDITStructureRule(ruleID));


    path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "delete: ditStructureRules",
         "ditStructureRules: ( 999008 " +
              "NAME 'testRemoveSuperiorDITStructureRule2' " +
              "FORM testRemoveSuperiorDITStructureRuleNF2 SUP 999007 " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "ditStructureRules: ( 999007 " +
              "NAME 'testRemoveSuperiorDITStructureRule' " +
              "FORM testRemoveSuperiorDITStructureRuleNF " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    args = new String[]
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
    assertFalse(DirectoryServer.getSchema().hasDITStructureRule(ruleID));
  }



  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * matching rule use that doesn't already exist.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddMatchingRuleUseSuccessful()
         throws Exception
  {
    SchemaTestMatchingRule matchingRule =
         new SchemaTestMatchingRule("testAddMRUSuccessfulMatch",
                                    "1.3.6.1.4.1.26027.1.999.10");
    DirectoryServer.registerMatchingRule(matchingRule, false);


    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: matchingRuleUse",
         "matchingRuleUse: ( 1.3.6.1.4.1.26027.1.999.10 " +
              "NAME 'testAddMRUSuccessful' APPLIES cn " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    assertFalse(DirectoryServer.getSchema().hasMatchingRuleUse(matchingRule));

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);

    MatchingRuleUse mru =
         DirectoryServer.getSchema().getMatchingRuleUse(matchingRule);
    assertNotNull(mru);
    assertTrue(mru.hasName("testaddmrusuccessful"));
  }



  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * matching rule to an alternate schema file.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddMatchingRuleUseToAltSchemaFile()
         throws Exception
  {
    SchemaTestMatchingRule matchingRule =
         new SchemaTestMatchingRule("testAddMRUToAltSchemaFileMatch",
                                    "1.3.6.1.4.1.26027.1.999.18");
    DirectoryServer.registerMatchingRule(matchingRule, false);


    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: matchingRuleUse",
         "matchingRuleUse: ( 1.3.6.1.4.1.26027.1.999.18 " +
              "NAME 'testAddMRUToAltSchemaFile' APPLIES cn " +
              "X-SCHEMA-FILE '98-schema-test-mru.ldif' " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    assertFalse(DirectoryServer.getSchema().hasMatchingRuleUse(matchingRule));

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    File schemaFile = new File(SchemaConfigManager.getSchemaDirectoryPath(),
                               "98-schema-test-mru.ldif");
    assertFalse(schemaFile.exists());

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);

    MatchingRuleUse mru =
         DirectoryServer.getSchema().getMatchingRuleUse(matchingRule);
    assertNotNull(mru);
    assertTrue(mru.hasName("testaddmrutoaltschemafile"));

    assertTrue(schemaFile.exists());
  }



  /**
   * Tests the behavior of the schema backend when attempting to replace an
   * existing matching rule use.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testReplaceMatchingRuleUseSuccessful()
         throws Exception
  {
    SchemaTestMatchingRule matchingRule =
         new SchemaTestMatchingRule("testReplaceMRUSuccessfulMatch",
                                    "1.3.6.1.4.1.26027.1.999.11");
    DirectoryServer.registerMatchingRule(matchingRule, false);


    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: matchingRuleUse",
         "matchingRuleUse: ( 1.3.6.1.4.1.26027.1.999.11 " +
              "NAME 'testReplaceMRUSuccessful' APPLIES cn " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "",
         "dn: cn=schema",
         "changetype: modify",
         "add: matchingRuleUse",
         "matchingRuleUse: ( 1.3.6.1.4.1.26027.1.999.11 " +
              "NAME 'testReplaceMRUSuccessful' APPLIES ( cn $ sn ) " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    assertFalse(DirectoryServer.getSchema().hasMatchingRuleUse(matchingRule));

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);

    MatchingRuleUse mru =
         DirectoryServer.getSchema().getMatchingRuleUse(matchingRule);
    assertNotNull(mru);
    assertTrue(mru.hasName("testreplacemrusuccessful"));
  }



  /**
   * Tests the behavior of the schema backend when attempting to remove and
   * re-add an existing matching rule use in the same operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRemoveAndAddMatchingRuleUse()
         throws Exception
  {
    SchemaTestMatchingRule matchingRule =
         new SchemaTestMatchingRule("testRemoveAndAddMRUMatch",
                                    "1.3.6.1.4.1.26027.1.999.12");
    DirectoryServer.registerMatchingRule(matchingRule, false);


    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: matchingRuleUse",
         "matchingRuleUse: ( 1.3.6.1.4.1.26027.1.999.12 " +
              "NAME 'testRemoveAndAddMRU' APPLIES cn " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "",
         "dn: cn=schema",
         "changetype: modify",
         "delete: matchingRuleUse",
         "matchingRuleUse: ( 1.3.6.1.4.1.26027.1.999.12 " +
              "NAME 'testRemoveAndAddMRU' APPLIES cn " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "-",
         "add: matchingRuleUse",
         "matchingRuleUse: ( 1.3.6.1.4.1.26027.1.999.12 " +
              "NAME 'testRemoveAndAddMRU' APPLIES ( cn $ sn ) " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    assertFalse(DirectoryServer.getSchema().hasMatchingRuleUse(matchingRule));

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);

    MatchingRuleUse mru =
         DirectoryServer.getSchema().getMatchingRuleUse(matchingRule);
    assertNotNull(mru);
    assertTrue(mru.hasName("testremoveandaddmru"));
  }



  /**
   * Tests the behavior of the schema backend when attempting to add a matching
   * rule use that references the same matching rule as another matching rule
   * use.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddMatchingRuleUseMRConflict()
         throws Exception
  {
    SchemaTestMatchingRule matchingRule =
         new SchemaTestMatchingRule("testAddMRUMRConflictMatch",
                                    "1.3.6.1.4.1.26027.1.999.14");
    DirectoryServer.registerMatchingRule(matchingRule, false);


    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: matchingRuleUse",
         "matchingRuleUse: ( 1.3.6.1.4.1.26027.1.999.14 " +
              "NAME 'testAddMRUMRConflict' APPLIES cn " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "",
         "dn: cn=schema",
         "changetype: modify",
         "add: matchingRuleUse",
         "matchingRuleUse: ( 1.3.6.1.4.1.26027.1.999.14 " +
              "NAME 'testAddMRUMRConflict2' APPLIES sn " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    assertFalse(DirectoryServer.getSchema().hasMatchingRuleUse(matchingRule));

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertFalse(LDAPModify.mainModify(args, false, null, null) == 0);

    MatchingRuleUse mru =
         DirectoryServer.getSchema().getMatchingRuleUse(matchingRule);
    assertNotNull(mru);
    assertTrue(mru.hasName("testaddmrumrconflict"));
  }



  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * matching rule use that references an undefined matching rule.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddMatchingRuleUseMRUndefined()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: matchingRuleUse",
         "matchingRuleUse: ( 1.3.6.1.4.1.26027.1.999.15 " +
              "NAME 'testAddMRUMRUndefined' APPLIES cn " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

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
   * matching rule use that references an undefined attribute type.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddMatchingRuleUseAttributeTypeUndefined()
         throws Exception
  {
    SchemaTestMatchingRule matchingRule =
         new SchemaTestMatchingRule("testAddMRUATUndefinedMatch",
                                    "1.3.6.1.4.1.26027.1.999.16");
    DirectoryServer.registerMatchingRule(matchingRule, false);


    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: matchingRuleUse",
         "matchingRuleUse: ( 1.3.6.1.4.1.26027.1.999.16 " +
              "NAME 'testAddMatchingRuleUseATUndefined' " +
              "APPLIES xxxundefinedxxx " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    assertFalse(DirectoryServer.getSchema().hasMatchingRuleUse(matchingRule));

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
   * Tests the behavior of the schema backend when attempting to remove an
   * existing matching rule use.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRemoveMatchingRuleUseSuccessful()
         throws Exception
  {
    SchemaTestMatchingRule matchingRule =
         new SchemaTestMatchingRule("testRemoveMRUSuccessfulMatch",
                                    "1.3.6.1.4.1.26027.1.999.13");
    DirectoryServer.registerMatchingRule(matchingRule, false);


    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: matchingRuleUse",
         "matchingRuleUse: ( 1.3.6.1.4.1.26027.1.999.13 " +
              "NAME 'testRemoveMRUSuccessful' APPLIES cn " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "",
         "dn: cn=schema",
         "changetype: modify",
         "delete: matchingRuleUse",
         "matchingRuleUse: ( 1.3.6.1.4.1.26027.1.999.13 " +
              "NAME 'testRemoveMRUSuccessful' APPLIES cn " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    assertFalse(DirectoryServer.getSchema().hasMatchingRuleUse(matchingRule));

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);

    MatchingRuleUse mru =
         DirectoryServer.getSchema().getMatchingRuleUse(matchingRule);
    assertNull(mru);
  }



  /**
   * Tests the {@code exportLDIF} method with a valid configuration.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testExportLDIF()
         throws Exception
  {
    DN configEntryDN =
            DN.decode("ds-cfg-backend-id=schema,cn=Backends,cn=config");
    DN[] baseDNs = { DN.decode("cn=schema") };

    ConfigEntry configEntry =
         DirectoryServer.getConfigHandler().getConfigEntry(configEntryDN);

    File tempFile = File.createTempFile("schema", "testExportLDIF");
    tempFile.deleteOnExit();
    LDIFExportConfig exportConfig =
         new LDIFExportConfig(tempFile.getAbsolutePath(),
                              ExistingFileBehavior.OVERWRITE);

    schemaBackend.exportLDIF(configEntry, baseDNs, exportConfig);

    assertTrue(tempFile.exists());
    assertTrue(tempFile.length() > 0);
  }



  /**
   * Tests the {@code importLDIF} method to ensure that it throws an exception.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { DirectoryException.class })
  public void testImportLDIF()
         throws Exception
  {
    DN configEntryDN =
            DN.decode("ds-cfg-backend-id=schema,cn=Backends,cn=config");
    DN[] baseDNs = { DN.decode("cn=schema") };

    ConfigEntry configEntry =
         DirectoryServer.getConfigHandler().getConfigEntry(configEntryDN);

    File tempFile = File.createTempFile("schema", "testImportLDIF");
    tempFile.deleteOnExit();

    LDIFImportConfig importConfig =
         new LDIFImportConfig(tempFile.getAbsolutePath());

    schemaBackend.importLDIF(configEntry, baseDNs, importConfig);
  }



  /**
   * Tests the {@code getComponentEntryDN} method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetComponentEntryDN()
         throws Exception
  {
    DN configEntryDN =
            DN.decode("ds-cfg-backend-id=schema,cn=Backends,cn=config");
    assertEquals(schemaBackend.getComponentEntryDN(), configEntryDN);
  }



  /**
   * Tests the {@code getClassName} method.
   */
  @Test()
  public void testGetClassName()
  {
    assertEquals(schemaBackend.getClassName(), SchemaBackend.class.getName());
  }



  /**
   * Tests the {@code getAlerts} method.
   */
  @Test()
  public void testGetAlerts()
  {
    LinkedHashMap<String,String> alerts = schemaBackend.getAlerts();
    assertNotNull(alerts);
    assertFalse(alerts.isEmpty());
  }
}

