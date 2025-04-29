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
import org.testcontainers.containers.MySQLContainer;
import org.testng.annotations.Test;

//docker run --rm --name mysql -p 3306:3306 -e MYSQL_DATABASE=database_name -e MYSQL_ROOT_PASSWORD=password mysql:latest

@Test
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

}
