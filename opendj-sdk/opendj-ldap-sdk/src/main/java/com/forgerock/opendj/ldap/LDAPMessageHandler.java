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
 */

package com.forgerock.opendj.ldap;

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
 * LDAP message handler interface.
 *
 * @param <P>
 *            A user provided handler parameter.
 */
interface LDAPMessageHandler<P> {
    void abandonRequest(P param, int messageID, AbandonRequest request)
            throws UnexpectedRequestException, IOException;

    void addRequest(P param, int messageID, AddRequest request) throws UnexpectedRequestException,
            IOException;

    void addResult(P param, int messageID, Result result) throws UnexpectedResponseException,
            IOException;

    void bindRequest(P param, int messageID, int version, GenericBindRequest request)
            throws UnexpectedRequestException, IOException;

    void bindResult(P param, int messageID, BindResult result) throws UnexpectedResponseException,
            IOException;

    void compareRequest(P param, int messageID, CompareRequest request)
            throws UnexpectedRequestException, IOException;

    void compareResult(P param, int messageID, CompareResult result)
            throws UnexpectedResponseException, IOException;

    void deleteRequest(P param, int messageID, DeleteRequest request)
            throws UnexpectedRequestException, IOException;

    void deleteResult(P param, int messageID, Result result) throws UnexpectedResponseException,
            IOException;

    <R extends ExtendedResult> void extendedRequest(P param, int messageID,
            ExtendedRequest<R> request) throws UnexpectedRequestException, IOException;

    void extendedResult(P param, int messageID, ExtendedResult result)
            throws UnexpectedResponseException, IOException;

    void intermediateResponse(P param, int messageID, IntermediateResponse response)
            throws UnexpectedResponseException, IOException;

    void modifyDNRequest(P param, int messageID, ModifyDNRequest request)
            throws UnexpectedRequestException, IOException;

    void modifyDNResult(P param, int messageID, Result result) throws UnexpectedResponseException,
            IOException;

    void modifyRequest(P param, int messageID, ModifyRequest request)
            throws UnexpectedRequestException, IOException;

    void modifyResult(P param, int messageID, Result result) throws UnexpectedResponseException,
            IOException;

    void searchRequest(P param, int messageID, SearchRequest request)
            throws UnexpectedRequestException, IOException;

    void searchResult(P param, int messageID, Result result) throws UnexpectedResponseException,
            IOException;

    void searchResultEntry(P param, int messageID, SearchResultEntry entry)
            throws UnexpectedResponseException, IOException;

    void searchResultReference(P param, int messageID, SearchResultReference reference)
            throws UnexpectedResponseException, IOException;

    void unbindRequest(P param, int messageID, UnbindRequest request)
            throws UnexpectedRequestException, IOException;

    void unrecognizedMessage(P param, int messageID, byte messageTag, ByteString messageBytes)
            throws UnsupportedMessageException, IOException;
}
