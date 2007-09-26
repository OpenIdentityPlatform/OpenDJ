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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.backends.jeb;

import org.opends.server.TestCaseUtils;
import org.opends.server.tasks.TaskUtils;
import org.opends.server.admin.std.server.LocalDBBackendCfg;
import org.opends.server.admin.std.meta.LocalDBBackendCfgDefn;
import org.opends.server.admin.server.AdminTestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.config.ConfigEntry;
import org.opends.server.util.Base64;
import static org.opends.server.util.ServerConstants.OC_TOP;
import static org.opends.server.util.ServerConstants.OC_EXTENSIBLE_OBJECT;
import org.opends.server.types.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.testng.annotations.AfterClass;
import static org.testng.Assert.*;
import static org.testng.Assert.assertEquals;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.ByteArrayOutputStream;
import java.util.*;


public class TestImportJob extends JebTestCase
{
  private  String beID="importRoot";
  private File tempDir;
  private String homeDirName;

  private DN[] baseDNs;
  private BackendImpl be;

  private  String errorCount="verify-error-count";

  private String top = "dn: dc=importtest,dc=com\n" +
      "objectclass: top\n" +
      "objectclass: domain\n" +
      "dc: example\n" +
      "\n" +
      "dn: ou=People,dc=importtest,dc=com\n" +
      "objectclass: top\n" +
      "objectclass: organizationalUnit\n" +
      "ou: People\n" +
      "\n" +
      "dn: ou=Others,ou=People,dc=importtest,dc=com\n" +
      "objectclass: top\n" +
      "objectclass: organizationalUnit\n" +
      "ou: Others\n" +
      "\n" +
      "dn: dc=importtest1,dc=com\n" +
      "objectclass: top\n" +
      "objectclass: domain\n" +
      "dc: example1\n";
  private String entries1 =
      "dn: uid=user.0,ou=People,dc=importtest,dc=com\n" +
      "objectClass: top\n" +
      "objectClass: person\n" +
      "objectClass: organizationalPerson\n" +
      "objectClass: inetOrgPerson\n" +
      "givenName: Aaccf\n" +
      "sn: Amar\n" +
      "cn: Aaccf Amar\n" +
      "initials: AQA\n" +
      "employeeNumber: 0\n" +
      "uid: user.0\n" +
      "mail: user.0@example.com\n" +
      "userPassword: password\n" +
      "telephoneNumber: 380-535-2354\n" +
      "homePhone: 707-626-3913\n" +
      "pager: 456-345-7750\n" +
      "mobile: 366-674-7274\n" +
      "street: 99262 Eleventh Street\n" +
      "l: Salem\n" +
      "st: NM\n" +
      "postalCode: 36530\n" +
      "postalAddress: Aaccf Amar$99262 Eleventh Street$Salem, NM  36530\n" +
      "description: This is the description for Aaccf Amar.\n" +
      "\n" +
      "dn: uid=user.539,ou=People,dc=importtest,dc=com\n" +
      "objectClass: top\n" +
      "objectClass: person\n" +
      "objectClass: organizationalPerson\n" +
      "objectClass: inetOrgPerson\n" +
      "givenName: Ardyth\n" +
      "sn: Bainton\n" +
      "cn: Ardyth Bainton\n" +
      "initials: AIB\n" +
      "employeeNumber: 539\n" +
      "uid: user.539\n" +
      "mail: user.539@example.com\n" +
      "userPassword: password\n" +
      "telephoneNumber: 641-433-7404\n" +
      "homePhone: 524-765-8780\n" +
      "pager: 985-331-1308\n" +
      "mobile: 279-423-0188\n" +
      "street: 81170 Taylor Street\n" +
      "l: Syracuse\n" +
      "st: WV\n" +
      "postalCode: 93507\n" +
      "postalAddress: Ardyth Bainton$81170 Taylor Street$Syracuse, WV  93507\n" +
      "description: This is the description for Ardyth Bainton.\n" +
      "\n" +
      "dn: uid=user.446,dc=importtest1,dc=com\n" +
      "objectClass: top\n" +
      "objectClass: person\n" +
      "objectClass: organizationalPerson\n" +
      "objectClass: inetOrgPerson\n" +
      "givenName: Annalee\n" +
      "sn: Avard\n" +
      "cn: Annalee Avard\n" +
      "initials: ANA\n" +
      "employeeNumber: 446\n" +
      "uid: user.446\n" +
      "mail: user.446@example.com\n" +
      "userPassword: password\n" +
      "telephoneNumber: 875-335-2712\n" +
      "homePhone: 181-995-6635\n" +
      "pager: 586-905-4185\n" +
      "mobile: 826-857-7592\n" +
      "street: 46168 Mill Street\n" +
      "l: Charleston\n" +
      "st: CO\n" +
      "postalCode: 60948\n" +
      "postalAddress: Annalee Avard$46168 Mill Street$Charleston, CO  60948\n" +
      "description: This is the description for Annalee Avard.\n" +
      "\n" +
      "dn: uid=user.362,dc=importtest1,dc=com\n" +
      "objectClass: top\n" +
      "objectClass: person\n" +
      "objectClass: organizationalPerson\n" +
      "objectClass: inetOrgPerson\n" +
      "givenName: Andaree\n" +
      "sn: Asawa\n" +
      "cn: Andaree Asawa\n" +
      "initials: AEA\n" +
      "employeeNumber: 362\n" +
      "uid: user.362\n" +
      "mail: user.362@example.com\n" +
      "userPassword: password\n" +
      "telephoneNumber: 399-788-7334\n" +
      "homePhone: 798-076-5683\n" +
      "pager: 034-026-9411\n" +
      "mobile: 948-743-9197\n" +
      "street: 81028 Forest Street\n" +
      "l: Wheeling\n" +
      "st: IA\n" +
      "postalCode: 60905\n" +
      "postalAddress: Andaree Asawa$81028 Forest Street$Wheeling, IA  60905\n" +
      "description: This is the description for Andaree Asawa.\n";

