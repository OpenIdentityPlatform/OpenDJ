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
package org.opends.server.plugins;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.server.config.server.PluginCfg;
import org.opends.server.api.plugin.DirectoryServerPlugin;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.operation.PostOperationModifyOperation;
import org.opends.server.types.operation.PreOperationModifyOperation;

/**
 * A plugin which records the modify operations it is invoked for, per target entry, and whether
 * it has been finalized. It refuses {@link #REFUSED_TYPE} in {@link #initializePlugin} only, the
 * way a plugin which checks its plugin types there and not in
 * {@link #isConfigurationAcceptable} does: such a configuration passes the acceptance phase.
 * Like most plugins, it registers a change listener on its configuration before it checks its
 * plugin types, and removes it in {@link #finalizePlugin()}.
 */
public class PluginTypeTrackingPlugin extends DirectoryServerPlugin<PluginCfg>
{
  /** The plugin type which {@link #initializePlugin} refuses. */
  public static final PluginType REFUSED_TYPE = PluginType.PRE_OPERATION_DELETE;

  private static final ConcurrentHashMap<DN, AtomicInteger> PRE_OPERATION_MODIFY_COUNTS = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<DN, AtomicInteger> POST_OPERATION_MODIFY_COUNTS = new ConcurrentHashMap<>();
  private static final AtomicInteger CHANGES_SEEN_WHEN_FINALIZED = new AtomicInteger();
  private static volatile boolean failToFinalize;

  private final ConfigurationChangeListener<PluginCfg> listener = new ConfigurationChangeListener<PluginCfg>()
  {
    @Override
    public boolean isConfigurationChangeAcceptable(PluginCfg configuration, List<LocalizableMessage> reasons)
    {
      return true;
    }

    @Override
    public ConfigChangeResult applyConfigurationChange(PluginCfg configuration)
    {
      if (finalized)
      {
        CHANGES_SEEN_WHEN_FINALIZED.incrementAndGet();
      }
      return new ConfigChangeResult();
    }
  };

  private volatile PluginCfg configuration;
  private volatile boolean finalized;
  private volatile DirectoryServerPlugin<?> registeredWhenFinalized;

  /**
   * Returns how many times a pre-operation modify of the given entry has invoked a plugin of this
   * class, and resets that count.
   *
   * @param entryDN
   *          The DN of the modified entry.
   * @return The number of invocations since the previous call.
   */
  public static int takePreOperationModifyCount(DN entryDN)
  {
    return take(PRE_OPERATION_MODIFY_COUNTS, entryDN);
  }

  /**
   * Returns how many times a post-operation modify of the given entry has invoked a plugin of this
   * class, and resets that count.
   *
   * @param entryDN
   *          The DN of the modified entry.
   * @return The number of invocations since the previous call.
   */
  public static int takePostOperationModifyCount(DN entryDN)
  {
    return take(POST_OPERATION_MODIFY_COUNTS, entryDN);
  }

  private static int take(ConcurrentHashMap<DN, AtomicInteger> counts, DN entryDN)
  {
    AtomicInteger count = counts.remove(entryDN);
    return count != null ? count.get() : 0;
  }

  /**
   * Returns how many configuration changes the change listeners of finalized plugins of this class
   * have received, and resets that count.
   *
   * @return The number of changes since the previous call.
   */
  public static int takeChangesSeenWhenFinalized()
  {
    return CHANGES_SEEN_WHEN_FINALIZED.getAndSet(0);
  }

  /**
   * Makes {@link #finalizePlugin()} throw once it has done its work, or stop throwing.
   *
   * @param fail
   *          Whether {@link #finalizePlugin()} throws.
   */
  public static void setFailToFinalize(boolean fail)
  {
    failToFinalize = fail;
  }

  @Override
  public void initializePlugin(Set<PluginType> pluginTypes, PluginCfg configuration) throws ConfigException
  {
    this.configuration = configuration;
    configuration.addChangeListener(listener);
    if (pluginTypes.contains(REFUSED_TYPE))
    {
      throw new ConfigException(LocalizableMessage.raw("Plugin type " + REFUSED_TYPE + " is not supported"));
    }
  }

  @Override
  public PluginResult.PreOperation doPreOperation(PreOperationModifyOperation modifyOperation)
  {
    count(PRE_OPERATION_MODIFY_COUNTS, modifyOperation.getEntryDN());
    return PluginResult.PreOperation.continueOperationProcessing();
  }

  @Override
  public PluginResult.PostOperation doPostOperation(PostOperationModifyOperation modifyOperation)
  {
    count(POST_OPERATION_MODIFY_COUNTS, modifyOperation.getEntryDN());
    return PluginResult.PostOperation.continueOperationProcessing();
  }

  private static void count(ConcurrentHashMap<DN, AtomicInteger> counts, DN entryDN)
  {
    counts.computeIfAbsent(entryDN, dn -> new AtomicInteger()).incrementAndGet();
  }

  @Override
  public void finalizePlugin()
  {
    configuration.removeChangeListener(listener);
    registeredWhenFinalized = DirectoryServer.getPluginConfigManager().getRegisteredPlugin(getPluginEntryDN());
    finalized = true;
    if (failToFinalize)
    {
      throw new IllegalStateException("The test makes finalizePlugin() fail");
    }
  }

  /**
   * Indicates whether the plugin manager has finalized this instance.
   *
   * @return {@code true} once {@link #finalizePlugin()} has been called.
   */
  public boolean isFinalized()
  {
    return finalized;
  }

  /**
   * Returns the plugin which the plugin manager had registered for the configuration entry of this
   * instance when it finalized this instance.
   *
   * @return The registered plugin, or {@code null} if there was none.
   */
  public DirectoryServerPlugin<?> getRegisteredWhenFinalized()
  {
    return registeredWhenFinalized;
  }
}
