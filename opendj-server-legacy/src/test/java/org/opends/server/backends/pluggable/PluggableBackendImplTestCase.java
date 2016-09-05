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
 * Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.server.backends.pluggable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.opendj.ldap.ModificationType.*;
import static org.mockito.Mockito.*;
import static org.opends.server.protocols.internal.InternalClientConnection.getRootConnection;
import static org.opends.server.protocols.internal.Requests.newSearchRequest;
import static org.opends.server.types.Attributes.create;
import static org.opends.server.types.IndexType.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.testng.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.CoreSchema;
import org.forgerock.util.Reject;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.forgerock.opendj.server.config.meta.BackendIndexCfgDefn.IndexType;
import org.forgerock.opendj.server.config.meta.BackendVLVIndexCfgDefn.Scope;
import org.forgerock.opendj.server.config.server.BackendIndexCfg;
import org.forgerock.opendj.server.config.server.BackendVLVIndexCfg;
import org.forgerock.opendj.server.config.server.PluggableBackendCfg;
import org.opends.server.api.Backend.BackendOperation;
import org.opends.server.api.ClientConnection;
import org.opends.server.backends.RebuildConfig;
import org.opends.server.backends.RebuildConfig.RebuildMode;
import org.opends.server.backends.VerifyConfig;
import org.opends.server.backends.pluggable.spi.AccessMode;
import org.opends.server.backends.pluggable.spi.ReadOnlyStorageException;
import org.opends.server.backends.pluggable.spi.ReadOperation;
import org.opends.server.backends.pluggable.spi.ReadableTransaction;
import org.opends.server.backends.pluggable.spi.Storage;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.WriteOperation;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.core.ServerContext;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.Modification;
import org.opends.server.types.RestoreConfig;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.workflowelement.localbackend.LocalBackendSearchOperation;
import org.testng.Reporter;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Unit tests for pluggable backend implementations. The test methods have side-effects and must be run in-order.
 */
@SuppressWarnings("javadoc")
@Test(groups = { "precommit", "pluggablebackend" }, sequential = true)
public abstract class PluggableBackendImplTestCase<C extends PluggableBackendCfg> extends DirectoryServerTestCase
{
  private BackendImpl<C> backend;
  private List<Entry> topEntries;
  private List<Entry> entries;
  private List<Entry> workEntries;
  private DN testBaseDN;
  private DN dnToDel;
  private DN searchDN;
  private DN badEntryDN;
  private String backupID;

  private Map<String, IndexType[]> backendIndexes = new HashMap<>();
  {
    backendIndexes.put("entryUUID", new IndexType[] { IndexType.EQUALITY });
    backendIndexes.put("cn", new IndexType[] { IndexType.SUBSTRING });
    backendIndexes.put("sn", new IndexType[] { IndexType.PRESENCE, IndexType.EQUALITY, IndexType.SUBSTRING });
    backendIndexes.put("uid", new IndexType[] { IndexType.EQUALITY });
    backendIndexes.put("telephoneNumber", new IndexType[] { IndexType.EQUALITY, IndexType.SUBSTRING });
    backendIndexes.put("mail", new IndexType[] { IndexType.SUBSTRING });
  }

  private String[] backendVlvIndexes = { "people" };
  private AttributeType modifyAttribute;
  private final ByteString modifyValue = ByteString.valueOfUtf8("foo");
  private BackupDirectory backupDirectory;

  /**
   * Factory method for creating a new unconfigured backend instance.
   *
   * @return the unconfigured backend instance.
   * @see #setUp()
   */
  protected abstract BackendImpl<C> createBackend();

  /**
   * Factory method for creating a new backend configuration. All methods specific to the sub-class
   * should be stubbed out.
   *
   * @return the new backend configuration.
   * @see #setUp()
   */
  protected abstract C createBackendCfg();

