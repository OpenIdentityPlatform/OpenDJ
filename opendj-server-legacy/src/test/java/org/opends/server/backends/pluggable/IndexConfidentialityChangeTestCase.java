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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.backends.pluggable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opends.messages.BackendMessages.NOTE_CONFIG_INDEX_CONFIDENTIALITY_REQUIRES_REBUILD;
import static org.opends.server.util.CollectionUtils.newTreeSet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.server.config.meta.BackendIndexCfgDefn.IndexType;
import org.forgerock.opendj.server.config.server.BackendIndexCfg;
import org.forgerock.opendj.server.config.server.PluggableBackendCfg;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.backends.pluggable.AttributeIndex.MatchingRuleIndex;
import org.opends.server.backends.pluggable.spi.Storage;
import org.opends.server.core.AddOperation;
import org.opends.server.core.ServerContext;
import org.opends.server.types.Entry;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Tests that a change of {@code confidentiality-enabled} on a backend index is applied to the
 * running backend, rather than only reported as requiring a rebuild which cannot help - see OpenDJ
 * issue #992.
 * <p>
 * The confidentiality of an index is carried by the {@link org.opends.server.crypto.CryptoSuite} the
 * indexes of one attribute share, and read from it when an index binds its codec at open time. A
 * change which does not put the new setting in force on that suite, and does not bind the codecs
 * again, leaves the stored records in the encoding of the previous setting for the life of the
 * container.
 */
@SuppressWarnings("javadoc")
@Test(groups = { "precommit", "pluggablebackend" }, sequential = true)
public abstract class IndexConfidentialityChangeTestCase<C extends PluggableBackendCfg> extends DirectoryServerTestCase
{
  private static final DN BASE_DN = DN.valueOf("dc=b992,dc=com");
  /** Indexed for presence, whose tree a confidentiality change keeps, and for equality, whose it does not. */
  private static final String INDEXED_ATTRIBUTE = "sn";
  /** The first byte {@code EntryIDSet.EntryIDSetCodecV3} prepends to a record it encrypted. */
  private static final byte ENCRYPTED_RECORD_TAG = 0x00;
  private static final int ENTRY_LIMIT = 4000;

  private ServerContext serverContext;
  private AttributeType attributeType;

  /**
   * Factory method for the configuration of the backend under test, with the settings specific to its
   * storage engine stubbed out.
   *
   * @return the new backend configuration
   */
  protected abstract C createBackendCfg();

  /**
   * Factory method for the storage of the backend under test, which the test reads the stored records
   * back from.
   *
   * @param cfg
   *          the configuration the backend was configured with
   * @param serverContext
   *          the server context of the running test server
   * @return the storage of the backend under test
   * @throws ConfigException
   *           if the configuration is not one the storage can be opened with
   */
  protected abstract Storage createStorage(C cfg, ServerContext serverContext) throws ConfigException;

  @BeforeClass
  public void startServer() throws Exception
  {
    TestCaseUtils.startServer();
    serverContext = TestCaseUtils.getServerContext();
    attributeType = serverContext.getSchema().getAttributeType(INDEXED_ATTRIBUTE);
  }

  /**
   * A test which fails before it closes its backend leaves the base DN behind in the server wide
   * registry, where it would outlive the test and break the next one to use that DN.
   */
  @AfterMethod
  public void deregisterLeftoverBaseDN()
  {
    try
    {
      serverContext.getBackendConfigManager().deregisterBaseDN(BASE_DN);
    }
    catch (Exception alreadyGone)
    {
      // Which is what a test that closed its backend has left behind.
    }
  }

