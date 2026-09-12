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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.backends.jeb;

import static org.forgerock.opendj.config.ConfigurationMock.mockCfg;
import static org.mockito.Mockito.when;

import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.server.config.server.JEBackendCfg;
import org.opends.server.backends.pluggable.IndexConfidentialityChangeTestCase;
import org.opends.server.backends.pluggable.spi.Storage;
import org.opends.server.core.ServerContext;
import org.testng.annotations.Test;

/**
 * A confidentiality change of a backend index of a {@link JEBackend}, which deletes and creates the
 * tree of an index through a storage engine of its own.
 */
@Test
public class JEIndexConfidentialityChangeTest extends IndexConfidentialityChangeTestCase<JEBackendCfg>
{
  @Override
  protected JEBackendCfg createBackendCfg()
  {
    final JEBackendCfg backendCfg = mockCfg(JEBackendCfg.class);
    when(backendCfg.getBackendId()).thenReturn("JEIndexConfidentialityChangeTest");
    when(backendCfg.getDBDirectory()).thenReturn("JEIndexConfidentialityChangeTest");
    when(backendCfg.getDBDirectoryPermissions()).thenReturn("755");
    when(backendCfg.getDBCacheSize()).thenReturn(0L);
    when(backendCfg.getDBCachePercent()).thenReturn(20);
    when(backendCfg.getDBNumCleanerThreads()).thenReturn(2);
    when(backendCfg.getDBNumLockTables()).thenReturn(63);
    return backendCfg;
  }

  @Override
  protected Storage createStorage(JEBackendCfg cfg, ServerContext serverContext) throws ConfigException
  {
    return new JEStorage(cfg, serverContext);
  }
}
