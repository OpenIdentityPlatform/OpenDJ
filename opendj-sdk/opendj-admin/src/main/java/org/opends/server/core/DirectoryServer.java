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
import org.forgerock.opendj.ldap.schema.UnknownSchemaElementException;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;

/**
 * TODO : this is a stub, with some default implementations for some methods,
 * and no implementation for others.
 */
public class DirectoryServer {

    /**
     * Retrieves the attribute type for the provided lowercase name or OID. It
     * can optionally return a generated "default" version if the requested
     * attribute type is not defined in the schema.
     *
     * @param lowerName
     *            The lowercase name or OID for the attribute type to retrieve.
     * @param returnDefault
     *            Indicates whether to generate a default version if the
     *            requested attribute type is not defined in the server schema.
     * @return The requested attribute type, or <CODE>null</CODE> if there is no
     *         attribute with the specified type defined in the server schema
     *         and a default type should not be returned.
     */
    public static AttributeType getAttributeType(String lowerName, boolean returnDefault) {
        if (returnDefault) {
            return getAttributeType(lowerName);
        } else {
            try {
                return Schema.getDefaultSchema().asStrictSchema().getAttributeType(lowerName);
            } catch (UnknownSchemaElementException e) {
                return null;
            }
        }
    }

    /**
     * Retrieves the attribute type for the provided lowercase name or OID.
     *
     * @param lowerName
     *            The lowercase attribute name or OID for the attribute type to
     *            retrieve.
     * @return The requested attribute type, or <CODE>null</CODE> if there is no
     *         attribute with the specified type defined in the server schema.
     */
    public static AttributeType getAttributeType(String lowerName) {
        return Schema.getDefaultSchema().getAttributeType(lowerName);
    }

    public static String getInstanceRoot() {
        throw new RuntimeException("Not implemented");
    }

    public static String getServerRoot() {
        throw new RuntimeException("Not implemented");
    }

    /**
     * Retrieves the objectclass for the provided lowercase name or OID.
     *
     * @param lowerName
     *            The lowercase name or OID for the objectclass to retrieve.
     * @return The requested objectclass, or <CODE>null</CODE> if there is no
     *         such objectclass defined in the server schema.
     */
    public static ObjectClass getObjectClass(String lowerName) {
        try {
            return Schema.getDefaultSchema().getObjectClass(lowerName);
        } catch (UnknownSchemaElementException e) {
            return null;
        }
    }

    /**
     * Causes the Directory Server to construct a new objectclass definition
     * with the provided name and with no required or allowed attributes. This
     * should only be used if there is no objectclass for the specified name. It
     * will not register the created objectclass with the Directory Server.
     *
     * @param name
     *            The name to use for the objectclass, as provided by the user.
     * @return The constructed objectclass definition.
     */
    public static ObjectClass getDefaultObjectClass(String name) {
        return getObjectClass(name);
    }

    public static ConfigEntry getConfigEntry(DN dn) throws ConfigException {
        throw new RuntimeException("Not implemented");
    }

}
