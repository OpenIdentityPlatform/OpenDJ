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



import static org.opends.server.extensions.LDAPPassThroughAuthenticationPolicyFactory.isServiceError;
import static org.opends.server.protocols.ldap.LDAPConstants.OID_NOTICE_OF_DISCONNECTION;
import static org.testng.Assert.*;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;

import org.opends.messages.Message;
import org.opends.server.TestCaseUtils;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.meta.LDAPPassThroughAuthenticationPolicyCfgDefn.MappingPolicy;
import org.opends.server.admin.std.server.AuthenticationPolicyCfg;
import org.opends.server.admin.std.server.LDAPPassThroughAuthenticationPolicyCfg;
import org.opends.server.api.AuthenticationPolicy;
import org.opends.server.api.AuthenticationPolicyState;
import org.opends.server.core.DirectoryServer;
import org.opends.server.extensions.LDAPPassThroughAuthenticationPolicyFactory.Connection;
import org.opends.server.extensions.LDAPPassThroughAuthenticationPolicyFactory.ConnectionFactory;
import org.opends.server.extensions.LDAPPassThroughAuthenticationPolicyFactory.ConnectionPool;
import org.opends.server.extensions.LDAPPassThroughAuthenticationPolicyFactory.LDAPConnectionFactory;
import org.opends.server.protocols.asn1.ASN1;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.protocols.ldap.*;
import org.opends.server.schema.DirectoryStringSyntax;
import org.opends.server.schema.GeneralizedTimeSyntax;
import org.opends.server.schema.UserPasswordSyntax;
import org.opends.server.tools.LDAPReader;
import org.opends.server.tools.LDAPWriter;
import org.opends.server.types.*;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.TimeThread;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;



/**
 * Test LDAP authentication mappingPolicy implementation.
 */
