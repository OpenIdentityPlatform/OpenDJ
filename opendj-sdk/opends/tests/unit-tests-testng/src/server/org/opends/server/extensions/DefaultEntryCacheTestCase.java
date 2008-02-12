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
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.SortedMap;
import java.util.TreeMap;
import org.opends.server.TestCaseUtils;
import org.opends.server.admin.server.AdminTestCaseUtils;
import org.testng.annotations.BeforeClass;
import org.opends.server.admin.std.meta.*;
import org.opends.server.admin.std.server.EntryCacheCfg;
import org.opends.server.admin.std.server.FileSystemEntryCacheCfg;
import org.opends.server.api.Backend;
import org.opends.server.api.EntryCache;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.util.ServerConstants;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterGroups;
import org.testng.annotations.BeforeGroups;
import org.testng.annotations.Test;
import static org.testng.Assert.*;



/**
 * A set of test cases for default entry cache implementation.
 */
@Test(groups = "entrycache", sequential=true)
public class DefaultEntryCacheTestCase
       extends CommonEntryCacheTestCase
{
  // Entry cache implementations participating in this test.
  private EntryCache softRefCache = null;
  private EntryCache fifoCache = null;
  private EntryCache fsCache = null;

  // ... and their configuration entries.
  Entry cacheSoftReferenceConfigEntry = null;
  Entry cacheFIFOConfigEntry = null;
  Entry cacheFSConfigEntry = null;

  // The entry cache order map sorted by the cache level.
  private SortedMap<Integer, EntryCache<? extends EntryCacheCfg>>
    cacheOrderMap = new TreeMap<Integer,
    EntryCache<? extends EntryCacheCfg>>();

  // Dummy test entries for each participating implementation.
  private ArrayList<Entry> testSoftRefEntriesList = null;
  private ArrayList<Entry> testFIFOEntriesList = null;
  private ArrayList<Entry> testFSEntriesList = null;

  /**
   * Initialize the entry cache test.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass()
  @SuppressWarnings("unchecked")
  public void entryCacheTestInit()
         throws Exception
  {
    // Ensure that the server is running.
    TestCaseUtils.startServer();

    // Get default cache.
    super.cache = DirectoryServer.getEntryCache();

    // Configure and initialize all entry cache implementations.
    softRefCache = new SoftReferenceEntryCache();
    cacheSoftReferenceConfigEntry = TestCaseUtils.makeEntry(
      "dn: cn=Soft Reference,cn=Entry Caches,cn=config",
      "objectClass: ds-cfg-soft-reference-entry-cache",
      "objectClass: ds-cfg-entry-cache",
      "objectClass: top",
      "cn: Soft Reference",
      "ds-cfg-cache-level: 1",
      "ds-cfg-java-class: " +
      "org.opends.server.extensions.SoftReferenceEntryCache",
      "ds-cfg-enabled: true",
      "ds-cfg-include-filter: uid=softref*",
      "ds-cfg-include-filter: uid=test1*",
      "ds-cfg-exclude-filter: uid=test0*");
    softRefCache.initializeEntryCache(AdminTestCaseUtils.getConfiguration(
      EntryCacheCfgDefn.getInstance(), cacheSoftReferenceConfigEntry));
    cacheOrderMap.put(1, softRefCache);

    fifoCache = new FIFOEntryCache();
    cacheFIFOConfigEntry = TestCaseUtils.makeEntry(
      "dn: cn=FIFO,cn=Entry Caches,cn=config",
      "objectClass: ds-cfg-fifo-entry-cache",
      "objectClass: ds-cfg-entry-cache",
      "objectClass: top",
      "cn: FIFO",
      "ds-cfg-cache-level: 2",
      "ds-cfg-java-class: org.opends.server.extensions.FIFOEntryCache",
      "ds-cfg-enabled: true",
      "ds-cfg-include-filter: uid=fifo*",
      "ds-cfg-include-filter: uid=test2*",
      "ds-cfg-exclude-filter: uid=test0*");
    fifoCache.initializeEntryCache(AdminTestCaseUtils.getConfiguration(
      EntryCacheCfgDefn.getInstance(), cacheFIFOConfigEntry));
    cacheOrderMap.put(2, fifoCache);

    fsCache = new FileSystemEntryCache();
    cacheFSConfigEntry = TestCaseUtils.makeEntry(
      "dn: cn=File System,cn=Entry Caches,cn=config",
      "objectClass: ds-cfg-file-system-entry-cache",
      "objectClass: ds-cfg-entry-cache",
      "objectClass: top",
      "cn: File System",
      "ds-cfg-cache-level: 3",
      "ds-cfg-java-class: " +
      "org.opends.server.extensions.FileSystemEntryCache",
      "ds-cfg-enabled: true",
      "ds-cfg-include-filter: uid=fs*",
      "ds-cfg-include-filter: uid=test3*",
      "ds-cfg-include-filter: uid=test0*");
    fsCache.initializeEntryCache(AdminTestCaseUtils.getConfiguration(
      EntryCacheCfgDefn.getInstance(), cacheFSConfigEntry));
    cacheOrderMap.put(3, fsCache);

    // Plug all cache implementations into default entry cache.
    final Method[] defaultCacheMethods =
        super.cache.getClass().getDeclaredMethods();
    for (int i = 0; i < defaultCacheMethods.length; ++i) {
      if (defaultCacheMethods[i].getName().equals("setCacheOrder")) {
        defaultCacheMethods[i].setAccessible(true);
        Object arglist[] = new Object[] { cacheOrderMap };
        defaultCacheMethods[i].invoke(cache, arglist);
      }
    }

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
    testSoftRefEntriesList = new ArrayList<Entry>(super.NUMTESTENTRIES);
    for(int i = 0; i < super.NUMTESTENTRIES; i++ ) {
      testSoftRefEntriesList.add(TestCaseUtils.makeEntry(
        "dn: uid=softref" + Integer.toString(i) + ".user" + Integer.toString(i)
         + ",ou=test" + Integer.toString(i) + ",o=test",
        "objectClass: person",
        "objectClass: inetorgperson",
        "objectClass: top",
        "objectClass: organizationalperson",
        "uid: softref" + Integer.toString(i) + ".user" + Integer.toString(i))
      );
    }
    testFIFOEntriesList = new ArrayList<Entry>(super.NUMTESTENTRIES);
    for(int i = 0; i < super.NUMTESTENTRIES; i++ ) {
      testFIFOEntriesList.add(TestCaseUtils.makeEntry(
        "dn: uid=fifo" + Integer.toString(i) + ".user" + Integer.toString(i)
         + ",ou=test" + Integer.toString(i) + ",o=test",
        "objectClass: person",
        "objectClass: inetorgperson",
        "objectClass: top",
        "objectClass: organizationalperson",
        "uid: fifo" + Integer.toString(i) + ".user" + Integer.toString(i))
      );
    }
    testFSEntriesList = new ArrayList<Entry>(super.NUMTESTENTRIES);
    for(int i = 0; i < super.NUMTESTENTRIES; i++ ) {
      testFSEntriesList.add(TestCaseUtils.makeEntry(
        "dn: uid=fs" + Integer.toString(i) + ".user" + Integer.toString(i)
         + ",ou=test" + Integer.toString(i) + ",o=test",
        "objectClass: person",
        "objectClass: inetorgperson",
        "objectClass: top",
        "objectClass: organizationalperson",
        "uid: fs" + Integer.toString(i) + ".user" + Integer.toString(i))
      );
    }

    // Force GC to make sure we have enough memory for
    // the cache capping constraints to work properly.
    System.gc();
  }



  /**
   * Finalize the entry cache test.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @AfterClass()
  public void entryCacheTestFini()
         throws Exception
  {
    // Unplug all cache implementations from default entry cache.
    SortedMap<Integer, EntryCache<? extends EntryCacheCfg>>
      emptyCacheOrderMap = new TreeMap<Integer,
      EntryCache<? extends EntryCacheCfg>>();
    final Method[] defaultCacheMethods =
        super.cache.getClass().getDeclaredMethods();
    for (int i = 0; i < defaultCacheMethods.length; ++i) {
      if (defaultCacheMethods[i].getName().equals("setCacheOrder")) {
        defaultCacheMethods[i].setAccessible(true);
        Object arglist[] = new Object[] { emptyCacheOrderMap };
        defaultCacheMethods[i].invoke(cache, arglist);
      }
    }

    // Finilize all entry cache implementations.
    for (EntryCache entryCache : cacheOrderMap.values()) {
      entryCache.finalizeEntryCache();
    }

    // Remove default FS cache JE environment.
    FileSystemEntryCacheCfg config = (FileSystemEntryCacheCfg)
      AdminTestCaseUtils.getConfiguration(EntryCacheCfgDefn.getInstance(),
      cacheFSConfigEntry);
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



  /**
   * Tests the entry cache level functionality where each set
   * of entries land on a specific cache level by some form
   * of selection criteria such as include / exclude filters.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testCacheLevels()
         throws Exception
  {
    assertNull(toVerboseString(),
      "Expected empty cache.  " + "Cache contents:" + ServerConstants.EOL +
      toVerboseString());

    TestCaseUtils.initializeTestBackend(false);
    Backend b = DirectoryServer.getBackend(DN.decode("o=test"));

    // Spread test entries among all cache levels via default cache.
    for (int i = 0; i < NUMTESTENTRIES; i++) {
      super.cache.putEntry(testSoftRefEntriesList.get(i), b, i);
      super.cache.putEntry(testFIFOEntriesList.get(i), b, i);
      super.cache.putEntry(testFSEntriesList.get(i), b, i);
    }

    // Ensure all test entries are available via default cache.
    for (int i = 0; i < NUMTESTENTRIES; i++) {
      assertNotNull(super.cache.getEntry(
        testSoftRefEntriesList.get(0).getDN()),
        "Expected to find " +
        testSoftRefEntriesList.get(0).getDN().toString() +
        " in the cache.  Cache contents:" +
        ServerConstants.EOL + toVerboseString());
      assertNotNull(super.cache.getEntry(
        testFIFOEntriesList.get(0).getDN()),
        "Expected to find " +
        testFIFOEntriesList.get(0).getDN().toString() +
        " in the cache.  Cache contents:" +
        ServerConstants.EOL + toVerboseString());
      assertNotNull(super.cache.getEntry(
        testFSEntriesList.get(0).getDN()),
        "Expected to find " +
        testFSEntriesList.get(0).getDN().toString() +
        " in the cache.  Cache contents:" +
        ServerConstants.EOL + toVerboseString());
    }

    // Ensure all test entries landed on their levels.
    for (int i = 0; i < NUMTESTENTRIES; i++) {
      assertNotNull(softRefCache.getEntry(
        testSoftRefEntriesList.get(0).getDN()),
        "Expected to find " +
        testSoftRefEntriesList.get(0).getDN().toString() +
        " in the cache.  Cache contents:" +
        ServerConstants.EOL + toVerboseString());
      assertNotNull(fifoCache.getEntry(
        testFIFOEntriesList.get(0).getDN()),
        "Expected to find " +
        testFIFOEntriesList.get(0).getDN().toString() +
        " in the cache.  Cache contents:" +
        ServerConstants.EOL + toVerboseString());
      assertNotNull(fsCache.getEntry(
        testFSEntriesList.get(0).getDN()),
        "Expected to find " +
        testFSEntriesList.get(0).getDN().toString() +
        " in the cache.  Cache contents:" +
        ServerConstants.EOL + toVerboseString());
    }

    // Clear the cache so that other tests can start from scratch.
    super.cache.clear();
  }



  @BeforeGroups(groups = "testDefaultCacheConcurrency")
  public void cacheConcurrencySetup()
         throws Exception
  {
    assertNull(super.toVerboseString(),
      "Expected empty cache.  " + "Cache contents:" + ServerConstants.EOL +
      super.toVerboseString());
  }



  @AfterGroups(groups = "testDefaultCacheConcurrency")
  public void cacheConcurrencyCleanup()
         throws Exception
  {
    // Clear the cache so that other tests can start from scratch.
    super.cache.clear();
  }



  /**
   * {@inheritDoc}
   */
  @Test(groups = { "slow", "testDefaultCacheConcurrency" },
        threadPoolSize = 10,
        invocationCount = 10,
        timeOut = 60000)
  @Override
  public void testCacheConcurrency()
         throws Exception
  {
    super.testCacheConcurrency();
  }
}
