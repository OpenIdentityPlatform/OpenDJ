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
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.types;

import static org.opends.server.protocols.ldap.LDAPConstants.*;

/**
 * This enumeration defines the set of possible filter types that may
 * be used for search filters.  This is based on the LDAP
 * specification defined in RFC 2251.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public enum FilterType
{
  /** The filter type for AND filters. */
  AND(TYPE_FILTER_AND),
  /** The filter type for OR filters. */
  OR(TYPE_FILTER_OR),
  /** The filter type for NOT filters. */
  NOT(TYPE_FILTER_NOT),
  /** The filter type for equality filters. */
  EQUALITY(TYPE_FILTER_EQUALITY),
  /** The filter type for substring filters. */
  SUBSTRING(TYPE_FILTER_SUBSTRING),
  /** The filter type for greater or equal filters. */
  GREATER_OR_EQUAL(TYPE_FILTER_GREATER_OR_EQUAL),
  /** The filter type for less or equal filters. */
  LESS_OR_EQUAL(TYPE_FILTER_LESS_OR_EQUAL),
  /** The filter type for presence filters. */
  PRESENT(TYPE_FILTER_PRESENCE),
  /** The filter type for approximate filters. */
  APPROXIMATE_MATCH(TYPE_FILTER_APPROXIMATE),
  /** The filter type for extensible matching filters. */
  EXTENSIBLE_MATCH(TYPE_FILTER_EXTENSIBLE_MATCH);

  /** The LDAP BER type for this filter type. */
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
  @Override
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
