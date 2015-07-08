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
 *      Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.types;

import java.util.*;
import java.util.Map.Entry;

import org.opends.server.DirectoryServerTestCase;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.*;
import static org.testng.Assert.*;

@SuppressWarnings("javadoc")
public class SmallMapTest extends DirectoryServerTestCase
{

  @Test
  public void testPutAndSize() throws Exception
  {
    SmallMap<Integer, String> map = new SmallMap<>();
    assertEquals(map.size(), 0);
    assertEquals(map.put(1, "one"), null);
    assertEquals(map.size(), 1);
    assertEquals(map.put(1, "ONE"), "one");
    assertEquals(map.size(), 1);
    assertEquals(map.put(2, "two"), null);
    assertEquals(map.size(), 2);
    assertEquals(map.put(3, "three"), null);
    assertEquals(map.size(), 3);
  }

  @Test(dependsOnMethods = { "testPutAndSize" })
  public void testGet() throws Exception
  {
    SmallMap<Integer, String> map = new SmallMap<>();
    assertEquals(map.get(1), null);
    assertEquals(map.get(2), null);
    map.put(1, "one");
    assertEquals(map.get(1), "one");
    assertEquals(map.get(2), null);
    map.put(1, "ONE");
    assertEquals(map.get(1), "ONE");
    assertEquals(map.get(2), null);
    map.put(2, "two");
    assertEquals(map.get(1), "ONE");
    assertEquals(map.get(2), "two");
  }

  @Test(dependsOnMethods = { "testPutAndSize" })
  public void testPutAll() throws Exception
  {
    final SmallMap<Integer, String> map = new SmallMap<>();
    final HashMap<Integer, String> hashMap = new HashMap<>();
    map.putAll(hashMap);
    assertEquals(map.size(), 0);
    hashMap.put(1, "one");
    map.putAll(hashMap);
    assertEquals(map.size(), 1);
    assertEquals(map.get(1), "one");
  }

  @Test(dependsOnMethods = { "testPutAndSize" })
  public void testRemove() throws Exception
  {
    SmallMap<Integer, String> map = new SmallMap<>();
    assertEquals(map.size(), 0);
    assertEquals(map.remove(2), null);
    assertEquals(map.remove(1), null);

    map.put(1, "one");
    assertEquals(map.size(), 1);
    assertEquals(map.remove(2), null);
    assertEquals(map.remove(1), "one");
    assertEquals(map.size(), 0);

    map.put(1, "one");
    map.put(2, "two");
    assertEquals(map.size(), 2);
    assertEquals(map.remove(1), "one");
    assertEquals(map.size(), 1);
  }

  @Test(dependsOnMethods = { "testPutAndSize" })
  public void testContains() throws Exception
  {
    SmallMap<Integer, String> map = new SmallMap<>();
    assertDoesNotContain(map, entry(2, "two"));

    map.put(1, null);
    assertContains(map, entry(1, (String) null));
    assertDoesNotContain(map, entry(2, "two"));

    map.put(1, "one");
    assertContains(map, entry(1, "one"));
    assertDoesNotContain(map, entry(2, "two"));

    map.put(2, "two");
    assertContains(map, entry(1, "one"));
    assertContains(map, entry(2, "two"));
    assertDoesNotContain(map, entry(3, "three"));
  }

  @Test(dependsOnMethods = { "testPutAndSize" })
  public void testClear() throws Exception
  {
    SmallMap<Integer, String> map = new SmallMap<>();
    map.clear();
    assertEquals(map.size(), 0);

    map.put(1, "one");
    map.clear();
    assertEquals(map.size(), 0);

    map.put(1, "one");
    map.put(2, "two");
    map.clear();
    assertEquals(map.size(), 0);
  }

  @Test(dependsOnMethods = { "testPutAndSize" })
  public void testEntrySetSize() throws Exception
  {
    SmallMap<Integer, String> map = new SmallMap<>();
    assertEquals(map.entrySet().size(), 0);

    map.put(1, "one");
    assertEquals(map.entrySet().size(), 1);

    map.put(1, "one");
    map.put(2, "two");
    assertEquals(map.entrySet().size(), 2);
  }

  @SuppressWarnings("unchecked")
  @Test(dependsOnMethods = { "testEntrySetSize" })
  public void testEntrySetIterator() throws Exception
  {
    SmallMap<Integer, String> map = new SmallMap<>();
    assertThat(map.entrySet().iterator()).isEmpty();

    map.put(1, "one");
    assertThat(map.entrySet().iterator()).containsExactly(
        entry(1, "one"));

    map.put(1, "one");
    map.put(2, "two");
    assertThat(map.entrySet().iterator()).containsExactly(
        entry(1, "one"), entry(2, "two"));
  }

  @Test(dependsOnMethods = { "testEntrySetIterator" })
  public void testEntrySetIteratorNextRemove() throws Exception
  {
    SmallMap<Integer, String> map = new SmallMap<>();
    map.put(1, "one");
    Iterator<Entry<Integer, String>> iter = map.entrySet().iterator();
    assertTrue(iter.hasNext());
    assertNotNull(iter.next());
    iter.remove();
    assertFalse(iter.hasNext());

    assertTrue(map.isEmpty());
  }

  @Test(dependsOnMethods = { "testEntrySetIterator" },
      expectedExceptions = { NoSuchElementException.class })
  public void testEntrySetIteratorNextThrowsNoSuchElementException() throws Exception
  {
    SmallMap<Integer, String> map = new SmallMap<>();
    map.put(1, "one");
    Iterator<Entry<Integer, String>> iter = map.entrySet().iterator();
    assertTrue(iter.hasNext());
    iter.next();
    assertFalse(iter.hasNext());
    iter.next(); // throw an exception
  }

  private <K, V> Entry<K, V> entry(K key, V value)
  {
    return new AbstractMap.SimpleImmutableEntry<>(key, value);
  }

  private void assertContains(SmallMap<Integer, String> map,
      Entry<Integer, String> entry)
  {
    assertContains(map, entry, true);
  }

  private void assertDoesNotContain(SmallMap<Integer, String> map,
      Entry<Integer, String> entry)
  {
    assertContains(map, entry, false);
  }

  private void assertContains(SmallMap<Integer, String> map,
      Entry<Integer, String> entry, boolean expected)
  {
    assertEquals(map.containsKey(entry.getKey()), expected);
    assertEquals(map.containsValue(entry.getValue()), expected);
  }

  @Test(expectedExceptions = { NullPointerException.class })
  public void testGetRejectsNull() throws Exception
  {
    new SmallMap<Integer, String>().get(null);
  }

  @Test(expectedExceptions = { NullPointerException.class })
  public void testContainsKeyRejectsNull() throws Exception
  {
    new SmallMap<Integer, String>().containsKey(null);
  }

  @Test(expectedExceptions = { NullPointerException.class })
  public void testPutRejectsNull() throws Exception
  {
    new SmallMap<Integer, String>().put(null, null);
  }

  @Test(expectedExceptions = { NullPointerException.class })
  public void testPutAllRejectsNull() throws Exception
  {
    final HashMap<Integer, String> map = new HashMap<>();
    map.put(null, null);
    new SmallMap<Integer, String>().putAll(map);
  }
}
