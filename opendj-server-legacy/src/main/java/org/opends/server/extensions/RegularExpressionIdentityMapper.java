/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.opends.server.util.CollectionUtils.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.server.config.server.IdentityMapperCfg;
import org.forgerock.opendj.server.config.server.RegularExpressionIdentityMapperCfg;
import org.opends.server.api.Backend;
import org.opends.server.api.IdentityMapper;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.opends.server.types.*;

/**
 * This class provides an implementation of a Directory Server identity mapper
 * that uses a regular expression to process the provided ID string, and then
 * looks for that processed value to appear in an attribute of a user's entry.
 * This mapper may be configured to look in one or more attributes using zero or
 * more search bases.  In order for the mapping to be established properly,
 * exactly one entry must have an attribute that exactly matches (according to
 * the equality matching rule associated with that attribute) the processed ID
 * value.
 */
public class RegularExpressionIdentityMapper
       extends IdentityMapper<RegularExpressionIdentityMapperCfg>
       implements ConfigurationChangeListener<
                       RegularExpressionIdentityMapperCfg>
{
  /** The set of attribute types to use when performing lookups. */
  private AttributeType[] attributeTypes;

  /** The DN of the configuration entry for this identity mapper. */
  private DN configEntryDN;

  /** The set of attributes to return in search result entries. */
  private LinkedHashSet<String> requestedAttributes;

  /** The regular expression pattern matcher for the current configuration. */
  private Pattern matchPattern;

  /** The current configuration for this identity mapper. */
  private RegularExpressionIdentityMapperCfg currentConfig;

  /** The replacement string to use for the pattern. */
  private String replacePattern;

  /**
   * Creates a new instance of this regular expression identity mapper.  All
   * initialization should be performed in the {@code initializeIdentityMapper}
   * method.
   */
  public RegularExpressionIdentityMapper()
  {
    super();

    // Don't do any initialization here.
  }

  @Override
  public void initializeIdentityMapper(
                   RegularExpressionIdentityMapperCfg configuration)
         throws ConfigException, InitializationException
  {
    configuration.addRegularExpressionChangeListener(this);

    currentConfig = configuration;
    configEntryDN = currentConfig.dn();

    try
    {
      matchPattern  = Pattern.compile(currentConfig.getMatchPattern());
    }
    catch (PatternSyntaxException pse) {
      LocalizableMessage message = ERR_REGEXMAP_INVALID_MATCH_PATTERN.get(
              currentConfig.getMatchPattern(),
              pse.getMessage());
      throw new ConfigException(message, pse);
    }

    replacePattern = currentConfig.getReplacePattern();
    if (replacePattern == null)
    {
      replacePattern = "";
    }

    // Get the attribute types to use for the searches.  Ensure that they are
    // all indexed for equality.
    attributeTypes =
         currentConfig.getMatchAttribute().toArray(new AttributeType[0]);

    Set<DN> cfgBaseDNs = configuration.getMatchBaseDN();
    if (cfgBaseDNs == null || cfgBaseDNs.isEmpty())
    {
      cfgBaseDNs = DirectoryServer.getPublicNamingContexts().keySet();
    }

    for (AttributeType t : attributeTypes)
    {
      for (DN baseDN : cfgBaseDNs)
      {
        Backend b = DirectoryServer.getBackend(baseDN);
        if (b != null && ! b.isIndexed(t, IndexType.EQUALITY))
        {
          throw new ConfigException(ERR_REGEXMAP_ATTR_UNINDEXED.get(
              configuration.dn(), t.getNameOrOID(), b.getBackendID()));
        }
      }
    }

    // Create the attribute list to include in search requests.  We want to
    // include all user and operational attributes.
    requestedAttributes = newLinkedHashSet("*", "+");
  }

  @Override
  public void finalizeIdentityMapper()
  {
    currentConfig.removeRegularExpressionChangeListener(this);
  }

  @Override
  public Entry getEntryForID(String id)
         throws DirectoryException
  {
    RegularExpressionIdentityMapperCfg config = currentConfig;
    AttributeType[] attributeTypes = this.attributeTypes;

    // Run the provided identifier string through the regular expression pattern
    // matcher and make the appropriate replacement.
    Matcher matcher = matchPattern.matcher(id);
    String processedID = matcher.replaceAll(replacePattern);

    // Construct the search filter to use to make the determination.
    SearchFilter filter;
    if (attributeTypes.length == 1)
    {
      ByteString value = ByteString.valueOfUtf8(processedID);
      filter = SearchFilter.createEqualityFilter(attributeTypes[0], value);
    }
    else
    {
      ArrayList<SearchFilter> filterComps = new ArrayList<>(attributeTypes.length);
      for (AttributeType t : attributeTypes)
      {
        ByteString value = ByteString.valueOfUtf8(processedID);
        filterComps.add(SearchFilter.createEqualityFilter(t, value));
      }

      filter = SearchFilter.createORFilter(filterComps);
    }

    // Iterate through the set of search bases and process an internal search
    // to find any matching entries.  Since we'll only allow a single match,
    // then use size and time limits to constrain costly searches resulting from
    // non-unique or inefficient criteria.
    Collection<DN> baseDNs = config.getMatchBaseDN();
    if (baseDNs == null || baseDNs.isEmpty())
    {
      baseDNs = DirectoryServer.getPublicNamingContexts().keySet();
    }

    SearchResultEntry matchingEntry = null;
    InternalClientConnection conn = getRootConnection();
    for (DN baseDN : baseDNs)
    {
      final SearchRequest request = newSearchRequest(baseDN, SearchScope.WHOLE_SUBTREE, filter)
          .setSizeLimit(1)
          .setTimeLimit(10)
          .addAttribute(requestedAttributes);
      InternalSearchOperation internalSearch = conn.processSearch(request);

      switch (internalSearch.getResultCode().asEnum())
      {
        case SUCCESS:
          // This is fine.  No action needed.
          break;

        case NO_SUCH_OBJECT:
          // The search base doesn't exist.  Not an ideal situation, but we'll
          // ignore it.
          break;

        case SIZE_LIMIT_EXCEEDED:
          // Multiple entries matched the filter.  This is not acceptable.
          LocalizableMessage message = ERR_REGEXMAP_MULTIPLE_MATCHING_ENTRIES.get(processedID);
          throw new DirectoryException(
                  ResultCode.CONSTRAINT_VIOLATION, message);

        case TIME_LIMIT_EXCEEDED:
        case ADMIN_LIMIT_EXCEEDED:
          // The search criteria was too inefficient.
          message = ERR_REGEXMAP_INEFFICIENT_SEARCH.get(processedID, internalSearch.getErrorMessage());
          throw new DirectoryException(internalSearch.getResultCode(), message);

        default:
          // Just pass on the failure that was returned for this search.
          message = ERR_REGEXMAP_SEARCH_FAILED.get(processedID, internalSearch.getErrorMessage());
          throw new DirectoryException(internalSearch.getResultCode(), message);
      }

      LinkedList<SearchResultEntry> searchEntries =
           internalSearch.getSearchEntries();
      if (searchEntries != null && ! searchEntries.isEmpty())
      {
        if (matchingEntry == null)
        {
          Iterator<SearchResultEntry> iterator = searchEntries.iterator();
          matchingEntry = iterator.next();
          if (iterator.hasNext())
          {
            LocalizableMessage message = ERR_REGEXMAP_MULTIPLE_MATCHING_ENTRIES.get(processedID);
            throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
          }
        }
        else
        {
          LocalizableMessage message = ERR_REGEXMAP_MULTIPLE_MATCHING_ENTRIES.get(processedID);
          throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
        }
      }
    }

    return matchingEntry;
  }

  @Override
  public boolean isConfigurationAcceptable(IdentityMapperCfg configuration,
                                           List<LocalizableMessage> unacceptableReasons)
  {
    RegularExpressionIdentityMapperCfg config =
         (RegularExpressionIdentityMapperCfg) configuration;
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
                      RegularExpressionIdentityMapperCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    boolean configAcceptable = true;

    // Make sure that all of the configured attributes are indexed for equality
    // in all appropriate backends.
    Set<DN> cfgBaseDNs = configuration.getMatchBaseDN();
    if (cfgBaseDNs == null || cfgBaseDNs.isEmpty())
    {
      cfgBaseDNs = DirectoryServer.getPublicNamingContexts().keySet();
    }

    for (AttributeType t : configuration.getMatchAttribute())
    {
      for (DN baseDN : cfgBaseDNs)
      {
        Backend b = DirectoryServer.getBackend(baseDN);
        if (b != null && ! b.isIndexed(t, IndexType.EQUALITY))
        {
          unacceptableReasons.add(ERR_REGEXMAP_ATTR_UNINDEXED.get(
              configuration.dn(), t.getNameOrOID(), b.getBackendID()));
          configAcceptable = false;
        }
      }
    }

    // Make sure that we can parse the match pattern.
    try
    {
      Pattern.compile(configuration.getMatchPattern());
    }
    catch (PatternSyntaxException pse)
    {
      unacceptableReasons.add(ERR_REGEXMAP_INVALID_MATCH_PATTERN.get(
          configuration.getMatchPattern(), pse.getMessage()));
      configAcceptable = false;
    }

    return configAcceptable;
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(
              RegularExpressionIdentityMapperCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    Pattern newMatchPattern = null;
    try
    {
      newMatchPattern = Pattern.compile(configuration.getMatchPattern());
    }
    catch (PatternSyntaxException pse)
    {
      ccr.addMessage(ERR_REGEXMAP_INVALID_MATCH_PATTERN.get(configuration.getMatchPattern(), pse.getMessage()));
      ccr.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
    }

    String newReplacePattern = configuration.getReplacePattern();
    if (newReplacePattern == null)
    {
      newReplacePattern = "";
    }

    AttributeType[] newAttributeTypes =
         configuration.getMatchAttribute().toArray(new AttributeType[0]);

    if (ccr.getResultCode() == ResultCode.SUCCESS)
    {
      attributeTypes = newAttributeTypes;
      currentConfig  = configuration;
      matchPattern   = newMatchPattern;
      replacePattern = newReplacePattern;
    }

    return ccr;
  }
}
