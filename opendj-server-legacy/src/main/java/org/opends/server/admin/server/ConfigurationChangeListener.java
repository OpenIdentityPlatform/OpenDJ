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
 *      Copyright 2007-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.admin.server;

import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.opends.server.admin.Configuration;

/**
 * This interface defines the methods that a Directory Server
 * configurable component should implement if it wishes to be able to
 * receive notifications when a its associated configuration is
 * changed.
 *
 * @param <T>
 *          The type of configuration that this listener should be
 *          notified about.
 */
public interface ConfigurationChangeListener<T extends Configuration> {

  /**
   * Indicates whether the proposed change to the configuration is
   * acceptable to this change listener.
   *
   * @param configuration
   *          The new configuration containing the changes.
   * @param unacceptableReasons
   *          A list that can be used to hold messages about why the
   *          provided configuration is not acceptable.
   * @return Returns <code>true</code> if the proposed change is
   *         acceptable, or <code>false</code> if it is not.
   */
  boolean isConfigurationChangeAcceptable(T configuration,
      List<LocalizableMessage> unacceptableReasons);

  /**
   * Applies the configuration changes to this change listener.
   *
   * @param configuration
   *          The new configuration containing the changes.
   * @return Returns information about the result of changing the
   *         configuration.
   */
  ConfigChangeResult applyConfigurationChange(T configuration);
}
