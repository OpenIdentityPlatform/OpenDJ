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

package org.forgerock.opendj.ldap.spi;

import java.io.IOException;

import org.forgerock.opendj.ldap.ByteString;
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
 * Interface for handler of LDAP messages (requests and responses).
 *
 */
public interface LDAPMessageHandler {

    /**
     * Handle an abandon request.
     *
     * @param messageID
     *            identifier of message
     * @param request
     *            abandon request to handle
     * @throws UnexpectedRequestException
     *             if request is not expected by this handler
     * @throws IOException
     *             if an errors occurs when sending a subsequent message
     */
    void abandonRequest(int messageID, AbandonRequest request)
            throws UnexpectedRequestException, IOException;

    /**
     * Handle an add request.
     *
     * @param messageID
     *            identifier of message
     * @param request
     *            add request to handle
     * @throws UnexpectedRequestException
     *             if request is not expected by this handler
     * @throws IOException
     *             if an errors occurs when sending a subsequent message
     */
    void addRequest(int messageID, AddRequest request) throws UnexpectedRequestException,
            IOException;

    /**
     * Handle a response to an add request.
     *
     * @param messageID
     *            identifier of message
     * @param result
     *            response received
     * @throws UnexpectedResponseException
     *             if response is not expected by this handler
     * @throws IOException
     *             if an errors occurs when sending a subsequent message
     */
    void addResult(int messageID, Result result) throws UnexpectedResponseException,
            IOException;

    /**
     * Handle a bind request.
     *
     * @param messageID
     *            identifier of message
     * @param version
     *            protocol version for this bind request
     * @param request
     *            bind request to handle
     * @throws UnexpectedRequestException
     *             if request is not expected by this handler
     * @throws IOException
     *             if an errors occurs when sending a subsequent message
     */
    void bindRequest(int messageID, int version, GenericBindRequest request)
            throws UnexpectedRequestException, IOException;

    /**
     * Handle a response to a bind request.
     *
     * @param messageID
     *            identifier of message
     * @param result
     *            response received
     * @throws UnexpectedResponseException
     *             if response is not expected by this handler
     * @throws IOException
     *             if an errors occurs when sending a subsequent message
     */
    void bindResult(int messageID, BindResult result) throws UnexpectedResponseException,
            IOException;

    /**
     * Handle a compare request.
     *
     * @param messageID
     *            identifier of message
     * @param request
     *            compare request to handle
     * @throws UnexpectedRequestException
     *             if request is not expected by this handler
     * @throws IOException
     *             if an errors occurs when sending a subsequent message
     */
    void compareRequest(int messageID, CompareRequest request)
            throws UnexpectedRequestException, IOException;

    /**
     * Handle a response to a compare request.
     *
     * @param messageID
     *            identifier of message
     * @param result
     *            response received
     * @throws UnexpectedResponseException
     *             if response is not expected by this handler
     * @throws IOException
     *             if an errors occurs when sending a subsequent message
     */
    void compareResult(int messageID, CompareResult result)
            throws UnexpectedResponseException, IOException;

    /**
     * Handle an delete request.
     *
     * @param messageID
     *            identifier of message
     * @param request
     *            delete request to handle
     * @throws UnexpectedRequestException
     *             if request is not expected by this handler
     * @throws IOException
     *             if an errors occurs when sending a subsequent message
     */
    void deleteRequest(int messageID, DeleteRequest request)
            throws UnexpectedRequestException, IOException;

    /**
     * Handle a response to a delete request.
     *
     * @param messageID
     *            identifier of message
     * @param result
     *            response received
     * @throws UnexpectedResponseException
     *             if response is not expected by this handler
     * @throws IOException
     *             if an errors occurs when sending a subsequent message
     */
    void deleteResult(int messageID, Result result) throws UnexpectedResponseException,
            IOException;

    /**
     * Handle an extended request.
     *
     * @param <R>
     *            type of extended result
     * @param messageID
     *            identifier of message
     * @param request
     *            extended request to handle
     * @throws UnexpectedRequestException
     *             if request is not expected by this handler
     * @throws IOException
     *             if an errors occurs when sending a subsequent message
     */
    <R extends ExtendedResult> void extendedRequest(int messageID, ExtendedRequest<R> request)
            throws UnexpectedRequestException, IOException;

    /**
     * Handle a response to an extended request.
     *
     * @param messageID
     *            identifier of message
     * @param result
     *            response received
     * @throws UnexpectedResponseException
     *             if response is not expected by this handler
     * @throws IOException
     *             if an errors occurs when sending a subsequent message
     */
    void extendedResult(int messageID, ExtendedResult result)
            throws UnexpectedResponseException, IOException;

