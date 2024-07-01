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
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions copyright 2014-2016 ForgeRock AS.
 */

package org.forgerock.opendj.config.client.ldap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.forgerock.opendj.config.AbstractManagedObjectDefinition;
import org.forgerock.opendj.config.Configuration;
import org.forgerock.opendj.config.ConfigurationClient;
import org.forgerock.opendj.config.InstantiableRelationDefinition;
import org.forgerock.opendj.config.LDAPProfile;
import org.forgerock.opendj.config.ManagedObjectPath;
import org.forgerock.opendj.config.ManagedObjectPathSerializer;
import org.forgerock.opendj.config.OptionalRelationDefinition;
import org.forgerock.opendj.config.RelationDefinition;
import org.forgerock.opendj.config.SetRelationDefinition;
import org.forgerock.opendj.config.SingletonRelationDefinition;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.RDN;

/** A strategy for creating <code>DN</code>s from managed object paths. */
final class DNBuilder implements ManagedObjectPathSerializer {

    /**
     * Creates a new DN representing the specified managed object path.
     *
     * @param path
     *            The managed object path.
     * @param profile
     *            The LDAP profile which should be used to construct LDAP names.
     * @return Returns a new DN representing the specified managed object path.
     */
    static DN create(ManagedObjectPath<?, ?> path, LDAPProfile profile) {
        DNBuilder builder = new DNBuilder(profile);
        path.serialize(builder);
        return builder.build();
    }

    /**
     * Creates a new DN representing the specified managed object path and
     * instantiable relation.
     *
     * @param path
     *            The managed object path.
     * @param relation
     *            The child instantiable relation.
     * @param profile
     *            The LDAP profile which should be used to construct LDAP names.
     * @return Returns a new DN representing the specified managed object path
     *         and instantiable relation.
     */
    static DN create(ManagedObjectPath<?, ?> path,
            InstantiableRelationDefinition<?, ?> relation, LDAPProfile profile) {
        DNBuilder builder = new DNBuilder(profile);
        path.serialize(builder);
        builder.appendManagedObjectPathElement(relation);
        return builder.build();
    }

    /**
     * Creates a new DN representing the specified managed object path and set
     * relation.
     *
     * @param path
     *            The managed object path.
     * @param relation
     *            The child set relation.
     * @param profile
     *            The LDAP profile which should be used to construct LDAP names.
     * @return Returns a new DN representing the specified managed object path
     *         and set relation.
     */
    static DN create(ManagedObjectPath<?, ?> path, SetRelationDefinition<?, ?> relation,
            LDAPProfile profile) {
        DNBuilder builder = new DNBuilder(profile);
        path.serialize(builder);
        builder.appendManagedObjectPathElement(relation);
        return builder.build();
    }

    /** The list of RDNs in big-endian order. */
    private final LinkedList<RDN> rdns = new LinkedList<>();

    private final LDAPProfile profile;

    /**
     * Create a new DN builder.
     *
     * @param profile
     *            The LDAP profile which should be used to construct DNs.
     */
    private DNBuilder(LDAPProfile profile) {
        this.profile = profile;
    }

    @Override
    public <C extends ConfigurationClient, S extends Configuration> void appendManagedObjectPathElement(
            InstantiableRelationDefinition<? super C, ? super S> r,
            AbstractManagedObjectDefinition<C, S> d, String name) {
        // Add the RDN sequence representing the relation.
        appendManagedObjectPathElement(r);

        // Now add the single RDN representing the named instance.
        String type = profile.getRelationChildRDNType(r);
        RDN rdn = new RDN(type, name.trim());
        rdns.add(rdn);

    }

    /**
     * Appends the RDN sequence representing the provided relation.
     *
     * @param r
     *            The relation definition.
     */
    private void appendManagedObjectPathElement(RelationDefinition<?, ?> r) {
        // Add the RDN sequence representing the relation.
        DN dn = DN.valueOf(profile.getRelationRDNSequence(r));
        List<RDN> rdnsOfDn = getRdnsInBigEndianOrder(dn);
        rdns.addAll(rdnsOfDn);
    }

    /**
     * Returns list of RDNs of provided DN in big-endian order.
     *
     * @param dn
     *            The DN to decompose in RDNs.
     * @return rdns in big endian order
     */
    private List<RDN> getRdnsInBigEndianOrder(DN dn) {
        List<RDN> rdnsOfDn = new ArrayList<>();
        for (RDN rdn : dn) {
            rdnsOfDn.add(rdn);
        }
        Collections.reverse(rdnsOfDn);
        return rdnsOfDn;
    }

    @Override
    public <C extends ConfigurationClient, S extends Configuration> void appendManagedObjectPathElement(
            OptionalRelationDefinition<? super C, ? super S> r,
            AbstractManagedObjectDefinition<C, S> d) {
        // Add the RDN sequence representing the relation.
        appendManagedObjectPathElement(r);
    }

    @Override
    public <C extends ConfigurationClient, S extends Configuration> void appendManagedObjectPathElement(
            SingletonRelationDefinition<? super C, ? super S> r,
            AbstractManagedObjectDefinition<C, S> d) {
        // Add the RDN sequence representing the relation.
        appendManagedObjectPathElement(r);
    }

    @Override
    public <C extends ConfigurationClient, S extends Configuration> void appendManagedObjectPathElement(
            SetRelationDefinition<? super C, ? super S> r, AbstractManagedObjectDefinition<C, S> d) {
        // Add the RDN sequence representing the relation.
        appendManagedObjectPathElement(r);

        // Now add the single RDN representing the named instance.
        String type = profile.getRelationChildRDNType(r);
        RDN rdn = new RDN(type, d.getName());
        rdns.add(rdn);
    }

    /**
     * Create a new DN using the current state of this builder.
     *
     * @return Returns the new DN instance.
     */
    private DN build() {
        DN dn = DN.rootDN();
        for (RDN rdn : rdns) {
            dn = dn.child(rdn);
        }
        return dn;
    }
}
