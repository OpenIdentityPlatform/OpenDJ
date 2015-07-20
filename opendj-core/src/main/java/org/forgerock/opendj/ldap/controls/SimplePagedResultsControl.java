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
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions Copyright 2012-2014 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.controls;

import static com.forgerock.opendj.ldap.CoreMessages.*;

import java.io.IOException;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;

import org.forgerock.util.Reject;

/**
 * The simple paged results request and response control as defined in RFC 2696.
 * This control allows a client to control the rate at which an LDAP server
 * returns the results of an LDAP search operation. This control may be useful
 * when the LDAP client has limited resources and may not be able to process the
 * entire result set from a given LDAP query, or when the LDAP client is
 * connected over a low-bandwidth connection.
 * <p>
 * This control is included in the searchRequest and searchResultDone messages
 * and has the following structure:
 *
 * <pre>
 * realSearchControlValue ::= SEQUENCE {
 *         size            INTEGER (0..maxInt),
 *                                 -- requested page size from client
 *                                 -- result set size estimate from server
 *         cookie          OCTET STRING
 * }
 * </pre>
 * <p>
 * The following example demonstrates use of simple paged results to handle
 * three entries at a time.
 *
 * <pre>
 * ByteString cookie = ByteString.empty();
 * SearchRequest request;
 * SearchResultHandler resultHandler = new MySearchResultHandler();
 * Result result;
 *
 * int page = 1;
 * do {
        System.out.println("# Simple paged results: Page " + page);
 *
 *      request = Requests.newSearchRequest(
                "dc=example,dc=com", SearchScope.WHOLE_SUBTREE, "(sn=Jensen)", "cn")
                .addControl(SimplePagedResultsControl.newControl(true, 3, cookie));
 *
 *      result = connection.search(request, resultHandler);
 *      try {
 *      SimplePagedResultsControl control = result.getControl(
                SimplePagedResultsControl.DECODER, new DecodeOptions());
        cookie = control.getCookie();
        } catch (final DecodeException e) {
            // Failed to decode the response control.
        }
 *
 *      ++page;
 * } while (cookie.length() != 0);
 * </pre>
 *
 * The search result handler in this case displays pages of results as LDIF on
 * standard out.
 *
 * <pre>
 * private static class MySearchResultHandler implements SearchResultHandler {
 *
 *     {@literal @}Override
 *     public void handleExceptionResult(LdapException error) {
 *         // Ignore.
 *     }
 *
 *     {@literal @}Override
 *     public void handleResult(Result result) {
 *         // Ignore.
 *     }
 *
 *     {@literal @}Override
 *     public boolean handleEntry(SearchResultEntry entry) {
 *         final LDIFEntryWriter writer = new LDIFEntryWriter(System.out);
 *         try {
 *             writer.writeEntry(entry);
 *             writer.flush();
 *         } catch (final IOException e) {
 *             // The writer could not write to System.out.
 *         }
 *         return true;
 *     }
 *
 *     {@literal @}Override
 *     public boolean handleReference(SearchResultReference reference) {
 *         System.out.println("Got a reference: " + reference.toString());
 *         return false;
 *     }
 * }
 * </pre>
 *
 * @see <a href="http://tools.ietf.org/html/rfc2696">RFC 2696 - LDAP Control
 *      Extension for Simple Paged Results Manipulation </a>
 */
public final class SimplePagedResultsControl implements Control {

    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();
    /**
     * The OID for the paged results request/response control defined in RFC
     * 2696.
     */
    public static final String OID = "1.2.840.113556.1.4.319";

