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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 * Portions Copyright 2024 3A Systems, LLC.
 */
package org.opends.server.backends;

import static org.forgerock.opendj.ldap.ResultCode.*;
import static org.forgerock.opendj.ldap.schema.CoreSchema.*;
import static org.mockito.Mockito.*;
import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.opends.server.types.ExistingFileBehavior.*;
import static org.opends.server.types.NullOutputStream.*;
import static org.opends.server.util.StaticUtils.*;
import static org.testng.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.DITContentRule;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.opendj.ldap.schema.MatchingRuleUse;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.forgerock.opendj.server.config.server.SchemaBackendCfg;
import org.forgerock.util.Utils;
import org.opends.server.ServerContextBuilder;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperationBasis;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperationBasis;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.schema.SchemaConstants;
import org.opends.server.schema.SchemaHandler.SchemaUpdater;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.LDIFImportResult;
import org.opends.server.types.SearchFilter;
import org.opends.server.util.Args;
import org.opends.server.util.ServerConstants;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.forgerock.opendj.ldap.tools.LDAPModify;

/** A set of test cases for the schema backend. */
@SuppressWarnings("javadoc")
public class SchemaBackendTestCase extends BackendTestCase
{
  /** A reference to the schema backend. */
  private SchemaBackend schemaBackend;

  /** Ensures that the Directory Server is running and gets a reference to the schema backend. */
  @BeforeClass
  public void startServer() throws Exception
  {
    TestCaseUtils.startServer();

    schemaBackend = (SchemaBackend) getServerContext().getBackendConfigManager().getLocalBackendById("schema");
    assertNotNull(schemaBackend);
  }

  @AfterClass
  public void removeFilesCreatedInTests()
  {
    final File schemaDir = DirectoryServer.getEnvironmentConfig().getSchemaDirectory();

    final List<String> filesCreatedInTests = Arrays.asList(
        "98-schema-test-attrtype.ldif",
        "98-schema-test-replaceattrtype.ldif",
        "98-schema-test-oc.ldif",
        "98-schema-test-nameform.ldif",
        "98-schema-test-dcr.ldif",
        "98-schema-test-dsr.ldif",
        "98-schema-test-mru.ldif");
    for (String fileCreatedInTests : filesCreatedInTests)
    {
      new File(schemaDir, fileCreatedInTests).delete();
    }
  }

  /**
   * Tests the {@code initializeBackend} method by providing a null
   * configuration entry.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ConfigException.class})
  public void testInitializeWithNullConfigEntry() throws Exception
  {
    new SchemaBackend().configureBackend(null, ServerContextBuilder.aServerContext().build());
  }

  @Test(expectedExceptions = { NullPointerException.class })
  public void testInitializeWithNullParams() throws Exception
  {
    new SchemaBackend().configureBackend(null, null);
  }

  /**
   * Tests the {@code getEntry} method to ensure that it is able to retrieve
   * the schema entry if it is given a valid entry DN.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testGetValidEntry()
         throws Exception
  {
    DN    schemaDN    = DN.valueOf("cn=schema");
    Entry schemaEntry = schemaBackend.getEntry(schemaDN);
    assertNotNull(schemaEntry);
    assertEquals(schemaEntry.getName(), schemaDN);

    assertTrue(schemaEntry.hasAttribute(getAttributeTypesAttributeType()));
    assertTrue(schemaEntry.hasAttribute(getObjectClassesAttributeType()));
    assertTrue(schemaEntry.hasAttribute(getLDAPSyntaxesAttributeType()));
    assertTrue(schemaEntry.hasAttribute(getMatchingRulesAttributeType()));
  }

  /**
   * Tests the {@code getEntry} method to ensure that it is not able to retrieve
   * anything when given an inappropriate DN.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testGetInvalidEntry()
         throws Exception
  {
    DN    schemaDN    = DN.valueOf("cn=notschema");
    Entry schemaEntry = schemaBackend.getEntry(schemaDN);
    assertNull(schemaEntry);

    schemaDN    = DN.valueOf("cn=child,cn=schema");
    schemaEntry = schemaBackend.getEntry(schemaDN);
    assertNull(schemaEntry);
  }

  /**
   * Tests the {@code getSchemaEntry} method to ensure that it is able to
   * retrieve the appropriate information with different DNs.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testGetSchemaEntry() throws Exception
  {
    DN    schemaDN    = DN.valueOf("cn=schema");
    Entry schemaEntry = schemaBackend.getSchemaEntry(schemaDN);
    assertNotNull(schemaEntry);
    assertEquals(schemaEntry.getName(), schemaDN);

    assertTrue(schemaEntry.hasAttribute(getAttributeTypesAttributeType()));
    assertTrue(schemaEntry.hasAttribute(getObjectClassesAttributeType()));
    assertTrue(schemaEntry.hasAttribute(getLDAPSyntaxesAttributeType()));
    assertTrue(schemaEntry.hasAttribute(getMatchingRulesAttributeType()));

    schemaDN    = DN.valueOf("cn=subschema");
    schemaEntry = schemaBackend.getSchemaEntry(schemaDN);
    assertNotNull(schemaEntry);
    assertEquals(schemaEntry.getName(), schemaDN);

    assertTrue(schemaEntry.hasAttribute(getAttributeTypesAttributeType()));
    assertTrue(schemaEntry.hasAttribute(getObjectClassesAttributeType()));
    assertTrue(schemaEntry.hasAttribute(getLDAPSyntaxesAttributeType()));
    assertTrue(schemaEntry.hasAttribute(getMatchingRulesAttributeType()));
  }

  /**
   * Tests the {@code entryExists} method with a valid schema DN.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testEntryExistsValidDN()
         throws Exception
  {
    DN schemaDN = DN.valueOf("cn=schema");
    assertTrue(schemaBackend.entryExists(schemaDN));
  }

  /**
   * Tests the {@code entryExists} method with an invalid schema DN.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testEntryExistsInvalidDN()
         throws Exception
  {
    DN schemaDN = DN.valueOf("cn=notschema");
    assertFalse(schemaBackend.entryExists(schemaDN));
  }

  /** Tests to ensure that the {@code addEntry} method always throws an exception. */
  @Test(expectedExceptions = DirectoryException.class)
  public void testAddEntry() throws Exception
  {
    Entry entry = createEntry(DN.valueOf("cn=schema"));
    AddOperation addOperation = getRootConnection().processAdd(entry);
    schemaBackend.addEntry(entry, addOperation);
  }

  /** Tests to ensure that the {@code deleteEntry} method always throws an exception. */
  @Test(expectedExceptions = DirectoryException.class)
  public void testDeleteEntry()
         throws Exception
  {
    DN schemaDN = DN.valueOf("cn=schema");

    DeleteOperationBasis deleteOperation = new DeleteOperationBasis(
        getRootConnection(), nextOperationID(), nextMessageID(),
        null, schemaDN);

    schemaBackend.deleteEntry(schemaDN, deleteOperation);
  }

  /** Tests to ensure that the {@code renameEntry} method always throws an exception. */
  @Test(expectedExceptions = DirectoryException.class)
  public void testRenameEntry()
         throws Exception
  {
    DN currentSchemaDN = DN.valueOf("cn=schema");
    DN newSchemaDN     = DN.valueOf("cn=newschema");

    ModifyDNOperationBasis modifyDNOperation = new ModifyDNOperationBasis(
        getRootConnection(), nextOperationID(), nextMessageID(),
        null, currentSchemaDN, newSchemaDN.rdn(), true, null);

    Entry newSchemaEntry = schemaBackend.getSchemaEntry(newSchemaDN);
    schemaBackend.renameEntry(currentSchemaDN, newSchemaEntry, modifyDNOperation);
  }

  /**
   * Performs a simple base-level search to verify that the schema entry is
   * returned.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testSimpleBaseSearch()
         throws Exception
  {
    String filterString = "(|(objectClass=*)(objectClass=ldapSubentry))";

    final SearchRequest request = newSearchRequest("cn=schema", SearchScope.BASE_OBJECT, filterString);
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
    assertNotNull(searchOperation);
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    assertFalse(searchOperation.getSearchEntries().isEmpty());
  }

  /**
   * Performs a simple single-level search to verify that nothing is returned.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testSimpleOneLevelSearch() throws Exception
  {
    String filterString = "(|(objectClass=*)(objectClass=ldapSubentry))";

    final SearchRequest request = newSearchRequest("cn=schema", SearchScope.SINGLE_LEVEL, filterString);
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
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
  @Test
  public void testSimpleSubtreeSearch()
         throws Exception
  {
    String filterString = "(|(objectClass=*)(objectClass=ldapSubentry))";

    final SearchRequest request = newSearchRequest("cn=schema", SearchScope.WHOLE_SUBTREE, filterString);
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
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
  @Test
  public void testSimpleSubordinateSubtreeSearch()
         throws Exception
  {
    String filterString = "(|(objectClass=*)(objectClass=ldapSubentry))";

    final SearchRequest request = newSearchRequest("cn=schema", SearchScope.SUBORDINATES, filterString);
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
    assertNotNull(searchOperation);
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    assertTrue(searchOperation.getSearchEntries().isEmpty());
  }

  /**
   * Performs a set of searches in the schema backend to ensure that they
   * correctly set the matched DN in the response.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testSearchMatchedDN() throws Exception
  {
    InternalClientConnection conn = getRootConnection();
    DN baseDN = DN.valueOf("o=bogus,cn=schema");
    SearchFilter filter = SearchFilter.objectClassPresent();

    for (SearchScope scope : SearchScope.values())
    {
      final SearchRequest request = newSearchRequest(baseDN, scope, filter);
      InternalSearchOperation searchOperation = conn.processSearch(request);
      assertNotNull(searchOperation.getMatchedDN(),
                    "No matched DN for scope " + scope);
    }
  }

  /**
   * Tests the behavior of the schema backend with regard to the
   * ds-cfg-show-all-attributes configuration.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test
  public void testTreatAsUserAttrs()
         throws Exception
  {
    DN schemaDN = DN.valueOf("cn=schema");
    AttributeType a = getAttributeTypesAttributeType();
    AttributeType o = getObjectClassesAttributeType();
    AttributeType m = getMatchingRulesAttributeType();
    AttributeType s = getLDAPSyntaxesAttributeType();

    assertFalse(schemaBackend.showAllAttributes());
    Entry schemaEntry = schemaBackend.getSchemaEntry(schemaDN);
    assertTrue(schemaEntry.hasOperationalAttribute(a));
    assertTrue(schemaEntry.hasOperationalAttribute(o));
    assertTrue(schemaEntry.hasOperationalAttribute(m));
    assertTrue(schemaEntry.hasOperationalAttribute(s));

    setShowAllAttributes(schemaBackend, true);
    schemaEntry = schemaBackend.getSchemaEntry(schemaDN);
    assertFalse(schemaEntry.hasOperationalAttribute(a));
    assertFalse(schemaEntry.hasOperationalAttribute(o));
    assertFalse(schemaEntry.hasOperationalAttribute(m));
    assertTrue(schemaEntry.hasOperationalAttribute(s));

    setShowAllAttributes(schemaBackend, false);
    assertFalse(schemaBackend.showAllAttributes());
    schemaEntry = schemaBackend.getSchemaEntry(schemaDN);
    assertTrue(schemaEntry.hasOperationalAttribute(a));
    assertTrue(schemaEntry.hasOperationalAttribute(o));
    assertTrue(schemaEntry.hasOperationalAttribute(m));
    assertTrue(schemaEntry.hasOperationalAttribute(s));
  }

  private void setShowAllAttributes(SchemaBackend schemaBackend, boolean showAllAttributes)
  {
    SchemaBackendCfg cfg = spy(SchemaBackendCfg.class);
    when(cfg.isShowAllAttributes()).thenReturn(showAllAttributes);
    schemaBackend.applyConfigurationChange(cfg);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * attribute type that is not allowed to be altered.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddUnsupportedAttr()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClass",
         "objectClass: extensibleObject");

    runModify(argsNotPermissive(), ldif, UNWILLING_TO_PERFORM);
  }

  /**
   * Tests the behavior of the schema backend when attempting to remove an
   * attribute type that is not allowed to be altered.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRemoveUnsupportedAttr()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "delete: objectClass",
         "objectClass: subschema",
         "-",
         "add: objectClass",
         "objectClass: extensibleObject");

    runModify(argsNotPermissive(), ldif, UNWILLING_TO_PERFORM);
  }

  /**
   * Tests the behavior of the schema backend when attempting to remove all
   * attribute type definitions.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRemoveAllAttributeTypes()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "delete: attributeTypes");

    runModify(argsNotPermissive(), ldif, UNWILLING_TO_PERFORM);
  }

  /**
   * Tests the behavior of the schema backend when attempting to replace all
   * attribute types.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testReplaceAllAttributeTypes()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "replace: attributeTypes");

    runModify(argsNotPermissive(), ldif, UNWILLING_TO_PERFORM);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * attribute type with a valid syntax and that isn't already defined.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddAttributeTypeSuccessful()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: attributeTypes",
         "attributeTypes: ( 1.3.6.1.4.1.26027.1.999.4 " +
              "NAME 'testAddAttributeTypeSuccessful' " +
              "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE " +
              "X-ORGIN 'SchemaBackendTestCase' )");

    String attrName = "testaddattributetypesuccessful";
    assertFalse(getSchema().hasAttributeType(attrName));

    runModify(argsNotPermissive(), ldif, System.err, SUCCESS);
    assertTrue(getSchema().hasAttributeType(attrName));
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * attribute type with a valid syntax (but using a textual OID rather than
   * numeric) and that isn't already defined.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddAttributeTypeSuccessfulNoOID()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: attributeTypes",
         "attributeTypes: ( testaddattributetypesuccessfulnooid-oid " +
              "NAME 'testAddAttributeTypeSuccessfulNoOID' " +
              "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE " +
              "X-ORGIN 'SchemaBackendTestCase' )");

    String attrName = "testaddattributetypesuccessfulnooid";
    assertFalse(getSchema().hasAttributeType(attrName));

    runModify(argsNotPermissive(), ldif, System.err, SUCCESS);
    assertTrue(getSchema().hasAttributeType(attrName));
  }

  private static Schema getSchema()
  {
    return getServerContext().getSchema();
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * attribute type with a valid syntax (but using a textual OID rather than
   * numeric) and that has no space before last parenthesis.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddAttributeType()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: attributeTypes",
         "attributeTypes: ( testaddattributetypenospacebeforepathenthesis-oid " +
              "NAME 'testAddAttributeTypeNoSpaceBeforeParenthesis' " +
              "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE " +
              "SINGLE-VALUE)");

    String attrName = "testaddattributetypenospacebeforeparenthesis";
    assertFalse(getSchema().hasAttributeType(attrName));

    runModify(argsNotPermissive(), ldif, System.err, SUCCESS);
    assertTrue(getSchema().hasAttributeType(attrName));
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * attribute type to a specific schema file.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddAttributeTypeToAltSchemaFile()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: attributeTypes",
         "attributeTypes: ( testaddattributetypetoaltschemafile-oid " +
              "NAME 'testAddAttributeTypeToAltSchemaFile' " +
              "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE " +
              "X-ORIGIN 'SchemaBackendTestCase' " +
              "X-SCHEMA-FILE '98-schema-test-attrtype.ldif' )");

    String attrName = "testaddattributetypetoaltschemafile";
    assertFalse(getSchema().hasAttributeType(attrName));
    assertSchemaFileExists("98-schema-test-attrtype.ldif", false);

    runModify(argsNotPermissive(), ldif, System.err, SUCCESS);

    assertTrue(getSchema().hasAttributeType(attrName));
    assertSchemaFileExists("98-schema-test-attrtype.ldif", true);
  }

  /**
   * The type of modification.
   * <p>
   * This enumeration allows to have more readable tests data (avoid use of boolean).
   */
  enum ModifyType {
    PERMISSIVE(true),
    NON_PERMISSIVE(false);

