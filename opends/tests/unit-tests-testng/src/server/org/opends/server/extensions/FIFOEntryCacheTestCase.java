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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import java.util.ArrayList;
import org.opends.server.TestCaseUtils;
import org.opends.server.admin.server.AdminTestCaseUtils;
import org.testng.annotations.BeforeClass;
import org.opends.server.admin.std.meta.*;
import org.opends.server.api.Backend;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.util.ServerConstants;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;
import static org.testng.Assert.*;



/**
 * A set of test cases for FIFO entry cache implementation.
 */
@Test(groups = "entrycache")
public class FIFOEntryCacheTestCase
       extends CommonEntryCacheTestCase
{
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

    // Configure this entry cache.
    Entry cacheConfigEntry = TestCaseUtils.makeEntry(
      "dn: cn=Entry Cache,cn=config",
      "objectClass: ds-cfg-fifo-entry-cache",
      "objectClass: ds-cfg-entry-cache",
      "objectClass: top",
      "cn: Entry Cache",
      "ds-cfg-entry-cache-class: org.opends.server.extensions.FIFOEntryCache",
      "ds-cfg-entry-cache-enabled: true",
      "ds-cfg-max-entries: " + Integer.toString(super.MAXENTRIES));
    super.configuration = AdminTestCaseUtils.getConfiguration(
      EntryCacheCfgDefn.getInstance(), cacheConfigEntry);

    // Force GC to make sure we have enough memory for
    // the cache capping constraints to work properly.
    System.gc();

    // Initialize the cache.
    super.cache = new FIFOEntryCache();
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
  @AfterClass()
  public void entryCacheTestFini()
         throws Exception
  {
    super.cache.finalizeEntryCache();
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
    assertNull(super.toVerboseString(),
      "Expected empty cache.  " + "Cache contents:" + ServerConstants.EOL +
      super.toVerboseString());

    Backend b = DirectoryServer.getBackend(DN.decode("o=test"));

    for(int i = 0; i < super.NUMTESTENTRIES; i++ ) {
      super.cache.putEntry(super.testEntriesList.get(i), b, i);
    }

    super.cache.handleLowMemory();

    // Make sure that the entries put previously on the
    // cache are no longer there after handleLowMemory.
    for(int i = 0; i < super.NUMTESTENTRIES; i++ ) {
      assertFalse(super.cache.containsEntry(
        super.testEntriesList.get(i).getDN()), "Not expected to find " +
        super.testEntriesList.get(i).getDN().toString() + " in the " +
        "cache.  Cache contents:" + ServerConstants.EOL +
        super.toVerboseString());
    }

    // Clear the cache so that other tests can start from scratch.
    super.cache.clear();
  }



  /**
   * {@inheritDoc}
   */
  @Test(groups="slow",
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
  @Test()
  public void testCacheRotation()
         throws Exception
  {
    assertNull(super.toVerboseString(),
      "Expected empty cache.  " + "Cache contents:" + ServerConstants.EOL +
      super.toVerboseString());

    Backend b = DirectoryServer.getBackend(DN.decode("o=test"));

    for(int i = 0; i < super.NUMTESTENTRIES; i++ ) {
      super.cache.putEntry(super.testEntriesList.get(i), b, i);
    }

    // Make sure first MAXENTRIES out of NUMTESTENTRIES rotated.
    for(int i = 0; i < super.MAXENTRIES; i++ ) {
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
}
