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
 *      Copyright 2012-2013 ForgeRock AS
 */
package org.opends.server.extensions;



import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.server.TestCaseUtils;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.meta.EntityTagVirtualAttributeCfgDefn.ChecksumAlgorithm;
import org.opends.server.admin.std.meta.VirtualAttributeCfgDefn;
import org.opends.server.admin.std.meta.VirtualAttributeCfgDefn.ConflictBehavior;
import org.opends.server.admin.std.meta.VirtualAttributeCfgDefn.Scope;
import org.opends.server.admin.std.server.EntityTagVirtualAttributeCfg;
import org.opends.server.admin.std.server.VirtualAttributeCfg;
import org.opends.server.controls.LDAPAssertionRequestControl;
import org.opends.server.controls.LDAPPostReadRequestControl;
import org.opends.server.controls.LDAPPostReadResponseControl;
import org.opends.server.controls.LDAPPreReadRequestControl;
import org.opends.server.controls.LDAPPreReadResponseControl;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.core.SearchOperationWrapper;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.schema.DirectoryStringSyntax;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.AttributeValues;
import org.opends.server.types.ByteString;
import org.opends.server.types.ConditionResult;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.ModificationType;
import org.opends.server.types.RawAttribute;
import org.opends.server.types.RawModification;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchScope;
import org.opends.server.types.VirtualAttributeRule;
import org.opends.server.util.StaticUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;



/**
 * A set of test cases for the entity tag virtual attribute provider.
 */
