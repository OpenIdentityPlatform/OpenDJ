/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyrighted [year] [name of copyright owner]".
 *
 * Copyright 2012 ForgeRock AS. All rights reserved.
 */

package org.forgerock.opendj.rest2ldap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.forgerock.json.fluent.JsonPointer;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.opendj.ldap.AssertionFailureException;
import org.forgerock.opendj.ldap.AuthenticationException;
import org.forgerock.opendj.ldap.AuthorizationException;
import org.forgerock.opendj.ldap.ConnectionException;
import org.forgerock.opendj.ldap.EntryNotFoundException;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.MultipleEntriesFoundException;
import org.forgerock.opendj.ldap.ResultHandler;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.TimeoutResultException;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;
import org.forgerock.resource.exception.NotSupportedException;
import org.forgerock.resource.exception.ResourceException;
import org.forgerock.resource.provider.ActionRequest;
import org.forgerock.resource.provider.ActionResultHandler;
import org.forgerock.resource.provider.Context;
import org.forgerock.resource.provider.CreateRequest;
import org.forgerock.resource.provider.CreateResultHandler;
import org.forgerock.resource.provider.DeleteRequest;
import org.forgerock.resource.provider.DeleteResultHandler;
import org.forgerock.resource.provider.PatchRequest;
import org.forgerock.resource.provider.PatchResultHandler;
import org.forgerock.resource.provider.QueryRequest;
import org.forgerock.resource.provider.QueryResultHandler;
import org.forgerock.resource.provider.ReadRequest;
import org.forgerock.resource.provider.ReadResultHandler;
import org.forgerock.resource.provider.Resource;
import org.forgerock.resource.provider.UpdateRequest;
import org.forgerock.resource.provider.UpdateResultHandler;

/**
 *
 */
public class LDAPResource implements Resource {
    private final EntryContainer entryContainer;
    private final AttributeMapper attributeMapper;

    /**
     * Creates a new LDAP resource.
     */
    public LDAPResource(final EntryContainer container, final AttributeMapper mapper) {
        this.entryContainer = container;
        this.attributeMapper = mapper;
    }

    /**
     * {@inheritDoc}
     */
    public void action(final ActionRequest request, final Context context,
            final ActionResultHandler out) {
        out.setFailure(new NotSupportedException("Not yet implemented"));
    }

    /**
     * {@inheritDoc}
     */
    public void create(final CreateRequest request, final Context context,
            final CreateResultHandler out) {
        out.setFailure(new NotSupportedException("Not yet implemented"));
    }

    /**
     * {@inheritDoc}
     */
    public void delete(final DeleteRequest request, final Context context,
            final DeleteResultHandler out) {
        out.setFailure(new NotSupportedException("Not yet implemented"));
    }

    /**
     * {@inheritDoc}
     */
    public void patch(final PatchRequest request, final Context context,
            final PatchResultHandler out) {
        out.setFailure(new NotSupportedException("Not yet implemented"));
    }

    /**
     * {@inheritDoc}
     */
    public void query(final QueryRequest request, final Context context,
            final QueryResultHandler out) {
        out.setFailure(new NotSupportedException("Not yet implemented"));
    }

