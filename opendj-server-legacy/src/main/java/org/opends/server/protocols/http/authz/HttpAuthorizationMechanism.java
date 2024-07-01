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
 * Copyright 2016 ForgeRock AS.
 */
package org.opends.server.protocols.http.authz;

import org.forgerock.http.Filter;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.rest2ldap.authz.ConditionalFilters.Condition;
import org.forgerock.opendj.rest2ldap.authz.ConditionalFilters.ConditionalFilter;
import org.forgerock.opendj.server.config.server.HTTPAuthorizationMechanismCfg;

/**
 * Provides foundation for http authorization mechanisms.
 *
 * @param <T>
 *          Type of the configuration specific to the {@link HttpAuthorizationMechanism}.
 */
public abstract class HttpAuthorizationMechanism<T extends HTTPAuthorizationMechanismCfg>
  implements ConditionalFilter, Comparable<HttpAuthorizationMechanism<?>>
{
  private final DN configDN;
  private final int priority;

  /**
   * Create a new {@link HttpAuthorizationMechanism}.
   *
   * @param configDN
   *          DN where the configuration of this {@link HttpAuthorizationMechanism} resides.
   * @param priority
   *          Priority of evaluation when multiple {@link HttpAuthorizationMechanism} are present. Authorization
   *          mechanism with lower value will processed before the ones with bigger values.
   */
  public HttpAuthorizationMechanism(DN configDN, int priority)
  {
    this.configDN = configDN;
    this.priority = priority;
  }

  @Override
  public final Filter getFilter()
  {
    return getDelegate().getFilter();
  }

  @Override
  public final Condition getCondition()
  {
    return getDelegate().getCondition();
  }

  @Override
  public final int compareTo(HttpAuthorizationMechanism<?> other)
  {
    return Integer.compare(priority, other.priority);
  }

  abstract ConditionalFilter getDelegate();

  @Override
  public String toString()
  {
    return configDN.rdn(0).toString();
  }
}
