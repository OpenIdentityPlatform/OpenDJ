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

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.opendj.config.ConfigurationMock.mockCfg;
import static org.mockito.Mockito.when;
import static org.opends.messages.BackendMessages.ERR_BACKEND_CONFIG_CACHE_SIZE_GREATER_THAN_JVM_HEAP;
import static org.opends.server.util.StaticUtils.MB;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.server.config.server.JEBackendCfg;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.MemoryQuota;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Tests what {@link ConfigurableEnvironment#parseConfigEntry} does to the server's memory quota: an
 * explicit cache size is checked against it and nothing is taken - the storage which asked for the
 * configuration reserves the size itself, and gives back what it reserved when it closes.
 */
@SuppressWarnings("javadoc")
public class ConfigurableEnvironmentTest extends DirectoryServerTestCase
{
  private static final String BACKEND_ID = "ConfigurableEnvironmentTest";
  /** A cache size the quota of the test JVM grants several times over, in bytes. */
  private static final long CACHE_SIZE = 64L * MB;

  @BeforeClass
  public static void startServer() throws Exception
  {
    TestCaseUtils.startServer();
  }

  /**
   * The quota the parse checks the size against: the server's own, reached through DirectoryServer
   * and not through a ServerContext a test could hand it - so the parse is watched on that one.
   */
  private static MemoryQuota serverQuota()
  {
    return DirectoryServer.getInstance().getServerContext().getMemoryQuota();
  }

  @Test
  public void anExplicitCacheSizeTakesNothingFromTheQuota() throws Exception
  {
    final long availableBefore = serverQuota().getAvailableMemory();

    ConfigurableEnvironment.parseConfigEntry(createBackendCfg(CACHE_SIZE));

    assertThat(serverQuota().getAvailableMemory()).isEqualTo(availableBefore);
  }

  @Test
  public void aCachePercentTakesNothingFromTheQuota() throws Exception
  {
    final long availableBefore = serverQuota().getAvailableMemory();

    ConfigurableEnvironment.parseConfigEntry(createBackendCfg(0L));

    assertThat(serverQuota().getAvailableMemory()).isEqualTo(availableBefore);
  }

  /**
   * A size the quota cannot grant is warned about, naming what the quota has left, and still takes
   * nothing: the storage's own reservation is what is refused, and the warning is the only word of
   * it - the open of a backend at startup is not checked against the quota beforehand.
   */
  @Test
  public void aCacheSizeTheQuotaCannotGrantIsWarnedAboutWithWhatIsLeft() throws Exception
  {
    final MemoryQuota quota = serverQuota();
    // all but half a cache size, so that the size does not fit
    final long held = quota.getAvailableMemory() - CACHE_SIZE / 2;
    assertThat(quota.acquireMemory(held)).isTrue();
    try
    {
      final long availableBefore = quota.getAvailableMemory();
      final LocalizableMessage warning =
          ERR_BACKEND_CONFIG_CACHE_SIZE_GREATER_THAN_JVM_HEAP.get(CACHE_SIZE, availableBefore);
      TestCaseUtils.ERROR_TEXT_WRITER.clear();

      ConfigurableEnvironment.parseConfigEntry(createBackendCfg(CACHE_SIZE));

      assertThat(quota.getAvailableMemory()).isEqualTo(availableBefore);
      assertThat(TestCaseUtils.ERROR_TEXT_WRITER.getMessages())
          .as("the size the quota cannot grant is warned about, with what the quota has left")
          .anyMatch(record -> record.contains("msgID=" + warning.ordinal() + " msg=" + warning));
    }
    finally
    {
      quota.releaseMemory(held);
    }
  }

  /** A configuration whose cache is the given size in bytes, or a fifth of the quota when it is zero. */
  private static JEBackendCfg createBackendCfg(long cacheSize)
  {
    final JEBackendCfg backendCfg = mockCfg(JEBackendCfg.class);
    when(backendCfg.dn()).thenReturn(DN.valueOf("ds-cfg-backend-id=" + BACKEND_ID + ",cn=Backends,cn=config"));
    when(backendCfg.getBackendId()).thenReturn(BACKEND_ID);
    when(backendCfg.getDBCacheSize()).thenReturn(cacheSize);
    when(backendCfg.getDBCachePercent()).thenReturn(20);
    when(backendCfg.getDBNumCleanerThreads()).thenReturn(2);
    when(backendCfg.getDBNumLockTables()).thenReturn(63);
    return backendCfg;
  }
}
