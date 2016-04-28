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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions copyright 2014-2016 ForgeRock AS.
 */
package com.example.opendj;

import static com.example.opendj.ExamplePluginMessages.*;

import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.opends.server.api.plugin.DirectoryServerPlugin;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.types.InitializationException;

import com.example.opendj.server.ExamplePluginCfg;

/**
 * The example plugin implementation class. This plugin will output the configured message to the
 * error log during server start up.
 */
public class ExamplePlugin extends DirectoryServerPlugin<ExamplePluginCfg>
                           implements ConfigurationChangeListener<ExamplePluginCfg> {
    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    /** The current configuration. */
    private ExamplePluginCfg config;

    /** Default constructor. */
    public ExamplePlugin() {
        super();
    }

    @Override
    public void initializePlugin(Set<PluginType> pluginTypes, ExamplePluginCfg configuration)
            throws ConfigException, InitializationException {
        // This plugin may only be used as a server startup plugin.
        for (PluginType t : pluginTypes) {
            switch (t) {
            case STARTUP:
                // This is fine.
                break;
            default:
                throw new ConfigException(ERR_INITIALIZE_PLUGIN.get(String.valueOf(t)));
            }
        }

        // Register change listeners. These are not really necessary for this plugin
        // since it is only used during server start-up.
        configuration.addExampleChangeListener(this);

        // Save the configuration.
        this.config = configuration;
    }

    @Override
    public PluginResult.Startup doStartup() {
        // Log the provided message.
        logger.info(NOTE_DO_STARTUP, StringUtils.upperCase(config.getMessage()));
        return PluginResult.Startup.continueStartup();
    }

    @Override
    public ConfigChangeResult applyConfigurationChange(ExamplePluginCfg config) {
        // The new configuration has already been validated.

        // Log a message to say that the configuration has changed.
        // This is not necessary, but we'll do it just to show that the change
        // has taken effect.
        logger.info(NOTE_APPLY_CONFIGURATION_CHANGE, this.config.getMessage(), config.getMessage());

        // Update the configuration.
        this.config = config;

        // Update was successful, no restart required.
        return new ConfigChangeResult();
    }

    @Override
    public boolean isConfigurationChangeAcceptable(ExamplePluginCfg config, List<LocalizableMessage> messages) {
        // The only thing that can be validated here is the plugin's message.
        // However, it is always going to be valid, so let's always return true.
        return true;
    }
}

