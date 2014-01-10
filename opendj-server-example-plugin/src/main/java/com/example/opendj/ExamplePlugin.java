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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions copyright 2014 ForgeRock AS.
 */
package com.example.opendj;

import static com.example.opendj.ExamplePluginMessages.*;

import java.util.List;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.server.config.meta.PluginCfgDefn.PluginType;
import org.opends.server.types.InitializationException;

/**
 * The example plugin implementation class. This plugin will output the
 * configured message to the error log during server start up.
 */
public class ExamplePlugin implements ConfigurationChangeListener<ExamplePluginCfg> {

    // The current configuration.
    private ExamplePluginCfg config;

    /**
     * Default constructor.
     */
    public ExamplePlugin() {
        // No implementation required.
    }

    /**
     * Performs any initialization necessary for this plugin. This will be
     * called as soon as the plugin has been loaded and before it is registered
     * with the server.
     *
     * @param pluginTypes
     *            The set of plugin types that indicate the ways in which this
     *            plugin will be invoked.
     * @param configuration
     *            The configuration for this plugin.
     * @throws ConfigException
     *             If the provided entry does not contain a valid configuration
     *             for this plugin.
     * @throws InitializationException
     *             If a problem occurs while initializing the plugin that is not
     *             related to the server configuration.
     */
    @Override()
    public void initializePlugin(Set<PluginType> pluginTypes, ExamplePluginCfg configuration)
            throws ConfigException, InitializationException {
        // This plugin may only be used as a server startup plugin.
        for (PluginType t : pluginTypes) {
            switch (t) {
            case STARTUP:
                // This is fine.
                break;
            default:
                LocalizableMessage message = SEVERE_ERR_INITIALIZE_PLUGIN.get(String.valueOf(t));
                throw new ConfigException(message);
            }
        }

        // Register change listeners. These are not really necessary for
        // this plugin since it is only used during server start-up.
        configuration.addExampleChangeListener(this);

        // Save the configuration.
        this.config = configuration;
    }

    /**
     * Performs any processing that should be done when the Directory Server is
     * in the process of starting. This method will be called after virtually
     * all other initialization has been performed but before the connection
     * handlers are started.
     *
     * @return The result of the startup plugin processing.
     */
    @Override
    public PluginResult.Startup doStartup() {
        // Log the provided message.
        LocalizableMessage message = NOTICE_DO_STARTUP.get(String.valueOf(config.getMessage()));
        logError(message);
        return PluginResult.Startup.continueStartup();
    }

    public ConfigChangeResult applyConfigurationChange(ExamplePluginCfg config) {
        // The new configuration has already been validated.

        // Log a message to say that the configuration has changed. This
        // isn't necessary, but we'll do it just to show that the change
        // has taken effect.
        LocalizableMessage message =
                NOTICE_APPLY_CONFIGURATION_CHANGE.get(String.valueOf(this.config.getMessage()),
                        String.valueOf(config.getMessage()));
        logError(message);

        // Update the configuration.
        this.config = config;

        // Update was successfull, no restart required.
        return new ConfigChangeResult(ResultCode.SUCCESS, false);
    }

    public boolean isConfigurationChangeAcceptable(ExamplePluginCfg config,
            List<LocalizableMessage> messages) {
        // The only thing that can be validated here is the plugin's
        // message. However, it is always going to be valid, so let's
        // always return true.
        return true;
    }
}
