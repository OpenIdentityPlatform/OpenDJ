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
 * Copyright 2009 Sun Microsystems, Inc.
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;

import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.ByteSequence;

import static org.forgerock.opendj.ldap.schema.SchemaConstants.*;
import static org.forgerock.opendj.ldap.schema.SchemaOptions.*;

/**
 * This class implements the JPEG attribute syntax. This is actually
 * two specifications - JPEG and JFIF. As an extension we allow JPEG
 * and Exif, which is what most digital cameras use. We only check for
 * valid JFIF and Exif headers.
 */
final class JPEGSyntaxImpl extends AbstractSyntaxImpl {
    @Override
    public String getEqualityMatchingRule() {
        return EMR_OCTET_STRING_OID;
    }

    @Override
    public String getName() {
        return SYNTAX_JPEG_NAME;
    }

    @Override
    public String getOrderingMatchingRule() {
        return OMR_OCTET_STRING_OID;
    }

    @Override
    public boolean isHumanReadable() {
        return false;
    }

    @Override
    public boolean valueIsAcceptable(final Schema schema, final ByteSequence value,
            final LocalizableMessageBuilder invalidReason) {
        return schema.getOption(ALLOW_MALFORMED_JPEG_PHOTOS) || isValidJfif(value) || isValidExif(value);
    }

    /**
     * JFIF files start:
     * <pre>
     * 0xff 0xd8 0xff 0xe0 LH LL 0x4a 0x46 0x49 0x46 ...
     * SOI       APP0      len   "JFIF"
     * </pre>
     * So all legal values must be at least 10 bytes long
     */
    private boolean isValidJfif(final ByteSequence value) {
        return value.length() >= 10
                && value.byteAt(0) == (byte) 0xff && value.byteAt(1) == (byte) 0xd8
                && value.byteAt(2) == (byte) 0xff && value.byteAt(3) == (byte) 0xe0
                && value.byteAt(6) == 'J' && value.byteAt(7) == 'F'
                && value.byteAt(8) == 'I' && value.byteAt(9) == 'F';
    }

    /**
     * Exif files (from most digital cameras) start:
     * <pre>
     * 0xff 0xd8 0xff 0xe1 LH LL 0x45 0x78 0x69 0x66 ...
     * SOI       APP1      len   "Exif"
     * </pre>
     * So all legal values must be at least 10 bytes long
     */
    private boolean isValidExif(final ByteSequence value) {
        return value.length() >= 10
                && value.byteAt(0) == (byte) 0xff && value.byteAt(1) == (byte) 0xd8
                && value.byteAt(2) == (byte) 0xff && value.byteAt(3) == (byte) 0xe1
                && value.byteAt(6) == 'E' && value.byteAt(7) == 'x'
                && value.byteAt(8) == 'i' && value.byteAt(9) == 'f';
    }
}
