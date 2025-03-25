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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import static org.testng.Assert.*;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.SortedMap;
import java.util.TreeMap;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.server.config.meta.FIFOEntryCacheCfgDefn;
import org.forgerock.opendj.server.config.meta.SoftReferenceEntryCacheCfgDefn;
import org.forgerock.opendj.server.config.server.EntryCacheCfg;
import org.opends.server.TestCaseUtils;
import org.opends.server.api.EntryCache;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Entry;
import org.opends.server.util.ServerConstants;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterGroups;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeGroups;
import org.testng.annotations.Test;

/**
 * A set of test cases for default entry cache implementation.
 */
@Test(groups = "entrycache", singleThreaded = true)
public class DefaultEntryCacheTestCase
       extends CommonEntryCache<EntryCacheCfg>
{
  // Entry cache implementations participating in this test.
  private SoftReferenceEntryCache softRefCache;
  private FIFOEntryCache fifoCache;

  // ... and their configuration entries.
  Entry cacheSoftReferenceConfigEntry;
  Entry cacheFIFOConfigEntry;

  /** The entry cache order map sorted by the cache level. */
  private SortedMap<Integer, EntryCache<? extends EntryCacheCfg>> cacheOrderMap = new TreeMap<>();

  // Dummy test entries for each participating implementation.
  private ArrayList<Entry> testSoftRefEntriesList;
  private ArrayList<Entry> testFIFOEntriesList;

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

    // Get default cache.
    super.cache = (EntryCache<EntryCacheCfg>) DirectoryServer.getEntryCache();

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
    softRefCache.initializeEntryCache(TestCaseUtils.getServerContext(), InitializationUtils.getConfiguration(
      SoftReferenceEntryCacheCfgDefn.getInstance(), cacheSoftReferenceConfigEntry));
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
      "ds-cfg-include-filter: uid=test0*");
    fifoCache.initializeEntryCache(TestCaseUtils.getServerContext(), InitializationUtils.getConfiguration(
      FIFOEntryCacheCfgDefn.getInstance(), cacheFIFOConfigEntry));
    cacheOrderMap.put(2, fifoCache);

    // Plug all cache implementations into default entry cache.
    final Method[] defaultCacheMethods =
        super.cache.getClass().getDeclaredMethods();
    for (Method defaultCacheMethod : defaultCacheMethods)
    {
      if (defaultCacheMethod.getName().equals("setCacheOrder")) {
        defaultCacheMethod.setAccessible(true);
        Object arglist[] = new Object[] { cacheOrderMap };
        defaultCacheMethod.invoke(cache, arglist);
      }
    }

    // Make some dummy test entries.
    super.testEntriesList = new ArrayList<>(super.NUMTESTENTRIES);
    for(int i = 0; i < super.NUMTESTENTRIES; i++ ) {
      super.testEntriesList.add(TestCaseUtils.makeEntry(
        "dn: uid=test" + i + ".user" + i + ",ou=test" + i + ",o=test",
        "objectClass: person",
        "objectClass: inetorgperson",
        "objectClass: top",
        "objectClass: organizationalperson",
        "postalAddress: somewhere in Testville" + i,
        "street: Under Construction Street" + i,
        "l: Testcounty" + i,
        "st: Teststate" + i,
        "telephoneNumber: +878 8378 8378" + i,
        "mobile: +878 8378 8378" + i,
        "homePhone: +878 8378 8378" + i,
        "pager: +878 8378 8378" + i,
        "mail: test" + i + ".user" + i + "@testdomain.net",
        "postalCode: 8378" + i,
        "userPassword: testpassword" + i,
        "description: description for Test" + i + "User" + i,
        "cn: Test" + i + "User" + i,
        "sn: User" + i,
        "givenName: Test" + i,
        "initials: TST" + i,
        "employeeNumber: 8378" + i,
        "uid: test" + i + ".user" + i)
      );
    }
    testSoftRefEntriesList = new ArrayList<>(super.NUMTESTENTRIES);
    for(int i = 0; i < super.NUMTESTENTRIES; i++ ) {
      testSoftRefEntriesList.add(TestCaseUtils.makeEntry(
        "dn: uid=softref" + i + ".user" + i + ",ou=test" + i + ",o=test",
        "objectClass: person",
        "objectClass: inetorgperson",
        "objectClass: top",
        "objectClass: organizationalperson",
        "uid: softref" + i + ".user" + i)
      );
    }
    testFIFOEntriesList = new ArrayList<>(super.NUMTESTENTRIES);
    for(int i = 0; i < super.NUMTESTENTRIES; i++ ) {
      testFIFOEntriesList.add(TestCaseUtils.makeEntry(
        "dn: uid=fifo" + i + ".user" + i + ",ou=test" + i + ",o=test",
        "objectClass: person",
        "objectClass: inetorgperson",
        "objectClass: top",
        "objectClass: organizationalperson",
        "uid: fifo" + i + ".user" + i)
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
  @AfterClass
  public void entryCacheTestFini()
         throws Exception
  {
    // Unplug all cache implementations from default entry cache.
    SortedMap<Integer, EntryCache<? extends EntryCacheCfg>> emptyCacheOrderMap = new TreeMap<>();
    final Method[] defaultCacheMethods =
        super.cache.getClass().getDeclaredMethods();
    for (Method defaultCacheMethod : defaultCacheMethods)
    {
      if (defaultCacheMethod.getName().equals("setCacheOrder")) {
        defaultCacheMethod.setAccessible(true);
        Object arglist[] = new Object[] { emptyCacheOrderMap };
        defaultCacheMethod.invoke(cache, arglist);
      }
    }

    // Finilize all entry cache implementations.
    for (EntryCache<?> entryCache : cacheOrderMap.values()) {
      entryCache.finalizeEntryCache();
    }
  }



  /** {@inheritDoc} */
  @Test
  @Override
  public void testContainsEntry()
         throws Exception
  {
    super.testContainsEntry();
  }



  /** {@inheritDoc} */
  @Test
  @Override
  public void testGetEntry1()
         throws Exception
  {
    super.testGetEntry1();
  }



  /** {@inheritDoc} */
  @Test
  @Override
  public void testGetEntry2()
         throws Exception
  {
    super.testGetEntry2();
  }



  /** {@inheritDoc} */
  @Test
  @Override
  public void testGetEntry3()
         throws Exception
  {
    super.testGetEntry3();
  }



  /** {@inheritDoc} */
  @Test
  @Override
  public void testGetEntryID()
         throws Exception
  {
    super.testGetEntryID();
  }



  /** {@inheritDoc} */
  @Test
  @Override
  public void testPutEntry()
         throws Exception
  {
    super.testPutEntry();
  }



  /** {@inheritDoc} */
  @Test
  @Override
  public void testPutEntryIfAbsent()
         throws Exception
  {
    super.testPutEntryIfAbsent();
  }



  /** {@inheritDoc} */
  @Test
  @Override
  public void testRemoveEntry()
         throws Exception
  {
    super.testRemoveEntry();
  }



  /** {@inheritDoc} */
  @Test
  @Override
  public void testClear()
         throws Exception
  {
    super.testClear();
  }



  /** {@inheritDoc} */
  @Test
  @Override
  public void testClearBackend()
         throws Exception
  {
    super.testClearBackend();
  }

  /**
   * Tests the entry cache level functionality where each set
   * of entries land on a specific cache level by some form
   * of selection criteria such as include / exclude filters.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(singleThreaded = true)
  public void testCacheLevels()
         throws Exception
  {
    assertNull(cache.toVerboseString(),
      "Expected empty cache.  " + "Cache contents:" + ServerConstants.EOL +
      cache.toVerboseString());

    TestCaseUtils.initializeTestBackend(false);
    String b = TestCaseUtils.getServerContext().getBackendConfigManager()
        .findLocalBackendForEntry(DN.valueOf("o=test")).getBackendID();

    // Spread test entries among all cache levels via default cache.
    for (int i = 0; i < NUMTESTENTRIES; i++) {
      super.cache.putEntry(testSoftRefEntriesList.get(i), b, i);
      super.cache.putEntry(testFIFOEntriesList.get(i), b, i);
    }

    // Ensure all test entries are available via default cache.
    for (int i = 0; i < NUMTESTENTRIES; i++) {
      assertNotNull(super.cache.getEntry(
        testSoftRefEntriesList.get(0).getName()),
        "Expected to find " +
        testSoftRefEntriesList.get(0).getName() +
        " in the cache.  Cache contents:" +
        ServerConstants.EOL + cache.toVerboseString());
      assertNotNull(super.cache.getEntry(
        testFIFOEntriesList.get(0).getName()),
        "Expected to find " +
        testFIFOEntriesList.get(0).getName() +
        " in the cache.  Cache contents:" +
        ServerConstants.EOL + cache.toVerboseString());
    }

    // Ensure all test entries landed on their levels.
    for (int i = 0; i < NUMTESTENTRIES; i++) {
      assertNotNull(softRefCache.getEntry(
        testSoftRefEntriesList.get(0).getName()),
        "Expected to find " +
        testSoftRefEntriesList.get(0).getName() +
        " in the cache.  Cache contents:" +
        ServerConstants.EOL + cache.toVerboseString());
      assertNotNull(fifoCache.getEntry(
        testFIFOEntriesList.get(0).getName()),
        "Expected to find " +
        testFIFOEntriesList.get(0).getName() +
        " in the cache.  Cache contents:" +
        ServerConstants.EOL + cache.toVerboseString());
    }

    // Clear the cache so that other tests can start from scratch.
    super.cache.clear();
  }



  @BeforeGroups(groups = "testDefaultCacheConcurrency")
  public void cacheConcurrencySetup() throws Exception
  {
    assertNull(cache.toVerboseString(),
      "Expected empty cache.  " + "Cache contents:" + ServerConstants.EOL +
      cache.toVerboseString());
  }



  @AfterGroups(groups = "testDefaultCacheConcurrency")
  public void cacheConcurrencyCleanup() throws Exception
  {
    // Clear the cache so that other tests can start from scratch.
    super.cache.clear();
  }



  /** {@inheritDoc} */
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