  @BeforeClass
  public void setUp() throws Exception
  {
    // Need the schema to be available, so make sure the server is started.
    TestCaseUtils.startServer();

    testBaseDN = DN.valueOf("dc=test,dc=com");

    C backendCfg = createBackendCfg();
    when(backendCfg.dn()).thenReturn(testBaseDN);
    when(backendCfg.getBaseDN()).thenReturn(newTreeSet(testBaseDN));
    when(backendCfg.listBackendIndexes()).thenReturn(backendIndexes.keySet().toArray(new String[0]));
    when(backendCfg.listBackendVLVIndexes()).thenReturn(backendVlvIndexes);

    for (Map.Entry<String, IndexType[]> index : backendIndexes.entrySet())
    {
      final String attributeName = index.getKey();
      final AttributeType attribute = DirectoryServer.getSchema().getAttributeType(attributeName);
      Reject.ifNull(attribute, "Attribute type '" + attributeName + "' doesn't exists.");

      BackendIndexCfg indexCfg = mock(BackendIndexCfg.class);
      when(indexCfg.getIndexType()).thenReturn(newTreeSet(index.getValue()));
      when(indexCfg.getAttribute()).thenReturn(attribute);
      when(indexCfg.getIndexEntryLimit()).thenReturn(4000);
      when(indexCfg.getSubstringLength()).thenReturn(6);
      when(backendCfg.getBackendIndex(index.getKey())).thenReturn(indexCfg);
      if (backendCfg.isConfidentialityEnabled())
      {
        when(indexCfg.isConfidentialityEnabled()).thenReturn(true);
      }
    }

    BackendVLVIndexCfg vlvIndexCfg = mock(BackendVLVIndexCfg.class);
    when(vlvIndexCfg.getName()).thenReturn("people");
    when(vlvIndexCfg.getBaseDN()).thenReturn(testBaseDN);
    when(vlvIndexCfg.getFilter()).thenReturn("(objectClass=person)");
    when(vlvIndexCfg.getScope()).thenReturn(Scope.WHOLE_SUBTREE);
    when(vlvIndexCfg.getSortOrder()).thenReturn("sn -employeeNumber +uid");
    when(backendCfg.getBackendVLVIndex(backendVlvIndexes[0])).thenReturn(vlvIndexCfg);

    backend = createBackend();
    backend.setBackendID(backendCfg.getBackendId());
    backend.configureBackend(backendCfg, DirectoryServer.getInstance().getServerContext());
    backend.openBackend();

    topEntries = TestCaseUtils.makeEntries(
                "dn: " + testBaseDN,
                "objectclass: top",
                "objectclass: domain",
                "",
                "dn: ou=People," + testBaseDN,
                "objectclass: top",
                "objectclass: organizationalUnit",
                "ou: People");
    entries = TestCaseUtils.makeEntries(
                "dn: uid=user.0,ou=People," + testBaseDN,
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
                "description: This is the description for Aaccf Amar.",
                "",
                "dn: uid=user.1,ou=People," + testBaseDN,
                "objectClass: top",
                "objectClass: person",
                "objectClass: organizationalPerson",
                "objectClass: inetOrgPerson",
                "givenName: Aaren",
                "givenName;lang-fr: test2",
                "givenName;lang-cn: test2",
                "givenName;lang-es: test3",
                "sn: Atp",
                "cn: Aaren Atp",
                "initials: APA",
                "employeeNumber: 1",
                "uid: user.1",
                "mail: user.1@example.com",
                "userPassword: password",
                "telephoneNumber: 643-278-6134",
                "homePhone: 546-786-4099",
                "pager: 508-261-3187",
                "mobile: 377-267-7824",
                "carLicense: 377-267-7824",
                "street: 78113 Fifth Street",
                "l: Chico",
                "st: TN",
                "postalCode: 72322",
                "postalAddress: Aaren Atp$78113 Fifth Street$Chico, TN  72322",
                "description: This is the description for Aaren Atp.",
                "",
                "dn: uid=user.2,ou=People," + testBaseDN,
                "objectClass: top",
                "objectClass: person",
                "objectClass: organizationalPerson",
                "objectClass: inetOrgPerson",
                "givenName: Aarika",
                "sn: Atpco",
                "cn: Aarika Atpco",
                "initials: ARA",
                "employeeNumber: 2",
                "uid: user.2",
                "mail: user.2@example.com",
                "userPassword: password",
                "telephoneNumber: 547-504-3498",
                "homePhone: 955-899-7308",
                "pager: 710-832-9316",
                "mobile: 688-388-4525",
                "carLicense: 688-388-4525",
                "street: 59208 Elm Street",
                "l: Youngstown",
                "st: HI",
                "postalCode: 57377",
                "postalAddress: Aarika Atpco$59208 Elm Street$Youngstown, HI  57377",
                "description: This is the description for Aarika Atpco.",
                "",
                "dn: uid=user.3,ou=People," + testBaseDN,
                "objectClass: top",
                "objectClass: person",
                "objectClass: organizationalPerson",
                "objectClass: inetOrgPerson",
                "givenName: Aaron",
                "sn: Atrc",
                "cn: Aaron Atrc",
                "initials: AIA",
                "employeeNumber: 3",
                "uid: user.3",
                "mail: user.3@example.com",
                "userPassword: password",
                "telephoneNumber: 128-108-4939",
                "homePhone: 512-782-9966",
                "pager: 322-646-5118",
                "mobile: 360-957-9137",
                "carLicense: 360-957-9137",
                "street: 25074 Hill Street",
                "l: Toledo",
                "st: OR",
                "postalCode: 55237",
                "postalAddress: Aaron Atrc$25074 Hill Street$Toledo, OR  55237",
                "description: This is the description for Aaron Atrc.",
                "",
                "dn: uid=user.4,ou=People," + testBaseDN,
                "objectClass: top",
                "objectClass: person",
                "objectClass: organizationalPerson",
                "objectClass: inetOrgPerson",
                "givenName: Aartjan",
                "sn: Aalders",
                "cn: Aartjan Aalders",
                "initials: ALA",
                "employeeNumber: 4",
                "uid: user.4",
                "mail: user.4@example.com",
                "userPassword: password",
                "telephoneNumber: 981-148-3303",
                "homePhone: 196-877-2684",
                "pager: 910-998-4607",
                "mobile: 123-239-8262",
                "carLicense: 123-239-8262",
                "street: 81512 Sunset Street",
                "l: Chattanooga",
                "st: WV",
                "postalCode: 29530",
                "postalAddress: Aartjan Aalders$81512 Sunset Street$Chattanooga, WV  29530",
                "description: This is the description for Aartjan Aalders.",
                "",
                "dn: uid=user.5,ou=People," + testBaseDN,
                "objectClass: top",
                "objectClass: person",
                "objectClass: organizationalPerson",
                "objectClass: inetOrgPerson",
                "givenName: Abagael",
                "sn: Aasen",
                "cn: Abagael Aasen",
                "initials: AKA",
                "employeeNumber: 5",
                "uid: user.5",
                "mail: user.5@example.com",
                "userPassword: password",
                "telephoneNumber: 930-493-2391",
                "homePhone: 078-254-3960",
                "pager: 281-936-8197",
                "mobile: 559-822-7712",
                "carLicense: 559-822-7712",
                "street: 31988 Central Street",
                "l: Chico",
                "st: MS",
                "postalCode: 20135",
                "postalAddress: Abagael Aasen$31988 Central Street$Chico, MS  20135",
                "description: This is the description for Abagael Aasen.",
                "",
                "dn: uid=user.6,ou=People," + testBaseDN,
                "objectClass: top",
                "objectClass: person",
                "objectClass: organizationalPerson",
                "objectClass: inetOrgPerson",
                "givenName: Abagail",
                "sn: Abadines",
                "cn: Abagail Abadines",
                "initials: AQA",
                "employeeNumber: 6",
                "uid: user.6",
                "mail: user.6@example.com",
                "userPassword: password",
                "telephoneNumber: 110-761-3861",
                "homePhone: 459-123-0553",
                "pager: 799-151-2688",
                "mobile: 953-582-7252",
                "carLicense: 953-582-7252",
                "street: 60100 Dogwood Street",
                "l: Hartford",
                "st: NE",
                "postalCode: 79353",
                "postalAddress: Abagail Abadines$60100 Dogwood Street$Hartford, NE  79353",
                "description: This is the description for Abagail Abadines.",
                "",
                "dn: uid=user.7,ou=People," + testBaseDN,
                "objectClass: top",
                "objectClass: person",
                "objectClass: organizationalPerson",
                "objectClass: inetOrgPerson",
                "givenName: Abahri",
                "sn: Abazari",
                "cn: Abahri Abazari",
                "initials: AXA",
                "employeeNumber: 7",
                "uid: user.7",
                "mail: user.7@example.com",
                "userPassword: password",
                "telephoneNumber: 594-537-4292",
                "homePhone: 174-724-6390",
                "pager: 733-217-8194",
                "mobile: 879-706-0172",
                "carLicense: 879-706-0172",
                "street: 77693 Oak Street",
                "l: Philadelphia",
                "st: MN",
                "postalCode: 78550",
                "postalAddress: Abahri Abazari$77693 Oak Street$Philadelphia, MN  78550",
                "description: This is the description for Abahri Abazari.",
                "",
                "dn: uid=user.8,ou=People," + testBaseDN,
                "objectClass: top",
                "objectClass: person",
                "objectClass: organizationalPerson",
                "objectClass: inetOrgPerson",
                "givenName: Abbas",
                "sn: Abbatantuono",
                "cn: Abbas Abbatantuono",
                "initials: AVA",
                "employeeNumber: 8",
                "uid: user.8",
                "mail: user.8@example.com",
                "userPassword: password",
                "telephoneNumber: 246-674-8407",
                "homePhone: 039-769-3372",
                "pager: 226-950-2371",
                "mobile: 587-709-2996",
                "carLicense: 587-709-2996",
                "street: 23230 Hill Street",
                "l: Little Rock",
                "st: AR",
                "",
                "dn: uid=user.9,ou=People," + testBaseDN,
                "objectClass: top",
                "objectClass: person",
                "objectClass: organizationalPerson",
                "objectClass: inetOrgPerson",
                "givenName: Abbe",
                "sn: Abbate",
                "cn: Abbe Abbate",
                "initials: AWA",
                "employeeNumber: 9",
                "uid: user.9",
                "mail: user.9@example.com",
                "userPassword: password",
                "telephoneNumber: 205-805-3357",
                "homePhone: 770-780-5917",
                "pager: 537-074-8005",
                "mobile: 120-204-7597",
                "carLicense: 120-204-7597",
                "street: 47952 Center Street",
                "l: Butte",
                "st: TN",
                "postalCode: 69384",
                "postalAddress: Abbe Abbate$47952 Center Street$Butte, TN  69384",
                "description: This is the description for Abbe Abbate.",
                "",
                "dn: uid=user.10,ou=People," + testBaseDN,
                "objectClass: top",
                "objectClass: person",
                "objectClass: organizationalPerson",
                "objectClass: inetOrgPerson",
                "givenName: Abbey",
                "sn: Abbie",
                "cn: Abbey Abbie",
                "initials: AZA",
                "employeeNumber: 10",
                "uid: user.10",
                "mail: user.10@example.com",
                "userPassword: password",
                "telephoneNumber: 457-819-0832",
                "homePhone: 931-305-5452",
                "pager: 118-165-7194",
                "mobile: 553-729-5572",
                "carLicense: 553-729-5572",
                "street: 54262 Highland Street",
                "l: Spartanburg",
                "st: PA",
                "postalCode: 38151",
                "postalAddress: Abbey Abbie$54262 Highland Street$Spartanburg, PA  38151",
                "description: This is the description for Abbey Abbie.",
                "",
                "dn: uid=user.539,ou=People," + testBaseDN,
                "objectClass: top",
                "objectClass: person",
                "objectClass: organizationalPerson",
                "objectClass: inetOrgPerson",
                "givenName: Ardyth",
                "sn: Bainton",
                "cn: Ardyth Bainton",
                "initials: AIB",
                "employeeNumber: 539",
                "uid: user.539",
                "mail: user.539@example.com",
                "userPassword: password",
                "telephoneNumber: 641-433-7404",
                "homePhone: 524-765-8780",
                "pager: 985-331-1308",
                "mobile: 279-423-0188",
                "carLicense: 279-423-0188",
                "street: 81170 Taylor Street",
                "l: Syracuse",
                "st: WV",
                "postalCode: 93507",
                "postalAddress: Ardyth Bainton$81170 Taylor Street$Syracuse, WV  93507",
                "description: This is the description for Ardyth Bainton.");

    workEntries = TestCaseUtils.makeEntries(
                "dn: uid=user.11,ou=People," + testBaseDN,
                "objectClass: top",
                "objectClass: person",
                "objectClass: organizationalPerson",
                "objectClass: inetOrgPerson",
                "givenName: Annalee",
                "sn: Avard",
                "cn: Annalee Avard",
                "initials: ANA",
                "employeeNumber: 11",
                "uid: user.11",
                "mail: user.11@example.com",
                "userPassword: password",
                "telephoneNumber: 875-335-2712",
                "homePhone: 181-995-6635",
                "pager: 586-905-4185",
                "mobile: 826-857-7592",
                "carLicense: 826-857-7592",
                "street: 46168 Mill Street",
                "l: Charleston",
                "st: CO",
                "postalCode: 60948",
                "postalAddress: Annalee Avard$46168 Mill Street$Charleston, CO  60948",
                "description: This is the description for Annalee Avard.",
                "",
                "dn: uid=user.12,ou=People," + testBaseDN,
                "objectClass: top",
                "objectClass: person",
                "objectClass: organizationalPerson",
                "objectClass: inetOrgPerson",
                "givenName: Andaree",
                "sn: Asawa",
                "cn: Andaree Asawa",
                "initials: AEA",
                "employeeNumber: 12",
                "uid: user.12",
                "mail: user.12@example.com",
                "userPassword: password",
                "telephoneNumber: 399-788-7334",
                "homePhone: 798-076-5683",
                "pager: 034-026-9411",
                "mobile: 948-743-9197",
                "carLicense: 948-743-9197",
                "street: 81028 Forest Street",
                "l: Wheeling",
                "st: IA",
                "postalCode: 60905",
                "postalAddress: Andaree Asawa$81028 Forest Street$Wheeling, IA  60905",
                "description: This is the description for Andaree Asawa.");

    dnToDel = workEntries.get(1).getName();
    searchDN = entries.get(1).getName();
    badEntryDN = testBaseDN.child(DN.valueOf("ou=bogus")).child(DN.valueOf("ou=dummy"));
    backupID = "backupID1";

    addEntriesToBackend(topEntries);
    addEntriesToBackend(entries);
    addEntriesToBackend(workEntries);
  }

