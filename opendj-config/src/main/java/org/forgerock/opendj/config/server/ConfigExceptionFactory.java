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
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.forgerock.opendj.config.server;

import static com.forgerock.opendj.ldap.config.ConfigMessages.*;
import static com.forgerock.opendj.util.StaticUtils.stackTraceToSingleLineString;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.DefinitionDecodingException;
import org.forgerock.opendj.ldap.DN;

/** A utility class for converting admin exceptions to config exceptions. */
final class ConfigExceptionFactory {

    /** The singleton instance. */
    private static final ConfigExceptionFactory INSTANCE = new ConfigExceptionFactory();

    /** Prevent instantiation. */
    private ConfigExceptionFactory() {
        // Do nothing.
    }

    /**
     * Get the configuration exception factory instance.
     *
     * @return Returns the configuration exception factory instance.
     */
    public static ConfigExceptionFactory getInstance() {
        return INSTANCE;
    }

    /**
     * Create a configuration exception from a definition decoding exception.
     *
     * @param dn
     *            The dn of the configuration entry that could not be decoded.
     * @param e
     *            The definition decoding exception
     * @return Returns the configuration exception.
     */
    public ConfigException createDecodingExceptionAdaptor(DN dn, DefinitionDecodingException e) {
        LocalizableMessage message = ERR_ADMIN_MANAGED_OBJECT_DECODING_PROBLEM.get(
                dn, stackTraceToSingleLineString(e, true));
        return new ConfigException(message, e);
    }

    /**
     * Create a configuration exception from a server managed object decoding
     * exception.
     *
     * @param e
     *            The server managed object decoding exception.
     * @return Returns the configuration exception.
     */

    public ConfigException createDecodingExceptionAdaptor(ServerManagedObjectDecodingException e) {
        DN dn = e.getPartialManagedObject().getDN();
        LocalizableMessage message = ERR_ADMIN_MANAGED_OBJECT_DECODING_PROBLEM.get(
                dn, stackTraceToSingleLineString(e, true));
        return new ConfigException(message, e);
    }

    /**
     * Create a configuration exception from a constraints violation decoding
     * exception.
     *
     * @param e
     *            The constraints violation decoding exception.
     * @return Returns the configuration exception.
     */
    public ConfigException createDecodingExceptionAdaptor(ConstraintViolationException e) {
        DN dn = e.getManagedObject().getDN();
        LocalizableMessage message = ERR_ADMIN_MANAGED_OBJECT_DECODING_PROBLEM.get(
                dn, stackTraceToSingleLineString(e, true));
        return new ConfigException(message, e);
    }

    /**
     * Create an exception that describes a problem that occurred when
     * attempting to load and instantiate a class.
     *
     * @param dn
     *            The dn of the configuration entry was being processed.
     * @param className
     *            The name of the class that could not be loaded or
     *            instantiated.
     * @param e
     *            The exception that occurred.
     * @return Returns the configuration exception.
     */

    public ConfigException createClassLoadingExceptionAdaptor(DN dn, String className, Exception e) {
        LocalizableMessage message = ERR_ADMIN_CANNOT_INSTANTIATE_CLASS.get(
                className, dn, stackTraceToSingleLineString(e, true));
        return new ConfigException(message, e);
    }
}