  /**
   * The records an index holds are in the encoding of the setting in force when they were written,
   * and the codec of the new setting cannot read them back. They are given up here rather than left
   * for the searches which run before the rebuild this change asks for.
   */
  @Test
  public void enablingConfidentialityEmptiesTheIndexItAsksToRebuild() throws Exception
  {
    final TestBackend backend = openBackend(false);
    try
    {
      addEntry(backend, "user.0");
      final AttributeIndex attributeIndex = attributeIndex(backend);
      assertThat(recordCount(backend, presenceIndex(attributeIndex))).isEqualTo(1);

      final ConfigChangeResult ccr = attributeIndex.applyConfigurationChange(indexCfg(true, ENTRY_LIMIT));

      assertThat(ccr.getResultCode()).isEqualTo(ResultCode.SUCCESS);
      assertThat(ccr.adminActionRequired()).isTrue();
      assertThat(ordinalsOf(ccr)).contains(NOTE_CONFIG_INDEX_CONFIDENTIALITY_REQUIRES_REBUILD.ordinal());
      final MatchingRuleIndex presence = presenceIndex(attributeIndex);
      assertThat(recordCount(backend, presence)).isEqualTo(0);
      assertThat(presence.isTrusted()).isFalse();
      // Undefined rather than empty, so that a search of it is not answered with no candidates.
      final ByteString key = keyOf(presence, entryOf("user.0"));
      final boolean defined = backend.storage.read(txn -> presence.get(txn, key).isDefined());
      assertThat(defined).isFalse();
    }
    finally
    {
      backend.finalizeBackend();
    }
  }

  /** The setting the operator asked for has to reach what the index writes from then on. */
  @Test
  public void enablingConfidentialityEncryptsWhatTheIndexWritesNext() throws Exception
  {
    final TestBackend backend = openBackend(false);
    try
    {
      final AttributeIndex attributeIndex = attributeIndex(backend);
      addEntry(backend, "user.0");
      assertThat(rawRecord(backend, presenceIndex(attributeIndex), "user.0").byteAt(0))
          .isNotEqualTo(ENCRYPTED_RECORD_TAG);

      attributeIndex.applyConfigurationChange(indexCfg(true, ENTRY_LIMIT));
      trust(backend, attributeIndex);
      addEntry(backend, "user.1");

      final MatchingRuleIndex presence = presenceIndex(attributeIndex);
      assertThat(rawRecord(backend, presence, "user.1").byteAt(0)).isEqualTo(ENCRYPTED_RECORD_TAG);
      assertThat(idsOf(backend, presence, "user.1")).hasSize(1);
    }
    finally
    {
      backend.finalizeBackend();
    }
  }

  /**
   * Enabling confidentiality of an equality index replaces its tree with one whose keys are hashed,
   * which the change creates and opens itself. That one has to be opened with the new setting in
   * force, or its keys are protected while its records are not.
   */
  @Test
  public void enablingConfidentialityEncryptsTheKeyHashedIndexItCreates() throws Exception
  {
    final TestBackend backend = openBackend(false);
    try
    {
      final AttributeIndex attributeIndex = attributeIndex(backend);
      addEntry(backend, "user.0");
      assertThat(keyHashedIndex(attributeIndex)).isNull();

      attributeIndex.applyConfigurationChange(indexCfg(true, ENTRY_LIMIT));
      trust(backend, attributeIndex);
      addEntry(backend, "user.1");

      final MatchingRuleIndex hashed = keyHashedIndex(attributeIndex);
      assertThat(hashed).isNotNull();
      assertThat(rawRecord(backend, hashed, "user.1").byteAt(0)).isEqualTo(ENCRYPTED_RECORD_TAG);
      assertThat(idsOf(backend, hashed, "user.1")).hasSize(1);
    }
    finally
    {
      backend.finalizeBackend();
    }
  }

  /** And the same the other way around: giving the setting up has to stop the encryption. */
  @Test
  public void disablingConfidentialityStopsEncryptingWhatTheIndexWritesNext() throws Exception
  {
    final TestBackend backend = openBackend(true);
    try
    {
      final AttributeIndex attributeIndex = attributeIndex(backend);
      addEntry(backend, "user.0");
      assertThat(rawRecord(backend, presenceIndex(attributeIndex), "user.0").byteAt(0))
          .isEqualTo(ENCRYPTED_RECORD_TAG);

      attributeIndex.applyConfigurationChange(indexCfg(false, ENTRY_LIMIT));
      trust(backend, attributeIndex);
      addEntry(backend, "user.1");

      final MatchingRuleIndex presence = presenceIndex(attributeIndex);
      assertThat(rawRecord(backend, presence, "user.1").byteAt(0)).isNotEqualTo(ENCRYPTED_RECORD_TAG);
      assertThat(idsOf(backend, presence, "user.1")).hasSize(1);
    }
    finally
    {
      backend.finalizeBackend();
    }
  }

