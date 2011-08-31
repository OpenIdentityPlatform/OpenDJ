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



import java.io.Closeable;
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
   * An LDAP connection which will be used in order to search for or
   * authenticate users.
   */
  static interface Connection extends Closeable
  {

    /**
     * Closes this connection.
     */
    @Override
    void close();



    /**
     * Returns the name of the user whose entry matches the provided search
     * criteria.
     *
     * @param baseDN
     *          The search base DN.
     * @param scope
     *          The search scope.
     * @param filter
     *          The search filter.
     * @return The name of the user whose entry matches the provided search
     *         criteria, or {@code null} if no matching user entry was found.
     * @throws DirectoryException
     *           If the search returned more than one entry, or if the search
     *           failed unexpectedly.
     */
    ByteString search(DN baseDN, SearchScope scope, SearchFilter filter)
        throws DirectoryException;



    /**
     * Performs a simple bind for the user.
     *
     * @param username
     *          The user name (usually a bind DN).
     * @param password
     *          The user's password.
     * @throws DirectoryException
     *           If the credentials were invalid, or the authentication failed
     *           unexpectedly.
     */
    void simpleBind(ByteString username, ByteString password)
        throws DirectoryException;
  }



  /**
   * An interface for obtaining connections: users of this interface will obtain
   * a connection, perform a single operation (search or bind), and then close
   * it.
   */
  static interface ConnectionFactory
  {
    /**
     * Returns a connection which can be used in order to search for or
     * authenticate users.
     *
     * @return The connection.
     * @throws DirectoryException
     *           If an unexpected error occurred while attempting to obtain a
     *           connection.
     */
    Connection getConnection() throws DirectoryException;
  }



  /**
   * An interface for obtaining a connection factory for LDAP connections to a
   * named LDAP server.
   */
  static interface LDAPConnectionFactoryProvider
  {
    /**
     * Returns a connection factory which can be used for obtaining connections
     * to the specified LDAP server.
     *
     * @param host
     *          The LDAP server host name.
     * @param port
     *          The LDAP server port.
     * @param options
     *          The LDAP connection options.
     * @return A connection factory which can be used for obtaining connections
     *         to the specified LDAP server.
     */
    ConnectionFactory getLDAPConnectionFactory(String host, int port,
        LDAPPassThroughAuthenticationPolicyCfg options);
  }



  /**
   * LDAP PTA policy implementation.
   */
  private static final class PolicyImpl extends AuthenticationPolicy implements
      ConfigurationChangeListener<LDAPPassThroughAuthenticationPolicyCfg>
  {

    // Current configuration.
    private LDAPPassThroughAuthenticationPolicyCfg configuration;



    private PolicyImpl(
        final LDAPPassThroughAuthenticationPolicyCfg configuration)
    {
      this.configuration = configuration;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public ConfigChangeResult applyConfigurationChange(
        final LDAPPassThroughAuthenticationPolicyCfg configuration)
    {
      // TODO: close and re-open connections if servers have changed.
      this.configuration = configuration;
      return new ConfigChangeResult(ResultCode.SUCCESS, false);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public AuthenticationPolicyState createAuthenticationPolicyState(
        final Entry userEntry, final long time) throws DirectoryException
    {
      return new StateImpl(this);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void finalizeAuthenticationPolicy()
    {
      // TODO: release pooled connections, etc.
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public DN getDN()
    {
      return configuration.dn();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isConfigurationChangeAcceptable(
        final LDAPPassThroughAuthenticationPolicyCfg configuration,
        final List<Message> unacceptableReasons)
    {
      // The configuration is always valid.
      return true;
    }

  }



  /**
   * LDAP PTA policy state implementation.
   */
  private static final class StateImpl extends AuthenticationPolicyState
  {

    private final PolicyImpl policy;



    private StateImpl(final PolicyImpl policy)
    {
      this.policy = policy;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void finalizeStateAfterBind() throws DirectoryException
    {
      // TODO: cache password if needed.
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public AuthenticationPolicy getAuthenticationPolicy()
    {
      return policy;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean passwordMatches(final ByteString password)
        throws DirectoryException
    {
      // TODO: perform PTA here.
      return false;
    }

  }



  // The provider which should be used by policies to create LDAP connections.
  private final LDAPConnectionFactoryProvider provider;

  /**
   * The default LDAP connection factory provider.
   */
  private static final LDAPConnectionFactoryProvider DEFAULT_PROVIDER =
    new LDAPConnectionFactoryProvider()
  {

    @Override
    public ConnectionFactory getLDAPConnectionFactory(final String host,
        final int port, final LDAPPassThroughAuthenticationPolicyCfg options)
    {
      // TODO: not yet implemented.
      return null;
    }

  };



  /**
   * Public default constructor used by the admin framework. This will use the
   * default LDAP connection factory provider.
   */
  public LDAPPassThroughAuthenticationPolicyFactory()
  {
    this(DEFAULT_PROVIDER);
  }



  /**
   * Package private constructor allowing unit tests to provide mock connection
   * implementations.
   *
   * @param provider
   *          The LDAP connection factory provider implementation which LDAP PTA
   *          authentication policies will use.
   */
  LDAPPassThroughAuthenticationPolicyFactory(
      final LDAPConnectionFactoryProvider provider)
  {
    this.provider = provider;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public AuthenticationPolicy createAuthenticationPolicy(
      final LDAPPassThroughAuthenticationPolicyCfg configuration)
      throws ConfigException, InitializationException
  {
    final PolicyImpl policy = new PolicyImpl(configuration);
    configuration.addLDAPPassThroughChangeListener(policy);
    return policy;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isConfigurationAcceptable(
      final LDAPPassThroughAuthenticationPolicyCfg configuration,
      final List<Message> unacceptableReasons)
  {
    // The configuration is always valid.
    return true;
  }

}
