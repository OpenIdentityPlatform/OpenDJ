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
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions Copyright 2014 ForgeRock AS
 */

package org.opends.server.api;



import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.admin.std.server.QOSPolicyCfg;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.types.InitializationException;



/**
 * A factory for creating configurable quality of service (QOS)
 * policies.
 * <p>
 * All implementations must have a default constructor, i.e. one that
 * does not require and arguments.
 *
 * @param <T>
 *          The type of QOS policy configuration handled by this
 *          factory.
 */
public interface QOSPolicyFactory<T extends QOSPolicyCfg>
{
  /**
   * Creates a new QOS policy using the provided configuration.
   *
   * @param configuration
   *          The configuration.
   * @return The new QOS policy configured using the provided
   *         configuration.
   * @throws ConfigException
   *           If an unrecoverable problem arises during
   *           initialization of the QOS policy as a result
   *           of the server configuration.
   * @throws InitializationException
   *           If a problem occurs during initialization of the QOS
   *           policy.
   */
  QOSPolicy createQOSPolicy(T configuration) throws ConfigException,
      InitializationException;



  /**
   * Indicates whether the provided QOS policy configuration is
   * acceptable.
   *
   * @param configuration
   *          The QOS policy configuration.
   * @param unacceptableReasons
   *          A list that can be used to hold messages about why the
   *          provided configuration is not acceptable.
   * @return Returns <code>true</code> if the provided QOS policy
   *         configuration is acceptable, or <code>false</code> if
   *         it is not.
   */
  boolean isConfigurationAcceptable(T configuration,
      List<LocalizableMessage> unacceptableReasons);
}
