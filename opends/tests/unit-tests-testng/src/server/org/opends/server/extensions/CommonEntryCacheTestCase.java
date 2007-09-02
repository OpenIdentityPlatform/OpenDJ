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



import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.concurrent.locks.Lock;

import org.opends.server.TestCaseUtils;
import org.opends.server.api.Backend;
import org.opends.server.api.EntryCache;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.LockType;
import org.opends.server.admin.std.server.EntryCacheCfg;
import org.opends.server.util.ServerConstants;

import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;


/**
 * A common set of test cases for all entry cache implementations.
 */
public abstract class CommonEntryCacheTestCase
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
  protected EntryCache cache;



  /**
   * Entry cache configuration instance.
   */
  protected EntryCacheCfg configuration;



  /**
   * Reflection of the toVerboseString implementation method.
   */
  protected String toVerboseString()
            throws Exception
  {
    final Method[] cacheMethods =
        cache.getClass().getDeclaredMethods();

    for (int i = 0; i < cacheMethods.length; ++i) {
      if (cacheMethods[i].getName().equals("toVerboseString")) {
        cacheMethods[i].setAccessible(true);
        Object verboseString =
          cacheMethods[i].invoke(cache, (Object[]) null);
        return (String) verboseString;
      }
    }

    return null;
  }



  /**
   * Tests the <CODE>containsEntry</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  public void testContainsEntry()
         throws Exception
  {
    assertNull(toVerboseString(),
      "Expected empty cache.  " + "Cache contents:" + ServerConstants.EOL +
      toVerboseString());

    TestCaseUtils.initializeTestBackend(false);
    Backend b = DirectoryServer.getBackend(DN.decode("o=test"));

    assertFalse(cache.containsEntry(testEntriesList.get(0).getDN()),
      "Not expected to find " + testEntriesList.get(0).getDN().toString() +
      " in the cache.  Cache contents:" + ServerConstants.EOL +
      toVerboseString());

    cache.putEntry(testEntriesList.get(0), b, 1);

    assertTrue(cache.containsEntry(testEntriesList.get(0).getDN()),
      "Expected to find " + testEntriesList.get(0).getDN().toString() +
      " in the cache.  Cache contents:" + ServerConstants.EOL +
      toVerboseString());

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
    assertNull(toVerboseString(),
      "Expected empty cache.  " + "Cache contents:" + ServerConstants.EOL +
      toVerboseString());

    TestCaseUtils.initializeTestBackend(false);
    Backend b = DirectoryServer.getBackend(DN.decode("o=test"));

    assertNull(cache.getEntry(testEntriesList.get(0).getDN()),
      "Not expected to find " + testEntriesList.get(0).getDN().toString() +
      " in the cache.  Cache contents:" + ServerConstants.EOL +
      toVerboseString());

    cache.putEntry(testEntriesList.get(0), b, 1);

    assertNotNull(cache.getEntry(testEntriesList.get(0).getDN()),
      "Expected to find " + testEntriesList.get(0).getDN().toString() +
      " in the cache.  Cache contents:" + ServerConstants.EOL +
      toVerboseString());

    // Clear the cache so that other tests can start from scratch.
    cache.clear();
  }



  /**
   * Tests the second <CODE>getEntry</CODE> method, which takes a DN, lock type,
   * and list attributes.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @SuppressWarnings("unchecked")
  public void testGetEntry2()
         throws Exception
  {
    assertNull(toVerboseString(),
      "Expected empty cache.  " + "Cache contents:" + ServerConstants.EOL +
      toVerboseString());

    TestCaseUtils.initializeTestBackend(false);
    Backend b = DirectoryServer.getBackend(DN.decode("o=test"));

    assertNull(cache.getEntry(testEntriesList.get(0).getDN(), LockType.NONE,
      new ArrayList<Lock>()),
      "Not expected to find " + testEntriesList.get(0).getDN().toString() +
      " in the cache.  Cache contents:" + ServerConstants.EOL +
      toVerboseString());

    cache.putEntry(testEntriesList.get(0), b, 1);

    assertNotNull(cache.getEntry(testEntriesList.get(0).getDN(), LockType.NONE,
      new ArrayList<Lock>()),
      "Expected to find " + testEntriesList.get(0).getDN().toString() +
      " in the cache.  Cache contents:" + ServerConstants.EOL +
      toVerboseString());

    // Clear the cache so that other tests can start from scratch.
    cache.clear();
  }



  /**
   * Tests the third <CODE>getEntry</CODE> method, which takes a backend, entry
   * ID, lock type, and list attributes.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @SuppressWarnings("unchecked")
  public void testGetEntry3()
         throws Exception
  {
    assertNull(toVerboseString(),
      "Expected empty cache.  " + "Cache contents:" + ServerConstants.EOL +
      toVerboseString());

    TestCaseUtils.initializeTestBackend(false);
    Backend b = DirectoryServer.getBackend(DN.decode("o=test"));

    assertNull(cache.getEntry(b, -1, LockType.NONE, new ArrayList<Lock>()),
      "Not expected to find entry id " + Integer.toString(-1) +
      " in the cache.  Cache contents:" + ServerConstants.EOL +
      toVerboseString());

    cache.putEntry(testEntriesList.get(0), b, 1);

    assertNotNull(cache.getEntry(b, 1, LockType.NONE, new ArrayList<Lock>()),
      "Expected to find entry id " + Integer.toString(1) +
      " in the cache.  Cache contents:" + ServerConstants.EOL +
      toVerboseString());

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
    assertNull(toVerboseString(),
      "Expected empty cache.  " + "Cache contents:" + ServerConstants.EOL +
      toVerboseString());

    TestCaseUtils.initializeTestBackend(false);
    Backend b = DirectoryServer.getBackend(DN.decode("o=test"));

    assertEquals(cache.getEntryID(testEntriesList.get(0).getDN()), -1,
      "Not expected to find " + testEntriesList.get(0).getDN().toString() +
      " in the cache.  Cache contents:" + ServerConstants.EOL +
      toVerboseString());

    cache.putEntry(testEntriesList.get(0), b, 1);

    assertEquals(cache.getEntryID(testEntriesList.get(0).getDN()), 1,
      "Expected to find " + testEntriesList.get(0).getDN().toString() +
      " in the cache.  Cache contents:" + ServerConstants.EOL +
      toVerboseString());

    // Clear the cache so that other tests can start from scratch.
    cache.clear();
  }



  /**
   * Tests the <CODE>putEntry</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @SuppressWarnings("unchecked")
  public void testPutEntry()
         throws Exception
  {
    assertNull(toVerboseString(),
      "Expected empty cache.  " + "Cache contents:" + ServerConstants.EOL +
      toVerboseString());

    TestCaseUtils.initializeTestBackend(false);
    Backend b = DirectoryServer.getBackend(DN.decode("o=test"));

    cache.putEntry(testEntriesList.get(0), b, 1);

    assertNotNull(cache.getEntry(testEntriesList.get(0).getDN()),
      "Expected to find " + testEntriesList.get(0).getDN().toString() +
      " in the cache.  Cache contents:" + ServerConstants.EOL +
      toVerboseString());

    assertNotNull(cache.getEntry(b, 1, LockType.NONE, new ArrayList<Lock>()),
      "Expected to find entry id " + Integer.toString(-1) +
      " in the cache.  Cache contents:" + ServerConstants.EOL +
      toVerboseString());

    // Clear the cache so that other tests can start from scratch.
    cache.clear();
  }



  /**
   * Tests the <CODE>putEntryIfAbsent</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @SuppressWarnings("unchecked")
  public void testPutEntryIfAbsent()
         throws Exception
  {
    assertNull(toVerboseString(),
      "Expected empty cache.  " + "Cache contents:" + ServerConstants.EOL +
      toVerboseString());

    TestCaseUtils.initializeTestBackend(false);
    Backend b = DirectoryServer.getBackend(DN.decode("o=test"));

    assertTrue(cache.putEntryIfAbsent(testEntriesList.get(0), b, 1),
      "Not expected to find " + testEntriesList.get(0).getDN().toString() +
      " in the cache.  Cache contents:" + ServerConstants.EOL +
      toVerboseString());

    assertFalse(cache.putEntryIfAbsent(testEntriesList.get(0), b, 1),
      "Expected to find " + testEntriesList.get(0).getDN().toString() +
      " in the cache.  Cache contents:" + ServerConstants.EOL +
      toVerboseString());

    assertNotNull(cache.getEntry(testEntriesList.get(0).getDN()),
      "Expected to find " + testEntriesList.get(0).getDN().toString() +
      " in the cache.  Cache contents:" + ServerConstants.EOL +
      toVerboseString());

    assertNotNull(cache.getEntry(b, 1, LockType.NONE, new ArrayList<Lock>()),
      "Expected to find entry id " + Integer.toString(-1) +
      " in the cache.  Cache contents:" + ServerConstants.EOL +
      toVerboseString());

    // Clear the cache so that other tests can start from scratch.
    cache.clear();
  }



  /**
   * Tests the <CODE>removeEntry</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @SuppressWarnings("unchecked")
  public void testRemoveEntry()
         throws Exception
  {
    assertNull(toVerboseString(),
      "Expected empty cache.  " + "Cache contents:" + ServerConstants.EOL +
      toVerboseString());

    TestCaseUtils.initializeTestBackend(false);
    Backend b = DirectoryServer.getBackend(DN.decode("o=test"));

    cache.removeEntry(testEntriesList.get(0).getDN());
    cache.putEntry(testEntriesList.get(0), b, 1);
    cache.removeEntry(testEntriesList.get(0).getDN());

    assertNull(cache.getEntry(testEntriesList.get(0).getDN()),
      "Not expected to find " + testEntriesList.get(0).getDN().toString() +
      " in the cache.  Cache contents:" + ServerConstants.EOL +
      toVerboseString());

    assertNull(cache.getEntry(b, 1, LockType.NONE, new ArrayList<Lock>()),
      "Not expected to find entry id " + Integer.toString(-1) +
      " in the cache.  Cache contents:" + ServerConstants.EOL +
      toVerboseString());

    // Clear the cache so that other tests can start from scratch.
    cache.clear();
  }



  /**
   * Tests the <CODE>clear</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @SuppressWarnings("unchecked")
  public void testClear()
         throws Exception
  {
    assertNull(toVerboseString(),
      "Expected empty cache.  " + "Cache contents:" + ServerConstants.EOL +
      toVerboseString());

    TestCaseUtils.initializeTestBackend(false);
    Backend b = DirectoryServer.getBackend(DN.decode("o=test"));

    cache.clear();
    cache.putEntry(testEntriesList.get(0), b, 1);
    cache.clear();

    assertNull(cache.getEntry(testEntriesList.get(0).getDN()),
      "Not expected to find " + testEntriesList.get(0).getDN().toString() +
      " in the cache.  Cache contents:" + ServerConstants.EOL +
      toVerboseString());

    assertNull(cache.getEntry(b, 1, LockType.NONE, new ArrayList<Lock>()),
      "Not expected to find entry id " + Integer.toString(-1) +
      " in the cache.  Cache contents:" + ServerConstants.EOL +
      toVerboseString());

    // Clear the cache so that other tests can start from scratch.
    cache.clear();
  }



  /**
   * Tests the <CODE>clearBackend</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @SuppressWarnings("unchecked")
  public void testClearBackend()
         throws Exception
  {
    assertNull(toVerboseString(),
      "Expected empty cache.  " + "Cache contents:" + ServerConstants.EOL +
      toVerboseString());

    TestCaseUtils.initializeTestBackend(false);
    Backend b = DirectoryServer.getBackend(DN.decode("o=test"));
    Backend c = DirectoryServer.getBackend(DN.decode("cn=config"));

    cache.clearBackend(b);
    cache.putEntry(testEntriesList.get(0), b, 1);
    cache.putEntry(testEntriesList.get(1), c, 1);
    cache.clearBackend(b);

    assertNull(cache.getEntry(b, 1, LockType.NONE, new ArrayList<Lock>()),
      "Not expected to find entry id " + Integer.toString(1) + " on backend " +
      b.getBackendID() + " in the cache.  Cache contents:" +
      ServerConstants.EOL + toVerboseString());

    assertNull(cache.getEntry(testEntriesList.get(0).getDN()),
      "Not expected to find " + testEntriesList.get(0).getDN().toString() +
      " in the cache.  Cache contents:" + ServerConstants.EOL +
      toVerboseString());

    assertNotNull(cache.getEntry(c, 1, LockType.NONE, new ArrayList<Lock>()),
      "Expected to find entry id " + Integer.toString(1) + " on backend " +
      c.getBackendID() + " in the cache.  Cache contents:" +
      ServerConstants.EOL + toVerboseString());

    // Clear the cache so that other tests can start from scratch.
    cache.clear();
  }



  /**
   * Tests the <CODE>clearSubtree</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @SuppressWarnings("unchecked")
  public void testClearSubtree()
         throws Exception
  {
    assertNull(toVerboseString(),
      "Expected empty cache.  " + "Cache contents:" + ServerConstants.EOL +
      toVerboseString());

    TestCaseUtils.initializeTestBackend(false);
    Backend b = DirectoryServer.getBackend(DN.decode("o=test"));
    Backend c = DirectoryServer.getBackend(DN.decode("cn=config"));

    cache.putEntry(testEntriesList.get(0), b, 1);
    Entry testEntry = testEntriesList.get(1);
    testEntry.setDN(testEntry.getDN().decode(
      testEntry.getDN().getRDN() + ",cn=config"));
    cache.putEntry(testEntry, c, 1);
    cache.clearSubtree(DN.decode("o=test"));

    assertNull(cache.getEntry(testEntriesList.get(0).getDN()),
      "Not expected to find " + testEntriesList.get(0).getDN().toString() +
      " in the cache.  Cache contents:" + ServerConstants.EOL +
      toVerboseString());

    assertNull(cache.getEntry(b, 1, LockType.NONE, new ArrayList<Lock>()),
      "Not expected to find entry id " + Integer.toString(-1) +
      " in the cache.  Cache contents:" + ServerConstants.EOL +
      toVerboseString());

    assertNotNull(cache.getEntry(testEntry.getDN()),
      "Expected to find " + testEntry.getDN().toString() +
      " in the cache.  Cache contents:" + ServerConstants.EOL +
      toVerboseString());

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
    assertNull(toVerboseString(),
      "Expected empty cache.  " + "Cache contents:" + ServerConstants.EOL +
      toVerboseString());

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
  @SuppressWarnings("unchecked")
  public void testCacheConcurrency()
         throws Exception
  {
    Backend b = DirectoryServer.getBackend(DN.decode("o=test"));

    for(int loops = 0; loops < CONCURRENCYLOOPS; loops++) {
      for(int i = 0; i < NUMTESTENTRIES; i++) {
        cache.putEntry(testEntriesList.get(i), b, i);
        cache.getEntry(testEntriesList.get(i).getDN());
        cache.removeEntry(testEntriesList.get(i).getDN());
        cache.putEntryIfAbsent(testEntriesList.get(i), b, i);
        cache.getEntry(b, i, LockType.NONE, new ArrayList<Lock>());
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
