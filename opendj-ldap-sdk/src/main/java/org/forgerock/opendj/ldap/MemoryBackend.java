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
 *      Copyright 2013 ForgeRock AS.
 */
package org.forgerock.opendj.ldap;

import static org.forgerock.opendj.ldap.Attributes.singletonAttribute;
import static org.forgerock.opendj.ldap.Entries.modifyEntry;
import static org.forgerock.opendj.ldap.ErrorResultException.newErrorResult;
import static org.forgerock.opendj.ldap.responses.Responses.newBindResult;
import static org.forgerock.opendj.ldap.responses.Responses.newCompareResult;
import static org.forgerock.opendj.ldap.responses.Responses.newResult;
import static org.forgerock.opendj.ldap.responses.Responses.newSearchResultEntry;

import java.io.IOException;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.controls.AssertionRequestControl;
import org.forgerock.opendj.ldap.controls.PostReadRequestControl;
import org.forgerock.opendj.ldap.controls.PostReadResponseControl;
import org.forgerock.opendj.ldap.controls.PreReadRequestControl;
import org.forgerock.opendj.ldap.controls.PreReadResponseControl;
import org.forgerock.opendj.ldap.controls.SubtreeDeleteRequestControl;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ExtendedRequest;
import org.forgerock.opendj.ldap.requests.GenericBindRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Request;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.requests.SimpleBindRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldif.EntryReader;

/**
 * A simple in memory back-end which can be used for testing. It is not intended
 * for production use due to various limitations. The back-end implementations
 * supports the following:
 * <ul>
 * <li>add, bind (simple), compare, delete, modify, and search operations, but
 * not modifyDN nor extended operations
 * <li>assertion, pre-, and post- read controls, subtree delete control, and
 * permissive modify control
 * <li>thread safety - supports concurrent operations
 * </ul>
 * It does not support the following:
 * <ul>
 * <li>high performance
 * <li>secure password storage
 * <li>schema checking
 * <li>persistence
 * <li>indexing
 * </ul>
 * This class can be used in conjunction with the factories defined in
 * {@link Connections} to create simpler servers as well as mock LDAP
 * connections. For example, to create a mock LDAP connection factory:
 *
 * <pre>
 * MemoryBackend backend = new MemoryBackend();
 * Connection connection = newInternalConnectionFactory(newServerConnectionFactory(backend), null)
 *         .getConnection();
 * </pre>
 */
public final class MemoryBackend implements RequestHandler<RequestContext> {
    private final DecodeOptions decodeOptions;
    private final ConcurrentSkipListMap<DN, Entry> entries = new ConcurrentSkipListMap<DN, Entry>();
    private final Object writeLock = new Object();
    private final Schema schema;

    /**
     * Creates a new empty memory backend which will use the default schema.
     */
    public MemoryBackend() {
        this(Schema.getDefaultSchema());
    }

    /**
     * Creates a new memory backend which will use the default schema, and will
     * contain the entries read from the provided entry reader.
     *
     * @param reader
     *            The entry reader.
     * @throws IOException
     *             If an unexpected IO error occurred while reading the entries.
     */
    public MemoryBackend(final EntryReader reader) throws IOException {
        this(Schema.getDefaultSchema(), reader);
    }

    /**
     * Creates a new empty memory backend which will use the provided schema.
     *
     * @param schema
     *            The schema to use for decoding filters, etc.
     */
    public MemoryBackend(final Schema schema) {
        this.schema = schema;
        this.decodeOptions = new DecodeOptions().setSchema(schema);
    }

    /**
     * Creates a new memory backend which will use the provided schema, and will
     * contain the entries read from the provided entry reader.
     *
     * @param schema
     *            The schema to use for decoding filters, etc.
     * @param reader
     *            The entry reader.
     * @throws IOException
     *             If an unexpected IO error occurred while reading the entries.
     */
    public MemoryBackend(final Schema schema, final EntryReader reader) throws IOException {
        this.schema = schema;
        this.decodeOptions = new DecodeOptions().setSchema(schema);
        if (reader != null) {
            try {
                while (reader.hasNext()) {
                    final Entry entry = reader.readEntry();
                    final DN dn = entry.getName();
                    if (entries.containsKey(dn)) {
                        throw new ErrorResultIOException(newErrorResult(
                                ResultCode.ENTRY_ALREADY_EXISTS, "Attempted to add the entry '"
                                        + dn.toString() + "' multiple times"));
                    } else {
                        entries.put(dn, entry);
                    }
                }
            } finally {
                reader.close();
            }
        }
    }

