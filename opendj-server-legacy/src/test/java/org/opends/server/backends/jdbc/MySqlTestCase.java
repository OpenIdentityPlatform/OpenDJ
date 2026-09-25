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
 * Copyright 2025-2026 3A Systems, LLC.
 */
package org.opends.server.backends.jdbc;

import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.backends.pluggable.spi.AccessMode;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.WriteOperation;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

//docker run --rm --name mysql -p 3306:3306 -e MYSQL_DATABASE=database_name -e MYSQL_ROOT_PASSWORD=password mysql:latest

// sequential, as every suite declaring a test of its own is: TestListener asks it of the class a
// running test is declared by, and the inherited ones answer for the class that declares them
@Test(sequential = true)
public class MySqlTestCase extends TestCase {

    @Override
    protected JdbcDatabaseContainer<?> getContainer() {
        return new MySQLContainer<>("mysql:9.2")
                .withExposedPorts(3306)
                .withUsername("root")
                .withPassword("password")
                .withDatabaseName("database_name");
    }

    @Override
    protected String getContainerDockerCommand() {
        return "run before test: docker run --rm --name mysql -p 3306:3306 -e MYSQL_DATABASE=database_name -e MYSQL_ROOT_PASSWORD=password mysql:latest";
    }

    @Override
    protected String getBackendId() {
        return MySqlTestCase.class.getSimpleName();
    }

    @Override
    protected String getJdbcUrl() {
        return "jdbc:mysql://root:password@localhost:" + ((container==null)?"3306":container.getMappedPort(3306)) + "/database_name";
    }

    /**
     * The vendor code a per-account connection limit is classified by is the code the server sends
     * for it, and it is the whole of what this verdict can be made of: an account whose grant caps
     * its simultaneous connections refuses the next connect with 1226 in the syntax error class -
     * 42000, where a statement the database rejected lands - rather than in a connection class of
     * its own. The unit tests pin what the pool does with the code; this pins that the code is the
     * one arriving from a real server through the driver this backend ships with (#1011).
     */
    @Test(timeOut = 120000)
    public void testAPerAccountConnectionLimitIsWorthRetrying() throws Exception {
        final String url = getJdbcUrl();
        final String limited = url.replace("root:password", "limited1011:secret");
        // dropped first: a run this one was killed in the middle of leaves the account behind
        grant(url, "drop user if exists 'limited1011'@'%'",
            "create user 'limited1011'@'%' identified by 'secret' with max_user_connections 1",
            "grant all on database_name.* to 'limited1011'@'%'");
        try (final Connection held = DriverManager.getConnection(limited)) {
            assertTrue(held.isValid(CachedConnection.VALIDATION_TIMEOUT_SECONDS),
                "the account is refused its first connection already");
            try (final Connection second = DriverManager.getConnection(limited)) {
                fail("an account limited to one connection must be refused a second: " + second);
            } catch (SQLException refused) {
                assertEquals(refused.getErrorCode(), 1226,
                    "the server reports a per-account connection limit as: " + refused);
                // the resource is what tells this code from the ones no wait clears, and the server
                // writes it as a literal of its own: read here from the server rather than assumed
                assertTrue(refused.getMessage().contains("'max_user_connections'"),
                    "the server names the resource of the concurrent limit as: " + refused.getMessage());
                assertTrue(CachedConnection.isWorthRetrying(refused, CachedConnection.ConnectDialect.of(limited)),
                    "a borrow must wait a per-account limit out rather than fail on it: " + refused);
            }
        } finally {
            grant(url, "drop user if exists 'limited1011'@'%'");
        }
    }

