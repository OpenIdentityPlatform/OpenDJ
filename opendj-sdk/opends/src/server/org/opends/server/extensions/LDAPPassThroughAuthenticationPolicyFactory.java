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
 *      Copyright 2011 ForgeRock AS.
 */

package org.opends.server.extensions;



import java.util.List;

import org.opends.messages.Message;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.
  LDAPPassThroughAuthenticationPolicyCfg;
import org.opends.server.api.AuthenticationPolicy;
import org.opends.server.api.AuthenticationPolicyFactory;
import org.opends.server.api.AuthenticationPolicyState;
import org.opends.server.config.ConfigException;
import org.opends.server.types.*;



/**
 * LDAP pass through authentication policy implementation.
 */
public final class LDAPPassThroughAuthenticationPolicyFactory implements
    AuthenticationPolicyFactory<LDAPPassThroughAuthenticationPolicyCfg>
{

  /**
   * LDAP PTA policy state implementation.
   */
  private static final class StateImpl extends AuthenticationPolicyState
  {

    private final PolicyImpl policy;



    private StateImpl(PolicyImpl policy)
    {
      this.policy = policy;
    }



    /**
     * {@inheritDoc}
     */
    public boolean passwordMatches(ByteString password)
        throws DirectoryException
    {
      // TODO: perform PTA here.
      return false;
    }



    /**
     * {@inheritDoc}
     */
    public AuthenticationPolicy getAuthenticationPolicy()
    {
      return policy;
    }



    /**
     * {@inheritDoc}
     */
    public void finalizeStateAfterBind() throws DirectoryException
    {
      // TODO: cache password if needed.
    }

  }



  /**
   * LDAP PTA policy implementation.
   */
  private static final class PolicyImpl extends AuthenticationPolicy implements
      ConfigurationChangeListener<LDAPPassThroughAuthenticationPolicyCfg>
  {

    private PolicyImpl(LDAPPassThroughAuthenticationPolicyCfg configuration)
    {
      this.configuration = configuration;
    }



    // Current configuration.
    private LDAPPassThroughAuthenticationPolicyCfg configuration;



    /**
     * {@inheritDoc}
     */
    public boolean isConfigurationChangeAcceptable(
        LDAPPassThroughAuthenticationPolicyCfg configuration,
        List<Message> unacceptableReasons)
    {
      // The configuration is always valid.
      return true;
    }



    /**
     * {@inheritDoc}
     */
    public ConfigChangeResult applyConfigurationChange(
        LDAPPassThroughAuthenticationPolicyCfg configuration)
    {
      // TODO: close and re-open connections if servers have changed.
      this.configuration = configuration;
      return new ConfigChangeResult(ResultCode.SUCCESS, false);
    }



    /**
     * {@inheritDoc}
     */
    public DN getDN()
    {
      return configuration.dn();
    }



    /**
     * {@inheritDoc}
     */
    public AuthenticationPolicyState createAuthenticationPolicyState(
        Entry userEntry, long time) throws DirectoryException
    {
      return new StateImpl(this);
    }



    /**
     * {@inheritDoc}
     */
    public void finalizeAuthenticationPolicy()
    {
      // TODO: release pooled connections, etc.
    }

  }



  /**
   * {@inheritDoc}
   */
  public AuthenticationPolicy createAuthenticationPolicy(
      LDAPPassThroughAuthenticationPolicyCfg configuration)
      throws ConfigException, InitializationException
  {
    PolicyImpl policy = new PolicyImpl(configuration);
    configuration.addLDAPPassThroughChangeListener(policy);
    return policy;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationAcceptable(
      LDAPPassThroughAuthenticationPolicyCfg configuration,
      List<Message> unacceptableReasons)
  {
    // The configuration is always valid.
    return true;
  }

}
