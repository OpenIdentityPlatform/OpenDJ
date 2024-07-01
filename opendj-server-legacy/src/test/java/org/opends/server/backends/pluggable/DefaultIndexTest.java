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
 * Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.server.backends.pluggable;

import static org.assertj.core.api.Assertions.*;
import static org.forgerock.opendj.ldap.ByteString.valueOfUtf8;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;
import static org.opends.server.backends.pluggable.EntryIDSet.*;
import static org.opends.server.backends.pluggable.State.IndexFlag.*;
import static org.opends.server.backends.pluggable.Utils.assertIdsEquals;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.TreeMap;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.backends.pluggable.State.IndexFlag;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.ReadableTransaction;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.UpdateFunction;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;
import org.opends.server.crypto.CryptoSuite;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
@Test(groups = { "precommit", "pluggablebackend" }, sequential = true)
public class DefaultIndexTest extends DirectoryServerTestCase
{
  private DefaultIndex index;
  private WriteableTransaction txn;

  @BeforeMethod
  public void setUp() {
    txn = new DummyWriteableTransaction();
    index = newIndex("test", 5, EnumSet.of(TRUSTED, COMPACTED));
    index.open(txn, true);
  }

  @Test
  public void testUpdateAddedIDs() {
    update(newDefinedSet(), newDefinedSet(1, 2, 3, 4));

    assertIdsEquals(get(), 1, 2, 3, 4);
  }

  @Test
  public void testUpdateDeletedIDs() {
    put(newDefinedSet(1, 2, 3, 4));
    update(newDefinedSet(2, 4), newDefinedSet());

    assertIdsEquals(get(), 1, 3);
  }

  @Test
  public void testUpdateMixedIDs() {
    put(newDefinedSet(1, 2, 3, 4));
    update(newDefinedSet(2, 4), newDefinedSet(5, 6));

    assertIdsEquals(get(), 1, 3, 5, 6);
  }

  @Test
  public void testAllIDs() {
    put(newDefinedSet(1, 2, 3, 4));
    update(newDefinedSet(1, 2), newDefinedSet(5, 6, 7, 8));

    assertThat(get().isDefined()).isFalse();
  }

  @Test
  public void testEmptyIdSetAreRemoved() {
    put(newDefinedSet(1, 2, 3, 4));
    update(newDefinedSet(1, 2, 3, 4), newDefinedSet());

    assertThat(txn.read(index.getName(), valueOfUtf8("key"))).isNull();
  }

  private void update(EntryIDSet deletedIDSet, EntryIDSet addedIDSet) {
    index.update(txn, valueOfUtf8("key"), deletedIDSet, addedIDSet);
  }

  private void put(EntryIDSet idSet)
  {
    txn.put(index.getName(), valueOfUtf8("key"), CODEC_V2.encode(idSet));
  }

  private ByteString getFromDb() {
    return txn.read(index.getName(), valueOfUtf8("key"));
  }

  private EntryIDSet get() {
    return CODEC_V2.decode(valueOfUtf8("key"), getFromDb());
  }

  private static DefaultIndex newIndex(String name, int indexLimit, EnumSet<IndexFlag> indexFlags)
  {
    final State state = mock(State.class);
    when(state.getIndexFlags(any(ReadableTransaction.class), any(TreeName.class))).thenReturn(indexFlags);
    final CryptoSuite cryptoSuite = mock(CryptoSuite.class);
    when(cryptoSuite.isEncrypted()).thenReturn(false);
    return new DefaultIndex(new TreeName("dc=example,dc=com", name), state, indexLimit, mock(EntryContainer.class),
        cryptoSuite);
  }

  static final class DummyWriteableTransaction implements WriteableTransaction {

    private final Map<TreeName, TreeMap<ByteString, ByteString>> storage = new HashMap<>();

    @Override
    public ByteString read(TreeName treeName, ByteSequence key)
    {
      return getTree(treeName).get(key);
    }

    private TreeMap<ByteString, ByteString> getTree(TreeName treeName) {
      final TreeMap<ByteString, ByteString> tree = storage.get(treeName);
      if ( tree == null ) {
        throw new StorageRuntimeException("Tree " + treeName + " doesn't exists");
      }
      return tree;
    }

    @Override
    public Cursor<ByteString, ByteString> openCursor(TreeName treeName)
    {
      final TreeMap<ByteString, ByteString> tree = getTree(treeName);

      return new Cursor<ByteString, ByteString>()
      {
        private Iterator<Entry<ByteString, ByteString>> it = tree.entrySet().iterator();
        private Entry<ByteString, ByteString> current;

        @Override
        public boolean next()
        {
          if(!it.hasNext()) {
            return false;
          }
          current = it.next();
          return true;
        }

        @Override
        public void delete() throws NoSuchElementException, UnsupportedOperationException
        {
          it.remove();
        }

        @Override
        public boolean isDefined()
        {
          return current != null;
        }

        @Override
        public ByteString getKey() throws NoSuchElementException
        {
          return current.getKey();
        }

        @Override
        public ByteString getValue() throws NoSuchElementException
        {
          return current.getValue();
        }

        @Override
        public void close()
        {
          it = null;
          current = null;
        }

        @Override
        public boolean positionToKey(ByteSequence key)
        {
          current = null;

          it = tree.tailMap(key.toByteString()).entrySet().iterator();
          return it.hasNext() && it.next().getKey().equals(key.toByteString());
        }

        @Override
        public boolean positionToKeyOrNext(ByteSequence key)
        {
          current = null;

          it = tree.tailMap(key.toByteString()).entrySet().iterator();
          if( it.hasNext() ) {
            it.next();
            return true;
          }
          return false;
        }

        @Override
        public boolean positionToLastKey()
        {
          current = null;

          while(it.hasNext()) {
            next();
          }

          return true;
        }

        @Override
        public boolean positionToIndex(int index)
        {
          current = null;
          it = tree.entrySet().iterator();
          int i;
          for(i = 0 ; i < index && it.hasNext() ; i++ ) {
            next();
          }
          return i == index;
        }
      };
    }

    @Override
    public long getRecordCount(TreeName treeName)
    {
      return getTree(treeName).size();
    }

    @Override
    public void openTree(TreeName name, boolean createOnDemand)
    {
      storage.put(name, new TreeMap<ByteString, ByteString>());
    }

    @Override
    public void deleteTree(TreeName name)
    {
      storage.remove(name);
    }

    @Override
    public void put(TreeName treeName, ByteSequence key, ByteSequence value)
    {
      getTree(treeName).put(key.toByteString(), value.toByteString());
    }

    @Override
    public boolean update(TreeName treeName, ByteSequence key, UpdateFunction f)
    {
      final ByteString oldValue = getTree(treeName).get(key);
      final ByteSequence newValue = f.computeNewValue(oldValue);
      if (newValue == null) {
        return getTree(treeName).remove(key) != null;
      }
      return newValue.equals(getTree(treeName).put(key.toByteString(), newValue.toByteString()));
    }

    @Override
    public boolean delete(TreeName treeName, ByteSequence key)
    {
      return getTree(treeName).remove(key) != null;
    }
  }
}
