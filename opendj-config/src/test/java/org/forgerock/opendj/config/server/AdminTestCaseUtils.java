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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2015 ForgeRock AS.
 */
package org.forgerock.opendj.config.server;

import org.forgerock.opendj.config.AbstractManagedObjectDefinition;
import org.forgerock.opendj.config.Configuration;
import org.forgerock.opendj.config.ConfigurationClient;
import org.forgerock.opendj.config.DefinitionDecodingException;
import org.forgerock.opendj.config.LDAPProfile;
import org.forgerock.opendj.config.ManagedObjectPath;
import org.forgerock.opendj.config.RelationDefinition;
import org.forgerock.opendj.config.SingletonRelationDefinition;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.server.config.meta.RootCfgDefn;

/**
 * This class defines some utility functions which can be used by test cases
 * which interact with the admin framework.
 */
public final class AdminTestCaseUtils {

    /**
     * The relation name which will be used for dummy configurations. A
     * deliberately obfuscated name is chosen to avoid clashes.
     */
    private static final String DUMMY_TEST_RELATION = "*dummy*test*relation*";

    /** Indicates if the dummy relation profile has been registered. */
    private static boolean isProfileRegistered;

    /** Prevent instantiation. */
    private AdminTestCaseUtils() {
        // No implementation required.
    }

    /**
     * Decodes a configuration entry into the required type of server
     * configuration.
     *
     * @param <S>
     *            The type of server configuration to be decoded.
     * @param context The server management context.
     * @param definition
     *            The required definition of the required managed object.
     * @param entry
     *            An entry containing the configuration to be decoded.
     * @return Returns the new server-side configuration.
     * @throws ConfigException
     *             If the entry could not be decoded.
     */
    public static <S extends Configuration> S getConfiguration(ServerManagementContext context,
            AbstractManagedObjectDefinition<?, S> definition, Entry entry) throws ConfigException {
        try {
            ServerManagedObject<? extends S> managedObject = context.decode(getPath(definition), entry);

            // Ensure constraints are satisfied.
            managedObject.ensureIsUsable();

            return managedObject.getConfiguration();
        } catch (DefinitionDecodingException e) {
            throw ConfigExceptionFactory.getInstance().createDecodingExceptionAdaptor(entry.getName(), e);
        } catch (ServerManagedObjectDecodingException e) {
            throw ConfigExceptionFactory.getInstance().createDecodingExceptionAdaptor(e);
        } catch (ConstraintViolationException e) {
            throw ConfigExceptionFactory.getInstance().createDecodingExceptionAdaptor(e);
        }
    }

    /** Construct a dummy path. */
    // @Checkstyle:off
    private static synchronized <C extends ConfigurationClient, S extends Configuration> ManagedObjectPath<C, S>
        getPath(AbstractManagedObjectDefinition<C, S> d) {
    // @Checkstyle:on
        if (!isProfileRegistered) {
            LDAPProfile.Wrapper profile = new LDAPProfile.Wrapper() {
                @Override
                public String getRelationRDNSequence(RelationDefinition<?, ?> r) {
                    if (DUMMY_TEST_RELATION.equals(r.getName())) {
                        return "cn=dummy configuration,cn=config";
                    }
                    return null;
                }

            };
            LDAPProfile.getInstance().pushWrapper(profile);
            isProfileRegistered = true;
        }
        SingletonRelationDefinition.Builder<C, S> builder =
            new SingletonRelationDefinition.Builder<>(RootCfgDefn.getInstance(), DUMMY_TEST_RELATION, d);
        ManagedObjectPath<?, ?> root = ManagedObjectPath.emptyPath();
        return root.child(builder.getInstance());
    }
}
