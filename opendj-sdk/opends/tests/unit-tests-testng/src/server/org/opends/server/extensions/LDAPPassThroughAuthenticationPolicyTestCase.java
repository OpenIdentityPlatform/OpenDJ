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
      mockProvider.assertNextEventExpected(event);
    }



    /**
     * {@inheritDoc}
     */
    public ByteString search(DN baseDN, SearchScope scope, SearchFilter filter)
        throws DirectoryException
    {
      SearchEvent event = new SearchEvent(getConnectionEvent, baseDN, scope,
          filter);
      Object result = mockProvider.assertNextEventExpected(event);
      if (result instanceof ByteString)
      {
        return (ByteString) result;
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
      DirectoryException e = mockProvider.assertNextEventExpected(event);
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

      DirectoryException e = mockProvider.assertNextEventExpected(event);
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
      assertNextEventExpected(event);
      return new MockFactory(this, event);
    }



    @SuppressWarnings("unchecked")
    <T> T assertNextEventExpected(Event<T> actualEvent)
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



    MockProvider withExpectedEvent(Event<?> expectedEvent)
    {
      expectedEvents.add(expectedEvent);
      return this;
    }
  }



  static class SearchEvent extends Event<Object>
  {
    private final DN baseDN;
    private final GetConnectionEvent cevent;
    private final SearchFilter filter;
    private final ResultCode resultCode;
    private final SearchScope scope;
    private final ByteString username;



    SearchEvent(GetConnectionEvent cevent, DN baseDN, SearchScope scope,
        SearchFilter filter)
    {
      this(cevent, baseDN, scope, filter, null, ResultCode.SUCCESS);
    }



    SearchEvent(GetConnectionEvent cevent, DN baseDN, SearchScope scope,
        SearchFilter filter, ByteString username)
    {
      this(cevent, baseDN, scope, filter, username, ResultCode.SUCCESS);
    }



    SearchEvent(GetConnectionEvent cevent, DN baseDN, SearchScope scope,
        SearchFilter filter, ResultCode resultCode)
    {
      this(cevent, baseDN, scope, filter, null, resultCode);
    }



    private SearchEvent(GetConnectionEvent cevent, DN baseDN,
        SearchScope scope, SearchFilter filter, ByteString username,
        ResultCode resultCode)
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
  private final String policyDNString = "cn=test mappingPolicy,o=test";

  private DN searchBindDN;
  private final String searchBindDNString = "cn=search bind dn";

  private DN trustManagerDN;
  private final String trustManagerDNString = "cn=ignored";

  private final String adDNString = "cn=ad user,o=ad";
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
   * Tests that initial connection errors are handled properly.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(enabled = false)
  public void testInitialConnectionFailure() throws Exception
  {
    LDAPPassThroughAuthenticationPolicyCfg cfg = mockCfg().withPrimaryServer(
        phost1);

    GetLDAPConnectionFactoryEvent fe = new GetLDAPConnectionFactoryEvent(
        phost1, cfg);
    GetConnectionEvent ce = new GetConnectionEvent(fe, ResultCode.UNAVAILABLE);

    MockProvider provider = new MockProvider().withExpectedEvent(fe)
        .withExpectedEvent(ce);

    LDAPPassThroughAuthenticationPolicyFactory factory = new LDAPPassThroughAuthenticationPolicyFactory(
        provider);

    assertTrue(factory.isConfigurationAcceptable(cfg, null));

    AuthenticationPolicy policy = factory.createAuthenticationPolicy(cfg);
    AuthenticationPolicyState state = policy
        .createAuthenticationPolicyState(userEntry);

    assertEquals(state.getAuthenticationPolicy(), policy);
    try
    {
      state.passwordMatches(ByteString.valueOf(userPassword));
      fail("password match unexpectedly succeeded");
    }
    catch (DirectoryException e)
    {
      assertEquals(e.getResultCode(), ResultCode.UNAVAILABLE);
    }

    state.finalizeStateAfterBind();
    policy.finalizeAuthenticationPolicy();
  }



  /**
   * Tests the unmapped policy where the connection fails during bind.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(enabled = false)
  public void testUnmappedPolicyConnectionFailure() throws Exception
  {
    LDAPPassThroughAuthenticationPolicyCfg cfg = mockCfg().withPrimaryServer(
        phost1);

    GetLDAPConnectionFactoryEvent fe = new GetLDAPConnectionFactoryEvent(
        phost1, cfg);
    GetConnectionEvent ce = new GetConnectionEvent(fe);

    MockProvider provider = new MockProvider()
        .withExpectedEvent(fe)
        .withExpectedEvent(ce)
        .withExpectedEvent(
            new SimpleBindEvent(ce, opendjDNString, userPassword,
                ResultCode.UNAVAILABLE)).withExpectedEvent(new CloseEvent(ce));

    LDAPPassThroughAuthenticationPolicyFactory factory = new LDAPPassThroughAuthenticationPolicyFactory(
        provider);

    assertTrue(factory.isConfigurationAcceptable(cfg, null));

    AuthenticationPolicy policy = factory.createAuthenticationPolicy(cfg);
    AuthenticationPolicyState state = policy
        .createAuthenticationPolicyState(userEntry);

    assertEquals(state.getAuthenticationPolicy(), policy);
    try
    {
      state.passwordMatches(ByteString.valueOf(userPassword));
      fail("password match unexpectedly succeeded");
    }
    catch (DirectoryException e)
    {
      assertEquals(e.getResultCode(), ResultCode.UNAVAILABLE);
    }

    state.finalizeStateAfterBind();
    policy.finalizeAuthenticationPolicy();
  }



  /**
   * Tests the unmapped policy with invalid credentials.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(enabled = false)
  public void testUnmappedPolicyInvalidCredentials() throws Exception
  {
    LDAPPassThroughAuthenticationPolicyCfg cfg = mockCfg().withPrimaryServer(
        phost1);

    GetLDAPConnectionFactoryEvent fe = new GetLDAPConnectionFactoryEvent(
        phost1, cfg);
    GetConnectionEvent ce = new GetConnectionEvent(fe);

    MockProvider provider = new MockProvider()
        .withExpectedEvent(fe)
        .withExpectedEvent(ce)
        .withExpectedEvent(
            new SimpleBindEvent(ce, opendjDNString, userPassword,
                ResultCode.INVALID_CREDENTIALS))
        .withExpectedEvent(new CloseEvent(ce));

    LDAPPassThroughAuthenticationPolicyFactory factory = new LDAPPassThroughAuthenticationPolicyFactory(
        provider);

    assertTrue(factory.isConfigurationAcceptable(cfg, null));

    AuthenticationPolicy policy = factory.createAuthenticationPolicy(cfg);
    AuthenticationPolicyState state = policy
        .createAuthenticationPolicyState(userEntry);

    assertEquals(state.getAuthenticationPolicy(), policy);
    assertFalse(state.passwordMatches(ByteString.valueOf(userPassword)));

    state.finalizeStateAfterBind();
    policy.finalizeAuthenticationPolicy();
  }



  /**
   * Tests the unmapped policy with valid credentials.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(enabled = false)
  public void testUnmappedPolicyValidCredentials() throws Exception
  {
    LDAPPassThroughAuthenticationPolicyCfg cfg = mockCfg().withPrimaryServer(
        phost1);

    GetLDAPConnectionFactoryEvent fe = new GetLDAPConnectionFactoryEvent(
        phost1, cfg);
    GetConnectionEvent ce = new GetConnectionEvent(fe);

    MockProvider provider = new MockProvider()
        .withExpectedEvent(fe)
        .withExpectedEvent(ce)
        .withExpectedEvent(
            new SimpleBindEvent(ce, opendjDNString, userPassword))
        .withExpectedEvent(new CloseEvent(ce));

    LDAPPassThroughAuthenticationPolicyFactory factory = new LDAPPassThroughAuthenticationPolicyFactory(
        provider);

    assertTrue(factory.isConfigurationAcceptable(cfg, null));

    AuthenticationPolicy policy = factory.createAuthenticationPolicy(cfg);
    AuthenticationPolicyState state = policy
        .createAuthenticationPolicyState(userEntry);

    assertEquals(state.getAuthenticationPolicy(), policy);
    assertTrue(state.passwordMatches(ByteString.valueOf(userPassword)));

    state.finalizeStateAfterBind();
    policy.finalizeAuthenticationPolicy();
  }



  private MockPolicyCfg mockCfg()
  {
    return new MockPolicyCfg();
  }
}
