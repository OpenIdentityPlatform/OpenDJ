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
 *      Copyright 2010 Sun Microsystems, Inc.
 */
package org.opends.server.api;



import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.opends.server.TestCaseUtils;
import org.testng.annotations.Test;

import org.opends.server.types.DN;
import org.testng.annotations.BeforeClass;

import static org.testng.Assert.*;

/**
 * A set of basic test cases for DITCacheMap class.
 */
public class DITCacheMapTestCase extends APITestCase
{
  private static final DITCacheMap<String> ditMap =
          new DITCacheMap<String>();

  private static final String dn0String =
          "cn=Object0,dc=example,dc=com";
  private static final String dn1String =
          "cn=Object1,ou=Objects,dc=example,dc=com";
  private static final String dn2String =
          "cn=Object2,ou=Objects,dc=example,dc=com";
  private static final String dn3String =
          "cn=Object3,ou=Objects,dc=example,dc=com";
  private static final String dn4String =
          "cn=Object4,ou=Classes,dc=example,dc=com";
  private static final String dn5String =
          "cn=Object5,ou=Classes,dc=example,dc=com";
  private static final String dn6String =
          "cn=Object6,ou=More,ou=Objects,dc=example,dc=com";
  private static final String dn7String =
          "cn=Object7,ou=More,ou=Objects,dc=example,dc=com";
  private static final String dn8String =
          "cn=Object8,ou=More,ou=Objects,dc=example,dc=com";
  private static final String dn9String =
          "cn=Object9,ou=No,ou=More,ou=Objects,dc=example,dc=com";

  private static DN dn0;
  private static DN dn1;
  private static DN dn2;
  private static DN dn3;
  private static DN dn4;
  private static DN dn5;
  private static DN dn6;
  private static DN dn7;
  private static DN dn8;
  private static DN dn9;

  private void putAllAndVerify()
  {
    Map<DN,String> hashMap =
          new HashMap<DN,String>();

    hashMap.put(dn0, dn0String);
    hashMap.put(dn1, dn1String);
    hashMap.put(dn2, dn2String);
    hashMap.put(dn3, dn3String);
    hashMap.put(dn4, dn4String);
    hashMap.put(dn5, dn5String);
    hashMap.put(dn6, dn6String);
    hashMap.put(dn7, dn7String);
    hashMap.put(dn8, dn8String);
    hashMap.put(dn9, dn9String);

    ditMap.putAll(hashMap);

    assertFalse(ditMap.isEmpty());
    assertEquals(ditMap.size(), 10);
    assertTrue(ditMap.containsKey(dn0));
    assertTrue(ditMap.containsKey(dn1));
    assertTrue(ditMap.containsKey(dn2));
    assertTrue(ditMap.containsKey(dn3));
    assertTrue(ditMap.containsKey(dn4));
    assertTrue(ditMap.containsKey(dn5));
    assertTrue(ditMap.containsKey(dn6));
    assertTrue(ditMap.containsKey(dn7));
    assertTrue(ditMap.containsKey(dn8));
    assertTrue(ditMap.containsKey(dn9));
    assertTrue(ditMap.containsValue(dn0String));
    assertTrue(ditMap.containsValue(dn1String));
    assertTrue(ditMap.containsValue(dn2String));
    assertTrue(ditMap.containsValue(dn3String));
    assertTrue(ditMap.containsValue(dn4String));
    assertTrue(ditMap.containsValue(dn5String));
    assertTrue(ditMap.containsValue(dn6String));
    assertTrue(ditMap.containsValue(dn7String));
    assertTrue(ditMap.containsValue(dn8String));
    assertTrue(ditMap.containsValue(dn9String));
  }

  private void clearTestMap()
  {
    ditMap.clear();
    assertTrue(ditMap.isEmpty());
    assertEquals(ditMap.size(), 0);
  }

  @BeforeClass()
  public void beforeClass()
         throws Exception
  {
    TestCaseUtils.startServer();

    dn0 = DN.decode(dn0String);
    dn1 = DN.decode(dn1String);
    dn2 = DN.decode(dn2String);
    dn3 = DN.decode(dn3String);
    dn4 = DN.decode(dn4String);
    dn5 = DN.decode(dn5String);
    dn6 = DN.decode(dn6String);
    dn7 = DN.decode(dn7String);
    dn8 = DN.decode(dn8String);
    dn9 = DN.decode(dn9String);
  }

