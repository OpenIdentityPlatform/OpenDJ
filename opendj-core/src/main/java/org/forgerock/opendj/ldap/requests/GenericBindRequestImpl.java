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
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2014 ForgeRock AS
 */

package org.forgerock.opendj.ldap.requests;

import static com.forgerock.opendj.util.StaticUtils.copyOfBytes;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.LdapException;

import org.forgerock.util.Reject;

/**
 * Generic bind request implementation.
 */
final class GenericBindRequestImpl extends AbstractBindRequest<GenericBindRequest> implements
        GenericBindRequest {
    private byte authenticationType;

    private byte[] authenticationValue;

    private final BindClient bindClient;

    private String name;

    GenericBindRequestImpl(final GenericBindRequest genericBindRequest) {
        super(genericBindRequest);
        this.name = genericBindRequest.getName();
        this.authenticationType = genericBindRequest.getAuthenticationType();
        this.authenticationValue = copyOfBytes(genericBindRequest.getAuthenticationValue());
        this.bindClient = null; // Create a new bind client each time.
    }

    GenericBindRequestImpl(final String name, final byte authenticationType,
            final byte[] authenticationValue) {
        this.name = name;
        this.authenticationType = authenticationType;
        this.authenticationValue = authenticationValue;
        this.bindClient = null; // Create a new bind client each time.
    }

    GenericBindRequestImpl(final String name, final byte authenticationType,
            final byte[] authenticationValue, final BindClient bindClient) {
        this.name = name;
        this.authenticationType = authenticationType;
        this.authenticationValue = authenticationValue;
        this.bindClient = bindClient; // Always return same bind client.
    }

    @Override
    public BindClient createBindClient(final String serverName) throws LdapException {
        if (bindClient != null) {
            return bindClient;
        }
        return new BindClientImpl(this).setNextAuthenticationValue(authenticationValue);
    }

    @Override
    public byte getAuthenticationType() {
        return authenticationType;
    }

    @Override
    public byte[] getAuthenticationValue() {
        return authenticationValue;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public GenericBindRequest setAuthenticationType(final byte type) {
        this.authenticationType = type;
        return this;
    }

    @Override
    public GenericBindRequest setAuthenticationValue(final byte[] bytes) {
        Reject.ifNull(bytes);
        this.authenticationValue = bytes;
        return this;
    }

    @Override
    public GenericBindRequest setName(final String name) {
        Reject.ifNull(name);
        this.name = name;
        return this;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("GenericBindRequest(name=");
        builder.append(getName());
        builder.append(", authenticationType=");
        builder.append(getAuthenticationType());
        builder.append(", authenticationValue=");
        builder.append(ByteString.wrap(getAuthenticationValue()));
        builder.append(", controls=");
        builder.append(getControls());
        builder.append(")");
        return builder.toString();
    }
}