    @Override
    public void handleAdd(final RequestContext requestContext, final AddRequest request,
            final IntermediateResponseHandler intermediateResponseHandler,
            final ResultHandler<Result> resultHandler) {
        try {
            synchronized (writeLock) {
                final DN dn = request.getName();
                final DN parent = dn.parent();
                if (entries.containsKey(dn)) {
                    throw newErrorResult(ResultCode.ENTRY_ALREADY_EXISTS, "The entry '"
                            + dn.toString() + "' already exists");
                } else if (!entries.containsKey(parent)) {
                    noSuchObject(parent);
                } else {
                    entries.put(dn, request);
                }
            }
            resultHandler.handleResult(getResult(request, null, request));
        } catch (final ErrorResultException e) {
            resultHandler.handleErrorResult(e);
        }
    }

    @Override
    public void handleBind(final RequestContext requestContext, final int version,
            final BindRequest request,
            final IntermediateResponseHandler intermediateResponseHandler,
            final ResultHandler<BindResult> resultHandler) {
        try {
            final Entry entry;
            synchronized (writeLock) {
                final DN username = DN.valueOf(request.getName(), schema);
                final byte[] password;
                if (request instanceof SimpleBindRequest) {
                    password = ((SimpleBindRequest) request).getPassword();
                } else if (request instanceof GenericBindRequest
                        && request.getAuthenticationType() == BindRequest.AUTHENTICATION_TYPE_SIMPLE) {
                    password = ((GenericBindRequest) request).getAuthenticationValue();
                } else {
                    throw newErrorResult(ResultCode.PROTOCOL_ERROR,
                            "non-SIMPLE authentication not supported: "
                                    + request.getAuthenticationType());
                }
                entry = getRequiredEntry(null, username);
                if (!entry.containsAttribute("userPassword", password)) {
                    throw newErrorResult(ResultCode.INVALID_CREDENTIALS, "Wrong password");
                }
            }
            resultHandler.handleResult(getBindResult(request, entry, entry));
        } catch (final LocalizedIllegalArgumentException e) {
            resultHandler.handleErrorResult(newErrorResult(ResultCode.PROTOCOL_ERROR, e));
        } catch (final EntryNotFoundException e) {
            /*
             * Usually you would not include a diagnostic message, but we'll add
             * one here because the memory back-end is not intended for
             * production use.
             */
            resultHandler.handleErrorResult(newErrorResult(ResultCode.INVALID_CREDENTIALS,
                    "Unknown user"));
        } catch (final ErrorResultException e) {
            resultHandler.handleErrorResult(e);
        }
    }

    @Override
    public void handleCompare(final RequestContext requestContext, final CompareRequest request,
            final IntermediateResponseHandler intermediateResponseHandler,
            final ResultHandler<CompareResult> resultHandler) {
        try {
            final Entry entry;
            final Attribute assertion;
            synchronized (writeLock) {
                final DN dn = request.getName();
                entry = getRequiredEntry(request, dn);
                assertion =
                        singletonAttribute(request.getAttributeDescription(), request
                                .getAssertionValue());
            }
            resultHandler.handleResult(getCompareResult(request, entry, entry.containsAttribute(
                    assertion, null)));
        } catch (final ErrorResultException e) {
            resultHandler.handleErrorResult(e);
        }
    }

