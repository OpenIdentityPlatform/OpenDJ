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

import static org.assertj.core.api.Assertions.*;

import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.protocol.ReplicaOfflineMsg;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

/** Test the {@link ReplicaCursor} class. */
@SuppressWarnings("javadoc")
public class ReplicaCursorTest extends ReplicationTestCase
{
  private int timestamp;
  private DBCursor<UpdateMsg> delegateCursor;

  @BeforeTest
  public void init()
  {
    timestamp = 1;
  }

  @Test
  public void cursorReturnsFalse() throws Exception
  {
    delegateCursor = new SequentialDBCursor();

    final ReplicaCursor cursor = newReplicaCursor(delegateCursor, null);
    assertThat(cursor.getRecord()).isNull();
    assertThat(cursor.next()).isFalse();
    assertThat(cursor.getRecord()).isNull();
  }

  @Test
  public void cursorReturnsTrue() throws Exception
  {
    final UpdateMsg updateMsg = new FakeUpdateMsg(timestamp++);
    delegateCursor = new SequentialDBCursor(updateMsg);

    final ReplicaCursor cursor = newReplicaCursor(delegateCursor, null);
    assertThat(cursor.getRecord()).isNull();
    assertThat(cursor.next()).isTrue();
    assertThat(cursor.getRecord()).isSameAs(updateMsg);
    assertThat(cursor.next()).isFalse();
    assertThat(cursor.getRecord()).isNull();
  }

  @Test
  public void cursorReturnsReplicaOfflineMsg() throws Exception
  {
    delegateCursor = new SequentialDBCursor();

    final CSN offlineCSN = new CSN(timestamp++, 1, 1);
    final ReplicaCursor cursor = newReplicaCursor(delegateCursor, offlineCSN);
    assertThat(cursor.getRecord()).isNull();
    assertThat(cursor.next()).isTrue();
    final UpdateMsg record = cursor.getRecord();
    assertThat(record).isInstanceOf(ReplicaOfflineMsg.class);
    assertThat(record.getCSN()).isEqualTo(offlineCSN);
    assertThat(cursor.next()).isFalse();
    assertThat(cursor.getRecord()).isNull();
  }

  @Test
  public void cursorReturnsUpdateMsgThenReplicaOfflineMsg() throws Exception
  {
    final UpdateMsg updateMsg = new FakeUpdateMsg(timestamp++);
    delegateCursor = new SequentialDBCursor(updateMsg);

    final CSN offlineCSN = new CSN(timestamp++, 1, 1);
    final ReplicaCursor cursor = newReplicaCursor(delegateCursor, offlineCSN);
    assertThat(cursor.getRecord()).isNull();
    assertThat(cursor.next()).isTrue();
    assertThat(cursor.getRecord()).isSameAs(updateMsg);
    assertThat(cursor.next()).isTrue();
    final UpdateMsg record = cursor.getRecord();
    assertThat(record).isInstanceOf(ReplicaOfflineMsg.class);
    assertThat(record.getCSN()).isEqualTo(offlineCSN);
    assertThat(cursor.next()).isFalse();
    assertThat(cursor.getRecord()).isNull();
  }

  @Test
  public void cursorReturnsUpdateMsgThenNeverReturnsOutdatedReplicaOfflineMsg() throws Exception
  {
    final CSN outdatedOfflineCSN = new CSN(timestamp++, 1, 1);

    final UpdateMsg updateMsg = new FakeUpdateMsg(timestamp++);
    delegateCursor = new SequentialDBCursor(updateMsg);

    final ReplicaCursor cursor = newReplicaCursor(delegateCursor, outdatedOfflineCSN);
    assertThat(cursor.getRecord()).isNull();
    assertThat(cursor.next()).isTrue();
    assertThat(cursor.getRecord()).isSameAs(updateMsg);
    assertThat(cursor.next()).isFalse();
    assertThat(cursor.getRecord()).isNull();
  }

  private ReplicaCursor newReplicaCursor(DBCursor<UpdateMsg> delegateCursor, CSN offlineCSN)
  {
    return new ReplicaCursor(delegateCursor, offlineCSN, null, null);
  }
}
