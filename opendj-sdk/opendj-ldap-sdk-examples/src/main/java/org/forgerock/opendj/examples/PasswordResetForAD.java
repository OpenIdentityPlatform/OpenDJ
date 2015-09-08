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
 *      Copyright 2013-2015 ForgeRock AS.
 *
 */

package org.forgerock.opendj.examples;

import static org.forgerock.opendj.ldap.LDAPConnectionFactory.*;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SSLContextBuilder;
import org.forgerock.opendj.ldap.TrustManagers;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.util.Options;

import javax.net.ssl.SSLContext;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;

/**
 * This command-line client demonstrates how to reset a user password in
 * Microsoft Active Directory.
 * <br>
 * The client takes as arguments the host and port of the Active Directory
 * server, a flag indicating whether this is a self-reset (user changing own
 * password) or an administrative reset (administrator changing a password),
 * the DN and password of the user performing the reset, and target user DN
 * and new user password.
 */
public final class PasswordResetForAD {

    /**
     * Reset a user password in Microsoft Active Directory.
     * <br>
     * The connection should be LDAPS, not LDAP, in order to perform the
     * modification.
     *
     * @param args The command line arguments: host, port, "admin"|"self",
     *             DN, password, targetDN, newPassword
     */
    public static void main(final String[] args) {
        // --- JCite main ---
        if (args.length != 7) {
            System.err.println("Usage: host port \"admin\"|\"self\" DN "
                    + "password targetDN newPassword");
            System.err.println("For example: ad.example.com 636 admin "
                    + "cn=administrator,cn=Users,DC=ad,DC=example,DC=com "
                    + "Secret123 cn=testuser,cn=Users,DC=ad,DC=example,DC=com "
                    + "NewP4s5w0rd");
            System.exit(1);
        }
        final String host = args[0];
        final int port = Integer.parseInt(args[1]);
        final String mode = args[2];
        final String bindDN = args[3];
        final String bindPassword = args[4];
        final String targetDN = args[5];
        final String newPassword = args[6];

        Connection connection = null;
        try {
            final LDAPConnectionFactory factory =
                    new LDAPConnectionFactory(host, port, getTrustAllOptions());
            connection = factory.getConnection();
            connection.bind(bindDN, bindPassword.toCharArray());

            ModifyRequest request =
                    Requests.newModifyRequest(DN.valueOf(targetDN));
            String passwordAttribute = "unicodePwd";

            if ("admin".equalsIgnoreCase(mode)) {
                // Request modify, replacing the password with the new.

                request.addModification(
                        ModificationType.REPLACE,
                        passwordAttribute,
                        encodePassword(newPassword)
                );
            } else if ("self".equalsIgnoreCase(mode)) {
                // Request modify, deleting the old password, adding the new.

                // The default password policy for Active Directory domain
                // controller systems sets minimum password age to 1 (day).
                // If you get a constraint violation error when trying this
                // example, set this minimum password age to 0 by executing
                // cmd.exe as Administrator and entering the following
                // command at the prompt:
                //
                // net accounts /MINPWAGE:0

                request.addModification(
                        ModificationType.DELETE,
                        passwordAttribute,
                        encodePassword(bindPassword)
                );
                request.addModification(
                        ModificationType.ADD,
                        passwordAttribute,
                        encodePassword(newPassword)
                );
            } else {
                System.err.println("Mode must be admin or self, not " + mode);
                System.exit(1);
            }

            connection.modify(request);

            System.out.println("Successfully changed password for "
                    + targetDN + " to " + newPassword + ".");
        } catch (final LdapException e) {
            System.err.println(e.getMessage());
            System.exit(e.getResult().getResultCode().intValue());
        } catch (final GeneralSecurityException e) {
            System.err.println(e.getMessage());
            System.exit(ResultCode.CLIENT_SIDE_CONNECT_ERROR.intValue());
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
        // --- JCite main ---
    }

    // --- JCite encodePassword ---
    /**
     * Encode new password in UTF-16LE format for use with Active Directory.
     *
     * @param password String representation of the password
     * @return Byte array containing encoded password
     */
    public static byte[] encodePassword(final String password) {
        return ("\"" + password + "\"").getBytes(Charset.forName("UTF-16LE"));
    }
    // --- JCite encodePassword ---

    /**
     * For SSL the connection factory needs SSL context options. This
     * implementation simply trusts all server certificates.
     */
    private static Options getTrustAllOptions() throws GeneralSecurityException {
        Options options = Options.defaultOptions();
        SSLContext sslContext = new SSLContextBuilder()
              .setTrustManager(TrustManagers.trustAll()).getSSLContext();
        options.set(SSL_CONTEXT, sslContext);
        return options;
    }

    /**
     * Constructor not used.
     */
    private PasswordResetForAD() {
        // Not used.
    }
}
