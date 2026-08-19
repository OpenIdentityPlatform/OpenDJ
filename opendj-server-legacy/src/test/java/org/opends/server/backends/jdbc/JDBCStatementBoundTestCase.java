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
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.concurrent.Executor;

import static org.forgerock.opendj.config.ConfigurationMock.mockCfg;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
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

		System.setProperty(StatementBound.OPERATION.property, "two minutes");
		assertEquals(StatementBound.OPERATION.seconds(), 120, "a value that is not a number must fall back to the default");
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
}
