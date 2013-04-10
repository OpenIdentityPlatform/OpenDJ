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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2013 ForgeRock AS.
 */

package org.forgerock.opendj.rest2ldap;

import static org.forgerock.opendj.ldap.requests.Requests.newSearchRequest;
import static org.forgerock.opendj.ldap.schema.CoreSchema.getEntryUUIDAttributeType;
import static org.forgerock.opendj.rest2ldap.ReadOnUpdatePolicy.CONTROLS;
import static org.forgerock.opendj.rest2ldap.Utils.ensureNotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.fluent.JsonValueException;
import org.forgerock.json.resource.BadRequestException;
import org.forgerock.json.resource.CollectionResourceProvider;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.opendj.ldap.AssertionFailureException;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.AuthenticationException;
import org.forgerock.opendj.ldap.AuthorizationException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConnectionException;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.Connections;
import org.forgerock.opendj.ldap.ConstraintViolationException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.EntryNotFoundException;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.FailoverLoadBalancingAlgorithm;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.LinkedAttribute;
import org.forgerock.opendj.ldap.MultipleEntriesFoundException;
import org.forgerock.opendj.ldap.RDN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.RoundRobinLoadBalancingAlgorithm;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.TimeoutResultException;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.Schema;

/**
 * Provides core factory methods and builders for constructing LDAP resource
 * collections.
 */
public final class Rest2LDAP {
    // @Checkstyle:off

    /**
     * A builder for incrementally constructing LDAP resource collections.
     */
    public static final class Builder {
        private final List<Attribute> additionalLDAPAttributes = new LinkedList<Attribute>();
        private AuthorizationPolicy authzPolicy = AuthorizationPolicy.NONE;
        private DN baseDN; // TODO: support template variables.
        private ConnectionFactory factory;
        private AttributeDescription etagAttribute;
        private NameStrategy nameStrategy;
        private AuthzIdTemplate proxiedAuthzTemplate;
        private ReadOnUpdatePolicy readOnUpdatePolicy = CONTROLS;
        private AttributeMapper rootMapper;
        private Schema schema = Schema.getDefaultSchema();
        private boolean useSubtreeDelete;
        private boolean usePermissiveModify;

        Builder() {
            useEtagAttribute();
            useClientDNNaming("uid");
        }

        public Builder additionalLDAPAttribute(final Attribute attribute) {
            additionalLDAPAttributes.add(attribute);
            return this;
        }

        public Builder additionalLDAPAttribute(final String attribute, final Object... values) {
            return additionalLDAPAttribute(new LinkedAttribute(ad(attribute), values));
        }

        public Builder authorizationPolicy(final AuthorizationPolicy policy) {
            this.authzPolicy = ensureNotNull(policy);
            return this;
        }

        public Builder baseDN(final DN dn) {
            ensureNotNull(dn);
            this.baseDN = dn;
            return this;
        }

        public Builder baseDN(final String dn) {
            return baseDN(DN.valueOf(dn, schema));
        }

        public CollectionResourceProvider build() {
            ensureNotNull(baseDN);
            if (rootMapper == null) {
                throw new IllegalStateException("No mappings provided");
            }
            switch (authzPolicy) {
            case NONE:
                if (factory == null) {
                    throw new IllegalStateException(
                            "A connection factory must be specified when the authorization policy is 'none'");
                }
                break;
            case PROXY:
                if (proxiedAuthzTemplate == null) {
                    throw new IllegalStateException(
                            "Proxied authorization enabled but no template defined");
                }
                if (factory == null) {
                    throw new IllegalStateException(
                            "A connection factory must be specified when using proxied authorization");
                }
                break;
            case REUSE:
                // This is always ok.
                break;
            }
            return new LDAPCollectionResourceProvider(baseDN, rootMapper, nameStrategy,
                    etagAttribute, new Config(factory, readOnUpdatePolicy, authzPolicy,
                            proxiedAuthzTemplate, useSubtreeDelete, usePermissiveModify, schema),
                    additionalLDAPAttributes);
        }

