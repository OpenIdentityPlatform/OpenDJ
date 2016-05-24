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
package org.opends.server.backends.jeb;

import static org.mockito.Mockito.when;
import static org.forgerock.opendj.config.ConfigurationMock.mockCfg;

import org.forgerock.opendj.server.config.server.JEBackendCfg;
import org.opends.server.backends.pluggable.PluggableBackendImplTestCase;
import org.testng.annotations.Test;

/** {@link JEBackend} Tester. */
@Test
public class JETestCase extends PluggableBackendImplTestCase<JEBackendCfg>
{
  @Override
  protected JEBackend createBackend()
  {
    return new JEBackend();
  }

  @Override
  protected JEBackendCfg createBackendCfg()
  {
    JEBackendCfg backendCfg = mockCfg(JEBackendCfg.class);
    when(backendCfg.getBackendId()).thenReturn("JETestCase");
    when(backendCfg.getDBDirectory()).thenReturn("JETestCase");
    when(backendCfg.getDBDirectoryPermissions()).thenReturn("755");
    when(backendCfg.getDBCacheSize()).thenReturn(0L);
    when(backendCfg.getDBCachePercent()).thenReturn(20);
    when(backendCfg.getDBNumCleanerThreads()).thenReturn(2);
    when(backendCfg.getDBNumLockTables()).thenReturn(63);
    return backendCfg;
  }
}
