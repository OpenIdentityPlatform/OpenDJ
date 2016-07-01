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
 * Copyright 2007-2010 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.admin.ads;

import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;

import org.forgerock.opendj.config.ManagedObjectNotFoundException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.server.config.client.LDIFBackendCfgClient;
import org.forgerock.opendj.server.config.client.RootCfgClient;
import org.forgerock.opendj.server.config.meta.BackendCfgDefn;
import org.forgerock.opendj.server.config.meta.LDIFBackendCfgDefn;
import org.opends.admin.ads.ADSContext.ServerProperty;
import org.opends.admin.ads.ADSContextException.ErrorType;
import org.opends.admin.ads.util.ConnectionWrapper;
import org.opends.server.config.ConfigConstants;
import org.opends.server.crypto.CryptoManagerImpl;
import org.opends.server.types.CryptoManagerException;

/**
 * This is the only class in the org.opends.admin.ads package that uses the
 * classes from OpenDS.jar (in particular the administration client framework
 * API).  Before calling this class OpenDS.jar must be
 * loaded.  The goal is basically to centralize in one single place the
 * dependencies of this package on OpenDS.jar.  This is done in order the
 * QuickSetup code to be able to use some of the functionalities provided
 * by the ADSContext classes before OpenDS.jar is downloaded.
 */
class ADSContextHelper
{
  /** Default constructor. */
  public ADSContextHelper()
  {
  }

  /**
   * Creates the Administration Suffix.
   * @param conn the connection to be used.
   * @param backendName the name of the backend where the administration
   * suffix is stored.
   * @throws ADSContextException if the administration suffix could not be
   * created.
   */
  void createAdministrationSuffix(ConnectionWrapper conn, String backendName)
  throws ADSContextException
  {
    try
    {
      RootCfgClient root = conn.getRootConfiguration();
      LDIFBackendCfgClient backend = null;
      try
      {
        backend = (LDIFBackendCfgClient)root.getBackend(backendName);
      }
      catch (ManagedObjectNotFoundException e)
      {
      }
      catch (ClassCastException cce)
      {
        throw new ADSContextException(ErrorType.UNEXPECTED_ADS_BACKEND_TYPE, cce);
      }

      if (backend == null)
      {
        LDIFBackendCfgDefn provider = LDIFBackendCfgDefn.getInstance();
        backend = root.createBackend(provider, backendName, null);
        backend.setEnabled(true);
        backend.setLDIFFile(ADSContext.getAdminLDIFFile());
        backend.setBackendId(backendName);
        backend.setWritabilityMode(BackendCfgDefn.WritabilityMode.ENABLED);
        backend.setIsPrivateBackend(true);
      }
      SortedSet<DN> suffixes = backend.getBaseDN();
      if (suffixes == null)
      {
        suffixes = new TreeSet<>();
      }
      DN newDN = DN.valueOf(ADSContext.getAdministrationSuffixDN());
      if (suffixes.add(newDN))
      {
        backend.setBaseDN(suffixes);
        backend.commit();
      }
    }
    catch (Throwable t)
    {
      throw new ADSContextException(ErrorType.ERROR_UNEXPECTED, t);
    }
  }

