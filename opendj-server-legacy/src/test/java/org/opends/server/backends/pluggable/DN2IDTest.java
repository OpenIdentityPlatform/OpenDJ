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
import static org.forgerock.opendj.config.ConfigurationMock.*;
import static org.mockito.Mockito.*;
import static org.opends.server.util.CollectionUtils.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.schema.CoreSchema;
import org.forgerock.opendj.server.config.meta.BackendIndexCfgDefn.IndexType;
import org.forgerock.opendj.server.config.server.BackendIndexCfg;
import org.forgerock.opendj.server.config.server.PDBBackendCfg;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.PromiseImpl;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.backends.pdb.PDBStorage;
import org.opends.server.backends.pluggable.spi.AccessMode;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.ReadOperation;
import org.opends.server.backends.pluggable.spi.ReadableTransaction;
import org.opends.server.backends.pluggable.spi.SequentialCursor;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.WriteOperation;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;
import org.opends.server.core.MemoryQuota;
import org.opends.server.core.ServerContext;
import org.opends.server.extensions.DiskSpaceMonitor;
import org.opends.server.types.DirectoryException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
@Test(groups = { "precommit", "pluggablebackend" }, sequential = true)
public class DN2IDTest extends DirectoryServerTestCase
{
  private final TreeName dn2IDTreeName = new TreeName("base-dn", "index-id");
  private DN baseDN;
  private DN2ID dn2ID;
  private PDBStorage storage;

  // FIXME: This is required since PDBStorage is now using
  // DirectoryServer static method.
  @BeforeClass
  public void startServer() throws Exception
  {
    TestCaseUtils.startServer();
  }

  @BeforeMethod
  public void setUp() throws Exception
  {
    ServerContext serverContext = mock(ServerContext.class);
    when(serverContext.getMemoryQuota()).thenReturn(new MemoryQuota());
    when(serverContext.getDiskSpaceMonitor()).thenReturn(mock(DiskSpaceMonitor.class));

    storage = new PDBStorage(createBackendCfg(), serverContext);
    storage.open(AccessMode.READ_WRITE);
    storage.write(new WriteOperation()
    {
      @Override
      public void run(WriteableTransaction txn) throws Exception
      {
        txn.openTree(dn2IDTreeName, true);
      }
    });

    baseDN = dn("dc=example, dc=com");
    dn2ID = new DN2ID(dn2IDTreeName, baseDN);
  }

  @AfterMethod
  public void tearDown()
  {
    storage.close();
    storage.removeStorageFiles();
  }

  private void populate() throws DirectoryException, Exception
  {
    final String[] dns =
      {
                                 "dc=example,dc=com",
                      "ou=Devices,dc=example,dc=com",
              "cn=dev0,ou=Devices,dc=example,dc=com",
                       "ou=People,dc=example,dc=com",
                "cn=foo,ou=People,dc=example,dc=com",
             "cn=barbar,ou=People,dc=example,dc=com",
             "cn=foofoo,ou=People,dc=example,dc=com",
                "cn=bar,ou=People,dc=example,dc=com",
        "cn=dev0,cn=bar,ou=People,dc=example,dc=com",
        "cn=dev1,cn=bar,ou=People,dc=example,dc=com"
      };

    for (int i = 0; i < dns.length; i++)
    {
      put(dn(dns[i]), i + 1);
    }
  }

  @Test
  public void testCanAddDN() throws Exception
  {
    populate();

    assertThat(get("dc=example,dc=com")).isEqualTo(id(1));
    assertThat(get("ou=People,dc=example,dc=com")).isEqualTo(id(4));
    assertThat(get("cn=dev1,cn=bar,ou=People,dc=example,dc=com")).isEqualTo(id(10));
  }

  @Test
  public void testIsChild() throws DirectoryException, Exception {
    put(dn("dc=example,dc=com"), 1);
    put(dn("ou=People,dc=example,dc=com"), 2);
    put(dn("uid=user.0,ou=People,dc=example,dc=com"), 3);
    put(dn("uid=user.1,ou=People,dc=example,dc=com"), 4);
    put(dn("uid=user.10,ou=People,dc=example,dc=com"), 5);

    storage.read(new ReadOperation<Void>()
    {
      @Override
      public Void run(ReadableTransaction txn) throws Exception
      {
        try (final Cursor<ByteString, ByteString> cursor = txn.openCursor(dn2ID.getName()))
        {
          cursor.next();
          final ByteString rootDN = cursor.getKey();
          cursor.next();
          final ByteString parentDN = cursor.getKey();
          cursor.next();
          assertThat(DnKeyFormat.isChild(rootDN, parentDN)).isTrue();

          final ByteString childDN = cursor.getKey();
          assertThat(DnKeyFormat.isChild(parentDN, childDN)).isTrue();

          cursor.next();
          final ByteString otherChildDN = cursor.getKey();
          assertThat(DnKeyFormat.isChild(parentDN, otherChildDN)).isTrue();
          assertThat(DnKeyFormat.isChild(childDN, otherChildDN)).isFalse();

          final ByteString lastChildDN = cursor.getKey();
          assertThat(DnKeyFormat.isChild(parentDN, lastChildDN)).isTrue();
          assertThat(DnKeyFormat.isChild(otherChildDN, lastChildDN)).isFalse();
          assertThat(DnKeyFormat.isChild(childDN, lastChildDN)).isFalse();
        }
        return null;
      }
    });

  }

