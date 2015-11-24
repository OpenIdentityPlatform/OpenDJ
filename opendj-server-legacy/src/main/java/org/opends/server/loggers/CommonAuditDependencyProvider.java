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
 *      Copyright 2013-2015 ForgeRock AS
 */
package org.opends.server.loggers;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.forgerock.audit.DependencyProvider;
import org.forgerock.audit.providers.LocalHostNameProvider;
import org.forgerock.audit.providers.ProductInfoProvider;
import org.opends.server.util.DynamicConstants;

/**
 * Simple implementation of dependency provider for common audit.
 */
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