  @Test()
  public void testDITCacheMapBasicOps()
         throws Exception
  {
    clearTestMap();

    ditMap.put(dn0, dn0String);
    ditMap.put(dn1, dn1String);
    ditMap.put(dn2, dn2String);
    ditMap.put(dn3, dn3String);
    ditMap.put(dn4, dn4String);
    ditMap.put(dn5, dn5String);
    ditMap.put(dn6, dn6String);
    ditMap.put(dn7, dn7String);
    ditMap.put(dn8, dn8String);
    ditMap.put(dn9, dn9String);

    assertFalse(ditMap.isEmpty());
    assertEquals(ditMap.size(), 10);

    assertTrue(ditMap.containsKey(dn0));
    assertTrue(ditMap.containsKey(dn1));
    assertTrue(ditMap.containsKey(dn2));
    assertTrue(ditMap.containsKey(dn3));
    assertTrue(ditMap.containsKey(dn4));
    assertTrue(ditMap.containsKey(dn5));
    assertTrue(ditMap.containsKey(dn6));
    assertTrue(ditMap.containsKey(dn7));
    assertTrue(ditMap.containsKey(dn8));
    assertTrue(ditMap.containsKey(dn9));

    assertFalse(ditMap.containsKey(DN.decode(
            "ou=No,ou=More,ou=Objects,dc=example,dc=com")));
    assertFalse(ditMap.containsKey(DN.decode(
            "ou=More,ou=Objects,dc=example,dc=com")));
    assertFalse(ditMap.containsKey(DN.decode(
            "ou=Objects,dc=example,dc=com")));
    assertFalse(ditMap.containsKey(DN.decode(
            "ou=Classes,dc=example,dc=com")));
    assertFalse(ditMap.containsKey(DN.decode(
            "dc=example,dc=com")));
    assertFalse(ditMap.containsKey(DN.decode(
            "dc=com")));

    assertTrue(ditMap.containsValue(dn0String));
    assertTrue(ditMap.containsValue(dn1String));
    assertTrue(ditMap.containsValue(dn2String));
    assertTrue(ditMap.containsValue(dn3String));
    assertTrue(ditMap.containsValue(dn4String));
    assertTrue(ditMap.containsValue(dn5String));
    assertTrue(ditMap.containsValue(dn6String));
    assertTrue(ditMap.containsValue(dn7String));
    assertTrue(ditMap.containsValue(dn8String));
    assertTrue(ditMap.containsValue(dn9String));

    assertEquals(ditMap.get(dn0), dn0String);
    assertEquals(ditMap.get(dn1), dn1String);
    assertEquals(ditMap.get(dn2), dn2String);
    assertEquals(ditMap.get(dn3), dn3String);
    assertEquals(ditMap.get(dn4), dn4String);
    assertEquals(ditMap.get(dn5), dn5String);
    assertEquals(ditMap.get(dn6), dn6String);
    assertEquals(ditMap.get(dn7), dn7String);
    assertEquals(ditMap.get(dn8), dn8String);
    assertEquals(ditMap.get(dn9), dn9String);

    assertNull(ditMap.get(DN.decode(
            "ou=No,ou=More,ou=Objects,dc=example,dc=com")));
    assertNull(ditMap.get(DN.decode(
            "ou=More,ou=Objects,dc=example,dc=com")));
    assertNull(ditMap.get(DN.decode(
            "ou=Objects,dc=example,dc=com")));
    assertNull(ditMap.get(DN.decode(
            "ou=Classes,dc=example,dc=com")));
    assertNull(ditMap.get(DN.decode(
            "dc=example,dc=com")));
    assertNull(ditMap.get(DN.decode(
            "dc=com")));
  }

