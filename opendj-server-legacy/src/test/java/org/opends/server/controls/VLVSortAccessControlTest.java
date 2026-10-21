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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.controls;

import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.opends.server.util.ServerConstants.*;
import static org.testng.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.controls.VirtualListViewRequestControl;
import org.forgerock.opendj.ldap.controls.VirtualListViewResponseControl;
import org.forgerock.opendj.ldap.responses.Result;
import org.opends.server.TestCaseUtils;
import org.opends.server.api.AccessControlHandler;
import org.opends.server.core.AccessControlConfigManager;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.types.Control;
import org.opends.server.types.SearchResultEntry;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Regression test for GHSA-6f6q-q9gw-4jr4: the server-side sort and virtual list view controls
 * disclosed entries and attribute values the caller is not allowed to read, through the order of
 * the results, the VLV target position and the VLV content count.
 * <p>
 * The sort keys used to be built from the raw backend entries, and a VLV index answers from its
 * stored keys, both before the access controls that strip unreadable attributes and entries from
 * the returned results. Sorting on {@code userPassword} with a VLV assertion thus recovered another
 * user's password verifier byte by byte. For a caller subject to access control, an entry it may
 * not see now takes no part in the sort, and the values of an attribute it may not read on an entry
 * take no part in its sort key.
 * <p>
 * The tests hide attributes from an ordinary user with ACIs rather than sorting on a salted
 * {@code userPassword}, whose stored order is random, so that both the leaks and their absence are
 * deterministic: the people are added in an order (charlie, alice, bob) other than the order of
 * their hidden values.
 */
@SuppressWarnings("javadoc")
public class VLVSortAccessControlTest extends ControlsTestCase
{
  private static final String BASE_DN = "dc=example,dc=com";
  private static final String PEOPLE_DN = "ou=people," + BASE_DN;
  private static final String BULK_DN = "ou=bulk," + BASE_DN;
  private static final String STAR_DN = "ou=star," + BASE_DN;
  private static final String ACTOR_DN = "uid=actor," + BASE_DN;
  private static final String LIMITED_DN = "uid=limited," + BASE_DN;
  private static final String EXACT_DN = "uid=exact," + BASE_DN;
  private static final String UNLIMITED_DN = "uid=unlimited," + BASE_DN;
  private static final String PASSWORD = "Actor-Secret-1";

  /**
   * One over the default index entry limit, so that neither the filter of the bulk searches nor
   * their scope yields candidate entries: only the VLV index of the bulk entries can.
   */
  private static final int BULK_SIZE = 4001;
  /** Lookthrough limit of the limited user, under {@link #BULK_SIZE}. */
  private static final int LIMITED_LOOKTHROUGH = 100;

  private static final DN CHARLIE_DN = DN.valueOf("uid=charlie," + PEOPLE_DN);
  private static final DN ALICE_DN = DN.valueOf("uid=alice," + PEOPLE_DN);
  private static final DN BOB_DN = DN.valueOf("uid=bob," + PEOPLE_DN);
  private static final DN HIDDEN_DN = DN.valueOf("uid=hidden," + PEOPLE_DN);
  private static final DN STAR_FIRST_DN = DN.valueOf("uid=first," + STAR_DN);
  private static final DN STAR_SECOND_DN = DN.valueOf("uid=second," + STAR_DN);
  private static final DN STAR_THIRD_DN = DN.valueOf("uid=third," + STAR_DN);
  private static final String VERSIONS_DN = "ou=versions," + BASE_DN;

