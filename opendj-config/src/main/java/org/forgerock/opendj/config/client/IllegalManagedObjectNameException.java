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
package org.forgerock.opendj.config.client;

import static com.forgerock.opendj.ldap.config.ConfigMessages.*;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.PropertyException;
import org.forgerock.opendj.config.OperationsException;
import org.forgerock.opendj.config.PropertyDefinition;
import org.forgerock.opendj.config.PropertyDefinitionUsageBuilder;

/**
 * Thrown when an attempt is made to create a new managed object with an illegal
 * name.
 * <p>
 * This exception can occur when a new managed object is given a name which is
 * either an empty string, a string containing just white-spaces, or a string
 * which is invalid according to the managed object's naming property (if it has
 * one).
 */
public class IllegalManagedObjectNameException extends OperationsException {

    /** Serialization ID. */
    private static final long serialVersionUID = 7491748228684293291L;

    /** Create the message. */
    private static LocalizableMessage createMessage(String illegalName,
            PropertyDefinition<?> namingPropertyDefinition) {
        if (illegalName.length() == 0) {
            return ERR_ILLEGAL_MANAGED_OBJECT_NAME_EXCEPTION_EMPTY.get();
        } else if (illegalName.trim().length() == 0) {
            return ERR_ILLEGAL_MANAGED_OBJECT_NAME_EXCEPTION_BLANK.get();
        } else if (namingPropertyDefinition != null) {
            try {
                namingPropertyDefinition.decodeValue(illegalName);
            } catch (PropertyException e) {
                PropertyDefinitionUsageBuilder builder = new PropertyDefinitionUsageBuilder(true);
                return ERR_ILLEGAL_MANAGED_OBJECT_NAME_EXCEPTION_SYNTAX.get(illegalName,
                        namingPropertyDefinition.getName(), builder
                                .getUsage(namingPropertyDefinition));
            }
        }

        return ERR_ILLEGAL_MANAGED_OBJECT_NAME_EXCEPTION_OTHER.get(illegalName);
    }

    /** The illegal name. */
    private final String illegalName;

    /** The naming property definition if applicable. */
    private final PropertyDefinition<?> namingPropertyDefinition;

    /**
     * Create a new illegal name exception and no naming property definition.
     *
     * @param illegalName
     *            The illegal managed object name.
     */
    public IllegalManagedObjectNameException(String illegalName) {
        this(illegalName, null);
    }

    /**
     * Create a new illegal name exception and a naming property definition.
     *
     * @param illegalName
     *            The illegal managed object name.
     * @param namingPropertyDefinition
     *            The naming property definition.
     */
    public IllegalManagedObjectNameException(String illegalName, PropertyDefinition<?> namingPropertyDefinition) {
        super(createMessage(illegalName, namingPropertyDefinition));

        this.illegalName = illegalName;
        this.namingPropertyDefinition = namingPropertyDefinition;
    }

    /**
     * Get the illegal managed object name.
     *
     * @return Returns the illegal managed object name.
     */
    public String getIllegalName() {
        return illegalName;
    }

    /**
     * Get the naming property definition if applicable.
     *
     * @return Returns naming property definition, or <code>null</code> if none
     *         was specified.
     */
    public PropertyDefinition<?> getNamingPropertyDefinition() {
        return namingPropertyDefinition;
    }

}
