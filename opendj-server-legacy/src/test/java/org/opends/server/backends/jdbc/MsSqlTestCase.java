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
import org.testcontainers.containers.MSSQLServerContainer;
import org.testng.annotations.Test;

//docker run --rm --name mssql -e ACCEPT_EULA=Y -e MSSQL_SA_PASSWORD=Passw0rd -p 1433:1433 mcr.microsoft.com/mssql/server:2017-CU12

@Test
public class MsSqlTestCase extends TestCase {

    @Override
    protected JdbcDatabaseContainer<?> getContainer() {
        return new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2019-CU30-ubuntu-20.04")
                .withExposedPorts(1433)
                .acceptLicense()
                .withPassword("Passw0rd");

    }

    @Override
    protected String getContainerDockerCommand() {
        return "run before test: docker run --rm --name mssql -e ACCEPT_EULA=Y -e MSSQL_SA_PASSWORD=Passw0rd -p 1433:1433 mcr.microsoft.com/mssql/server:2017-CU12";
    }

    @Override
    protected String getBackendId() {
        return MsSqlTestCase.class.getSimpleName();
    }

    @Override
    protected String getJdbcUrl() {
        return "jdbc:sqlserver://localhost:" + ((container==null)?"1433":container.getMappedPort(1433)) + ";encrypt=false;user=sa;password=Passw0rd;";
    }

}
