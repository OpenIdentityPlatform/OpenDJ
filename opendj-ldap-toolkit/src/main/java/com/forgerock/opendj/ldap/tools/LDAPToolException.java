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
 *  Copyright 2016 ForgeRock AS.
 */

package com.forgerock.opendj.ldap.tools;

import com.forgerock.opendj.cli.ConsoleApplication;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ResultCode;

@SuppressWarnings("serial")
final class LDAPToolException extends Exception {

    static LDAPToolException newToolExceptionAlreadyPrinted(final Exception rootException, ResultCode rc) {
        return new LDAPToolException(rootException, rc);
    }

    static LDAPToolException newToolParamException(final Exception rootException, final LocalizableMessage message) {
        return new LDAPToolException(rootException, ResultCode.CLIENT_SIDE_PARAM_ERROR, message);
    }

    static LDAPToolException newToolParamException(final LocalizableMessage message) {
        return new LDAPToolException(null, ResultCode.CLIENT_SIDE_PARAM_ERROR, message);
    }

    static LDAPToolException newToolException(final Exception rootException,
                                              final ResultCode rc,
                                              final LocalizableMessage message) {
        return new LDAPToolException(rootException, rc, message);
    }

    private final ResultCode resultCode;
    private final LocalizableMessage errorMsg;

    private LDAPToolException(final Exception rootException, final ResultCode rc) {
        super(rootException);
        this.resultCode = rc;
        this.errorMsg = null;
    }

    private LDAPToolException(final Exception rootException, final ResultCode rc, final LocalizableMessage errorMsg) {
        super(errorMsg.toString(), rootException);
        this.resultCode = rc;
        this.errorMsg = errorMsg;
    }

    int getResultCode() {
        return resultCode.intValue();
    }

    void printErrorMessage(final ConsoleApplication console) {
        if (errorMsg != null) {
            console.errPrintln(errorMsg);
        }
    }
}
