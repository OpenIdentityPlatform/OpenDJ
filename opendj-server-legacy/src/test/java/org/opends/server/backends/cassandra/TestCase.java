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
 * Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.server.backends.cassandra;

import static org.mockito.Mockito.when;
import static org.forgerock.opendj.config.ConfigurationMock.mockCfg;

import org.forgerock.opendj.server.config.server.CASBackendCfg;
import org.opends.server.backends.pluggable.PluggableBackendImplTestCase;
import org.testng.annotations.Test;

//docker run --rm -it -p 9042:9042 --name cassandra cassandra

@Test
public class TestCase extends PluggableBackendImplTestCase<CASBackendCfg>
{
  @Override
  protected Backend createBackend()
  {
	  System.setProperty("datastax-java-driver.basic.request.timeout", "10 seconds"); //for docker slow start
	  return new Backend();
  }

  @Override
  protected CASBackendCfg createBackendCfg()
  {
	  CASBackendCfg backendCfg = mockCfg(CASBackendCfg.class);
	  when(backendCfg.getBackendId()).thenReturn("CASTestCase");
	  when(backendCfg.getDBDirectory()).thenReturn("CASTestCase");
	  return backendCfg;
  }
}
