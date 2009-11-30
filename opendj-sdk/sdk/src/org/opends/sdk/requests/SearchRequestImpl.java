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

package org.opends.sdk.requests;



import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.opends.sdk.DN;
import org.opends.sdk.DereferenceAliasesPolicy;
import org.opends.sdk.Filter;
import org.opends.sdk.SearchScope;
import org.opends.sdk.util.LocalizedIllegalArgumentException;
import org.opends.sdk.util.Validator;



/**
 * Search request implementation.
 */
final class SearchRequestImpl extends
    AbstractRequestImpl<SearchRequest> implements SearchRequest
{

  private final List<String> attributes = new LinkedList<String>();

  private DN name;

  private DereferenceAliasesPolicy dereferenceAliasesPolicy = DereferenceAliasesPolicy.NEVER;

  private Filter filter;

  private SearchScope scope;

  private int sizeLimit = 0;

  private int timeLimit = 0;

  private boolean typesOnly = false;



  /**
   * Creates a new search request using the provided distinguished name,
   * scope, and filter, decoded using the default schema.
   *
   * @param name
   *          The distinguished name of the base entry relative to which
   *          the search is to be performed.
   * @param scope
   *          The scope of the search.
   * @param filter
   *          The filter that defines the conditions that must be
   *          fulfilled in order for an entry to be returned.
   * @param attributeDescriptions
   *          The names of the attributes to be included with each
   *          entry.
   * @throws NullPointerException
   *           If the {@code name}, {@code scope}, or {@code filter}
   *           were {@code null}.
   */
  SearchRequestImpl(DN name, SearchScope scope, Filter filter)
      throws NullPointerException
  {
    this.name = name;
    this.scope = scope;
    this.filter = filter;
  }



  /**
   * {@inheritDoc}
   */
  public SearchRequest addAttribute(
      Collection<String> attributeDescriptions)
      throws NullPointerException
  {
    Validator.ensureNotNull(attributeDescriptions);

    attributes.addAll(attributeDescriptions);
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public SearchRequest addAttribute(String attributeDescription)
      throws NullPointerException
  {
    Validator.ensureNotNull(attributeDescription);

    attributes.add(attributeDescription);
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public SearchRequest addAttribute(String... attributeDescriptions)
      throws NullPointerException
  {
    Validator.ensureNotNull((Object) attributeDescriptions);

    for (final String attributeDescription : attributeDescriptions)
    {
      attributes.add(attributeDescription);
    }
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public SearchRequest clearAttributes()
  {
    attributes.clear();
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public Iterable<String> getAttributes()
  {
    return attributes;
  }



  /**
   * {@inheritDoc}
   */
  public DereferenceAliasesPolicy getDereferenceAliasesPolicy()
  {
    return dereferenceAliasesPolicy;
  }



  /**
   * {@inheritDoc}
   */
  public Filter getFilter()
  {
    return filter;
  }



  /**
   * {@inheritDoc}
   */
  public DN getName()
  {
    return name;
  }



  /**
   * {@inheritDoc}
   */
  public SearchScope getScope()
  {
    return scope;
  }



  /**
   * {@inheritDoc}
   */
  public int getSizeLimit()
  {
    return sizeLimit;
  }



  /**
   * {@inheritDoc}
   */
  public int getTimeLimit()
  {
    return timeLimit;
  }



  /**
   * {@inheritDoc}
   */
  public boolean hasAttributes()
  {
    return !attributes.isEmpty();
  }



  /**
   * {@inheritDoc}
   */
  public boolean isTypesOnly()
  {
    return typesOnly;
  }



  /**
   * {@inheritDoc}
   */
  public boolean removeAttribute(String attributeDescription)
      throws NullPointerException
  {
    Validator.ensureNotNull(attributeDescription);

    return attributes.remove(attributeDescription);
  }



  /**
   * {@inheritDoc}
   */
  public SearchRequest setDereferenceAliasesPolicy(
      DereferenceAliasesPolicy policy) throws NullPointerException
  {
    Validator.ensureNotNull(policy);

    this.dereferenceAliasesPolicy = policy;
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public SearchRequest setFilter(Filter filter)
      throws NullPointerException
  {
    Validator.ensureNotNull(filter);

    this.filter = filter;
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public SearchRequest setFilter(String filter)
      throws LocalizedIllegalArgumentException, NullPointerException
  {
    this.filter = Filter.valueOf(filter);
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public SearchRequest setName(DN dn) throws NullPointerException
  {
    Validator.ensureNotNull(dn);

    this.name = dn;
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public SearchRequest setName(String dn)
      throws LocalizedIllegalArgumentException, NullPointerException
  {
    Validator.ensureNotNull(dn);

    this.name = DN.valueOf(dn);
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public SearchRequest setScope(SearchScope scope)
      throws NullPointerException
  {
    Validator.ensureNotNull(scope);

    this.scope = scope;
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public SearchRequest setSizeLimit(int limit)
      throws LocalizedIllegalArgumentException
  {
    // FIXME: I18N error message.
    Validator.ensureTrue(limit >= 0, "negative size limit");

    this.sizeLimit = limit;
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public SearchRequest setTimeLimit(int limit)
      throws LocalizedIllegalArgumentException
  {
    // FIXME: I18N error message.
    Validator.ensureTrue(limit >= 0, "negative time limit");

    this.timeLimit = limit;
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public SearchRequest setTypesOnly(boolean typesOnly)
  {
    this.typesOnly = typesOnly;
    return this;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    final StringBuilder builder = new StringBuilder();
    builder.append("SearchRequest(name=");
    builder.append(getName());
    builder.append(", scope=");
    builder.append(getScope());
    builder.append(", dereferenceAliasesPolicy=");
    builder.append(getDereferenceAliasesPolicy());
    builder.append(", sizeLimit=");
    builder.append(getSizeLimit());
    builder.append(", timeLimit=");
    builder.append(getTimeLimit());
    builder.append(", typesOnly=");
    builder.append(isTypesOnly());
    builder.append(", filter=");
    builder.append(getFilter());
    builder.append(", attributes=");
    builder.append(getAttributes());
    builder.append(", controls=");
    builder.append(getControls());
    builder.append(")");
    return builder.toString();
  }



  SearchRequest getThis()
  {
    return this;
  }
}