  /** The global ACIs that name attributes, which keep the "all attributes matched" shortcut off. */
  private static final String[] ATTRIBUTE_GLOBAL_ACIS = {
    "(targetattr!=\"userPassword||authPassword||debugsearchindex||changes||changeNumber||changeType||changeTime"
        + "||targetDN||newRDN||newSuperior||deleteOldRDN\")(version 3.0; acl \"Anonymous read access\"; "
        + "allow (read,search,compare) userdn=\"ldap:///anyone\";)",
    "(targetattr=\"userPassword||authPassword\")(version 3.0; acl \"Self entry read\"; "
        + "allow (read,search,compare) userdn=\"ldap:///self\";)",
    "(targetattr=\"createTimestamp||creatorsName||modifiersName||modifyTimestamp||entryDN||entryUUID"
        + "||subschemaSubentry||etag||governingStructureRule||structuralObjectClass||hasSubordinates"
        + "||numSubordinates||isMemberOf\")(version 3.0; acl \"User-Visible Operational Attributes\"; "
        + "allow (read,search,compare) userdn=\"ldap:///anyone\";)",
  };
  /**
   * A rule on all user and all operational attributes: once it replaces the global ACIs that name
   * attributes, it alone matches an entry without ACIs of its own, which sets both halves of the shortcut.
   */
  private static final String ALL_ATTRIBUTES_GLOBAL_ACI = "(targetattr=\"*||+\")(version 3.0; "
      + "acl \"Read everything\"; allow (read,search,compare) userdn=\"ldap:///anyone\";)";

  private InternalClientConnection actorConn;

