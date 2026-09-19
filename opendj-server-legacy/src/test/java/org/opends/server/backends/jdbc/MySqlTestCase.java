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

import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.testng.Assert.assertEquals;
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
                assertTrue(CachedConnection.isWorthRetrying(refused, CachedConnection.ConnectDialect.of(limited)),
                    "a borrow must wait a per-account limit out rather than fail on it: " + refused);
            }
        } finally {
            grant(url, "drop user if exists 'limited1011'@'%'");
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
