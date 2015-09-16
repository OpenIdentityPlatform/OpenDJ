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
 *      Copyright 2014-2015 ForgeRock AS
 */
package org.forgerock.opendj.ldap.schema;

import org.forgerock.util.Option;

import static org.forgerock.opendj.ldap.schema.SchemaConstants.*;

/**
 * Common options for LDAP schemas.
 * <p>
 * For example you set schema option as you want when using a schema.
 *
 * <pre>
 * // Retrieves options from builder.
 * SchemaOptions options = new SchemaBuilder().getOptions();
 * // Creates a new option.
 * Option myIntegerOption = options.set(Option.of(Integer.class, 0));
 * // Retrieves option value from SchemaOption
 * boolean allowMalformedNamesAndOptions = options.get(SchemaOptions.ALLOW_MALFORMED_NAMES_AND_OPTIONS);
 * </pre>
 */
public final class SchemaOptions {
    /**
     * Specifies whether the schema should allow certain illegal
     * characters in OIDs and attribute options. When this compatibility option
     * is set to {@code true} the following illegal characters will be permitted
     * in addition to those permitted in section 1.4 of RFC 4512:
     *
     * <pre>
     * USCORE  = %x5F ; underscore ("_")
     * DOT     = %x2E ; period (".")
     * </pre>
     *
     * By default this compatibility option is set to {@code true} because these
     * characters are often used for naming purposes (such as collation rules).
     */
    public static final Option<Boolean> ALLOW_MALFORMED_NAMES_AND_OPTIONS = Option.withDefault(true);

    /**
     * Specifies whether the JPEG Photo syntax should allow values which
     * do not conform to the JFIF or Exif specifications.
     * <p>
     * By default this compatibility option is set to {@code true}.
     */
    public static final Option<Boolean> ALLOW_MALFORMED_JPEG_PHOTOS = Option.withDefault(true);

    /**
     * Specifies whether the Certificate syntax should allow values which
     * do not conform to the X.509 specifications.
     * <p>
     * By default this compatibility option is set to {@code true}.
     */
    public static final Option<Boolean> ALLOW_MALFORMED_CERTIFICATES = Option.withDefault(true);

    /**
     * Specifies whether the Telephone Number syntax should allow values
     * which do not conform to the E.123 international telephone number format.
     * <p>
     * By default this compatibility option is set to {@code true}.
     */
    public static final Option<Boolean> ALLOW_NON_STANDARD_TELEPHONE_NUMBERS = Option.withDefault(true);

    /**
     * Specifies whether zero-length values will be allowed by the
     * Directory String syntax. This is technically forbidden by the LDAP
     * specification, but it was allowed in earlier versions of the server, and
     * the discussion of the directory string syntax in RFC 2252 does not
     * explicitly state that they are not allowed.
     * <p>
     * By default this compatibility option is set to {@code false}.
     */
    public static final Option<Boolean> ALLOW_ZERO_LENGTH_DIRECTORY_STRINGS = Option.withDefault(false);

    /**
     * Specifies the OID of the default syntax which will be used when parsing
     * unrecognized attributes.
     * <p>
     * By default the {@link SchemaConstants#SYNTAX_OCTET_STRING_OID OctetString}
     * syntax will be used.
     */
    public static final Option<String> DEFAULT_SYNTAX_OID = Option.of(String.class, SYNTAX_OCTET_STRING_OID);

    /**
     * Specifies the OID of the default matching rule which will be used when
     * parsing unrecognized attributes.
     * <p>
     * By default the {@link SchemaConstants#EMR_OCTET_STRING_OID OctetString}
     * matching rule will be used.
     */
    public static final Option<String> DEFAULT_MATCHING_RULE_OID = Option.of(String.class, EMR_OCTET_STRING_OID);

    /**
     * Indicates whether country code values are required to strictly
     * comply with the standard definition for this syntax.
     * <p>
     * When set to false, country codes will not be validated and, as a result
     * any string containing 2 characters will be acceptable.
     * By default this compatibility option is set to {@code true}.
     */
    public static final Option<Boolean> STRICT_FORMAT_FOR_COUNTRY_STRINGS = Option.withDefault(true);

    /**
     * Indicates whether the minimum upper bound value should be stripped from
     * the Attribute Type Syntax Description.
     * <p>
     * By default this compatibility option is set to {@code false}.
     */
    public static final Option<Boolean> STRIP_UPPER_BOUND_FOR_ATTRIBUTE_TYPE = Option.withDefault(false);

    private SchemaOptions() { }
}
