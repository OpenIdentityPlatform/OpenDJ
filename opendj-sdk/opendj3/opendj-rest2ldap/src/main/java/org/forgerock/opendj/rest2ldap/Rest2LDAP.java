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

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.forgerock.json.resource.CollectionResourceProvider;
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
        private DN baseDN; // TODO: support template variables.
        private ConnectionFactory factory;
        private final Filter falseFilter = Filter.present("1.1");
        private final List<AttributeMapper> mappers = new LinkedList<AttributeMapper>();
        private MVCCStrategy mvccStrategy;
        private NameStrategy nameStrategy;
        private ReadOnUpdatePolicy readOnUpdatePolicy = USE_READ_ENTRY_CONTROLS;
        private Schema schema = Schema.getDefaultSchema();
        private Filter trueFilter = Filter.objectClassPresent();

        Builder() {
            useEtagAttribute();
            useServerEntryUUIDNaming("uid");
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
            if (mappers.isEmpty()) {
                throw new IllegalStateException("No mappings provided");
            }
            return new LDAPCollectionResourceProvider(baseDN, mapOf(mappers), factory,
                    nameStrategy, mvccStrategy, new Config(trueFilter, falseFilter,
                            readOnUpdatePolicy, schema));
        }

        public Builder factory(final ConnectionFactory factory) {
            ensureNotNull(factory);
            this.factory = factory;
            return this;
        }

        /**
         * Sets the absolute false filter which should be used when querying the
         * LDAP server.
         *
         * @param filter
         *            The absolute false filter.
         * @return A reference to this builder.
         */
        public Builder falseFilter(final Filter filter) {
            this.trueFilter = ensureNotNull(filter);
            return this;
        }

        public Builder map(final AttributeMapper... mappers) {
            ensureNotNull(mappers);
            this.mappers.addAll(Arrays.asList(mappers));
            return this;
        }

        public Builder map(final Collection<AttributeMapper> mappers) {
            ensureNotNull(mappers);
            this.mappers.addAll(mappers);
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

        /**
         * Sets the absolute true filter which should be used when querying the
         * LDAP server.
         *
         * @param filter
         *            The absolute true filter.
         * @return A reference to this builder.
         */
        public Builder trueFilter(final Filter filter) {
            this.trueFilter = ensureNotNull(filter);
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
                final Entry entry) {
            if (!isServerProvided) {
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
            return newSearchRequest(baseDN.child(rdn(resourceId)), SearchScope.BASE_OBJECT, c
                    .getConfig().trueFilter());
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
                final Entry entry) {
            entry.setName(baseDN.child(rdn(resourceId)));
            entry.addAttribute(new LinkedAttribute(attribute, ByteString.valueOf(resourceId)));
        }

        private RDN rdn(final String resourceId) {
            return new RDN(attribute.getAttributeType(), resourceId);
        }

    }

    public static Builder builder() {
        return new Builder();
    }

    public static SimpleAttributeMapper map(final AttributeDescription attribute) {
        return map(attribute.toString(), attribute);
    }

    public static SimpleAttributeMapper map(final String attribute) {
        return map(attribute, attribute);
    }

    public static SimpleAttributeMapper map(final String jsonAttribute,
            final AttributeDescription ldapAttribute) {
        return new SimpleAttributeMapper(jsonAttribute, ldapAttribute);
    }

    public static SimpleAttributeMapper map(final String jsonAttribute, final String ldapAttribute) {
        return map(jsonAttribute, AttributeDescription.valueOf(ldapAttribute));
    }

    public static AttributeMapper mapAllExcept(final String... attributes) {
        return new DefaultAttributeMapper().excludeAttribute(attributes);
    }

    public static AttributeMapper mapAllOf(final String... attributes) {
        return new DefaultAttributeMapper().includeAttribute(attributes);
    }

    public static AttributeMapper mapComplex(final String jsonAttribute,
            final AttributeMapper... mappers) {
        return mapComplex(jsonAttribute, Arrays.asList(mappers));
    }

    public static AttributeMapper mapComplex(final String jsonAttribute,
            final Collection<AttributeMapper> mappers) {
        return new ComplexAttributeMapper(jsonAttribute, mapOf(mappers));
    }

    public static AttributeMapper mapJSONConstant(final String attribute,
            final Object attributeValue) {
        return new JSONConstantAttributeMapper(attribute, attributeValue);
    }

    public static AttributeMapper mapLDAPConstant(final AttributeDescription attribute,
            final Object attributeValue) {
        return new LDAPConstantAttributeMapper(attribute, attributeValue);
    }

    public static AttributeMapper mapLDAPConstant(final String attribute,
            final Object attributeValue) {
        return mapLDAPConstant(AttributeDescription.valueOf(attribute), attributeValue);
    }

    private static AttributeMapper mapOf(final Collection<AttributeMapper> mappers) {
        return new CompositeAttributeMapper(mappers);
    }

    private Rest2LDAP() {
        // Prevent instantiation.
    }

}
