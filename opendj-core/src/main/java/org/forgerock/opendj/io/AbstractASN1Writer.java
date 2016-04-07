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
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions copyright 2011-2016 ForgeRock AS.
 */
package org.forgerock.opendj.io;

import java.io.IOException;

import org.forgerock.opendj.ldap.ByteSequence;

/**
 * An abstract {@code ASN1Writer} which can be used as the basis for
 * implementing new ASN1 writer implementations.
 */
public abstract class AbstractASN1Writer implements ASN1Writer {

    /** Creates a new abstract ASN.1 writer. */
    protected AbstractASN1Writer() {
        // No implementation required.
    }

    @Override
    public ASN1Writer writeBoolean(final boolean value) throws IOException {
        return writeBoolean(ASN1.UNIVERSAL_BOOLEAN_TYPE, value);
    }

    @Override
    public ASN1Writer writeEnumerated(final int value) throws IOException {
        return writeEnumerated(ASN1.UNIVERSAL_ENUMERATED_TYPE, value);
    }

    @Override
    public ASN1Writer writeInteger(final int value) throws IOException {
        return writeInteger(ASN1.UNIVERSAL_INTEGER_TYPE, value);
    }

    @Override
    public ASN1Writer writeInteger(final long value) throws IOException {
        return writeInteger(ASN1.UNIVERSAL_INTEGER_TYPE, value);
    }

    @Override
    public ASN1Writer writeNull() throws IOException {
        return writeNull(ASN1.UNIVERSAL_NULL_TYPE);
    }

    @Override
    public ASN1Writer writeOctetString(byte type, byte[] value) throws IOException {
        return writeOctetString(type, value, 0, value.length);
    }

    @Override
    public ASN1Writer writeOctetString(byte[] value) throws IOException {
        return writeOctetString(value, 0, value.length);
    }

    @Override
    public ASN1Writer writeOctetString(final byte[] value, final int offset, final int length)
            throws IOException {
        return writeOctetString(ASN1.UNIVERSAL_OCTET_STRING_TYPE, value, offset, length);
    }

    @Override
    public ASN1Writer writeOctetString(final ByteSequence value) throws IOException {
        return writeOctetString(ASN1.UNIVERSAL_OCTET_STRING_TYPE, value);
    }

    @Override
    public ASN1Writer writeOctetString(final String value) throws IOException {
        return writeOctetString(ASN1.UNIVERSAL_OCTET_STRING_TYPE, value);
    }

    @Override
    public ASN1Writer writeStartSequence() throws IOException {
        return writeStartSequence(ASN1.UNIVERSAL_SEQUENCE_TYPE);
    }

    @Override
    public ASN1Writer writeStartSet() throws IOException {
        return writeStartSet(ASN1.UNIVERSAL_SET_TYPE);
    }

}
