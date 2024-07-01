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
 * Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;

import java.util.List;
import java.util.Map;

/**
 * Interface for schema elements.
 */
public interface SchemaElement {

    /**
     * Returns the description of this schema element, or the empty string if it does not have a description.
     *
     * @return The description of this schema element, or the empty string if it does not have a description.
     */
    String getDescription();

    /**
     * Returns an unmodifiable map containing all of the extra properties associated with this schema element.
     *
     * @return An unmodifiable map containing all of the extra properties associated with this schema element.
     */
    Map<String, List<String>> getExtraProperties();

}
