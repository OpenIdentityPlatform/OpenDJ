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
 * Copyright 2013-2015 ForgeRock AS.
 */
package org.forgerock.opendj.rest2ldap;

import static java.util.Arrays.asList;
import static org.forgerock.json.resource.ResourceException.newResourceException;
import static org.forgerock.opendj.ldap.Connections.newCachedConnectionPool;
import static org.forgerock.opendj.ldap.Connections.newFailoverLoadBalancer;
import static org.forgerock.opendj.ldap.Connections.newRoundRobinLoadBalancer;
import static org.forgerock.opendj.ldap.LDAPConnectionFactory.*;
import static org.forgerock.opendj.ldap.LoadBalancingAlgorithm.LOAD_BALANCER_MONITORING_INTERVAL;
import static org.forgerock.opendj.ldap.requests.Requests.newSearchRequest;
import static org.forgerock.opendj.ldap.schema.CoreSchema.getEntryUUIDAttributeType;
import static org.forgerock.opendj.rest2ldap.ReadOnUpdatePolicy.CONTROLS;
import static org.forgerock.opendj.rest2ldap.Utils.ensureNotNull;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
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
import org.forgerock.opendj.ldap.ConstraintViolationException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.EntryNotFoundException;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LinkedAttribute;
import org.forgerock.opendj.ldap.MultipleEntriesFoundException;
import org.forgerock.opendj.ldap.RDN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SSLContextBuilder;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.TimeoutResultException;
import org.forgerock.opendj.ldap.TrustManagers;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.util.Options;
import org.forgerock.util.time.Duration;

/** Provides core factory methods and builders for constructing LDAP resource collections. */
public final class Rest2LDAP {
    /** Indicates whether or not LDAP client connections should use SSL or StartTLS. */
    private enum ConnectionSecurity {
        NONE, SSL, STARTTLS
    }

    /**
     * Specifies the mechanism which should be used for trusting certificates
     * presented by the LDAP server.
     */
    private enum TrustManagerType {
        TRUSTALL, JVM, FILE
    }

    /** A builder for incrementally constructing LDAP resource collections. */
    public static final class Builder {
        private final List<Attribute> additionalLDAPAttributes = new LinkedList<>();
        private AuthorizationPolicy authzPolicy = AuthorizationPolicy.NONE;
        private DN baseDN; // TODO: support template variables.
        private AttributeDescription etagAttribute;
        private ConnectionFactory factory;
        private NameStrategy nameStrategy;
        private AuthzIdTemplate proxiedAuthzTemplate;
        private ReadOnUpdatePolicy readOnUpdatePolicy = CONTROLS;
        private AttributeMapper rootMapper;
        private Schema schema = Schema.getDefaultSchema();
        private boolean usePermissiveModify;
        private boolean useSubtreeDelete;

        private Builder() {
            useEtagAttribute();
            useClientDNNaming("uid");
        }

        /**
         * Specifies an additional LDAP attribute which should be included with
         * new LDAP entries when they are created. Use this method to specify
         * the LDAP objectClass attribute.
         *
         * @param attribute
         *            The additional LDAP attribute to be included with new LDAP
         *            entries.
         * @return A reference to this LDAP resource collection builder.
         */
        public Builder additionalLDAPAttribute(final Attribute attribute) {
            additionalLDAPAttributes.add(attribute);
            return this;
        }

        /**
         * Specifies an additional LDAP attribute which should be included with
         * new LDAP entries when they are created. Use this method to specify
         * the LDAP objectClass attribute.
         *
         * @param attribute
         *            The name of the additional LDAP attribute to be included
         *            with new LDAP entries.
         * @param values
         *            The value(s) of the additional LDAP attribute.
         * @return A reference to this LDAP resource collection builder.
         */
        public Builder additionalLDAPAttribute(final String attribute, final Object... values) {
            return additionalLDAPAttribute(new LinkedAttribute(ad(attribute), values));
        }

