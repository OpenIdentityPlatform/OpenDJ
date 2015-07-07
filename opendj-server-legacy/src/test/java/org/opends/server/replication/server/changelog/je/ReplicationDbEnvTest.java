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
package org.opends.server.replication.server.changelog.je;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.HashSet;

import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.server.ChangelogState;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.types.DN;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.sleepycat.je.Database;
import com.sleepycat.je.Environment;

import static java.util.Arrays.*;
import static java.util.Collections.*;

import static org.assertj.core.api.Assertions.*;
import static org.opends.server.replication.server.changelog.je.ReplicationDbEnv.*;

@SuppressWarnings("javadoc")
public class ReplicationDbEnvTest extends DirectoryServerTestCase
{

  /**
   * Bypass heavyweight setup.
   */
  private final class TestableReplicationDbEnv extends ReplicationDbEnv
  {
    private TestableReplicationDbEnv() throws ChangelogException
    {
      super(null, null);
    }

    @Override
    protected Environment openJEEnvironment(String path)
    {
      return null;
    }

    @Override
    protected Database openDatabase(String databaseName) throws ChangelogException, RuntimeException
    {
      return null;
    }

    @Override
    protected ChangelogState readOnDiskChangelogState() throws ChangelogException
    {
      return new ChangelogState();
    }
  }

  @BeforeClass
  public void setup() throws Exception
  {
    TestCaseUtils.startFakeServer();
  }

  @AfterClass
  public void teardown()
  {
    TestCaseUtils.shutdownFakeServer();
  }

  @DataProvider
  public Object[][] changelogStateDataProvider() throws Exception
  {
    final int genId = 524157415;
    final int id1 = 42;
    final int id2 = 346;
    final int t1 = 1956245524;
    return new Object[][] {
      { DN.valueOf("dc=example,dc=com"), genId, EMPTY_LIST, EMPTY_LIST },
      { DN.valueOf("dc=example,dc=com"), genId, asList(id1, id2),
        asList(new CSN(id2, 0, t1)) },
      // test with a space in the baseDN (space is the field separator in the DB)
      { DN.valueOf("cn=admin data"), genId, asList(id1, id2), EMPTY_LIST }, };
  }

  @Test(dataProvider = "changelogStateDataProvider")
  public void encodeDecodeChangelogState(DN baseDN, long generationId,
      List<Integer> replicas, List<CSN> offlineReplicas) throws Exception
  {
    final ReplicationDbEnv changelogStateDB = new TestableReplicationDbEnv();

    // encode data
    final Map<byte[], byte[]> wholeState = new LinkedHashMap<>();
    put(wholeState, toGenIdEntry(baseDN, generationId));
    for (Integer serverId : replicas)
    {
      put(wholeState, toByteArray(toReplicaEntry(baseDN, serverId)));
    }
    for (CSN offlineCSN : offlineReplicas)
    {
      put(wholeState, toReplicaOfflineEntry(baseDN, offlineCSN));
    }

    // decode data
    final ChangelogState state =
        changelogStateDB.decodeChangelogState(wholeState);
    assertThat(state.getDomainToGenerationId()).containsExactly(
        entry(baseDN, generationId));
    if (!replicas.isEmpty())
    {
      assertThat(state.getDomainToServerIds())
          .containsExactly(entry(baseDN, new HashSet<Integer>(replicas)));
    }
    else
    {
      assertThat(state.getDomainToServerIds()).isEmpty();
    }
    if (!offlineReplicas.isEmpty())
    {
      assertThat(state.getOfflineReplicas().getSnapshot())
          .containsExactly(entry(baseDN, offlineReplicas));
    }
    else
    {
      assertThat(state.getOfflineReplicas()).isEmpty();
    }
  }

  private void put(Map<byte[], byte[]> map, Entry<byte[], byte[]> entry)
  {
    map.put(entry.getKey(), entry.getValue());
  }

}
