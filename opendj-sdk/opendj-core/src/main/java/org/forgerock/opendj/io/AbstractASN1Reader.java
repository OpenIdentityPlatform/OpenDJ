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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions copyright 2012-2016 ForgeRock AS.
 * Portions Copyright 2014 Manuel Gaupp
 */

package org.forgerock.opendj.io;

import static com.forgerock.opendj.ldap.CoreMessages.ERR_ASN1_UNEXPECTED_TAG;

import java.io.IOException;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DecodeException;

/**
 * An abstract {@code ASN1Reader} which can be used as the basis for
 * implementing new ASN1 reader implementations.
 */
public abstract class AbstractASN1Reader implements ASN1Reader {
    /** Creates a new abstract ASN.1 reader. */
    protected AbstractASN1Reader() {
        // No implementation required.
    }

    @Override
    public boolean readBoolean(byte type) throws IOException {
        if (type == 0x00) {
            type = ASN1.UNIVERSAL_BOOLEAN_TYPE;
        }
        checkType(type);
        return readBoolean();
    }

    @Override
    public int readEnumerated(byte type) throws IOException {
        if (type == 0x00) {
            type = ASN1.UNIVERSAL_ENUMERATED_TYPE;
        }
        checkType(type);
        return readEnumerated();
    }

    @Override
    public long readInteger(byte type) throws IOException {
        if (type == 0x00) {
            type = ASN1.UNIVERSAL_INTEGER_TYPE;
        }
        checkType(type);
        return readInteger();
    }

    @Override
    public void readNull(byte type) throws IOException {
        if (type == 0x00) {
            type = ASN1.UNIVERSAL_NULL_TYPE;
        }
        checkType(type);
        readNull();
    }

    @Override
    public ByteString readOctetString(byte type) throws IOException {
        if (type == 0x00) {
            type = ASN1.UNIVERSAL_OCTET_STRING_TYPE;
        }
        checkType(type);
        return readOctetString();
    }

    @Override
    public ByteStringBuilder readOctetString(byte type, final ByteStringBuilder builder)
            throws IOException {
        if (type == 0x00) {
            type = ASN1.UNIVERSAL_OCTET_STRING_TYPE;
        }
        checkType(type);
        readOctetString(builder);
        return builder;
    }

    @Override
    public String readOctetStringAsString(byte type) throws IOException {
        // We could cache the UTF-8 CharSet if performance proves to be an
        // issue.
        if (type == 0x00) {
            type = ASN1.UNIVERSAL_OCTET_STRING_TYPE;
        }
        checkType(type);
        return readOctetStringAsString();
    }

    @Override
    public void readStartExplicitTag(byte type) throws IOException {
        if (type == 0x00) {
            type = (ASN1.TYPE_MASK_CONTEXT | ASN1.TYPE_MASK_CONSTRUCTED);
        }
        checkType(type);
        readStartExplicitTag();
    }

    @Override
    public void readStartSequence(byte type) throws IOException {
        if (type == 0x00) {
            type = ASN1.UNIVERSAL_SEQUENCE_TYPE;
        }
        checkType(type);
        readStartSequence();
    }

    @Override
    public void readStartSet(byte type) throws IOException {
        // From an implementation point of view, a set is equivalent to a
        // sequence.
        if (type == 0x00) {
            type = ASN1.UNIVERSAL_SET_TYPE;
        }
        checkType(type);
        readStartSet();
    }

    @Override
    public ASN1Reader skipElement(final byte expectedType) throws IOException {
        if (peekType() != expectedType) {
            final LocalizableMessage message =
                    ERR_ASN1_UNEXPECTED_TAG.get(expectedType, peekType());
            throw DecodeException.fatalError(message);
        }
        skipElement();
        return this;
    }

    private void checkType(final byte expectedType) throws IOException {
        if (peekType() != expectedType) {
            final LocalizableMessage message =
                    ERR_ASN1_UNEXPECTED_TAG.get(expectedType, peekType());
            throw DecodeException.fatalError(message);
        }
    }
}