  private String replacement1 =
      "dn: uid=user.446,dc=importtest1,dc=com\n" +
      "objectClass: top\n" +
      "objectClass: person\n" +
      "objectClass: organizationalPerson\n" +
      "objectClass: inetOrgPerson\n" +
      "givenName: Annalee\n" +
      "sn: Bogard\n" +
      "cn: Annalee Bogard\n" +
      "initials: ANG\n" +
      "employeeNumber: 446\n" +
      "uid: user.446\n" +
      "mail: user.446@example.com\n" +
      "userPassword: password\n" +
      "telephoneNumber: 875-335-8882\n" +
      "homePhone: 181-995-6635\n" +
      "pager: 586-905-4185\n" +
      "mobile: 826-857-7592\n" +
      "street: 43221 Hill Street\n" +
      "l: Charleston\n" +
      "st: CO\n" +
      "postalCode: 60918\n" +
      "postalAddress: Annalee Avard$43221 Hill Street$Charleston, CO  60918\n" +
      "description: This is the description for Annalee Bogard.\n";

  private String skippedEntries =
    "dn: dc=skipped,dc=importtest1,dc=com\n" +
    "objectclass: top\n" +
    "objectclass: domain\n" +
    "dc: skipped\n" +
    "\n" +
    "dn: uid=user.446,dc=skipped,dc=importtest1,dc=com\n" +
    "objectClass: top\n" +
    "objectClass: person\n" +
    "objectClass: organizationalPerson\n" +
    "objectClass: inetOrgPerson\n" +
    "givenName: Annalee\n" +
    "sn: Bogard\n" +
    "cn: Annalee Bogard\n" +
    "initials: ANG\n" +
    "employeeNumber: 446\n" +
    "uid: user.446\n" +
    "mail: user.446@example.com\n" +
    "userPassword: password\n" +
    "telephoneNumber: 875-335-8882\n" +
    "homePhone: 181-995-6635\n" +
    "pager: 586-905-4185\n" +
    "mobile: 826-857-7592\n" +
    "street: 43221 Hill Street\n" +
    "l: Charleston\n" +
    "st: CO\n" +
    "postalCode: 60918\n" +
    "postalAddress: Annalee Avard$43221 Hill Street$Charleston, CO  60918\n" +
    "description: This is the description for Annalee Bogard.\n";
  

