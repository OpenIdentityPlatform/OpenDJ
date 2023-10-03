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
 * Copyright 2023 3A Systems, LLC.
 */
package org.opends.server.backends.cassandra;

import static org.mockito.Mockito.when;
import static org.forgerock.opendj.config.ConfigurationMock.mockCfg;

import org.forgerock.opendj.server.config.server.CASBackendCfg;
import org.opends.server.backends.pluggable.PluggableBackendImplTestCase;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.CassandraContainer;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import com.datastax.oss.driver.api.core.AllNodesFailedException;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;

import java.net.InetSocketAddress;

//docker run --rm -it -p 9042:9042 --name cassandra cassandra

@Test
public class TestCase extends PluggableBackendImplTestCase<CASBackendCfg> {

	CassandraContainer cassandraContainer;
	@Override
	protected Backend createBackend() {
		if(DockerClientFactory.instance().isDockerAvailable()) {
			cassandraContainer = new CassandraContainer<>("cassandra:latest").withExposedPorts(9042);
			cassandraContainer.start();
			InetSocketAddress contactPoint = cassandraContainer.getContactPoint();
			final String contactPointString = String.format("%s:%s", contactPoint.getHostName(), contactPoint.getPort());
			System.setProperty("datastax-java-driver.basic.contact-points.0", contactPointString);
			System.setProperty("datastax-java-driver.basic.load-balancing-policy.local-datacenter", cassandraContainer.getLocalDatacenter());
		}

		//test allow cassandra
		try(CqlSession session=CqlSession.builder()
				.withConfigLoader(DriverConfigLoader.fromDefaults(Storage.class.getClassLoader()))
				.build()){
			session.close();
		}catch (AllNodesFailedException e) {
			throw new SkipException("run before test: docker run --rm -it -p 9042:9042 --name cassandra cassandra");
		}
		return new Backend();
	}

	@Override
	protected CASBackendCfg createBackendCfg() {
		CASBackendCfg backendCfg = mockCfg(CASBackendCfg.class);
		when(backendCfg.getBackendId()).thenReturn("CASTestCase");
		when(backendCfg.getDBDirectory()).thenReturn("CASTestCase");
		return backendCfg;
	}

	@AfterClass
	@Override
	public void cleanUp() throws Exception {
		super.cleanUp();
		if(cassandraContainer != null) {
			cassandraContainer.close();
		}
	}
}
