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
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions copyright 2013 ForgeRock AS.
 */
package org.forgerock.opendj.config;

import static com.forgerock.opendj.ldap.AdminMessages.*;

import org.forgerock.i18n.LocalizableMessage;

/**
 * Thrown to indicate that a property value was invalid according to its
 * associated property definition.
 */
public class IllegalPropertyValueException extends PropertyException {

    /**
     * Serialization ID.
     */
    private static final long serialVersionUID = -3145632074909281823L;

    // The illegal property value.
    private final Object value;

    /**
     * Create a new illegal property value exception.
     *
     * @param pd
     *            The property definition.
     * @param value
     *            The illegal property value.
     */
    public IllegalPropertyValueException(PropertyDefinition<?> pd, Object value) {
        super(pd, createMessage(pd, value));
        this.value = value;
    }

    /**
     * Create a new illegal property value exception.
     *
     * @param pd
     *            The property definition.
     * @param value
     *            The illegal property value.
     * @param cause
     *            The cause.
     */
    public IllegalPropertyValueException(PropertyDefinition<?> pd, Object value, Throwable cause) {
        super(pd, createMessage(pd, value), cause);
        this.value = value;
    }

    /**
     * Get the illegal property value that caused the exception.
     *
     * @return Returns the illegal property value.
     */
    public final Object getIllegalValue() {
        return value;
    }

    // Create the message.
    private static LocalizableMessage createMessage(PropertyDefinition<?> pd, Object value) {
        PropertyDefinitionUsageBuilder builder = new PropertyDefinitionUsageBuilder(true);
        return ERR_ILLEGAL_PROPERTY_VALUE_EXCEPTION.get(String.valueOf(value), pd.getName(), builder.getUsage(pd));
    }

}