  @BeforeClass
  public void setUp() throws Exception
  {
    // This test suite depends on having the schema available, so we'll make
    // sure the server is started.
    TestCaseUtils.startServer();

    tempDir = TestCaseUtils.createTemporaryDirectory("jebimporttest");
    homeDirName = tempDir.getAbsolutePath();

    EnvManager.createHomeDir(homeDirName);

    FileOutputStream ldifFile = new FileOutputStream(homeDirName + File.separator + "top.ldif");
    PrintStream writer = new PrintStream(ldifFile);

    writer.println(top);
    writer.close();
    ldifFile.close();

    ldifFile = new FileOutputStream(homeDirName + File.separator + "entries1.ldif");
    writer = new PrintStream(ldifFile);

    writer.println(entries1);
    writer.close();
    ldifFile.close();

    ldifFile = new FileOutputStream(homeDirName + File.separator + "replacement1.ldif");
    writer = new PrintStream(ldifFile);

    writer.println(replacement1);
    writer.close();
    ldifFile.close();

    ldifFile = new FileOutputStream(homeDirName + File.separator + "skipped.ldif");
    writer = new PrintStream(ldifFile);

    writer.println(skippedEntries);
    writer.close();
    ldifFile.close();

    baseDNs = new DN[]
    {
      DN.decode("dc=importtest,dc=com"),
      DN.decode("dc=importtest1,dc=com")
    };

  }

  @AfterClass
  public void cleanUp() throws Exception
  {
    TestCaseUtils.deleteDirectory(tempDir);
  }