    /**
     * {@inheritDoc}
     */
    public void read(final ReadRequest request, final Context context, final ReadResultHandler out) {
        final String id = request.getId();
        if (id == null) {
            // List the entries.
            final SearchResultHandler handler = new SearchResultHandler() {
                private final List<String> resourceIDs = new ArrayList<String>();

                public boolean handleEntry(final SearchResultEntry entry) {
                    // TODO: should the resource or the container define the ID
                    // mapping?
                    resourceIDs.add(entryContainer.getIDFromEntry(entry));
                    return true;
                }

                public void handleErrorResult(final ErrorResultException error) {
                    out.setFailure(adaptErrorResult(error));
                }

                public boolean handleReference(final SearchResultReference reference) {
                    // TODO: should this be classed as an error since rest2ldap
                    // assumes entries are all colocated?
                    return true;
                }

                public void handleResult(final Result result) {
                    out.setResult(id, null, new JsonValue(resourceIDs));
                }
            };
            entryContainer.listEntries(context, handler);
        } else {
            // Read a single entry.

            // TODO: Determine the set of LDAP attributes that need to be read.
            final Set<JsonPointer> requestedAttributes = new LinkedHashSet<JsonPointer>();
            final Collection<String> requestedLDAPAttributes =
                    getRequestedLDAPAttributes(requestedAttributes);

            final ResultHandler<SearchResultEntry> handler =
                    new ResultHandler<SearchResultEntry>() {
                        public void handleErrorResult(final ErrorResultException error) {
                            out.setFailure(adaptErrorResult(error));
                        }

                        public void handleResult(final SearchResultEntry entry) {
                            final String revision = entryContainer.getEtagFromEntry(entry);
                            final AttributeMapperCompletionHandler<Map<String, Object>> mapHandler =
                                    new AttributeMapperCompletionHandler<Map<String, Object>>() {
                                        public void onFailure(final ResourceException e) {
                                            out.setFailure(e);
                                        }

                                        public void onSuccess(final Map<String, Object> result) {
                                            out.setResult(id, revision, new JsonValue(result));
                                        }
                                    };
                            attributeMapper.toJson(context, entry, mapHandler);
                        }
                    };
            entryContainer.readEntry(context, id, requestedLDAPAttributes, handler);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void update(final UpdateRequest request, final Context context,
            final UpdateResultHandler out) {
        out.setFailure(new NotSupportedException("Not yet implemented"));
    }

    /**
     * Adapts an LDAP result code to a resource exception.
     *
     * @param error
     *            The LDAP error that should be adapted.
     * @return The equivalent resource exception.
     */
    private ResourceException adaptErrorResult(final ErrorResultException error) {
        int resourceResultCode;
        try {
            throw error;
        } catch (AssertionFailureException e) {
            resourceResultCode = ResourceException.VERSION_MISMATCH;
        } catch (AuthenticationException e) {
            resourceResultCode = 401;
        } catch (AuthorizationException e) {
            resourceResultCode = ResourceException.FORBIDDEN;
        } catch (ConnectionException e) {
            resourceResultCode = ResourceException.UNAVAILABLE;
        } catch (EntryNotFoundException e) {
            resourceResultCode = ResourceException.NOT_FOUND;
        } catch (MultipleEntriesFoundException e) {
            resourceResultCode = ResourceException.INTERNAL_ERROR;
        } catch (TimeoutResultException e) {
            resourceResultCode = 408;
        } catch (ErrorResultException e) {
            resourceResultCode = ResourceException.INTERNAL_ERROR;
        }
        return ResourceException.getException(resourceResultCode, null, error.getMessage(), error);
    }

    /**
     * Determines the set of LDAP attributes to request in an LDAP read (search,
     * post-read), based on the provided set of JSON pointers.
     *
     * @param requestedAttributes
     *            The set of resource attributes to be read.
     * @return The set of LDAP attributes associated with the resource
     *         attributes.
     */
    private Collection<String> getRequestedLDAPAttributes(final Set<JsonPointer> requestedAttributes) {
        final Set<String> requestedLDAPAttributes;
        if (requestedAttributes.isEmpty()) {
            // Full read.
            requestedLDAPAttributes = new LinkedHashSet<String>();
            attributeMapper.getLDAPAttributes(new JsonPointer(), requestedLDAPAttributes);
        } else {
            // Partial read.
            requestedLDAPAttributes = new LinkedHashSet<String>(requestedAttributes.size());
            for (final JsonPointer requestedAttribute : requestedAttributes) {
                attributeMapper.getLDAPAttributes(requestedAttribute, requestedLDAPAttributes);
            }
        }
        return requestedLDAPAttributes;
    }

}
