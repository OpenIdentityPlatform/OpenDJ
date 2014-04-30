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
 *      Copyright 2013-2014 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.je;

import java.util.HashMap;
import java.util.Map;

import org.opends.server.DirectoryServerTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.forgerock.opendj.util.Pair;

import static com.forgerock.opendj.util.Pair.*;

import static org.testng.Assert.*;

@SuppressWarnings({ "javadoc", "unchecked" })
public class CompositeDBCursorTest extends DirectoryServerTestCase
{

  private UpdateMsg msg1;
  private UpdateMsg msg2;
  private UpdateMsg msg3;
  private UpdateMsg msg4;
  private String baseDN1 = "dc=forgerock,dc=com";
  private String baseDN2 = "dc=example,dc=com";

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
    final CompositeDBCursor<String> compCursor =
        newCompositeDBCursor(of(new SequentialDBCursor(), baseDN1));
    assertInOrder(compCursor);
  }

  @Test
  public void oneElementCursor() throws Exception
  {
    final CompositeDBCursor<String> compCursor =
        newCompositeDBCursor(of(new SequentialDBCursor(msg1), baseDN1));
    assertInOrder(compCursor, of(msg1, baseDN1));
  }

  @Test
  public void twoElementsCursor() throws Exception
  {
    final CompositeDBCursor<String> compCursor =
        newCompositeDBCursor(of(new SequentialDBCursor(msg1, msg2), baseDN1));
    assertInOrder(compCursor,
        of(msg1, baseDN1),
        of(msg2, baseDN1));
  }

  @Test
  public void twoEmptyCursors() throws Exception
  {
    final CompositeDBCursor<String> compCursor = newCompositeDBCursor(
        of(new SequentialDBCursor(), baseDN1),
        of(new SequentialDBCursor(), baseDN2));
    assertInOrder(compCursor);
  }

  @Test
  public void twoOneElementCursors() throws Exception
  {
    final CompositeDBCursor<String> compCursor = newCompositeDBCursor(
        of(new SequentialDBCursor(msg2), baseDN1),
        of(new SequentialDBCursor(msg1), baseDN2));
    assertInOrder(compCursor,
        of(msg1, baseDN2),
        of(msg2, baseDN1));
  }

  @Test
  public void twoTwoElementCursors() throws Exception
  {
    final CompositeDBCursor<String> compCursor = newCompositeDBCursor(
        of(new SequentialDBCursor(msg2, msg3), baseDN1),
        of(new SequentialDBCursor(msg1, msg4), baseDN2));
    assertInOrder(compCursor,
        of(msg1, baseDN2),
        of(msg2, baseDN1),
        of(msg3, baseDN1),
        of(msg4, baseDN2));
  }

  @Test
  public void recycleTwoElementCursors() throws Exception
  {
    final CompositeDBCursor<String> compCursor = newCompositeDBCursor(
        of(new SequentialDBCursor(msg2, null, msg4), baseDN1),
        of(new SequentialDBCursor(null, msg1, msg3), baseDN2));
    assertInOrder(compCursor,
        of(msg1, baseDN2),
        of(msg2, baseDN1),
        of(msg3, baseDN2),
        of(msg4, baseDN1));
  }

  private UpdateMsg newUpdateMsg(final int t)
  {
    return new UpdateMsg(new CSN(t, t, t), new byte[t])
    {
      /** {@inheritDoc} */
      @Override
      public String toString()
      {
        return "UpdateMsg(" + t + ")";
      }
    };
  }

  private CompositeDBCursor<String> newCompositeDBCursor(
      Pair<? extends DBCursor<UpdateMsg>, String>... pairs) throws Exception
  {
    final Map<DBCursor<UpdateMsg>, String> cursorsMap =
        new HashMap<DBCursor<UpdateMsg>, String>();
    for (Pair<? extends DBCursor<UpdateMsg>, String> pair : pairs)
    {
      cursorsMap.put(pair.getFirst(), pair.getSecond());
    }
    return new CompositeDBCursor<String>(cursorsMap, true);
  }

  private void assertInOrder(final CompositeDBCursor<String> compCursor,
      Pair<UpdateMsg, String>... expecteds) throws ChangelogException
  {
    for (final Pair<UpdateMsg, String> expected : expecteds)
    {
      assertTrue(compCursor.next());
      assertSame(compCursor.getRecord(), expected.getFirst());
      assertSame(compCursor.getData(), expected.getSecond());
    }
    assertFalse(compCursor.next());
    compCursor.close();
  }
}
