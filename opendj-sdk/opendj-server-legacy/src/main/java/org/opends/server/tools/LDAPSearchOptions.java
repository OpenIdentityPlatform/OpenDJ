/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.tools;

import static org.forgerock.opendj.ldap.DereferenceAliasesPolicy.*;
import static org.forgerock.opendj.ldap.SearchScope.*;
import static org.opends.messages.ToolMessages.*;

import static com.forgerock.opendj.cli.Utils.*;

import java.io.PrintStream;

import org.forgerock.opendj.ldap.DereferenceAliasesPolicy;
import org.forgerock.opendj.ldap.SearchScope;



/**
 * This class defines options for the search operations used
 * by the ldapsearch tool.
 */
public class LDAPSearchOptions extends LDAPToolOptions
{

  private DereferenceAliasesPolicy dereferencePolicy =  NEVER;
  private SearchScope searchScope = WHOLE_SUBTREE;
  private int sizeLimit;
  private int timeLimit;
  private boolean typesOnly;
  private boolean countMatchingEntries;

  /**
   * Creates the options instance.
   */
  public LDAPSearchOptions()
  {
  }

  /**
   * Set the timeLimit for the operation.
   *
   * @param timeLimit    The time limit for the search.
   */
  public void setTimeLimit(int timeLimit)
  {
    this.timeLimit = timeLimit;
  }

  /**
   * Return the timeLimit value.
   *
   * @return  The timeLimit value.
   */
  public int getTimeLimit()
  {
    return timeLimit;
  }

  /**
   * Set the sizeLimit for the operation.
   *
   * @param sizeLimit    The size limit for the search.
   *
   */

  public void setSizeLimit(int sizeLimit)
  {
    this.sizeLimit = sizeLimit;
  }

  /**
   * Return the sizeLimit value.
   *
   * @return  The sizeLimit value.
   */
  public int getSizeLimit()
  {
    return sizeLimit;
  }

  /**
   * Set the search scope .
   *
   * @param  scope  The search scope string.
   * @param  err    A print stream to which error messages should be written if
   *                a problem occurs.
   *
   * @return  <CODE>true</CODE> if the scope was set properly, or
   *          <CODE>false</CODE> if not.
   */

  public boolean setSearchScope(String scope, PrintStream err)
  {
      if(scope == null)
      {
        searchScope = WHOLE_SUBTREE;
      }
      else if(scope.equalsIgnoreCase("base"))
      {
        searchScope = BASE_OBJECT;
      } else if(scope.equalsIgnoreCase("one"))
      {
        searchScope = SINGLE_LEVEL;
      } else if (scope.equalsIgnoreCase("sub"))
      {
        searchScope = WHOLE_SUBTREE;
      } else if (scope.equalsIgnoreCase("subordinate"))
      {
        searchScope = SUBORDINATES;
      } else
      {
        printWrappedText(err, ERR_SEARCH_INVALID_SEARCH_SCOPE.get(scope));
        return false;
      }
      return true;
  }

  /**
   * Get the search scope value.
   *
   * @return  The search scope value.
   */
  public SearchScope getSearchScope()
  {
    return searchScope;
  }

  /**
   * Set the dereference policy.
   *
   * @param policy  The dereference policy.
   * @param  err    A print stream to which error messages should be written if
   *                a problem occurs.
   *
   * @return  <CODE>true</CODE> if the dereference policy was set properly, or
   *          <CODE>false</CODE> if not.
   */

  public boolean setDereferencePolicy(String policy, PrintStream err)
  {
      if(policy == null)
      {
        dereferencePolicy = NEVER;
      } else if(policy.equals("never"))
      {
        dereferencePolicy = NEVER;
      } else if(policy.equals("always"))
      {
        dereferencePolicy = ALWAYS;
      } else if (policy.equals("search"))
      {
        dereferencePolicy = IN_SEARCHING;
      } else if (policy.equals("find"))
      {
        dereferencePolicy = FINDING_BASE;
      } else
      {
        printWrappedText(err, ERR_SEARCH_INVALID_DEREFERENCE_POLICY.get(policy));
        return false;
      }
      return true;
  }

  /**
   * Return the dereference policy.
   *
   * @return  The alias dereference policy.
   */
  public DereferenceAliasesPolicy getDereferencePolicy()
  {
    return dereferencePolicy;
  }

  /**
   * Return only the attribute types in the search result.
   *
   * @return  <CODE>true</CODE> if only attribute types should be returned in
   *          matching entries, or <CODE>false</CODE> if both types and values
   *          should be included.
   */
  public boolean getTypesOnly()
  {
    return this.typesOnly;
  }


  /**
   * Return only the attribute types in the search result.
   *
   * @param  typesOnly  Specifies whether only attribute types should be
   *                    returned in matching entries, or both types and values.
   */
  public void setTypesOnly(boolean typesOnly)
  {
    this.typesOnly = typesOnly;
  }


  /**
   * Indicates whether to report the number of matching entries returned by the
   * server.
   *
   * @return  {@code true} if the number of matching entries should be reported,
   *          or {@code false} if not.
   */
  public boolean countMatchingEntries()
  {
    return countMatchingEntries;
  }


  /**
   * Specifies whether to report the number of matching entries returned by the
   * server.
   *
   * @param  countMatchingEntries  Specifies whether to report the number of
   *                               matching entries returned by the server.
   */
  public void setCountMatchingEntries(boolean countMatchingEntries)
  {
    this.countMatchingEntries = countMatchingEntries;
  }
}