  /**
   * Once the change is applied, the comparison it is reported by converges: an unrelated change of
   * the same index must not untrust it again, and must not repeat the rebuild message.
   */
  @Test
  public void aLaterUnrelatedChangeLeavesTheIndexTrusted() throws Exception
  {
    final TestBackend backend = openBackend(false);
    try
    {
      final AttributeIndex attributeIndex = attributeIndex(backend);
      addEntry(backend, "user.0");
      attributeIndex.applyConfigurationChange(indexCfg(true, ENTRY_LIMIT));
      trust(backend, attributeIndex);

      // A smaller index entry limit does not invalidate what the index holds, so this change has no
      // rebuild of its own to ask for.
      final ConfigChangeResult ccr = attributeIndex.applyConfigurationChange(indexCfg(true, ENTRY_LIMIT - 1));

      assertThat(ccr.getResultCode()).isEqualTo(ResultCode.SUCCESS);
      assertThat(ccr.getMessages()).isEmpty();
      assertThat(ccr.adminActionRequired()).isFalse();
      for (MatchingRuleIndex index : attributeIndex.getNameToIndexes().values())
      {
        assertThat(index.isTrusted()).as(index.getName().toString()).isTrue();
      }
    }
    finally
    {
      backend.finalizeBackend();
    }
  }

  private TestBackend openBackend(boolean indexConfidentiality) throws Exception
  {
    final C cfg = backendCfg(indexConfidentiality);
    final TestBackend backend = new TestBackend();
    backend.setBackendID(cfg.getBackendId());
    backend.configureBackend(cfg, serverContext);
    // Start from a pristine on-disk state, so that a previous run cannot mask the defect.
    backend.storage.removeStorageFiles();
    try
    {
      backend.openBackend();
      backend.addEntry(TestCaseUtils.makeEntry(
          "dn: " + BASE_DN,
          "objectClass: top",
          "objectClass: domain",
          "dc: b992"), mock(AddOperation.class));
    }
    catch (Exception e)
    {
      // openBackend() registers the base DN and the monitor before it returns, so a failure after
      // that would leave both behind and break every following test rather than only this one.
      try
      {
        backend.finalizeBackend();
      }
      catch (Exception cleanupFailure)
      {
        e.addSuppressed(cleanupFailure);
      }
      throw e;
    }
    return backend;
  }

  private Entry addEntry(TestBackend backend, String uid) throws Exception
  {
    final Entry entry = entryOf(uid);
    backend.addEntry(entry, mock(AddOperation.class));
    return entry;
  }

  /** Trusts every index of the attribute, which is what the rebuild the change asks for leaves behind. */
  private void trust(TestBackend backend, final AttributeIndex attributeIndex) throws Exception
  {
    backend.storage.write(txn -> {
      for (MatchingRuleIndex index : attributeIndex.getNameToIndexes().values())
      {
        index.setTrusted(txn, true);
      }
    });
  }

  private AttributeIndex attributeIndex(TestBackend backend)
  {
    return backend.getRootContainer().getEntryContainer(BASE_DN).getAttributeIndex(attributeType);
  }

  private static MatchingRuleIndex presenceIndex(AttributeIndex attributeIndex)
  {
    return attributeIndex.getNameToIndexes().get(IndexType.PRESENCE.toString());
  }

  private static MatchingRuleIndex keyHashedIndex(AttributeIndex attributeIndex)
  {
    for (Map.Entry<String, MatchingRuleIndex> index : attributeIndex.getNameToIndexes().entrySet())
    {
      if (index.getKey().endsWith(AttributeIndex.PROTECTED_INDEX_ID))
      {
        return index.getValue();
      }
    }
    return null;
  }

  private static ByteString keyOf(MatchingRuleIndex index, Entry entry)
  {
    return index.indexEntry(entry).iterator().next();
  }

