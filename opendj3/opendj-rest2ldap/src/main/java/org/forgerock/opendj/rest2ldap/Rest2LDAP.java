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
        private Config config = Config.defaultConfig();
        private ConnectionFactory factory;
        private final List<AttributeMapper> mappers = new LinkedList<AttributeMapper>();
        private MVCCStrategy mvccStrategy = mvccUsingEtag();
        private NameStrategy nameStrategy = nameByEntryUUID("uid");

        Builder() {
            // No implementation required.
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
            return new LDAPCollectionResourceProvider(baseDN, mapOf(mappers), factory, config,
                    nameStrategy, mvccStrategy);
        }

        public Builder config(final Config config) {
            ensureNotNull(config);
            this.config = config;
            return this;
        }

        public Builder factory(final ConnectionFactory factory) {
            ensureNotNull(factory);
            this.factory = factory;
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

        public Builder with(final MVCCStrategy strategy) {
            ensureNotNull(strategy);
            this.mvccStrategy = strategy;
            return this;
        }

        public Builder with(final NameStrategy strategy) {
            ensureNotNull(strategy);
            this.nameStrategy = strategy;
            return this;
        }
    }

    private static final class AttributeMVCCStrategy extends MVCCStrategy {
        private final AttributeDescription ldapAttribute;

        private AttributeMVCCStrategy(final AttributeDescription ldapAttribute) {
            this.ldapAttribute = ldapAttribute;
        }

        @Override
        String getRevisionFromEntry(final Context c, final Entry entry) {
            return entry.parseAttribute(ldapAttribute).asString();
        }

        @Override
        void getLDAPAttributes(final Context c, final Set<String> ldapAttributes) {
            ldapAttributes.add(ldapAttribute.toString());
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

    public static AttributeMapper mapConstant(final String attribute, final Object attributeValue) {
        return new ConstantAttributeMapper(attribute, attributeValue);
    }

    public static MVCCStrategy mvccUsingAttribute(final AttributeDescription attribute) {
        return new AttributeMVCCStrategy(attribute);
    }

    public static MVCCStrategy mvccUsingAttribute(final String attribute) {
        return mvccUsingAttribute(AttributeDescription.valueOf(attribute));
    }

    public static MVCCStrategy mvccUsingEtag() {
        return mvccUsingAttribute("etag");
    }

    public static NameStrategy nameByClient(final AttributeType dnAttribute,
            final AttributeDescription idAttribute) {
        return new AttributeNameStrategy(dnAttribute, idAttribute, false);
    }

    public static NameStrategy nameByClient(final String dnAttribute, final String idAttribute) {
        return nameByClient(Schema.getDefaultSchema().getAttributeType(dnAttribute),
                AttributeDescription.valueOf(idAttribute));
    }

    public static NameStrategy nameByDN(final AttributeType attribute) {
        return new DNNameStrategy(attribute);
    }

    public static NameStrategy nameByDN(final String attribute) {
        return nameByDN(Schema.getDefaultSchema().getAttributeType(attribute));
    }

    public static NameStrategy nameByEntryUUID(final AttributeType dnAttribute) {
        return nameByServer(dnAttribute, AttributeDescription.create(getEntryUUIDAttributeType()));
    }

    public static NameStrategy nameByEntryUUID(final String dnAttribute) {
        return nameByEntryUUID(Schema.getDefaultSchema().getAttributeType(dnAttribute));
    }

    public static NameStrategy nameByServer(final AttributeType dnAttribute,
            final AttributeDescription idAttribute) {
        return new AttributeNameStrategy(dnAttribute, idAttribute, true);
    }

    public static NameStrategy nameByServer(final String dnAttribute, final String idAttribute) {
        return nameByServer(Schema.getDefaultSchema().getAttributeType(dnAttribute),
                AttributeDescription.valueOf(idAttribute));
    }

    public static Builder collection() {
        return new Builder();
    }

    private static AttributeMapper mapOf(final Collection<AttributeMapper> mappers) {
        return new CompositeAttributeMapper(mappers);
    }

    private Rest2LDAP() {
        // Prevent instantiation.
    }

}
