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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions copyright 2012-2013 ForgeRock AS.
 *      Portions Copyright 2014 Manuel Gaupp
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
    /**
     * Creates a new abstract ASN.1 reader.
     */
    protected AbstractASN1Reader() {
        // No implementation required.
    }

    /** {@inheritDoc} */
    public boolean readBoolean(byte type) throws IOException {
        if (type == 0x00) {
            type = ASN1.UNIVERSAL_BOOLEAN_TYPE;
        }
        checkType(type);
        return readBoolean();
    }

    /** {@inheritDoc} */
    public int readEnumerated(byte type) throws IOException {
        if (type == 0x00) {
            type = ASN1.UNIVERSAL_ENUMERATED_TYPE;
        }
        checkType(type);
        return readEnumerated();
    }

    /** {@inheritDoc} */
    public long readInteger(byte type) throws IOException {
        if (type == 0x00) {
            type = ASN1.UNIVERSAL_INTEGER_TYPE;
        }
        checkType(type);
        return readInteger();
    }

    /** {@inheritDoc} */
    public void readNull(byte type) throws IOException {
        if (type == 0x00) {
            type = ASN1.UNIVERSAL_NULL_TYPE;
        }
        checkType(type);
        readNull();
    }

    /** {@inheritDoc} */
    public ByteString readOctetString(byte type) throws IOException {
        if (type == 0x00) {
            type = ASN1.UNIVERSAL_OCTET_STRING_TYPE;
        }
        checkType(type);
        return readOctetString();
    }

    /** {@inheritDoc} */
    public ByteStringBuilder readOctetString(byte type, final ByteStringBuilder builder)
            throws IOException {
        if (type == 0x00) {
            type = ASN1.UNIVERSAL_OCTET_STRING_TYPE;
        }
        checkType(type);
        readOctetString(builder);
        return builder;
    }

    /** {@inheritDoc} */
    public String readOctetStringAsString(byte type) throws IOException {
        // We could cache the UTF-8 CharSet if performance proves to be an
        // issue.
        if (type == 0x00) {
            type = ASN1.UNIVERSAL_OCTET_STRING_TYPE;
        }
        checkType(type);
        return readOctetStringAsString();
    }

    /** {@inheritDoc} */
    public void readStartExplicitTag(byte type) throws IOException {
        if (type == 0x00) {
            type = (ASN1.TYPE_MASK_CONTEXT | ASN1.TYPE_MASK_CONSTRUCTED);
        }
        checkType(type);
        readStartExplicitTag();
    }

    /** {@inheritDoc} */
    public void readStartSequence(byte type) throws IOException {
        if (type == 0x00) {
            type = ASN1.UNIVERSAL_SEQUENCE_TYPE;
        }
        checkType(type);
        readStartSequence();
    }

    /** {@inheritDoc} */
    public void readStartSet(byte type) throws IOException {
        // From an implementation point of view, a set is equivalent to a
        // sequence.
        if (type == 0x00) {
            type = ASN1.UNIVERSAL_SET_TYPE;
        }
        checkType(type);
        readStartSet();
    }

    /** {@inheritDoc} */
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