  @Test
  public void testImportAll() throws Exception
  {
    TestCaseUtils.clearJEBackend(false, beID, null);
    ArrayList<String> fileList = new ArrayList<String>();
    fileList.add(homeDirName + File.separator + "top.ldif");
    fileList.add(homeDirName + File.separator + "entries1.ldif");

    ByteArrayOutputStream rejectedEntries = new ByteArrayOutputStream();
    ByteArrayOutputStream skippedEntries = new ByteArrayOutputStream();
    LDIFImportConfig importConfig = new LDIFImportConfig(fileList);
    importConfig.setAppendToExistingData(false);
    importConfig.setReplaceExistingEntries(false);
    importConfig.setValidateSchema(true);
    importConfig.writeRejectedEntries(rejectedEntries);
    importConfig.writeSkippedEntries(skippedEntries);

    be=(BackendImpl) DirectoryServer.getBackend(beID);
    TaskUtils.disableBackend(beID);
    try
    {
      be.importLDIF(importConfig);
    }
    finally
    {
      TaskUtils.enableBackend(beID);
    }

    be=(BackendImpl) DirectoryServer.getBackend(beID);
    RootContainer rootContainer = be.getRootContainer();
    EntryContainer entryContainer;

    assertTrue(rejectedEntries.size() <= 0);
    assertTrue(skippedEntries.size() <= 0);
    for(DN baseDN : baseDNs)
    {
      entryContainer = rootContainer.getEntryContainer(baseDN);
      entryContainer.sharedLock.lock();
      try
      {
        assertNotNull(entryContainer);

        if(baseDN.toString().equals("dc=importtest,dc=com"))
        {
          assertEquals(entryContainer.getEntryCount(), 5);
          assertTrue(entryContainer.entryExists(baseDN));
          assertTrue(entryContainer.entryExists(DN.decode("ou=People,dc=importtest,dc=com")));
          assertTrue(entryContainer.entryExists(DN.decode("ou=Others,ou=People,dc=importtest,dc=com")));
          assertTrue(entryContainer.entryExists(DN.decode("uid=user.0,ou=People,dc=importtest,dc=com")));
          assertTrue(entryContainer.entryExists(DN.decode("uid=user.539,ou=People,dc=importtest,dc=com")));

          VerifyConfig verifyConfig = new VerifyConfig();
          verifyConfig.setBaseDN(baseDN);

          Entry statEntry=bldStatEntry("");
          be=(BackendImpl) DirectoryServer.getBackend(beID);
          be.verifyBackend(verifyConfig, statEntry);
          assertEquals(getStatEntryCount(statEntry, errorCount), 0);
        }
        else if(baseDN.toString().equals("dc=importtest1,dc=com"))
        {
          assertEquals(entryContainer.getEntryCount(), 3);
          assertTrue(entryContainer.entryExists(baseDN));
          assertTrue(entryContainer.entryExists(DN.decode("uid=user.446,dc=importtest1,dc=com")));
          assertTrue(entryContainer.entryExists(DN.decode("uid=user.362,dc=importtest1,dc=com")));

          VerifyConfig verifyConfig = new VerifyConfig();
          verifyConfig.setBaseDN(baseDN);

          Entry statEntry=bldStatEntry("");
          be=(BackendImpl) DirectoryServer.getBackend(beID);
          be.verifyBackend(verifyConfig, statEntry);
          assertEquals(getStatEntryCount(statEntry, errorCount), 0);
        }
      }
      finally
      {
        entryContainer.sharedLock.unlock();

      }
    }
  }

  @Test(dependsOnMethods = "testImportAll")
  public void testImportPartial() throws Exception
  {
    ArrayList<String> fileList = new ArrayList<String>();
    fileList.add(homeDirName + File.separator + "top.ldif");
    fileList.add(homeDirName + File.separator + "entries1.ldif");

    ArrayList<DN> includeBranches = new ArrayList<DN>();
    includeBranches.add(DN.decode("ou=People,dc=importtest,dc=com"));
    ArrayList<DN> excludeBranches = new ArrayList<DN>();
    excludeBranches.add(DN.decode("ou=Others,ou=People,dc=importtest,dc=com"));

    ByteArrayOutputStream rejectedEntries = new ByteArrayOutputStream();
    ByteArrayOutputStream skippedEntries = new ByteArrayOutputStream();
    LDIFImportConfig importConfig = new LDIFImportConfig(fileList);
    importConfig.setAppendToExistingData(false);
    importConfig.setReplaceExistingEntries(false);
    importConfig.setValidateSchema(true);
    importConfig.writeRejectedEntries(rejectedEntries);
    importConfig.writeSkippedEntries(skippedEntries);
    importConfig.setIncludeBranches(includeBranches);
    importConfig.setExcludeBranches(excludeBranches);

    be=(BackendImpl) DirectoryServer.getBackend(beID);
    TaskUtils.disableBackend(beID);
    try
    {
      be.importLDIF(importConfig);
    }
    finally
    {
      TaskUtils.enableBackend(beID);
    }

    be=(BackendImpl) DirectoryServer.getBackend(beID);
    RootContainer rootContainer = be.getRootContainer();
    EntryContainer entryContainer;

    assertTrue(rejectedEntries.size() <= 0);
    for(DN baseDN : baseDNs)
    {
      entryContainer = rootContainer.getEntryContainer(baseDN);
      entryContainer.sharedLock.lock();
      try
      {
        assertNotNull(entryContainer);

        if(baseDN.toString().equals("dc=importtest,dc=com"))
        {
          assertEquals(entryContainer.getEntryCount(), 5);
          assertTrue(entryContainer.entryExists(baseDN));
          assertTrue(entryContainer.entryExists(DN.decode("ou=People,dc=importtest,dc=com")));
          assertTrue(entryContainer.entryExists(DN.decode("ou=Others,ou=People,dc=importtest,dc=com")));
          assertTrue(entryContainer.entryExists(DN.decode("uid=user.0,ou=People,dc=importtest,dc=com")));
          assertTrue(entryContainer.entryExists(DN.decode("uid=user.539,ou=People,dc=importtest,dc=com")));

          VerifyConfig verifyConfig = new VerifyConfig();
          verifyConfig.setBaseDN(baseDN);

          Entry statEntry=bldStatEntry("");
          be=(BackendImpl) DirectoryServer.getBackend(beID);
          be.verifyBackend(verifyConfig, statEntry);
          assertEquals(getStatEntryCount(statEntry, errorCount), 0);
        }
        else if(baseDN.toString().equals("dc=importtest1,dc=com"))
        {
          assertEquals(entryContainer.getEntryCount(), 3);
          assertTrue(entryContainer.entryExists(baseDN));
          assertTrue(entryContainer.entryExists(DN.decode("uid=user.446,dc=importtest1,dc=com")));
          assertTrue(entryContainer.entryExists(DN.decode("uid=user.362,dc=importtest1,dc=com")));

          VerifyConfig verifyConfig = new VerifyConfig();
          verifyConfig.setBaseDN(baseDN);

          Entry statEntry=bldStatEntry("");
          be=(BackendImpl) DirectoryServer.getBackend(beID);
          be.verifyBackend(verifyConfig, statEntry);
          assertEquals(getStatEntryCount(statEntry, errorCount), 0);
        }
      }
      finally
      {
        entryContainer.sharedLock.unlock();

      }
    }
  }

