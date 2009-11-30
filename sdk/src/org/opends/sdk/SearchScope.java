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

package org.opends.sdk;



import java.util.Arrays;
import java.util.Collections;
import java.util.List;



/**
 * A Search operation search scope as defined in RFC 4511 section
 * 4.5.1.2 is used to specify the scope of a Search operation.
 *
 * @see <a href="http://tools.ietf.org/html/rfc4511#section-4.5.1.2">RFC
 *      4511 - Lightweight Directory Access Protocol (LDAP): The
 *      Protocol </a>
 * @see <a
 *      href="http://tools.ietf.org/html/draft-sermersheim-ldap-subordinate-scope">
 *      draft-sermersheim-ldap-subordinate-scope - Subordinate Subtree
 *      Search Scope for LDAP </a>
 */
public final class SearchScope
{
  private static final SearchScope[] ELEMENTS = new SearchScope[4];

  private static final List<SearchScope> IMMUTABLE_ELEMENTS = Collections
      .unmodifiableList(Arrays.asList(ELEMENTS));

  /**
   * The scope is constrained to the search base entry.
   */
  public static final SearchScope BASE_OBJECT = register(0, "base");

  /**
   * The scope is constrained to the immediate subordinates of the
   * search base entry.
   */
  public static final SearchScope SINGLE_LEVEL = register(1, "one");

  /**
   * The scope is constrained to the search base entry and to all its
   * subordinates.
   */
  public static final SearchScope WHOLE_SUBTREE = register(2, "sub");

  /**
   * The scope is constrained to all the subordinates of the search base
   * entry, but does not include the search base entry itself (as
   * wholeSubtree does).
   */
  public static final SearchScope SUBORDINATES = register(3,
      "subordinates");



  /**
   * Creates and registers a new search scope with the application.
   *
   * @param intValue
   *          The integer value of the search scope as defined in RFC
   *          4511 section 4.5.1.2.
   * @param name
   *          The name of the search scope as defined in RFC 4516.
   * @return The new search scope.
   */
  private static SearchScope register(int intValue, String name)
  {
    SearchScope t = new SearchScope(intValue, name);
    ELEMENTS[intValue] = t;
    return t;
  }



  /**
   * Returns the search scope having the specified integer value as
   * defined in RFC 4511 section 4.5.1.2.
   *
   * @param intValue
   *          The integer value of the search scope.
   * @return The search scope, or {@code null} if there was no search
   *         scope associated with {@code intValue}.
   */
  public static SearchScope valueOf(int intValue)
  {
    if (intValue < 0 || intValue >= ELEMENTS.length)
    {
      return null;
    }
    return ELEMENTS[intValue];
  }



  /**
   * Returns an unmodifiable list containing the set of available search
   * scopes indexed on their integer value as defined in RFC 4511
   * section 4.5.1.2.
   *
   * @return An unmodifiable list containing the set of available search
   *         scopes.
   */
  public static List<SearchScope> values()
  {
    return IMMUTABLE_ELEMENTS;
  }



  private final int intValue;

  private final String name;



  // Prevent direct instantiation.
  private SearchScope(int intValue, String name)
  {
    this.intValue = intValue;
    this.name = name;
  }



  /**
   * {@inheritDoc}
   */
  public boolean equals(Object obj)
  {
    if (this == obj)
    {
      return true;
    }
    else if (obj instanceof SearchScope)
    {
      return this.intValue == ((SearchScope) obj).intValue;
    }
    else
    {
      return false;
    }
  }



  /**
   * {@inheritDoc}
   */
  public int hashCode()
  {
    return intValue;
  }



  /**
   * Returns the integer value of this search scope as defined in RFC
   * 4511 section 4.5.1.2.
   *
   * @return The integer value of this search scope.
   */
  public int intValue()
  {
    return intValue;
  }



  /**
   * Returns the string representation of this search scope as defined
   * in RFC 4516.
   *
   * @return The string representation of this search scope.
   */
  public String toString()
  {
    return name;
  }
}
