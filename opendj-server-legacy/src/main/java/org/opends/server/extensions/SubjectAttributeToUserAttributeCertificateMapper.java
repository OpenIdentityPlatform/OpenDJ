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
 * Copyright 2007-2008 Sun Microsystems, Inc.
 * Portions Copyright 2012-2016 ForgeRock AS.
 * Portions Copyright 2013 Manuel Gaupp
 */
package org.opends.server.extensions;

import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.opends.server.util.CollectionUtils.*;
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
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.ldap.AVA;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.RDN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.server.config.server.CertificateMapperCfg;
import org.forgerock.opendj.server.config.server.SubjectAttributeToUserAttributeCertificateMapperCfg;
import org.opends.server.api.Backend;
import org.opends.server.api.CertificateMapper;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.IndexType;
import org.opends.server.types.InitializationException;
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

  @Override
  public void initializeCertificateMapper(
      SubjectAttributeToUserAttributeCertificateMapperCfg configuration)
         throws ConfigException, InitializationException
  {
    configuration.addSubjectAttributeToUserAttributeChangeListener(this);

    currentConfig = configuration;

    // Get and validate the subject attribute to user attribute mappings.
    ConfigChangeResult ccr = new ConfigChangeResult();
    attributeMap = buildAttributeMap(configuration, ccr);
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
    requestedAttributes = newLinkedHashSet("*", "+");
  }

  @Override
  public void finalizeCertificateMapper()
  {
    currentConfig.removeSubjectAttributeToUserAttributeChangeListener(this);
  }

  @Override
  public Entry mapCertificateToUser(Certificate[] certificateChain)
         throws DirectoryException
  {
    SubjectAttributeToUserAttributeCertificateMapperCfg config = currentConfig;
    LinkedHashMap<String,AttributeType> theAttributeMap = this.attributeMap;

    // Make sure that a peer certificate was provided.
    if (certificateChain == null || certificateChain.length == 0)
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
    catch (ClassCastException e)
    {
      logger.traceException(e);

      LocalizableMessage message = ERR_SATUACM_PEER_CERT_NOT_X509.get(certificateChain[0].getType());
      throw new DirectoryException(ResultCode.INVALID_CREDENTIALS, message);
    }

    // Get the subject from the peer certificate and use it to create a search filter
    DN peerDN;
    X500Principal peerPrincipal = peerCertificate.getSubjectX500Principal();
    String peerName = peerPrincipal.getName(X500Principal.RFC2253);
    try
    {
      peerDN = DN.valueOf(peerName);
    }
    catch (LocalizedIllegalArgumentException de)
    {
      LocalizableMessage message = ERR_SATUACM_CANNOT_DECODE_SUBJECT_AS_DN.get(
          peerName, de.getMessageObject());
      throw new DirectoryException(ResultCode.INVALID_CREDENTIALS, message, de);
    }

    LinkedList<SearchFilter> filterComps = new LinkedList<>();
    for (RDN rdn : peerDN)
    {
      for (AVA ava : rdn)
      {
        String lowerName = normalizeAttributeName(ava.getAttributeName());
        AttributeType attrType = theAttributeMap.get(lowerName);
        if (attrType != null)
        {
          filterComps.add(SearchFilter.createEqualityFilter(attrType, ava.getAttributeValue()));
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

    // We either found exactly one user entry or we did not find any.
    return userEntry;
  }

  @Override
  public boolean isConfigurationAcceptable(CertificateMapperCfg configuration,
                                           List<LocalizableMessage> unacceptableReasons)
  {
    SubjectAttributeToUserAttributeCertificateMapperCfg config =
         (SubjectAttributeToUserAttributeCertificateMapperCfg) configuration;
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
              SubjectAttributeToUserAttributeCertificateMapperCfg configuration,
              List<LocalizableMessage> unacceptableReasons)
  {
    ConfigChangeResult ccr = new ConfigChangeResult();
    buildAttributeMap(configuration, ccr);
    unacceptableReasons.addAll(ccr.getMessages());
    return ResultCode.SUCCESS.equals(ccr.getResultCode());
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(SubjectAttributeToUserAttributeCertificateMapperCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();
    LinkedHashMap<String, AttributeType> newAttributeMap = buildAttributeMap(configuration, ccr);

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
    if (baseDNs == null || baseDNs.isEmpty())
    {
      baseDNs = DirectoryServer.getPublicNamingContexts().keySet();
    }
    return baseDNs;
  }

  /** Get and validate the subject attribute to user attribute mappings. */
  private LinkedHashMap<String, AttributeType> buildAttributeMap(
      SubjectAttributeToUserAttributeCertificateMapperCfg cfg, ConfigChangeResult ccr)
  {
    LinkedHashMap<String, AttributeType> results = new LinkedHashMap<>();
    for (String mapStr : cfg.getSubjectAttributeMapping())
    {
      String lowerMap = toLowerCase(mapStr);
      int colonPos = lowerMap.indexOf(':');
      if (colonPos <= 0)
      {
        ccr.setResultCodeIfSuccess(ResultCode.CONSTRAINT_VIOLATION);
        ccr.addMessage(ERR_SATUACM_INVALID_MAP_FORMAT.get(cfg.dn(), mapStr));
        return null;
      }

      String certAttrName = lowerMap.substring(0, colonPos).trim();
      String userAttrName = lowerMap.substring(colonPos+1).trim();
      if (certAttrName.length() == 0 || userAttrName.length() == 0)
      {
        ccr.setResultCodeIfSuccess(ResultCode.CONSTRAINT_VIOLATION);
        ccr.addMessage(ERR_SATUACM_INVALID_MAP_FORMAT.get(cfg.dn(), mapStr));
        return null;
      }

      // Try to normalize the provided certAttrName
      certAttrName = normalizeAttributeName(certAttrName);
      if (results.containsKey(certAttrName))
      {
        ccr.setResultCodeIfSuccess(ResultCode.CONSTRAINT_VIOLATION);
        ccr.addMessage(ERR_SATUACM_DUPLICATE_CERT_ATTR.get(cfg.dn(), certAttrName));
        return null;
      }

      AttributeType userAttrType = DirectoryServer.getSchema().getAttributeType(userAttrName);
      if (userAttrType.isPlaceHolder())
      {
        ccr.setResultCodeIfSuccess(ResultCode.CONSTRAINT_VIOLATION);
        ccr.addMessage(ERR_SATUACM_NO_SUCH_ATTR.get(mapStr, cfg.dn(), userAttrName));
        return null;
      }
      if (results.values().contains(userAttrType))
      {
        ccr.setResultCodeIfSuccess(ResultCode.CONSTRAINT_VIOLATION);
        ccr.addMessage(ERR_SATUACM_DUPLICATE_USER_ATTR.get(cfg.dn(), userAttrType.getNameOrOID()));
        return null;
      }

      results.put(certAttrName, userAttrType);
    }
    return results;
  }

  private static String normalizeAttributeName(String attrName)
  {
    return toLowerCase(DirectoryServer.getSchema().getAttributeType(attrName).getNameOrOID());
  }
}
