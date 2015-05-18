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
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@Test(groups = { "precommit", "pluggablebackend", "unit" }, sequential=true)
public class EntryIDSetTest extends DirectoryServerTestCase
{
  private final static ByteString KEY = ByteString.valueOf("test");

  @Test(expectedExceptions = NullPointerException.class)
  public void testDefinedCannotCreateWithNull()
  {
    newDefinedSet(null);
  }

  @Test
  public void testDefinedAdd()
  {
    final EntryIDSet set = newDefinedSet(6, 8, 10, 12);

    assertThat(set.add(id(4))).isTrue();
    assertIdsEquals(set, 4, 6, 8, 10, 12);

    assertThat(set.add(id(14))).isTrue();
    assertIdsEquals(set, 4, 6, 8, 10, 12, 14);

    assertThat(set.add(id(11))).isTrue();
    assertIdsEquals(set, 4, 6, 8, 10, 11, 12, 14);

    assertThat(set.add(id(10))).isFalse();
    assertIdsEquals(set, 4, 6, 8, 10, 11, 12, 14);
  }

  @Test
  public void testDefinedAddAll()
  {
    final EntryIDSet set = newDefinedSet(10, 12);

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
    final EntryIDSet set = newDefinedSet(4, 6, 8, 10, 12, 14);

    assertThat(set.remove(id(4))).isTrue();
    assertIdsEquals(set, 6, 8, 10, 12, 14);

    assertThat(set.remove(id(14))).isTrue();
    assertIdsEquals(set, 6, 8, 10, 12);

    assertThat(set.remove(id(10))).isTrue();
    assertIdsEquals(set, 6, 8, 12);

    assertThat(set.remove(id(10))).isFalse();
    assertIdsEquals(set, 6, 8, 12);
  }

  @Test
  public void testDefinedRemoveAll()
  {
    final EntryIDSet set = newDefinedSet(1, 2, 4, 6, 8, 9, 10, 12, 13, 14, 16, 18, 20, 21);

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
    final EntryIDSet set = newDefinedSet(4, 6, 8, 10, 12, 14);

    assertThat(set.contains(id(2))).isFalse();
    assertThat(set.contains(id(4))).isTrue();

    assertThat(set.contains(id(9))).isFalse();
    assertThat(set.contains(id(10))).isTrue();

    assertThat(set.contains(id(14))).isTrue();
    assertThat(set.contains(id(16))).isFalse();
  }

  @Test
  public void testDefinedIterator()
  {
    assertIdsEquals(newDefinedSet(4, 6, 8, 10, 12).iterator(), 4, 6, 8, 10, 12);
  }

  @Test
  public void testDefinedIteratorWithBegin()
  {
    final EntryIDSet set = newDefinedSet(4, 6, 8, 10, 12);

    assertIdsEquals(set.iterator(id(4)), 4, 6, 8, 10, 12);
    assertIdsEquals(set.iterator(id(8)), 8, 10, 12);
    assertIdsEquals(set.iterator(id(12)), 12);
    assertIdsEquals(set.iterator(id(13)), 4, 6, 8, 10, 12);
  }

  @Test(dataProvider = "codecs")
  public void testCodecs(EntryIDSetCodec codec)
  {
    ByteString string = codec.encode(newDefinedSet(4, 6, 8, 10, 12));
    assertIdsEquals(codec.decode(KEY, string), 4, 6, 8, 10, 12);

    string = codec.encode(newUndefinedSet());
    assertThat(codec.decode(KEY, string).isDefined()).isFalse();
    assertThat(codec.decode(KEY, string).size()).isEqualTo(Long.MAX_VALUE);

    string = codec.encode(newUndefinedSetWithKey(ByteString.valueOf("none")));
    assertThat(codec.decode(KEY, string).isDefined()).isFalse();
    assertThat(codec.decode(KEY, string).size()).isEqualTo(Long.MAX_VALUE);
  }

  @Test(enabled = false, dataProvider = "codec")
  public void testCodecsEmptyDefinedSet(EntryIDSetCodec codec)
  {
    // FIXME: When decoded, an empty defined set becomes an undefined set
    // see OPENDJ-1833
    ByteString string = codec.encode(newDefinedSet());
    assertThat(codec.decode(KEY, string).size()).isEqualTo(0);

    string = codec.encode(newDefinedSet());
    assertThat(codec.decode(KEY, string).size()).isEqualTo(0);
  }

  @Test(expectedExceptions = NullPointerException.class)
  public void testUndefinedCannotCreateWithNull()
  {
    newUndefinedSetWithKey(null);
  }

  @Test
  public void testUndefinedAddDoesNothing()
  {
    final EntryIDSet undefined = newUndefinedSet();
    assertThat(undefined.add(id(4))).isTrue();
    assertThat(undefined.size()).isEqualTo(Long.MAX_VALUE);
  }

  @Test
  public void testUndefinedAddAllDoesNothing()
  {
    final EntryIDSet undefined = newUndefinedSet();

    undefined.addAll(newDefinedSet());
    assertThat(undefined.size()).isEqualTo(Long.MAX_VALUE);

    undefined.addAll(newDefinedSet(2, 4, 6));
    assertThat(undefined.size()).isEqualTo(Long.MAX_VALUE);
  }

  @Test
  public void testUndefinedRemoveDoesNothing()
  {
    final EntryIDSet undefined = newUndefinedSet();
    assertThat(undefined.remove(id(4))).isTrue();
    assertThat(undefined.size()).isEqualTo(Long.MAX_VALUE);
  }

  @Test
  public void testUndefinedDeleteAllDoesNothing()
  {
    final EntryIDSet undefined = newUndefinedSet();
    undefined.removeAll(newDefinedSet(20, 21, 22));
    assertThat(undefined.size()).isEqualTo(Long.MAX_VALUE);
  }

  @Test
  public void testUndefinedContain()
  {
    assertThat(newUndefinedSet().contains(id(4))).isTrue();
  }

  @Test
  public void testUndefinedIterator()
  {
    assertThat(newUndefinedSet().iterator().hasNext()).isFalse();
  }

  @Test
  public void testUndefinedIteratorWithBegin()
  {
    assertThat(newUndefinedSet().iterator(id(8)).hasNext()).isFalse();
  }

  @Test
  public void testNewEmptySet()
  {
    assertThat(newDefinedSet().isDefined()).isTrue();
    assertThat(newDefinedSet().size()).isEqualTo(0);
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

  @DataProvider(name = "codecs")
  public static Object[][] codecs() {
     return new Object[][] { { CODEC_V1 }, { CODEC_V2 } };
  }

}
