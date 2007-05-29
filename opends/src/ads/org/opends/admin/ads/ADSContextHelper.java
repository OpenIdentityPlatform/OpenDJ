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

import java.util.SortedSet;
import java.util.TreeSet;

import javax.naming.ldap.InitialLdapContext;

import org.opends.server.admin.client.ManagementContext;
import org.opends.server.admin.client.ldap.JNDIDirContextAdaptor;
import org.opends.server.admin.client.ldap.LDAPManagementContext;
import org.opends.server.admin.std.client.*;
import org.opends.server.admin.std.meta.*;
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
      BackendCfgClient backend = root.getBackend(backendName);
      if (backend != null)
      {
        SortedSet<DN> suffixes = backend.getBackendBaseDN();
        if (suffixes != null)
        {
          if (suffixes.remove(
              DN.decode(ADSContext.getAdministrationSuffixDN())))
          {
            if (suffixes.size() > 0)
            {
              backend.setBackendBaseDN(suffixes);
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
      String backendName) throws ADSContextException
  {
    try
    {
      ManagementContext mCtx = LDAPManagementContext.createFromContext(
          JNDIDirContextAdaptor.adapt(ctx));
      RootCfgClient root = mCtx.getRootConfiguration();
      BackendCfgClient backend = root.getBackend(backendName);
      if (backend == null)
      {
        BackendCfgDefn provider = BackendCfgDefn.getInstance();
        backend = root.createBackend(provider, backendName, null);
      }
      SortedSet<DN> suffixes = backend.getBackendBaseDN();
      if (suffixes == null)
      {
        suffixes = new TreeSet<DN>();
      }
      suffixes.add(DN.decode(ADSContext.getAdministrationSuffixDN()));
      backend.setBackendBaseDN(suffixes);
      backend.commit();
    }
    catch (Throwable t)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.ERROR_UNEXPECTED, t);
    }
  }
}
