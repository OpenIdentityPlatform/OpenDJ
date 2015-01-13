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
 *      Portions copyright 2014-2015 ForgeRock AS.
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
