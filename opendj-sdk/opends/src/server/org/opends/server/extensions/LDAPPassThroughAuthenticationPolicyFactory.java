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



import static org.opends.messages.ExtensionMessages.*;

import java.io.Closeable;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import org.opends.messages.Message;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.
    LDAPPassThroughAuthenticationPolicyCfg;
import org.opends.server.api.AuthenticationPolicy;
import org.opends.server.api.AuthenticationPolicyFactory;
import org.opends.server.api.AuthenticationPolicyState;
import org.opends.server.config.ConfigException;
import org.opends.server.types.*;
import org.opends.server.util.StaticUtils;



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
     * <p>
     * TODO: define result codes used when no entries found or too many entries.
     *
     * @param baseDN
     *          The search base DN.
     * @param scope
     *          The search scope.
     * @param filter
     *          The search filter.
     * @return The name of the user whose entry matches the provided search
     *         criteria.
     * @throws DirectoryException
     *           If the search returned no entries, more than one entry, or if
     *           the search failed unexpectedly.
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
  private final class PolicyImpl extends AuthenticationPolicy implements
      ConfigurationChangeListener<LDAPPassThroughAuthenticationPolicyCfg>
  {
    /**
     * LDAP PTA policy state implementation.
     */
    private final class StateImpl extends AuthenticationPolicyState
    {

      private final Entry userEntry;
      private ByteString cachedPassword = null;



      private StateImpl(final Entry userEntry)
      {
        this.userEntry = userEntry;
      }



      /**
       * {@inheritDoc}
       */
      @Override
      public void finalizeStateAfterBind() throws DirectoryException
      {
        if (cachedPassword != null)
        {
          // TODO: persist cached password if needed.
          cachedPassword = null;
        }
      }



      /**
       * {@inheritDoc}
       */
      @Override
      public AuthenticationPolicy getAuthenticationPolicy()
      {
        return PolicyImpl.this;
      }



      /**
       * {@inheritDoc}
       */
      @Override
      public boolean passwordMatches(final ByteString password)
          throws DirectoryException
      {
        sharedLock.lock();
        try
        {
          // First of determine the user name to use when binding to the remote
          // directory.
          ByteString username = null;

          switch (configuration.getMappingPolicy())
          {
          case UNMAPPED:
            // The bind DN is the name of the user's entry.
            username = ByteString.valueOf(userEntry.getDN().toString());
            break;
          case MAPPED_BIND:
            // The bind DN is contained in an attribute in the user's entry.
            mapBind: for (final AttributeType at : configuration
                .getMappedAttribute())
            {
              final List<Attribute> attributes = userEntry.getAttribute(at);
              if (attributes != null && !attributes.isEmpty())
              {
                for (final Attribute attribute : attributes)
                {
                  if (!attribute.isEmpty())
                  {
                    username = attribute.iterator().next().getValue();
                    break mapBind;
                  }
                }
              }
            }

            if (username == null)
            {
              /*
               * The mapping attribute(s) is not present in the entry. This
               * could be a configuration error, but it could also be because
               * someone is attempting to authenticate using a bind DN which
               * references a non-user entry.
               */
              throw new DirectoryException(ResultCode.INVALID_CREDENTIALS,
                  ERR_LDAP_PTA_MAPPING_ATTRIBUTE_NOT_FOUND.get(
                      String.valueOf(userEntry.getDN()),
                      String.valueOf(configuration.dn()),
                      StaticUtils.collectionToString(
                          configuration.getMappedAttribute(), ", ")));
            }

            break;
          case MAPPED_SEARCH:
            // A search against the remote directory is required in order to
            // determine the bind DN.

            // Construct the search filter.
            final LinkedList<SearchFilter> filterComponents =
              new LinkedList<SearchFilter>();
            for (final AttributeType at : configuration.getMappedAttribute())
            {
              final List<Attribute> attributes = userEntry.getAttribute(at);
              if (attributes != null && !attributes.isEmpty())
              {
                for (final Attribute attribute : attributes)
                {
                  for (final AttributeValue value : attribute)
                  {
                    filterComponents.add(SearchFilter.createEqualityFilter(at,
                        value));
                  }
                }
              }
            }

            if (filterComponents.isEmpty())
            {
              /*
               * The mapping attribute(s) is not present in the entry. This
               * could be a configuration error, but it could also be because
               * someone is attempting to authenticate using a bind DN which
               * references a non-user entry.
               */
              throw new DirectoryException(ResultCode.INVALID_CREDENTIALS,
                  ERR_LDAP_PTA_MAPPING_ATTRIBUTE_NOT_FOUND.get(
                      String.valueOf(userEntry.getDN()),
                      String.valueOf(configuration.dn()),
                      StaticUtils.collectionToString(
                          configuration.getMappedAttribute(), ", ")));
            }

            final SearchFilter filter;
            if (filterComponents.size() == 1)
            {
              filter = filterComponents.getFirst();
            }
            else
            {
              filter = SearchFilter.createORFilter(filterComponents);
            }

            // Now search the configured base DNs, stopping at the first
            // success.
            for (final DN baseDN : configuration.getMappedSearchBaseDN())
            {
              Connection connection = null;
              try
              {
                connection = searchFactory.getConnection();
                username = connection.search(baseDN, SearchScope.WHOLE_SUBTREE,
                    filter);
              }
              catch (final DirectoryException e)
              {
                switch (e.getResultCode())
                {
                // FIXME: specify possible result codes. What about authz
                // errors?
                case NO_SUCH_OBJECT:
                case CLIENT_SIDE_NO_RESULTS_RETURNED:
                  // Ignore and try next base DN.
                  break;
                case CLIENT_SIDE_MORE_RESULTS_TO_RETURN:
                  // More than one matching entry was returned.
                  throw new DirectoryException(ResultCode.INVALID_CREDENTIALS,
                      ERR_LDAP_PTA_MAPPED_SEARCH_TOO_MANY_CANDIDATES.get(
                          String.valueOf(userEntry.getDN()),
                          String.valueOf(configuration.dn()),
                          String.valueOf(baseDN), String.valueOf(filter)));
                default:
                  // We don't want to propagate this internal error to the
                  // client. We should log it and map it to a more appropriate
                  // error.
                  throw new DirectoryException(ResultCode.INVALID_CREDENTIALS,
                      ERR_LDAP_PTA_MAPPED_SEARCH_FAILED.get(
                          String.valueOf(userEntry.getDN()),
                          String.valueOf(configuration.dn()),
                          e.getMessageObject()), e);
                }
              }
              finally
              {
                if (connection != null)
                {
                  connection.close();
                }
              }
            }

            if (username == null)
            {
              /*
               * No matching entries were found in the remote directory.
               */
              throw new DirectoryException(ResultCode.INVALID_CREDENTIALS,
                  ERR_LDAP_PTA_MAPPED_SEARCH_NO_CANDIDATES.get(
                      String.valueOf(userEntry.getDN()),
                      String.valueOf(configuration.dn()),
                      String.valueOf(filter)));
            }

            break;
          }

          // Now perform the bind.
          Connection connection = null;
          try
          {
            connection = bindFactory.getConnection();
            connection.simpleBind(username, password);
            return true;
          }
          catch (final DirectoryException e)
          {
            switch (e.getResultCode())
            {
            // FIXME: specify possible result codes.
            case NO_SUCH_OBJECT:
            case INVALID_CREDENTIALS:
              return false;
            default:
              // We don't want to propagate this internal error to the
              // client. We should log it and map it to a more appropriate
              // error.
              throw new DirectoryException(
                  ResultCode.INVALID_CREDENTIALS,
                  ERR_LDAP_PTA_MAPPED_BIND_FAILED.get(
                      String.valueOf(userEntry.getDN()),
                      String.valueOf(configuration.dn()), e.getMessageObject()),
                  e);
            }
          }
          finally
          {
            if (connection != null)
            {
              connection.close();
            }
          }
        }
        finally
        {
          sharedLock.unlock();
        }
      }
    }



    // Guards against configuration changes.
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ReadLock sharedLock = lock.readLock();
    private final WriteLock exclusiveLock = lock.writeLock();

    // Current configuration.
    private LDAPPassThroughAuthenticationPolicyCfg configuration;

    // FIXME: initialize connection factories.
    private ConnectionFactory searchFactory = null;
    private ConnectionFactory bindFactory = null;



    private PolicyImpl(
        final LDAPPassThroughAuthenticationPolicyCfg configuration)
    {
      initializeConfiguration(configuration);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public ConfigChangeResult applyConfigurationChange(
        final LDAPPassThroughAuthenticationPolicyCfg configuration)
    {
      exclusiveLock.lock();
      try
      {
        closeConnections();
        initializeConfiguration(configuration);
      }
      finally
      {
        exclusiveLock.unlock();
      }
      return new ConfigChangeResult(ResultCode.SUCCESS, false);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public AuthenticationPolicyState createAuthenticationPolicyState(
        final Entry userEntry, final long time) throws DirectoryException
    {
      // The current time is not needed for LDAP PTA.
      return new StateImpl(userEntry);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void finalizeAuthenticationPolicy()
    {
      exclusiveLock.lock();
      try
      {
        configuration.removeLDAPPassThroughChangeListener(this);
        closeConnections();
      }
      finally
      {
        exclusiveLock.unlock();
      }
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



    private void closeConnections()
    {
      exclusiveLock.lock();
      try
      {
        // TODO: close all connections.
      }
      finally
      {
        exclusiveLock.unlock();
      }
    }



    private void initializeConfiguration(
        final LDAPPassThroughAuthenticationPolicyCfg configuration)
    {
      this.configuration = configuration;

      // TODO: implement FO/LB/CP + authenticated search factory.
      final String hostPort = configuration.getPrimaryRemoteLDAPServer()
          .first();
      searchFactory = newLDAPConnectionFactory(hostPort);
      bindFactory = newLDAPConnectionFactory(hostPort);
    }



    private ConnectionFactory newLDAPConnectionFactory(final String hostPort)
    {
      // Validation already performed by admin framework.
      final int colonIndex = hostPort.lastIndexOf(":");
      final String hostname = hostPort.substring(0, colonIndex);
      final int port = Integer.parseInt(hostPort.substring(colonIndex + 1));
      return provider.getLDAPConnectionFactory(hostname, port, configuration);
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
