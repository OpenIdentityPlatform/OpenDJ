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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.api;


import java.util.Collection;
import java.util.List;

import org.opends.server.admin.std.server.MatchingRuleCfg;
import org.opends.server.config.ConfigException;
import org.opends.messages.Message;
import org.opends.server.types.InitializationException;

/**
 * This class defines the set of methods and structures that must be
 * implemented by a Directory Server module that implements a matching
 * rule factory.
 *
 * @param  <T>  The type of configuration handled by this matching
 *              rule.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=true,
     mayInvoke=false)
public abstract class MatchingRuleFactory<T extends MatchingRuleCfg>
{

  /**
   * Initializes the matching rule(s) based on the information in the
   * provided configuration entry.
   *
   * @param  configuration  The configuration to use to intialize this
   *                        matching rule.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in
   *                           the process of performing the
   *                           initialization.
   *
   * @throws  InitializationException  If a problem that is not
   *                                   configuration-related occurs
   *                                   during initialization.
   */
  public abstract void initializeMatchingRule(T configuration)
         throws ConfigException, InitializationException;



  /**
   * Performs any finalization that may be needed whenever this
   * matching rule factory is taken out of service.
   */
  public  void finalizeMatchingRule()
  {
    //No implementation is required by default.
  }



  /**
   * Indicates whether the provided configuration is acceptable for
   * this matching rule.  It should be possible to call this method on
   * an uninitialized matching rule instance in order to determine
   * whether the matching rule would be able to use the provided
   * configuration.
   * <BR><BR>
   * Note that implementations which use a subclass of the provided
   * configuration class will likely need to cast the configuration
   * to the appropriate subclass type.
   *
   * @param  configuration        The matching rule configuration for
   *                              which to make the determination.
   * @param  unacceptableReasons  A list that may be used to hold the
   *                              reasons that the provided
   *                              configuration is not acceptable.
   *
   * @return  {@code true} if the provided configuration is acceptable
   *          for this matching rule, or {@code false} if not.
   */
  public  boolean isConfigurationAcceptable(
                      T configuration,
                      List<Message> unacceptableReasons)
  {
    // This default implementation does not perform any special
    // validation.  It should be overridden by matching rule
    // implementations that wish to perform more detailed validation.
    return true;
  }



  /**
   * Returns an umodifiable view of Collection of associated
   * MatchingRules.
   *
   * @return  An unmodifiable view of Collection of
   *          MatchingRule instances.
   */
  public abstract Collection<MatchingRule> getMatchingRules();
}