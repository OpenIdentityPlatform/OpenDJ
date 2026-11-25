/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2013-2016 ForgeRock AS.
 * Portions Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.loggers;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.opends.server.util.CollectionUtils.*;

import java.util.TreeSet;

import org.forgerock.opendj.ldap.AddressMask;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.TestTextWriter;
import org.forgerock.opendj.server.config.meta.AccessLogFilteringCriteriaCfgDefn.LogRecordType;
import org.forgerock.opendj.server.config.server.AccessLogFilteringCriteriaCfg;
import org.opends.server.api.ClientConnection;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.SearchOperation;
import org.opends.server.loggers.AbstractTextAccessLogPublisher.CriteriaFilter;
import org.opends.server.loggers.AbstractTextAccessLogPublisher.RootFilter;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.Operation;
import org.opends.server.types.OperationType;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class AbstractTextAccessLogPublisherTest extends DirectoryServerTestCase
{

  @AfterClass
  public void afterClass() throws Exception
  {
    // Make sure group is removed from group manager.
    TestCaseUtils.deleteEntry(dn("cn=group 1,ou=Groups,o=test"));
    TestCaseUtils.clearDataBackends();
  }



  @BeforeClass
  public void beforeClass() throws Exception
  {
    TestCaseUtils.startServer();
    TestCaseUtils.clearDataBackends();
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntries(
        // @formatter:off
        "dn: ou=People,o=test",
        "objectClass: top",
        "objectClass: organizationalUnit",
        "ou: People",
        "",
        "dn: ou=Groups,o=test",
        "objectClass: top",
        "objectClass: organizationalUnit",
        "ou: Groups",
        "",
        "dn: cn=group 1,ou=Groups,o=test",
        "objectClass: top",
        "objectClass: groupOfNames",
        "cn: group 1",
        "member: uid=user.1,ou=People,o=test",
        "",
        "dn: uid=user.1,ou=People,o=test",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: user.1",
        "givenName: User",
        "sn: 1",
        "cn: User 1",
        "userPassword: password",
        "",
        "dn: uid=user.2,ou=People,o=test",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: user.2",
        "givenName: User",
        "sn: 2",
        "cn: User 2",
        "userPassword: password");
        // @formatter:on
  }



  @DataProvider(name = "isLoggableData")
  private Object[][] getIsLoggableData()
  {
    // When suppress is set to true and the corresponding operation is set to
    // true too, then the operation is not loggable.
    // You can read the array like this: read two by two from line start, if
    // both are true in a pair, then the expected result is false (not
    // loggable).
    // There is just one exception: when the operation is a synchronization
    // operation and we do not suppress synchronization operation, then we
    // return true regardless of whether this is an internal operation
    // @formatter:off
    return new Object[][] {
      { true, true, true, true, false },
      { true, true, true, false, false },
      { true, true, false, true, false },
      { true, true, false, false, false },
      { true, false, true, true, false },
      { true, false, true, false, true },
      { true, false, false, true, true },
      { true, false, false, false, true },
      { false, true, true, true, true },
      { false, true, true, false, true },
      { false, true, false, true, true },
      { false, true, false, false, true },
      { false, false, true, true, false },
      { false, false, true, false, true },
      { false, false, false, true, true },
      { false, false, false, false, true }, };
    // @formatter:on
  }



  @Test(dataProvider = "isLoggableData")
  public void rootFilterIsLoggable(final boolean suppressSynchronization,
      final boolean isSynchronizationOp, final boolean suppressInternal,
      final boolean isInternalOp, final boolean expectedTestResult)
  {
    final Operation operation = mock(Operation.class);
    when(operation.isSynchronizationOperation())
        .thenReturn(isSynchronizationOp);
    when(operation.isInnerOperation()).thenReturn(isInternalOp);

    final RootFilter filter = new RootFilter(suppressInternal,
        suppressSynchronization, null, null);
    assertThat(filter.isLoggable(operation)).isEqualTo(expectedTestResult);
  }



  @Test
  public void proxyRecordsKeepOriginalRequestFilterDecision() throws Exception
  {
    assertProxyRequestFilterDecision("dc=com", true);
    assertProxyRequestFilterDecision("ou=child,dc=com", false);
  }

  @Test
  public void deferredProxyFailuresKeepTheirOwnResults() throws Exception
  {
    org.opends.server.protocols.internal.InternalClientConnection client =
        new org.opends.server.protocols.internal.InternalClientConnection(new AuthenticationInfo());
    org.opends.server.core.SearchOperationBasis operation = new org.opends.server.core.SearchOperationBasis(
        client, 1, 1, java.util.Collections.emptyList(), dn("dc=com"),
        org.forgerock.opendj.ldap.SearchScope.WHOLE_SUBTREE,
        org.forgerock.opendj.ldap.DereferenceAliasesPolicy.NEVER, 0, 0, false,
        org.opends.server.types.SearchFilter.createFilterFromString("(objectClass=*)"),
        java.util.Collections.emptySet());
    TestCaseUtils.ACCESS_TEXT_WRITER.clear();
    operation.setResultCode(ResultCode.BUSY);
    AccessLogger.deferProxyResponse(operation, 1001, null, null, null, null, null, 0, null, 1);
    operation.setResultCode(ResultCode.UNAVAILABLE);
    AccessLogger.deferProxyResponse(operation, 1002, null, null, null, null, null, 0, null, 2);
    operation.setResultCode(ResultCode.OPERATIONS_ERROR);
    AccessLogger.logProxyResponses(operation);
    assertThat(TestCaseUtils.ACCESS_TEXT_WRITER.getMessages()).anySatisfy(line ->
        assertThat(line).contains("proxyOp=1001", "outcome=no-response", " result=51"));
    assertThat(TestCaseUtils.ACCESS_TEXT_WRITER.getMessages()).anySatisfy(line ->
        assertThat(line).contains("proxyOp=1002", "outcome=no-response", " result=52"));
  }

  @Test
  public void combinedBindFilteringUsesTheFinalIdentity() throws Exception
  {
    AccessLogFilteringCriteriaCfg cfg = mockCriteriaFilterCfg();
    when(cfg.getUserDNEqualTo()).thenReturn(newTreeSet("uid=user.1,ou=People,o=test"));
    TestTextWriter writer = new TestTextWriter();
    TextAccessLogPublisher publisher = TextAccessLogPublisher.getStartupTextAccessPublisher(writer, false);
    java.lang.reflect.Field filterField = AbstractTextAccessLogPublisher.class.getDeclaredField("filter");
    filterField.setAccessible(true);
    filterField.set(publisher, new RootFilter(false, false,
        org.forgerock.opendj.server.config.meta.AccessLogPublisherCfgDefn.FilteringPolicy.INCLUSIVE,
        new CriteriaFilter(cfg)));
    java.lang.reflect.Field combined = TextAccessLogPublisher.class.getDeclaredField("isCombinedMode");
    combined.setAccessible(true);
    combined.setBoolean(publisher, true);
    org.opends.server.protocols.internal.InternalClientConnection client =
        new org.opends.server.protocols.internal.InternalClientConnection(new AuthenticationInfo());
    org.opends.server.core.BindOperationBasis operation = new org.opends.server.core.BindOperationBasis(
        client, 1, 1, java.util.Collections.emptyList(), "3",
        org.forgerock.opendj.ldap.ByteString.valueOfUtf8("uid=user.1,ou=People,o=test"),
        org.forgerock.opendj.ldap.ByteString.valueOfUtf8("password"));
    operation.setResultCode(ResultCode.SUCCESS);
    publisher.logBindRequest(operation);
    publisher.logProxyRequest(operation, 1, "client", "test", "uid=user.1,dc=remote", null, null);
    assertThat(writer.getMessages()).isEmpty();
    client.setAuthenticationInfo(AuthenticationInfo.internal(new org.opends.server.types.Entry(
        dn("uid=user.1,ou=People,o=test"), null, null, null)));
    publisher.logProxyResponse(operation, 1, "localhost:389", "localhost:389", ResultCode.SUCCESS,
        "uid=user.1,dc=remote", null, -1, null, 1, null);
    publisher.logBindResponse(operation);
    assertThat(writer.getMessages()).hasSize(3);
    assertThat(writer.getMessages().get(0)).contains("BIND PROXY_REQ");
    assertThat(writer.getMessages().get(1)).contains("BIND PROXY_RES");
    assertThat(writer.getMessages().get(2)).contains("BIND conn=");
  }

  @Test
  public void proxyBindFilteringKeepsTheIdentityFromTheClientRequest() throws Exception
  {
    AccessLogFilteringCriteriaCfg cfg = mockCriteriaFilterCfg();
    when(cfg.getUserDNEqualTo()).thenReturn(newTreeSet("uid=user.1,ou=People,o=test"));
    TestTextWriter writer = new TestTextWriter();
    TextAccessLogPublisher publisher = TextAccessLogPublisher.getStartupTextAccessPublisher(writer, false);
    java.lang.reflect.Field filterField = AbstractTextAccessLogPublisher.class.getDeclaredField("filter");
    filterField.setAccessible(true);
    filterField.set(publisher, new RootFilter(false, false,
        org.forgerock.opendj.server.config.meta.AccessLogPublisherCfgDefn.FilteringPolicy.INCLUSIVE,
        new CriteriaFilter(cfg)));
    org.opends.server.protocols.internal.InternalClientConnection client =
        new org.opends.server.protocols.internal.InternalClientConnection(AuthenticationInfo.internal(
            new org.opends.server.types.Entry(dn("uid=user.1,ou=People,o=test"), null, null, null)));
    org.opends.server.core.BindOperationBasis operation = new org.opends.server.core.BindOperationBasis(
        client, 1, 1, java.util.Collections.emptyList(), "3",
        org.forgerock.opendj.ldap.ByteString.valueOfUtf8("uid=user.2,ou=People,o=test"),
        org.forgerock.opendj.ldap.ByteString.valueOfUtf8("password"));
    operation.setResultCode(ResultCode.SUCCESS);
    publisher.logBindRequest(operation);
    client.setAuthenticationInfo(new AuthenticationInfo());
    publisher.logProxyRequest(operation, 1, "client", "test", "uid=user.2,dc=remote", null, null);
    publisher.logProxyResponse(operation, 1, "localhost:389", "localhost:389", ResultCode.SUCCESS,
        "uid=user.2,dc=remote", null, -1, null, 1, null);
    publisher.logBindResponse(operation);
    assertThat(writer.getMessages()).hasSize(4);
  }

  @Test
  public void proxyResponseUsesOriginalDecisionForEveryFilteringCriterion() throws Exception
  {
    AccessLogFilteringCriteriaCfg first = mockCriteriaFilterCfg();
    when(first.getRequestTargetDNEqualTo()).thenReturn(newTreeSet("dc=com"));
    when(first.getResponseResultCodeEqualTo()).thenReturn(newTreeSet(32));
    AccessLogFilteringCriteriaCfg second = mockCriteriaFilterCfg();
    when(second.getRequestTargetDNEqualTo()).thenReturn(newTreeSet("ou=child,dc=com"));
    Class<?> filterType = Class.forName(AbstractTextAccessLogPublisher.class.getName() + "$Filter");
    Object filters = java.lang.reflect.Array.newInstance(filterType, 2);
    java.lang.reflect.Array.set(filters, 0, new CriteriaFilter(first));
    java.lang.reflect.Array.set(filters, 1, new CriteriaFilter(second));
    Class<?> orType = Class.forName(AbstractTextAccessLogPublisher.class.getName() + "$OrFilter");
    java.lang.reflect.Constructor<?> orConstructor = orType.getDeclaredConstructor(filters.getClass());
    orConstructor.setAccessible(true);
    Object orFilter = orConstructor.newInstance(filters);
    java.lang.reflect.Constructor<?> rootConstructor = RootFilter.class.getDeclaredConstructor(boolean.class,
        boolean.class, org.forgerock.opendj.server.config.meta.AccessLogPublisherCfgDefn.FilteringPolicy.class, filterType);
    rootConstructor.setAccessible(true);
    Object rootFilter = rootConstructor.newInstance(false, false,
        org.forgerock.opendj.server.config.meta.AccessLogPublisherCfgDefn.FilteringPolicy.INCLUSIVE, orFilter);
    TestTextWriter writer = new TestTextWriter();
    TextAccessLogPublisher publisher = TextAccessLogPublisher.getStartupTextAccessPublisher(writer, false);
    java.lang.reflect.Field filterField = AbstractTextAccessLogPublisher.class.getDeclaredField("filter");
    filterField.setAccessible(true);
    filterField.set(publisher, rootFilter);
    org.opends.server.core.SearchOperationBasis operation = new org.opends.server.core.SearchOperationBasis(
        new org.opends.server.protocols.internal.InternalClientConnection(new AuthenticationInfo()),
        1, 1, java.util.Collections.emptyList(), dn("dc=com"),
        org.forgerock.opendj.ldap.SearchScope.WHOLE_SUBTREE,
        org.forgerock.opendj.ldap.DereferenceAliasesPolicy.NEVER, 0, 0, false,
        org.opends.server.types.SearchFilter.createFilterFromString("(objectClass=*)"),
        java.util.Collections.emptySet());
    operation.setResultCode(ResultCode.SUCCESS);
    publisher.logSearchRequest(operation);
    publisher.logProxyRequest(operation, 1, "client", "test", "dc=com", "(objectClass=*)", "sub");
    operation.setBaseDN(dn("ou=child,dc=com"));
    publisher.logProxyRequest(operation, 2, "client", "test", "ou=child,dc=com", "(objectClass=*)", "sub");
    publisher.logProxyResponse(operation, 2, "localhost:389", "localhost:389", ResultCode.SUCCESS,
        "", null, 0, null, 1, null);
    publisher.logSearchResultDone(operation);
    assertThat(writer.getMessages()).hasSize(3);
  }

  private void assertProxyRequestFilterDecision(String allowedTarget, boolean expected) throws Exception
  {
    AccessLogFilteringCriteriaCfg cfg = mockCriteriaFilterCfg();
    when(cfg.getRequestTargetDNEqualTo()).thenReturn(newTreeSet(allowedTarget));
    TestTextWriter writer = new TestTextWriter();
    TextAccessLogPublisher publisher = TextAccessLogPublisher.getStartupTextAccessPublisher(writer, false);
    java.lang.reflect.Field filterField = AbstractTextAccessLogPublisher.class.getDeclaredField("filter");
    filterField.setAccessible(true);
    filterField.set(publisher, new RootFilter(false, false,
        org.forgerock.opendj.server.config.meta.AccessLogPublisherCfgDefn.FilteringPolicy.INCLUSIVE,
        new CriteriaFilter(cfg)));
    org.opends.server.protocols.internal.InternalClientConnection client =
        new org.opends.server.protocols.internal.InternalClientConnection(new AuthenticationInfo());
    org.opends.server.core.SearchOperationBasis operation = new org.opends.server.core.SearchOperationBasis(
        client, 1, 1, java.util.Collections.emptyList(), dn("dc=com"),
        org.forgerock.opendj.ldap.SearchScope.WHOLE_SUBTREE,
        org.forgerock.opendj.ldap.DereferenceAliasesPolicy.NEVER, 0, 0, false,
        org.opends.server.types.SearchFilter.createFilterFromString("(objectClass=*)"),
        java.util.Collections.emptySet());
    operation.setResultCode(ResultCode.SUCCESS);
    publisher.logSearchRequest(operation);
    publisher.logProxyRequest(operation, 1, "client", "test", "dc=com", "(objectClass=*)", "sub");
    operation.setBaseDN(dn("ou=child,dc=com"));
    publisher.logProxyRequest(operation, 2, "client", "test", "ou=child,dc=com", "(objectClass=*)", "sub");
    publisher.logProxyResponse(operation, 2, "localhost:389", "localhost:389", ResultCode.SUCCESS,
        "", null, 0, null, 1, null);
    publisher.logSearchResultDone(operation);
    if (expected)
    {
      assertThat(writer.getMessages()).hasSize(5);
    }
    else
    {
      assertThat(writer.getMessages()).isEmpty();
    }
  }

  @Test
  public void testCriteriaFilterDefault() throws Exception
  {
    final AccessLogFilteringCriteriaCfg cfg = mockCriteriaFilterCfg();
    final CriteriaFilter filter = new CriteriaFilter(cfg);
    final Operation operation = mockAnonymousSearchOperation();
    assertThat(filter.isRequestLoggable(operation)).isTrue();
  }



  @Test
  public void testCriteriaFilterRequestTargetDNEqualTo() throws Exception
  {
    final AccessLogFilteringCriteriaCfg cfg = mockCriteriaFilterCfg();
    when(cfg.getRequestTargetDNEqualTo()).thenReturn(newTreeSet("dc=com"));
    final CriteriaFilter filter = new CriteriaFilter(cfg);
    final SearchOperation operation = mockAnonymousSearchOperation();
    when(operation.getBaseDN()).thenReturn(dn("dc=com"), dn("dc=org"));
    assertThat(filter.isRequestLoggable(operation)).isTrue();
    assertThat(filter.isRequestLoggable(operation)).isFalse();
  }



  @Test
  public void testCriteriaFilterRequestTargetDNNotEqualTo() throws Exception
  {
    final AccessLogFilteringCriteriaCfg cfg = mockCriteriaFilterCfg();
    when(cfg.getRequestTargetDNNotEqualTo()).thenReturn(newTreeSet("dc=com"));
    final CriteriaFilter filter = new CriteriaFilter(cfg);
    final SearchOperation operation = mockAnonymousSearchOperation();
    when(operation.getBaseDN()).thenReturn(dn("dc=com"), dn("dc=org"));
    assertThat(filter.isRequestLoggable(operation)).isFalse();
    assertThat(filter.isRequestLoggable(operation)).isTrue();
  }



  @Test
  public void testCriteriaFilterResponseEtimeGreaterThan() throws Exception
  {
    final AccessLogFilteringCriteriaCfg cfg = mockCriteriaFilterCfg();
    when(cfg.getResponseEtimeGreaterThan()).thenReturn(100);
    final CriteriaFilter filter = new CriteriaFilter(cfg);
    final SearchOperation operation = mockAnonymousSearchOperation();
    when(operation.getProcessingTime()).thenReturn(50L, 150L);
    assertThat(filter.isResponseLoggable(operation)).isFalse();
    assertThat(filter.isResponseLoggable(operation)).isTrue();
  }



  @Test
  public void testCriteriaFilterResponseEtimeLessThan() throws Exception
  {
    final AccessLogFilteringCriteriaCfg cfg = mockCriteriaFilterCfg();
    when(cfg.getResponseEtimeLessThan()).thenReturn(100);
    final CriteriaFilter filter = new CriteriaFilter(cfg);
    final SearchOperation operation = mockAnonymousSearchOperation();
    when(operation.getProcessingTime()).thenReturn(50L, 150L);
    assertThat(filter.isResponseLoggable(operation)).isTrue();
    assertThat(filter.isResponseLoggable(operation)).isFalse();
  }



  @Test
  public void testCriteriaFilterResponseResultCodeEqualTo() throws Exception
  {
    final AccessLogFilteringCriteriaCfg cfg = mockCriteriaFilterCfg();
    when(cfg.getResponseResultCodeEqualTo()).thenReturn(newTreeSet(32));
    final CriteriaFilter filter = new CriteriaFilter(cfg);
    final SearchOperation operation = mockAnonymousSearchOperation();
    when(operation.getResultCode()).thenReturn(ResultCode.NO_SUCH_OBJECT,
        ResultCode.SUCCESS);
    assertThat(filter.isResponseLoggable(operation)).isTrue();
    assertThat(filter.isResponseLoggable(operation)).isFalse();
  }



  @Test
  public void testCriteriaFilterResponseResultCodeNotEqualTo() throws Exception
  {
    final AccessLogFilteringCriteriaCfg cfg = mockCriteriaFilterCfg();
    when(cfg.getResponseResultCodeNotEqualTo()).thenReturn(newTreeSet(32));
    final CriteriaFilter filter = new CriteriaFilter(cfg);
    final SearchOperation operation = mockAnonymousSearchOperation();
    when(operation.getResultCode()).thenReturn(ResultCode.NO_SUCH_OBJECT,
        ResultCode.SUCCESS);
    assertThat(filter.isResponseLoggable(operation)).isFalse();
    assertThat(filter.isResponseLoggable(operation)).isTrue();
  }



  @Test
  public void testCriteriaFilterUserDNEqualTo() throws Exception
  {
    final AccessLogFilteringCriteriaCfg cfg = mockCriteriaFilterCfg();
    when(cfg.getUserDNEqualTo())
        .thenReturn(newTreeSet(dnOfUserInGroup().toString()));
    final CriteriaFilter filter = new CriteriaFilter(cfg);
    final SearchOperation operation1 = mockAuthenticatedSearchOperation(dnOfUserInGroup());
    assertThat(filter.isRequestLoggable(operation1)).isTrue();
    final SearchOperation operation2 = mockAuthenticatedSearchOperation(dnOfUserNotInGroup());
    assertThat(filter.isRequestLoggable(operation2)).isFalse();
  }



  @Test
  public void testCriteriaFilterUserDNNotEqualTo() throws Exception
  {
    final AccessLogFilteringCriteriaCfg cfg = mockCriteriaFilterCfg();
    when(cfg.getUserDNNotEqualTo()).thenReturn(
        newTreeSet(dnOfUserInGroup().toString()));
    final CriteriaFilter filter = new CriteriaFilter(cfg);
    final SearchOperation operation1 = mockAuthenticatedSearchOperation(dnOfUserInGroup());
    assertThat(filter.isRequestLoggable(operation1)).isFalse();
    final SearchOperation operation2 = mockAuthenticatedSearchOperation(dnOfUserNotInGroup());
    assertThat(filter.isRequestLoggable(operation2)).isTrue();
  }



  @Test
  public void testCriteriaFilterUserIsMemberOf() throws Exception
  {
    final AccessLogFilteringCriteriaCfg cfg = mockCriteriaFilterCfg();
    when(cfg.getUserIsMemberOf()).thenReturn(newTreeSet(dnOfGroup()));
    final CriteriaFilter filter = new CriteriaFilter(cfg);
    final SearchOperation operation1 = mockAuthenticatedSearchOperation(dnOfUserInGroup());
    assertThat(filter.isRequestLoggable(operation1)).isTrue();
    final SearchOperation operation2 = mockAuthenticatedSearchOperation(dnOfUserNotInGroup());
    assertThat(filter.isRequestLoggable(operation2)).isFalse();
  }



  @Test
  public void testCriteriaFilterUserIsNotMemberOf() throws Exception
  {
    final AccessLogFilteringCriteriaCfg cfg = mockCriteriaFilterCfg();
    when(cfg.getUserIsNotMemberOf()).thenReturn(newTreeSet(dnOfGroup()));
    final CriteriaFilter filter = new CriteriaFilter(cfg);
    final SearchOperation operation1 = mockAuthenticatedSearchOperation(dnOfUserInGroup());
    assertThat(filter.isRequestLoggable(operation1)).isFalse();
    final SearchOperation operation2 = mockAuthenticatedSearchOperation(dnOfUserNotInGroup());
    assertThat(filter.isRequestLoggable(operation2)).isTrue();
  }



  private DN dn(final String dn)
  {
    return DN.valueOf(dn);
  }



  private DN dnOfGroup()
  {
    return dn("cn=group 1,ou=Groups,o=test");
  }



  private DN dnOfUserInGroup()
  {
    return dn("uid=user.1,ou=People,o=test");
  }



  private DN dnOfUserNotInGroup()
  {
    return dn("uid=user.2,ou=People,o=test");
  }



  private SearchOperation mockAnonymousSearchOperation() throws Exception
  {
    return mockSearchOperation(new AuthenticationInfo());
  }



  private SearchOperation mockAuthenticatedSearchOperation(final DN user)
      throws Exception
  {
    return mockSearchOperation(new AuthenticationInfo(
        DirectoryServer.getEntry(user), false));
  }



  private AccessLogFilteringCriteriaCfg mockCriteriaFilterCfg()
  {
    final AccessLogFilteringCriteriaCfg cfg = mock(AccessLogFilteringCriteriaCfg.class);
    when(cfg.getConnectionClientAddressEqualTo()).thenReturn(
        new TreeSet<AddressMask>());
    when(cfg.getConnectionClientAddressNotEqualTo()).thenReturn(
        new TreeSet<AddressMask>());
    when(cfg.getConnectionPortEqualTo()).thenReturn(new TreeSet<Integer>());
    when(cfg.getConnectionProtocolEqualTo()).thenReturn(new TreeSet<String>());
    when(cfg.getLogRecordType()).thenReturn(new TreeSet<LogRecordType>());
    when(cfg.getRequestTargetDNEqualTo()).thenReturn(new TreeSet<String>());
    when(cfg.getRequestTargetDNNotEqualTo()).thenReturn(new TreeSet<String>());
    when(cfg.getResponseEtimeGreaterThan()).thenReturn(null);
    when(cfg.getResponseEtimeLessThan()).thenReturn(null);
    when(cfg.getResponseResultCodeEqualTo()).thenReturn(new TreeSet<Integer>());
    when(cfg.getResponseResultCodeNotEqualTo()).thenReturn(
        new TreeSet<Integer>());
    when(cfg.isSearchResponseIsIndexed()).thenReturn(null);
    when(cfg.getSearchResponseNentriesGreaterThan()).thenReturn(null);
    when(cfg.getSearchResponseNentriesLessThan()).thenReturn(null);
    when(cfg.getUserDNEqualTo()).thenReturn(new TreeSet<String>());
    when(cfg.getUserDNNotEqualTo()).thenReturn(new TreeSet<String>());
    when(cfg.getUserIsMemberOf()).thenReturn(new TreeSet<DN>());
    when(cfg.getUserIsNotMemberOf()).thenReturn(new TreeSet<DN>());
    return cfg;
  }

  private SearchOperation mockSearchOperation(final AuthenticationInfo authInfo)
      throws Exception
  {
    final SearchOperation operation = mock(SearchOperation.class);
    final ClientConnection connection = mock(ClientConnection.class);
    when(operation.getOperationType()).thenReturn(OperationType.SEARCH);
    when(operation.getClientConnection()).thenReturn(connection);
    when(operation.getResultCode()).thenReturn(ResultCode.SUCCESS);
    when(connection.getAuthenticationInfo()).thenReturn(authInfo);
    return operation;
  }
}