public class LDAPPassThroughAuthenticationPolicyTestCase extends
    ExtensionsTestCase
{

  static class CloseEvent extends Event<Void>
  {
    private final GetConnectionEvent getConnectionEvent;



    CloseEvent(final GetConnectionEvent getConnectionEvent)
    {
      this.getConnectionEvent = getConnectionEvent;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    boolean matchesEvent(final Event<?> event)
    {
      if (event instanceof CloseEvent)
      {
        final CloseEvent closeEvent = (CloseEvent) event;
        return getConnectionEvent.matchesEvent(closeEvent.getConnectionEvent);
      }
      else
      {
        return false;
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    StringBuilder toString(final StringBuilder builder)
    {
      builder.append("CloseEvent(");
      builder.append(getConnectionEvent);
      builder.append(')');
      return builder;
    }

  }



  static abstract class Event<T>
  {

    @Override
    public final boolean equals(final Object obj)
    {
      if (obj instanceof Event<?>)
      {
        return matchesEvent((Event<?>) obj);
      }
      else
      {
        return false;
      }
    }



    @Override
    public final String toString()
    {
      final StringBuilder builder = new StringBuilder();
      return toString(builder).toString();
    }



    T getResult()
    {
      return null;
    }



    abstract boolean matchesEvent(Event<?> event);



    abstract StringBuilder toString(StringBuilder builder);
  }



  static class GetConnectionEvent extends Event<DirectoryException>
  {
    private final GetLDAPConnectionFactoryEvent fevent;
    private final ResultCode resultCode;



    GetConnectionEvent(final GetLDAPConnectionFactoryEvent fevent)
    {
      this(fevent, ResultCode.SUCCESS);
    }



    GetConnectionEvent(final GetLDAPConnectionFactoryEvent fevent,
        final ResultCode resultCode)
    {
      this.fevent = fevent;
      this.resultCode = resultCode;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    DirectoryException getResult()
    {
      if (resultCode != ResultCode.SUCCESS)
      {
        return new DirectoryException(resultCode,
            resultCode.getResultCodeName());
      }
      else
      {
        return null;
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    boolean matchesEvent(final Event<?> event)
    {
      if (event instanceof GetConnectionEvent)
      {
        final GetConnectionEvent getConnectionEvent = (GetConnectionEvent) event;
        return fevent.matchesEvent(getConnectionEvent.fevent);
      }
      else
      {
        return false;
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    StringBuilder toString(final StringBuilder builder)
    {
      builder.append("GetConnectionEvent(");
      builder.append(fevent);
      builder.append(')');
      return builder;
    }

  }



  static class GetLDAPConnectionFactoryEvent extends Event<Void>
  {
    private final String hostPort;
    private final LDAPPassThroughAuthenticationPolicyCfg options;



    GetLDAPConnectionFactoryEvent(final String hostPort,
        final LDAPPassThroughAuthenticationPolicyCfg options)
    {
      this.hostPort = hostPort;
      this.options = options;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    boolean matchesEvent(final Event<?> event)
    {
      if (event instanceof GetLDAPConnectionFactoryEvent)
      {
        final GetLDAPConnectionFactoryEvent providerEvent = (GetLDAPConnectionFactoryEvent) event;

        return hostPort.equals(providerEvent.hostPort)
            && options == providerEvent.options;
      }
      else
      {
        return false;
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    StringBuilder toString(final StringBuilder builder)
    {
      builder.append("GetLDAPConnectionFactoryEvent(");
      builder.append(hostPort);
      builder.append(')');
      return builder;
    }

  }



  static final class MockConnection implements
      LDAPPassThroughAuthenticationPolicyFactory.Connection
  {

    private final GetConnectionEvent getConnectionEvent;
    private final MockProvider mockProvider;



    MockConnection(final MockProvider mockProvider,
        final GetConnectionEvent getConnectionEvent)
    {
      this.mockProvider = mockProvider;
      this.getConnectionEvent = getConnectionEvent;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void close()
    {
      final CloseEvent event = new CloseEvent(getConnectionEvent);
      mockProvider.assertExpectedEventWasReceived(event);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public ByteString search(final DN baseDN, final SearchScope scope,
        final SearchFilter filter) throws DirectoryException
    {
      final SearchEvent event = new SearchEvent(getConnectionEvent,
          baseDN.toString(), scope, filter.toString());
      final Object result = mockProvider.assertExpectedEventWasReceived(event);
      if (result instanceof String)
      {
        return ByteString.valueOf((String) result);
      }
      else
      {
        throw (DirectoryException) result;
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void simpleBind(final ByteString username, final ByteString password)
        throws DirectoryException
    {
      final SimpleBindEvent event = new SimpleBindEvent(getConnectionEvent,
          username.toString(), password.toString());
      final DirectoryException e = mockProvider
          .assertExpectedEventWasReceived(event);
      if (e != null)
      {
        throw e;
      }
    }

  }



  static final class MockFactory implements
      LDAPPassThroughAuthenticationPolicyFactory.ConnectionFactory
  {
    private final GetLDAPConnectionFactoryEvent getLDAPConnectionFactoryEvent;
    private final MockProvider mockProvider;



    MockFactory(final MockProvider mockProvider,
        final GetLDAPConnectionFactoryEvent providerEvent)
    {
      this.mockProvider = mockProvider;
      this.getLDAPConnectionFactoryEvent = providerEvent;
    }



    /**
     * {@inheritDoc}
     */
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
      final GetConnectionEvent event = new GetConnectionEvent(
          getLDAPConnectionFactoryEvent);

      final DirectoryException e = mockProvider
          .assertExpectedEventWasReceived(event);
      if (e != null)
      {
        throw e;
      }
      else
      {
        return new MockConnection(mockProvider, event);
      }
    }

  }



  final class MockPolicyCfg implements LDAPPassThroughAuthenticationPolicyCfg
  {
    private final SortedSet<DN> baseDNs = new TreeSet<DN>();
    private final SortedSet<AttributeType> mappedAttributes = new TreeSet<AttributeType>();
    private MappingPolicy mappingPolicy = MappingPolicy.UNMAPPED;
    private final SortedSet<String> primaryServers = new TreeSet<String>();
    private final SortedSet<String> secondaryServers = new TreeSet<String>();
    private int timeoutMS = 0; // unlimited
    private DN mappedSearchBindDN = searchBindDN;
    private String mappedSearchBindPassword = "searchPassword";
    private String mappedSearchBindPasswordEnvVar = null;
    private String mappedSearchBindPasswordFile = null;
    private String mappedSearchBindPasswordProperty = null;
    private boolean usePasswordCaching = false;




    @Override
    public void addChangeListener(
        final ConfigurationChangeListener<AuthenticationPolicyCfg> listener)
    {
      // Do nothing.
    }



    @Override
    public void addLDAPPassThroughChangeListener(
        final ConfigurationChangeListener<LDAPPassThroughAuthenticationPolicyCfg> listener)
    {
      // Do nothing.
    }



    @Override
    public Class<? extends LDAPPassThroughAuthenticationPolicyCfg> configurationClass()
    {
      return LDAPPassThroughAuthenticationPolicyCfg.class;
    }



    @Override
    public DN dn()
    {
      return policyDN;
    }



    @Override
    public long getConnectionTimeout()
    {
      return timeoutMS;
    }



    @Override
    public String getJavaClass()
    {
      return LDAPPassThroughAuthenticationPolicyFactory.class.getName();
    }



    @Override
    public SortedSet<AttributeType> getMappedAttribute()
    {
      return mappedAttributes;
    }



    @Override
    public SortedSet<DN> getMappedSearchBaseDN()
    {
      return baseDNs;
    }



    @Override
    public DN getMappedSearchBindDN()
    {
      return mappedSearchBindDN;
    }



    @Override
    public String getMappedSearchBindPassword()
    {
      return mappedSearchBindPassword;
    }



    @Override
    public MappingPolicy getMappingPolicy()
    {
      return mappingPolicy;
    }



    @Override
    public SortedSet<String> getPrimaryRemoteLDAPServer()
    {
      return primaryServers;
    }



    @Override
    public SortedSet<String> getSecondaryRemoteLDAPServer()
    {
      return secondaryServers;
    }



    @Override
    public SortedSet<String> getSSLCipherSuite()
    {
      return new TreeSet<String>();
    }



    @Override
    public SortedSet<String> getSSLProtocol()
    {
      return new TreeSet<String>();
    }



    @Override
    public String getTrustManagerProvider()
    {
      return "ignored";
    }



    @Override
    public DN getTrustManagerProviderDN()
    {
      return trustManagerDN;
    }



    @Override
    public boolean isUseSSL()
    {
      return false;
    }



    @Override
    public boolean isUseTCPKeepAlive()
    {
      return false;
    }



    @Override
    public boolean isUseTCPNoDelay()
    {
      return false;
    }



    @Override
    public void removeChangeListener(
        final ConfigurationChangeListener<AuthenticationPolicyCfg> listener)
    {
      // Do nothing.
    }



    @Override
    public void removeLDAPPassThroughChangeListener(
        final ConfigurationChangeListener<LDAPPassThroughAuthenticationPolicyCfg> listener)
    {
      // Do nothing.
    }



    MockPolicyCfg withBaseDN(final String baseDN)
    {
      try
      {
        baseDNs.add(DN.decode(baseDN));
      }
      catch (final DirectoryException e)
      {
        throw new RuntimeException(e);
      }
      return this;
    }



    MockPolicyCfg withConnectionTimeout(final int timeoutMS)
    {
      this.timeoutMS = timeoutMS;
      return this;
    }



    MockPolicyCfg withMappedAttribute(final String atype)
    {
      mappedAttributes.add(DirectoryServer.getAttributeType(
          StaticUtils.toLowerCase(atype), true));
      return this;
    }



    MockPolicyCfg withMappingPolicy(final MappingPolicy policy)
    {
      this.mappingPolicy = policy;
      return this;
    }



    MockPolicyCfg withPrimaryServer(final String hostPort)
    {
      primaryServers.add(hostPort);
      return this;
    }



    MockPolicyCfg withSecondaryServer(final String hostPort)
    {
      secondaryServers.add(hostPort);
      return this;
    }



    MockPolicyCfg withMappedSearchBindDN(final DN value)
    {
      this.mappedSearchBindDN = value;
      return this;
    }



    MockPolicyCfg withMappedSearchBindPassword(final String value)
    {
      this.mappedSearchBindPassword = value;
      return this;
    }



    MockPolicyCfg withMappedSearchBindPasswordEnvironmentVariable(final String value)
    {
      this.mappedSearchBindPasswordEnvVar = value;
      return this;
    }



    MockPolicyCfg withMappedSearchBindPasswordFile(final String value)
    {
      this.mappedSearchBindPasswordFile = value;
      return this;
    }



    MockPolicyCfg withMappedSearchBindPasswordProperty(final String value)
    {
      this.mappedSearchBindPasswordProperty = value;
      return this;
    }



    MockPolicyCfg withUsePasswordCaching(final boolean usePasswordCaching)
    {
      this.usePasswordCaching = usePasswordCaching;
      return this;
    }



    /**
     * {@inheritDoc}
     */
    public String getMappedSearchBindPasswordEnvironmentVariable()
    {
      return mappedSearchBindPasswordEnvVar;
    }



    /**
     * {@inheritDoc}
     */
    public String getMappedSearchBindPasswordFile()
    {
      return mappedSearchBindPasswordFile;
    }



    /**
     * {@inheritDoc}
     */
    public String getMappedSearchBindPasswordProperty()
    {
      return mappedSearchBindPasswordProperty;
    }



    /**
     * {@inheritDoc}
     */
    public long getCachedPasswordTTL()
    {
      return 86400;
    }



    /**
     * {@inheritDoc}
     */
    public String getCachedPasswordStorageScheme()
    {
      return "Salted SHA-1";
    }



    /**
     * {@inheritDoc}
     */
    public DN getCachedPasswordStorageSchemeDN()
    {
      try
      {
        return DN.decode("cn=Salted SHA-1,cn=Password Storage Schemes,cn=config");
      }
      catch (DirectoryException e)
      {
        throw new RuntimeException(e);
      }
    }



    /**
     * {@inheritDoc}
     */
    public boolean isUsePasswordCaching()
    {
      return usePasswordCaching;
    }
  }



  static final class MockProvider implements
      LDAPPassThroughAuthenticationPolicyFactory.Provider
  {

    private final class MockScheduledFuture implements ScheduledFuture<Void>
    {
      private final Runnable runnable;



      MockScheduledFuture(final Runnable runnable)
      {
        this.runnable = runnable;
      }



      public boolean cancel(final boolean mayInterruptIfRunning)
      {
        monitorRunnables.remove(this);
        return true;
      }



      public int compareTo(final Delayed o)
      {
        return 0;
      }



      public Void get() throws InterruptedException, ExecutionException
      {
        return null;
      }



      public Void get(final long timeout, final TimeUnit unit)
          throws InterruptedException, ExecutionException, TimeoutException
      {
        return null;
      }



      public long getDelay(final TimeUnit unit)
      {
        return 0;
      }



      public boolean isCancelled()
      {
        return false;
      }



      public boolean isDone()
      {
        return false;
      }



      Runnable getRunnable()
      {
        return runnable;
      }
    }



    private final Queue<Event<?>> expectedEvents = new LinkedList<Event<?>>();
    private final List<MockScheduledFuture> monitorRunnables = new LinkedList<MockScheduledFuture>();
    private String currentTime = TimeThread.getGMTTime();

    // All methods unused excepted scheduleWithFixedDelay.
    private final ScheduledExecutorService mockScheduler = new ScheduledExecutorService()
    {

      @Override
      public boolean awaitTermination(final long timeout, final TimeUnit unit)
          throws InterruptedException
      {
        throw new UnsupportedOperationException();
      }



      @Override
      public void execute(final Runnable command)
      {
        throw new UnsupportedOperationException();
      }



      @Override
      public <T> List<Future<T>> invokeAll(
          final Collection<? extends Callable<T>> tasks)
          throws InterruptedException
      {
        throw new UnsupportedOperationException();
      }



      @Override
      public <T> List<Future<T>> invokeAll(
          final Collection<? extends Callable<T>> tasks, final long timeout,
          final TimeUnit unit) throws InterruptedException
      {
        throw new UnsupportedOperationException();
      }



      @Override
      public <T> T invokeAny(final Collection<? extends Callable<T>> tasks)
          throws InterruptedException, ExecutionException
      {
        throw new UnsupportedOperationException();
      }



      @Override
      public <T> T invokeAny(final Collection<? extends Callable<T>> tasks,
          final long timeout, final TimeUnit unit) throws InterruptedException,
          ExecutionException, TimeoutException
      {
        throw new UnsupportedOperationException();
      }



      @Override
      public boolean isShutdown()
      {
        return false;
      }



      @Override
      public boolean isTerminated()
      {
        return false;
      }



      @Override
      public <V> ScheduledFuture<V> schedule(final Callable<V> callable,
          final long delay, final TimeUnit unit)
      {
        throw new UnsupportedOperationException();
      }



      @Override
      public ScheduledFuture<?> schedule(final Runnable command,
          final long delay, final TimeUnit unit)
      {
        throw new UnsupportedOperationException();
      }



      @Override
      public ScheduledFuture<?> scheduleAtFixedRate(final Runnable command,
          final long initialDelay, final long period, final TimeUnit unit)
      {
        throw new UnsupportedOperationException();
      }



      @Override
      public ScheduledFuture<?> scheduleWithFixedDelay(final Runnable command,
          final long initialDelay, final long delay, final TimeUnit unit)
      {
        final MockScheduledFuture future = new MockScheduledFuture(command);
        monitorRunnables.add(future);
        return future;
      }



      @Override
      public void shutdown()
      {
        throw new UnsupportedOperationException();
      }



      @Override
      public List<Runnable> shutdownNow()
      {
        throw new UnsupportedOperationException();
      }



      @Override
      public <T> Future<T> submit(final Callable<T> task)
      {
        throw new UnsupportedOperationException();
      }



      @Override
      public Future<?> submit(final Runnable task)
      {
        throw new UnsupportedOperationException();
      }



      @Override
      public <T> Future<T> submit(final Runnable task, final T result)
      {
        throw new UnsupportedOperationException();
      }
    };



    /**
     * {@inheritDoc}
     */
    @Override
    public ConnectionFactory getLDAPConnectionFactory(final String host,
        final int port, final LDAPPassThroughAuthenticationPolicyCfg options)
    {
      final GetLDAPConnectionFactoryEvent event = new GetLDAPConnectionFactoryEvent(
          host + ":" + port, options);
      assertExpectedEventWasReceived(event);
      return new MockFactory(this, event);
    }



    /**
     * {@inheritDoc}
     */
    public ScheduledExecutorService getScheduledExecutorService()
    {
      return mockScheduler;
    }



    /**
     * {@inheritDoc}
     */
    public String getCurrentTime()
    {
      return currentTime;
    }



    /**
     * {@inheritDoc}
     */
    public long getCurrentTimeMS()
    {
      try
      {
        return GeneralizedTimeSyntax.decodeGeneralizedTimeValue(ByteString
            .valueOf(currentTime));
      }
      catch (DirectoryException e)
      {
        throw new RuntimeException(e);
      }
    }



    void assertAllExpectedEventsReceived()
    {
      assertTrue(expectedEvents.isEmpty());
    }



    @SuppressWarnings("unchecked")
    <T> T assertExpectedEventWasReceived(final Event<T> actualEvent)
    {
      final Event<?> expectedEvent = expectedEvents.poll();
      if (expectedEvent == null)
      {
        fail("Unexpected event: " + actualEvent);
      }
      else
      {
        assertEquals(actualEvent, expectedEvent);
      }
      return ((Event<T>) expectedEvent).getResult();
    }



    MockProvider expectEvent(final Event<?> expectedEvent)
    {
      expectedEvents.add(expectedEvent);
      return this;
    }



    MockProvider withCurrentTime(final String currentTime)
    {
      this.currentTime = currentTime;
      return this;
    }



    void runMonitorTasks()
    {
      for (final MockScheduledFuture task : monitorRunnables)
      {
        task.getRunnable().run();
      }
    }

  }



  final class MockServer
  {
    // Waits for an incoming client connection.
    class AcceptAction extends Action
    {
      void run() throws Exception
      {
        accept();
      }
    }



    abstract class Action
    {
      abstract void run() throws Exception;
    }



    // Blocks the server until it is released.
    class BlockAction extends Action
    {
      private final CountDownLatch latch = new CountDownLatch(1);



      void release()
      {
        latch.countDown();
      }



      void run() throws Exception
      {
        latch.await();
      }
    }



    // Close the client socket.
    class CloseAction extends Action
    {
      @Override
      void run() throws Exception
      {
        getSocket().close();
      }
    }



    // Read the next message and check it matches the expected message.
    class ReceiveAction extends Action
    {
      private final int messageID;
      private final ProtocolOp expectedOp;



      ReceiveAction(final int messageID, final ProtocolOp expectedOp)
      {
        this.messageID = messageID;
        this.expectedOp = expectedOp;
      }



      @Override
      void run() throws Exception
      {
        // Read next message.
        final LDAPReader reader = new LDAPReader(getSocket());
        final LDAPMessage message = reader.readMessage();

        // Check message ID matches.
        assertEquals(message.getMessageID(), messageID);

        // Check protocol op matches.
        final ProtocolOp actualOp = message.getProtocolOp();
        final ByteStringBuilder b1 = new ByteStringBuilder();
        final ByteStringBuilder b2 = new ByteStringBuilder();
        final ASN1Writer w1 = ASN1.getWriter(b1);
        final ASN1Writer w2 = ASN1.getWriter(b2);
        expectedOp.write(w1);
        actualOp.write(w2);
        assertEquals(b1, b2);
      }
    }



    // Sends a message.
    class SendAction extends Action
    {
      private final int messageID;
      private final ProtocolOp op;



      SendAction(final int messageID, final ProtocolOp op)
      {
        this.messageID = messageID;
        this.op = op;
      }



      @Override
      void run() throws Exception
      {
        final LDAPWriter writer = new LDAPWriter(getSocket());
        final LDAPMessage message = new LDAPMessage(messageID, op);
        writer.writeMessage(message);
      }
    }



    private final ServerSocket serverSocket;
    private final List<Action> actions = new LinkedList<Action>();
    private Socket socket = null;
    private volatile Exception e = null;
    private Thread serverThread = null;
    private final CountDownLatch stopLatch = new CountDownLatch(1);
    private final Queue<BlockAction> blockers = new LinkedList<BlockAction>();



    MockServer(final ServerSocket serverSocket)
    {
      this.serverSocket = serverSocket;
    }



    void assertNoExceptions() throws Exception
    {
      if (e != null)
      {
        throw e;
      }
    }



    int getPort()
    {
      return serverSocket.getLocalPort();
    }



    MockServer start()
    {
      serverThread = new Thread(new Runnable()
      {
        /**
         * {@inheritDoc}
         */
        public void run()
        {
          for (final Action action : actions)
          {
            try
            {
              action.run();
            }
            catch (final Exception e)
            {
              MockServer.this.e = e;
              break;
            }
          }

          if (socket != null)
          {
            try
            {
              socket.close();
            }
            catch (final IOException ignored)
            {
              // Ignore
            }
          }
          if (serverSocket != null)
          {
            try
            {
              serverSocket.close();
            }
            catch (final IOException ignored)
            {
              // Ignore
            }
          }

          // Release test thread.
          stopLatch.countDown();
        }
      }, "MockServer on port " + serverSocket.getLocalPort());
      serverThread.start();
      return this;
    }



    void stop() throws Exception
    {
      stopLatch.await(10, TimeUnit.SECONDS);
      if (serverThread != null)
      {
        serverThread.interrupt();
      }
      stopLatch.await();
      assertNoExceptions();
    }



    MockServer thenAccept()
    {
      actions.add(new AcceptAction());
      return this;
    }



    MockServer thenBlock()
    {
      final BlockAction action = new BlockAction();
      actions.add(action);
      blockers.add(action);
      return this;
    }



    MockServer thenClose()
    {
      actions.add(new CloseAction());
      return this;
    }



    MockServer thenReceive(final int messageID, final ProtocolOp op)
    {
      actions.add(new ReceiveAction(messageID, op));
      return this;
    }



    MockServer thenSend(final int messageID, final ProtocolOp op)
    {
      actions.add(new SendAction(messageID, op));
      return this;
    }



    void unblock() throws Exception
    {
      final BlockAction action = blockers.poll();
      assertNotNull(action);
      action.release();
    }



    private Socket accept() throws IOException
    {
      socket = serverSocket.accept();
      return socket;
    }



    private Socket getSocket()
    {
      return socket;
    }

  }



  static class SearchEvent extends Event<Object>
  {
    private final String baseDN;
    private final GetConnectionEvent cevent;
    private final String filter;
    private final ResultCode resultCode;
    private final SearchScope scope;
    private final String username;



    SearchEvent(final GetConnectionEvent cevent, final String baseDN,
        final SearchScope scope, final String filter)
    {
      this(cevent, baseDN, scope, filter, null, ResultCode.SUCCESS);
    }



    SearchEvent(final GetConnectionEvent cevent, final String baseDN,
        final SearchScope scope, final String filter,
        final ResultCode resultCode)
    {
      this(cevent, baseDN, scope, filter, null, resultCode);
    }



    SearchEvent(final GetConnectionEvent cevent, final String baseDN,
        final SearchScope scope, final String filter, final String username)
    {
      this(cevent, baseDN, scope, filter, username, ResultCode.SUCCESS);
    }



    private SearchEvent(final GetConnectionEvent cevent, final String baseDN,
        final SearchScope scope, final String filter, final String username,
        final ResultCode resultCode)
    {
      this.cevent = cevent;
      this.baseDN = baseDN;
      this.scope = scope;
      this.filter = filter;
      this.username = username;
      this.resultCode = resultCode;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    Object getResult()
    {
      return resultCode == ResultCode.SUCCESS ? username
          : new DirectoryException(resultCode, resultCode.getResultCodeName());
    }



    /**
     * {@inheritDoc}
     */
    @Override
    boolean matchesEvent(final Event<?> event)
    {
      if (event instanceof SearchEvent)
      {
        final SearchEvent searchEvent = (SearchEvent) event;
        return cevent.matchesEvent(searchEvent.cevent)
            && baseDN.equals(searchEvent.baseDN)
            && scope.equals(searchEvent.scope)
            && filter.equals(searchEvent.filter);
      }
      else
      {
        return false;
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    StringBuilder toString(final StringBuilder builder)
    {
      builder.append("SearchEvent(");
      builder.append(cevent);
      builder.append(", ");
      builder.append(baseDN);
      builder.append(", ");
      builder.append(scope);
      builder.append(", ");
      builder.append(filter);
      builder.append(')');
      return builder;
    }

  }



  static class SimpleBindEvent extends Event<DirectoryException>
  {
    private final GetConnectionEvent cevent;
    private final String password;
    private final ResultCode resultCode;
    private final String username;



    SimpleBindEvent(final GetConnectionEvent cevent, final String username,
        final String password)
    {
      this(cevent, username, password, ResultCode.SUCCESS);
    }



    SimpleBindEvent(final GetConnectionEvent cevent, final String username,
        final String password, final ResultCode resultCode)
    {
      this.cevent = cevent;
      this.username = username;
      this.password = password;
      this.resultCode = resultCode;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    DirectoryException getResult()
    {
      if (resultCode != ResultCode.SUCCESS)
      {
        return new DirectoryException(resultCode,
            resultCode.getResultCodeName());
      }
      else
      {
        return null;
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    boolean matchesEvent(final Event<?> event)
    {
      if (event instanceof SimpleBindEvent)
      {
        final SimpleBindEvent simpleBindEvent = (SimpleBindEvent) event;
        return cevent.matchesEvent(simpleBindEvent.cevent)
            && username.equals(simpleBindEvent.username)
            && password.equals(simpleBindEvent.password);
      }
      else
      {
        return false;
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    StringBuilder toString(final StringBuilder builder)
    {
      builder.append("SimpleBindEvent(");
      builder.append(cevent);
      builder.append(", ");
      builder.append(username);
      builder.append(", ");
      builder.append(password);
      builder.append(')');
      return builder;
    }

  }



  private final String phost1 = "phost1:11";
  private final String phost2 = "phost2:22";
  private final String phost3 = "phost3:33";
  private final String shost1 = "shost1:11";
  private DN policyDN;
  private final String policyDNString = "cn=test policy,o=test";
  private DN searchBindDN;
  private final String searchBindDNString = "cn=search bind dn";
  private DN trustManagerDN;
  private final String trustManagerDNString = "cn=ignored";
  private final String adDNString = "uid=aduser,o=ad";
  private final String opendjDNString = "cn=test user,o=opendj";
  private Entry userEntry;
  private final String userPassword = "password";



  /**
   * Ensures that the Directory Server is running and creates a test backend
   * containing a single test user.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @BeforeClass()
  public void beforeClass() throws Exception
  {
    TestCaseUtils.startServer();

    policyDN = DN.decode(policyDNString);
    trustManagerDN = DN.decode(trustManagerDNString);
    searchBindDN = DN.decode(searchBindDNString);
    userEntry = TestCaseUtils.makeEntry(
        /* @formatter:off */
        "dn: " + opendjDNString,
        "objectClass: top",
        "objectClass: person",
        "sn: user",
        "cn: test user",
        "aduser: " + adDNString,
        "uid: aduser"
        /* @formatter:on */
    );
  }



  /**
   * Tests that failures during the search are handled properly.
   * <p>
   * Non-fatal errors (e.g. entry not found, too many entries returned) should
   * not cause the search connection to be closed.
   *
   * @param searchResultCode
   *          The search result code.
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(enabled = true, dataProvider = "testConnectionFailureDuringSearchData")
  public void testConnectionFailureDuringSearch(
      final ResultCode searchResultCode) throws Exception
  {
    // Mock configuration.
    final LDAPPassThroughAuthenticationPolicyCfg cfg = mockCfg()
        .withPrimaryServer(phost1)
        .withMappingPolicy(MappingPolicy.MAPPED_SEARCH)
        .withMappedAttribute("uid").withBaseDN("o=ad");

    // Create the provider and its list of expected events.
    final GetLDAPConnectionFactoryEvent fe = new GetLDAPConnectionFactoryEvent(
        phost1, cfg);
    final GetConnectionEvent ceSearch = new GetConnectionEvent(fe);

    final MockProvider provider = new MockProvider()
        .expectEvent(fe)
        .expectEvent(ceSearch)
        .expectEvent(
            new SimpleBindEvent(ceSearch, searchBindDNString, "searchPassword",
                ResultCode.SUCCESS))
        .expectEvent(
            new SearchEvent(ceSearch, "o=ad", SearchScope.WHOLE_SUBTREE,
                "(uid=aduser)", searchResultCode));
    if (isServiceError(searchResultCode))
    {
      // The connection will fail and be closed immediately, and the pool will
      // retry on new connection.
      provider.expectEvent(new CloseEvent(ceSearch));
      provider.expectEvent(new GetConnectionEvent(fe, searchResultCode));
    }

    // Obtain policy and state.
    final LDAPPassThroughAuthenticationPolicyFactory factory = new LDAPPassThroughAuthenticationPolicyFactory(
        provider);
    assertTrue(factory.isConfigurationAcceptable(cfg, null));
    final AuthenticationPolicy policy = factory.createAuthenticationPolicy(cfg);
    final AuthenticationPolicyState state = policy
        .createAuthenticationPolicyState(userEntry);
    assertEquals(state.getAuthenticationPolicy(), policy);

    // Perform authentication.
    try
    {
      state.passwordMatches(ByteString.valueOf(userPassword));
      fail("password match unexpectedly succeeded");
    }
    catch (final DirectoryException e)
    {
      // No valid connections available so this should always fail with
      // INVALID_CREDENTIALS.
      assertEquals(e.getResultCode(), ResultCode.INVALID_CREDENTIALS,
          e.getMessage());
    }

    // There should be no more pending events.
    provider.assertAllExpectedEventsReceived();
    state.finalizeStateAfterBind();

    // Cached connections should be closed when the policy is finalized.
    if (!isServiceError(searchResultCode))
    {
      provider.expectEvent(new CloseEvent(ceSearch));
    }

    // Tear down and check final state.
    policy.finalizeAuthenticationPolicy();
    provider.assertAllExpectedEventsReceived();
  }



  /**
   * Tests that failures to authenticate a search connection are handled
   * properly.
   * <p>
   * Any kind of failure occurring while trying to authenticate a search
   * connection should result in the connection being closed and periodically
   * retried.
   *
   * @param bindResultCode
   *          The bind result code.
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(enabled = true, dataProvider = "testConnectionFailureDuringSearchBindData")
  public void testConnectionFailureDuringSearchBind(
      final ResultCode bindResultCode) throws Exception
  {
    // Mock configuration.
    final LDAPPassThroughAuthenticationPolicyCfg cfg = mockCfg()
        .withPrimaryServer(phost1)
        .withMappingPolicy(MappingPolicy.MAPPED_SEARCH)
        .withMappedAttribute("uid").withBaseDN("o=ad");

    // Create the provider and its list of expected events.
    final GetLDAPConnectionFactoryEvent fe = new GetLDAPConnectionFactoryEvent(
        phost1, cfg);
    final GetConnectionEvent ceSearch = new GetConnectionEvent(fe);

    final MockProvider provider = new MockProvider()
        .expectEvent(fe)
        .expectEvent(ceSearch)
        .expectEvent(
            new SimpleBindEvent(ceSearch, searchBindDNString, "searchPassword",
                bindResultCode)).expectEvent(new CloseEvent(ceSearch));

    // Obtain policy and state.
    final LDAPPassThroughAuthenticationPolicyFactory factory = new LDAPPassThroughAuthenticationPolicyFactory(
        provider);
    assertTrue(factory.isConfigurationAcceptable(cfg, null));
    final AuthenticationPolicy policy = factory.createAuthenticationPolicy(cfg);
    final AuthenticationPolicyState state = policy
        .createAuthenticationPolicyState(userEntry);
    assertEquals(state.getAuthenticationPolicy(), policy);

    // Perform authentication.
    try
    {
      state.passwordMatches(ByteString.valueOf(userPassword));
      fail("password match unexpectedly succeeded");
    }
    catch (final DirectoryException e)
    {
      // No valid connections available so this should always fail with
      // INVALID_CREDENTIALS.
      assertEquals(e.getResultCode(), ResultCode.INVALID_CREDENTIALS,
          e.getMessage());
    }

    // Tear down and check final state.
    provider.assertAllExpectedEventsReceived();
    state.finalizeStateAfterBind();
    policy.finalizeAuthenticationPolicy();
  }



  /**
   * Returns test data for {@link #testConnectionFailureDuringSearchBind}.
   *
   * @return Test data for {@link #testConnectionFailureDuringSearchBind}.
   */
  @DataProvider
  public Object[][] testConnectionFailureDuringSearchBindData()
  {
    // @formatter:off
    return new Object[][] {
        { ResultCode.INVALID_CREDENTIALS },
        { ResultCode.NO_SUCH_OBJECT },
        { ResultCode.UNAVAILABLE }
    };
    // @formatter:on
  }



  /**
   * Returns test data for {@link #testConnectionFailureDuringSearch}.
   *
   * @return Test data for {@link #testConnectionFailureDuringSearch}.
   */
  @DataProvider
  public Object[][] testConnectionFailureDuringSearchData()
  {
    // @formatter:off
    return new Object[][] {
        { ResultCode.NO_SUCH_OBJECT },
        { ResultCode.CLIENT_SIDE_NO_RESULTS_RETURNED },
        { ResultCode.CLIENT_SIDE_MORE_RESULTS_TO_RETURN },
        { ResultCode.UNAVAILABLE }
    };
    // @formatter:on
  }



  /**
   * Tests that failures to obtain a search connection are handled properly.
   *
   * @param connectResultCode
   *          The connection failure result code.
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(enabled = true, dataProvider = "testConnectionFailureDuringSearchGetConnectionData")
  public void testConnectionFailureDuringSearchGetConnection(
      final ResultCode connectResultCode) throws Exception
  {
    // Mock configuration.
    final LDAPPassThroughAuthenticationPolicyCfg cfg = mockCfg()
        .withPrimaryServer(phost1)
        .withMappingPolicy(MappingPolicy.MAPPED_SEARCH)
        .withMappedAttribute("uid").withBaseDN("o=ad");

    // Create the provider and its list of expected events.
    final GetLDAPConnectionFactoryEvent fe = new GetLDAPConnectionFactoryEvent(
        phost1, cfg);
    final GetConnectionEvent ceSearch = new GetConnectionEvent(fe,
        connectResultCode);

    final MockProvider provider = new MockProvider().expectEvent(fe)
        .expectEvent(ceSearch);

    // Obtain policy and state.
    final LDAPPassThroughAuthenticationPolicyFactory factory = new LDAPPassThroughAuthenticationPolicyFactory(
        provider);
    assertTrue(factory.isConfigurationAcceptable(cfg, null));
    final AuthenticationPolicy policy = factory.createAuthenticationPolicy(cfg);
    final AuthenticationPolicyState state = policy
        .createAuthenticationPolicyState(userEntry);
    assertEquals(state.getAuthenticationPolicy(), policy);

    // Perform authentication.
    try
    {
      state.passwordMatches(ByteString.valueOf(userPassword));
      fail("password match unexpectedly succeeded");
    }
    catch (final DirectoryException e)
    {
      // No valid connections available so this should always fail with
      // INVALID_CREDENTIALS.
      assertEquals(e.getResultCode(), ResultCode.INVALID_CREDENTIALS,
          e.getMessage());
    }

    // Tear down and check final state.
    provider.assertAllExpectedEventsReceived();
    state.finalizeStateAfterBind();
    policy.finalizeAuthenticationPolicy();
  }



  /**
   * Returns test data for
   * {@link #testConnectionFailureDuringSearchGetConnection}.
   *
   * @return Test data for
   *         {@link #testConnectionFailureDuringSearchGetConnection}.
   */
  @DataProvider
  public Object[][] testConnectionFailureDuringSearchGetConnectionData()
  {
    // @formatter:off
    return new Object[][] {
        { ResultCode.UNAVAILABLE }
    };
    // @formatter:on
  }



  /**
   * Tests fail-over between 2 primary servers then to the secondary data
   * center.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(enabled = true)
  public void testFailOverOnConnect() throws Exception
  {
    // Mock configuration.
    final LDAPPassThroughAuthenticationPolicyCfg cfg = mockCfg()
        .withPrimaryServer(phost1).withPrimaryServer(phost2)
        .withSecondaryServer(shost1)
        .withMappingPolicy(MappingPolicy.MAPPED_SEARCH)
        .withMappedAttribute("uid").withBaseDN("o=ad");

    // Create all the events.
    final MockProvider provider = new MockProvider();

    // First of all the connection factories are created.
    final GetLDAPConnectionFactoryEvent fe1 = new GetLDAPConnectionFactoryEvent(
        phost1, cfg);
    final GetLDAPConnectionFactoryEvent fe2 = new GetLDAPConnectionFactoryEvent(
        phost2, cfg);
    final GetLDAPConnectionFactoryEvent fe3 = new GetLDAPConnectionFactoryEvent(
        shost1, cfg);
    provider.expectEvent(fe1).expectEvent(fe2).expectEvent(fe3);

    // Get connection for phost1, then search, then bind.
    final GetConnectionEvent ceSearch1 = new GetConnectionEvent(fe1,
        ResultCode.CLIENT_SIDE_CONNECT_ERROR);
    final GetConnectionEvent ceSearch2 = new GetConnectionEvent(fe2,
        ResultCode.CLIENT_SIDE_CONNECT_ERROR);
    final GetConnectionEvent ceSearch3 = new GetConnectionEvent(fe3);

    final GetConnectionEvent ceBind1 = new GetConnectionEvent(fe1,
        ResultCode.CLIENT_SIDE_CONNECT_ERROR);
    final GetConnectionEvent ceBind2 = new GetConnectionEvent(fe2,
        ResultCode.CLIENT_SIDE_CONNECT_ERROR);
    final GetConnectionEvent ceBind3 = new GetConnectionEvent(fe3);

    provider
        .expectEvent(ceSearch1)
        .expectEvent(ceSearch2)
        .expectEvent(ceSearch3)
        .expectEvent(
            new SimpleBindEvent(ceSearch3, searchBindDNString,
                "searchPassword", ResultCode.SUCCESS))
        .expectEvent(
            new SearchEvent(ceSearch3, "o=ad", SearchScope.WHOLE_SUBTREE,
                "(uid=aduser)", adDNString))
        .expectEvent(ceBind1)
        .expectEvent(ceBind2)
        .expectEvent(ceBind3)
        .expectEvent(
            new SimpleBindEvent(ceBind3, adDNString, userPassword,
                ResultCode.SUCCESS));

    // Repeat again using cached connection to shost1: search, then bind.

    // phost1 and phost2 will have been marked as failed and won't be tried.
    provider.expectEvent(
        new SearchEvent(ceSearch3, "o=ad", SearchScope.WHOLE_SUBTREE,
            "(uid=aduser)", adDNString)).expectEvent(
        new SimpleBindEvent(ceBind3, adDNString, userPassword,
            ResultCode.SUCCESS));

    // Now simulate monitor thread run in which phost2 is determined to be
    // available again.
    final GetConnectionEvent ceSearch2ok = new GetConnectionEvent(fe2);
    final GetConnectionEvent ceBind2ok = new GetConnectionEvent(fe2);
    provider
        .expectEvent(ceSearch1)
        .expectEvent(ceSearch2ok)
        .expectEvent(
            new SimpleBindEvent(ceSearch2ok, searchBindDNString,
                "searchPassword", ResultCode.SUCCESS)).expectEvent(ceBind1)
        .expectEvent(ceBind2ok);

    provider.expectEvent(
        new SearchEvent(ceSearch2ok, "o=ad", SearchScope.WHOLE_SUBTREE,
            "(uid=aduser)", adDNString)).expectEvent(
        new SimpleBindEvent(ceBind2ok, adDNString, userPassword,
            ResultCode.SUCCESS));

    // Connections should be cached until the policy is finalized.

    // Obtain policy and state.
    final LDAPPassThroughAuthenticationPolicyFactory factory = new LDAPPassThroughAuthenticationPolicyFactory(
        provider);
    assertTrue(factory.isConfigurationAcceptable(cfg, null));
    final AuthenticationPolicy policy = factory.createAuthenticationPolicy(cfg);

    // Authenticate 3 times test above fail-over.
    for (int i = 0; i < 3; i++)
    {
      final AuthenticationPolicyState state = policy
          .createAuthenticationPolicyState(userEntry);
      assertEquals(state.getAuthenticationPolicy(), policy);

      // Run monitor which should try to connect to phost1&2 and determine that
      // phost2 is available again.
      if (i == 2)
      {
        provider.runMonitorTasks();
      }

      // Perform authentication.
      assertTrue(state.passwordMatches(ByteString.valueOf(userPassword)));

      state.finalizeStateAfterBind();
    }

    // Cached connections should be closed when the policy is finalized
    // (primaries first, then secondaries).
    provider.expectEvent(new CloseEvent(ceSearch2ok));
    provider.expectEvent(new CloseEvent(ceSearch3));
    provider.expectEvent(new CloseEvent(ceBind2ok));
    provider.expectEvent(new CloseEvent(ceBind3));

    // Tear down and check final state.
    policy.finalizeAuthenticationPolicy();
    provider.assertAllExpectedEventsReceived();
  }



  /**
   * Tests that searches which fail in one LB pool are automatically retried in
   * the secondary LB pool.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(enabled = true)
  public void testFBRetrySearchOnFailure() throws Exception
  {
    // Mock configuration.
    final LDAPPassThroughAuthenticationPolicyCfg cfg = mockCfg()
        .withPrimaryServer(phost1).withSecondaryServer(shost1)
        .withMappingPolicy(MappingPolicy.MAPPED_SEARCH)
        .withMappedAttribute("uid").withBaseDN("o=ad");

    // Create all the events.
    final MockProvider provider = new MockProvider();

    // First of all the connection factories are created.
    final GetLDAPConnectionFactoryEvent fe1 = new GetLDAPConnectionFactoryEvent(
        phost1, cfg);
    final GetLDAPConnectionFactoryEvent fe2 = new GetLDAPConnectionFactoryEvent(
        shost1, cfg);
    provider.expectEvent(fe1).expectEvent(fe2);

    // Get connection for phost1, then search (fail), and retry on shost1
    final GetConnectionEvent ceSearch1 = new GetConnectionEvent(fe1);
    final GetConnectionEvent ceSearch2 = new GetConnectionEvent(fe2);
    final GetConnectionEvent ceBind1 = new GetConnectionEvent(fe1);

    provider
        .expectEvent(ceSearch1)
        .expectEvent(
            new SimpleBindEvent(ceSearch1, searchBindDNString,
                "searchPassword", ResultCode.SUCCESS))
        .expectEvent(
            new SearchEvent(ceSearch1, "o=ad", SearchScope.WHOLE_SUBTREE,
                "(uid=aduser)", ResultCode.UNAVAILABLE))
        .expectEvent(new CloseEvent(ceSearch1))
        .expectEvent(new GetConnectionEvent(fe1, ResultCode.UNAVAILABLE)) // pool retry
        .expectEvent(ceSearch2)
        .expectEvent(
            new SimpleBindEvent(ceSearch2, searchBindDNString,
                "searchPassword", ResultCode.SUCCESS))
        .expectEvent(
            new SearchEvent(ceSearch2, "o=ad", SearchScope.WHOLE_SUBTREE,
                "(uid=aduser)", adDNString))
        .expectEvent(ceBind1)
        .expectEvent(
            new SimpleBindEvent(ceBind1, adDNString, userPassword,
                ResultCode.SUCCESS));

    // Now simulate shost1 going down as well.

    // phost1 will have been marked as failed and won't be retried.
    provider
        .expectEvent(
            new SearchEvent(ceSearch2, "o=ad", SearchScope.WHOLE_SUBTREE,
                "(uid=aduser)", ResultCode.UNAVAILABLE))
        .expectEvent(new CloseEvent(ceSearch2))
        .expectEvent(new GetConnectionEvent(fe2, ResultCode.UNAVAILABLE)); // pool retry

    // Now simulate phost1 coming back and fail back to it.

    // Now simulate monitor thread run in which phost1 and shost1 are determined
    // to be available again.
    provider
        .expectEvent(ceSearch1)
        .expectEvent(
            new SimpleBindEvent(ceSearch1, searchBindDNString,
                "searchPassword", ResultCode.SUCCESS))
        .expectEvent(ceSearch2)
        .expectEvent(
            new SimpleBindEvent(ceSearch2, searchBindDNString,
                "searchPassword", ResultCode.SUCCESS));

    provider.expectEvent(
        new SearchEvent(ceSearch1, "o=ad", SearchScope.WHOLE_SUBTREE,
            "(uid=aduser)", adDNString)).expectEvent(
        new SimpleBindEvent(ceBind1, adDNString, userPassword,
            ResultCode.SUCCESS));

    // Connections should be cached until the policy is finalized.

    // Obtain policy and state.
    final LDAPPassThroughAuthenticationPolicyFactory factory = new LDAPPassThroughAuthenticationPolicyFactory(
        provider);
    assertTrue(factory.isConfigurationAcceptable(cfg, null));
    final AuthenticationPolicy policy = factory.createAuthenticationPolicy(cfg);

    // Authenticate 3 times, second should fail.
    for (int i = 0; i < 3; i++)
    {
      final AuthenticationPolicyState state = policy
          .createAuthenticationPolicyState(userEntry);
      assertEquals(state.getAuthenticationPolicy(), policy);

      // Perform authentication.
      switch (i)
      {
      case 0:
        // First attempt should succeed.
        assertTrue(state.passwordMatches(ByteString.valueOf(userPassword)));
        break;
      case 1:
        // Second attempt should fail.
        try
        {
          state.passwordMatches(ByteString.valueOf(userPassword));
          fail("password match unexpectedly succeeded");
        }
        catch (final DirectoryException e)
        {
          // No valid connections available so this should always fail with
          // INVALID_CREDENTIALS.
          assertEquals(e.getResultCode(), ResultCode.INVALID_CREDENTIALS,
              e.getMessage());
        }
        break;
      case 2:
        // Third attempt should succeed, once the monitor has run.
        provider.runMonitorTasks();
        assertTrue(state.passwordMatches(ByteString.valueOf(userPassword)));
        break;
      }

      state.finalizeStateAfterBind();
    }

    // Cached connections should be closed when the policy is finalized.
    provider.expectEvent(new CloseEvent(ceSearch1));
    provider.expectEvent(new CloseEvent(ceSearch2));
    provider.expectEvent(new CloseEvent(ceBind1));

    // Tear down and check final state.
    policy.finalizeAuthenticationPolicy();
    provider.assertAllExpectedEventsReceived();
  }



  /**
   * Tests configuration validation.
   *
   * @param cfg
   *          The configuration to be tested.
   * @param isValid
   *          Whether or not the provided configuration is valid.
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(enabled = true, dataProvider = "testIsConfigurationAcceptableData")
  public void testIsConfigurationAcceptable(
      final LDAPPassThroughAuthenticationPolicyCfg cfg, final boolean isValid)
      throws Exception
  {
    final LDAPPassThroughAuthenticationPolicyFactory factory = new LDAPPassThroughAuthenticationPolicyFactory();
    assertEquals(
        factory.isConfigurationAcceptable(cfg, new LinkedList<Message>()),
        isValid);
  }



  /**
   * Returns test data for {@link #testIsConfigurationAcceptable}.
   *
   * @return Test data for {@link #testIsConfigurationAcceptable}.
   */
  @DataProvider
  public Object[][] testIsConfigurationAcceptableData()
  {
    // @formatter:off
    return new Object[][] {
        /* cfg, isValid */

        // Test server configuration.
        { mockCfg().withPrimaryServer("test:1"), true },
        { mockCfg().withPrimaryServer("test:65535"), true },
        { mockCfg().withPrimaryServer("test:0"), false },
        { mockCfg().withPrimaryServer("test:65536"), false },
        { mockCfg().withPrimaryServer("test:1000000"), false },
        { mockCfg().withSecondaryServer("test:1"), true },
        { mockCfg().withSecondaryServer("test:65535"), true },
        { mockCfg().withSecondaryServer("test:0"), false },
        { mockCfg().withSecondaryServer("test:65536"), false },
        { mockCfg().withSecondaryServer("test:1000000"), false },

        // Test mapped search parameters.
        { mockCfg().withMappingPolicy(MappingPolicy.MAPPED_SEARCH), true },
        { mockCfg().withMappingPolicy(MappingPolicy.MAPPED_SEARCH).withMappedSearchBindDN(null).withMappedSearchBindPassword(null), true },
        { mockCfg().withMappingPolicy(MappingPolicy.MAPPED_SEARCH).withMappedSearchBindPassword(null), false },
        { mockCfg().withMappingPolicy(MappingPolicy.MAPPED_SEARCH).withMappedSearchBindPasswordProperty("org.opendj.dummy.property"), false },
        { mockCfg().withMappingPolicy(MappingPolicy.MAPPED_SEARCH).withMappedSearchBindPasswordProperty("java.version"), true },
        { mockCfg().withMappingPolicy(MappingPolicy.MAPPED_SEARCH).withMappedSearchBindPasswordEnvironmentVariable("ORG_OPENDJ_DUMMY_ENVVAR"), false },
        { mockCfg().withMappingPolicy(MappingPolicy.MAPPED_SEARCH).withMappedSearchBindPasswordFile("dummy_file.txt"), false },
        { mockCfg().withMappingPolicy(MappingPolicy.MAPPED_SEARCH).withMappedSearchBindPasswordFile("config/admin-keystore.pin"), true },

    };
    // @formatter:on
  }



  /**
   * Tests that searches which fail on one server are automatically retried on
   * another within the same LB.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(enabled = true)
  public void testLBRetrySearchOnFailure() throws Exception
  {
    // Mock configuration.
    final LDAPPassThroughAuthenticationPolicyCfg cfg = mockCfg()
        .withPrimaryServer(phost1).withPrimaryServer(phost2)
        .withMappingPolicy(MappingPolicy.MAPPED_SEARCH)
        .withMappedAttribute("uid").withBaseDN("o=ad");

    // Create all the events.
    final MockProvider provider = new MockProvider();

    // First of all the connection factories are created.
    final GetLDAPConnectionFactoryEvent fe1 = new GetLDAPConnectionFactoryEvent(
        phost1, cfg);
    final GetLDAPConnectionFactoryEvent fe2 = new GetLDAPConnectionFactoryEvent(
        phost2, cfg);
    provider.expectEvent(fe1).expectEvent(fe2);

    // Get connection for phost1, then search (fail), and retry on phost2
    final GetConnectionEvent ceSearch1 = new GetConnectionEvent(fe1);
    final GetConnectionEvent ceSearch2 = new GetConnectionEvent(fe2);
    final GetConnectionEvent ceBind1 = new GetConnectionEvent(fe1);
    final GetConnectionEvent ceBind2 = new GetConnectionEvent(fe2);

    provider
        .expectEvent(ceSearch1)
        .expectEvent(
            new SimpleBindEvent(ceSearch1, searchBindDNString,
                "searchPassword", ResultCode.SUCCESS))
        .expectEvent(
            new SearchEvent(ceSearch1, "o=ad", SearchScope.WHOLE_SUBTREE,
                "(uid=aduser)", ResultCode.UNAVAILABLE))
        .expectEvent(new CloseEvent(ceSearch1))
        .expectEvent(new GetConnectionEvent(fe1, ResultCode.UNAVAILABLE)) // pool retry
        .expectEvent(ceSearch2)
        .expectEvent(
            new SimpleBindEvent(ceSearch2, searchBindDNString,
                "searchPassword", ResultCode.SUCCESS))
        .expectEvent(
            new SearchEvent(ceSearch2, "o=ad", SearchScope.WHOLE_SUBTREE,
                "(uid=aduser)", adDNString))
        .expectEvent(ceBind1)
        .expectEvent(
            new SimpleBindEvent(ceBind1, adDNString, userPassword,
                ResultCode.SUCCESS));

    // Now simulate phost2 going down as well.

    // phost1 will have been marked as failed and won't be retried.
    provider.expectEvent(
        new SearchEvent(ceSearch2, "o=ad", SearchScope.WHOLE_SUBTREE,
            "(uid=aduser)", ResultCode.UNAVAILABLE)).expectEvent(
        new CloseEvent(ceSearch2))
        .expectEvent(new GetConnectionEvent(fe2, ResultCode.UNAVAILABLE)); // pool retry

    // Now simulate phost1 coming back and fail back to it.

    // Now simulate monitor thread run in which phost1 and shost1 are determined
    // to be available again.
    provider
        .expectEvent(ceSearch1)
        .expectEvent(
            new SimpleBindEvent(ceSearch1, searchBindDNString,
                "searchPassword", ResultCode.SUCCESS))
        .expectEvent(ceSearch2)
        .expectEvent(
            new SimpleBindEvent(ceSearch2, searchBindDNString,
                "searchPassword", ResultCode.SUCCESS));

    // Note that the bind will be load-balanced.
    provider
        .expectEvent(
            new SearchEvent(ceSearch1, "o=ad", SearchScope.WHOLE_SUBTREE,
                "(uid=aduser)", adDNString))
        .expectEvent(ceBind2)
        .expectEvent(
            new SimpleBindEvent(ceBind2, adDNString, userPassword,
                ResultCode.SUCCESS));

    // Connections should be cached until the policy is finalized.

    // Obtain policy and state.
    final LDAPPassThroughAuthenticationPolicyFactory factory = new LDAPPassThroughAuthenticationPolicyFactory(
        provider);
    assertTrue(factory.isConfigurationAcceptable(cfg, null));
    final AuthenticationPolicy policy = factory.createAuthenticationPolicy(cfg);

    // Authenticate 3 times, second should fail.
    for (int i = 0; i < 3; i++)
    {
      final AuthenticationPolicyState state = policy
          .createAuthenticationPolicyState(userEntry);
      assertEquals(state.getAuthenticationPolicy(), policy);

      // Perform authentication.
      switch (i)
      {
      case 0:
        // First attempt should succeed.
        assertTrue(state.passwordMatches(ByteString.valueOf(userPassword)));
        break;
      case 1:
        // Second attempt should fail.
        try
        {
          state.passwordMatches(ByteString.valueOf(userPassword));
          fail("password match unexpectedly succeeded");
        }
        catch (final DirectoryException e)
        {
          // No valid connections available so this should always fail with
          // INVALID_CREDENTIALS.
          assertEquals(e.getResultCode(), ResultCode.INVALID_CREDENTIALS,
              e.getMessage());
        }
        break;
      case 2:
        // Third attempt should succeed, once the monitor has run.
        provider.runMonitorTasks();
        assertTrue(state.passwordMatches(ByteString.valueOf(userPassword)));
        break;
      }

      state.finalizeStateAfterBind();
    }

    // Cached connections should be closed when the policy is finalized.
    provider.expectEvent(new CloseEvent(ceSearch1));
    provider.expectEvent(new CloseEvent(ceSearch2));
    provider.expectEvent(new CloseEvent(ceBind1));
    provider.expectEvent(new CloseEvent(ceBind2));

    // Tear down and check final state.
    policy.finalizeAuthenticationPolicy();
    provider.assertAllExpectedEventsReceived();
  }



  /**
   * Tests valid bind which times out at the client. These should trigger a
   * CLIENT_SIDE_TIMEOUT result code.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(enabled = true)
  public void testLDAPConnectionFactoryBindClientTimeout() throws Exception
  {
    // Mock configuration.
    final LDAPPassThroughAuthenticationPolicyCfg cfg = mockCfg()
        .withConnectionTimeout(500);

    // Mock server.
    final MockServer server = mockServer(cfg).thenAccept().thenBlock().start();

    // Test connect and close.
    final LDAPConnectionFactory factory = new LDAPConnectionFactory(
        "127.0.0.1", server.getPort(), cfg);
    Connection connection = null;
    try
    {
      connection = factory.getConnection();
      connection.simpleBind(ByteString.valueOf(searchBindDNString),
          ByteString.valueOf(userPassword));
      fail("Bind attempt should have failed");
    }
    catch (final DirectoryException e)
    {
      assertEquals(e.getResultCode(), ResultCode.CLIENT_SIDE_TIMEOUT,
          e.getMessage());
    }
    finally
    {
      if (connection != null)
      {
        connection.close();
      }
      server.unblock();
      server.stop();
    }
  }



  /**
   * Tests valid bind which never receives a response because the server
   * abruptly closes the connection.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(enabled = true)
  public void testLDAPConnectionFactoryBindConnectionClosed() throws Exception
  {
    // Mock configuration.
    final LDAPPassThroughAuthenticationPolicyCfg cfg = mockCfg();

    // Mock server.
    final MockServer server = mockServer(cfg).thenAccept()
        .thenReceive(1, newBindRequest(searchBindDNString, userPassword))
        .thenClose().start();

    // Test connect and close.
    final LDAPConnectionFactory factory = new LDAPConnectionFactory(
        "127.0.0.1", server.getPort(), cfg);
    Connection connection = null;
    try
    {
      connection = factory.getConnection();
      connection.simpleBind(ByteString.valueOf(searchBindDNString),
          ByteString.valueOf(userPassword));
      fail("Bind attempt should have failed");
    }
    catch (final DirectoryException e)
    {
      assertEquals(e.getResultCode(), ResultCode.CLIENT_SIDE_SERVER_DOWN,
          e.getMessage());
    }
    finally
    {
      if (connection != null)
      {
        connection.close();
      }
      server.stop();
    }
  }



  /**
   * Tests bind which receives a disconnect notification. The error result code
   * should be passed back to the called.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(enabled = true)
  public void testLDAPConnectionFactoryBindDisconnectNotification()
      throws Exception
  {
    // Mock configuration.
    final LDAPPassThroughAuthenticationPolicyCfg cfg = mockCfg();

    // Mock server.
    final MockServer server = mockServer(cfg)
        .thenAccept()
        .thenReceive(1, newBindRequest(searchBindDNString, userPassword))
        .thenSend(0 /* disconnect ID */,
            newDisconnectNotification(ResultCode.UNAVAILABLE)).start();

    // Test connect and close.
    final LDAPConnectionFactory factory = new LDAPConnectionFactory(
        "127.0.0.1", server.getPort(), cfg);
    Connection connection = null;
    try
    {
      connection = factory.getConnection();
      connection.simpleBind(ByteString.valueOf(searchBindDNString),
          ByteString.valueOf(userPassword));
      fail("Bind attempt should have failed");
    }
    catch (final DirectoryException e)
    {
      // Should be the result code sent by the server.
      assertEquals(e.getResultCode(), ResultCode.UNAVAILABLE, e.getMessage());
    }
    finally
    {
      if (connection != null)
      {
        connection.close();
      }
      server.stop();
    }
  }



  /**
   * Tests bind with invalid credentials which should return a
   * INVALID_CREDENTIALS result code.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(enabled = true)
  public void testLDAPConnectionFactoryBindInvalidCredentials()
      throws Exception
  {
    // Mock configuration.
    final LDAPPassThroughAuthenticationPolicyCfg cfg = mockCfg();

    // Mock server.
    final MockServer server = mockServer(cfg).thenAccept()
        .thenReceive(1, newBindRequest(searchBindDNString, userPassword))
        .thenSend(1, newBindResult(ResultCode.INVALID_CREDENTIALS)).start();

    // Test connect and close.
    final LDAPConnectionFactory factory = new LDAPConnectionFactory(
        "127.0.0.1", server.getPort(), cfg);
    Connection connection = null;
    try
    {
      connection = factory.getConnection();
      connection.simpleBind(ByteString.valueOf(searchBindDNString),
          ByteString.valueOf(userPassword));
      fail("Bind attempt should have failed");
    }
    catch (final DirectoryException e)
    {
      assertEquals(e.getResultCode(), ResultCode.INVALID_CREDENTIALS,
          e.getMessage());
    }
    finally
    {
      if (connection != null)
      {
        connection.close();
      }
      server.stop();
    }
  }



  /**
   * Tests bind which returns an error result. The error result code should be
   * passed back to the caller.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(enabled = true)
  public void testLDAPConnectionFactoryBindOtherError() throws Exception
  {
    // Mock configuration.
    final LDAPPassThroughAuthenticationPolicyCfg cfg = mockCfg();

    // Mock server.
    final MockServer server = mockServer(cfg).thenAccept()
        .thenReceive(1, newBindRequest(searchBindDNString, userPassword))
        .thenSend(1, newBindResult(ResultCode.UNAVAILABLE)).start();

    // Test connect and close.
    final LDAPConnectionFactory factory = new LDAPConnectionFactory(
        "127.0.0.1", server.getPort(), cfg);
    Connection connection = null;
    try
    {
      connection = factory.getConnection();
      connection.simpleBind(ByteString.valueOf(searchBindDNString),
          ByteString.valueOf(userPassword));
      fail("Bind attempt should have failed");
    }
    catch (final DirectoryException e)
    {
      assertEquals(e.getResultCode(), ResultCode.UNAVAILABLE, e.getMessage());
    }
    finally
    {
      if (connection != null)
      {
        connection.close();
      }
      server.stop();
    }
  }



  /**
   * Tests valid bind returning success.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(enabled = true)
  public void testLDAPConnectionFactoryBindSuccess() throws Exception
  {
    // Mock configuration.
    final LDAPPassThroughAuthenticationPolicyCfg cfg = mockCfg();

    // Mock server.
    final MockServer server = mockServer(cfg).thenAccept()
        .thenReceive(1, newBindRequest(searchBindDNString, userPassword))
        .thenSend(1, newBindResult(ResultCode.SUCCESS)).start();

    // Test connect and close.
    final LDAPConnectionFactory factory = new LDAPConnectionFactory(
        "127.0.0.1", server.getPort(), cfg);
    Connection connection = null;
    try
    {
      connection = factory.getConnection();
      connection.simpleBind(ByteString.valueOf(searchBindDNString),
          ByteString.valueOf(userPassword));
    }
    finally
    {
      if (connection != null)
      {
        connection.close();
      }
      server.stop();
    }
  }



  /**
   * Tests successful connect/unbind.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(enabled = true)
  public void testLDAPConnectionFactoryConnectAndUnbind() throws Exception
  {
    // Mock configuration.
    final LDAPPassThroughAuthenticationPolicyCfg cfg = mockCfg();

    // Mock server.
    final MockServer server = mockServer(cfg).thenAccept()
        .thenReceive(1, new UnbindRequestProtocolOp()).thenClose().start();

    // Test connect and close.
    final LDAPConnectionFactory factory = new LDAPConnectionFactory(
        "127.0.0.1", server.getPort(), cfg);
    try
    {
      final Connection connection = factory.getConnection();
      connection.close();
    }
    finally
    {
      server.stop();
    }
  }



  /**
   * Tests that invalid ports are handled properly. These should trigger a
   * CLIENT_SIDE_CONNECT_ERROR result code.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(enabled = true)
  public void testLDAPConnectionFactoryConnectPortNotInUse() throws Exception
  {
    // Grab an unused port.
    final ServerSocket socket = TestCaseUtils.bindFreePort();
    final int port = socket.getLocalPort();

    // FIXME: will it matter if the port is left in TIME_WAIT?
    socket.close();

    // Mock configuration.
    final LDAPPassThroughAuthenticationPolicyCfg cfg = mockCfg();

    // Test connect and close.
    final LDAPConnectionFactory factory = new LDAPConnectionFactory(
        "127.0.0.1", port, cfg);
    Connection connection = null;
    try
    {
      connection = factory.getConnection();
      fail("Connect attempt should have failed");
    }
    catch (final DirectoryException e)
    {
      assertEquals(e.getResultCode(), ResultCode.CLIENT_SIDE_CONNECT_ERROR,
          e.getMessage());
    }
    finally
    {
      if (connection != null)
      {
        connection.close();
      }
    }
  }



  /**
   * Tests that unknown hosts are handled properly. These should trigger a
   * CLIENT_SIDE_CONNECT_ERROR result code.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(enabled = true)
  public void testLDAPConnectionFactoryConnectUnknownHost() throws Exception
  {
    // Mock configuration.
    final LDAPPassThroughAuthenticationPolicyCfg cfg = mockCfg();

    // FIXME: can we guarantee that "unknownhost" does not exist?
    final LDAPConnectionFactory factory = new LDAPConnectionFactory(
        "unknownhost", 31415, cfg);
    Connection connection = null;
    try
    {
      connection = factory.getConnection();
      fail("Connect attempt should have failed");
    }
    catch (final DirectoryException e)
    {
      assertEquals(e.getResultCode(), ResultCode.CLIENT_SIDE_CONNECT_ERROR,
          e.getMessage());
    }
    finally
    {
      if (connection != null)
      {
        connection.close();
      }
    }
  }



  /**
   * Tests valid search which times out at the client. These should trigger a
   * CLIENT_SIDE_TIMEOUT result code.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(enabled = true)
  public void testLDAPConnectionFactorySearchClientTimeout() throws Exception
  {
    // Mock configuration.
    final LDAPPassThroughAuthenticationPolicyCfg cfg = mockCfg()
        .withConnectionTimeout(500);

    // Mock server.
    final MockServer server = mockServer(cfg).thenAccept().thenBlock().start();

    // Test connect and close.
    final LDAPConnectionFactory factory = new LDAPConnectionFactory(
        "127.0.0.1", server.getPort(), cfg);
    Connection connection = null;
    try
    {
      connection = factory.getConnection();
      connection.search(searchBindDN, SearchScope.WHOLE_SUBTREE,
          SearchFilter.createFilterFromString("(objectClass=*)"));
      fail("Search attempt should have timed out");
    }
    catch (final DirectoryException e)
    {
      assertEquals(e.getResultCode(), ResultCode.CLIENT_SIDE_TIMEOUT,
          e.getMessage());
    }
    finally
    {
      if (connection != null)
      {
        connection.close();
      }
      server.unblock();
      server.stop();
    }
  }



  /**
   * Tests valid search which never receives a response because the server
   * abruptly closes the connection.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(enabled = true)
  public void testLDAPConnectionFactorySearchConnectionClosed()
      throws Exception
  {
    // Mock configuration.
    final LDAPPassThroughAuthenticationPolicyCfg cfg = mockCfg();

    // Mock server.
    final MockServer server = mockServer(cfg)
        .thenAccept()
        .thenReceive(1,
            newSearchRequest(searchBindDNString, "(uid=aduser)", cfg))
        .thenClose().start();

    // Test connect and close.
    final LDAPConnectionFactory factory = new LDAPConnectionFactory(
        "127.0.0.1", server.getPort(), cfg);
    Connection connection = null;
    try
    {
      connection = factory.getConnection();
      connection.search(searchBindDN, SearchScope.WHOLE_SUBTREE,
          SearchFilter.createFilterFromString("(uid=aduser)"));
      fail("Search attempt should have failed");
    }
    catch (final DirectoryException e)
    {
      assertEquals(e.getResultCode(), ResultCode.CLIENT_SIDE_SERVER_DOWN,
          e.getMessage());
    }
    finally
    {
      if (connection != null)
      {
        connection.close();
      }
      server.stop();
    }
  }



  /**
   * Tests valid search which receives a disconnect notification. The error
   * result code should be passed back to the called.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(enabled = true)
  public void testLDAPConnectionFactorySearchDisconnectNotification()
      throws Exception
  {
    // Mock configuration.
    final LDAPPassThroughAuthenticationPolicyCfg cfg = mockCfg();

    // Mock server.
    final MockServer server = mockServer(cfg)
        .thenAccept()
        .thenReceive(1,
            newSearchRequest(searchBindDNString, "(uid=aduser)", cfg))
        .thenSend(0 /* disconnect ID */,
            newDisconnectNotification(ResultCode.UNAVAILABLE)).start();

    // Test connect and close.
    final LDAPConnectionFactory factory = new LDAPConnectionFactory(
        "127.0.0.1", server.getPort(), cfg);
    Connection connection = null;
    try
    {
      connection = factory.getConnection();
      connection.search(searchBindDN, SearchScope.WHOLE_SUBTREE,
          SearchFilter.createFilterFromString("(uid=aduser)"));
      fail("Search attempt should have failed");
    }
    catch (final DirectoryException e)
    {
      // Should be the result code sent by the server.
      assertEquals(e.getResultCode(), ResultCode.UNAVAILABLE, e.getMessage());
    }
    finally
    {
      if (connection != null)
      {
        connection.close();
      }
      server.stop();
    }
  }



  /**
   * Tests valid search returning no results are handled properly. These should
   * trigger a CLIENT_SIDE_NO_RESULTS_RETURNED result code.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(enabled = true)
  public void testLDAPConnectionFactorySearchNoResults() throws Exception
  {
    // Mock configuration.
    final LDAPPassThroughAuthenticationPolicyCfg cfg = mockCfg();

    // Mock server.
    final MockServer server = mockServer(cfg)
        .thenAccept()
        .thenReceive(1,
            newSearchRequest(searchBindDNString, "(uid=aduser)", cfg))
        .thenSend(1, newSearchResult(ResultCode.SUCCESS)).start();

    // Test connect and close.
    final LDAPConnectionFactory factory = new LDAPConnectionFactory(
        "127.0.0.1", server.getPort(), cfg);
    Connection connection = null;
    try
    {
      connection = factory.getConnection();
      connection.search(searchBindDN, SearchScope.WHOLE_SUBTREE,
          SearchFilter.createFilterFromString("(uid=aduser)"));
      fail("Search attempt should have failed");
    }
    catch (final DirectoryException e)
    {
      assertEquals(e.getResultCode(),
          ResultCode.CLIENT_SIDE_NO_RESULTS_RETURNED, e.getMessage());
    }
    finally
    {
      if (connection != null)
      {
        connection.close();
      }
      server.stop();
    }
  }



  /**
   * Tests search returning no entries and an error result. The error result
   * code should be passed back to the caller.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(enabled = true)
  public void testLDAPConnectionFactorySearchOtherError() throws Exception
  {
    // Mock configuration.
    final LDAPPassThroughAuthenticationPolicyCfg cfg = mockCfg();

    // Mock server.
    final MockServer server = mockServer(cfg)
        .thenAccept()
        .thenReceive(1,
            newSearchRequest(searchBindDNString, "(uid=aduser)", cfg))
        .thenSend(1, newSearchResult(ResultCode.UNAVAILABLE)).start();

    // Test connect and close.
    final LDAPConnectionFactory factory = new LDAPConnectionFactory(
        "127.0.0.1", server.getPort(), cfg);
    Connection connection = null;
    try
    {
      connection = factory.getConnection();
      connection.search(searchBindDN, SearchScope.WHOLE_SUBTREE,
          SearchFilter.createFilterFromString("(uid=aduser)"));
      fail("Search attempt should have failed");
    }
    catch (final DirectoryException e)
    {
      // Should be the result code sent by the server.
      assertEquals(e.getResultCode(), ResultCode.UNAVAILABLE, e.getMessage());
    }
    finally
    {
      if (connection != null)
      {
        connection.close();
      }
      server.stop();
    }
  }



  /**
   * Tests valid search returning a single entry followed by a size limit
   * exceeded error are handled properly. These should trigger a
   * CLIENT_SIDE_MORE_RESULTS_TO_RETURN result code.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(enabled = true)
  public void testLDAPConnectionFactorySearchSizeLimit() throws Exception
  {
    // Mock configuration.
    final LDAPPassThroughAuthenticationPolicyCfg cfg = mockCfg();

    // Mock server.
    final MockServer server = mockServer(cfg)
        .thenAccept()
        .thenReceive(1,
            newSearchRequest(searchBindDNString, "(uid=aduser)", cfg))
        .thenSend(1, newSearchEntry(adDNString))
        .thenSend(1, newSearchResult(ResultCode.SIZE_LIMIT_EXCEEDED)).start();

    // Test connect and close.
    final LDAPConnectionFactory factory = new LDAPConnectionFactory(
        "127.0.0.1", server.getPort(), cfg);
    Connection connection = null;
    try
    {
      connection = factory.getConnection();
      connection.search(searchBindDN, SearchScope.WHOLE_SUBTREE,
          SearchFilter.createFilterFromString("(uid=aduser)"));
      fail("Search attempt should have failed");
    }
    catch (final DirectoryException e)
    {
      assertEquals(e.getResultCode(),
          ResultCode.CLIENT_SIDE_MORE_RESULTS_TO_RETURN, e.getMessage());
    }
    finally
    {
      if (connection != null)
      {
        connection.close();
      }
      server.stop();
    }
  }



  /**
   * Tests valid search returning a single entry works properly.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(enabled = true)
  public void testLDAPConnectionFactorySearchSuccess() throws Exception
  {
    // Mock configuration.
    final LDAPPassThroughAuthenticationPolicyCfg cfg = mockCfg();

    // Mock server.
    final MockServer server = mockServer(cfg)
        .thenAccept()
        .thenReceive(1,
            newSearchRequest(searchBindDNString, "(uid=aduser)", cfg))
        .thenSend(1, newSearchEntry(adDNString))
        .thenSend(1, newSearchResult(ResultCode.SUCCESS)).start();

    // Test connect and close.
    final LDAPConnectionFactory factory = new LDAPConnectionFactory(
        "127.0.0.1", server.getPort(), cfg);
    Connection connection = null;
    try
    {
      connection = factory.getConnection();
      final ByteString username = connection.search(searchBindDN,
          SearchScope.WHOLE_SUBTREE,
          SearchFilter.createFilterFromString("(uid=aduser)"));
      assertEquals(username, ByteString.valueOf(adDNString));
    }
    finally
    {
      if (connection != null)
      {
        connection.close();
      }
      server.stop();
    }
  }



  /**
   * Tests valid search returning a single entry followed by a time limit
   * exceeded error are handled properly. These should trigger a
   * CLIENT_SIDE_MORE_RESULTS_TO_RETURN result code.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(enabled = true)
  public void testLDAPConnectionFactorySearchTimeLimit() throws Exception
  {
    // Mock configuration.
    final LDAPPassThroughAuthenticationPolicyCfg cfg = mockCfg();

    // Mock server.
    final MockServer server = mockServer(cfg)
        .thenAccept()
        .thenReceive(1,
            newSearchRequest(searchBindDNString, "(uid=aduser)", cfg))
        .thenSend(1, newSearchEntry(adDNString))
        .thenSend(1, newSearchResult(ResultCode.TIME_LIMIT_EXCEEDED)).start();

    // Test connect and close.
    final LDAPConnectionFactory factory = new LDAPConnectionFactory(
        "127.0.0.1", server.getPort(), cfg);
    Connection connection = null;
    try
    {
      connection = factory.getConnection();
      connection.search(searchBindDN, SearchScope.WHOLE_SUBTREE,
          SearchFilter.createFilterFromString("(uid=aduser)"));
      fail("Search attempt should have failed");
    }
    catch (final DirectoryException e)
    {
      assertEquals(e.getResultCode(), ResultCode.TIME_LIMIT_EXCEEDED,
          e.getMessage());
    }
    finally
    {
      if (connection != null)
      {
        connection.close();
      }
      server.stop();
    }
  }



  /**
   * Tests valid search returning many results are handled properly. These
   * should trigger a CLIENT_SIDE_MORE_RESULTS_TO_RETURN result code.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(enabled = true)
  public void testLDAPConnectionFactorySearchTooManyResults() throws Exception
  {
    // Mock configuration.
    final LDAPPassThroughAuthenticationPolicyCfg cfg = mockCfg();

    // Mock server.
    final MockServer server = mockServer(cfg)
        .thenAccept()
        .thenReceive(1,
            newSearchRequest(searchBindDNString, "(uid=aduser)", cfg))
        .thenSend(1, newSearchEntry(adDNString))
        .thenSend(1, newSearchEntry(opendjDNString))
        .thenSend(1, newSearchResult(ResultCode.SUCCESS)).start();

    // Test connect and close.
    final LDAPConnectionFactory factory = new LDAPConnectionFactory(
        "127.0.0.1", server.getPort(), cfg);
    Connection connection = null;
    try
    {
      connection = factory.getConnection();
      connection.search(searchBindDN, SearchScope.WHOLE_SUBTREE,
          SearchFilter.createFilterFromString("(uid=aduser)"));
      fail("Search attempt should have failed");
    }
    catch (final DirectoryException e)
    {
      assertEquals(e.getResultCode(),
          ResultCode.CLIENT_SIDE_MORE_RESULTS_TO_RETURN, e.getMessage());
    }
    finally
    {
      if (connection != null)
      {
        connection.close();
      }
      server.stop();
    }
  }



  /**
   * Tests load balancing across 3 servers.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(enabled = true)
  public void testLoadBalancing() throws Exception
  {
    // Mock configuration.
    final LDAPPassThroughAuthenticationPolicyCfg cfg = mockCfg()
        .withPrimaryServer(phost1).withPrimaryServer(phost2)
        .withPrimaryServer(phost3)
        .withMappingPolicy(MappingPolicy.MAPPED_SEARCH)
        .withMappedAttribute("uid").withBaseDN("o=ad");

    // Create all the events.
    final MockProvider provider = new MockProvider();

    // First of all the connection factories are created.
    final GetLDAPConnectionFactoryEvent fe1 = new GetLDAPConnectionFactoryEvent(
        phost1, cfg);
    final GetLDAPConnectionFactoryEvent fe2 = new GetLDAPConnectionFactoryEvent(
        phost2, cfg);
    final GetLDAPConnectionFactoryEvent fe3 = new GetLDAPConnectionFactoryEvent(
        phost3, cfg);
    provider.expectEvent(fe1).expectEvent(fe2).expectEvent(fe3);

    // Get connection for phost1, then search, then bind.
    final GetConnectionEvent ceSearch1 = new GetConnectionEvent(fe1);
    final GetConnectionEvent ceBind1 = new GetConnectionEvent(fe1);
    provider
        .expectEvent(ceSearch1)
        .expectEvent(
            new SimpleBindEvent(ceSearch1, searchBindDNString,
                "searchPassword", ResultCode.SUCCESS))
        .expectEvent(
            new SearchEvent(ceSearch1, "o=ad", SearchScope.WHOLE_SUBTREE,
                "(uid=aduser)", adDNString))
        .expectEvent(ceBind1)
        .expectEvent(
            new SimpleBindEvent(ceBind1, adDNString, userPassword,
                ResultCode.SUCCESS));

    // Get connection for phost2, then search, then bind.
    final GetConnectionEvent ceSearch2 = new GetConnectionEvent(fe2);
    final GetConnectionEvent ceBind2 = new GetConnectionEvent(fe2);
    provider
        .expectEvent(ceSearch2)
        .expectEvent(
            new SimpleBindEvent(ceSearch2, searchBindDNString,
                "searchPassword", ResultCode.SUCCESS))
        .expectEvent(
            new SearchEvent(ceSearch2, "o=ad", SearchScope.WHOLE_SUBTREE,
                "(uid=aduser)", adDNString))
        .expectEvent(ceBind2)
        .expectEvent(
            new SimpleBindEvent(ceBind2, adDNString, userPassword,
                ResultCode.SUCCESS));

    // Get connection for phost3, then search, then bind.
    final GetConnectionEvent ceSearch3 = new GetConnectionEvent(fe3);
    final GetConnectionEvent ceBind3 = new GetConnectionEvent(fe3);
    provider
        .expectEvent(ceSearch3)
        .expectEvent(
            new SimpleBindEvent(ceSearch3, searchBindDNString,
                "searchPassword", ResultCode.SUCCESS))
        .expectEvent(
            new SearchEvent(ceSearch3, "o=ad", SearchScope.WHOLE_SUBTREE,
                "(uid=aduser)", adDNString))
        .expectEvent(ceBind3)
        .expectEvent(
            new SimpleBindEvent(ceBind3, adDNString, userPassword,
                ResultCode.SUCCESS));

    // Repeat again using cached connection to phost1: search, then bind.
    provider.expectEvent(
        new SearchEvent(ceSearch1, "o=ad", SearchScope.WHOLE_SUBTREE,
            "(uid=aduser)", adDNString)).expectEvent(
        new SimpleBindEvent(ceBind1, adDNString, userPassword,
            ResultCode.SUCCESS));

    // Repeat again using cached connection to phost2: search, then bind.
    provider.expectEvent(
        new SearchEvent(ceSearch2, "o=ad", SearchScope.WHOLE_SUBTREE,
            "(uid=aduser)", adDNString)).expectEvent(
        new SimpleBindEvent(ceBind2, adDNString, userPassword,
            ResultCode.SUCCESS));

    // Repeat again using cached connection to phost3: search, then bind.
    provider.expectEvent(
        new SearchEvent(ceSearch3, "o=ad", SearchScope.WHOLE_SUBTREE,
            "(uid=aduser)", adDNString)).expectEvent(
        new SimpleBindEvent(ceBind3, adDNString, userPassword,
            ResultCode.SUCCESS));

    // Connections should be cached until the policy is finalized.

    // Obtain policy and state.
    final LDAPPassThroughAuthenticationPolicyFactory factory = new LDAPPassThroughAuthenticationPolicyFactory(
        provider);
    assertTrue(factory.isConfigurationAcceptable(cfg, null));
    final AuthenticationPolicy policy = factory.createAuthenticationPolicy(cfg);

    // Cycle twice through the LB pool.
    for (int i = 0; i < 6; i++)
    {
      final AuthenticationPolicyState state = policy
          .createAuthenticationPolicyState(userEntry);
      assertEquals(state.getAuthenticationPolicy(), policy);

      // Perform authentication.
      assertTrue(state.passwordMatches(ByteString.valueOf(userPassword)));

      state.finalizeStateAfterBind();
    }

    // Cached connections should be closed when the policy is finalized.
    provider.expectEvent(new CloseEvent(ceSearch1));
    provider.expectEvent(new CloseEvent(ceSearch2));
    provider.expectEvent(new CloseEvent(ceSearch3));
    provider.expectEvent(new CloseEvent(ceBind1));
    provider.expectEvent(new CloseEvent(ceBind2));
    provider.expectEvent(new CloseEvent(ceBind3));

    // Tear down and check final state.
    policy.finalizeAuthenticationPolicy();
    provider.assertAllExpectedEventsReceived();
  }



  /**
   * Tests the different mapping policies: connection attempts will succeed, as
   * will any searches, but the final user bind may or may not succeed depending
   * on the provided result code.
   * <p>
   * Non-fatal errors (e.g. entry not found) should not cause the bind
   * connection to be closed.
   *
   * @param mappingPolicy
   *          The mapping policy.
   * @param bindResultCode
   *          The bind result code.
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(enabled = true, dataProvider = "testMappingPolicyAuthenticationData")
  public void testMappingPolicyAuthentication(
      final MappingPolicy mappingPolicy, final ResultCode bindResultCode)
      throws Exception
  {
    // Mock configuration.
    final LDAPPassThroughAuthenticationPolicyCfg cfg = mockCfg()
        .withPrimaryServer(phost1)
        .withMappingPolicy(mappingPolicy)
        .withMappedAttribute(
            mappingPolicy == MappingPolicy.MAPPED_BIND ? "aduser" : "uid")
        .withBaseDN("o=ad");

    // Create the provider and its list of expected events.
    final GetLDAPConnectionFactoryEvent fe = new GetLDAPConnectionFactoryEvent(
        phost1, cfg);
    final MockProvider provider = new MockProvider().expectEvent(fe);

    // Add search events if doing a mapped search.
    GetConnectionEvent ceSearch = null;
    if (mappingPolicy == MappingPolicy.MAPPED_SEARCH)
    {
      ceSearch = new GetConnectionEvent(fe);
      provider
          .expectEvent(ceSearch)
          .expectEvent(
              new SimpleBindEvent(ceSearch, searchBindDNString,
                  "searchPassword", ResultCode.SUCCESS))
          .expectEvent(
              new SearchEvent(ceSearch, "o=ad", SearchScope.WHOLE_SUBTREE,
                  "(uid=aduser)", adDNString));
      // Connection should be cached until the policy is finalized.
    }

    // Add bind events.
    final GetConnectionEvent ceBind = new GetConnectionEvent(fe);
    provider.expectEvent(ceBind).expectEvent(
        new SimpleBindEvent(ceBind,
            mappingPolicy == MappingPolicy.UNMAPPED ? opendjDNString
                : adDNString, userPassword, bindResultCode));
    if (isServiceError(bindResultCode))
    {
      // The connection will fail and be closed immediately, and the pool will
      // retry on new connection.
      provider.expectEvent(new CloseEvent(ceBind));
      provider.expectEvent(new GetConnectionEvent(fe, bindResultCode));
    }

    // Connection should be cached until the policy is finalized or until the
    // connection fails.

    // Obtain policy and state.
    final LDAPPassThroughAuthenticationPolicyFactory factory = new LDAPPassThroughAuthenticationPolicyFactory(
        provider);
    assertTrue(factory.isConfigurationAcceptable(cfg, null));
    final AuthenticationPolicy policy = factory.createAuthenticationPolicy(cfg);
    final AuthenticationPolicyState state = policy
        .createAuthenticationPolicyState(userEntry);
    assertEquals(state.getAuthenticationPolicy(), policy);

    // Perform authentication.
    switch (bindResultCode)
    {
    case SUCCESS:
      assertTrue(state.passwordMatches(ByteString.valueOf(userPassword)));
      break;
    case INVALID_CREDENTIALS:
      assertFalse(state.passwordMatches(ByteString.valueOf(userPassword)));
      break;
    default:
      try
      {
        state.passwordMatches(ByteString.valueOf(userPassword));
        fail("password match did not fail");
      }
      catch (final DirectoryException e)
      {
        // No valid connections available so this should always fail with
        // INVALID_CREDENTIALS.
        assertEquals(e.getResultCode(), ResultCode.INVALID_CREDENTIALS,
            e.getMessage());
      }
      break;
    }

    // There should be no more pending events.
    provider.assertAllExpectedEventsReceived();
    state.finalizeStateAfterBind();

    // Cached connections should be closed when the policy is finalized.
    if (ceSearch != null)
    {
      provider.expectEvent(new CloseEvent(ceSearch));
    }
    if (!isServiceError(bindResultCode))
    {
      provider.expectEvent(new CloseEvent(ceBind));
    }

    // Tear down and check final state.
    policy.finalizeAuthenticationPolicy();
    provider.assertAllExpectedEventsReceived();
  }



  /**
   * Returns test data for {@link #testMappingPolicyAuthentication}.
   *
   * @return Test data for {@link #testMappingPolicyAuthentication}.
   */
  @DataProvider
  public Object[][] testMappingPolicyAuthenticationData()
  {
    // @formatter:off
    return new Object[][] {
        /* policy, bind result code */
        { MappingPolicy.UNMAPPED, ResultCode.SUCCESS },
        { MappingPolicy.UNMAPPED, ResultCode.INVALID_CREDENTIALS },
        { MappingPolicy.UNMAPPED, ResultCode.UNAVAILABLE },

        { MappingPolicy.MAPPED_BIND, ResultCode.SUCCESS },
        { MappingPolicy.MAPPED_BIND, ResultCode.INVALID_CREDENTIALS },
        { MappingPolicy.MAPPED_BIND, ResultCode.UNAVAILABLE },

        { MappingPolicy.MAPPED_SEARCH, ResultCode.SUCCESS },
        { MappingPolicy.MAPPED_SEARCH, ResultCode.INVALID_CREDENTIALS },
        { MappingPolicy.MAPPED_SEARCH, ResultCode.UNAVAILABLE },
    };
    // @formatter:on
  }



  /**
   * Tests that mapped PTA fails when no match attribute values are found.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(enabled = true)
  public void testMissingMappingAttributes() throws Exception
  {
    // Mock configuration.
    final LDAPPassThroughAuthenticationPolicyCfg cfg = mockCfg()
        .withPrimaryServer(phost1)
        .withMappingPolicy(MappingPolicy.MAPPED_SEARCH)
        .withMappedAttribute("uid1").withBaseDN("o=ad");

    // Create the provider and its list of expected events.
    final GetLDAPConnectionFactoryEvent fe = new GetLDAPConnectionFactoryEvent(
        phost1, cfg);
    final MockProvider provider = new MockProvider().expectEvent(fe);

    // Obtain policy and state.
    final Entry testUser = TestCaseUtils.makeEntry(
        /* @formatter:off */
        "dn: " + opendjDNString,
        "objectClass: top",
        "objectClass: person",
        "sn: user",
        "cn: test user",
        "aduser: " + adDNString
        /* @formatter:on */
    );

    final LDAPPassThroughAuthenticationPolicyFactory factory = new LDAPPassThroughAuthenticationPolicyFactory(
        provider);
    assertTrue(factory.isConfigurationAcceptable(cfg, null));
    final AuthenticationPolicy policy = factory.createAuthenticationPolicy(cfg);
    final AuthenticationPolicyState state = policy
        .createAuthenticationPolicyState(testUser);
    assertEquals(state.getAuthenticationPolicy(), policy);

    // Perform authentication.
    try
    {
      state.passwordMatches(ByteString.valueOf(userPassword));
      fail("password match unexpectedly succeeded");
    }
    catch (final DirectoryException e)
    {
      // No mapping attributes so this should always fail with
      // INVALID_CREDENTIALS.
      assertEquals(e.getResultCode(), ResultCode.INVALID_CREDENTIALS,
          e.getMessage());
    }

    // There should be no more pending events.
    provider.assertAllExpectedEventsReceived();
    state.finalizeStateAfterBind();

    // Tear down and check final state.
    policy.finalizeAuthenticationPolicy();
    provider.assertAllExpectedEventsReceived();
  }



  /**
   * Tests that mapped PTA uses an appropriate filter when multiple match
   * attributes are defined.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(enabled = true)
  public void testMultipleMappingAttributes() throws Exception
  {
    // Mock configuration.
    final LDAPPassThroughAuthenticationPolicyCfg cfg = mockCfg()
        .withPrimaryServer(phost1)
        .withMappingPolicy(MappingPolicy.MAPPED_SEARCH)
        .withMappedAttribute("uid1").withMappedAttribute("uid2")
        .withMappedAttribute("uid3").withBaseDN("o=ad");

    // Create the provider and its list of expected events.
    final GetLDAPConnectionFactoryEvent fe = new GetLDAPConnectionFactoryEvent(
        phost1, cfg);
    final MockProvider provider = new MockProvider().expectEvent(fe);

    // Add search events.
    final GetConnectionEvent ceSearch = new GetConnectionEvent(fe);
    provider
        .expectEvent(ceSearch)
        .expectEvent(
            new SimpleBindEvent(ceSearch, searchBindDNString, "searchPassword",
                ResultCode.SUCCESS))
        .expectEvent(
            new SearchEvent(ceSearch, "o=ad", SearchScope.WHOLE_SUBTREE,
                "(|(uid1=one)(uid2=two)(uid3=three))", adDNString));
    // Connection should be cached until the policy is finalized.

    // Add bind events.
    final GetConnectionEvent ceBind = new GetConnectionEvent(fe);
    provider.expectEvent(ceBind).expectEvent(
        new SimpleBindEvent(ceBind, adDNString, userPassword,
            ResultCode.SUCCESS));

    // Connection should be cached until the policy is finalized.

    // Obtain policy and state.
    final Entry testUser = TestCaseUtils.makeEntry(
        /* @formatter:off */
        "dn: " + opendjDNString,
        "objectClass: top",
        "objectClass: person",
        "sn: user",
        "cn: test user",
        "aduser: " + adDNString,
        "uid1: one",
        "uid2: two",
        "uid3: three"
        /* @formatter:on */
    );

    final LDAPPassThroughAuthenticationPolicyFactory factory = new LDAPPassThroughAuthenticationPolicyFactory(
        provider);
    assertTrue(factory.isConfigurationAcceptable(cfg, null));
    final AuthenticationPolicy policy = factory.createAuthenticationPolicy(cfg);
    final AuthenticationPolicyState state = policy
        .createAuthenticationPolicyState(testUser);
    assertEquals(state.getAuthenticationPolicy(), policy);

    // Perform authentication.
    assertTrue(state.passwordMatches(ByteString.valueOf(userPassword)));

    // There should be no more pending events.
    provider.assertAllExpectedEventsReceived();
    state.finalizeStateAfterBind();

    // Cached connections should be closed when the policy is finalized.
    provider.expectEvent(new CloseEvent(ceSearch));
    provider.expectEvent(new CloseEvent(ceBind));

    // Tear down and check final state.
    policy.finalizeAuthenticationPolicy();
    provider.assertAllExpectedEventsReceived();
  }



  /**
   * Tests that mapped PTA uses an appropriate filter when multiple match
   * attribute values are found.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(enabled = true)
  public void testMultipleMappingAttributeValues() throws Exception
  {
    // Mock configuration.
    final LDAPPassThroughAuthenticationPolicyCfg cfg = mockCfg()
        .withPrimaryServer(phost1)
        .withMappingPolicy(MappingPolicy.MAPPED_SEARCH)
        .withMappedAttribute("uid1").withBaseDN("o=ad");

    // Create the provider and its list of expected events.
    final GetLDAPConnectionFactoryEvent fe = new GetLDAPConnectionFactoryEvent(
        phost1, cfg);
    final MockProvider provider = new MockProvider().expectEvent(fe);

    // Add search events.
    final GetConnectionEvent ceSearch = new GetConnectionEvent(fe);
    provider
        .expectEvent(ceSearch)
        .expectEvent(
            new SimpleBindEvent(ceSearch, searchBindDNString, "searchPassword",
                ResultCode.SUCCESS))
        .expectEvent(
            new SearchEvent(ceSearch, "o=ad", SearchScope.WHOLE_SUBTREE,
                "(|(uid1=one)(uid1=two)(uid1=three))", adDNString));
    // Connection should be cached until the policy is finalized.

    // Add bind events.
    final GetConnectionEvent ceBind = new GetConnectionEvent(fe);
    provider.expectEvent(ceBind).expectEvent(
        new SimpleBindEvent(ceBind, adDNString, userPassword,
            ResultCode.SUCCESS));

    // Connection should be cached until the policy is finalized.

    // Obtain policy and state.
    final Entry testUser = TestCaseUtils.makeEntry(
        /* @formatter:off */
        "dn: " + opendjDNString,
        "objectClass: top",
        "objectClass: person",
        "sn: user",
        "cn: test user",
        "aduser: " + adDNString,
        "uid1: one",
        "uid1: two",
        "uid1: three"
        /* @formatter:on */
    );

    final LDAPPassThroughAuthenticationPolicyFactory factory = new LDAPPassThroughAuthenticationPolicyFactory(
        provider);
    assertTrue(factory.isConfigurationAcceptable(cfg, null));
    final AuthenticationPolicy policy = factory.createAuthenticationPolicy(cfg);
    final AuthenticationPolicyState state = policy
        .createAuthenticationPolicyState(testUser);
    assertEquals(state.getAuthenticationPolicy(), policy);

    // Perform authentication.
    assertTrue(state.passwordMatches(ByteString.valueOf(userPassword)));

    // There should be no more pending events.
    provider.assertAllExpectedEventsReceived();
    state.finalizeStateAfterBind();

    // Cached connections should be closed when the policy is finalized.
    provider.expectEvent(new CloseEvent(ceSearch));
    provider.expectEvent(new CloseEvent(ceBind));

    // Tear down and check final state.
    policy.finalizeAuthenticationPolicy();
    provider.assertAllExpectedEventsReceived();
  }



  /**
   * Tests that mapped PTA performs searches across multiple base DNs if
   * configured.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(enabled = true)
  public void testMultipleSearchBaseDNs() throws Exception
  {
    // Mock configuration.
    final LDAPPassThroughAuthenticationPolicyCfg cfg = mockCfg()
        .withPrimaryServer(phost1)
        .withMappingPolicy(MappingPolicy.MAPPED_SEARCH)
        .withMappedAttribute("uid").withBaseDN("o=first")
        .withBaseDN("o=second").withBaseDN("o=third");

    // Create the provider and its list of expected events.
    final GetLDAPConnectionFactoryEvent fe = new GetLDAPConnectionFactoryEvent(
        phost1, cfg);
    final MockProvider provider = new MockProvider().expectEvent(fe);

    // Add search events.
    final GetConnectionEvent ceSearch = new GetConnectionEvent(fe);
    provider
        .expectEvent(ceSearch)
        .expectEvent(
            new SimpleBindEvent(ceSearch, searchBindDNString, "searchPassword",
                ResultCode.SUCCESS))
        .expectEvent(
            new SearchEvent(ceSearch, "o=first", SearchScope.WHOLE_SUBTREE,
                "(uid=aduser)", ResultCode.CLIENT_SIDE_NO_RESULTS_RETURNED))
        .expectEvent(
            new SearchEvent(ceSearch, "o=second", SearchScope.WHOLE_SUBTREE,
                "(uid=aduser)", ResultCode.CLIENT_SIDE_NO_RESULTS_RETURNED))
        .expectEvent(
            new SearchEvent(ceSearch, "o=third", SearchScope.WHOLE_SUBTREE,
                "(uid=aduser)", adDNString));
    // Connection should be cached until the policy is finalized.

    // Add bind events.
    final GetConnectionEvent ceBind = new GetConnectionEvent(fe);
    provider.expectEvent(ceBind).expectEvent(
        new SimpleBindEvent(ceBind, adDNString, userPassword,
            ResultCode.SUCCESS));

    // Connection should be cached until the policy is finalized.

    // Obtain policy and state.
    final LDAPPassThroughAuthenticationPolicyFactory factory = new LDAPPassThroughAuthenticationPolicyFactory(
        provider);
    assertTrue(factory.isConfigurationAcceptable(cfg, null));
    final AuthenticationPolicy policy = factory.createAuthenticationPolicy(cfg);
    final AuthenticationPolicyState state = policy
        .createAuthenticationPolicyState(userEntry);
    assertEquals(state.getAuthenticationPolicy(), policy);

    // Perform authentication.
    assertTrue(state.passwordMatches(ByteString.valueOf(userPassword)));

    // There should be no more pending events.
    provider.assertAllExpectedEventsReceived();
    state.finalizeStateAfterBind();

    // Cached connections should be closed when the policy is finalized.
    provider.expectEvent(new CloseEvent(ceSearch));
    provider.expectEvent(new CloseEvent(ceBind));

    // Tear down and check final state.
    policy.finalizeAuthenticationPolicy();
    provider.assertAllExpectedEventsReceived();
  }



  /**
   * Test for issue OPENDJ-292
   * (https://bugster.forgerock.org/jira/browse/OPENDJ-292). This test checks
   * that the last exception is correctly cached in the case where initial
   * connection attempts fail.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(enabled = true)
  public void testIssueOPENDJ292_1() throws Exception
  {
    // Mock configuration.
    final LDAPPassThroughAuthenticationPolicyCfg cfg = mockCfg()
        .withPrimaryServer(phost1)
        .withSecondaryServer(shost1)
        .withMappingPolicy(MappingPolicy.MAPPED_SEARCH)
        .withMappedAttribute("uid").withBaseDN("o=ad");

    // Create all the events.
    final MockProvider provider = new MockProvider();

    // First of all the connection factories are created.
    final GetLDAPConnectionFactoryEvent fe1 = new GetLDAPConnectionFactoryEvent(
        phost1, cfg);
    final GetLDAPConnectionFactoryEvent fe2 = new GetLDAPConnectionFactoryEvent(
        shost1, cfg);
    provider.expectEvent(fe1).expectEvent(fe2);

    // Get connection for phost1, then search, then bind.
    final GetConnectionEvent ceSearch1 = new GetConnectionEvent(fe1,
        ResultCode.CLIENT_SIDE_CONNECT_ERROR);
    final GetConnectionEvent ceSearch2 = new GetConnectionEvent(fe2);

    provider
        .expectEvent(ceSearch1)
        .expectEvent(ceSearch2)
        .expectEvent(
            new SimpleBindEvent(ceSearch2, searchBindDNString,
                "searchPassword", ResultCode.INVALID_CREDENTIALS))
        .expectEvent(new CloseEvent(ceSearch2));

    // Obtain policy and state.
    final LDAPPassThroughAuthenticationPolicyFactory factory = new LDAPPassThroughAuthenticationPolicyFactory(
        provider);
    assertTrue(factory.isConfigurationAcceptable(cfg, null));
    final AuthenticationPolicy policy = factory.createAuthenticationPolicy(cfg);

    // Authenticate twice, the second time was causing a NPE because none of the
    // factories were available and an attempt was made to throw a null
    // exception.
    for (int i = 0; i < 2; i++)
    {
      final AuthenticationPolicyState state = policy
          .createAuthenticationPolicyState(userEntry);
      assertEquals(state.getAuthenticationPolicy(), policy);

      try
      {
        state.passwordMatches(ByteString.valueOf(userPassword));
        fail("password match unexpectedly succeeded");
      }
      catch (final DirectoryException e)
      {
        // No mapping attributes so this should always fail with
        // INVALID_CREDENTIALS.
        assertEquals(e.getResultCode(), ResultCode.INVALID_CREDENTIALS,
            e.getMessage());
      }
      state.finalizeStateAfterBind();
    }

    // Tear down and check final state.
    policy.finalizeAuthenticationPolicy();
    provider.assertAllExpectedEventsReceived();
  }



  /**
   * Test for issue OPENDJ-292
   * (https://bugster.forgerock.org/jira/browse/OPENDJ-292). This test checks
   * that the last exception is correctly cached in the case where a usable
   * connection fails during a search/bind.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(enabled = true)
  public void testIssueOPENDJ292_2() throws Exception
  {
    // Mock configuration.
    final LDAPPassThroughAuthenticationPolicyCfg cfg = mockCfg()
        .withPrimaryServer(phost1)
        .withSecondaryServer(shost1)
        .withMappingPolicy(MappingPolicy.MAPPED_SEARCH)
        .withMappedAttribute("uid").withBaseDN("o=ad");

    // Create all the events.
    final MockProvider provider = new MockProvider();

    // First of all the connection factories are created.
    final GetLDAPConnectionFactoryEvent fe1 = new GetLDAPConnectionFactoryEvent(
        phost1, cfg);
    final GetLDAPConnectionFactoryEvent fe2 = new GetLDAPConnectionFactoryEvent(
        shost1, cfg);
    provider.expectEvent(fe1).expectEvent(fe2);

    // Get connection for phost1, then search, then bind.
    final GetConnectionEvent ceSearch1 = new GetConnectionEvent(fe1);
    final GetConnectionEvent ceBind1 = new GetConnectionEvent(fe1);
    final GetConnectionEvent ceSearch2 = new GetConnectionEvent(fe2);

    provider
        .expectEvent(ceSearch1)
        .expectEvent(
            new SimpleBindEvent(ceSearch1, searchBindDNString, "searchPassword"))
        .expectEvent(
            new SearchEvent(ceSearch1, "o=ad", SearchScope.WHOLE_SUBTREE,
                "(uid=aduser)", adDNString))
        .expectEvent(ceBind1)
        .expectEvent(
            new SimpleBindEvent(ceBind1, adDNString, userPassword,
                ResultCode.SUCCESS));

    // Repeat and fail-over to shost1.
    provider
        .expectEvent(
            new SearchEvent(ceSearch1, "o=ad", SearchScope.WHOLE_SUBTREE,
                "(uid=aduser)", ResultCode.UNAVAILABLE))
        .expectEvent(new CloseEvent(ceSearch1))
        .expectEvent(new GetConnectionEvent(fe1, ResultCode.UNAVAILABLE)) // pool retry
        .expectEvent(ceSearch2)
        .expectEvent(
            new SimpleBindEvent(ceSearch2, searchBindDNString, "searchPassword"))
        .expectEvent(
            new SearchEvent(ceSearch2, "o=ad", SearchScope.WHOLE_SUBTREE,
                "(uid=aduser)", adDNString))
        .expectEvent(
            new SimpleBindEvent(ceBind1, adDNString, userPassword,
                ResultCode.SUCCESS));

    // Repeat, but fail on shost1 as well, leaving no available servers.
    provider
        .expectEvent(
            new SearchEvent(ceSearch2, "o=ad", SearchScope.WHOLE_SUBTREE,
                "(uid=aduser)", ResultCode.UNAVAILABLE))
        .expectEvent(new CloseEvent(ceSearch2))
        .expectEvent(new GetConnectionEvent(fe2, ResultCode.UNAVAILABLE)); // pool retry

    // Obtain policy and state.
    final LDAPPassThroughAuthenticationPolicyFactory factory = new LDAPPassThroughAuthenticationPolicyFactory(
        provider);
    assertTrue(factory.isConfigurationAcceptable(cfg, null));
    final AuthenticationPolicy policy = factory.createAuthenticationPolicy(cfg);

    // Authenticate four times, the fourth time was causing a NPE because none
    // of the factories were available and an attempt was made to throw a null
    // exception.
    for (int i = 0; i < 4; i++)
    {
      final AuthenticationPolicyState state = policy
          .createAuthenticationPolicyState(userEntry);
      assertEquals(state.getAuthenticationPolicy(), policy);

      // Perform authentication.
      if (i < 2)
      {
        assertTrue(state.passwordMatches(ByteString.valueOf(userPassword)));
      }
      else
      {
        try
        {
          state.passwordMatches(ByteString.valueOf(userPassword));
          fail("password match unexpectedly succeeded");
        }
        catch (final DirectoryException e)
        {
          // No mapping attributes so this should always fail with
          // INVALID_CREDENTIALS.
          assertEquals(e.getResultCode(), ResultCode.INVALID_CREDENTIALS,
              e.getMessage());
        }
      }
      state.finalizeStateAfterBind();
    }

    // Cached connections should be closed when the policy is finalized.
    provider.expectEvent(new CloseEvent(ceBind1));

    // Tear down and check final state.
    policy.finalizeAuthenticationPolicy();
    provider.assertAllExpectedEventsReceived();
  }



  /**
   * Test for issue OPENDJ-294
   * (https://bugster.forgerock.org/jira/browse/OPENDJ-294). Password
   * configuration changes do not seem to be taking effect.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @SuppressWarnings("unchecked")
  @Test(enabled = true)
  public void testIssueOPENDJ294() throws Exception
  {
    // Mock configurations.
    final LDAPPassThroughAuthenticationPolicyCfg cfg = mockCfg()
        .withPrimaryServer(phost1)
        .withMappingPolicy(MappingPolicy.MAPPED_SEARCH)
        .withMappedAttribute("uid").withBaseDN("o=ad");

    final LDAPPassThroughAuthenticationPolicyCfg cfg2 = mockCfg()
        .withPrimaryServer(phost1)
        .withMappingPolicy(MappingPolicy.MAPPED_SEARCH)
        .withMappedAttribute("uid").withBaseDN("o=ad")
        .withMappedSearchBindPassword("newSearchPassword");

    // Create all the events.
    final MockProvider provider = new MockProvider();

    // First of all the connection factories are created.
    final GetLDAPConnectionFactoryEvent fe1 = new GetLDAPConnectionFactoryEvent(
        phost1, cfg);
    provider.expectEvent(fe1);

    // Get connection for phost1, then search, then bind.
    final GetConnectionEvent ceSearch1 = new GetConnectionEvent(fe1);
    provider
        .expectEvent(ceSearch1)
        .expectEvent(
            new SimpleBindEvent(ceSearch1, searchBindDNString,
                "searchPassword", ResultCode.INVALID_CREDENTIALS))
        .expectEvent(new CloseEvent(ceSearch1));

    // Now simulate monitor thread run which will fail for the same reason.
    final GetConnectionEvent ceSearch2 = new GetConnectionEvent(fe1);
    provider
        .expectEvent(ceSearch2)
        .expectEvent(
            new SimpleBindEvent(ceSearch1, searchBindDNString,
                "searchPassword", ResultCode.INVALID_CREDENTIALS))
        .expectEvent(new CloseEvent(ceSearch1));

    // Change configuration, re-run monitor (none remaining), and search/bind
    // again.

    // First of all the connection factories are created.
    final GetLDAPConnectionFactoryEvent fe2 = new GetLDAPConnectionFactoryEvent(
        phost1, cfg2);
    provider.expectEvent(fe2);

    final GetConnectionEvent ceSearch3 = new GetConnectionEvent(fe2);
    final GetConnectionEvent ceBind1 = new GetConnectionEvent(fe2);
    provider
        .expectEvent(ceSearch3)
        .expectEvent(
            new SimpleBindEvent(ceSearch3, searchBindDNString,
                "newSearchPassword"))
        .expectEvent(
            new SearchEvent(ceSearch3, "o=ad", SearchScope.WHOLE_SUBTREE,
                "(uid=aduser)", adDNString))
        .expectEvent(ceBind1)
        .expectEvent(
            new SimpleBindEvent(ceBind1, adDNString, userPassword,
                ResultCode.SUCCESS));

    // Re-run monitor - again nothing to do.

    // Connections should be cached until the policy is finalized.

    // Obtain policy and state.
    final LDAPPassThroughAuthenticationPolicyFactory factory = new LDAPPassThroughAuthenticationPolicyFactory(
        provider);
    assertTrue(factory.isConfigurationAcceptable(cfg, null));
    final AuthenticationPolicy policy = factory.createAuthenticationPolicy(cfg);

    // Authenticate twice, correcting the configuration in between.
    final AuthenticationPolicyState state = policy
        .createAuthenticationPolicyState(userEntry);
    assertEquals(state.getAuthenticationPolicy(), policy);

    // Perform authentication.
    try
    {
      state.passwordMatches(ByteString.valueOf(userPassword));
      fail("password match unexpectedly succeeded");
    }
    catch (final DirectoryException e)
    {
      // No mapping attributes so this should always fail with
      // INVALID_CREDENTIALS.
      assertEquals(e.getResultCode(), ResultCode.INVALID_CREDENTIALS,
          e.getMessage());
    }
    state.finalizeStateAfterBind();

    // Run monitor which should try to connect to phost1 and fail again.
    provider.runMonitorTasks();

    // Change the configuration, fixing the password.
    ((ConfigurationChangeListener<LDAPPassThroughAuthenticationPolicyCfg>) policy)
        .applyConfigurationChange(cfg2);

    // Run monitor which should now have nothing to do.
    provider.runMonitorTasks();

    // Perform authentication, which should now succeed.
    final AuthenticationPolicyState state2 = policy
        .createAuthenticationPolicyState(userEntry);
    assertEquals(state2.getAuthenticationPolicy(), policy);
    assertTrue(state2.passwordMatches(ByteString.valueOf(userPassword)));
    state2.finalizeStateAfterBind();

    // Run monitor which should still have nothing to do.
    provider.runMonitorTasks();

    // Cached connections should be closed when the policy is finalized.
    provider.expectEvent(new CloseEvent(ceSearch3));
    provider.expectEvent(new CloseEvent(ceBind1));

    // Tear down and check final state.
    policy.finalizeAuthenticationPolicy();
    provider.assertAllExpectedEventsReceived();
  }



  /**
   * Test for issue OPENDJ-290
   * (https://bugster.forgerock.org/jira/browse/OPENDJ-290).
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(enabled = true)
  public void testIssueOPENDJ290() throws Exception
  {
    // Mock configuration.
    final LDAPPassThroughAuthenticationPolicyCfg cfg = mockCfg()
        .withPrimaryServer(phost1)
        .withMappingPolicy(MappingPolicy.MAPPED_SEARCH)
        .withMappedAttribute("uid").withBaseDN("o=ad");

    // Create all the events.
    final MockProvider provider = new MockProvider();

    // First of all the connection factories are created.
    final GetLDAPConnectionFactoryEvent fe1 = new GetLDAPConnectionFactoryEvent(
        phost1, cfg);
    provider.expectEvent(fe1);

    // Get connection then bind twice creating two pooled connections.
    final GetConnectionEvent ce1 = new GetConnectionEvent(fe1);
    final GetConnectionEvent ce2 = new GetConnectionEvent(fe1);
    provider
        .expectEvent(ce1)
        .expectEvent(
            new SimpleBindEvent(ce1, searchBindDNString, "searchPassword"))
        .expectEvent(ce2)
        .expectEvent(
            new SimpleBindEvent(ce2, searchBindDNString, "searchPassword"));

    // Once two pooled connections are created, retry but this time simulate the
    // first connection failing. The attempt should automatically retry on a new
    // connection.
    final GetConnectionEvent ce3 = new GetConnectionEvent(fe1);
    provider
        .expectEvent(
            new SimpleBindEvent(ce1, searchBindDNString, "searchPassword",
                ResultCode.UNAVAILABLE))
        .expectEvent(new CloseEvent(ce1))
        .expectEvent(ce3)
        .expectEvent(
            new SimpleBindEvent(ce3, searchBindDNString, "searchPassword"));

    // Use a connection pool directly for this test.
    ConnectionPool pool = new ConnectionPool(provider.getLDAPConnectionFactory(
        "phost1", 11, cfg));

    // Authenticate three times, the third time was failing because the pool
    // would not retry the operation on a new connection.
    ByteString username = ByteString.valueOf(searchBindDNString);
    ByteString password = ByteString.valueOf("searchPassword");

    Connection c1 = pool.getConnection();
    c1.simpleBind(username, password);

    // Don't release c1 because we want to force creation of a second connection.

    Connection c2 = pool.getConnection();
    c2.simpleBind(username, password);

    // Release both the connections.
    c1.close();
    c2.close();

    // This was failing because the pool would not retry with a new connection.
    Connection c3 = pool.getConnection();
    c3.simpleBind(username, password);
    c3.close();

    // There should be no more pending events.
    provider.assertAllExpectedEventsReceived();

    // Cached connections should be closed when the pool is closed.
    provider.expectEvent(new CloseEvent(ce2));
    provider.expectEvent(new CloseEvent(ce3));

    // Tear down and check final state.
    pool.close();
    provider.assertAllExpectedEventsReceived();
  }



  /**
   * Returns test data for {@link #testPasswordCaching}.
   *
   * @return Test data for {@link #testPasswordCaching}.
   */
  @DataProvider
  public Object[][] testPasswordCachingData()
  {
    // @formatter:off
    return new Object[][] {
        /* cacheState, matchesCache, matchesReal */
        { "notPresent",     false, false },
        { "notPresent",     false, true },
        { "notPresent",     true,  false },
        { "notPresent",     true,  true },
        { "presentValid",   false, false },
        { "presentValid",   false, true },
        { "presentValid",   true,  false },
        { "presentValid",   true,  true },
        { "presentExpired", false, false },
        { "presentExpired", false, true },
        { "presentExpired", true,  false },
        { "presentExpired", true,  true },
    };
    // @formatter:on
  }



  /**
   * Tests password caching functionality.
   *
   * @param cacheState
   *          A string indicating the state of the cached password in the user's
   *          entry.
   * @param matchesCache
   *          {@code true} if the presented password should match the cached
   *          password.
   * @param matchesReal
   *          {@code true} if the presented password should match the real
   *          password.
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(enabled = true, dataProvider = "testPasswordCachingData")
  public void testPasswordCaching(String cacheState, boolean matchesCache,
      boolean matchesReal) throws Exception
  {
    // Create an empty test backend 'o=test'
    TestCaseUtils.initializeTestBackend(true);

    // Choose arbitrary date.
    final String testCurrentTimeGMT = "20100621120000Z";

    final boolean expectPTA;
    final boolean expectCacheInfo;
    final boolean expectCacheUpdate;
    final boolean expectedBindResultIsSuccess;
    final Entry testUser;
    final String testCachedPassword = "{SSHA}N8QSu9kXHkODFxFtwtwVEqM5XMCfnSaq/5gWew==";
    final String testCachedPasswordTime;

    if (cacheState.equals("notPresent"))
    {
      expectPTA = true;
      expectCacheInfo = matchesReal;
      expectCacheUpdate = matchesReal;
      expectedBindResultIsSuccess = matchesReal;
      testCachedPasswordTime = null;

      testUser = TestCaseUtils.makeEntry(
          /* @formatter:off */
          "dn: cn=test user,o=test",
          "objectClass: top",
          "objectClass: person",
          "sn: user",
          "cn: test user"
          /* @formatter:on */
      );
    }
    else if (cacheState.equals("presentValid"))
    {
      expectPTA = !matchesCache;
      expectCacheInfo = true;
      expectCacheUpdate = !matchesCache && matchesReal;
      expectedBindResultIsSuccess = matchesCache | matchesReal;
      testCachedPasswordTime = "20100621110000Z"; // 1 hour old

      // Create an entry whose cached password is 10s old.
      testUser = TestCaseUtils.makeEntry(
          /* @formatter:off */
          "dn: cn=test user,o=test",
          "objectClass: top",
          "objectClass: person",
          "sn: user",
          "cn: test user",
          "ds-pta-cached-password: " + testCachedPassword,
          "ds-pta-cached-password-time: " + testCachedPasswordTime
          /* @formatter:on */
      );
    }
    else
    {
      // presentExpired
      expectPTA = true;
      expectCacheInfo = true;
      expectCacheUpdate = matchesReal;
      expectedBindResultIsSuccess = matchesReal;
      testCachedPasswordTime = "20100620110000Z"; // 1 day + 1 hour old

      // Create an entry whose cached password is more than 1 day old.
      testUser = TestCaseUtils.makeEntry(
          /* @formatter:off */
          "dn: cn=test user,o=test",
          "objectClass: top",
          "objectClass: person",
          "sn: user",
          "cn: test user",
          "ds-pta-cached-password: " + testCachedPassword,
          "ds-pta-cached-password-time: " + testCachedPasswordTime
          /* @formatter:on */
      );
    }

    final String presentedPassword;
    if (matchesCache)
    {
      presentedPassword = "password";
    }
    else
    {
      presentedPassword = "doesNotMatchCache";
    }

    // Add the test entry.
    TestCaseUtils.addEntry(testUser);

    // Mock configuration.
    final LDAPPassThroughAuthenticationPolicyCfg cfg = mockCfg()
        .withPrimaryServer(phost1).withUsePasswordCaching(true);

    // Create all the events.
    final GetLDAPConnectionFactoryEvent fe = new GetLDAPConnectionFactoryEvent(
        phost1, cfg);

    final MockProvider provider = new MockProvider().withCurrentTime(
        testCurrentTimeGMT).expectEvent(fe);

    if (expectPTA)
    {
      // Get connection then bind twice creating two pooled connections.
      final GetConnectionEvent ce = new GetConnectionEvent(fe);
      provider
          .expectEvent(ce)
          .expectEvent(
              new SimpleBindEvent(ce, "cn=test user,o=test", presentedPassword,
                  expectedBindResultIsSuccess ? ResultCode.SUCCESS
                      : ResultCode.INVALID_CREDENTIALS))
          .expectEvent(new CloseEvent(ce));
    }

    // Obtain policy and state.
    final LDAPPassThroughAuthenticationPolicyFactory factory = new LDAPPassThroughAuthenticationPolicyFactory(
        provider);
    assertTrue(factory.isConfigurationAcceptable(cfg, null));
    final AuthenticationPolicy policy = factory.createAuthenticationPolicy(cfg);

    // Perform the authentication.
    final AuthenticationPolicyState state = policy
        .createAuthenticationPolicyState(testUser);
    assertEquals(state.getAuthenticationPolicy(), policy);
    assertEquals(state.passwordMatches(ByteString.valueOf(presentedPassword)),
        expectedBindResultIsSuccess);
    state.finalizeStateAfterBind();

    // Check that the password has been cached if needed.
    Entry updatedTestUser = DirectoryServer.getEntry(DN
        .decode("cn=test user,o=test"));

    String newCachedPassword = updatedTestUser.getAttributeValue(
        DirectoryServer.getAttributeType("ds-pta-cached-password"),
        DirectoryStringSyntax.DECODER);

    String newCachedPasswordTime = updatedTestUser.getAttributeValue(
        DirectoryServer.getAttributeType("ds-pta-cached-password-time"),
        DirectoryStringSyntax.DECODER);

    if (expectCacheInfo)
    {
      assertNotNull(newCachedPassword);
      assertNotNull(newCachedPasswordTime);

      if (expectCacheUpdate)
      {
        assertFalse(newCachedPassword.equals(testCachedPassword));
        assertTrue(UserPasswordSyntax.isEncoded(ByteString
            .valueOf(newCachedPassword)));
        assertEquals(newCachedPasswordTime, testCurrentTimeGMT);
      }
      else
      {
        assertEquals(newCachedPassword, testCachedPassword);
        assertEquals(newCachedPasswordTime, testCachedPasswordTime);
      }
    }
    else
    {
      assertNull(newCachedPassword);
      assertNull(newCachedPasswordTime);
    }

    // Tear down and check final state.
    policy.finalizeAuthenticationPolicy();
    provider.assertAllExpectedEventsReceived();
  }



  MockPolicyCfg mockCfg()
  {
    return new MockPolicyCfg();
  }



  MockServer mockServer(final LDAPPassThroughAuthenticationPolicyCfg cfg)
      throws IOException
  {
    final ServerSocket serverSocket = TestCaseUtils.bindFreePort();
    return new MockServer(serverSocket);
  }



  BindRequestProtocolOp newBindRequest(final String dn, final String password)
      throws LDAPException
  {
    return new BindRequestProtocolOp(ByteString.valueOf(dn), 3,
        ByteString.valueOf(password));
  }



  BindResponseProtocolOp newBindResult(final ResultCode resultCode)
  {
    return new BindResponseProtocolOp(resultCode.getIntValue());
  }



  ExtendedResponseProtocolOp newDisconnectNotification(
      final ResultCode resultCode)
  {
    return new ExtendedResponseProtocolOp(resultCode.getIntValue(), null, null,
        null, OID_NOTICE_OF_DISCONNECTION, null);
  }



  SearchResultEntryProtocolOp newSearchEntry(final String dn)
      throws DirectoryException
  {
    return new SearchResultEntryProtocolOp(DN.decode(dn));
  }



  SearchRequestProtocolOp newSearchRequest(final String dn,
      final String filter, final LDAPPassThroughAuthenticationPolicyCfg cfg)
      throws LDAPException
  {
    final int timeout = (int) (cfg.getConnectionTimeout() / 1000);
    return new SearchRequestProtocolOp(ByteString.valueOf(dn),
        SearchScope.WHOLE_SUBTREE, DereferencePolicy.DEREF_ALWAYS, 1, timeout,
        true, RawFilter.create(filter),
        LDAPPassThroughAuthenticationPolicyFactory.NO_ATTRIBUTES);
  }



  SearchResultDoneProtocolOp newSearchResult(final ResultCode resultCode)
  {
    return new SearchResultDoneProtocolOp(resultCode.getIntValue());
  }
}
