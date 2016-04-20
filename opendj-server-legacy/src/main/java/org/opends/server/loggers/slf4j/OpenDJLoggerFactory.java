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
 * Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.loggers.slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;

/** Factory to retrieve an openDJ implementation of SLF4J Logger. */
public final class OpenDJLoggerFactory implements ILoggerFactory {
    private final ConcurrentMap<String, Logger> loggerMap;

    /** Create the factory. */
    public OpenDJLoggerFactory() {
        loggerMap = new ConcurrentHashMap<>();
    }

    @Override
    public Logger getLogger(String name) {
        if (name.equalsIgnoreCase(Logger.ROOT_LOGGER_NAME)) {
            name = "org.forgerock";
        }

        Logger slf4jLogger = loggerMap.get(name);
        if (slf4jLogger != null) {
            return slf4jLogger;
        }
        Logger newInstance = new OpenDJLoggerAdapter(name);
        Logger oldInstance = loggerMap.putIfAbsent(name, newInstance);
        return oldInstance == null ? newInstance : oldInstance;
    }
}
