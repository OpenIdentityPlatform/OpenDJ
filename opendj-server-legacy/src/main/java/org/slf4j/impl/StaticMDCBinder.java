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

import org.slf4j.helpers.BasicMDCAdapter;
import org.slf4j.spi.MDCAdapter;

/**
 * Binds {@link org.slf4j.MarkerFactory} class with an instance of {@link org.slf4j.IMarkerFactory}.
 */
//@Checkstyle:off
public class StaticMDCBinder {

    /**
     * The unique instance of this class.
     */
    public static final StaticMDCBinder SINGLETON = new StaticMDCBinder();

    private StaticMDCBinder() {
        // no implementation
    }

    /**
     * Returns an instance of MDC.
     *
     * @return a MDC
     */
    public MDCAdapter getMDCA() {
        return new BasicMDCAdapter();
    }

    /**
     * Returns the class name of MDC.
     *
     * @return the class name
     */
    public String getMDCAdapterClassStr() {
        return BasicMDCAdapter.class.getName();
    }
}
