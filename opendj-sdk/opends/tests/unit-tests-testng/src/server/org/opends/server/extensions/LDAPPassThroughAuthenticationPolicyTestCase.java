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



import static org.opends.server.extensions.LDAPPassThroughAuthenticationPolicyFactory.isFatalResultCode;
import static org.testng.Assert.*;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
import org.opends.server.extensions.LDAPPassThroughAuthenticationPolicyFactory.LDAPConnectionFactory;
import org.opends.server.protocols.asn1.ASN1;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.ProtocolOp;
import org.opends.server.protocols.ldap.UnbindRequestProtocolOp;
import org.opends.server.tools.LDAPReader;
import org.opends.server.tools.LDAPWriter;
import org.opends.server.types.*;
import org.opends.server.util.StaticUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;



/**
 * Test LDAP authentication mappingPolicy implementation.
 */
public class LDAPPassThroughAuthenticationPolicyTestCase extends
    ExtensionsTestCase
{

  // TODO: multiple search base DNs
  // TODO: multiple mapping attributes/values
  // TODO: searches returning no results or too many
  // TODO: missing mapping attributes
  // TODO: connection pooling
  // TODO: load balancing
  // TODO: fail-over

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
      builder.append(", ");
      builder.append(options);
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
    private int timeoutMS = 3000;



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
      return searchBindDN;
    }



    @Override
    public String getMappedSearchBindPassword()
    {
      return "searchPassword";
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
  }



  static final class MockProvider implements
      LDAPPassThroughAuthenticationPolicyFactory.LDAPConnectionFactoryProvider
  {

    private final Queue<Event<?>> expectedEvents = new LinkedList<Event<?>>();



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



    MockServer thenAccept()
    {
      actions.add(new AcceptAction());
      return this;
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



    MockServer thenBlock()
    {
      BlockAction action = new BlockAction();
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
      BlockAction action = blockers.poll();
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
  private final String shost2 = "shost2:22";

  private final String shost3 = "shost3:33";
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
    if (isFatalResultCode(searchResultCode))
    {
      // The connection will fail and be closed immediately.
      provider.expectEvent(new CloseEvent(ceSearch));
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
      assertEquals(e.getResultCode(), ResultCode.INVALID_CREDENTIALS);
    }

    // There should be no more pending events.
    provider.assertAllExpectedEventsReceived();
    state.finalizeStateAfterBind();

    // Cached connections should be closed when the policy is finalized.
    if (!isFatalResultCode(searchResultCode))
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
      assertEquals(e.getResultCode(), ResultCode.INVALID_CREDENTIALS);
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
      assertEquals(e.getResultCode(), ResultCode.INVALID_CREDENTIALS);
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
   * Checks connect and immediately unbind work ok.
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
      Connection connection = factory.getConnection();
      connection.close();
    }
    finally
    {
      server.stop();
    }
  }



  /**
   * Checks that client side IO timeouts are enforced.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(enabled = true)
  public void testLDAPConnectionFactoryConnectTimeout() throws Exception
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
    catch (DirectoryException e)
    {
      if (e.getResultCode() != ResultCode.CLIENT_SIDE_TIMEOUT)
      {
        throw e;
      }
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
    if (isFatalResultCode(bindResultCode))
    {
      // The connection will fail and be closed immediately.
      provider.expectEvent(new CloseEvent(ceBind));
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
        assertEquals(e.getResultCode(), ResultCode.INVALID_CREDENTIALS);
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
    if (!isFatalResultCode(bindResultCode))
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
}
