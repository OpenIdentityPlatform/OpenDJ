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
 *      Copyright 2015 ForgeRock AS
 */
package org.opends.server.backends.pluggable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.opends.server.ConfigurationMock.legacyMockCfg;
import static org.opends.server.TestCaseUtils.makeEntry;
import static org.opends.server.protocols.internal.InternalClientConnection.getRootConnection;
import static org.opends.server.protocols.internal.Requests.newSearchRequest;
import static org.opends.server.util.CollectionUtils.*;
import static org.opends.server.util.ServerConstants.*;
import static org.testng.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.admin.std.meta.BackendVLVIndexCfgDefn.Scope;
import org.opends.server.admin.std.server.BackendVLVIndexCfg;
import org.opends.server.admin.std.server.PDBBackendCfg;
import org.opends.server.backends.pdb.PDBBackend;
import org.opends.server.controls.ServerSideSortRequestControl;
import org.opends.server.controls.ServerSideSortResponseControl;
import org.opends.server.controls.VLVRequestControl;
import org.opends.server.controls.VLVResponseControl;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.SearchResultEntry;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * This class contains a number of test cases for the controls which are supported within the
 * backend. In particular, VLV, paged results, and server side sorting controls.
 */
@SuppressWarnings("javadoc")
public class ControlsTestCase extends DirectoryServerTestCase
{

  private static final String BACKEND_BASE_DN = "dc=pluggable-vlv,dc=com";
  private static final String BACKEND_NAME = "pluggable-vlv";
  private static final String VLV_FILTER = "(objectClass=person)";

  // @formatter:off
  private static final User[] USERS = {
    user(givenNames("Alice"),              surname("Zorro"),       employeeNumber(0)),
    user(givenNames("Bob", "Robert"),      surname("deBuilder"),   employeeNumber(1)),
    user(givenNames("Charlie"),            surname("Speed"),       employeeNumber(2)),
    user(givenNames("Mickie"),             surname("Mouse"),       employeeNumber(3)),
    user(givenNames("William", "Bill"),    surname("Beak"),        employeeNumber(4)),
    user(givenNames(),                     surname("Prince"),      employeeNumber(5)),
    user(givenNames("Charlie"),            surname("Chalk"),       employeeNumber(6)),
    user(givenNames("Albert"),             surname("Einstein"),    employeeNumber(7)),
    user(givenNames("Mini"),               surname("Mouse"),       employeeNumber(8)),
  };
  // @formatter:on

  private static final int CONTENT_COUNT = USERS.length;

  /** Indexed: ordered by ascending givenName then entryID */
  private static final String SORT_ORDER_1 = "givenName";

  /** Indexed: ordered by descending sn and descending employeeNumber then entryID */
  private static final String SORT_ORDER_2 = "-sn -employeeNumber";

  /** Unindexed: ordered by ascending sn and ascending employee number then entryID */
  private static final String SORT_ORDER_3 = "sn employeeNumber";

