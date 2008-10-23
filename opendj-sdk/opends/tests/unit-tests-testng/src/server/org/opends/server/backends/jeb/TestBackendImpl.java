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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.backends.jeb;

import java.util.*;

import org.opends.server.TestCaseUtils;
import org.opends.server.admin.std.server.LocalDBBackendCfg;
import org.opends.server.admin.std.meta.LocalDBBackendCfgDefn;
import org.opends.server.admin.server.AdminTestCaseUtils;
import org.opends.server.core.ModifyDNOperationBasis;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.types.*;
import org.opends.server.util.Base64;
import static
    org.opends.server.util.ServerConstants.OID_SUBTREE_DELETE_CONTROL;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.AfterClass;

import static org.testng.Assert.*;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.LockMode;

import org.opends.server.core.DeleteOperationBasis;
import org.opends.server.core.ModifyOperationBasis;

/**
 * BackendImpl Tester.
 */
public class TestBackendImpl extends JebTestCase {
  private String homeDirName;

  private BackendImpl backend;

  private List<Entry> topEntries;
  private List<Entry> entries;
  private List<Entry> additionalEntries;
  private Entry replaceEntry;
  private Entry newTop;

  /**
   * The attribute used to return a search index debug string to the client.
   */
  public static final String ATTR_DEBUG_SEARCH_INDEX = "debugsearchindex";

