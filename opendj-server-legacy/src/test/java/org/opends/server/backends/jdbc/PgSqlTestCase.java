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

import org.opends.server.backends.pluggable.spi.AccessMode;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.WriteOperation;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

//docker run --rm -it -p 5432:5432 -e POSTGRES_PASSWORD=password --name postgres postgres

/**
 * The class-level annotation governs the cases declared here, and it has to carry
 * {@code sequential = true} of its own: {@code TestListener.enforceTestClassTypeAndAnnotations()}
 * looks it up on the class declaring the case rather than on the one running it, so the
 * {@code @Test(groups = ..., sequential = true)} of {@code PluggableBackendImplTestCase} answers for
 * the inherited cases alone and a bare {@code @Test} here fails every case this class declares.
 * {@code OracleTestCase} carries it for the same reason.
 */
@Test(sequential = true)
public class PgSqlTestCase extends TestCase {

    @Override
    protected JdbcDatabaseContainer<?> getContainer() {
        return new PostgreSQLContainer<>("postgres:latest")
                .withExposedPorts(5432)
                .withUsername("postgres")
                .withPassword("password")
                .withDatabaseName("database_name");
    }

    @Override
    protected String getContainerDockerCommand() {
        return "run before test: docker run --rm -it -p 5432:5432 -e POSTGRES_DB=database_name -e POSTGRES_PASSWORD=password --name postgres postgres";
    }

    @Override
    protected String getBackendId() {
        return PgSqlTestCase.class.getSimpleName();
    }

    @Override
    protected String getJdbcUrl() {
        return "jdbc:postgresql://localhost:"+ ((container==null)?"5432":container.getMappedPort(5432))+"/database_name?user=postgres&password=password";
    }

    /** The schema put ahead of the one this suite's tables are in, for the case below and for nothing else. */
    private static final String AHEAD_ON_THE_PATH = "opendj_ahead";

    /**
     * A table of this backend is found where an unqualified statement of the same connection reaches it, and
     * not only in the schema that connection happens to work in.
     * <p>
     * PostgreSQL resolves an unqualified reference across the whole {@code search_path} while an unqualified
     * {@code create} lands in {@code current_schema()} alone, so the two are the same schema only as long as
     * nothing was put in front of the one the tables were made in. Adding a schema of its own to a role is the
     * standard remedy since PG15 took {@code CREATE} off {@code public}, and it makes them differ on an
     * installation whose tables are already there: the backend goes on reading and writing them unqualified,
     * and a lookup asking only about {@code current_schema()} would report every one of them absent. What that
     * would cost is this issue over again - the clear would drop nothing and say nothing, which is #888 - and
     * one thing worse besides: the next open would create a second, empty set of tables in the schema ahead,
     * and from that commit on they would shadow the populated ones for every later unqualified reference.
     * <p>
     * The connection string carries the path rather than a role being altered, because the pools of this
     * backend are keyed by it: a storage of another url is a storage of connections of its own, where an
     * {@code ALTER ROLE} would leave every connection already pooled resolving the way it always did.
     */
    @Test
    public void testAClearFindsATableOfAnotherSchemaOfTheSearchPath() throws Exception {
        final TreeName tree = new TreeName("testSearchPath", "tree");
        final String backendId = getBackendId() + "_searchPath";
        // the tables of an installation made before anything was put in front of the schema they are in
        final JDBCStorage created = new JDBCStorage(createBackendCfg(backendId), null);
        final String tableName = created.getTableName(tree);
        // the same backend, over connections resolving in a schema of its own first and in the one the
        // tables are in behind it: what they reach unqualified is unchanged, what they create is not
        final String aheadOfThem = getJdbcUrl() + "&currentSchema=" + AHEAD_ON_THE_PATH + ",public";
        final JDBCStorage storage = new JDBCStorage(createBackendCfg(backendId, aheadOfThem), null);
        try {
            try {
                created.open(AccessMode.READ_WRITE);
                created.write(new WriteOperation() {
                    @Override
                    public void run(WriteableTransaction txn) throws Exception {
                        txn.openTree(tree, true);
                    }
                });
            } finally {
                created.close();
            }
            assertTrue(isExistsTable(tableName), "the case did not make the table it is about");

            try (final Connection con = DriverManager.getConnection(getJdbcUrl());
                 final Statement st = con.createStatement()) {
                st.execute("create schema if not exists " + AHEAD_ON_THE_PATH);
            }
            storage.open(AccessMode.READ_WRITE);
            try (final Connection con = DriverManager.getConnection(aheadOfThem)) {
                // the fixture is the whole of the case: without this the two schemas are the same one and
                // the assertions below hold of the version this case is about as well
                assertEquals(con.getSchema(), AHEAD_ON_THE_PATH,
                    "the connections of this storage do not work in the schema put ahead of the tables");
                assertNotEquals(con.getSchema(), "public", "the tables of this case are not in public after all");
            }

            assertTrue(storage.listTrees().contains(tree),
                "a tree whose table this connection reads unqualified was named by none of them");

            // the other half of what the narrowing decides, and the destructive one: openTree() creates a
            // table where its lookup answers that there is none, and an unqualified "create table" lands in
            // current_schema() - the schema ahead of the tables. A lookup asking about that schema alone
            // would answer no here and leave the populated table in public orphaned behind a second, empty
            // one, from this commit on. The clear below drops what the catalog names and would go on
            // passing while it happened, which is why this is asserted here rather than left to it
            storage.write(new WriteOperation() {
                @Override
                public void run(WriteableTransaction txn) throws Exception {
                    txn.openTree(tree, true);
                }
            });
            assertFalse(isExistsTableInSchema(AHEAD_ON_THE_PATH, tableName),
                "the open created a second table in the schema ahead of the tables, shadowing the populated one");
            assertTrue(isExistsTableInSchema("public", tableName),
                "the open did not leave the populated table where it is");

            storage.removeStorageFiles();

            assertFalse(isExistsTable(tableName),
                "the clear left a table it reaches unqualified standing, for living in another schema of the search path");
        } finally {
            // the same backend id, so this clears what either half of the case created - including the
            // run where the clear under test drops nothing and the tables would otherwise be left for
            // whatever case of this class runs next
            clearQuietly(storage);
            clearQuietly(new JDBCStorage(createBackendCfg(backendId), null));
            try (final Connection con = DriverManager.getConnection(getJdbcUrl());
                 final Statement st = con.createStatement()) {
                st.execute("drop schema if exists " + AHEAD_ON_THE_PATH + " cascade");
            }
        }
    }

    /**
     * Whether the table is in that one schema, which is the question the case above asks and the one
     * {@code TestCase.isExistsTable} cannot answer: it walks every schema the connection can see, so a
     * table created in the wrong one of the two reads there exactly like a table created in the right
     * one. Asked of {@code information_schema} with the schema and the name bound rather than through
     * {@code getTables()}, whose schema is a pattern - {@code opendj_ahead} would match a schema named
     * {@code opendjXahead} as readily, {@code _} being a single-character wildcard there.
     */
    private boolean isExistsTableInSchema(String schema, String tableName) throws SQLException {
        try (final Connection con = DriverManager.getConnection(getJdbcUrl());
             final PreparedStatement st = con.prepareStatement(
                 "select 1 from information_schema.tables where table_schema=? and lower(table_name)=lower(?)")) {
            st.setString(1, schema);
            st.setString(2, tableName);
            try (final ResultSet rs = st.executeQuery()) {
                return rs.next();
            }
        }
    }

}
