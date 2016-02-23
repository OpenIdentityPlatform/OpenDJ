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
 * Portions Copyright 2014 ForgeRock AS.
 */
package com.forgerock.opendj.cli;

/**
 * An interactive console-based menu.
 *
 * @param <T>
 *          The type of success result value(s) returned by the
 *          call-back. Use <code>Void</code> if the call-backs do
 *          not return any values.
 */
public interface Menu<T> {

    /**
     * Displays the menu and waits for the user to select a valid option. When the user selects an option, the call-back
     * associated with the option will be invoked and its result returned.
     *
     * @return Returns the result of invoking the chosen menu call-back.
     * @throws ClientException
     *             If an I/O exception occurred or if one of the menu option call-backs failed for some reason.
     */
    MenuResult<T> run() throws ClientException;
}
