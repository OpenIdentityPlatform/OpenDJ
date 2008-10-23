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
 *      Copyright 2007-2008 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import java.io.File;
import java.util.ArrayList;
import org.opends.server.TestCaseUtils;
import org.opends.server.admin.server.AdminTestCaseUtils;
import org.testng.annotations.BeforeClass;
import org.opends.server.admin.std.meta.*;
import org.opends.server.admin.std.server.FileSystemEntryCacheCfg;
import org.opends.server.api.Backend;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.util.ServerConstants;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterGroups;
import org.testng.annotations.BeforeGroups;
import org.testng.annotations.Test;
import static org.testng.Assert.*;



/**
 * A set of test cases for FileSystem entry cache implementation.
 */
@Test(groups = "entrycache", sequential=true)
public class FileSystemEntryCacheTestCase
       extends CommonEntryCacheTestCase
{
  /**
   * Configuration entry for this cache.
   */
  private Entry cacheConfigEntry;

  /**
   * Temporary folder to setup dummy JE backend environment in.
   */
  private File jeBackendTempDir;

  /**
   * Utility method to restore default cache configuration.
   */
  @SuppressWarnings("unchecked")
  private void restoreCacheDefaults()
          throws Exception
  {
    // Finalize this cache so it can be reconfigured.
    super.cache.finalizeEntryCache();

    // Configure this cache back to defaults.
    super.configuration = AdminTestCaseUtils.getConfiguration(
      EntryCacheCfgDefn.getInstance(), cacheConfigEntry);

    // Initialize the cache.
    super.cache = new FileSystemEntryCache();
    super.cache.initializeEntryCache(configuration);

    // Make sure the cache is empty.
    assertNull(super.toVerboseString(),
      "Expected empty cache.  " + "Cache contents:" + ServerConstants.EOL +
      super.toVerboseString());
  }



  /**
   * Utility method to configure the cache with LRU access order.
   */
  @SuppressWarnings("unchecked")
  private void setupLRUCache()
          throws Exception
  {
    // Finalize this cache so it can be reconfigured.
    super.cache.finalizeEntryCache();

    // Configure this cache as LRU.
    Entry newCacheConfigEntry = cacheConfigEntry.duplicate(true);
    Attribute cacheConfigTypeAttr =
      Attributes.create("ds-cfg-cache-type", "LRU");
    newCacheConfigEntry.addAttribute(cacheConfigTypeAttr, null);
    super.configuration = AdminTestCaseUtils.getConfiguration(
      EntryCacheCfgDefn.getInstance(), newCacheConfigEntry);

    // Initialize the cache.
    super.cache = new FileSystemEntryCache();
    super.cache.initializeEntryCache(configuration);

    // Make sure the cache is empty.
    assertNull(super.toVerboseString(),
      "Expected empty cache.  " + "Cache contents:" + ServerConstants.EOL +
      super.toVerboseString());
  }



  /**
   * Utility method to initialize persistent cache.
   */
  @SuppressWarnings("unchecked")
  private void persistentCacheSetup()
          throws Exception
  {
    // Make sure JE directory exist.
    jeBackendTempDir = TestCaseUtils.createTemporaryDirectory("db-cachetest");
    String jeDir = jeBackendTempDir.getAbsolutePath();

    // Create dummy JE backend for this test.
    TestCaseUtils.dsconfig("create-backend", "--backend-name", "cacheTest",
      "--type", "local-db", "--set", "db-directory:" + jeDir, "--set",
      "base-dn:o=cachetest", "--set",
      "writability-mode:enabled", "--set", "enabled:true");

    // Finalize this cache so it can be reconfigured.
    super.cache.finalizeEntryCache();

    // Configure this cache as persistent cache with
    // unlimited number of entries.
    Entry newCacheConfigEntry = cacheConfigEntry.duplicate(true);
    Attribute cacheConfigPersistAttr =
      Attributes.create("ds-cfg-persistent-cache", "true");
    newCacheConfigEntry.addAttribute(cacheConfigPersistAttr, null);
    Attribute cacheConfigMaxAttr =
      Attributes.create("ds-cfg-max-entries", Integer.toString(super.MAXENTRIES));
    newCacheConfigEntry.removeAttribute(cacheConfigMaxAttr, null);
    super.configuration = AdminTestCaseUtils.getConfiguration(
      EntryCacheCfgDefn.getInstance(), newCacheConfigEntry);

    // Initialize the cache.
    super.cache = new FileSystemEntryCache();
    super.cache.initializeEntryCache(configuration);

    // Make sure the cache is empty.
    assertNull(super.toVerboseString(),
      "Expected empty cache.  " + "Cache contents:" + ServerConstants.EOL +
      super.toVerboseString());
  }



  /**
   * Utility method to finalize persistent cache.
   */
  private void persistentCacheTeardown()
          throws Exception
  {
    // Dummy JE backend cleanup.
    TestCaseUtils.dsconfig("delete-backend", "--backend-name", "cacheTest");
    TestCaseUtils.deleteDirectory(jeBackendTempDir);

    // Configure this cache back to defaults.
    restoreCacheDefaults();
  }



  /**
   * Initialize the entry cache test.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass
  @SuppressWarnings("unchecked")
  public void entryCacheTestInit()
         throws Exception
  {
    // Ensure that the server is running.
    TestCaseUtils.startServer();

    // Configure this entry cache.
    cacheConfigEntry = TestCaseUtils.makeEntry(
      "dn: cn=File System,cn=Entry Caches,cn=config",
      "objectClass: ds-cfg-file-system-entry-cache",
      "objectClass: ds-cfg-entry-cache",
      "objectClass: top",
      "cn: File System",
      "ds-cfg-cache-level: 1",
      "ds-cfg-java-class: " +
      "org.opends.server.extensions.FileSystemEntryCache",
      "ds-cfg-enabled: true",
      "ds-cfg-max-entries: " + Integer.toString(super.MAXENTRIES));
    super.configuration = AdminTestCaseUtils.getConfiguration(
      EntryCacheCfgDefn.getInstance(), cacheConfigEntry);

    // Force GC to make sure we have enough memory for
    // the cache capping constraints to work properly.
    System.gc();

    // Initialize the cache.
    super.cache = new FileSystemEntryCache();
    super.cache.initializeEntryCache(configuration);

    // Make some dummy test entries.
    super.testEntriesList = new ArrayList<Entry>(super.NUMTESTENTRIES);
    for(int i = 0; i < super.NUMTESTENTRIES; i++ ) {
      super.testEntriesList.add(TestCaseUtils.makeEntry(
        "dn: uid=test" + Integer.toString(i) + ".user" + Integer.toString(i)
         + ",ou=test" + Integer.toString(i) + ",o=test",
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
        "uid: test" + Integer.toString(i) + ".user" + Integer.toString(i))
      );
    }
  }



  /**
   * Finalize the entry cache test.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @AfterClass
  public void entryCacheTestFini()
         throws Exception
  {
    super.cache.finalizeEntryCache();

    // Remove default FS cache JE environment.
    FileSystemEntryCacheCfg config =
      (FileSystemEntryCacheCfg) super.configuration;
    TestCaseUtils.deleteDirectory(new File(config.getCacheDirectory()));
  }



  /**
   * {@inheritDoc}
   */
  @Test()
  @Override
  public void testContainsEntry()
         throws Exception
  {
    super.testContainsEntry();
  }



  /**
   * {@inheritDoc}
   */
  @Test()
  @Override
  public void testGetEntry1()
         throws Exception
  {
    super.testGetEntry1();
  }



  /**
   * {@inheritDoc}
   */
  @Test()
  @Override
  public void testGetEntry2()
         throws Exception
  {
    super.testGetEntry2();
  }



  /**
   * {@inheritDoc}
   */
  @Test()
  @Override
  public void testGetEntry3()
         throws Exception
  {
    super.testGetEntry3();
  }



  /**
   * {@inheritDoc}
   */
  @Test()
  @Override
  public void testGetEntryID()
         throws Exception
  {
    super.testGetEntryID();
  }



  /**
   * {@inheritDoc}
   */
  @Test()
  @Override
  public void testPutEntry()
         throws Exception
  {
    super.testPutEntry();
  }



  /**
   * {@inheritDoc}
   */
  @Test()
  @Override
  public void testPutEntryIfAbsent()
         throws Exception
  {
    super.testPutEntryIfAbsent();
  }



  /**
   * {@inheritDoc}
   */
  @Test()
  @Override
  public void testRemoveEntry()
         throws Exception
  {
    super.testRemoveEntry();
  }



  /**
   * {@inheritDoc}
   */
  @Test()
  @Override
  public void testClear()
         throws Exception
  {
    super.testClear();
  }



  /**
   * {@inheritDoc}
   */
  @Test()
  @Override
  public void testClearBackend()
         throws Exception
  {
    super.testClearBackend();
  }



  /**
   * {@inheritDoc}
   */
  @Test()
  @Override
  public void testClearSubtree()
         throws Exception
  {
    super.testClearSubtree();
  }



  /**
   * {@inheritDoc}
   */
  @Test()
  @Override
  public void testHandleLowMemory()
         throws Exception
  {
    super.testHandleLowMemory();
  }



  @BeforeGroups(groups = "testFSFIFOCacheConcurrency")
  public void cacheConcurrencySetup()
         throws Exception
  {
    assertNull(super.toVerboseString(),
      "Expected empty cache.  " + "Cache contents:" + ServerConstants.EOL +
      super.toVerboseString());
  }



  @AfterGroups(groups = "testFSFIFOCacheConcurrency")
  public void cacheConcurrencyCleanup()
         throws Exception
  {
    // Clear the cache so that other tests can start from scratch.
    super.cache.clear();
  }



  /**
   * {@inheritDoc}
   */
  @Test(groups = { "slow", "testFSFIFOCacheConcurrency" },
        threadPoolSize = 10,
        invocationCount = 10,
        // In case of disk based FS.
        timeOut = 600000)
  @Override
  public void testCacheConcurrency()
         throws Exception
  {
    super.testCacheConcurrency();
  }



  @BeforeGroups(groups = "testFSLRUCacheConcurrency")
  public void LRUCacheConcurrencySetup()
         throws Exception
  {
    // Setup LRU cache.
    setupLRUCache();
  }



  @AfterGroups(groups = "testFSLRUCacheConcurrency")
  public void LRUCacheConcurrencyCleanup()
         throws Exception
  {
    // Configure this cache back to defaults.
    restoreCacheDefaults();
  }



  /**
   * Tests the entry cache concurrency/threadsafety by executing
   * core entry cache operations on several threads concurrently
   * on LRU access order cache.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(groups = { "slow", "testFSLRUCacheConcurrency" },
        threadPoolSize = 10,
        invocationCount = 10,
        // In case of disk based FS.
        timeOut = 600000)
  public void testLRUCacheConcurrency()
         throws Exception
  {
    super.testCacheConcurrency();
  }



  /**
   * Tests FIFO cache rotation on specific number of entries.
   */
  @Test(groups = "slow")
  public void testCacheRotationFIFO()
         throws Exception
  {
    assertNull(super.toVerboseString(),
      "Expected empty cache.  " + "Cache contents:" + ServerConstants.EOL +
      super.toVerboseString());

    // Put some test entries in the cache.
    Backend b = DirectoryServer.getBackend(DN.decode("o=test"));
    for(int i = 0; i < super.NUMTESTENTRIES; i++ ) {
      super.cache.putEntry(super.testEntriesList.get(i), b, i);
    }

    // Make sure first NUMTESTENTRIES - MAXENTRIES got rotated.
    for(int i = 0; i < (super.NUMTESTENTRIES - super.MAXENTRIES); i++ ) {
      assertFalse(super.cache.containsEntry(
        super.testEntriesList.get(i).getDN()), "Not expected to find " +
        super.testEntriesList.get(i).getDN().toString() + " in the " +
        "cache.  Cache contents:" + ServerConstants.EOL +
        super.toVerboseString());
    }

    // Make sure remaining NUMTESTENTRIES are still in the cache.
    for(int i = (super.NUMTESTENTRIES - super.MAXENTRIES);
        i < super.NUMTESTENTRIES;
        i++)
    {
      assertTrue(super.cache.containsEntry(
        super.testEntriesList.get(i).getDN()), "Expected to find " +
        super.testEntriesList.get(i).getDN().toString() + " in the " +
        "cache.  Cache contents:" + ServerConstants.EOL +
        super.toVerboseString());
    }

    // Clear the cache so that other tests can start from scratch.
    super.cache.clear();
  }



  /**
   * Tests LRU cache rotation on specific number of entries.
   */
  @Test(groups = "slow")
  @SuppressWarnings("unchecked")
  public void testCacheRotationLRU()
         throws Exception
  {
    // Setup LRU cache.
    setupLRUCache();

    // Put some test entries in the cache.
    Backend b = DirectoryServer.getBackend(DN.decode("o=test"));
    for(int i = 0; i < super.NUMTESTENTRIES; i++) {
      super.cache.putEntry(super.testEntriesList.get(i), b, i);
      // Sacrifice one cache entry to support rotation.
      for(int j = 0; j < (super.MAXENTRIES - 1); j++) {
        // Generate access.
        super.cache.getEntry(super.testEntriesList.get(j).getDN());
      }
    }

    // Make sure MAXENTRIES - 1 are still in the cache.
    for(int i = 0; i < (super.MAXENTRIES - 1); i++) {
        assertTrue(super.cache.containsEntry(
          super.testEntriesList.get(i).getDN()),
          "Expected to find " +
          super.testEntriesList.get(i).getDN().toString() + " in the " +
          "cache.  Cache contents:" + ServerConstants.EOL +
          super.toVerboseString());
    }

    // Plus the last cache entry added.
    assertTrue(super.cache.containsEntry(
      super.testEntriesList.get(super.NUMTESTENTRIES - 1).getDN()),
      "Expected to find " +
      super.testEntriesList.get(super.NUMTESTENTRIES - 1).getDN().toString() +
      " in the cache.  Cache contents:" + ServerConstants.EOL +
      super.toVerboseString());

    // And remaining NUMTESTENTRIES - 1 are now rotated.
    for(int i = (super.MAXENTRIES - 1);
        i < (super.NUMTESTENTRIES - 1);
        i++) {
        assertFalse(super.cache.containsEntry(
          super.testEntriesList.get(i).getDN()),
          "Not expected to find " +
          super.testEntriesList.get(i).getDN().toString() + " in the " +
          "cache.  Cache contents:" + ServerConstants.EOL +
          super.toVerboseString());
    }

    // Clear the cache so that other tests can start from scratch.
    super.cache.clear();

    // Configure this cache back to defaults.
    restoreCacheDefaults();
  }



  /**
   * Tests cache persistence with consistent backend.
   */
  @Test(groups = "slow")
  @SuppressWarnings("unchecked")
  public void testCachePersistence()
         throws Exception
  {
    // Setup this test.
    persistentCacheSetup();

    // Put some test entries in the cache.
    Backend b = DirectoryServer.getBackend(DN.decode("o=cachetest"));
    for(int i = 0; i < super.NUMTESTENTRIES; i++) {
      super.cache.putEntry(super.testEntriesList.get(i), b, i);
    }

    // Should trigger backend checksum.
    b.finalizeBackend();

    // Finalize and persist this cache.
    super.cache.finalizeEntryCache();

    // Get cachetest backend online again.
    b.initializeBackend();

    // Initialize the cache again.
    super.cache = new FileSystemEntryCache();
    super.cache.initializeEntryCache(configuration);

    // Check that this cache is persistent indeed.
    for(int i = 0; i < super.NUMTESTENTRIES; i++) {
      assertTrue(super.cache.containsEntry(
          super.testEntriesList.get(i).getDN()),
          "Expected to find " +
          super.testEntriesList.get(i).getDN().toString() + " in the " +
          "cache.  Cache contents:" + ServerConstants.EOL +
          super.toVerboseString());
    }

    // Clear the cache so that other tests can start from scratch.
    super.cache.clear();

    // Finalize this cache so it can be reconfigured.
    super.cache.finalizeEntryCache();

    // Clean up.
    b.finalizeBackend();
    persistentCacheTeardown();
  }



  /**
   * Tests cache persistence with inconsistent backend.
   */
  @Test(groups = "slow")
  @SuppressWarnings("unchecked")
  public void testCachePersistenceInconsistent()
         throws Exception
  {
    // Setup this test.
    persistentCacheSetup();

    // Put some test entries in the cache.
    Backend b = DirectoryServer.getBackend(DN.decode("o=cachetest"));
    for(int i = 0; i < super.NUMTESTENTRIES; i++) {
      super.cache.putEntry(super.testEntriesList.get(i), b, i);
    }

    // Should trigger backend checksum.
    b.finalizeBackend();

    // Finalize and persist this cache.
    super.cache.finalizeEntryCache();

    // Get cachetest backend online again.
    b.initializeBackend();

    // Add dummy entry to cachetest backend to trigger inconsistent
    // offline state with persistent entry cache.
    TestCaseUtils.addEntry(
      "dn: o=cachetest",
      "objectClass: top",
      "objectClass: organization");

    // Should trigger backend checksum.
    b.finalizeBackend();

    // Get cachetest backend online again, now modified.
    b.initializeBackend();

    // Initialize the cache again.
    super.cache = new FileSystemEntryCache();
    super.cache.initializeEntryCache(configuration);

    // Check that this cache is persistent indeed.
    for(int i = 0; i < super.NUMTESTENTRIES; i++) {
      assertFalse(super.cache.containsEntry(
          super.testEntriesList.get(i).getDN()),
          "Not expected to find " +
          super.testEntriesList.get(i).getDN().toString() + " in the " +
          "cache.  Cache contents:" + ServerConstants.EOL +
          super.toVerboseString());
    }

    // Clear the cache so that other tests can start from scratch.
    super.cache.clear();

    // Finalize this cache so it can be reconfigured.
    super.cache.finalizeEntryCache();

    // Clean up.
    b.finalizeBackend();
    persistentCacheTeardown();
  }
}
