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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.rest2ldap;

/**
 * Represents an {@link org.forgerock.json.resource.ActionRequest action} that may be performed against a resource.
 * Resources will only accept actions which have been {@link Resource#supportedAction(Action) registered} as being
 * supported.
 */
public enum Action {
    // Note: actions are likely to become an extension point in future versions of Rest2Ldap, in which case this enum
    // will need to be converted into a regular class or interface,

    /** An action that allows users to change their password. */
    MODIFY_PASSWORD("modifyPassword"),

    /** An action that allows users to reset their password. */
    RESET_PASSWORD("resetPassword");

    private final String actionId;

    Action(final String actionId) {
        this.actionId = actionId;
    }

    @Override
    public String toString() {
        return actionId;
    }
}