  @Test()
  public void testDITCacheMapGetSubTree()
         throws Exception
  {
    clearTestMap();

    putAllAndVerify();

    Collection<String> subtreeSet = ditMap.getSubtree(
            DN.decode("dc=example,dc=com"));
    assertFalse(subtreeSet.isEmpty());
    assertEquals(subtreeSet.size(), 10);
    assertTrue(subtreeSet.contains(dn0String));
    assertTrue(subtreeSet.contains(dn1String));
    assertTrue(subtreeSet.contains(dn2String));
    assertTrue(subtreeSet.contains(dn3String));
    assertTrue(subtreeSet.contains(dn4String));
    assertTrue(subtreeSet.contains(dn5String));
    assertTrue(subtreeSet.contains(dn6String));
    assertTrue(subtreeSet.contains(dn7String));
    assertTrue(subtreeSet.contains(dn8String));
    assertTrue(subtreeSet.contains(dn9String));

    subtreeSet = ditMap.getSubtree(
            DN.decode("ou=Objects,dc=example,dc=com"));
    assertFalse(subtreeSet.isEmpty());
    assertEquals(subtreeSet.size(), 7);
    assertTrue(subtreeSet.contains(dn1String));
    assertTrue(subtreeSet.contains(dn2String));
    assertTrue(subtreeSet.contains(dn3String));
    assertTrue(subtreeSet.contains(dn6String));
    assertTrue(subtreeSet.contains(dn7String));
    assertTrue(subtreeSet.contains(dn8String));
    assertTrue(subtreeSet.contains(dn9String));


    subtreeSet = ditMap.getSubtree(
            DN.decode("ou=Classes,dc=example,dc=com"));
    assertFalse(subtreeSet.isEmpty());
    assertEquals(subtreeSet.size(), 2);
    assertTrue(subtreeSet.contains(dn4String));
    assertTrue(subtreeSet.contains(dn5String));

    subtreeSet = ditMap.getSubtree(
            DN.decode("ou=More,ou=Objects,dc=example,dc=com"));
    assertFalse(subtreeSet.isEmpty());
    assertEquals(subtreeSet.size(), 4);
    assertTrue(subtreeSet.contains(dn6String));
    assertTrue(subtreeSet.contains(dn7String));
    assertTrue(subtreeSet.contains(dn8String));
    assertTrue(subtreeSet.contains(dn9String));

    subtreeSet = ditMap.getSubtree(
            DN.decode("ou=No,ou=More,ou=Objects,dc=example,dc=com"));
    assertFalse(subtreeSet.isEmpty());
    assertEquals(subtreeSet.size(), 1);
    assertTrue(subtreeSet.contains(dn9String));

    subtreeSet = ditMap.getSubtree(dn0);
    assertFalse(subtreeSet.isEmpty());
    assertEquals(subtreeSet.size(), 1);
    assertTrue(subtreeSet.contains(dn0String));

    subtreeSet = ditMap.getSubtree(dn1);
    assertFalse(subtreeSet.isEmpty());
    assertEquals(subtreeSet.size(), 1);
    assertTrue(subtreeSet.contains(dn1String));

    subtreeSet = ditMap.getSubtree(dn2);
    assertFalse(subtreeSet.isEmpty());
    assertEquals(subtreeSet.size(), 1);
    assertTrue(subtreeSet.contains(dn2String));

    subtreeSet = ditMap.getSubtree(dn3);
    assertFalse(subtreeSet.isEmpty());
    assertEquals(subtreeSet.size(), 1);
    assertTrue(subtreeSet.contains(dn3String));
  }

  @Test()
  public void testDITCacheMapKeyAndEntrySet()
         throws Exception
  {
    clearTestMap();

    putAllAndVerify();

    Set<DN> dnSet = ditMap.keySet();
    assertFalse(dnSet.isEmpty());
    assertEquals(dnSet.size(), 10);
    assertTrue(dnSet.contains(dn0));
    assertTrue(dnSet.contains(dn1));
    assertTrue(dnSet.contains(dn2));
    assertTrue(dnSet.contains(dn3));
    assertTrue(dnSet.contains(dn4));
    assertTrue(dnSet.contains(dn5));
    assertTrue(dnSet.contains(dn6));
    assertTrue(dnSet.contains(dn7));
    assertTrue(dnSet.contains(dn8));
    assertTrue(dnSet.contains(dn9));

    Set<Entry<DN,String>> entrySet = ditMap.entrySet();
    assertFalse(entrySet.isEmpty());
    assertEquals(entrySet.size(), 10);
    Iterator<Entry<DN,String>> iterator = entrySet.iterator();
    Map<DN,String> tempMap = new HashMap<DN,String>();
    while (iterator.hasNext())
    {
      Entry<DN,String> entry = iterator.next();
      if ((entry.getKey().equals(dn0) &&
          entry.getValue().equals(dn0String)) ||
          (entry.getKey().equals(dn1) &&
          entry.getValue().equals(dn1String)) ||
          (entry.getKey().equals(dn2) &&
          entry.getValue().equals(dn2String)) ||
          (entry.getKey().equals(dn3) &&
          entry.getValue().equals(dn3String)) ||
          (entry.getKey().equals(dn4) &&
          entry.getValue().equals(dn4String)) ||
          (entry.getKey().equals(dn5) &&
          entry.getValue().equals(dn5String)) ||
          (entry.getKey().equals(dn6) &&
          entry.getValue().equals(dn6String)) ||
          (entry.getKey().equals(dn7) &&
          entry.getValue().equals(dn7String)) ||
          (entry.getKey().equals(dn8) &&
          entry.getValue().equals(dn8String)) ||
          (entry.getKey().equals(dn9) &&
          entry.getValue().equals(dn9String)))
      {
        assertFalse(tempMap.containsKey(entry.getKey()));
        assertFalse(tempMap.containsValue(entry.getValue()));
        assertNull(tempMap.put(entry.getKey(), entry.getValue()));
      }
      else
      {
        fail();
      }
      iterator.remove();
    }
    assertEquals(tempMap.size(), 10);
    assertEquals(ditMap.size(), 0);
    assertTrue(ditMap.isEmpty());
  }

