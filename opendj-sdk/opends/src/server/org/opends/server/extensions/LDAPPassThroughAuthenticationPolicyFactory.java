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
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import org.opends.messages.Message;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.*;
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

  // TODO: retry operations transparently until all connections exhausted.
  // TODO: handle password policy response controls? AD?
  // TODO: periodically ping offline servers in order to detect when they come
  // back.

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
     * criteria. This will return CLIENT_SIDE_NO_RESULTS_RETURNED/NO_SUCH_OBJECT
     * if no search results were returned, or CLIENT_SIDE_MORE_RESULTS_TO_RETURN
     * if too many results were returned.
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
     * A factory which returns pre-authenticated connections for searches.
     */
    private final class AuthenticatedConnectionFactory implements
        ConnectionFactory
    {

      private final ConnectionFactory factory;



      private AuthenticatedConnectionFactory(final ConnectionFactory factory)
      {
        this.factory = factory;
      }



      /**
       * {@inheritDoc}
       */
      @Override
      public Connection getConnection() throws DirectoryException
      {
        final DN username = configuration.getMappedSearchBindDN();
        final String password = configuration.getMappedSearchBindPassword();

        final Connection connection = factory.getConnection();
        if (username != null && !username.isNullDN())
        {
          try
          {
            connection.simpleBind(ByteString.valueOf(username.toString()),
                ByteString.valueOf(password));
          }
          catch (final DirectoryException e)
          {
            connection.close();
            throw e;
          }
        }
        return connection;
      }



      /**
       * {@inheritDoc}
       */
      @Override
      public String toString()
      {
        final StringBuilder builder = new StringBuilder();
        builder.append("AuthenticationConnectionFactory(");
        builder.append(factory);
        builder.append(')');
        return builder.toString();
      }

    }



    /**
     * PTA connection pool.
     */
    private final class ConnectionPool implements ConnectionFactory, Closeable
    {

      /**
       * Pooled connection's intercept close and release connection back to the
       * pool.
       */
      private final class PooledConnection implements Connection
      {
        private final Connection connection;
        private boolean connectionIsClosed = false;



        private PooledConnection(final Connection connection)
        {
          this.connection = connection;
        }



        /**
         * {@inheritDoc}
         */
        @Override
        public void close()
        {
          if (!connectionIsClosed)
          {
            connectionIsClosed = true;

            // Guarded by PolicyImpl
            if (poolIsClosed)
            {
              connection.close();
            }
            else
            {
              pooledConnections.offer(this);
            }
            availableConnections.release();
          }
        }



        /**
         * {@inheritDoc}
         */
        @Override
        public ByteString search(final DN baseDN, final SearchScope scope,
            final SearchFilter filter) throws DirectoryException
        {
          try
          {
            return connection.search(baseDN, scope, filter);
          }
          catch (final DirectoryException e)
          {
            // Don't put the connection back in the pool if it has failed.
            closeConnectionOnFatalError(e);
            throw e;
          }
        }



        /**
         * {@inheritDoc}
         */
        @Override
        public void simpleBind(final ByteString username,
            final ByteString password) throws DirectoryException
        {
          try
          {
            connection.simpleBind(username, password);
          }
          catch (final DirectoryException e)
          {
            // Don't put the connection back in the pool if it has failed.
            closeConnectionOnFatalError(e);
            throw e;
          }
        }



        private void closeConnectionOnFatalError(final DirectoryException e)
        {
          if (isFatalResultCode(e.getResultCode()))
          {
            connectionIsClosed = true;
            connection.close();
            availableConnections.release();
          }
        }

      }



      // Guarded by PolicyImpl.lock.
      private boolean poolIsClosed = false;

      private final ConnectionFactory factory;
      private final int poolSize =
        Runtime.getRuntime().availableProcessors() * 2;
      private final Semaphore availableConnections = new Semaphore(poolSize);
      private final LinkedBlockingQueue<PooledConnection> pooledConnections =
        new LinkedBlockingQueue<PooledConnection>();



      private ConnectionPool(final ConnectionFactory factory)
      {
        this.factory = factory;
      }



      /**
       * Release all connections: do we want to block?
       */
      @Override
      public void close()
      {
        // No need for synchronization as this can only be called with the
        // policy's exclusive lock.
        poolIsClosed = true;

        PooledConnection pooledConnection;
        while ((pooledConnection = pooledConnections.poll()) != null)
        {
          pooledConnection.connection.close();
        }

        // Since we have the exclusive lock, there should be no more connections
        // in use.
        if (availableConnections.availablePermits() != poolSize)
        {
          throw new IllegalStateException(
              "Pool has remaining connections open after close");
        }
      }



      /**
       * {@inheritDoc}
       */
      @Override
      public Connection getConnection() throws DirectoryException
      {
        // This should only be called with the policy's shared lock.
        if (poolIsClosed)
        {
          throw new IllegalStateException("pool is closed");
        }

        availableConnections.acquireUninterruptibly();

        // There is either a pooled connection or we are allowed to create
        // one.
        PooledConnection pooledConnection = pooledConnections.poll();
        if (pooledConnection == null)
        {
          try
          {
            final Connection connection = factory.getConnection();
            pooledConnection = new PooledConnection(connection);
          }
          catch (final DirectoryException e)
          {
            availableConnections.release();
            throw e;
          }
        }

        return pooledConnection;
      }



      /**
       * {@inheritDoc}
       */
      @Override
      public String toString()
      {
        final StringBuilder builder = new StringBuilder();
        builder.append("ConnectionPool(");
        builder.append(factory);
        builder.append(", poolSize=");
        builder.append(poolSize);
        builder.append(", inPool=");
        builder.append(pooledConnections.size());
        builder.append(", available=");
        builder.append(availableConnections.availablePermits());
        builder.append(')');
        return builder.toString();
      }
    }



    /**
     * A simplistic two-way fail-over connection factory implementation.
     */
    private final class FailoverConnectionFactory implements ConnectionFactory,
        Closeable
    {
      private final LoadBalancer primary;
      private final LoadBalancer secondary;



      private FailoverConnectionFactory(final LoadBalancer primary,
          final LoadBalancer secondary)
      {
        this.primary = primary;
        this.secondary = secondary;
      }



      /**
       * Close underlying load-balancers.
       */
      @Override
      public void close()
      {
        primary.close();
        if (secondary != null)
        {
          secondary.close();
        }
      }



      /**
       * {@inheritDoc}
       */
      @Override
      public Connection getConnection() throws DirectoryException
      {
        if (secondary == null)
        {
          // No fail-over so just use the primary.
          return primary.getConnection();
        }
        else
        {
          try
          {
            return primary.getConnection();
          }
          catch (final DirectoryException e)
          {
            return secondary.getConnection();
          }
        }
      }



      /**
       * {@inheritDoc}
       */
      @Override
      public String toString()
      {
        final StringBuilder builder = new StringBuilder();
        builder.append("FailoverConnectionFactory(");
        builder.append(primary);
        builder.append(", ");
        builder.append(secondary);
        builder.append(')');
        return builder.toString();
      }

    }



    /**
     * A simplistic load-balancer connection factory implementation using
     * approximately round-robin balancing.
     */
    private final class LoadBalancer implements ConnectionFactory, Closeable
    {
      private final ConnectionPool[] factories;
      private final AtomicInteger nextIndex = new AtomicInteger();
      private final int maxIndex;



      private LoadBalancer(final ConnectionPool[] factories)
      {
        this.factories = factories;
        this.maxIndex = factories.length;
      }



      /**
       * Close underlying connection pools.
       */
      @Override
      public void close()
      {
        for (final ConnectionPool pool : factories)
        {
          pool.close();
        }
      }



      /**
       * {@inheritDoc}
       */
      @Override
      public Connection getConnection() throws DirectoryException
      {
        final int startIndex = getStartIndex();
        int index = startIndex;
        for (;;)
        {
          final ConnectionFactory factory = factories[index];

          try
          {
            return factory.getConnection();
          }
          catch (final DirectoryException e)
          {
            // Try the next index.
            if (++index == maxIndex)
            {
              index = 0;
            }

            // If all the factories have been tried then give up and throw the
            // exception.
            if (index == startIndex)
            {
              throw e;
            }
          }
        }
      }



      /**
       * {@inheritDoc}
       */
      @Override
      public String toString()
      {
        final StringBuilder builder = new StringBuilder();
        builder.append("LoadBalancer(");
        builder.append(nextIndex);
        for (final ConnectionFactory factory : factories)
        {
          builder.append(", ");
          builder.append(factory);
        }
        builder.append(')');
        return builder.toString();
      }



      // Determine the start index.
      private int getStartIndex()
      {
        // A round robin pool of one connection factories is unlikely in
        // practice and requires special treatment.
        if (maxIndex == 1)
        {
          return 0;
        }

        // Determine the next factory to use: avoid blocking algorithm.
        int oldNextIndex;
        int newNextIndex;
        do
        {
          oldNextIndex = nextIndex.get();
          newNextIndex = oldNextIndex + 1;
          if (newNextIndex == maxIndex)
          {
            newNextIndex = 0;
          }
        }
        while (!nextIndex.compareAndSet(oldNextIndex, newNextIndex));

        // There's a potential, but benign, race condition here: other threads
        // could jump in and rotate through the list before we return the
        // connection factory.
        return newNextIndex;
      }

    }



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

    private FailoverConnectionFactory searchFactory = null;
    private FailoverConnectionFactory bindFactory = null;



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
        if (searchFactory != null)
        {
          searchFactory.close();
          searchFactory = null;
        }

        if (bindFactory != null)
        {
          bindFactory.close();
          bindFactory = null;
        }

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

      // Create load-balancers for primary servers.
      final LoadBalancer primarySearchLoadBalancer;
      final LoadBalancer primaryBindLoadBalancer;

      Set<String> servers = configuration.getPrimaryRemoteLDAPServer();
      ConnectionPool[] searchPool = new ConnectionPool[servers.size()];
      ConnectionPool[] bindPool = new ConnectionPool[servers.size()];
      int index = 0;
      for (final String hostPort : servers)
      {
        final ConnectionFactory factory = newLDAPConnectionFactory(hostPort);
        searchPool[index] = new ConnectionPool(
            new AuthenticatedConnectionFactory(factory));
        bindPool[index++] = new ConnectionPool(factory);
      }
      primarySearchLoadBalancer = new LoadBalancer(searchPool);
      primaryBindLoadBalancer = new LoadBalancer(bindPool);

      // Create load-balancers for secondary servers.
      final LoadBalancer secondarySearchLoadBalancer;
      final LoadBalancer secondaryBindLoadBalancer;

      servers = configuration.getSecondaryRemoteLDAPServer();
      if (servers.isEmpty())
      {
        secondarySearchLoadBalancer = null;
        secondaryBindLoadBalancer = null;
      }
      else
      {
        searchPool = new ConnectionPool[servers.size()];
        bindPool = new ConnectionPool[servers.size()];
        index = 0;
        for (final String hostPort : servers)
        {
          final ConnectionFactory factory = newLDAPConnectionFactory(hostPort);
          searchPool[index] = new ConnectionPool(
              new AuthenticatedConnectionFactory(factory));
          bindPool[index++] = new ConnectionPool(factory);
        }
        secondarySearchLoadBalancer = new LoadBalancer(searchPool);
        secondaryBindLoadBalancer = new LoadBalancer(bindPool);
      }

      searchFactory = new FailoverConnectionFactory(primarySearchLoadBalancer,
          secondarySearchLoadBalancer);
      bindFactory = new FailoverConnectionFactory(primaryBindLoadBalancer,
          secondaryBindLoadBalancer);
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
   * Determines whether or no a result code is expected to trigger the
   * associated connection to be closed immediately.
   *
   * @param resultCode
   *          The result code.
   * @return {@code true} if the result code is expected to trigger the
   *         associated connection to be closed immediately.
   */
  static boolean isFatalResultCode(final ResultCode resultCode)
  {
    switch (resultCode)
    {
    case BUSY:
    case UNAVAILABLE:
    case PROTOCOL_ERROR:
    case OTHER:
    case UNWILLING_TO_PERFORM:
    case OPERATIONS_ERROR:
      return true;
    default:
      return false;
    }
  }



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
