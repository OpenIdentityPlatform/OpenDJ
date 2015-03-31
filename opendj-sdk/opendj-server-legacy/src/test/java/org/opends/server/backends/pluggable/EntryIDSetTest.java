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
 *
 *      Copyright 2015 ForgeRock AS
 */
package org.opends.server.backends.pluggable;

import static org.assertj.core.api.Assertions.*;
import static org.opends.server.backends.pluggable.EntryIDSet.*;
import static org.opends.server.backends.pluggable.Utils.*;

import java.util.Arrays;

import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.DirectoryServerTestCase;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
@Test(groups = { "precommit", "pluggablebackend" }, sequential=true)
public class EntryIDSetTest extends DirectoryServerTestCase
{

  private final static int UNDEFINED_INITIAL_SIZE = 10;
  private final static ByteString KEY = ByteString.valueOf("test");

  @Test(expectedExceptions = NullPointerException.class)
  public void testDefinedCannotCreateWithNull()
  {
    newDefinedSet(null);
  }

  @Test
  public void testDefinedAdd()
  {
    EntryIDSet set = newDefinedSet(6, 8, 10, 12);

    assertThat(set.add(new EntryID(4))).isTrue();
    assertIdsEquals(set, 4L, 6, 8, 10, 12);

    assertThat(set.add(new EntryID(14L))).isTrue();
    assertIdsEquals(set, 4L, 6, 8, 10, 12, 14L);

    assertThat(set.add(new EntryID(11))).isTrue();
    assertIdsEquals(set, 4L, 6, 8, 10, 11, 12, 14L);

    assertThat(set.add(new EntryID(10))).isFalse();
    assertIdsEquals(set, 4L, 6, 8, 10, 11, 12, 14);
  }

  @Test
  public void testDefinedAddAll()
  {
    EntryIDSet set = newDefinedSet(10, 12);

    // Add nothing
    set.addAll(newDefinedSet());
    assertIdsEquals(set, 10, 12);

    // Prepend
    set.addAll(newDefinedSet(6, 8));
    assertIdsEquals(set, 6, 8, 10, 12);

    // Append
    set.addAll(newDefinedSet(14, 16));
    assertIdsEquals(set, 6, 8, 10, 12, 14, 16);

    // Prepend & middle
    set.addAll(newDefinedSet(2, 4, 6, 8, 9));
    assertIdsEquals(set, 2, 4, 6, 8, 9, 10, 12, 14, 16);

    // Middle & append
    set.addAll(newDefinedSet(13, 14, 16, 18, 20));
    assertIdsEquals(set, 2, 4, 6, 8, 9, 10, 12, 13, 14, 16, 18, 20);

    // Fully overlapping
    set.addAll(newDefinedSet(1, 2, 4, 6, 8, 9, 10, 12, 13, 14, 16, 18, 20, 21));
    assertIdsEquals(set, 1, 2, 4, 6, 8, 9, 10, 12, 13, 14, 16, 18, 20, 21);
  }

  @Test
  public void testDefinedRemove()
  {
    EntryIDSet set = newDefinedSet(4, 6, 8, 10, 12, 14);

    assertThat(set.remove(new EntryID(4))).isTrue();
    assertIdsEquals(set, 6, 8, 10, 12, 14);

    assertThat(set.remove(new EntryID(14))).isTrue();
    assertIdsEquals(set, 6, 8, 10, 12);

    assertThat(set.remove(new EntryID(10))).isTrue();
    assertIdsEquals(set, 6, 8, 12);

    assertThat(set.remove(new EntryID(10))).isFalse();
    assertIdsEquals(set, 6, 8, 12);
  }

  @Test
  public void testDefinedRemoveAll()
  {
    EntryIDSet set = newDefinedSet(1, 2, 4, 6, 8, 9, 10, 12, 13, 14, 16, 18, 20, 21);

    // Remove nothing
    set.removeAll(newDefinedSet());
    assertIdsEquals(set, 1, 2, 4, 6, 8, 9, 10, 12, 13, 14, 16, 18, 20, 21);

    // Sequential from the beginning
    set.removeAll(newDefinedSet(0, 1, 2));
    assertIdsEquals(set, 4, 6, 8, 9, 10, 12, 13, 14, 16, 18, 20, 21);

    // Sequential from the end
    set.removeAll(newDefinedSet(20, 21, 22));
    assertIdsEquals(set, 4, 6, 8, 9, 10, 12, 13, 14, 16, 18);

    // Random in the middle
    set.removeAll(newDefinedSet(9, 13));
    assertIdsEquals(set, 4, 6, 8, 10, 12, 14, 16, 18);

    // All missing
    set.removeAll(newDefinedSet(1, 5, 11, 17));
    assertIdsEquals(set, 4, 6, 8, 10, 12, 14, 16, 18);
  }

