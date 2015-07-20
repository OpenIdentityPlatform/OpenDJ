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
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2012-2015 ForgeRock AS.
 */
package org.forgerock.opendj.rest2ldap;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.json.fluent.JsonPointer;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.resource.BadRequestException;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ResultHandler;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.EntryNotFoundException;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.LinkedAttribute;
import org.forgerock.opendj.ldap.MultipleEntriesFoundException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;
import org.forgerock.util.Function;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.ExceptionHandler;

import static org.forgerock.opendj.ldap.LdapException.*;
import static org.forgerock.opendj.ldap.requests.Requests.*;
import static org.forgerock.opendj.rest2ldap.Rest2LDAP.*;
import static org.forgerock.opendj.rest2ldap.Utils.*;

/**
 * An attribute mapper which provides a mapping from a JSON value to a single DN
 * valued LDAP attribute.
 */
public final class ReferenceAttributeMapper extends AbstractLDAPAttributeMapper<ReferenceAttributeMapper> {
    /**
     * The maximum number of candidate references to allow in search filters.
     */
    private static final int SEARCH_MAX_CANDIDATES = 1000;

    private final DN baseDN;
    private Filter filter;
    private final AttributeMapper mapper;
    private final AttributeDescription primaryKey;
    private SearchScope scope = SearchScope.WHOLE_SUBTREE;

    ReferenceAttributeMapper(final AttributeDescription ldapAttributeName, final DN baseDN,
        final AttributeDescription primaryKey, final AttributeMapper mapper) {
        super(ldapAttributeName);
        this.baseDN = baseDN;
        this.primaryKey = primaryKey;
        this.mapper = mapper;
    }

    /**
     * Sets the filter which should be used when searching for referenced LDAP
     * entries. The default is {@code (objectClass=*)}.
     *
     * @param filter
     *            The filter which should be used when searching for referenced
     *            LDAP entries.
     * @return This attribute mapper.
     */
    public ReferenceAttributeMapper searchFilter(final Filter filter) {
        this.filter = ensureNotNull(filter);
        return this;
    }

    /**
     * Sets the filter which should be used when searching for referenced LDAP
     * entries. The default is {@code (objectClass=*)}.
     *
     * @param filter
     *            The filter which should be used when searching for referenced
     *            LDAP entries.
     * @return This attribute mapper.
     */
    public ReferenceAttributeMapper searchFilter(final String filter) {
        return searchFilter(Filter.valueOf(filter));
    }

    /**
     * Sets the search scope which should be used when searching for referenced
     * LDAP entries. The default is {@link SearchScope#WHOLE_SUBTREE}.
     *
     * @param scope
     *            The search scope which should be used when searching for
     *            referenced LDAP entries.
     * @return This attribute mapper.
     */
    public ReferenceAttributeMapper searchScope(final SearchScope scope) {
        this.scope = ensureNotNull(scope);
        return this;
    }

    @Override
    public String toString() {
        return "reference(" + ldapAttributeName + ")";
    }

    @Override
    void getLDAPFilter(final Context c, final JsonPointer path, final JsonPointer subPath, final FilterType type,
        final String operator, final Object valueAssertion, final ResultHandler<Filter> h) {
        // Construct a filter which can be used to find referenced resources.
        mapper.getLDAPFilter(c, path, subPath, type, operator, valueAssertion, new ResultHandler<Filter>() {
            @Override
            public void handleError(final ResourceException error) {
                h.handleError(error); // Propagate.
            }

            @Override
            public void handleResult(final Filter result) {
                // Search for all referenced entries and construct a filter.
                final SearchRequest request = createSearchRequest(result);
                final List<Filter> subFilters = new LinkedList<>();

                final ExceptionHandler<LdapException> exceptionHandler = new ExceptionHandler<LdapException>() {
                    @Override
                    public void handleException(LdapException exception) {
                        h.handleError(asResourceException(exception)); // Propagate.
                    }
                };

                c.getConnection().searchAsync(request, new SearchResultHandler() {
                    @Override
                    public boolean handleEntry(final SearchResultEntry entry) {
                        if (subFilters.size() < SEARCH_MAX_CANDIDATES) {
                            subFilters.add(Filter.equality(ldapAttributeName.toString(), entry.getName()));
                            return true;
                        } else {
                            // No point in continuing - maximum candidates reached.
                            return false;
                        }
                    }

                    @Override
                    public boolean handleReference(final SearchResultReference reference) {
                        // Ignore references.
                        return true;
                    }
                }).thenOnResult(new org.forgerock.util.promise.ResultHandler<Result>() {
                    @Override
                    public void handleResult(Result result) {
                        if (subFilters.size() >= SEARCH_MAX_CANDIDATES) {
                            exceptionHandler.handleException(newLdapException(ResultCode.ADMIN_LIMIT_EXCEEDED));
                        } else if (subFilters.size() == 1) {
                            h.handleResult(subFilters.get(0));
                        } else {
                            h.handleResult(Filter.or(subFilters));
                        }
                    }
                }).thenOnException(exceptionHandler);
            }
        });
    }