    /**
     * The connections an account is granted per hour carry the same 1226 as the ones it may hold at
     * once, and only the top of the hour clears them: a borrow waiting one out would park a worker
     * thread in every attempt for as long as the hour lasts. What tells the two apart is the
     * resource the server names, and this pins that name against the server itself (#1011).
     */
    @Test(timeOut = 120000)
    public void testTheHourlyConnectionLimitIsNotWorthRetrying() throws Exception {
        final String url = getJdbcUrl();
        final String limited = url.replace("root:password", "perhour1011:secret");
        grant(url, "drop user if exists 'perhour1011'@'%'",
            "create user 'perhour1011'@'%' identified by 'secret' with max_connections_per_hour 1",
            "grant all on database_name.* to 'perhour1011'@'%'");
        try {
            try (final Connection spent = DriverManager.getConnection(limited)) {
                assertTrue(spent.isValid(CachedConnection.VALIDATION_TIMEOUT_SECONDS),
                    "the account is refused the one connection of its hour already");
            }
            try (final Connection second = DriverManager.getConnection(limited)) {
                fail("an account granted one connection an hour must be refused a second: " + second);
            } catch (SQLException refused) {
                assertEquals(refused.getErrorCode(), 1226,
                    "the server reports an hourly connection limit as: " + refused);
                assertTrue(refused.getMessage().contains("'max_connections_per_hour'"),
                    "the server names the hourly resource as: " + refused.getMessage());
                assertFalse(CachedConnection.isWorthRetrying(refused, CachedConnection.ConnectDialect.of(limited)),
                    "an hourly limit must be reported rather than waited out: " + refused);
            }
        } finally {
            grant(url, "drop user if exists 'perhour1011'@'%'");
        }
    }

    /**
     * The queries an account is granted per hour are named by the server without the _per_hour of
     * the GRANT keyword - max_questions - and they reach this gate on the road it guards: an
     * account whose quota is spent is refused the connect itself, Connector/J spending what is
     * left of the quota on the queries of its own login. Waited out, that would cost every borrow
     * and every catalog connect the whole deadline of the pool until the hour turned (#1011).
     */
    @Test(timeOut = 120000)
    public void testTheHourlyQueryLimitIsNotWorthRetrying() throws Exception {
        final String url = getJdbcUrl();
        final String limited = url.replace("root:password", "hourly1011:secret");
        grant(url, "drop user if exists 'hourly1011'@'%'",
            "create user 'hourly1011'@'%' identified by 'secret' with max_queries_per_hour 1",
            "grant all on database_name.* to 'hourly1011'@'%'");
        try {
            final SQLException refused = spendTheHourlyQueries(limited);
            assertEquals(refused.getErrorCode(), 1226,
                "the server reports an hourly query limit as: " + refused);
            assertTrue(refused.getMessage().contains("'max_questions'"),
                "the server names the hourly query resource as: " + refused.getMessage());
            assertFalse(CachedConnection.isWorthRetrying(refused, CachedConnection.ConnectDialect.of(limited)),
                "an hourly query limit must be reported rather than waited out: " + refused);
        } finally {
            grant(url, "drop user if exists 'hourly1011'@'%'");
        }
    }

    /**
     * Spends the hourly query quota of an account and hands back what the server refused it with.
     * The connect is attempted rather than one statement over a connection held open, since the
     * login of the driver spends the quota as readily as a statement does: whichever of the two
     * meets the limit, the failure is the one a borrow of this pool would catch.
     */
    private static SQLException spendTheHourlyQueries(String url) throws SQLException {
        for (int attempt = 0; attempt < 4; attempt++) {
            try (final Connection con = DriverManager.getConnection(url);
                 final Statement st = con.createStatement()) {
                st.execute("select 1");
            } catch (SQLException refused) {
                return refused;
            }
        }
        throw new AssertionError("an account granted one query an hour must be refused within four connects");
    }

    /** The database of the case below: a directory next to the one of this suite, on the same server. */
    private static final String NEIGHBOUR = "opendj_neighbour1075";

    @Override
    protected void dropStaleNeighbours() throws SQLException {
        grant(getJdbcUrl(), "drop database if exists " + NEIGHBOUR);
    }

    @DataProvider
    public Object[][] databaseTerms() {
        return new Object[][] {
            // the default of Connector/J: the database is the catalog, and the lookups are asked in it
            { "catalog", "" },
            // the database is the schema: the connection names no catalog, the lookups span the server,
            // and only the schema path read off the connection tells the neighbour apart
            { "schema", "?databaseTerm=SCHEMA" },
        };
    }

