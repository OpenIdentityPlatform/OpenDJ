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
 *      Portions copyright 2014 ForgeRock AS.
 */

package org.forgerock.opendj.config.client.ldap;

import org.forgerock.opendj.config.LDAPProfile;
import org.forgerock.opendj.config.client.ManagementContext;
import org.forgerock.opendj.config.client.spi.Driver;
import org.forgerock.opendj.ldap.Connection;
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
     * @return Returns the new management context.
     */
    public static ManagementContext newManagementContext(Connection connection, LDAPProfile profile) {
        Reject.ifNull(connection, profile);
        LDAPDriver driver = new LDAPDriver(connection, profile);
        LDAPManagementContext context = new LDAPManagementContext(driver);
        driver.setManagementContext(context);
        return context;
    }

    /** The LDAP management context driver. */
    private final LDAPDriver driver;

    /** Private constructor. */
    private LDAPManagementContext(LDAPDriver driver) {
        this.driver = driver;
    }

    /** {@inheritDoc} */
    @Override
    protected Driver getDriver() {
        return driver;
    }
}
