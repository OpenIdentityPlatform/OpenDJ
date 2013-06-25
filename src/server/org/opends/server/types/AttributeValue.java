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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.types;



/**
 * This class defines a data structure that holds information about a
 * single value of an attribute.
 */
public interface AttributeValue
{

  /**
   * Indicates whether the provided object is an attribute value that
   * is equal to this attribute value. It will be considered equal if
   * the normalized representations of both attribute values are
   * equal.
   *
   * @param o
   *          The object for which to make the determination.
   * @return <CODE>true</CODE> if the provided object is an attribute
   *         value that is equal to this attribute value, or
   *         <CODE>false</CODE> if not.
   */
  boolean equals(Object o);



  /**
   * Retrieves the normalized form of this attribute value.
   *
   * @return The normalized form of this attribute value.
   * @throws DirectoryException
   *           If an error occurs while trying to normalize the value
   *           (e.g., if it is not acceptable for use with the
   *           associated equality matching rule).
   */
  ByteString getNormalizedValue() throws DirectoryException;



  /**
   * Retrieves the user-defined form of this attribute value.
   *
   * @return The user-defined form of this attribute value.
   */
  ByteString getValue();



  /**
   * Retrieves the hash code for this attribute value. It will be
   * calculated using the normalized representation of the value.
   *
   * @return The hash code for this attribute value.
   */
  int hashCode();



  /**
   * Retrieves a string representation of this attribute value.
   *
   * @return A string representation of this attribute value.
   */
  String toString();



  /**
   * Appends a string representation of this attribute value to the
   * provided buffer.
   *
   * @param buffer
   *          The buffer to which the information should be appended.
   */
  void toString(StringBuilder buffer);
}