  @BeforeClass
  public void setUp() throws Exception
  {
    TestCaseUtils.startServer();

    // A VLV index is trusted, and thus used, only when it is created while the backend is empty.
    TestCaseUtils.clearBackend("userRoot");
    TestCaseUtils.dsconfig("create-backend-vlv-index",
        "--backend-name", "userRoot",
        "--index-name", "people-by-description",
        "--set", "base-dn:" + PEOPLE_DN,
        "--set", "scope:single-level",
        "--set", "filter:(objectClass=person)",
        "--set", "sort-order:description");
    TestCaseUtils.dsconfig("create-backend-vlv-index",
        "--backend-name", "userRoot",
        "--index-name", "bulk-by-sn",
        "--set", "base-dn:" + BULK_DN,
        "--set", "scope:single-level",
        "--set", "filter:(employeeNumber=*)",
        "--set", "sort-order:sn");

    TestCaseUtils.addEntries(
        "dn: " + BASE_DN,
        "objectClass: top",
        "objectClass: domain",
        "dc: example",
        "",
        // Inherited by the people below. The actor may neither read nor search "description"
        // and "cn", and may search but not read "employeeNumber".
        "dn: " + PEOPLE_DN,
        "objectClass: top",
        "objectClass: organizationalUnit",
        "ou: people",
        "aci: (targetattr=\"description||cn\")(version 3.0; acl \"deny description and cn to actor\"; "
            + "deny(read,search,compare) userdn=\"ldap:///" + ACTOR_DN + "\";)",
        "aci: (targetattr=\"employeeNumber\")(version 3.0; acl \"deny reading employeeNumber to actor\"; "
            + "deny(read) userdn=\"ldap:///" + ACTOR_DN + "\";)",
        "",
        "dn: " + ACTOR_DN,
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: actor",
        "sn: Or",
        "cn: Act Or",
        "userPassword: " + PASSWORD,
        "",
        "dn: " + LIMITED_DN,
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: limited",
        "sn: Ited",
        "cn: Lim Ited",
        "userPassword: " + PASSWORD,
        "ds-rlim-lookthrough-limit: " + LIMITED_LOOKTHROUGH,
        "",
        "dn: " + EXACT_DN,
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: exact",
        "sn: Act",
        "cn: Ex Act",
        "userPassword: " + PASSWORD,
        "ds-rlim-lookthrough-limit: " + BULK_SIZE,
        "",
        "dn: " + UNLIMITED_DN,
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: unlimited",
        "sn: Limited",
        "cn: Un Limited",
        "userPassword: " + PASSWORD,
        "ds-rlim-lookthrough-limit: 0",
        "",
        // Hidden values in order charlie, bob, alice for description, cn and employeeNumber alike,
        // sn in order alice, bob, charlie.
        "dn: " + CHARLIE_DN,
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: charlie",
        "sn: Xavier",
        "cn: A Charlie",
        "description: aaa",
        "employeeNumber: 1",
        "",
        "dn: " + ALICE_DN,
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: alice",
        "sn: Baker",
        "cn: C Alice",
        "description: zzz",
        "employeeNumber: 3",
        "",
        "dn: " + BOB_DN,
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: bob",
        "sn: Monroe",
        "cn: B Bob",
        "description: mmm",
        "employeeNumber: 2",
        "",
        // The actor may not see this entry at all.
        "dn: " + HIDDEN_DN,
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: hidden",
        "sn: Hidden",
        "cn: Hid Den",
        "description: bbb",
        "aci: (targetattr=\"*\")(version 3.0; acl \"hide entry from actor\"; "
            + "deny(all) userdn=\"ldap:///" + ACTOR_DN + "\";)",
        "",
        // The first entry hides its description from the actor, the third one its description and
        // entryUUID; once the global ACIs that name attributes are gone, only rules on all
        // attributes match the second one.
        "dn: " + STAR_DN,
        "objectClass: top",
        "objectClass: organizationalUnit",
        "ou: star",
        "",
        "dn: " + STAR_FIRST_DN,
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: first",
        "sn: Alpha",
        "cn: First Alpha",
        "description: secret",
        "aci: (targetattr=\"description\")(version 3.0; acl \"deny description to actor\"; "
            + "deny(read,search,compare) userdn=\"ldap:///" + ACTOR_DN + "\";)",
        "",
        "dn: " + STAR_SECOND_DN,
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: second",
        "sn: Beta",
        "cn: Second Beta",
        "",
        "dn: " + STAR_THIRD_DN,
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: third",
        "sn: Gamma",
        "cn: Third Gamma",
        "description: secret",
        "aci: (targetattr=\"description\")(version 3.0; acl \"deny description to actor\"; "
            + "deny(read,search,compare) userdn=\"ldap:///" + ACTOR_DN + "\";)",
        "aci: (targetattr=\"entryUUID\")(version 3.0; acl \"deny entryUUID to actor\"; "
            + "deny(read,search,compare) userdn=\"ldap:///" + ACTOR_DN + "\";)",
        "",
        // Its rule hides "description" from the actor on the versions of an entry whose content
        // matches its target filter alone, whatever their DN.
        "dn: " + VERSIONS_DN,
        "objectClass: top",
        "objectClass: organizationalUnit",
        "ou: versions",
        "aci: (targetattr=\"description\")(targetfilter=\"(employeeType=secret)\")(version 3.0; "
            + "acl \"deny description of secret entries to actor\"; "
            + "deny(read,search,compare) userdn=\"ldap:///" + ACTOR_DN + "\";)",
        "",
        "dn: " + BULK_DN,
        "objectClass: top",
        "objectClass: organizationalUnit",
        "ou: bulk");

    final List<String> bulk = new ArrayList<>();
    for (int i = 0; i < BULK_SIZE; i++)
    {
      bulk.addAll(Arrays.asList(
          "dn: uid=bulk" + i + "," + BULK_DN,
          "objectClass: top",
          "objectClass: person",
          "objectClass: organizationalPerson",
          "objectClass: inetOrgPerson",
          "uid: bulk" + i,
          "sn: " + String.format("Bulk%05d", i),
          "cn: Bulk " + i,
          "employeeNumber: " + i,
          ""));
    }
    TestCaseUtils.addEntries(bulk.toArray(new String[0]));

    actorConn = new InternalClientConnection(DN.valueOf(ACTOR_DN));
  }

  @AfterClass
  public void tearDown() throws Exception
  {
    TestCaseUtils.dsconfig("delete-backend-vlv-index", "--backend-name", "userRoot",
        "--index-name", "people-by-description");
    TestCaseUtils.dsconfig("delete-backend-vlv-index", "--backend-name", "userRoot",
        "--index-name", "bulk-by-sn");
    TestCaseUtils.clearBackend("userRoot", BASE_DN);
  }

  private static List<DN> dns(InternalSearchOperation search)
  {
    final List<DN> dns = new ArrayList<>();
    for (SearchResultEntry e : search.getSearchEntries())
    {
      dns.add(e.getName());
    }
    return dns;
  }

