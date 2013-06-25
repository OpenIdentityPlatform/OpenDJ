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
 *      Copyright 2008 Sun Microsystems, Inc.
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



  // The human-readable name for this index type.
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
  public String toString()
  {
    return indexName;
  }
}

