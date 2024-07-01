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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.types;

import static org.opends.server.util.StaticUtils.*;



/**
 * This class implements an enumeration that may be used to define the
 * ways in which an attribute may be indexed within the server.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public enum IndexType
{
  /**
   * Used to denote a presence index, which may be used to identify
   * entries containing the associated attribute (regardless of the
   * value for that attribute).
   */
  PRESENCE("presence"),



  /**
   * Used to denote an equality index, which may be used to identify
   * entries containing a specified value for the associated
   * attribute.
   */
  EQUALITY("equality"),



  /**
   * Used to denote a substring index, which may be used to identify
   * entries with one or more values for the associated attribute that
   * match a given substring assertion.  That substring assertion may
   * contain any or all of subInitial, subAny, and subFinal elements.
   */
  SUBSTRING("substring"),



  /**
   * Used to denote a subInitial index, which may be used to identify
   * entries with one or more values for the associated attribute that
   * begin with a specified string.
   */
  SUBINITIAL("subinitial"),



  /**
   * Used to denote a subAny index, which may be used to identify
   * entries with one or more values for the associated attribute that
   * contain a specified string.
   */
  SUBANY("subany"),



  /**
   * Used to denote a subFinal index, which may be used to identify
   * entries with one or more values for the associated attribute that
   * end with a specified string.
   */
  SUBFINAL("subfinal"),



  /**
   * Used to denote a greater-or-equal index, which may be used to
   * identify entries with one or more values that are greater than or
   * equal to a specified value.
   */
  GREATER_OR_EQUAL("greater-or-equal"),



  /**
   * Used to denote a less-or-equal index, which may be used to
   * identify entries with one or more values that are less than or
   * equal to a specified value.
   */
  LESS_OR_EQUAL("less-or-equal"),



  /**
   * Used to denote an approximate index, which may be used to
   * identify entries with one or more values that are approximately
   * equal to a specified value.
   */
  APPROXIMATE("approximate");



  /** The human-readable name for this index type. */
  private final String indexName;



  /**
   * Creates a new index type with the specified name.
   *
   * @param  indexName  The human-readable name for this index type.
   */
  private IndexType(String indexName)
  {
    this.indexName = indexName;
  }



  /**
   * Retrieves the index type for the specified name.
   *
   * @param  indexName  The name for which to retrieve the
   *                    associated index type.
   *
   * @return  The requested index type, or {@code null} if there is no
   *          such index type.
   */
  public static IndexType forName(String indexName)
  {
    String lowerName = toLowerCase(indexName);
    if (lowerName.equals("presence") || lowerName.equals("pres"))
    {
      return PRESENCE;
    }
    else if (lowerName.equals("equality") || lowerName.equals("eq"))
    {
      return EQUALITY;
    }
    else if (lowerName.equals("substring") || lowerName.equals("sub"))
    {
      return SUBSTRING;
    }
    else if (lowerName.equals("subinitial"))
    {
      return SUBINITIAL;
    }
    else if (lowerName.equals("subany"))
    {
      return SUBANY;
    }
    else if (lowerName.equals("subfinal"))
    {
      return SUBFINAL;
    }
    else if (lowerName.equals("greater-or-equal") ||
             lowerName.equals("greaterorequal") ||
             lowerName.equals("greater-than-or-equal-to") ||
             lowerName.equals("greaterthanorequalto"))
    {
      return GREATER_OR_EQUAL;
    }
    else if (lowerName.equals("less-or-equal") ||
             lowerName.equals("lessorequal") ||
             lowerName.equals("less-than-or-equal-to") ||
             lowerName.equals("lessthanorequalto"))
    {
      return LESS_OR_EQUAL;
    }
    else if (lowerName.equals("approximate") ||
             lowerName.equals("approx"))
    {
      return APPROXIMATE;
    }

    return null;
  }



  /**
   * Retrieves the human-readable name for this index type.
   *
   * @return  The human-readable name for this index type.
   */
  @Override
  public String toString()
  {
    return indexName;
  }
}