        /**
         * Configures the JSON to LDAP mapping using the provided JSON
         * configuration. The caller is still required to set the connection
         * factory. See the sample configuration file for a detailed description
         * of its content.
         *
         * @param configuration
         *            The JSON configuration.
         * @return A reference to this builder.
         * @throws IllegalArgumentException
         *             If the configuration is invalid.
         */
        public Builder configureMapping(final JsonValue configuration) {
            baseDN(configuration.get("baseDN").required().asString());

            final JsonValue readOnUpdatePolicy = configuration.get("readOnUpdatePolicy");
            if (!readOnUpdatePolicy.isNull()) {
                readOnUpdatePolicy(readOnUpdatePolicy.asEnum(ReadOnUpdatePolicy.class));
            }

            for (final JsonValue v : configuration.get("additionalLDAPAttributes")) {
                final String type = v.get("type").required().asString();
                final List<Object> values = v.get("values").required().asList();
                additionalLDAPAttribute(new LinkedAttribute(type, values));
            }

            final JsonValue namingStrategy = configuration.get("namingStrategy");
            if (!namingStrategy.isNull()) {
                final String name = namingStrategy.get("strategy").required().asString();
                if (name.equalsIgnoreCase("clientDNNaming")) {
                    useClientDNNaming(namingStrategy.get("dnAttribute").required().asString());
                } else if (name.equalsIgnoreCase("clientNaming")) {
                    useClientNaming(namingStrategy.get("dnAttribute").required().asString(),
                            namingStrategy.get("idAttribute").required().asString());
                } else if (name.equalsIgnoreCase("serverNaming")) {
                    useServerNaming(namingStrategy.get("dnAttribute").required().asString(),
                            namingStrategy.get("idAttribute").required().asString());
                } else {
                    throw new IllegalArgumentException(
                            "Illegal naming strategy. Must be one of: clientDNNaming, clientNaming, or serverNaming");
                }
            }

            final JsonValue etagAttribute = configuration.get("etagAttribute");
            if (!etagAttribute.isNull()) {
                useEtagAttribute(etagAttribute.asString());
            }

            /*
             * Default to false, even though it is supported by OpenDJ, because
             * it requires additional permissions.
             */
            if (configuration.get("useSubtreeDelete").defaultTo(false).asBoolean()) {
                useSubtreeDelete();
            }

            /*
             * Default to true because it is supported by OpenDJ and does not
             * require additional permissions.
             */
            if (configuration.get("usePermissiveModify").defaultTo(true).asBoolean()) {
                usePermissiveModify();
            }

            mapper(configureObjectMapper(configuration.get("attributes").required()));

            return this;
        }

        public Builder ldapConnectionFactory(final ConnectionFactory factory) {
            this.factory = factory;
            return this;
        }

        public Builder mapper(final AttributeMapper mapper) {
            this.rootMapper = mapper;
            return this;
        }

        public Builder proxyAuthzIdTemplate(final String template) {
            this.proxiedAuthzTemplate = template != null ? new AuthzIdTemplate(template) : null;
            return this;
        }

        /**
         * Sets the policy which should be used in order to read an entry before
         * it is deleted, or after it is added or modified. The default read on
         * update policy is to use {@link ReadOnUpdatePolicy#CONTROLS controls}.
         *
         * @param policy
         *            The policy which should be used in order to read an entry
         *            before it is deleted, or after it is added or modified.
         * @return A reference to this builder.
         */
        public Builder readOnUpdatePolicy(final ReadOnUpdatePolicy policy) {
            this.readOnUpdatePolicy = ensureNotNull(policy);
            return this;
        }

        /**
         * Sets the schema which should be used when attribute types and
         * controls.
         *
         * @param schema
         *            The schema which should be used when attribute types and
         *            controls.
         * @return A reference to this builder.
         */
        public Builder schema(final Schema schema) {
            this.schema = ensureNotNull(schema);
            return this;
        }

        public Builder useClientDNNaming(final AttributeType attribute) {
            this.nameStrategy = new DNNameStrategy(attribute);
            return this;
        }

        public Builder useClientDNNaming(final String attribute) {
            return useClientDNNaming(at(attribute));
        }

        public Builder useClientNaming(final AttributeType dnAttribute,
                final AttributeDescription idAttribute) {
            this.nameStrategy = new AttributeNameStrategy(dnAttribute, idAttribute, false);
            return this;
        }

        public Builder useClientNaming(final String dnAttribute, final String idAttribute) {
            return useClientNaming(at(dnAttribute), ad(idAttribute));
        }

