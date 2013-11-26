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
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.requests.UnbindRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.IntermediateResponse;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;

/**
 * An interface for handling LDAP messages decoded using an {@link LDAPReader}.
 */
public interface LDAPMessageHandler {

    /**
     * Handles an LDAP abandon request message.
     *
     * @param messageID
     *            The LDAP message ID.
     * @param request
     *            The decoded abandon request.
     * @throws DecodeException
     *             If this handler does not support abandon requests.
     * @throws IOException
     *             If an unexpected IO error occurred while processing the
     *             request.
     */
    void abandonRequest(int messageID, AbandonRequest request) throws DecodeException, IOException;

    /**
     * Handles an LDAP add request message.
     *
     * @param messageID
     *            The LDAP message ID.
     * @param request
     *            The decoded add request.
     * @throws DecodeException
     *             If this handler does not support add requests.
     * @throws IOException
     *             If an unexpected IO error occurred while processing the
     *             request.
     */
    void addRequest(int messageID, AddRequest request) throws DecodeException, IOException;

    /**
     * Handles an LDAP add result message.
     *
     * @param messageID
     *            The LDAP message ID.
     * @param result
     *            The decoded add result.
     * @throws DecodeException
     *             If this handler does not support add results.
     * @throws IOException
     *             If an unexpected IO error occurred while processing the
     *             response.
     */
    void addResult(int messageID, Result result) throws DecodeException, IOException;

    /**
     * Handles an LDAP bind request message.
     *
     * @param messageID
     *            The LDAP message ID.
     * @param version
     *            The requested LDAP protocol version.
     * @param request
     *            The decoded bind request.
     * @throws DecodeException
     *             If this handler does not support bind requests.
     * @throws IOException
     *             If an unexpected IO error occurred while processing the
     *             request.
     */
    void bindRequest(int messageID, int version, GenericBindRequest request)
            throws DecodeException, IOException;

    /**
     * Handles an LDAP bind result message.
     *
     * @param messageID
     *            The LDAP message ID.
     * @param result
     *            The decoded bind result.
     * @throws DecodeException
     *             If this handler does not support bind results.
     * @throws IOException
     *             If an unexpected IO error occurred while processing the
     *             response.
     */
    void bindResult(int messageID, BindResult result) throws DecodeException, IOException;

    /**
     * Handles an LDAP compare request message.
     *
     * @param messageID
     *            The LDAP message ID.
     * @param request
     *            The decoded compare request.
     * @throws DecodeException
     *             If this handler does not support compare requests.
     * @throws IOException
     *             If an unexpected IO error occurred while processing the
     *             request.
     */
    void compareRequest(int messageID, CompareRequest request) throws DecodeException, IOException;

    /**
     * Handles an LDAP compare result message.
     *
     * @param messageID
     *            The LDAP message ID.
     * @param result
     *            The decoded compare result.
     * @throws DecodeException
     *             If this handler does not support compare results.
     * @throws IOException
     *             If an unexpected IO error occurred while processing the
     *             response.
     */
    void compareResult(int messageID, CompareResult result) throws DecodeException, IOException;

    /**
     * Handles an LDAP delete request message.
     *
     * @param messageID
     *            The LDAP message ID.
     * @param request
     *            The decoded delete request.
     * @throws DecodeException
     *             If this handler does not support delete requests.
     * @throws IOException
     *             If an unexpected IO error occurred while processing the
     *             request.
     */
    void deleteRequest(int messageID, DeleteRequest request) throws DecodeException, IOException;

    /**
     * Handles an LDAP delete result message.
     *
     * @param messageID
     *            The LDAP message ID.
     * @param result
     *            The decoded delete result.
     * @throws DecodeException
     *             If this handler does not support delete results.
     * @throws IOException
     *             If an unexpected IO error occurred while processing the
     *             response.
     */
    void deleteResult(int messageID, Result result) throws DecodeException, IOException;

    /**
     * Handles an LDAP extended request message.
     *
     * @param <R>
     *            type of extended result
     * @param messageID
     *            The LDAP message ID.
     * @param request
     *            The decoded extended request.
     * @throws DecodeException
     *             If this handler does not support extended requests.
     * @throws IOException
     *             If an unexpected IO error occurred while processing the
     *             request.
     */
    <R extends ExtendedResult> void extendedRequest(int messageID, ExtendedRequest<R> request)
            throws DecodeException, IOException;

    /**
     * Handles an LDAP extended result message.
     *
     * @param messageID
     *            The LDAP message ID.
     * @param result
     *            The decoded extended result.
     * @throws DecodeException
     *             If this handler does not support extended results.
     * @throws IOException
     *             If an unexpected IO error occurred while processing the
     *             response.
     */
    void extendedResult(int messageID, ExtendedResult result) throws DecodeException, IOException;

