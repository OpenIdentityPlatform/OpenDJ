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
 * Copyright 2012 ForgeRock AS.
 */
package org.forgerock.opendj.rest2ldap;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.forgerock.json.fluent.JsonPointer;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.resource.ResultHandler;
import org.forgerock.json.resource.ServerContext;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.Entry;

/**
 * An attribute mapper which maps DN valued LDAP attributes to resource IDs.
 */
public class ReferenceAttributeMapper implements AttributeMapper {

    // private final EntryContainer referencedContainer;

    /**
     * {@inheritDoc}
     */
    @Override
    public void getLDAPAttributes(final JsonPointer jsonAttribute, final Set<String> ldapAttributes) {
        // TODO Auto-generated method stub

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void toJSON(final ServerContext c, final Entry e,
            final ResultHandler<Map<String, Object>> h) {
        // TODO Auto-generated method stub

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void toLDAP(final ServerContext c, final JsonValue v,
            final ResultHandler<List<Attribute>> h) {
        // TODO Auto-generated method stub

    }

}
