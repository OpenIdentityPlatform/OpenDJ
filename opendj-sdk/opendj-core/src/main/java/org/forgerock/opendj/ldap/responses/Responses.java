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
 *      Portions copyright 2012-2015 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.responses;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.LinkedHashMapEntry;
import org.forgerock.opendj.ldap.ResultCode;

import org.forgerock.util.Reject;

/**
 * This class contains various methods for creating and manipulating responses.
 * <p>
 * All copy constructors of the form {@code copyOfXXXResult} perform deep copies
 * of their response parameter. More specifically, any controls, modifications,
 * and attributes contained within the response will be duplicated.
 * <p>
 * Similarly, all unmodifiable views of responses returned by methods of the
 * form {@code unmodifiableXXXResult} return deep unmodifiable views of their
 * response parameter. More specifically, any controls, modifications, and
 * attributes contained within the returned response will be unmodifiable.
 */
public final class Responses {

    // TODO: search reference from LDAP URL.

    // TODO: referral from LDAP URL.

    // TODO: synchronized requests?

    /**
     * Creates a new bind result that is an exact copy of the provided result.
     *
     * @param result
     *            The bind result to be copied.
     * @return The new bind result.
     * @throws NullPointerException
     *             If {@code result} was {@code null}.
     */
    public static BindResult copyOfBindResult(final BindResult result) {
        return new BindResultImpl(result);
    }

    /**
     * Creates a new compare result that is an exact copy of the provided
     * result.
     *
     * @param result
     *            The compare result to be copied.
     * @return The new compare result.
     * @throws NullPointerException
     *             If {@code result} was {@code null}.
     */
    public static CompareResult copyOfCompareResult(final CompareResult result) {
        return new CompareResultImpl(result);
    }

    /**
     * Creates a new generic extended result that is an exact copy of the
     * provided result.
     *
     * @param result
     *            The generic extended result to be copied.
     * @return The new generic extended result.
     * @throws NullPointerException
     *             If {@code result} was {@code null}.
     */
    public static GenericExtendedResult copyOfGenericExtendedResult(
            final GenericExtendedResult result) {
        return new GenericExtendedResultImpl(result);
    }

    /**
     * Creates a new generic intermediate response that is an exact copy of the
     * provided response.
     *
     * @param result
     *            The generic intermediate response to be copied.
     * @return The new generic intermediate response.
     * @throws NullPointerException
     *             If {@code result} was {@code null}.
     */
    public static GenericIntermediateResponse copyOfGenericIntermediateResponse(
            final GenericIntermediateResponse result) {
        return new GenericIntermediateResponseImpl(result);
    }

    /**
     * Creates a new password modify extended result that is an exact copy of
     * the provided result.
     *
     * @param result
     *            The password modify extended result to be copied.
     * @return The new password modify extended result.
     * @throws NullPointerException
     *             If {@code result} was {@code null}.
     */
    public static PasswordModifyExtendedResult copyOfPasswordModifyExtendedResult(
            final PasswordModifyExtendedResult result) {
        return new PasswordModifyExtendedResultImpl(result);
    }

    /**
     * Creates a new result that is an exact copy of the provided result.
     *
     * @param result
     *            The result to be copied.
     * @return The new result.
     * @throws NullPointerException
     *             If {@code result} was {@code null}.
     */
    public static Result copyOfResult(final Result result) {
        return new ResultImpl(result);
    }

    /**
     * Creates a new search result entry that is an exact copy of the provided
     * result.
     *
     * @param entry
     *            The search result entry to be copied.
     * @return The new search result entry.
     * @throws NullPointerException
     *             If {@code entry} was {@code null}.
     */
    public static SearchResultEntry copyOfSearchResultEntry(final SearchResultEntry entry) {
        return new SearchResultEntryImpl(entry);
    }

