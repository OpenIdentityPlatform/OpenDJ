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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.util.Reject;

/**
 * This exception is thrown when the client or server refuses to create, delete,
 * or modify a managed object due to one or more constraints that cannot be
 * satisfied.
 * <p>
 * Operations can be rejected either by a client-side constraint violation
 * triggered by {@link ClientConstraintHandler}, or by a server-side error.
 * <p>
 * For example, the Directory Server might not be able perform an operation due
 * to some OS related problem, such as lack of disk space, or missing files.
 */
public class OperationRejectedException extends AdminClientException {

    /** The type of operation that caused this exception. */
    public enum OperationType {
        /** A managed object could not be created. */
        CREATE,
        /** A managed object could not be deleted. */
        DELETE,
        /** A managed object could not be modified. */
        MODIFY;
    }

    /** Serialization ID. */
    private static final long serialVersionUID = 8547688890613079044L;

    /** Gets the default message. */
    private static LocalizableMessage getDefaultMessage(Collection<LocalizableMessage> messages) {
        Reject.ifNull(messages);
        Reject.ifFalse(!messages.isEmpty(), "Messages should not be empty");

        if (messages.size() == 1) {
            return ERR_OPERATION_REJECTED_EXCEPTION_SINGLE.get(messages.iterator().next());
        } else {
            return ERR_OPERATION_REJECTED_EXCEPTION_PLURAL.get(getSingleMessage(messages));
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

    /** The type of operation that caused this exception. */
    private final OperationType type;

    /** The user friendly name of the component that caused this exception. */
    private final LocalizableMessage ufn;

    /**
     * Creates a new operation rejected exception with a default message.
     *
     * @param type
     *            The type of operation that caused this exception.
     * @param ufn
     *            The user friendly name of the component that caused this
     *            exception.
     */
    public OperationRejectedException(OperationType type, LocalizableMessage ufn) {
        this(type, ufn, ERR_OPERATION_REJECTED_DEFAULT.get());
    }

    /**
     * Creates a new operation rejected exception with the provided messages.
     *
     * @param type
     *            The type of operation that caused this exception.
     * @param ufn
     *            The user friendly name of the component that caused this
     *            exception.
     * @param messages
     *            The messages describing the constraint violations that
     *            occurred (must be non-<code>null</code> and non-empty).
     */
    public OperationRejectedException(OperationType type, LocalizableMessage ufn,
        Collection<LocalizableMessage> messages) {
        super(getDefaultMessage(messages));

        this.messages = new ArrayList<>(messages);
        this.type = type;
        this.ufn = ufn;
    }

    /**
     * Creates a new operation rejected exception with the provided message.
     *
     * @param type
     *            The type of operation that caused this exception.
     * @param ufn
     *            The user friendly name of the component that caused this
     *            exception.
     * @param message
     *            The message describing the constraint violation that occurred.
     */
    public OperationRejectedException(OperationType type, LocalizableMessage ufn, LocalizableMessage message) {
        this(type, ufn, Collections.singleton(message));
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
     * Gets the type of operation that caused this exception.
     *
     * @return Returns the type of operation that caused this exception.
     */
    public OperationType getOperationType() {
        return type;
    }

    /**
     * Gets the user friendly name of the component that caused this exception.
     *
     * @return Returns the user friendly name of the component that caused this
     *         exception.
     */
    public LocalizableMessage getUserFriendlyName() {
        return ufn;
    }

}
