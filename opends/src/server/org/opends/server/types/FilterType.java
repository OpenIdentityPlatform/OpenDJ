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



import static org.opends.server.protocols.ldap.LDAPConstants.*;



/**
 * This enumeration defines the set of possible filter types that may
 * be used for search filters.  This is based on the LDAP
 * specification defined in RFC 2251.
 */
public enum FilterType
{
  /**
   * The filter type for AND filters.
   */
  AND(TYPE_FILTER_AND),



  /**
   * The filter type for OR filters.
   */
  OR(TYPE_FILTER_OR),



  /**
   * The filter type for NOT filters.
   */
  NOT(TYPE_FILTER_NOT),



  /**
   * The filter type for equality filters.
   */
  EQUALITY(TYPE_FILTER_EQUALITY),



  /**
   * The filter type for substring filters.
   */
  SUBSTRING(TYPE_FILTER_SUBSTRING),



  /**
   * The filter type for greater or equal filters.
   */
  GREATER_OR_EQUAL(TYPE_FILTER_GREATER_OR_EQUAL),



  /**
   * The filter type for less or equal filters.
   */
  LESS_OR_EQUAL(TYPE_FILTER_LESS_OR_EQUAL),



  /**
   * The filter type for presence filters.
   */
  PRESENT(TYPE_FILTER_PRESENCE),



  /**
   * The filter type for approximate filters.
   */
  APPROXIMATE_MATCH(TYPE_FILTER_APPROXIMATE),



  /**
   * The filter type for extensible matching filters.
   */
  EXTENSIBLE_MATCH(TYPE_FILTER_EXTENSIBLE_MATCH);



  // The LDAP BER type for this filter type.
  private byte berType;



  /**
   * Creates a new filter type with the provided BER type.
   *
   * @param  berType  The LDAP BER type for this filter type.
   */
  private FilterType(byte berType)
  {
    this.berType = berType;
  }



  /**
   * Retrieves the LDAP BER type for this filter type.
   *
   * @return  The LDAP BER type for this filter type.
   */
  public byte getBERType()
  {
    return berType;
  }



  /**
   * Retrieves a string representation of this filter type.
   *
   * @return  A string representation of this filter type.
   */
  public String toString()
  {
    switch (berType)
    {
      case TYPE_FILTER_AND:
        return "and";
      case TYPE_FILTER_OR:
        return "or";
      case TYPE_FILTER_NOT:
        return "not";
      case TYPE_FILTER_EQUALITY:
        return "equalityMatch";
      case TYPE_FILTER_SUBSTRING:
        return "substrings";
      case TYPE_FILTER_GREATER_OR_EQUAL:
        return "greaterOrEqual";
      case TYPE_FILTER_LESS_OR_EQUAL:
        return "lessOrEqual";
      case TYPE_FILTER_PRESENCE:
        return "present";
      case TYPE_FILTER_APPROXIMATE:
        return "approxMatch";
      case TYPE_FILTER_EXTENSIBLE_MATCH:
        return "extensibleMatch";
      default:
        return "Unknown";
    }
  }
}