  private static InternalSearchOperation search(InternalClientConnection conn, SearchRequest request)
  {
    final InternalSearchOperation search = conn.processSearch(request);
    assertEquals(search.getResultCode(), ResultCode.SUCCESS, String.valueOf(search.getErrorMessage()));
    return search;
  }

  /** People in the whole subtree, which no VLV index serves: the sort runs in memory. */
  private static List<DN> sortedPeople(InternalClientConnection conn, String sortOrder) throws Exception
  {
    return dns(search(conn, newSearchRequest(PEOPLE_DN, SearchScope.WHOLE_SUBTREE, "(objectClass=person)")
        .addControl(new ServerSideSortRequestControl(sortOrder))));
  }

  private static List<Control> responseControls(InternalSearchOperation search, String oid)
  {
    final List<Control> controls = new ArrayList<>();
    for (Control c : search.getResponseControls())
    {
      if (oid.equals(c.getOID()))
      {
        controls.add(c);
      }
    }
    return controls;
  }

  private static VLVResponseControl vlvResponse(InternalSearchOperation search) throws Exception
  {
    final List<Control> controls = responseControls(search, OID_VLV_RESPONSE_CONTROL);
    assertEquals(controls.size(), 1, "Expected one VLV response control");
    final Control c = controls.get(0);
    return c instanceof LDAPControl
        ? VLVResponseControl.DECODER.decode(c.isCritical(), ((LDAPControl) c).getValue())
        : (VLVResponseControl) c;
  }

  /** The first page of the bulk entries sorted on "sn", which only their VLV index can give. */
  private static org.forgerock.opendj.ldap.requests.SearchRequest bulkPageRequest()
  {
    return org.forgerock.opendj.ldap.requests.Requests.newSearchRequest(
            BULK_DN, SearchScope.SINGLE_LEVEL, "(employeeNumber=*)", "1.1")
        .addControl(org.forgerock.opendj.ldap.controls.ServerSideSortRequestControl.newControl(true, "sn"))
        .addControl(VirtualListViewRequestControl.newOffsetControl(true, 1, 0, 0, 0, null));
  }

  /** Requests {@link #bulkPageRequest()} over LDAP as the provided user, so that its resource limits apply. */
  private static Result bulkPageOverLdap(String bindDN) throws Exception
  {
    final LDAPConnectionFactory factory = new LDAPConnectionFactory("localhost", TestCaseUtils.getServerLdapPort());
    try (Connection connection = factory.getConnection())
    {
      connection.bind(bindDN, PASSWORD.toCharArray());
      return connection.search(bulkPageRequest(),
          new ArrayList<org.forgerock.opendj.ldap.responses.SearchResultEntry>());
    }
    finally
    {
      factory.close();
    }
  }

  private static void assertBulkPageServed(Result result) throws Exception
  {
    assertEquals(result.getResultCode(), ResultCode.SUCCESS);
    final VirtualListViewResponseControl vlv =
        result.getControl(VirtualListViewResponseControl.DECODER, new DecodeOptions());
    assertNotNull(vlv, "Expected a VLV response control");
    assertEquals(vlv.getContentCount(), BULK_SIZE);
  }

  private static void replaceAttributeGlobalAcis(String remove, String add)
  {
    final List<String> args = new ArrayList<>(Arrays.asList("set-access-control-handler-prop"));
    for (String aci : ATTRIBUTE_GLOBAL_ACIS)
    {
      args.add(remove);
      args.add("global-aci:" + aci);
    }
    args.add(add);
    args.add("global-aci:" + ALL_ATTRIBUTES_GLOBAL_ACI);
    TestCaseUtils.dsconfig(args.toArray(new String[0]));
  }

  /** A version of an entry under {@link #VERSIONS_DN}, which the backend need not hold, all of them of one DN. */
  private static SearchResultEntry versionOf(String employeeType) throws Exception
  {
    return new SearchResultEntry(TestCaseUtils.makeEntry(
        "dn: uid=versioned," + VERSIONS_DN,
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: versioned",
        "sn: Sioned",
        "cn: Ver Sioned",
        "employeeType: " + employeeType,
        "description: classified"));
  }

