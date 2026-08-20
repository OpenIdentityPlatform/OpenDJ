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
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.backends.jdbc;

import org.forgerock.opendj.server.config.server.JDBCBackendCfg;
import org.mockito.InOrder;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.backends.jdbc.JDBCStorage.StatementBound;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLTimeoutException;
import java.util.concurrent.Executor;

import static org.forgerock.opendj.config.ConfigurationMock.mockCfg;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Which bound a statement of the JDBC backend is given, and what reaching it looks like to the
 * caller (#877). Needs no database: the statement is a mock, so the policy is pinned wherever the
 * build runs, while the container suites cover a statement really blocked on a lock.
 */
@SuppressWarnings("javadoc")
@Test(groups = { "precommit", "jdbc" }, sequential = true)
public class JDBCStatementBoundTestCase extends DirectoryServerTestCase {

	private JDBCStorage storage;

	@BeforeClass
	public void createStorage() {
		storage = new JDBCStorage(mockCfg(JDBCBackendCfg.class), null);
	}

	@AfterMethod
	public void clearProperties() {
		for (final StatementBound bound : StatementBound.values()) {
			System.clearProperty(bound.property);
		}
	}

	/**
	 * An entry read that has not come back in two minutes is stuck, while a count or the delete
	 * that empties a tree before an import legitimately takes longer than anything can guess - so
	 * the bulk class stays unbounded until a deployment says otherwise.
	 */
	@Test
	public void testDefaultsBoundAnOperationAndLeaveBulkAlone() throws Exception {
		assertEquals(StatementBound.OPERATION.seconds(), 120);
		assertEquals(StatementBound.BULK.seconds(), 0);
	}

	@Test
	public void testEachClassIsConfiguredByItsOwnProperty() throws Exception {
		System.setProperty(StatementBound.OPERATION.property, "7");
		assertEquals(StatementBound.OPERATION.seconds(), 7);
		assertEquals(StatementBound.BULK.seconds(), 0, "the bulk class followed the operation one");

		System.setProperty(StatementBound.BULK.property, "900");
		assertEquals(StatementBound.BULK.seconds(), 900);
		assertEquals(StatementBound.OPERATION.seconds(), 7);
	}

	@Test
	public void testAValueThatIsNoBoundLeavesTheStatementUnbounded() throws Exception {
		System.setProperty(StatementBound.OPERATION.property, "0");
		assertEquals(StatementBound.OPERATION.seconds(), 0);

		System.setProperty(StatementBound.OPERATION.property, "-1");
		assertEquals(StatementBound.OPERATION.seconds(), 0);
	}

	/**
	 * A value that is not a number is not a way to switch the bound off: it is ignored in favour
	 * of the default, as {@code Integer.getInteger()} has it, so a typo leaves the class bounded
	 * rather than silently unbounding it.
	 */
	@Test
	public void testAValueThatIsNotANumberFallsBackToTheDefault() throws Exception {
		System.setProperty(StatementBound.OPERATION.property, "two minutes");
		assertEquals(StatementBound.OPERATION.seconds(), 120);

		System.setProperty(StatementBound.BULK.property, "as long as it takes");
		assertEquals(StatementBound.BULK.seconds(), 0);
	}

	@Test
	public void testTheBoundReachesTheStatement() throws Exception {
		System.setProperty(StatementBound.OPERATION.property, "7");
		final PreparedStatement statement = mock(PreparedStatement.class);

		storage.execute(statement);

		verify(statement).setQueryTimeout(7);
	}

	/** An unbounded class costs no call of its own: a fresh statement is unbounded already. */
	@Test
	public void testAnUnboundedClassSetsNothing() throws Exception {
		final PreparedStatement statement = mock(PreparedStatement.class);

		storage.execute(statement, StatementBound.BULK);

		verify(statement, never()).setQueryTimeout(anyInt());
	}

	/**
	 * Behind the cancel is a socket read timeout, for the databases that do not act on a cancel:
	 * it is armed for the statement and put back afterwards, so a bulk statement sharing the
	 * connection is not cut by the bound of an entry read.
	 */
	@Test
	public void testTheBackstopIsArmedAndPutBack() throws Exception {
		System.setProperty(StatementBound.OPERATION.property, "7");
		final Connection con = mock(Connection.class);
		when(con.getNetworkTimeout()).thenReturn(0); // no bound of its own
		final PreparedStatement statement = mock(PreparedStatement.class);
		when(statement.getConnection()).thenReturn(con);

		storage.execute(statement);

		final InOrder inOrder = inOrder(con);
		inOrder.verify(con).setNetworkTimeout(any(Executor.class), eq((7 + JDBCStorage.BACKSTOP_MARGIN_SECONDS) * 1000));
		inOrder.verify(con).setNetworkTimeout(any(Executor.class), eq(0));
	}

	/**
	 * The backstop only ever tightens. A read timeout a deployment gave its connections is the
	 * bound it asked for, and this one - deliberately the looser of the two, so that the cancel
	 * has room to arrive first - must not stand in for it while a statement runs.
	 */
	@Test
	public void testTheBackstopDoesNotLoosenATighterBound() throws Exception {
		System.setProperty(StatementBound.OPERATION.property, "7");
		final Connection con = mock(Connection.class);
		when(con.getNetworkTimeout()).thenReturn(5000); // tighter than 7s plus the margin
		final PreparedStatement statement = mock(PreparedStatement.class);
		when(statement.getConnection()).thenReturn(con);

		storage.execute(statement);

		verify(con, never()).setNetworkTimeout(any(Executor.class), anyInt());
	}

	/**
	 * A failure that arrives before the bound is the caller's to classify and must reach it as it
	 * stands: a lock wait reported in class 40 is the conflict {@code JDBCStorage.write()} replays,
	 * and wrapping it would take it out of that class.
	 */
	@Test
	public void testAFailureInsideTheBoundIsPassedThrough() throws Exception {
		System.setProperty(StatementBound.OPERATION.property, "60");
		final SQLException conflict = new SQLException("lock wait timeout exceeded", "40001", 1205);
		final PreparedStatement statement = mock(PreparedStatement.class);
		when(statement.executeUpdate()).thenThrow(conflict);

		try {
			storage.execute(statement);
			fail("the failure of the statement must reach the caller");
		} catch (SQLException e) {
			assertSame(e, conflict);
		}
	}

	/**
	 * A failure that arrives at the bound names the property that produced it - every driver
	 * reports a cancelled statement differently, and none of them knows why it was cancelled - and
	 * still carries the SQL state and the error number of the failure it replaces.
	 */
	@Test
	public void testAFailureAtTheBoundNamesTheProperty() throws Exception {
		System.setProperty(StatementBound.OPERATION.property, "1");
		final PreparedStatement statement = mock(PreparedStatement.class);
		when(statement.executeUpdate()).thenAnswer(new Answer<Integer>() {
			@Override
			public Integer answer(InvocationOnMock invocation) throws Throwable {
				Thread.sleep(1100); // the driver cancelled it at the bound this test set
				throw new SQLException("canceling statement due to user request", "57014", 0);
			}
		});

		try {
			storage.execute(statement);
			fail("the failure of the statement must reach the caller");
		} catch (SQLTimeoutException e) {
			assertEquals(e.getSQLState(), "57014");
			assertTrue(e.getMessage().contains(StatementBound.OPERATION.property), e.getMessage());
			assertEquals(((SQLException) e.getCause()).getSQLState(), "57014");
		}
	}

	/**
	 * The rows are read while the bound is still armed. A driver hands them over as they are asked
	 * for - oracle prefetches ten at a time, mssql buffers adaptively - so a drain that happened
	 * after the bound was released would be a wait with nothing bounding it, which is the hang
	 * #877 is about rather than a detail of where the call sits.
	 */
	@Test
	public void testTheRowsAreReadWhileTheBoundIsStillArmed() throws Exception {
		System.setProperty(StatementBound.OPERATION.property, "7");
		final Connection con = mock(Connection.class);
		when(con.getNetworkTimeout()).thenReturn(0);
		final ResultSet rows = mock(ResultSet.class);
		final PreparedStatement statement = mock(PreparedStatement.class);
		when(statement.getConnection()).thenReturn(con);
		when(statement.executeQuery()).thenReturn(rows);

		assertEquals(storage.executeResultSet(statement, ResultSet::next), Boolean.FALSE);

		final InOrder inOrder = inOrder(con, rows);
		inOrder.verify(con).setNetworkTimeout(any(Executor.class), eq((7 + JDBCStorage.BACKSTOP_MARGIN_SECONDS) * 1000));
		inOrder.verify(rows).next();
		inOrder.verify(rows).close(); // the rows are done with before the backstop goes back
		inOrder.verify(con).setNetworkTimeout(any(Executor.class), eq(0));
	}

	/** A transfer of rows cut at the bound names the property that cut it, as an execution does. */
	@Test
	public void testAFailureWhileTheRowsAreReadIsMeasuredAgainstTheBound() throws Exception {
		System.setProperty(StatementBound.OPERATION.property, "1");
		final ResultSet rows = mock(ResultSet.class);
		when(rows.next()).thenAnswer(new Answer<Boolean>() {
			@Override
			public Boolean answer(InvocationOnMock invocation) throws Throwable {
				Thread.sleep(1100); // the driver cancelled the transfer at the bound this test set
				throw new SQLException("canceling statement due to user request", "57014", 0);
			}
		});
		final PreparedStatement statement = mock(PreparedStatement.class);
		when(statement.executeQuery()).thenReturn(rows);

		try {
			storage.executeResultSet(statement, ResultSet::next);
			fail("the failure of the transfer must reach the caller");
		} catch (SQLTimeoutException e) {
			assertTrue(e.getMessage().contains(StatementBound.OPERATION.property), e.getMessage());
			assertEquals(e.getSQLState(), "57014");
		}
	}

	/**
	 * A driver is allowed to have no query timeout at all, and this backend takes whatever URL a
	 * deployment configures. Such a driver has to keep working, with the socket read timeout as
	 * its whole bound, rather than fail every statement it is given.
	 */
	@Test
	public void testADriverWithoutAQueryTimeoutKeepsWorkingUnderTheBackstop() throws Exception {
		System.setProperty(StatementBound.OPERATION.property, "7");
		final Connection con = mock(Connection.class);
		when(con.getNetworkTimeout()).thenReturn(0);
		final PreparedStatement statement = mock(PreparedStatement.class);
		when(statement.getConnection()).thenReturn(con);
		doThrow(new SQLFeatureNotSupportedException("no query timeout")).when(statement).setQueryTimeout(anyInt());
		when(statement.executeUpdate()).thenReturn(1);

		assertEquals(storage.execute(statement), 1);

		verify(con).setNetworkTimeout(any(Executor.class), eq((7 + JDBCStorage.BACKSTOP_MARGIN_SECONDS) * 1000));
	}

	/**
	 * The catalog lookups of {@code openTree()} are bounded too. {@code DatabaseMetaData} takes no
	 * query timeout, so the socket read timeout behind the cancel is the only layer they can be
	 * given - and they run once per tree on every open of a backend, behind the same locks as the
	 * {@code create table} they guard.
	 */
	@Test
	public void testACatalogLookupIsBoundedByTheBackstopAlone() throws Exception {
		System.setProperty(StatementBound.OPERATION.property, "7");
		final Connection con = mock(Connection.class);
		when(con.getNetworkTimeout()).thenReturn(0);

		assertEquals(storage.bounded(con, StatementBound.OPERATION, () -> "asked the catalog"), "asked the catalog");

		final InOrder inOrder = inOrder(con);
		inOrder.verify(con).setNetworkTimeout(any(Executor.class), eq((7 + JDBCStorage.BACKSTOP_MARGIN_SECONDS) * 1000));
		inOrder.verify(con).setNetworkTimeout(any(Executor.class), eq(0));
	}

	/**
	 * Which class a batch of a cursor belongs to follows what the database has to do to answer it.
	 * {@code positionToLastKey()} has no key to seek on, so it is an {@code order by k desc} over
	 * the whole table - a scan and a sort of it on mssql, where {@code k} cannot be an index key -
	 * and every open of a backend runs it once per base DN through
	 * {@code EntryContainer.getHighestEntryID()}, outside the try/catch of
	 * {@code BackendImpl.openBackend()}. Bounding that as an entry read would turn a large backend
	 * that opens slowly into one that does not open at all, while the batches the cursor walks
	 * along its index stay operations and keep the bound of one.
	 */
	@Test
	public void testTheScanBehindTheHighestEntryIdIsBulkAndTheBatchesOfACursorAreNot() throws Exception {
		System.setProperty(StatementBound.OPERATION.property, "7");
		System.setProperty(StatementBound.BULK.property, "0");
		final PreparedStatement statement = mock(PreparedStatement.class);
		when(statement.executeQuery()).thenReturn(mock(ResultSet.class));
		final Connection parent = mock(Connection.class);
		when(parent.prepareStatement(anyString())).thenReturn(statement);
		final JDBCStorage.CursorImpl cursor =
			storage.new CursorImpl(true, new CachedConnection("jdbc:mock", parent), new TreeName("dc=example,dc=com", "id2entry"));

		cursor.positionToLastKey();
		verify(statement, never()).setQueryTimeout(anyInt());

		cursor.next();
		verify(statement).setQueryTimeout(7);
	}
}
