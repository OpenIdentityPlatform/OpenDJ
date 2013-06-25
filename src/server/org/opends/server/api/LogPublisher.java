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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 *      Portions copyright 2011-2013 ForgeRock AS.
 */
package org.opends.server.api;

import java.io.Closeable;
import java.util.List;

import org.opends.messages.Message;
import org.opends.server.admin.std.server.LogPublisherCfg;
import org.opends.server.config.ConfigException;
import org.opends.server.types.DN;
import org.opends.server.types.InitializationException;

/**
 * This class defines the set of methods and structures that must be implemented
 * for a Directory Server log publisher.
 *
 * @param <T>
 *          The type of log publisher configuration handled by this log
 *          publisher implementation.
 */
@org.opends.server.types.PublicAPI(
    stability = org.opends.server.types.StabilityLevel.VOLATILE,
    mayInstantiate = false,
    mayExtend = true,
    mayInvoke = false)
public interface LogPublisher<T extends LogPublisherCfg> extends Closeable
{

  /**
   * Initializes this publisher provider based on the information in the
   * provided debug publisher configuration.
   *
   * @param config
   *          The publisher configuration that contains the information to use
   *          to initialize this publisher.
   * @throws ConfigException
   *           If an unrecoverable problem arises in the process of performing
   *           the initialization as a result of the server configuration.
   * @throws InitializationException
   *           If a problem occurs during initialization that is not related to
   *           the server configuration.
   */
  void initializeLogPublisher(T config) throws ConfigException,
      InitializationException;



  /**
   * Indicates whether the provided configuration is acceptable for this log
   * publisher. It should be possible to call this method on an uninitialized
   * log publisher instance in order to determine whether the log publisher
   * would be able to use the provided configuration.
   * <BR><BR>
   * Note that implementations which use a subclass of the provided
   * configuration class will likely need to cast the configuration to the
   * appropriate subclass type.
   *
   * @param configuration
   *          The log publisher configuration for which to make the
   *          determination.
   * @param unacceptableReasons
   *          A list that may be used to hold the reasons that the provided
   *          configuration is not acceptable.
   * @return {@code true} if the provided configuration is acceptable for this
   *         log publisher, or {@code false} if not.
   */
  boolean isConfigurationAcceptable(T configuration,
      List<Message> unacceptableReasons);



  /**
   * Close this publisher.
   */
  @Override
  void close();



  /**
   * Gets the DN of the configuration entry for this log publisher.
   *
   * @return The configuration entry DN.
   */
  DN getDN();

}
