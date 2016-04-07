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
 * Copyright 2010 Sun Microsystems, Inc.
 * Portions copyright 2012 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

/**
 * Attribute factories are included with a set of {@code DecodeOptions} in order
 * to allow application to control how {@code Attribute} instances are created
 * when decoding requests and responses.
 *
 * @see Attribute
 * @see DecodeOptions
 */
public interface AttributeFactory {
    /**
     * Creates an attribute using the provided attribute description and no
     * values.
     *
     * @param attributeDescription
     *            The attribute description.
     * @return The new attribute.
     * @throws NullPointerException
     *             If {@code attributeDescription} was {@code null}.
     */
    Attribute newAttribute(AttributeDescription attributeDescription);
}