  @Test
  public void testGetNonExistingDNReturnNull() throws Exception
  {
    assertThat(get("dc=non,dc=existing")).isNull();
  }

  @Test
  public void testCanRemove() throws Exception
  {
    populate();

    assertThat(get("ou=People,dc=example,dc=com")).isNotNull();
    assertThat(remove("ou=People,dc=example,dc=com")).isTrue();
    assertThat(get("ou=People,dc=example,dc=com")).isNull();
  }

  @Test
  public void testRemoveNonExistingEntry() throws Exception
  {
    assertThat(remove("dc=non,dc=existing")).isFalse();
  }

  @Test
  public void testTraverseChildren() throws Exception
  {
    populate();
    assertThat(traverseChildren("ou=People,dc=example,dc=com"))
      .containsExactly(
                       get("cn=bar,ou=People,dc=example,dc=com"),
                    get("cn=barbar,ou=People,dc=example,dc=com"),
                       get("cn=foo,ou=People,dc=example,dc=com"),
                    get("cn=foofoo,ou=People,dc=example,dc=com"));
  }

  @Test
  public void testTraverseSubordinates() throws Exception
  {
    populate();
    assertThat(traverseSubordinates("ou=People,dc=example,dc=com"))
      .containsExactly(
                      get("cn=bar,ou=People,dc=example,dc=com"),
              get("cn=dev0,cn=bar,ou=People,dc=example,dc=com"),
              get("cn=dev1,cn=bar,ou=People,dc=example,dc=com"),
                   get("cn=barbar,ou=People,dc=example,dc=com"),
                      get("cn=foo,ou=People,dc=example,dc=com"),
                   get("cn=foofoo,ou=People,dc=example,dc=com"));
  }

  private EntryID get(final String dn) throws Exception
  {
    return storage.read(new ReadOperation<EntryID>()
    {
      @Override
      public EntryID run(ReadableTransaction txn) throws Exception
      {
        return dn2ID.get(txn, dn(dn));
      }
    });
  }

  private List<EntryID> traverseChildren(final String dn) throws Exception
  {
    return storage.read(new ReadOperation<List<EntryID>>()
    {
      @Override
      public List<EntryID> run(ReadableTransaction txn) throws Exception
      {
        try (final SequentialCursor<Void, EntryID> cursor = dn2ID.openChildrenCursor(txn, dn(dn)))
        {
          return getAllIDs(cursor);
        }
      }
    });
  }

  private List<EntryID> traverseSubordinates(final String dn) throws Exception
  {
    return storage.read(new ReadOperation<List<EntryID>>()
    {
      @Override
      public List<EntryID> run(ReadableTransaction txn) throws Exception
      {
        try (final SequentialCursor<Void, EntryID> cursor = dn2ID.openSubordinatesCursor(txn, dn(dn)))
        {
          return getAllIDs(cursor);
        }
      }
    });
  }

  private static <K, V> List<V> getAllIDs(SequentialCursor<K, V> cursor) {
    final List<V> values = new ArrayList<>();
    while(cursor.next()) {
      values.add(cursor.getValue());
    }
    return values;
  }

  private void put(final DN dn, final long id) throws Exception
  {
    storage.write(new WriteOperation()
    {
      @Override
      public void run(WriteableTransaction txn) throws Exception
      {
        dn2ID.put(txn, dn, new EntryID(id));
      }
    });
  }

  private boolean remove(final String dn) throws Exception
  {
    final PromiseImpl<Boolean, NeverThrowsException> p = PromiseImpl.create();
    storage.write(new WriteOperation()
    {
      @Override
      public void run(WriteableTransaction txn) throws Exception
      {
        p.handleResult(dn2ID.remove(txn, dn(dn)));
      }
    });
    return p.get(10, TimeUnit.SECONDS);
  }

  private static DN dn(String dn) throws DirectoryException
  {
    return DN.valueOf(dn);
  }

  private static EntryID id(long id)
  {
    return new EntryID(id);
  }

  private static PDBBackendCfg createBackendCfg() throws ConfigException, DirectoryException
  {
    String homeDirName = "pdb_test";
    PDBBackendCfg backendCfg = mockCfg(PDBBackendCfg.class);

    when(backendCfg.getBackendId()).thenReturn("persTest" + homeDirName);
    when(backendCfg.getDBDirectory()).thenReturn(homeDirName);
    when(backendCfg.getDBDirectoryPermissions()).thenReturn("755");
    when(backendCfg.getDBCacheSize()).thenReturn(0L);
    when(backendCfg.getDBCachePercent()).thenReturn(20);
    when(backendCfg.getBaseDN()).thenReturn(newTreeSet(DN.valueOf("dc=test,dc=com")));
    when(backendCfg.dn()).thenReturn(DN.valueOf("dc=test,dc=com"));
    when(backendCfg.listBackendIndexes()).thenReturn(new String[] { "sn" });
    when(backendCfg.listBackendVLVIndexes()).thenReturn(new String[0]);

    BackendIndexCfg indexCfg = mockCfg(BackendIndexCfg.class);
    when(indexCfg.getIndexType()).thenReturn(newTreeSet(IndexType.PRESENCE, IndexType.EQUALITY));
    when(indexCfg.getAttribute()).thenReturn(CoreSchema.getSNAttributeType());
    when(backendCfg.getBackendIndex("sn")).thenReturn(indexCfg);

    return backendCfg;
  }
}
