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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions copyright 2014-2015 ForgeRock AS
 */
package com.forgerock.opendj.ldap.tools;

import static com.forgerock.opendj.cli.Utils.readBytesFromFile;
import static com.forgerock.opendj.cli.Utils.secondsToTimeString;
import static com.forgerock.opendj.ldap.tools.ToolsMessages.*;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.controls.AuthorizationIdentityRequestControl;
import org.forgerock.opendj.ldap.controls.AuthorizationIdentityResponseControl;
import org.forgerock.opendj.ldap.controls.GenericControl;
import org.forgerock.opendj.ldap.controls.GetEffectiveRightsRequestControl;
import org.forgerock.opendj.ldap.controls.PasswordExpiredResponseControl;
import org.forgerock.opendj.ldap.controls.PasswordExpiringResponseControl;
import org.forgerock.opendj.ldap.controls.PasswordPolicyErrorType;
import org.forgerock.opendj.ldap.controls.PasswordPolicyRequestControl;
import org.forgerock.opendj.ldap.controls.PasswordPolicyResponseControl;
import org.forgerock.opendj.ldap.controls.PasswordPolicyWarningType;
import org.forgerock.opendj.ldap.controls.SubtreeDeleteRequestControl;
import org.forgerock.opendj.ldap.responses.BindResult;

import com.forgerock.opendj.cli.ConsoleApplication;
import com.forgerock.opendj.ldap.controls.AccountUsabilityRequestControl;
import com.forgerock.opendj.util.StaticUtils;

/**
 * This class provides utility functions for all the client side tools.
 */
final class Utils {

    /**
     * Parse the specified command line argument to create the appropriate
     * LDAPControl. The argument string should be in the format
     * controloid[:criticality[:value|::b64value|:&lt;fileurl]]
     *
     * @param argString
     *            The argument string containing the encoded control
     *            information.
     * @return The control decoded from the provided string, or
     *         <CODE>null</CODE> if an error occurs while parsing the argument
     *         value.
     * @throws org.forgerock.opendj.ldap.DecodeException
     *             If an error occurs.
     */
    static GenericControl getControl(final String argString) throws DecodeException {
        String controlOID = null;
        boolean controlCriticality = false;
        ByteString controlValue = null;

        int idx = argString.indexOf(":");

        if (idx < 0) {
            controlOID = argString;
        } else {
            controlOID = argString.substring(0, idx);
        }

        final String lowerOID = StaticUtils.toLowerCase(controlOID);
        if ("accountusable".equals(lowerOID) || "accountusability".equals(lowerOID)) {
            controlOID = AccountUsabilityRequestControl.OID;
        } else if ("authzid".equals(lowerOID) || "authorizationidentity".equals(lowerOID)) {
            controlOID = AuthorizationIdentityRequestControl.OID;
        } else if ("noop".equals(lowerOID) || "no-op".equals(lowerOID)) {
            // controlOID = OID_LDAP_NOOP_OPENLDAP_ASSIGNED;
        } else if ("subentries".equals(lowerOID)) {
            // controlOID = OID_LDAP_SUBENTRIES;
        } else if ("managedsait".equals(lowerOID)) {
            // controlOID = OID_MANAGE_DSAIT_CONTROL;
        } else if ("pwpolicy".equals(lowerOID) || "passwordpolicy".equals(lowerOID)) {
            controlOID = PasswordPolicyRequestControl.OID;
        } else if ("subtreedelete".equals(lowerOID) || "treedelete".equals(lowerOID)) {
            controlOID = SubtreeDeleteRequestControl.OID;
        } else if ("realattrsonly".equals(lowerOID) || "realattributesonly".equals(lowerOID)) {
            // controlOID = OID_REAL_ATTRS_ONLY;
        } else if ("virtualattrsonly".equals(lowerOID) || "virtualattributesonly".equals(lowerOID)) {
            // controlOID = OID_VIRTUAL_ATTRS_ONLY;
        } else if ("effectiverights".equals(lowerOID) || "geteffectiverights".equals(lowerOID)) {
            controlOID = GetEffectiveRightsRequestControl.OID;
        }

        if (idx < 0) {
            return GenericControl.newControl(controlOID);
        }

        final String remainder = argString.substring(idx + 1, argString.length());

        idx = remainder.indexOf(":");
        if (idx == -1) {
            if ("true".equalsIgnoreCase(remainder)) {
                controlCriticality = true;
            } else if ("false".equalsIgnoreCase(remainder)) {
                controlCriticality = false;
            } else {
                // TODO: I18N
                throw DecodeException.error(LocalizableMessage
                        .raw("Invalid format for criticality value:" + remainder));
            }
            return GenericControl.newControl(controlOID, controlCriticality);

        }

        final String critical = remainder.substring(0, idx);
        if ("true".equalsIgnoreCase(critical)) {
            controlCriticality = true;
        } else if ("false".equalsIgnoreCase(critical)) {
            controlCriticality = false;
        } else {
            // TODO: I18N
            throw DecodeException.error(LocalizableMessage
                    .raw("Invalid format for criticality value:" + critical));
        }

        final String valString = remainder.substring(idx + 1, remainder.length());
        if (valString.charAt(0) == ':') {
            controlValue = ByteString.valueOfUtf8(valString.substring(1, valString.length()));
        } else if (valString.charAt(0) == '<') {
            // Read data from the file.
            final String filePath = valString.substring(1, valString.length());
            try {
                final byte[] val = readBytesFromFile(filePath);
                controlValue = ByteString.wrap(val);
            } catch (final Exception e) {
                return null;
            }
        } else {
            controlValue = ByteString.valueOfUtf8(valString);
        }

        return GenericControl.newControl(controlOID, controlCriticality, controlValue);
    }

