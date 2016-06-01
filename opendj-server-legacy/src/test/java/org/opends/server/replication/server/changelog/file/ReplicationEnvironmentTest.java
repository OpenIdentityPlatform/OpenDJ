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
 * Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.replication.server.changelog.file;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.assertj.core.data.MapEntry;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.util.time.TimeService;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.crypto.CryptoSuite;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.ChangelogState;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.server.changelog.api.ChangeNumberIndexRecord;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.TimeThread;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.opends.server.replication.server.changelog.file.ReplicationEnvironment.*;

@SuppressWarnings("javadoc")
public class ReplicationEnvironmentTest extends DirectoryServerTestCase
{
  private static final int SERVER_ID_1 = 1;
  private static final int SERVER_ID_2 = 2;

  private static final String DN1_AS_STRING = "cn=test1,dc=company.com";
  private static final String DN2_AS_STRING = "cn=te::st2,dc=company.com";
  private static final String DN3_AS_STRING = "cn=test3,dc=company.com";
  private static final String TEST_DIRECTORY_CHANGELOG = "test-output/changelog";

  private static final CryptoSuite cryptoSuite = mock(CryptoSuite.class);

  @BeforeClass
  public void setUp() throws Exception
  {
    // This test suite depends on having the schema available for DN decoding.
    when(cryptoSuite.isEncrypted()).thenReturn(false);
  }

  @AfterMethod
  public void cleanTestChangelogDirectory()
  {
    final File rootPath = new File(TEST_DIRECTORY_CHANGELOG);
    if (rootPath.exists())
    {
      StaticUtils.recursiveDelete(rootPath);
    }
  }

  @Test
  public void testReadChangelogStateWithSingleDN() throws Exception
  {
    Log<Long,ChangeNumberIndexRecord> cnDB = null;
    Log<CSN,UpdateMsg> replicaDB = null, replicaDB2 = null;
    try
    {
      final File rootPath = new File(TEST_DIRECTORY_CHANGELOG);
      final DN domainDN = DN.valueOf(DN1_AS_STRING);
      ReplicationEnvironment environment = createReplicationEnv(rootPath);
      cnDB = environment.getOrCreateCNIndexDB();
      replicaDB = environment.getOrCreateReplicaDB(domainDN, SERVER_ID_1, 1, cryptoSuite);
      replicaDB2 = environment.getOrCreateReplicaDB(domainDN, SERVER_ID_2, 1, cryptoSuite);

      final ChangelogState state = environment.readOnDiskChangelogState();

      assertThat(state.getDomainToServerIds()).containsKeys(domainDN);
      assertThat(state.getDomainToServerIds().get(domainDN)).containsOnly(SERVER_ID_1, SERVER_ID_2);
      assertThat(state.getDomainToGenerationId()).containsExactly(MapEntry.entry(domainDN, 1L));

      assertThat(state.isEqualTo(environment.getChangelogState())).isTrue();
    }
    finally
    {
      StaticUtils.close(cnDB, replicaDB, replicaDB2);
    }
  }

  @Test
  public void testReadChangelogStateWithMultipleDN() throws Exception
  {
    Log<Long,ChangeNumberIndexRecord> cnDB = null;
    List<Log<CSN,UpdateMsg>> replicaDBs = new ArrayList<>();
    try
    {
      File rootPath = new File(TEST_DIRECTORY_CHANGELOG);
      List<DN> domainDNs = Arrays.asList(
          DN.valueOf(DN1_AS_STRING), DN.valueOf(DN2_AS_STRING), DN.valueOf(DN3_AS_STRING));
      ReplicationEnvironment environment = createReplicationEnv(rootPath);
      cnDB = environment.getOrCreateCNIndexDB();
      for (int i = 0; i <= 2 ; i++)
      {
        for (int j = 1; j <= 10; j++)
        {
          // 3 domains, 10 server id each, generation id is different for each domain
          replicaDBs.add(environment.getOrCreateReplicaDB(domainDNs.get(i), j, i+1, cryptoSuite));
        }
      }

      final ChangelogState state = environment.readOnDiskChangelogState();

      assertThat(state.getDomainToServerIds()).containsKeys(domainDNs.get(0), domainDNs.get(1), domainDNs.get(2));
      for (int i = 0; i <= 2 ; i++)
      {
        assertThat(state.getDomainToServerIds().get(domainDNs.get(i))).containsOnly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
      }
      assertThat(state.getDomainToGenerationId()).containsOnly(
          MapEntry.entry(domainDNs.get(0), 1L),
          MapEntry.entry(domainDNs.get(1), 2L),
          MapEntry.entry(domainDNs.get(2), 3L));

      assertThat(state.isEqualTo(environment.getChangelogState())).isTrue();
    }
    finally
    {
      StaticUtils.close(cnDB);
      StaticUtils.close(replicaDBs);
    }
  }