  @Test(dependsOnMethods = "testImportPartial")
  public void testImportReplaceExisting() throws Exception
  {
    ByteArrayOutputStream rejectedEntries = new ByteArrayOutputStream();
    LDIFImportConfig importConfig = new LDIFImportConfig(homeDirName + File.separator + "replacement1.ldif");
    importConfig.setAppendToExistingData(true);
    importConfig.setReplaceExistingEntries(true);
    importConfig.setValidateSchema(true);
    importConfig.writeRejectedEntries(rejectedEntries);

    be=(BackendImpl) DirectoryServer.getBackend(beID);
    TaskUtils.disableBackend(beID);
    try
    {
      be.importLDIF(importConfig);
    }
    finally
    {
      TaskUtils.enableBackend(beID);
    }

    be=(BackendImpl) DirectoryServer.getBackend(beID);
    RootContainer rootContainer = be.getRootContainer();
    EntryContainer entryContainer;

    entryContainer = rootContainer.getEntryContainer(DN.decode("dc=importtest1,dc=com"));
    assertNotNull(entryContainer);

    entryContainer.sharedLock.lock();
    try
    {
      assertTrue(rejectedEntries.size() <= 0);
      Entry entry = entryContainer.getEntry(DN.decode("uid=user.446,dc=importtest1,dc=com"));
      assertNotNull(entry);

      AttributeType attribute = entry.getAttribute("cn").get(0).getAttributeType();

      assertTrue(entry.hasValue(attribute, null, new AttributeValue(attribute,"Annalee Bogard")));

      VerifyConfig verifyConfig = new VerifyConfig();
      verifyConfig.setBaseDN(DN.decode("dc=importtest1,dc=com"));

      Entry statEntry=bldStatEntry("");
      be=(BackendImpl) DirectoryServer.getBackend(beID);
      be.verifyBackend(verifyConfig, statEntry);
      assertEquals(getStatEntryCount(statEntry, errorCount), 0);
    }
    finally
    {
      entryContainer.sharedLock.unlock();
    }
  }