  private void addEntriesToBackend(List<Entry> entries) throws Exception
  {
    AddOperation op = mock(AddOperation.class);
    for (Entry newEntry : entries)
    {
      backend.addEntry(newEntry, op);
    }
  }

  @AfterClass
  public void cleanUp() throws Exception
  {
    try
    {
      if (backupDirectory != null)
      {
        backend.removeBackup(backupDirectory, backupID);
      }
    }
    catch (DirectoryException ignore)
    {
      /*
       *  Due to test sequencing (Import, backup, restore, export) in case of intermediate failure
       *  Backup may or may not be present. -> ignore.
       */
      Reporter.log(ignore.getMessage(), true);
    }
    finally
    {
      backend.finalizeBackend();
      backend = null;
    }
  }

  /**
   * Tests the storage API for resource checking. The tested method has no return value, but an
   * exception, while not systematic, may be thrown, in which case the test must fail.
   *
   * @throws Exception
   *           if resources are low.
   */
  @Test
  public void testCheckForEnoughResources() throws Exception
  {
    backend.getRootContainer().checkForEnoughResources(null);
  }

  @Test(expectedExceptions = DirectoryException.class,
      expectedExceptionsMessageRegExp="The entry .* cannot be added because its parent entry does not exist")
  public void testAddNoParent() throws Exception
  {
    Entry newEntry = TestCaseUtils.makeEntry("dn: " + badEntryDN, "objectclass: ou", "");
    backend.addEntry(newEntry, null);
  }

