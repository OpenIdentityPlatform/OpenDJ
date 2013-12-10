/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */

package org.opends.server.admin.client.ldap;

import java.util.Collections;
import java.util.LinkedList;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.RDN;
import org.opends.server.admin.AbstractManagedObjectDefinition;
import org.opends.server.admin.Configuration;
import org.opends.server.admin.ConfigurationClient;
import org.opends.server.admin.InstantiableRelationDefinition;
import org.opends.server.admin.LDAPProfile;
import org.opends.server.admin.ManagedObjectPath;
import org.opends.server.admin.ManagedObjectPathSerializer;
import org.opends.server.admin.OptionalRelationDefinition;
import org.opends.server.admin.RelationDefinition;
import org.opends.server.admin.SetRelationDefinition;
import org.opends.server.admin.SingletonRelationDefinition;

/**
 * A strategy for creating <code>DN</code>s from managed object paths.
 */
final class LDAPNameBuilder implements ManagedObjectPathSerializer {

    /**
     * Creates a new DN representing the specified managed object path.
     *
     * @param path
     *            The managed object path.
     * @param profile
     *            The LDAP profile which should be used to construct LDAP names.
     * @return Returns a new DN representing the specified managed object path.
     */
    public static DN create(ManagedObjectPath<?, ?> path, LDAPProfile profile) {
        LDAPNameBuilder builder = new LDAPNameBuilder(profile);
        path.serialize(builder);
        return builder.getInstance();
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
    public static DN create(ManagedObjectPath<?, ?> path, InstantiableRelationDefinition<?, ?> relation,
            LDAPProfile profile) {
        LDAPNameBuilder builder = new LDAPNameBuilder(profile);
        path.serialize(builder);
        builder.appendManagedObjectPathElement(relation);
        return builder.getInstance();
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
    public static DN create(ManagedObjectPath<?, ?> path, SetRelationDefinition<?, ?> relation, LDAPProfile profile) {
        LDAPNameBuilder builder = new LDAPNameBuilder(profile);
        path.serialize(builder);
        builder.appendManagedObjectPathElement(relation);
        return builder.getInstance();
    }

    // The list of RDNs in big-endian order.
    private final LinkedList<RDN> rdns;

    // The LDAP profile.
    private final LDAPProfile profile;

    /**
     * Create a new DN builder.
     *
     * @param profile
     *            The LDAP profile which should be used to construct DNs.
     */
    public LDAPNameBuilder(LDAPProfile profile) {
        this.rdns = new LinkedList<RDN>();
        this.profile = profile;
    }

    /**
     * {@inheritDoc}
     */
    public <C extends ConfigurationClient, S extends Configuration> void appendManagedObjectPathElement(
            InstantiableRelationDefinition<? super C, ? super S> r, AbstractManagedObjectDefinition<C, S> d, String name) {
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
    public void appendManagedObjectPathElement(RelationDefinition<?, ?> r) {
        // Add the RDN sequence representing the relation.
        DN dn = DN.valueOf(profile.getRelationRDNSequence(r));
        for (RDN rdn : dn) {
            rdns.add(rdn);
        }
    }

    /**
     * {@inheritDoc}
     */
    public <C extends ConfigurationClient, S extends Configuration> void appendManagedObjectPathElement(
            OptionalRelationDefinition<? super C, ? super S> r, AbstractManagedObjectDefinition<C, S> d) {
        // Add the RDN sequence representing the relation.
        appendManagedObjectPathElement(r);
    }

    /**
     * {@inheritDoc}
     */
    public <C extends ConfigurationClient, S extends Configuration> void appendManagedObjectPathElement(
            SingletonRelationDefinition<? super C, ? super S> r, AbstractManagedObjectDefinition<C, S> d) {
        // Add the RDN sequence representing the relation.
        appendManagedObjectPathElement(r);
    }

    /**
     * {@inheritDoc}
     */
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
    public DN getInstance() {
        if (rdns.isEmpty()) {
            return DN.rootDN();
        }
        else {
            Collections.reverse(rdns);
            DN dn = DN.valueOf(rdns.removeFirst().toString());
            for (RDN rdn : rdns) {
                dn = dn.child(rdn);
            }
            return dn;
        }
    }
}
