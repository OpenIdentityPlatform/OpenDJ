/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.opends.sdk.schema;



/**
 * This class provides various schema compatibility options which may be
 * used to facilitate interoperability with legacy LDAP applications.
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
  public static SchemaCompatOptions copyOf(SchemaCompatOptions options)
  {
    return defaultOptions().assign(options);
  }



  /**
   * Creates a new set of schema compatibility options with default
   * settings.
   * 
   * @return The new schema compatibility options.
   */
  public static SchemaCompatOptions defaultOptions()
  {
    return new SchemaCompatOptions();
  }

  private boolean isTelephoneNumberSyntaxStrict = false;

  private boolean isZeroLengthDirectoryStringsAllowed = false;



  // Prevent direct instantiation.
  private SchemaCompatOptions()
  {
    // Nothing to do.
  }



  /**
   * Indicates whether or not the Telephone Number syntax should ensure
   * that all values conform to the E.123 international telephone number
   * format. By default this compatibility option is set to {@code
   * false}.
   * 
   * @return {@code true} if the Telephone Number syntax should ensure
   *         that all values conform to the E.123 international
   *         telephone number format, or {@code false} if not.
   */
  public boolean isTelephoneNumberSyntaxStrict()
  {
    return isTelephoneNumberSyntaxStrict;
  }



  /**
   * Indicates whether or not zero-length values will be allowed by the
   * Directory String syntax. This is technically forbidden by the LDAP
   * specification, but it was allowed in earlier versions of the
   * server, and the discussion of the directory string syntax in RFC
   * 2252 does not explicitly state that they are not allowed. By
   * default this compatibility option is set to {@code false}.
   * 
   * @return {@code true} if zero-length values will be allowed by the
   *         Directory String syntax, or {@code false} if not.
   */
  public boolean isZeroLengthDirectoryStringsAllowed()
  {
    return isZeroLengthDirectoryStringsAllowed;
  }



  /**
   * Indicates whether or not the Telephone Number syntax should ensure
   * that all values conform to the E.123 international telephone number
   * format. By default this compatibility option is set to {@code
   * false}.
   * 
   * @param isStrict
   *          {@code true} if the Telephone Number syntax should ensure
   *          that all values conform to the E.123 international
   *          telephone number format, or {@code false} if not.
   * @return A reference to this {@code SchemaCompat}.
   */
  public SchemaCompatOptions setTelephoneNumberSyntaxStrict(
      boolean isStrict)
  {
    this.isTelephoneNumberSyntaxStrict = isStrict;
    return this;
  }



  /**
   * Specifies whether or not zero-length values will be allowed by the
   * Directory String syntax. This is technically forbidden by the LDAP
   * specification, but it was allowed in earlier versions of the
   * server, and the discussion of the directory string syntax in RFC
   * 2252 does not explicitly state that they are not allowed. By
   * default this compatibility option is set to {@code false}.
   * 
   * @param isAllowed
   *          {@code true} if zero-length values will be allowed by the
   *          Directory String syntax, or {@code false} if not.
   * @return A reference to this {@code SchemaCompat}.
   */
  public SchemaCompatOptions setZeroLengthDirectoryStringsAllowed(
      boolean isAllowed)
  {
    this.isZeroLengthDirectoryStringsAllowed = isAllowed;
    return this;
  }



  // Assigns the provided options to this set of options.
  SchemaCompatOptions assign(SchemaCompatOptions options)
  {
    return setTelephoneNumberSyntaxStrict(
        options.isTelephoneNumberSyntaxStrict)
        .setZeroLengthDirectoryStringsAllowed(
            options.isZeroLengthDirectoryStringsAllowed);
  }

}