        /**
         * Sets the policy which should be for performing authorization.
         *
         * @param policy
         *            The policy which should be for performing authorization.
         * @return A reference to this LDAP resource collection builder.
         */
        public Builder authorizationPolicy(final AuthorizationPolicy policy) {
            this.authzPolicy = ensureNotNull(policy);
            return this;
        }

        /**
         * Sets the base DN beneath which LDAP entries (resources) are to be found.
         *
         * @param dn
         *            The base DN.
         * @return A reference to this LDAP resource collection builder.
         */
        public Builder baseDN(final DN dn) {
            ensureNotNull(dn);
            this.baseDN = dn;
            return this;
        }

        /**
         * Sets the base DN beneath which LDAP entries (resources) are to be found.
         *
         * @param dn
         *            The base DN.
         * @return A reference to this LDAP resource collection builder.
         */
        public Builder baseDN(final String dn) {
            return baseDN(DN.valueOf(dn, schema));
        }

        /**
         * Creates a new LDAP resource collection configured using this builder.
         *
         * @return The new LDAP resource collection.
         */
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
         * @return A reference to this LDAP resource collection builder.
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

        /**
         * Sets the LDAP connection factory to be used for accessing the LDAP
         * directory. Each HTTP request will obtain a single connection from the
         * factory and then close it once the HTTP response has been sent. It is
         * recommended that the provided connection factory supports connection
         * pooling.
         *
         * @param factory
         *            The LDAP connection factory to be used for accessing the
         *            LDAP directory.
         * @return A reference to this LDAP resource collection builder.
         */
        public Builder ldapConnectionFactory(final ConnectionFactory factory) {
            this.factory = factory;
            return this;
        }

        /**
         * Sets the attribute mapper which should be used for mapping JSON
         * resources to and from LDAP entries.
         *
         * @param mapper
         *            The attribute mapper.
         * @return A reference to this LDAP resource collection builder.
         */
        public Builder mapper(final AttributeMapper mapper) {
            this.rootMapper = mapper;
            return this;
        }

        /**
         * Sets the authorization ID template which will be used for proxied
         * authorization. Template parameters are specified by including the
         * parameter name surrounded by curly braces. The template should
         * contain fields which are expected to be found in the security context
         * create during authentication, e.g. "dn:{dn}" or "u:{id}".
         *
         * @param template
         *            The authorization ID template which will be used for
         *            proxied authorization.
         * @return A reference to this LDAP resource collection builder.
         */
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
         * @return A reference to this LDAP resource collection builder.
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
         * @return A reference to this LDAP resource collection builder.
         */
        public Builder schema(final Schema schema) {
            this.schema = ensureNotNull(schema);
            return this;
        }

        /**
         * Indicates that the JSON resource ID must be provided by the user, and
         * will be used for naming the associated LDAP entry. More specifically,
         * LDAP entry names will be derived by appending a single RDN to the
         * {@link #baseDN(String) base DN} composed of the specified attribute
         * type and LDAP value taken from the LDAP entry once attribute mapping
         * has been performed.
         * <p>
         * Note that this naming policy requires that the user provides the
         * resource name when creating new resources, which means it must be
         * included in the resource content when not specified explicitly in the
         * create request.
         *
         * @param attribute
         *            The LDAP attribute which will be used for naming.
         * @return A reference to this LDAP resource collection builder.
         */
        public Builder useClientDNNaming(final AttributeType attribute) {
            this.nameStrategy = new DNNameStrategy(attribute);
            return this;
        }

        /**
         * Indicates that the JSON resource ID must be provided by the user, and
         * will be used for naming the associated LDAP entry. More specifically,
         * LDAP entry names will be derived by appending a single RDN to the
         * {@link #baseDN(String) base DN} composed of the specified attribute
         * type and LDAP value taken from the LDAP entry once attribute mapping
         * has been performed.
         * <p>
         * Note that this naming policy requires that the user provides the
         * resource name when creating new resources, which means it must be
         * included in the resource content when not specified explicitly in the
         * create request.
         *
         * @param attribute
         *            The LDAP attribute which will be used for naming.
         * @return A reference to this LDAP resource collection builder.
         */
        public Builder useClientDNNaming(final String attribute) {
            return useClientDNNaming(at(attribute));
        }

