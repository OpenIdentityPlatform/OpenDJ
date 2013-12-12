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
 *      Copyright 2013 ForgeRock AS.
 */
package org.opends.server.core;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.forgerock.opendj.ldap.schema.Schema;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;

/**
 * TODO : this is a stub, with default or no implementation
 */
public class DirectoryServer {

    public static AttributeType getAttributeType(String name, boolean b) {
        return Schema.getDefaultSchema().getAttributeType(name);
    }

    public static String getInstanceRoot() {
        throw new RuntimeException("Not implemented");
    }

    public static String getServerRoot() {
        throw new RuntimeException("Not implemented");
    }

    public static ObjectClass getObjectClass(String name) {
        return Schema.getDefaultSchema().getObjectClass(name);
    }

    public static ObjectClass getDefaultObjectClass(String name) {
        return getObjectClass(name);
    }

    public static ConfigEntry getConfigEntry(DN dn) throws ConfigException {
        throw new RuntimeException("Not implemented");
    }

}
