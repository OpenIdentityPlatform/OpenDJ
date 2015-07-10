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
 *      Copyright 2007-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2012-2015 ForgeRock AS
 *      Portions Copyright 2013 Manuel Gaupp
 */
package org.opends.server.extensions;

import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.opends.server.util.StaticUtils.*;

import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.security.auth.x500.X500Principal;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.CertificateMapperCfg;
import org.opends.server.admin.std.server.SubjectAttributeToUserAttributeCertificateMapperCfg;
import org.opends.server.api.Backend;
import org.opends.server.api.CertificateMapper;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.IndexType;
import org.opends.server.types.InitializationException;
import org.opends.server.types.RDN;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchResultEntry;

/**
 * This class implements a very simple Directory Server certificate mapper that
 * will map a certificate to a user based on attributes contained in both the
 * certificate subject and the user's entry.  The configuration may include
 * mappings from certificate attributes to attributes in user entries, and all
 * of those certificate attributes that are present in the subject will be used
 * to search for matching user entries.
 */
public class SubjectAttributeToUserAttributeCertificateMapper
       extends CertificateMapper<
               SubjectAttributeToUserAttributeCertificateMapperCfg>
       implements ConfigurationChangeListener<
                  SubjectAttributeToUserAttributeCertificateMapperCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The DN of the configuration entry for this certificate mapper. */
  private DN configEntryDN;
  /** The mappings between certificate attribute names and user attribute types. */
  private LinkedHashMap<String,AttributeType> attributeMap;
  /** The current configuration for this certificate mapper. */
  private SubjectAttributeToUserAttributeCertificateMapperCfg currentConfig;
  /** The set of attributes to return in search result entries. */
  private LinkedHashSet<String> requestedAttributes;


  /**
   * Creates a new instance of this certificate mapper.  Note that all actual
   * initialization should be done in the
   * <CODE>initializeCertificateMapper</CODE> method.
   */
  public SubjectAttributeToUserAttributeCertificateMapper()
  {
    super();
  }



  /** {@inheritDoc} */
  @Override
  public void initializeCertificateMapper(
      SubjectAttributeToUserAttributeCertificateMapperCfg configuration)
         throws ConfigException, InitializationException
  {
    configuration.addSubjectAttributeToUserAttributeChangeListener(this);

    currentConfig = configuration;
    configEntryDN = configuration.dn();

    // Get and validate the subject attribute to user attribute mappings.
    ConfigChangeResult ccr = new ConfigChangeResult();
    attributeMap = buildAttributeMap(configuration, configEntryDN, ccr);
    List<LocalizableMessage> messages = ccr.getMessages();
    if (!messages.isEmpty())
    {
      throw new ConfigException(messages.iterator().next());
    }

    // Make sure that all the user attributes are configured with equality
    // indexes in all appropriate backends.
    Set<DN> cfgBaseDNs = getUserBaseDNs(configuration);
    for (DN baseDN : cfgBaseDNs)
    {
      for (AttributeType t : attributeMap.values())
      {
        Backend<?> b = DirectoryServer.getBackend(baseDN);
        if (b != null && ! b.isIndexed(t, IndexType.EQUALITY))
        {
          logger.warn(WARN_SATUACM_ATTR_UNINDEXED, configuration.dn(),
              t.getNameOrOID(), b.getBackendID());
        }
      }
    }

    // Create the attribute list to include in search requests. We want to
    // include all user and operational attributes.
    requestedAttributes = new LinkedHashSet<>(2);
    requestedAttributes.add("*");
    requestedAttributes.add("+");
  }

  /** {@inheritDoc} */
  @Override
  public void finalizeCertificateMapper()
  {
    currentConfig.removeSubjectAttributeToUserAttributeChangeListener(this);
  }



  /** {@inheritDoc} */
  @Override
  public Entry mapCertificateToUser(Certificate[] certificateChain)
         throws DirectoryException
  {
    SubjectAttributeToUserAttributeCertificateMapperCfg config = currentConfig;
    LinkedHashMap<String,AttributeType> theAttributeMap = this.attributeMap;


    // Make sure that a peer certificate was provided.
    if ((certificateChain == null) || (certificateChain.length == 0))
    {
      LocalizableMessage message = ERR_SATUACM_NO_PEER_CERTIFICATE.get();
      throw new DirectoryException(ResultCode.INVALID_CREDENTIALS, message);
    }


    // Get the first certificate in the chain.  It must be an X.509 certificate.
    X509Certificate peerCertificate;
    try
    {
      peerCertificate = (X509Certificate) certificateChain[0];
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message = ERR_SATUACM_PEER_CERT_NOT_X509.get(certificateChain[0].getType());
      throw new DirectoryException(ResultCode.INVALID_CREDENTIALS, message);
    }


    // Get the subject from the peer certificate and use it to create a search
    // filter.
    DN peerDN;
    X500Principal peerPrincipal = peerCertificate.getSubjectX500Principal();
    String peerName = peerPrincipal.getName(X500Principal.RFC2253);
    try
    {
      peerDN = DN.valueOf(peerName);
    }
    catch (DirectoryException de)
    {
      LocalizableMessage message = ERR_SATUACM_CANNOT_DECODE_SUBJECT_AS_DN.get(
          peerName, de.getMessageObject());
      throw new DirectoryException(ResultCode.INVALID_CREDENTIALS, message, de);
    }

    LinkedList<SearchFilter> filterComps = new LinkedList<>();
    for (int i=0; i < peerDN.size(); i++)
    {
      RDN rdn = peerDN.getRDN(i);
      for (int j=0; j < rdn.getNumValues(); j++)
      {
        String lowerName = toLowerCase(rdn.getAttributeName(j));

        // Try to normalize lowerName
        lowerName = normalizeAttributeName(lowerName);

        AttributeType attrType = theAttributeMap.get(lowerName);
        if (attrType != null)
        {
          filterComps.add(SearchFilter.createEqualityFilter(attrType, rdn.getAttributeValue(j)));
        }
      }
    }

    if (filterComps.isEmpty())
    {
      LocalizableMessage message = ERR_SATUACM_NO_MAPPABLE_ATTRIBUTES.get(peerDN);
      throw new DirectoryException(ResultCode.INVALID_CREDENTIALS, message);
    }

    SearchFilter filter = SearchFilter.createANDFilter(filterComps);
    Collection<DN> baseDNs = getUserBaseDNs(config);

    // For each base DN, issue an internal search in an attempt to map the certificate.
    Entry userEntry = null;
    InternalClientConnection conn = getRootConnection();
    for (DN baseDN : baseDNs)
    {
      final SearchRequest request = newSearchRequest(baseDN, SearchScope.WHOLE_SUBTREE, filter)
          .setSizeLimit(1)
          .setTimeLimit(10)
          .addAttribute(requestedAttributes);
      InternalSearchOperation searchOperation = conn.processSearch(request);

      switch (searchOperation.getResultCode().asEnum())
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
          LocalizableMessage message = ERR_SATUACM_MULTIPLE_SEARCH_MATCHING_ENTRIES.get(peerDN);
          throw new DirectoryException(ResultCode.INVALID_CREDENTIALS, message);

        case TIME_LIMIT_EXCEEDED:
        case ADMIN_LIMIT_EXCEEDED:
          // The search criteria was too inefficient.
          message = ERR_SATUACM_INEFFICIENT_SEARCH.get(peerDN, searchOperation.getErrorMessage());
          throw new DirectoryException(searchOperation.getResultCode(), message);

        default:
          // Just pass on the failure that was returned for this search.
          message = ERR_SATUACM_SEARCH_FAILED.get(peerDN, searchOperation.getErrorMessage());
          throw new DirectoryException(searchOperation.getResultCode(), message);
      }

      for (SearchResultEntry entry : searchOperation.getSearchEntries())
      {
        if (userEntry == null)
        {
          userEntry = entry;
        }
        else
        {
          LocalizableMessage message = ERR_SATUACM_MULTIPLE_MATCHING_ENTRIES.
              get(peerDN, userEntry.getName(), entry.getName());
          throw new DirectoryException(ResultCode.INVALID_CREDENTIALS, message);
        }
      }
    }


    // If we've gotten here, then we either found exactly one user entry or we
    // didn't find any.  Either way, return the entry or null to the caller.
    return userEntry;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationAcceptable(CertificateMapperCfg configuration,
                                           List<LocalizableMessage> unacceptableReasons)
  {
    SubjectAttributeToUserAttributeCertificateMapperCfg config =
         (SubjectAttributeToUserAttributeCertificateMapperCfg) configuration;
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }



  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationChangeAcceptable(
              SubjectAttributeToUserAttributeCertificateMapperCfg configuration,
              List<LocalizableMessage> unacceptableReasons)
  {
    ConfigChangeResult ccr = new ConfigChangeResult();
    buildAttributeMap(configuration, configuration.dn(), ccr);
    unacceptableReasons.addAll(ccr.getMessages());
    return ResultCode.SUCCESS.equals(ccr.getResultCode());
  }

  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationChange(SubjectAttributeToUserAttributeCertificateMapperCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();
    LinkedHashMap<String, AttributeType> newAttributeMap = buildAttributeMap(configuration, configEntryDN, ccr);

    // Make sure that all the user attributes are configured with equality
    // indexes in all appropriate backends.
    Set<DN> cfgBaseDNs = getUserBaseDNs(configuration);
    for (DN baseDN : cfgBaseDNs)
    {
      for (AttributeType t : newAttributeMap.values())
      {
        Backend<?> b = DirectoryServer.getBackend(baseDN);
        if (b != null && !b.isIndexed(t, IndexType.EQUALITY))
        {
          LocalizableMessage message =
              WARN_SATUACM_ATTR_UNINDEXED.get(configuration.dn(), t.getNameOrOID(), b.getBackendID());
          ccr.addMessage(message);
          logger.error(message);
        }
      }
    }

    if (ccr.getResultCode() == ResultCode.SUCCESS)
    {
      attributeMap = newAttributeMap;
      currentConfig = configuration;
    }

    return ccr;
  }

  /**
   * If we have an explicit set of base DNs, then use it.
   * Otherwise, use the set of public naming contexts in the server.
   */
  private Set<DN> getUserBaseDNs(SubjectAttributeToUserAttributeCertificateMapperCfg config)
  {
    Set<DN> baseDNs = config.getUserBaseDN();
    if ((baseDNs == null) || baseDNs.isEmpty())
    {
      baseDNs = DirectoryServer.getPublicNamingContexts().keySet();
    }
    return baseDNs;
  }

  /** Get and validate the subject attribute to user attribute mappings. */
  private LinkedHashMap<String, AttributeType> buildAttributeMap(
      SubjectAttributeToUserAttributeCertificateMapperCfg configuration, DN cfgEntryDN, ConfigChangeResult ccr)
  {
    LinkedHashMap<String, AttributeType> results = new LinkedHashMap<>();
    for (String mapStr : configuration.getSubjectAttributeMapping())
    {
      String lowerMap = toLowerCase(mapStr);
      int colonPos = lowerMap.indexOf(':');
      if (colonPos <= 0)
      {
        ccr.setResultCodeIfSuccess(ResultCode.CONSTRAINT_VIOLATION);
        ccr.addMessage(ERR_SATUACM_INVALID_MAP_FORMAT.get(cfgEntryDN, mapStr));
        return null;
      }

      String certAttrName = lowerMap.substring(0, colonPos).trim();
      String userAttrName = lowerMap.substring(colonPos+1).trim();
      if ((certAttrName.length() == 0) || (userAttrName.length() == 0))
      {
        ccr.setResultCodeIfSuccess(ResultCode.CONSTRAINT_VIOLATION);
        ccr.addMessage(ERR_SATUACM_INVALID_MAP_FORMAT.get(cfgEntryDN, mapStr));
        return null;
      }

      // Try to normalize the provided certAttrName
      certAttrName = normalizeAttributeName(certAttrName);
      if (results.containsKey(certAttrName))
      {
        ccr.setResultCodeIfSuccess(ResultCode.CONSTRAINT_VIOLATION);
        ccr.addMessage(ERR_SATUACM_DUPLICATE_CERT_ATTR.get(cfgEntryDN, certAttrName));
        return null;
      }

      AttributeType userAttrType = DirectoryServer.getAttributeType(userAttrName);
      if (userAttrType == null)
      {
        ccr.setResultCodeIfSuccess(ResultCode.CONSTRAINT_VIOLATION);
        ccr.addMessage(ERR_SATUACM_NO_SUCH_ATTR.get(mapStr, cfgEntryDN, userAttrName));
        return null;
      }
      if (results.values().contains(userAttrType))
      {
        ccr.setResultCodeIfSuccess(ResultCode.CONSTRAINT_VIOLATION);
        ccr.addMessage(ERR_SATUACM_DUPLICATE_USER_ATTR.get(cfgEntryDN, userAttrType.getNameOrOID()));
        return null;
      }

      results.put(certAttrName, userAttrType);
    }
    return results;
  }



  /**
   * Tries to normalize the given attribute name; if normalization is not
   * possible the original String value is returned.
   *
   * @param   attrName  The attribute name which should be normalized.
   *
   * @return  The normalized attribute name.
   */
  private static String normalizeAttributeName(String attrName)
  {
    AttributeType attrType = DirectoryServer.getAttributeType(attrName);
    if (attrType != null)
    {
      String attrNameNormalized = attrType.getNormalizedPrimaryName();
      if (attrNameNormalized != null)
      {
         attrName = attrNameNormalized;
      }
    }
    return attrName;
  }
}