  @Test
  public void testDefinedContain()
  {
    EntryIDSet set = newDefinedSet(4, 6, 8, 10, 12, 14);

    assertThat(set.contains(new EntryID(2))).isFalse();
    assertThat(set.contains(new EntryID(4))).isTrue();

    assertThat(set.contains(new EntryID(9))).isFalse();
    assertThat(set.contains(new EntryID(10))).isTrue();

    assertThat(set.contains(new EntryID(14))).isTrue();
    assertThat(set.contains(new EntryID(16))).isFalse();
  }

  @Test
  public void testDefinedIterator()
  {
    assertIdsEquals(newDefinedSet(4, 6, 8, 10, 12).iterator(), 4L, 6L, 8L, 10L, 12L);
  }

  @Test
  public void testDefinedIteratorWithBegin()
  {
    EntryIDSet set = newDefinedSet(4, 6, 8, 10, 12);

    assertIdsEquals(set.iterator(new EntryID(4)), 4L, 6L, 8L, 10L, 12L);
    assertIdsEquals(set.iterator(new EntryID(8)), 8L, 10L, 12L);
    assertIdsEquals(set.iterator(new EntryID(12)), 12L);
    assertIdsEquals(set.iterator(new EntryID(13)), 4L, 6L, 8L, 10L, 12L);
  }

  @Test
  public void testDefinedByteString()
  {
    ByteString string = newDefinedSet(4, 6, 8, 10, 12).toByteString();
    assertThat(decodeEntryIDSet(string)).containsExactly(4, 6, 8, 10, 12);

    string = newDefinedSet().toByteString();
    assertThat(decodeEntryIDSet(string)).isEmpty();
  }

  @Test(expectedExceptions = NullPointerException.class)
  public void testUndefinedCannotCreateWithNull()
  {
    newUndefinedSetWithSize(null, 1);
  }

  @Test
  public void testUndefinedAdd()
  {
    EntryIDSet undefined = newUndefinedWithInitialSize();

    assertThat(undefined.add(new EntryID(4))).isTrue();
    assertThat(undefined.size()).isEqualTo(UNDEFINED_INITIAL_SIZE + 1);
  }

  @Test
  public void testUndefinedAddAll()
  {
    EntryIDSet undefined = newUndefinedWithInitialSize();

    undefined.addAll(newDefinedSet());
    assertThat(newUndefinedWithInitialSize().size()).isEqualTo(UNDEFINED_INITIAL_SIZE);

    undefined.addAll(newDefinedSet(2, 4, 6));
    assertThat(undefined.size()).isEqualTo(UNDEFINED_INITIAL_SIZE + 3);
  }

  @Test
  public void testUndefinedRemove()
  {
    EntryIDSet undefined = newUndefinedWithInitialSize();

    assertThat(undefined.remove(new EntryID(4))).isTrue();
    assertThat(undefined.size()).isEqualTo(UNDEFINED_INITIAL_SIZE - 1);
  }

  @Test
  public void testUndefinedRemoveUnderflow()
  {
    EntryIDSet undefined = newUndefinedSetWithSize(ByteString.valueOf("test"), 0);

    assertThat(undefined.remove(new EntryID(4))).isTrue();
    assertThat(undefined.size()).isEqualTo(0);
  }

  @Test
  public void testUndefinedDeleteAll()
  {
    EntryIDSet undefined = newUndefinedWithInitialSize();

    undefined.removeAll(newDefinedSet(20, 21, 22));
    assertThat(undefined.size()).isEqualTo(UNDEFINED_INITIAL_SIZE - 3);
  }

  @Test
  public void testUndefinedDeleteAllUnderflow()
  {
    EntryIDSet undefined = newUndefinedSetWithSize(ByteString.valueOf("test"), 0);

    undefined.removeAll(newDefinedSet(20, 21, 22));
    assertThat(undefined.size()).isEqualTo(0);
  }