  /** The record as it is stored, which is what says whether it was encrypted. */
  private ByteString rawRecord(TestBackend backend, final MatchingRuleIndex index, String uid) throws Exception
  {
    final ByteString key = keyOf(index, entryOf(uid));
    final ByteString record = backend.storage.read(txn -> txn.read(index.getName(), key));
    assertThat(record).as("the record of " + index.getName() + " at key " + key).isNotNull();
    return record;
  }

  private List<Long> idsOf(TestBackend backend, final MatchingRuleIndex index, String uid) throws Exception
  {
    final ByteString key = keyOf(index, entryOf(uid));
    final EntryIDSet idSet = backend.storage.read(txn -> index.get(txn, key));
    assertThat(idSet.isDefined()).as("the entry IDs of " + index.getName() + " at key " + key).isTrue();
    final List<Long> ids = new ArrayList<>();
    for (EntryID id : idSet)
    {
      ids.add(id.longValue());
    }
    return ids;
  }

  /** The entry as it was added, which the indexers generate the keys of a record from. */
  private Entry entryOf(String uid) throws Exception
  {
    return TestCaseUtils.makeEntry(
        "dn: uid=" + uid + "," + BASE_DN,
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: " + uid,
        "cn: " + uid,
        "sn: " + uid);
  }

  private long recordCount(TestBackend backend, final MatchingRuleIndex index) throws Exception
  {
    return backend.storage.read(txn -> index.getRecordCount(txn));
  }

  private static Set<Integer> ordinalsOf(ConfigChangeResult ccr)
  {
    final Set<Integer> ordinals = new HashSet<>();
    for (LocalizableMessage message : ccr.getMessages())
    {
      ordinals.add(message.ordinal());
    }
    return ordinals;
  }

  private C backendCfg(boolean indexConfidentiality) throws ConfigException
  {
    final C cfg = createBackendCfg();
    // Read outside the when() below, which calling a mock inside would leave unfinished.
    final String backendId = cfg.getBackendId();
    when(cfg.dn()).thenReturn(DN.valueOf("ds-cfg-backend-id=" + backendId + ",cn=Backends,cn=config"));
    when(cfg.getBaseDN()).thenReturn(newTreeSet(BASE_DN));
    when(cfg.listBackendIndexes()).thenReturn(new String[] { INDEXED_ATTRIBUTE });
    when(cfg.listBackendVLVIndexes()).thenReturn(new String[0]);
    // An index can only be confidential in a backend which is, and the cipher of the backend is what
    // the crypto suite of an index takes its parameters from.
    when(cfg.isConfidentialityEnabled()).thenReturn(true);
    when(cfg.getCipherTransformation()).thenReturn("AES/CBC/PKCS5Padding");
    when(cfg.getCipherKeyLength()).thenReturn(128);
    // Stubbed outside the when() below, which stubbing another mock inside would leave unfinished.
    final BackendIndexCfg indexCfg = indexCfg(indexConfidentiality, ENTRY_LIMIT);
    when(cfg.getBackendIndex(INDEXED_ATTRIBUTE)).thenReturn(indexCfg);
    return cfg;
  }

  private BackendIndexCfg indexCfg(boolean confidentiality, int entryLimit)
  {
    final BackendIndexCfg cfg = mock(BackendIndexCfg.class);
    when(cfg.getIndexType()).thenReturn(newTreeSet(IndexType.PRESENCE, IndexType.EQUALITY));
    when(cfg.getAttribute()).thenReturn(attributeType);
    when(cfg.getIndexEntryLimit()).thenReturn(entryLimit);
    when(cfg.getSubstringLength()).thenReturn(6);
    when(cfg.isConfidentialityEnabled()).thenReturn(confidentiality);
    return cfg;
  }

  /** A backend whose storage the test reads the stored records back from. */
  private final class TestBackend extends BackendImpl<C>
  {
    private Storage storage;

    @Override
    protected Storage configureStorage(C cfg, ServerContext serverContext) throws ConfigException
    {
      storage = createStorage(cfg, serverContext);
      return storage;
    }
  }
}
