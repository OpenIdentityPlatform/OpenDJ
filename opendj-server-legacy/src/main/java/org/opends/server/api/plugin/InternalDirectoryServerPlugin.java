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
 * Copyright 2009 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.api.plugin;

import java.util.List;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.server.config.server.PluginCfg;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.InitializationException;

/**
 * An internal directory server plugin which can be registered with
 * the server without requiring any associated configuration.
 */
public abstract class InternalDirectoryServerPlugin extends
    DirectoryServerPlugin<PluginCfg>
{
  /**
   * Creates a new internal directory server plugin using the provided
   * component name and plugin types.
   *
   * @param componentDN
   *          The configuration entry name of the component associated
   *          with this internal plugin.
   * @param pluginTypes
   *          The set of plugin types for which this internal plugin
   *          is registered.
   * @param invokeForInternalOps
   *          Indicates whether this internal plugin should be invoked
   *          for internal operations.
   */
  protected InternalDirectoryServerPlugin(DN componentDN,
      Set<PluginType> pluginTypes, boolean invokeForInternalOps)
  {
    // TODO: server context should be provided in constructor
    initializeInternal(DirectoryServer.getInstance().getServerContext(), componentDN, pluginTypes,
        invokeForInternalOps);
  }

  @Override
  public final void initializePlugin(Set<PluginType> pluginTypes,
      PluginCfg configuration) throws ConfigException,
      InitializationException
  {
    // Unused.
  }

  @Override
  public final boolean isConfigurationAcceptable(
      PluginCfg configuration, List<LocalizableMessage> unacceptableReasons)
  {
    // Unused.
    return true;
  }
}
