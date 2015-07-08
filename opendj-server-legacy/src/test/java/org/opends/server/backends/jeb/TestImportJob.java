/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.backends.jeb;

import static org.testng.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.TestCaseUtils;
import org.opends.server.api.Backend;
import org.opends.server.backends.VerifyConfig;
import org.opends.server.core.DirectoryServer;
import org.opends.server.tasks.TaskUtils;
import org.opends.server.types.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class TestImportJob extends JebTestCase
{
  private String backendID = "importRoot";
  private File tempDir;
  private String homeDirName;

  private DN[] baseDNs;
  private Backend<?> backend;

  // @formatter:off
  private String top =
        "dn: dc=importtest,dc=com\n"
      + "objectclass: top\n"
      + "objectclass: domain\n"
      + "dc: importtest\n"
      + "\n"
      + "dn: ou=People,dc=importtest,dc=com\n"
      + "objectclass: top\n"
      + "objectclass: organizationalUnit\n"
      + "ou: People\n"
      + "\n"
      + "dn: ou=Others,ou=People,dc=importtest,dc=com\n"
      + "objectclass: top\n"
      + "objectclass: organizationalUnit\n"
      + "ou: Others\n"
      + "\n"
      + "dn: dc=importtest1,dc=com\n"
      + "objectclass: top\n"
      + "objectclass: domain\n"
      + "dc: importtest1\n";

  private String entries1 =
        "dn: uid=user.0,ou=People,dc=importtest,dc=com\n"
      + "objectClass: top\n"
      + "objectClass: person\n"
      + "objectClass: organizationalPerson\n"
      + "objectClass: inetOrgPerson\n"
      + "givenName: Aaccf\n"
      + "sn: Amar\n"
      + "cn: Aaccf Amar\n"
      + "initials: AQA\n"
      + "employeeNumber: 0\n"
      + "uid: user.0\n"
      + "mail: user.0@example.com\n"
      + "userPassword: password\n"
      + "telephoneNumber: 380-535-2354\n"
      + "homePhone: 707-626-3913\n"
      + "pager: 456-345-7750\n"
      + "mobile: 366-674-7274\n"
      + "street: 99262 Eleventh Street\n"
      + "l: Salem\n"
      + "st: NM\n"
      + "postalCode: 36530\n"
      + "postalAddress: Aaccf Amar$99262 Eleventh Street$Salem, NM  36530\n"
      + "description: This is the description for Aaccf Amar.\n"
      + "\n"
      + "dn: uid=user.539,ou=People,dc=importtest,dc=com\n"
      + "objectClass: top\n"
      + "objectClass: person\n"
      + "objectClass: organizationalPerson\n"
      + "objectClass: inetOrgPerson\n"
      + "givenName: Ardyth\n"
      + "sn: Bainton\n"
      + "cn: Ardyth Bainton\n"
      + "initials: AIB\n"
      + "employeeNumber: 539\n"
      + "uid: user.539\n"
      + "mail: user.539@example.com\n"
      + "userPassword: password\n"
      + "telephoneNumber: 641-433-7404\n"
      + "homePhone: 524-765-8780\n"
      + "pager: 985-331-1308\n"
      + "mobile: 279-423-0188\n"
      + "street: 81170 Taylor Street\n"
      + "l: Syracuse\n"
      + "st: WV\n"
      + "postalCode: 93507\n"
      + "postalAddress: Ardyth Bainton$81170 Taylor Street$Syracuse, WV  93507\n"
      + "description: This is the description for Ardyth Bainton.\n"
      + "\n"
      + "dn: uid=user.446,dc=importtest1,dc=com\n"
      + "objectClass: top\n"
      + "objectClass: person\n"
      + "objectClass: organizationalPerson\n"
      + "objectClass: inetOrgPerson\n"
      + "givenName: Annalee\n"
      + "sn: Avard\n"
      + "cn: Annalee Avard\n"
      + "initials: ANA\n"
      + "employeeNumber: 446\n"
      + "uid: user.446\n"
      + "mail: user.446@example.com\n"
      + "userPassword: password\n"
      + "telephoneNumber: 875-335-2712\n"
      + "homePhone: 181-995-6635\n"
      + "pager: 586-905-4185\n"
      + "mobile: 826-857-7592\n"
      + "street: 46168 Mill Street\n"
      + "l: Charleston\n"
      + "st: CO\n"
      + "postalCode: 60948\n"
      + "postalAddress: Annalee Avard$46168 Mill Street$Charleston, CO  60948\n"
      + "description: This is the description for Annalee Avard.\n"
      + "\n"
      + "dn: uid=user.362,dc=importtest1,dc=com\n"
      + "objectClass: top\n"
      + "objectClass: person\n"
      + "objectClass: organizationalPerson\n"
      + "objectClass: inetOrgPerson\n"
      + "givenName: Andaree\n"
      + "sn: Asawa\n"
      + "cn: Andaree Asawa\n"
      + "initials: AEA\n"
      + "employeeNumber: 362\n"
      + "uid: user.362\n"
      + "mail: user.362@example.com\n"
      + "userPassword: password\n"
      + "telephoneNumber: 399-788-7334\n"
      + "homePhone: 798-076-5683\n"
      + "pager: 034-026-9411\n"
      + "mobile: 948-743-9197\n"
      + "street: 81028 Forest Street\n"
      + "l: Wheeling\n"
      + "st: IA\n"
      + "postalCode: 60905\n"
      + "postalAddress: Andaree Asawa$81028 Forest Street$Wheeling, IA  60905\n"
      + "description: This is the description for Andaree Asawa.\n";

  private String replacement1 =
        "dn: uid=user.446,dc=importtest1,dc=com\n"
      + "objectClass: top\n"
      + "objectClass: person\n"
      + "objectClass: organizationalPerson\n"
      + "objectClass: inetOrgPerson\n"
      + "givenName: Annalee\n"
      + "sn: Bogard\n"
      + "cn: Annalee Bogard\n"
      + "initials: ANG\n"
      + "employeeNumber: 446\n"
      + "uid: user.446\n"
      + "mail: user.446@example.com\n"
      + "userPassword: password\n"
      + "telephoneNumber: 875-335-8882\n"
      + "homePhone: 181-995-6635\n"
      + "pager: 586-905-4185\n"
      + "mobile: 826-857-7592\n"
      + "street: 43221 Hill Street\n"
      + "l: Charleston\n"
      + "st: CO\n"
      + "postalCode: 60918\n"
      + "postalAddress: Annalee Avard$43221 Hill Street$Charleston, CO  60918\n"
      + "description: This is the description for Annalee Bogard.\n";

  private String skippedEntries =
        "dn: dc=skipped,dc=importtest1,dc=com\n"
      + "objectclass: top\n"
      + "objectclass: domain\n"
      + "dc: skipped\n"
      + "\n"
      + "dn: uid=user.446,dc=skipped,dc=importtest1,dc=com\n"
      + "objectClass: top\n"
      + "objectClass: person\n"
      + "objectClass: organizationalPerson\n"
      + "objectClass: inetOrgPerson\n"
      + "givenName: Annalee\n"
      + "sn: Bogard\n"
      + "cn: Annalee Bogard\n"
      + "initials: ANG\n"
      + "employeeNumber: 446\n"
      + "uid: user.446\n"
      + "mail: user.446@example.com\n"
      + "userPassword: password\n"
      + "telephoneNumber: 875-335-8882\n"
      + "homePhone: 181-995-6635\n"
      + "pager: 586-905-4185\n"
      + "mobile: 826-857-7592\n"
      + "street: 43221 Hill Street\n"
      + "l: Charleston\n"
      + "st: CO\n"
      + "postalCode: 60918\n"
      + "postalAddress: Annalee Avard$43221 Hill Street$Charleston, CO  60918\n"
      + "description: This is the description for Annalee Bogard.\n";
  // @formatter:on

  @BeforeClass
  public void setUp() throws Exception
  {
    // This test suite depends on having the schema available, so we'll make
    // sure the server is started.
    TestCaseUtils.startServer();
    TestCaseUtils.enableBackend(backendID);

    tempDir = TestCaseUtils.createTemporaryDirectory("jebimporttest");
    homeDirName = tempDir.getAbsolutePath();

    EnvManager.createHomeDir(homeDirName);

    FileOutputStream ldifFile = new FileOutputStream(homeDirName
        + File.separator + "top.ldif");
    PrintStream writer = new PrintStream(ldifFile);

    writer.println(top);
    writer.close();
    ldifFile.close();

    ldifFile = new FileOutputStream(homeDirName + File.separator
        + "entries1.ldif");
    writer = new PrintStream(ldifFile);

    writer.println(entries1);
    writer.close();
    ldifFile.close();

    ldifFile = new FileOutputStream(homeDirName + File.separator
        + "replacement1.ldif");
    writer = new PrintStream(ldifFile);

    writer.println(replacement1);
    writer.close();
    ldifFile.close();

    ldifFile = new FileOutputStream(homeDirName + File.separator
        + "skipped.ldif");
    writer = new PrintStream(ldifFile);

    writer.println(skippedEntries);
    writer.close();
    ldifFile.close();

    baseDNs = new DN[] { DN.valueOf("dc=importtest,dc=com"),
        DN.valueOf("dc=importtest1,dc=com") };

  }



  @AfterClass
  public void cleanUp() throws Exception
  {
    TestCaseUtils.disableBackend(backendID);
    TestCaseUtils.deleteDirectory(tempDir);
  }


  @Test(enabled = true)
  public void testImportAll() throws Exception
  {
    TestCaseUtils.clearJEBackend(backendID);
    ArrayList<String> fileList = new ArrayList<>();
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

    importLDIF(importConfig);

    backend = DirectoryServer.getBackend(backendID);
    RootContainer rootContainer = ((BackendImpl) backend).getRootContainer();
    EntryContainer entryContainer;

    assertTrue(rejectedEntries.size() <= 0);
    assertTrue(skippedEntries.size() <= 0);
    for (DN baseDN : baseDNs)
    {
      entryContainer = rootContainer.getEntryContainer(baseDN);
      entryContainer.sharedLock.lock();
      try
      {
        assertNotNull(entryContainer);

        if (baseDN.toString().equals("dc=importtest,dc=com"))
        {
          assertEquals(entryContainer.getEntryCount(), 5);
          assertTrue(entryContainer.entryExists(baseDN));
          assertTrue(entryContainer.entryExists(DN
              .valueOf("ou=People,dc=importtest,dc=com")));
          assertTrue(entryContainer.entryExists(DN
              .valueOf("ou=Others,ou=People,dc=importtest,dc=com")));
          assertTrue(entryContainer.entryExists(DN
              .valueOf("uid=user.0,ou=People,dc=importtest,dc=com")));
          assertTrue(entryContainer.entryExists(DN
              .valueOf("uid=user.539,ou=People,dc=importtest,dc=com")));

          VerifyConfig verifyConfig = new VerifyConfig();
          verifyConfig.setBaseDN(baseDN);

          backend = DirectoryServer.getBackend(backendID);
          assertEquals(backend.verifyBackend(verifyConfig), 0);
        }
        else if (baseDN.toString().equals("dc=importtest1,dc=com"))
        {
          assertEquals(entryContainer.getEntryCount(), 3);
          assertTrue(entryContainer.entryExists(baseDN));
          assertTrue(entryContainer.entryExists(DN
              .valueOf("uid=user.446,dc=importtest1,dc=com")));
          assertTrue(entryContainer.entryExists(DN
              .valueOf("uid=user.362,dc=importtest1,dc=com")));

          VerifyConfig verifyConfig = new VerifyConfig();
          verifyConfig.setBaseDN(baseDN);

          backend = DirectoryServer.getBackend(backendID);
          assertEquals(backend.verifyBackend(verifyConfig), 0);
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
    ArrayList<String> fileList = new ArrayList<>();
    fileList.add(homeDirName + File.separator + "top.ldif");
    fileList.add(homeDirName + File.separator + "entries1.ldif");

    Set<DN> includeBranches = Collections.singleton(DN.valueOf("ou=People,dc=importtest,dc=com"));
    Set<DN> excludeBranches = Collections.singleton(DN.valueOf("ou=Others,ou=People,dc=importtest,dc=com"));

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

    importLDIF(importConfig);

    backend = DirectoryServer.getBackend(backendID);
    RootContainer rootContainer = ((BackendImpl) backend).getRootContainer();
    EntryContainer entryContainer;

    assertTrue(rejectedEntries.size() <= 0);
    for (DN baseDN : baseDNs)
    {
      entryContainer = rootContainer.getEntryContainer(baseDN);
      entryContainer.sharedLock.lock();
      try
      {
        assertNotNull(entryContainer);

        if (baseDN.toString().equals("dc=importtest,dc=com"))
        {
          assertEquals(entryContainer.getEntryCount(), 5);
          assertTrue(entryContainer.entryExists(baseDN));
          assertTrue(entryContainer.entryExists(DN
              .valueOf("ou=People,dc=importtest,dc=com")));
          assertTrue(entryContainer.entryExists(DN
              .valueOf("ou=Others,ou=People,dc=importtest,dc=com")));
          assertTrue(entryContainer.entryExists(DN
              .valueOf("uid=user.0,ou=People,dc=importtest,dc=com")));
          assertTrue(entryContainer.entryExists(DN
              .valueOf("uid=user.539,ou=People,dc=importtest,dc=com")));

          VerifyConfig verifyConfig = new VerifyConfig();
          verifyConfig.setBaseDN(baseDN);

          backend = DirectoryServer.getBackend(backendID);
          assertEquals(backend.verifyBackend(verifyConfig), 0);
        }
        else if (baseDN.toString().equals("dc=importtest1,dc=com"))
        {
          assertEquals(entryContainer.getEntryCount(), 3);
          assertTrue(entryContainer.entryExists(baseDN));
          assertTrue(entryContainer.entryExists(DN
              .valueOf("uid=user.446,dc=importtest1,dc=com")));
          assertTrue(entryContainer.entryExists(DN
              .valueOf("uid=user.362,dc=importtest1,dc=com")));

          VerifyConfig verifyConfig = new VerifyConfig();
          verifyConfig.setBaseDN(baseDN);

          backend = DirectoryServer.getBackend(backendID);
          assertEquals(backend.verifyBackend(verifyConfig), 0);
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
    LDIFImportConfig importConfig = new LDIFImportConfig(homeDirName
        + File.separator + "replacement1.ldif");
    importConfig.setAppendToExistingData(true);
    importConfig.setReplaceExistingEntries(true);
    importConfig.setValidateSchema(true);
    importConfig.writeRejectedEntries(rejectedEntries);

    importLDIF(importConfig);

    backend = DirectoryServer.getBackend(backendID);
    RootContainer rootContainer = ((BackendImpl) backend).getRootContainer();
    EntryContainer entryContainer;

    entryContainer = rootContainer.getEntryContainer(DN
        .valueOf("dc=importtest1,dc=com"));
    assertNotNull(entryContainer);

    entryContainer.sharedLock.lock();
    try
    {
      assertTrue(rejectedEntries.size() <= 0);
      Entry entry = entryContainer.getEntry(DN
          .valueOf("uid=user.446,dc=importtest1,dc=com"));
      assertNotNull(entry);

      AttributeType attribute = entry.getAttribute("cn").get(0)
          .getAttributeType();

      assertTrue(entry.hasValue(attribute, null, ByteString.valueOf("Annalee Bogard")));

      VerifyConfig verifyConfig = new VerifyConfig();
      verifyConfig.setBaseDN(DN.valueOf("dc=importtest1,dc=com"));

      backend = DirectoryServer.getBackend(backendID);
      assertEquals(backend.verifyBackend(verifyConfig), 0);
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
    LDIFImportConfig importConfig = new LDIFImportConfig(homeDirName
        + File.separator + "replacement1.ldif");
    importConfig.setAppendToExistingData(false);
    importConfig.setReplaceExistingEntries(true);
    importConfig.setValidateSchema(true);
    importConfig.writeRejectedEntries(rejectedEntries);

    importLDIF(importConfig);

    assertTrue(rejectedEntries.toString().contains(
        "uid=user.446,dc=importtest1,dc=com"));
  }


  @Test(dependsOnMethods = "testImportReplaceExisting")
  public void testImportAppend() throws Exception
  {
    TestCaseUtils.clearJEBackend(backendID);

    LDIFImportConfig importConfig = new LDIFImportConfig(homeDirName
        + File.separator + "top.ldif");
    importConfig.setAppendToExistingData(false);
    importConfig.setReplaceExistingEntries(false);
    importConfig.setValidateSchema(true);

    importLDIF(importConfig);

    importConfig = new LDIFImportConfig(homeDirName + File.separator
        + "entries1.ldif");
    importConfig.setAppendToExistingData(true);
    importConfig.setReplaceExistingEntries(false);
    importConfig.setValidateSchema(true);

    importLDIF(importConfig);

    backend = DirectoryServer.getBackend(backendID);
    RootContainer rootContainer = ((BackendImpl) backend).getRootContainer();
    EntryContainer entryContainer;

    for (DN baseDN : baseDNs)
    {
      entryContainer = rootContainer.getEntryContainer(baseDN);
      assertNotNull(entryContainer);
      entryContainer.sharedLock.lock();
      try
      {
        if (baseDN.toString().equals("dc=importtest,dc=com"))
        {
          assertEquals(entryContainer.getEntryCount(), 5);
          assertTrue(entryContainer.entryExists(baseDN));
          assertTrue(entryContainer.entryExists(DN
              .valueOf("ou=People,dc=importtest,dc=com")));
          assertTrue(entryContainer.entryExists(DN
              .valueOf("uid=user.0,ou=People,dc=importtest,dc=com")));
          assertTrue(entryContainer.entryExists(DN
              .valueOf("uid=user.539,ou=People,dc=importtest,dc=com")));
        }
        else if (baseDN.toString().equals("dc=importtest1,dc=com"))
        {
          assertEquals(entryContainer.getEntryCount(), 3);
          assertTrue(entryContainer.entryExists(baseDN));
          assertTrue(entryContainer.entryExists(DN
              .valueOf("uid=user.446,dc=importtest1,dc=com")));
          assertTrue(entryContainer.entryExists(DN
              .valueOf("uid=user.362,dc=importtest1,dc=com")));
        }
      }
      finally
      {
        entryContainer.sharedLock.unlock();
        TaskUtils.enableBackend(backendID);
      }
    }
  }



  @Test(dependsOnMethods = "testImportPartial")
  public void testImportNotReplaceExisting() throws Exception
  {
    ByteArrayOutputStream rejectedEntries = new ByteArrayOutputStream();
    LDIFImportConfig importConfig = new LDIFImportConfig(homeDirName
        + File.separator + "replacement1.ldif");
    importConfig.setAppendToExistingData(true);
    importConfig.setReplaceExistingEntries(false);
    importConfig.setValidateSchema(true);
    importConfig.writeRejectedEntries(rejectedEntries);

    importLDIF(importConfig);

    assertTrue(rejectedEntries.toString().contains(
        "uid=user.446,dc=importtest1,dc=com"));
  }



  @Test(dependsOnMethods = "testImportPartial")
  public void testImportSkip() throws Exception
  {
    Set<DN> excludeBranches = Collections.singleton(DN.valueOf("dc=skipped,dc=importtest1,dc=com"));
    ByteArrayOutputStream skippedEntries = new ByteArrayOutputStream();
    LDIFImportConfig importConfig = new LDIFImportConfig(homeDirName
        + File.separator + "skipped.ldif");
    importConfig.setAppendToExistingData(true);
    importConfig.setReplaceExistingEntries(true);
    importConfig.setValidateSchema(true);
    importConfig.setExcludeBranches(excludeBranches);
    importConfig.writeSkippedEntries(skippedEntries);

    importLDIF(importConfig);

    assertTrue(skippedEntries.toString().contains(
        "dc=skipped,dc=importtest1,dc=com"));
    assertTrue(skippedEntries.toString().contains(
        "uid=user.446,dc=skipped,dc=importtest1,dc=com"));
  }

  private void importLDIF(LDIFImportConfig importConfig) throws DirectoryException
  {
    backend = DirectoryServer.getBackend(backendID);
    TaskUtils.disableBackend(backendID);
    try
    {
      backend.importLDIF(importConfig, DirectoryServer.getInstance().getServerContext());
    }
    finally
    {
      TaskUtils.enableBackend(backendID);
    }
  }
}
