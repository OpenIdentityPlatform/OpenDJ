/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2015 ForgeRock AS
 */

package org.opends.server.backends.pluggable.persistit;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.opends.server.admin.std.server.PersistitBackendCfg;
import org.opends.server.backends.persistit.PitBackend;
import org.opends.server.backends.pluggable.PluggableBackendImplTestCase;

/**
 * PersistIt Tester.
 */
public class PersistitTestCase extends PluggableBackendImplTestCase<PersistitBackendCfg>
{
  @Override
  protected PitBackend createBackend()
  {
    return new PitBackend();
  }

  @Override
  protected PersistitBackendCfg createBackendCfg()
  {
    PersistitBackendCfg backendCfg = mock(PersistitBackendCfg.class);
    when(backendCfg.getDBDirectory()).thenReturn(backendTestName);
    when(backendCfg.getDBDirectoryPermissions()).thenReturn("755");
    when(backendCfg.getDBCacheSize()).thenReturn(0L);
    when(backendCfg.getDBCachePercent()).thenReturn(20);
    return backendCfg;
  }
}
