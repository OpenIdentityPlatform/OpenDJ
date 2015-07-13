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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2012-2015 ForgeRock AS
 */
package org.opends.server.schema;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.schema.CoreSchema;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.opendj.ldap.schema.Schema;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.CollationMatchingRuleCfg;
import org.opends.server.api.MatchingRuleFactory;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.opends.server.util.CollectionUtils;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.messages.SchemaMessages.*;

/**
 * This class is a factory class for Collation matching rules. It
 * creates different matching rules based on the configuration entries.
 */
public final class CollationMatchingRuleFactory extends
    MatchingRuleFactory<CollationMatchingRuleCfg> implements
    ConfigurationChangeListener<CollationMatchingRuleCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** Stores the list of available locales on this JVM. */
  private static final Set<Locale> supportedLocales = CollectionUtils.newHashSet(Locale.getAvailableLocales());

  /** Current Configuration. */
  private CollationMatchingRuleCfg currentConfig;
  /** Map of OID and the Matching Rule. */
  private final Map<String, MatchingRule> matchingRules = new HashMap<>();


  /** Creates a new instance of CollationMatchingRuleFactory. */
  public CollationMatchingRuleFactory()
  {
    super();
  }

  /** {@inheritDoc} */
  @Override
  public final Collection<org.forgerock.opendj.ldap.schema.MatchingRule> getMatchingRules()
  {
    return Collections.unmodifiableCollection(matchingRules.values());
  }

  /**
   * Adds a new mapping of OID and MatchingRule.
   *
   * @param oid
   *          OID of the matching rule
   * @param matchingRule
   *          instance of a MatchingRule.
   */
  private void addMatchingRule(String oid, MatchingRule matchingRule)
  {
    matchingRules.put(oid, matchingRule);
  }

  /**
   * Clears the Map containing matching Rules.
   */
  private void resetRules()
  {
    matchingRules.clear();
  }

  /** {@inheritDoc} */
  @Override
  public void initializeMatchingRule(CollationMatchingRuleCfg configuration)
      throws ConfigException, InitializationException
  {
    final Schema coreSchema = CoreSchema.getInstance();
    for (String collation : configuration.getCollation())
    {
      CollationMapper mapper = new CollationMapper(collation);

      String nOID = mapper.getNumericOID();
      String languageTag = mapper.getLanguageTag();
      if (nOID == null || languageTag == null)
      {
        logger.error(WARN_ATTR_INVALID_COLLATION_MATCHING_RULE_FORMAT, collation);
        continue;
      }

      Locale locale = getLocale(languageTag);
      if (locale != null)
      {
        try
        {
          final int[] numericSuffixes = { 1, 2, 3, 4, 5, 6 };
          for (int suffix : numericSuffixes)
          {
            final String oid =  nOID + "." + suffix;
            addMatchingRule(oid, coreSchema.getMatchingRule(oid));
          }
          // the default (equality) matching rule
          addMatchingRule(nOID, coreSchema.getMatchingRule(nOID));
        }
        catch (Exception e)
        {
          logger.error(LocalizableMessage.raw("Error when adding a collation matching rule with oid %s, tag %s: %s",
              nOID, languageTag, e.getMessage()));
        }
      }
      else
      {
        // This locale is not supported by JVM.
        logger.error(WARN_ATTR_INVALID_COLLATION_MATCHING_RULE_LOCALE,
                collation, configuration.dn().toString(), languageTag);
      }
    }

    // Save this configuration.
    currentConfig = configuration;

    // Register for change events.
    currentConfig.addCollationChangeListener(this);
  }

  /** {@inheritDoc} */
  @Override
  public void finalizeMatchingRule()
  {
    // De-register the listener.
    currentConfig.removeCollationChangeListener(this);
  }

  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationChange(
      CollationMatchingRuleCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    if (!configuration.isEnabled()
        || currentConfig.isEnabled() != configuration.isEnabled())
    {
      // Don't do anything if:
      // 1. The configuration is disabled.
      // 2. There is a change in the enable status
      // i.e. (disable->enable or enable->disable). In this case, the
      // ConfigManager will have already created the new Factory object.
      return ccr;
    }

    // Since we have come here it means that this Factory is enabled and
    // there is a change in the CollationMatchingRuleFactory's
    // configuration.
    // Deregister all the Matching Rule corresponding to this factory.
    for (MatchingRule rule : getMatchingRules())
    {
      DirectoryServer.deregisterMatchingRule(rule);
    }

    // Clear the associated matching rules.
    resetRules();

    final Schema coreSchema = CoreSchema.getInstance();
    for (String collation : configuration.getCollation())
    {
      // validation has already been performed in isConfigurationChangeAcceptable()
      CollationMapper mapper = new CollationMapper(collation);
      String nOID = mapper.getNumericOID();
      addMatchingRule(nOID, coreSchema.getMatchingRule(nOID));
    }

    try
    {
      for (MatchingRule matchingRule : getMatchingRules())
      {
        DirectoryServer.registerMatchingRule(matchingRule, false);
      }
    }
    catch (DirectoryException de)
    {
      LocalizableMessage message =
          WARN_CONFIG_SCHEMA_MR_CONFLICTING_MR.get(configuration.dn(), de.getMessageObject());
      ccr.setAdminActionRequired(true);
      ccr.addMessage(message);
    }
    currentConfig = configuration;
    return ccr;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationChangeAcceptable(
      CollationMatchingRuleCfg configuration,
      List<LocalizableMessage> unacceptableReasons)
  {
    boolean configAcceptable = true;

    // If the new configuration disables this factory, don't do
    // anything.
    if (!configuration.isEnabled())
    {
      return configAcceptable;
    }

    // If it comes here we don't need to verify MatchingRuleType; it
    // should be okay as its syntax is verified by the admin framework.
    // Iterate over the collations and verify if the format is okay.
    // Also, verify if the locale is allowed by the JVM.
    for (String collation : configuration.getCollation())
    {
      CollationMapper mapper = new CollationMapper(collation);

      String nOID = mapper.getNumericOID();
      String languageTag = mapper.getLanguageTag();
      if (nOID == null || languageTag == null)
      {
        configAcceptable = false;
        LocalizableMessage msg = WARN_ATTR_INVALID_COLLATION_MATCHING_RULE_FORMAT.get(collation);
        unacceptableReasons.add(msg);
        continue;
      }

      Locale locale = getLocale(languageTag);
      if (locale == null)
      {
        LocalizableMessage msg = WARN_ATTR_INVALID_COLLATION_MATCHING_RULE_LOCALE.get(
                collation, configuration.dn(), languageTag);
        unacceptableReasons.add(msg);
        configAcceptable = false;
        continue;
      }
    }
    return configAcceptable;
  }


  /**
   * Verifies if the locale is supported by the JVM.
   *
   * @param lTag
   *          The language tag specified in the configuration.
   * @return Locale The locale corresponding to the languageTag.
   */
  private Locale getLocale(String lTag)
  {
    // Separates the language and the country from the locale.
    Locale locale;

    int countryIndex = lTag.indexOf("-");
    int variantIndex = lTag.lastIndexOf("-");

    if (countryIndex > 0)
    {
      String lang = lTag.substring(0, countryIndex);
      String country;

      if (variantIndex > countryIndex)
      {
        country = lTag.substring(countryIndex + 1, variantIndex);
        String variant = lTag.substring(variantIndex + 1, lTag.length());
        locale = new Locale(lang, country, variant);
      }
      else
      {
        country = lTag.substring(countryIndex + 1, lTag.length());
        locale = new Locale(lang, country);
      }
    }
    else
    {
      locale = new Locale(lTag);
    }

    if (!supportedLocales.contains(locale))
    {
      // This locale is not supported by this JVM.
      locale = null;
    }
    return locale;
  }

  /**
   * A utility class for extracting the OID and Language Tag from the
   * configuration entry.
   */
  private final class CollationMapper
  {
    /** OID of the collation rule. */
    private String oid;

    /** Language Tag. */
    private String lTag;

    /**
     * Creates a new instance of CollationMapper.
     *
     * @param collation
     *          The collation text in the LOCALE:OID format.
     */
    private CollationMapper(String collation)
    {
      int index = collation.indexOf(":");
      if (index > 0)
      {
        oid = collation.substring(index + 1, collation.length());
        lTag = collation.substring(0, index);
      }
    }

    /**
     * Returns the OID part of the collation text.
     *
     * @return OID part of the collation text.
     */
    private String getNumericOID()
    {
      return oid;
    }

    /**
     * Returns the language Tag of collation text.
     *
     * @return Language Tag part of the collation text.
     */
    private String getLanguageTag()
    {
      return lTag;
    }
  }
}
