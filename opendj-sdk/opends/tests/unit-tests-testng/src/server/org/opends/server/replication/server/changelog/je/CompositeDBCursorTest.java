/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2013 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.je;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.opends.server.replication.common.CSN;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

@SuppressWarnings("javadoc")
public class CompositeDBCursorTest
{

  private static class MyDBCursor implements DBCursor<UpdateMsg>
  {

    private final List<UpdateMsg> msgs;
    private UpdateMsg current;

    public MyDBCursor(UpdateMsg... msgs)
    {
      this.msgs = new ArrayList<UpdateMsg>(Arrays.asList(msgs));
      next();
    }

    /** {@inheritDoc} */
    @Override
    public UpdateMsg getRecord()
    {
      return this.current;
    }

    /** {@inheritDoc} */
    @Override
    public boolean next()
    {
      if (!this.msgs.isEmpty())
      {
        this.current = this.msgs.remove(0);
        return true;
      }
      this.current = null;
      return false;
    }

    /** {@inheritDoc} */
    @Override
    public void close()
    {
      // nothing to do
    }

  }

  private UpdateMsg msg1;
  private UpdateMsg msg2;
  private UpdateMsg msg3;
  private UpdateMsg msg4;

  @BeforeClass
  public void setupMsgs()
  {
    msg1 = newUpdateMsg(1);
    msg2 = newUpdateMsg(2);
    msg3 = newUpdateMsg(3);
    msg4 = newUpdateMsg(4);
  }

  @Test
  public void emptyCursor() throws Exception
  {
    final CompositeDBCursor compCursor = newCompositeDBCursor(new MyDBCursor());
    assertInOrder(compCursor);
  }

  @Test
  public void oneElementCursor() throws Exception
  {
    final CompositeDBCursor compCursor =
        newCompositeDBCursor(new MyDBCursor(msg1));
    assertInOrder(compCursor, msg1);
  }

  @Test
  public void twoElementsCursor() throws Exception
  {
    final CompositeDBCursor compCursor =
        newCompositeDBCursor(new MyDBCursor(msg1, msg2));
    assertInOrder(compCursor, msg1, msg2);
  }

  @Test
  public void twoEmptyCursors() throws Exception
  {
    final CompositeDBCursor compCursor = newCompositeDBCursor(
        new MyDBCursor(),
        new MyDBCursor());
    assertInOrder(compCursor);
  }

  @Test
  public void twoOneElementCursors() throws Exception
  {
    final CompositeDBCursor compCursor = newCompositeDBCursor(
        new MyDBCursor(msg2),
        new MyDBCursor(msg1));
    assertInOrder(compCursor, msg1, msg2);
  }

  @Test
  public void twoTwoElementCursors() throws Exception
  {
    final CompositeDBCursor compCursor = newCompositeDBCursor(
        new MyDBCursor(msg2, msg3),
        new MyDBCursor(msg1, msg4));
    assertInOrder(compCursor, msg1, msg2, msg3, msg4);
  }

  @Test
  public void recycleTwoElementCursors() throws Exception
  {
    final CompositeDBCursor compCursor = newCompositeDBCursor(
        new MyDBCursor(msg2, null, msg3),
        new MyDBCursor(null, msg1, msg4));
    assertInOrder(compCursor, msg1, msg2, msg3, msg4);
  }

  private UpdateMsg newUpdateMsg(int t)
  {
    return new UpdateMsg(new CSN(t, t, t), new byte[t]);
  }

  private CompositeDBCursor newCompositeDBCursor(DBCursor<UpdateMsg>... cursors)
  {
    return new CompositeDBCursor(Arrays.asList(cursors));
  }

  private void assertInOrder(final CompositeDBCursor compCursor,
      UpdateMsg... msgs) throws ChangelogException
  {
    for (UpdateMsg msg : msgs)
    {
      assertTrue(compCursor.next());
      assertSame(compCursor.getRecord(), msg);
    }
    assertFalse(compCursor.next());
    compCursor.close();
  }
}
