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
 * Copyright 2013-2015 ForgeRock AS.
 */
package org.opends.server.loggers;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.forgerock.audit.DependencyProvider;
import org.forgerock.audit.providers.LocalHostNameProvider;
import org.forgerock.audit.providers.ProductInfoProvider;
import org.opends.server.util.DynamicConstants;

/** Simple implementation of dependency provider for common audit. */
class CommonAuditDependencyProvider implements DependencyProvider
{
  @Override
  public <T> T getDependency(Class<T> clazz) throws ClassNotFoundException
  {
    if (clazz.isAssignableFrom(LocalHostNameProvider.class))
    {
      return (T) new DJLocalHostNameProvider();
    }
    else if (clazz.isAssignableFrom(ProductInfoProvider.class))
    {
      return (T) new DJProductInfoProvider();
    }
    throw new ClassNotFoundException(String.format("Class %s could not be found", clazz));
  }

  /** DJ implementation for LocalHostNameProvider. */
  private static class DJLocalHostNameProvider implements LocalHostNameProvider
  {
    @Override
    public String getLocalHostName()
    {
      try
      {
        return InetAddress.getLocalHost().getHostName();
      }
      catch (UnknownHostException uhe)
      {
        return null;
      }
    }
  }

  /** DJ implementation for ProductInfoProvider. */
  private static class DJProductInfoProvider implements ProductInfoProvider
  {
    @Override
    public String getProductName()
    {
      return DynamicConstants.PRODUCT_NAME;
    }
  }
}