  @Test
  public void testUtilityAPIs()
  {
    assertEquals(backend.getEntryCount(), getTotalNumberOfLDIFEntries());
    assertFalse(backend.isIndexed(modifyAttribute, EQUALITY));
    for (Map.Entry<String, IndexType[]> index : backendIndexes.entrySet())
    {
      for (IndexType type : index.getValue())
      {
        final AttributeType attributeType = DirectoryServer.getSchema().getAttributeType(index.getKey());
        assertTrue(backend.isIndexed(attributeType,
            org.opends.server.types.IndexType.valueOf(type.toString().toUpperCase())));
      }
    }
  }

  private int getTotalNumberOfLDIFEntries()
  {
    return topEntries.size() + entries.size() + workEntries.size();
  }

  @Test
  public void testHasSubordinates() throws Exception
  {
    assertEquals(backend.hasSubordinates(testBaseDN), ConditionResult.TRUE,
        "Base DN should have subordinates.");

    // Testing ConditionResult.UNDEFINED needs either no entry container or a big DIT...
    assertEquals(backend.hasSubordinates(DN.valueOf("dc=a")), ConditionResult.UNDEFINED,
        "Subordinates query on unknown baseDN should return UNDEFINED.");

    assertEquals(backend.getNumberOfChildren(testBaseDN), 1);
    assertEquals(backend.getNumberOfEntriesInBaseDN(testBaseDN), getTotalNumberOfLDIFEntries(), "Wrong DIT count.");
    assertEquals(backend.hasSubordinates(searchDN), ConditionResult.FALSE,
        "Leaf entry should not have any subordinates.");
  }

