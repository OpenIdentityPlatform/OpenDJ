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
package org.opends.server.replication.server.changelog.file;

import java.util.HashSet;
import java.util.Set;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.ReplicationDomainDB;
import org.opends.server.replication.server.changelog.api.DBCursor.CursorOptions;
import org.opends.server.replication.server.changelog.file.ECLEnabledDomainPredicate;
import org.opends.server.replication.server.changelog.file.ECLMultiDomainDBCursor;
import org.opends.server.replication.server.changelog.file.MultiDomainDBCursor;
import org.opends.server.types.DN;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.opends.server.replication.server.changelog.api.DBCursor.KeyMatchingStrategy.*;
import static org.opends.server.replication.server.changelog.api.DBCursor.PositionStrategy.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("javadoc")
public class ECLMultiDomainDBCursorTest extends DirectoryServerTestCase
{

  @Mock
  private ReplicationDomainDB domainDB;
  private CursorOptions options;
  private MultiDomainDBCursor multiDomainCursor;
  private ECLMultiDomainDBCursor eclCursor;
  private final Set<DN> eclEnabledDomains = new HashSet<>();
  private ECLEnabledDomainPredicate predicate = new ECLEnabledDomainPredicate()
  {
    @Override
    public boolean isECLEnabledDomain(DN baseDN)
    {
      return eclEnabledDomains.contains(baseDN);
    }
  };


  @BeforeMethod
  public void setup() throws Exception
  {
    TestCaseUtils.startFakeServer();
    MockitoAnnotations.initMocks(this);
    options = new CursorOptions(GREATER_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY);
    multiDomainCursor = new MultiDomainDBCursor(domainDB, options);
    eclCursor = new ECLMultiDomainDBCursor(predicate, multiDomainCursor);
  }

  @AfterMethod
  public void teardown() throws Exception
  {
    TestCaseUtils.shutdownFakeServer();
    domainDB = null;
    multiDomainCursor = null;
    eclCursor.close();
    eclCursor = null;
    eclEnabledDomains.clear();
  }

  @Test
  public void testEmptyCursor() throws Exception
  {
    assertEmpty();
  }

  @Test
  public void testECLDisabledDomainWithCursor() throws Exception
  {
    final DN baseDN = DN.valueOf("dc=example,dc=com");

    final UpdateMsg msg1 = new FakeUpdateMsg(1);
    addDomainCursorToCursor(baseDN, new SequentialDBCursor(msg1));

    assertEmpty();
  }

  @Test
  public void testECLEnabledDomainWithCursor() throws Exception
  {
    final DN baseDN = DN.valueOf("dc=example,dc=com");
    eclEnabledDomains.add(baseDN);

    final UpdateMsg msg1 = new FakeUpdateMsg(1);
    addDomainCursorToCursor(baseDN, new SequentialDBCursor(msg1));

    assertSingleMessage(baseDN, msg1);
  }

  @Test(dependsOnMethods = { "testECLEnabledDomainWithCursor", "testECLDisabledDomainWithCursor" })
  public void testECLEnabledAndDisabledDomainCursors() throws Exception
  {
    final DN baseDN1 = DN.valueOf("dc=example,dc=com");
    final DN baseDN2 = DN.valueOf("cn=admin data");
    eclEnabledDomains.add(baseDN1);

    final UpdateMsg msg1 = new FakeUpdateMsg(1);
    final UpdateMsg msg2 = new FakeUpdateMsg(2);
    final UpdateMsg msg3 = new FakeUpdateMsg(3);
    final UpdateMsg msg4 = new FakeUpdateMsg(4);
    addDomainCursorToCursor(baseDN1, new SequentialDBCursor(msg1, msg4));
    addDomainCursorToCursor(baseDN2, new SequentialDBCursor(msg2, msg3));

    assertMessagesInOrder(baseDN1, msg1, msg4);
  }

  private void assertEmpty() throws Exception
  {
    assertMessagesInOrder(null, null, null);
  }

  private void assertSingleMessage(DN baseDN, UpdateMsg msg1) throws Exception
  {
    assertMessagesInOrder(baseDN, msg1, null);
  }

  private void assertMessagesInOrder(DN baseDN, UpdateMsg msg1, UpdateMsg msg2) throws Exception
  {
    assertThat(eclCursor.getRecord()).isNull();
    assertThat(eclCursor.getData()).isNull();

    if (msg1 != null)
    {
      assertThat(eclCursor.next()).isTrue();
      assertThat(eclCursor.getRecord()).isEqualTo(msg1);
      assertThat(eclCursor.getData()).isEqualTo(baseDN);
    }
    if (msg2 != null)
    {
      assertThat(eclCursor.next()).isTrue();
      assertThat(eclCursor.getRecord()).isEqualTo(msg2);
      assertThat(eclCursor.getData()).isEqualTo(baseDN);
    }

    assertThat(eclCursor.next()).isFalse();
    assertThat(eclCursor.getRecord()).isNull();
    assertThat(eclCursor.getData()).isNull();
  }

  private void addDomainCursorToCursor(DN baseDN, SequentialDBCursor cursor) throws ChangelogException
  {
    final ServerState state = new ServerState();
    when(domainDB.getCursorFrom(baseDN, state, options)).thenReturn(cursor);
    multiDomainCursor.addDomain(baseDN, state);
  }
}
