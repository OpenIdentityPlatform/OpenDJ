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

import org.forgerock.i18n.LocalizableMessage;

/** The requested managed object was found but its type could not be determined. */
public class DefinitionDecodingException extends DecodingException {

    /** An enumeration defining the reasons why the definition could not be resolved. */
    public static enum Reason {
        /**
         * The managed object could be found but its type resolved to an
         * abstract managed object definition.
         */
        ABSTRACT_TYPE_INFORMATION(),

        /**
         * The managed object could be found but did not contain any type
         * information (eg missing object classes in LDAP).
         */
        NO_TYPE_INFORMATION(),

        /**
         * The managed object could be found but did not contain the expected
         * type information (eg incorrect object classes in LDAP).
         */
        WRONG_TYPE_INFORMATION();

    }

    /** Version ID required by serializable classes. */
    private static final long serialVersionUID = 3459033551415663416L;

    /** Create the message. */
    private static LocalizableMessage createLocalizableMessage(AbstractManagedObjectDefinition<?, ?> d, Reason reason) {
        LocalizableMessage ufn = d.getUserFriendlyName();
        switch (reason) {
        case NO_TYPE_INFORMATION:
            return ERR_DECODING_EXCEPTION_NO_TYPE_INFO.get(ufn);
        case WRONG_TYPE_INFORMATION:
            return ERR_DECODING_EXCEPTION_WRONG_TYPE_INFO.get(ufn);
        default:
            return ERR_DECODING_EXCEPTION_ABSTRACT_TYPE_INFO.get(ufn);
        }
    }

    /** The expected type of managed object. */
    private final AbstractManagedObjectDefinition<?, ?> d;

    /** The reason why the definition could not be determined. */
    private final Reason reason;

    /**
     * Create a new definition decoding exception.
     *
     * @param d
     *            The expected type of managed object.
     * @param reason
     *            The reason why the definition could not be determined.
     */
    public DefinitionDecodingException(AbstractManagedObjectDefinition<?, ?> d, Reason reason) {
        super(createLocalizableMessage(d, reason));
        this.d = d;
        this.reason = reason;
    }

    /**
     * Gets the expected managed object definition.
     *
     * @return Returns the expected managed object definition.
     */
    public AbstractManagedObjectDefinition<?, ?> getManagedObjectDefinition() {
        return d;
    }

    /**
     * Gets the reason why the definition could not be determined.
     *
     * @return Returns the reason why the definition could not be determined.
     */
    public Reason getReason() {
        return reason;
    }
}