    private final String[] modifyArgs;

    private ModifyType(boolean permissive)
    {
      this.modifyArgs = SchemaBackendTestCase.args(permissive);
    }

    /** Returns the arguments to use for the modify operation. */
    String[] args()
    {
      return modifyArgs;
    }
  }

  @DataProvider
  public Object[][] dataForAddAttributeTypeReplaceTest()
  {
    return new Object[][] {
      // change syntax
      { ModifyType.PERMISSIVE, "attributeTypes:( at1-replace-oid " +
          "NAME 'at1-replace' " +
          "SYNTAX 1.3.6.1.4.1.1466.115.121.1.26 SINGLE-VALUE " +
          "X-ORIGIN 'SchemaBackendTestCase' )", SUCCESS },
      // add an unused name
      { ModifyType.PERMISSIVE, "attributeTypes:( at1-replace-oid " +
          "NAME ('at1-replace' 'at1-replace-bis')" +
          "SYNTAX 1.3.6.1.4.1.1466.115.121.1.26 SINGLE-VALUE " +
          "X-ORIGIN 'SchemaBackendTestCase' )", SUCCESS },
      // use another unused name
      { ModifyType.PERMISSIVE, "attributeTypes:( at1-replace-oid " +
          "NAME 'at1-replace-bis'" +
          "SYNTAX 1.3.6.1.4.1.1466.115.121.1.26 SINGLE-VALUE " +
          "X-ORIGIN 'SchemaBackendTestCase' )", SUCCESS },
      // add a name already used by another attribute type
      { ModifyType.PERMISSIVE, "attributeTypes:( at1-replace-oid " +
          "NAME ('at1-replace' 'cn')" +
          "SYNTAX 1.3.6.1.4.1.1466.115.121.1.26 SINGLE-VALUE " +
          "X-ORIGIN 'SchemaBackendTestCase' )", CONSTRAINT_VIOLATION },
      // use another unused oid with the same name
      { ModifyType.PERMISSIVE, "attributeTypes:( at1-replace-oid-new " +
          "NAME 'at1-replace'" +
          "SYNTAX 1.3.6.1.4.1.1466.115.121.1.26 SINGLE-VALUE " +
          "X-ORIGIN 'SchemaBackendTestCase' )", CONSTRAINT_VIOLATION },

      // change syntax
      { ModifyType.NON_PERMISSIVE, "attributeTypes:( at1-replace-oid " +
          "NAME 'at1-replace' " +
          "SYNTAX 1.3.6.1.4.1.1466.115.121.1.26 SINGLE-VALUE " +
          "X-ORIGIN 'SchemaBackendTestCase' )", ATTRIBUTE_OR_VALUE_EXISTS },
      // add an unused name
      { ModifyType.NON_PERMISSIVE, "attributeTypes:( at1-replace-oid " +
          "NAME ('at1-replace' 'at1-replace-bis')" +
          "SYNTAX 1.3.6.1.4.1.1466.115.121.1.26 SINGLE-VALUE " +
          "X-ORIGIN 'SchemaBackendTestCase' )", ATTRIBUTE_OR_VALUE_EXISTS },
      // use another unused name
      { ModifyType.NON_PERMISSIVE, "attributeTypes:( at1-replace-oid " +
          "NAME 'at1-replace-bis'" +
          "SYNTAX 1.3.6.1.4.1.1466.115.121.1.26 SINGLE-VALUE " +
          "X-ORIGIN 'SchemaBackendTestCase' )", ATTRIBUTE_OR_VALUE_EXISTS },
      // add a name already used by another attribute type
      { ModifyType.NON_PERMISSIVE, "attributeTypes:( at1-replace-oid " +
          "NAME ('at1-replace' 'cn')" +
          "SYNTAX 1.3.6.1.4.1.1466.115.121.1.26 SINGLE-VALUE " +
          "X-ORIGIN 'SchemaBackendTestCase' )", ATTRIBUTE_OR_VALUE_EXISTS },
      // use another unused oid with the same name
      { ModifyType.NON_PERMISSIVE, "attributeTypes:( at1-replace-oid-new " +
          "NAME 'at1-replace'" +
          "SYNTAX 1.3.6.1.4.1.1466.115.121.1.26 SINGLE-VALUE " +
          "X-ORIGIN 'SchemaBackendTestCase' )", CONSTRAINT_VIOLATION },
    };
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * attribute type in a manner that replaces an existing definition.
   */
  @Test(dataProvider= "dataForAddAttributeTypeReplaceTest")
  public void testAddAttributeTypeReplace(ModifyType modifyType, String newAttributeTypeDefinition,
      ResultCode expectedResultCode)
          throws Exception
  {
    final String initialOid = "at1-replace-oid";
    final String initialName = "at1-replace";
    try
    {
      // add the attribute that does not exists yet, this should always succeed
      String ldifAdd1 = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: attributeTypes",
         "attributeTypes: ( " + initialOid + " " +
             "NAME '" + initialName + "' " +
              "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE " +
              "X-ORIGIN 'SchemaBackendTestCase' )");
      assertFalse(getSchema().hasAttributeType(initialOid));
      assertFalse(getSchema().hasAttributeType(initialName));
      runModify(argsNotPermissive(), ldifAdd1, System.err, SUCCESS);
      assertTrue(getSchema().hasAttributeType(initialOid));
      assertTrue(getSchema().hasAttributeType(initialName));

      // try to add the attribute again, with its new definition
      String ldifAdd2 = toLdif(
          "dn: cn=schema",
          "changetype: modify",
          "add: attributeTypes",
          newAttributeTypeDefinition);

      runModify(modifyType.args(), ldifAdd2, System.err, expectedResultCode);
    }
    finally
    {
      // clean the attribute to put back the schema in its initial state before the test
      if (getSchema().hasAttributeType(initialOid))
      {
        String removalLdif = toLdif(
          "dn: cn=schema",
          "changetype: modify",
          "delete: attributeTypes",
          "attributeTypes: ( " + initialOid + " )"
          );
        runModify(argsPermissive(), removalLdif, System.err, SUCCESS);
      }
    }
  }

