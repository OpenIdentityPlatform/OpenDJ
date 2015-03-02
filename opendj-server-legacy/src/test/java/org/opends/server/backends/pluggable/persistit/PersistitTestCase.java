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

import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.TestCaseUtils;
import org.opends.server.admin.std.meta.BackendIndexCfgDefn.IndexType;
import org.opends.server.admin.std.server.BackendIndexCfg;
import org.opends.server.admin.std.server.PersistitBackendCfg;
import org.opends.server.admin.std.server.PluggableBackendCfg;
import org.opends.server.backends.persistit.PitBackend;
import org.opends.server.backends.pluggable.PluggableBackendImplTestCase;
import org.opends.server.core.DirectoryServer;
import org.testng.annotations.Test;

/**
 * PersistIt Tester.
 */
public class PersistitTestCase extends PluggableBackendImplTestCase
{
  /**
   * Tests the storage API for resource checking.
   * The tested method has no return value, but an exception, while not systematic, may be thrown,
   * in which case the test must fail.
   *
   * @throws Exception if resources are low.
   */
  @Test
  public void testPersistitCfg() throws Exception
  {
    backend.getRootContainer().checkForEnoughResources(null);
  }

  @Override
  public PitBackend createBackend() throws Exception
  {
    PluggableBackendCfg backendCfg = createBackendCfg();
  
    PitBackend b = new PitBackend();
    b.setBackendID(backendCfg.getBackendId());
    b.configureBackend((PersistitBackendCfg)backendCfg);
    return b;
  }

  private PluggableBackendCfg createBackendCfg() throws ConfigException
  {
    String homeDirName = "pdb_test";
    PersistitBackendCfg backendCfg = mock(PersistitBackendCfg.class);

    when(backendCfg.getBackendId()).thenReturn("persTest" + homeDirName);
    when(backendCfg.getDBDirectory()).thenReturn(homeDirName);
    when(backendCfg.getDBDirectoryPermissions()).thenReturn("755");
    when(backendCfg.getDBCacheSize()).thenReturn(0L);
    when(backendCfg.getDBCachePercent()).thenReturn(20);
    when(backendCfg.isSubordinateIndexesEnabled()).thenReturn(true);
    when(backendCfg.getBaseDN()).thenReturn(TestCaseUtils.newSortedSet(testBaseDN));
    when(backendCfg.dn()).thenReturn(testBaseDN);
    when(backendCfg.listBackendIndexes()).thenReturn(backendIndexes);
    when(backendCfg.listBackendVLVIndexes()).thenReturn(backendVlvIndexes);
    
    BackendIndexCfg indexCfg = mock(BackendIndexCfg.class);
    when(indexCfg.getIndexType()).thenReturn(TestCaseUtils.newSortedSet(IndexType.PRESENCE, IndexType.EQUALITY));
    when(indexCfg.getAttribute()).thenReturn(DirectoryServer.getAttributeType(backendIndexes[0]));
    when(backendCfg.getBackendIndex(backendIndexes[0])).thenReturn(indexCfg);
    return backendCfg;
  }
}