    @Override
    public void handleDelete(final RequestContext requestContext, final DeleteRequest request,
            final IntermediateResponseHandler intermediateResponseHandler,
            final ResultHandler<Result> resultHandler) {
        try {
            final Entry entry;
            synchronized (writeLock) {
                final DN dn = request.getName();
                entry = getRequiredEntry(request, dn);
                if (request.getControl(SubtreeDeleteRequestControl.DECODER, decodeOptions) != null) {
                    // Subtree delete.
                    entries.subMap(dn, dn.child(RDN.maxValue())).clear();
                } else {
                    // Must be leaf.
                    final DN next = entries.higherKey(dn);
                    if (next == null || !next.isChildOf(dn)) {
                        entries.remove(dn);
                    } else {
                        throw newErrorResult(ResultCode.NOT_ALLOWED_ON_NONLEAF);
                    }
                }
            }
            resultHandler.handleResult(getResult(request, entry, null));
        } catch (final DecodeException e) {
            resultHandler.handleErrorResult(newErrorResult(ResultCode.PROTOCOL_ERROR, e));
        } catch (final ErrorResultException e) {
            resultHandler.handleErrorResult(e);
        }
    }

    @Override
    public <R extends ExtendedResult> void handleExtendedRequest(
            final RequestContext requestContext, final ExtendedRequest<R> request,
            final IntermediateResponseHandler intermediateResponseHandler,
            final ResultHandler<R> resultHandler) {
        resultHandler.handleErrorResult(newErrorResult(ResultCode.UNWILLING_TO_PERFORM,
                "Extended request operation not supported"));
    }

    @Override
    public void handleModify(final RequestContext requestContext, final ModifyRequest request,
            final IntermediateResponseHandler intermediateResponseHandler,
            final ResultHandler<Result> resultHandler) {
        try {
            final Entry entry;
            final Entry newEntry;
            synchronized (writeLock) {
                final DN dn = request.getName();
                entry = getRequiredEntry(request, dn);
                newEntry = new LinkedHashMapEntry(entry);
                entries.put(dn, modifyEntry(newEntry, request));
            }
            resultHandler.handleResult(getResult(request, entry, newEntry));
        } catch (final ErrorResultException e) {
            resultHandler.handleErrorResult(e);
        }
    }

    @Override
    public void handleModifyDN(final RequestContext requestContext, final ModifyDNRequest request,
            final IntermediateResponseHandler intermediateResponseHandler,
            final ResultHandler<Result> resultHandler) {
        resultHandler.handleErrorResult(newErrorResult(ResultCode.UNWILLING_TO_PERFORM,
                "ModifyDN request operation not supported"));
    }

    @Override
    public void handleSearch(final RequestContext requestContext, final SearchRequest request,
            final IntermediateResponseHandler intermediateResponseHandler,
            final SearchResultHandler resultHandler) {
        try {
            final DN dn = request.getName();
            final Entry baseEntry = getRequiredEntry(request, dn);
            final SearchScope scope = request.getScope();
            final Filter filter = request.getFilter();
            final Matcher matcher = filter.matcher(schema);
            final AttributeFilter attributeFilter =
                    new AttributeFilter(request.getAttributes(), schema).typesOnly(request
                            .isTypesOnly());
            if (scope.equals(SearchScope.BASE_OBJECT)) {
                if (matcher.matches(baseEntry).toBoolean()) {
                    sendEntry(attributeFilter, resultHandler, baseEntry);
                }
            } else if (scope.equals(SearchScope.SINGLE_LEVEL)) {
                final NavigableMap<DN, Entry> subtree =
                        entries.subMap(dn, dn.child(RDN.maxValue()));
                for (final Entry entry : subtree.values()) {
                    // Check for cancellation.
                    requestContext.checkIfCancelled(false);
                    final DN childDN = entry.getName();
                    if (childDN.isChildOf(dn)) {
                        if (matcher.matches(entry).toBoolean()
                                && !sendEntry(attributeFilter, resultHandler, entry)) {
                            // Caller has asked to stop sending results.
                            break;
                        }
                    }
                }
            } else if (scope.equals(SearchScope.WHOLE_SUBTREE)) {
                final NavigableMap<DN, Entry> subtree =
                        entries.subMap(dn, dn.child(RDN.maxValue()));
                for (final Entry entry : subtree.values()) {
                    // Check for cancellation.
                    requestContext.checkIfCancelled(false);
                    if (matcher.matches(entry).toBoolean()
                            && !sendEntry(attributeFilter, resultHandler, entry)) {
                        // Caller has asked to stop sending results.
                        break;
                    }
                }
            } else {
                throw newErrorResult(ResultCode.PROTOCOL_ERROR,
                        "Search request contains an unsupported search scope");
            }
            resultHandler.handleResult(newResult(ResultCode.SUCCESS));
        } catch (final ErrorResultException e) {
            resultHandler.handleErrorResult(e);
        }
    }

