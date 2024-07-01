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

package org.forgerock.opendj.config.server;

import static com.forgerock.opendj.ldap.config.ConfigMessages.*;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.config.DecodingException;
import org.forgerock.opendj.config.ManagedObjectDefinition;
import org.forgerock.opendj.config.PropertyException;
import org.forgerock.util.Reject;

/**
 * The requested server managed object was found but one or more of its
 * properties could not be decoded successfully.
 */
public class ServerManagedObjectDecodingException extends DecodingException {

    /** Version ID required by serializable classes. */
    private static final long serialVersionUID = 1598401431084729853L;

    /** Create the message. */
    private static LocalizableMessage createMessage(ServerManagedObject<?> partialManagedObject,
            Collection<PropertyException> causes) {
        Reject.ifNull(causes);
        Reject.ifFalse(!causes.isEmpty(), "causes should nnot be empty");

        ManagedObjectDefinition<?, ?> d = partialManagedObject.getManagedObjectDefinition();
        if (causes.size() == 1) {
            return ERR_MANAGED_OBJECT_DECODING_EXCEPTION_SINGLE.get(d.getUserFriendlyName(), causes.iterator().next()
                    .getMessageObject());
        } else {
            LocalizableMessageBuilder builder = new LocalizableMessageBuilder();

            boolean isFirst = true;
            for (PropertyException cause : causes) {
                if (!isFirst) {
                    builder.append("; ");
                }
                builder.append(cause.getMessageObject());
                isFirst = false;
            }

            return ERR_MANAGED_OBJECT_DECODING_EXCEPTION_PLURAL.get(d.getUserFriendlyName(), builder.toMessage());
        }
    }

    /** The exception(s) that caused this decoding exception. */
    private final Collection<PropertyException> causes;

    /** The partially created server managed object. */
    private final ServerManagedObject<?> partialManagedObject;

    /**
     * Create a new property decoding exception.
     *
     * @param partialManagedObject
     *            The partially created server managed object containing
     *            properties which were successfully decoded and empty
     *            properties for those which were not (this may include empty
     *            mandatory properties).
     * @param causes
     *            The exception(s) that caused this decoding exception.
     */
    public ServerManagedObjectDecodingException(ServerManagedObject<?> partialManagedObject,
            Collection<PropertyException> causes) {
        super(createMessage(partialManagedObject, causes));

        this.partialManagedObject = partialManagedObject;
        this.causes = Collections.unmodifiableList(new LinkedList<PropertyException>(causes));
    }

    /**
     * Get an unmodifiable collection view of the causes of this exception.
     *
     * @return Returns an unmodifiable collection view of the causes of this
     *         exception.
     */
    public Collection<PropertyException> getCauses() {
        return causes;
    }

    /**
     * Get the partially created server managed object containing properties
     * which were successfully decoded and empty properties for those which were
     * not (this may include empty mandatory properties).
     *
     * @return Returns the partially created server managed object containing
     *         properties which were successfully decoded and empty properties
     *         for those which were not (this may include empty mandatory
     *         properties).
     */
    public ServerManagedObject<?> getPartialManagedObject() {
        return partialManagedObject;
    }

}
