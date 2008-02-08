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



import org.opends.server.protocols.ldap.LDAPConstants;



/**
 * This enumeration defines the set of possible scopes that may be
 * used for a search request.  This is based on the LDAP specification
 * defined in RFC 2251 but also includes the subordinate subtree
 * search scope defined in draft-sermersheim-ldap-subordinate-scope.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public enum SearchScope
{
  /**
   * The search scope that indicates only the entry specified as the
   * search base should be considered a candidate for matching.
   */
  BASE_OBJECT(LDAPConstants.SCOPE_BASE_OBJECT),



  /**
   * The search scope that indicates only those entries that are
   * immediate children of the entry specified as the search base (and
   * not any of their descendants, and not the search base itself)
   * should be considered candidates for matching.
   */
  SINGLE_LEVEL(LDAPConstants.SCOPE_SINGLE_LEVEL),



  /**
   * The search scope that indicates the entry specified as the search
   * base and all descendants (recursively) should be considered
   * candidates for matching.
   */
  WHOLE_SUBTREE(LDAPConstants.SCOPE_WHOLE_SUBTREE),



  /**
   * The search scope that indicates all descendants (recursively)
   * below the entry specified as the search base (but not the search
   * base entry itself) should be considered candidates for matching.
   */
  SUBORDINATE_SUBTREE(LDAPConstants.SCOPE_SUBORDINATE_SUBTREE);



  // The integer value associated with this search scope.
  private int intValue;



  /**
   * Creates a new search scope with the provided integer value.
   *
   * @param  intValue  The integer value associated with this search
   *                   scope.
   */
  private SearchScope(int intValue)
  {
    this.intValue = intValue;
  }



  /**
   * Retrieves the integer value associated with this search scope.
   *
   * @return  The integer value associated with this search scope.
   */
  public int intValue()
  {
    return intValue;
  }



  /**
   * Retrieves a string representation of this search scope.
   *
   * @return  A string representation of this search scope.
   */
  public String toString()
  {
    switch (intValue)
    {
      case LDAPConstants.SCOPE_BASE_OBJECT:
        return "baseObject";
      case LDAPConstants.SCOPE_SINGLE_LEVEL:
        return "singleLevel";
      case LDAPConstants.SCOPE_WHOLE_SUBTREE:
        return "wholeSubtree";
      case LDAPConstants.SCOPE_SUBORDINATE_SUBTREE:
        return "subordinateSubtree";
      default:
        return "Unknown";
    }
  }
}

