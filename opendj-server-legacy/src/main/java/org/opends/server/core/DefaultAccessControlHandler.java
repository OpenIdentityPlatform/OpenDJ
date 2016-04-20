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
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.core;

import org.forgerock.opendj.server.config.server.AccessControlHandlerCfg;
import org.opends.server.api.AccessControlHandler;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.DN;
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
  /** Create a new default access control handler. */
  public DefaultAccessControlHandler()
  {
    super();

    // No implementation required.
  }

  @Override
  public void initializeAccessControlHandler(AccessControlHandlerCfg
                                                  configuration)
      throws ConfigException, InitializationException
  {
    // No implementation required.
  }

  @Override
  public void finalizeAccessControlHandler()
  {
    // No implementation required.
  }

  @Override
  public boolean isAllowed(LocalBackendAddOperation addOperation)
  {
    return true;
  }

  @Override
  public boolean isAllowed(BindOperation bindOperation)
  {
    return true;
  }

  @Override
  public boolean isAllowed(LocalBackendCompareOperation compareOperation)
  {
    return true;
  }

  @Override
  public boolean isAllowed(LocalBackendDeleteOperation deleteOperation)
  {
    return true;
  }

  @Override
  public boolean isAllowed(ExtendedOperation extendedOperation)
  {
    return true;
  }

  @Override
  public boolean isAllowed(LocalBackendModifyOperation modifyOperation)
  {
    return true;
  }

  @Override
   public  boolean isAllowed(DN dn, Operation  op, Control control)
   {
     return true;
   }

  @Override
  public boolean isAllowed(ModifyDNOperation modifyDNOperation)
  {
    return true;
  }

  @Override
  public boolean isAllowed(SearchOperation searchOperation)
  {
    return true;
  }

  @Override
  public boolean isAllowed(Operation operation, Entry entry,
    SearchFilter filter) throws DirectoryException
  {
    return true;
  }

  @Override
  public boolean maySend(Operation operation, SearchResultEntry unfilteredEntry)
  {
    return true;
  }

  @Override
  public void filterEntry(Operation operation,
      SearchResultEntry unfilteredEntry, SearchResultEntry filteredEntry)
  {
    return;
  }

  @Override
  public boolean maySend(DN dn, Operation operation,
                         SearchResultReference searchReference)
  {
    return true;
  }

  @Override
  public  boolean mayProxy(Entry proxyUser, Entry proxiedUser,
                           Operation operation) {
      return true;
  }
}
