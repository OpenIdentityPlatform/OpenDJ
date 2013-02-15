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
import static org.forgerock.opendj.rest2ldap.ReadOnUpdatePolicy.USE_READ_ENTRY_CONTROLS;
import static org.forgerock.opendj.rest2ldap.Utils.ensureNotNull;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.resource.BadRequestException;
import org.forgerock.json.resource.CollectionResourceProvider;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.LinkedAttribute;
import org.forgerock.opendj.ldap.RDN;
import org.forgerock.opendj.ldap.SearchScope;
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
        private ReadOnUpdatePolicy readOnUpdatePolicy = USE_READ_ENTRY_CONTROLS;
        private final ObjectAttributeMapper rootMapper = new ObjectAttributeMapper();
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
            additionalLDAPAttributes.add(new LinkedAttribute(attribute, values));
            return this;
        }

        /**
         * Creates a mapping for the named JSON attribute.
         *
         * @param name
         *            The name of the JSON attribute to be mapped.
         * @param mapper
         *            The attribute mapper responsible for mapping the JSON
         *            attribute to LDAP attribute(s).
         * @return A reference to this builder.
         */
        public Builder attribute(final String name, final AttributeMapper mapper) {
            rootMapper.attribute(name, mapper);
            return this;
        }

        public Builder baseDN(final DN dn) {
            ensureNotNull(dn);
            this.baseDN = dn;
            return this;
        }

        public Builder baseDN(final String dn) {
            ensureNotNull(dn);
            this.baseDN = DN.valueOf(dn);
            return this;
        }

        public CollectionResourceProvider build() {
            ensureNotNull(factory);
            ensureNotNull(baseDN);
            if (rootMapper.isEmpty()) {
                throw new IllegalStateException("No mappings provided");
            }
            return new LDAPCollectionResourceProvider(baseDN, rootMapper, factory, nameStrategy,
                    mvccStrategy, new Config(readOnUpdatePolicy, schema), additionalLDAPAttributes);
        }

        public Builder factory(final ConnectionFactory factory) {
            ensureNotNull(factory);
            this.factory = factory;
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
            return useClientDNNaming(Schema.getDefaultSchema().getAttributeType(attribute));
        }

        public Builder useClientNaming(final AttributeType dnAttribute,
                final AttributeDescription idAttribute) {
            this.nameStrategy = new AttributeNameStrategy(dnAttribute, idAttribute, false);
            return this;
        }

        public Builder useClientNaming(final String dnAttribute, final String idAttribute) {
            return useClientNaming(Schema.getDefaultSchema().getAttributeType(dnAttribute),
                    AttributeDescription.valueOf(idAttribute));
        }

        public Builder useEtagAttribute() {
            return useEtagAttribute("etag");
        }

        public Builder useEtagAttribute(final AttributeDescription attribute) {
            this.mvccStrategy = new AttributeMVCCStrategy(attribute);
            return this;
        }

        public Builder useEtagAttribute(final String attribute) {
            return useEtagAttribute(AttributeDescription.valueOf(attribute));
        }

        public Builder useServerEntryUUIDNaming(final AttributeType dnAttribute) {
            return useServerNaming(dnAttribute, AttributeDescription
                    .create(getEntryUUIDAttributeType()));
        }

        public Builder useServerEntryUUIDNaming(final String dnAttribute) {
            return useServerEntryUUIDNaming(Schema.getDefaultSchema().getAttributeType(dnAttribute));
        }

        public Builder useServerNaming(final AttributeType dnAttribute,
                final AttributeDescription idAttribute) {
            this.nameStrategy = new AttributeNameStrategy(dnAttribute, idAttribute, true);
            return this;
        }

        public Builder useServerNaming(final String dnAttribute, final String idAttribute) {
            return useServerNaming(Schema.getDefaultSchema().getAttributeType(dnAttribute),
                    AttributeDescription.valueOf(idAttribute));
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
     * Creates a new builder from the provided JSON configuration. See the
     * documentation of {@link #valueOf(JsonValue)} for a detailed specification
     * of the JSON configuration.
     *
     * @param configuration
     *            The JSON configuration.
     * @return A new builder from the provided JSON configuration.
     * @throws IllegalArgumentException
     *             If the configuration is invalid.
     */
    public static Builder builder(final JsonValue configuration) throws IllegalArgumentException {
        final Builder builder = builder();

        return builder;
    }

    /**
     * Creates a new REST 2 LDAP resource provider from the provided JSON
     * configuration. The configuration should look like this, excluding the
     * C-like comments:
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
     *     // SSL/TLS configuration (optional and TBD).
     *     "useSSL" : {
     *         // Elect to use StartTLS instead of SSL.
     *         "useStartTLS" : true,
     *         ...
     *     },
     *
     *     // Authentication configuration (mandatory and TBD).
     *     "authentication" : {
     *         ...
     *     },
     *
     *     // The base DN beneath which LDAP entries are to be found.
     *     "baseDN" : "ou=people,dc=example,dc=com",
     *
     *     // The mechanism which should be used for read resources during updates, must be
     *     // one of "disabled", "useReadEntryControls", or "useSearch".
     *     "readOnUpdatePolicy" : "useReadEntryControls",
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
     *     "attributes" : [
     *         "schemas"     : { "constant" : [ "urn:scim:schemas:core:1.0" ] },
     *         "id"          : { "simple"   : { "ldapAttribute" : "uid", "isSingleValued" : true, "isRequired" : true, "writability" : "createOnly" } },
     *         "rev"         : { "simple"   : { "ldapAttribute" : "etag", "isSingleValued" : true, "writability" : "readOnly" } },
     *         "userName"    : { "simple"   : { "ldapAttribute" : "mail", "isSingleValued" : true, "writability" : "readOnly" } },
     *         "displayName" : { "simple"   : { "ldapAttribute" : "cn", "isSingleValued" : true, "isRequired" : true } },
     *         "name"        : { "object"   : [
     *             "givenName"  : { "simple"   : { "ldapAttribute" : "givenName", "isSingleValued" : true } },
     *             "familyName" : { "simple"   : { "ldapAttribute" : "sn", "isSingleValued" : true, "isRequired" : true } },
     *         ],
     *         ...
     *     ]
     * }
     * </pre>
     *
     * @param configuration
     *            The JSON configuration.
     * @return A new REST 2 LDAP resource provider configured using the provided
     *         JSON configuration.
     * @throws IllegalArgumentException
     *             If the configuration is invalid.
     */
    public static CollectionResourceProvider valueOf(final JsonValue configuration)
            throws IllegalArgumentException {
        return builder(configuration).build();
    }

    public static AttributeMapper constant(final Object value) {
        return new JSONConstantAttributeMapper(value);
    }

    public static ObjectAttributeMapper object() {
        return new ObjectAttributeMapper();
    }

    public static SimpleAttributeMapper simple(final AttributeDescription attribute) {
        return new SimpleAttributeMapper(attribute);
    }

    public static SimpleAttributeMapper simple(final String attribute) {
        return simple(AttributeDescription.valueOf(attribute));
    }

    private Rest2LDAP() {
        // Prevent instantiation.
    }

}