        /**
         * Indicates that the JSON resource ID must be provided by the user, but
         * will not be used for naming the associated LDAP entry. Instead the
         * JSON resource ID will be taken from the {@code idAttribute} in the
         * LDAP entry, and the LDAP entry name will be derived by appending a
         * single RDN to the {@link #baseDN(String) base DN} composed of the
         * {@code dnAttribute} taken from the LDAP entry once attribute mapping
         * has been performed.
         * <p>
         * Note that this naming policy requires that the user provides the
         * resource name when creating new resources, which means it must be
         * included in the resource content when not specified explicitly in the
         * create request.
         *
         * @param dnAttribute
         *            The attribute which will be used for naming LDAP entries.
         * @param idAttribute
         *            The attribute which will be used for JSON resource IDs.
         * @return A reference to this LDAP resource collection builder.
         */
        public Builder useClientNaming(final AttributeType dnAttribute,
                final AttributeDescription idAttribute) {
            this.nameStrategy = new AttributeNameStrategy(dnAttribute, idAttribute, false);
            return this;
        }

        /**
         * Indicates that the JSON resource ID must be provided by the user, but
         * will not be used for naming the associated LDAP entry. Instead the
         * JSON resource ID will be taken from the {@code idAttribute} in the
         * LDAP entry, and the LDAP entry name will be derived by appending a
         * single RDN to the {@link #baseDN(String) base DN} composed of the
         * {@code dnAttribute} taken from the LDAP entry once attribute mapping
         * has been performed.
         * <p>
         * Note that this naming policy requires that the user provides the
         * resource name when creating new resources, which means it must be
         * included in the resource content when not specified explicitly in the
         * create request.
         *
         * @param dnAttribute
         *            The attribute which will be used for naming LDAP entries.
         * @param idAttribute
         *            The attribute which will be used for JSON resource IDs.
         * @return A reference to this LDAP resource collection builder.
         */
        public Builder useClientNaming(final String dnAttribute, final String idAttribute) {
            return useClientNaming(at(dnAttribute), ad(idAttribute));
        }

        /**
         * Indicates that the "etag" LDAP attribute should be used for resource
         * versioning. This is the default behavior.
         *
         * @return A reference to this LDAP resource collection builder.
         */
        public Builder useEtagAttribute() {
            return useEtagAttribute("etag");
        }

        /**
         * Indicates that the provided LDAP attribute should be used for
         * resource versioning. The "etag" attribute will be used by default.
         *
         * @param attribute
         *            The name of the attribute to use for versioning, or
         *            {@code null} if resource versioning will not supported.
         * @return A reference to this LDAP resource collection builder.
         */
        public Builder useEtagAttribute(final AttributeDescription attribute) {
            this.etagAttribute = attribute;
            return this;
        }

        /**
         * Indicates that the provided LDAP attribute should be used for
         * resource versioning. The "etag" attribute will be used by default.
         *
         * @param attribute
         *            The name of the attribute to use for versioning, or
         *            {@code null} if resource versioning will not supported.
         * @return A reference to this LDAP resource collection builder.
         */
        public Builder useEtagAttribute(final String attribute) {
            return useEtagAttribute(attribute != null ? ad(attribute) : null);
        }

        /**
         * Indicates that all LDAP modify operations should be performed using
         * the LDAP permissive modify control. The default behavior is to not
         * use the permissive modify control. Use of the control is strongly
         * recommended.
         *
         * @return A reference to this LDAP resource collection builder.
         */
        public Builder usePermissiveModify() {
            this.usePermissiveModify = true;
            return this;
        }

