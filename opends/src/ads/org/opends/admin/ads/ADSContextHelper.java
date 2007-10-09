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

package org.opends.admin.ads;

import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;

import org.opends.admin.ads.ADSContext.ServerProperty;
import org.opends.server.admin.ManagedObjectNotFoundException;
import org.opends.server.admin.client.ManagementContext;
import org.opends.server.admin.client.ldap.JNDIDirContextAdaptor;
import org.opends.server.admin.client.ldap.LDAPManagementContext;
import org.opends.server.admin.std.client.*;
import org.opends.server.admin.std.meta.BackendCfgDefn;
import org.opends.server.admin.std.meta.LDIFBackendCfgDefn;
import org.opends.server.config.ConfigConstants;
import org.opends.server.types.CryptoManager;
import org.opends.server.types.DN;

/**
 * This is the only class in the org.opends.admin.ads package that uses the
 * classes from OpenDS.jar (in particular the administration client framework
 * API).  Before calling this class OpenDS.jar must be
 * loaded.  The goal is basically to centralize in one single place the
 * dependencies of this package on OpenDS.jar.  This is done in order the
 * QuickSetup code to be able to use some of the functionalities provided
 * by the ADSContext classes before OpenDS.jar is downloaded.
 */
public class ADSContextHelper
{
  /**
   * Default constructor.
   */
  public ADSContextHelper()
  {
  }
  /**
   * Removes the administration suffix.
   * @param ctx the DirContext to be used.
   * @param backendName the name of the backend where the administration
   * suffix is stored.
   * @throws ADSContextException if the administration suffix could not be
   * removed.
   */
  public void removeAdministrationSuffix(InitialLdapContext ctx,
      String backendName) throws ADSContextException
  {
    try
    {
      ManagementContext mCtx = LDAPManagementContext.createFromContext(
          JNDIDirContextAdaptor.adapt(ctx));
      RootCfgClient root = mCtx.getRootConfiguration();
      BackendCfgClient backend = null;
      try
      {
        backend = root.getBackend(backendName);
      }
      catch (ManagedObjectNotFoundException monfe)
      {
        // It does not exist.
      }
      if (backend != null)
      {
        SortedSet<DN> suffixes = backend.getBaseDN();
        if (suffixes != null)
        {
          if (suffixes.remove(
              DN.decode(ADSContext.getAdministrationSuffixDN())))
          {
            if (suffixes.size() > 0)
            {
              backend.setBaseDN(suffixes);
              backend.commit();
            }
            else
            {
              root.removeBackend(backendName);
            }
          }
        }
      }
    }
    catch (Throwable t)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.ERROR_UNEXPECTED, t);
    }
  }

  /**
   * Creates the Administration Suffix.
   * @param ctx the DirContext to be used.
   * @param backendName the name of the backend where the administration
   * suffix is stored.
   * @throws ADSContextException if the administration suffix could not be
   * created.
   */
  public void createAdministrationSuffix(InitialLdapContext ctx,
      String backendName)
  throws ADSContextException
  {
      try
    {
      ManagementContext mCtx = LDAPManagementContext.createFromContext(
          JNDIDirContextAdaptor.adapt(ctx));
      RootCfgClient root = mCtx.getRootConfiguration();
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
        throw new ADSContextException(
            ADSContextException.ErrorType.UNEXPECTED_ADS_BACKEND_TYPE, cce);
      }
      if (backend == null)
      {
        LDIFBackendCfgDefn provider = LDIFBackendCfgDefn.getInstance();
        backend = root.createBackend(provider, backendName, null);
        backend.setEnabled(true);
        backend.setBackendId(backendName);
        backend.setWritabilityMode(BackendCfgDefn.WritabilityMode.ENABLED);
      }
      SortedSet<DN> suffixes = backend.getBaseDN();
      if (suffixes == null)
      {
        suffixes = new TreeSet<DN>();
      }
      DN newDN = DN.decode(ADSContext.getAdministrationSuffixDN());
      if (suffixes.contains(newDN))
      {
        suffixes.add(newDN);
        backend.setBaseDN(suffixes);
        backend.commit();
      }
    }
    catch (Throwable t)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.ERROR_UNEXPECTED, t);
    }
  }

  /**
  Register instance key-pair public-key certificate provided in
  serverProperties: generate a key-id attribute if one is not provided (as
  expected); add an instance key public-key certificate entry for the key
  certificate; and associate the certificate entry with the server entry via
  the key ID attribute.
  @param ctx the InitialLdapContext on the server we want to update.
  @param serverProperties Properties of the server being registered to which
  the instance key entry belongs.
  @param serverEntryDn The server's ADS entry DN.
  @throws ADSContextException In case some JNDI operation fails or there is a
  problem getting the instance public key certificate ID.
  */
  public void registerInstanceKeyCertificate(
      InitialLdapContext ctx, Map<ServerProperty, Object> serverProperties,
      LdapName serverEntryDn)
  throws ADSContextException {
    assert serverProperties.containsKey(
        ServerProperty.INSTANCE_PUBLIC_KEY_CERTIFICATE);
    if (! serverProperties.containsKey(
        ServerProperty.INSTANCE_PUBLIC_KEY_CERTIFICATE)) {
      return;
    }

    /* the key ID might be supplied in serverProperties (although, I am unaware
   of any such case). */
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
    try
    {
      final NamingEnumeration<SearchResult> results = ctx.search(
          ADSContext.getInstanceKeysContainerDN(), keyAttrs, attrIDs);
      if (results.hasMore()) {
        final Attribute keyIdAttr =
          results.next().getAttributes().get(attrIDs[0]);
        if (null != keyIdAttr) {
          /* attribute ds-cfg-key-id is the entry is a MUST in the schema */
          keyID = (String)keyIdAttr.get();
        }
      }
      /* TODO: It is possible (but unexpected) that the caller specifies a
   ds-cfg-key-id value for which there is a certificate entry in ADS, but
   the certificate value does not match that supplied by the caller. The
   above search would not return the entry, but the below attempt to add
   an new entry with the supplied ds-cfg-key-id will fail (throw a
   NameAlreadyBoundException) */
      else {
        /* create key ID, if it was not supplied in serverProperties */
        if (null == keyID) {
          keyID = CryptoManager.getInstanceKeyID(
              (byte[])serverProperties.get(
                  ServerProperty.INSTANCE_PUBLIC_KEY_CERTIFICATE));
          keyAttrs.put(new BasicAttribute(
              ServerProperty.INSTANCE_KEY_ID.getAttributeName(), keyID));
        }

        /* add public-key certificate entry */
        final LdapName keyDn = new LdapName((new StringBuilder())
            .append(ServerProperty.INSTANCE_KEY_ID.getAttributeName())
            .append("=").append(Rdn.escapeValue(keyID)).append(",")
            .append(ADSContext.getInstanceKeysContainerDN()).toString());
        ctx.createSubcontext(keyDn, keyAttrs).close();
      }

      /* associate server entry with certificate entry via key ID attribute */
      ctx.modifyAttributes(serverEntryDn,
          InitialLdapContext.REPLACE_ATTRIBUTE,
          (new BasicAttributes(
              ServerProperty.INSTANCE_KEY_ID.getAttributeName(), keyID)));
    }
    catch (NamingException ne)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.ERROR_UNEXPECTED, ne);
    }
    catch (CryptoManager.CryptoManagerException cme)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.ERROR_UNEXPECTED, cme);
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
}