  /**
   * Tests the behavior of the schema backend when attempting to replace an
   * attribute type definition in a custom schema file.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testReplaceAttributeTypeInAltSchemaFile()
         throws Exception
  {
    String ldif = toLdif(
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
    assertFalse(getSchema().hasAttributeType(attrName));
    assertSchemaFileExists("98-schema-test-replaceattrtype.ldif", false);

    runModify(argsPermissive(), ldif, System.err, SUCCESS);

    assertTrue(getSchema().hasAttributeType(attrName));
    assertSchemaFileExists("98-schema-test-replaceattrtype.ldif", true);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * attribute type definition that can't be parsed.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddAttributeTypeInvalidSyntax()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: attributeTypes",
         "attributeTypes: invalidsyntax");

    runModify(argsNotPermissive(), ldif, INVALID_ATTRIBUTE_SYNTAX);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * attribute type with an undefined syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddAttributeTypeUndefinedSyntax()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: attributeTypes",
         "attributeTypes: ( testaddatundefinedsyntax-oid " +
              "NAME 'testAddATUndefinedSyntax' " +
              "SYNTAX 1.3.6.1.4.1.1466.115.121.1.99999 SINGLE-VALUE " +
              "X-ORGIN 'SchemaBackendTestCase' )");

    runModify(argsNotPermissive(), ldif, System.err, CONSTRAINT_VIOLATION);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * attribute type with an undefined equality matching rule.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddAttributeTypeUndefinedEMR()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: attributeTypes",
         "attributeTypes: ( testaddatundefinedemr-oid " +
              "NAME 'testAddATUndefinedEMR' EQUALITY xxxundefinedxxx " +
              "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE " +
              "X-ORGIN 'SchemaBackendTestCase' )");

    runModify(argsNotPermissive(), ldif, System.err, CONSTRAINT_VIOLATION);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * attribute type with an undefined ordering matching rule.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddAttributeTypeUndefinedOMR()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: attributeTypes",
         "attributeTypes: ( testaddatundefinedomr-oid " +
              "NAME 'testAddATUndefinedOMR' ORDERING xxxundefinedxxx " +
              "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE " +
              "X-ORGIN 'SchemaBackendTestCase' )");

    runModify(argsNotPermissive(), ldif, System.err, CONSTRAINT_VIOLATION);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * attribute type with an undefined substring matching rule.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddAttributeTypeUndefinedSMR()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: attributeTypes",
         "attributeTypes: ( testaddatundefinedsmr-oid " +
              "NAME 'testAddATUndefinedSMR' SUBSTR xxxundefinedxxx " +
              "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE " +
              "X-ORGIN 'SchemaBackendTestCase' )");

    runModify(argsNotPermissive(), ldif, System.err, CONSTRAINT_VIOLATION);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * attribute type with an undefined approximate matching rule.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddAttributeTypeUndefinedAMR()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: attributeTypes",
         "attributeTypes: ( testaddatundefinedamr-oid " +
              "NAME 'testAddATUndefinedAMR' " +
              "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE " +
              "X-APPROX 'xxxundefinedxxx' X-ORGIN 'SchemaBackendTestCase' )");

    runModify(argsNotPermissive(), ldif, System.err, CONSTRAINT_VIOLATION);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * attribute type with an invalid usage.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddAttributeTypeInvalidUsage()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: attributeTypes",
         "attributeTypes: ( testaddatundefinedsyntax-oid " +
              "NAME 'testAddATUndefinedSyntax' " +
              "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE " +
              "USAGE xxxinvalidxxx X-ORGIN 'SchemaBackendTestCase' )");

    runModify(argsNotPermissive(), ldif, System.err, INVALID_ATTRIBUTE_SYNTAX);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * attribute type whose superior type is marked OBSOLETE in the server schema.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddAttributeTypeObsoleteSuperior()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: attributeTypes",
         "attributeTypes: ( testaddatobsoletesuperiorsup-oid " +
              "NAME 'testAddATObsoleteSuperiorSup' OBSOLETE " +
              "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE " +
              "X-ORGIN 'SchemaBackendTestCase' )",
         "attributeTypes: ( testaddatobsoletesuperior-oid " +
              "NAME 'testAddATObsoleteSuperior' " +
              "SUP testAddATObsoleteSuperiorSup " +
              "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE " +
              "X-ORGIN 'SchemaBackendTestCase' )");

    runModify(argsNotPermissive(), ldif, System.err, CONSTRAINT_VIOLATION);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * attribute type whose equality matching rule is marked OBSOLETE in the
   * server schema.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddAttributeTypeObsoleteEMR() throws Exception
  {
    MatchingRule matchingRule =
        registerNewMatchingRule("testAddATObsoleteEMRMatch", "1.3.6.1.4.1.26027.1.999.20", true);

    try
    {
      String ldif = toLdif(
          "dn: cn=schema",
          "changetype: modify",
          "add: attributeTypes",
          "attributeTypes: ( testaddatobsoleteemr-oid " +
              "NAME 'testAddATObsoleteEMR' " +
              "EQUALITY testAddATObsoleteEMRMatch " +
              "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE " +
          "X-ORGIN 'SchemaBackendTestCase' )");

      runModify(argsNotPermissive(), ldif, System.err, CONSTRAINT_VIOLATION);
    }
    finally
    {
      deregisterAttributeType("testaddatobsoleteemr-oid");
      deregisterMatchingRule(matchingRule);
    }
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * attribute type that conflicts with multiple existing types.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddAttributeTypeMultipleConflicts()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: attributeTypes",
         "attributeTypes: ( testaddattributetypemultipleconflicts-oid NAME " +
              "( 'testAddAttributeTypeMultipleConflicts' 'cn' 'uid' ) SYNTAX " +
              "1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE X-ORIGIN " +
              "'SchemaBackendTestCase' )");

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * attribute type that references an undefined superior attribute type.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddAttributeTypeUndefinedSuperior()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: attributeTypes",
         "attributeTypes: ( testaddattributetypeundefinedsuperior-oid NAME " +
              "'testAddAttributeTypeUndefinedSuperior' SUP undefined SYNTAX " +
              "1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE X-ORIGIN " +
              "'SchemaBackendTestCase' )");

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
  }

  /**
   * Tests the behavior of the schema backend when attempting to remove an
   * attribute type that is defined in the server schema and does not have any
   * dependencies.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRemoveAttributeTypeSuccessful()
         throws Exception
  {
    String ldif = toLdif(
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
    assertFalse(getSchema().hasAttributeType(attrName));

    runModify(argsNotPermissive(), ldif, System.err, SUCCESS);
    assertFalse(getSchema().hasAttributeType(attrName));
  }

  /**
   * Tests the behavior of the schema backend when attempting to remove an
   * attribute type and add it back in the same modification.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRemoveThenAddAttributeTypeSuccessful()
         throws Exception
  {
    String ldif = toLdif(
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
    assertFalse(getSchema().hasAttributeType(attrName));

    runModify(argsNotPermissive(), ldif, System.err, SUCCESS);
    assertFalse(getSchema().hasAttributeType(attrName));
  }

  /**
   * Tests the behavior of the schema backend when attempting to remove an
   * attribute type that is not defined in the server schema.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRemoveAttributeTypeUndefined()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "delete: attributeTypes",
         "attributeTypes: ( testremoveattributetypeundefined-oid " +
              "NAME 'testRemoveAttributeTypeUndefined' " +
              "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    String attrName = "testremoveattributetypeundefined";
    assertFalse(getSchema().hasAttributeType(attrName));

    runModify(argsNotPermissive(), ldif, NO_SUCH_ATTRIBUTE);
  }

  /**
   * Tests the behavior of the schema backend when attempting to remove an
   * attribute type that is referenced as the superior type for another
   * attribute type.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRemoveSuperiorAttributeType()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "delete: attributeTypes",
         "attributeTypes: ( 2.5.4.41 NAME 'name' EQUALITY caseIgnoreMatch " +
              "SUBSTR caseIgnoreSubstringsMatch " +
              "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15{32768} " +
              "X-ORIGIN 'RFC 2256' )");

    String attrName = "name";
    assertTrue(getSchema().hasAttributeType(attrName));

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
    assertTrue(getSchema().hasAttributeType(attrName));
  }

  /**
   * Tests the behavior of the schema backend when attempting to remove an
   * attribute type that is referenced by an existing objectclass.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRemoveAttributeTypeReferencedByObjectClass()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "delete: attributeTypes",
         "attributeTypes: ( 0.9.2342.19200300.100.1.1 NAME 'uid' " +
              "EQUALITY caseIgnoreMatch SUBSTR caseIgnoreSubstringsMatch " +
              "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15{256} " +
              "X-ORIGIN 'RFC 1274' )");

    String attrName = "uid";
    assertTrue(getSchema().hasAttributeType(attrName));

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
    assertTrue(getSchema().hasAttributeType(attrName));
  }

  /**
   * Tests the behavior of the schema backend when attempting to remove an
   * attribute type that is referenced by an existing name form.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRemoveAttributeTypeReferencedByNameForm()
         throws Exception
  {
    String attrName = "testremoveattributetypereferencedbynf";
    String modifyAttributeTypes = "attributeTypes: ( " + attrName + "-oid " +
          "NAME '" + attrName + "' " +
          "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE " +
          "X-ORIGIN 'SchemaBackendTestCase' )";
    String modifyObjectClasses = "objectClasses:  ( " + attrName + "oc-oid " +
          "NAME '" + attrName + "OC' SUP top " +
          "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase')";
    String modifyNameForms = "nameForms: ( " + attrName + "nf-oid " +
          "NAME '" + attrName + "NF' " +
          "OC " + attrName + "OC " +
          "MUST " + attrName + " " +
          "X-ORIGIN 'SchemaBackendTestCase' )";
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: attributeTypes",
         modifyAttributeTypes,
         "-",
         "add: objectClasses",
         modifyObjectClasses,
         "-",
         "add: nameForms",
         modifyNameForms);

    String ldif1 = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "delete: attributeTypes",
         modifyAttributeTypes);

    String ldif2 = toLdif(
        "dn: cn=schema",
        "changetype: modify",
        "delete: nameForms",
        modifyNameForms,
        "-",
        "delete: objectClasses",
        modifyObjectClasses,
        "-",
        "delete: attributeTypes",
        modifyAttributeTypes);

    try
    {
      assertFalse(getSchema().hasAttributeType(attrName));
      runModify(argsNotPermissive(), ldif, SUCCESS);

      runModify(argsNotPermissive(), ldif1, CONSTRAINT_VIOLATION);
      assertTrue(getSchema().hasAttributeType(attrName));
    }
    finally
    {
      runModify(argsNotPermissive(), ldif2, SUCCESS);
      assertFalse(getSchema().hasAttributeType(attrName));
    }
  }

  /**
   * Tests the behavior of the schema backend when attempting to remove an
   * attribute type that is referenced by an existing DIT content rule.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRemoveAttributeTypeReferencedByDCR()
         throws Exception
  {
    String ldif = toLdif(
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
    assertFalse(getSchema().hasAttributeType(attrName));

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
    assertTrue(getSchema().hasAttributeType(attrName));
  }

  /**
   * Tests the behavior of the schema backend when attempting to remove an
   * attribute type that is referenced by an existing matching rule use.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRemoveAttributeTypeReferencedByMRU() throws Exception
  {
    MatchingRule matchingRule =
        registerNewMatchingRule("testRemoveATRefByMRUMatch", "1.3.6.1.4.1.26027.1.999.17", false);

    try
    {
      String ldif = toLdif(
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
      assertFalse(getSchema().hasAttributeType(attrName));

      runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);

      assertMatchingRuleUseExistsWithName(matchingRule, "testremoveatrefbymrumru");

      assertTrue(getSchema().hasAttributeType(attrName));
    }
    finally
    {
      getSchema();
      deregisterMatchingRuleUse(matchingRule);
      deregisterAttributeType("testremoveatrefbymruat-oid");
      deregisterMatchingRule(matchingRule);
    }
  }

  private void updateSchema(SchemaUpdater updater) throws DirectoryException
  {
    getServerContext().getSchemaHandler().updateSchema(updater);
  }

  private void deregisterAttributeType(final String nameOrOid) throws DirectoryException
  {
    updateSchema(new SchemaUpdater()
    {
      @Override
      public void update(SchemaBuilder builder) throws DirectoryException
      {
        builder.removeAttributeType(nameOrOid);
      }
    });
  }

  private void deregisterMatchingRuleUse(final MatchingRule matchingRule) throws DirectoryException
  {
    updateSchema(new SchemaUpdater()
    {
      @Override
      public void update(SchemaBuilder builder) throws DirectoryException
      {
        builder.removeMatchingRuleUse(matchingRule.getOID());
      }
    });
  }

  private void deregisterMatchingRule(final MatchingRule matchingRule) throws DirectoryException
  {
    updateSchema(new SchemaUpdater()
    {
      @Override
      public void update(SchemaBuilder builder) throws DirectoryException
      {
        builder.removeMatchingRule(matchingRule.getOID());
      }
    });
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * objectclass that doesn't already exist, that has a valid superior class,
   * and for which all attributes contained in it are already defined.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddObjectClassSuccessful()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses: ( 1.3.6.1.4.1.26027.1.999.5 NAME " +
              "'testAddObjectClassSuccessful' SUP top STRUCTURAL MUST cn " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    String ocName = "testaddobjectclasssuccessful";
    assertFalse(getSchema().hasObjectClass(ocName));

    runModify(argsNotPermissive(), ldif, System.err, SUCCESS);
    assertTrue(getSchema().hasObjectClass(ocName));
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * objectclass that doesn't already exist, that has a textual OID rather than
   * numeric, has a valid superior class, and for which all attributes contained
   * in it are already defined.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddObjectClassSuccessfulNoOID()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses: ( testaddobjectclasssuccessfulnooid-oid NAME " +
              "'testAddObjectClassSuccessfulNoOID' SUP top STRUCTURAL " +
              "MUST cn X-ORIGIN 'SchemaBackendTestCase' )");

    String ocName = "testaddobjectclasssuccessfulnooid";
    assertFalse(getSchema().hasObjectClass(ocName));

    runModify(argsNotPermissive(), ldif, System.err, SUCCESS);
    assertTrue(getSchema().hasObjectClass(ocName));
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * objectclass to a specific schema file.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddObjectClassToAltSchemaFile()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses: ( testaddobjectclasstoaltschemafile-oid NAME " +
              "'testAddObjectClassToAltSchemaFile' SUP top STRUCTURAL " +
              "MUST cn X-ORIGIN 'SchemaBackendTestCase' " +
              "X-SCHEMA-FILE '98-schema-test-oc.ldif' )");

    String ocName = "testaddobjectclasstoaltschemafile";
    assertFalse(getSchema().hasObjectClass(ocName));
    assertSchemaFileExists("98-schema-test-oc.ldif", false);

    runModify(argsNotPermissive(), ldif, System.err, SUCCESS);

    assertTrue(getSchema().hasObjectClass(ocName));
    assertSchemaFileExists("98-schema-test-oc.ldif", true);
  }

  @DataProvider
  public Object[][] dataForAddObjectClassReplaceTest()
  {
    return new Object[][] {
      // change optional attribute
      { ModifyType.PERMISSIVE, "objectClasses: ( oc1-replace-oid " +
          "NAME 'oc1-replace' SUP top STRUCTURAL " +
          "MUST cn MAY description X-ORIGIN 'SchemaBackendTestCase' )", SUCCESS },
      // add an unused name
      { ModifyType.PERMISSIVE, "objectClasses: ( oc1-replace-oid " +
          "NAME ('oc1-replace' 'oc1-replace-bis') SUP top STRUCTURAL " +
          "MUST cn MAY description X-ORIGIN 'SchemaBackendTestCase' )", SUCCESS },
      // use another unused name
      { ModifyType.PERMISSIVE, "objectClasses: ( oc1-replace-oid " +
          "NAME 'oc1-replace-bis' SUP top STRUCTURAL " +
          "MUST cn MAY description X-ORIGIN 'SchemaBackendTestCase' )", SUCCESS },
      // add a name already used by another object class
      { ModifyType.PERMISSIVE, "objectClasses: ( oc1-replace-oid " +
          "NAME ('oc1-replace' 'person') SUP top STRUCTURAL " +
          "MUST cn MAY description X-ORIGIN 'SchemaBackendTestCase' )", CONSTRAINT_VIOLATION },
      // use another unused oid with the same name
      { ModifyType.PERMISSIVE, "objectClasses: ( oc1-replace-oid-new " +
          "NAME 'oc1-replace' SUP top STRUCTURAL " +
          "MUST cn MAY description X-ORIGIN 'SchemaBackendTestCase' )", CONSTRAINT_VIOLATION },

      // change optional attribute
      { ModifyType.NON_PERMISSIVE, "objectClasses: ( oc1-replace-oid " +
          "NAME 'oc1-replace' SUP top STRUCTURAL " +
          "MUST cn MAY description X-ORIGIN 'SchemaBackendTestCase' )", ATTRIBUTE_OR_VALUE_EXISTS },
      // add an unused name
      { ModifyType.NON_PERMISSIVE, "objectClasses: ( oc1-replace-oid " +
          "NAME ('oc1-replace' 'oc1-replace-bis') SUP top STRUCTURAL " +
          "MUST cn MAY description X-ORIGIN 'SchemaBackendTestCase' )", ATTRIBUTE_OR_VALUE_EXISTS },
      // use another unused name
      { ModifyType.NON_PERMISSIVE, "objectClasses: ( oc1-replace-oid " +
          "NAME 'oc1-replace-bis' SUP top STRUCTURAL " +
          "MUST cn MAY description X-ORIGIN 'SchemaBackendTestCase' )", ATTRIBUTE_OR_VALUE_EXISTS },
      // add a name already used by another object class
      { ModifyType.NON_PERMISSIVE, "objectClasses: ( oc1-replace-oid " +
          "NAME ('oc1-replace' 'person') SUP top STRUCTURAL " +
          "MUST cn MAY description X-ORIGIN 'SchemaBackendTestCase' )", ATTRIBUTE_OR_VALUE_EXISTS },
      // use another unused oid with the same name
      { ModifyType.NON_PERMISSIVE, "objectClasses: ( oc1-replace-oid-new " +
          "NAME 'oc1-replace' SUP top STRUCTURAL " +
          "MUST cn MAY description X-ORIGIN 'SchemaBackendTestCase' )", CONSTRAINT_VIOLATION },
    };
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * objectclass that already exists (i.e., a replace).
   */
  @Test(dataProvider="dataForAddObjectClassReplaceTest")
  public void testAddObjectClassReplace(ModifyType modifyType, String newObjectClassDefinition,
      ResultCode expectedResultCode)
         throws Exception
  {
    final String initialOid = "oc1-replace-oid";
    final String initialName = "oc1-replace";
    try
    {
      // add the attribute that does not exists yet, this should always succeed
      String ldifAdd1 = toLdif(
           "dn: cn=schema",
           "changetype: modify",
           "add: objectClasses",
           "objectClasses: ( " + initialOid + " " +
                "NAME '" + initialName + "' " +
                "SUP top STRUCTURAL " +
                "MUST cn X-ORIGIN 'SchemaBackendTestCase' )");
      assertFalse(getSchema().hasObjectClass(initialOid));
      assertFalse(getSchema().hasObjectClass(initialName));
      runModify(argsNotPermissive(), ldifAdd1, System.err, SUCCESS);
      assertTrue(getSchema().hasObjectClass(initialOid));
      assertTrue(getSchema().hasObjectClass(initialName));

      // try to add the attribute again, with its new definition
      String ldifAdd2 = toLdif(
          "dn: cn=schema",
          "changetype: modify",
          "add: objectClasses",
          newObjectClassDefinition);

      runModify(modifyType.args(), ldifAdd2, System.err, expectedResultCode);
    }
    finally
    {
      // clean the object class to put back the schema in its initial state before the test
      if (getSchema().hasObjectClass(initialOid))
      {
        String removalLdif = toLdif(
          "dn: cn=schema",
          "changetype: modify",
          "delete: objectClasses",
          "objectClasses: ( " + initialOid + " )");
        runModify(argsPermissive(), removalLdif, System.err, SUCCESS);
      }
    }
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * objectclass that conflicts with multiple existing objectclasses.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddObjectClassMultipleConflicts()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses: ( testaddobjectclassmultipleconflicts-oid " +
              "NAME ( 'testAddObjectClassMultipleConflicts' 'person' " +
              "'device' ) SUP top STRUCTURAL MUST cn " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    String ocName = "testaddobjectclassmultipleconflicts";
    assertFalse(getSchema().hasObjectClass(ocName));

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
    assertFalse(getSchema().hasObjectClass(ocName));
  }

  /**
   * Tests the behavior of the schema backend when attempting to remove an
   * existing objectclass definition and then add it back in the same operation
   * with a different definition.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRemoveThenAddAddObjectClassSuccessful()
         throws Exception
  {
    String ldif = toLdif(
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
    assertFalse(getSchema().hasObjectClass(ocName));

    runModify(argsNotPermissive(), ldif, System.err, SUCCESS);
    assertTrue(getSchema().hasObjectClass(ocName));
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * objectclass definition that can't be parsed.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddObjectClassInvalidSyntax()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses: invalidsyntax");

    runModify(argsNotPermissive(), ldif, INVALID_ATTRIBUTE_SYNTAX);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * objectclass that references an undefined superior class.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddObjectClassUndefinedSuperiorClass()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses: ( testaddocundefinedsuperior-oid NAME " +
              "'testAddOCUndefinedSuperior' SUP undefined STRUCTURAL " +
              "MUST cn X-ORIGIN 'SchemaBackendTestCase' )");

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * objectclass that references an obsolete superior class.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddObjectClassObsoleteSuperiorClass()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses: ( testaddocobsoletesuperiorsup-oid " +
              "NAME 'testAddOCObsoleteSuperiorSup' OBSOLETE STRUCTURAL " +
              "MUST cn X-ORIGIN 'SchemaBackendTestCase' )",
         "objectClasses: ( testaddocobsoletesuperior-oid " +
              "NAME 'testAddOCObsoleteSuperior' OBSOLETE " +
              "SUP testAddOCObsoleteSuperiorSup STRUCTURAL MUST cn " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    // object class is obsolete so having an obsolete superior is OK
    runModify(argsNotPermissive(), ldif, SUCCESS);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * objectclass that references an obsolete required attribute type.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddObjectClassObsoleteRequiredAttribute()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: attributeTypes",
         "attributeTypes: ( testaddocobsoleterequiredattrat-oid " +
              "NAME 'testAddOCObsoleteRequiredAttrAT' OBSOLETE " +
              "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "-",
         "add: objectClasses",
         "objectClasses: ( testaddocobsoleterequiredattroc-oid " +
              "NAME 'testAddOCObsoleteRequiredAttrOC' " +
              "STRUCTURAL MUST testAddOCObsoleteRequiredAttrAT " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * objectclass that references an obsolete optional attribute type.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddObjectClassObsoleteOptionalAttribute()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: attributeTypes",
         "attributeTypes: ( testaddocobsoleteoptionalattrat-oid " +
              "NAME 'testAddOCObsoleteOptionalAttrAT' OBSOLETE " +
              "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "-",
         "add: objectClasses",
         "objectClasses: ( testaddocobsoleteoptionalattroc-oid " +
              "NAME 'testAddOCObsoleteOptionalAttrOC' " +
              "STRUCTURAL MAY testAddOCObsoleteOptionalAttrAT " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * objectclass that references an undefined required attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddObjectClassUndefinedRequiredAttribute()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses: ( testaddocundefinedrequired-oid NAME " +
              "'testAddOCUndefinedRequired' SUP top STRUCTURAL " +
              "MUST undefined X-ORIGIN 'SchemaBackendTestCase' )");

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * objectclass that references an undefined required attribute when multiple
   * required attributes were provided.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddObjectClassMultipleUndefinedRequiredAttribute()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses: ( testaddocmultipleundefinedrequired-oid NAME " +
              "'testAddOCMultipleUndefinedRequired' SUP top STRUCTURAL " +
              "MUST ( cn $ xxxundefinedxxx ) " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * objectclass that references an undefined optional attribute.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddObjectClassUndefinedOptionalAttribute()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses: ( testaddocundefinedoptional-oid NAME " +
              "'testAddOCUndefinedOptional' SUP top STRUCTURAL " +
              "MAY undefined X-ORIGIN 'SchemaBackendTestCase' )");

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * objectclass that references an undefined optional attribute when multiple
   * optional attributes were provided.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddObjectClassMultipleUndefinedOptionalAttribute()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses: ( testaddocmultipleundefinedoptional-oid NAME " +
              "'testAddOCMultipleUndefinedOptional' SUP top STRUCTURAL " +
              "MAY ( cn $ xxxundefinedxxx ) " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * abstract objectclass whose superior class is not abstract.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddAbstractObjectClassWithNonAbstractSuperior()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses: ( testaddabstractocwithnonabstractsuperior-oid NAME " +
              "'testAddAbstractOCWithNonAbstractSuperior' SUP person " +
              "ABSTRACT MAY description X-ORIGIN 'SchemaBackendTestCase' )");

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * auxiliary objectclass whose superior class is structural.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddAuxiliaryObjectClassWithStructuralSuperior()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses: ( testaddauxiliaryocwithstructuralsuperior-oid NAME " +
              "'testAddAuxiliaryOCWithStructuralSuperior' SUP person " +
              "AUXILIARY MAY description X-ORIGIN 'SchemaBackendTestCase' )");

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * structural objectclass whose superior class is auxiliary.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddStructuralObjectClassWithAuxiliarySuperior()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses: ( testaddstructuralocwithauxiliarysuperior-oid NAME " +
              "'testAddStructuralOCWithAuxiliarySuperior' SUP posixAccount " +
              "STRUCTURAL MAY description X-ORIGIN 'SchemaBackendTestCase' )");

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
  }

  /**
   * Tests the behavior of the schema backend when attempting to remove an
   * objectclass that exists and for which there are no dependencies.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRemoveObjectClassSuccessful()
         throws Exception
  {
    String ldif = toLdif(
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
    assertFalse(getSchema().hasObjectClass(ocName));

    runModify(argsNotPermissive(), ldif, System.err, SUCCESS);
    assertFalse(getSchema().hasObjectClass(ocName));
  }

  /**
   * Tests the behavior of the schema backend when attempting to remove an
   * objectclass that is the superior class for another objectclass.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRemoveSuperiorObjectClass()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "delete: objectClasses",
         "objectClasses: ( 2.5.6.6 NAME 'person' SUP top STRUCTURAL " +
              "MUST ( sn $ cn ) MAY ( userPassword $ telephoneNumber $ " +
              "seeAlso $ description ) X-ORIGIN 'RFC 2256' )");

    String ocName = "person";
    assertTrue(getSchema().hasObjectClass(ocName));

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
    assertTrue(getSchema().hasObjectClass(ocName));
  }

  /**
   * Tests the behavior of the schema backend when attempting to remove an
   * objectclass that is referenced by an existing name form.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRemoveObjectClassReferencedByNameForm() throws Exception
  {
    String ocName = "testremoveobjectclassreferencedbynf";
    String modifyObjectClasses = "objectClasses:  ( " + ocName + "-oid " +
          "NAME '" + ocName + "' SUP top " +
          "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase')";
    String modifyNameForms = "nameForms: ( testremoveattributetypereferencedbynfnf-oid " +
          "NAME '" + ocName + "NF' " +
          "OC " + ocName + " MUST cn " +
          "X-ORIGIN 'SchemaBackendTestCase' )";

    String addOCThenNF = toLdif(
        "dn: cn=schema",
        "changetype: modify",
        "add: objectClasses",
        modifyObjectClasses,
        "-",
        "add: nameForms",
        modifyNameForms);
    String deleteOC = toLdif(
        "dn: cn=schema",
        "changetype: modify",
        "delete: objectClasses",
        modifyObjectClasses);
    String deleteNFThenOC = toLdif(
        "dn: cn=schema",
        "changetype: modify",
        "delete: nameForms",
        modifyNameForms,
        "-",
        "delete: objectClasses",
        modifyObjectClasses);

    try
    {
      assertFalse(getSchema().hasObjectClass(ocName));
      runModify(argsPermissive(), addOCThenNF, SUCCESS);

      runModify(argsPermissive(), deleteOC, CONSTRAINT_VIOLATION);
      assertTrue(getSchema().hasObjectClass(ocName));
    }
    finally
    {
      runModify(argsPermissive(), deleteNFThenOC, SUCCESS);
      assertFalse(getSchema().hasObjectClass(ocName));
    }
  }

  /**
   * Tests the behavior of the schema backend when attempting to remove an
   * objectclass that is referenced by an existing DIT content rule.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRemoveObjectClassReferencedByDCR()
         throws Exception
  {
    String ldif = toLdif(
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
    assertFalse(getSchema().hasObjectClass(ocName));

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
    assertTrue(getSchema().hasObjectClass(ocName));
  }

  private static String[] argsNotPermissive()
  {
    return args(false);
  }

  private static String[] argsPermissive()
  {
    return args(true);
  }

  private static String[] args(boolean usePermissiveModifyControl)
  {
    final Args args = new Args()
        .add("-h", "127.0.0.1")
        .add("-p", TestCaseUtils.getServerLdapPort())
        .add("-D", "cn=Directory Manager")
        .add("-w", "password");
    if (usePermissiveModifyControl)
    {
      args.add("-J", ServerConstants.OID_PERMISSIVE_MODIFY_CONTROL);
    }
    return args.toArray();
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new name
   * form that doesn't already exist.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddNameFormSuccessful()
         throws Exception
  {
    String ldif = toLdif(
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
    assertFalse(getSchema().hasNameForm(nameFormName));

    runModify(argsNotPermissive(), ldif, System.err, SUCCESS);
    assertTrue(getSchema().hasNameForm(nameFormName));
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new name
   * form that doesn't already exist to an alternate schema file.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddNameFormToAltSchemaFile()
         throws Exception
  {
    String ldif = toLdif(
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
    assertFalse(getSchema().hasNameForm(nameFormName));
    assertSchemaFileExists("98-schema-test-nameform.ldif", false);

    runModify(argsNotPermissive(), ldif, System.err, SUCCESS);

    assertTrue(getSchema().hasNameForm(nameFormName));
    assertSchemaFileExists("98-schema-test-nameform.ldif", true);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new name
   * form that references a required attribute type not defined in the server
   * schema.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddNameFormWithUndefinedReqAT()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses:  ( testaddnameformwithundefinedreqatoc-oid " +
              "NAME 'testAddNameFormWithUndefinedReqATOC' SUP top STRUCTURAL " +
              "MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: nameForms",
         "nameForms: ( testaddnameformwithundefinedreqat-oid " +
              "NAME 'testAddNameFormWithUndefinedReqAT' " +
              "OC testAddNameFormWithUndefinedReqATOC MUST xxxundefinedxxx " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    String nameFormName = "testaddnameformwithundefinedreqat";
    assertFalse(getSchema().hasNameForm(nameFormName));

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
    assertFalse(getSchema().hasNameForm(nameFormName));
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new name
   * form that references a required attribute type not defined in the server
   * schema when multiple required attributes were given.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddNameFormWithMultipleUndefinedReqAT()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses:  ( testaddnameformwithmultipleundefinedreqatoc-oid " +
              "NAME 'testAddNameFormWithMultipleUndefinedReqATOC' SUP top " +
              "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: nameForms",
         "nameForms: ( testaddnameformwithmultipleundefinedreqat-oid " +
              "NAME 'testAddNameFormWithMultipleUndefinedReqAT' " +
              "OC testAddNameFormWithMultipleUndefinedReqATOC " +
              "MUST ( cn $ xxxundefinedxxx ) " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    String nameFormName = "testaddnameformwithmultipleundefinedreqat";
    assertFalse(getSchema().hasNameForm(nameFormName));

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
    assertFalse(getSchema().hasNameForm(nameFormName));
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new name
   * form that references an optional attribute type not defined in the server
   * schema.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddNameFormWithUndefinedOptAT()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses:  ( testaddnameformwithundefinedoptatoc-oid " +
              "NAME 'testAddNameFormWithUndefinedOptATOC' SUP top STRUCTURAL " +
              "MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: nameForms",
         "nameForms: ( testaddnameformwithundefinedoptat-oid " +
              "NAME 'testAddNameFormWithUndefinedOptAT' " +
              "OC testAddNameFormWithUndefinedOptATOC MUST cn " +
              "MAY xxxundefinedxxx X-ORIGIN 'SchemaBackendTestCase' )");

    String nameFormName = "testaddnameformwithundefinedoptat";
    assertFalse(getSchema().hasNameForm(nameFormName));

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
    assertFalse(getSchema().hasNameForm(nameFormName));
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new name
   * form that references an optional attribute type not defined in the server
   * schema when multiple optional attribute types were provided.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddNameFormWithMultipleUndefinedOptAT()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses:  ( testaddnameformwithmultipleundefinedoptatoc-oid " +
              "NAME 'testAddNameFormWithMultipleUndefinedOptATOC' SUP top " +
              "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: nameForms",
         "nameForms: ( testaddnameformwithmultipleundefinedoptat-oid " +
              "NAME 'testAddNameFormWithMultipleUndefinedOptAT' " +
              "OC testAddNameFormWithMultipleUndefinedOptATOC MUST cn " +
              "MAY ( description $ xxxundefinedxxx ) " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    String nameFormName = "testaddnameformwithmultipleundefinedoptat";
    assertFalse(getSchema().hasNameForm(nameFormName));

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
    assertFalse(getSchema().hasNameForm(nameFormName));
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new name
   * form whose structural objectclass is not defined in the server schema.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddNameFormWithUndefinedOC()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: nameForms",
         "nameForms: ( testaddnameformwithundefinedoc-oid " +
              "NAME 'testAddNameFormWithUndefinedOC' " +
              "OC xxxundefinedxxx MUST cn X-ORIGIN 'SchemaBackendTestCase' )");

    String nameFormName = "testaddnameformwithundefinedoc";
    assertFalse(getSchema().hasNameForm(nameFormName));

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
    assertFalse(getSchema().hasNameForm(nameFormName));
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new name
   * form whose objectclass auxiliary rather than structural.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddNameFormWithAuxiliaryOC()
         throws Exception
  {
    String ldif = toLdif(
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
    assertFalse(getSchema().hasNameForm(nameFormName));

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
    assertFalse(getSchema().hasNameForm(nameFormName));
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new name
   * form whose structural objectclass is OBSOLETE rather than structural.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddNameFormWithObsoleteOC()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses:  ( testaddnameformwithobsoleteococ-oid " +
              "NAME 'testAddNameFormWithObsoleteOCOC' OBSOLETE SUP top " +
              "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: nameForms",
         "nameForms: ( testaddnameformwithobsoleteoc-oid " +
              "NAME 'testAddNameFormWithObsoleteOC' " +
              "OC testAddNameFormWithObsoleteOCOC MUST cn " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    String nameFormName = "testaddnameformwithobsoleteoc";
    assertFalse(getSchema().hasNameForm(nameFormName));

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
    assertFalse(getSchema().hasNameForm(nameFormName));
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new name
   * form with a required attribute type that is declared OBSOLETE.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddNameFormWithObsoleteReqAT()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: attributeTypes",
         "attributeTypes: ( testaddnfwithobsoletereqatat-oid " +
              "NAME 'testAddNFWithObsoleteReqATAT' OBSOLETE " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "-",
         "add: objectClasses",
         "objectClasses:  ( testaddnfwithobsoletereqatoc-oid " +
              "NAME 'testAddNFWithObsoleteReqATOC' SUP top STRUCTURAL " +
              "MUST cn X-ORIGIN 'SchemaBackendTestCase' )",
         "-",
         "add: nameForms",
         "nameForms: ( testaddnfwithobsoletereqatnf-oid " +
              "NAME 'testAddNFWithObsoleteReqATNF' " +
              "OC testAddNFWithObsoleteReqATOC " +
              "MUST testAddNFWithObsoleteReqATAT " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new name
   * form with an optional attribute type that is declared OBSOLETE.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddNameFormWithObsoleteOptAT()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: attributeTypes",
         "attributeTypes: ( testaddnfwithobsoleteoptatat-oid " +
              "NAME 'testAddNFWithObsoleteOptATAT' OBSOLETE " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "-",
         "add: objectClasses",
         "objectClasses:  ( testaddnfwithobsoleteoptatoc-oid " +
              "NAME 'testAddNFWithObsoleteOptATOC' SUP top STRUCTURAL " +
              "MUST cn X-ORIGIN 'SchemaBackendTestCase' )",
         "-",
         "add: nameForms",
         "nameForms: ( testaddnfwithobsoleteoptatnf-oid " +
              "NAME 'testAddNFWithObsoleteOptATNF' " +
              "OC testAddNFWithObsoleteOptATOC " +
              "MUST cn MAY testAddNFWithObsoleteOptATAT " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new name
   * form that references a structural objectclass already referenced by another
   * name form.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddNameFormOCConflict()
         throws Exception
  {
    String ldif = toLdif(
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
    assertFalse(getSchema().hasNameForm(nameFormName));

    runModify(argsNotPermissive(), ldif, SUCCESS);
    assertTrue(getSchema().hasNameForm(nameFormName));
  }

  /**
   * Tests the behavior of the schema backend when attempting to remove an
   * existing name form.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRemoveNameFormSuccessful()
         throws Exception
  {
    String ldif = toLdif(
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
    assertFalse(getSchema().hasNameForm(nameFormName));

    runModify(argsNotPermissive(), ldif, System.err, SUCCESS);
    assertFalse(getSchema().hasNameForm(nameFormName));
  }

  /**
   * Tests the behavior of the schema backend when attempting to remove an
   * existing name form and then add it back in the same operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRemoveThenAddNameFormSuccessful()
         throws Exception
  {
    String ldif = toLdif(
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
    assertFalse(getSchema().hasNameForm(nameFormName));

    runModify(argsNotPermissive(), ldif, System.err, SUCCESS);
    assertTrue(getSchema().hasNameForm(nameFormName));
  }

  /**
   * Tests the behavior of the schema backend when attempting to remove a name
   * form that is referenced by a DIT structure rule.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRemoveNameFormReferencedByDSR()
         throws Exception
  {
    String ldif = toLdif(
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

    String nameFormName = "testremovenameformreferencedbydsrnf";
    assertFalse(getSchema().hasNameForm(nameFormName));

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
    assertTrue(getSchema().hasNameForm(nameFormName));
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new DIT
   * content rule that doesn't already exist.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddDITContentRuleSuccessful()
         throws Exception
  {
    String ldif = toLdif(
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

    String ocName = "testaddditcontentrulesuccessfuloc";
    assertFalse(getSchema().hasObjectClass(ocName));

    runModify(argsNotPermissive(), ldif, System.err, SUCCESS);

    ObjectClass oc = getSchema().getObjectClass(ocName);
    assertFalse(oc.isPlaceHolder());

    DITContentRule dcr = getSchema().getDITContentRule(oc);
    assertNotNull(dcr);
    assertTrue(dcr.hasName("testaddditcontentrulesuccessful"));
  }

  /**
   * Tests the behavior of the schema backend when attempting to replace an
   * existing DIT content rule.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testReplaceDITContentRuleSuccessful()
         throws Exception
  {
    String ldif = toLdif(
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

    String ocName = "testreplaceditcontentrulesuccessfuloc";
    assertFalse(getSchema().hasObjectClass(ocName));

    runModify(argsPermissive(), ldif, System.err, SUCCESS);

    ObjectClass oc = getSchema().getObjectClass(ocName);
    assertFalse(oc.isPlaceHolder());

    DITContentRule dcr = getSchema().getDITContentRule(oc);
    assertNotNull(dcr);
    assertTrue(dcr.hasName("testreplaceditcontentrulesuccessful"));
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new DIT
   * content rule to an alternate schema file.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddDITContentRuleToAltSchemaFile()
         throws Exception
  {
    String ldif = toLdif(
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

    String ocName = "testadddcrtoaltschemafileoc";
    assertFalse(getSchema().hasObjectClass(ocName));
    assertSchemaFileExists("98-schema-test-dcr.ldif", false);

    runModify(argsNotPermissive(), ldif, System.err, SUCCESS);

    ObjectClass oc = getSchema().getObjectClass(ocName);
    assertFalse(oc.isPlaceHolder());

    DITContentRule dcr = getSchema().getDITContentRule(oc);
    assertNotNull(dcr);
    assertTrue(dcr.hasName("testadddcrtoaltschemafile"));

    assertSchemaFileExists("98-schema-test-dcr.ldif", true);
  }

  /**
   * Tests the behavior of the schema backend when attempting to remove an
   * existing DIT content rule and add it back in the same operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRemoveThenAddDITContentRule()
         throws Exception
  {
    String ldif = toLdif(
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

    String ocName = "testremovethenaddditcontentruleoc";
    assertFalse(getSchema().hasObjectClass(ocName));

    runModify(argsNotPermissive(), ldif, System.err, SUCCESS);

    ObjectClass oc = getSchema().getObjectClass(ocName);
    assertFalse(oc.isPlaceHolder());

    DITContentRule dcr = getSchema().getDITContentRule(oc);
    assertNotNull(dcr);
    assertTrue(dcr.hasName("testremovethenaddditcontentrule"));
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new DIT
   * content rule whose structural objectclass is not defined in the schema.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddDITContentRuleUndefinedOC()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: ditContentRules",
         "ditContentRules: ( xxxundefinedxxx-oid " +
              "NAME 'testAddDITContentRuleUndefinedOC' NOT description " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new DIT
   * content rule whose structural objectclass is not actually structural.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddDITContentRuleAuxiliaryOC()
         throws Exception
  {
    String ldif = toLdif(
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

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new DIT
   * content rule whose structural objectclass is OBSOLETE.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddDITContentRuleObsoleteOC()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses:  ( testaddditcontentruleobsoleteococ-oid " +
              "NAME 'testAddDITContentRuleObsoleteOCOC' OBSOLETE SUP top " +
              "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: ditContentRules",
         "ditContentRules: ( testaddditcontentruleobsoleteococ-oid " +
              "NAME 'testAddDITContentRuleObsoleteOC' NOT description " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new DIT
   * content rule whose structural objectclass is already referenced by an
   * existing DIT content rule.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddDITContentRuleConflictingOC()
         throws Exception
  {
    String ldif = toLdif(
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

    runModify(argsNotPermissive(), ldif, ATTRIBUTE_OR_VALUE_EXISTS);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new DIT
   * content rule with an undefined auxiliary objectclass.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddDITContentRuleUndefinedAuxOC()
         throws Exception
  {
    String ldif = toLdif(
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

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new DIT
   * content rule with an undefined auxiliary objectclass when multiple
   * auxiliary classes were provided.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddDITContentRuleMultipleUndefinedAuxOC()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses:  ( testaddditcontentrulemultundefinedauxococ-oid " +
              "NAME 'testAddDITContentRuleMultUndefinedAuxOCOC' SUP top " +
              "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: ditContentRules",
         "ditContentRules: ( testaddditcontentrulemultundefinedauxococ-oid " +
              "NAME 'testAddDITContentRuleMultUndefinedAuxOC' " +
              "AUX ( posixAccount $ xxxundefinedxxx ) " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new DIT
   * content rule with an auxiliary objectclass that is not auxiliary.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddDITContentRuleAuxOCNotAux()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses:  ( testaddditcontentruleauxocnotauxoc-oid " +
              "NAME 'testAddDITContentRuleAuxOCNotAuxOC' SUP top " +
              "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: ditContentRules",
         "ditContentRules: ( testaddditcontentruleauxocnotaux-oid " +
              "NAME 'testAddDITContentRuleAuxOCNotAuxOC' " +
              "AUX person X-ORIGIN 'SchemaBackendTestCase' )");

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new DIT
   * content rule with an auxiliary objectclass that is not auxiliary when
   * multiple auxiliary classes were provided.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddDITContentRuleMultipleAuxOCNotAux()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses:  ( testaddditcontentrulemultipleauxocnotauxoc-oid " +
              "NAME 'testAddDITContentRuleMultipleAuxOCNotAuxOC' SUP top " +
              "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: ditContentRules",
         "ditContentRules: ( testaddditcontentrulemultipleauxocnotaux-oid " +
              "NAME 'testAddDITContentRuleMultipleAuxOCNotAuxOC' " +
              "AUX ( posixAccount $ person ) " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new DIT
   * content rule with an auxiliary objectclass that is OBSOLETE.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddDITContentRuleObsoleteAuxOC()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses:  ( testaddditcontentruleobsoleteauxstructural-oid " +
              "NAME 'testAddDITContentRuleObsoleteAuxOCStructural' SUP top " +
              "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase' )",
         "objectClasses:  ( testaddditcontentruleobsoleteauxauxiliary-oid " +
              "NAME 'testAddDITContentRuleObsoleteAuxOCAuxiliary' OBSOLETE " +
              "SUP top AUXILIARY MUST cn X-ORIGIN 'SchemaBackendTestCase' )",
         "-",
         "add: ditContentRules",
         "ditContentRules: ( testaddditcontentruleobsoleteauxstructural-oid " +
              "NAME 'testAddDITContentRuleObsoleteAuxOC' " +
              "AUX testAddDITContentRuleObsoleteAuxOCAuxiliary " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new DIT
   * content rule that references an undefined required attribute type.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddDITContentRuleUndefinedReqAT()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses:  ( testaddditcontentruleundefinedreqatoc-oid " +
              "NAME 'testAddDITContentRuleUndefinedReqATOC' SUP top " +
              "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: ditContentRules",
         "ditContentRules: ( testaddditcontentruleundefinedreqatoc-oid " +
              "NAME 'testAddDITContentRuleUndefinedReqAT' " +
              "MUST xxxundefinedxxx X-ORIGIN 'SchemaBackendTestCase' )");

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new DIT
   * content rule that references an undefined required attribute type when
   * multiple required attributes were provided.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddDITContentRuleMultipleUndefinedReqAT()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses:  ( testaddditcontentrulemultundefinedreqatoc-oid " +
              "NAME 'testAddDITContentRuleMultUndefinedReqATOC' SUP top " +
              "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: ditContentRules",
         "ditContentRules: ( testaddditcontentrulemultundefinedreqatoc-oid " +
              "NAME 'testAddDITContentMultRuleUndefinedReqAT' " +
              "MUST ( cn $ xxxundefinedxxx ) " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new DIT
   * content rule that references an undefined optional attribute type.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddDITContentRuleUndefinedOptAT()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses:  ( testaddditcontentruleundefinedoptatoc-oid " +
              "NAME 'testAddDITContentRuleUndefinedOptATOC' SUP top " +
              "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: ditContentRules",
         "ditContentRules: ( testaddditcontentruleundefinedoptatoc-oid " +
              "NAME 'testAddDITContentRuleUndefinedOptAT' " +
              "MAY xxxundefinedxxx X-ORIGIN 'SchemaBackendTestCase' )");

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new DIT
   * content rule that references an undefined optional attribute type when
   * multiple optional attributes were provided.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddDITContentRuleMultipleUndefinedOptAT()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses:  ( testaddditcontentrulemultundefinedoptatoc-oid " +
              "NAME 'testAddDITContentRuleMultUndefinedOptATOC' SUP top " +
              "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: ditContentRules",
         "ditContentRules: ( testaddditcontentrulemultundefinedoptatoc-oid " +
              "NAME 'testAddDITContentRuleMultUndefinedOptAT' " +
              "MAY ( cn $ xxxundefinedxxx ) " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new DIT
   * content rule that references an undefined prohibited attribute type.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddDITContentRuleUndefinedNotAT()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses:  ( testaddditcontentruleundefinednotatoc-oid " +
              "NAME 'testAddDITContentRuleUndefinedNotATOC' SUP top " +
              "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: ditContentRules",
         "ditContentRules: ( testaddditcontentruleundefinednotatoc-oid " +
              "NAME 'testAddDITContentRuleUndefinedNotAT' " +
              "NOT xxxundefinedxxx X-ORIGIN 'SchemaBackendTestCase' )");

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new DIT
   * content rule that references an undefined prohibited attribute type when
   * multiple prohibited attributes were provided.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddDITContentRuleMultipleUndefinedNotAT()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses:  ( testaddditcontentrulemultundefinednotatoc-oid " +
              "NAME 'testAddDITContentRuleMultUndefinedNotATOC' SUP top " +
              "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: ditContentRules",
         "ditContentRules: ( testaddditcontentrulemultundefinednotatoc-oid " +
              "NAME 'testAddDITContentRuleMultUndefinedNotAT' " +
              "NOT ( description $ xxxundefinedxxx ) " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new DIT
   * content rule that prohibits an attribute type that is required by the
   * structural object class.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddDITContentRuleProhibitRequiredStructuralAttribute()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses:  ( testadddcrprohibitreqstructuralatoc-oid " +
              "NAME 'testAddDCRProhibitReqStructuralATOC' SUP top " +
              "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: ditContentRules",
         "ditContentRules: ( testadddcrprohibitreqstructuralatoc-oid " +
              "NAME 'testAddDCRProhibitReqStructuralAT' " +
              "NOT cn X-ORIGIN 'SchemaBackendTestCase' )");

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new DIT
   * content rule that prohibits an attribute type that is required by an
   * associated auxiliary object class.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddDITContentRuleProhibitRequiredAuxiliaryAttribute()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses:  ( testadddcrprohibitreqauxiliaryatoc-oid " +
              "NAME 'testAddDCRProhibitReqAuxiliaryATOC' SUP top " +
              "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: ditContentRules",
         "ditContentRules: ( testadddcrprohibitreqauxiliaryatoc-oid " +
              "NAME 'testAddDCRProhibitReqAuxiliaryAT' AUX posixAccount " +
              "NOT uid X-ORIGIN 'SchemaBackendTestCase' )");

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new DIT
   * content rule with an OBSOLETE required attribute type.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddDITContentRuleObsoleteRequiredAttributeType()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: attributeTypes",
         "attributeTypes: ( testadddcrobsoletereqatat-oid " +
              "NAME 'testAddDCRObsoleteReqATAT' OBSOLETE " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "-",
         "add: objectClasses",
         "objectClasses:  ( testadddcrobsoletereqatoc-oid " +
              "NAME 'testAddDCRObsoleteReqATOC' SUP top " +
              "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: ditContentRules",
         "ditContentRules: ( testadddcrobsoletereqatoc-oid " +
              "NAME 'testAddDCRObsoleteReqATDCR' " +
              "MUST testAddDCRObsoleteReqATAT " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new DIT
   * content rule with an OBSOLETE optional attribute type.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddDITContentRuleObsoleteOptionalAttributeType()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: attributeTypes",
         "attributeTypes: ( testadddcrobsoleteoptatat-oid " +
              "NAME 'testAddDCRObsoleteOptATAT' OBSOLETE " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "-",
         "add: objectClasses",
         "objectClasses:  ( testadddcrobsoleteoptatoc-oid " +
              "NAME 'testAddDCRObsoleteOptATOC' SUP top " +
              "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: ditContentRules",
         "ditContentRules: ( testadddcrobsoleteoptatoc-oid " +
              "NAME 'testAddDCRObsoleteOptATDCR' " +
              "MAY testAddDCRObsoleteOptATAT " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new DIT
   * content rule with an OBSOLETE prohibited attribute type.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddDITContentRuleObsoleteProhibitedAttributeType()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: attributeTypes",
         "attributeTypes: ( testadddcrobsoletenotatat-oid " +
              "NAME 'testAddDCRObsoleteNotATAT' OBSOLETE " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "-",
         "add: objectClasses",
         "objectClasses:  ( testadddcrobsoletenotatoc-oid " +
              "NAME 'testAddDCRObsoleteNotATOC' SUP top " +
              "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: ditContentRules",
         "ditContentRules: ( testadddcrobsoletenotatoc-oid " +
              "NAME 'testAddDCRObsoleteNotATDCR' " +
              "NOT testAddDCRObsoleteNotATAT " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
  }

  /**
   * Tests the behavior of the schema backend when attempting to remove an
   * existing DIT content rule.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRemoveDITContentRuleSuccessful()
         throws Exception
  {
    String ldif = toLdif(
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

    String ocName = "testremoveditcontentrulesuccessfuloc";
    assertFalse(getSchema().hasObjectClass(ocName));

    runModify(argsNotPermissive(), ldif, System.err, SUCCESS);

    ObjectClass oc = getSchema().getObjectClass(ocName);
    assertFalse(oc.isPlaceHolder());

    DITContentRule dcr = getSchema().getDITContentRule(oc);
    assertNull(dcr);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * DIT structure rule.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddDITStructureRuleSuccessful() throws Exception
  {
    String ldif = toLdif(
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

    int ruleID = 999001;
    assertSchemaHasDITStructureRule(ruleID, false);

    try
    {
      runModify(argsNotPermissive(), ldif, System.err, SUCCESS);
      assertSchemaHasDITStructureRule(ruleID, true);
    }
    finally
    {
      // delete in reverse order
      String ldif2 = toLdif(
           "dn: cn=schema",
           "changetype: modify",
           "delete: ditStructureRules",
           "ditStructureRules: ( 999001 " +
                "NAME 'testAddDITStructureRuleSuccessful' " +
                "FORM testAddDITStructureRuleSuccessfulNF " +
                "X-ORIGIN 'SchemaBackendTestCase' )",
          "-",
          "delete: nameForms",
          "nameForms: ( testaddditstructurerulesuccessfulnf-oid " +
               "NAME 'testAddDITStructureRuleSuccessfulNF' " +
               "OC testAddDITStructureRuleSuccessfulOC MUST cn " +
               "X-ORIGIN 'SchemaBackendTestCase' )",
           "-",
           "delete: objectClasses",
           "objectClasses:  ( testaddditstructurerulesuccessfuloc-oid " +
                "NAME 'testAddDITStructureRuleSuccessfulOC' SUP top " +
                "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase')");
      runModify(argsNotPermissive(), ldif2, System.err, SUCCESS);
      assertSchemaHasDITStructureRule(ruleID, false);
    }
  }

  @Test
  public void testAddDITStructureRuleSupSuccessful() throws Exception
  {
    String ldif = toLdif(
            "dn: cn=schema",
            "changetype: modify",
            "add: nameForms",
            "nameForms: ( 1.3.6.1.4.1.56521.999.8.1.7 NAME 'commonNameForm' DESC 'Name Form for a commonName orgRole structure' OC organizationalRole MUST cn )",
            "-",
            "add: ditStructureRules",
            "dITStructureRules: ( 150 NAME 'commonNameStructureRule'  FORM commonNameForm )",
            "dITStructureRules: ( 151 NAME 'commonNameStructureRule2' FORM commonNameForm )",
            "dITStructureRules: ( 152 NAME 'commonNameStructureRule3' FORM commonNameForm SUP ( 150 $ 151 ) )"

            );

    int ruleID = 152;
    assertSchemaHasDITStructureRule(ruleID, false);

    try
    {
      runModify(argsNotPermissive(), ldif, System.err, SUCCESS);
      assertSchemaHasDITStructureRule(ruleID, true);
    }
    finally
    {
      // delete in reverse order
      String ldif2 = toLdif(
              "dn: cn=schema",
              "changetype: modify",
              "delete: ditStructureRules",
              "dITStructureRules: ( 152 NAME 'commonNameStructureRule3' FORM commonNameForm SUP ( 150 151 ) )",
              "dITStructureRules: ( 150 NAME 'commonNameStructureRule'  FORM commonNameForm )",
              "dITStructureRules: ( 151 NAME 'commonNameStructureRule2' FORM commonNameForm )",
              "-",
              "delete: nameForms",
              "nameForms: ( 1.3.6.1.4.1.56521.999.8.1.7 NAME 'commonNameForm' DESC 'Name Form for a commonName orgRole structure' OC organizationalRole MUST cn )"
              );
      runModify(argsNotPermissive(), ldif2, System.err, SUCCESS);
      assertSchemaHasDITStructureRule(ruleID, false);
    }
  }

  @Test
  public void testAddDITStructureRuleSupSuccessful2() throws Exception
  {
    String ldif = toLdif(
            "dn: cn=schema",
            "changetype: modify",
            "add: nameForms",
            "nameForms: ( 1.3.6.1.4.1.56521.999.8.1.7 NAME 'commonNameForm' DESC 'Name Form for a commonName orgRole structure' OC organizationalRole MUST cn )",
            "-",
            "add: ditStructureRules",
            "dITStructureRules: ( 150 NAME 'commonNameStructureRule'  FORM commonNameForm )",
            "dITStructureRules: ( 151 NAME 'commonNameStructureRule2' FORM commonNameForm )",
            "dITStructureRules: ( 152 NAME 'commonNameStructureRule3' FORM commonNameForm )",
            "dITStructureRules: ( 153 NAME 'commonNameStructureRule4' FORM commonNameForm SUP ( 150 151 152 ) )"

    );

    int ruleID = 153;
    assertSchemaHasDITStructureRule(ruleID, false);

    try
    {
      runModify(argsNotPermissive(), ldif, System.err, SUCCESS);
      assertSchemaHasDITStructureRule(ruleID, true);
    }
    finally
    {
      // delete in reverse order
      String ldif2 = toLdif(
              "dn: cn=schema",
              "changetype: modify",
              "delete: ditStructureRules",
              "dITStructureRules: ( 153 NAME 'commonNameStructureRule4' FORM commonNameForm SUP ( 150 $ 151 $ 152 ) )",
              "dITStructureRules: ( 150 NAME 'commonNameStructureRule'  FORM commonNameForm )",
              "dITStructureRules: ( 151 NAME 'commonNameStructureRule2' FORM commonNameForm )",
              "dITStructureRules: ( 152 NAME 'commonNameStructureRule3' FORM commonNameForm )",
              "-",
              "delete: nameForms",
              "nameForms: ( 1.3.6.1.4.1.56521.999.8.1.7 NAME 'commonNameForm' DESC 'Name Form for a commonName orgRole structure' OC organizationalRole MUST cn )"
      );
      runModify(argsNotPermissive(), ldif2, System.err, SUCCESS);
      assertSchemaHasDITStructureRule(ruleID, false);
    }
  }
  private void assertSchemaHasDITStructureRule(int ruleID, boolean expected)
  {
    boolean hasDITStructureRule = getSchema().hasDITStructureRule(ruleID);
    assertEquals(hasDITStructureRule, expected, "Expected to find a DITStructureRule with ruleID " + ruleID);
  }

  /**
   * Tests the behavior of the schema backend when attempting to replace an
   * existing DIT structure rule definition.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testReplaceDITStructureRuleSuccessful()
         throws Exception
  {
    String ldif = toLdif(
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

    int ruleID = 999002;
    assertSchemaHasDITStructureRule(ruleID, false);

    runModify(argsPermissive(), ldif, System.err, SUCCESS);
    assertSchemaHasDITStructureRule(ruleID, true);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * DIT structure rule to an alternate schema file.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddDITStructureRuleToAltSchemaFile()
         throws Exception
  {
    String ldif = toLdif(
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

    int ruleID = 999010;
    assertSchemaHasDITStructureRule(ruleID, false);
    assertSchemaFileExists("98-schema-test-dsr.ldif", false);

    runModify(argsNotPermissive(), ldif, System.err, SUCCESS);

    assertSchemaHasDITStructureRule(ruleID, true);
    assertSchemaFileExists("98-schema-test-dsr.ldif", true);
  }

  /**
   * Tests the behavior of the schema backend when attempting to remove an
   * existing DIT structure rule definition and add it back in the same
   * operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRemoveAndAddDITStructureRuleSuccessful()
         throws Exception
  {
    String ldif = toLdif(
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

    int ruleID = 999003;
    assertSchemaHasDITStructureRule(ruleID, false);

    runModify(argsNotPermissive(), ldif, System.err, SUCCESS);
    assertSchemaHasDITStructureRule(ruleID, true);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * DIT structure rule with an undefined name form.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddDITStructureRuleUndefinedNameForm()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: ditStructureRules",
         "ditStructureRules: ( 999004 " +
              "NAME 'testAddDITStructureRuleUndefinedNameForm' " +
              "FORM xxxundefinedxxx " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    int ruleID = 999004;
    assertSchemaHasDITStructureRule(ruleID, false);

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
    assertSchemaHasDITStructureRule(ruleID, false);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * DIT structure rule that references an undefined superior rule.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddDITStructureRuleUndefinedSuperior()
         throws Exception
  {
    String ldif = toLdif(
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

    int ruleID = 999005;
    assertSchemaHasDITStructureRule(ruleID, false);

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
    assertSchemaHasDITStructureRule(ruleID, false);
  }

  /**
   * Tests the addition of a new DITContentRule with a conflicting rule identifier.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test
  public void testAddDITStructureRuleConflictingRuleID() throws Exception
  {
    String ldif = toLdif(
        "dn: cn=schema",
        "changetype: modify",
        "add: nameForms",
        "nameForms: ( 1.3.6.1.1.10.15.100 NAME 'domainNameForm' OC domain MUST ( dc ) )",
        "-",
        "add: dITStructureRules",
        "dITStructureRules: ( 1 NAME 'dummyStructureRule' FORM domainNameForm )");
    runModify(argsNotPermissive(), ldif, ATTRIBUTE_OR_VALUE_EXISTS);
  }

  /**
   * Tests the addition of a new DITContentRule with a conflicting rule identifier and permissive control.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test
  public void testAddDITStructureRuleConflictingRuleIDWithPermissiveControl() throws Exception
  {

    String ldif = toLdif(
        "dn: cn=schema",
        "changetype: modify",
        "add: nameForms",
        "nameForms: ( 1.3.6.1.1.10.15.100 NAME 'domainNameForm' OC domain MUST ( dc ) )",
        "-",
        "add: dITStructureRules",
        "dITStructureRules: ( 1 NAME 'dummyStructureRule' FORM domainNameForm )");
    runModify(argsPermissive(), ldif, SUCCESS);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * DIT structure rule that references a name form which is OBSOLETE.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddDITStructureRuleObsoleteNameForm()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses:  ( testaddditstructureruleobsoletenameformoc-oid " +
              "NAME 'testAddDITStructureRuleObsoleteNameFormOC' SUP top " +
              "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: nameForms",
         "nameForms: ( testaddditstructureruleobsoletenameformnf-oid " +
              "NAME 'testAddDITStructureRuleObsoleteNameFormNF' OBSOLETE " +
              "OC testAddDITStructureRuleObsoleteNameFormOC MUST cn " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "-",
         "add: ditStructureRules",
         "ditStructureRules: ( 999011 " +
              "NAME 'testAddDITStructureRuleObsoleteNameForm' " +
              "FORM testAddDITStructureRuleObsoleteNameFormNF " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * DIT structure rule that references a superior rule which is OBSOLETE.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddDITStructureRuleObsoleteSuperiorRule()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses:  ( testaddditstructureruleobsoletesuperioroc1-oid " +
              "NAME 'testAddDITStructureRuleObsoleteSuperiorOC1' SUP top " +
              "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "objectClasses:  ( testaddditstructureruleobsoletesuperioroc2-oid " +
              "NAME 'testAddDITStructureRuleObsoleteSuperiorOC2' SUP top " +
              "STRUCTURAL MUST cn X-ORIGIN 'SchemaBackendTestCase')",
         "-",
         "add: nameForms",
         "nameForms: ( testaddditstructureruleobsoletesuperiornf1-oid " +
              "NAME 'testAddDITStructureRuleObsoleteSuperiorNF1' " +
              "OC testAddDITStructureRuleObsoleteSuperiorOC1 MUST cn " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "nameForms: ( testaddditstructureruleobsoletesuperiornf2-oid " +
              "NAME 'testAddDITStructureRuleObsoleteSuperiorNF2' " +
              "OC testAddDITStructureRuleObsoleteSuperiorOC2 MUST cn " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "-",
         "add: ditStructureRules",
         "ditStructureRules: ( 999012 " +
              "NAME 'testAddDITStructureRuleObsoleteSuperiorSup' OBSOLETE " +
              "FORM testAddDITStructureRuleObsoleteSuperiorNF1 " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "ditStructureRules: ( 999013 " +
              "NAME 'testAddDITStructureRuleObsoleteSuperiorSub' " +
              "FORM testAddDITStructureRuleObsoleteSuperiorNF2 SUP 999012 " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
  }

  /**
   * Tests the behavior of the schema backend when attempting to remove an
   * existing DIT structure rule definition.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRemoveDITStructureRuleSuccessful()
         throws Exception
  {
    String ldif = toLdif(
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

    int ruleID = 999006;
    assertSchemaHasDITStructureRule(ruleID, false);

    runModify(argsNotPermissive(), ldif, System.err, SUCCESS);
    assertSchemaHasDITStructureRule(ruleID, false);
  }

  /**
   * Tests the behavior of the schema backend when attempting to remove an
   * existing DIT structure rule definition which is the superior rule for
   * another DIT structure rule.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRemoveSuperiorDITStructureRule()
         throws Exception
  {
    String ldif = toLdif(
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

    int ruleID = 999007;
    assertSchemaHasDITStructureRule(ruleID, false);

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
    assertSchemaHasDITStructureRule(ruleID, true);

    ldif = toLdif(
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

    runModify(argsNotPermissive(), ldif, System.err, SUCCESS);
    assertSchemaHasDITStructureRule(ruleID, false);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * matching rule use that doesn't already exist.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddMatchingRuleUseSuccessful() throws Exception
  {
    MatchingRule matchingRule =
        registerNewMatchingRule("testAddMRUSuccessfulMatch", "1.3.6.1.4.1.26027.1.999.10", false);

    try
    {
      String ldif = toLdif(
          "dn: cn=schema",
          "changetype: modify",
          "add: matchingRuleUse",
          "matchingRuleUse: ( 1.3.6.1.4.1.26027.1.999.10 " +
              "NAME 'testAddMRUSuccessful' APPLIES cn " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

      assertSchemaDoesNotHaveMatchingRuleUse(matchingRule);

      runModify(argsNotPermissive(), ldif, System.err, SUCCESS);

      assertMatchingRuleUseExistsWithName(matchingRule, "testaddmrusuccessful");
    }
    finally
    {
      deregisterMatchingRuleUse(matchingRule);
      deregisterMatchingRule(matchingRule);
    }
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * matching rule to an alternate schema file.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddMatchingRuleUseToAltSchemaFile() throws Exception
  {
    MatchingRule matchingRule =
        registerNewMatchingRule("testAddMRUToAltSchemaFileMatch", "1.3.6.1.4.1.26027.1.999.18", false);

    try
    {
      String ldif = toLdif(
          "dn: cn=schema",
          "changetype: modify",
          "add: matchingRuleUse",
          "matchingRuleUse: ( 1.3.6.1.4.1.26027.1.999.18 " +
              "NAME 'testAddMRUToAltSchemaFile' APPLIES cn " +
              "X-SCHEMA-FILE '98-schema-test-mru.ldif' " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

      assertSchemaDoesNotHaveMatchingRuleUse(matchingRule);
      assertSchemaFileExists("98-schema-test-mru.ldif", false);

      runModify(argsNotPermissive(), ldif, System.err, SUCCESS);

      assertMatchingRuleUseExistsWithName(matchingRule, "testaddmrutoaltschemafile");
      assertSchemaFileExists("98-schema-test-mru.ldif", true);
    }
    finally
    {
      deregisterMatchingRuleUse(matchingRule);
      deregisterMatchingRule(matchingRule);
    }
  }

  private File assertSchemaFileExists(String schemaFileName, boolean expectExists)
  {
    File schemaFile = new File(DirectoryServer.getEnvironmentConfig().getSchemaDirectory(), schemaFileName);
    assertEquals(schemaFile.exists(), expectExists);
    return schemaFile;
  }

  /**
   * Tests the behavior of the schema backend when attempting to replace an
   * existing matching rule use.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testReplaceMatchingRuleUseSuccessful() throws Exception
  {
    MatchingRule matchingRule =
        registerNewMatchingRule("testReplaceMRUSuccessfulMatch", "1.3.6.1.4.1.26027.1.999.11", false);

    try
    {
      String ldif = toLdif(
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

      assertSchemaDoesNotHaveMatchingRuleUse(matchingRule);

      runModify(argsPermissive(), ldif, System.err, SUCCESS);

      assertMatchingRuleUseExistsWithName(matchingRule, "testreplacemrusuccessful");
    }
    finally
    {
      deregisterMatchingRuleUse(matchingRule);
      deregisterMatchingRule(matchingRule);
    }
  }

  /**
   * Tests the behavior of the schema backend when attempting to remove and
   * re-add an existing matching rule use in the same operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRemoveAndAddMatchingRuleUse() throws Exception
  {
    MatchingRule matchingRule =
        registerNewMatchingRule("testRemoveAndAddMRUMatch", "1.3.6.1.4.1.26027.1.999.12", false);

    try
    {
      String ldif = toLdif(
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

      assertSchemaDoesNotHaveMatchingRuleUse(matchingRule);

      runModify(argsNotPermissive(), ldif, System.err, SUCCESS);

      assertMatchingRuleUseExistsWithName(matchingRule, "testremoveandaddmru");
    }
    finally
    {
      deregisterMatchingRuleUse(matchingRule);
      deregisterMatchingRule(matchingRule);
    }
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a matching
   * rule use that references the same matching rule as another matching rule
   * use.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddMatchingRuleUseMRConflict() throws Exception
  {
    MatchingRule matchingRule =
        registerNewMatchingRule("testAddMRUMRConflictMatch", "1.3.6.1.4.1.26027.1.999.14", false);

    try
    {
      String ldif = toLdif(
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

      assertSchemaDoesNotHaveMatchingRuleUse(matchingRule);

      runModify(argsNotPermissive(), ldif, ATTRIBUTE_OR_VALUE_EXISTS);

      assertMatchingRuleUseExistsWithName(matchingRule, "testaddmrumrconflict");
    }
    finally
    {
      deregisterMatchingRuleUse(matchingRule);
      deregisterMatchingRule(matchingRule);
    }
  }

  private void assertMatchingRuleUseExistsWithName(MatchingRule matchingRule, String mruName)
  {
    MatchingRuleUse mru = getSchema().getMatchingRuleUse(matchingRule);
    assertNotNull(mru);
    assertTrue(mru.hasName(mruName));
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * matching rule use that references an undefined matching rule.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddMatchingRuleUseMRUndefined()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: matchingRuleUse",
         "matchingRuleUse: ( 1.3.6.1.4.1.26027.1.999.15 " +
              "NAME 'testAddMRUMRUndefined' APPLIES cn " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * matching rule use that references an undefined attribute type.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddMatchingRuleUseAttributeTypeUndefined() throws Exception
  {
    MatchingRule matchingRule =
        registerNewMatchingRule("testAddMRUATUndefinedMatch", "1.3.6.1.4.1.26027.1.999.16", false);

    try
    {
      String ldif = toLdif(
          "dn: cn=schema",
          "changetype: modify",
          "add: matchingRuleUse",
          "matchingRuleUse: ( 1.3.6.1.4.1.26027.1.999.16 " +
              "NAME 'testAddMatchingRuleUseATUndefined' " +
              "APPLIES xxxundefinedxxx " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

      assertSchemaDoesNotHaveMatchingRuleUse(matchingRule);

      runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
    }
    finally
    {
      deregisterMatchingRule(matchingRule);
    }
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * matching rule use that references an undefined attribute type.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddMatchingRuleUseAttributeTypeMultipleUndefined() throws Exception
  {
    MatchingRule matchingRule =
        registerNewMatchingRule("testAddMRUATMultipleUndefinedMatch", "1.3.6.1.4.1.26027.1.999.19", false);

    try
    {
      String ldif = toLdif(
          "dn: cn=schema",
          "changetype: modify",
          "add: matchingRuleUse",
          "matchingRuleUse: ( 1.3.6.1.4.1.26027.1.999.19 " +
              "NAME 'testAddMatchingRuleUseATMultipleUndefined' " +
              "APPLIES ( cn $ xxxundefinedxxx ) " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

      assertSchemaDoesNotHaveMatchingRuleUse(matchingRule);

      runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
    }
    finally
    {
      deregisterMatchingRule(matchingRule);
    }
  }

  private void assertSchemaDoesNotHaveMatchingRuleUse(MatchingRule matchingRule)
  {
    for (MatchingRuleUse matchingRuleUse : getSchema().getMatchingRuleUses())
    {
      assertFalse(matchingRuleUse.getMatchingRule().equals(matchingRule));
    }
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * matching rule whose matching rule is OBSOLETE.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddMatchingRuleUseObsoleteMatchingRule() throws Exception
  {
    MatchingRule matchingRule =
        registerNewMatchingRule("testAddMRUObsoleteMRMatch", "1.3.6.1.4.1.26027.1.999.21", true);

    try
    {
      String ldif = toLdif(
          "dn: cn=schema",
          "changetype: modify",
          "add: matchingRuleUse",
          "matchingRuleUse: ( 1.3.6.1.4.1.26027.1.999.21 " +
              "NAME 'testAddMatchingRuleUseObsoleteMatchingRule' " +
          "APPLIES cn X-ORIGIN 'SchemaBackendTestCase' )");

      assertSchemaDoesNotHaveMatchingRuleUse(matchingRule);

      runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
    }
    finally
    {
      deregisterMatchingRule(matchingRule);
    }
  }

  /**
   * Tests the behavior of the schema backend when attempting to add a new
   * matching rule with an associated attribute type that is marked OBSOLETE.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddMatchingRuleUseObsoleteAttributeType() throws Exception
  {
    MatchingRule matchingRule =
        registerNewMatchingRule("testAddMRUObsoleteATMatch", "1.3.6.1.4.1.26027.1.999.22", false);

    try
    {
      String ldif = toLdif(
          "dn: cn=schema",
          "changetype: modify",
          "add: attributeTypes",
          "attributeTypes: ( testaddmruobsoleteat-oid " +
              "NAME 'testAddMRUObsoleteAT' OBSOLETE )",
          "-",
          "add: matchingRuleUse",
          "matchingRuleUse: ( 1.3.6.1.4.1.26027.1.999.22 " +
              "NAME 'testAddMatchingRuleUseObsoleteAttributeType' " +
              "APPLIES testAddMRUObsoleteAT " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

      assertSchemaDoesNotHaveMatchingRuleUse(matchingRule);

      runModify(argsNotPermissive(), ldif, CONSTRAINT_VIOLATION);
    }
    finally
    {
      deregisterAttributeType("testaddmruobsoleteat-oid");
      deregisterMatchingRule(matchingRule);
    }
  }

  private MatchingRule registerNewMatchingRule(final String name, final String oid, final boolean obsolete)
      throws DirectoryException
  {
    updateSchema(new SchemaUpdater()
    {

      @Override
      public void update(SchemaBuilder builder) throws DirectoryException
      {
        builder
          .buildMatchingRule(oid)
          .syntaxOID(SchemaConstants.SYNTAX_DIRECTORY_STRING_OID)
          .names(name)
          .implementation(new SchemaTestMatchingRuleImpl())
          .obsolete(obsolete)
          .addToSchema();
      }
    });
    return getSchema().getMatchingRule(oid);
  }

  private void runModify(String[] args, String ldifContent, ResultCode expectedRC)
  {
    runModify(args, ldifContent, nullPrintStream(), expectedRC);
  }

  private void runModify(String[] args, String ldifContent, PrintStream stderr, ResultCode expectedRC)
  {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    int rc = runModify(args, ldifContent, new PrintStream(output), stderr);
    assertEquals(rc, expectedRC.intValue(), output.toString());
  }

  private int runModify(String[] args, String ldifContent, PrintStream stdout, PrintStream stderr)
  {
    final InputStream stdin = System.in;
    try
    {
      System.setIn(new ByteArrayInputStream(ldifContent.getBytes()));
      return LDAPModify.run(stdout, stderr, args);
    }
    finally
    {
      System.setIn(stdin);
    }
  }

  private String toLdif(Object... ldifLines)
  {
    return Utils.joinAsString("\n", ldifLines);
  }

  /**
   * Tests the behavior of the schema backend when attempting to remove an
   * existing matching rule use.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRemoveMatchingRuleUseSuccessful() throws Exception
  {
    MatchingRule matchingRule =
        registerNewMatchingRule("testRemoveMRUSuccessfulMatch", "1.3.6.1.4.1.26027.1.999.13", false);

    try
    {
      String ldif = toLdif(
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

      assertSchemaDoesNotHaveMatchingRuleUse(matchingRule);
      runModify(argsNotPermissive(), ldif, System.err, SUCCESS);
      assertSchemaDoesNotHaveMatchingRuleUse(matchingRule);
    }
    finally
    {
      deregisterMatchingRule(matchingRule);
    }
  }

  /**
   * Tests the behavior of the schema backend when attempting to add another
   * value to attributeTypes that matches an existing one using the correct
   * matching rules.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAttributeTypesMatchingRule()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: attributeTypes",
         "attributeTypes: ( testattributetypesmatchingrule-oid " +
              "NAME 'testAttributeTypesMatchingRule' " +
              "EQUALITY booleanMatch SYNTAX 1.3.6.1.4.1.1466.115.121.1.7 " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "",
         "dn: cn=schema",
         "changetype: modify",
         "add: attributeTypes",
         "attributeTypes: ( testattributetypesmatchingrule-oid " +
              "NAME 'testAttributeTypesMatchingRule' " +
              "EQUALITY caseIgnoreMatch SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    String attrName = "testattributetypesmatchingrule";
    assertFalse(getSchema().hasAttributeType(attrName));

    runModify(argsNotPermissive(), ldif, System.err, ATTRIBUTE_OR_VALUE_EXISTS);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add another
   * value to objectClasses that matches an existing one using the correct
   * matching rules.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testObjectClassesMatchingRule()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses: ( testobjectclassesmatchingrule-oid " +
              "NAME 'testObjectClassesMatchingRule' " +
              "SUP top STRUCTURAL MAY cn " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "",
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses: ( testobjectclassesmatchingrule-oid " +
              "NAME 'testObjectClassesMatchingRule' " +
              "SUP top AUXILIARY MAY mail " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    String objectClassName = "testobjectclassesmatchingrule";
    assertFalse(getSchema().hasObjectClass(objectClassName));

    runModify(argsNotPermissive(), ldif, System.err, ATTRIBUTE_OR_VALUE_EXISTS);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add another
   * value to nameForms that matches an existing one using the correct
   * matching rules.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testNameFormsMatchingRule()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses: ( testnameformsmatchingruleoc-oid " +
              "NAME 'testNameFormsMatchingRuleOC' " +
              "SUP top STRUCTURAL MAY ( cn $ mail ) " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "",
         "dn: cn=schema",
         "changetype: modify",
         "add: nameForms",
         "nameForms: ( testnameformsmatchingrule-oid " +
              "NAME 'testNameFormsMatchingRule' " +
              "OC testNameFormsMatchingRuleOC MUST mail " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "",
         "dn: cn=schema",
         "changetype: modify",
         "add: nameForms",
         "nameForms: ( testnameformsmatchingrule-oid " +
              "NAME 'testNameFormsMatchingRule' " +
              "OC testNameFormsMatchingRuleOC MUST cn " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    String nameFormName = "testnameformsmatchingrule";
    assertFalse(getSchema().hasNameForm(nameFormName));

    runModify(argsNotPermissive(), ldif, System.err, ATTRIBUTE_OR_VALUE_EXISTS);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add another
   * value to dITContentRules that matches an existing one using the correct
   * matching rules.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDitContentRulesMatchingRule()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses: ( testditcontentrulesmatchingrule-oid " +
              "NAME 'testDitContentRulesMatchingRuleOC' " +
              "SUP top STRUCTURAL MAY ( cn $ mail ) " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "",
         "dn: cn=schema",
         "changetype: modify",
         "add: dITContentRules",
         "dITContentRules: ( testditcontentrulesmatchingrule-oid " +
              "NAME 'testDitContentRulesMatchingRule' " +
              "MUST mail " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "",
         "dn: cn=schema",
         "changetype: modify",
         "add: dITContentRules",
         "dITContentRules: ( testditcontentrulesmatchingrule-oid " +
              "NAME 'testDitContentRulesMatchingRule' " +
              "MUST cn " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    String objectClassName = "testditcontentrulesmatchingruleoc";
    assertTrue(getSchema().getObjectClass(objectClassName).isPlaceHolder());

    runModify(argsNotPermissive(), ldif, System.err, ATTRIBUTE_OR_VALUE_EXISTS);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add another
   * value to dITStructureRules that matches an existing one using the correct
   * matching rules.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDitStructureRulesMatchingRule()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses: ( testditstructurerulesmatchingruleoc1-oid " +
              "NAME 'testDitStructureRulesMatchingRuleOC1' " +
              "SUP top STRUCTURAL MAY ( cn $ mail ) " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "",
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses: ( testditstructurerulesmatchingruleoc2-oid " +
              "NAME 'testDitStructureRulesMatchingRuleOC2' " +
              "SUP top STRUCTURAL MAY ( cn $ mail ) " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "",
         "dn: cn=schema",
         "changetype: modify",
         "add: nameForms",
         "nameForms: ( testditstructurerulesmatchingrulenf1-oid " +
              "NAME 'testDitStructureRulesMatchingRuleNF1' " +
              "OC testDitStructureRulesMatchingRuleOC1 MUST mail " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "",
         "dn: cn=schema",
         "changetype: modify",
         "add: nameForms",
         "nameForms: ( testditstructurerulesmatchingrulenf2-oid " +
              "NAME 'testDitStructureRulesMatchingRuleNF2' " +
              "OC testDitStructureRulesMatchingRuleOC2 MUST mail " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "",
         "dn: cn=schema",
         "changetype: modify",
         "add: dITStructureRules",
         "dITStructureRules: ( 999666 " +
              "NAME 'testDitStructureRulesMatchingRule' " +
              "FORM testDitStructureRulesMatchingRuleNF1 " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "",
         "dn: cn=schema",
         "changetype: modify",
         "add: dITStructureRules",
         "dITStructureRules: ( 999666 " +
              "NAME 'testDitStructureRulesMatchingRule' " +
              "FORM testDitStructureRulesMatchingRuleNF2 " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    String objectClassName = "testditcontentrulesmatchingruleoc1";
    assertTrue(getSchema().getObjectClass(objectClassName).isPlaceHolder());

    runModify(argsNotPermissive(), ldif, System.err, ATTRIBUTE_OR_VALUE_EXISTS);
  }

  /**
   * Tests the behavior of the schema backend when attempting to add another
   * value to matchingRuleUse that matches an existing one using the correct
   * matching rules.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testMatchingRuleUseMatchingRule()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: attributeTypes",
         "attributeTypes: ( testmatchingruleusematchingruleat1-oid " +
              "NAME 'testMatchingRuleUseMatchingRuleAT1' " +
              "EQUALITY caseIgnoreMatch SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "",
         "dn: cn=schema",
         "changetype: modify",
         "add: attributeTypes",
         "attributeTypes: ( testmatchingruleusematchingruleat2-oid " +
              "NAME 'testMatchingRuleUseMatchingRuleAT2' " +
              "EQUALITY caseIgnoreMatch SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "",
         "dn: cn=schema",
         "changetype: modify",
         "add: matchingRuleUse",
         "matchingRuleUse: ( booleanMatch " +
              "NAME 'testMatchingRuleUseMatchingRule' " +
              "APPLIES testMatchingRuleUseMatchingRuleAT1 " +
              "X-ORIGIN 'SchemaBackendTestCase' )",
         "",
         "dn: cn=schema",
         "changetype: modify",
         "add: matchingRuleUse",
         "matchingRuleUse: ( booleanMatch " +
              "NAME 'testMatchingRuleUseMatchingRule' " +
              "APPLIES testMatchingRuleUseMatchingRuleAT2 " +
              "X-ORIGIN 'SchemaBackendTestCase' )");

    String attrName = "testmatchingruleusematchingruleat1";

    assertFalse(getSchema().hasAttributeType(attrName));

    runModify(argsNotPermissive(), ldif, System.err, ATTRIBUTE_OR_VALUE_EXISTS);
  }

  /**
   * This test case covers the problem identified in issue #1318.  In that
   * issue, a problem arose if the following elements occurred in the following
   * order in a single modify request:
   *
   * <OL>
   *   <LI>Delete an existing object class</LI>
   *   <LI>Add a new attribute type</LI>
   *   <LI>Add a new object class (different from the one that was removed) that
   *       depends on the new attribute type</LI>
   * </OL>
   *
   * The problem was that in the process of removing the object class in step 1,
   * the server checks to see if the same object class is going to be re-added
   * again later.  It does that by looking through the remaining modifications
   * in the operation and for each modification that would add a new object
   * class we decode it to see if it has the same OID.  The process of decoding
   * that object class would fail because it depended on a new attribute type
   * that wasn't yet defined in the schema, and the server wasn't told to ignore
   * missing schema elements.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRemoveAndAddObjectClassIssue1318()
         throws Exception
  {
    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: objectClasses",
         "objectClasses: ( testissue1318oc1-oid NAME 'testIssue1381OC1')",
         "",
         "dn: cn=schema",
         "changetype: modify",
         "delete: objectClasses",
         "objectClasses: ( testissue1318oc1-oid NAME 'testIssue1381OC1' )",
         "-",
         "add: attributeTypes",
         "attributeTypes: ( testissue1318at-oid NAME 'testIssue1381AT' SUP name)",
         "-",
         "add: objectClasses",
         "objectClasses: ( testissue1318oc2-oid NAME 'testIssue1381OC2' " +
              "MUST testIssue1381AT )");

    runModify(argsNotPermissive(), ldif, System.err, SUCCESS);
  }

  /**
   * Tests to ensure that the schema subentry includes the lastmod attributes
   * and that the modifiersName and modifyTimestamp attributes get updated when
   * the schema is modified.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testLastModAttributes()
         throws Exception
  {
    Entry schemaEntry = DirectoryServer.getEntry(DN.valueOf("cn=schema"));
    assertNotNull(schemaEntry);

    AttributeType cnType = getCreatorsNameAttributeType();
    AttributeType ctType = getCreateTimestampAttributeType();
    AttributeType mnType = getModifiersNameAttributeType();
    AttributeType mtType = getModifyTimestampAttributeType();

    assertTrue(schemaEntry.hasAttribute(cnType));
    assertTrue(schemaEntry.hasAttribute(ctType));
    assertTrue(schemaEntry.hasAttribute(mnType));
    assertTrue(schemaEntry.hasAttribute(mtType));

    ByteString oldMTValue =
         schemaEntry.getAllAttributes(mtType).get(0).iterator().next();

    String ldif = toLdif(
         "dn: cn=schema",
         "changetype: modify",
         "add: attributeTypes",
         "attributeTypes: ( testlastmodattributes-oid " +
              "NAME 'testLastModAttributes' " +
              "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE " +
              "X-ORGIN 'SchemaBackendTestCase' )");

    // Sleep longer than the TimeThread delay to ensure the modifytimestamp
    // will be different.
    Thread.sleep(6000);
    runModify(argsNotPermissive(), ldif, System.err, SUCCESS);

    schemaEntry = DirectoryServer.getEntry(DN.valueOf("cn=schema"));
    assertNotNull(schemaEntry);
    assertTrue(schemaEntry.hasAttribute(cnType));
    assertTrue(schemaEntry.hasAttribute(ctType));
    assertTrue(schemaEntry.hasAttribute(mnType));
    assertTrue(schemaEntry.hasAttribute(mtType));

    ByteString newMTValue =
         schemaEntry.getAllAttributes(mtType).get(0).iterator().next();
    assertNotEquals(oldMTValue, newMTValue);
  }

  /**
   * Tests the ability to properly handle adding and removing a schema
   * definition in which the definition has extra spaces.  This was added as a
   * test case for issue #2171.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddAndDeleteDefinitionWithExtraSpaces()
         throws Exception
  {
    int resultCode = TestCaseUtils.applyModifications(false,
      "dn: cn=schema",
      "changetype: modify",
      "add: objectClasses",
      "objectClasses: ( testaddanddeletedefinitionwithextraspaces-oid",
      "  NAME 'testAddAndDeleteDefinitionWithExtraSpaces'  SUP person",
      "  MAY ( street $ c) X-ORIGIN 'user defined' )");
    assertEquals(resultCode, 0);

    assertFalse(getSchema().getObjectClass("testaddanddeletedefinitionwithextraspaces").isPlaceHolder());
    assertFalse(getSchema().getObjectClass("testaddanddeletedefinitionwithextraspaces-oid").isPlaceHolder());

    resultCode = TestCaseUtils.applyModifications(false,
      "dn: cn=schema",
      "changetype: modify",
      "delete: objectClasses",
      "objectClasses: ( testaddanddeletedefinitionwithextraspaces-oid",
      "  NAME 'testAddAndDeleteDefinitionWithExtraSpaces'  SUP person",
      "  MAY ( street $ c) X-ORIGIN 'user defined' )");
    assertEquals(resultCode, 0);

    assertTrue(getSchema().getObjectClass("testaddanddeletedefinitionwithextraspaces").isPlaceHolder());
    assertTrue(getSchema().getObjectClass("testaddanddeletedefinitionwithextraspaces-oid").isPlaceHolder());
  }

  /**
   * Tests the {@code exportLDIF} method with a valid configuration.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testExportLDIF()
         throws Exception
  {
    File tempFile = File.createTempFile("schema", "testExportLDIF");
    tempFile.deleteOnExit();

    LDIFExportConfig exportConfig = new LDIFExportConfig(tempFile.getAbsolutePath(), OVERWRITE);
    schemaBackend.exportLDIF(exportConfig);

    assertTrue(tempFile.exists());
    assertTrue(tempFile.length() > 0);
  }

  /**
   * Tests the {@code importLDIF} method to ensure that it throws an exception.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testImportLDIFFails() throws Exception
  {
    File tempFile = File.createTempFile("schema", "testImportLDIFFails");
    tempFile.deleteOnExit();

    LDIFExportConfig exportConfig = new LDIFExportConfig(tempFile.getAbsolutePath(), OVERWRITE);
    schemaBackend.exportLDIF(exportConfig);

    LDIFImportConfig importConfig = new LDIFImportConfig(tempFile.getAbsolutePath());
    LDIFImportResult importResult = schemaBackend.importLDIF(importConfig, getServerContext());
    assertEquals(importResult.getEntriesRead(), 1);
    assertEquals(importResult.getEntriesImported(), 0);
    assertEquals(importResult.getEntriesRejected(), 1);
    assertEquals(importResult.getEntriesSkipped(), 0);
  }

  @Test
  public void testImportLDIFSuccess() throws Exception
  {
    File tempFile = File.createTempFile("schema", "testImportLDIFSucceeds");
    tempFile.deleteOnExit();

    LDIFExportConfig exportConfig = new LDIFExportConfig(tempFile.getAbsolutePath(), OVERWRITE);
    schemaBackend.exportLDIF(exportConfig);

    // replication does not validate schema
    LDIFImportConfig importConfig = new LDIFImportConfig(tempFile.getAbsolutePath());
    importConfig.setValidateSchema(false);

    LDIFImportResult importResult = schemaBackend.importLDIF(importConfig, getServerContext());
    assertEquals(importResult.getEntriesRead(), 1);
    assertEquals(importResult.getEntriesImported(), 1);
    assertEquals(importResult.getEntriesRejected(), 0);
    assertEquals(importResult.getEntriesSkipped(), 0);
  }

  /**
   * Tests the {@code getComponentEntryDN} method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testGetComponentEntryDN() throws Exception
  {
    DN configEntryDN =
            DN.valueOf("ds-cfg-backend-id=schema,cn=Backends,cn=config");
    assertEquals(schemaBackend.getComponentEntryDN(), configEntryDN);
  }

  /** Tests the {@code getClassName} method. */
  @Test
  public void testGetClassName()
  {
    assertEquals(schemaBackend.getClassName(), SchemaBackend.class.getName());
  }

  /** Tests the {@code getAlerts} method. */
  @Test
  public void testGetAlerts()
  {
    Map<String, String> alerts = schemaBackend.getAlerts();
    assertNotNull(alerts);
    assertFalse(alerts.isEmpty());
  }
}
