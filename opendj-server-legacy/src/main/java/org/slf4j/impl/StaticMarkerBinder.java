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
 * Copyright 2014 ForgeRock AS.
 */
package org.slf4j.impl;

import org.slf4j.IMarkerFactory;
import org.slf4j.helpers.BasicMarkerFactory;
import org.slf4j.spi.MarkerFactoryBinder;

/**
 * Binds {@link org.slf4j.MarkerFactory} class with an instance of {@link IMarkerFactory}.
 */
//@Checkstyle:off
public class StaticMarkerBinder implements MarkerFactoryBinder {

    private static final String FACTORY_CLASSNAME = BasicMarkerFactory.class.getName();

    /**
     * The unique instance of this class.
     */
    public static final StaticMarkerBinder SINGLETON = new StaticMarkerBinder();

    final IMarkerFactory markerFactory = new BasicMarkerFactory();

    private StaticMarkerBinder() {
        // no implementation
    }

    /** {@inheritDoc} */
    @Override
    public IMarkerFactory getMarkerFactory() {
        return markerFactory;
    }

    /** {@inheritDoc} */
    @Override
    public String getMarkerFactoryClassStr() {
        return FACTORY_CLASSNAME;
    }

}