    @Override
    void getNewLDAPAttributes(final Context c, final JsonPointer path, final List<Object> newValues,
        final ResultHandler<Attribute> h) {
        /*
         * For each value use the subordinate mapper to obtain the LDAP primary
         * key, the perform a search for each one to find the corresponding entries.
         */
        final Attribute newLDAPAttribute = new LinkedAttribute(ldapAttributeName);
        final AtomicInteger pendingSearches = new AtomicInteger(newValues.size());
        final AtomicReference<ResourceException> exception = new AtomicReference<>();

        for (final Object value : newValues) {
            mapper.create(c, path, new JsonValue(value), new ResultHandler<List<Attribute>>() {

                @Override
                public void handleError(final ResourceException error) {
                    h.handleError(error);
                }

                @Override
                public void handleResult(final List<Attribute> result) {
                    Attribute primaryKeyAttribute = null;
                    for (final Attribute attribute : result) {
                        if (attribute.getAttributeDescription().equals(primaryKey)) {
                            primaryKeyAttribute = attribute;
                            break;
                        }
                    }

                    if (primaryKeyAttribute == null || primaryKeyAttribute.isEmpty()) {
                        h.handleError(new BadRequestException(i18n(
                            "The request cannot be processed because the reference "
                                + "field '%s' contains a value which does not contain " + "a primary key", path)));
                        return;
                    }

                    if (primaryKeyAttribute.size() > 1) {
                        h.handleError(new BadRequestException(i18n(
                            "The request cannot be processed because the reference "
                                + "field '%s' contains a value which contains multiple " + "primary keys", path)));
                        return;
                    }

                    // Now search for the referenced entry in to get its DN.
                    final ByteString primaryKeyValue = primaryKeyAttribute.firstValue();
                    final Filter filter = Filter.equality(primaryKey.toString(), primaryKeyValue);
                    final SearchRequest search = createSearchRequest(filter);
                    c.getConnection().searchSingleEntryAsync(search).thenOnResult(
                            new org.forgerock.util.promise.ResultHandler<SearchResultEntry>() {
                            @Override
                            public void handleResult(final SearchResultEntry result) {
                                synchronized (newLDAPAttribute) {
                                    newLDAPAttribute.add(result.getName());
                                }
                                completeIfNecessary();
                            }
                        }).thenOnException(new ExceptionHandler<LdapException>() {
                            @Override
                            public void handleException(final LdapException error) {
                                ResourceException re;
                                try {
                                    throw error;
                                } catch (final EntryNotFoundException e) {
                                    re = new BadRequestException(i18n(
                                            "The request cannot be processed " + "because the resource '%s' "
                                                    + "referenced in field '%s' does " + "not exist",
                                            primaryKeyValue.toString(), path));
                                } catch (final MultipleEntriesFoundException e) {
                                    re = new BadRequestException(i18n(
                                            "The request cannot be processed " + "because the resource '%s' "
                                                    + "referenced in field '%s' is " + "ambiguous",
                                            primaryKeyValue.toString(), path));
                                } catch (final LdapException e) {
                                    re = asResourceException(e);
                                }
                                exception.compareAndSet(null, re);
                                completeIfNecessary();
                            }
                        });
                }

                private void completeIfNecessary() {
                    if (pendingSearches.decrementAndGet() == 0) {
                        if (exception.get() != null) {
                            h.handleError(exception.get());
                        } else {
                            h.handleResult(newLDAPAttribute);
                        }
                    }
                }
            });
        }
    }

    @Override
    ReferenceAttributeMapper getThis() {
        return this;
    }

    @Override
    void read(final Context c, final JsonPointer path, final Entry e, final ResultHandler<JsonValue> h) {
        final Attribute attribute = e.getAttribute(ldapAttributeName);
        if (attribute == null || attribute.isEmpty()) {
            h.handleResult(null);
        } else if (attributeIsSingleValued()) {
            try {
                final DN dn = attribute.parse().usingSchema(c.getConfig().schema()).asDN();
                readEntry(c, path, dn, h);
            } catch (final Exception ex) {
                // The LDAP attribute could not be decoded.
                h.handleError(asResourceException(ex));
            }
        } else {
            try {
                final Set<DN> dns = attribute.parse().usingSchema(c.getConfig().schema()).asSetOfDN();
                final ResultHandler<JsonValue> handler =
                    accumulate(dns.size(), transform(new Function<List<JsonValue>, JsonValue, NeverThrowsException>() {
                        @Override
                        public JsonValue apply(final List<JsonValue> value) {
                            if (value.isEmpty()) {
                                /*
                                 * No values, so omit the entire JSON object
                                 * from the resource.
                                 */
                                return null;
                            } else {
                                // Combine values into a single JSON array.
                                final List<Object> result = new ArrayList<>(value.size());
                                for (final JsonValue e : value) {
                                    result.add(e.getObject());
                                }
                                return new JsonValue(result);
                            }
                        }
                    }, h));
                for (final DN dn : dns) {
                    readEntry(c, path, dn, handler);
                }
            } catch (final Exception ex) {
                // The LDAP attribute could not be decoded.
                h.handleError(asResourceException(ex));
            }
        }
    }

    private SearchRequest createSearchRequest(final Filter result) {
        final Filter searchFilter = filter != null ? Filter.and(filter, result) : result;
        return newSearchRequest(baseDN, scope, searchFilter, "1.1");
    }

    private void readEntry(final Context c, final JsonPointer path, final DN dn,
        final ResultHandler<JsonValue> handler) {
        final Set<String> requestedLDAPAttributes = new LinkedHashSet<>();
        mapper.getLDAPAttributes(c, path, new JsonPointer(), requestedLDAPAttributes);
        c.getConnection().readEntryAsync(dn, requestedLDAPAttributes)
                .thenOnResult(new org.forgerock.util.promise.ResultHandler<SearchResultEntry>() {
                    @Override
                    public void handleResult(final SearchResultEntry result) {
                        mapper.read(c, path, result, handler);
                    }
                }).thenOnException(new ExceptionHandler<LdapException>() {
                    @Override
                    public void handleException(final LdapException error) {
                        if (!(error instanceof EntryNotFoundException)) {
                            handler.handleError(asResourceException(error));
                        } else {
                            /*
                             * The referenced entry does not exist so ignore it
                             * since it cannot be mapped.
                             */
                            handler.handleResult(null);
                        }
                    }
                });
    }

}
