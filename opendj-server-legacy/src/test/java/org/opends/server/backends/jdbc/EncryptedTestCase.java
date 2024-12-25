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
 * Copyright 2023-2024 3A Systems, LLC.
 */
package org.opends.server.backends.jdbc;

import org.forgerock.opendj.server.config.server.JDBCBackendCfg;
import org.opends.server.backends.pluggable.PluggableBackendImplTestCase;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.DriverManager;

import static org.forgerock.opendj.config.ConfigurationMock.mockCfg;
import static org.mockito.Mockito.when;

@Test
public class EncryptedTestCase extends PluggableBackendImplTestCase<JDBCBackendCfg> {
	PostgreSQLContainer container;

	@BeforeClass
	@Override
	public void setUp() throws Exception {
		if(DockerClientFactory.instance().isDockerAvailable()) {
			container = new PostgreSQLContainer<>("postgres:latest")
					.withExposedPorts(5432)
					.withUsername("postgres")
					.withPassword("password")
					.withDatabaseName("database_name");
			container.start();
		}
		try(Connection con= DriverManager.getConnection(createBackendCfg().getDBDirectory())){
		} catch (Exception e) {
			throw new SkipException("run before test: docker run --rm -it -p 5432:5432 -e POSTGRES_DB=database_name -e POSTGRES_PASSWORD=password --name postgres postgres");
		}
		super.setUp();
	}

	@Override
	protected Backend createBackend() {
		return new Backend();
	}

	@Override
	protected JDBCBackendCfg createBackendCfg() {
		JDBCBackendCfg backendCfg = mockCfg(JDBCBackendCfg.class);
		when(backendCfg.getBackendId()).thenReturn("EncPsqlTestCase"+System.currentTimeMillis());
		when(backendCfg.getDBDirectory()).thenReturn("jdbc:postgresql://localhost:"+ ((container==null)?"5432":container.getMappedPort(5432))+"/database_name?user=postgres&password=password");

		when(backendCfg.isConfidentialityEnabled()).thenReturn(true);
		when(backendCfg.getCipherKeyLength()).thenReturn(128);
		when(backendCfg.getCipherTransformation()).thenReturn("AES/CBC/PKCS5Padding");
		return backendCfg;
	}


	@AfterClass
	@Override
	public void cleanUp() throws Exception {
		super.cleanUp();
		if(container != null) {
			container.close();
		}
	}
}