        public Builder useEtagAttribute() {
            return useEtagAttribute("etag");
        }

        public Builder useEtagAttribute(final AttributeDescription attribute) {
            this.etagAttribute = attribute;
            return this;
        }

        public Builder useEtagAttribute(final String attribute) {
            return useEtagAttribute(ad(attribute));
        }

        public Builder useServerEntryUUIDNaming(final AttributeType dnAttribute) {
            return useServerNaming(dnAttribute, AttributeDescription
                    .create(getEntryUUIDAttributeType()));
        }

        public Builder useServerEntryUUIDNaming(final String dnAttribute) {
            return useServerEntryUUIDNaming(at(dnAttribute));
        }

        public Builder useServerNaming(final AttributeType dnAttribute,
                final AttributeDescription idAttribute) {
            this.nameStrategy = new AttributeNameStrategy(dnAttribute, idAttribute, true);
            return this;
        }

        public Builder useServerNaming(final String dnAttribute, final String idAttribute) {
            return useServerNaming(at(dnAttribute), ad(idAttribute));
        }

        public Builder useSubtreeDelete() {
            this.useSubtreeDelete = true;
            return this;
        }

        public Builder usePermissiveModify() {
            this.usePermissiveModify = true;
            return this;
        }

        private AttributeDescription ad(final String attribute) {
            return AttributeDescription.valueOf(attribute, schema);
        }

        private AttributeType at(final String attribute) {
            return schema.getAttributeType(attribute);
        }

        private AttributeMapper configureMapper(final JsonValue mapper) {
            if (mapper.isDefined("constant")) {
                return constant(mapper.get("constant").getObject());
            } else if (mapper.isDefined("simple")) {
                final JsonValue config = mapper.get("simple");
                final SimpleAttributeMapper s =
                        simple(ad(config.get("ldapAttribute").required().asString()));
                if (config.isDefined("defaultJSONValue")) {
                    s.defaultJSONValue(config.get("defaultJSONValue").getObject());
                }
                if (config.get("isBinary").defaultTo(false).asBoolean()) {
                    s.isBinary();
                }
                if (config.get("isRequired").defaultTo(false).asBoolean()) {
                    s.isRequired();
                }
                if (config.get("isSingleValued").defaultTo(false).asBoolean()) {
                    s.isSingleValued();
                }
                s.writability(parseWritability(mapper, config));
                return s;
            } else if (mapper.isDefined("reference")) {
                final JsonValue config = mapper.get("reference");
                final AttributeDescription ldapAttribute =
                        ad(config.get("ldapAttribute").required().asString());
                final DN baseDN = DN.valueOf(config.get("baseDN").required().asString(), schema);
                final AttributeDescription primaryKey =
                        ad(config.get("primaryKey").required().asString());
                final AttributeMapper m = configureMapper(config.get("mapper").required());
                final ReferenceAttributeMapper r = reference(ldapAttribute, baseDN, primaryKey, m);
                if (config.get("isRequired").defaultTo(false).asBoolean()) {
                    r.isRequired();
                }
                if (config.get("isSingleValued").defaultTo(false).asBoolean()) {
                    r.isSingleValued();
                }
                if (config.isDefined("searchFilter")) {
                    r.searchFilter(config.get("searchFilter").asString());
                }
                r.writability(parseWritability(mapper, config));
                return r;
            } else if (mapper.isDefined("object")) {
                return configureObjectMapper(mapper.get("object"));
            } else {
                throw new JsonValueException(mapper,
                        "Illegal mapping: must contain constant, simple, or object");
            }
        }

        private ObjectAttributeMapper configureObjectMapper(final JsonValue mapper) {
            final ObjectAttributeMapper object = object();
            for (final String attribute : mapper.keys()) {
                object.attribute(attribute, configureMapper(mapper.get(attribute)));
            }
            return object;
        }