  /**
  Register instance key-pair public-key certificate provided in
  serverProperties: generate a key-id attribute if one is not provided (as
  expected); add an instance key public-key certificate entry for the key
  certificate; and associate the certificate entry with the server entry via
  the key ID attribute.
  @param conn the connection on the server we want to update.
  @param serverProperties Properties of the server being registered to which
  the instance key entry belongs.
  @param serverEntryDn The server's ADS entry DN.
  @throws ADSContextException In case some JNDI operation fails or there is a
  problem getting the instance public key certificate ID.
   */
  void registerInstanceKeyCertificate(
      ConnectionWrapper conn, Map<ServerProperty, Object> serverProperties,
      LdapName serverEntryDn)
  throws ADSContextException {
    assert serverProperties.containsKey(
        ServerProperty.INSTANCE_PUBLIC_KEY_CERTIFICATE);
    if (! serverProperties.containsKey(
        ServerProperty.INSTANCE_PUBLIC_KEY_CERTIFICATE)) {
      return;
    }

    // the key ID might be supplied in serverProperties (although, I am unaware of any such case).
    String keyID = (String)serverProperties.get(ServerProperty.INSTANCE_KEY_ID);

    /* these attributes are used both to search for an existing certificate
   entry and, if one does not exist, add a new certificate entry */
    final BasicAttributes keyAttrs = new BasicAttributes();
    final Attribute oc = new BasicAttribute("objectclass");
    oc.add("top"); oc.add("ds-cfg-instance-key");
    keyAttrs.put(oc);
    if (null != keyID) {
      keyAttrs.put(new BasicAttribute(
          ServerProperty.INSTANCE_KEY_ID.getAttributeName(), keyID));
    }
    keyAttrs.put(new BasicAttribute(
        ServerProperty.INSTANCE_PUBLIC_KEY_CERTIFICATE.getAttributeName()
        + ";binary",
        serverProperties.get(
            ServerProperty.INSTANCE_PUBLIC_KEY_CERTIFICATE)));

    /* search for public-key certificate entry in ADS DIT */
    final String attrIDs[] = { "ds-cfg-key-id" };
    NamingEnumeration<SearchResult> results = null;
    try
    {
      results = conn.getLdapContext().search(ADSContext.getInstanceKeysContainerDN(), keyAttrs, attrIDs);
      boolean found = false;
      while (results.hasMore()) {
        final Attribute keyIdAttr =
          results.next().getAttributes().get(attrIDs[0]);
        if (null != keyIdAttr) {
          /* attribute ds-cfg-key-id is the entry is a MUST in the schema */
          keyID = (String)keyIdAttr.get();
        }
        found = true;
      }
      /* TODO: It is possible (but unexpected) that the caller specifies a
   ds-cfg-key-id value for which there is a certificate entry in ADS, but
   the certificate value does not match that supplied by the caller. The
   above search would not return the entry, but the below attempt to add
   an new entry with the supplied ds-cfg-key-id will fail (throw a
   NameAlreadyBoundException) */
      if (!found) {
        /* create key ID, if it was not supplied in serverProperties */
        if (null == keyID) {
          keyID = CryptoManagerImpl.getInstanceKeyID(
              (byte[])serverProperties.get(
                  ServerProperty.INSTANCE_PUBLIC_KEY_CERTIFICATE));
          keyAttrs.put(new BasicAttribute(
              ServerProperty.INSTANCE_KEY_ID.getAttributeName(), keyID));
        }

        /* add public-key certificate entry */
        final LdapName keyDn = new LdapName(
            ServerProperty.INSTANCE_KEY_ID.getAttributeName() + "=" + Rdn.escapeValue(keyID)
                + "," + ADSContext.getInstanceKeysContainerDN());
        conn.getLdapContext().createSubcontext(keyDn, keyAttrs).close();
      }

      if (serverEntryDn != null)
      {
        /* associate server entry with certificate entry via key ID attribute */
        conn.getLdapContext().modifyAttributes(serverEntryDn,
          DirContext.REPLACE_ATTRIBUTE,
          new BasicAttributes(ServerProperty.INSTANCE_KEY_ID.getAttributeName(), keyID));
      }
    }
    catch (NamingException | CryptoManagerException ne)
    {
      throw new ADSContextException(ErrorType.ERROR_UNEXPECTED, ne);
    }
    finally
    {
      handleCloseNamingEnumeration(results);
    }
  }

  /**
   * Returns the crypto instance key objectclass name as defined in
   * ConfigConstants.
   * @return the crypto instance key objectclass name as defined in
   * ConfigConstants.
   */
  public String getOcCryptoInstanceKey()
  {
    return ConfigConstants.OC_CRYPTO_INSTANCE_KEY;
  }

  /**
   * Returns the crypto key compromised time attribute name as defined in
   * ConfigConstants.
   * @return the crypto key compromised time attribute name as defined in
   * ConfigConstants.
   */
  public String getAttrCryptoKeyCompromisedTime()
  {
    return ConfigConstants.ATTR_CRYPTO_KEY_COMPROMISED_TIME;
  }

  private void handleCloseNamingEnumeration(NamingEnumeration<?> ne)
  throws ADSContextException
  {
    if (ne != null)
    {
      try
      {
        ne.close();
      }
      catch (NamingException ex)
      {
        throw new ADSContextException(ErrorType.ERROR_UNEXPECTED, ex);
      }
    }
  }
}
