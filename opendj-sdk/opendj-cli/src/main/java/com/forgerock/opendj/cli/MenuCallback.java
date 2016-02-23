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
 * Copyright 2008 Sun Microsystems
 * Portions Copyright 2014 ForgeRock AS.
 */
package com.forgerock.opendj.cli;


/**
 * A menu call-back which should be associated with each menu option.
 * When an option is selected the call-back is invoked.
 *
 * @param <T>
 *          The type of success result value(s) returned by the
 *          call-back. Use <code>Void</code> if the call-backs do
 *          not return any values.
 */
public interface MenuCallback<T> {

   /**
    * Invoke the menu call-back.
    *
    * @param app
    *          The application console.
    * @return Returns the result of invoking the menu call-back.
    * @throws ClientException
    *           If the menu call-back fails for some reason.
    */
    MenuResult<T> invoke(ConsoleApplication app) throws ClientException;
}
