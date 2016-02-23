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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2014 ForgeRock AS.
 */
package com.forgerock.opendj.cli;

/**
 * An interface for incrementally configuring a table serializer. Once
 * configured, the table printer can be used to create a new
 * {@link TableSerializer} instance using the {@link #getSerializer()}
 * method.
 */
public abstract class TablePrinter {

    /**
     * Creates a new abstract table printer.
     */
    protected TablePrinter() {
        // No implementation required.
    }

    /**
     * Creates a new table serializer based on the configuration of this table printer.
     *
     * @return Returns a new table serializer based on the configuration of this table printer.
     */
    protected abstract TableSerializer getSerializer();
}