  @Test
  public void testUndefinedContain()
  {
    assertThat(newUndefinedWithInitialSize().contains(new EntryID(4))).isTrue();
  }

  @Test
  public void testUndefinedIterator()
  {
    assertThat(newUndefinedWithInitialSize().iterator().hasNext()).isFalse();
  }

  @Test
  public void testUndefinedIteratorWithBegin()
  {
    assertThat(newUndefinedWithInitialSize().iterator(new EntryID(8)).hasNext()).isFalse();
  }

  @Test
  public void testUndefinedByteString()
  {
    assertThat(newUndefinedWithInitialSize().toByteString()).isEqualTo(
        ByteString.valueOf(UNDEFINED_INITIAL_SIZE | Long.MIN_VALUE));
  }

  @Test
  public void testNewEmptySet()
  {
    assertThat(newDefinedSet().isDefined()).isTrue();
    assertThat(newDefinedSet().size()).isEqualTo(0);
  }

  @Test
  public void testNewSetFromBytes()
  {
    assertThat(newSetFromBytes(KEY, ByteString.empty()).isDefined()).isFalse();
    assertThat(newSetFromBytes(KEY, ByteString.valueOf(42 | Long.MIN_VALUE)).isDefined()).isFalse();
    assertThat(newSetFromBytes(KEY, ByteString.valueOf(42 | Long.MIN_VALUE)).size()).isEqualTo(42);

    assertThat(newSetFromBytes(KEY, newDefinedSet(1, 2, 3).toByteString()).isDefined()).isTrue();
    assertThat(newSetFromBytes(KEY, newDefinedSet(1, 2, 3).toByteString()).size()).isEqualTo(3);
  }

  @Test
  public void testNewSetWIthIDs()
  {
    assertThat(newDefinedSet().isDefined()).isTrue();
    assertThat(newDefinedSet().size()).isEqualTo(0);

    assertThat(newDefinedSet(1, 2, 3).isDefined()).isTrue();
    assertThat(newDefinedSet(1, 2, 3).size()).isEqualTo(3);
  }

  @Test
  public void testNewUndefinedSet()
  {
    assertThat(newUndefinedSet().isDefined()).isFalse();
    assertThat(newUndefinedSetWithKey(KEY).isDefined()).isFalse();
    assertThat(newUndefinedSetWithKey(KEY).size()).isEqualTo(Long.MAX_VALUE);

    assertThat(newUndefinedSetWithSize(KEY, 42).isDefined()).isFalse();
    assertThat(newUndefinedSetWithSize(KEY, 42).size()).isEqualTo(42);
  }

  @Test
  public void testNewSetFromUnions()
  {
    EntryIDSet union =
        newSetFromUnion(Arrays.asList(newDefinedSet(1, 2, 3), newDefinedSet(4, 5, 6), newDefinedSet(3, 4)));
    assertIdsEquals(union, 1, 2, 3, 4, 5, 6);

    union = newSetFromUnion(Arrays.asList(newDefinedSet(), newDefinedSet(4, 5, 6), newDefinedSet(3, 4)));
    assertIdsEquals(union, 3, 4, 5, 6);

    union = newSetFromUnion(Arrays.asList(newDefinedSet(), newDefinedSet(4, 5, 6), newUndefinedSet()));
    assertThat(union.isDefined()).isFalse();
  }

  @Test
  public void testRetainAll()
  {
    EntryIDSet retained = newDefinedSet(2, 4, 6, 8);
    retained.retainAll(newDefinedSet(1, 2, 3, 5, 6, 7, 8));
    assertThat(retained.isDefined()).isTrue();
    assertIdsEquals(retained, 2, 6, 8);

    retained = newDefinedSet(2, 4, 6, 8);
    retained.retainAll(newDefinedSet(1, 3, 5, 7, 9));
    assertThat(retained.isDefined()).isTrue();
    assertIdsEquals(retained);

    retained = newUndefinedSet();
    retained.retainAll(newDefinedSet(1, 3, 5, 7, 9));
    assertThat(retained.isDefined()).isTrue();
    assertIdsEquals(retained, 1, 3, 5, 7, 9);
  }

  private static EntryIDSet newUndefinedWithInitialSize()
  {
    return newUndefinedSetWithSize(ByteString.valueOf("test"), UNDEFINED_INITIAL_SIZE);
  }

}
