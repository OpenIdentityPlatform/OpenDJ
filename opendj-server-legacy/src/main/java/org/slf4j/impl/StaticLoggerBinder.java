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
 * Copyright 2014-2015 ForgeRock AS.
 */
package org.slf4j.impl;

import org.opends.server.loggers.slf4j.OpenDJLoggerFactory;
import org.slf4j.ILoggerFactory;
import org.slf4j.spi.LoggerFactoryBinder;

/**
 * Binds {@link org.slf4j.LoggerFactory} class with an instance of {@link ILoggerFactory}.
 */
//@Checkstyle:off
public class StaticLoggerBinder implements LoggerFactoryBinder {

    private static final StaticLoggerBinder SINGLETON = new StaticLoggerBinder();

    /**
     * Declare the version of the SLF4J API this implementation is compiled
     * against.
     */
    // to avoid constant folding by the compiler, this field must *not* be final

    public static String REQUESTED_API_VERSION = "1.7.5";

    private static final String FACTORY_CLASSNAME = OpenDJLoggerFactory.class.getName();

    /**
     * The ILoggerFactory instance returned by the {@link #getLoggerFactory}
     * method should always be the same object.
     */
    private final ILoggerFactory loggerFactory;

    private StaticLoggerBinder() {
        loggerFactory = new OpenDJLoggerFactory();
    }

    /**
     * Return the singleton of this class.
     *
     * @return the StaticLoggerBinder singleton
     */
    public static StaticLoggerBinder getSingleton() {
        return SINGLETON;
    }

    /** {@inheritDoc} */
    @Override
    public ILoggerFactory getLoggerFactory() {
        return loggerFactory;
    }

    /** {@inheritDoc} */
    @Override
    public String getLoggerFactoryClassStr() {
        return FACTORY_CLASSNAME;
    }
}