  private List<SearchResultEntry> runSearch(SearchRequest request, boolean useInternalConnection) throws Exception
  {
    InternalClientConnection conn = getRootConnection();

    if (useInternalConnection)
    {
      InternalSearchOperation search = conn.processSearch(request);
      return search.getSearchEntries();
    }
    else
    {
      InternalSearchOperation search = new InternalSearchOperation(conn, -1, -1, request);
      backend.search(new LocalBackendSearchOperation(search));
      return search.getSearchEntries();
    }
  }

  @Test
  public void testModifyEntry() throws Exception
  {
    Entry oldEntry = workEntries.get(0);
    Entry newEntry = oldEntry.duplicate(false);

    modifyAttribute = DirectoryServer.getSchema().getAttributeType("jpegphoto");
    List<Modification> mods = Arrays.asList(
        // unindexed
        new Modification(ADD, create(modifyAttribute, modifyValue)),
        // indexed
        new Modification(REPLACE, create("sn", "Smith")));
    newEntry.applyModifications(mods);

    ModifyOperation modifyOp = mock(ModifyOperation.class);
    when(modifyOp.getModifications()).thenReturn(mods);
    backend.replaceEntry(oldEntry, newEntry, modifyOp);
    assertTrue(backend.getEntry(oldEntry.getName()).hasValue(modifyAttribute, modifyValue));

    final List<Entry> returnedEntries = new ArrayList<>();
    backend.search(createSearchOperation(
        newEntry.getName().parent(), SearchScope.WHOLE_SUBTREE, "(&(sn=Smith)(jpegphoto=foo))", returnedEntries));
    assertThat(returnedEntries).hasSize(1);
    assertThat((Object) returnedEntries.get(0).getName()).isEqualTo(newEntry.getName());
  }

  private SearchOperation createSearchOperation(DN baseDN, SearchScope scope, String searchFilter,
      final List<Entry> returnedEntries) throws DirectoryException
  {
    SearchOperation searchOp = mock(SearchOperation.class);
    when(searchOp.getBaseDN()).thenReturn(baseDN);
    when(searchOp.getScope()).thenReturn(scope);
    when(searchOp.getFilter()).thenReturn(SearchFilter.createFilterFromString(searchFilter));
    when(searchOp.getClientConnection()).thenReturn(new ClientConnectionStub());
    doAnswer(new Answer<Object>()
    {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable
      {
        returnedEntries.add(invocation.getArgumentAt(0, Entry.class));
        return null;
      }
    }).when(searchOp).returnEntry(any(Entry.class), any(List.class));
    return searchOp;
  }

  @Test
  public void testRenameEntry() throws Exception
  {
    // Move the entire subtree to another name and move it back.
    DN prevDN = DN.valueOf("ou=People," + testBaseDN);
    DN newDN = DN.valueOf("ou=users," + testBaseDN);
    Entry renameEntry = backend.getEntry(prevDN).duplicate(false);
    ModifyDNOperation op = mock(ModifyDNOperation.class);

    renameEntry.setDN(newDN);
    backend.renameEntry(prevDN, renameEntry, op);
    Entry dbEntry = backend.getEntry(newDN);
    assertEquals(dbEntry.getName(), newDN, "Renamed entry is missing.");

    renameEntry.setDN(prevDN);
    backend.renameEntry(newDN, renameEntry, op);
    dbEntry = backend.getEntry(prevDN);
    assertEquals(dbEntry.getName(), prevDN, "Original entry has not been renamed");
  }

  @Test(description = "OPENDJ-2404")
  public void testRenameEntrySameDNDifferentCase() throws Exception
  {
    DN prevDN = DN.valueOf("uid=user.0,ou=People," + testBaseDN);
    DN newDN = DN.valueOf("uid=USER.0,ou=People," + testBaseDN);
    Entry renameEntry = backend.getEntry(prevDN).duplicate(false);

    ModifyDNOperation op = mock(ModifyDNOperation.class);
    renameEntry.setDN(newDN);
    backend.renameEntry(prevDN, renameEntry, op);
    Entry dbEntry = backend.getEntry(newDN);
    assertEquals(dbEntry.getName().rdn().toString(), "uid=USER.0");

    renameEntry.setDN(prevDN);
    backend.renameEntry(newDN, renameEntry, op);
    dbEntry = backend.getEntry(prevDN);
    assertEquals(dbEntry.getName().rdn().toString(), "uid=user.0");
  }

  @Test(expectedExceptions = DirectoryException.class)
  public void testDeleteEntry() throws Exception
  {
    Entry deletedEntry = backend.getEntry(dnToDel).duplicate(false);
    deleteEntry(dnToDel);
    try
    {
      deleteEntry(dnToDel);
      fail("Should have generated a DirectoryException");
    }
    finally
    {
      AddOperation op = mock(AddOperation.class);
      backend.addEntry(deletedEntry, op);
    }
  }

  private void deleteEntry(DN dn) throws Exception
  {
    DeleteOperation op = mock(DeleteOperation.class);
    backend.deleteEntry(dn, op);
    assertNull(backend.getEntry(workEntries.get(1).getName()));
  }

  @Test
  public void testBaseSearch() throws Exception
  {
    baseSearch(false);
    baseSearch(true);
  }