  @Test(dependsOnMethods = "testImportReplaceExisting")
  public void testImportNoParent() throws Exception
  {
    ByteArrayOutputStream rejectedEntries = new ByteArrayOutputStream();
    LDIFImportConfig importConfig = new LDIFImportConfig(homeDirName + File.separator + "replacement1.ldif");
    importConfig.setAppendToExistingData(false);
    importConfig.setReplaceExistingEntries(true);
    importConfig.setValidateSchema(true);
    importConfig.writeRejectedEntries(rejectedEntries);

    be=(BackendImpl) DirectoryServer.getBackend(beID);
    TaskUtils.disableBackend(beID);
    try
    {
      be.importLDIF(importConfig);
    }
    finally
    {
      TaskUtils.enableBackend(beID);
    }
    assertTrue(rejectedEntries.toString().contains("uid=user.446,dc=importtest1,dc=com"));
  }

  @Test(dependsOnMethods = "testImportReplaceExisting")
  public void testImportAppend() throws Exception
  {
    LDIFImportConfig importConfig = new LDIFImportConfig(homeDirName + File.separator + "top.ldif");
    importConfig.setAppendToExistingData(false);
    importConfig.setReplaceExistingEntries(false);
    importConfig.setValidateSchema(true);

    be=(BackendImpl) DirectoryServer.getBackend(beID);
    TaskUtils.disableBackend(beID);
    try
    {
      be.importLDIF(importConfig);
    }
    finally
    {
      TaskUtils.enableBackend(beID);
    }

    importConfig = new LDIFImportConfig(homeDirName + File.separator + "entries1.ldif");
    importConfig.setAppendToExistingData(true);
    importConfig.setReplaceExistingEntries(false);
    importConfig.setValidateSchema(true);

    be=(BackendImpl) DirectoryServer.getBackend(beID);
    TaskUtils.disableBackend(beID);
    try
    {
      be.importLDIF(importConfig);
    }
    finally
    {
      TaskUtils.enableBackend(beID);
    }

    be=(BackendImpl) DirectoryServer.getBackend(beID);
    RootContainer rootContainer = be.getRootContainer();
    EntryContainer entryContainer;

    for(DN baseDN : baseDNs)
    {
      entryContainer = rootContainer.getEntryContainer(baseDN);
      assertNotNull(entryContainer);
      entryContainer.sharedLock.lock();
      try
      {
        if(baseDN.toString().equals("dc=importtest,dc=com"))
        {
          assertEquals(entryContainer.getEntryCount(), 5);
          assertTrue(entryContainer.entryExists(baseDN));
          assertTrue(entryContainer.entryExists(DN.decode("ou=People,dc=importtest,dc=com")));
          assertTrue(entryContainer.entryExists(DN.decode("uid=user.0,ou=People,dc=importtest,dc=com")));
          assertTrue(entryContainer.entryExists(DN.decode("uid=user.539,ou=People,dc=importtest,dc=com")));
        }
        else if(baseDN.toString().equals("dc=importtest1,dc=com"))
        {
          assertEquals(entryContainer.getEntryCount(), 3);
          assertTrue(entryContainer.entryExists(baseDN));
          assertTrue(entryContainer.entryExists(DN.decode("uid=user.446,dc=importtest1,dc=com")));
          assertTrue(entryContainer.entryExists(DN.decode("uid=user.362,dc=importtest1,dc=com")));
        }
      }
      finally
      {
        entryContainer.sharedLock.unlock();
        TaskUtils.enableBackend(beID);
      }
    }
  }

