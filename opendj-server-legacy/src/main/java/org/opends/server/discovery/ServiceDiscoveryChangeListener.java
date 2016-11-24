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
package org.opends.server.discovery;

/**
 * This interface defines the methods that a Service Discovery consumer
 * should implement if it wishes to be notified of changes in the service.
 */
public interface ServiceDiscoveryChangeListener
{
  /**
   * A change in the service, configuration or other, was detected.
   *
   * @param changedService the Service Discovery Mechanism who detected a change
   */
  void serviceChanged(ServiceDiscoveryMechanism<?> changedService);
}