  /**
   * The actor may not read "description", so sorting on it must not order the people by its values:
   * they come in entry ID order (charlie, alice, bob) instead of value order (charlie, bob, alice).
   */
  @Test
  public void sortOnUnreadableAttributeDoesNotLeakOrder() throws Exception
  {
    assertEquals(sortedPeople(actorConn, "description"), Arrays.asList(CHARLIE_DN, ALICE_DN, BOB_DN),
        "sorting on an attribute the caller cannot read must not reveal the order of its values");
  }

  /** A bypass-acl caller may read everything, so it still gets the value order. */
  @Test
  public void sortOnReadableAttributeIsUnchangedForRoot() throws Exception
  {
    assertEquals(sortedPeople(getRootConnection(), "description"),
        Arrays.asList(CHARLIE_DN, HIDDEN_DN, BOB_DN, ALICE_DN));
  }

  /** The actor may read "sn", so sorting on it still orders by its values. */
  @Test
  public void sortOnReadableAttributeStillWorksForActor() throws Exception
  {
    assertEquals(sortedPeople(actorConn, "sn"), Arrays.asList(ALICE_DN, BOB_DN, CHARLIE_DN));
  }

  /**
   * The actor may read the virtual "entryDN", so sorting on it still orders by its values: the sort
   * keys must see the virtual attributes of an entry, as the returned entry does.
   */
  @Test
  public void sortOnReadableVirtualAttributeStillWorksForActor() throws Exception
  {
    assertEquals(sortedPeople(actorConn, "entryDN:caseIgnoreOrderingMatch"),
        Arrays.asList(ALICE_DN, BOB_DN, CHARLIE_DN));
  }

  /**
   * "name" takes its values from its subtypes "cn" and "sn". The actor may read "name" and "sn" but
   * not "cn", so the order must follow "sn" (alice, bob, charlie), not the lower "cn" values
   * (charlie, bob, alice).
   */
  @Test
  public void sortOnSupertypeLeavesOutUnreadableSubtypeValues() throws Exception
  {
    assertEquals(sortedPeople(actorConn, "name"), Arrays.asList(ALICE_DN, BOB_DN, CHARLIE_DN),
        "a sort on a supertype must not follow the values of a subtype the caller cannot read");
  }

  /**
   * The actor may search "employeeNumber" but not read it: the sort keys need the right to read,
   * so the order must not follow its values (charlie, bob, alice).
   */
  @Test
  public void sortOnAttributeDeniedOnlyReadDoesNotLeakOrder() throws Exception
  {
    assertEquals(sortedPeople(actorConn, "employeeNumber"), Arrays.asList(CHARLIE_DN, ALICE_DN, BOB_DN));
  }

  /** Each sort key is checked on its own: the unreadable first key leaves the order to "sn". */
  @Test
  public void sortOnSeveralKeysChecksEachKey() throws Exception
  {
    assertEquals(sortedPeople(actorConn, "description,sn"), Arrays.asList(ALICE_DN, BOB_DN, CHARLIE_DN));
  }

  /** The VLV content count must leave out the entry the actor may not see. */
  @Test
  public void vlvContentCountLeavesOutEntryHiddenFromCaller() throws Exception
  {
    final InternalSearchOperation search = search(actorConn,
        newSearchRequest(PEOPLE_DN, SearchScope.WHOLE_SUBTREE, "(objectClass=person)")
            .addControl(new ServerSideSortRequestControl("sn"))
            .addControl(new VLVRequestControl(0, 3, 1, 0)));
    assertEquals(vlvResponse(search).getContentCount(), 3);
    assertEquals(dns(search), Arrays.asList(ALICE_DN, BOB_DN, CHARLIE_DN));
  }