  @Test
  public void testReadChangelogStateWithReplicaOffline() throws Exception
  {
    Log<Long,ChangeNumberIndexRecord> cnDB = null;
    Log<CSN,UpdateMsg> replicaDB = null;
    try
    {
      final File rootPath = new File(TEST_DIRECTORY_CHANGELOG);
      final DN domainDN = DN.valueOf(DN1_AS_STRING);
      ReplicationEnvironment environment = createReplicationEnv(rootPath);
      cnDB = environment.getOrCreateCNIndexDB();
      replicaDB = environment.getOrCreateReplicaDB(domainDN, SERVER_ID_1, 1, cryptoSuite);

      // put server id 1 offline
      CSN offlineCSN = new CSN(TimeThread.getTime(), 0, SERVER_ID_1);
      environment.notifyReplicaOffline(domainDN, offlineCSN);

      final ChangelogState state = environment.readOnDiskChangelogState();

      assertThat(state.getOfflineReplicas().getSnapshot())
          .containsExactly(MapEntry.entry(domainDN, Arrays.asList(offlineCSN)));

      assertThat(state.isEqualTo(environment.getChangelogState())).isTrue();
    }
    finally
    {
      StaticUtils.close(cnDB, replicaDB);
    }
  }

  @Test(expectedExceptions=ChangelogException.class)
  public void testReadChangelogStateWithReplicaOfflineStateFileCorrupted() throws Exception
  {
    Log<Long,ChangeNumberIndexRecord> cnDB = null;
    Log<CSN,UpdateMsg> replicaDB = null;
    try
    {
      final File rootPath = new File(TEST_DIRECTORY_CHANGELOG);
      final DN domainDN = DN.valueOf(DN1_AS_STRING);
      ReplicationEnvironment environment = createReplicationEnv(rootPath);
      cnDB = environment.getOrCreateCNIndexDB();
      replicaDB = environment.getOrCreateReplicaDB(domainDN, SERVER_ID_1, 1, cryptoSuite);

      File offlineStateFile = new File(environment.getServerIdPath("1", 1), REPLICA_OFFLINE_STATE_FILENAME);
      offlineStateFile.createNewFile();

      environment.readOnDiskChangelogState();
    }
    finally
    {
      StaticUtils.close(cnDB, replicaDB);
    }
  }

  @Test
  public void testReadChangelogStateWithReplicaOfflineSentTwice() throws Exception
  {
    Log<Long,ChangeNumberIndexRecord> cnDB = null;
    Log<CSN,UpdateMsg> replicaDB = null;
    try
    {
      final File rootPath = new File(TEST_DIRECTORY_CHANGELOG);
      final DN domainDN = DN.valueOf(DN1_AS_STRING);

      ReplicationEnvironment environment = createReplicationEnv(rootPath);
      cnDB = environment.getOrCreateCNIndexDB();
      replicaDB = environment.getOrCreateReplicaDB(domainDN, SERVER_ID_1, 1, cryptoSuite);

      // put server id 1 offline twice
      CSNGenerator csnGenerator = new CSNGenerator(SERVER_ID_1, 100);
      environment.notifyReplicaOffline(domainDN, csnGenerator.newCSN());
      CSN lastOfflineCSN = csnGenerator.newCSN();
      environment.notifyReplicaOffline(domainDN, lastOfflineCSN);

      final ChangelogState state = environment.readOnDiskChangelogState();
      assertThat(state.getOfflineReplicas().getSnapshot())
          .containsExactly(MapEntry.entry(domainDN, Arrays.asList(lastOfflineCSN)));
      assertThat(state.isEqualTo(environment.getChangelogState())).isTrue();
    }
    finally
    {
      StaticUtils.close(cnDB, replicaDB);
    }
  }

  @Test
  public void testReadChangelogStateWithReplicaOfflineThenReplicaOnline() throws Exception
  {
    Log<Long,ChangeNumberIndexRecord> cnDB = null;
    Log<CSN,UpdateMsg> replicaDB = null;
    try
    {
      final File rootPath = new File(TEST_DIRECTORY_CHANGELOG);
      final DN domainDN = DN.valueOf(DN1_AS_STRING);

      ReplicationEnvironment environment = createReplicationEnv(rootPath);
      cnDB = environment.getOrCreateCNIndexDB();
      replicaDB = environment.getOrCreateReplicaDB(domainDN, SERVER_ID_1, 1, cryptoSuite);

      // put server id 1 offline
      environment.notifyReplicaOffline(domainDN, new CSN(TimeThread.getTime(), 0, SERVER_ID_1));
      // put server id 1 online again
      environment.notifyReplicaOnline(domainDN, SERVER_ID_1);

      final ChangelogState state = environment.readOnDiskChangelogState();
      assertThat(state.getOfflineReplicas()).isEmpty();
      assertThat(state.isEqualTo(environment.getChangelogState())).isTrue();
    }
    finally
    {
      StaticUtils.close(cnDB, replicaDB);
    }
  }

