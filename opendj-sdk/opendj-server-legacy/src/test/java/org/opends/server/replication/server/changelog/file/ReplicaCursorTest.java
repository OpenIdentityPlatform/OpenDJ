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

import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.protocol.ReplicaOfflineMsg;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.opends.server.replication.server.changelog.file.ReplicaCursor;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Test the {@link ReplicaCursor} class.
 */
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
