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

import java.util.HashSet;
import java.util.Set;

import org.forgerock.opendj.ldap.DN;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor.CursorOptions;
import org.opends.server.replication.server.changelog.api.ReplicationDomainDB;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.opends.server.replication.server.changelog.api.DBCursor.KeyMatchingStrategy.*;
import static org.opends.server.replication.server.changelog.api.DBCursor.PositionStrategy.*;

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
    MockitoAnnotations.initMocks(this);
    options = new CursorOptions(GREATER_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY);
    multiDomainCursor = new MultiDomainDBCursor(domainDB, options);
    eclCursor = new ECLMultiDomainDBCursor(predicate, multiDomainCursor);
  }

  @AfterMethod
  public void teardown() throws Exception
  {
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
    final UpdateMsg msgs[] = newUpdateMsgs(13);

    // At least two updates in an enabled domain
    addDomainCursorToCursor(baseDN1, new SequentialDBCursor(msgs[0], msgs[3]));
    addDomainCursorToCursor(baseDN2, new SequentialDBCursor(msgs[1], msgs[2]));

    assertMessagesInOrder(baseDN1, msgs[0], msgs[3]);
    assertEmpty();

    //Only one update in an enabled domain
    addDomainCursorToCursor(baseDN1, new SequentialDBCursor(msgs[4]));
    addDomainCursorToCursor(baseDN2, new SequentialDBCursor(msgs[5], msgs[6]));

    assertMessagesInOrder(baseDN1, msgs[4], null);
    assertEmpty();

    // Two disabled domains
    final DN baseDN3 = DN.valueOf("dc=example,dc=net");

    addDomainCursorToCursor(baseDN1, new SequentialDBCursor(msgs[7], msgs[9]));
    addDomainCursorToCursor(baseDN2, new SequentialDBCursor(msgs[8], msgs[10]));
    addDomainCursorToCursor(baseDN3, new SequentialDBCursor(msgs[11], msgs[12]));

    assertMessagesInOrder(baseDN1, msgs[7], msgs[9]);
    assertEmpty();

    // Test disable/enable domain tracking
    eclEnabledDomains.add(baseDN3);
    assertThat(eclCursor.shouldReInitialize()).isTrue();
    assertThat(eclCursor.shouldReInitialize()).isFalse();
  }

  private void assertEmpty() throws Exception
  {
    assertMessagesInOrder(null, null, null);
  }

  private void assertSingleMessage(DN baseDN, UpdateMsg msg1) throws Exception
  {
    assertMessagesInOrder(baseDN, msg1, null);
  }

  private UpdateMsg[] newUpdateMsgs(int num)
  {
    UpdateMsg[] results = new UpdateMsg[num];
    for (int i = 0; i < num; i++)
    {
      results[i] = new FakeUpdateMsg(i + 1);
    }
    return results;
  }

  private void assertMessagesInOrder(DN baseDN, UpdateMsg msg1, UpdateMsg msg2) throws Exception
  {
    assertThat(eclCursor.getRecord()).isNull();
    assertThat((Object) eclCursor.getData()).isNull();

    if (msg1 != null)
    {
      assertThat(eclCursor.next()).isTrue();
      assertThat(eclCursor.getRecord()).isEqualTo(msg1);
      assertThat((Object) eclCursor.getData()).isEqualTo(baseDN);
    }
    if (msg2 != null)
    {
      assertThat(eclCursor.next()).isTrue();
      assertThat(eclCursor.getRecord()).isEqualTo(msg2);
      assertThat((Object) eclCursor.getData()).isEqualTo(baseDN);
    }

    assertThat(eclCursor.next()).isFalse();
    assertThat(eclCursor.getRecord()).isNull();
    assertThat((Object) eclCursor.getData()).isNull();
  }

  private void addDomainCursorToCursor(DN baseDN, SequentialDBCursor cursor) throws ChangelogException
  {
    final ServerState state = new ServerState();
    when(domainDB.getCursorFrom(baseDN, state, options)).thenReturn(cursor);
    multiDomainCursor.addDomain(baseDN, state);
  }
}
