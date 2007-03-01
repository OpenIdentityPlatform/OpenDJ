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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.types;



/**
 * This enumeration defines the set of possible objectclass types that
 * may be used, as defined in RFC 2252.
 */
public enum ObjectClassType
{
  /**
   * The objectclass type that to use for classes declared "abstract".
   */
  ABSTRACT("ABSTRACT"),



  /**
   * The objectclass type that to use for classes declared
   * "structural".
   */
  STRUCTURAL("STRUCTURAL"),



  /**
   * The objectclass type that to use for classes declared
   * "auxiliary".
   */
  AUXILIARY("AUXILIARY");



  // The string representation of this objectclass type.
  String typeString;



  /**
   * Creates a new objectclass type with the provided string
   * representation.
   *
   * @param  typeString  The string representation for this
   *                     objectclass type.
   */
  private ObjectClassType(String typeString)
  {
    this.typeString = typeString;
  }



  /**
   * Retrieves a string representation of this objectclass type.
   *
   * @return  A string representation of this objectclass type.
   */
  public String toString()
  {
    return typeString;
  }
}

