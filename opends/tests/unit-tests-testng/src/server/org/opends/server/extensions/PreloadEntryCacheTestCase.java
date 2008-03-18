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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import org.opends.server.TestCaseUtils;
import org.opends.server.admin.server.AdminTestCaseUtils;
import org.testng.annotations.BeforeClass;
import org.opends.server.admin.std.meta.*;
import org.opends.server.admin.std.server.EntryCacheCfg;
import org.opends.server.admin.std.server.FileSystemEntryCacheCfg;
import org.opends.server.api.Backend;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Entry;
import org.opends.server.util.ServerConstants;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;
import static org.testng.Assert.*;



/**
 * The entry cache pre-load test class.
 */
@Test(groups = { "entrycache", "slow" }, sequential=true)
public class PreloadEntryCacheTestCase
       extends ExtensionsTestCase
{
  /**
   * Number of unique dummy test entries.
   */
  protected int NUMTESTENTRIES = 1000;

  /**
   * Dummy test entries.
   */
  protected ArrayList<Entry> testEntriesList;

  /**
   * Entry cache configuration instance.
   */
  protected EntryCacheCfg configuration;

  /**
   * Temporary folder to setup dummy JE backend environment in.
   */
  private File jeBackendTempDir;

  /**
   * Initialize the entry cache pre-load test.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass()
  @SuppressWarnings("unchecked")
  public void preloadEntryCacheTestInit()
         throws Exception
  {
    // Ensure that the server is running.
    TestCaseUtils.startServer();

    // Make sure JE directory exist.
    jeBackendTempDir = TestCaseUtils.createTemporaryDirectory("db-cachetest");
    String jeDir = jeBackendTempDir.getAbsolutePath();

    // Create dummy JE backend for this test.
    TestCaseUtils.dsconfig("create-backend", "--backend-name", "cacheTest",
      "--type", "local-db", "--set", "db-directory:" + jeDir, "--set",
      "base-dn:o=cachetest", "--set",
      "import-temp-directory:importTmp", "--set",
      "writability-mode:enabled", "--set", "enabled:true");

    // Configure the entry cache, use FileSystemEntryCache.
    Entry cacheConfigEntry = TestCaseUtils.makeEntry(
      "dn: cn=File System,cn=Entry Caches,cn=config",
      "objectClass: ds-cfg-file-system-entry-cache",
      "objectClass: ds-cfg-entry-cache",
      "objectClass: top",
      "cn: File System",
      "ds-cfg-cache-level: 1",
      "ds-cfg-java-class: " +
      "org.opends.server.extensions.FileSystemEntryCache",
      "ds-cfg-enabled: true");
    configuration = AdminTestCaseUtils.getConfiguration(
      EntryCacheCfgDefn.getInstance(), cacheConfigEntry);

    // Make parent entry.
    Entry parentEntry = TestCaseUtils.makeEntry(
      "dn: o=cachetest",
      "o: cachetest",
      "objectClass: top",
      "objectClass: organization");
    TestCaseUtils.addEntry(parentEntry);

    // Make some dummy test entries.
    testEntriesList = new ArrayList<Entry>(NUMTESTENTRIES);
    for(int i = 0; i < NUMTESTENTRIES; i++ ) {
      Entry testEntry = TestCaseUtils.makeEntry(
        "dn: uid=test" + Integer.toString(i) + ".user" + Integer.toString(i)
         + ",o=cachetest",
        "objectClass: person",
        "objectClass: inetorgperson",
        "objectClass: top",
        "objectClass: organizationalperson",
        "postalAddress: somewhere in Testville" + Integer.toString(i),
        "street: Under Construction Street" + Integer.toString(i),
        "l: Testcounty" + Integer.toString(i),
        "st: Teststate" + Integer.toString(i),
        "telephoneNumber: +878 8378 8378" + Integer.toString(i),
        "mobile: +878 8378 8378" + Integer.toString(i),
        "homePhone: +878 8378 8378" + Integer.toString(i),
        "pager: +878 8378 8378" + Integer.toString(i),
        "mail: test" + Integer.toString(i) + ".user" + Integer.toString(i)
         + "@testdomain.net",
        "postalCode: 8378" + Integer.toString(i),
        "userPassword: testpassword" + Integer.toString(i),
        "description: description for Test" + Integer.toString(i) + "User"
         + Integer.toString(i),
        "cn: Test" + Integer.toString(i) + "User" + Integer.toString(i),
        "sn: User" + Integer.toString(i),
        "givenName: Test" + Integer.toString(i),
        "initials: TST" + Integer.toString(i),
        "employeeNumber: 8378" + Integer.toString(i),
        "uid: test" + Integer.toString(i) + ".user" + Integer.toString(i)
      );
      testEntriesList.add(testEntry);
      TestCaseUtils.addEntry(testEntry);
    }

    // Initialize the cache reflecting on DirectoryServer
    // and EntryCacheConfigManager.
    final Field[] directoryFields =
        DirectoryServer.class.getDeclaredFields();
    for (int i = 0; i < directoryFields.length; ++i) {
      if (directoryFields[i].getName().equals("entryCacheConfigManager")) {
        directoryFields[i].setAccessible(true);
        final Method[] cacheManagerMethods =
          directoryFields[i].getType().getDeclaredMethods();
        for (int j = 0; j < cacheManagerMethods.length; ++j) {
          if (cacheManagerMethods[j].getName().equals(
            "loadAndInstallEntryCache")) {
            cacheManagerMethods[j].setAccessible(true);
            cacheManagerMethods[j].invoke(directoryFields[i].get(
              DirectoryServer.getInstance()),
              configuration.getJavaClass(), configuration);
          }
        }
      }
    }

    // Attempt to force GC to possibly free some memory.
    System.gc();
  }



  /**
   * Tests the entry cache pre-load.
   */
  @Test()
  public void testEntryCachePreload()
         throws Exception
  {
    // Make sure the entry cache is empty.
    assertNull(toVerboseString(),
      "Expected empty cache.  " + "Cache contents:" + ServerConstants.EOL +
      toVerboseString());

    // Preload.
    Backend backend = DirectoryServer.getBackend("cacheTest");
    backend.preloadEntryCache();

    // Check that all test entries are preloaded.
    for(int i = 0; i < NUMTESTENTRIES; i++ ) {
      assertNotNull(DirectoryServer.getEntryCache().getEntry(
        testEntriesList.get(i).getDN()), "Expected to find " +
        testEntriesList.get(i).getDN().toString() +
        " in the cache.  Cache contents:" +
        ServerConstants.EOL + toVerboseString());
    }
  }



  /**
   * Finalize the entry cache pre-load test.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @AfterClass()
  public void preloadEntryCacheTestFini()
         throws Exception
  {
    // Dummy JE backend cleanup.
    TestCaseUtils.dsconfig("delete-backend", "--backend-name", "cacheTest");
    TestCaseUtils.deleteDirectory(jeBackendTempDir);

    // Sanity in-core restart.
    TestCaseUtils.restartServer();

    // Remove default FS cache JE environment.
    FileSystemEntryCacheCfg config =
      (FileSystemEntryCacheCfg) configuration;
    TestCaseUtils.deleteDirectory(new File(config.getCacheDirectory()));
  }



  /**
   * Reflection of the toVerboseString implementation method.
   */
  protected String toVerboseString()
            throws Exception
  {
    final Method[] cacheMethods =
        DirectoryServer.getEntryCache().getClass().getDeclaredMethods();

    for (int i = 0; i < cacheMethods.length; ++i) {
      if (cacheMethods[i].getName().equals("toVerboseString")) {
        cacheMethods[i].setAccessible(true);
        Object verboseString =
          cacheMethods[i].invoke(DirectoryServer.getEntryCache(),
          (Object[]) null);
        return (String) verboseString;
      }
    }

    return null;
  }
}
