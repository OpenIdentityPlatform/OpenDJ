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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.forgerock.opendj.config.client.ldap;

import org.forgerock.opendj.config.LDAPProfile;
import org.forgerock.opendj.config.PropertyDefinitionsOptions;
import org.forgerock.opendj.config.client.ManagementContext;
import org.forgerock.opendj.config.client.spi.Driver;
import org.forgerock.util.Reject;

/**
 * An LDAP management connection context.
 */
public final class LDAPManagementContext extends ManagementContext {

    /**
     * Create a new LDAP management context using the provided LDAP connection.
     *
     * @param connection
     *            The LDAP connection.
     * @param profile
     *            The LDAP profile.
     * @param options
     *            Options to decode values of property definitions.
     * @return Returns the new management context.
     */
    public static ManagementContext createFromContext(LDAPConnection connection, LDAPProfile profile,
        PropertyDefinitionsOptions options) {
        Reject.ifNull(connection, profile, options);
        LDAPDriver driver = new LDAPDriver(connection, profile, options);
        LDAPManagementContext context = new LDAPManagementContext(driver, options);
        driver.setManagementContext(context);
        return context;
    }

    /** The LDAP management context driver. */
    private final LDAPDriver driver;

    /** Options to validate and decode values of property definitions. */
    private final PropertyDefinitionsOptions options;

    /** Private constructor. */
    private LDAPManagementContext(LDAPDriver driver, PropertyDefinitionsOptions options) {
        this.driver = driver;
        this.options = options;
    }

    /** {@inheritDoc} */
    @Override
    protected Driver getDriver() {
        return driver;
    }

    /**
     * Returns the property definitions options.
     *
     * @return the options to validate and decode values of property
     *         definitions.
     */
    protected PropertyDefinitionsOptions getPropertyDefOptions() {
        return options;
    }
}
