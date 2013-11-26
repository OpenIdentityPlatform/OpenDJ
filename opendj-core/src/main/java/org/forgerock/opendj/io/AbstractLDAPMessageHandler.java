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
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions copyright 2013 ForgeRock AS.
 */

package org.forgerock.opendj.io;

import java.io.IOException;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.requests.AbandonRequest;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ExtendedRequest;
import org.forgerock.opendj.ldap.requests.GenericBindRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Request;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.requests.UnbindRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.IntermediateResponse;
import org.forgerock.opendj.ldap.responses.Response;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;

/**
 * This class provides a skeletal implementation of the
 * {@link LDAPMessageHandler} interface, in order to minimize the effort
 * required to implement this interface. By default each method throws a fatal
 * {@link DecodeException}.
 */
public abstract class AbstractLDAPMessageHandler implements LDAPMessageHandler {
    /**
     * Default constructor.
     */
    protected AbstractLDAPMessageHandler() {
        // No implementation required.
    }

    @Override
    public void abandonRequest(final int messageID, final AbandonRequest request)
            throws DecodeException, IOException {
        throw newUnexpectedRequestException(messageID, request);
    }

    @Override
    public void addRequest(final int messageID, final AddRequest request) throws DecodeException,
            IOException {
        throw newUnexpectedRequestException(messageID, request);
    }

    @Override
    public void addResult(final int messageID, final Result result) throws DecodeException,
            IOException {
        throw newUnexpectedResponseException(messageID, result);
    }

    @Override
    public void bindRequest(final int messageID, final int version, final GenericBindRequest request)
            throws DecodeException, IOException {
        throw newUnexpectedRequestException(messageID, request);
    }

    @Override
    public void bindResult(final int messageID, final BindResult result) throws DecodeException,
            IOException {
        throw newUnexpectedResponseException(messageID, result);
    }

    @Override
    public void compareRequest(final int messageID, final CompareRequest request)
            throws DecodeException, IOException {
        throw newUnexpectedRequestException(messageID, request);
    }

    @Override
    public void compareResult(final int messageID, final CompareResult result)
            throws DecodeException, IOException {
        throw newUnexpectedResponseException(messageID, result);
    }

    @Override
    public void deleteRequest(final int messageID, final DeleteRequest request)
            throws DecodeException, IOException {
        throw newUnexpectedRequestException(messageID, request);
    }

    @Override
    public void deleteResult(final int messageID, final Result result) throws DecodeException,
            IOException {
        throw newUnexpectedResponseException(messageID, result);
    }

    @Override
    public <R extends ExtendedResult> void extendedRequest(final int messageID,
            final ExtendedRequest<R> request) throws DecodeException, IOException {
        throw newUnexpectedRequestException(messageID, request);
    }

    @Override
    public void extendedResult(final int messageID, final ExtendedResult result)
            throws DecodeException, IOException {
        throw newUnexpectedResponseException(messageID, result);
    }

    @Override
    public void intermediateResponse(final int messageID, final IntermediateResponse response)
            throws DecodeException, IOException {
        throw newUnexpectedResponseException(messageID, response);
    }

    @Override
    public void modifyDNRequest(final int messageID, final ModifyDNRequest request)
            throws DecodeException, IOException {
        throw newUnexpectedRequestException(messageID, request);
    }

    @Override
    public void modifyDNResult(final int messageID, final Result result) throws DecodeException,
            IOException {
        throw newUnexpectedResponseException(messageID, result);
    }

    @Override
    public void modifyRequest(final int messageID, final ModifyRequest request)
            throws DecodeException, IOException {
        throw newUnexpectedRequestException(messageID, request);
    }

    @Override
    public void modifyResult(final int messageID, final Result result) throws DecodeException,
            IOException {
        throw newUnexpectedResponseException(messageID, result);
    }

    @Override
    public void searchRequest(final int messageID, final SearchRequest request)
            throws DecodeException, IOException {
        throw newUnexpectedRequestException(messageID, request);
    }

    @Override
    public void searchResult(final int messageID, final Result result) throws DecodeException,
            IOException {
        throw newUnexpectedResponseException(messageID, result);
    }

    @Override
    public void searchResultEntry(final int messageID, final SearchResultEntry entry)
            throws DecodeException, IOException {
        throw newUnexpectedResponseException(messageID, entry);
    }

    @Override
    public void searchResultReference(final int messageID, final SearchResultReference reference)
            throws DecodeException, IOException {
        throw newUnexpectedResponseException(messageID, reference);
    }

    @Override
    public void unbindRequest(final int messageID, final UnbindRequest request)
            throws DecodeException, IOException {
        throw newUnexpectedRequestException(messageID, request);
    }

    @Override
    public void unrecognizedMessage(final int messageID, final byte messageTag,
            final ByteString messageBytes) throws DecodeException, IOException {
        throw newUnsupportedMessageException(messageID, messageTag, messageBytes);
    }

    /**
     * Returns a decoding exception suitable for use when an unsupported LDAP
     * message is received.
     *
     * @param messageID
     *            The LDAP message ID.
     * @param messageTag
     *            The LDAP message type.
     * @param messageBytes
     *            The LDAP message content.
     * @return A decoding exception suitable for use when an unsupported LDAP
     *         message is received.
     */
    protected DecodeException newUnsupportedMessageException(final int messageID,
            final byte messageTag, final ByteString messageBytes) {
        return DecodeException.fatalError(LocalizableMessage.raw(
                "Unsupported LDAP message: id=%d, tag=%d, content=%s", messageID, messageTag,
                messageBytes));
    }

    /**
     * Returns a decoding exception suitable for use when an unexpected LDAP
     * request is received.
     *
     * @param messageID
     *            The LDAP message ID.
     * @param request
     *            The LDAP request.
     * @return A decoding exception suitable for use when an unexpected LDAP
     *         request is received.
     */
    protected DecodeException newUnexpectedRequestException(final int messageID,
            final Request request) {
        return DecodeException.fatalError(LocalizableMessage.raw(
                "Unexpected LDAP request: id=%d, message=%s", messageID, request));
    }

    /**
     * Returns a decoding exception suitable for use when an unexpected LDAP
     * response is received.
     *
     * @param messageID
     *            The LDAP message ID.
     * @param response
     *            The LDAP response.
     * @return A decoding exception suitable for use when an unexpected LDAP
     *         response is received.
     */
    protected DecodeException newUnexpectedResponseException(final int messageID,
            final Response response) {
        return DecodeException.fatalError(LocalizableMessage.raw(
                "Unexpected LDAP response: id=%d, message=%s", messageID, response));
    }
}