  private void baseSearch(boolean useInternalConnection) throws Exception
  {
    SearchRequest request = newSearchRequest(testBaseDN, SearchScope.BASE_OBJECT, "objectclass=*");
    List<SearchResultEntry> result = runSearch(request, useInternalConnection);

    assertEquals(result.size(), 1, "Base Search should return only one Entry");
    assertEquals(result.get(0).getName(), testBaseDN, "Base Search on the suffix should return the suffix itself");
  }

  @Test
  public void testOneLevelSearch() throws Exception
  {
    oneLevelSearch(false);
    oneLevelSearch(true);
  }

  private void oneLevelSearch(boolean useInternalConnection) throws Exception
  {
    SearchRequest request = newSearchRequest(testBaseDN, SearchScope.SINGLE_LEVEL, "objectclass=*");
    List<SearchResultEntry> result = runSearch(request, useInternalConnection);

    assertEquals(result.size(), 1, "One Level search should return a single child entry");
    SearchResultEntry resEntry = result.get(0);
    assertEquals(topEntries.get(1).getName(), resEntry.getName(),
        "One Level search should return the expected child");
  }

  @Test
  public void testSubTreeSearch() throws Exception
  {
    subTreeSearch(false);
    subTreeSearch(true);
  }

  @Test
  public void testSubTreeSearchAgainstAnIndexWithUnrecognizedMatchingRule() throws Exception
  {
    SearchRequest request = newSearchRequest(testBaseDN, SearchScope.WHOLE_SUBTREE, "entryUUID=xxx*");
    assertThat(runSearch(request, false)).isEmpty();
  }

  @DataProvider
  protected Object[][] subStringSearchFilter()
  {
    return new Object[][] {
      // @formatter:off
      { "sn=abadin*", 1},
      { "sn=*abadin*", 1},
      { "sn=*abadin", 0},
      { "sn=*adines", 1}
      // @formatter:on
    };
  }

  @Test(dataProvider = "subStringSearchFilter")
  public void testFilterUsingSubStringSearch(final String filter, int numberOfEntries) throws Exception
  {
    SearchRequest request = newSearchRequest(testBaseDN, SearchScope.WHOLE_SUBTREE, filter);
    assertThat(runSearch(request, false)).hasSize(numberOfEntries);
  }

