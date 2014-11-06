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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2012-2014 ForgeRock AS
 */
package org.opends.server.api;

import java.util.Collection;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.opends.server.admin.std.server.VirtualAttributeCfg;
import org.opends.server.core.SearchOperation;
import org.opends.server.types.Attribute;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.VirtualAttributeRule;

/**
 * This class defines the set of methods and structures that must be
 * implemented by a Directory Server module that implements the
 * functionality required for one or more virtual attributes.
 *
 * @param  <T>  The type of configuration handled by this virtual
 *              attribute provider.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=true,
     mayInvoke=false)
public abstract class VirtualAttributeProvider
       <T extends VirtualAttributeCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * Initializes this virtual attribute based on the information in
   * the provided configuration entry.
   *
   * @param  configuration  The configuration to use to initialize
   *                        this virtual attribute provider.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in
   *                           the process of performing the
   *                           initialization.
   *
   * @throws  InitializationException  If a problem occurs during
   *                                   initialization that is not
   *                                   related to the server
   *                                   configuration.
   */
  public void initializeVirtualAttributeProvider(T configuration)
      throws ConfigException, InitializationException
  {
    // No initialization required
  }



  /**
   * Indicates whether the provided configuration is acceptable for
   * this virtual attribute provider.  It should be possible to call
   * this method on an uninitialized virtual attribute provider
   * instance in order to determine whether the virtual attribute
   * provider would be able to use the provided configuration.
   *
   * @param  configuration        The virtual attribute provider
   *                              configuration for which to make the
   *                              determination.
   * @param  unacceptableReasons  A list that may be used to hold the
   *                              reasons that the provided
   *                              configuration is not acceptable.
   *
   * @return  {@code true} if the provided configuration is acceptable
   *          for this virtual attribute provider, or {@code false} if
   *          not.
   */
  public boolean isConfigurationAcceptable(
                      VirtualAttributeCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    // This default implementation does not perform any special validation.
    // It should be overridden by virtual attribute provider implementations
    // that wish to perform more detailed validation.
    return true;
  }



  /**
   * Performs any finalization that may be necessary whenever this
   * virtual attribute provider is taken out of service.
   */
  public void finalizeVirtualAttributeProvider()
  {
    // No implementation required by default.
  }



  /**
   * Indicates whether this virtual attribute provider may generate
   * multiple values.
   *
   * @return  {@code true} if this virtual attribute provider may
   *          generate multiple values, or {@code false} if not.
   */
  public abstract boolean isMultiValued();



  /**
   * Generates an unmodifiable attribute with the values for the provided entry.
   *
   * @param entry
   *          The entry for which the values are to be generated.
   * @param rule
   *          The virtual attribute rule which defines the constraints
   *          for the virtual attribute.
   * @return The unmodifiable attribute with the values generated for the
   *         provided entry. It may be empty, but it must not be {@code null}.
   */
  public abstract Attribute getValues(Entry entry, VirtualAttributeRule rule);



  /**
   * Indicates whether this virtual attribute provider will generate
   * at least one value for the provided entry.
   *
   * @param  entry  The entry for which to make the determination.
   * @param  rule   The virtual attribute rule which defines the
   *                constraints for the virtual attribute.
   *
   * @return  {@code true} if this virtual attribute provider will
   *          generate at least one value for the provided entry, or
   *          {@code false} if not.
   */
  public boolean hasValue(Entry entry, VirtualAttributeRule rule)
  {
    return !getValues(entry, rule).isEmpty();
  }



  /**
   * Indicates whether this virtual attribute provider will generate
   * the provided value.
   *
   * @param  entry  The entry for which to make the determination.
   * @param  rule   The virtual attribute rule which defines the
   *                constraints for the virtual attribute.
   * @param  value  The value for which to make the determination.
   *
   * @return  {@code true} if this virtual attribute provider will
   *          generate the specified value for the provided entry, or
   *          {@code false} if not.
   */
  public boolean hasValue(Entry entry, VirtualAttributeRule rule, ByteString value)
  {
    return getValues(entry, rule).contains(value);
  }

  /**
   * Indicates whether this virtual attribute provider matches the assertion
   * value.
   *
   * @param entry
   *          The entry for which to make the determination.
   * @param rule
   *          The virtual attribute rule which defines the constraints for the
   *          virtual attribute.
   * @param assertionValue
   *          The assertion value for which to make the determination.
   * @return {@code true} if this virtual attribute provider matches the
   *         specified assertion value for the provided entry, or {@code false}
   *         if not.
   */
  public ConditionResult matchesEqualityAssertion(Entry entry,
      VirtualAttributeRule rule, ByteString assertionValue)
  {
    return getValues(entry, rule).matchesEqualityAssertion(assertionValue);
  }


  /**
   * Indicates whether this virtual attribute provider will generate
   * all of the values in the provided collection.
   *
   * @param  entry   The entry for which to make the determination.
   * @param  rule    The virtual attribute rule which defines the
   *                 constraints for the virtual attribute.
   * @param  values  The set of values for which to make the
   *                 determination.
   *
   * @return  {@code true} if this attribute provider will generate
   *          all of the values in the provided collection, or
   *          {@code false} if it will not generate at least one of
   *          them.
   */
  public boolean hasAllValues(Entry entry, VirtualAttributeRule rule,
                              Collection<ByteString> values)
  {
    return getValues(entry, rule).containsAll(values);
  }



  /**
   * Indicates whether this virtual attribute provider will generate
   * any value which matches the provided substring.
   *
   * @param  entry       The entry for which to make the
   *                     determination.
   * @param  rule        The virtual attribute rule which defines the
   *                     constraints for the virtual attribute.
   * @param  subInitial  The subInitial component to use in the
   *                     determination.
   * @param  subAny      The subAny components to use in the
   *                     determination.
   * @param  subFinal    The subFinal component to use in the
   *                     determination.
   *
   * @return  {@code UNDEFINED} if this attribute does not have a
   *          substring matching rule, {@code TRUE} if at least one
   *          value matches the provided substring, or {@code FALSE}
   *          otherwise.
   */
  public ConditionResult matchesSubstring(Entry entry,
                                          VirtualAttributeRule rule,
                                          ByteString subInitial,
                                          List<ByteString> subAny,
                                          ByteString subFinal)
  {
    MatchingRule matchingRule = rule.getAttributeType().getSubstringMatchingRule();
    if (matchingRule == null)
    {
      return ConditionResult.UNDEFINED;
    }

    Assertion assertion;
    try
    {
      assertion = matchingRule.getSubstringAssertion(subInitial, subAny, subFinal);
    }
    catch(DecodeException e) {
      logger.traceException(e);
      return ConditionResult.UNDEFINED;
    }

    ConditionResult result = ConditionResult.FALSE;
    for (ByteString value : getValues(entry, rule))
    {
      try
      {
        if (assertion.matches(matchingRule.normalizeAttributeValue(value)).toBoolean())
        {
          return ConditionResult.TRUE;
        }
      }
      catch (Exception e)
      {
        logger.traceException(e);

        // We couldn't normalize one of the attribute values.
        // We will return "undefined" if we can't find a definite match
        result = ConditionResult.UNDEFINED;
      }
    }

    return result;
  }


  /**
   * Indicates whether this virtual attribute provider will generate any value
   * for the provided entry that is greater than or equal to the given value.
   *
   * @param entry
   *          The entry for which to make the determination.
   * @param rule
   *          The virtual attribute rule which defines the constraints for the
   *          virtual attribute.
   * @param assertionValue
   *          The assertion value for which to make the determination.
   * @return {@code UNDEFINED} if the associated attribute type does not have an
   *         ordering matching rule, {@code TRUE} if at least one of the
   *         generated values will be greater than or equal to the specified
   *         assertion value, or {@code FALSE} if none of the generated values
   *         will be greater than or equal to the specified value.
   */
  public ConditionResult greaterThanOrEqualTo(Entry entry,
                              VirtualAttributeRule rule,
                              ByteString assertionValue)
  {
    MatchingRule matchingRule = rule.getAttributeType().getOrderingMatchingRule();
    if (matchingRule == null)
    {
      return ConditionResult.UNDEFINED;
    }

    Assertion assertion = null;
    try
    {
      assertion = matchingRule.getGreaterOrEqualAssertion(assertionValue);
    }
    catch (Exception e)
    {
      logger.traceException(e);
      return ConditionResult.UNDEFINED;
    }

    ConditionResult result = ConditionResult.FALSE;
    for (ByteString v : getValues(entry, rule))
    {
      try
      {
        if (assertion.matches(matchingRule.normalizeAttributeValue(v)).toBoolean())
        {
          return ConditionResult.TRUE;
        }
      }
      catch (Exception e)
      {
        logger.traceException(e);
        // We couldn't normalize one of the attribute values.
        // We will return "undefined" if we can't find a definite match
        result = ConditionResult.UNDEFINED;
      }
    }

    return result;
  }



  /**
   * Indicates whether this virtual attribute provider will generate any value
   * for the provided entry that is less than or equal to the given value.
   *
   * @param entry
   *          The entry for which to make the determination.
   * @param rule
   *          The virtual attribute rule which defines the constraints for the
   *          virtual attribute.
   * @param assertionValue
   *          The assertion value for which to make the determination.
   * @return {@code UNDEFINED} if the associated attribute type does not have an
   *         ordering matching rule, {@code TRUE} if at least one of the
   *         generated values will be less than or equal to the specified
   *         assertion value, or {@code FALSE} if none of the generated values
   *         will be greater than or equal to the specified value.
   */
  public ConditionResult lessThanOrEqualTo(Entry entry,
                              VirtualAttributeRule rule,
                              ByteString assertionValue)
  {
    MatchingRule matchingRule = rule.getAttributeType().getOrderingMatchingRule();
    if (matchingRule == null)
    {
      return ConditionResult.UNDEFINED;
    }

    Assertion assertion = null;
    try
    {
      assertion = matchingRule.getLessOrEqualAssertion(assertionValue);
    }
    catch (Exception e)
    {
      logger.traceException(e);
      return ConditionResult.UNDEFINED;
    }

    ConditionResult result = ConditionResult.FALSE;
    for (ByteString v : getValues(entry, rule))
    {
      try
      {
        if (assertion.matches(matchingRule.normalizeAttributeValue(v)).toBoolean())
        {
          return ConditionResult.TRUE;
        }
      }
      catch (Exception e)
      {
        logger.traceException(e);

        // We couldn't normalize one of the attribute values.
        // We will return "undefined" if we can't find a definite match
        result = ConditionResult.UNDEFINED;
      }
    }

    return result;
  }



  /**
   * Indicates whether this virtual attribute provider will generate
   * any value for the provided entry that is approximately equal to
   * the given value.
   *
   * @param  entry  The entry for which to make the determination.
   * @param  rule   The virtual attribute rule which defines the
   *                constraints for the virtual attribute.
   * @param  assertionValue
   *          The assertion value for which to make the determination.
   *
   * @return  {@code UNDEFINED} if the associated attribute type does
   *          not have an approximate matching rule, {@code TRUE} if at
   *          least one of the generated values will be approximately
   *          equal to the specified value, or {@code FALSE} if none
   *          of the generated values will be approximately equal to
   *          the specified assertion value.
   */
  public ConditionResult approximatelyEqualTo(Entry entry,
                              VirtualAttributeRule rule,
                              ByteString assertionValue)
  {
    MatchingRule matchingRule = rule.getAttributeType().getApproximateMatchingRule();
    if (matchingRule == null)
    {
      return ConditionResult.UNDEFINED;
    }

    Assertion assertion = null;
    try
    {
      assertion = matchingRule.getAssertion(assertionValue);
    }
    catch (Exception e)
    {
      logger.traceException(e);
      return ConditionResult.UNDEFINED;
    }

    ConditionResult result = ConditionResult.FALSE;
    for (ByteString v : getValues(entry, rule))
    {
      try
      {
        if  (assertion.matches(matchingRule.normalizeAttributeValue(v)).toBoolean())
        {
          return ConditionResult.TRUE;
        }
      }
      catch (Exception e)
      {
        logger.traceException(e);
        // We couldn't normalize one of the attribute values.
        // We will return "undefined" if we can't find a definite match
        result = ConditionResult.UNDEFINED;
      }
    }
    return result;
  }



  /**
   * Indicates whether this attribute may be included in search
   * filters as part of the criteria for locating entries.
   *
   * @param rule             The virtual attribute rule which defines
   *                         the constraints for the virtual
   *                         attribute.
   * @param searchOperation  The search operation for which to make
   *                         the determination.
   * @param isPreIndexed     Indicates if we expect the search on the virtual
   *                         attribute to be faster than an index search.
   * @return {@code true} if this attribute may be included in search
   * filters, or {@code false} if not.
   */
  public abstract boolean isSearchable(VirtualAttributeRule rule,
                                       SearchOperation searchOperation,
                                       boolean isPreIndexed);



  /**
   * Processes the provided search operation in which the search
   * criteria includes an operation targeted at this virtual
   * attribute.  This method should only be called if
   * {@code isSearchable} returns true and it is not possible to
   * construct a manageable candidate list by processing other
   * elements of the search criteria.
   *
   * @param  rule             The virtual attribute rule which defines
   *                          the constraints for the virtual
   *                          attribute.
   * @param  searchOperation  The search operation to be processed.
   */
  public abstract void processSearch(VirtualAttributeRule rule,
                                     SearchOperation searchOperation);
}

