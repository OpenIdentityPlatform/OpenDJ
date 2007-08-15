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
package org.opends.server.admin.server;
import org.opends.messages.Message;



import java.util.List;

import org.opends.server.types.ConfigChangeResult;



/**
 * This interface defines the methods that a Directory Server
 * configurable component should implement if it wishes to be able to
 * receive notifications when a new configuration is added.
 *
 * @param <T>
 *          The type of configuration that this listener should be
 *          notified about.
 */
public interface ConfigurationAddListener<T> {

  /**
   * Indicates whether the proposed addition of a new configuration is
   * acceptable to this add listener.
   *
   * @param configuration
   *          The configuration that will be added.
   * @param unacceptableReasons
   *          A list that can be used to hold messages about why the
   *          provided configuration is not acceptable.
   * @return Returns <code>true</code> if the proposed addition is
   *         acceptable, or <code>false</code> if it is not.
   */
  public boolean isConfigurationAddAcceptable(T configuration,
      List<Message> unacceptableReasons);



  /**
   * Adds a new configuration to this add listener.
   *
   * @param configuration
   *          The configuration that will be added.
   * @return Returns information about the result of adding the
   *         configuration.
   */
  public ConfigChangeResult applyConfigurationAdd(T configuration);
}
