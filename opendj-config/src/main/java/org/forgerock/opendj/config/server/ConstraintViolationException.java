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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.config.DecodingException;
import org.forgerock.util.Reject;

/**
 * This exception is thrown when the server refuses to use or delete a managed
 * object due to one or more constraints that cannot be satisfied.
 */
class ConstraintViolationException extends DecodingException {

    /** Serialization ID. */
    private static final long serialVersionUID = -4902443848460011875L;

    /** The server managed object. */
    private final ServerManagedObject<?> managedObject;

    /** Gets the default message. */
    private static LocalizableMessage getDefaultMessage(Collection<LocalizableMessage> messages) {
        Reject.ifNull(messages);
        Reject.ifFalse(!messages.isEmpty(), "messages should not be empty");

        if (messages.size() == 1) {
            return ERR_CONSTRAINT_VIOLATION_EXCEPTION_SINGLE.get(messages.iterator().next());
        } else {
            return ERR_CONSTRAINT_VIOLATION_EXCEPTION_PLURAL.get(getSingleMessage(messages));
        }
    }

    /** Merge the messages into a single message. */
    private static LocalizableMessage getSingleMessage(Collection<LocalizableMessage> messages) {
        if (messages.size() == 1) {
            return messages.iterator().next();
        } else {
            LocalizableMessageBuilder builder = new LocalizableMessageBuilder();

            boolean isFirst = true;
            for (LocalizableMessage m : messages) {
                if (!isFirst) {
                    builder.append(";  ");
                }
                builder.append(m);
                isFirst = false;
            }

            return builder.toMessage();
        }
    }

    /** The messages describing the constraint violations that occurred. */
    private final Collection<LocalizableMessage> messages;

    /**
     * Creates a new constraint violation exception with the provided messages.
     *
     * @param managedObject
     *            The server managed object which caused the constraint
     *            violations.
     * @param messages
     *            The messages describing the constraint violations that
     *            occurred (must be non-<code>null</code> and non-empty).
     */
    public ConstraintViolationException(ServerManagedObject<?> managedObject, Collection<LocalizableMessage> messages) {
        super(getDefaultMessage(messages));

        this.managedObject = managedObject;
        this.messages = new ArrayList<>(messages);
    }

    /**
     * Creates a new constraint violation exception with the provided message.
     *
     * @param managedObject
     *            The server managed object which caused the constraint
     *            violations.
     * @param message
     *            The message describing the constraint violation that occurred.
     */
    public ConstraintViolationException(ServerManagedObject<?> managedObject, LocalizableMessage message) {
        this(managedObject, Collections.singleton(message));
    }

    /**
     * Gets an unmodifiable collection view of the messages describing the
     * constraint violations that occurred.
     *
     * @return Returns an unmodifiable collection view of the messages
     *         describing the constraint violations that occurred.
     */
    public Collection<LocalizableMessage> getMessages() {
        return Collections.unmodifiableCollection(messages);
    }

    /**
     * Creates a single message listing all the messages combined into a single
     * list separated by semi-colons.
     *
     * @return Returns a single message listing all the messages combined into a
     *         single list separated by semi-colons.
     */
    public LocalizableMessage getMessagesAsSingleMessage() {
        return getSingleMessage(messages);
    }

    /**
     * Gets the server managed object which caused the constraint violations.
     *
     * @return Returns the server managed object which caused the constraint
     *         violations.
     */
    public ServerManagedObject<?> getManagedObject() {
        return managedObject;
    }
}
