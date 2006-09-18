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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import java.util.ArrayList;
import java.util.concurrent.locks.Lock;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.api.Backend;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.LockType;

import static org.testng.Assert.*;



/**
 * A set of test cases for the default entry cache.
 */
public class DefaultEntryCacheTestCase
       extends ExtensionsTestCase
{
  /**
   * Ensures that the Directory Server is running.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass()
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();
  }



  /**
   * Tests the process of creating, initializing, and finalizing the cache.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testInitializeAndFinalizeCache()
         throws Exception
  {
    DefaultEntryCache cache = new DefaultEntryCache();
    cache.initializeEntryCache(null);
    cache.finalizeEntryCache();
  }



  /**
   * Tests the <CODE>containsEntry</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testContainsEntry()
         throws Exception
  {
    DefaultEntryCache cache = new DefaultEntryCache();
    cache.initializeEntryCache(null);

    assertFalse(cache.containsEntry(DN.decode("uid=test,o=test")));

    cache.finalizeEntryCache();
  }



  /**
   * Tests the first <CODE>getEntry</CODE> method, which takes a single DN
   * argument.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetEntry1()
         throws Exception
  {
    DefaultEntryCache cache = new DefaultEntryCache();
    cache.initializeEntryCache(null);

    assertNull(cache.getEntry(DN.decode("uid=test,o=test")));

    cache.finalizeEntryCache();
  }



  /**
   * Tests the second <CODE>getEntry</CODE> method, which takes a DN, lock type,
   * and list attributes.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetEntry2()
         throws Exception
  {
    DefaultEntryCache cache = new DefaultEntryCache();
    cache.initializeEntryCache(null);

    assertNull(cache.getEntry(DN.decode("uid=test,o=test"), LockType.NONE,
                              new ArrayList<Lock>()));

    cache.finalizeEntryCache();
  }



  /**
   * Tests the third <CODE>getEntry</CODE> method, which takes a backend, entry
   * ID, lock type, and list attributes.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetEntry3()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(false);
    Backend b = DirectoryServer.getBackend(DN.decode("o=test"));

    DefaultEntryCache cache = new DefaultEntryCache();
    cache.initializeEntryCache(null);

    assertNull(cache.getEntry(b, -1, LockType.NONE, new ArrayList<Lock>()));

    cache.finalizeEntryCache();
  }



  /**
   * Tests the <CODE>getEntryID</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetEntryID()
         throws Exception
  {
    DefaultEntryCache cache = new DefaultEntryCache();
    cache.initializeEntryCache(null);

    assertEquals(cache.getEntryID(DN.decode("uid=test,o=test")), -1);

    cache.finalizeEntryCache();
  }



  /**
   * Tests the <CODE>putEntry</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testPutEntry()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(false);
    Backend b = DirectoryServer.getBackend(DN.decode("o=test"));

    Entry e = TestCaseUtils.makeEntry("dn: o=test",
                                      "objectClass: top",
                                      "objectClass: organization",
                                      "o: test");

    DefaultEntryCache cache = new DefaultEntryCache();
    cache.initializeEntryCache(null);

    cache.putEntry(e, b, 1);

    cache.finalizeEntryCache();
  }



  /**
   * Tests the <CODE>putEntryIfAbsent</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testPutEntryIfAbsent()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(false);
    Backend b = DirectoryServer.getBackend(DN.decode("o=test"));

    Entry e = TestCaseUtils.makeEntry("dn: o=test",
                                      "objectClass: top",
                                      "objectClass: organization",
                                      "o: test");

    DefaultEntryCache cache = new DefaultEntryCache();
    cache.initializeEntryCache(null);

    assertTrue(cache.putEntryIfAbsent(e, b, 1));

    cache.finalizeEntryCache();
  }



  /**
   * Tests the <CODE>removeEntry</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRemoveEntry()
         throws Exception
  {
    DefaultEntryCache cache = new DefaultEntryCache();
    cache.initializeEntryCache(null);

    cache.removeEntry(DN.decode("uid=test,o=test"));

    cache.finalizeEntryCache();
  }



  /**
   * Tests the <CODE>clear</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testClear()
         throws Exception
  {
    DefaultEntryCache cache = new DefaultEntryCache();
    cache.initializeEntryCache(null);

    cache.clear();

    cache.finalizeEntryCache();
  }



  /**
   * Tests the <CODE>clearBackend</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testClearBackend()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(false);
    Backend b = DirectoryServer.getBackend(DN.decode("o=test"));

    DefaultEntryCache cache = new DefaultEntryCache();
    cache.initializeEntryCache(null);

    cache.clearBackend(b);

    cache.finalizeEntryCache();
  }



  /**
   * Tests the <CODE>clearSubtree</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testClearSubtree()
         throws Exception
  {
    DefaultEntryCache cache = new DefaultEntryCache();
    cache.initializeEntryCache(null);

    cache.clearSubtree(DN.decode("o=test"));

    cache.finalizeEntryCache();
  }



  /**
   * Tests the <CODE>handleLowMemory</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void handleLowMemory()
         throws Exception
  {
    DefaultEntryCache cache = new DefaultEntryCache();
    cache.initializeEntryCache(null);

    cache.handleLowMemory();

    cache.finalizeEntryCache();
  }
}

