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
 * Copyright 2012-2016 ForgeRock AS.
 */
package org.forgerock.opendj.rest2ldap;

import static org.forgerock.opendj.ldap.ResultCode.ADMIN_LIMIT_EXCEEDED;
import static org.forgerock.opendj.rest2ldap.Rest2ldapMessages.*;
import static org.forgerock.opendj.ldap.LdapException.newLdapException;
import static org.forgerock.opendj.ldap.requests.Requests.newSearchRequest;
import static org.forgerock.opendj.rest2ldap.Rest2Ldap.asResourceException;
import static org.forgerock.util.Reject.checkNotNull;
import static org.forgerock.opendj.rest2ldap.Utils.newBadRequestException;
import static org.forgerock.util.promise.Promises.newResultPromise;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.json.JsonPointer;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.EntryNotFoundException;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LinkedAttribute;
import org.forgerock.opendj.ldap.MultipleEntriesFoundException;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.Function;
import org.forgerock.util.promise.ExceptionHandler;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.PromiseImpl;
import org.forgerock.util.promise.Promises;
import org.forgerock.util.promise.ResultHandler;

/**
 * An property mapper which provides a mapping from a JSON value to a single DN
 * valued LDAP attribute.
 */
public final class ReferencePropertyMapper extends AbstractLdapPropertyMapper<ReferencePropertyMapper> {
    /**
     * The maximum number of candidate references to allow in search filters.
     */
    private static final int SEARCH_MAX_CANDIDATES = 1000;

    private final DN baseDn;
    private final Schema schema;
    private Filter filter;
    private final PropertyMapper mapper;
    private final AttributeDescription primaryKey;
    private SearchScope scope = SearchScope.WHOLE_SUBTREE;