    /**
     * A decoder which can be used for decoding the simple paged results
     * control.
     */
    public static final ControlDecoder<SimplePagedResultsControl> DECODER =
            new ControlDecoder<SimplePagedResultsControl>() {

                public SimplePagedResultsControl decodeControl(final Control control,
                        final DecodeOptions options) throws DecodeException {
                    Reject.ifNull(control);

                    if (control instanceof SimplePagedResultsControl) {
                        return (SimplePagedResultsControl) control;
                    }

                    if (!control.getOID().equals(OID)) {
                        throw DecodeException.error(ERR_LDAP_PAGED_RESULTS_CONTROL_BAD_OID.get(control.getOID(), OID));
                    }

                    if (!control.hasValue()) {
                        // The control must always have a value.
                        throw DecodeException.error(ERR_LDAP_PAGED_RESULTS_DECODE_NULL.get());
                    }

                    final ASN1Reader reader = ASN1.getReader(control.getValue());
                    try {
                        reader.readStartSequence();
                    } catch (final Exception e) {
                        logger.debug(LocalizableMessage.raw("Unable to read start sequence", e));
                        throw DecodeException.error(ERR_LDAP_PAGED_RESULTS_DECODE_SEQUENCE.get(e), e);
                    }

                    int size;
                    try {
                        size = (int) reader.readInteger();
                    } catch (final Exception e) {
                        logger.debug(LocalizableMessage.raw("Unable to read size", e));
                        throw DecodeException.error(ERR_LDAP_PAGED_RESULTS_DECODE_SIZE.get(e), e);
                    }

                    ByteString cookie;
                    try {
                        cookie = reader.readOctetString();
                    } catch (final Exception e) {
                        logger.debug(LocalizableMessage.raw("Unable to read cookie", e));
                        throw DecodeException.error(ERR_LDAP_PAGED_RESULTS_DECODE_COOKIE.get(e), e);
                    }

                    try {
                        reader.readEndSequence();
                    } catch (final Exception e) {
                        logger.debug(LocalizableMessage.raw("Unable to read end sequence", e));
                        throw DecodeException.error(ERR_LDAP_PAGED_RESULTS_DECODE_SEQUENCE.get(e), e);
                    }

                    return new SimplePagedResultsControl(control.isCritical(), size, cookie);
                }

                public String getOID() {
                    return OID;
                }
            };

    /**
     * Creates a new simple paged results control with the provided criticality,
     * size, and cookie.
     *
     * @param isCritical
     *            {@code true} if it is unacceptable to perform the operation
     *            without applying the semantics of this control, or
     *            {@code false} if it can be ignored.
     * @param size
     *            The requested page size when used in a request control from
     *            the client, or an estimate of the result set size when used in
     *            a response control from the server (may be 0, indicating that
     *            the server does not know).
     * @param cookie
     *            An opaque cookie which is used by the server to track its
     *            position in the set of search results. The cookie must be
     *            empty in the initial search request sent by the client. For
     *            subsequent search requests the client must include the cookie
     *            returned with the previous search result, until the server
     *            returns an empty cookie indicating that the final page of
     *            results has been returned.
     * @return The new control.
     * @throws NullPointerException
     *             If {@code cookie} was {@code null}.
     */
    public static SimplePagedResultsControl newControl(final boolean isCritical, final int size,
            final ByteString cookie) {
        Reject.ifNull(cookie);
        return new SimplePagedResultsControl(isCritical, size, cookie);
    }

    /**
     * The control value size element, which is either the requested page size
     * from the client, or the result set size estimate from the server.
     */
    private final int size;

    /**
     * The control value cookie element.
     */
    private final ByteString cookie;

    private final boolean isCritical;

    private SimplePagedResultsControl(final boolean isCritical, final int size,
            final ByteString cookie) {
        this.isCritical = isCritical;
        this.size = size;
        this.cookie = cookie;
    }

    /**
     * Returns the opaque cookie which is used by the server to track its
     * position in the set of search results. The cookie must be empty in the
     * initial search request sent by the client. For subsequent search requests
     * the client must include the cookie returned with the previous search
     * result, until the server returns an empty cookie indicating that the
     * final page of results has been returned.
     *
     * @return The opaque cookie which is used by the server to track its
     *         position in the set of search results.
     */
    public ByteString getCookie() {
        return cookie;
    }

    /** {@inheritDoc} */
    public String getOID() {
        return OID;
    }

    /**
     * Returns the requested page size when used in a request control from the
     * client, or an estimate of the result set size when used in a response
     * control from the server (may be 0, indicating that the server does not
     * know).
     *
     * @return The requested page size when used in a request control from the
     *         client, or an estimate of the result set size when used in a
     *         response control from the server.
     */
    public int getSize() {
        return size;
    }

    /** {@inheritDoc} */
    public ByteString getValue() {
        final ByteStringBuilder buffer = new ByteStringBuilder();
        final ASN1Writer writer = ASN1.getWriter(buffer);
        try {
            writer.writeStartSequence();
            writer.writeInteger(size);
            writer.writeOctetString(cookie);
            writer.writeEndSequence();
            return buffer.toByteString();
        } catch (final IOException ioe) {
            // This should never happen unless there is a bug somewhere.
            throw new RuntimeException(ioe);
        }
    }

    /** {@inheritDoc} */
    public boolean hasValue() {
        return true;
    }

    /** {@inheritDoc} */
    public boolean isCritical() {
        return isCritical;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("SimplePagedResultsControl(oid=");
        builder.append(getOID());
        builder.append(", criticality=");
        builder.append(isCritical());
        builder.append(", size=");
        builder.append(size);
        builder.append(", cookie=");
        builder.append(cookie);
        builder.append(")");
        return builder.toString();
    }
}