    /**
     * Creates a new search result reference that is an exact copy of the
     * provided result.
     *
     * @param reference
     *            The search result reference to be copied.
     * @return The new search result reference.
     * @throws NullPointerException
     *             If {@code reference} was {@code null}.
     */
    public static SearchResultReference copyOfSearchResultReference(
            final SearchResultReference reference) {
        return new SearchResultReferenceImpl(reference);
    }

    /**
     * Creates a new who am I extended result that is an exact copy of the
     * provided result.
     *
     * @param result
     *            The who am I result to be copied.
     * @return The new who am I extended result.
     * @throws NullPointerException
     *             If {@code result} was {@code null} .
     */
    public static WhoAmIExtendedResult copyOfWhoAmIExtendedResult(final WhoAmIExtendedResult result) {
        return new WhoAmIExtendedResultImpl(result);
    }

    /**
     * Creates a new bind result using the provided result code.
     *
     * @param resultCode
     *            The result code.
     * @return The new bind result.
     * @throws NullPointerException
     *             If {@code resultCode} was {@code null}.
     */
    public static BindResult newBindResult(final ResultCode resultCode) {
        Reject.ifNull(resultCode);
        return new BindResultImpl(resultCode);
    }

    /**
     * Creates a new compare result using the provided result code.
     *
     * @param resultCode
     *            The result code.
     * @return The new compare result.
     * @throws NullPointerException
     *             If {@code resultCode} was {@code null}.
     */
    public static CompareResult newCompareResult(final ResultCode resultCode) {
        Reject.ifNull(resultCode);
        return new CompareResultImpl(resultCode);
    }

    /**
     * Creates a new generic extended result using the provided result code.
     *
     * @param resultCode
     *            The result code.
     * @return The new generic extended result.
     * @throws NullPointerException
     *             If {@code resultCode} was {@code null}.
     */
    public static GenericExtendedResult newGenericExtendedResult(final ResultCode resultCode) {
        Reject.ifNull(resultCode);
        return new GenericExtendedResultImpl(resultCode);
    }

    /**
     * Creates a new generic intermediate response with no name or value.
     *
     * @return The new generic intermediate response.
     */
    public static GenericIntermediateResponse newGenericIntermediateResponse() {
        return new GenericIntermediateResponseImpl();
    }

    /**
     * Creates a new generic intermediate response using the provided response
     * name and value.
     * <p>
     * If the response value is not an instance of {@code ByteString} then it
     * will be converted using the {@link ByteString#valueOfObject(Object)} method.
     *
     * @param responseName
     *            The dotted-decimal representation of the unique OID
     *            corresponding to this intermediate response, which may be
     *            {@code null} indicating that none was provided.
     * @param responseValue
     *            The response value associated with this generic intermediate
     *            response, which may be {@code null} indicating that none was
     *            provided.
     * @return The new generic intermediate response.
     */
    public static GenericIntermediateResponse newGenericIntermediateResponse(
            final String responseName, final Object responseValue) {
        return new GenericIntermediateResponseImpl().setOID(responseName).setValue(responseValue);
    }

    /**
     * Creates a new password modify extended result using the provided result
     * code, and no generated password.
     *
     * @param resultCode
     *            The result code.
     * @return The new password modify extended result.
     * @throws NullPointerException
     *             If {@code resultCode} was {@code null}.
     */
    public static PasswordModifyExtendedResult newPasswordModifyExtendedResult(
            final ResultCode resultCode) {
        Reject.ifNull(resultCode);
        return new PasswordModifyExtendedResultImpl(resultCode);
    }

    /**
     * Creates a new result using the provided result code.
     *
     * @param resultCode
     *            The result code.
     * @return The new result.
     * @throws NullPointerException
     *             If {@code resultCode} was {@code null}.
     */
    public static Result newResult(final ResultCode resultCode) {
        Reject.ifNull(resultCode);
        return new ResultImpl(resultCode);
    }

