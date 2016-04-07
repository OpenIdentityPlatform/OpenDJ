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
 * Copyright 2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2014 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.requests;

import org.forgerock.opendj.io.LDAP;
import org.forgerock.opendj.ldap.LdapException;

import com.forgerock.opendj.util.StaticUtils;
import org.forgerock.util.Reject;

/**
 * Simple bind request implementation.
 */
final class SimpleBindRequestImpl extends AbstractBindRequest<SimpleBindRequest> implements
        SimpleBindRequest {
    private String name = "".intern();
    private byte[] password = new byte[0];

    SimpleBindRequestImpl(final SimpleBindRequest simpleBindRequest) {
        super(simpleBindRequest);
        this.name = simpleBindRequest.getName();
        this.password = StaticUtils.copyOfBytes(simpleBindRequest.getPassword());
    }

    SimpleBindRequestImpl(final String name, final byte[] password) {
        this.name = name;
        this.password = password;
    }

    @Override
    public BindClient createBindClient(final String serverName) throws LdapException {
        return new BindClientImpl(this).setNextAuthenticationValue(password);
    }

    @Override
    public byte getAuthenticationType() {
        return LDAP.TYPE_AUTHENTICATION_SIMPLE;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public byte[] getPassword() {
        return password;
    }

    @Override
    public SimpleBindRequest setName(final String name) {
        Reject.ifNull(name);
        this.name = name;
        return this;
    }

    @Override
    public SimpleBindRequest setPassword(final byte[] password) {
        Reject.ifNull(password);
        this.password = password;
        return this;
    }

    @Override
    public SimpleBindRequest setPassword(final char[] password) {
        Reject.ifNull(password);
        this.password = StaticUtils.getBytes(password);
        return this;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("SimpleBindRequest(name=");
        builder.append(getName());
        builder.append(", authentication=simple");
        builder.append(", controls=");
        builder.append(getControls());
        builder.append(")");
        return builder.toString();
    }
}