  @Test()
  public void testDITCacheMapRemoveSubTree()
         throws Exception
  {
    clearTestMap();

    putAllAndVerify();

    Set<String> removeSet = new HashSet<String>();
    assertTrue(ditMap.removeSubtree(DN.decode(
            "dc=example,dc=com"),
            removeSet));
    assertFalse(removeSet.isEmpty());
    assertEquals(removeSet.size(), 10);
    assertTrue(removeSet.contains(dn0String));
    assertTrue(removeSet.contains(dn1String));
    assertTrue(removeSet.contains(dn2String));
    assertTrue(removeSet.contains(dn3String));
    assertTrue(removeSet.contains(dn4String));
    assertTrue(removeSet.contains(dn5String));
    assertTrue(removeSet.contains(dn6String));
    assertTrue(removeSet.contains(dn7String));
    assertTrue(removeSet.contains(dn8String));
    assertTrue(removeSet.contains(dn9String));
    assertTrue(ditMap.isEmpty());

    putAllAndVerify();

    removeSet.clear();
    assertTrue(ditMap.removeSubtree(DN.decode(
            "ou=Objects,dc=example,dc=com"),
            removeSet));
    assertFalse(removeSet.isEmpty());
    assertEquals(removeSet.size(), 7);
    assertTrue(removeSet.contains(dn1String));
    assertTrue(removeSet.contains(dn2String));
    assertTrue(removeSet.contains(dn3String));
    assertTrue(removeSet.contains(dn6String));
    assertTrue(removeSet.contains(dn7String));
    assertTrue(removeSet.contains(dn8String));
    assertTrue(removeSet.contains(dn9String));
    assertEquals(ditMap.size(), 3);
    assertTrue(ditMap.containsKey(dn0));
    assertTrue(ditMap.containsKey(dn4));
    assertTrue(ditMap.containsKey(dn5));

    clearTestMap();

    putAllAndVerify();

    removeSet.clear();
    assertTrue(ditMap.removeSubtree(DN.decode(
            "ou=Classes,dc=example,dc=com"),
            removeSet));
    assertFalse(removeSet.isEmpty());
    assertEquals(removeSet.size(), 2);
    assertTrue(removeSet.contains(dn4String));
    assertTrue(removeSet.contains(dn5String));
    assertEquals(ditMap.size(), 8);
    assertTrue(ditMap.containsKey(dn0));
    assertTrue(ditMap.containsKey(dn1));
    assertTrue(ditMap.containsKey(dn2));
    assertTrue(ditMap.containsKey(dn3));
    assertTrue(ditMap.containsKey(dn6));
    assertTrue(ditMap.containsKey(dn7));
    assertTrue(ditMap.containsKey(dn8));
    assertTrue(ditMap.containsKey(dn9));

    clearTestMap();

    putAllAndVerify();

    removeSet.clear();
    assertTrue(ditMap.removeSubtree(DN.decode(
            "ou=More,ou=Objects,dc=example,dc=com"),
            removeSet));
    assertFalse(removeSet.isEmpty());
    assertEquals(removeSet.size(), 4);
    assertTrue(removeSet.contains(dn6String));
    assertTrue(removeSet.contains(dn7String));
    assertTrue(removeSet.contains(dn8String));
    assertTrue(removeSet.contains(dn9String));
    assertEquals(ditMap.size(), 6);
    assertTrue(ditMap.containsKey(dn0));
    assertTrue(ditMap.containsKey(dn1));
    assertTrue(ditMap.containsKey(dn2));
    assertTrue(ditMap.containsKey(dn3));
    assertTrue(ditMap.containsKey(dn4));
    assertTrue(ditMap.containsKey(dn5));

    clearTestMap();

    putAllAndVerify();

    removeSet.clear();
    assertTrue(ditMap.removeSubtree(DN.decode(
            "ou=No,ou=More,ou=Objects,dc=example,dc=com"),
            removeSet));
    assertFalse(removeSet.isEmpty());
    assertEquals(removeSet.size(), 1);
    assertTrue(removeSet.contains(dn9String));
    assertEquals(ditMap.size(), 9);
    assertTrue(ditMap.containsKey(dn0));
    assertTrue(ditMap.containsKey(dn1));
    assertTrue(ditMap.containsKey(dn2));
    assertTrue(ditMap.containsKey(dn3));
    assertTrue(ditMap.containsKey(dn4));
    assertTrue(ditMap.containsKey(dn5));
    assertTrue(ditMap.containsKey(dn6));
    assertTrue(ditMap.containsKey(dn7));
    assertTrue(ditMap.containsKey(dn8));

    clearTestMap();

    putAllAndVerify();

    removeSet.clear();
    assertTrue(ditMap.removeSubtree(dn0,
            removeSet));
    assertFalse(removeSet.isEmpty());
    assertEquals(removeSet.size(), 1);
    assertTrue(removeSet.contains(dn0String));
    assertEquals(ditMap.size(), 9);
    assertTrue(ditMap.containsKey(dn1));
    assertTrue(ditMap.containsKey(dn2));
    assertTrue(ditMap.containsKey(dn3));
    assertTrue(ditMap.containsKey(dn4));
    assertTrue(ditMap.containsKey(dn5));
    assertTrue(ditMap.containsKey(dn6));
    assertTrue(ditMap.containsKey(dn7));
    assertTrue(ditMap.containsKey(dn8));
    assertTrue(ditMap.containsKey(dn9));

    clearTestMap();

    putAllAndVerify();

    removeSet.clear();
    assertTrue(ditMap.removeSubtree(dn1,
            removeSet));
    assertFalse(removeSet.isEmpty());
    assertEquals(removeSet.size(), 1);
    assertTrue(removeSet.contains(dn1String));
    assertEquals(ditMap.size(), 9);
    assertTrue(ditMap.containsKey(dn0));
    assertTrue(ditMap.containsKey(dn2));
    assertTrue(ditMap.containsKey(dn3));
    assertTrue(ditMap.containsKey(dn4));
    assertTrue(ditMap.containsKey(dn5));
    assertTrue(ditMap.containsKey(dn6));
    assertTrue(ditMap.containsKey(dn7));
    assertTrue(ditMap.containsKey(dn8));
    assertTrue(ditMap.containsKey(dn9));

    clearTestMap();

    putAllAndVerify();

    removeSet.clear();
    assertTrue(ditMap.removeSubtree(dn2,
            removeSet));
    assertFalse(removeSet.isEmpty());
    assertEquals(removeSet.size(), 1);
    assertTrue(removeSet.contains(dn2String));
    assertEquals(ditMap.size(), 9);
    assertTrue(ditMap.containsKey(dn0));
    assertTrue(ditMap.containsKey(dn1));
    assertTrue(ditMap.containsKey(dn3));
    assertTrue(ditMap.containsKey(dn4));
    assertTrue(ditMap.containsKey(dn5));
    assertTrue(ditMap.containsKey(dn6));
    assertTrue(ditMap.containsKey(dn7));
    assertTrue(ditMap.containsKey(dn8));
    assertTrue(ditMap.containsKey(dn9));

    clearTestMap();

    putAllAndVerify();

    removeSet.clear();
    assertTrue(ditMap.removeSubtree(dn3,
            removeSet));
    assertFalse(removeSet.isEmpty());
    assertEquals(removeSet.size(), 1);
    assertTrue(removeSet.contains(dn3String));
    assertEquals(ditMap.size(), 9);
    assertTrue(ditMap.containsKey(dn0));
    assertTrue(ditMap.containsKey(dn1));
    assertTrue(ditMap.containsKey(dn2));
    assertTrue(ditMap.containsKey(dn4));
    assertTrue(ditMap.containsKey(dn5));
    assertTrue(ditMap.containsKey(dn6));
    assertTrue(ditMap.containsKey(dn7));
    assertTrue(ditMap.containsKey(dn8));
    assertTrue(ditMap.containsKey(dn9));
  }
}
