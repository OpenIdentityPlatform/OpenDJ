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
 * Copyright 2025 3A Systems, LLC.
 */
package org.opends.server.backends.jdbc;

import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testng.annotations.Test;

//docker run --rm -it -p 5432:5432 -e POSTGRES_PASSWORD=password --name postgres postgres

@Test
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

}
