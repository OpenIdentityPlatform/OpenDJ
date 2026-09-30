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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.server.config.server.PluginCfg;
import org.opends.server.api.plugin.DirectoryServerPlugin;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.types.operation.PostOperationModifyOperation;
import org.opends.server.types.operation.PreOperationModifyOperation;

/**
 * A plugin which records the modify operations it is invoked for, per target entry, and whether
 * it has been finalized. It refuses {@link #REFUSED_TYPE} in {@link #initializePlugin} only, the
 * way a plugin which checks its plugin types there and not in
 * {@link #isConfigurationAcceptable} does: such a configuration passes the acceptance phase.
 */
public class PluginTypeTrackingPlugin extends DirectoryServerPlugin<PluginCfg>
{
  /** The plugin type which {@link #initializePlugin} refuses. */
  public static final PluginType REFUSED_TYPE = PluginType.PRE_OPERATION_DELETE;

  private static final ConcurrentHashMap<DN, AtomicInteger> PRE_OPERATION_MODIFY_COUNTS = new ConcurrentHashMap<>();

  private volatile boolean finalized;

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
    AtomicInteger count = PRE_OPERATION_MODIFY_COUNTS.remove(entryDN);
    return count != null ? count.get() : 0;
  }

  @Override
  public void initializePlugin(Set<PluginType> pluginTypes, PluginCfg configuration) throws ConfigException
  {
    if (pluginTypes.contains(REFUSED_TYPE))
    {
      throw new ConfigException(LocalizableMessage.raw("Plugin type " + REFUSED_TYPE + " is not supported"));
    }
  }

  @Override
  public PluginResult.PreOperation doPreOperation(PreOperationModifyOperation modifyOperation)
  {
    PRE_OPERATION_MODIFY_COUNTS.computeIfAbsent(modifyOperation.getEntryDN(), dn -> new AtomicInteger())
        .incrementAndGet();
    return PluginResult.PreOperation.continueOperationProcessing();
  }

  @Override
  public PluginResult.PostOperation doPostOperation(PostOperationModifyOperation modifyOperation)
  {
    return PluginResult.PostOperation.continueOperationProcessing();
  }

  @Override
  public void finalizePlugin()
  {
    finalized = true;
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
}