        private WritabilityPolicy parseWritability(final JsonValue mapper, final JsonValue config) {
            if (config.isDefined("writability")) {
                final String writability = config.get("writability").asString();
                if (writability.equalsIgnoreCase("readOnly")) {
                    return WritabilityPolicy.READ_ONLY;
                } else if (writability.equalsIgnoreCase("readOnlyDiscardWrites")) {
                    return WritabilityPolicy.READ_ONLY_DISCARD_WRITES;
                } else if (writability.equalsIgnoreCase("createOnly")) {
                    return WritabilityPolicy.CREATE_ONLY;
                } else if (writability.equalsIgnoreCase("createOnlyDiscardWrites")) {
                    return WritabilityPolicy.CREATE_ONLY_DISCARD_WRITES;
                } else if (writability.equalsIgnoreCase("readWrite")) {
                    return WritabilityPolicy.READ_WRITE;
                } else {
                    throw new JsonValueException(mapper,
                            "Illegal writability: must be one of readOnly, readOnlyDiscardWrites, "
                                    + "createOnly, createOnlyDiscardWrites, or readWrite");
                }
            } else {
                return WritabilityPolicy.READ_WRITE;
            }
        }
    }

    private static final class AttributeNameStrategy extends NameStrategy {
        private final AttributeDescription dnAttribute;
        private final AttributeDescription idAttribute;
        private final boolean isServerProvided;

        private AttributeNameStrategy(final AttributeType dnAttribute,
                final AttributeDescription idAttribute, final boolean isServerProvided) {
            this.dnAttribute = AttributeDescription.create(dnAttribute);
            if (dnAttribute.equals(idAttribute)) {
                throw new IllegalArgumentException("DN and ID attributes must be different");
            }
            this.idAttribute = ensureNotNull(idAttribute);
            this.isServerProvided = isServerProvided;
        }

        @Override
        SearchRequest createSearchRequest(final Context c, final DN baseDN, final String resourceId) {
            return newSearchRequest(baseDN, SearchScope.SINGLE_LEVEL, Filter.equality(idAttribute
                    .toString(), resourceId));
        }

        @Override
        void getLDAPAttributes(final Context c, final Set<String> ldapAttributes) {
            ldapAttributes.add(idAttribute.toString());
        }

        @Override
        String getResourceId(final Context c, final Entry entry) {
            return entry.parseAttribute(idAttribute).asString();
        }

        @Override
        void setResourceId(final Context c, final DN baseDN, final String resourceId,
                final Entry entry) throws ResourceException {
            if (isServerProvided) {
                if (resourceId != null) {
                    throw new BadRequestException("Resources cannot be created with a "
                            + "client provided resource ID");
                }
            } else {
                entry.addAttribute(new LinkedAttribute(idAttribute, ByteString.valueOf(resourceId)));
            }
            final String rdnValue = entry.parseAttribute(dnAttribute).asString();
            final RDN rdn = new RDN(dnAttribute.getAttributeType(), rdnValue);
            entry.setName(baseDN.child(rdn));

        }
    }

    private static final class DNNameStrategy extends NameStrategy {
        private final AttributeDescription attribute;

        private DNNameStrategy(final AttributeType attribute) {
            this.attribute = AttributeDescription.create(attribute);
        }

        @Override
        SearchRequest createSearchRequest(final Context c, final DN baseDN, final String resourceId) {
            return newSearchRequest(baseDN.child(rdn(resourceId)), SearchScope.BASE_OBJECT, Filter
                    .objectClassPresent());
        }

        @Override
        void getLDAPAttributes(final Context c, final Set<String> ldapAttributes) {
            ldapAttributes.add(attribute.toString());
        }

        @Override
        String getResourceId(final Context c, final Entry entry) {
            return entry.parseAttribute(attribute).asString();
        }

        @Override
        void setResourceId(final Context c, final DN baseDN, final String resourceId,
                final Entry entry) throws ResourceException {
            if (resourceId != null) {
                entry.setName(baseDN.child(rdn(resourceId)));
                entry.addAttribute(new LinkedAttribute(attribute, ByteString.valueOf(resourceId)));
            } else if (entry.getAttribute(attribute) != null) {
                entry.setName(baseDN.child(rdn(entry.parseAttribute(attribute).asString())));
            } else {
                throw new BadRequestException("Resources cannot be created without a "
                        + "client provided resource ID");
            }
        }

        private RDN rdn(final String resourceId) {
            return new RDN(attribute.getAttributeType(), resourceId);
        }

    }

