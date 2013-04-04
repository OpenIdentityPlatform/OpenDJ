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
 *      Copyright 2011-2013 ForgeRock AS.
 */
package org.opends.server.extensions;



import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.util.StaticUtils.getExceptionMessage;
import static org.opends.server.util.StaticUtils.getFileForPath;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import javax.net.ssl.*;

import org.opends.messages.Message;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.meta.
  LDAPPassThroughAuthenticationPolicyCfgDefn.MappingPolicy;
import org.opends.server.admin.std.server.*;
import org.opends.server.api.*;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.loggers.debug.DebugLogger;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.ldap.*;
import org.opends.server.schema.GeneralizedTimeSyntax;
import org.opends.server.schema.SchemaConstants;
import org.opends.server.schema.UserPasswordSyntax;
import org.opends.server.tools.LDAPReader;
import org.opends.server.tools.LDAPWriter;
import org.opends.server.types.*;
import org.opends.server.util.TimeThread;



/**
 * LDAP pass through authentication policy implementation.
 */
public final class LDAPPassThroughAuthenticationPolicyFactory implements
    AuthenticationPolicyFactory<LDAPPassThroughAuthenticationPolicyCfg>
{

  // TODO: handle password policy response controls? AD?
  // TODO: custom aliveness pings
  // TODO: improve debug logging and error messages.

  /**
   * A simplistic load-balancer connection factory implementation using
   * approximately round-robin balancing.
   */
  static abstract class AbstractLoadBalancer implements ConnectionFactory,
      Runnable
  {
    /**
     * A connection which automatically retries operations on other servers.
     */
    private final class FailoverConnection implements Connection
    {
      private Connection connection;
      private MonitoredConnectionFactory factory;
      private final int startIndex;
      private int nextIndex;



      private FailoverConnection(final int startIndex)
          throws DirectoryException
      {
        this.startIndex = nextIndex = startIndex;

        DirectoryException lastException;
        do
        {
          factory = factories[nextIndex];
          if (factory.isAvailable)
          {
            try
            {
              connection = factory.getConnection();
              incrementNextIndex();
              return;
            }
            catch (final DirectoryException e)
            {
              // Ignore this error and try the next factory.
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);
              }
              lastException = e;
            }
          }
          else
          {
            lastException = factory.lastException;
          }
          incrementNextIndex();
        }
        while (nextIndex != startIndex);

        // All the factories have been tried so give up and throw the exception.
        throw lastException;
      }



      /**
       * {@inheritDoc}
       */
      @Override
      public void close()
      {
        connection.close();
      }



      /**
       * {@inheritDoc}
       */
      @Override
      public ByteString search(final DN baseDN, final SearchScope scope,
          final SearchFilter filter) throws DirectoryException
      {
        for (;;)
        {
          try
          {
            return connection.search(baseDN, scope, filter);
          }
          catch (final DirectoryException e)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }
            handleDirectoryException(e);
          }
        }
      }



      /**
       * {@inheritDoc}
       */
      @Override
      public void simpleBind(final ByteString username,
          final ByteString password) throws DirectoryException
      {
        for (;;)
        {
          try
          {
            connection.simpleBind(username, password);
            return;
          }
          catch (final DirectoryException e)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }
            handleDirectoryException(e);
          }
        }
      }



      private void handleDirectoryException(final DirectoryException e)
          throws DirectoryException
      {
        // If the error does not indicate that the connection has failed, then
        // pass this back to the caller.
        if (!isServiceError(e.getResultCode()))
        {
          throw e;
        }

        // The associated server is unavailable, so close the connection and
        // try the next connection factory.
        connection.close();
        factory.lastException = e;
        factory.isAvailable = false; // publishes lastException

        while (nextIndex != startIndex)
        {
          factory = factories[nextIndex];
          if (factory.isAvailable)
          {
            try
            {
              connection = factory.getConnection();
              incrementNextIndex();
              return;
            }
            catch (final DirectoryException de)
            {
              // Ignore this error and try the next factory.
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, de);
              }
            }
          }
          incrementNextIndex();
        }

        // All the factories have been tried so give up and throw the exception.
        throw e;
      }



      private void incrementNextIndex()
      {
        // Try the next index.
        if (++nextIndex == maxIndex)
        {
          nextIndex = 0;
        }
      }

    }



    /**
     * A connection factory which caches its online/offline state in order to
     * avoid unnecessary connection attempts when it is known to be offline.
     */
    private final class MonitoredConnectionFactory implements ConnectionFactory
    {
      private final ConnectionFactory factory;

      // isAvailable acts as memory barrier for lastException.
      private volatile boolean isAvailable = true;
      private DirectoryException lastException = null;



      private MonitoredConnectionFactory(final ConnectionFactory factory)
      {
        this.factory = factory;
      }



      /**
       * {@inheritDoc}
       */
      @Override
      public void close()
      {
        factory.close();
      }



      /**
       * {@inheritDoc}
       */
      @Override
      public Connection getConnection() throws DirectoryException
      {
        try
        {
          final Connection connection = factory.getConnection();
          isAvailable = true;
          return connection;
        }
        catch (final DirectoryException e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
          lastException = e;
          isAvailable = false; // publishes lastException
          throw e;
        }
      }
    }



    private final MonitoredConnectionFactory[] factories;
    private final int maxIndex;
    private final ScheduledFuture<?> monitorFuture;



    /**
     * Creates a new abstract load-balancer.
     *
     * @param factories
     *          The list of underlying connection factories.
     * @param scheduler
     *          The monitoring scheduler.
     */
    AbstractLoadBalancer(final ConnectionFactory[] factories,
        final ScheduledExecutorService scheduler)
    {
      this.factories = new MonitoredConnectionFactory[factories.length];
      this.maxIndex = factories.length;

      for (int i = 0; i < maxIndex; i++)
      {
        this.factories[i] = new MonitoredConnectionFactory(factories[i]);
      }

      this.monitorFuture = scheduler.scheduleWithFixedDelay(this, 5, 5,
          TimeUnit.SECONDS);
    }



    /**
     * Close underlying connection pools.
     */
    @Override
    public final void close()
    {
      monitorFuture.cancel(true);

      for (final ConnectionFactory factory : factories)
      {
        factory.close();
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public final Connection getConnection() throws DirectoryException
    {
      final int startIndex = getStartIndex();
      return new FailoverConnection(startIndex);
    }



    /**
     * Try to connect to any offline connection factories.
     */
    @Override
    public void run()
    {
      for (final MonitoredConnectionFactory factory : factories)
      {
        if (!factory.isAvailable)
        {
          try
          {
            factory.getConnection().close();
          }
          catch (final DirectoryException e)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }
          }
        }
      }
    }



    /**
     * Return the start which should be used for the next connection attempt.
     *
     * @return The start which should be used for the next connection attempt.
     */
    abstract int getStartIndex();

  }



  /**
   * A factory which returns pre-authenticated connections for searches.
   * <p>
   * Package private for testing.
   */
  static final class AuthenticatedConnectionFactory implements
      ConnectionFactory
  {

    private final ConnectionFactory factory;
    private final DN username;
    private final String password;



    /**
     * Creates a new authenticated connection factory which will bind on
     * connect.
     *
     * @param factory
     *          The underlying connection factory whose connections are to be
     *          authenticated.
     * @param username
     *          The username taken from the configuration.
     * @param password
     *          The password taken from the configuration.
     */
    AuthenticatedConnectionFactory(final ConnectionFactory factory,
        final DN username, final String password)
    {
      this.factory = factory;
      this.username = username;
      this.password = password;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void close()
    {
      factory.close();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public Connection getConnection() throws DirectoryException
    {
      final Connection connection = factory.getConnection();
      if (username != null && !username.isNullDN() && password != null
          && password.length() > 0)
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

  }



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
  static interface ConnectionFactory extends Closeable
  {
    /**
     * {@inheritDoc}
     * <p>
     * Must never throw an exception.
     */
    @Override
    void close();



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
   * PTA connection pool.
   * <p>
   * Package private for testing.
   */
  static final class ConnectionPool implements ConnectionFactory
  {

    /**
     * Pooled connection's intercept close and release connection back to the
     * pool.
     */
    private final class PooledConnection implements Connection
    {
      private Connection connection;
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
            connectionPool.offer(connection);
          }

          connection = null;
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
        catch (final DirectoryException e1)
        {
          // Fail immediately if the result indicates that the operation failed
          // for a reason other than connection/server failure.
          reconnectIfConnectionFailure(e1);

          // The connection has failed, so retry the operation using the new
          // connection.
          try
          {
            return connection.search(baseDN, scope, filter);
          }
          catch (final DirectoryException e2)
          {
            // If the connection has failed again then give up: don't put the
            // connection back in the pool.
            closeIfConnectionFailure(e2);
            throw e2;
          }
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
        catch (final DirectoryException e1)
        {
          // Fail immediately if the result indicates that the operation failed
          // for a reason other than connection/server failure.
          reconnectIfConnectionFailure(e1);

          // The connection has failed, so retry the operation using the new
          // connection.
          try
          {
            connection.simpleBind(username, password);
          }
          catch (final DirectoryException e2)
          {
            // If the connection has failed again then give up: don't put the
            // connection back in the pool.
            closeIfConnectionFailure(e2);
            throw e2;
          }
        }
      }



      private void closeIfConnectionFailure(final DirectoryException e)
          throws DirectoryException
      {
        if (isServiceError(e.getResultCode()))
        {
          connectionIsClosed = true;
          connection.close();
          connection = null;
          availableConnections.release();
        }
      }



      private void reconnectIfConnectionFailure(final DirectoryException e)
          throws DirectoryException
      {
        if (!isServiceError(e.getResultCode()))
        {
          throw e;
        }

        // The connection has failed (e.g. idle timeout), so repeat the
        // request on a new connection.
        connection.close();
        try
        {
          connection = factory.getConnection();
        }
        catch (final DirectoryException e2)
        {
          // Give up - the server is unreachable.
          connectionIsClosed = true;
          connection = null;
          availableConnections.release();
          throw e2;
        }
      }
    }



    // Guarded by PolicyImpl.lock.
    private boolean poolIsClosed = false;

    private final ConnectionFactory factory;
    private final int poolSize = Runtime.getRuntime().availableProcessors() * 2;
    private final Semaphore availableConnections = new Semaphore(poolSize);
    private final Queue<Connection> connectionPool =
      new ConcurrentLinkedQueue<Connection>();



    /**
     * Creates a new connection pool for the provided factory.
     *
     * @param factory
     *          The underlying connection factory whose connections are to be
     *          pooled.
     */
    ConnectionPool(final ConnectionFactory factory)
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

      Connection connection;
      while ((connection = connectionPool.poll()) != null)
      {
        connection.close();
      }

      factory.close();

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
      Connection connection = connectionPool.poll();
      if (connection == null)
      {
        try
        {
          connection = factory.getConnection();
        }
        catch (final DirectoryException e)
        {
          availableConnections.release();
          throw e;
        }
      }

      return new PooledConnection(connection);
    }
  }



  /**
   * A simplistic two-way fail-over connection factory implementation.
   * <p>
   * Package private for testing.
   */
  static final class FailoverLoadBalancer extends AbstractLoadBalancer
  {

    /**
     * Creates a new fail-over connection factory which will always try the
     * primary connection factory first, before trying the second.
     *
     * @param primary
     *          The primary connection factory.
     * @param secondary
     *          The secondary connection factory.
     * @param scheduler
     *          The monitoring scheduler.
     */
    FailoverLoadBalancer(final ConnectionFactory primary,
        final ConnectionFactory secondary,
        final ScheduledExecutorService scheduler)
    {
      super(new ConnectionFactory[] { primary, secondary }, scheduler);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    int getStartIndex()
    {
      // Always start with the primaries.
      return 0;
    }

  }



  /**
   * The PTA design guarantees that connections are only used by a single thread
   * at a time, so we do not need to perform any synchronization.
   * <p>
   * Package private for testing.
   */
  static final class LDAPConnectionFactory implements ConnectionFactory
  {
    /**
     * LDAP connection implementation.
     */
    private final class LDAPConnection implements Connection
    {
      private final Socket plainSocket;
      private final Socket ldapSocket;
      private final LDAPWriter writer;
      private final LDAPReader reader;
      private int nextMessageID = 1;
      private boolean isClosed = false;



      private LDAPConnection(final Socket plainSocket, final Socket ldapSocket,
          final LDAPReader reader, final LDAPWriter writer)
      {
        this.plainSocket = plainSocket;
        this.ldapSocket = ldapSocket;
        this.reader = reader;
        this.writer = writer;
      }



      /**
       * {@inheritDoc}
       */
      @Override
      public void close()
      {
        /*
         * This method is intentionally a bit "belt and braces" because we have
         * seen far too many subtle resource leaks due to bugs within JDK,
         * especially when used in conjunction with SSL (e.g.
         * http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=7025227).
         */
        if (isClosed)
        {
          return;
        }
        isClosed = true;

        // Send an unbind request.
        final LDAPMessage message = new LDAPMessage(nextMessageID++,
            new UnbindRequestProtocolOp());
        try
        {
          writer.writeMessage(message);
        }
        catch (final IOException e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
        }

        // Close all IO resources.
        writer.close();
        reader.close();

        try
        {
          ldapSocket.close();
        }
        catch (final IOException e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
        }

        try
        {
          plainSocket.close();
        }
        catch (final IOException e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
        }
      }



      /**
       * {@inheritDoc}
       */
      @Override
      public ByteString search(final DN baseDN, final SearchScope scope,
          final SearchFilter filter) throws DirectoryException
      {
        // Create the search request and send it to the server.
        final SearchRequestProtocolOp searchRequest =
          new SearchRequestProtocolOp(
            ByteString.valueOf(baseDN.toString()), scope,
            DereferencePolicy.DEREF_ALWAYS, 1 /* size limit */,
            (timeoutMS / 1000), true /* types only */,
            RawFilter.create(filter), NO_ATTRIBUTES);
        sendRequest(searchRequest);

        // Read the responses from the server. We cannot fail-fast since this
        // could leave unread search response messages.
        byte opType;
        ByteString username = null;
        int resultCount = 0;

        do
        {
          final LDAPMessage responseMessage = readResponse();
          opType = responseMessage.getProtocolOpType();

          switch (opType)
          {
          case OP_TYPE_SEARCH_RESULT_ENTRY:
            final SearchResultEntryProtocolOp searchEntry = responseMessage
                .getSearchResultEntryProtocolOp();
            if (username == null)
            {
              username = ByteString.valueOf(searchEntry.getDN().toString());
            }
            resultCount++;
            break;

          case OP_TYPE_SEARCH_RESULT_REFERENCE:
            // The reference does not necessarily mean that there would have
            // been any matching results, so lets ignore it.
            break;

          case OP_TYPE_SEARCH_RESULT_DONE:
            final SearchResultDoneProtocolOp searchResult = responseMessage
                .getSearchResultDoneProtocolOp();

            final ResultCode resultCode = ResultCode.valueOf(searchResult
                .getResultCode());
            switch (resultCode)
            {
            case SUCCESS:
              // The search succeeded. Drop out of the loop and check that we
              // got a matching entry.
              break;

            case SIZE_LIMIT_EXCEEDED:
              // Multiple matching candidates.
              throw new DirectoryException(
                  ResultCode.CLIENT_SIDE_MORE_RESULTS_TO_RETURN,
                  ERR_LDAP_PTA_CONNECTION_SEARCH_SIZE_LIMIT.get(host, port,
                      String.valueOf(cfg.dn()), String.valueOf(baseDN),
                      String.valueOf(filter)));

            default:
              // The search failed for some reason.
              throw new DirectoryException(resultCode,
                  ERR_LDAP_PTA_CONNECTION_SEARCH_FAILED.get(host, port,
                      String.valueOf(cfg.dn()), String.valueOf(baseDN),
                      String.valueOf(filter), resultCode.getIntValue(),
                      resultCode.getResultCodeName(),
                      searchResult.getErrorMessage()));
            }

            break;

          default:
            // Check for disconnect notifications.
            handleUnexpectedResponse(responseMessage);
            break;
          }
        }
        while (opType != OP_TYPE_SEARCH_RESULT_DONE);

        if (resultCount > 1)
        {
          // Multiple matching candidates.
          throw new DirectoryException(
              ResultCode.CLIENT_SIDE_MORE_RESULTS_TO_RETURN,
              ERR_LDAP_PTA_CONNECTION_SEARCH_SIZE_LIMIT.get(host, port,
                  String.valueOf(cfg.dn()), String.valueOf(baseDN),
                  String.valueOf(filter)));
        }

        if (username == null)
        {
          // No matching entries found.
          throw new DirectoryException(
              ResultCode.CLIENT_SIDE_NO_RESULTS_RETURNED,
              ERR_LDAP_PTA_CONNECTION_SEARCH_NO_MATCHES.get(host, port,
                  String.valueOf(cfg.dn()), String.valueOf(baseDN),
                  String.valueOf(filter)));
        }

        return username;
      }



      /**
       * {@inheritDoc}
       */
      @Override
      public void simpleBind(final ByteString username,
          final ByteString password) throws DirectoryException
      {
        // Create the bind request and send it to the server.
        final BindRequestProtocolOp bindRequest = new BindRequestProtocolOp(
            username, 3, password);
        sendRequest(bindRequest);

        // Read the response from the server.
        final LDAPMessage responseMessage = readResponse();
        switch (responseMessage.getProtocolOpType())
        {
        case OP_TYPE_BIND_RESPONSE:
          final BindResponseProtocolOp bindResponse = responseMessage
              .getBindResponseProtocolOp();

          final ResultCode resultCode = ResultCode.valueOf(bindResponse
              .getResultCode());
          if (resultCode == ResultCode.SUCCESS)
          {
            // FIXME: need to look for things like password expiration
            // warning, reset notice, etc.
            return;
          }
          else
          {
            // The bind failed for some reason.
            throw new DirectoryException(resultCode,
                ERR_LDAP_PTA_CONNECTION_BIND_FAILED.get(host, port,
                    String.valueOf(cfg.dn()), String.valueOf(username),
                    resultCode.getIntValue(), resultCode.getResultCodeName(),
                    bindResponse.getErrorMessage()));
          }

        default:
          // Check for disconnect notifications.
          handleUnexpectedResponse(responseMessage);
          break;
        }
      }



      /**
       * {@inheritDoc}
       */
      @Override
      protected void finalize()
      {
        close();
      }



      private void handleUnexpectedResponse(final LDAPMessage responseMessage)
          throws DirectoryException
      {
        if (responseMessage.getProtocolOpType() == OP_TYPE_EXTENDED_RESPONSE)
        {
          final ExtendedResponseProtocolOp extendedResponse = responseMessage
              .getExtendedResponseProtocolOp();
          final String responseOID = extendedResponse.getOID();

          if ((responseOID != null)
              && responseOID.equals(OID_NOTICE_OF_DISCONNECTION))
          {
            ResultCode resultCode = ResultCode.valueOf(extendedResponse
                .getResultCode());

            /*
             * Since the connection has been disconnected we want to ensure that
             * upper layers treat all disconnect notifications as fatal and
             * close the connection. Therefore we map the result code to a fatal
             * error code if needed. A good example of a non-fatal error code
             * being returned is INVALID_CREDENTIALS which is used to indicate
             * that the currently bound user has had their entry removed. We
             * definitely don't want to pass this straight back to the caller
             * since it will be misinterpreted as an authentication failure if
             * the operation being performed is a bind.
             */
            ResultCode mappedResultCode = isServiceError(resultCode) ?
                resultCode : ResultCode.UNAVAILABLE;

            throw new DirectoryException(mappedResultCode,
                ERR_LDAP_PTA_CONNECTION_DISCONNECTING.get(host, port,
                    String.valueOf(cfg.dn()), resultCode.getIntValue(),
                    resultCode.getResultCodeName(),
                    extendedResponse.getErrorMessage()));
          }
        }

        // Unexpected response type.
        throw new DirectoryException(ResultCode.CLIENT_SIDE_DECODING_ERROR,
            ERR_LDAP_PTA_CONNECTION_WRONG_RESPONSE.get(host, port,
                String.valueOf(cfg.dn()),
                String.valueOf(responseMessage.getProtocolOp())));
      }



      // Reads a response message and adapts errors to directory exceptions.
      private LDAPMessage readResponse() throws DirectoryException
      {
        final LDAPMessage responseMessage;
        try
        {
          responseMessage = reader.readMessage();
        }
        catch (final ASN1Exception e)
        {
          // ASN1 layer hides all underlying IO exceptions.
          if (e.getCause() instanceof SocketTimeoutException)
          {
            throw new DirectoryException(ResultCode.CLIENT_SIDE_TIMEOUT,
                ERR_LDAP_PTA_CONNECTION_TIMEOUT.get(host, port,
                    String.valueOf(cfg.dn())), e);
          }
          else if (e.getCause() instanceof IOException)
          {
            throw new DirectoryException(ResultCode.CLIENT_SIDE_SERVER_DOWN,
                ERR_LDAP_PTA_CONNECTION_OTHER_ERROR.get(host, port,
                    String.valueOf(cfg.dn()), e.getMessage()), e);
          }
          else
          {
            throw new DirectoryException(ResultCode.CLIENT_SIDE_DECODING_ERROR,
                ERR_LDAP_PTA_CONNECTION_DECODE_ERROR.get(host, port,
                    String.valueOf(cfg.dn()), e.getMessage()), e);
          }
        }
        catch (final LDAPException e)
        {
          throw new DirectoryException(ResultCode.CLIENT_SIDE_DECODING_ERROR,
              ERR_LDAP_PTA_CONNECTION_DECODE_ERROR.get(host, port,
                  String.valueOf(cfg.dn()), e.getMessage()), e);
        }
        catch (final SocketTimeoutException e)
        {
          throw new DirectoryException(ResultCode.CLIENT_SIDE_TIMEOUT,
              ERR_LDAP_PTA_CONNECTION_TIMEOUT.get(host, port,
                  String.valueOf(cfg.dn())), e);
        }
        catch (final IOException e)
        {
          throw new DirectoryException(ResultCode.CLIENT_SIDE_SERVER_DOWN,
              ERR_LDAP_PTA_CONNECTION_OTHER_ERROR.get(host, port,
                  String.valueOf(cfg.dn()), e.getMessage()), e);
        }

        if (responseMessage == null)
        {
          throw new DirectoryException(ResultCode.CLIENT_SIDE_SERVER_DOWN,
              ERR_LDAP_PTA_CONNECTION_CLOSED.get(host, port,
                  String.valueOf(cfg.dn())));
        }
        return responseMessage;
      }



      // Sends a request message and adapts errors to directory exceptions.
      private void sendRequest(final ProtocolOp request)
          throws DirectoryException
      {
        final LDAPMessage requestMessage = new LDAPMessage(nextMessageID++,
            request);
        try
        {
          writer.writeMessage(requestMessage);
        }
        catch (final IOException e)
        {
          throw new DirectoryException(ResultCode.CLIENT_SIDE_SERVER_DOWN,
              ERR_LDAP_PTA_CONNECTION_OTHER_ERROR.get(host, port,
                  String.valueOf(cfg.dn()), e.getMessage()), e);
        }
      }
    }



    private final String host;
    private final int port;
    private final LDAPPassThroughAuthenticationPolicyCfg cfg;
    private final int timeoutMS;



    /**
     * LDAP connection factory implementation is package private so that it can
     * be tested.
     *
     * @param host
     *          The server host name.
     * @param port
     *          The server port.
     * @param cfg
     *          The configuration (for SSL).
     */
    LDAPConnectionFactory(final String host, final int port,
        final LDAPPassThroughAuthenticationPolicyCfg cfg)
    {
      this.host = host;
      this.port = port;
      this.cfg = cfg;

      // Normalize the timeoutMS to an integer (admin framework ensures that the
      // value is non-negative).
      this.timeoutMS = (int) Math.min(cfg.getConnectionTimeout(),
          Integer.MAX_VALUE);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void close()
    {
      // Nothing to do.
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public Connection getConnection() throws DirectoryException
    {
      try
      {
        // Create the remote ldapSocket address.
        final InetAddress address = InetAddress.getByName(host);
        final InetSocketAddress socketAddress = new InetSocketAddress(address,
            port);

        // Create the ldapSocket and connect to the remote server.
        final Socket plainSocket = new Socket();
        Socket ldapSocket = null;
        LDAPReader reader = null;
        LDAPWriter writer = null;
        LDAPConnection ldapConnection = null;

        try
        {
          // Set ldapSocket cfg before connecting.
          plainSocket.setTcpNoDelay(cfg.isUseTCPNoDelay());
          plainSocket.setKeepAlive(cfg.isUseTCPKeepAlive());
          plainSocket.setSoTimeout(timeoutMS);

          // Connect the ldapSocket.
          plainSocket.connect(socketAddress, timeoutMS);

          if (cfg.isUseSSL())
          {
            // Obtain the optional configured trust manager which will be used
            // in order to determine the trust of the remote LDAP server.
            TrustManager[] tm = null;
            final DN trustManagerDN = cfg.getTrustManagerProviderDN();
            if (trustManagerDN != null)
            {
              final TrustManagerProvider<?> trustManagerProvider =
                DirectoryServer.getTrustManagerProvider(trustManagerDN);
              if (trustManagerProvider != null)
              {
                tm = trustManagerProvider.getTrustManagers();
              }
            }

            // Create the SSL context and initialize it.
            final SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null /* key managers */, tm, null /* rng */);

            // Create the SSL socket.
            final SSLSocketFactory sslSocketFactory = sslContext
                .getSocketFactory();
            final SSLSocket sslSocket = (SSLSocket) sslSocketFactory
                .createSocket(plainSocket, host, port, true);
            ldapSocket = sslSocket;

            sslSocket.setUseClientMode(true);
            if (!cfg.getSSLProtocol().isEmpty())
            {
              sslSocket.setEnabledProtocols(cfg.getSSLProtocol().toArray(
                  new String[0]));
            }
            if (!cfg.getSSLCipherSuite().isEmpty())
            {
              sslSocket.setEnabledCipherSuites(cfg.getSSLCipherSuite().toArray(
                  new String[0]));
            }

            // Force TLS negotiation.
            sslSocket.startHandshake();
          }
          else
          {
            ldapSocket = plainSocket;
          }

          reader = new LDAPReader(ldapSocket);
          writer = new LDAPWriter(ldapSocket);

          ldapConnection = new LDAPConnection(plainSocket, ldapSocket, reader,
              writer);

          return ldapConnection;
        }
        finally
        {
          if (ldapConnection == null)
          {
            // Connection creation failed for some reason, so clean up IO
            // resources.
            if (reader != null)
            {
              reader.close();
            }
            if (writer != null)
            {
              writer.close();
            }

            if (ldapSocket != null)
            {
              try
              {
                ldapSocket.close();
              }
              catch (final IOException ignored)
              {
                // Ignore.
              }
            }

            if (ldapSocket != plainSocket)
            {
              try
              {
                plainSocket.close();
              }
              catch (final IOException ignored)
              {
                // Ignore.
              }
            }
          }
        }
      }
      catch (final UnknownHostException e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
        throw new DirectoryException(ResultCode.CLIENT_SIDE_CONNECT_ERROR,
            ERR_LDAP_PTA_CONNECT_UNKNOWN_HOST.get(host, port,
                String.valueOf(cfg.dn()), host), e);
      }
      catch (final ConnectException e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
        throw new DirectoryException(ResultCode.CLIENT_SIDE_CONNECT_ERROR,
            ERR_LDAP_PTA_CONNECT_ERROR.get(host, port,
                String.valueOf(cfg.dn()), port), e);
      }
      catch (final SocketTimeoutException e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
        throw new DirectoryException(ResultCode.CLIENT_SIDE_TIMEOUT,
            ERR_LDAP_PTA_CONNECT_TIMEOUT.get(host, port,
                String.valueOf(cfg.dn())), e);
      }
      catch (final SSLException e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
        throw new DirectoryException(ResultCode.CLIENT_SIDE_CONNECT_ERROR,
            ERR_LDAP_PTA_CONNECT_SSL_ERROR.get(host, port,
                String.valueOf(cfg.dn()), e.getMessage()), e);
      }
      catch (final Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
        throw new DirectoryException(ResultCode.CLIENT_SIDE_CONNECT_ERROR,
            ERR_LDAP_PTA_CONNECT_OTHER_ERROR.get(host, port,
                String.valueOf(cfg.dn()), e.getMessage()), e);
      }

    }
  }



  /**
   * An interface for obtaining a connection factory for LDAP connections to a
   * named LDAP server and the monitoring scheduler.
   */
  static interface Provider
  {
    /**
     * Returns a connection factory which can be used for obtaining connections
     * to the specified LDAP server.
     *
     * @param host
     *          The LDAP server host name.
     * @param port
     *          The LDAP server port.
     * @param cfg
     *          The LDAP connection configuration.
     * @return A connection factory which can be used for obtaining connections
     *         to the specified LDAP server.
     */
    ConnectionFactory getLDAPConnectionFactory(String host, int port,
        LDAPPassThroughAuthenticationPolicyCfg cfg);



    /**
     * Returns the scheduler which should be used to periodically ping
     * connection factories to determine when they are online.
     *
     * @return The scheduler which should be used to periodically ping
     *         connection factories to determine when they are online.
     */
    ScheduledExecutorService getScheduledExecutorService();



    /**
     * Returns the current time in order to perform cached password expiration
     * checks. The returned string will be formatted as a a generalized time
     * string
     *
     * @return The current time.
     */
    String getCurrentTime();



    /**
     * Returns the current time in order to perform cached password expiration
     * checks.
     *
     * @return The current time in MS.
     */
    long getCurrentTimeMS();
  }



  /**
   * A simplistic load-balancer connection factory implementation using
   * approximately round-robin balancing.
   */
  static final class RoundRobinLoadBalancer extends AbstractLoadBalancer
  {
    private final AtomicInteger nextIndex = new AtomicInteger();
    private final int maxIndex;



    /**
     * Creates a new load-balancer which will distribute connection requests
     * across a set of underlying connection factories.
     *
     * @param factories
     *          The list of underlying connection factories.
     * @param scheduler
     *          The monitoring scheduler.
     */
    RoundRobinLoadBalancer(final ConnectionFactory[] factories,
        final ScheduledExecutorService scheduler)
    {
      super(factories, scheduler);
      this.maxIndex = factories.length;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    int getStartIndex()
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
      return oldNextIndex;
    }

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

      private final AttributeType cachedPasswordAttribute;
      private final AttributeType cachedPasswordTimeAttribute;

      private ByteString newCachedPassword = null;




      private StateImpl(final Entry userEntry)
      {
        super(userEntry);

        this.cachedPasswordAttribute = DirectoryServer.getAttributeType(
            OP_ATTR_PTAPOLICY_CACHED_PASSWORD, true);
        this.cachedPasswordTimeAttribute = DirectoryServer.getAttributeType(
            OP_ATTR_PTAPOLICY_CACHED_PASSWORD_TIME, true);
      }



      /**
       * {@inheritDoc}
       */
      @Override
      public void finalizeStateAfterBind() throws DirectoryException
      {
        sharedLock.lock();
        try
        {
          if (cfg.isUsePasswordCaching() && newCachedPassword != null)
          {
            // Update the user's entry to contain the cached password and
            // time stamp.
            ByteString encodedPassword = pwdStorageScheme
                .encodePasswordWithScheme(newCachedPassword);

            List<RawModification> modifications =
              new ArrayList<RawModification>(2);
            modifications.add(RawModification.create(ModificationType.REPLACE,
                OP_ATTR_PTAPOLICY_CACHED_PASSWORD, encodedPassword));
            modifications.add(RawModification.create(ModificationType.REPLACE,
                OP_ATTR_PTAPOLICY_CACHED_PASSWORD_TIME,
                provider.getCurrentTime()));

            InternalClientConnection conn = InternalClientConnection
                .getRootConnection();
            ModifyOperation internalModify = conn.processModify(userEntry
                .getDN().toString(), modifications);

            ResultCode resultCode = internalModify.getResultCode();
            if (resultCode != ResultCode.SUCCESS)
            {
              // The modification failed for some reason. This should not
              // prevent the bind from succeeded since we are only updating
              // cache data. However, the performance of the server may be
              // impacted, so log a debug warning message.
              if (debugEnabled())
              {
                TRACER.debugWarning(
                    "An error occurred while trying to update the LDAP PTA "
                        + "cached password for user %s: %s", userEntry.getDN()
                        .toString(), String.valueOf(internalModify
                        .getErrorMessage()));
              }
            }

            newCachedPassword = null;
          }
        }
        finally
        {
          sharedLock.unlock();
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
          // First check the cached password if enabled and available.
          if (passwordMatchesCachedPassword(password))
          {
            return true;
          }

          // The cache lookup failed, so perform full PTA.
          ByteString username = null;

          switch (cfg.getMappingPolicy())
          {
          case UNMAPPED:
            // The bind DN is the name of the user's entry.
            username = ByteString.valueOf(userEntry.getDN().toString());
            break;
          case MAPPED_BIND:
            // The bind DN is contained in an attribute in the user's entry.
            mapBind: for (final AttributeType at : cfg.getMappedAttribute())
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
                      String.valueOf(cfg.dn()),
                      mappedAttributesAsString(cfg.getMappedAttribute())));
            }

            break;
          case MAPPED_SEARCH:
            // A search against the remote directory is required in order to
            // determine the bind DN.

            // Construct the search filter.
            final LinkedList<SearchFilter> filterComponents =
              new LinkedList<SearchFilter>();
            for (final AttributeType at : cfg.getMappedAttribute())
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
                      String.valueOf(cfg.dn()),
                      mappedAttributesAsString(cfg.getMappedAttribute())));
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
            for (final DN baseDN : cfg.getMappedSearchBaseDN())
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
                          String.valueOf(cfg.dn()), String.valueOf(baseDN),
                          String.valueOf(filter)));
                default:
                  // We don't want to propagate this internal error to the
                  // client. We should log it and map it to a more appropriate
                  // error.
                  throw new DirectoryException(ResultCode.INVALID_CREDENTIALS,
                      ERR_LDAP_PTA_MAPPED_SEARCH_FAILED.get(
                          String.valueOf(userEntry.getDN()),
                          String.valueOf(cfg.dn()), e.getMessageObject()), e);
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
                      String.valueOf(cfg.dn()), String.valueOf(filter)));
            }

            break;
          }

          // Now perform the bind.
          Connection connection = null;
          try
          {
            connection = bindFactory.getConnection();
            connection.simpleBind(username, password);

            // The password matched, so cache it, it will be stored in the
            // user's entry when the state is finalized and only if caching is
            // enabled.
            newCachedPassword = password;
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
              throw new DirectoryException(ResultCode.INVALID_CREDENTIALS,
                  ERR_LDAP_PTA_MAPPED_BIND_FAILED.get(
                      String.valueOf(userEntry.getDN()),
                      String.valueOf(cfg.dn()), e.getMessageObject()), e);
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



      private boolean passwordMatchesCachedPassword(ByteString password)
      {
        if (!cfg.isUsePasswordCaching())
        {
          return false;
        }

        // First determine if the cached password time is present and valid.
        boolean foundValidCachedPasswordTime = false;

        List<Attribute> cptlist = userEntry
            .getAttribute(cachedPasswordTimeAttribute);
        if (cptlist != null && !cptlist.isEmpty())
        {
          foundCachedPasswordTime:
          {
            for (Attribute attribute : cptlist)
            {
              // Ignore any attributes with options.
              if (!attribute.hasOptions())
              {
                for (AttributeValue value : attribute)
                {
                  try
                  {
                    long cachedPasswordTime = GeneralizedTimeSyntax
                        .decodeGeneralizedTimeValue(value.getNormalizedValue());
                    long currentTime = provider.getCurrentTimeMS();
                    long expiryTime = cachedPasswordTime
                        + (cfg.getCachedPasswordTTL() * 1000);
                    foundValidCachedPasswordTime = (expiryTime > currentTime);
                  }
                  catch (DirectoryException e)
                  {
                    // Fall-through and give up immediately.
                    if (debugEnabled())
                    {
                      TRACER.debugCaught(DebugLogLevel.ERROR, e);
                    }
                  }

                  break foundCachedPasswordTime;
                }
              }
            }
          }
        }

        if (!foundValidCachedPasswordTime)
        {
          // The cached password time was not found or it has expired, so give
          // up immediately.
          return false;
        }

        // Next determine if there is a cached password.
        ByteString cachedPassword = null;

        List<Attribute> cplist = userEntry
            .getAttribute(cachedPasswordAttribute);
        if (cplist != null && !cplist.isEmpty())
        {
          foundCachedPassword:
          {
            for (Attribute attribute : cplist)
            {
              // Ignore any attributes with options.
              if (!attribute.hasOptions())
              {
                for (AttributeValue value : attribute)
                {
                  cachedPassword = value.getValue();
                  break foundCachedPassword;
                }
              }
            }
          }
        }

        if (cachedPassword == null)
        {
          // The cached password was not found, so give up immediately.
          return false;
        }

        // Decode the password and match it according to its storage scheme.
        try
        {
          String[] userPwComponents = UserPasswordSyntax
              .decodeUserPassword(cachedPassword.toString());
          PasswordStorageScheme<?> scheme = DirectoryServer
              .getPasswordStorageScheme(userPwComponents[0]);
          if (scheme != null)
          {
            return scheme.passwordMatches(password,
                ByteString.valueOf(userPwComponents[1]));
          }
        }
        catch (DirectoryException e)
        {
          // Unable to decode the cached password, so give up.
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
        }

        return false;
      }
    }



    // Guards against configuration changes.
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ReadLock sharedLock = lock.readLock();
    private final WriteLock exclusiveLock = lock.writeLock();

    // Current configuration.
    private LDAPPassThroughAuthenticationPolicyCfg cfg;

    private ConnectionFactory searchFactory = null;
    private ConnectionFactory bindFactory = null;

    private PasswordStorageScheme<?> pwdStorageScheme = null;



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
        final LDAPPassThroughAuthenticationPolicyCfg cfg)
    {
      exclusiveLock.lock();
      try
      {
        closeConnections();
        initializeConfiguration(cfg);
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
        cfg.removeLDAPPassThroughChangeListener(this);
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
      return cfg.dn();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isConfigurationChangeAcceptable(
        final LDAPPassThroughAuthenticationPolicyCfg cfg,
        final List<Message> unacceptableReasons)
    {
      return LDAPPassThroughAuthenticationPolicyFactory.this
          .isConfigurationAcceptable(cfg, unacceptableReasons);
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
        final LDAPPassThroughAuthenticationPolicyCfg cfg)
    {
      this.cfg = cfg;

      // First obtain the mapped search password if needed, ignoring any errors
      // since these should have already been detected during configuration
      // validation.
      final String mappedSearchPassword;
      if (cfg.getMappingPolicy() == MappingPolicy.MAPPED_SEARCH
          && cfg.getMappedSearchBindDN() != null
          && !cfg.getMappedSearchBindDN().isNullDN())
      {
        mappedSearchPassword = getMappedSearchBindPassword(cfg,
            new LinkedList<Message>());
      }
      else
      {
        mappedSearchPassword = null;
      }

      // Use two pools per server: one for authentication (bind) and one for
      // searches. Even if the searches are performed anonymously we cannot use
      // the same pool, otherwise they will be performed as the most recently
      // authenticated user.

      // Create load-balancers for primary servers.
      final RoundRobinLoadBalancer primarySearchLoadBalancer;
      final RoundRobinLoadBalancer primaryBindLoadBalancer;
      final ScheduledExecutorService scheduler = provider
          .getScheduledExecutorService();

      Set<String> servers = cfg.getPrimaryRemoteLDAPServer();
      ConnectionPool[] searchPool = new ConnectionPool[servers.size()];
      ConnectionPool[] bindPool = new ConnectionPool[servers.size()];
      int index = 0;
      for (final String hostPort : servers)
      {
        final ConnectionFactory factory = newLDAPConnectionFactory(hostPort);
        searchPool[index] = new ConnectionPool(
            new AuthenticatedConnectionFactory(factory,
                cfg.getMappedSearchBindDN(),
                mappedSearchPassword));
        bindPool[index++] = new ConnectionPool(factory);
      }
      primarySearchLoadBalancer = new RoundRobinLoadBalancer(searchPool,
          scheduler);
      primaryBindLoadBalancer = new RoundRobinLoadBalancer(bindPool, scheduler);

      // Create load-balancers for secondary servers.
      servers = cfg.getSecondaryRemoteLDAPServer();
      if (servers.isEmpty())
      {
        searchFactory = primarySearchLoadBalancer;
        bindFactory = primaryBindLoadBalancer;
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
              new AuthenticatedConnectionFactory(factory,
                  cfg.getMappedSearchBindDN(),
                  mappedSearchPassword));
          bindPool[index++] = new ConnectionPool(factory);
        }
        final RoundRobinLoadBalancer secondarySearchLoadBalancer =
          new RoundRobinLoadBalancer(searchPool, scheduler);
        final RoundRobinLoadBalancer secondaryBindLoadBalancer =
          new RoundRobinLoadBalancer(bindPool, scheduler);
        searchFactory = new FailoverLoadBalancer(primarySearchLoadBalancer,
            secondarySearchLoadBalancer, scheduler);
        bindFactory = new FailoverLoadBalancer(primaryBindLoadBalancer,
            secondaryBindLoadBalancer, scheduler);
      }

      if (cfg.isUsePasswordCaching())
      {
        pwdStorageScheme = DirectoryServer.getPasswordStorageScheme(cfg
            .getCachedPasswordStorageSchemeDN());
      }
    }



    private ConnectionFactory newLDAPConnectionFactory(final String hostPort)
    {
      // Validation already performed by admin framework.
      final int colonIndex = hostPort.lastIndexOf(":");
      final String hostname = hostPort.substring(0, colonIndex);
      final int port = Integer.parseInt(hostPort.substring(colonIndex + 1));
      return provider.getLDAPConnectionFactory(hostname, port, cfg);
    }

  }



  // Debug tracer for this class.
  private static final DebugTracer TRACER = DebugLogger.getTracer();

  /**
   * Attribute list for searches requesting no attributes.
   */
  static final LinkedHashSet<String> NO_ATTRIBUTES = new LinkedHashSet<String>(
      1);
  static
  {
    NO_ATTRIBUTES.add(SchemaConstants.NO_ATTRIBUTES);
  }

  // The provider which should be used by policies to create LDAP connections.
  private final Provider provider;

  /**
   * The default LDAP connection factory provider.
   */
  private static final Provider DEFAULT_PROVIDER = new Provider()
  {

    // Global scheduler used for periodically monitoring connection factories in
    // order to detect when they are online.
    private final ScheduledExecutorService scheduler = Executors
        .newScheduledThreadPool(2, new ThreadFactory()
        {

          @Override
          public Thread newThread(final Runnable r)
          {
            final Thread t = new DirectoryThread(r,
                "LDAP PTA connection monitor thread");
            t.setDaemon(true);
            return t;
          }
        });



    @Override
    public ConnectionFactory getLDAPConnectionFactory(final String host,
        final int port, final LDAPPassThroughAuthenticationPolicyCfg cfg)
    {
      return new LDAPConnectionFactory(host, port, cfg);
    }



    @Override
    public ScheduledExecutorService getScheduledExecutorService()
    {
      return scheduler;
    }



    public String getCurrentTime()
    {
      return TimeThread.getGMTTime();
    }



    public long getCurrentTimeMS()
    {
      return TimeThread.getTime();
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
  static boolean isServiceError(final ResultCode resultCode)
  {
    switch (resultCode)
    {
    case OPERATIONS_ERROR:
    case PROTOCOL_ERROR:
    case TIME_LIMIT_EXCEEDED:
    case ADMIN_LIMIT_EXCEEDED:
    case UNAVAILABLE_CRITICAL_EXTENSION:
    case BUSY:
    case UNAVAILABLE:
    case UNWILLING_TO_PERFORM:
    case LOOP_DETECT:
    case OTHER:
    case CLIENT_SIDE_CONNECT_ERROR:
    case CLIENT_SIDE_DECODING_ERROR:
    case CLIENT_SIDE_ENCODING_ERROR:
    case CLIENT_SIDE_LOCAL_ERROR:
    case CLIENT_SIDE_SERVER_DOWN:
    case CLIENT_SIDE_TIMEOUT:
      return true;
    default:
      return false;
    }
  }



//Get the search bind password performing mapped searches.
  //
  // We will offer several places to look for the password, and we will
  // do so in the following order:
  //
  // - In a specified Java property
  // - In a specified environment variable
  // - In a specified file on the server filesystem.
  // - As the value of a configuration attribute.
  //
  // In any case, the password must be in the clear.
  private static String getMappedSearchBindPassword(
      final LDAPPassThroughAuthenticationPolicyCfg cfg,
      final List<Message> unacceptableReasons)
  {
    String password = null;

    if (cfg.getMappedSearchBindPasswordProperty() != null)
    {
      String propertyName = cfg.getMappedSearchBindPasswordProperty();
      password = System.getProperty(propertyName);
      if (password == null)
      {
        unacceptableReasons.add(ERR_LDAP_PTA_PWD_PROPERTY_NOT_SET.get(
            String.valueOf(cfg.dn()), String.valueOf(propertyName)));
      }
    }
    else if (cfg.getMappedSearchBindPasswordEnvironmentVariable() != null)
    {
      String envVarName = cfg.getMappedSearchBindPasswordEnvironmentVariable();
      password = System.getenv(envVarName);
      if (password == null)
      {
        unacceptableReasons.add(ERR_LDAP_PTA_PWD_ENVAR_NOT_SET.get(
            String.valueOf(cfg.dn()), String.valueOf(envVarName)));
      }
    }
    else if (cfg.getMappedSearchBindPasswordFile() != null)
    {
      String fileName = cfg.getMappedSearchBindPasswordFile();
      File passwordFile = getFileForPath(fileName);
      if (!passwordFile.exists())
      {
        unacceptableReasons.add(ERR_LDAP_PTA_PWD_NO_SUCH_FILE.get(
            String.valueOf(cfg.dn()), String.valueOf(fileName)));
      }
      else
      {
        BufferedReader br = null;
        try
        {
          br = new BufferedReader(new FileReader(passwordFile));
          password = br.readLine();
          if (password == null)
          {
            unacceptableReasons.add(ERR_LDAP_PTA_PWD_FILE_EMPTY.get(
                String.valueOf(cfg.dn()), String.valueOf(fileName)));
          }
        }
        catch (IOException e)
        {
          unacceptableReasons.add(ERR_LDAP_PTA_PWD_FILE_CANNOT_READ.get(
              String.valueOf(cfg.dn()), String.valueOf(fileName),
              getExceptionMessage(e)));
        }
        finally
        {
          try
          {
            br.close();
          }
          catch (Exception e)
          {
            // Ignored.
          }
        }
      }
    }
    else if (cfg.getMappedSearchBindPassword() != null)
    {
      password = cfg.getMappedSearchBindPassword();
    }
    else
    {
      // Password wasn't defined anywhere.
      unacceptableReasons
          .add(ERR_LDAP_PTA_NO_PWD.get(String.valueOf(cfg.dn())));
    }

    return password;
  }



  private static boolean isServerAddressValid(
      final LDAPPassThroughAuthenticationPolicyCfg configuration,
      final List<Message> unacceptableReasons, final String hostPort)
  {
    final int colonIndex = hostPort.lastIndexOf(":");
    final int port = Integer.parseInt(hostPort.substring(colonIndex + 1));
    if (port < 1 || port > 65535)
    {
      if (unacceptableReasons != null)
      {
        final Message msg = ERR_LDAP_PTA_INVALID_PORT_NUMBER.get(
            String.valueOf(configuration.dn()), hostPort);
        unacceptableReasons.add(msg);
      }
      return false;
    }
    return true;
  }



  private static String mappedAttributesAsString(
      final Collection<AttributeType> attributes)
  {
    switch (attributes.size())
    {
    case 0:
      return "";
    case 1:
      return attributes.iterator().next().getNameOrOID();
    default:
      final StringBuilder builder = new StringBuilder();
      final Iterator<AttributeType> i = attributes.iterator();
      builder.append(i.next().getNameOrOID());
      while (i.hasNext())
      {
        builder.append(", ");
        builder.append(i.next().getNameOrOID());
      }
      return builder.toString();
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
  LDAPPassThroughAuthenticationPolicyFactory(final Provider provider)
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
      final LDAPPassThroughAuthenticationPolicyCfg cfg,
      final List<Message> unacceptableReasons)
  {
    // Check that the port numbers are valid. We won't actually try and connect
    // to the server since they may not be available (hence we have fail-over
    // capabilities).
    boolean configurationIsAcceptable = true;

    for (final String hostPort : cfg.getPrimaryRemoteLDAPServer())
    {
      configurationIsAcceptable &= isServerAddressValid(cfg,
          unacceptableReasons, hostPort);
    }

    for (final String hostPort : cfg.getSecondaryRemoteLDAPServer())
    {
      configurationIsAcceptable &= isServerAddressValid(cfg,
          unacceptableReasons, hostPort);
    }

    // Ensure that the search bind password is defined somewhere.
    if (cfg.getMappingPolicy() == MappingPolicy.MAPPED_SEARCH
        && cfg.getMappedSearchBindDN() != null
        && !cfg.getMappedSearchBindDN().isNullDN())
    {
      if (getMappedSearchBindPassword(cfg, unacceptableReasons) == null)
      {
        configurationIsAcceptable = false;
      }
    }

    return configurationIsAcceptable;
  }
}