    /**
     * Creates a new search result entry using the provided distinguished name.
     *
     * @param name
     *            The distinguished name of the entry.
     * @return The new search result entry.
     * @throws NullPointerException
     *             If {@code name} was {@code null}.
     */
    public static SearchResultEntry newSearchResultEntry(final DN name) {
        final Entry entry = new LinkedHashMapEntry().setName(name);
        return new SearchResultEntryImpl(entry);
    }

    /**
     * Creates a new search result entry backed by the provided entry.
     * Modifications made to {@code entry} will be reflected in the returned
     * search result entry. The returned search result entry supports updates to
     * its list of controls, as well as updates to the name and attributes if
     * the underlying entry allows.
     *
     * @param entry
     *            The entry.
     * @return The new search result entry.
     * @throws NullPointerException
     *             If {@code entry} was {@code null} .
     */
    public static SearchResultEntry newSearchResultEntry(final Entry entry) {
        Reject.ifNull(entry);
        return new SearchResultEntryImpl(entry);
    }

    /**
     * Creates a new search result entry using the provided distinguished name
     * decoded using the default schema.
     *
     * @param name
     *            The distinguished name of the entry.
     * @return The new search result entry.
     * @throws LocalizedIllegalArgumentException
     *             If {@code name} could not be decoded using the default
     *             schema.
     * @throws NullPointerException
     *             If {@code name} was {@code null}.
     */
    public static SearchResultEntry newSearchResultEntry(final String name) {
        final Entry entry = new LinkedHashMapEntry().setName(name);
        return new SearchResultEntryImpl(entry);
    }

    /**
     * Creates a new search result entry using the provided lines of LDIF
     * decoded using the default schema.
     *
     * @param ldifLines
     *            Lines of LDIF containing an LDIF add change record or an LDIF
     *            entry record.
     * @return The new search result entry.
     * @throws LocalizedIllegalArgumentException
     *             If {@code ldifLines} was empty, or contained invalid LDIF, or
     *             could not be decoded using the default schema.
     * @throws NullPointerException
     *             If {@code ldifLines} was {@code null} .
     */
    public static SearchResultEntry newSearchResultEntry(final String... ldifLines) {
        return newSearchResultEntry(new LinkedHashMapEntry(ldifLines));
    }

    /**
     * Creates a new search result reference using the provided continuation
     * reference URI.
     *
     * @param uri
     *            The first continuation reference URI to be added to this
     *            search result reference.
     * @return The new search result reference.
     * @throws NullPointerException
     *             If {@code uri} was {@code null}.
     */
    public static SearchResultReference newSearchResultReference(final String uri) {
        Reject.ifNull(uri);
        return new SearchResultReferenceImpl(uri);
    }

    /**
     * Creates a new who am I extended result with the provided result code and
     * no authorization ID.
     *
     * @param resultCode
     *            The result code.
     * @return The new who am I extended result.
     * @throws NullPointerException
     *             If {@code resultCode} was {@code null} .
     */
    public static WhoAmIExtendedResult newWhoAmIExtendedResult(final ResultCode resultCode) {
        Reject.ifNull(resultCode);
        return new WhoAmIExtendedResultImpl(ResultCode.SUCCESS);
    }

    /**
     * Creates an unmodifiable bind result using the provided response.
     *
     * @param result
     *            The bind result to be copied.
     * @return The unmodifiable bind result.
     * @throws NullPointerException
     *             If {@code result} was {@code null}.
     */
    public static BindResult unmodifiableBindResult(final BindResult result) {
        if (result instanceof UnmodifiableBindResultImpl) {
            return result;
        }
        return new UnmodifiableBindResultImpl(result);
    }

    /**
     * Creates an unmodifiable compare result using the provided response.
     *
     * @param result
     *            The compare result to be copied.
     * @return The unmodifiable compare result.
     * @throws NullPointerException
     *             If {@code result} was {@code null}.
     */
    public static CompareResult unmodifiableCompareResult(final CompareResult result) {
        if (result instanceof UnmodifiableCompareResultImpl) {
            return result;
        }
        return new UnmodifiableCompareResultImpl(result);
    }

