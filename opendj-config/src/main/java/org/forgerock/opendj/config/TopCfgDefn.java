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
 * Portions Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.config;

/**
 * Configuration definition <code>TopCfgDefn</code> is the root of the
 * configuration definition hierarchy. Every configuration has
 * <code>TopCfgDefn</code> as a superclass.
 * <p>
 * The <code>TopCfgDefn</code> has no properties or relations. However, it can
 * be used to determine all the configuration definitions currently available to
 * the administration framework using the {@link #getAllChildren()}.
 * <p>
 * <b>NOTE:</b> it is not possible to retrieve I18N related information or
 * profile information for this managed object definition. In particular, calls
 * to the methods {@link #getSynopsis()}, {@link #getDescription()},
 * {@link #getUserFriendlyName()}, and {@link #getUserFriendlyPluralName()} will
 * not work.
 */
public final class TopCfgDefn extends AbstractManagedObjectDefinition<ConfigurationClient, Configuration> {

    /** The singleton configuration definition instance. */
    private static final TopCfgDefn INSTANCE = new TopCfgDefn();

    /**
     * Get the Top configuration definition singleton.
     *
     * @return Returns the Top configuration definition singleton.
     */
    public static TopCfgDefn getInstance() {
        return INSTANCE;
    }

    /** Private constructor. */
    private TopCfgDefn() {
        super("top", null);
    }

}
