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


package org.opends.server.schema;

import java.text.CollationKey;
import java.text.Collator;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import org.opends.messages.Message;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.meta.CollationMatchingRuleCfgDefn.
        MatchingRuleType;
import org.opends.server.api.MatchingRuleFactory;
import org.opends.server.admin.std.server.CollationMatchingRuleCfg;
import org.opends.server.api.ExtensibleMatchingRule;
import org.opends.server.api.MatchingRule;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.ByteString;
import org.opends.server.types.ConditionResult;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;

import org.opends.server.types.ResultCode;
import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.messages.SchemaMessages.*;
import static org.opends.messages.CoreMessages.*;
import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class is a factory class for Collation matching rules. It creates
 * different matching rules based on the configuration entries.
 */
public final class CollationMatchingRuleFactory
        extends MatchingRuleFactory<CollationMatchingRuleCfg>
        implements ConfigurationChangeListener<CollationMatchingRuleCfg>
{

  //Whether equality matching rules are enabled.
  private boolean equalityMatchingRuleType;

  //Whether less-than matching rules are enabled.
  private boolean lessThanMatchingRuleType;

  //Whether less-than-equal-to matching rules are enabled.
  private boolean lessThanEqualToMatchingRuleType;

  //Whether less-than-equal-to matching rules are enabled.
  private boolean greaterThanMatchingRuleType;

  //Whether greater-than matching rules are enabled.
  private boolean greaterThanEqualToMatchingRuleType;

  //Whether greater-than-equal-to matching rules are enabled.
  private boolean substringMatchingRuleType;

  //Stores the list of available locales on this JVM.
  private static final Set<Locale> supportedLocales;

  //Current Configuration.
  private CollationMatchingRuleCfg currentConfig;

  //Map of OID and the Matching Rule.
  private  final Map<String, MatchingRule> matchingRules;


  static
  {
    supportedLocales = new HashSet<Locale>();
    for(Locale l:Locale.getAvailableLocales())
    {
      supportedLocales.add(l);
    }
  }


/**
  * Creates a new instance of CollationMatchingRuleFactory.
 */
  public CollationMatchingRuleFactory()
  {
    //Initialize the matchingRules.
    matchingRules = new HashMap<String,MatchingRule>();
  }



  /**
  * {@inheritDoc}
  */
  @Override
  public final Collection<MatchingRule> getMatchingRules()
  {
    return Collections.unmodifiableCollection(matchingRules.values());
  }



 /**
  * Adds a new mapping of OID and MatchingRule.
  *
  * @param oid OID of the matching rule
  * @param matchingRule instance of a MatchingRule.
  */
  private final void addMatchingRule(String oid,
          MatchingRule matchingRule)
  {
     matchingRules.put(oid, matchingRule);
  }



  /**
   * Returns the Matching rule for the specified OID.
   *
   * @param oid OID of the matching rule to be searched.
   * @return  MatchingRule corresponding to an OID.
   */
  private final MatchingRule getMatchingRule(String oid)
  {
    return matchingRules.get(oid);
  }




  /**
   * Clears the Map containing matching Rules.
   */
  private void resetRules()
  {
    matchingRules.clear();
  }



  /**
  * Reads the configuration and initializes matching rule types.
  *
  * @param  ruleTypes  The Set containing allowed matching rule types.
  */
  private void initializeMatchingRuleTypes(SortedSet<MatchingRuleType>
          ruleTypes)
  {
    for(MatchingRuleType type:ruleTypes)
    {
      switch(type)
      {
        case EQUALITY:
          equalityMatchingRuleType = true;
          break;
        case LESS_THAN:
          lessThanMatchingRuleType = true;
          break;
        case LESS_THAN_OR_EQUAL_TO:
          lessThanEqualToMatchingRuleType = true;
          break;
        case GREATER_THAN:
          greaterThanMatchingRuleType = true;
          break;
        case GREATER_THAN_OR_EQUAL_TO:
          greaterThanEqualToMatchingRuleType = true;
          break;
        case SUBSTRING:
          substringMatchingRuleType = true;
          break;
        default:
          //No default values allowed.
      }
    }
  }



 /**
  * Creates a new Collator instance.
  *
  * @param locale Locale for the collator
  * @return Returns a new Collator instance
  */
  private Collator createCollator(Locale locale)
  {
    Collator collator = Collator.getInstance(locale);
    collator.setStrength(Collator.PRIMARY);
    collator.setDecomposition(Collator.FULL_DECOMPOSITION);
    return collator;
  }



 /**
  * {@inheritDoc}
  */
  @Override
  public void initializeMatchingRule(CollationMatchingRuleCfg configuration)
  throws ConfigException, InitializationException
  {
    initializeMatchingRuleTypes(configuration.getMatchingRuleType());
    for(String collation:configuration.getCollation())
    {
      CollationMapper mapper = new CollationMapper(collation);

      String nOID = mapper.getNumericOID();
      String languageTag = mapper.getLanguageTag();
      if(nOID==null || languageTag==null)
      {
        Message msg =
                WARN_ATTR_INVALID_COLLATION_MATCHING_RULE_FORMAT.
                get(collation);
        logError(msg);
        continue;
      }

      Locale locale = getLocale(languageTag);
      if(locale!=null)
      {
        createLessThanMatchingRule(mapper,locale);
        createLessThanOrEqualToMatchingRule(mapper,locale);
        createEqualityMatchingRule(mapper,locale);
        createGreaterThanOrEqualToMatchingRule(mapper,locale);
        createGreaterThanMatchingRule(mapper,locale);
        createSubstringMatchingRule(mapper,locale);
      }
      else
      {
        //This locale is not supported by JVM.
        Message msg =
              WARN_ATTR_INVALID_COLLATION_MATCHING_RULE_LOCALE.
              get(collation,configuration.dn().toNormalizedString(),
                  languageTag);

        logError(msg);
      }
    }
    //Save this configuration.
    currentConfig = configuration;
    //Register for change events.
    currentConfig.addCollationChangeListener(this);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void finalizeMatchingRule()
  {
    //De-register the listener.
    currentConfig.removeCollationChangeListener(this);
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
                                 CollationMatchingRuleCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();

    if(!configuration.isEnabled() ||
            currentConfig.isEnabled()!=configuration.isEnabled())
    {
      //Don't do anything if:
      // 1. The configuration is disabled.
      // 2. There is a change in the enable status
      //  i.e. (disable->enable or enable->disable). In this case, the
      //     ConfigManager will have already created the new Factory object.
      return new ConfigChangeResult(resultCode,
              adminActionRequired, messages);
    }

    //Since we have come here it means that this Factory is enabled and
    //there is a change in the CollationMatchingRuleFactory's configuration.
    // Deregister all the Matching Rule corresponding to this factory..
    for(MatchingRule rule: getMatchingRules())
    {
      DirectoryServer.deregisterMatchingRule(rule);
    }
    //Clear the associated matching rules.
    resetRules();

    initializeMatchingRuleTypes(configuration.getMatchingRuleType());
    for(String collation:configuration.getCollation())
    {
      CollationMapper mapper = new CollationMapper(collation);
      String nOID = mapper.getNumericOID();
      String languageTag = mapper.getLanguageTag();
      Locale locale = getLocale(languageTag);
      createLessThanMatchingRule(mapper,locale);
      createLessThanOrEqualToMatchingRule(mapper,locale);
      createEqualityMatchingRule(mapper,locale);
      createGreaterThanOrEqualToMatchingRule(mapper,locale);
      createGreaterThanMatchingRule(mapper,locale);
      createSubstringMatchingRule(mapper,locale);
    }

    try
    {
      for(MatchingRule matchingRule: getMatchingRules())
      {
        DirectoryServer.registerMatchingRule(matchingRule, false);
      }
    }
    catch (DirectoryException de)
    {
        Message message = WARN_CONFIG_SCHEMA_MR_CONFLICTING_MR.get(
          String.valueOf(configuration.dn()), de.getMessageObject());
        adminActionRequired = true;
        messages.add(message);
    }
    currentConfig = configuration;
    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
          CollationMatchingRuleCfg configuration,
                      List<Message> unacceptableReasons)
  {
    boolean configAcceptable = true;

    //If the new configuration disables this factory, don't do anything.
    if(!configuration.isEnabled())
    {
      return configAcceptable;
    }


    //If it comes here we don't need to verify MatchingRuleType; it should be
    //okay as its syntax is verified by the admin framework. Iterate over the
    //collations and verify if the format is okay. Also, verify if the
    //locale is allowed by the JVM.
    for(String collation:configuration.getCollation())
    {
      CollationMapper mapper = new CollationMapper(collation);

      String nOID = mapper.getNumericOID();
      String languageTag = mapper.getLanguageTag();
      if(nOID==null || languageTag==null)
      {
        configAcceptable = false;
        Message msg =
                WARN_ATTR_INVALID_COLLATION_MATCHING_RULE_FORMAT.
                get(collation);
        unacceptableReasons.add(msg);
        continue;
      }

      Locale locale = getLocale(languageTag);
      if(locale==null)
      {
        Message msg =
              WARN_ATTR_INVALID_COLLATION_MATCHING_RULE_LOCALE.
              get(collation,configuration.dn().toNormalizedString(),
                  languageTag);
        unacceptableReasons.add(msg);
        configAcceptable = false;
        continue;
      }
    }
    return configAcceptable;
  }



  /**
  * Creates Less-than Matching Rule.
  *
  * @param mapper CollationMapper containing OID and the language Tag.
  * @param locale  Locale value
  */
  private void createLessThanMatchingRule(CollationMapper mapper,Locale locale)
  {
    if(!lessThanMatchingRuleType)
      return;

    String oid = mapper.getNumericOID()+".1";
    String lTag = mapper.getLanguageTag();

    Collection<String> names = new HashSet<String>();
    MatchingRule matchingRule = getMatchingRule(oid);
    if(matchingRule!=null)
    {
      for(String name: matchingRule.getAllNames())
      {
        names.add(name);
      }
    }

    names.add(lTag+".lt");
    names.add(lTag + ".1");

    matchingRule = new CollationLessThanMatchingRule(oid, names, locale);
    addMatchingRule(oid, matchingRule);
  }



 /**
  * Creates Less-Than-Equal-To Matching Rule.
  *
  * @param mapper CollationMapper containing OID and the language Tag.
  * @param locale  Locale value
  */
  private void createLessThanOrEqualToMatchingRule(CollationMapper mapper,
          Locale locale)
  {
    if(!lessThanEqualToMatchingRuleType)
      return;

    String oid = mapper.getNumericOID()+".2";
    String lTag = mapper.getLanguageTag();

    Collection<String> names = new HashSet<String>();
    MatchingRule matchingRule = getMatchingRule(oid);
    if(matchingRule!=null)
    {
      for(String name: matchingRule.getAllNames())
      {
        names.add(name);
      }
    }

    names.add(lTag+".lte");
    names.add(lTag + ".2");

    matchingRule =
            new CollationLessThanOrEqualToMatchingRule(oid,names,locale);
    addMatchingRule(oid, matchingRule);
  }



 /**
  * Creates Equality Matching Rule.
  *
  * @param mapper CollationMapper containing OID and the language Tag.
  * @param locale  Locale value
  */
  private void createEqualityMatchingRule(CollationMapper mapper,Locale locale)
  {
    if(!equalityMatchingRuleType)
      return;
    //Register the default OID as equality matching rule.
    String lTag = mapper.getLanguageTag();
    String nOID = mapper.getNumericOID();

    MatchingRule matchingRule = getMatchingRule(nOID);
    Collection<String> names = new HashSet<String>();
    if(matchingRule!=null)
    {
      for(String name: matchingRule.getAllNames())
      {
        names.add(name);
      }
    }

    names.add(lTag);
    matchingRule =
          new CollationEqualityMatchingRule(nOID,names,locale);
    addMatchingRule(nOID, matchingRule);

    // Register OID.3 as the equality matching rule.
    String OID = mapper.getNumericOID() + ".3";
    MatchingRule equalityMatchingRule = getMatchingRule(OID);
    Collection<String> equalityNames = new HashSet<String>();
    if(equalityMatchingRule!=null)
    {
      for(String name: equalityMatchingRule.getAllNames())
      {
        equalityNames.add(name);
      }
    }

    equalityNames.add(lTag+".eq");
    equalityNames.add(lTag+".3");

    equalityMatchingRule =
          new CollationEqualityMatchingRule(OID,equalityNames,locale);
    addMatchingRule(OID, equalityMatchingRule);
  }



  /**
  * Creates Greater-than-equal-to Matching Rule.
  *
  * @param mapper CollationMapper containing OID and the language Tag.
  * @param locale  Locale value
  */
  private void createGreaterThanOrEqualToMatchingRule(CollationMapper mapper,
          Locale locale)
  {
    if(!greaterThanEqualToMatchingRuleType)
      return;

    String oid = mapper.getNumericOID()+".4";
    String lTag = mapper.getLanguageTag();

    Collection<String> names = new HashSet<String>();
    MatchingRule matchingRule = getMatchingRule(oid);
    if(matchingRule!=null)
    {
      for(String name: matchingRule.getAllNames())
      {
        names.add(name);
      }
    }

    names.add(lTag+".gte");
    names.add(lTag + ".4");
    matchingRule =
          new CollationGreaterThanOrEqualToMatchingRule(oid,names,locale);
    addMatchingRule(oid, matchingRule);
  }



 /**
  * Creates Greater-than Matching Rule.
  *
  * @param mapper CollationMapper containing OID and the language Tag.
  * @param locale  Locale value
  */
  private void createGreaterThanMatchingRule(CollationMapper mapper,
          Locale locale)
  {
    if(!greaterThanMatchingRuleType)
      return;

    String oid = mapper.getNumericOID()+".5";
    String lTag = mapper.getLanguageTag();

    Collection<String> names = new HashSet<String>();
    MatchingRule matchingRule = getMatchingRule(oid);
    if(matchingRule!=null)
    {
      for(String name: matchingRule.getAllNames())
      {
        names.add(name);
      }
    }

    names.add(lTag+".gt");
    names.add(lTag + ".5");
    matchingRule =
          new CollationGreaterThanMatchingRule(oid,names,locale);
    addMatchingRule(oid, matchingRule);
  }



 /**
  * Creates substring Matching Rule.
  *
  * @param mapper CollationMapper containing OID and the language Tag.
  * @param locale  Locale value
  */
  private void createSubstringMatchingRule(CollationMapper mapper,Locale locale)
  {
    if(!substringMatchingRuleType)
      return;

    String oid = mapper.getNumericOID()+".6";
    String lTag = mapper.getLanguageTag();

    Collection<String> names = new HashSet<String>();
    MatchingRule matchingRule = getMatchingRule(oid);
    if(matchingRule!=null)
    {
      for(String name: matchingRule.getAllNames())
      {
        names.add(name);
      }
    }
    names.add(lTag+".sub");
    names.add(lTag + ".6");
    matchingRule =
          new CollationSubstringMatchingRule(oid,names,locale);
    addMatchingRule(oid, matchingRule);
  }




 /**
  * Verifies if the locale is supported by the JVM.
  *
  * @param  lTag  The language tag specified in the configuration.
  * @return  Locale The locale correspoding to the languageTag.
  */
  private Locale getLocale(String lTag)
  {
    //Separates the language and the country from the locale.
    Locale locale;
    String lang = null;
    String country = null;
    String variant = null;

    int countryIndex = lTag.indexOf("-");
    int variantIndex = lTag.lastIndexOf("-");

    if(countryIndex > 0)
    {
      lang = lTag.substring(0,countryIndex);

      if(variantIndex>countryIndex)
      {
        country = lTag.substring(countryIndex+1,variantIndex);
        variant = lTag.substring(variantIndex+1,lTag.length());
        locale = new Locale(lang,country,variant);
      }
      else
      {
        country = lTag.substring(countryIndex+1,lTag.length());
        locale = new Locale(lang,country);
      }
    }
    else
    {
      lang = lTag;
      locale = new Locale(lTag);
    }

    if(!supportedLocales.contains(locale))
    {
      //This locale is not supported by this JVM.
      locale = null;
    }
    return locale;
  }



 /**
  *Collation rule for Equality matching rule.
  */
  private final class CollationEqualityMatchingRule
         extends ExtensibleMatchingRule
  {
    //Names for this class.
    private final Collection<String> names;

    //Collator for performing equality match.
    private final Collator collator;

    //Numeric OID of the rule.
    private final String nOID;


    /**
     * Constructs a new CollationEqualityMatchingRule.
     *
     * @param nOID OID of the collation matching rule
     * @param names names of this matching rule
     * @param locale Locale of the collation matching rule
     */
    private CollationEqualityMatchingRule(String nOID,Collection<String> names,
            Locale locale)
    {
      super();
      this.names = names;
      this.collator =createCollator(locale);
      this.nOID = nOID;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public String getName()
    {
      //Concatenate all the names and return.
      StringBuilder builder = new StringBuilder();
      for(String name: getAllNames())
      {
        builder.append(name);
        builder.append("\b");
      }
      return builder.toString();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<String> getAllNames()
    {
      return Collections.unmodifiableCollection(names);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String getOID()
    {
      return nOID;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription()
    {
      // There is no standard description for this matching rule.
      return null;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public String getSyntaxOID()
    {
      return SYNTAX_DIRECTORY_STRING_OID;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public ByteString normalizeValue(ByteString value)
           throws DirectoryException
    {
      CollationKey key = collator.getCollationKey(value.stringValue());
      return new ASN1OctetString(key.toByteArray());
    }



   /**
    * Indicates whether the two provided normalized values are equal to
    * each other.
    *
    * @param  value1  The normalized form of the first value to
    *                 compare.
    * @param  value2  The normalized form of the second value to
    *                 compare.
    *
    * @return  {@code true} if the provided values are equal, or
    *          {@code false} if not.
    */
    private boolean areEqual(ByteString value1, ByteString value2)
    {
      return Arrays.equals(value1.value(), value2.value());
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public ConditionResult valuesMatch(ByteString attributeValue,
                                     ByteString assertionValue)
    {
      if (areEqual(attributeValue, assertionValue))
      {
        return ConditionResult.TRUE;
      }
      else
      {
        return ConditionResult.FALSE;
      }
    }
  }


 /**
  * Collation rule for Substring matching rule.
  */
  private final class CollationSubstringMatchingRule
         extends ExtensibleMatchingRule
  {
    //Names for this class.
    private final Collection<String> names;

    //Collator for performing equality match.
    private final Collator collator;

    //Numeric OID of the rule.
    private final String nOID;


    /**
     * Constructs a new CollationSubstringMatchingRule.
     *
     * @param nOID OID of the collation matching rule
     * @param names names of this matching rule
     * @param locale Locale of the collation matching rule
     */
    private CollationSubstringMatchingRule(String nOID,
            Collection<String> names,Locale locale)
    {
      super();
      this.names = names;
      this.collator =createCollator(locale);
      this.nOID = nOID;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public String getName()
    {
      //Concatenate all the names and return.
      StringBuilder builder = new StringBuilder();
      for(String name: getAllNames())
      {
        builder.append(name);
        builder.append("\b");
      }
      return builder.toString();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<String> getAllNames()
    {
      return Collections.unmodifiableCollection(names);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String getOID()
    {
      return nOID;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription()
    {
      return null;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public String getSyntaxOID()
    {
      return SYNTAX_DIRECTORY_STRING_OID;
    }



   /**
    * {@inheritDoc}
    */
    @Override
    public ByteString normalizeValue(ByteString value)
           throws DirectoryException
    {
      CollationKey key = collator.getCollationKey(value.stringValue());
      return new ASN1OctetString(key.toByteArray());
    }



   /**
    * {@inheritDoc}
    */
    @Override
    public  ByteString normalizeAssertionValue(ByteString value)
           throws DirectoryException
    {
      // Get a string  representation of the value.
      String filterString = value.stringValue();
      int endPos = filterString.length();

      // Find the locations of all the asterisks in the value.  Also,
      // check to see if there are any escaped values, since they will
      // need special treatment.
      boolean hasEscape = false;
      LinkedList<Integer> asteriskPositions = new LinkedList<Integer>();
      for (int i=0; i < endPos; i++)
      {
        if (filterString.charAt(i) == 0x2A) // The asterisk.
        {
          asteriskPositions.add(i);
        }
        else if (filterString.charAt(i) == 0x5C) // The backslash.
        {
          hasEscape = true;
        }
      }


      // If there were no asterisks, then this isn't a substring filter.
      if (asteriskPositions.isEmpty())
      {
        Message message = ERR_SEARCH_FILTER_SUBSTRING_NO_ASTERISKS.get(
            filterString, 0, endPos);
        throw new DirectoryException(
                ResultCode.PROTOCOL_ERROR, message);
      }

      // If the value starts with an asterisk, then there is no
      // subInitial component.  Otherwise, parse out the subInitial.
      String subInitial;
      int firstPos = asteriskPositions.removeFirst();
      if (firstPos == 0)
      {
        subInitial = null;
      }
      else
      {
        if (hasEscape)
        {
          CharBuffer buffer = CharBuffer.allocate(firstPos);
          for (int i=0; i < firstPos; i++)
          {
            if (filterString.charAt(i) == 0x5C)
            {
              char escapeValue = hexToEscapedChar(filterString, i +1);
              i +=2; //Move to the next sequence.
              buffer.put(escapeValue);
            }
            else
            {
              buffer.put(filterString.charAt(i));
            }
          }

          char[] subInitialChars = new char[buffer.position()];
          buffer.flip();
          buffer.get(subInitialChars);
          subInitial = new String(subInitialChars);
        }
        else
        {
          subInitial = filterString.substring(0,firstPos);
        }
      }


      // Next, process through the rest of the asterisks to get the
      // subAny values.
      List<String> subAny = new ArrayList<String>();
      for (int asteriskPos : asteriskPositions)
      {
        int length = asteriskPos - firstPos - 1;

        if (hasEscape)
        {
          CharBuffer buffer = CharBuffer.allocate(length);
          for (int i=firstPos+1; i < asteriskPos; i++)
          {
            if (filterString.charAt(i) == 0x5C)
            {
              char escapeValue = hexToEscapedChar(filterString, i + 1);
              i +=2; //Move to the next sequence.
              buffer.put(escapeValue);
            }
            else
            {
              buffer.put(filterString.charAt(i));
            }
          }

          char[] subAnyChars = new char[buffer.position()];
          buffer.flip();
          buffer.get(subAnyChars);
          subAny.add(new String(subAnyChars));
        }
        else
        {
          subAny.add(filterString.substring(firstPos+1, firstPos+length+1));
        }


        firstPos = asteriskPos;
      }


      // Finally, see if there is anything after the last asterisk,
      // which would be the subFinal value.
      String subFinal;
      if (firstPos == (endPos-1))
      {
        subFinal = null;
      }
      else
      {
        int length = endPos - firstPos - 1;

        if (hasEscape)
        {
          CharBuffer buffer = CharBuffer.allocate(length);
          for (int i=firstPos+1; i < endPos; i++)
          {
            if (filterString.charAt(i) == 0x5C)
            {
              char escapeValue = hexToEscapedChar(filterString, i + 1);
              i +=2; //Move to the next sequence.
              buffer.put(escapeValue);
            }
            else
            {
              buffer.put(filterString.charAt(i));
            }
          }

          char[] subFinalChars = new char[buffer.position()];
          buffer.flip();
          buffer.get(subFinalChars);
          subFinal = new String(subFinalChars);
        }
        else
        {
          subFinal = filterString.substring(firstPos+1, length + firstPos + 1);
        }
      }

      // Normalize the Values in the following format:
      // initialLength, initial, numberofany, anyLength1, any1, anyLength2,
      // any2, ..., anyLengthn, anyn, finalLength, final
      CollationKey key = null;
      List<Integer> normalizedList = new ArrayList<Integer>();

      if(subInitial == null)
      {
        normalizedList.add(0);
      }
      else
      {
        key = collator.getCollationKey(subInitial);
        byte[] initialBytes = key.toByteArray();
        // Last 4 bytes are 0s with PRIMARY strenght.
        int length = initialBytes.length - 4 ;
        normalizedList.add(length);
        for(int i=0;i<length;i++)
        {
          normalizedList.add((int)initialBytes[i]);
        }
      }
      if(subAny.size()==0)
      {
        normalizedList.add(0);
      }
      else
      {
        normalizedList.add(subAny.size());
        for(String any:subAny)
        {
          key = collator.getCollationKey(any);
          byte[] anyBytes = key.toByteArray();
          int length = anyBytes.length - 4;
          normalizedList.add(length);
          for(int i=0;i<length;i++)
          {
            normalizedList.add((int)anyBytes[i]);
          }
        }
      }
      if(subFinal ==null)
      {
        normalizedList.add(0);
      }
      else
      {
        key = collator.getCollationKey(subFinal);
        byte[] subFinalBytes = key.toByteArray();
        int length = subFinalBytes.length - 4;
        normalizedList.add(length);
        for(int i=0;i<length;i++)
        {
          normalizedList.add((int)subFinalBytes[i]);
        }
      }

      byte[] normalizedBytes = new byte[normalizedList.size()];
      for(int i=0;i<normalizedList.size();i++)
      {
        normalizedBytes[i] = normalizedList.get(i).byteValue();
      }
      return new ASN1OctetString(normalizedBytes);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public ConditionResult valuesMatch(ByteString attributeValue,
                                       ByteString assertionValue)
    {
      byte[] valueBytes = attributeValue.value();
      int valueLength = valueBytes.length - 4;

      byte[] assertionBytes = assertionValue.value();

      int valuePos = 0; // position in the value bytes array.
      int assertPos = 0; // position in the assertion bytes array.
      int subInitialLength = 0xFF & assertionBytes[0];
      //First byte is the length of subInitial.

      if(subInitialLength!= 0)
      {
        if(subInitialLength > valueLength)
        {
          return ConditionResult.FALSE;
        }

        for(;valuePos < subInitialLength; valuePos++)
        {
          if(valueBytes[valuePos]!=assertionBytes[valuePos+1])
          {
            return ConditionResult.FALSE;
          }
        }
      }
      assertPos = subInitialLength + 1;
      int anySize = 0xFF & assertionBytes[assertPos++];
      if(anySize!=0)
      {
        while(anySize-- > 0)
        {
          int anyLength = 0xFF & assertionBytes[assertPos++];
          int end = valueLength - anyLength;
          boolean match = false;
          for(; valuePos <= end; valuePos++)
          {

            if(assertionBytes[assertPos] == valueBytes[valuePos])
            {
              boolean subMatch = true;
              for(int i=1;i<anyLength;i++)
              {

                if(assertionBytes[assertPos+i] != valueBytes[valuePos+i])
                {
                  subMatch = false;
                  break;
                }
              }

              if(subMatch)
              {
                match = subMatch;
                break;
              }

            }
          }

          if(match)
          {
            valuePos += anyLength;
          }
          else
          {
            return ConditionResult.FALSE;
          }
          assertPos = assertPos + anyLength;
        }
       }

      int finalLength = 0xFF & assertionBytes[assertPos++];
      if(finalLength!=0)
      {
        if((valueLength - finalLength) < valuePos)
        {
          return ConditionResult.FALSE;
        }

        valuePos = valueLength - finalLength;

        if(finalLength != assertionBytes.length - assertPos )
        {
          //Some issue with the encoding.
          return ConditionResult.FALSE;
        }

        valuePos = valueLength - finalLength;
        for (int i=0; i < finalLength; i++,valuePos++)
        {
          if (assertionBytes[assertPos+i] != valueBytes[valuePos])
          {
            return ConditionResult.FALSE;
          }
        }
      }

      return ConditionResult.TRUE;
     }
  }

 /**
  *Collation rule for less-than matching rule.
  */
  private final class CollationLessThanMatchingRule
         extends ExtensibleMatchingRule
  {
    //Names for this class.
    private final Collection<String> names;

    //Collator for performing equality match.
    private final Collator collator;

    //Numeric OID of the rule.
    private final String nOID;

    /**
     * Constructs a new CollationLessThanMatchingRule.
     *
     * @param nOID OID of the collation matching rule
     * @param names names of this matching rule
     * @param locale Locale of the collation matching rule
     */
    private CollationLessThanMatchingRule(String nOID,
            Collection<String> names,Locale locale)
    {
      super();
      this.names = names;
      this.collator =createCollator(locale);
      this.nOID = nOID;
    }




   /**
    * {@inheritDoc}
    */
    @Override
    public String getName()
    {
       //Concatenate all the names and return.
      StringBuilder builder = new StringBuilder();
      for(String name: getAllNames())
      {
        builder.append(name);
        builder.append("\b");
      }
      return builder.toString();
    }




   /**
    * {@inheritDoc}
    */
    @Override
    public Collection<String> getAllNames()
    {
      return Collections.unmodifiableCollection(names);
    }




    /**
    * {@inheritDoc}
    */
    @Override
    public String getOID()
    {
      return nOID;
    }



    /**
    * {@inheritDoc}
    */
    @Override
    public String getDescription()
    {
      // There is no standard description for this matching rule.
      return null;
    }



    /**
    * {@inheritDoc}
    */
    @Override
    public String getSyntaxOID()
    {
      return SYNTAX_DIRECTORY_STRING_OID;
    }



    /**
    * {@inheritDoc}
    */
    @Override
    public ByteString normalizeValue(ByteString value)
           throws DirectoryException
    {
      CollationKey key = collator.getCollationKey(value.stringValue());
      return new ASN1OctetString(key.toByteArray());
    }



   /**
    * Compares the first value to the second and returns a value that
    * indicates their relative order.
    *
    * @param  b1  The normalized form of the first value to
    *                 compare.
    * @param  b2  The normalized form of the second value to
    *                 compare.
    *
    * @return  A negative integer if {@code value1} should come before
    *          {@code value2} in ascending order, a positive integer if
    *          {@code value1} should come after {@code value2} in
    *          ascending order, or zero if there is no difference
    *          between the values with regard to ordering.
    */
    private int compare(byte[] b1, byte[] b2)
    {
      //Compare values using byte arrays.
      int minLength = Math.min(b1.length, b2.length);

      for (int i=0; i < minLength; i++)
      {
        int firstByte = 0xFF & ((int)b1[i]);
        int secondByte = 0xFF & ((int)b2[i]);

        if (firstByte == secondByte)
        {
          continue;
        }
        else if (firstByte < secondByte)
        {
          return -1;
        }
        else if (firstByte > secondByte)
        {
          return 1;
        }
      }

      if (b1.length == b2.length)
      {
        return 0;
      }
      else if (b1.length < b2.length)
      {
        return -1;
      }
      else
      {
        return 1;
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public ConditionResult valuesMatch(ByteString attributeValue,
                                       ByteString assertionValue)
    {
      int ret = compare(attributeValue.value(),assertionValue.value());

      if(ret <0)
      {
        return ConditionResult.TRUE;
      }
      else
      {
        return ConditionResult.FALSE;
      }
    }
  }


  /**
  * Collation rule for less-than-equal-to matching rule.
  */
  private final class CollationLessThanOrEqualToMatchingRule
         extends ExtensibleMatchingRule
  {
    //Names for this class.
    private final Collection<String> names;

    //Collator for performing equality match.
    private final Collator collator;

    //Numeric OID of the rule.
    private final String nOID;


    /**
     * Constructs a new CollationLessThanOrEqualToMatchingRule.
     *
     * @param nOID OID of the collation matching rule
     * @param names names of this matching rule
     * @param locale Locale of the collation matching rule
     */
    private CollationLessThanOrEqualToMatchingRule(String nOID,
                                    Collection<String> names,
                                    Locale locale)
    {
      super();
      this.names = names;
      this.collator =createCollator(locale);
      this.nOID = nOID;
    }



   /**
    * {@inheritDoc}
    */
    @Override
    public String getName()
    {
      //Concatenate all the names and return.
      StringBuilder builder = new StringBuilder();
      for(String name: getAllNames())
      {
        builder.append(name);
        builder.append("\b");
      }
      return builder.toString();
    }



   /**
    * {@inheritDoc}
    */
    @Override
    public Collection<String> getAllNames()
    {
      return Collections.unmodifiableCollection(names);
    }




   /**
    * {@inheritDoc}
    */
    @Override
    public String getOID()
    {
      return nOID;
    }




   /**
    * {@inheritDoc}
    */
    @Override
    public String getDescription()
    {
      // There is no standard description for this matching rule.
      return null;
    }




    /**
    * {@inheritDoc}
    */
    @Override
    public String getSyntaxOID()
    {
      return SYNTAX_DIRECTORY_STRING_OID;
    }



   /**
    * {@inheritDoc}
    */
    @Override
    public ByteString normalizeValue(ByteString value)
           throws DirectoryException
    {
      CollationKey key = collator.getCollationKey(value.stringValue());
      return new ASN1OctetString(key.toByteArray());
    }



   /**
    * Compares the first value to the second and returns a value that
    * indicates their relative order.
    *
    * @param  b1  The normalized form of the first value to
    *                 compare.
    * @param  b2  The normalized form of the second value to
    *                 compare.
    *
    * @return  A negative integer if {@code value1} should come before
    *          {@code value2} in ascending order, a positive integer if
    *          {@code value1} should come after {@code value2} in
    *          ascending order, or zero if there is no difference
    *          between the values with regard to ordering.
    */
    public int compare(byte[] b1, byte[] b2)
    {
      //Compare values using byte arrays.
      int minLength = Math.min(b1.length, b2.length);

      for (int i=0; i < minLength; i++)
      {
        int firstByte = 0xFF & ((int)b1[i]);
        int secondByte = 0xFF & ((int)b2[i]);

        if (firstByte == secondByte)
        {
          continue;
        }
        else if (firstByte < secondByte)
        {
          return -1;
        }
        else if (firstByte > secondByte)
        {
          return 1;
        }
      }

      if (b1.length == b2.length)
      {
        return 0;
      }
      else if (b1.length < b2.length)
      {
        return -1;
      }
      else
      {
        return 1;
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public ConditionResult valuesMatch(ByteString attributeValue,
                                       ByteString assertionValue)
    {
      int ret = compare(attributeValue.value(),assertionValue.value());

      if(ret <= 0)
      {
        return ConditionResult.TRUE;
      }
      else
      {
        return ConditionResult.FALSE;
      }
    }
  }



 /**
  * Collation rule for greater-than matching rule.
  */
  private final class CollationGreaterThanMatchingRule
         extends ExtensibleMatchingRule
  {
    //Names for this class.
    private final Collection<String> names;

    //Collator for performing equality match.
    private final Collator collator;

    //Numeric OID of the rule.
    private final String nOID;


    /**
     * Constructs a new CollationGreaterThanMatchingRule.
     *
     * @param nOID OID of the collation matching rule
     * @param names names of this matching rule
     * @param locale Locale of the collation matching rule
     */
    private CollationGreaterThanMatchingRule(String nOID,
            Collection<String> names,Locale locale)
    {
      super();
      this.names = names;
      this.collator =createCollator(locale);
      this.nOID = nOID;
    }




   /**
    * {@inheritDoc}
    */
    @Override
    public String getName()
    {
     //Concatenate all the names and return.
      StringBuilder builder = new StringBuilder();
      for(String name: getAllNames())
      {
        builder.append(name);
        builder.append("\b");
      }
      return builder.toString();
    }




   /**
    * {@inheritDoc}
    */
    @Override
    public Collection<String> getAllNames()
    {
      return Collections.unmodifiableCollection(names);
    }




   /**
    * {@inheritDoc}
    */
    @Override
    public String getOID()
    {
      return nOID;
    }



   /**
    * {@inheritDoc}
    */
    @Override
    public String getDescription()
    {
      // There is no standard description for this matching rule.
      return null;
    }



   /**
    * {@inheritDoc}
    */
    @Override
    public String getSyntaxOID()
    {
      return SYNTAX_DIRECTORY_STRING_OID;
    }




   /**
    * {@inheritDoc}
    */
    @Override
    public ByteString normalizeValue(ByteString value)
           throws DirectoryException
    {
      CollationKey key = collator.getCollationKey(value.stringValue());
      return new ASN1OctetString(key.toByteArray());
    }



   /**
    * Compares the first value to the second and returns a value that
    * indicates their relative order.
    *
    * @param  b1  The normalized form of the first value to
    *                 compare.
    * @param  b2  The normalized form of the second value to
    *                 compare.
    *
    * @return  A negative integer if {@code value1} should come before
    *          {@code value2} in ascending order, a positive integer if
    *          {@code value1} should come after {@code value2} in
    *          ascending order, or zero if there is no difference
    *          between the values with regard to ordering.
    */
    public int compare(byte[] b1, byte[] b2)
    {
      //Compare values using byte arrays.
      int minLength = Math.min(b1.length, b2.length);

      for (int i=0; i < minLength; i++)
      {
        int firstByte = 0xFF & ((int)b1[i]);
        int secondByte = 0xFF & ((int)b2[i]);

        if (firstByte == secondByte)
        {
          continue;
        }
        else if (firstByte < secondByte)
        {
          return -1;
        }
        else if (firstByte > secondByte)
        {
          return 1;
        }
      }

      if (b1.length == b2.length)
      {
        return 0;
      }
      else if (b1.length < b2.length)
      {
        return -1;
      }
      else
      {
        return 1;
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public ConditionResult valuesMatch(ByteString attributeValue,
                                       ByteString assertionValue)
    {
      int ret = compare(attributeValue.value(),assertionValue.value());

      if(ret > 0)
      {
        return ConditionResult.TRUE;
      }
      else
      {
        return ConditionResult.FALSE;
      }
    }
  }


 /**
  * Collation rule for greater-than-equal-to matching rule.
  */
  private final class CollationGreaterThanOrEqualToMatchingRule
         extends ExtensibleMatchingRule
  {

    //Names for this class.
    private final Collection<String> names;

    //Collator for performing equality match.
    private final Collator collator;

    //Numeric OID of the rule.
    private final String nOID;


    /**
     * Constructs a new CollationGreaterThanOrEqualToMatchingRule.
     *
     * @param nOID OID of the collation matching rule
     * @param names names of this matching rule
     * @param locale Locale of the collation matching rule
     */
    private CollationGreaterThanOrEqualToMatchingRule(String nOID,
                                    Collection<String> names,
                                    Locale locale)
    {
      super();
      this.names = names;
      this.collator =createCollator(locale);
      this.nOID = nOID;
    }



   /**
    * {@inheritDoc}
    */
    @Override
    public String getName()
    {
       //Concatenate all the names and return.
      StringBuilder builder = new StringBuilder();
      for(String name: getAllNames())
      {
        builder.append(name);
        builder.append("\b");
      }
      return builder.toString();
    }



   /**
    * {@inheritDoc}
    */
    @Override
    public Collection<String> getAllNames()
    {
      return Collections.unmodifiableCollection(names);
    }



   /**
    * {@inheritDoc}
    */
    @Override
    public String getOID()
    {
      return nOID;
    }



   /**
    * {@inheritDoc}
    */
    @Override
    public String getDescription()
    {
      // There is no standard description for this matching rule.
      return null;
    }



   /**
    * {@inheritDoc}
    */
    @Override
    public String getSyntaxOID()
    {
      return SYNTAX_DIRECTORY_STRING_OID;
    }



   /**
    * {@inheritDoc}
    */
    @Override
    public ByteString normalizeValue(ByteString value)
           throws DirectoryException
    {
      CollationKey key = collator.getCollationKey(value.stringValue());
      return new ASN1OctetString(key.toByteArray());
    }



   /**
    * Compares the first value to the second and returns a value that
    * indicates their relative order.
    *
    * @param  b1  The normalized form of the first value to
    *                 compare.
    * @param  b2  The normalized form of the second value to
    *                 compare.
    *
    * @return  A negative integer if {@code value1} should come before
    *          {@code value2} in ascending order, a positive integer if
    *          {@code value1} should come after {@code value2} in
    *          ascending order, or zero if there is no difference
    *          between the values with regard to ordering.
    */
    public int compare(byte[] b1, byte[] b2)
    {
      //Compare values using byte arrays.
      int minLength = Math.min(b1.length, b2.length);

      for (int i=0; i < minLength; i++)
      {
        int firstByte = 0xFF & ((int)b1[i]);
        int secondByte = 0xFF & ((int)b2[i]);

        if (firstByte == secondByte)
        {
          continue;
        }
        else if (firstByte < secondByte)
        {
          return -1;
        }
        else if (firstByte > secondByte)
        {
          return 1;
        }
      }

      if (b1.length == b2.length)
      {
        return 0;
      }
      else if (b1.length < b2.length)
      {
        return -1;
      }
      else
      {
        return 1;
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public ConditionResult valuesMatch(ByteString attributeValue,
                                       ByteString assertionValue)
    {
      int ret = compare(attributeValue.value(),assertionValue.value());

      if(ret >= 0)
      {
        return ConditionResult.TRUE;
      }
      else
      {
        return ConditionResult.FALSE;
      }
    }
  }


 /**
  * A utility class for extracting the OID and Language Tag from the
  * configuration entry.
  */
  private final class CollationMapper
  {
    //OID of the collation rule.
    private  String oid;

    //Language Tag.
    private  String lTag;


    /**
     * Creates a new instance of CollationMapper.
     *
     * @param collation The collation text in the LOCALE:OID format.
     */
    private CollationMapper(String collation)
    {
      int index = collation.indexOf(":");
      if(index>0)
      {
        oid = collation.substring(index+1,collation.length());
        lTag = collation.substring(0,index);
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