  @BeforeClass
  public void setUp() throws Exception {
    // This test suite depends on having the schema available, so we'll make
    // sure the server is started.
    TestCaseUtils.startServer();

    homeDirName = "db_index_test";

    backend = (BackendImpl)DirectoryServer.getBackend("indexRoot");

    topEntries = TestCaseUtils.makeEntries(
        "dn: dc=test,dc=com",
        "objectclass: top",
        "objectclass: domain",
        "dc: example",
        "",
        "dn: ou=People,dc=test,dc=com",
        "objectclass: top",
        "objectclass: organizationalUnit",
        "ou: People",
        "",
        "dn: dc=test1,dc=com",
        "objectclass: top",
        "objectclass: domain",
        "dc: example1");
    entries = TestCaseUtils.makeEntries(
        "dn: uid=user.0,ou=People,dc=test,dc=com",
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
        "dn: uid=user.1,ou=People,dc=test,dc=com",
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
        "dn: uid=user.2,ou=People,dc=test,dc=com",
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
        "dn: uid=user.3,ou=People,dc=test,dc=com",
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
        "dn: uid=user.4,ou=People,dc=test,dc=com",
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
        "dn: uid=user.5,ou=People,dc=test,dc=com",
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
        "dn: uid=user.6,ou=People,dc=test,dc=com",
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
        "dn: uid=user.7,ou=People,dc=test,dc=com",
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
        "dn: uid=user.8,ou=People,dc=test,dc=com",
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
        "dn: uid=user.9,ou=People,dc=test,dc=com",
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
        "dn: uid=user.10,ou=People,dc=test,dc=com",
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
        "dn: uid=user.539,ou=People,dc=test,dc=com",
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
        "description: This is the description for Ardyth Bainton.",
        "",
        "dn: uid=user.446,dc=test1,dc=com",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "givenName: Annalee",
        "sn: Avard",
        "cn: Annalee Avard",
        "initials: ANA",
        "employeeNumber: 446",
        "uid: user.446",
        "mail: user.446@example.com",
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
        "dn: uid=user.362,dc=test1,dc=com",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "givenName: Andaree",
        "sn: Asawa",
        "cn: Andaree Asawa",
        "initials: AEA",
        "employeeNumber: 362",
        "uid: user.362",
        "mail: user.362@example.com",
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

    replaceEntry = TestCaseUtils.makeEntry(
        "dn: uid=user.0,ou=People,dc=test,dc=com",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "givenName: Testing",
        "sn: Test",
        "cn: Testing Test",
        "initials: TT",
        "employeeNumber: 777",
        "uid: user.0",
        "mail: user.0@example.com",
        "userPassword: password",
        "telephoneNumber: 380-535-2354",
        "homePhone: 707-626-3913",
        "pager: 456-345-7750",
        "mobile: 366-674-7274",
        "carLicense: 366-674-7274",
        "street: 99262 Eleventh Street",
        "l: Salem",
        "st: NM",
        "postalCode: 36530",
        "postalAddress: Aaccf Amar$99262 Eleventh Street$Salem, NM  36530",
        "description: This is the description for Aaccf Amar.");

    additionalEntries = TestCaseUtils.makeEntries(
        "dn: uid=user.446,ou=People,dc=test,dc=com",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "givenName: Annalee",
        "sn: Avard",
        "cn: Annalee Avard",
        "initials: ANA",
        "employeeNumber: 446",
        "uid: user.446",
        "mail: user.446@example.com",
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
        "dn: uid=user.362,ou=People,dc=test,dc=com",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "givenName: Andaree",
        "sn: Asawa",
        "cn: Andaree Asawa",
        "initials: AEA",
        "employeeNumber: 362",
        "uid: user.362",
        "mail: user.362@example.com",
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

    newTop = TestCaseUtils.makeEntry(
        "dn: ou=JEB Testers,dc=test,dc=com",
        "objectclass: top",
        "objectclass: organizationalUnit",
        "ou: People"
    );

  }

  @AfterClass
  public void cleanUp() throws Exception {
  }

  @Test(expectedExceptions = DirectoryException.class)
  public void testAddNoParent() throws Exception {
    for (Entry entry : entries) {
      backend.addEntry(entry, null);
    }
  }

  @Test(dependsOnMethods = "testAddNoParent")
  public void testAdd() throws Exception {
    for (Entry topEntry : topEntries) {
      backend.addEntry(topEntry, null);
      assertNotNull(backend.getEntry(topEntry.getDN()));
    }

    for (Entry entry : entries) {
      backend.addEntry(entry, null);
      assertNotNull(backend.getEntry(entry.getDN()));
    }
  }

  @Test(dependsOnMethods = "testAdd")
  public void testSearchScope() throws Exception {
    InternalClientConnection conn =
        InternalClientConnection.getRootConnection();

    InternalSearchOperation search =
        conn.processSearch(DN.decode("dc=test,dc=com"), SearchScope.BASE_OBJECT,
            LDAPFilter.decode("(objectClass=*)").toSearchFilter());
    LinkedList<SearchResultEntry> result = search.getSearchEntries();

    assertEquals(result.size(), 1);
    assertEquals(result.get(0).getDN().toString(), "dc=test,dc=com");

    search = conn.processSearch(DN.decode("dc=test,dc=com"),
        SearchScope.BASE_OBJECT, LDAPFilter.decode("(ou=People)").toSearchFilter());
    result = search.getSearchEntries();

    assertEquals(result.size(), 0);

    search = conn.processSearch(DN.decode("dc=test,dc=com"),
        SearchScope.SINGLE_LEVEL,
        LDAPFilter.decode("(objectClass=*)").toSearchFilter());
    result = search.getSearchEntries();

    assertEquals(result.size(), 1);
    assertEquals(result.get(0).getDN().toString(),
        "ou=People,dc=test,dc=com");

    search = conn.processSearch(DN.decode("dc=test,dc=com"),
        SearchScope.SUBORDINATE_SUBTREE,
        LDAPFilter.decode("(objectClass=*)").toSearchFilter());
    result = search.getSearchEntries();

    assertEquals(result.size(), 13);
    for (Entry entry : result) {
      assertNotSame(entry.getDN().toString(), "dc=test,dc=com");
    }

    search = conn.processSearch(DN.decode("dc=test,dc=com"),
        SearchScope.WHOLE_SUBTREE,
        LDAPFilter.decode("(objectClass=*)").toSearchFilter());
    result = search.getSearchEntries();

    assertEquals(result.size(), 14);
  }

  @Test(dependsOnMethods = "testAdd")
  public void testNumSubordinates() throws Exception
  {
    DN dn = DN.decode("dc=test,dc=com");
    assertEquals(backend.numSubordinates(dn, false), 1);
    assertEquals(backend.numSubordinates(dn, true), 13);
    dn = DN.decode("ou=People,dc=test,dc=com");
    assertEquals(backend.numSubordinates(dn, false), 12);
    assertEquals(backend.numSubordinates(dn, true), 12);
    dn = DN.decode("dc=com");
    assertEquals(backend.numSubordinates(dn, false), -1);
    assertEquals(backend.numSubordinates(dn, true), -1);
    dn = DN.decode("dc=test1,dc=com");
    assertEquals(backend.numSubordinates(dn, false), 2);
    assertEquals(backend.numSubordinates(dn, true), 2);
    dn = DN.decode("uid=user.10,ou=People,dc=test,dc=com");
    assertEquals(backend.numSubordinates(dn, false), 0);
    assertEquals(backend.numSubordinates(dn, true), 0);
    dn = DN.decode("uid=does not exist,ou=People,dc=test,dc=com");
    assertEquals(backend.numSubordinates(dn, false), -1);
    assertEquals(backend.numSubordinates(dn, true), -1);
  }

  @Test(dependsOnMethods = "testAdd")
  public void testSearchIndex() throws Exception {
    InternalClientConnection conn =
        InternalClientConnection.getRootConnection();
    LinkedHashSet<String> attribs = new LinkedHashSet<String>();

    String debugString;
    int finalStartPos;
    int finalEndPos;
    int finalCount;

    InternalSearchOperation search =
        conn.processSearch(DN.decode("dc=test,dc=com"),

            SearchScope.WHOLE_SUBTREE,

            DereferencePolicy.NEVER_DEREF_ALIASES,
            0,
            0,
            false,

            LDAPFilter.decode("(&(cn=Aaccf Amar)(cn=Ardyth Bainton))").toSearchFilter(),
            attribs);

    LinkedList<SearchResultEntry> result = search.getSearchEntries();

    assertEquals(result.size(), 0);

    attribs.add(ATTR_DEBUG_SEARCH_INDEX);

    search = conn.processSearch(DN.decode("dc=test,dc=com"),
        SearchScope.WHOLE_SUBTREE,
        DereferencePolicy.NEVER_DEREF_ALIASES,
        0,
        0,
        false,
        LDAPFilter.decode("(&(cn=Aaccf Amar)(employeeNumber=222))").toSearchFilter(),
        attribs);

    result = search.getSearchEntries();

    //Only one index should be used because it is below the FILTER_CANDIDATEassertEquals(ec.getDN2URI().)_THRESHOLD.
    debugString =
        result.get(0).getAttribute("debugsearchindex").get(0).toString();
    assertTrue(debugString.split("cn").length <= 3);
    finalStartPos = debugString.indexOf("final=") + 13;
    finalEndPos = debugString.indexOf("]", finalStartPos);
    finalCount = Integer.valueOf(debugString.substring(finalStartPos,
        finalEndPos));
    assertEquals(finalCount, 1);

    search = conn.processSearch(DN.decode("dc=test,dc=com"),
        SearchScope.WHOLE_SUBTREE,
        DereferencePolicy.NEVER_DEREF_ALIASES,
        0,
        0,
        false,
        LDAPFilter.decode("(|(cn=Aaccf Amar)(cn=Ardyth Bainton))").toSearchFilter(),
        attribs);

    result = search.getSearchEntries();

    debugString =
        result.get(0).getAttribute("debugsearchindex").get(0).toString();
    assertTrue(!debugString.contains("NOT-INDEXED"));
    finalStartPos = debugString.indexOf("final=") + 13;
    finalEndPos = debugString.indexOf("]", finalStartPos);
    finalCount = Integer.valueOf(debugString.substring(finalStartPos,
        finalEndPos));
    assertEquals(finalCount, 2);

    search = conn.processSearch(DN.decode("dc=test,dc=com"),
        SearchScope.WHOLE_SUBTREE,
        DereferencePolicy.NEVER_DEREF_ALIASES,
        0,
        0,
        false,

        LDAPFilter.decode("(&(employeeNumber=*)(cn=A*)(employeeNumber>=0)(employeeNumber<=z))").toSearchFilter(),
        attribs);
    result = search.getSearchEntries();

    debugString =
        result.get(0).getAttribute("debugsearchindex").get(0).toString();
    assertTrue(!debugString.contains("NOT-INDEXED"));
    finalStartPos = debugString.indexOf("final=") + 13;
    finalEndPos = debugString.indexOf("]", finalStartPos);
    finalCount = Integer.valueOf(debugString.substring(finalStartPos,
        finalEndPos));
    assertEquals(finalCount, 12);

    search = conn.processSearch(DN.decode("dc=test,dc=com"),
        SearchScope.WHOLE_SUBTREE,
        DereferencePolicy.NEVER_DEREF_ALIASES,
        0,
        0,
        false,

        LDAPFilter.decode("(&(employeeNumber<=z)(cn<=Abbey Abbie)(cn>=0)(|(cn>=Abahri Abazari)(employeeNumber<=9)))").toSearchFilter(),
        attribs);
    result = search.getSearchEntries();

    debugString =
        result.get(0).getAttribute("debugsearchindex").get(0).toString();
    assertTrue(!debugString.contains("NOT-INDEXED"));
    finalStartPos = debugString.indexOf("final=") + 13;
    finalEndPos = debugString.indexOf("]", finalStartPos);
    finalCount = Integer.valueOf(debugString.substring(finalStartPos,
        finalEndPos));
    assertEquals(finalCount, 11);

    search = conn.processSearch(DN.decode("dc=test,dc=com"),
        SearchScope.WHOLE_SUBTREE,
        DereferencePolicy.NEVER_DEREF_ALIASES,
        0,
        0,
        false,

        LDAPFilter.decode("(cn~=Aartjan)").toSearchFilter(),
        attribs);
    result = search.getSearchEntries();

    debugString =
        result.get(0).getAttribute("debugsearchindex").get(0).toString();
    assertTrue(!debugString.contains("NOT-INDEXED"));
    finalStartPos = debugString.indexOf("final=") + 13;
    finalEndPos = debugString.indexOf("]", finalStartPos);
    finalCount = Integer.valueOf(debugString.substring(finalStartPos,
        finalEndPos));
    assertEquals(finalCount, 1);
  }

  @Test(dependsOnMethods = {"testAdd", "testSearchIndex",
      "testSearchScope", "testSearchNotIndexed", "testModifyDNNewSuperior",
      "testMatchedDN", "testNumSubordinates",
      "testNumSubordinatesIndexEntryLimitExceeded"})
  public void testDeleteSubtree() throws Exception {
    Control control = new Control(OID_SUBTREE_DELETE_CONTROL, false);
    ArrayList<Control> deleteSubTreeControl = new ArrayList<Control>();
    deleteSubTreeControl.add(control);
    InternalClientConnection conn =
        InternalClientConnection.getRootConnection();

    DeleteOperationBasis delete = new DeleteOperationBasis(conn,
        conn.nextOperationID(),
        conn.nextMessageID(),
        deleteSubTreeControl,

        DN.decode("dc=test1,dc=com"));

    backend.deleteEntry(DN.decode("dc=test1,dc=com"), delete);

    EntryContainer ec =
        backend.getRootContainer().getEntryContainer(DN.decode("dc=test1,dc=com"));
    ec.sharedLock.lock();
    try
    {
      assertFalse(ec.entryExists(DN.decode("dc=test1,dc=com")));
      assertFalse(ec.entryExists(DN.decode("uid=user.362,dc=test1,dc=com")));
    }
    finally
    {
      ec.sharedLock.unlock();
    }
  }

  @Test(dependsOnMethods = {"testAdd", "testSearchIndex",
      "testSearchScope", "testMatchedDN"})
  public void testDeleteEntry() throws Exception {
    ArrayList<Control> noControls = new ArrayList<Control>(0);
    InternalClientConnection conn =
        InternalClientConnection.getRootConnection();

    Entry entry;
    EntryID entryID;
    AttributeType attribute;
    AttributeIndex index;
    HashSet<byte[]> addKeys;
    DatabaseEntry key;
    PresenceIndexer presenceIndexer;
    EqualityIndexer equalityIndexer;
    SubstringIndexer substringIndexer;
    OrderingIndexer orderingIndexer;

    EntryContainer ec =
        backend.getRootContainer().getEntryContainer(DN.decode("ou=People,dc=test,dc=com"));

    ec.sharedLock.lock();
    try
    {
      entry = ec.getEntry(DN.decode("uid=user.539,ou=People,dc=test,dc=com"));
      entryID = ec.getDN2ID().get(null,
          DN.decode("uid=user.539,ou=People,dc=test,dc=com"), LockMode.DEFAULT);

      DeleteOperationBasis delete = new DeleteOperationBasis(conn,
        conn.nextOperationID(),
        conn.nextMessageID(),
        noControls,

          DN.decode("uid=user.539,ou=People,dc=test,dc=com"));


      backend.deleteEntry(DN.decode("uid=user.539,ou=People,dc=test,dc=com"),
          delete);


      assertFalse(ec.entryExists(DN.decode("uid=user.539,ou=People,dc=test,dc=com")));
      assertNull(ec.getDN2ID().get(null,
          DN.decode("uid=user.539,ou=People,dc=test,dc=com"), LockMode.DEFAULT));
      assertFalse(ec.getDN2URI().delete(null,
          DN.decode("uid=user.539,ou=People,dc=test,dc=com")));

      attribute = entries.get(0).getAttribute("cn").get(0).getAttributeType();
      index = ec.getAttributeIndex(attribute);


      addKeys = new HashSet<byte[]>();
      presenceIndexer = new PresenceIndexer(index.getAttributeType());
      presenceIndexer.indexEntry(entry, addKeys);

      key = new DatabaseEntry();
      for (byte[] keyBytes : addKeys) {
        key.setData(keyBytes);
      }
      assertEquals(index.presenceIndex.containsID(null, key, entryID),
          ConditionResult.FALSE);

      addKeys = new HashSet<byte[]>();
      equalityIndexer = new EqualityIndexer(index.getAttributeType());
      equalityIndexer.indexEntry(entry, addKeys);

      key = new DatabaseEntry();
      for (byte[] keyBytes : addKeys) {
        key.setData(keyBytes);
      }
      assertEquals(index.equalityIndex.containsID(null, key, entryID),
          ConditionResult.FALSE);

      addKeys = new HashSet<byte[]>();
      substringIndexer = new SubstringIndexer(index.getAttributeType(),
                   index.getConfiguration().getSubstringLength());
      substringIndexer.indexEntry(entry, addKeys);

      key = new DatabaseEntry();
      for (byte[] keyBytes : addKeys) {
        key.setData(keyBytes);
      }
      assertEquals(index.substringIndex.containsID(null, key, entryID),
          ConditionResult.FALSE);

      addKeys = new HashSet<byte[]>();
      orderingIndexer = new OrderingIndexer(index.getAttributeType());
      orderingIndexer.indexEntry(entry, addKeys);

      key = new DatabaseEntry();
      for (byte[] keyBytes : addKeys) {
        key.setData(keyBytes);
      }
      assertEquals(index.orderingIndex.containsID(null, key, entryID),
          ConditionResult.FALSE);
    }
    finally
    {
      ec.sharedLock.unlock();
    }
  }

  @Test(dependsOnMethods = {"testSearchNotIndexed", "testAdd",
      "testSearchIndex", "testSearchScope", "testMatchedDN",
      "testNumSubordinates", "testNumSubordinatesIndexEntryLimitExceeded"})
  public void testReplaceEntry() throws Exception {
    Entry entry;
    Entry oldEntry;
    EntryID entryID;
    AttributeType attribute;
    AttributeIndex index;
    HashSet<byte[]> addKeys;
    DatabaseEntry key;
    EqualityIndexer equalityIndexer;
    SubstringIndexer substringIndexer;
    OrderingIndexer orderingIndexer;

    oldEntry = entries.get(0);
    backend.replaceEntry(oldEntry, replaceEntry, null);

    EntryContainer ec =
        backend.getRootContainer().getEntryContainer(DN.decode("dc=test,dc=com"));
    ec.sharedLock.lock();
    try
    {
      entry = ec.getEntry(DN.decode("uid=user.0,ou=People,dc=test,dc=com"));
      entryID = ec.getDN2ID().get(null,
          DN.decode("uid=user.0,ou=People,dc=test,dc=com"), LockMode.DEFAULT);

      assertNotNull(entry);
      for (AttributeValue value : entry.getAttribute("cn").get(0)) {
        assertEquals(value.getStringValue(), "Testing Test");
      }
      for (AttributeValue value : entry.getAttribute("sn").get(0)) {
        assertEquals(value.getStringValue(), "Test");
      }
      for (AttributeValue value : entry.getAttribute("givenname").get(0)) {
        assertEquals(value.getStringValue(), "Testing");
      }
      for (AttributeValue value : entry.getAttribute("employeenumber").get(0)) {
        assertEquals(value.getStringValue(), "777");
      }

      attribute = entry.getAttribute("cn").get(0).getAttributeType();
      index = ec.getAttributeIndex(attribute);

      addKeys = new HashSet<byte[]>();
      orderingIndexer = new OrderingIndexer(index.getAttributeType());
      orderingIndexer.indexEntry(entry, addKeys);

      key = new DatabaseEntry();
      for (byte[] keyBytes : addKeys) {
        key.setData(keyBytes);
      }
      assertEquals(index.orderingIndex.containsID(null, key, entryID),
          ConditionResult.TRUE);

      addKeys = new HashSet<byte[]>();
      orderingIndexer.indexEntry(oldEntry, addKeys);

      key = new DatabaseEntry();
      for (byte[] keyBytes : addKeys) {
        key.setData(keyBytes);
      }
      assertEquals(index.orderingIndex.containsID(null, key, entryID),
                   ConditionResult.FALSE);

      addKeys = new HashSet<byte[]>();
      substringIndexer = new SubstringIndexer(index.getAttributeType(),
                                              index.getConfiguration().getSubstringLength());
      substringIndexer.indexEntry(entry, addKeys);

      key = new DatabaseEntry();
      for (byte[] keyBytes : addKeys) {
        key.setData(keyBytes);
      }
      assertEquals(index.substringIndex.containsID(null, key, entryID),
                   ConditionResult.TRUE);

      addKeys = new HashSet<byte[]>();
      substringIndexer.indexEntry(oldEntry, addKeys);

      key = new DatabaseEntry();
      for (byte[] keyBytes : addKeys) {
        key.setData(keyBytes);
      }
      assertEquals(index.substringIndex.containsID(null, key, entryID),
                   ConditionResult.FALSE);

      addKeys = new HashSet<byte[]>();
      equalityIndexer = new EqualityIndexer(index.getAttributeType());
      equalityIndexer.indexEntry(entry, addKeys);

      key = new DatabaseEntry();
      for (byte[] keyBytes : addKeys) {
        key.setData(keyBytes);
      }
      assertEquals(index.equalityIndex.containsID(null, key, entryID),
          ConditionResult.TRUE);

      addKeys = new HashSet<byte[]>();
      equalityIndexer.indexEntry(oldEntry, addKeys);

      key = new DatabaseEntry();
      for (byte[] keyBytes : addKeys) {
        key.setData(keyBytes);
      }
      assertEquals(index.equalityIndex.containsID(null, key, entryID),
          ConditionResult.FALSE);
    }
    finally
    {
      ec.sharedLock.unlock();
    }

  }

  @Test(dependsOnMethods = {"testSearchNotIndexed", "testAdd",
      "testSearchIndex", "testSearchScope", "testMatchedDN",
      "testNumSubordinates", "testNumSubordinatesIndexEntryLimitExceeded"})
  public void testModifyEntry() throws Exception
  {
    Entry entry;
    Entry newEntry;
    EntryID entryID;
    AttributeType attribute;
    AttributeIndex titleIndex;
    AttributeIndex nameIndex;
    HashSet<byte[]> addKeys;
    DatabaseEntry key;
    PresenceIndexer presenceIndexer;
    EqualityIndexer equalityIndexer;
    SubstringIndexer substringIndexer;
    OrderingIndexer orderingIndexer;

    EntryContainer ec = backend.getRootContainer().getEntryContainer(
        DN.decode("dc=test,dc=com"));
    ec.sharedLock.lock();
    try
    {
      ArrayList<Modification> modifications = new ArrayList<Modification>();
      modifications.add(new Modification(ModificationType.ADD, Attributes
          .create("title", "debugger")));

      AttributeBuilder builder = new AttributeBuilder("title");
      builder.setOption("lang-en");
      builder.add("debugger2");

      modifications.add(new Modification(ModificationType.ADD, builder
          .toAttribute()));
      modifications.add(new Modification(ModificationType.DELETE,
          Attributes.create("cn", "Aaren Atp")));
      modifications.add(new Modification(ModificationType.ADD, Attributes
          .create("cn", "Aaren Rigor")));
      modifications.add(new Modification(ModificationType.ADD, Attributes
          .create("cn", "Aarenister Rigor")));

      builder = new AttributeBuilder("givenname");
      builder.add("test");
      builder.setOption("lang-de");
      modifications.add(new Modification(ModificationType.ADD, builder
          .toAttribute()));

      builder = new AttributeBuilder("givenname");
      builder.add("test2");
      builder.setOption("lang-cn");
      modifications.add(new Modification(ModificationType.DELETE, builder
          .toAttribute()));

      builder = new AttributeBuilder("givenname");
      builder.add("newtest3");
      builder.setOption("lang-es");
      modifications.add(new Modification(ModificationType.REPLACE, builder
          .toAttribute()));

      modifications.add(new Modification(ModificationType.REPLACE,
          Attributes.create("employeenumber", "222")));

      newEntry = entries.get(1);
      newEntry.applyModifications(modifications);
      entry = ec.getEntry(DN.decode("uid=user.1,ou=People,dc=test,dc=com"));
      entryID = ec.getDN2ID().get(null,
          DN.decode("uid=user.1,ou=People,dc=test,dc=com"), LockMode.DEFAULT);

      assertNotNull(entryID);

      attribute = DirectoryServer.getAttributeType("title");
      titleIndex = ec.getAttributeIndex(attribute);
      attribute = DirectoryServer.getAttributeType("name");
      nameIndex = ec.getAttributeIndex(attribute);

      // This current entry in the DB shouldn't be in the presence
      // titleIndex.
      addKeys = new HashSet<byte[]>();
      addKeys.add(AttributeIndex.presenceKey.getData());
      key = new DatabaseEntry();
      for (byte[] keyBytes : addKeys)
      {
        key.setData(keyBytes);
      }
      assertEquals(titleIndex.presenceIndex.containsID(null, key, entryID),
          ConditionResult.FALSE);

      // This current entry should be in the presence nameIndex.
      addKeys = new HashSet<byte[]>();
      addKeys.add(AttributeIndex.presenceKey.getData());
      key = new DatabaseEntry();
      for (byte[] keyBytes : addKeys)
      {
        key.setData(keyBytes);
      }
      assertEquals(nameIndex.presenceIndex.containsID(null, key, entryID),
          ConditionResult.TRUE);

      ArrayList<Control> noControls = new ArrayList<Control>(0);
      InternalClientConnection conn = InternalClientConnection
          .getRootConnection();

      ModifyOperationBasis modifyOp = new ModifyOperationBasis(conn, conn
          .nextOperationID(), conn.nextMessageID(), noControls, DN
          .decode("uid=user.1,ou=People,dc=test,dc=com"), modifications);

      backend.replaceEntry(entry, newEntry, modifyOp);

      entry = ec.getEntry(DN.decode("uid=user.1,ou=People,dc=test,dc=com"));

      assertTrue(entry.getAttribute("title").contains(
          Attributes.create("title", "debugger")));

      assertTrue(entry.getAttribute("cn").get(0)
          .contains(
              new AttributeValue(entry.getAttribute("cn").get(0)
                  .getAttributeType(), "Aaren Rigor")));
      assertTrue(entry.getAttribute("cn").get(0).contains(
          new AttributeValue(
              entry.getAttribute("cn").get(0).getAttributeType(),
              "Aarenister Rigor")));
      assertFalse(entry.getAttribute("cn").get(0).contains(
          new AttributeValue(
              entry.getAttribute("cn").get(0).getAttributeType(), "Aaren Atp")));

      Set<String> options = new LinkedHashSet<String>();
      options.add("lang-de");
      assertTrue(entry.getAttribute("givenname", options).get(0).contains(
          new AttributeValue(entry.getAttribute("givenname", options).get(0)
              .getAttributeType(), "test")));
      options = new LinkedHashSet<String>();
      options.add("lang-cn");
      assertNull(entry.getAttribute("givenname", options));
      options = new LinkedHashSet<String>();
      options.add("lang-es");
      assertTrue(entry.getAttribute("givenname", options).get(0).contains(
          new AttributeValue(entry.getAttribute("givenname", options).get(0)
              .getAttributeType(), "newtest3")));
      options = new LinkedHashSet<String>();
      options.add("lang-fr");
      assertTrue(entry.getAttribute("givenname", options).get(0).contains(
          new AttributeValue(entry.getAttribute("givenname", options).get(0)
              .getAttributeType(), "test2")));

      assertTrue(entry.getAttribute("employeenumber").contains(
          Attributes.create("employeenumber", "222")));
      assertFalse(entry.getAttribute("employeenumber").contains(
          Attributes.create("employeenumber", "1")));

      addKeys = new HashSet<byte[]>();
      presenceIndexer = new PresenceIndexer(titleIndex.getAttributeType());
      presenceIndexer.indexEntry(entry, addKeys);

      key = new DatabaseEntry();
      for (byte[] keyBytes : addKeys)
      {
        key.setData(keyBytes);
        assertEquals(titleIndex.presenceIndex.containsID(null, key, entryID),
            ConditionResult.TRUE);
      }

      addKeys = new HashSet<byte[]>();
      presenceIndexer = new PresenceIndexer(nameIndex.getAttributeType());
      presenceIndexer.indexEntry(entry, addKeys);

      key = new DatabaseEntry();
      for (byte[] keyBytes : addKeys)
      {
        key.setData(keyBytes);
        assertEquals(nameIndex.presenceIndex.containsID(null, key, entryID),
            ConditionResult.TRUE);
      }

      addKeys = new HashSet<byte[]>();
      orderingIndexer = new OrderingIndexer(titleIndex.getAttributeType());
      orderingIndexer.indexEntry(entry, addKeys);

      key = new DatabaseEntry();
      for (byte[] keyBytes : addKeys)
      {
        key.setData(keyBytes);
        assertEquals(titleIndex.orderingIndex.containsID(null, key, entryID),
            ConditionResult.TRUE);
      }

      addKeys = new HashSet<byte[]>();
      orderingIndexer = new OrderingIndexer(nameIndex.getAttributeType());
      orderingIndexer.indexEntry(entry, addKeys);

      key = new DatabaseEntry();
      for (byte[] keyBytes : addKeys)
      {
        key.setData(keyBytes);
        assertEquals(nameIndex.orderingIndex.containsID(null, key, entryID),
            ConditionResult.TRUE);
      }

      addKeys = new HashSet<byte[]>();
      equalityIndexer = new EqualityIndexer(titleIndex.getAttributeType());
      equalityIndexer.indexEntry(entry, addKeys);

      key = new DatabaseEntry();
      for (byte[] keyBytes : addKeys)
      {
        key.setData(keyBytes);
        assertEquals(titleIndex.equalityIndex.containsID(null, key, entryID),
            ConditionResult.TRUE);
      }

      addKeys = new HashSet<byte[]>();
      equalityIndexer = new EqualityIndexer(nameIndex.getAttributeType());
      equalityIndexer.indexEntry(entry, addKeys);

      key = new DatabaseEntry();
      for (byte[] keyBytes : addKeys)
      {
        key.setData(keyBytes);
        assertEquals(nameIndex.equalityIndex.containsID(null, key, entryID),
            ConditionResult.TRUE);
      }

      addKeys = new HashSet<byte[]>();
      substringIndexer = new SubstringIndexer(titleIndex.getAttributeType(),
          titleIndex.getConfiguration().getSubstringLength());
      substringIndexer.indexEntry(entry, addKeys);

      key = new DatabaseEntry();
      for (byte[] keyBytes : addKeys)
      {
        key.setData(keyBytes);
        assertEquals(titleIndex.substringIndex.containsID(null, key, entryID),
            ConditionResult.TRUE);
      }

      addKeys = new HashSet<byte[]>();
      substringIndexer = new SubstringIndexer(nameIndex.getAttributeType(),
          nameIndex.getConfiguration().getSubstringLength());
      substringIndexer.indexEntry(entry, addKeys);

      key = new DatabaseEntry();
      for (byte[] keyBytes : addKeys)
      {
        key.setData(keyBytes);
        assertEquals(nameIndex.substringIndex.containsID(null, key, entryID),
            ConditionResult.TRUE);
      }
    }
    finally
    {
      ec.sharedLock.unlock();
    }

  }

  @Test(dependsOnMethods = {"testAdd", "testSearchIndex", "testSearchScope",
      "testMatchedDN"})
  public void testModifyDN() throws Exception {
    EntryContainer ec =
        backend.getRootContainer().getEntryContainer(DN.decode("dc=test,dc=com"));
    ec.sharedLock.lock();
    try
    {
      Entry entry =
          ec.getEntry(DN.decode("uid=user.2,ou=People,dc=test,dc=com"));
      entry.setDN(DN.decode("cn=Abbey Abbie,ou=People,dc=test,dc=com"));


      backend.renameEntry(DN.decode("uid=user.2,ou=People,dc=test,dc=com"),
          entry, null);

      assertNotNull(backend.getEntry(DN.decode("cn=Abbey Abbie,ou=People,dc=test,dc=com")));
      assertNotNull(ec.getDN2ID().get(null, DN.decode("cn=Abbey Abbie,ou=People,dc=test,dc=com"), LockMode.DEFAULT));


      assertNull(backend.getEntry(DN.decode("uid=user.2,ou=People,dc=test,dc=com")));
      assertNull(ec.getDN2ID().get(null,
          DN.decode("uid=user.2,ou=People,dc=test,dc=com"), LockMode.DEFAULT));
    }
    finally
    {
      ec.sharedLock.unlock();
    }
  }

  @Test(dependsOnMethods = {"testSearchNotIndexed", "testAdd", "testSearchIndex",
      "testSearchScope", "testModifyEntry", "testModifyDN", "testReplaceEntry",
      "testDeleteEntry", "testMatchedDN", "testNumSubordinates",
      "testNumSubordinatesIndexEntryLimitExceeded"})
  public void testModifyDNNewSuperior() throws Exception {
    //Add the new superior entry we want to move to. Test to see if the child ID
    //always above parent invarient is preseved.
    backend.addEntry(newTop, null);

    EntryContainer ec =
        backend.getRootContainer().getEntryContainer(DN.decode("dc=test,dc=com"));
    ec.sharedLock.lock();
    try
    {
      EntryID newSuperiorID = ec.getDN2ID().get(null, DN.decode("ou=JEB Testers,dc=test,dc=com"), LockMode.DEFAULT);
      EntryID oldID = ec.getDN2ID().get(null,
          DN.decode("ou=People,dc=test,dc=com"), LockMode.DEFAULT);
      assertTrue(newSuperiorID.compareTo(oldID) > 0);

      ArrayList<Control> noControls = new ArrayList<Control>(0);
      InternalClientConnection conn =
          InternalClientConnection.getRootConnection();

      ModifyDNOperationBasis modifyDN = new ModifyDNOperationBasis(conn,
          conn.nextOperationID(),
          conn.nextMessageID(),
          noControls,
          DN.decode("ou=People,dc=test,dc=com"),
          RDN.decode("ou=Good People"),
          false,
          DN.decode("ou=JEB Testers,dc=test,dc=com"));

      modifyDN.run();

      assertNotNull(backend.getEntry(DN.decode("ou=Good People,ou=JEB Testers,dc=test,dc=com")));
      EntryID newID = ec.getDN2ID().get(null, DN.decode("ou=Good People,ou=JEB Testers,dc=test,dc=com"), LockMode.DEFAULT);
      assertNotNull(newID);
      assertTrue(newID.compareTo(newSuperiorID) > 0);
      assertNotNull(backend.getEntry(DN.decode("uid=user.0,ou=Good People,ou=JEB Testers,dc=test,dc=com")));
      EntryID newSubordinateID = ec.getDN2ID().get(null,
          DN.decode("uid=user.0,ou=Good People,ou=JEB Testers,dc=test,dc=com"), LockMode.DEFAULT);
      assertTrue(newSubordinateID.compareTo(newID) > 0);

      assertNull(backend.getEntry(DN.decode("ou=People,dc=test,dc=com")));
      assertNull(ec.getDN2ID().get(null,
          DN.decode("ou=People,dc=test,dc=com"), LockMode.DEFAULT));
    }
    finally
    {
      ec.sharedLock.unlock();
    }
  }

  @Test(dependsOnMethods = {"testModifyDN",
      "testSearchScope", "testSearchIndex", "testReplaceEntry",
      "testModifyEntry", "testModifyDN", "testDeleteSubtree",
      "testDeleteEntry", "testAddNoParent", "testAdd",
      "testSearchNotIndexed",
      "testModifyDNNewSuperior", "testApplyIndexConfig", "testMatchedDN"})
  public void testApplyConfig() throws Exception {
    Entry configEntry = TestCaseUtils.makeEntry(
        "dn: ds-cfg-backend-id=indexRoot,cn=Backends,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-backend",
        "objectClass: ds-cfg-local-db-backend",
        "ds-cfg-base-dn: dc=test,dc=com",
        "ds-cfg-base-dn: dc=newsuffix,dc=com",
        "ds-cfg-enabled: true",
        "ds-cfg-writability-mode: enabled",
        "ds-cfg-java-class: org.opends.server.backends.jeb.BackendImpl",
        "ds-cfg-backend-id: indexRoot",
        "ds-cfg-db-directory:: " +
            Base64.encode(homeDirName.getBytes()),
        "ds-cfg-import-temp-directory: import-tmp");

    LocalDBBackendCfg cfg = AdminTestCaseUtils.getConfiguration(
         LocalDBBackendCfgDefn.getInstance(), configEntry);

    backend.applyConfigurationChange(cfg);

    RootContainer rootContainer = backend.getRootContainer();

    assertNull(rootContainer.getEntryContainer(DN.decode("dc=test1,dc=com")));

    assertNotNull(rootContainer.getEntryContainer(DN.decode("dc=newsuffix,dc=com")));
  }

  @Test(dependsOnMethods = {"testModifyDN",
      "testSearchScope", "testSearchIndex", "testReplaceEntry",
      "testModifyEntry", "testModifyDN", "testDeleteSubtree",
      "testDeleteEntry", "testAddNoParent", "testAdd",
      "testSearchNotIndexed",
      "testModifyDNNewSuperior", "testMatchedDN"})
  public void testApplyIndexConfig() throws Exception {
    int resultCode = TestCaseUtils.applyModifications(true,
        "dn: ds-cfg-attribute=givenName,cn=Index," +
            "ds-cfg-backend-id=indexRoot,cn=Backends,cn=config",
        "changetype: modify",
        "replace: ds-cfg-index-type",
        "ds-cfg-index-type: approximate");

    assertEquals(resultCode, 0);

    RootContainer rootContainer = backend.getRootContainer();
    EntryContainer ec = rootContainer.getEntryContainer(DN.decode("dc=test,dc=com"));

    AttributeIndex index =
        ec.getAttributeIndex(DirectoryServer.getAttributeType("givenname"));
    assertNull(index.equalityIndex);
    assertNull(index.presenceIndex);
    assertNull(index.substringIndex);
    assertNull(index.orderingIndex);
    assertNotNull(index.approximateIndex);
    ArrayList<DatabaseContainer> databases = new ArrayList<DatabaseContainer>();
    ec.listDatabases(databases);
    boolean eqfound = false;
    boolean prfound = false;
    boolean subfound = false;
    boolean orfound = false;
    boolean apfound = false;
    for(DatabaseContainer dc : databases)
    {
      if(dc.getName().toLowerCase().contains("givenname.approximate"))
      {
        apfound = true;
      }
      if(dc.getName().toLowerCase().contains("givenname.presence"))
      {
        prfound = true;
      }
      if(dc.getName().toLowerCase().contains("givenname.substring"))
      {
        subfound = true;
      }
      if(dc.getName().toLowerCase().contains("givenname.ordering"))
      {
        orfound = true;
      }
      if(dc.getName().toLowerCase().contains("givenname.equality"))
      {
        eqfound = true;
      }
    }
    assertFalse(eqfound);
    assertFalse(prfound);
    assertFalse(subfound);
    assertFalse(orfound);
    assertTrue(apfound);

    InternalClientConnection conn =
        InternalClientConnection.getRootConnection();
    LinkedHashSet<String> attribs = new LinkedHashSet<String>();
    attribs.add(ATTR_DEBUG_SEARCH_INDEX);

    InternalSearchOperation search =
        conn.processSearch(DN.decode("dc=test,dc=com"),

                           SearchScope.SUBORDINATE_SUBTREE,

                           DereferencePolicy.NEVER_DEREF_ALIASES,
                           0,
                           0,
                           false,

                           LDAPFilter.decode("(givenName~=Aaccf)").
                               toSearchFilter(),
                           attribs);

    LinkedList<SearchResultEntry> result = search.getSearchEntries();

    //No indexes should be used.
    String debugString =
        result.get(0).getAttribute("debugsearchindex").get(0).toString();
    assertTrue(debugString.contains("NOT-INDEXED"));

    resultCode = TestCaseUtils.applyModifications(true,
        "dn: ds-cfg-attribute=givenName,cn=Index," +
            "ds-cfg-backend-id=indexRoot,cn=Backends,cn=config",
        "changetype: modify",
        "replace: ds-cfg-index-type",
        "ds-cfg-index-type: equality",
        "ds-cfg-index-type: presence",
        "ds-cfg-index-type: ordering",
        "ds-cfg-index-type: substring");

    assertEquals(resultCode, 0);

    assertNotNull(index.equalityIndex);
    assertNotNull(index.presenceIndex);
    assertNotNull(index.substringIndex);
    assertNotNull(index.orderingIndex);
    assertNull(index.approximateIndex);
    databases = new ArrayList<DatabaseContainer>();
    ec.listDatabases(databases);
    eqfound = false;
    prfound = false;
    subfound = false;
    orfound = false;
    apfound = false;
    for(DatabaseContainer dc : databases)
    {
      if(dc.getName().toLowerCase().contains("givenname.approximate"))
      {
        apfound = true;
      }
      if(dc.getName().toLowerCase().contains("givenname.presence"))
      {
        prfound = true;
      }
      if(dc.getName().toLowerCase().contains("givenname.substring"))
      {
        subfound = true;
      }
      if(dc.getName().toLowerCase().contains("givenname.ordering"))
      {
        orfound = true;
      }
      if(dc.getName().toLowerCase().contains("givenname.equality"))
      {
        eqfound = true;
      }
    }
    assertTrue(eqfound);
    assertTrue(prfound);
    assertTrue(subfound);
    assertTrue(orfound);
    assertFalse(apfound);

    // Delete the entries attribute index.
    resultCode = TestCaseUtils.applyModifications(true,
        "dn: ds-cfg-attribute=givenName,cn=Index," +
            "ds-cfg-backend-id=indexRoot,cn=Backends,cn=config",
        "changetype: delete");

    assertEquals(resultCode, 0);

    assertNull(ec.getAttributeIndex(
        DirectoryServer.getAttributeType("givenname")));
    databases = new ArrayList<DatabaseContainer>();
    ec.listDatabases(databases);
    for(DatabaseContainer dc : databases)
    {
      assertFalse(dc.getName().toLowerCase().contains("givenname"));
    }

    // Add it back
    resultCode = TestCaseUtils.applyModifications(true,
        "dn: ds-cfg-attribute=givenName,cn=Index," +
            "ds-cfg-backend-id=indexRoot,cn=Backends,cn=config",
        "changetype: add",
        "objectClass: top",
        "objectClass: ds-cfg-local-db-index",
        "ds-cfg-attribute: givenName",
        "ds-cfg-index-type: equality",
        "ds-cfg-index-type: presence",
        "ds-cfg-index-type: ordering",
        "ds-cfg-index-type: substring");

    assertEquals(resultCode, 0);

    assertNotNull(ec.getAttributeIndex(
        DirectoryServer.getAttributeType("givenname")));
    databases = new ArrayList<DatabaseContainer>();
    ec.listDatabases(databases);
    eqfound = false;
    prfound = false;
    subfound = false;
    orfound = false;
    apfound = false;
    for(DatabaseContainer dc : databases)
    {
      if(dc.getName().toLowerCase().contains("givenname.approximate"))
      {
        apfound = true;
      }
      if(dc.getName().toLowerCase().contains("givenname.presence"))
      {
        prfound = true;
      }
      if(dc.getName().toLowerCase().contains("givenname.substring"))
      {
        subfound = true;
      }
      if(dc.getName().toLowerCase().contains("givenname.ordering"))
      {
        orfound = true;
      }
      if(dc.getName().toLowerCase().contains("givenname.equality"))
      {
        eqfound = true;
      }
    }
    assertTrue(eqfound);
    assertTrue(prfound);
    assertTrue(subfound);
    assertTrue(orfound);
    assertFalse(apfound);

    // Make sure changing the index entry limit on an index where the limit
    // is already exceeded causes warnings.
    resultCode = TestCaseUtils.applyModifications(true,
        "dn: ds-cfg-attribute=mail,cn=Index," +
            "ds-cfg-backend-id=indexRoot,cn=Backends,cn=config",
        "changetype: modify",
        "replace: ds-cfg-index-entry-limit",
        "ds-cfg-index-entry-limit: 30");

    assertEquals(resultCode, 0);

    // Make sure removing a index entry limit for an index makes it use the
    // backend wide setting.
    resultCode = TestCaseUtils.applyModifications(true,
        "dn: ds-cfg-attribute=mail,cn=Index," +
            "ds-cfg-backend-id=indexRoot,cn=Backends,cn=config",
        "changetype: modify",
        "delete: ds-cfg-index-entry-limit");

    assertEquals(resultCode, 0);
  }



  @Test(dependsOnMethods = {"testDeleteEntry", "testSearchScope",
      "testSearchIndex", "testMatchedDN"})
  public void testSearchNotIndexed() throws Exception {
    //Add 2 more entries to overflow the index entry limit.
    for (Entry entry : additionalEntries) {
      backend.addEntry(entry, null);
      assertNotNull(backend.getEntry(entry.getDN()));
    }

    InternalClientConnection conn =
        InternalClientConnection.getRootConnection();
    LinkedHashSet<String> attribs = new LinkedHashSet<String>();
    attribs.add(ATTR_DEBUG_SEARCH_INDEX);

    String debugString;

    InternalSearchOperation search =
        conn.processSearch(DN.decode("dc=test,dc=com"),

            SearchScope.SUBORDINATE_SUBTREE,

            DereferencePolicy.NEVER_DEREF_ALIASES,
            0,
            0,
            false,

            LDAPFilter.decode("(carLicense=377*)").toSearchFilter(),
            attribs);

    LinkedList<SearchResultEntry> result = search.getSearchEntries();

    //No indexes should be used.
    debugString =
        result.get(0).getAttribute("debugsearchindex").get(0).toString();
    assertTrue(debugString.contains("NOT-INDEXED"));

  }

    @Test(dependsOnMethods = "testSearchNotIndexed")
  public void testNumSubordinatesIndexEntryLimitExceeded() throws Exception
  {
    DN dn = DN.decode("dc=test,dc=com");
    assertEquals(backend.numSubordinates(dn, false), 1);
    assertEquals(backend.numSubordinates(dn, true), 14);

    // 1 entry was deleted and 2 added for a total of 13
    dn = DN.decode("ou=People,dc=test,dc=com");
    assertEquals(backend.numSubordinates(dn, false), 13);
    assertEquals(backend.numSubordinates(dn, true), 13);
    dn = DN.decode("dc=com");
    assertEquals(backend.numSubordinates(dn, false), -1);
    assertEquals(backend.numSubordinates(dn, true), -1);
    dn = DN.decode("dc=test1,dc=com");
    assertEquals(backend.numSubordinates(dn, false), 2);
    assertEquals(backend.numSubordinates(dn, true), 2);
    dn = DN.decode("uid=user.10,ou=People,dc=test,dc=com");
    assertEquals(backend.numSubordinates(dn, false), 0);
    assertEquals(backend.numSubordinates(dn, true), 0);
    dn = DN.decode("uid=does not exist,ou=People,dc=test,dc=com");
    assertEquals(backend.numSubordinates(dn, false), -1);
    assertEquals(backend.numSubordinates(dn, true), -1);
  }


  /**
   * Provides a set of DNs for the matched DN test case.
   *
   * @return set of DNs
   * @throws Exception  when DN.decode fails
   */
  @DataProvider(name = "MatchedDNs")
  public Object[][] initMatchedDNs() throws Exception {

    ResultCode success      = ResultCode.SUCCESS;
    ResultCode noSuchObject = ResultCode.NO_SUCH_OBJECT;

    DN testComDN            = null;
    DN peopleTestComDN      = null;
    DN dummyTestComDN       = null;
    DN dummyPeopleTestComDN = null;

    try
    {
      testComDN            = DN.decode(                   "dc=test,dc=com");
      dummyTestComDN       = DN.decode(          "cn=dummy,dc=test,dc=com");
      peopleTestComDN      = DN.decode(         "ou=people,dc=test,dc=com");
      dummyPeopleTestComDN = DN.decode("cn=dummy,ou=people,dc=test,dc=com");
    }
    catch (DirectoryException de)
    {
      throw de;
    }

    // Sets of DNs
    Object[][] myData =
    {
      {testComDN,            null,            success},
      {peopleTestComDN,      null,            success},
      {dummyTestComDN,       testComDN,       noSuchObject},
      {dummyPeopleTestComDN, peopleTestComDN, noSuchObject},
    };

    return myData;
  }


  /**
   * Executes an internal search operation and check the result code and
   * matched DN field.
   *
   * @param searchBaseDN       the search base DN to use for the current test
   * @param expectedResultCode the expected LDAP result code
   * @param expectedMatchedDN  the expected matched DN, may be <code>null</code>
   */
  @Test(dataProvider = "MatchedDNs", dependsOnMethods = "testAdd")
  public void testMatchedDN(
    DN         searchBaseDN,
    DN         expectedMatchedDN,
    ResultCode expectedResultCode
    ) throws Exception
  {
    InternalClientConnection conn =
      InternalClientConnection.getRootConnection();

    // Filter for the search
    SearchFilter filter = LDAPFilter.decode("(objectClass=*)").toSearchFilter();

    // Test is performed with each and every scope
    for (SearchScope scope: SearchScope.values())
    {
      InternalSearchOperation searchOperation =
        conn.processSearch(searchBaseDN, scope, filter);

      assertEquals(searchOperation.getResultCode(), expectedResultCode);
      assertEquals(searchOperation.getMatchedDN(), expectedMatchedDN);
    }
  }

}
