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
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.fluent.JsonValueException;
import org.forgerock.json.resource.BadRequestException;
import org.forgerock.json.resource.CollectionResourceProvider;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.Connections;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.FailoverLoadBalancingAlgorithm;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.LinkedAttribute;
import org.forgerock.opendj.ldap.RDN;
import org.forgerock.opendj.ldap.RoundRobinLoadBalancingAlgorithm;
import org.forgerock.opendj.ldap.SearchScope;
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
        private DN baseDN; // TODO: support template variables.
        private ConnectionFactory factory;
        private MVCCStrategy mvccStrategy;
        private NameStrategy nameStrategy;
        private ReadOnUpdatePolicy readOnUpdatePolicy = CONTROLS;
        private AttributeMapper rootMapper;
        private Schema schema = Schema.getDefaultSchema();

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

        public Builder baseDN(final DN dn) {
            ensureNotNull(dn);
            this.baseDN = dn;
            return this;
        }

        public Builder baseDN(final String dn) {
            return baseDN(DN.valueOf(dn, schema));
        }

        public CollectionResourceProvider build() {
            ensureNotNull(factory);
            ensureNotNull(baseDN);
            if (rootMapper == null) {
                throw new IllegalStateException("No mappings provided");
            }
            return new LDAPCollectionResourceProvider(baseDN, rootMapper, factory, nameStrategy,
                    mvccStrategy, new Config(readOnUpdatePolicy, schema), additionalLDAPAttributes);
        }

        /**
         * Configures the connection factory using the provided JSON
         * configuration. See
         * {@link Rest2LDAP#configureConnectionFactory(JsonValue)} for a
         * detailed specification of the JSON configuration.
         * 
         * @param configuration
         *            The JSON configuration.
         * @return A reference to this builder.
         * @throws IllegalArgumentException
         *             If the configuration is invalid.
         */
        public Builder configureConnectionFactory(final JsonValue configuration) {
            connectionFactory(Rest2LDAP.configureConnectionFactory(configuration));
            return this;
        }

        /**
         * Configures the JSON to LDAP mapping using the provided JSON
         * configuration. The caller is still required to set the connection
         * factory. The configuration should look like this, excluding the
         * C-like comments:
         * 
         * <pre>
         * {
         *     // The base DN beneath which LDAP entries are to be found.
         *     "baseDN" : "ou=people,dc=example,dc=com",
         * 
         *     // The mechanism which should be used for read resources during updates, must be
         *     // one of "disabled", "controls", or "search".
         *     "readOnUpdatePolicy" : "controls",
         * 
         *     // Additional LDAP attributes which should be included with entries during add (create) operations.
         *     "additionalLDAPAttributes" : [
         *         {
         *             "type" : "objectClass",
         *             "values" : [
         *                 "top",
         *                 "person"
         *             ]
         *         }
         *     ],
         * 
         *     // The strategy which should be used for deriving LDAP entry names from JSON resources.
         *     "namingStrategy" : {
         *         // Option 1) the RDN and resource ID are both derived from a single user attribute in the entry.
         *         "strategy" : "clientDNNaming",
         *         "dnAttribute" : "uid"
         * 
         *         // Option 2) the RDN and resource ID are derived from separate user attributes in the entry.
         *         "strategy" : "clientNaming",
         *         "dnAttribute" : "uid",
         *         "idAttribute" : "mail"
         * 
         *         // Option 3) the RDN and is derived from a user attribute and the resource ID from an operational
         *         //           attribute in the entry.
         *         "strategy" : "serverNaming",
         *         "dnAttribute" : "uid",
         *         "idAttribute" : "entryUUID"
         *     },
         * 
         *     // The attribute which will be used for performing MVCC.
         *     "etagAttribute" : "etag",
         * 
         *     // The JSON to LDAP attribute mappings.
         *     "attributes" : {
         *         "schemas"     : { "constant" : [ "urn:scim:schemas:core:1.0" ] },
         *         "id"          : { "simple"   : { "ldapAttribute" : "uid", "isSingleValued" : true, "isRequired" : true, "writability" : "createOnly" } },
         *         "rev"         : { "simple"   : { "ldapAttribute" : "etag", "isSingleValued" : true, "writability" : "readOnly" } },
         *         "userName"    : { "simple"   : { "ldapAttribute" : "mail", "isSingleValued" : true, "writability" : "readOnly" } },
         *         "displayName" : { "simple"   : { "ldapAttribute" : "cn", "isSingleValued" : true, "isRequired" : true } },
         *         "name"        : { "object"   : {
         *             "givenName"  : { "simple"   : { "ldapAttribute" : "givenName", "isSingleValued" : true } },
         *             "familyName" : { "simple"   : { "ldapAttribute" : "sn", "isSingleValued" : true, "isRequired" : true } },
         *         },
         *         "manager"     : { "reference" : {
         *             "ldapAttribute" : "manager",
         *             "mapping"       : { "object" : {
         *                 "id"          : { "simple"   : { "ldapAttribute" : "uid", "isSingleValued" : true } },
         *                 "displayName" : { "simple"   : { "ldapAttribute" : "cn", "isSingleValued" : true } }
         *             } }
         *         },
         *         ...
         *     }
         * }
         * </pre>
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

            mapper(configureObjectMapper(configuration.get("attributes").required()));

            return this;
        }

        public Builder connectionFactory(final ConnectionFactory factory) {
            ensureNotNull(factory);
            this.factory = factory;
            return this;
        }

        public Builder mapper(final AttributeMapper mapper) {
            this.rootMapper = mapper;
            return this;
        }

        /**
         * Sets the policy which should be used in order to read an entry before
         * it is deleted, or after it is added or modified.
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
            this.mvccStrategy = new AttributeMVCCStrategy(attribute);
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
                if (config.isDefined("defaultLDAPValue")) {
                    s.defaultLDAPValue(config.get("defaultLDAPValue").getObject());
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
                final AttributeMapper m = configureMapper(config.get("mapper").required());
                final ReferenceAttributeMapper r = reference(ldapAttribute, m);
                if (config.get("isRequired").defaultTo(false).asBoolean()) {
                    r.isRequired();
                }
                if (config.get("isSingleValued").defaultTo(false).asBoolean()) {
                    r.isSingleValued();
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

    private static final class AttributeMVCCStrategy extends MVCCStrategy {
        private final AttributeDescription ldapAttribute;

        private AttributeMVCCStrategy(final AttributeDescription ldapAttribute) {
            this.ldapAttribute = ldapAttribute;
        }

        @Override
        void getLDAPAttributes(final Context c, final Set<String> ldapAttributes) {
            ldapAttributes.add(ldapAttribute.toString());
        }

        @Override
        String getRevisionFromEntry(final Context c, final Entry entry) {
            return entry.parseAttribute(ldapAttribute).asString();
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

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a new connection factory using the provided JSON configuration.
     * The configuration should look like this, excluding the C-like comments:
     * 
     * <pre>
     * {
     *     // The primary data center, must contain at least one LDAP server.
     *     "primaryLDAPServers" : [
     *         {
     *             "hostname" : "host1.example.com",
     *             "port"     : 389
     *         },
     *         {
     *             "hostname" : "host2.example.com",
     *             "port"     : 389
     *         },
     *     ],
     * 
     *     // The optional secondary (fail-over) data center.
     *     "secondaryLDAPServers" : [
     *         {
     *             "hostname" : "host3.example.com",
     *             "port"     : 389
     *         },
     *         {
     *             "hostname" : "host4.example.com",
     *             "port"     : 389
     *         },
     *     ],
     * 
     *     // Connection pool configuration.
     *     "connectionPoolSize"       : 10,
     *     "heartBeatIntervalSeconds" : 30,
     * 
     *     // SSL/TLS configuration (optional and TBD).
     *     "useSSL" : {
     *         // Elect to use StartTLS instead of SSL.
     *         "useStartTLS" : true,
     *         ...
     *     },
     * 
     *     // Authentication configuration (optional and TBD).
     *     "authentication" : {
     *         "bindDN"   : "cn=directory manager",
     *         "password" : "password"
     *     },
     * }
     * </pre>
     * 
     * @param configuration
     *            The JSON configuration.
     * @return A new connection factory using the provided JSON configuration.
     * @throws IllegalArgumentException
     *             If the configuration is invalid.
     */
    public static ConnectionFactory configureConnectionFactory(final JsonValue configuration) {
        // Parse pool parameters,
        final int connectionPoolSize =
                Math.max(configuration.get("connectionPoolSize").defaultTo(10).asInteger(), 1);
        final int heartBeatIntervalSeconds =
                Math.max(configuration.get("heartBeatIntervalSeconds").defaultTo(30).asInteger(), 1);

        // Parse authentication parameters.
        final BindRequest bindRequest;
        if (configuration.isDefined("authentication")) {
            final JsonValue authn = configuration.get("authentication");
            bindRequest =
                    Requests.newSimpleBindRequest(authn.get("bindDN").required().asString(), authn
                            .get("password").required().asString().toCharArray());
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
        if (secondaryLDAPServers.isList() && secondaryLDAPServers.size() != 0) {
            secondary =
                    parseLDAPServers(secondaryLDAPServers, bindRequest, connectionPoolSize,
                            heartBeatIntervalSeconds);
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

    public static AttributeMapper constant(final Object value) {
        return new JSONConstantAttributeMapper(value);
    }

    public static ObjectAttributeMapper object() {
        return new ObjectAttributeMapper();
    }

    public static ReferenceAttributeMapper reference(final AttributeDescription attribute,
            final AttributeMapper mapper) {
        return new ReferenceAttributeMapper(attribute, mapper);
    }

    public static ReferenceAttributeMapper reference(final String attribute,
            final AttributeMapper mapper) {
        return reference(AttributeDescription.valueOf(attribute), mapper);
    }

    public static SimpleAttributeMapper simple(final AttributeDescription attribute) {
        return new SimpleAttributeMapper(attribute);
    }

    public static SimpleAttributeMapper simple(final String attribute) {
        return simple(AttributeDescription.valueOf(attribute));
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