  @Test
  public void testSearchIsConsideredUnindexedBasedOnLookThroughLimit() throws DirectoryException {
    final int nbEntries = topEntries.size() + entries.size() + workEntries.size();

    final SearchRequest request = newSearchRequest(testBaseDN, SearchScope.WHOLE_SUBTREE, "objectclass=*");
    final ClientConnection connection = new ClientConnectionStub();
    connection.setLookthroughLimit(0);
    InternalSearchOperation searchOperation = new InternalSearchOperation(connection, 1, 1, request, null);
    searchOperation.run();
    assertThat(searchOperation.getEntriesSent()).isEqualTo(nbEntries);

    connection.setLookthroughLimit(nbEntries);
    searchOperation = new InternalSearchOperation(connection, 1, 1, request, null);
    searchOperation.run();
    assertThat(searchOperation.getEntriesSent()).isEqualTo(nbEntries);

    connection.setLookthroughLimit(nbEntries - 1);
    searchOperation = new InternalSearchOperation(connection, 1, 1, request, null);
    searchOperation.run();
    assertThat(searchOperation.getResultCode()).isEqualTo(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
    assertThat(searchOperation.getErrorMessage().toString())
    .contains("not have sufficient privileges", "unindexed search");
    assertThat(searchOperation.getEntriesSent()).isEqualTo(0);
  }

  private void subTreeSearch(boolean useInternalConnection) throws Exception
  {
    SearchRequest request = newSearchRequest(testBaseDN, SearchScope.WHOLE_SUBTREE, "objectclass=*");
    List<SearchResultEntry> result = runSearch(request, useInternalConnection);

    // Sum of all entry sets minus a delete
    assertEquals(result.size(), getTotalNumberOfLDIFEntries(),
        "Subtree search should return a correct number of entries");
  }


  @DataProvider
  protected Object[][] userEntrySearchData()
  {
    return new Object[][] {
      // @formatter:off
      { true,  SearchScope.BASE_OBJECT, 1 },
      { false, SearchScope.BASE_OBJECT, 1 },
      { true,  SearchScope.SINGLE_LEVEL, 0 },
      { false, SearchScope.SINGLE_LEVEL, 0 },
      { true,  SearchScope.WHOLE_SUBTREE, 1 },
      { false, SearchScope.WHOLE_SUBTREE, 1 },
      // @formatter:on
    };
  }

  @Test(dataProvider = "userEntrySearchData")
  public void testUserEntrySearch(boolean useInternalConnection, SearchScope scope, int expectedEntryCount)
      throws Exception
  {
    SearchRequest request = newSearchRequest(searchDN, scope, "objectclass=*");
    List<SearchResultEntry> result = runSearch(request, useInternalConnection);

    assertEquals(result.size(), expectedEntryCount, "User entry search should return " + expectedEntryCount
        + " child entry");
    if (expectedEntryCount > 0)
    {
      assertEquals(searchDN, result.get(0).getName(), "User entry search should return the expected entry");
    }
  }

  @Test
  public void testGetEntry() throws Exception
  {
    for (Entry expected : entries)
    {
      Entry dbEntry = backend.getEntry(expected.getName());
      Entry actual = new Entry(dbEntry.getName(), dbEntry.getObjectClasses(), dbEntry.getUserAttributes(), null);

      // Remove the userPassword because it will have been encoded.
      expected.removeAttribute(CoreSchema.getUserPasswordAttributeType());
      actual.removeAttribute(CoreSchema.getUserPasswordAttributeType());

      assertThat(actual).isEqualTo(expected);
    }
  }

  @Test
  public void testExportLDIFAndImportLDIF() throws Exception
  {
    assertTrue(backend.supports(BackendOperation.LDIF_EXPORT), "Export not supported");
    ByteArrayOutputStream ldifOutputContent = new ByteArrayOutputStream();
    try (final LDIFExportConfig exportConfig = new LDIFExportConfig(ldifOutputContent))
    {
      exportConfig.setIncludeOperationalAttributes(true);
      backend.exportLDIF(exportConfig);
    }

    String ldifString = ldifOutputContent.toString();
    assertEquals(ldifString.contains(testBaseDN.toString()), true, "Export without rootDN");
    assertEquals(ldifString.contains(searchDN.toString()), true, "Export without rootDN");


    // Import wants the backend to be configured but not initialized. Finalizing resets the status.
    assertTrue(backend.supports(BackendOperation.LDIF_IMPORT), "Import not supported");
    backend.finalizeBackend();

    ByteArrayInputStream ldifImportContent = new ByteArrayInputStream(ldifOutputContent.toByteArray());
    ByteArrayOutputStream rejectedEntries = new ByteArrayOutputStream();
    try (final LDIFImportConfig importConf = new LDIFImportConfig(ldifImportContent))
    {
      importConf.setInvokeImportPlugins(true);
      importConf.setClearBackend(true);
      importConf.writeRejectedEntries(rejectedEntries);
      importConf.setIncludeBranches(Collections.singleton(testBaseDN));
      importConf.setThreadCount(0);
      backend.importLDIF(importConf, DirectoryServer.getInstance().getServerContext());
    }
    assertEquals(rejectedEntries.size(), 0,
                 "No entries should be rejected. Content was:\n" + rejectedEntries.toString());

    backend.openBackend();
    assertEquals(backend.getEntryCount(), getTotalNumberOfLDIFEntries(), "Not enough entries in DIT.");
    /** +1 for the testBaseDN itself */
    assertEquals(backend.getNumberOfEntriesInBaseDN(testBaseDN), getTotalNumberOfLDIFEntries(),
                 "Not enough entries in DIT.");
    assertEquals(backend.getNumberOfChildren(testBaseDN), 1,
                 "Not enough entries in DIT.");
    /** -2 for baseDn and People entry */
    assertEquals(backend.getNumberOfChildren(testBaseDN.child(DN.valueOf("ou=People"))),
                 getTotalNumberOfLDIFEntries() - 2,
                 "Not enough entries in DIT.");

    VerifyConfig config = new VerifyConfig();
    config.setBaseDN(DN.valueOf("dc=test,dc=com"));
    config.addCompleteIndex("dn2id");
    for (String indexName : backendIndexes.keySet())
    {
      config.addCompleteIndex(indexName);
    }
    assertThat(backend.verifyBackend(config)).isEqualTo(0);

    config = new VerifyConfig();
    config.setBaseDN(DN.valueOf("dc=test,dc=com"));
    config.addCleanIndex("dn2id");
    for (String indexName : backendIndexes.keySet())
    {
      config.addCleanIndex(indexName);
    }
    assertThat(backend.verifyBackend(config)).isEqualTo(0);
  }

  @Test
  public void testRebuildAllIndex() throws Exception
  {
    final EntryContainer entryContainer =  backend.getRootContainer().getEntryContainers().iterator().next();

    // Delete all the indexes
    backend.getRootContainer().getStorage().write(new WriteOperation()
    {
      @Override
      public void run(WriteableTransaction txn) throws Exception
      {
        entryContainer.getDN2ID().delete(txn);
        entryContainer.getDN2URI().delete(txn);
        entryContainer.getID2ChildrenCount().delete(txn);
        for(VLVIndex idx : entryContainer.getVLVIndexes())
        {
          idx.setTrusted(txn, false);
          idx.delete(txn);
        }
        for(AttributeIndex attribute : entryContainer.getAttributeIndexes())
        {
          for(Index idx : attribute.getNameToIndexes().values())
          {
            idx.setTrusted(txn, false);
            idx.delete(txn);
          }
        }
      }
    });

    RebuildConfig rebuildConf = new RebuildConfig();
    rebuildConf.setBaseDN(DN.valueOf("dc=test,dc=com"));
    rebuildConf.setRebuildMode(RebuildMode.ALL);

    backend.closeBackend();
    backend.rebuildBackend(rebuildConf, DirectoryServer.getInstance().getServerContext());
    backend.openBackend();

    VerifyConfig config = new VerifyConfig();
    config.setBaseDN(DN.valueOf("dc=test,dc=com"));
    config.addCompleteIndex("dn2id");
    for (String indexName : backendIndexes.keySet())
    {
      config.addCompleteIndex(indexName);
    }
    assertThat(backend.verifyBackend(config)).isEqualTo(0);


    config = new VerifyConfig();
    config.setBaseDN(DN.valueOf("dc=test,dc=com"));
    config.addCleanIndex("dn2id");
    for (String indexName : backendIndexes.keySet())
    {
      config.addCleanIndex(indexName);
    }
    assertThat(backend.verifyBackend(config)).isEqualTo(0);
  }

  @Test
  public void testRebuildDegradedIndex() throws Exception
  {
    final EntryContainer entryContainer =  backend.getRootContainer().getEntryContainers().iterator().next();

    final Set<String> dirtyIndexes = new HashSet<>(Arrays.asList("sn", "uid", "telephoneNumber"));
    assertThat(backendIndexes.keySet()).containsAll(dirtyIndexes);

    // Delete all the indexes
    backend.getRootContainer().getStorage().write(new WriteOperation()
    {
      @Override
      public void run(WriteableTransaction txn) throws Exception
      {
        for(AttributeIndex attribute : entryContainer.getAttributeIndexes())
        {
          boolean trusted = !dirtyIndexes.contains(attribute.getAttributeType().getNameOrOID());
          for(Index idx : attribute.getNameToIndexes().values())
          {
            idx.setTrusted(txn, trusted);
          }
        }
      }
    });

    RebuildConfig rebuildConf = new RebuildConfig();
    rebuildConf.setBaseDN(DN.valueOf("dc=test,dc=com"));
    rebuildConf.setRebuildMode(RebuildMode.DEGRADED);

    backend.closeBackend();
    backend.rebuildBackend(rebuildConf, DirectoryServer.getInstance().getServerContext());
    backend.openBackend();

    VerifyConfig config = new VerifyConfig();
    config.setBaseDN(DN.valueOf("dc=test,dc=com"));
    config.addCompleteIndex("dn2id");
    for (String indexName : backendIndexes.keySet())
    {
      config.addCompleteIndex(indexName);
    }
    assertThat(backend.verifyBackend(config)).isEqualTo(0);

    config = new VerifyConfig();
    config.setBaseDN(DN.valueOf("dc=test,dc=com"));
    config.addCleanIndex("dn2id");
    for (String indexName : backendIndexes.keySet())
    {
      config.addCleanIndex(indexName);
    }
    assertThat(backend.verifyBackend(config)).isEqualTo(0);
  }

  @Test
  public void testVerifyID2ChildrenCount() throws Exception
  {
    final Storage storage = backend.getRootContainer().getStorage();
    final DN2ID dn2ID = backend.getRootContainer().getEntryContainer(testBaseDN).getDN2ID();
    final ID2ChildrenCount id2ChildrenCount =
        backend.getRootContainer().getEntryContainer(testBaseDN).getID2ChildrenCount();

    final VerifyConfig config = new VerifyConfig();
    config.setBaseDN(DN.valueOf("dc=test,dc=com"));
    config.addCleanIndex("dn2id");

    assertThat(backend.verifyBackend(config)).isEqualTo(0);

    // Insert an error
    final EntryID peopleID = storage.read(new ReadOperation<EntryID>()
    {
      @Override
      public EntryID run(ReadableTransaction txn) throws Exception
      {
        return dn2ID.get(txn, testBaseDN.child(DN.valueOf("ou=People")));
      }
    });
    storage.write(new WriteOperation()
    {
      @Override
      public void run(WriteableTransaction txn) throws Exception
      {
        final long delta = id2ChildrenCount.removeCount(txn, peopleID);
        id2ChildrenCount.updateTotalCount(txn, -delta);
        id2ChildrenCount.updateCount(txn, peopleID, 1);
        id2ChildrenCount.updateTotalCount(txn, 1);
      }
    });

    assertThat(backend.verifyBackend(config)).isEqualTo(1);
  }

  @Test
  public void testBackupAndRestore() throws Exception
  {
    assertEquals(backend.supports(BackendOperation.BACKUP), true, "Skip Backup");
    assertNotNull(backupID, "Need to setup a backupID");

    final String backupPath = TestCaseUtils.createTemporaryDirectory("backup").getAbsolutePath();
    backupDirectory = new BackupDirectory(backupPath, testBaseDN);
    BackupConfig backupConf = new BackupConfig(backupDirectory, backupID, false);
    backend.createBackup(backupConf);

    assertTrue(backend.supports(BackendOperation.RESTORE), "Skip Restore");
    backend.restoreBackup(new RestoreConfig(backupDirectory, backupID, true));
  }

  @Test(expectedExceptions=ReadOnlyStorageException.class)
  public void testReadOnly() throws Exception
  {
    C backendCfg = createBackendCfg();
    when(backendCfg.dn()).thenReturn(testBaseDN);
    when(backendCfg.getBaseDN()).thenReturn(newTreeSet(testBaseDN));
    when(backendCfg.listBackendIndexes()).thenReturn(new String[0]);
    when(backendCfg.listBackendVLVIndexes()).thenReturn(new String[0]);

    ServerContext serverContext = DirectoryServer.getInstance().getServerContext();
    final Storage storage = backend.configureStorage(backendCfg, serverContext);
    final RootContainer readOnlyContainer =
        new RootContainer(backend.getBackendID(), serverContext, storage, backendCfg);

    // Put backend offline so that export LDIF open read-only container
    backend.finalizeBackend();
    try
    {
      readOnlyContainer.open(AccessMode.READ_ONLY);
      readOnlyContainer.getStorage().write(new WriteOperation()
      {
        @Override
        public void run(WriteableTransaction txn) throws Exception
        {
          txn.put(new TreeName("dc=test,dc=com", "id2entry"),
                  ByteString.valueOfUtf8("key"),
                  ByteString.valueOfUtf8("value"));
        }
      });
    }
    finally
    {
      readOnlyContainer.close();
      backend.openBackend();
    }
  }
}
