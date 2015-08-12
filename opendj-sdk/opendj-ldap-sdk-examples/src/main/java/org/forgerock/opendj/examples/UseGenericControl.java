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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS.
 */

package org.forgerock.opendj.examples;

import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.controls.GenericControl;
import org.forgerock.opendj.ldap.controls.PreReadResponseControl;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldif.LDIFEntryWriter;

import java.io.IOException;

/**
 * An example client application which uses
 * {@link org.forgerock.opendj.ldap.controls.GenericControl} to pass the
 * pre-read request control from <a href="http://tools.ietf.org/html/rfc4527"
 * >RFC 4527 - Lightweight Directory Access Protocol (LDAP) Read Entry Controls</a>.
 *
 * <br>This example takes the following command line parameters:
 *
 * <pre>
 *  {@code <host> <port> <username> <password> <userDN>}
 * </pre>
 *
 * <br>This example modifies the description attribute of an entry that
 * you specify in the {@code <userDN>} command line parameter.
 */
public final class UseGenericControl {
    /**
     * Main method.
     *
     * @param args The command line arguments: host, port, username, password,
     *             base DN, where the base DN is the root of a naming context.
     */
    public static void main(final String[] args) {
        if (args.length < 5) {
            System.err.println("Usage: host port username password userDN");
            System.exit(1);
        }

        // Parse command line arguments.
        final String hostName = args[0];
        final int port = Integer.parseInt(args[1]);
        final String userName = args[2];
        final String password = args[3];
        final String userDN = args[4];

        // --- JCite ---
        // Create an LDIF writer to write entries to stdout.
        final LDIFEntryWriter writer = new LDIFEntryWriter(System.out);

        // Connect and bind to the server.
        final LDAPConnectionFactory factory =
                new LDAPConnectionFactory(hostName, port);
        Connection connection = null;

        // Prepare the value for the GenericControl.

        // http://tools.ietf.org/html/rfc4527#section-3.1 says:
        // "The Pre-Read request control is a LDAP Control [RFC4511] whose
        // controlType is 1.3.6.1.1.13.1 and whose controlValue is a BER-encoded
        // AttributeSelection [RFC4511], as extended by [RFC3673]."

        ByteStringBuilder builder = new ByteStringBuilder();
        ASN1Writer asn1Writer = ASN1.getWriter(builder);
        try {
            asn1Writer.writeStartSequence();
            asn1Writer.writeOctetString("description");
            asn1Writer.writeEndSequence();
            asn1Writer.flush();
            asn1Writer.close();
        } catch (Exception e) {
            System.out.println("Failed to prepare control value: "
                    + e.getCause());
            System.exit(-1);
        }

        try {
            connection = factory.getConnection();
            connection.bind(userName, password.toCharArray());

            // Modify the user description.
            final ModifyRequest request =
                    Requests
                            .newModifyRequest(userDN)
                            .addModification(ModificationType.REPLACE,
                                    "description", "A new description")
                            .addControl(
                                    GenericControl
                                            .newControl(
                                                    "1.3.6.1.1.13.1",
                                                    true,
                                                    builder.toByteString()));
            final Result result = connection.modify(request);

            // Display the description before and after the modification.
            if (result.isSuccess()) {
                final PreReadResponseControl control = result.getControl(
                        PreReadResponseControl.DECODER, new DecodeOptions()
                );
                final Entry unmodifiedEntry = control.getEntry();
                writer.writeComment("Before modification");
                writer.writeEntry(unmodifiedEntry);
                writer.flush();

                final SearchResultEntry modifiedEntry =
                        connection.searchSingleEntry(
                                userDN,
                                SearchScope.BASE_OBJECT,
                                "(objectclass=*)",
                                "description");
                writer.writeComment("After modification");
                writer.writeEntry(modifiedEntry);
                writer.flush();
            }

        } catch (final LdapException e) {
            System.err.println(e.getMessage());
            System.exit(e.getResult().getResultCode().intValue());
        } catch (final IOException e) {
            System.err.println(e.getMessage());
            System.exit(ResultCode.CLIENT_SIDE_LOCAL_ERROR.intValue());
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
        // --- JCite ---
    }

    private UseGenericControl() {
        // Not used.
    }
}
