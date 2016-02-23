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
 * Portions copyright 2014-2015 ForgeRock AS.
 */
package com.example.opendj;

import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;

import com.example.opendj.server.ExamplePluginCfg;

/**
 * The example plugin implementation class. This plugin will output the
 * configured message to the error log during server start up.
 */
public class ExamplePlugin implements ConfigurationChangeListener<ExamplePluginCfg> {
    // FIXME: fill in the remainder of this class once the server plugin API is migrated.

    /**
     * Default constructor.
     */
    public ExamplePlugin() {
        // No implementation required.
    }

    /** {@inheritDoc} */
    @Override
    public ConfigChangeResult applyConfigurationChange(final ExamplePluginCfg config) {
        // The new configuration has already been validated.

        // Update was successful, no restart required.
        return new ConfigChangeResult();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isConfigurationChangeAcceptable(final ExamplePluginCfg config,
            final List<LocalizableMessage> messages) {
        /*
         * The only thing that can be validated here is the plugin's message.
         * However, it is always going to be valid, so let's always return true.
         */
        return true;
    }
}
