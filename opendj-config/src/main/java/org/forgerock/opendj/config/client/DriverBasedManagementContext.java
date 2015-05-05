/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.forgerock.opendj.config.client;

import java.util.Set;
import java.util.SortedSet;

import org.forgerock.opendj.config.AbstractManagedObjectDefinition;
import org.forgerock.opendj.config.Configuration;
import org.forgerock.opendj.config.ConfigurationClient;
import org.forgerock.opendj.config.DefinitionDecodingException;
import org.forgerock.opendj.config.InstantiableRelationDefinition;
import org.forgerock.opendj.config.ManagedObjectNotFoundException;
import org.forgerock.opendj.config.ManagedObjectPath;
import org.forgerock.opendj.config.OptionalRelationDefinition;
import org.forgerock.opendj.config.PropertyDefinition;
import org.forgerock.opendj.config.SetRelationDefinition;
import org.forgerock.opendj.config.client.spi.Driver;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.server.config.client.RootCfgClient;

/**
 * Driver based client management connection context.
 */
public abstract class DriverBasedManagementContext implements ManagementContext {

    /**
     * Creates a new management context.
     */
    protected DriverBasedManagementContext() {
        // No implementation required.
    }

    @Override
    public final <C extends ConfigurationClient, S extends Configuration> boolean deleteManagedObject(
            ManagedObjectPath<?, ?> parent, InstantiableRelationDefinition<C, S> rd, String name)
            throws ManagedObjectNotFoundException, OperationRejectedException,
            LdapException {
        return getDriver().deleteManagedObject(parent, rd, name);
    }

    @Override
    public final <C extends ConfigurationClient, S extends Configuration> boolean deleteManagedObject(
            ManagedObjectPath<?, ?> parent, OptionalRelationDefinition<C, S> rd) throws
            ManagedObjectNotFoundException, OperationRejectedException, LdapException {
        return getDriver().deleteManagedObject(parent, rd);
    }

    @Override
    public final <C extends ConfigurationClient, S extends Configuration> boolean deleteManagedObject(
            ManagedObjectPath<?, ?> parent, SetRelationDefinition<C, S> rd, String name)
            throws ManagedObjectNotFoundException, OperationRejectedException, LdapException {
        return getDriver().deleteManagedObject(parent, rd, name);
    }

    @Override
    @SuppressWarnings("unchecked")
    public final <C extends ConfigurationClient, S extends Configuration> ManagedObject<? extends C> getManagedObject(
            ManagedObjectPath<C, S> path) throws DefinitionDecodingException, ManagedObjectDecodingException,
            ManagedObjectNotFoundException, LdapException {
        // Be careful to handle the root configuration.
        if (path.isEmpty()) {
            return (ManagedObject<C>) getRootConfigurationManagedObject();
        }

        return getDriver().getManagedObject(path);
    }

    @Override
    public final <P> P getPropertyValue(ManagedObjectPath<?, ?> path, PropertyDefinition<P> pd)
            throws DefinitionDecodingException, LdapException, ManagedObjectNotFoundException {
        Set<P> values = getPropertyValues(path, pd);
        if (values.isEmpty()) {
            return null;
        } else {
            return values.iterator().next();
        }
    }

    @Override
    public final <P> SortedSet<P> getPropertyValues(ManagedObjectPath<?, ?> path, PropertyDefinition<P> pd)
            throws DefinitionDecodingException, LdapException, ManagedObjectNotFoundException {
        return getDriver().getPropertyValues(path, pd);
    }

    @Override
    public final RootCfgClient getRootConfiguration() {
        return getRootConfigurationManagedObject().getConfiguration();
    }

    @Override
    public final ManagedObject<RootCfgClient> getRootConfigurationManagedObject() {
        return getDriver().getRootConfigurationManagedObject();
    }

    @Override
    public final <C extends ConfigurationClient, S extends Configuration> String[] listManagedObjects(
            ManagedObjectPath<?, ?> parent, InstantiableRelationDefinition<C, S> rd) throws
            ManagedObjectNotFoundException, LdapException {
        return listManagedObjects(parent, rd, rd.getChildDefinition());
    }

    @Override
    public final <C extends ConfigurationClient, S extends Configuration> String[] listManagedObjects(
            ManagedObjectPath<?, ?> parent, InstantiableRelationDefinition<C, S> rd,
            AbstractManagedObjectDefinition<? extends C, ? extends S> d) throws
            ManagedObjectNotFoundException, LdapException {
        return getDriver().listManagedObjects(parent, rd, d);
    }

    @Override
    public final <C extends ConfigurationClient, S extends Configuration> String[] listManagedObjects(
            ManagedObjectPath<?, ?> parent, SetRelationDefinition<C, S> rd) throws
            ManagedObjectNotFoundException, LdapException {
        return getDriver().listManagedObjects(parent, rd, rd.getChildDefinition());
    }

    @Override
    public final boolean managedObjectExists(ManagedObjectPath<?, ?> path) throws ManagedObjectNotFoundException,
            LdapException {
        return getDriver().managedObjectExists(path);
    }

    /**
     * Gets the driver associated with this management context.
     *
     * @return Returns the driver associated with this management context.
     */
    protected abstract Driver getDriver();

    @Override
    public final void close() {
        getDriver().close();
    }

}