    /**
     * Handle an intermediate response.
     *
     * @param messageID
     *            identifier of message
     * @param response
     *            intermediate response to handle
     * @throws UnexpectedResponseException
     *             if response is not expected by this handler
     * @throws IOException
     *             if an errors occurs when sending a subsequent message
     */
    void intermediateResponse(int messageID, IntermediateResponse response)
            throws UnexpectedResponseException, IOException;

    /**
     * Handle a modify DN request.
     *
     * @param messageID
     *            identifier of message
     * @param request
     *            modify DN request to handle
     * @throws UnexpectedRequestException
     *             if request is not expected by this handler
     * @throws IOException
     *             if an errors occurs when sending a subsequent message
     */
    void modifyDNRequest(int messageID, ModifyDNRequest request)
            throws UnexpectedRequestException, IOException;

    /**
     * Handle a response to a modify DN request.
     *
     * @param messageID
     *            identifier of message
     * @param result
     *            response received
     * @throws UnexpectedResponseException
     *             if response is not expected by this handler
     * @throws IOException
     *             if an errors occurs when sending a subsequent message
     */
    void modifyDNResult(int messageID, Result result) throws UnexpectedResponseException,
            IOException;

    /**
     * Handle a modify request.
     *
     * @param messageID
     *            identifier of message
     * @param request
     *            modify request to handle
     * @throws UnexpectedRequestException
     *             if request is not expected by this handler
     * @throws IOException
     *             if an errors occurs when sending a subsequent message
     */
    void modifyRequest(int messageID, ModifyRequest request)
            throws UnexpectedRequestException, IOException;

    /**
     * Handle a response to a modify request.
     *
     * @param messageID
     *            identifier of message
     * @param result
     *            response received
     * @throws UnexpectedResponseException
     *             if response is not expected by this handler
     * @throws IOException
     *             if an errors occurs when sending a subsequent message
     */
    void modifyResult(int messageID, Result result) throws UnexpectedResponseException,
            IOException;

    /**
     * Handle a search request.
     *
     * @param messageID
     *            identifier of message
     * @param request
     *            search request to handle
     * @throws UnexpectedRequestException
     *             if request is not expected by this handler
     * @throws IOException
     *             if an errors occurs when sending a subsequent message
     */
    void searchRequest(int messageID, SearchRequest request)
            throws UnexpectedRequestException, IOException;

    /**
     * Handle a response to a search request.
     *
     * @param messageID
     *            identifier of message
     * @param result
     *            response received
     * @throws UnexpectedResponseException
     *             if response is not expected by this handler
     * @throws IOException
     *             if an errors occurs when sending a subsequent message
     */
    void searchResult(int messageID, Result result) throws UnexpectedResponseException,
            IOException;

    /**
     * Handle a response to a search request returning an entry.
     *
     * @param messageID
     *            identifier of message
     * @param entry
     *            entry received
     * @throws UnexpectedResponseException
     *             if response is not expected by this handler
     * @throws IOException
     *             if an errors occurs when sending a subsequent message
     */
    void searchResultEntry(int messageID, SearchResultEntry entry)
            throws UnexpectedResponseException, IOException;

    /**
     * Handle a response to a search request returning an reference.
     *
     * @param messageID
     *            identifier of message
     * @param reference
     *            reference received
     * @throws UnexpectedResponseException
     *             if response is not expected by this handler
     * @throws IOException
     *             if an errors occurs when sending a subsequent message
     */
    void searchResultReference(int messageID, SearchResultReference reference)
            throws UnexpectedResponseException, IOException;

    /**
     * Handle an unbind request.
     *
     * @param messageID
     *            identifier of message
     * @param request
     *            unbind request to handle
     * @throws UnexpectedRequestException
     *             if request is not expected by this handler
     * @throws IOException
     *             if an errors occurs when sending a subsequent message
     */
    void unbindRequest(int messageID, UnbindRequest request)
            throws UnexpectedRequestException, IOException;

    /**
     * Handle an unrecognized message.
     *
     * @param messageID
     *            identifier of message
     * @param messageTag
     *            tag identifying the message type
     * @param messageBytes
     *          content of the message
     * @throws UnsupportedMessageException
     *             if the received message is not supported
     * @throws IOException
     *             if an errors occurs when sending a subsequent message
     */
    void unrecognizedMessage(int messageID, byte messageTag, ByteString messageBytes)
            throws UnsupportedMessageException, IOException;
}