    ReferencePropertyMapper(final Schema schema, final AttributeDescription ldapAttributeName, final DN baseDn,
                            final AttributeDescription primaryKey, final PropertyMapper mapper) {
        super(ldapAttributeName);
        this.schema = schema;
        this.baseDn = baseDn;
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
     * @return This property mapper.
     */
    public ReferencePropertyMapper searchFilter(final Filter filter) {
        this.filter = checkNotNull(filter);
        return this;
    }

    /**
     * Sets the filter which should be used when searching for referenced LDAP
     * entries. The default is {@code (objectClass=*)}.
     *
     * @param filter
     *            The filter which should be used when searching for referenced
     *            LDAP entries.
     * @return This property mapper.
     */
    public ReferencePropertyMapper searchFilter(final String filter) {
        return searchFilter(Filter.valueOf(filter));
    }

    /**
     * Sets the search scope which should be used when searching for referenced
     * LDAP entries. The default is {@link SearchScope#WHOLE_SUBTREE}.
     *
     * @param scope
     *            The search scope which should be used when searching for
     *            referenced LDAP entries.
     * @return This property mapper.
     */
    public ReferencePropertyMapper searchScope(final SearchScope scope) {
        this.scope = checkNotNull(scope);
        return this;
    }

    @Override
    public String toString() {
        return "reference(" + ldapAttributeName + ")";
    }

    @Override
    Promise<Filter, ResourceException> getLdapFilter(final Connection connection, final Resource resource,
                                                     final JsonPointer path, final JsonPointer subPath,
                                                     final FilterType type, final String operator,
                                                     final Object valueAssertion) {
        return mapper.getLdapFilter(connection, resource, path, subPath, type, operator, valueAssertion)
                .thenAsync(new AsyncFunction<Filter, Filter, ResourceException>() {
                    @Override
                    public Promise<Filter, ResourceException> apply(final Filter result) {
                        // Search for all referenced entries and construct a filter.
                        final SearchRequest request = createSearchRequest(result);
                        final List<Filter> subFilters = new LinkedList<>();

                        return connection.searchAsync(request, new SearchResultHandler() {
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
                        }).then(new Function<Result, Filter, ResourceException>() {
                            @Override
                            public Filter apply(Result result) throws ResourceException {
                                if (subFilters.size() >= SEARCH_MAX_CANDIDATES) {
                                    throw asResourceException(newLdapException(ADMIN_LIMIT_EXCEEDED));
                                } else if (subFilters.size() == 1) {
                                    return subFilters.get(0);
                                } else {
                                    return Filter.or(subFilters);
                                }
                            }
                        }, new Function<LdapException, Filter, ResourceException>() {
                            @Override
                            public Filter apply(LdapException exception) throws ResourceException {
                                throw asResourceException(exception);
                            }
                        });
                    }
                });
    }

    @Override
    Promise<Attribute, ResourceException> getNewLdapAttributes(final Connection connection, final Resource resource,
                                                               final JsonPointer path, final List<Object> newValues) {
        /*
         * For each value use the subordinate mapper to obtain the LDAP primary
         * key, the perform a search for each one to find the corresponding entries.
         */
        final Attribute newLDAPAttribute = new LinkedAttribute(ldapAttributeName);
        final AtomicInteger pendingSearches = new AtomicInteger(newValues.size());
        final AtomicReference<ResourceException> exception = new AtomicReference<>();
        final PromiseImpl<Attribute, ResourceException> promise = PromiseImpl.create();

        for (final Object value : newValues) {
            mapper.create(connection, resource, path, new JsonValue(value))
                  .thenOnResult(new ResultHandler<List<Attribute>>() {
                      @Override
                      public void handleResult(List<Attribute> result) {
                          Attribute primaryKeyAttribute = null;
                          for (final Attribute attribute : result) {
                              if (attribute.getAttributeDescription().equals(primaryKey)) {
                                  primaryKeyAttribute = attribute;
                                  break;
                              }
                          }

                          if (primaryKeyAttribute == null || primaryKeyAttribute.isEmpty()) {
                              promise.handleException(newBadRequestException(
                                      ERR_REFERENCE_FIELD_NO_PRIMARY_KEY.get(path)));
                              return;
                          }

                          if (primaryKeyAttribute.size() > 1) {
                              promise.handleException(
                                      newBadRequestException(ERR_REFERENCE_FIELD_MULTIPLE_PRIMARY_KEYS.get(path)));
                              return;
                          }

                          // Now search for the referenced entry in to get its DN.
                          final ByteString primaryKeyValue = primaryKeyAttribute.firstValue();
                          final Filter filter = Filter.equality(primaryKey.toString(), primaryKeyValue);
                          final SearchRequest search = createSearchRequest(filter);
                          connection.searchSingleEntryAsync(search)
                                    .thenOnResult(new ResultHandler<SearchResultEntry>() {
                                        @Override
                                        public void handleResult(final SearchResultEntry result) {
                                            synchronized (newLDAPAttribute) {
                                                newLDAPAttribute.add(result.getName());
                                            }
                                            completeIfNecessary();
                                        }
                                    })
                                    .thenOnException(new ExceptionHandler<LdapException>() {
                                        @Override
                                        public void handleException(final LdapException error) {
                                            ResourceException re;
                                            try {
                                                throw error;
                                            } catch (final EntryNotFoundException e) {
                                                re = newBadRequestException(
                                                        ERR_REFERENCE_FIELD_DOES_NOT_EXIST.get(primaryKeyValue, path));
                                            } catch (final MultipleEntriesFoundException e) {
                                                re = newBadRequestException(
                                                        ERR_REFERENCE_FIELD_AMBIGUOUS.get(primaryKeyValue, path));
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
                                  promise.handleException(exception.get());
                              } else {
                                  promise.handleResult(newLDAPAttribute);
                              }
                          }
                      }
                  });
        }
        return promise;
    }

    @Override
    ReferencePropertyMapper getThis() {
        return this;
    }

    @SuppressWarnings("fallthrough")
    @Override
    Promise<JsonValue, ResourceException> read(final Connection connection, final Resource resource,
                                               final JsonPointer path, final Entry e) {
        final Set<DN> dns = e.parseAttribute(ldapAttributeName).usingSchema(schema).asSetOfDN();
        switch (dns.size()) {
        case 0:
            return newResultPromise(null);
        case 1:
            if (attributeIsSingleValued()) {
                try {
                    return readEntry(connection, resource, path, dns.iterator().next());
                } catch (final Exception ex) {
                    // The LDAP attribute could not be decoded.
                    return Promises.newExceptionPromise(asResourceException(ex));
                }
            }
            // Fall-though: unexpectedly got multiple values. It's probably best to just return them.
        default:
            try {
                final List<Promise<JsonValue, ResourceException>> promises = new ArrayList<>(dns.size());
                for (final DN dn : dns) {
                    promises.add(readEntry(connection, resource, path, dn));
                }
                return Promises.when(promises)
                               .then(new Function<List<JsonValue>, JsonValue, ResourceException>() {
                                   @Override
                                   public JsonValue apply(final List<JsonValue> value) {
                                       if (value.isEmpty()) {
                                           // No values, so omit the entire JSON object from the resource.
                                           return null;
                                       } else {
                                           // Combine values into a single JSON array.
                                           final List<Object> result = new ArrayList<>(value.size());
                                           for (final JsonValue e : value) {
                                               if (e != null) {
                                                   result.add(e.getObject());
                                               }
                                           }
                                           return result.isEmpty() ? null : new JsonValue(result);
                                       }
                                   }
                               });
            } catch (final Exception ex) {
                // The LDAP attribute could not be decoded.
                return Promises.newExceptionPromise(asResourceException(ex));
            }
        }
    }

    private SearchRequest createSearchRequest(final Filter result) {
        final Filter searchFilter = filter != null ? Filter.and(filter, result) : result;
        return newSearchRequest(baseDn, scope, searchFilter, "1.1");
    }

    private Promise<JsonValue, ResourceException> readEntry(
            final Connection connection, final Resource resource, final JsonPointer path, final DN dn) {
        final Set<String> requestedLDAPAttributes = new LinkedHashSet<>();
        mapper.getLdapAttributes(path, new JsonPointer(), requestedLDAPAttributes);

        final Filter searchFilter = filter != null ? filter : Filter.alwaysTrue();
        final String[] attributes = requestedLDAPAttributes.toArray(new String[requestedLDAPAttributes.size()]);
        final SearchRequest request = newSearchRequest(dn, SearchScope.BASE_OBJECT, searchFilter, attributes);

        return connection
                .searchSingleEntryAsync(request)
                .thenAsync(new AsyncFunction<SearchResultEntry, JsonValue, ResourceException>() {
                    @Override
                    public Promise<JsonValue, ResourceException> apply(final SearchResultEntry result) {
                        return mapper.read(connection, resource, path, result);
                    }
                }, new AsyncFunction<LdapException, JsonValue, ResourceException>() {
                    @Override
                    public Promise<JsonValue, ResourceException> apply(final LdapException error) {
                        if (error instanceof EntryNotFoundException) {
                            // Ignore missing entry since it cannot be mapped.
                            return Promises.newResultPromise(null);
                        }
                        return Promises.newExceptionPromise(asResourceException(error));
                    }
                });
    }
}
