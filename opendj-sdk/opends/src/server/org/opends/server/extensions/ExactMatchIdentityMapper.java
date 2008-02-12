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
package org.opends.server.extensions;
import org.opends.messages.Message;



import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.ExactMatchIdentityMapperCfg;
import org.opends.server.admin.std.server.IdentityMapperCfg;
import org.opends.server.api.Backend;
import org.opends.server.api.IdentityMapper;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.IndexType;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchScope;

import static org.opends.messages.ExtensionMessages.*;

import static org.opends.server.util.StaticUtils.*;



/**
 * This class provides an implementation of a Directory Server identity mapper
 * that looks for the exact value provided as the ID string to appear in an
 * attribute of a user's entry.  This mapper may be configured to look in one or
 * more attributes using zero or more search bases.  In order for the mapping to
 * be established properly, exactly one entry must have an attribute that
 * exactly matches (according to the equality matching rule associated with that
 * attribute) the ID value.
 */
public class ExactMatchIdentityMapper
       extends IdentityMapper<ExactMatchIdentityMapperCfg>
       implements ConfigurationChangeListener<
                       ExactMatchIdentityMapperCfg>
{
  // The set of attribute types to use when performing lookups.
  private AttributeType[] attributeTypes;

  // The DN of the configuration entry for this identity mapper.
  private DN configEntryDN;

  // The current configuration for this identity mapper.
  private ExactMatchIdentityMapperCfg currentConfig;

  // The set of attributes to return in search result entries.
  private LinkedHashSet<String> requestedAttributes;



  /**
   * Creates a new instance of this exact match identity mapper.  All
   * initialization should be performed in the {@code initializeIdentityMapper}
   * method.
   */
  public ExactMatchIdentityMapper()
  {
    super();

    // Don't do any initialization here.
  }



  /**
   * {@inheritDoc}
   */
  public void initializeIdentityMapper(
                   ExactMatchIdentityMapperCfg configuration)
         throws ConfigException, InitializationException
  {
    configuration.addExactMatchChangeListener(this);

    currentConfig = configuration;
    configEntryDN = currentConfig.dn();


    // Get the attribute types to use for the searches.  Ensure that they are
    // all indexed for equality.
    attributeTypes =
         currentConfig.getMatchAttribute().toArray(new AttributeType[0]);

    Set<DN> cfgBaseDNs = configuration.getMatchBaseDN();
    if ((cfgBaseDNs == null) || cfgBaseDNs.isEmpty())
    {
      cfgBaseDNs = DirectoryServer.getPublicNamingContexts().keySet();
    }

    for (AttributeType t : attributeTypes)
    {
      for (DN baseDN : cfgBaseDNs)
      {
        Backend b = DirectoryServer.getBackend(baseDN);
        if ((b != null) && (! b.isIndexed(t, IndexType.EQUALITY)))
        {
          throw new ConfigException(ERR_EXACTMAP_ATTR_UNINDEXED.get(
                                         configuration.dn().toString(),
                                         t.getNameOrOID(),
                                         b.getBackendID()));
        }
      }
    }


    // Create the attribute list to include in search requests.  We want to
    // include all user and operational attributes.
    requestedAttributes = new LinkedHashSet<String>(2);
    requestedAttributes.add("*");
    requestedAttributes.add("+");
  }



  /**
   * Performs any finalization that may be necessary for this identity mapper.
   */
  public void finalizeIdentityMapper()
  {
    currentConfig.removeExactMatchChangeListener(this);
  }



  /**
   * Retrieves the user entry that was mapped to the provided identification
   * string.
   *
   * @param  id  The identification string that is to be mapped to a user.
   *
   * @return  The user entry that was mapped to the provided identification, or
   *          <CODE>null</CODE> if no users were found that could be mapped to
   *          the provided ID.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to map
   *                              the given ID to a user entry, or if there are
   *                              multiple user entries that could map to the
   *                              provided ID.
   */
  public Entry getEntryForID(String id)
         throws DirectoryException
  {
    ExactMatchIdentityMapperCfg config = currentConfig;
    AttributeType[] attributeTypes = this.attributeTypes;


    // Construct the search filter to use to make the determination.
    SearchFilter filter;
    if (attributeTypes.length == 1)
    {
      AttributeValue value = new AttributeValue(attributeTypes[0], id);
      filter = SearchFilter.createEqualityFilter(attributeTypes[0], value);
    }
    else
    {
      ArrayList<SearchFilter> filterComps =
           new ArrayList<SearchFilter>(attributeTypes.length);
      for (AttributeType t : attributeTypes)
      {
        AttributeValue value = new AttributeValue(t, id);
        filterComps.add(SearchFilter.createEqualityFilter(t, value));
      }

      filter = SearchFilter.createORFilter(filterComps);
    }


    // Iterate through the set of search bases and process an internal search
    // to find any matching entries.  Since we'll only allow a single match,
    // then use size and time limits to constrain costly searches resulting from
    // non-unique or inefficient criteria.
    Collection<DN> baseDNs = config.getMatchBaseDN();
    if ((baseDNs == null) || baseDNs.isEmpty())
    {
      baseDNs = DirectoryServer.getPublicNamingContexts().keySet();
    }

    SearchResultEntry matchingEntry = null;
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    for (DN baseDN : baseDNs)
    {
      InternalSearchOperation internalSearch =
           conn.processSearch(baseDN, SearchScope.WHOLE_SUBTREE,
                              DereferencePolicy.NEVER_DEREF_ALIASES, 1, 10,
                              false, filter, requestedAttributes);

      switch (internalSearch.getResultCode())
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
          Message message =
              ERR_EXACTMAP_MULTIPLE_MATCHING_ENTRIES.get(String.valueOf(id));
          throw new DirectoryException(
                  ResultCode.CONSTRAINT_VIOLATION, message);

        case TIME_LIMIT_EXCEEDED:
        case ADMIN_LIMIT_EXCEEDED:
          // The search criteria was too inefficient.
          message = ERR_EXACTMAP_INEFFICIENT_SEARCH.
              get(String.valueOf(id),
                  String.valueOf(internalSearch.getErrorMessage()));
          throw new DirectoryException(internalSearch.getResultCode(), message);

        default:
          // Just pass on the failure that was returned for this search.
          message = ERR_EXACTMAP_SEARCH_FAILED.
              get(String.valueOf(id),
                  String.valueOf(internalSearch.getErrorMessage()));
          throw new DirectoryException(internalSearch.getResultCode(), message);
      }

      LinkedList<SearchResultEntry> searchEntries =
           internalSearch.getSearchEntries();
      if ((searchEntries != null) && (! searchEntries.isEmpty()))
      {
        if (matchingEntry == null)
        {
          Iterator<SearchResultEntry> iterator = searchEntries.iterator();
          matchingEntry = iterator.next();
          if (iterator.hasNext())
          {
            Message message =
                ERR_EXACTMAP_MULTIPLE_MATCHING_ENTRIES.get(String.valueOf(id));
            throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                         message);
          }
        }
        else
        {
          Message message =
              ERR_EXACTMAP_MULTIPLE_MATCHING_ENTRIES.get(String.valueOf(id));
          throw new DirectoryException(
                  ResultCode.CONSTRAINT_VIOLATION, message);
        }
      }
    }


    if (matchingEntry == null)
    {
      return null;
    }
    else
    {
      return matchingEntry;
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isConfigurationAcceptable(IdentityMapperCfg configuration,
                                           List<Message> unacceptableReasons)
  {
    ExactMatchIdentityMapperCfg config =
         (ExactMatchIdentityMapperCfg) configuration;
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
                      ExactMatchIdentityMapperCfg configuration,
                      List<Message> unacceptableReasons)
  {
    boolean configAcceptable = true;

    // Make sure that all of the configured attributes are indexed for equality
    // in all appropriate backends.
    Set<DN> cfgBaseDNs = configuration.getMatchBaseDN();
    if ((cfgBaseDNs == null) || cfgBaseDNs.isEmpty())
    {
      cfgBaseDNs = DirectoryServer.getPublicNamingContexts().keySet();
    }

    for (AttributeType t : configuration.getMatchAttribute())
    {
      for (DN baseDN : cfgBaseDNs)
      {
        Backend b = DirectoryServer.getBackend(baseDN);
        if ((b != null) && (! b.isIndexed(t, IndexType.EQUALITY)))
        {
          unacceptableReasons.add(ERR_EXACTMAP_ATTR_UNINDEXED.get(
                                       configuration.dn().toString(),
                                       t.getNameOrOID(),
                                       b.getBackendID()));
          configAcceptable = false;
        }
      }
    }

    return configAcceptable;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
              ExactMatchIdentityMapperCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();


    attributeTypes =
         configuration.getMatchAttribute().toArray(new AttributeType[0]);
    currentConfig = configuration;


   return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }
}