  @Test(dependsOnMethods = "testImportPartial")
  public void testImportNotReplaceExisting() throws Exception
  {
    ByteArrayOutputStream rejectedEntries = new ByteArrayOutputStream();
    LDIFImportConfig importConfig = new LDIFImportConfig(homeDirName + File.separator + "replacement1.ldif");
    importConfig.setAppendToExistingData(true);
    importConfig.setReplaceExistingEntries(false);
    importConfig.setValidateSchema(true);
    importConfig.writeRejectedEntries(rejectedEntries);

    be=(BackendImpl) DirectoryServer.getBackend(beID);
    TaskUtils.disableBackend(beID);
    try
    {
      be.importLDIF(importConfig);
    }
    finally
    {
      TaskUtils.enableBackend(beID);
    }

    assertTrue(rejectedEntries.toString().contains("uid=user.446,dc=importtest1,dc=com"));
  }

  @Test(dependsOnMethods = "testImportPartial")
  public void testImportSkip() throws Exception
  {
    ArrayList<DN> excludeBranches = new ArrayList<DN>();
    excludeBranches.add(DN.decode("dc=skipped,dc=importtest1,dc=com"));
    ByteArrayOutputStream skippedEntries = new ByteArrayOutputStream();
    LDIFImportConfig importConfig = new LDIFImportConfig(homeDirName + File.separator + "skipped.ldif");
    importConfig.setAppendToExistingData(true);
    importConfig.setReplaceExistingEntries(true);
    importConfig.setValidateSchema(true);
    importConfig.setExcludeBranches(excludeBranches);
    importConfig.writeSkippedEntries(skippedEntries);

    be=(BackendImpl) DirectoryServer.getBackend(beID);
    TaskUtils.disableBackend(beID);
    try
    {
      be.importLDIF(importConfig);
    }
    finally
    {
      TaskUtils.enableBackend(beID);
    }
    assertTrue(skippedEntries.toString().contains("dc=skipped,dc=importtest1,dc=com"));
    assertTrue(skippedEntries.toString().contains("uid=user.446,dc=skipped,dc=importtest1,dc=com"));
  }
  
      /**
     * Builds an entry suitable for using in the verify job to gather statistics about
     * the verify.
     * @param dn to put into the entry.
     * @return a suitable entry.
     * @throws DirectoryException if the cannot be created.
     */
    private Entry bldStatEntry(String dn) throws DirectoryException {
    	DN entryDN = DN.decode(dn);
    	HashMap<ObjectClass, String> ocs = new HashMap<ObjectClass, String>(2);
    	ObjectClass topOC = DirectoryServer.getObjectClass(OC_TOP);
    	if (topOC == null) {
    		topOC = DirectoryServer.getDefaultObjectClass(OC_TOP);
    	}
    	ocs.put(topOC, OC_TOP);
    	ObjectClass extensibleObjectOC = DirectoryServer
    	.getObjectClass(OC_EXTENSIBLE_OBJECT);
    	if (extensibleObjectOC == null) {
    		extensibleObjectOC = DirectoryServer
    		.getDefaultObjectClass(OC_EXTENSIBLE_OBJECT);
    	}
    	ocs.put(extensibleObjectOC, OC_EXTENSIBLE_OBJECT);
    	return new Entry(entryDN, ocs,
    			new LinkedHashMap<AttributeType, List<Attribute>>(0),
    			new HashMap<AttributeType, List<Attribute>>(0));
    }

    /**
     * Gets information from the stat entry and returns that value as a Long.
     * @param e entry to search.
     * @param type attribute type
     * @return Long
     * @throws NumberFormatException if the attribute value cannot be parsed.
     */
    private long getStatEntryCount(Entry e, String type)
    throws NumberFormatException {
    	AttributeType attrType =
    		DirectoryServer.getAttributeType(type);
    	if (attrType == null)
    		attrType = DirectoryServer.getDefaultAttributeType(type);
    	List<Attribute> attrList = e.getAttribute(attrType, null);
    	LinkedHashSet<AttributeValue> values =
    		attrList.get(0).getValues();
    	AttributeValue v = values.iterator().next();
    	long retVal = Long.parseLong(v.getStringValue());
    	return (retVal);
    }
}