  /**
   * This search matches the VLV index of the people. Its keys hold the hidden values (aaa, bbb,
   * mmm, zzz), so answering from them places the assertion "b" at position 2 of 4. The actor sees
   * three people and none of their values, so it must get position 1 of 3, with a single sort
   * response control.
   */
  @Test
  public void vlvTargetPositionFromIndexDoesNotDependOnUnreadableValue() throws Exception
  {
    final InternalSearchOperation search = search(actorConn,
        newSearchRequest(PEOPLE_DN, SearchScope.SINGLE_LEVEL, "(objectClass=person)")
            .addControl(new ServerSideSortRequestControl("description"))
            .addControl(new VLVRequestControl(0, 0, ByteString.valueOfUtf8("b"))));
    final VLVResponseControl vlv = vlvResponse(search);
    assertEquals(vlv.getContentCount(), 3, "the content count must leave out the hidden entry");
    assertEquals(vlv.getTargetPosition(), 1, "the target position must not depend on the hidden values");
    assertEquals(responseControls(search, OID_SERVER_SIDE_SORT_RESPONSE_CONTROL).size(), 1);
  }

  /**
   * The VLV index of the people keeps its entries in the order of their hidden values. A negative
   * offset makes the sort fail; the sort control is not critical, so the search goes on with the
   * entries unsorted, and they must come in entry ID order, not in the order of the index.
   */
  @Test
  public void sortErrorOverIndexDoesNotReturnEntriesInHiddenValueOrder() throws Exception
  {
    final InternalSearchOperation search = search(actorConn,
        newSearchRequest(PEOPLE_DN, SearchScope.SINGLE_LEVEL, "(objectClass=person)")
            .addControl(new ServerSideSortRequestControl("description"))
            .addControl(new VLVRequestControl(0, 3, -1, 0)));
    assertEquals(dns(search), Arrays.asList(CHARLIE_DN, ALICE_DN, BOB_DN),
        "after a sort error, the entries of the index must not come in the order of the hidden values");
  }

  /**
   * Neither the filter nor the scope of this search gives candidate entries, only the VLV index of
   * the bulk entries does: the actor must still get its page, sorted through its access controls.
   */
  @Test
  public void vlvOverIndexOnlyEntriesIsStillServedToCaller() throws Exception
  {
    final InternalSearchOperation search = search(actorConn,
        newSearchRequest(BULK_DN, SearchScope.SINGLE_LEVEL, "(employeeNumber=*)")
            .addControl(new ServerSideSortRequestControl("sn"))
            .addControl(new VLVRequestControl(0, 0, 1, 0)));
    assertEquals(vlvResponse(search).getContentCount(), BULK_SIZE);
    assertEquals(dns(search), Arrays.asList(DN.valueOf("uid=bulk0," + BULK_DN)));
  }

  /**
   * Sorting the entries of a VLV index through the caller's access controls reads all of them, so
   * it must not read more than the caller's lookthrough limit; the refusal answers both controls.
   */
  @Test
  public void vlvOverIndexHonoursCallerLookthroughLimit() throws Exception
  {
    try
    {
      bulkPageOverLdap(LIMITED_DN);
      fail("Expected the lookthrough limit of " + LIMITED_LOOKTHROUGH + " to be exceeded");
    }
    catch (LdapException e)
    {
      final Result result = e.getResult();
      assertEquals(result.getResultCode(), ResultCode.ADMIN_LIMIT_EXCEEDED);
      final org.forgerock.opendj.ldap.controls.ServerSideSortResponseControl sort = result.getControl(
          org.forgerock.opendj.ldap.controls.ServerSideSortResponseControl.DECODER, new DecodeOptions());
      assertNotNull(sort, "Expected a sort response control");
      assertEquals(sort.getResult(), ResultCode.ADMIN_LIMIT_EXCEEDED);
      final VirtualListViewResponseControl vlv =
          result.getControl(VirtualListViewResponseControl.DECODER, new DecodeOptions());
      assertNotNull(vlv, "Expected a VLV response control");
      assertEquals(vlv.getResult(), ResultCode.ADMIN_LIMIT_EXCEEDED);
    }
  }

