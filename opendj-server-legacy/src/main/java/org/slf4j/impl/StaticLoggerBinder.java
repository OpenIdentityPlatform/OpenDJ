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
 *      Copyright 2014 ForgeRock AS.
 */
package org.slf4j.impl;

import org.opends.server.loggers.OpenDJLoggerFactory;
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
