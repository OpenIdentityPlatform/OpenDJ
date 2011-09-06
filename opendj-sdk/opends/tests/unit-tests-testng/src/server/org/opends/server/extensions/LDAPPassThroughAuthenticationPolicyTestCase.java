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
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.LinkedList;
import java.util.Queue;
import java.util.SortedSet;
import java.util.TreeSet;

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



    CloseEvent(GetConnectionEvent getConnectionEvent)
    {
      this.getConnectionEvent = getConnectionEvent;
    }



    /**
     * {@inheritDoc}
     */
    boolean matchesEvent(Event<?> event)
    {
      if (event instanceof CloseEvent)
      {
        CloseEvent closeEvent = (CloseEvent) event;
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
    StringBuilder toString(StringBuilder builder)
    {
      builder.append("CloseEvent(");
      builder.append(getConnectionEvent);
      builder.append(')');
      return builder;
    }

  }



  static abstract class Event<T>
  {

    public final boolean equals(Object obj)
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



    public final String toString()
    {
      StringBuilder builder = new StringBuilder();
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



    GetConnectionEvent(GetLDAPConnectionFactoryEvent fevent)
    {
      this(fevent, ResultCode.SUCCESS);
    }



    GetConnectionEvent(GetLDAPConnectionFactoryEvent fevent,
        ResultCode resultCode)
    {
      this.fevent = fevent;
      this.resultCode = resultCode;
    }



    /**
     * {@inheritDoc}
     */
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
    boolean matchesEvent(Event<?> event)
    {
      if (event instanceof GetConnectionEvent)
      {
        GetConnectionEvent getConnectionEvent = (GetConnectionEvent) event;
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
    StringBuilder toString(StringBuilder builder)
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



    GetLDAPConnectionFactoryEvent(String hostPort,
        LDAPPassThroughAuthenticationPolicyCfg options)
    {
      this.hostPort = hostPort;
      this.options = options;
    }



    /**
     * {@inheritDoc}
     */
    boolean matchesEvent(Event<?> event)
    {
      if (event instanceof GetLDAPConnectionFactoryEvent)
      {
        GetLDAPConnectionFactoryEvent providerEvent = (GetLDAPConnectionFactoryEvent) event;

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
    StringBuilder toString(StringBuilder builder)
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



    MockConnection(MockProvider mockProvider,
        GetConnectionEvent getConnectionEvent)
    {
      this.mockProvider = mockProvider;
      this.getConnectionEvent = getConnectionEvent;
    }



    /**
     * {@inheritDoc}
     */
    public void close()
    {
      CloseEvent event = new CloseEvent(getConnectionEvent);
      mockProvider.assertExpectedEventWasReceived(event);
    }



    /**
     * {@inheritDoc}
     */
    public ByteString search(DN baseDN, SearchScope scope, SearchFilter filter)
        throws DirectoryException
    {
      SearchEvent event = new SearchEvent(getConnectionEvent,
          baseDN.toString(), scope, filter.toString());
      Object result = mockProvider.assertExpectedEventWasReceived(event);
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
    public void simpleBind(ByteString username, ByteString password)
        throws DirectoryException
    {
      SimpleBindEvent event = new SimpleBindEvent(getConnectionEvent,
          username.toString(), password.toString());
      DirectoryException e = mockProvider.assertExpectedEventWasReceived(event);
      if (e != null) throw e;
    }

  }



  static final class MockFactory implements
      LDAPPassThroughAuthenticationPolicyFactory.ConnectionFactory
  {
    private final GetLDAPConnectionFactoryEvent getLDAPConnectionFactoryEvent;
    private final MockProvider mockProvider;



    MockFactory(MockProvider mockProvider,
        GetLDAPConnectionFactoryEvent providerEvent)
    {
      this.mockProvider = mockProvider;
      this.getLDAPConnectionFactoryEvent = providerEvent;
    }



    /**
     * {@inheritDoc}
     */
    public Connection getConnection() throws DirectoryException
    {
      GetConnectionEvent event = new GetConnectionEvent(
          getLDAPConnectionFactoryEvent);

      DirectoryException e = mockProvider.assertExpectedEventWasReceived(event);
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



    public void addChangeListener(
        ConfigurationChangeListener<AuthenticationPolicyCfg> listener)
    {
      // Do nothing.
    }



    public void addLDAPPassThroughChangeListener(
        ConfigurationChangeListener<LDAPPassThroughAuthenticationPolicyCfg> listener)
    {
      // Do nothing.
    }



    public Class<? extends LDAPPassThroughAuthenticationPolicyCfg> configurationClass()
    {
      return LDAPPassThroughAuthenticationPolicyCfg.class;
    }



    public DN dn()
    {
      return policyDN;
    }



    public long getConnectionTimeout()
    {
      return 3000;
    }



    public String getJavaClass()
    {
      return LDAPPassThroughAuthenticationPolicyFactory.class.getName();
    }



    public SortedSet<AttributeType> getMappedAttribute()
    {
      return mappedAttributes;
    }



    public SortedSet<DN> getMappedSearchBaseDN()
    {
      return baseDNs;
    }



    public DN getMappedSearchBindDN()
    {
      return searchBindDN;
    }



    public String getMappedSearchBindPassword()
    {
      return "searchPassword";
    }



    public MappingPolicy getMappingPolicy()
    {
      return mappingPolicy;
    }



    public SortedSet<String> getPrimaryRemoteLDAPServer()
    {
      return primaryServers;
    }



    public SortedSet<String> getSecondaryRemoteLDAPServer()
    {
      return secondaryServers;
    }



    public SortedSet<String> getSSLCipherSuite()
    {
      return new TreeSet<String>();
    }



    public SortedSet<String> getSSLProtocol()
    {
      return new TreeSet<String>();
    }



    public String getTrustManagerProvider()
    {
      return "ignored";
    }



    public DN getTrustManagerProviderDN()
    {
      return trustManagerDN;
    }



    public boolean isUseSSL()
    {
      return false;
    }



    public boolean isUseTCPKeepAlive()
    {
      return false;
    }



    public boolean isUseTCPNoDelay()
    {
      return false;
    }



    public void removeChangeListener(
        ConfigurationChangeListener<AuthenticationPolicyCfg> listener)
    {
      // Do nothing.
    }



    public void removeLDAPPassThroughChangeListener(
        ConfigurationChangeListener<LDAPPassThroughAuthenticationPolicyCfg> listener)
    {
      // Do nothing.
    }



    MockPolicyCfg withBaseDN(String baseDN)
    {
      try
      {
        baseDNs.add(DN.decode(baseDN));
      }
      catch (DirectoryException e)
      {
        throw new RuntimeException(e);
      }
      return this;
    }



    MockPolicyCfg withMappedAttribute(String atype)
    {
      mappedAttributes.add(DirectoryServer.getAttributeType(
          StaticUtils.toLowerCase(atype), true));
      return this;
    }



    MockPolicyCfg withMappingPolicy(MappingPolicy policy)
    {
      this.mappingPolicy = policy;
      return this;
    }



    MockPolicyCfg withPrimaryServer(String hostPort)
    {
      primaryServers.add(hostPort);
      return this;
    }



    MockPolicyCfg withSecondaryServer(String hostPort)
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
    public ConnectionFactory getLDAPConnectionFactory(String host, int port,
        LDAPPassThroughAuthenticationPolicyCfg options)
    {
      GetLDAPConnectionFactoryEvent event = new GetLDAPConnectionFactoryEvent(
          host + ":" + port, options);
      assertExpectedEventWasReceived(event);
      return new MockFactory(this, event);
    }



    @SuppressWarnings("unchecked")
    <T> T assertExpectedEventWasReceived(Event<T> actualEvent)
    {
      Event<?> expectedEvent = expectedEvents.poll();
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



    MockProvider expectEvent(Event<?> expectedEvent)
    {
      expectedEvents.add(expectedEvent);
      return this;
    }



    void assertAllExpectedEventsReceived()
    {
      assertTrue(expectedEvents.isEmpty());
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



    SearchEvent(GetConnectionEvent cevent, String baseDN, SearchScope scope,
        String filter)
    {
      this(cevent, baseDN, scope, filter, null, ResultCode.SUCCESS);
    }



    SearchEvent(GetConnectionEvent cevent, String baseDN, SearchScope scope,
        String filter, String username)
    {
      this(cevent, baseDN, scope, filter, username, ResultCode.SUCCESS);
    }



    SearchEvent(GetConnectionEvent cevent, String baseDN, SearchScope scope,
        String filter, ResultCode resultCode)
    {
      this(cevent, baseDN, scope, filter, null, resultCode);
    }



    private SearchEvent(GetConnectionEvent cevent, String baseDN,
        SearchScope scope, String filter, String username, ResultCode resultCode)
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
    Object getResult()
    {
      return resultCode == ResultCode.SUCCESS ? username
          : new DirectoryException(resultCode, resultCode.getResultCodeName());
    }



    /**
     * {@inheritDoc}
     */
    boolean matchesEvent(Event<?> event)
    {
      if (event instanceof SearchEvent)
      {
        SearchEvent searchEvent = (SearchEvent) event;
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
    StringBuilder toString(StringBuilder builder)
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



    SimpleBindEvent(GetConnectionEvent cevent, String username, String password)
    {
      this(cevent, username, password, ResultCode.SUCCESS);
    }



    SimpleBindEvent(GetConnectionEvent cevent, String username,
        String password, ResultCode resultCode)
    {
      this.cevent = cevent;
      this.username = username;
      this.password = password;
      this.resultCode = resultCode;
    }



    /**
     * {@inheritDoc}
     */
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
    boolean matchesEvent(Event<?> event)
    {
      if (event instanceof SimpleBindEvent)
      {
        SimpleBindEvent simpleBindEvent = (SimpleBindEvent) event;
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
    StringBuilder toString(StringBuilder builder)
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
   * Tests that failures to obtain a search connection are handled properly.
   *
   * @param connectResultCode
   *          The connection failure result code.
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(enabled = true, dataProvider = "testConnectionFailureDuringSearchGetConnectionData")
  public void testConnectionFailureDuringSearchGetConnection(
      ResultCode connectResultCode) throws Exception
  {
    // Mock configuration.
    LDAPPassThroughAuthenticationPolicyCfg cfg = mockCfg()
        .withPrimaryServer(phost1)
        .withMappingPolicy(MappingPolicy.MAPPED_SEARCH)
        .withMappedAttribute("uid").withBaseDN("o=ad");

    // Create the provider and its list of expected events.
    GetLDAPConnectionFactoryEvent fe = new GetLDAPConnectionFactoryEvent(
        phost1, cfg);
    GetConnectionEvent ceSearch = new GetConnectionEvent(fe, connectResultCode);

    MockProvider provider = new MockProvider().expectEvent(fe).expectEvent(
        ceSearch);

    // Obtain policy and state.
    LDAPPassThroughAuthenticationPolicyFactory factory = new LDAPPassThroughAuthenticationPolicyFactory(
        provider);
    assertTrue(factory.isConfigurationAcceptable(cfg, null));
    AuthenticationPolicy policy = factory.createAuthenticationPolicy(cfg);
    AuthenticationPolicyState state = policy
        .createAuthenticationPolicyState(userEntry);
    assertEquals(state.getAuthenticationPolicy(), policy);

    // Perform authentication.
    try
    {
      state.passwordMatches(ByteString.valueOf(userPassword));
      fail("password match unexpectedly succeeded");
    }
    catch (DirectoryException e)
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
  public void testConnectionFailureDuringSearchBind(ResultCode bindResultCode)
      throws Exception
  {
    // Mock configuration.
    LDAPPassThroughAuthenticationPolicyCfg cfg = mockCfg()
        .withPrimaryServer(phost1)
        .withMappingPolicy(MappingPolicy.MAPPED_SEARCH)
        .withMappedAttribute("uid").withBaseDN("o=ad");

    // Create the provider and its list of expected events.
    GetLDAPConnectionFactoryEvent fe = new GetLDAPConnectionFactoryEvent(
        phost1, cfg);
    GetConnectionEvent ceSearch = new GetConnectionEvent(fe);

    MockProvider provider = new MockProvider()
        .expectEvent(fe)
        .expectEvent(ceSearch)
        .expectEvent(
            new SimpleBindEvent(ceSearch, searchBindDNString, "searchPassword",
                bindResultCode)).expectEvent(new CloseEvent(ceSearch));

    // Obtain policy and state.
    LDAPPassThroughAuthenticationPolicyFactory factory = new LDAPPassThroughAuthenticationPolicyFactory(
        provider);
    assertTrue(factory.isConfigurationAcceptable(cfg, null));
    AuthenticationPolicy policy = factory.createAuthenticationPolicy(cfg);
    AuthenticationPolicyState state = policy
        .createAuthenticationPolicyState(userEntry);
    assertEquals(state.getAuthenticationPolicy(), policy);

    // Perform authentication.
    try
    {
      state.passwordMatches(ByteString.valueOf(userPassword));
      fail("password match unexpectedly succeeded");
    }
    catch (DirectoryException e)
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
  public void testConnectionFailureDuringSearch(ResultCode searchResultCode)
      throws Exception
  {
    // Mock configuration.
    LDAPPassThroughAuthenticationPolicyCfg cfg = mockCfg()
        .withPrimaryServer(phost1)
        .withMappingPolicy(MappingPolicy.MAPPED_SEARCH)
        .withMappedAttribute("uid").withBaseDN("o=ad");

    // Create the provider and its list of expected events.
    GetLDAPConnectionFactoryEvent fe = new GetLDAPConnectionFactoryEvent(
        phost1, cfg);
    GetConnectionEvent ceSearch = new GetConnectionEvent(fe);

    MockProvider provider = new MockProvider()
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
    LDAPPassThroughAuthenticationPolicyFactory factory = new LDAPPassThroughAuthenticationPolicyFactory(
        provider);
    assertTrue(factory.isConfigurationAcceptable(cfg, null));
    AuthenticationPolicy policy = factory.createAuthenticationPolicy(cfg);
    AuthenticationPolicyState state = policy
        .createAuthenticationPolicyState(userEntry);
    assertEquals(state.getAuthenticationPolicy(), policy);

    // Perform authentication.
    try
    {
      state.passwordMatches(ByteString.valueOf(userPassword));
      fail("password match unexpectedly succeeded");
    }
    catch (DirectoryException e)
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
  public void testMappingPolicyAuthentication(MappingPolicy mappingPolicy,
      ResultCode bindResultCode) throws Exception
  {
    // Mock configuration.
    LDAPPassThroughAuthenticationPolicyCfg cfg = mockCfg()
        .withPrimaryServer(phost1)
        .withMappingPolicy(mappingPolicy)
        .withMappedAttribute(
            mappingPolicy == MappingPolicy.MAPPED_BIND ? "aduser" : "uid")
        .withBaseDN("o=ad");

    // Create the provider and its list of expected events.
    GetLDAPConnectionFactoryEvent fe = new GetLDAPConnectionFactoryEvent(
        phost1, cfg);
    MockProvider provider = new MockProvider().expectEvent(fe);

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
    GetConnectionEvent ceBind = new GetConnectionEvent(fe);
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
    LDAPPassThroughAuthenticationPolicyFactory factory = new LDAPPassThroughAuthenticationPolicyFactory(
        provider);
    assertTrue(factory.isConfigurationAcceptable(cfg, null));
    AuthenticationPolicy policy = factory.createAuthenticationPolicy(cfg);
    AuthenticationPolicyState state = policy
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
      catch (DirectoryException e)
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



  private MockPolicyCfg mockCfg()
  {
    return new MockPolicyCfg();
  }
}
