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
 *      Copyright 2011 ForgeRock AS
 */

package org.forgerock.opendj.sync.filters;



import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldif.ChangeRecord;
import org.forgerock.opendj.sync.filters.FilterResult.Action;



/**
 * Factory methods for constructing common types of matching filters. Matchers
 * are logical filters which can be used to construct conditional
 * transformations. Furthermore, matchers implement boolean logic where
 * {@code true} is indicated by the filter action {@link Action#NEXT} and
 * {@code false} is indicated by the filter action {@link Action#STOP}.
 */
public final class Matchers
{
  private static final class AndMatcher implements Filter
  {
    private final Filter[] subFilters;



    private AndMatcher(final Filter[] subFilters)
    {
      this.subFilters = subFilters;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void close()
    {
      for (final Filter filter : subFilters)
      {
        filter.close();
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final AddRequest change)
    {
      for (final Filter subFilter : subFilters)
      {
        final FilterResult result = subFilter.filterChangeRecord(context,
            change);
        if (result.getAction() != Action.NEXT)
        {
          return result;
        }
      }
      return FilterResult.next();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final DeleteRequest change)
    {
      for (final Filter subFilter : subFilters)
      {
        final FilterResult result = subFilter.filterChangeRecord(context,
            change);
        if (result.getAction() != Action.NEXT)
        {
          return result;
        }
      }
      return FilterResult.next();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final ModifyDNRequest change)
    {
      for (final Filter subFilter : subFilters)
      {
        final FilterResult result = subFilter.filterChangeRecord(context,
            change);
        if (result.getAction() != Action.NEXT)
        {
          return result;
        }
      }
      return FilterResult.next();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final ModifyRequest change)
    {
      for (final Filter subFilter : subFilters)
      {
        final FilterResult result = subFilter.filterChangeRecord(context,
            change);
        if (result.getAction() != Action.NEXT)
        {
          return result;
        }
      }
      return FilterResult.next();
    }
  }



  private static final class ChangeInitiatorInScopeMatcher implements Filter
  {
    private final DN dn;
    private final SearchScope scope;



    private ChangeInitiatorInScopeMatcher(final DN dn, final SearchScope scope)
    {
      this.dn = dn;
      this.scope = scope;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void close()
    {
      // Nothing to do.
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final AddRequest change)
    {
      return matchesScope(change);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final DeleteRequest change)
    {
      return matchesScope(change);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final ModifyDNRequest change)
    {
      return matchesScope(change);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final ModifyRequest change)
    {
      return matchesScope(change);
    }



    private FilterResult matchesScope(final ChangeRecord change)
    {
      if (change.getName().isInScopeOf(dn, scope))
      {
        return FilterResult.next();
      }
      else
      {
        return FilterResult.stop();
      }
    }
  }



  private static final class ImpliesMatcher implements Filter
  {
    private final Filter condition;
    private final Filter transformation;



    private ImpliesMatcher(final Filter condition, final Filter transformation)
    {
      this.condition = condition;
      this.transformation = transformation;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void close()
    {
      condition.close();
      transformation.close();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final AddRequest change)
    {
      final FilterResult result = condition.filterChangeRecord(context, change);
      if (result.getAction() == Action.NEXT)
      {
        return transformation.filterChangeRecord(context, change);
      }
      else
      {
        return result;
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final DeleteRequest change)
    {
      final FilterResult result = condition.filterChangeRecord(context, change);
      if (result.getAction() == Action.NEXT)
      {
        return transformation.filterChangeRecord(context, change);
      }
      else
      {
        return result;
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final ModifyDNRequest change)
    {
      final FilterResult result = condition.filterChangeRecord(context, change);
      if (result.getAction() == Action.NEXT)
      {
        return transformation.filterChangeRecord(context, change);
      }
      else
      {
        return result;
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final ModifyRequest change)
    {
      final FilterResult result = condition.filterChangeRecord(context, change);
      if (result.getAction() == Action.NEXT)
      {
        return transformation.filterChangeRecord(context, change);
      }
      else
      {
        return result;
      }
    }
  }



  private static final class NotMatcher implements Filter
  {
    private final Filter subFilter;



    private NotMatcher(final Filter subFilter)
    {
      this.subFilter = subFilter;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void close()
    {
      subFilter.close();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final AddRequest change)
    {
      return transformResult(subFilter.filterChangeRecord(context, change));
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final DeleteRequest change)
    {
      return transformResult(subFilter.filterChangeRecord(context, change));
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final ModifyDNRequest change)
    {
      return transformResult(subFilter.filterChangeRecord(context, change));
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final ModifyRequest change)
    {
      return transformResult(subFilter.filterChangeRecord(context, change));
    }



    private FilterResult transformResult(final FilterResult result)
    {
      switch (result.getAction())
      {
      case NEXT:
        return FilterResult.stop();
      case STOP:
        return FilterResult.next();
      default:
        return result;
      }
    }
  }



  private static final class OrMatcher implements Filter
  {
    private final Filter[] subFilters;



    private OrMatcher(final Filter[] subFilters)
    {
      this.subFilters = subFilters;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void close()
    {
      for (final Filter filter : subFilters)
      {
        filter.close();
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final AddRequest change)
    {
      for (final Filter subFilter : subFilters)
      {
        final FilterResult result = subFilter.filterChangeRecord(context,
            change);
        if (result.getAction() != Action.STOP)
        {
          return result;
        }
      }
      return FilterResult.stop();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final DeleteRequest change)
    {
      for (final Filter subFilter : subFilters)
      {
        final FilterResult result = subFilter.filterChangeRecord(context,
            change);
        if (result.getAction() != Action.STOP)
        {
          return result;
        }
      }
      return FilterResult.stop();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final ModifyDNRequest change)
    {
      for (final Filter subFilter : subFilters)
      {
        final FilterResult result = subFilter.filterChangeRecord(context,
            change);
        if (result.getAction() != Action.STOP)
        {
          return result;
        }
      }
      return FilterResult.stop();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final ModifyRequest change)
    {
      for (final Filter subFilter : subFilters)
      {
        final FilterResult result = subFilter.filterChangeRecord(context,
            change);
        if (result.getAction() != Action.STOP)
        {
          return result;
        }
      }
      return FilterResult.stop();
    }
  }



  private static final class TargetInScopeMatcher implements Filter
  {
    private final DN dn;
    private final SearchScope scope;



    private TargetInScopeMatcher(final DN dn, final SearchScope scope)
    {
      this.dn = dn;
      this.scope = scope;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void close()
    {
      // Nothing to do.
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final AddRequest change)
    {
      return matchesScope(change);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final DeleteRequest change)
    {
      return matchesScope(change);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final ModifyDNRequest change)
    {
      return matchesScope(change);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final ModifyRequest change)
    {
      return matchesScope(change);
    }



    private FilterResult matchesScope(final ChangeRecord change)
    {
      if (change.getName().isInScopeOf(dn, scope))
      {
        return FilterResult.next();
      }
      else
      {
        return FilterResult.stop();
      }
    }
  }



  private static final Filter TRUE = new Filter()
  {

    @Override
    public void close()
    {
      // Nothing to do.
    }



    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final AddRequest change)
    {
      return FilterResult.next();
    }



    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final DeleteRequest change)
    {
      return FilterResult.next();
    }



    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final ModifyDNRequest change)
    {
      return FilterResult.next();
    }



    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final ModifyRequest change)
    {
      return FilterResult.next();
    }

  };

  private static final Filter FALSE = new Filter()
  {

    @Override
    public void close()
    {
      // Nothing to do.
    }



    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final AddRequest change)
    {
      return FilterResult.stop();
    }



    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final DeleteRequest change)
    {
      return FilterResult.stop();
    }



    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final ModifyDNRequest change)
    {
      return FilterResult.stop();
    }



    @Override
    public FilterResult filterChangeRecord(final ChangeRecordContext context,
        final ModifyRequest change)
    {
      return FilterResult.stop();
    }

  };



  /**
   * Returns a matcher which always evaluates to {@code false}.
   *
   * @return A matcher which always evaluates to {@code false}.
   */
  public static Filter alwaysFalse()
  {
    return FALSE;
  }



  /**
   * Returns a matcher which always evaluates to {@code true}.
   *
   * @return A matcher which always evaluates to {@code true}.
   */
  public static Filter alwaysTrue()
  {
    return TRUE;
  }



  /**
   * Returns a matcher which evaluates to {@code true} if and only if all of the
   * provided matchers evaluate to {@code true}. This method evaluates the
   * matchers in the order provided and stops as soon as one evaluates to
   * {@code false}.
   *
   * @param matchers
   *          The matchers whose results must all evaluate to {@code true} in
   *          order for the returned matcher to evaluate to {@code true}.
   * @return A matcher which evaluates to {@code true} if and only if all of the
   *         provided matchers evaluate to {@code true}.
   */
  public static Filter and(final Filter... matchers)
  {
    return new AndMatcher(matchers);
  }



  /**
   * Returns a matcher which will only evaluate a transformation if a condition
   * evaluates to {@code true}. This method can be used to construct conditional
   * if...then... style logic. Note that the returned matcher will evaluate to
   * {@code true} if the condition evaluated to {@code false}.
   *
   * @param condition
   *          The condition matcher which must evaluate to {@code true} in order
   *          for the transformation to be evaluated.
   * @param transformation
   *          The transformation which will only be evaluated if the condition
   *          evaluates to {@code true}.
   * @return A matcher which will only evaluate a transformation if a condition
   *         evaluates to {@code true}.
   */
  public static Filter implies(final Filter condition,
      final Filter transformation)
  {
    return new ImpliesMatcher(condition, transformation);
  }



  /**
   * Returns a matcher which evaluates to {@code true} if and only if the name
   * of the entity which performed the change exactly matches the provided DN.
   *
   * @param dn
   *          The name of the change initiator.
   * @return A matcher which evaluates to {@code true} if and only if the name
   *         of the entity which performed the change exactly matches the
   *         provided DN.
   */
  public static Filter initiatorIsEqualTo(final DN dn)
  {
    return new ChangeInitiatorInScopeMatcher(dn, SearchScope.BASE_OBJECT);
  }



  /**
   * Returns a matcher which evaluates to {@code true} if and only if the name
   * of the entity which performed the change matches the provided base DN and
   * search scope.
   *
   * @param baseDN
   *          The base DN.
   * @param scope
   *          The search scope.
   * @return A matcher which evaluates to {@code true} if and only if the name
   *         of the entity which performed the change matches the provided base
   *         DN and search scope.
   */
  public static Filter initiatorMatchesBaseAndScope(final DN baseDN,
      final SearchScope scope)
  {
    return new ChangeInitiatorInScopeMatcher(baseDN, scope);
  }



  /**
   * Returns a matcher which evaluates to {@code true} if the provided matcher
   * evaluates to {@code false}, and vice versa.
   *
   * @param matcher
   *          The matcher whose result is to be inverted.
   * @return A matcher which evaluates to {@code true} if the provided matcher
   *         evaluates to {@code false}, and vice versa.
   */
  public static Filter not(final Filter matcher)
  {
    return new NotMatcher(matcher);
  }



  /**
   * Returns a matcher which evaluates to {@code false} if and only if all of
   * the provided matchers evaluate to {@code false}. This method evaluates the
   * matchers in the order provided and stops as soon as one evaluates to
   * {@code true}.
   *
   * @param matchers
   *          The matchers whose results must all evaluate to {@code false} in
   *          order for the returned matcher to evaluate to {@code false}.
   * @return A matcher which evaluates to {@code false} if and only if all of
   *         the provided matchers evaluate to {@code false}.
   */
  public static Filter or(final Filter... matchers)
  {
    return new OrMatcher(matchers);
  }



  /**
   * Returns a matcher which evaluates to {@code true} if and only if the name
   * of the entry targeted by the change record matches the provided base DN and
   * search scope.
   *
   * @param baseDN
   *          The base DN.
   * @param scope
   *          The search scope.
   * @return A matcher which evaluates to {@code true} if and only if the name
   *         of the entry targeted by the change record matches the provided
   *         base DN and search scope.
   */
  public static Filter targetMatchesBaseAndScope(final DN baseDN,
      final SearchScope scope)
  {
    return new TargetInScopeMatcher(baseDN, scope);
  }



  private Matchers()
  {
    // Prevent instantiation.
  }

}