    /**
     * Creates an unmodifiable generic extended result using the provided
     * response.
     *
     * @param result
     *            The generic extended result to be copied.
     * @return The unmodifiable generic extended result.
     * @throws NullPointerException
     *             If {@code result} was {@code null}.
     */
    public static GenericExtendedResult unmodifiableGenericExtendedResult(
            final GenericExtendedResult result) {
        if (result instanceof UnmodifiableGenericExtendedResultImpl) {
            return result;
        }
        return new UnmodifiableGenericExtendedResultImpl(result);
    }

    /**
     * Creates an unmodifiable generic intermediate response using the provided
     * response.
     *
     * @param response
     *            The generic intermediate response to be copied.
     * @return The unmodifiable generic intermediate response.
     * @throws NullPointerException
     *             If {@code response} was {@code null}.
     */
    public static GenericIntermediateResponse unmodifiableGenericIntermediateResponse(
            final GenericIntermediateResponse response) {
        if (response instanceof UnmodifiableGenericIntermediateResponseImpl) {
            return response;
        }
        return new UnmodifiableGenericIntermediateResponseImpl(response);
    }

    /**
     * Creates an unmodifiable password modify extended result using the
     * provided response.
     *
     * @param result
     *            The password modify extended result to be copied.
     * @return The unmodifiable password modify extended result.
     * @throws NullPointerException
     *             If {@code result} was {@code null}.
     */
    public static PasswordModifyExtendedResult unmodifiablePasswordModifyExtendedResult(
            final PasswordModifyExtendedResult result) {
        if (result instanceof UnmodifiablePasswordModifyExtendedResultImpl) {
            return result;
        }
        return new UnmodifiablePasswordModifyExtendedResultImpl(result);
    }

    /**
     * Creates an unmodifiable result using the provided response.
     *
     * @param result
     *            The result to be copied.
     * @return The unmodifiable result.
     * @throws NullPointerException
     *             If {@code result} was {@code null}.
     */
    public static Result unmodifiableResult(final Result result) {
        if (result instanceof UnmodifiableResultImpl) {
            return result;
        }
        return new UnmodifiableResultImpl(result);
    }

    /**
     * Creates an unmodifiable search result entry using the provided response.
     *
     * @param entry
     *            The search result entry to be copied.
     * @return The unmodifiable search result entry.
     * @throws NullPointerException
     *             If {@code entry} was {@code null}.
     */
    public static SearchResultEntry unmodifiableSearchResultEntry(final SearchResultEntry entry) {
        if (entry instanceof UnmodifiableSearchResultEntryImpl) {
            return entry;
        }
        return new UnmodifiableSearchResultEntryImpl(entry);
    }

    /**
     * Creates an unmodifiable search result reference using the provided
     * response.
     *
     * @param reference
     *            The search result reference to be copied.
     * @return The unmodifiable search result reference.
     * @throws NullPointerException
     *             If {@code searchResultReference} was {@code null}.
     */
    public static SearchResultReference unmodifiableSearchResultReference(
            final SearchResultReference reference) {
        if (reference instanceof UnmodifiableSearchResultReferenceImpl) {
            return reference;
        }
        return new UnmodifiableSearchResultReferenceImpl(reference);
    }

    /**
     * Creates an unmodifiable who am I extended result using the provided
     * response.
     *
     * @param result
     *            The who am I result to be copied.
     * @return The unmodifiable who am I extended result.
     * @throws NullPointerException
     *             If {@code result} was {@code null} .
     */
    public static WhoAmIExtendedResult unmodifiableWhoAmIExtendedResult(
            final WhoAmIExtendedResult result) {
        if (result instanceof UnmodifiableSearchResultReferenceImpl) {
            return result;
        }
        return new UnmodifiableWhoAmIExtendedResultImpl(result);
    }

    /** Private constructor. */
    private Responses() {
        // Prevent instantiation.
    }
}
