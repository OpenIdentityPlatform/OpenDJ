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
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions Copyright 2012-2014 ForgeRock AS
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

    /**
     * Indicates whether the provided value is acceptable for use in an
     * attribute with this syntax. If it is not, then the reason may be appended
     * to the provided buffer.
     *
     * @param schema
     *            The schema in which this syntax is defined.
     * @param value
     *            The value for which to make the determination.
     * @param invalidReason
     *            The buffer to which the invalid reason should be appended.
     * @return <CODE>true</CODE> if the provided value is acceptable for use
     *         with this syntax, or <CODE>false</CODE> if not.
     */
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