    /**
     * Handles an LDAP intermediate response message.
     *
     * @param messageID
     *            The LDAP message ID.
     * @param response
     *            The decoded intermediate response.
     * @throws DecodeException
     *             If this handler does not support intermediate responses.
     * @throws IOException
     *             If an unexpected IO error occurred while processing the
     *             response.
     */
    void intermediateResponse(int messageID, IntermediateResponse response) throws DecodeException,
            IOException;

    /**
     * Handles an LDAP modify DN request message.
     *
     * @param messageID
     *            The LDAP message ID.
     * @param request
     *            The decoded modify DN request.
     * @throws DecodeException
     *             If this handler does not support modify DN requests.
     * @throws IOException
     *             If an unexpected IO error occurred while processing the
     *             request.
     */
    void modifyDNRequest(int messageID, ModifyDNRequest request) throws DecodeException,
            IOException;

    /**
     * Handles an LDAP modify DN result message.
     *
     * @param messageID
     *            The LDAP message ID.
     * @param result
     *            The decoded modify DN result.
     * @throws DecodeException
     *             If this handler does not support modify DN results.
     * @throws IOException
     *             If an unexpected IO error occurred while processing the
     *             response.
     */
    void modifyDNResult(int messageID, Result result) throws DecodeException, IOException;

    /**
     * Handles an LDAP modify request message.
     *
     * @param messageID
     *            The LDAP message ID.
     * @param request
     *            The decoded modify request.
     * @throws DecodeException
     *             If this handler does not support modify requests.
     * @throws IOException
     *             If an unexpected IO error occurred while processing the
     *             request.
     */
    void modifyRequest(int messageID, ModifyRequest request) throws DecodeException, IOException;

    /**
     * Handles an LDAP modify result message.
     *
     * @param messageID
     *            The LDAP message ID.
     * @param result
     *            The decoded modify result.
     * @throws DecodeException
     *             If this handler does not support modify results.
     * @throws IOException
     *             If an unexpected IO error occurred while processing the
     *             response.
     */
    void modifyResult(int messageID, Result result) throws DecodeException, IOException;

    /**
     * Handles an LDAP search request message.
     *
     * @param messageID
     *            The LDAP message ID.
     * @param request
     *            The decoded search request.
     * @throws DecodeException
     *             If this handler does not support search requests.
     * @throws IOException
     *             If an unexpected IO error occurred while processing the
     *             request.
     */
    void searchRequest(int messageID, SearchRequest request) throws DecodeException, IOException;

    /**
     * Handles an LDAP search result message.
     *
     * @param messageID
     *            The LDAP message ID.
     * @param result
     *            The decoded search result.
     * @throws DecodeException
     *             If this handler does not support search results.
     * @throws IOException
     *             If an unexpected IO error occurred while processing the
     *             response.
     */
    void searchResult(int messageID, Result result) throws DecodeException, IOException;

    /**
     * Handles an LDAP search result entry message.
     *
     * @param messageID
     *            The LDAP message ID.
     * @param entry
     *            The decoded search result entry.
     * @throws DecodeException
     *             If this handler does not support search result entries.
     * @throws IOException
     *             If an unexpected IO error occurred while processing the
     *             response.
     */
    void searchResultEntry(int messageID, SearchResultEntry entry) throws DecodeException,
            IOException;

    /**
     * Handles an LDAP search result reference message.
     *
     * @param messageID
     *            The LDAP message ID.
     * @param reference
     *            The decoded search result reference.
     * @throws DecodeException
     *             If this handler does not support search result references.
     * @throws IOException
     *             If an unexpected IO error occurred while processing the
     *             response.
     */
    void searchResultReference(int messageID, SearchResultReference reference)
            throws DecodeException, IOException;

    /**
     * Handles an LDAP unbind request message.
     *
     * @param messageID
     *            The LDAP message ID.
     * @param request
     *            The decoded unbind request.
     * @throws DecodeException
     *             If this handler does not support unbind requests.
     * @throws IOException
     *             If an unexpected IO error occurred while processing the
     *             request.
     */
    void unbindRequest(int messageID, UnbindRequest request) throws DecodeException, IOException;

    /**
     * Handles an unrecognized LDAP message.
     *
     * @param messageID
     *            The LDAP message ID.
     * @param messageTag
     *            The LDAP message type.
     * @param messageBytes
     *            The contents of the LDAP message.
     * @throws DecodeException
     *             If this handler does not support the message type.
     * @throws IOException
     *             If an unexpected IO error occurred while processing the
     *             message.
     */
    void unrecognizedMessage(int messageID, byte messageTag, ByteString messageBytes)
            throws DecodeException, IOException;
}