        /**
         * Indicates that the JSON resource ID will be derived from the server
         * provided "entryUUID" LDAP attribute. The LDAP entry name will be
         * derived by appending a single RDN to the {@link #baseDN(String) base
         * DN} composed of the {@code dnAttribute} taken from the LDAP entry
         * once attribute mapping has been performed.
         * <p>
         * Note that this naming policy requires that the server provides the
         * resource name when creating new resources, which means it must not be
         * specified in the create request, nor included in the resource
         * content.
         *
         * @param dnAttribute
         *            The attribute which will be used for naming LDAP entries.
         * @return A reference to this LDAP resource collection builder.
         */
        public Builder useServerEntryUUIDNaming(final AttributeType dnAttribute) {
            return useServerNaming(dnAttribute, AttributeDescription
                    .create(getEntryUUIDAttributeType()));
        }

        /**
         * Indicates that the JSON resource ID will be derived from the server
         * provided "entryUUID" LDAP attribute. The LDAP entry name will be
         * derived by appending a single RDN to the {@link #baseDN(String) base
         * DN} composed of the {@code dnAttribute} taken from the LDAP entry
         * once attribute mapping has been performed.
         * <p>
         * Note that this naming policy requires that the server provides the
         * resource name when creating new resources, which means it must not be
         * specified in the create request, nor included in the resource
         * content.
         *
         * @param dnAttribute
         *            The attribute which will be used for naming LDAP entries.
         * @return A reference to this LDAP resource collection builder.
         */
        public Builder useServerEntryUUIDNaming(final String dnAttribute) {
            return useServerEntryUUIDNaming(at(dnAttribute));
        }

        /**
         * Indicates that the JSON resource ID must not be provided by the user,
         * and will not be used for naming the associated LDAP entry. Instead
         * the JSON resource ID will be taken from the {@code idAttribute} in
         * the LDAP entry, and the LDAP entry name will be derived by appending
         * a single RDN to the {@link #baseDN(String) base DN} composed of the
         * {@code dnAttribute} taken from the LDAP entry once attribute mapping
         * has been performed.
         * <p>
         * Note that this naming policy requires that the server provides the
         * resource name when creating new resources, which means it must not be
         * specified in the create request, nor included in the resource
         * content.
         *
         * @param dnAttribute
         *            The attribute which will be used for naming LDAP entries.
         * @param idAttribute
         *            The attribute which will be used for JSON resource IDs.
         * @return A reference to this LDAP resource collection builder.
         */
        public Builder useServerNaming(final AttributeType dnAttribute,
                final AttributeDescription idAttribute) {
            this.nameStrategy = new AttributeNameStrategy(dnAttribute, idAttribute, true);
            return this;
        }

        /**
         * Indicates that the JSON resource ID must not be provided by the user,
         * and will not be used for naming the associated LDAP entry. Instead
         * the JSON resource ID will be taken from the {@code idAttribute} in
         * the LDAP entry, and the LDAP entry name will be derived by appending
         * a single RDN to the {@link #baseDN(String) base DN} composed of the
         * {@code dnAttribute} taken from the LDAP entry once attribute mapping
         * has been performed.
         * <p>
         * Note that this naming policy requires that the server provides the
         * resource name when creating new resources, which means it must not be
         * specified in the create request, nor included in the resource
         * content.
         *
         * @param dnAttribute
         *            The attribute which will be used for naming LDAP entries.
         * @param idAttribute
         *            The attribute which will be used for JSON resource IDs.
         * @return A reference to this LDAP resource collection builder.
         */
        public Builder useServerNaming(final String dnAttribute, final String idAttribute) {
            return useServerNaming(at(dnAttribute), ad(idAttribute));
        }

        /**
         * Indicates that all LDAP delete operations should be performed using
         * the LDAP subtree delete control. The default behavior is to not use
         * the subtree delete control.
         *
         * @return A reference to this LDAP resource collection builder.
         */
        public Builder useSubtreeDelete() {
            this.useSubtreeDelete = true;
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
            if (this.dnAttribute.equals(idAttribute)) {
                throw new IllegalArgumentException("DN and ID attributes must be different");
            }
            this.idAttribute = ensureNotNull(idAttribute);
            this.isServerProvided = isServerProvided;
        }