    /**
     * Adapts a {@code Throwable} to a {@code ResourceException}. If the
     * {@code Throwable} is an LDAP {@code ErrorResultException} then an
     * appropriate {@code ResourceException} is returned, otherwise an
     * {@code InternalServerErrorException} is returned.
     *
     * @param t
     *            The {@code Throwable} to be converted.
     * @return The equivalent resource exception.
     */
    public static ResourceException asResourceException(final Throwable t) {
        int resourceResultCode;
        try {
            throw t;
        } catch (final ResourceException e) {
            return e;
        } catch (final AssertionFailureException e) {
            resourceResultCode = ResourceException.VERSION_MISMATCH;
        } catch (final ConstraintViolationException e) {
            final ResultCode rc = e.getResult().getResultCode();
            if (rc.equals(ResultCode.ENTRY_ALREADY_EXISTS)) {
                resourceResultCode = ResourceException.VERSION_MISMATCH; // Consistent with MVCC.
            } else {
                // Schema violation, etc.
                resourceResultCode = ResourceException.BAD_REQUEST;
            }
        } catch (final AuthenticationException e) {
            resourceResultCode = 401;
        } catch (final AuthorizationException e) {
            resourceResultCode = ResourceException.FORBIDDEN;
        } catch (final ConnectionException e) {
            resourceResultCode = ResourceException.UNAVAILABLE;
        } catch (final EntryNotFoundException e) {
            resourceResultCode = ResourceException.NOT_FOUND;
        } catch (final MultipleEntriesFoundException e) {
            resourceResultCode = ResourceException.INTERNAL_ERROR;
        } catch (final TimeoutResultException e) {
            resourceResultCode = 408;
        } catch (final ErrorResultException e) {
            final ResultCode rc = e.getResult().getResultCode();
            if (rc.equals(ResultCode.ADMIN_LIMIT_EXCEEDED)) {
                resourceResultCode = 413; // Request Entity Too Large
            } else if (rc.equals(ResultCode.SIZE_LIMIT_EXCEEDED)) {
                resourceResultCode = 413; // Request Entity Too Large
            } else {
                resourceResultCode = ResourceException.INTERNAL_ERROR;
            }
        } catch (final Throwable tmp) {
            resourceResultCode = ResourceException.INTERNAL_ERROR;
        }
        return ResourceException.getException(resourceResultCode, t.getMessage(), t);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a new connection factory using the named configuration in the
     * provided JSON list of factory configurations. See the sample
     * configuration file for a detailed description of its content.
     *
     * @param configuration
     *            The JSON configuration.
     * @param name
     *            The name of the connection factory configuration to be parsed.
     * @return A new connection factory using the provided JSON configuration.
     * @throws IllegalArgumentException
     *             If the configuration is invalid.
     */
    public static ConnectionFactory configureConnectionFactory(final JsonValue configuration,
            final String name) {
        final JsonValue normalizedConfiguration =
                normalizeConnectionFactory(configuration, name, 0);
        return configureConnectionFactory(normalizedConfiguration);
    }

    public static AttributeMapper constant(final Object value) {
        return new JSONConstantAttributeMapper(value);
    }

    public static ObjectAttributeMapper object() {
        return new ObjectAttributeMapper();
    }

    public static ReferenceAttributeMapper reference(final AttributeDescription attribute,
            final DN baseDN, final AttributeDescription primaryKey, final AttributeMapper mapper) {
        return new ReferenceAttributeMapper(attribute, baseDN, primaryKey, mapper);
    }

    public static ReferenceAttributeMapper reference(final String attribute, final String baseDN,
            final String primaryKey, final AttributeMapper mapper) {
        return reference(AttributeDescription.valueOf(attribute), DN.valueOf(baseDN),
                AttributeDescription.valueOf(primaryKey), mapper);
    }

    public static SimpleAttributeMapper simple(final AttributeDescription attribute) {
        return new SimpleAttributeMapper(attribute);
    }

    public static SimpleAttributeMapper simple(final String attribute) {
        return simple(AttributeDescription.valueOf(attribute));
    }

    private static ConnectionFactory configureConnectionFactory(final JsonValue configuration) {
        // Parse pool parameters,
        final int connectionPoolSize =
                Math.max(configuration.get("connectionPoolSize").defaultTo(10).asInteger(), 1);
        final int heartBeatIntervalSeconds =
                Math.max(configuration.get("heartBeatIntervalSeconds").defaultTo(30).asInteger(), 1);

        // Parse authentication parameters.
        final BindRequest bindRequest;
        if (configuration.isDefined("authentication")) {
            final JsonValue authn = configuration.get("authentication");
            if (authn.isDefined("simple")) {
                final JsonValue simple = authn.get("simple");
                bindRequest =
                        Requests.newSimpleBindRequest(simple.get("bindDN").required().asString(),
                                simple.get("bindPassword").required().asString().toCharArray());
            } else {
                throw new IllegalArgumentException("Only simple authentication is supported");
            }
        } else {
            bindRequest = null;
        }

        // Parse primary data center.
        final JsonValue primaryLDAPServers = configuration.get("primaryLDAPServers");
        if (!primaryLDAPServers.isList() || primaryLDAPServers.size() == 0) {
            throw new IllegalArgumentException("No primaryLDAPServers");
        }
        final ConnectionFactory primary =
                parseLDAPServers(primaryLDAPServers, bindRequest, connectionPoolSize,
                        heartBeatIntervalSeconds);

        // Parse secondary data center(s).
        final JsonValue secondaryLDAPServers = configuration.get("secondaryLDAPServers");
        final ConnectionFactory secondary;
        if (secondaryLDAPServers.isList()) {
            if (secondaryLDAPServers.size() > 0) {
                secondary =
                        parseLDAPServers(secondaryLDAPServers, bindRequest, connectionPoolSize,
                                heartBeatIntervalSeconds);
            } else {
                secondary = null;
            }
        } else if (!secondaryLDAPServers.isNull()) {
            throw new IllegalArgumentException("Invalid secondaryLDAPServers configuration");
        } else {
            secondary = null;
        }

        // Create fail-over.
        if (secondary != null) {
            return Connections.newLoadBalancer(new FailoverLoadBalancingAlgorithm(Arrays.asList(
                    primary, secondary), heartBeatIntervalSeconds, TimeUnit.SECONDS));
        } else {
            return primary;
        }
    }

    private static JsonValue normalizeConnectionFactory(final JsonValue configuration,
            final String name, final int depth) {
        // Protect against infinite recursion in the configuration.
        if (depth > 100) {
            throw new IllegalArgumentException(
                    "The LDAP server configuration '"
                            + name
                            + "' could not be parsed because of potential circular inheritance dependencies");
        }

        final JsonValue current = configuration.get(name).required();
        if (current.isDefined("inheritFrom")) {
            // Inherit missing fields from inherited configuration.
            final JsonValue parent =
                    normalizeConnectionFactory(configuration,
                            current.get("inheritFrom").asString(), depth + 1);
            final Map<String, Object> normalized =
                    new LinkedHashMap<String, Object>(parent.asMap());
            normalized.putAll(current.asMap());
            normalized.remove("inheritFrom");
            return new JsonValue(normalized);
        } else {
            // No normalization required.
            return current;
        }
    }

    private static ConnectionFactory parseLDAPServers(final JsonValue config,
            final BindRequest bindRequest, final int connectionPoolSize,
            final int heartBeatIntervalSeconds) {
        final List<ConnectionFactory> servers = new ArrayList<ConnectionFactory>(config.size());
        for (final JsonValue server : config) {
            final String host = server.get("hostname").required().asString();
            final int port = server.get("port").required().asInteger();
            ConnectionFactory factory = new LDAPConnectionFactory(host, port);
            if (bindRequest != null) {
                factory = Connections.newAuthenticatedConnectionFactory(factory, bindRequest);
            }
            if (connectionPoolSize > 1) {
                factory =
                        Connections.newHeartBeatConnectionFactory(factory,
                                heartBeatIntervalSeconds, TimeUnit.SECONDS);
                factory = Connections.newFixedConnectionPool(factory, connectionPoolSize);
            }
            servers.add(factory);
        }
        if (servers.size() > 1) {
            return Connections.newLoadBalancer(new RoundRobinLoadBalancingAlgorithm(servers,
                    heartBeatIntervalSeconds, TimeUnit.SECONDS));
        } else {
            return servers.get(0);
        }
    }

    private Rest2LDAP() {
        // Prevent instantiation.
    }

}
