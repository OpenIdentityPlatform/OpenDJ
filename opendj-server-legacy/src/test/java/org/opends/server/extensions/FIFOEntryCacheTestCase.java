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
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import java.util.ArrayList;

import org.opends.server.TestCaseUtils;
import org.testng.annotations.BeforeClass;
import org.forgerock.opendj.server.config.meta.*;
import org.forgerock.opendj.server.config.server.FIFOEntryCacheCfg;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.Entry;
import org.opends.server.util.ServerConstants;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterGroups;
import org.testng.annotations.BeforeGroups;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * A set of test cases for FIFO entry cache implementation.
 */
@Test(groups = "entrycache", sequential=true)
public class FIFOEntryCacheTestCase
       extends CommonEntryCacheTestCase<FIFOEntryCacheCfg>
{
  /**
   * Initialize the entry cache test.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass
  public void entryCacheTestInit()
         throws Exception
  {
    // Ensure that the server is running.
    TestCaseUtils.startServer();

    // Configure this entry cache.
    Entry cacheConfigEntry = TestCaseUtils.makeEntry(
      "dn: cn=FIFO,cn=Entry Caches,cn=config",
      "objectClass: ds-cfg-fifo-entry-cache",
      "objectClass: ds-cfg-entry-cache",
      "objectClass: top",
      "cn: FIFO",
      "ds-cfg-cache-level: 1",
      "ds-cfg-java-class: org.opends.server.extensions.FIFOEntryCache",
      "ds-cfg-enabled: true",
      "ds-cfg-max-entries: " + super.MAXENTRIES);
    super.configuration = InitializationUtils.getConfiguration(
      FIFOEntryCacheCfgDefn.getInstance(), cacheConfigEntry);

    // Force GC to make sure we have enough memory for
    // the cache capping constraints to work properly.
    System.gc();

    // Initialize the cache.
    super.cache = new FIFOEntryCache();
    super.cache.initializeEntryCache(TestCaseUtils.getServerContext(), configuration);

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

  /** {@inheritDoc} */
  @Test
  @Override
  public void testHandleLowMemory()
         throws Exception
  {
    assertNull(cache.toVerboseString(),
      "Expected empty cache.  " + "Cache contents:" + ServerConstants.EOL +
      cache.toVerboseString());

    String b = TestCaseUtils.getServerContext().getBackendConfigManager()
        .findLocalBackendForEntry(DN.valueOf("o=test")).getBackendID();

    for(int i = 0; i < super.NUMTESTENTRIES; i++ ) {
      super.cache.putEntry(super.testEntriesList.get(i), b, i);
    }

    super.cache.handleLowMemory();

    // Make sure that the entries put previously on the
    // cache are no longer there after handleLowMemory.
    for(int i = 0; i < super.NUMTESTENTRIES; i++ ) {
      assertFalse(super.cache.containsEntry(
        super.testEntriesList.get(i).getName()), "Not expected to find " +
        super.testEntriesList.get(i).getName() + " in the " +
        "cache.  Cache contents:" + ServerConstants.EOL +
        cache.toVerboseString());
    }

    // Clear the cache so that other tests can start from scratch.
    super.cache.clear();
  }



  @BeforeGroups(groups = "testFIFOCacheConcurrency")
  public void cacheConcurrencySetup()
         throws Exception
  {
    assertNull(cache.toVerboseString(),
      "Expected empty cache.  " + "Cache contents:" + ServerConstants.EOL +
      cache.toVerboseString());
  }



  @AfterGroups(groups = "testFIFOCacheConcurrency")
  public void cacheConcurrencyCleanup()
         throws Exception
  {
    // Clear the cache so that other tests can start from scratch.
    super.cache.clear();
  }



  /** {@inheritDoc} */
  @Test(groups = { "slow", "testFIFOCacheConcurrency" },
        threadPoolSize = 10,
        invocationCount = 10,
        timeOut = 60000)
  @Override
  public void testCacheConcurrency()
         throws Exception
  {
    super.testCacheConcurrency();
  }



  /**
   * Tests cache rotation on specific number of entries.
   */
  @Test
  public void testCacheRotation()
         throws Exception
  {
    assertNull(cache.toVerboseString(),
      "Expected empty cache.  " + "Cache contents:" + ServerConstants.EOL +
      cache.toVerboseString());

    String b = TestCaseUtils.getServerContext().getBackendConfigManager()
        .findLocalBackendForEntry(DN.valueOf("o=test")).getBackendID();

    for(int i = 0; i < super.NUMTESTENTRIES; i++ ) {
      super.cache.putEntry(super.testEntriesList.get(i), b, i);
    }

    // Make sure first NUMTESTENTRIES - MAXENTRIES got rotated.
    for(int i = 0; i < (super.NUMTESTENTRIES - super.MAXENTRIES); i++ ) {
      assertFalse(super.cache.containsEntry(
        super.testEntriesList.get(i).getName()), "Not expected to find " +
        super.testEntriesList.get(i).getName() + " in the " +
        "cache.  Cache contents:" + ServerConstants.EOL +
        cache.toVerboseString());
    }

    // Make sure remaining NUMTESTENTRIES are still in the cache.
    for(int i = (super.NUMTESTENTRIES - super.MAXENTRIES);
        i < super.NUMTESTENTRIES;
        i++)
    {
      assertTrue(super.cache.containsEntry(
        super.testEntriesList.get(i).getName()), "Expected to find " +
        super.testEntriesList.get(i).getName() + " in the " +
        "cache.  Cache contents:" + ServerConstants.EOL +
        cache.toVerboseString());
    }

    // Clear the cache so that other tests can start from scratch.
    super.cache.clear();
  }
}
