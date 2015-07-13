/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2013-2015 ForgeRock AS
 */
package org.opends.server.loggers;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.opends.server.util.CollectionUtils.*;

import java.util.TreeSet;

import org.forgerock.opendj.ldap.AddressMask;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.admin.std.meta.AccessLogFilteringCriteriaCfgDefn.LogRecordType;
import org.opends.server.admin.std.server.AccessLogFilteringCriteriaCfg;
import org.opends.server.api.ClientConnection;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.SearchOperation;
import org.opends.server.loggers.AbstractTextAccessLogPublisher.CriteriaFilter;
import org.opends.server.loggers.AbstractTextAccessLogPublisher.RootFilter;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
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
    try
    {
      return DN.valueOf(dn);
    }
    catch (final DirectoryException e)
    {
      throw new RuntimeException(e);
    }
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
