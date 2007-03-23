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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import javax.security.auth.x500.X500Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;

import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.
            SubjectAttributeToUserAttributeCertificateMapperCfg;
import org.opends.server.api.CertificateMapper;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.AttributeType;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.RDN;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchScope;

import static org.opends.server.loggers.debug.DebugLogger.debugCaught;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.messages.ExtensionsMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.StaticUtils.*;



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
  // The DN of the configuration entry for this certificate mapper.
  private DN configEntryDN;

  // The mappings between certificate attribute names and user attribute types.
  private LinkedHashMap<String,AttributeType> attributeMap;

  // The current configuration for this certificate mapper.
  private SubjectAttributeToUserAttributeCertificateMapperCfg
               currentConfig;



  /**
   * Creates a new instance of this certificate mapper.  Note that all actual
   * initialization should be done in the
   * <CODE>initializeCertificateMapper</CODE> method.
   */
  public SubjectAttributeToUserAttributeCertificateMapper()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  public void initializeCertificateMapper(
                   SubjectAttributeToUserAttributeCertificateMapperCfg
                        configuration)
         throws ConfigException, InitializationException
  {
    configuration
        .addSubjectAttributeToUserAttributeChangeListener(this);

    currentConfig = configuration;
    configEntryDN = configuration.dn();

    // Get and validate the subject attribute to user attribute mappings.
    LinkedHashMap<String,AttributeType> attributeMap =
         new LinkedHashMap<String,AttributeType>();
    for (String mapStr : configuration.getSubjectAttributeMapping())
    {
      String lowerMap = toLowerCase(mapStr);
      int colonPos = lowerMap.indexOf(':');
      if (colonPos <= 0)
      {
        int    msgID   = MSGID_SATUACM_INVALID_MAP_FORMAT;
        String message = getMessage(msgID, String.valueOf(configEntryDN),
                                    mapStr);
        throw new ConfigException(msgID, message);
      }

      String certAttrName = lowerMap.substring(0, colonPos).trim();
      String userAttrName = lowerMap.substring(colonPos+1).trim();
      if ((certAttrName.length() == 0) || (userAttrName.length() == 0))
      {
        int    msgID   = MSGID_SATUACM_INVALID_MAP_FORMAT;
        String message = getMessage(msgID, String.valueOf(configEntryDN),
                                    mapStr);
        throw new ConfigException(msgID, message);
      }

      if (attributeMap.containsKey(certAttrName))
      {
        int    msgID   = MSGID_SATUACM_DUPLICATE_CERT_ATTR;
        String message = getMessage(msgID, String.valueOf(configEntryDN),
                                    certAttrName);
        throw new ConfigException(msgID, message);
      }

      AttributeType userAttrType =
           DirectoryServer.getAttributeType(userAttrName, false);
      if (userAttrType == null)
      {
        int    msgID   = MSGID_SATUACM_NO_SUCH_ATTR;
        String message = getMessage(msgID, mapStr,
                                    String.valueOf(configEntryDN),
                                    userAttrName);
        throw new ConfigException(msgID, message);
      }

      for (AttributeType attrType : attributeMap.values())
      {
        if (attrType.equals(userAttrType))
        {
          int    msgID   = MSGID_SATUACM_DUPLICATE_USER_ATTR;
          String message = getMessage(msgID, String.valueOf(configEntryDN),
                                      attrType.getNameOrOID());
          throw new ConfigException(msgID, message);
        }
      }

      attributeMap.put(certAttrName, userAttrType);
    }
  }



  /**
   * {@inheritDoc}
   */
  public void finalizeCertificateMapper()
  {
    currentConfig
        .removeSubjectAttributeToUserAttributeChangeListener(this);
  }



  /**
   * {@inheritDoc}
   */
  public Entry mapCertificateToUser(Certificate[] certificateChain)
         throws DirectoryException
  {
    SubjectAttributeToUserAttributeCertificateMapperCfg config =
         currentConfig;
    LinkedHashMap<String,AttributeType> attributeMap = this.attributeMap;


    // Make sure that a peer certificate was provided.
    if ((certificateChain == null) || (certificateChain.length == 0))
    {
      int    msgID   = MSGID_SATUACM_NO_PEER_CERTIFICATE;
      String message = getMessage(msgID);
      throw new DirectoryException(ResultCode.INVALID_CREDENTIALS, message,
                                   msgID);
    }


    // Get the first certificate in the chain.  It must be an X.509 certificate.
    X509Certificate peerCertificate;
    try
    {
      peerCertificate = (X509Certificate) certificateChain[0];
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_SATUACM_PEER_CERT_NOT_X509;
      String message =
           getMessage(msgID, String.valueOf(certificateChain[0].getType()));
      throw new DirectoryException(ResultCode.INVALID_CREDENTIALS, message,
                                   msgID);
    }


    // Get the subject from the peer certificate and use it to create a search
    // filter.
    DN peerDN;
    X500Principal peerPrincipal = peerCertificate.getSubjectX500Principal();
    String peerName = peerPrincipal.getName(X500Principal.RFC2253);
    try
    {
      peerDN = DN.decode(peerName);
    }
    catch (DirectoryException de)
    {
      int    msgID   = MSGID_SATUACM_CANNOT_DECODE_SUBJECT_AS_DN;
      String message = getMessage(msgID, peerName, de.getErrorMessage());
      throw new DirectoryException(ResultCode.INVALID_CREDENTIALS, message,
                                   msgID, de);
    }

    LinkedList<SearchFilter> filterComps = new LinkedList<SearchFilter>();
    for (int i=0; i < peerDN.getNumComponents(); i++)
    {
      RDN rdn = peerDN.getRDN(i);
      for (int j=0; j < rdn.getNumValues(); j++)
      {
        String lowerName = toLowerCase(rdn.getAttributeName(j));
        AttributeType attrType = attributeMap.get(lowerName);
        if (attrType != null)
        {
          filterComps.add(SearchFilter.createEqualityFilter(attrType,
                                            rdn.getAttributeValue(j)));
        }
      }
    }

    if (filterComps.isEmpty())
    {
      int    msgID   = MSGID_SATUACM_NO_MAPPABLE_ATTRIBUTES;
      String message = getMessage(msgID, peerName);
      throw new DirectoryException(ResultCode.INVALID_CREDENTIALS, message,
                                   msgID);
    }

    SearchFilter filter = SearchFilter.createANDFilter(filterComps);


    // If we have an explicit set of base DNs, then use it.  Otherwise, use the
    // set of public naming contexts in the server.
    Collection<DN> baseDNs = config.getUserBaseDN();
    if ((baseDNs == null) || baseDNs.isEmpty())
    {
      baseDNs = DirectoryServer.getPublicNamingContexts().keySet();
    }


    // For each base DN, issue an internal search in an attempt to map the
    // certificate.
    Entry userEntry = null;
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    for (DN baseDN : baseDNs)
    {
      InternalSearchOperation searchOperation =
           conn.processSearch(baseDN, SearchScope.WHOLE_SUBTREE, filter);
      for (SearchResultEntry entry : searchOperation.getSearchEntries())
      {
        if (userEntry == null)
        {
          userEntry = entry;
        }
        else
        {
          int    msgID   = MSGID_SATUACM_MULTIPLE_MATCHING_ENTRIES;
          String message = getMessage(msgID, peerName,
                                      String.valueOf(userEntry.getDN()),
                                      String.valueOf(entry.getDN()));
          throw new DirectoryException(ResultCode.INVALID_CREDENTIALS, message,
                                       msgID);
        }
      }
    }


    // If we've gotten here, then we either found exactly one user entry or we
    // didn't find any.  Either way, return the entry or null to the caller.
    return userEntry;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
              SubjectAttributeToUserAttributeCertificateMapperCfg
                   configuration,
              List<String> unacceptableReasons)
  {
    boolean configAcceptable = true;

    // Get and validate the subject attribute to user attribute mappings.
    LinkedHashMap<String,AttributeType> newAttributeMap =
         new LinkedHashMap<String,AttributeType>();
mapLoop:
    for (String mapStr : configuration.getSubjectAttributeMapping())
    {
      String lowerMap = toLowerCase(mapStr);
      int colonPos = lowerMap.indexOf(':');
      if (colonPos <= 0)
      {
        int msgID = MSGID_SATUACM_INVALID_MAP_FORMAT;
        unacceptableReasons.add(getMessage(msgID, String.valueOf(configEntryDN),
                                           mapStr));
        configAcceptable = false;
        break;
      }

      String certAttrName = lowerMap.substring(0, colonPos).trim();
      String userAttrName = lowerMap.substring(colonPos+1).trim();
      if ((certAttrName.length() == 0) || (userAttrName.length() == 0))
      {
        int msgID = MSGID_SATUACM_INVALID_MAP_FORMAT;
        unacceptableReasons.add(getMessage(msgID, String.valueOf(configEntryDN),
                                           mapStr));
        configAcceptable = false;
        break;
      }

      if (newAttributeMap.containsKey(certAttrName))
      {
        int msgID = MSGID_SATUACM_DUPLICATE_CERT_ATTR;
        unacceptableReasons.add(getMessage(msgID, String.valueOf(configEntryDN),
                                           certAttrName));
        configAcceptable = false;
        break;
      }

      AttributeType userAttrType =
           DirectoryServer.getAttributeType(userAttrName, false);
      if (userAttrType == null)
      {
        int msgID = MSGID_SATUACM_NO_SUCH_ATTR;
        unacceptableReasons.add(getMessage(msgID, mapStr,
                                           String.valueOf(configEntryDN),
                                           userAttrName));
        configAcceptable = false;
        break;
      }

      for (AttributeType attrType : newAttributeMap.values())
      {
        if (attrType.equals(userAttrType))
        {
          int msgID = MSGID_SATUACM_DUPLICATE_USER_ATTR;
          unacceptableReasons.add(getMessage(msgID,
                                             String.valueOf(configEntryDN),
                                             attrType.getNameOrOID()));
          configAcceptable = false;
          break mapLoop;
        }
      }

      newAttributeMap.put(certAttrName, userAttrType);
    }


    return configAcceptable;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
              SubjectAttributeToUserAttributeCertificateMapperCfg
                   configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();


    // Get and validate the subject attribute to user attribute mappings.
    LinkedHashMap<String,AttributeType> newAttributeMap =
         new LinkedHashMap<String,AttributeType>();
mapLoop:
    for (String mapStr : configuration.getSubjectAttributeMapping())
    {
      String lowerMap = toLowerCase(mapStr);
      int colonPos = lowerMap.indexOf(':');
      if (colonPos <= 0)
      {
        if (resultCode == ResultCode.SUCCESS)
        {
          resultCode = ResultCode.CONSTRAINT_VIOLATION;
        }

        int msgID = MSGID_SATUACM_INVALID_MAP_FORMAT;
        messages.add(getMessage(msgID, String.valueOf(configEntryDN), mapStr));
        break;
      }

      String certAttrName = lowerMap.substring(0, colonPos).trim();
      String userAttrName = lowerMap.substring(colonPos+1).trim();
      if ((certAttrName.length() == 0) || (userAttrName.length() == 0))
      {
        if (resultCode == ResultCode.SUCCESS)
        {
          resultCode = ResultCode.CONSTRAINT_VIOLATION;
        }

        int msgID = MSGID_SATUACM_INVALID_MAP_FORMAT;
        messages.add(getMessage(msgID, String.valueOf(configEntryDN), mapStr));
        break;
      }

      if (newAttributeMap.containsKey(certAttrName))
      {
        if (resultCode == ResultCode.SUCCESS)
        {
          resultCode = ResultCode.CONSTRAINT_VIOLATION;
        }

        int msgID = MSGID_SATUACM_DUPLICATE_CERT_ATTR;
        messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                                certAttrName));
        break;
      }

      AttributeType userAttrType =
           DirectoryServer.getAttributeType(userAttrName, false);
      if (userAttrType == null)
      {
        if (resultCode == ResultCode.SUCCESS)
        {
          resultCode = ResultCode.CONSTRAINT_VIOLATION;
        }

        int msgID = MSGID_SATUACM_NO_SUCH_ATTR;
        messages.add(getMessage(msgID, mapStr, String.valueOf(configEntryDN),
                                userAttrName));
        break;
      }

      for (AttributeType attrType : newAttributeMap.values())
      {
        if (attrType.equals(userAttrType))
        {
          if (resultCode == ResultCode.SUCCESS)
          {
            resultCode = ResultCode.CONSTRAINT_VIOLATION;
          }

          int msgID = MSGID_SATUACM_DUPLICATE_USER_ATTR;
          messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                                  attrType.getNameOrOID()));
          break mapLoop;
        }
      }

      newAttributeMap.put(certAttrName, userAttrType);
    }


    if (resultCode == ResultCode.SUCCESS)
    {
      attributeMap  = newAttributeMap;
      currentConfig = configuration;
    }


   return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }
}

