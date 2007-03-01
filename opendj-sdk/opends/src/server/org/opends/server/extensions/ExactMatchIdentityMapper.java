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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;

import org.opends.server.api.ConfigurableComponent;
import org.opends.server.api.IdentityMapper;
import org.opends.server.config.ConfigAttribute;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.DNConfigAttribute;
import org.opends.server.config.StringConfigAttribute;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.types.AttributeType;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchScope;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.debugCaught;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.messages.ExtensionsMessages.*;
import static org.opends.server.messages.MessageHandler.*;
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
       extends IdentityMapper
       implements ConfigurableComponent
{



  // The set of search base DNs that will be used to find a matching user.
  private ASN1OctetString[] rawSearchBases;

  // The DN of the configuration entry for this identity mapper.
  private DN configEntryDN;

  // The set of attributes to return in search result entries.
  private LinkedHashSet<String> requestedAttributes;

  // The set of attributes that will be searched to find a matching user.
  private String[] rawMatchAttributes;



  /**
   * Initializes this identity mapper based on the information in the provided
   * configuration entry.
   *
   * @param  configEntry  The configuration entry that contains the information
   *                      to use to initialize this identity mapper.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in the
   *                           process of performing the initialization.
   *
   * @throws  InitializationException  If a problem occurs during initialization
   *                                   that is not related to the server
   *                                   configuration.
   */
  public void initializeIdentityMapper(ConfigEntry configEntry)
         throws ConfigException, InitializationException
  {
    configEntryDN = configEntry.getDN();


    // Get the set of attributes that should be checked to see if they have a
    // value equal to the identity string.  We will only accept attributes that
    // have been defined in the server schema.
    int msgID = MSGID_EXACTMAP_DESCRIPTION_MATCH_ATTR;
    StringConfigAttribute attrStub =
         new StringConfigAttribute(ATTR_MATCH_ATTRIBUTE, getMessage(msgID),
                                   true, true, false);
    try
    {
      StringConfigAttribute attrAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(attrStub);
      if (attrAttr == null)
      {
        msgID = MSGID_EXACTMAP_NO_MATCH_ATTR;
        String message = getMessage(msgID, String.valueOf(configEntryDN));
        throw new ConfigException(msgID, message);
      }
      else
      {
        List<String> attrNames = attrAttr.activeValues();
        if (attrNames.size() == 0)
        {
          msgID = MSGID_EXACTMAP_NO_MATCH_ATTR;
          String message = getMessage(msgID, String.valueOf(configEntryDN));
          throw new ConfigException(msgID, message);
        }

        rawMatchAttributes = new String[attrNames.size()];
        for (int i=0; i < rawMatchAttributes.length; i++)
        {
          String name      = attrNames.get(i);
          String lowerName = toLowerCase(name);
          AttributeType attrType = DirectoryServer.getAttributeType(lowerName);
          if (attrType == null)
          {
            msgID = MSGID_EXACTMAP_UNKNOWN_ATTR;
            String message = getMessage(msgID, String.valueOf(configEntryDN),
                                        lowerName);
            throw new ConfigException(msgID, message);
          }

          rawMatchAttributes[i] = name;
        }
      }
    }
    catch(ConfigException ce)
    {
      throw ce;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_EXACTMAP_CANNOT_DETERMINE_MATCH_ATTR;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));

      throw new InitializationException(msgID, message, e);
    }


    // Get the set of base DNs that should be used for the searches.  If none
    // are provided, then use the root DSE.
    msgID = MSGID_EXACTMAP_DESCRIPTION_SEARCH_BASE;
    DNConfigAttribute baseStub =
         new DNConfigAttribute(ATTR_MATCH_BASE, getMessage(msgID), false, true,
                               false);
    try
    {
      DNConfigAttribute baseAttr =
           (DNConfigAttribute) configEntry.getConfigAttribute(baseStub);
      if (baseAttr == null)
      {
        // This is fine -- just use the root DSE.
        rawSearchBases = new ASN1OctetString[] { new ASN1OctetString() };
      }
      else
      {
        List<DN> baseDNs = baseAttr.activeValues();
        if (baseDNs.size() == 0)
        {
          // This is fine -- just use the root DSE.
          rawSearchBases = new ASN1OctetString[] { new ASN1OctetString() };
        }
        else
        {
          rawSearchBases = new ASN1OctetString[baseDNs.size()];
          for (int i=0; i < rawSearchBases.length; i++)
          {
            rawSearchBases[i] = new ASN1OctetString(baseDNs.get(i).toString());
          }
        }
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_EXACTMAP_CANNOT_DETERMINE_MATCH_BASE;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));

      throw new InitializationException(msgID, message, e);
    }


    // Register with the Directory Server as a configurable component.
    DirectoryServer.registerConfigurableComponent(this);


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
    // Deregister with the server as a configurable component.
    DirectoryServer.deregisterConfigurableComponent(this);
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
    // Get the attribute type and base DN arrays as local variables to protect
    // against concurrent modifications.
    String[]          matchAttrs = rawMatchAttributes;
    ASN1OctetString[] matchBases = rawSearchBases;


    // Construct the search filter to use to make the determination.
    LDAPFilter filter;
    if (matchAttrs.length == 1)
    {
      filter = LDAPFilter.createEqualityFilter(matchAttrs[0],
                                               new ASN1OctetString(id));
    }
    else
    {
      ArrayList<LDAPFilter> filterComponents =
           new ArrayList<LDAPFilter>(matchAttrs.length);

      ASN1OctetString idOS = new ASN1OctetString(id);
      for (String s : matchAttrs)
      {
        filterComponents.add(LDAPFilter.createEqualityFilter(s, idOS));
      }

      filter = LDAPFilter.createORFilter(filterComponents);
    }


    // Iterate through the set of search bases and process an internal search
    // to find any matching entries.  Since we'll only allow a single match,
    // then use size and time limits to constrain costly searches resulting from
    // non-unique or inefficient criteria.
    SearchResultEntry matchingEntry = null;
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    for (ASN1OctetString rawBase : matchBases)
    {
      InternalSearchOperation internalSearch =
           conn.processSearch(rawBase, SearchScope.WHOLE_SUBTREE,
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
          int    msgID   = MSGID_EXACTMAP_MULTIPLE_MATCHING_ENTRIES;
          String message = getMessage(msgID, String.valueOf(id));
          throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                       msgID);

        case TIME_LIMIT_EXCEEDED:
        case ADMIN_LIMIT_EXCEEDED:
          // The search criteria was too inefficient.
          msgID   = MSGID_EXACTMAP_INEFFICIENT_SEARCH;
          message = getMessage(msgID, String.valueOf(id),
                         String.valueOf(internalSearch.getErrorMessage()));
          throw new DirectoryException(internalSearch.getResultCode(), message,
                                       msgID);

        default:
          // Just pass on the failure that was returned for this search.
          msgID   = MSGID_EXACTMAP_SEARCH_FAILED;
          message = getMessage(msgID, String.valueOf(id),
                         String.valueOf(internalSearch.getErrorMessage()));
          throw new DirectoryException(internalSearch.getResultCode(), message,
                                       msgID);
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
            int    msgID   = MSGID_EXACTMAP_MULTIPLE_MATCHING_ENTRIES;
            String message = getMessage(msgID, String.valueOf(id));
            throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                         message, msgID);
          }
        }
        else
        {
          int    msgID   = MSGID_EXACTMAP_MULTIPLE_MATCHING_ENTRIES;
          String message = getMessage(msgID, String.valueOf(id));
          throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                       msgID);
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
   * Retrieves the DN of the configuration entry with which this component is
   * associated.
   *
   * @return  The DN of the configuration entry with which this component is
   *          associated.
   */
  public DN getConfigurableComponentEntryDN()
  {
    return configEntryDN;
  }



  /**
   * Retrieves the set of configuration attributes that are associated with this
   * configurable component.
   *
   * @return  The set of configuration attributes that are associated with this
   *          configurable component.
   */
  public List<ConfigAttribute> getConfigurationAttributes()
  {
    LinkedList<ConfigAttribute> attrList = new LinkedList<ConfigAttribute>();

    String[] attrs = rawMatchAttributes;
    ArrayList<String> matchAttrs = new ArrayList<String>(attrs.length);
    for (String s : attrs)
    {
      matchAttrs.add(s);
    }

    String description = getMessage(MSGID_EXACTMAP_DESCRIPTION_MATCH_ATTR);
    attrList.add(new StringConfigAttribute(ATTR_MATCH_ATTRIBUTE, description,
                                           true, true, false, matchAttrs));


    ASN1OctetString[] bases = rawSearchBases;
    ArrayList<DN> baseDNs = new ArrayList<DN>(bases.length);
    for (ASN1OctetString dn : bases)
    {
      try
      {
        baseDNs.add(DN.decode(dn));
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        // This should never happen.
      }
    }

    description = getMessage(MSGID_EXACTMAP_DESCRIPTION_SEARCH_BASE);
    attrList.add(new DNConfigAttribute(ATTR_MATCH_BASE, description, false,
                                       true, false, baseDNs));


    return attrList;
  }



  /**
   * Indicates whether the provided configuration entry has an acceptable
   * configuration for this component.  If it does not, then detailed
   * information about the problem(s) should be added to the provided list.
   *
   * @param  configEntry          The configuration entry for which to make the
   *                              determination.
   * @param  unacceptableReasons  A list that can be used to hold messages about
   *                              why the provided entry does not have an
   *                              acceptable configuration.
   *
   * @return  <CODE>true</CODE> if the provided entry has an acceptable
   *          configuration for this component, or <CODE>false</CODE> if not.
   */
  public boolean hasAcceptableConfiguration(ConfigEntry configEntry,
                                            List<String> unacceptableReasons)
  {
    boolean configAcceptable = true;


    // Make sure that the entry has a valid set of match attributes.
    int msgID = MSGID_EXACTMAP_DESCRIPTION_MATCH_ATTR;
    StringConfigAttribute attrStub =
         new StringConfigAttribute(ATTR_MATCH_ATTRIBUTE, getMessage(msgID),
                                   true, true, false);
    try
    {
      StringConfigAttribute attrAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(attrStub);
      if (attrAttr == null)
      {
        msgID = MSGID_EXACTMAP_NO_MATCH_ATTR;
        unacceptableReasons.add(getMessage(msgID,
                                           String.valueOf(configEntryDN)));
        configAcceptable = false;
      }
      else
      {
        List<String> attrNames = attrAttr.activeValues();
        if (attrNames.size() == 0)
        {
          msgID = MSGID_EXACTMAP_NO_MATCH_ATTR;
          unacceptableReasons.add(getMessage(msgID,
                                             String.valueOf(configEntryDN)));
          configAcceptable = false;
        }

        for (String attrName : attrNames)
        {
          String lowerName = toLowerCase(attrName);
          AttributeType attrType = DirectoryServer.getAttributeType(lowerName);
          if (attrType == null)
          {
            msgID = MSGID_EXACTMAP_UNKNOWN_ATTR;
            unacceptableReasons.add(getMessage(msgID,
                                               String.valueOf(configEntryDN),
                                               attrName));
            configAcceptable = false;
          }
        }
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_EXACTMAP_CANNOT_DETERMINE_MATCH_ATTR;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(configEntryDN),
                                         stackTraceToSingleLineString(e)));
      configAcceptable = false;
    }


    // Make sure that the entry has a valid set of base DNs.
    msgID = MSGID_EXACTMAP_DESCRIPTION_SEARCH_BASE;
    DNConfigAttribute baseStub =
         new DNConfigAttribute(ATTR_MATCH_BASE, getMessage(msgID), false, true,
                               false);
    try
    {
      DNConfigAttribute baseAttr =
           (DNConfigAttribute) configEntry.getConfigAttribute(baseStub);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_EXACTMAP_CANNOT_DETERMINE_MATCH_BASE;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(configEntryDN),
                                         stackTraceToSingleLineString(e)));
      configAcceptable = false;
    }


    return configAcceptable;
  }



  /**
   * Makes a best-effort attempt to apply the configuration contained in the
   * provided entry.  Information about the result of this processing should be
   * added to the provided message list.  Information should always be added to
   * this list if a configuration change could not be applied.  If detailed
   * results are requested, then information about the changes applied
   * successfully (and optionally about parameters that were not changed) should
   * also be included.
   *
   * @param  configEntry      The entry containing the new configuration to
   *                          apply for this component.
   * @param  detailedResults  Indicates whether detailed information about the
   *                          processing should be added to the list.
   *
   * @return  Information about the result of the configuration update.
   */
  public ConfigChangeResult applyNewConfiguration(ConfigEntry configEntry,
                                                  boolean detailedResults)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();


    // Get the set of match attributes.
    int msgID = MSGID_EXACTMAP_DESCRIPTION_MATCH_ATTR;
    String[] newMatchAttrs = null;
    StringConfigAttribute attrStub =
         new StringConfigAttribute(ATTR_MATCH_ATTRIBUTE, getMessage(msgID),
                                   true, true, false);
    try
    {
      StringConfigAttribute attrAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(attrStub);
      if (attrAttr == null)
      {
        msgID = MSGID_EXACTMAP_NO_MATCH_ATTR;
        messages.add(getMessage(msgID, String.valueOf(configEntryDN)));

        if (resultCode == ResultCode.SUCCESS)
        {
          resultCode = ResultCode.CONSTRAINT_VIOLATION;
        }
      }
      else
      {
        List<String> attrNames = attrAttr.activeValues();
        if (attrNames.size() == 0)
        {
          msgID = MSGID_EXACTMAP_NO_MATCH_ATTR;
          messages.add(getMessage(msgID, String.valueOf(configEntryDN)));

          if (resultCode == ResultCode.SUCCESS)
          {
            resultCode = ResultCode.CONSTRAINT_VIOLATION;
          }
        }
        else
        {
          newMatchAttrs = new String[attrNames.size()];
          for (int i=0; i < newMatchAttrs.length; i++)
          {
            String name      = attrNames.get(i);
            String lowerName = toLowerCase(name);
            AttributeType t = DirectoryServer.getAttributeType(lowerName);
            if (t == null)
            {
              msgID = MSGID_EXACTMAP_UNKNOWN_ATTR;
              messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                                      String.valueOf(attrNames.get(i))));

              if (resultCode == ResultCode.SUCCESS)
              {
                resultCode = ResultCode.CONSTRAINT_VIOLATION;
              }
            }
            else
            {
              newMatchAttrs[i] = name;
            }
          }
        }
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_EXACTMAP_CANNOT_DETERMINE_MATCH_ATTR;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              stackTraceToSingleLineString(e)));

      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = DirectoryServer.getServerErrorResultCode();
      }
    }


    // Get the new set of base DNs.
    msgID = MSGID_EXACTMAP_DESCRIPTION_SEARCH_BASE;
    ASN1OctetString[] newBases = null;
    DNConfigAttribute baseStub =
         new DNConfigAttribute(ATTR_MATCH_BASE, getMessage(msgID), false, true,
                               false);
    try
    {
      DNConfigAttribute baseAttr =
           (DNConfigAttribute) configEntry.getConfigAttribute(baseStub);
      if (baseAttr == null)
      {
        // This is fine -- just use the root DSE.
        newBases = new ASN1OctetString[] { new ASN1OctetString() };
      }
      else
      {
        List<DN> baseDNs = baseAttr.pendingValues();
        if ((baseDNs == null) || baseDNs.isEmpty())
        {
          // This is fine -- just use the root DSE.
          newBases = new ASN1OctetString[] { new ASN1OctetString() };
        }
        else
        {
          newBases = new ASN1OctetString[baseDNs.size()];
          for (int i=0; i < newBases.length; i++)
          {
            newBases[i] = new ASN1OctetString(baseDNs.get(i).toString());
          }
        }
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_EXACTMAP_CANNOT_DETERMINE_MATCH_BASE;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              stackTraceToSingleLineString(e)));

      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = DirectoryServer.getServerErrorResultCode();
      }
    }


    // If everything checks out, then apply the changes.
    if (resultCode == ResultCode.SUCCESS)
    {
      if (! Arrays.equals(rawMatchAttributes, newMatchAttrs))
      {
        rawMatchAttributes = newMatchAttrs;

        if (detailedResults)
        {
          msgID = MSGID_EXACTMAP_UPDATED_MATCH_ATTRS;
          messages.add(getMessage(msgID, String.valueOf(configEntryDN)));
        }
      }

      if (! Arrays.equals(rawSearchBases, newBases))
      {
        rawSearchBases = newBases;

        if (detailedResults)
        {
          msgID = MSGID_EXACTMAP_UPDATED_MATCH_BASES;
          messages.add(getMessage(msgID, String.valueOf(configEntryDN)));
        }
      }
    }


    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }
}