        @Override
        SearchRequest createSearchRequest(final RequestState requestState, final DN baseDN, final String resourceId) {
            return newSearchRequest(baseDN, SearchScope.SINGLE_LEVEL, Filter.equality(idAttribute
                    .toString(), resourceId));
        }

        @Override
        void getLDAPAttributes(final RequestState requestState, final Set<String> ldapAttributes) {
            ldapAttributes.add(idAttribute.toString());
        }

        @Override
        String getResourceId(final RequestState requestState, final Entry entry) {
            return entry.parseAttribute(idAttribute).asString();
        }

        @Override
        void setResourceId(final RequestState requestState, final DN baseDN, final String resourceId,
                final Entry entry) throws ResourceException {
            if (isServerProvided) {
                if (resourceId != null) {
                    throw new BadRequestException("Resources cannot be created with a "
                            + "client provided resource ID");
                }
            } else {
                entry.addAttribute(new LinkedAttribute(idAttribute, ByteString.valueOfUtf8(resourceId)));
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
        SearchRequest createSearchRequest(final RequestState requestState, final DN baseDN, final String resourceId) {
            return newSearchRequest(baseDN.child(rdn(resourceId)), SearchScope.BASE_OBJECT, Filter
                    .objectClassPresent());
        }

        @Override
        void getLDAPAttributes(final RequestState requestState, final Set<String> ldapAttributes) {
            ldapAttributes.add(attribute.toString());
        }

        @Override
        String getResourceId(final RequestState requestState, final Entry entry) {
            return entry.parseAttribute(attribute).asString();
        }

        @Override
        void setResourceId(final RequestState requestState, final DN baseDN, final String resourceId,
                final Entry entry) throws ResourceException {
            if (resourceId != null) {
                entry.setName(baseDN.child(rdn(resourceId)));
                entry.addAttribute(new LinkedAttribute(attribute, ByteString.valueOfUtf8(resourceId)));
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
     * {@code Throwable} is an LDAP {@link LdapException} then an
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
        } catch (final LdapException e) {
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
        return newResourceException(resourceResultCode, t.getMessage(), t);
    }

    /**
     * Returns a builder for incrementally constructing LDAP resource
     * collections.
     *
     * @return An LDAP resource collection builder.
     */
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
     * @param providerClassLoader
     *            The {@link ClassLoader} used to fetch the
     *            {@link org.forgerock.opendj.ldap.spi.TransportProvider}.
     *            This can be useful in OSGI environments.
     * @return A new connection factory using the provided JSON configuration.
     * @throws IllegalArgumentException
     *             If the configuration is invalid.
     */
    public static ConnectionFactory configureConnectionFactory(final JsonValue configuration,
                                                               final String name,
                                                               final ClassLoader providerClassLoader) {
        final JsonValue normalizedConfiguration =
                normalizeConnectionFactory(configuration, name, 0);
        return configureConnectionFactory(normalizedConfiguration, providerClassLoader);
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
        return configureConnectionFactory(configuration, name, null);
    }

    /**
     * Returns an attribute mapper which maps a single JSON attribute to a JSON
     * constant.
     *
     * @param value
     *            The constant JSON value (a Boolean, Number, String, Map, or
     *            List).
     * @return The attribute mapper.
     */
    public static AttributeMapper constant(final Object value) {
        return new JSONConstantAttributeMapper(value);
    }

    /**
     * Returns an attribute mapper which maps JSON objects to LDAP attributes.
     *
     * @return The attribute mapper.
     */
    public static ObjectAttributeMapper object() {
        return new ObjectAttributeMapper();
    }

    /**
     * Returns an attribute mapper which provides a mapping from a JSON value to
     * a single DN valued LDAP attribute.
     *
     * @param attribute
     *            The DN valued LDAP attribute to be mapped.
     * @param baseDN
     *            The search base DN for performing reverse lookups.
     * @param primaryKey
     *            The search primary key LDAP attribute to use for performing
     *            reverse lookups.
     * @param mapper
     *            An attribute mapper which will be used to map LDAP attributes
     *            in the referenced entry.
     * @return The attribute mapper.
     */
    public static ReferenceAttributeMapper reference(final AttributeDescription attribute,
            final DN baseDN, final AttributeDescription primaryKey, final AttributeMapper mapper) {
        return new ReferenceAttributeMapper(attribute, baseDN, primaryKey, mapper);
    }

    /**
     * Returns an attribute mapper which provides a mapping from a JSON value to
     * a single DN valued LDAP attribute.
     *
     * @param attribute
     *            The DN valued LDAP attribute to be mapped.
     * @param baseDN
     *            The search base DN for performing reverse lookups.
     * @param primaryKey
     *            The search primary key LDAP attribute to use for performing
     *            reverse lookups.
     * @param mapper
     *            An attribute mapper which will be used to map LDAP attributes
     *            in the referenced entry.
     * @return The attribute mapper.
     */
    public static ReferenceAttributeMapper reference(final String attribute, final String baseDN,
            final String primaryKey, final AttributeMapper mapper) {
        return reference(AttributeDescription.valueOf(attribute), DN.valueOf(baseDN),
                AttributeDescription.valueOf(primaryKey), mapper);
    }

    /**
     * Returns an attribute mapper which provides a simple mapping from a JSON
     * value to a single LDAP attribute.
     *
     * @param attribute
     *            The LDAP attribute to be mapped.
     * @return The attribute mapper.
     */
    public static SimpleAttributeMapper simple(final AttributeDescription attribute) {
        return new SimpleAttributeMapper(attribute);
    }

    /**
     * Returns an attribute mapper which provides a simple mapping from a JSON
     * value to a single LDAP attribute.
     *
     * @param attribute
     *            The LDAP attribute to be mapped.
     * @return The attribute mapper.
     */
    public static SimpleAttributeMapper simple(final String attribute) {
        return simple(AttributeDescription.valueOf(attribute));
    }

    private static ConnectionFactory configureConnectionFactory(final JsonValue configuration,
                                                                final ClassLoader providerClassLoader) {
        final long heartBeatIntervalSeconds = configuration.get("heartBeatIntervalSeconds").defaultTo(30L).asLong();
        final Duration heartBeatInterval = new Duration(Math.max(heartBeatIntervalSeconds, 1L), TimeUnit.SECONDS);

        final long heartBeatTimeoutMillis = configuration.get("heartBeatTimeoutMilliSeconds").defaultTo(500L).asLong();
        final Duration heartBeatTimeout = new Duration(Math.max(heartBeatTimeoutMillis, 100L), TimeUnit.MILLISECONDS);

        final Options options = Options.defaultOptions()
                                       .set(TRANSPORT_PROVIDER_CLASS_LOADER, providerClassLoader)
                                       .set(HEARTBEAT_ENABLED, true)
                                       .set(HEARTBEAT_INTERVAL, heartBeatInterval)
                                       .set(HEARTBEAT_TIMEOUT, heartBeatTimeout)
                                       .set(LOAD_BALANCER_MONITORING_INTERVAL, heartBeatInterval);

        // Parse pool parameters,
        final int connectionPoolSize =
                Math.max(configuration.get("connectionPoolSize").defaultTo(10).asInteger(), 1);

        // Parse authentication parameters.
        if (configuration.isDefined("authentication")) {
            final JsonValue authn = configuration.get("authentication");
            if (authn.isDefined("simple")) {
                final JsonValue simple = authn.get("simple");
                final BindRequest bindRequest =
                        Requests.newSimpleBindRequest(simple.get("bindDN").required().asString(),
                                simple.get("bindPassword").required().asString().toCharArray());
                options.set(AUTHN_BIND_REQUEST, bindRequest);
            } else {
                throw new IllegalArgumentException("Only simple authentication is supported");
            }
        }

        // Parse SSL/StartTLS parameters.
        final ConnectionSecurity connectionSecurity =
                configuration.get("connectionSecurity").defaultTo(ConnectionSecurity.NONE).asEnum(
                        ConnectionSecurity.class);
        if (connectionSecurity != ConnectionSecurity.NONE) {
            try {
                // Configure SSL.
                final SSLContextBuilder builder = new SSLContextBuilder();

                // Parse trust store configuration.
                final TrustManagerType trustManagerType =
                        configuration.get("trustManager").defaultTo(TrustManagerType.TRUSTALL)
                                .asEnum(TrustManagerType.class);
                switch (trustManagerType) {
                case TRUSTALL:
                    builder.setTrustManager(TrustManagers.trustAll());
                    break;
                case JVM:
                    // Do nothing: JVM trust manager is the default.
                    break;
                case FILE:
                    final String fileName =
                            configuration.get("fileBasedTrustManagerFile").required().asString();
                    final String password =
                            configuration.get("fileBasedTrustManagerPassword").asString();
                    final String type = configuration.get("fileBasedTrustManagerType").asString();
                    builder.setTrustManager(TrustManagers.checkUsingTrustStore(fileName,
                            password != null ? password.toCharArray() : null, type));
                    break;
                }
                options.set(SSL_CONTEXT, builder.getSSLContext());
                options.set(SSL_USE_STARTTLS,
                            connectionSecurity == ConnectionSecurity.STARTTLS);
            } catch (GeneralSecurityException | IOException e) {
                // Rethrow as unchecked exception.
                throw new IllegalArgumentException(e);
            }
        }

        // Parse primary data center.
        final JsonValue primaryLDAPServers = configuration.get("primaryLDAPServers");
        if (!primaryLDAPServers.isList() || primaryLDAPServers.size() == 0) {
            throw new IllegalArgumentException("No primaryLDAPServers");
        }
        final ConnectionFactory primary = parseLDAPServers(primaryLDAPServers, connectionPoolSize, options);

        // Parse secondary data center(s).
        final JsonValue secondaryLDAPServers = configuration.get("secondaryLDAPServers");
        ConnectionFactory secondary = null;
        if (secondaryLDAPServers.isList()) {
            if (secondaryLDAPServers.size() > 0) {
                secondary = parseLDAPServers(secondaryLDAPServers, connectionPoolSize, options);
            }
        } else if (!secondaryLDAPServers.isNull()) {
            throw new IllegalArgumentException("Invalid secondaryLDAPServers configuration");
        }

        // Create fail-over.
        if (secondary != null) {
            return newFailoverLoadBalancer(asList(primary, secondary), options);
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
            final Map<String, Object> normalized = new LinkedHashMap<>(parent.asMap());
            normalized.putAll(current.asMap());
            normalized.remove("inheritFrom");
            return new JsonValue(normalized);
        } else {
            // No normalization required.
            return current;
        }
    }

    private static ConnectionFactory parseLDAPServers(JsonValue config, int poolSize, Options options) {
        final List<ConnectionFactory> servers = new ArrayList<>(config.size());
        for (final JsonValue server : config) {
            final String host = server.get("hostname").required().asString();
            final int port = server.get("port").required().asInteger();
            final ConnectionFactory factory = new LDAPConnectionFactory(host, port, options);
            if (poolSize > 1) {
                servers.add(newCachedConnectionPool(factory, 0, poolSize, 60L, TimeUnit.SECONDS));
            } else {
                servers.add(factory);
            }
        }
        if (servers.size() > 1) {
            return newRoundRobinLoadBalancer(servers, options);
        } else {
            return servers.get(0);
        }
    }

    private Rest2LDAP() {
        // Prevent instantiation.
    }
}