    /**
     * A table and an index of another database of this server answer for none of this backend's, however
     * the connection names its database (#1075) - the mysql twin of the postgres case of #902.
     * <p>
     * A table is named after its tree and an index after its table, so two directories on one server - the
     * stock backend id in two databases - hold the same table and the same index. Connector/J asked with no
     * catalog lists the tables of every database, and both guards of {@code openTree()} would then skip a
     * create this backend needs: the table one leaves every statement addressing a table that is not there,
     * and the index one - the quiet half - leaves the {@code where k>? order by k} batches of every cursor
     * a full scan for the life of the deployment.
     * <p>
     * Under the default {@code databaseTerm} the guards hold by two layers at once - the catalog they pass
     * and the database {@code TableScope.covers()} reads off every row - and this case goes red only when
     * both give way; the unit cases of {@code JDBCStorageRetryTest} pin each of them. Under
     * {@code databaseTerm=SCHEMA} the connection names no catalog and the schema path is the one layer
     * there is, which is what this case exists for: what it rests on is how the driver lists a table of
     * another database, and only a live server answers that.
     */
    @Test(dataProvider = "databaseTerms")
    public void testAnOpenIsAnsweredForByNoTableOfAnotherDatabase(String term, String urlOptions) throws Exception {
        final TreeName tree = new TreeName("testAnotherDatabase", "tree");
        final JDBCStorage storage =
            new JDBCStorage(createBackendCfg(getBackendId() + "_" + term, getJdbcUrl() + urlOptions), null);
        final String tableName = storage.getTableName(tree);
        final String indexName = "k_" + tableName.substring("opendj_".length());
        try {
            // the neighbouring directory: the same table and the same index, in a database this storage
            // reaches through no unqualified name of its own. Spelled out rather than opened by a storage,
            // so that the fixture is the collision and nothing else
            grant(getJdbcUrl(), "drop database if exists " + NEIGHBOUR,
                "create database " + NEIGHBOUR,
                "create table " + NEIGHBOUR + "." + tableName
                    + " (h char(128),k varbinary(255),v longblob,primary key(h,k))",
                "create index " + indexName + " on " + NEIGHBOUR + "." + tableName + " (k)");
            assertFalse(isExistsIn(DATABASE, tableName, null),
                "the case did not start with the table of this backend absent from its database");

            storage.open(AccessMode.READ_WRITE);
            storage.write(new WriteOperation() {
                @Override
                public void run(WriteableTransaction txn) throws Exception {
                    txn.openTree(tree, true);
                    // the destructive half of the table guard, and the reason it is loud: found abroad, the
                    // table is created nowhere and this statement addresses a table that is not there
                    txn.put(tree, ByteString.valueOfUtf8("a key of this backend"),
                        ByteString.valueOfUtf8("a value of this backend"));
                }
            });

            assertTrue(isExistsIn(DATABASE, tableName, null),
                "the open took the table of another database for its own and created none");
            assertTrue(isExistsIn(DATABASE, tableName, indexName),
                "the open took the index of another database for its own: the cursor batches of this tree are full scans behind it");
            assertEquals(rowCountIn(DATABASE, tableName), 1,
                "the write of this backend landed in a table other than the one the open made");
            assertEquals(rowCountIn(NEIGHBOUR, tableName), 0,
                "the write of this backend landed in the table of the neighbouring database");
        } finally {
            clearQuietly(storage);
            grant(getJdbcUrl(), "drop database if exists " + NEIGHBOUR);
        }
    }

    /** The database of the connections of this suite. */
    private static final String DATABASE = "database_name";

    /**
     * Whether that one database holds the table - or, given an index name, that index of it - asked of
     * information_schema by name rather than through the catalog lookups under test.
     */
    private boolean isExistsIn(String database, String tableName, String indexName) throws SQLException {
        final String query = indexName == null
            ? "select 1 from information_schema.tables where table_schema=? and table_name=?"
            : "select 1 from information_schema.statistics where table_schema=? and table_name=? and index_name=?";
        try (final Connection con = DriverManager.getConnection(getJdbcUrl());
             final PreparedStatement st = con.prepareStatement(query)) {
            st.setString(1, database);
            st.setString(2, tableName);
            if (indexName != null) {
                st.setString(3, indexName);
            }
            try (final ResultSet rs = st.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** What the table of that one database holds, which says which of the two tables a write went to. */
    private int rowCountIn(String database, String tableName) throws SQLException {
        try (final Connection con = DriverManager.getConnection(getJdbcUrl());
             final Statement st = con.createStatement();
             final ResultSet rs = st.executeQuery("select count(*) from " + database + "." + tableName)) {
            return rs.next() ? rs.getInt(1) : -1;
        }
    }

    /** The account of the test is made and unmade on the connection of the suite's own credentials. */
    private static void grant(String url, String... statements) throws SQLException {
        try (final Connection admin = DriverManager.getConnection(url);
             final Statement st = admin.createStatement()) {
            for (final String statement : statements) {
                st.execute(statement);
            }
        }
    }

}
