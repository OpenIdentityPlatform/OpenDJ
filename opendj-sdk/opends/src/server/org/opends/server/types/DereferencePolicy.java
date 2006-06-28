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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.types;



import org.opends.server.protocols.ldap.LDAPConstants;



/**
 * This enumeration defines the set of behaviors that a search
 * operation can exhibit whenever an alias is encountered.  This is
 * based on the LDAP specification defined in RFC 2251.
 */
public enum DereferencePolicy
{
  /**
   * The dereference policy that indicates that aliases should never
   * be dereferenced when processing the search.
   */
  NEVER_DEREF_ALIASES(LDAPConstants.DEREF_NEVER),



  /**
   * The dereference policy that indicates that any aliases found
   * while looking for candidate entries should be dereferenced.
   */
  DEREF_IN_SEARCHING(LDAPConstants.DEREF_IN_SEARCHING),



  /**
   * The dereference policy that indicates that if the entry specified
   * as the base DN is an alias, it should be dereferenced.
   */
  DEREF_FINDING_BASE_OBJECT(LDAPConstants.DEREF_FINDING_BASE),



  /**
   * The dereference policy that indicates that any dereferences
   * encountered should be dereferenced.
   */
  DEREF_ALWAYS(LDAPConstants.DEREF_ALWAYS);



  // The integer value associated with this dereference policy.
  private int intValue;



  /**
   * Creates a new dereference policy with the provided integer value.
   *
   * @param  intValue  The integer value for this dereference policy.
   */
  private DereferencePolicy(int intValue)
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
   * Retrieves a string representation of this alias dereferencing
   * policy.
   *
   * @return  A string representation of this alias dereferencing
   *          policy.
   */
  public String toString()
  {
    switch (intValue)
    {
      case LDAPConstants.DEREF_NEVER:
        return "neverDerefAliases";
      case LDAPConstants.DEREF_IN_SEARCHING:
        return "derefInSearching";
      case LDAPConstants.DEREF_FINDING_BASE:
        return "derefFidingBaseObj";
      case LDAPConstants.DEREF_ALWAYS:
        return "derefAlways";
      default:
        return "Unknown";
    }
  }
}