    /**
     * Prints a multi-line error message with the provided information to the
     * given print stream.
     *
     * @param app
     *            The console app to use to write the error message.
     * @param ere
     *            The error result.
     * @return The error code.
     */
    static int printErrorMessage(final ConsoleApplication app, final LdapException ere) {
         /* if ((ere.getMessage() != null) && (ere.getMessage().length() > 0)) {
             app.println(LocalizableMessage.raw(ere.getMessage()));
         }*/

        if (ere.getResult().getResultCode().intValue() >= 0) {
            app.errPrintln(ERR_TOOL_RESULT_CODE.get(ere.getResult().getResultCode().intValue(), ere
                    .getResult().getResultCode().toString()));
        }

        if (ere.getResult().getDiagnosticMessage() != null
                && ere.getResult().getDiagnosticMessage().length() > 0) {
            app.errPrintln(ERR_TOOL_ERROR_MESSAGE.get(ere.getResult().getDiagnosticMessage()));
        }

        if (ere.getResult().getMatchedDN() != null && ere.getResult().getMatchedDN().length() > 0) {
            app.errPrintln(ERR_TOOL_MATCHED_DN.get(ere.getResult().getMatchedDN()));
        }

        if (app.isVerbose() && ere.getResult().getCause() != null) {
            ere.getResult().getCause().printStackTrace(app.getErrorStream());
        }

        return ere.getResult().getResultCode().intValue();
    }

    static void printPasswordPolicyResults(final ConsoleApplication app, final BindResult result) {
        try {
            final AuthorizationIdentityResponseControl control = result.getControl(
                    AuthorizationIdentityResponseControl.DECODER, new DecodeOptions());
            if (control != null) {
                app.println(INFO_BIND_AUTHZID_RETURNED.get(control.getAuthorizationID()));
            }
        } catch (final DecodeException e) {
            app.errPrintln(ERR_DECODE_CONTROL_FAILURE.get(e.getLocalizedMessage()));
        }

        try {
            final PasswordExpiredResponseControl control = result.getControl(PasswordExpiredResponseControl.DECODER,
                                                                             new DecodeOptions());
            if (control != null) {
                app.println(INFO_BIND_PASSWORD_EXPIRED.get());
            }
        } catch (final DecodeException e) {
            app.errPrintln(ERR_DECODE_CONTROL_FAILURE.get(e.getLocalizedMessage()));
        }

        try {
            final PasswordExpiringResponseControl control = result.getControl(PasswordExpiringResponseControl.DECODER,
                                                                              new DecodeOptions());
            if (control != null) {
                final LocalizableMessage timeString = secondsToTimeString(control.getSecondsUntilExpiration());
                app.println(INFO_BIND_PASSWORD_EXPIRING.get(timeString));
            }
        } catch (final DecodeException e) {
            app.errPrintln(ERR_DECODE_CONTROL_FAILURE.get(e.getLocalizedMessage()));
        }

        try {
            final PasswordPolicyResponseControl control = result.getControl(PasswordPolicyResponseControl.DECODER,
                                                                            new DecodeOptions());
            if (control != null) {
                final PasswordPolicyErrorType errorType = control.getErrorType();
                if (errorType == PasswordPolicyErrorType.PASSWORD_EXPIRED) {
                    app.println(INFO_BIND_PASSWORD_EXPIRED.get());
                } else if (errorType == PasswordPolicyErrorType.ACCOUNT_LOCKED) {
                    app.println(INFO_BIND_ACCOUNT_LOCKED.get());
                } else if (errorType == PasswordPolicyErrorType.CHANGE_AFTER_RESET) {

                    app.println(INFO_BIND_MUST_CHANGE_PASSWORD.get());
                }

                final PasswordPolicyWarningType warningType = control.getWarningType();
                if (warningType == PasswordPolicyWarningType.TIME_BEFORE_EXPIRATION) {
                    final LocalizableMessage timeString = secondsToTimeString(control.getWarningValue());
                    app.println(INFO_BIND_PASSWORD_EXPIRING.get(timeString));
                } else if (warningType == PasswordPolicyWarningType.GRACE_LOGINS_REMAINING) {
                    app.println(INFO_BIND_GRACE_LOGINS_REMAINING.get(control.getWarningValue()));
                }
            }
        } catch (final DecodeException e) {
            app.errPrintln(ERR_DECODE_CONTROL_FAILURE.get(e.getLocalizedMessage()));
        }
    }

    /**
     * Sets default system property settings for the xxxrate performance tools.
     */
    static void setDefaultPerfToolProperties() {
        // Use SameThreadStrategy by default.
        if (System.getProperty("org.forgerock.opendj.transport.useWorkerThreads") == null) {
            System.setProperty("org.forgerock.opendj.transport.useWorkerThreads", "false");
        }

        /* Configure connections to be terminate immediately after closing (this
         prevents port exhaustion in xxxrate tools when
         connecting/disconnecting).*/
        if (System.getProperty("org.forgerock.opendj.transport.linger") == null) {
            System.setProperty("org.forgerock.opendj.transport.linger", "0");
        }
    }

    /** Prevent instantiation. */
    private Utils() {
        // Do nothing.
    }
}
