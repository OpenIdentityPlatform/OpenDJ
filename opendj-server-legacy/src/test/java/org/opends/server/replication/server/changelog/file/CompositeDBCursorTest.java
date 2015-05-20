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
 *      Copyright 2013-2015 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.file;

import static org.forgerock.util.Pair.*;
import static org.testng.Assert.*;

import org.forgerock.util.Pair;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.opends.server.replication.server.changelog.file.CompositeDBCursor;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@SuppressWarnings({ "javadoc", "unchecked" })
public class CompositeDBCursorTest extends DirectoryServerTestCase
{

  private final class ConcreteCompositeDBCursor extends CompositeDBCursor<String>
  {
    @Override
    protected void incorporateNewCursors() throws ChangelogException
    {
    }
  }

  private UpdateMsg msg1;
  private UpdateMsg msg2;
  private UpdateMsg msg3;
  private UpdateMsg msg4;
  private UpdateMsg msg5;
  private UpdateMsg msg6;
  private String baseDN1 = "dc=forgerock,dc=com";
  private String baseDN2 = "dc=example,dc=com";

  @BeforeClass
  public void setupMsgs()
  {
    msg1 = new FakeUpdateMsg(1);
    msg2 = new FakeUpdateMsg(2);
    msg3 = new FakeUpdateMsg(3);
    msg4 = new FakeUpdateMsg(4);
    msg5 = new FakeUpdateMsg(5);
    msg6 = new FakeUpdateMsg(6);
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
  public void threeElementsCursor() throws Exception
  {
    final CompositeDBCursor<String> compCursor =
        newCompositeDBCursor(of(new SequentialDBCursor(msg1, msg2, msg3), baseDN1));
    assertInOrder(compCursor,
        of(msg1, baseDN1),
        of(msg2, baseDN1),
        of(msg3, baseDN1));
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
  public void twoThreeElementCursors() throws Exception
  {
    final CompositeDBCursor<String> compCursor = newCompositeDBCursor(
        of(new SequentialDBCursor(msg2, msg3, msg6), baseDN1),
        of(new SequentialDBCursor(msg1, msg4, msg5), baseDN2));
    assertInOrder(compCursor,
        of(msg1, baseDN2),
        of(msg2, baseDN1),
        of(msg3, baseDN1),
        of(msg4, baseDN2),
        of(msg5, baseDN2),
        of(msg6, baseDN1));
  }

  @Test
  public void recycleTwoElementsCursor() throws Exception
  {
    final CompositeDBCursor<String> compCursor = newCompositeDBCursor(
        of(new SequentialDBCursor(msg1, null, msg2), baseDN1));
    assertNextRecord(compCursor, of(msg1, baseDN1));
    assertFalse(compCursor.next());
    assertNextRecord(compCursor, of(msg2, baseDN1));
  }

  @Test
  public void recycleTwoElementsCursors() throws Exception
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

  @Test
  public void recycleTwoElementsCursorsLongerExhaustion() throws Exception
  {
    final CompositeDBCursor<String> compCursor = newCompositeDBCursor(
        of(new SequentialDBCursor(null, null, msg1), baseDN1),
        of(new SequentialDBCursor(msg2, msg3, msg4), baseDN2));
    assertInOrder(compCursor,
        of(msg2, baseDN2),
        of(msg1, baseDN1),
        of(msg3, baseDN2),
        of(msg4, baseDN2));
  }

  @Test
  public void recycleThreeElementsCursors() throws Exception
  {
    final CompositeDBCursor<String> compCursor = newCompositeDBCursor(
        of(new SequentialDBCursor(msg2, msg3, null, msg6), baseDN1),
        of(new SequentialDBCursor(null, msg1, null, msg4, msg5), baseDN2));
    assertInOrder(compCursor,
        of(msg1, baseDN2),
        of(msg2, baseDN1),
        of(msg3, baseDN1),
        of(msg4, baseDN2),
        of(msg5, baseDN2),
        of(msg6, baseDN1));
  }

  @Test
  public void recycleThreeElementsCursorsLongerExhaustion() throws Exception
  {
    final CompositeDBCursor<String> compCursor = newCompositeDBCursor(
        of(new SequentialDBCursor(msg2, msg3, null, msg6), baseDN1),
        of(new SequentialDBCursor(null, msg1, null, null, msg4, msg5), baseDN2));
    assertInOrder(compCursor,
        of(msg1, baseDN2),
        of(msg2, baseDN1),
        of(msg3, baseDN1),
        of(msg4, baseDN2),
        of(msg5, baseDN2),
        of(msg6, baseDN1));
  }

  private CompositeDBCursor<String> newCompositeDBCursor(
      Pair<? extends DBCursor<UpdateMsg>, String>... pairs) throws Exception
  {
    final CompositeDBCursor<String> cursor = new ConcreteCompositeDBCursor();
    for (Pair<? extends DBCursor<UpdateMsg>, String> pair : pairs)
    {
      cursor.addCursor(pair.getFirst(), pair.getSecond());
    }
    return cursor;
  }

  private void assertInOrder(final CompositeDBCursor<String> compCursor,
      Pair<UpdateMsg, String>... expecteds) throws ChangelogException
  {
    for (int i = 0; i < expecteds.length ; i++)
    {
      final Pair<UpdateMsg, String> expected = expecteds[i];
      final String index = " at element i=" + i;
      assertTrue(compCursor.next(), index);
      assertSame(compCursor.getRecord(), expected.getFirst(), index);
      assertSame(compCursor.getData(), expected.getSecond(), index);
    }
    assertFalse(compCursor.next());
    assertNull(compCursor.getRecord());
    assertNull(compCursor.getData());
    compCursor.close();
  }

  private void assertNextRecord(final CompositeDBCursor<String> compCursor,
      Pair<UpdateMsg, String> expected) throws ChangelogException
  {
    assertTrue(compCursor.next());
    assertSame(compCursor.getRecord(), expected.getFirst());
    assertSame(compCursor.getData(), expected.getSecond());
  }

}