  /** Ordered by {@link #SORT_ORDER_1} */
  private static final List<Integer> USERS_BY_ENTRY_ID = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8);

  /** Indexed: ordered by {@link #SORT_ORDER_1} */
  private static final List<Integer> USERS_BY_SORT_ORDER_1 = Arrays.asList(7, 0, 4, 1, 2, 6, 3, 8, 5);

  /** Indexed: ordered by {@link #SORT_ORDER_2} */
  private static final List<Integer> USERS_BY_SORT_ORDER_2 = Arrays.asList(0, 2, 5, 8, 3, 7, 1, 6, 4);

  /** Unindexed: ordered by {@link #SORT_ORDER_3} */
  private static final List<Integer> USERS_BY_SORT_ORDER_3 = Arrays.asList(4, 6, 1, 7, 3, 8, 5, 2, 0);

  private PDBBackend backend;

  @BeforeClass
  public void beforeClass() throws Exception
  {
    TestCaseUtils.startServer();

    final DN baseDN = DN.valueOf(BACKEND_BASE_DN);

    final PDBBackendCfg backendCfg = legacyMockCfg(PDBBackendCfg.class);
    when(backendCfg.dn()).thenReturn(baseDN);
    when(backendCfg.getBackendId()).thenReturn(BACKEND_NAME);
    when(backendCfg.getBaseDN()).thenReturn(newTreeSet(baseDN));
    when(backendCfg.listBackendIndexes()).thenReturn(new String[0]);
    when(backendCfg.listBackendVLVIndexes()).thenReturn(new String[] { SORT_ORDER_1, SORT_ORDER_2 });

    when(backendCfg.getDBDirectory()).thenReturn(BACKEND_NAME);
    when(backendCfg.getDBDirectoryPermissions()).thenReturn("755");
    when(backendCfg.getDBCacheSize()).thenReturn(0L);
    when(backendCfg.getDBCachePercent()).thenReturn(20);

    createVlvIndex(baseDN, backendCfg, SORT_ORDER_1);
    createVlvIndex(baseDN, backendCfg, SORT_ORDER_2);

    backend = new PDBBackend();
    backend.setBackendID(backendCfg.getBackendId());
    backend.configureBackend(backendCfg, DirectoryServer.getInstance().getServerContext());
    backend.openBackend();

    backend.addEntry(makeEntry("dn: " + BACKEND_BASE_DN, "objectclass: top", "objectclass: domain"), null);
    for (final User user : USERS)
    {
      backend.addEntry(user.toEntry(), null);
    }
  }

  private void createVlvIndex(final DN baseDN, final PDBBackendCfg backendCfg, final String sortOrder)
      throws ConfigException
  {
    final BackendVLVIndexCfg vlvIndexCfg = legacyMockCfg(BackendVLVIndexCfg.class);
    when(vlvIndexCfg.getName()).thenReturn(sortOrder);
    when(vlvIndexCfg.getBaseDN()).thenReturn(baseDN);
    when(vlvIndexCfg.getFilter()).thenReturn(VLV_FILTER);
    when(vlvIndexCfg.getScope()).thenReturn(Scope.WHOLE_SUBTREE);
    when(vlvIndexCfg.getSortOrder()).thenReturn(sortOrder);
    when(backendCfg.getBackendVLVIndex(sortOrder)).thenReturn(vlvIndexCfg);
  }

  @DataProvider
  private Object[][] encodedKeyDataProvider()
  {
    // @formatter:off
    return new Object[][] {
      // Null keys sort after everything else.
      { null,           null,           0 },
      { "",             null,          -1 },
      { null,           "",             1 },
      { "00",           null,          -1 },
      { null,           "00",           1 },
      { "ff",           null,          -1 },
      { null,           "ff",           1 },

      // Empty keys sort before everything else.
      { "",             "",             0 },
      { "00",           "",             1 },
      { "",             "00",          -1 },
      { "ff",           "",             1 },
      { "",             "ff",          -1 },

      // Bytes comparisons are unsigned.
      { "00",           "00",           0 },
      { "00",           "ff",          -1 },
      { "ff",           "00",           1 },
      { "ff",           "ff",           0 },

      // Short keys sort before long keys.
      { "0000",         "00",           1 },
      { "00",           "0000",        -1 },
      { "ffff",         "ff",           1 },
      { "ff",           "ffff",        -1 },
      { "0000",         "0000",         0 },
      { "ffff",         "ffff",         0 },
      { "0000",         "ffff",        -1 },
      { "ffff",         "0000",         1 },
    };
    // @formatter:on
  }

  @Test(dataProvider = "encodedKeyDataProvider")
  public void vlvKeyEncodingGenerateCorrectAscendingSortOrder(String key1, String key2, int expectedCompareResult)
  {
    ByteString bytes1 = key1 != null ? ByteString.valueOfHex(key1) : null;
    ByteStringBuilder encodedBytes1 = new ByteStringBuilder();
    VLVIndex.encodeVLVKeyValue(bytes1, encodedBytes1, true);

    ByteString bytes2 = key2 != null ? ByteString.valueOfHex(key2) : null;
    ByteStringBuilder encodedBytes2 = new ByteStringBuilder();
    VLVIndex.encodeVLVKeyValue(bytes2, encodedBytes2, true);

    int actualResult = Math.min(Math.max(encodedBytes1.compareTo(encodedBytes2), -1), 1);
    assertThat(actualResult).isEqualTo(expectedCompareResult);
  }

  @Test(dataProvider = "encodedKeyDataProvider")
  public void vlvKeyEncodingGenerateCorrectDescendingSortOrder(String key1, String key2, int expectedCompareResult)
  {
    ByteString bytes1 = key1 != null ? ByteString.valueOfHex(key1) : null;
    ByteStringBuilder encodedBytes1 = new ByteStringBuilder();
    VLVIndex.encodeVLVKeyValue(bytes1, encodedBytes1, false);

    ByteString bytes2 = key2 != null ? ByteString.valueOfHex(key2) : null;
    ByteStringBuilder encodedBytes2 = new ByteStringBuilder();
    VLVIndex.encodeVLVKeyValue(bytes2, encodedBytes2, false);

    int actualResult = Math.min(Math.max(encodedBytes1.compareTo(encodedBytes2), -1), 1);
    assertThat(actualResult).isEqualTo(-expectedCompareResult);
  }

  @DataProvider
  private Object[][] indexedVlvByAssertionDataProvider()
  {
    // @formatter:off
    return new Object[][] {
      {
        SORT_ORDER_2,
        beforeCount(0),
        afterCount(5),
        assertion("ZZ"),                                // before first
        USERS_BY_SORT_ORDER_2.subList(0, 6),
        expectedPosition(1)
      },
      {
        SORT_ORDER_2,
        beforeCount(0),
        afterCount(5),
        assertion("zorro"),                             // matches first
        USERS_BY_SORT_ORDER_2.subList(0, 6),
        expectedPosition(1)
      },
      {
        SORT_ORDER_2,
        beforeCount(0),
        afterCount(3),
        assertion("PRINCE"),                            // matches third
        USERS_BY_SORT_ORDER_2.subList(2, 6),
        expectedPosition(3)
      },
      {
        SORT_ORDER_2,
        beforeCount(1),
        afterCount(3),
        assertion("prince"),                            // matches third
        USERS_BY_SORT_ORDER_2.subList(1, 6),
        expectedPosition(3)
      },
      {
        SORT_ORDER_2,
        beforeCount(10),
        afterCount(10),
        assertion("prince"),                            // matches third
        USERS_BY_SORT_ORDER_2,
        expectedPosition(3)
      },
      {
        SORT_ORDER_2,
        beforeCount(0),
        afterCount(3),
        assertion("a"),                                 // after all
        USERS_BY_SORT_ORDER_2.subList(9, 9),            // nothing
        expectedPosition(10)
      },
    };
    // @formatter:on
  }

  @Test(dataProvider = "indexedVlvByAssertionDataProvider")
  public void indexedVlvByAssertionShouldReturnPageOfResultsInCorrectOrder(final String sortOrder,
      final int beforeCount, final int afterCount, final String assertion, final List<Integer> expectedOrder,
      final int expectedPosition) throws Exception
  {
    vlvByAssertion(sortOrder, beforeCount, afterCount, assertion, expectedOrder, expectedPosition);
  }

  @DataProvider
  private Object[][] indexedVlvByOffsetDataProvider()
  {
    // @formatter:off
    return new Object[][] {
      {
        SORT_ORDER_1,
        beforeCount(0),
        afterCount(3),
        offset(1),
        USERS_BY_SORT_ORDER_1.subList(0, 4)
      },
      {
        SORT_ORDER_1,
        beforeCount(0),
        afterCount(3),
        offset(3),
        USERS_BY_SORT_ORDER_1.subList(2, 6)
      },
      {
        SORT_ORDER_1,
        beforeCount(0),
        afterCount(0),
        offset(5),
        USERS_BY_SORT_ORDER_1.subList(4, 5)
      },
      {
        SORT_ORDER_1,
        beforeCount(3),
        afterCount(3),
        offset(5),
        USERS_BY_SORT_ORDER_1.subList(1, 8)
      },
      {
        SORT_ORDER_1,
        beforeCount(0),
        afterCount(3),
        offset(0),                                      // 0 is equivalent to 1
        USERS_BY_SORT_ORDER_1.subList(0, 4)
      },
      {
        SORT_ORDER_1,
        beforeCount(3),                                 // underflow
        afterCount(3),
        offset(1),
        USERS_BY_SORT_ORDER_1.subList(0, 4)
      },
      {
        SORT_ORDER_1,
        beforeCount(3),
        afterCount(3),
        offset(30),                                     // overflow
        USERS_BY_SORT_ORDER_1.subList(6, 9)
      },
      {
        SORT_ORDER_1,
        beforeCount(3),
        afterCount(3),                                  // overflow
        offset(7),
        USERS_BY_SORT_ORDER_1.subList(3, 9)
      },
      {
        SORT_ORDER_1,
        beforeCount(30),                                // underflow
        afterCount(30),                                 // overflow
        offset(7),
        USERS_BY_SORT_ORDER_1                           // everything
      },
      {
        SORT_ORDER_1,
        beforeCount(0),                                 // underflow
        afterCount(0),                                  // overflow
        offset(30),
        USERS_BY_SORT_ORDER_1.subList(9, 9)             //  nothing
      },

    };
    // @formatter:on
  }

  @Test(dataProvider = "indexedVlvByOffsetDataProvider")
  public void indexedVlvByOffsetShouldReturnPageOfResultsInCorrectOrder(final String sortOrder, final int beforeCount,
      final int afterCount, final int offset, final List<Integer> expectedOrder) throws Exception
  {
    vlvByOffset(sortOrder, beforeCount, afterCount, offset, expectedOrder);
  }

  @Test
  public void indexedVlvByOffsetShouldReturnOffsetRangeErrorForNegativeOffsets() throws Exception
  {
    final List<Control> responseControls = vlvByOffset0(SORT_ORDER_2, 0, 3, -1, USERS_BY_ENTRY_ID);
    assertThat(responseControls).isNotEmpty();
    final VLVResponseControl vlvResponse = getVLVResponseControl(responseControls);
    assertThat(vlvResponse.getVLVResultCode()).isEqualTo(LDAPResultCode.OFFSET_RANGE_ERROR);
  }

  @DataProvider
  private Object[][] unindexedVlvByAssertionDataProvider()
  {
    // @formatter:off
    return new Object[][] {
      {
        SORT_ORDER_3,
        beforeCount(0),
        afterCount(3),
        assertion("a"),                                 // before first
        USERS_BY_SORT_ORDER_3.subList(0, 4),
        expectedPosition(1)
      },
      {
        SORT_ORDER_3,
        beforeCount(0),
        afterCount(3),
        assertion("beak"),                              // matches first
        USERS_BY_SORT_ORDER_3.subList(0, 4),
        expectedPosition(1)
      },
      {
        SORT_ORDER_3,
        beforeCount(0),
        afterCount(3),
        assertion("debuilder"),                         // matches third
        USERS_BY_SORT_ORDER_3.subList(2, 6),
        expectedPosition(3)
      },
      {
        SORT_ORDER_3,
        beforeCount(1),
        afterCount(3),
        assertion("debuilder"),                         // matches third
        USERS_BY_SORT_ORDER_3.subList(1, 6),
        expectedPosition(3)
      },
      {
        SORT_ORDER_3,
        beforeCount(0),
        afterCount(3),
        assertion("zz"),                                // after all
        USERS_BY_SORT_ORDER_3.subList(9, 9),            // nothing
        expectedPosition(10)
      },
    };
    // @formatter:on
  }

  @Test(dataProvider = "unindexedVlvByAssertionDataProvider")
  public void unindexedVlvByAssertionShouldReturnPageOfResultsInCorrectOrder(final String sortOrder,
      final int beforeCount, final int afterCount, final String assertion, final List<Integer> expectedOrder,
      final int expectedPosition) throws Exception
  {
    vlvByAssertion(sortOrder, beforeCount, afterCount, assertion, expectedOrder, expectedPosition);
  }

  @DataProvider
  private Object[][] unindexedVlvByOffsetDataProvider()
  {
    // @formatter:off
    return new Object[][] {
      {
        SORT_ORDER_3,
        beforeCount(0),
        afterCount(3),
        offset(1),
        USERS_BY_SORT_ORDER_3.subList(0, 4)
      },
      {
        SORT_ORDER_3,
        beforeCount(0),
        afterCount(3),
        offset(3),
        USERS_BY_SORT_ORDER_3.subList(2, 6)
      },
      {
        SORT_ORDER_3,
        beforeCount(0),
        afterCount(0),
        offset(5),
        USERS_BY_SORT_ORDER_3.subList(4, 5)
      },
      {
        SORT_ORDER_3,
        beforeCount(3),
        afterCount(3),
        offset(5),
        USERS_BY_SORT_ORDER_3.subList(1, 8)
      },
      {
        SORT_ORDER_3,
        beforeCount(0),
        afterCount(3),
        offset(0),                                      // 0 is equivalent to 1
        USERS_BY_SORT_ORDER_3.subList(0, 4)
      },
      {
        SORT_ORDER_3,
        beforeCount(3),                                 // underflow
        afterCount(3),
        offset(1),
        USERS_BY_SORT_ORDER_3.subList(0, 4)
      },
      {
        SORT_ORDER_3,
        beforeCount(3),
        afterCount(3),
        offset(30),                                     // overflow
        USERS_BY_SORT_ORDER_3.subList(6, 9)
      },
      {
        SORT_ORDER_3,
        beforeCount(3),
        afterCount(3),                                  // overflow
        offset(7),
        USERS_BY_SORT_ORDER_3.subList(3, 9)
      },
      {
        SORT_ORDER_3,
        beforeCount(30),                                // underflow
        afterCount(30),                                 // overflow
        offset(7),
        USERS_BY_SORT_ORDER_3                           // everything
      },
      {
        SORT_ORDER_3,
        beforeCount(0),                                 // underflow
        afterCount(0),                                  // overflow
        offset(30),
        USERS_BY_SORT_ORDER_3.subList(9, 9)             //  nothing
      },

    };
    // @formatter:on
  }

  @Test(dataProvider = "unindexedVlvByOffsetDataProvider")
  public void unindexedVlvByOffsetShouldReturnPageOfResultsInCorrectOrder(final String sortOrder,
      final int beforeCount, final int afterCount, final int offset, final List<Integer> expectedOrder)
      throws Exception
  {
    vlvByOffset(sortOrder, beforeCount, afterCount, offset, expectedOrder);
  }

  @AfterClass
  public void afterClass() throws Exception
  {
    backend.finalizeBackend();
    backend = null;
  }

  private static int employeeNumber(final int i)
  {
    return i;
  }

  private static List<String> givenNames(final String... names)
  {
    return Arrays.asList(names);
  }

  private static String surname(final String name)
  {
    return name;
  }

  private static User user(final List<String> givenNames, final String sn, final int employeeNumber)
  {
    return new User(givenNames, sn, employeeNumber);
  }

  private int beforeCount(final int i)
  {
    return i;
  }

  private int afterCount(final int i)
  {
    return i;
  }

  private String assertion(final String s)
  {
    return s;
  }

  private int offset(final int i)
  {
    return i;
  }

  private int expectedPosition(final int i)
  {
    return i;
  }

  private ArrayList<DN> getDNs(final LinkedList<SearchResultEntry> entries)
  {
    final ArrayList<DN> results = new ArrayList<>();
    for (final Entry e : entries)
    {
      results.add(e.getName());
    }
    return results;
  }

  private List<DN> getDNs(List<Integer> expectedOrder) throws Exception
  {
    List<DN> dns = new ArrayList<>(expectedOrder.size());
    for (int i : expectedOrder)
    {
      dns.add(USERS[i].toDN());
    }
    return dns;
  }

  private ServerSideSortResponseControl getServerSideSortResponseControl(final Control c) throws DirectoryException
  {
    if (c instanceof LDAPControl)
    {
      return ServerSideSortResponseControl.DECODER.decode(c.isCritical(), ((LDAPControl) c).getValue());
    }
    return (ServerSideSortResponseControl) c;
  }

  private ServerSideSortResponseControl getServerSideSortResponseControl(final List<Control> responseControls)
      throws DirectoryException
  {
    for (final Control c : responseControls)
    {
      if (c.getOID().equals(OID_SERVER_SIDE_SORT_RESPONSE_CONTROL))
      {
        return getServerSideSortResponseControl(c);
      }
    }
    fail("Expected to find ServerSideSortResponseControl");
    return null;
  }

  private VLVResponseControl getVLVResponseControl(final Control c) throws DirectoryException
  {
    if (c instanceof LDAPControl)
    {
      return VLVResponseControl.DECODER.decode(c.isCritical(), ((LDAPControl) c).getValue());
    }
    return (VLVResponseControl) c;
  }

  private VLVResponseControl getVLVResponseControl(final List<Control> responseControls) throws DirectoryException
  {
    for (final Control c : responseControls)
    {
      if (c.getOID().equals(OID_VLV_RESPONSE_CONTROL))
      {
        return getVLVResponseControl(c);
      }
    }
    fail("Expected to find VLVResponseControl");
    return null;
  }

  private void vlvByAssertion(final String sortOrder, final int beforeCount, final int afterCount,
      final String assertion, final List<Integer> expectedOrder, final int expectedPosition) throws Exception
  {
    final SearchRequest request =
        newSearchRequest(BACKEND_BASE_DN, SearchScope.WHOLE_SUBTREE, VLV_FILTER).addControl(
            new ServerSideSortRequestControl(mangleSortOrder(sortOrder))).addControl(
            new VLVRequestControl(beforeCount, afterCount, ByteString.valueOf(assertion)));
    final InternalSearchOperation internalSearch = getRootConnection().processSearch(request);
    assertThat(internalSearch.getResultCode()).isEqualTo(ResultCode.SUCCESS);
    assertThat(getDNs(internalSearch.getSearchEntries())).isEqualTo(getDNs(expectedOrder));

    final List<Control> responseControls = internalSearch.getResponseControls();
    assertThat(responseControls).hasSize(2);

    final ServerSideSortResponseControl sortResponse = getServerSideSortResponseControl(responseControls);
    assertThat(sortResponse.getResultCode()).isEqualTo(LDAPResultCode.SUCCESS);

    final VLVResponseControl vlvResponse = getVLVResponseControl(responseControls);
    assertThat(vlvResponse.getVLVResultCode()).isEqualTo(LDAPResultCode.SUCCESS);
    assertThat(vlvResponse.getTargetPosition()).isEqualTo(expectedPosition);
    assertThat(vlvResponse.getContentCount()).isEqualTo(CONTENT_COUNT);
  }

  private String mangleSortOrder(final String sortOrder)
  {
    return sortOrder.replaceAll(" ", ",");
  }

  private void vlvByOffset(final String sortOrder, final int beforeCount, final int afterCount, final int offset,
      final List<Integer> expectedOrder) throws Exception, DirectoryException
  {
    final List<Control> responseControls = vlvByOffset0(sortOrder, beforeCount, afterCount, offset, expectedOrder);
    assertThat(responseControls).hasSize(2);

    final ServerSideSortResponseControl sortResponse = getServerSideSortResponseControl(responseControls);
    assertThat(sortResponse.getResultCode()).isEqualTo(LDAPResultCode.SUCCESS);

    final VLVResponseControl vlvResponse = getVLVResponseControl(responseControls);
    assertThat(vlvResponse.getVLVResultCode()).isEqualTo(LDAPResultCode.SUCCESS);
    final int offsetInRange = Math.min(Math.max(offset, 1), CONTENT_COUNT + 1);
    assertThat(vlvResponse.getTargetPosition()).isEqualTo(offsetInRange);
    assertThat(vlvResponse.getContentCount()).isEqualTo(CONTENT_COUNT);
  }

  private List<Control> vlvByOffset0(final String sortOrder, final int beforeCount, final int afterCount,
      final int offset, final List<Integer> expectedOrder) throws Exception
  {
    final SearchRequest request =
        newSearchRequest(BACKEND_BASE_DN, SearchScope.WHOLE_SUBTREE, VLV_FILTER).addControl(
            new ServerSideSortRequestControl(mangleSortOrder(sortOrder))).addControl(
            new VLVRequestControl(beforeCount, afterCount, offset, 0));
    final InternalSearchOperation internalSearch = getRootConnection().processSearch(request);

    assertThat(internalSearch.getResultCode()).isEqualTo(ResultCode.SUCCESS);
    assertThat(getDNs(internalSearch.getSearchEntries())).isEqualTo(getDNs(expectedOrder));
    return internalSearch.getResponseControls();
  }

  private static class User
  {
    private final int employeeNumber;
    private final List<String> givenNames;
    private final String sn;

    private User(final List<String> givenNames, final String sn, final int employeeNumber)
    {
      this.givenNames = givenNames;
      this.sn = sn;
      this.employeeNumber = employeeNumber;
    }

    private DN toDN() throws Exception
    {
      return DN.valueOf(String.format("employeeNumber=%d,%s", employeeNumber, BACKEND_BASE_DN));
    }

    private Entry toEntry() throws Exception
    {
      final List<String> ldif = new ArrayList<>();
      ldif.add("dn: " + toDN());
      ldif.add("objectClass: top");
      ldif.add("objectClass: person");
      ldif.add("objectClass: organizationalPerson");
      ldif.add("objectClass: inetOrgPerson");
      ldif.add(String.format("sn: %s", sn));
      ldif.add(String.format("employeeNumber: %d", employeeNumber));
      for (final String givenName : givenNames)
      {
        ldif.add(String.format("givenName: %s", givenName));
      }
      if (givenNames.isEmpty())
      {
        ldif.add(String.format("cn: %s", sn));
      }
      else
      {
        ldif.add(String.format("cn: %s %s", givenNames.get(0), sn));
      }
      return makeEntry(ldif.toArray(new String[0]));
    }
  }
}
