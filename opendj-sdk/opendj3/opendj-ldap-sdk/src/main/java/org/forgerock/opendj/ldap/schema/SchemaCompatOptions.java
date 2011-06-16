/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions copyright 2011 ForgeRock AS
 */

package org.forgerock.opendj.ldap.schema;



/**
 * This class provides various schema compatibility options which may be used to
 * facilitate interoperability with legacy LDAP applications.
 */
public final class SchemaCompatOptions
{
  /**
   * Creates a copy of the provided schema compatibility options.
   *
   * @param options
   *          The options to be copied.
   * @return The copy of the provided schema compatibility options.
   */
  public static SchemaCompatOptions copyOf(
      final SchemaCompatOptions options)
  {
    return defaultOptions().assign(options);
  }



  /**
   * Creates a new set of schema compatibility options with default settings.
   *
   * @return The new schema compatibility options.
   */
  public static SchemaCompatOptions defaultOptions()
  {
    return new SchemaCompatOptions();
  }



  private boolean allowNonStandardTelephoneNumbers = true;

  private boolean allowZeroLengthDirectoryStrings = false;

  private boolean allowMalformedNamesAndOptions = false;



  // Prevent direct instantiation.
  private SchemaCompatOptions()
  {
    // Nothing to do.
  }



  /**
   * Returns {@code true} if the schema should allow certain illegal characters
   * in OIDs and attribute options. When this compatibility option is set to
   * {@code true} the following illegal characters will be permitted:
   *
   * <pre>
   * USCORE  = %x5F ; underscore ("_")
   * DOT     = %x2E ; period (".")
   * </pre>
   *
   * By default this compatibility option is set to {@code false}.
   *
   * @return {@code true} if the schema should allow certain illegal characters
   *         in OIDs and attribute options.
   * @see <a href="http://tools.ietf.org/html/rfc4512">RFC 4512 - Lightweight
   *      Directory Access Protocol (LDAP): Directory Information Models </a>
   */
  public boolean allowMalformedNamesAndOptions()
  {
    return allowMalformedNamesAndOptions;
  }



  /**
   * Specifies whether or not the schema should allow certain illegal characters
   * in OIDs and attribute options. When this compatibility option is set to
   * {@code true} the following illegal characters will be permitted:
   *
   * <pre>
   * USCORE  = %x5F ; underscore ("_")
   * DOT     = %x2E ; period (".")
   * </pre>
   *
   * By default this compatibility option is set to {@code false}.
   *
   * @param allowMalformedNamesAndOptions
   *          {@code true} if the schema should allow certain illegal characters
   *          in OIDs and attribute options.
   * @return A reference to this {@code SchemaCompatOptions}.
   * @see <a href="http://tools.ietf.org/html/rfc4512">RFC 4512 - Lightweight
   *      Directory Access Protocol (LDAP): Directory Information Models </a>
   */
  public SchemaCompatOptions allowMalformedNamesAndOptions(
      final boolean allowMalformedNamesAndOptions)
  {
    this.allowMalformedNamesAndOptions = allowMalformedNamesAndOptions;
    return this;
  }



  /**
   * Returns {@code true} if the Telephone Number syntax should allow values
   * which do not conform to the E.123 international telephone number format.
   * <p>
   * By default this compatibility option is set to {@code true}.
   *
   * @return {@code true} if the Telephone Number syntax should allow values
   *         which do not conform to the E.123 international telephone number
   *         format.
   */
  public boolean allowNonStandardTelephoneNumbers()
  {
    return allowNonStandardTelephoneNumbers;
  }



  /**
   * Specifies whether or not the Telephone Number syntax should allow values
   * which do not conform to the E.123 international telephone number format.
   * <p>
   * By default this compatibility option is set to {@code true}.
   *
   * @param allowNonStandardTelephoneNumbers
   *          {@code true} if the Telephone Number syntax should allow values
   *          which do not conform to the E.123 international telephone number
   *          format.
   * @return A reference to this {@code SchemaCompatOptions}.
   */
  public SchemaCompatOptions allowNonStandardTelephoneNumbers(
      final boolean allowNonStandardTelephoneNumbers)
  {
    this.allowNonStandardTelephoneNumbers = allowNonStandardTelephoneNumbers;
    return this;
  }



  /**
   * Returns {@code true} if zero-length values will be allowed by the Directory
   * String syntax. This is technically forbidden by the LDAP specification, but
   * it was allowed in earlier versions of the server, and the discussion of the
   * directory string syntax in RFC 2252 does not explicitly state that they are
   * not allowed.
   * <p>
   * By default this compatibility option is set to {@code false}.
   *
   * @return {@code true} if zero-length values will be allowed by the Directory
   *         String syntax, or {@code false} if not.
   */
  public boolean allowZeroLengthDirectoryStrings()
  {
    return allowZeroLengthDirectoryStrings;
  }



  /**
   * Specifies whether or not zero-length values will be allowed by the
   * Directory String syntax. This is technically forbidden by the LDAP
   * specification, but it was allowed in earlier versions of the server, and
   * the discussion of the directory string syntax in RFC 2252 does not
   * explicitly state that they are not allowed.
   * <p>
   * By default this compatibility option is set to {@code false}.
   *
   * @param allowZeroLengthDirectoryStrings
   *          {@code true} if zero-length values will be allowed by the
   *          Directory String syntax, or {@code false} if not.
   * @return A reference to this {@code SchemaCompatOptions}.
   */
  public SchemaCompatOptions allowZeroLengthDirectoryStrings(
      final boolean allowZeroLengthDirectoryStrings)
  {
    this.allowZeroLengthDirectoryStrings = allowZeroLengthDirectoryStrings;
    return this;
  }



  // Assigns the provided options to this set of options.
  SchemaCompatOptions assign(final SchemaCompatOptions options)
  {
    this.allowMalformedNamesAndOptions = options.allowMalformedNamesAndOptions;
    this.allowNonStandardTelephoneNumbers =options.allowNonStandardTelephoneNumbers;
    this.allowZeroLengthDirectoryStrings = options.allowZeroLengthDirectoryStrings;
    return this;
  }

}