  /** The same refusal for a sort without a VLV request answers the sort control alone. */
  @Test
  public void sortOverIndexHonoursCallerLookthroughLimit() throws Exception
  {
    final LDAPConnectionFactory factory = new LDAPConnectionFactory("localhost", TestCaseUtils.getServerLdapPort());
    try (Connection connection = factory.getConnection())
    {
      connection.bind(LIMITED_DN, PASSWORD.toCharArray());
      connection.search(org.forgerock.opendj.ldap.requests.Requests.newSearchRequest(
              BULK_DN, SearchScope.SINGLE_LEVEL, "(employeeNumber=*)", "1.1")
              .addControl(org.forgerock.opendj.ldap.controls.ServerSideSortRequestControl.newControl(true, "sn")),
          new ArrayList<org.forgerock.opendj.ldap.responses.SearchResultEntry>());
      fail("Expected the lookthrough limit of " + LIMITED_LOOKTHROUGH + " to be exceeded");
    }
    catch (LdapException e)
    {
      final Result result = e.getResult();
      assertEquals(result.getResultCode(), ResultCode.ADMIN_LIMIT_EXCEEDED);
      final org.forgerock.opendj.ldap.controls.ServerSideSortResponseControl sort = result.getControl(
          org.forgerock.opendj.ldap.controls.ServerSideSortResponseControl.DECODER, new DecodeOptions());
      assertNotNull(sort, "Expected a sort response control");
      assertEquals(sort.getResult(), ResultCode.ADMIN_LIMIT_EXCEEDED);
      assertNull(result.getControl(VirtualListViewResponseControl.DECODER, new DecodeOptions()),
          "A search without a VLV request must not get a VLV response control");
    }
    finally
    {
      factory.close();
    }
  }

  /** A lookthrough limit equal to the size of the VLV index lets the caller read all of it. */
  @Test
  public void vlvOverIndexIsServedAtCallerLookthroughLimit() throws Exception
  {
    assertBulkPageServed(bulkPageOverLdap(EXACT_DN));
  }

  /** No lookthrough limit lets the caller read the whole VLV index. */
  @Test
  public void vlvOverIndexIsServedWithoutCallerLookthroughLimit() throws Exception
  {
    assertBulkPageServed(bulkPageOverLdap(UNLIMITED_DN));
  }

  /**
   * maySend() leaves on the operation a shortcut for the entry about to be returned: when only rules
   * on all attributes matched, the entry's attributes are not checked one by one. Once the global
   * ACIs that name attributes are gone, the second entry, outside the page, sets that shortcut while
   * the sort checks every candidate; the first entry, on the page, must still have its hidden
   * "description" stripped.
   */
  @Test(priority = 1)
  public void vlvPageEntryKeepsItsAttributeChecksAfterSort() throws Exception
  {
    replaceAttributeGlobalAcis("--remove", "--add");
    try
    {
      final InternalSearchOperation search = search(actorConn,
          newSearchRequest(STAR_DN, SearchScope.SINGLE_LEVEL, "(objectClass=person)")
              .addAttribute("description")
              .addControl(new ServerSideSortRequestControl("sn"))
              .addControl(new VLVRequestControl(0, 0, 1, 0)));
      assertEquals(dns(search), Arrays.asList(STAR_FIRST_DN));
      assertFalse(search.getSearchEntries().getFirst().hasAttribute(AttributeDescription.valueOf("description")),
          "the returned entry must not carry the attribute the caller may not read");
    }
    finally
    {
      replaceAttributeGlobalAcis("--add", "--remove");
    }
  }

