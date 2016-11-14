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

import static org.testng.Assert.*;

import java.util.ArrayList;

import org.opends.server.TestCaseUtils;
import org.forgerock.opendj.server.config.server.EntryCacheCfg;
import org.opends.server.api.EntryCache;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.Entry;
import org.opends.server.util.ServerConstants;
import org.testng.annotations.AfterClass;

/**
 * A common set of test cases for all entry cache implementations.
 * @param <C> The type of entry cache configuration.
 */
public abstract class CommonEntryCacheTestCase<C extends EntryCacheCfg>
       extends ExtensionsTestCase
{
  /**
   * Number of unique dummy test entries.
   * Note that the value affects MAXENTRIES.
   */
  protected int NUMTESTENTRIES = 30;



  /**
   * Maximum number of entries the cache can hold.
   * Note that the value depends on NUMTESTENTRIES.
   */
  protected int MAXENTRIES = 10;



  /**
   * Number of loops for each concurrency test.
   */
  protected int CONCURRENCYLOOPS = 100;



  /**
   * Dummy test entries.
   * Note that this list should contain at least two entry
   * elements for the following tests to function properly.
   */
  protected ArrayList<Entry> testEntriesList;



  /**
   * Cache implementation instance.
   */
  protected EntryCache<C> cache;



  /**
   * Entry cache configuration instance.
   */
  protected C configuration;



  /**
   * Tests the <CODE>containsEntry</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  public void testContainsEntry()
         throws Exception
  {
    assertNull(cache.toVerboseString(),
      "Expected empty cache.  " + "Cache contents:" + ServerConstants.EOL +
      cache.toVerboseString());

    TestCaseUtils.initializeTestBackend(false);
    String b = TestCaseUtils.getServerContext().getBackendConfigManager()
        .findLocalBackendForEntry(DN.valueOf("o=test")).getBackendID();

    assertFalse(cache.containsEntry(testEntriesList.get(0).getName()),
      "Not expected to find " + testEntriesList.get(0).getName() +
      " in the cache.  Cache contents:" + ServerConstants.EOL +
      cache.toVerboseString());

    cache.putEntry(testEntriesList.get(0), b, 1);

    assertTrue(cache.containsEntry(testEntriesList.get(0).getName()),
      "Expected to find " + testEntriesList.get(0).getName() +
      " in the cache.  Cache contents:" + ServerConstants.EOL +
      cache.toVerboseString());

    // Clear the cache so that other tests can start from scratch.
    cache.clear();
  }



  /**
   * Tests the first <CODE>getEntry</CODE> method, which takes a single DN
   * argument.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  public void testGetEntry1()
         throws Exception
  {
    assertNull(cache.toVerboseString(),
      "Expected empty cache.  " + "Cache contents:" + ServerConstants.EOL +
      cache.toVerboseString());

    TestCaseUtils.initializeTestBackend(false);
    String b = TestCaseUtils.getServerContext().getBackendConfigManager()
        .findLocalBackendForEntry(DN.valueOf("o=test")).getBackendID();

    assertNull(cache.getEntry(testEntriesList.get(0).getName()),
      "Not expected to find " + testEntriesList.get(0).getName() +
      " in the cache.  Cache contents:" + ServerConstants.EOL +
      cache.toVerboseString());

    cache.putEntry(testEntriesList.get(0), b, 1);

    assertNotNull(cache.getEntry(testEntriesList.get(0).getName()),
      "Expected to find " + testEntriesList.get(0).getName() +
      " in the cache.  Cache contents:" + ServerConstants.EOL +
      cache.toVerboseString());

    // Clear the cache so that other tests can start from scratch.
    cache.clear();
  }



  /**
   * Tests the second <CODE>getEntry</CODE> method, which takes a DN, lock type,
   * and list attributes.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  public void testGetEntry2()
         throws Exception
  {
    assertNull(cache.toVerboseString(),
      "Expected empty cache.  " + "Cache contents:" + ServerConstants.EOL +
      cache.toVerboseString());

    TestCaseUtils.initializeTestBackend(false);
    String b = TestCaseUtils.getServerContext().getBackendConfigManager()
        .findLocalBackendForEntry(DN.valueOf("o=test")).getBackendID();

    assertNull(cache.getEntry(testEntriesList.get(0).getName()),
      "Not expected to find " + testEntriesList.get(0).getName() +
      " in the cache.  Cache contents:" + ServerConstants.EOL +
      cache.toVerboseString());

    cache.putEntry(testEntriesList.get(0), b, 1);

    assertNotNull(cache.getEntry(testEntriesList.get(0).getName()),
      "Expected to find " + testEntriesList.get(0).getName() +
      " in the cache.  Cache contents:" + ServerConstants.EOL +
      cache.toVerboseString());

    // Clear the cache so that other tests can start from scratch.
    cache.clear();
  }



  /**
   * Tests the third <CODE>getEntry</CODE> method, which takes a backend, entry
   * ID, lock type, and list attributes.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  public void testGetEntry3()
         throws Exception
  {
    assertNull(cache.toVerboseString(),
      "Expected empty cache.  " + "Cache contents:" + ServerConstants.EOL +
      cache.toVerboseString());

    TestCaseUtils.initializeTestBackend(false);
    String b = TestCaseUtils.getServerContext().getBackendConfigManager()
        .findLocalBackendForEntry(DN.valueOf("o=test")).getBackendID();

    assertNull(cache.getEntry(b, -1),
      "Not expected to find entry id " + -1 +
      " in the cache.  Cache contents:" + ServerConstants.EOL +
      cache.toVerboseString());

    cache.putEntry(testEntriesList.get(0), b, 1);

    assertNotNull(cache.getEntry(b, 1),
      "Expected to find entry id " + 1 +
      " in the cache.  Cache contents:" + ServerConstants.EOL +
      cache.toVerboseString());

    // Clear the cache so that other tests can start from scratch.
    cache.clear();
  }



  /**
   * Tests the <CODE>getEntryID</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  public void testGetEntryID()
         throws Exception
  {
    assertNull(cache.toVerboseString(),
      "Expected empty cache.  " + "Cache contents:" + ServerConstants.EOL +
      cache.toVerboseString());

    TestCaseUtils.initializeTestBackend(false);
    String b = TestCaseUtils.getServerContext().getBackendConfigManager()
        .findLocalBackendForEntry(DN.valueOf("o=test")).getBackendID();

    assertEquals(cache.getEntryID(testEntriesList.get(0).getName()), -1,
      "Not expected to find " + testEntriesList.get(0).getName() +
      " in the cache.  Cache contents:" + ServerConstants.EOL +
      cache.toVerboseString());

    cache.putEntry(testEntriesList.get(0), b, 1);

    assertEquals(cache.getEntryID(testEntriesList.get(0).getName()), 1,
      "Expected to find " + testEntriesList.get(0).getName() +
      " in the cache.  Cache contents:" + ServerConstants.EOL +
      cache.toVerboseString());

    // Clear the cache so that other tests can start from scratch.
    cache.clear();
  }



  /**
   * Tests the <CODE>putEntry</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  public void testPutEntry()
         throws Exception
  {
    assertNull(cache.toVerboseString(),
      "Expected empty cache.  " + "Cache contents:" + ServerConstants.EOL +
      cache.toVerboseString());

    TestCaseUtils.initializeTestBackend(false);
    String b = TestCaseUtils.getServerContext().getBackendConfigManager()
        .findLocalBackendForEntry(DN.valueOf("o=test")).getBackendID();

    cache.putEntry(testEntriesList.get(0), b, 1);

    assertNotNull(cache.getEntry(testEntriesList.get(0).getName()),
      "Expected to find " + testEntriesList.get(0).getName() +
      " in the cache.  Cache contents:" + ServerConstants.EOL +
      cache.toVerboseString());

    assertNotNull(cache.getEntry(b, 1),
      "Expected to find entry id " + -1 +
      " in the cache.  Cache contents:" + ServerConstants.EOL +
      cache.toVerboseString());

    // Clear the cache so that other tests can start from scratch.
    cache.clear();
  }



  /**
   * Tests the <CODE>putEntryIfAbsent</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  public void testPutEntryIfAbsent()
         throws Exception
  {
    assertNull(cache.toVerboseString(),
      "Expected empty cache.  " + "Cache contents:" + ServerConstants.EOL +
      cache.toVerboseString());

    TestCaseUtils.initializeTestBackend(false);
    String b = TestCaseUtils.getServerContext().getBackendConfigManager()
        .findLocalBackendForEntry(DN.valueOf("o=test")).getBackendID();

    assertTrue(cache.putEntryIfAbsent(testEntriesList.get(0), b, 1),
      "Not expected to find " + testEntriesList.get(0).getName() +
      " in the cache.  Cache contents:" + ServerConstants.EOL +
      cache.toVerboseString());

    assertFalse(cache.putEntryIfAbsent(testEntriesList.get(0), b, 1),
      "Expected to find " + testEntriesList.get(0).getName() +
      " in the cache.  Cache contents:" + ServerConstants.EOL +
      cache.toVerboseString());

    assertNotNull(cache.getEntry(testEntriesList.get(0).getName()),
      "Expected to find " + testEntriesList.get(0).getName() +
      " in the cache.  Cache contents:" + ServerConstants.EOL +
      cache.toVerboseString());

    assertNotNull(cache.getEntry(b, 1),
      "Expected to find entry id " + -1 +
      " in the cache.  Cache contents:" + ServerConstants.EOL +
      cache.toVerboseString());

    // Clear the cache so that other tests can start from scratch.
    cache.clear();
  }



  /**
   * Tests the <CODE>removeEntry</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  public void testRemoveEntry()
         throws Exception
  {
    assertNull(cache.toVerboseString(),
      "Expected empty cache.  " + "Cache contents:" + ServerConstants.EOL +
      cache.toVerboseString());

    TestCaseUtils.initializeTestBackend(false);
    String b = TestCaseUtils.getServerContext().getBackendConfigManager()
        .findLocalBackendForEntry(DN.valueOf("o=test")).getBackendID();

    cache.removeEntry(testEntriesList.get(0).getName());
    cache.putEntry(testEntriesList.get(0), b, 1);
    cache.removeEntry(testEntriesList.get(0).getName());

    assertNull(cache.getEntry(testEntriesList.get(0).getName()),
      "Not expected to find " + testEntriesList.get(0).getName() +
      " in the cache.  Cache contents:" + ServerConstants.EOL +
      cache.toVerboseString());

    assertNull(cache.getEntry(b, 1),
      "Not expected to find entry id " + -1 +
      " in the cache.  Cache contents:" + ServerConstants.EOL +
      cache.toVerboseString());

    // Clear the cache so that other tests can start from scratch.
    cache.clear();
  }



  /**
   * Tests the <CODE>clear</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  public void testClear()
         throws Exception
  {
    assertNull(cache.toVerboseString(),
      "Expected empty cache.  " + "Cache contents:" + ServerConstants.EOL +
      cache.toVerboseString());

    TestCaseUtils.initializeTestBackend(false);
    String b = TestCaseUtils.getServerContext().getBackendConfigManager()
        .findLocalBackendForEntry(DN.valueOf("o=test")).getBackendID();

    cache.clear();
    cache.putEntry(testEntriesList.get(0), b, 1);
    cache.clear();

    assertNull(cache.getEntry(testEntriesList.get(0).getName()),
      "Not expected to find " + testEntriesList.get(0).getName() +
      " in the cache.  Cache contents:" + ServerConstants.EOL +
      cache.toVerboseString());

    assertNull(cache.getEntry(b, 1),
      "Not expected to find entry id " + -1 +
      " in the cache.  Cache contents:" + ServerConstants.EOL +
      cache.toVerboseString());

    // Clear the cache so that other tests can start from scratch.
    cache.clear();
  }



  /**
   * Tests the <CODE>clearBackend</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  public void testClearBackend()
         throws Exception
  {
    assertNull(cache.toVerboseString(),
      "Expected empty cache.  " + "Cache contents:" + ServerConstants.EOL +
      cache.toVerboseString());

    TestCaseUtils.initializeTestBackend(false);
    String b = TestCaseUtils.getServerContext().getBackendConfigManager()
        .findLocalBackendForEntry(DN.valueOf("o=test")).getBackendID();
    String c = TestCaseUtils.getServerContext().getBackendConfigManager()
        .findLocalBackendForEntry(DN.valueOf("cn=config")).getBackendID();

    cache.clearBackend(b);
    cache.putEntry(testEntriesList.get(0), b, 1);
    cache.putEntry(testEntriesList.get(1), c, 1);
    cache.clearBackend(b);

    assertNull(cache.getEntry(b, 1),
      "Not expected to find entry id " + 1 + " on backend " +
      b + " in the cache.  Cache contents:" +
      ServerConstants.EOL + cache.toVerboseString());

    assertNull(cache.getEntry(testEntriesList.get(0).getName()),
      "Not expected to find " + testEntriesList.get(0).getName() +
      " in the cache.  Cache contents:" + ServerConstants.EOL +
      cache.toVerboseString());

    assertNotNull(cache.getEntry(c, 1),
      "Expected to find entry id " + 1 + " on backend " +
      c + " in the cache.  Cache contents:" +
      ServerConstants.EOL + cache.toVerboseString());

    // Clear the cache so that other tests can start from scratch.
    cache.clear();
  }

  /**
   * Tests the <CODE>handleLowMemory</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  public void testHandleLowMemory()
         throws Exception
  {
    assertNull(cache.toVerboseString(),
      "Expected empty cache.  " + "Cache contents:" + ServerConstants.EOL +
      cache.toVerboseString());

    cache.handleLowMemory();

    // Clear the cache so that other tests can start from scratch.
    cache.clear();
  }



  /**
   * Tests the entry cache concurrency/threadsafety by executing
   * core entry cache operations on several threads concurrently.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  public void testCacheConcurrency()
         throws Exception
  {
    String b = TestCaseUtils.getServerContext().getBackendConfigManager()
        .findLocalBackendForEntry(DN.valueOf("o=test")).getBackendID();

    for(int loops = 0; loops < CONCURRENCYLOOPS; loops++) {
      for(int i = 0; i < NUMTESTENTRIES; i++) {
        cache.putEntry(testEntriesList.get(i), b, i);
        cache.getEntry(testEntriesList.get(i).getName());
        cache.removeEntry(testEntriesList.get(i).getName());
        cache.putEntryIfAbsent(testEntriesList.get(i), b, i);
        cache.getEntry(b, i);
      }
    }
  }

  /**
   * Clear out references to save memory.
   */
  @AfterClass
  public void clearReferences() {
    cache = null;
    configuration = null;
  }
}
