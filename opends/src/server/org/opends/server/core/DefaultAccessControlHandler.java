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
package org.opends.server.core;



import org.opends.server.admin.std.server.AccessControlHandlerCfg;
import org.opends.server.api.AccessControlHandler;
import org.opends.server.config.ConfigException;
import org.opends.server.types.*;
import org.opends.server.workflowelement.localbackend.*;



/**
 * This class implements a default access control provider for the Directory
 * Server.
 * <p>
 * This class provides and access control handler which is used when access
 * control is disabled and implements a default access control decision function
 * which grants access to everything and anyone.
 */
class DefaultAccessControlHandler
      extends AccessControlHandler<AccessControlHandlerCfg>
{
  /**
   * The single handler instance.
   */
  private static DefaultAccessControlHandler instance = null;



  /**
   * Create a new default access control handler.
   */
  public DefaultAccessControlHandler()
  {
    super();

    // No implementation required.
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void initializeAccessControlHandler(AccessControlHandlerCfg
                                                  configuration)
      throws ConfigException, InitializationException
  {
    // No implementation required.
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void finalizeAccessControlHandler()
  {
    // No implementation required.
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isAllowed(LocalBackendAddOperation addOperation)
  {
    return true;
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isAllowed(LocalBackendBindOperation bindOperation)
  {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isAllowed(LocalBackendCompareOperation compareOperation)
  {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isAllowed(LocalBackendDeleteOperation deleteOperation)
  {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isAllowed(ExtendedOperation extendedOperation)
  {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isAllowed(LocalBackendModifyOperation modifyOperation)
  {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override
   public  boolean isAllowed(DN dn, Operation  op, Control control)
   {
     return true;
   }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isAllowed(LocalBackendModifyDNOperation modifyDNOperation)
  {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isAllowed(LocalBackendSearchOperation searchOperation)
  {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isAllowed(Operation operation, Entry entry,
    SearchFilter filter) throws DirectoryException
  {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean maySend(SearchOperation searchOperation,
                         SearchResultEntry searchEntry)
  {
    return true;
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public SearchResultEntry filterEntry(SearchOperation searchOperation,
                                       SearchResultEntry searchEntry)
  {
    // No implementation required.
    return searchEntry;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public SearchResultEntry filterEntry(Operation operation, Entry entry)
  {
    // No implementation required.
    return new SearchResultEntry(entry);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean maySend(DN dn, SearchOperation searchOperation,
                         SearchResultReference searchReference)
  {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public  boolean mayProxy(Entry proxyUser, Entry proxiedUser,
                           Operation operation) {
      return true;
  }
}