  /**
   * The same shortcut, without any sort: the second entry is returned before the third one, and the
   * third one must still have its hidden "description" stripped.
   */
  @Test(priority = 1)
  public void eachReturnedEntryKeepsItsAttributeChecks() throws Exception
  {
    replaceAttributeGlobalAcis("--remove", "--add");
    try
    {
      final InternalSearchOperation search = search(actorConn,
          newSearchRequest(STAR_DN, SearchScope.SINGLE_LEVEL, "(objectClass=person)").addAttribute("description"));
      assertEquals(search.getSearchEntries().size(), 3);
      for (SearchResultEntry e : search.getSearchEntries())
      {
        assertFalse(e.hasAttribute(AttributeDescription.valueOf("description")),
            e.getName() + " must not carry the attribute the caller may not read");
      }
    }
    finally
    {
      replaceAttributeGlobalAcis("--add", "--remove");
    }
  }

  /**
   * The operational attribute half of the same shortcut: the second entry, matched by rules on all
   * attributes alone, is returned before the third one, which must still have its hidden "entryUUID"
   * stripped.
   */
  @Test(priority = 1)
  public void eachReturnedEntryKeepsItsOperationalAttributeChecks() throws Exception
  {
    replaceAttributeGlobalAcis("--remove", "--add");
    try
    {
      final InternalSearchOperation search = search(actorConn,
          newSearchRequest(STAR_DN, SearchScope.SINGLE_LEVEL, "(objectClass=person)").addAttribute("+"));
      assertEquals(dns(search), Arrays.asList(STAR_FIRST_DN, STAR_SECOND_DN, STAR_THIRD_DN));
      final AttributeDescription entryUUID = AttributeDescription.valueOf("entryUUID");
      assertTrue(search.getSearchEntries().get(1).hasAttribute(entryUUID),
          "the caller may read the operational attributes of " + STAR_SECOND_DN);
      assertFalse(search.getSearchEntries().get(2).hasAttribute(entryUUID),
          STAR_THIRD_DN + " must not carry the operational attribute the caller may not read");
    }
    finally
    {
      replaceAttributeGlobalAcis("--add", "--remove");
    }
  }

  /**
   * The shortcut holds the very entry maySend() checked, not its DN: two versions of one entry, which
   * the interleaved persistent search notifications of two modifications of it hand out, keep their
   * own checks. Only the later version is matched by rules on all attributes alone; the earlier one,
   * whose content a target filter rule hides "description" of, must still have it stripped.
   */
  @Test(priority = 1)
  public void entryVersionKeepsItsAttributeChecksAfterAnotherVersionOfItsDN() throws Exception
  {
    replaceAttributeGlobalAcis("--remove", "--add");
    try
    {
      final AccessControlHandler<?> handler = AccessControlConfigManager.getInstance().getAccessControlHandler();
      final InternalSearchOperation search = new InternalSearchOperation(actorConn, nextOperationID(),
          nextMessageID(), newSearchRequest(VERSIONS_DN, SearchScope.SINGLE_LEVEL, "(objectClass=person)"));
      final SearchResultEntry secret = versionOf("secret");
      final SearchResultEntry plain = versionOf("plain");
      assertTrue(handler.maySend(search, secret));
      assertTrue(handler.maySend(search, plain));

      final SearchResultEntry filtered = new SearchResultEntry(secret.duplicate(false));
      handler.filterEntry(search, secret, filtered);
      assertTrue(filtered.hasAttribute(AttributeDescription.valueOf("sn")),
          "the caller may read the other attributes of the entry");
      assertFalse(filtered.hasAttribute(AttributeDescription.valueOf("description")),
          "a version of an entry must not carry the attribute the caller may not read on it");
    }
    finally
    {
      replaceAttributeGlobalAcis("--add", "--remove");
    }
  }

  /**
   * With access control disabled, the sort of a non-root caller need not go through it: the VLV
   * index answers, so its size may exceed the caller's lookthrough limit.
   */
  @Test(priority = 2)
  public void vlvOverIndexIsAnsweredByIndexWithAccessControlDisabled() throws Exception
  {
    TestCaseUtils.dsconfig("set-access-control-handler-prop", "--set", "enabled:false");
    try
    {
      assertBulkPageServed(bulkPageOverLdap(LIMITED_DN));
    }
    finally
    {
      TestCaseUtils.dsconfig("set-access-control-handler-prop", "--set", "enabled:true");
    }
  }
}
