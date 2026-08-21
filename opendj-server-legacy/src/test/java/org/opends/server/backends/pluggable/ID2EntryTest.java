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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.backends.pluggable;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
@Test(groups = { "precommit", "pluggablebackend" }, sequential = true)
public class ID2EntryTest extends DirectoryServerTestCase
{
  /**
   * The read that checks the tree is there when a backend opens asks for a bulk cursor. Its first
   * batch carries no key to seek on, so a storage engine sees a walk of the whole tree - on the
   * JDBC backend against SQL Server, a scan and a sort of it, {@code k} being a
   * {@code varbinary(max)} that cannot be an index key - and this runs once per base DN on every
   * open, outside the try/catch of {@code BackendImpl.openBackend()}. Bounded as the work of a
   * client operation, a large backend would stop opening at all (#877).
   */
  @Test
  public void testTheReadThatOpensTheTreeAsksForABulkCursor() throws Exception
  {
    final TreeName name = new TreeName("dc=example,dc=com", "id2entry");
    final WriteableTransaction txn = mock(WriteableTransaction.class);
    @SuppressWarnings("unchecked")
    final Cursor<ByteString, ByteString> cursor = mock(Cursor.class);
    when(txn.openBulkCursor(name)).thenReturn(cursor);

    new ID2Entry(name, new DataConfig.Builder().build()).open(txn, false);

    verify(txn).openBulkCursor(name);
    verify(cursor).next();
  }
}