    private <R extends Result> R addResultControls(final Request request, final Entry before,
            final Entry after, final R result) throws ErrorResultException {
        try {
            // Add pre-read response control if requested.
            final PreReadRequestControl preRead =
                    request.getControl(PreReadRequestControl.DECODER, decodeOptions);
            if (preRead != null) {
                if (preRead.isCritical() && before == null) {
                    throw newErrorResult(ResultCode.UNAVAILABLE_CRITICAL_EXTENSION);
                } else {
                    final AttributeFilter filter =
                            new AttributeFilter(preRead.getAttributes(), schema);
                    result.addControl(PreReadResponseControl.newControl(filter
                            .filteredViewOf(before)));
                }
            }

            // Add post-read response control if requested.
            final PostReadRequestControl postRead =
                    request.getControl(PostReadRequestControl.DECODER, decodeOptions);
            if (postRead != null) {
                if (postRead.isCritical() && after == null) {
                    throw newErrorResult(ResultCode.UNAVAILABLE_CRITICAL_EXTENSION);
                } else {
                    final AttributeFilter filter =
                            new AttributeFilter(postRead.getAttributes(), schema);
                    result.addControl(PostReadResponseControl.newControl(filter
                            .filteredViewOf(after)));
                }
            }
            return result;
        } catch (final DecodeException e) {
            throw newErrorResult(ResultCode.PROTOCOL_ERROR, e);
        }
    }

    private BindResult getBindResult(final BindRequest request, final Entry before,
            final Entry after) throws ErrorResultException {
        return addResultControls(request, before, after, newBindResult(ResultCode.SUCCESS));
    }

    private CompareResult getCompareResult(final CompareRequest request, final Entry entry,
            final boolean compareResult) throws ErrorResultException {
        return addResultControls(
                request,
                entry,
                entry,
                newCompareResult(compareResult ? ResultCode.COMPARE_TRUE : ResultCode.COMPARE_FALSE));
    }

    private Entry getRequiredEntry(final Request request, final DN dn) throws ErrorResultException {
        final Entry entry = entries.get(dn);
        if (entry == null) {
            noSuchObject(dn);
        } else if (request != null) {
            AssertionRequestControl control;
            try {
                control = request.getControl(AssertionRequestControl.DECODER, decodeOptions);
            } catch (final DecodeException e) {
                throw newErrorResult(ResultCode.PROTOCOL_ERROR, e);
            }
            if (control != null) {
                final Filter filter = control.getFilter();
                final Matcher matcher = filter.matcher(schema);
                if (!matcher.matches(entry).toBoolean()) {
                    throw newErrorResult(ResultCode.ASSERTION_FAILED, "The filter '"
                            + filter.toString() + "' did not match the entry '"
                            + entry.getName().toString() + "'");
                }
            }
        }
        return entry;
    }

    private Result getResult(final Request request, final Entry before, final Entry after)
            throws ErrorResultException {
        return addResultControls(request, before, after, newResult(ResultCode.SUCCESS));
    }

    private void noSuchObject(final DN dn) throws ErrorResultException {
        throw newErrorResult(ResultCode.NO_SUCH_OBJECT, "The entry '" + dn.toString()
                + "' does not exist");
    }

    private boolean sendEntry(final AttributeFilter filter,
            final SearchResultHandler resultHandler, final Entry entry) {
        return resultHandler.handleEntry(newSearchResultEntry(filter.filteredViewOf(entry)));
    }
}