public class EntityTagVirtualAttributeProviderTestCase extends
    ExtensionsTestCase
{
  private static final String DESCRIPTION = "description";
  private static final String ETAG = "etag";

  private final AttributeValue dummyValue = AttributeValues.create(
      ByteString.valueOf("dummy"), ByteString.valueOf("dummy"));
  private final EntityTagVirtualAttributeProvider provider = new EntityTagVirtualAttributeProvider();
  private boolean changeListenerRemoved = false;
  private boolean changeListenerAdded = false;

  private final EntityTagVirtualAttributeCfg config = new EntityTagVirtualAttributeCfg()
  {
    private final TreeSet<AttributeType> excludedAttributes = new TreeSet<AttributeType>();



    public void addChangeListener(
        final ConfigurationChangeListener<VirtualAttributeCfg> listener)
    {
      // Should not be called.
      throw new IllegalStateException();
    }



    public void addEntityTagChangeListener(
        final ConfigurationChangeListener<EntityTagVirtualAttributeCfg> listener)
    {
      changeListenerAdded = true;
    }



    public Class<? extends EntityTagVirtualAttributeCfg> configurationClass()
    {
      // Not needed.
      return null;
    }



    public DN dn()
    {
      // Not needed.
      return null;
    }



    public AttributeType getAttributeType()
    {
      // Not needed.
      return null;
    }



    public SortedSet<DN> getBaseDN()
    {
      // Not needed.
      return null;
    }



    public ChecksumAlgorithm getChecksumAlgorithm()
    {
      return ChecksumAlgorithm.ADLER_32;
    }



    public ConflictBehavior getConflictBehavior()
    {
      // Not needed.
      return null;
    }



    public SortedSet<AttributeType> getExcludedAttribute()
    {
      return excludedAttributes;
    }



    public SortedSet<String> getFilter()
    {
      // Not needed.
      return null;
    }



    public SortedSet<DN> getGroupDN()
    {
      // Not needed.
      return null;
    }



    public String getJavaClass()
    {
      // Not needed.
      return null;
    }



    public Scope getScope()
    {
      // Not needed.
      return null;
    }



    public boolean isEnabled()
    {
      return true;
    }



    public void removeChangeListener(
        final ConfigurationChangeListener<VirtualAttributeCfg> listener)
    {
      // Should not be called.
      throw new IllegalStateException();
    }



    public void removeEntityTagChangeListener(
        final ConfigurationChangeListener<EntityTagVirtualAttributeCfg> listener)
    {
      changeListenerRemoved = true;
    }
  };



  /**
   * Ensures that the Directory Server is running.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @BeforeClass()
  public void startServer() throws Exception
  {
    TestCaseUtils.startServer();

    // Initialize the provider.
    config.getExcludedAttribute().add(
        DirectoryServer.getAttributeType("modifytimestamp"));
    provider.initializeVirtualAttributeProvider(config);
  }



  /**
   * Tests that approximate matching is not supported.
   */
  @Test
  public void testApproximatelyEqualTo()
  {
    assertEquals(provider.approximatelyEqualTo(null, null, null),
        ConditionResult.UNDEFINED);
  }



  /**
   * Tests that finalization removes the change listener.
   */
  @Test
  public void testFinalizeVirtualAttributeProvider()
  {
    provider.finalizeVirtualAttributeProvider();
    assertTrue(changeListenerRemoved);
  }



  /**
   * Tests the getValues method returns an ETag whose value represents a 64-bit
   * non-zero long encoded as hex.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test
  public void testGetValuesBasic() throws Exception
  {
    final Entry e = TestCaseUtils.makeEntry("dn: dc=example,dc=com",
        "objectClass: top", "objectClass: domain", "dc: example");
    getEntityTag(e);
  }



  /**
   * Tests the getValues method returns a different value for entries which are
   * different.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test
  public void testGetValuesDifferent() throws Exception
  {
    final Entry e1 = TestCaseUtils.makeEntry("dn: dc=example1,dc=com",
        "objectClass: top", "objectClass: domain", "dc: example1");
    final Entry e2 = TestCaseUtils.makeEntry("dn: dc=example2,dc=com",
        "objectClass: top", "objectClass: domain", "dc: example2");
    assertFalse(getEntityTag(e1).equals(getEntityTag(e2)));
  }



  /**
   * Tests the getValues method ignores excluded attributes.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test
  public void testGetValuesIgnoresExcludedAttributes() throws Exception
  {
    final Entry e1 = TestCaseUtils.makeEntry("dn: dc=example,dc=com",
        "objectClass: top", "objectClass: domain", "dc: example");
    final Entry e2 = TestCaseUtils.makeEntry("dn: dc=example,dc=com",
        "objectClass: top", "objectClass: domain", "dc: example",
        "modifyTimestamp: 20120222232918Z");
    assertEquals(getEntityTag(e1), getEntityTag(e2));
  }



  /**
   * Tests the getValues method returns the same value for entries having the
   * same content but with attributes in a different order.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test
  public void testGetValuesNormalizedOrder() throws Exception
  {
    final Entry e1 = TestCaseUtils.makeEntry("dn: dc=example,dc=com",
        "objectClass: top", "objectClass: domain", "description: one",
        "description: two", "dc: example");
    final Entry e2 = TestCaseUtils.makeEntry("dn: dc=example,dc=com",
        "objectClass: top", "objectClass: domain", "dc: example",
        "description: two", "description: one");
    assertEquals(getEntityTag(e1), getEntityTag(e2));
  }



  /**
   * Tests the getValues method returns the same value for different instances
   * of the same entry.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test
  public void testGetValuesRepeatable() throws Exception
  {
    final Entry e1 = TestCaseUtils.makeEntry("dn: dc=example,dc=com",
        "objectClass: top", "objectClass: domain", "dc: example");
    final Entry e2 = TestCaseUtils.makeEntry("dn: dc=example,dc=com",
        "objectClass: top", "objectClass: domain", "dc: example");
    assertEquals(getEntityTag(e1), getEntityTag(e2));
  }



  /**
   * Tests that ordering matching is not supported.
   */
  @Test
  public void testGreaterThanOrEqualTo()
  {
    assertEquals(provider.greaterThanOrEqualTo(null, null, null),
        ConditionResult.UNDEFINED);
  }



  /**
   * Tests hasAllValues() membership.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test
  public void testHasAllValues() throws Exception
  {
    final Entry e = TestCaseUtils.makeEntry("dn: dc=example,dc=com",
        "objectClass: top", "objectClass: domain", "dc: example");
    final AttributeValue value = getEntityTag(e);
    assertTrue(provider.hasAllValues(e, null,
        Collections.<AttributeValue> emptySet()));
    assertTrue(provider.hasAllValues(e, null, Collections.singleton(value)));
    assertFalse(provider.hasAllValues(e, null,
        Collections.singleton(dummyValue)));
    assertFalse(provider
        .hasAllValues(e, null, Arrays.asList(value, dummyValue)));
  }



  /**
   * Tests hasAnyValues() membership.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test
  public void testHasAnyValue() throws Exception
  {
    final Entry e = TestCaseUtils.makeEntry("dn: dc=example,dc=com",
        "objectClass: top", "objectClass: domain", "dc: example");
    final AttributeValue value = getEntityTag(e);
    assertTrue(provider.hasAnyValue(e, null, Collections.singleton(value)));
    assertFalse(provider
        .hasAnyValue(e, null, Collections.singleton(dummyValue)));
    assertTrue(provider.hasAnyValue(e, null, Arrays.asList(value, dummyValue)));
  }



  /**
   * Tests that the etags are always present.
   */
  @Test
  public void testHasValue1()
  {
    assertTrue(provider.hasValue(null, null));
  }



  /**
   * Tests testHasValue membership.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test
  public void testHasValue2() throws Exception
  {
    final Entry e = TestCaseUtils.makeEntry("dn: dc=example,dc=com",
        "objectClass: top", "objectClass: domain", "dc: example");
    final AttributeValue value = getEntityTag(e);
    assertTrue(provider.hasValue(e, null, value));
    assertFalse(provider.hasValue(e, null, dummyValue));
  }



  /**
   * Tests that initialization adds the change listener.
   */
  @Test
  public void testInitializeVirtualAttributeProvider()
  {
    // This was actually done during initialization of this test. Check that the
    // listener was registered.
    assertTrue(changeListenerAdded);
  }



  /**
   * Tests that isConfigurationAcceptable always returns true.
   */
  @Test
  public void testIsConfigurationAcceptable()
  {
    assertTrue(provider.isConfigurationAcceptable(config, null));
  }



  /**
   * Tests that the etags are single-valued.
   */
  @Test
  public void testIsMultiValued()
  {
    assertFalse(provider.isMultiValued());
  }



  /**
   * Tests that searching based on etag filters is not supported.
   */
  @Test
  public void testIsSearchable()
  {
    assertFalse(provider.isSearchable(null, null, false));
    assertFalse(provider.isSearchable(null, null, true));
  }



  /**
   * Tests that ordering matching is not supported.
   */
  @Test
  public void testLessThanOrEqualTo()
  {
    assertEquals(provider.lessThanOrEqualTo(null, null, null),
        ConditionResult.UNDEFINED);
  }



  /**
   * Tests that substring matching is not supported.
   */
  @Test
  public void testMatchesSubstring()
  {
    assertEquals(provider.matchesSubstring(null, null, null, null, null),
        ConditionResult.UNDEFINED);
  }



  /**
   * Tests that searching based on etag filters is not supported.
   */
  @Test
  public void testProcessSearch()
  {
    final SearchOperation search = new SearchOperationWrapper(null)
    {
      ResultCode resultCode = null;
      MessageBuilder message = null;



      /**
       * {@inheritDoc}
       */
      public void appendErrorMessage(final Message message)
      {
        this.message = new MessageBuilder(message);
      }



      /**
       * {@inheritDoc}
       */
      public MessageBuilder getErrorMessage()
      {
        return message;
      }



      /**
       * @return the resultCode
       */
      public ResultCode getResultCode()
      {
        return resultCode;
      }



      /**
       * {@inheritDoc}
       */
      public void setResultCode(final ResultCode resultCode)
      {
        this.resultCode = resultCode;
      }

    };

    VirtualAttributeRule rule = new VirtualAttributeRule(
        DirectoryServer.getAttributeType(ETAG), provider,
        Collections.<DN> emptySet(), SearchScope.WHOLE_SUBTREE,
        Collections.<DN> emptySet(), Collections.<SearchFilter> emptySet(),
        VirtualAttributeCfgDefn.ConflictBehavior.REAL_OVERRIDES_VIRTUAL);
    provider.processSearch(rule, search);
    assertEquals(search.getResultCode(), ResultCode.UNWILLING_TO_PERFORM);
    assertNotNull(search.getErrorMessage());
  }



  /**
   * Simulates the main use case for entity tag support: optimistic concurrency.
   * <p>
   * This test reads an entry requesting its etag, then performs an update using
   * an assertion control to prevent the change from being applied if the etag
   * has changed since the read was performed.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test
  public void testOptimisticConcurrency() throws Exception
  {
    // Use an internal connection.
    AttributeType etagType = DirectoryServer.getAttributeType(ETAG);
    AttributeType descrType = DirectoryServer.getAttributeType(DESCRIPTION);
    String userDN = "uid=test.user,ou=People,o=test";
    InternalClientConnection conn = InternalClientConnection
        .getRootConnection();

    // Create a test backend containing the user entry to be modified.
    TestCaseUtils.initializeTestBackend(true);

    // @formatter:off
    TestCaseUtils.addEntries(
      "dn: ou=People,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: People",
      "",
      "dn: uid=test.user,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test.user",
      "givenName: Test",
      "sn: User",
      "cn: Test User",
      "userPassword: password");
    // @formatter:on

    // Read the user entry and get the etag.
    Entry e1 = readEntry(conn, userDN);
    String etag1 = e1
        .getAttributeValue(etagType, DirectoryStringSyntax.DECODER);
    assertNotNull(etag1);

    // Apply a change using the assertion control for optimistic concurrency.
    List<RawModification> mods = Collections
        .<RawModification> singletonList(new LDAPModification(
            ModificationType.REPLACE, RawAttribute.create(DESCRIPTION,
                "first modify")));
    List<Control> ctrls = Collections
        .<Control> singletonList(new LDAPAssertionRequestControl(true,
            LDAPFilter.createEqualityFilter(ETAG, ByteString.valueOf(etag1))));
    ModifyOperation modifyOperation = conn.processModify(userDN, mods, ctrls);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);

    // Reread the entry and check that the description has been added and that
    // the etag has changed.
    Entry e2 = readEntry(conn, userDN);

    String etag2 = e2
        .getAttributeValue(etagType, DirectoryStringSyntax.DECODER);
    assertNotNull(etag2);
    assertFalse(etag1.equals(etag2));

    String description2 = e2.getAttributeValue(descrType,
        DirectoryStringSyntax.DECODER);
    assertNotNull(description2);
    assertEquals(description2, "first modify");

    // Simulate a concurrent update: perform another update using the old etag.
    mods = Collections.<RawModification> singletonList(new LDAPModification(
        ModificationType.REPLACE, RawAttribute.create(DESCRIPTION,
            "second modify")));
    modifyOperation = conn.processModify(userDN, mods, ctrls);
    assertEquals(modifyOperation.getResultCode(), ResultCode.ASSERTION_FAILED);

    // Reread the entry and check that the description and etag have not
    // changed.
    Entry e3 = readEntry(conn, userDN);

    String etag3 = e3
        .getAttributeValue(etagType, DirectoryStringSyntax.DECODER);
    assertNotNull(etag3);
    assertEquals(etag2, etag3);

    String description3 = e3.getAttributeValue(descrType,
        DirectoryStringSyntax.DECODER);
    assertNotNull(description3);
    assertEquals(description3, description2);
  }



  /**
   * Tests that the etag returned with a pre-read control after a modify
   * operation is correct. See OPENDJ-861.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test
  public void testPreReadControl() throws Exception
  {
    AttributeType etagType = DirectoryServer.getAttributeType(ETAG);
    AttributeType descrType = DirectoryServer.getAttributeType(DESCRIPTION);
    String userDN = "uid=test.user,ou=People,o=test";

    // Use an internal connection.
    InternalClientConnection conn = InternalClientConnection
        .getRootConnection();

    // Create a test backend containing the user entry to be modified.
    TestCaseUtils.initializeTestBackend(true);

    // @formatter:off
    TestCaseUtils.addEntries(
      "dn: ou=People,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: People",
      "",
      "dn: uid=test.user,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test.user",
      "givenName: Test",
      "sn: User",
      "cn: Test User",
      "userPassword: password",
      "description: initial value");
    // @formatter:on

    // Read the user entry and get the etag.
    Entry e1 = readEntry(conn, userDN);
    String etag1 = e1
        .getAttributeValue(etagType, DirectoryStringSyntax.DECODER);
    assertNotNull(etag1);

    // Apply a change using the pre and post read controls.
    List<RawModification> mods = Collections
        .<RawModification> singletonList(new LDAPModification(
            ModificationType.REPLACE, RawAttribute.create(DESCRIPTION,
                "modified value")));
    List<Control> ctrls = singletonList((Control) new LDAPPreReadRequestControl(
        true, singleton(ETAG)));
    ModifyOperation modifyOperation = conn.processModify(userDN, mods, ctrls);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);

    // Reread the entry and check that the description has been added and that
    // the etag has changed.
    Entry e2 = readEntry(conn, userDN);

    String etag2 = e2
        .getAttributeValue(etagType, DirectoryStringSyntax.DECODER);
    assertNotNull(etag2);
    assertFalse(etag1.equals(etag2));

    String description2 = e2.getAttributeValue(descrType,
        DirectoryStringSyntax.DECODER);
    assertNotNull(description2);
    assertEquals(description2, "modified value");

    // Now check that the pre-read is the same as the initial etag.
    LDAPPreReadResponseControl preReadControl = null;
    for (Control control : modifyOperation.getResponseControls())
    {
      if (control instanceof LDAPPreReadResponseControl)
      {
        preReadControl = (LDAPPreReadResponseControl) control;
        break;
      }
    }
    assertNotNull(preReadControl);
    String etagPreRead = preReadControl.getSearchEntry().getAttributeValue(
        etagType, DirectoryStringSyntax.DECODER);
    assertEquals(etagPreRead, etag1);
  }



  /**
   * Tests that the etag returned with a post-read control after a modify
   * operation is correct. See OPENDJ-861.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test
  public void testPostReadControl() throws Exception
  {
    AttributeType etagType = DirectoryServer.getAttributeType(ETAG);
    AttributeType descrType = DirectoryServer.getAttributeType(DESCRIPTION);
    String userDN = "uid=test.user,ou=People,o=test";

    // Use an internal connection.
    InternalClientConnection conn = InternalClientConnection
        .getRootConnection();

    // Create a test backend containing the user entry to be modified.
    TestCaseUtils.initializeTestBackend(true);

    // @formatter:off
    TestCaseUtils.addEntries(
      "dn: ou=People,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: People",
      "",
      "dn: uid=test.user,ou=People,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test.user",
      "givenName: Test",
      "sn: User",
      "cn: Test User",
      "userPassword: password",
      "description: initial value");
    // @formatter:on

    // Read the user entry and get the etag.
    Entry e1 = readEntry(conn, userDN);
    String etag1 = e1
        .getAttributeValue(etagType, DirectoryStringSyntax.DECODER);
    assertNotNull(etag1);

    // Apply a change using the pre and post read controls.
    List<RawModification> mods = Collections
        .<RawModification> singletonList(new LDAPModification(
            ModificationType.REPLACE, RawAttribute.create(DESCRIPTION,
                "modified value")));
    List<Control> ctrls = singletonList((Control) new LDAPPostReadRequestControl(
        true, singleton(ETAG)));
    ModifyOperation modifyOperation = conn.processModify(userDN, mods, ctrls);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);

    // Reread the entry and check that the description has been added and that
    // the etag has changed.
    Entry e2 = readEntry(conn, userDN);

    String etag2 = e2
        .getAttributeValue(etagType, DirectoryStringSyntax.DECODER);
    assertNotNull(etag2);
    assertFalse(etag1.equals(etag2));

    String description2 = e2.getAttributeValue(descrType,
        DirectoryStringSyntax.DECODER);
    assertNotNull(description2);
    assertEquals(description2, "modified value");

    // Now check that the post-read is the same as the initial etag.
    LDAPPostReadResponseControl postReadControl = null;
    for (Control control : modifyOperation.getResponseControls())
    {
      if (control instanceof LDAPPostReadResponseControl)
      {
        postReadControl = (LDAPPostReadResponseControl) control;
        break;
      }
    }
    assertNotNull(postReadControl);
    String etagPostRead = postReadControl.getSearchEntry().getAttributeValue(
        etagType, DirectoryStringSyntax.DECODER);
    assertEquals(etagPostRead, etag2);
  }



  private Entry readEntry(InternalClientConnection conn, String userDN)
      throws DirectoryException
  {
    LinkedHashSet<String> attrList = new LinkedHashSet<String>(2);
    attrList.add("*");
    attrList.add(ETAG);
    InternalSearchOperation searchOperation = conn.processSearch(userDN,
        SearchScope.BASE_OBJECT, DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0,
        false, "(objectClass=*)", attrList);
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    assertEquals(searchOperation.getSearchEntries().size(), 1);
    Entry e = searchOperation.getSearchEntries().get(0);
    assertNotNull(e);
    return e;
  }



  private AttributeValue getEntityTag(final Entry e)
  {
    final Set<AttributeValue> values = provider.getValues(e, null);
    assertEquals(values.size(), 1);
    final AttributeValue value = values.iterator().next();
    final ByteString bs = value.getValue();
    assertEquals(bs.length(), 16);
    boolean gotNonZeroByte = false;
    for (int i = 0; i < 16; i++)
    {
      assertTrue(StaticUtils.isHexDigit(bs.byteAt(i)));
      if (bs.byteAt(i) != 0x30)
      {
        gotNonZeroByte = true;
      }
    }
    assertTrue(gotNonZeroByte);
    return value;
  }

}
