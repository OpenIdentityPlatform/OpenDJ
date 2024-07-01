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
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.forgerock.opendj.config;

import static com.forgerock.opendj.ldap.config.ConfigMessages.*;

/**
 * Thrown when an attempt is made to retrieve a property using its name but the
 * name was not recognized.
 * <p>
 * This exception can occur when attempt is made to retrieve inherited default
 * values from a managed object.
 */
public class PropertyNotFoundException extends OperationsException {

    /** Serialization ID. */
    private static final long serialVersionUID = -895548482881819610L;

    /** The name of the property that could not be found. */
    private final String propertyName;

    /**
     * Create a new property not found exception.
     *
     * @param propertyName
     *            The name of the property that could not be found.
     */
    public PropertyNotFoundException(String propertyName) {
        super(ERR_PROPERTY_NOT_FOUND_EXCEPTION.get(propertyName));

        this.propertyName = propertyName;
    }

    /**
     * Get the name of the property that could not be found.
     *
     * @return Returns the name of the property that could not be found.
     */
    public String getPropertyName() {
        return propertyName;
    }

}
