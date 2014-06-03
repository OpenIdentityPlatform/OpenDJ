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
 *      Copyright 2014 ForgeRock AS.
 */
package org.opends.server.replication.server.changelog.file;

import static org.assertj.core.api.Assertions.*;
import static org.opends.server.replication.server.changelog.file.ReplicationEnvironment.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.assertj.core.data.MapEntry;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.ChangelogState;
import org.opends.server.replication.server.changelog.api.ChangeNumberIndexRecord;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.types.DN;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.TimeThread;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class ReplicationEnvironmentTest extends DirectoryServerTestCase
{
  private static final int SERVER_ID_1 = 1;
  private static final int SERVER_ID_2 = 2;

  private static final String DN1_AS_STRING = "cn=test1,dc=company.com";
  private static final String DN2_AS_STRING = "cn=te::st2,dc=company.com";
  private static final String DN3_AS_STRING = "cn=test3,dc=company.com";

  private static final String TEST_DIRECTORY_CHANGELOG = "test-output/changelog";

  @BeforeClass
  public void setUp() throws Exception
  {
    // This test suite depends on having the schema available for DN decoding.
    TestCaseUtils.startServer();
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
      final DN domainDN = DN.decode(DN1_AS_STRING);
      ReplicationEnvironment environment = new ReplicationEnvironment(rootPath.getAbsolutePath(), null);
      cnDB = environment.getOrCreateCNIndexDB();
      replicaDB = environment.getOrCreateReplicaDB(domainDN, SERVER_ID_1, 1);
      replicaDB2 = environment.getOrCreateReplicaDB(domainDN, SERVER_ID_2, 1);

      ChangelogState state = environment.readChangelogState();

      assertThat(state.getDomainToServerIds()).containsKeys(domainDN);
      assertThat(state.getDomainToServerIds().get(domainDN)).containsOnly(SERVER_ID_1, SERVER_ID_2);
      assertThat(state.getDomainToGenerationId()).containsExactly(MapEntry.entry(domainDN, 1L));
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
    List<Log<CSN,UpdateMsg>> replicaDBs = new ArrayList<Log<CSN,UpdateMsg>>();
    try
    {
      File rootPath = new File(TEST_DIRECTORY_CHANGELOG);
      List<DN> domainDNs = Arrays.asList(DN.decode(DN1_AS_STRING), DN.decode(DN2_AS_STRING), DN.decode(DN3_AS_STRING));
      ReplicationEnvironment environment = new ReplicationEnvironment(rootPath.getAbsolutePath(), null);
      cnDB = environment.getOrCreateCNIndexDB();
      for (int i = 0; i <= 2 ; i++)
      {
        for (int j = 1; j <= 10; j++)
        {
          // 3 domains, 10 server id each, generation id is different for each domain
          replicaDBs.add(environment.getOrCreateReplicaDB(domainDNs.get(i), j, i+1));
        }
      }

      ChangelogState state = environment.readChangelogState();

      assertThat(state.getDomainToServerIds()).containsKeys(domainDNs.get(0), domainDNs.get(1), domainDNs.get(2));
      for (int i = 0; i <= 2 ; i++)
      {
        assertThat(state.getDomainToServerIds().get(domainDNs.get(i))).containsOnly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
      }
      assertThat(state.getDomainToGenerationId()).containsOnly(
          MapEntry.entry(domainDNs.get(0), 1L),
          MapEntry.entry(domainDNs.get(1), 2L),
          MapEntry.entry(domainDNs.get(2), 3L));
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
      final DN domainDN = DN.decode(DN1_AS_STRING);
      ReplicationEnvironment environment = new ReplicationEnvironment(rootPath.getAbsolutePath(), null);
      cnDB = environment.getOrCreateCNIndexDB();
      replicaDB = environment.getOrCreateReplicaDB(domainDN, SERVER_ID_1, 1);

      // put server id 1 offline
      CSN offlineCSN = new CSN(TimeThread.getTime(), 0, SERVER_ID_1);
      environment.notifyReplicaOffline(domainDN, offlineCSN);

      ChangelogState state = environment.readChangelogState();

      assertThat(state.getOfflineReplicas()).containsExactly(MapEntry.entry(domainDN, Arrays.asList(offlineCSN)));
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
      final DN domainDN = DN.decode(DN1_AS_STRING);
      ReplicationEnvironment environment = new ReplicationEnvironment(rootPath.getAbsolutePath(), null);
      cnDB = environment.getOrCreateCNIndexDB();
      replicaDB = environment.getOrCreateReplicaDB(domainDN, SERVER_ID_1, 1);

      File offlineStateFile = new File(environment.getServerIdPath("1", 1), REPLICA_OFFLINE_STATE_FILENAME);
      offlineStateFile.createNewFile();

      environment.readChangelogState();
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
      final DN domainDN = DN.decode(DN1_AS_STRING);

      ReplicationEnvironment environment = new ReplicationEnvironment(rootPath.getAbsolutePath(), null);
      cnDB = environment.getOrCreateCNIndexDB();
      replicaDB = environment.getOrCreateReplicaDB(domainDN, SERVER_ID_1, 1);

      // put server id 1 offline twice
      CSNGenerator csnGenerator = new CSNGenerator(SERVER_ID_1, 100);
      environment.notifyReplicaOffline(domainDN, csnGenerator.newCSN());
      CSN lastOfflineCSN = csnGenerator.newCSN();
      environment.notifyReplicaOffline(domainDN, lastOfflineCSN);

      ChangelogState state = environment.readChangelogState();

      assertThat(state.getOfflineReplicas()).containsExactly(MapEntry.entry(domainDN, Arrays.asList(lastOfflineCSN)));
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
      final DN domainDN = DN.decode(DN1_AS_STRING);

      ReplicationEnvironment environment = new ReplicationEnvironment(rootPath.getAbsolutePath(), null);
      cnDB = environment.getOrCreateCNIndexDB();
      replicaDB = environment.getOrCreateReplicaDB(domainDN, SERVER_ID_1, 1);

      // put server id 1 offline
      environment.notifyReplicaOffline(domainDN, new CSN(TimeThread.getTime(), 0, SERVER_ID_1));
      // put server id 1 online again
      environment.notifyReplicaOnline(domainDN, SERVER_ID_1);

      ChangelogState state = environment.readChangelogState();

      assertThat(state.getOfflineReplicas()).isEmpty();
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
      final DN domainDN = DN.decode(DN1_AS_STRING);

      ReplicationEnvironment environment = new ReplicationEnvironment(rootPath.getAbsolutePath(), null);
      cnDB = environment.getOrCreateCNIndexDB();
      replicaDB = environment.getOrCreateReplicaDB(domainDN, SERVER_ID_1, 1);
      CSN offlineCSN = new CSN(TimeThread.getTime(), 0, SERVER_ID_1);
      environment.notifyReplicaOffline(domainDN, offlineCSN);

      ChangelogState state = environment.readChangelogState();

      assertThat(state.getDomainToServerIds()).containsKeys(domainDN);
      assertThat(state.getDomainToServerIds().get(domainDN)).containsOnly(SERVER_ID_1);
      assertThat(state.getDomainToGenerationId()).containsExactly(MapEntry.entry(domainDN, 1L));
      assertThat(state.getOfflineReplicas()).containsExactly(MapEntry.entry(domainDN, Arrays.asList(offlineCSN)));
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
      DN domainDN = DN.decode(DN1_AS_STRING);
      ReplicationEnvironment environment = new ReplicationEnvironment(rootPath.getAbsolutePath(), null);
      replicaDB = environment.getOrCreateReplicaDB(domainDN, SERVER_ID_1, 1);
      replicaDB2 = environment.getOrCreateReplicaDB(domainDN, SERVER_ID_2, 1);

      // delete the domain directory created for the 2 replica DBs to break the
      // consistency with domain state file
      StaticUtils.recursiveDelete(new File(rootPath, "1.domain"));

      environment.readChangelogState();
    }
    finally
    {
      StaticUtils.close(cnDB, replicaDB, replicaDB2);
    }
  }
}