  @Test
  public void testCreateThenReadChangelogStateWithReplicaOffline() throws Exception
  {
    Log<Long,ChangeNumberIndexRecord> cnDB = null;
    Log<CSN,UpdateMsg> replicaDB = null;
    try
    {
      final File rootPath = new File(TEST_DIRECTORY_CHANGELOG);
      final DN domainDN = DN.valueOf(DN1_AS_STRING);

      ReplicationEnvironment environment = createReplicationEnv(rootPath);
      cnDB = environment.getOrCreateCNIndexDB();
      replicaDB = environment.getOrCreateReplicaDB(domainDN, SERVER_ID_1, 1, cryptoSuite);
      CSN offlineCSN = new CSN(TimeThread.getTime(), 0, SERVER_ID_1);
      environment.notifyReplicaOffline(domainDN, offlineCSN);

      final ChangelogState state = environment.readOnDiskChangelogState();

      assertThat(state.getDomainToServerIds()).containsKeys(domainDN);
      assertThat(state.getDomainToServerIds().get(domainDN)).containsOnly(SERVER_ID_1);
      assertThat(state.getDomainToGenerationId()).containsExactly(MapEntry.entry(domainDN, 1L));
      assertThat(state.getOfflineReplicas().getSnapshot())
          .containsExactly(MapEntry.entry(domainDN, Arrays.asList(offlineCSN)));

      assertThat(state.isEqualTo(environment.getChangelogState())).isTrue();
    }
    finally
    {
      StaticUtils.close(cnDB, replicaDB);
    }
  }

  @Test(expectedExceptions=ChangelogException.class)
  public void testMissingDomainDirectory() throws Exception
  {
    Log<Long,ChangeNumberIndexRecord> cnDB = null;
    Log<CSN,UpdateMsg> replicaDB = null, replicaDB2 = null;
    try
    {
      File rootPath = new File(TEST_DIRECTORY_CHANGELOG);
      DN domainDN = DN.valueOf(DN1_AS_STRING);
      ReplicationEnvironment environment = createReplicationEnv(rootPath);
      replicaDB = environment.getOrCreateReplicaDB(domainDN, SERVER_ID_1, 1, cryptoSuite);
      replicaDB2 = environment.getOrCreateReplicaDB(domainDN, SERVER_ID_2, 1, cryptoSuite);

      // delete the domain directory created for the 2 replica DBs to break the
      // consistency with domain state file
      StaticUtils.recursiveDelete(new File(rootPath, "1.dom"));

      environment.readOnDiskChangelogState();
    }
    finally
    {
      StaticUtils.close(cnDB, replicaDB, replicaDB2);
    }
  }

  private ReplicationEnvironment createReplicationEnv(File rootPath) throws ChangelogException
  {
    ReplicationServer unusedReplicationServer = null;
    return new ReplicationEnvironment(rootPath.getAbsolutePath(), unusedReplicationServer, TimeService.SYSTEM);
  }

  @Test
  public void testLastRotationTimeRetrievalWithNoRotationFile() throws Exception
  {
    final File rootPath = new File(TEST_DIRECTORY_CHANGELOG);
    TimeService time = mock(TimeService.class);
    when(time.now()).thenReturn(100L);
    ReplicationEnvironment environment = new ReplicationEnvironment(rootPath.getAbsolutePath(), null, time);

    assertThat(environment.getCnIndexDBLastRotationTime()).isEqualTo(100L);
  }

  @Test
  public void testLastRotationTimeRetrievalWithRotationFile() throws Exception
  {
    final File rootPath = new File(TEST_DIRECTORY_CHANGELOG);
    final TimeService time = mock(TimeService.class);
    when(time.now()).thenReturn(100L, 200L);
    ReplicationEnvironment environment = new ReplicationEnvironment(rootPath.getAbsolutePath(), null, time);
    Log<Long,ChangeNumberIndexRecord> cnIndexDB = environment.getOrCreateCNIndexDB();

    try {
      environment.notifyLogFileRotation(cnIndexDB);

      // check runtime change of last rotation time is effective
      // this should also persist the time in a file, but this is checked later in the test
      assertThat(environment.getCnIndexDBLastRotationTime()).isEqualTo(200L);
    }
    finally
    {
      cnIndexDB.close();
      environment.shutdown();
    }

    // now check last rotation time is correctly read from persisted file when re-creating environment
    when(time.now()).thenReturn(0L);
    environment = new ReplicationEnvironment(rootPath.getAbsolutePath(), null, time);
    assertThat(environment.getCnIndexDBLastRotationTime()).isEqualTo(200L);
  }
}
