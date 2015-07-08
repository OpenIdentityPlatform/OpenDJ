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
 *      Portions Copyright 2012-2015 ForgeRock AS
 */
package org.opends.server.extensions;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.mockito.ArgumentCaptor;
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
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.Requests;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.Attributes;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.VirtualAttributeRule;
import org.opends.server.util.StaticUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static java.util.Collections.*;

import static org.mockito.Mockito.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.testng.Assert.*;

/**
 * A set of test cases for the entity tag virtual attribute provider.
 */
public class EntityTagVirtualAttributeProviderTestCase extends ExtensionsTestCase
{
  private static final String DESCRIPTION = "description";
  private static final String ETAG = "etag";

  private final ByteString dummyValue = ByteString.valueOf("dummy");
  private final EntityTagVirtualAttributeProvider provider = new EntityTagVirtualAttributeProvider();
  private boolean changeListenerRemoved;
  private boolean changeListenerAdded;

  private final EntityTagVirtualAttributeCfg config = new EntityTagVirtualAttributeCfg()
  {
    private final TreeSet<AttributeType> excludedAttributes = new TreeSet<>();

    @Override
    public void addChangeListener(
        final ConfigurationChangeListener<VirtualAttributeCfg> listener)
    {
      // Should not be called.
      throw new IllegalStateException();
    }

    @Override
    public void addEntityTagChangeListener(
        final ConfigurationChangeListener<EntityTagVirtualAttributeCfg> listener)
    {
      changeListenerAdded = true;
    }

    @Override
    public Class<? extends EntityTagVirtualAttributeCfg> configurationClass()
    {
      // Not needed.
      return null;
    }

    @Override
    public DN dn()
    {
      // Not needed.
      return null;
    }

    @Override
    public AttributeType getAttributeType()
    {
      // Not needed.
      return null;
    }

    @Override
    public SortedSet<DN> getBaseDN()
    {
      // Not needed.
      return null;
    }

    @Override
    public ChecksumAlgorithm getChecksumAlgorithm()
    {
      return ChecksumAlgorithm.ADLER_32;
    }

    @Override
    public ConflictBehavior getConflictBehavior()
    {
      // Not needed.
      return null;
    }

    @Override
    public SortedSet<AttributeType> getExcludedAttribute()
    {
      return excludedAttributes;
    }

    @Override
    public SortedSet<String> getFilter()
    {
      // Not needed.
      return null;
    }

    @Override
    public SortedSet<DN> getGroupDN()
    {
      // Not needed.
      return null;
    }

    @Override
    public String getJavaClass()
    {
      // Not needed.
      return null;
    }

    @Override
    public Scope getScope()
    {
      // Not needed.
      return null;
    }

    @Override
    public boolean isEnabled()
    {
      return true;
    }

    @Override
    public void removeChangeListener(
        final ConfigurationChangeListener<VirtualAttributeCfg> listener)
    {
      // Should not be called.
      throw new IllegalStateException();
    }

    @Override
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
  @BeforeClass
  public void startServer() throws Exception
  {
    TestCaseUtils.startServer();

    // Initialize the provider.
    config.getExcludedAttribute().add(DirectoryServer.getAttributeType("modifytimestamp"));
    provider.initializeVirtualAttributeProvider(config);
  }

  /**
   * Tests that approximate matching is not supported.
   */
  @Test
  public void testApproximatelyEqualTo()
  {
    assertEquals(provider.approximatelyEqualTo(null, null, null), ConditionResult.UNDEFINED);
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
    getEntityTag(e, getRule());
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
    VirtualAttributeRule rule = getRule();
    assertFalse(getEntityTag(e1, rule).equals(getEntityTag(e2, rule)));
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
    VirtualAttributeRule rule = getRule();
    assertEquals(getEntityTag(e1, rule), getEntityTag(e2, rule));
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
    VirtualAttributeRule rule = getRule();
    assertEquals(getEntityTag(e1, rule), getEntityTag(e2, rule));
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
    VirtualAttributeRule rule = getRule();
    assertEquals(getEntityTag(e1, rule), getEntityTag(e2, rule));
  }

  /**
   * Tests that ordering matching is not supported.
   */
  @Test
  public void testGreaterThanOrEqualTo()
  {
    assertEquals(provider.greaterThanOrEqualTo(null, null, null), ConditionResult.UNDEFINED);
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
    VirtualAttributeRule rule = getRule();
    final ByteString value = getEntityTag(e, rule);
    assertTrue(provider.hasAllValues(e, rule, Collections.<ByteString> emptySet()));
    assertTrue(provider.hasAllValues(e, rule, Collections.singleton(value)));
    assertFalse(provider.hasAllValues(e, rule, Collections.singleton(dummyValue)));
    assertFalse(provider.hasAllValues(e, rule, Arrays.asList(value, dummyValue)));
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
    VirtualAttributeRule rule = getRule();
    final ByteString value = getEntityTag(e, rule);
    assertTrue(provider.hasValue(e, rule, value));
    assertFalse(provider.hasValue(e, rule, dummyValue));
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
    assertEquals(provider.lessThanOrEqualTo(null, null, null), ConditionResult.UNDEFINED);
  }

  /**
   * Tests that substring matching is not supported.
   */
  @Test
  public void testMatchesSubstring()
  {
    assertEquals(provider.matchesSubstring(null, null, null, null, null), ConditionResult.UNDEFINED);
  }

  /**
   * Tests that searching based on etag filters is not supported.
   */
  @Test
  public void testProcessSearch()
  {
    final SearchOperation searchOp = mock(SearchOperation.class);

    VirtualAttributeRule rule = new VirtualAttributeRule(
        DirectoryServer.getAttributeType(ETAG), provider,
        Collections.<DN> emptySet(), SearchScope.WHOLE_SUBTREE,
        Collections.<DN> emptySet(), Collections.<SearchFilter> emptySet(),
        VirtualAttributeCfgDefn.ConflictBehavior.REAL_OVERRIDES_VIRTUAL);
    provider.processSearch(rule, searchOp);

    final ArgumentCaptor<ResultCode> resultCode = ArgumentCaptor.forClass(ResultCode.class);
    verify(searchOp).setResultCode(resultCode.capture());
    assertEquals(resultCode.getValue(), ResultCode.UNWILLING_TO_PERFORM);

    final ArgumentCaptor<LocalizableMessage> errorMsg = ArgumentCaptor.forClass(LocalizableMessage.class);
    verify(searchOp).appendErrorMessage(errorMsg.capture());
    assertNotNull(errorMsg.getValue());
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
    DN userDN = DN.valueOf("uid=test.user,ou=People,o=test");
    InternalClientConnection conn = getRootConnection();

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
    Entry e1 = readEntry(userDN);
    String etag1 = e1.parseAttribute(ETAG).asString();
    assertNotNull(etag1);

    // Apply a change using the assertion control for optimistic concurrency.
    Attribute attr = Attributes.create(DESCRIPTION, "first modify");
    List<Modification> mods = newArrayList(new Modification(ModificationType.REPLACE, attr));
    Control c = new LDAPAssertionRequestControl(true, LDAPFilter.createEqualityFilter(ETAG, ByteString.valueOf(etag1)));
    List<Control> ctrls = Collections.singletonList(c);
    ModifyOperation modifyOperation = conn.processModify(userDN, mods, ctrls);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);

    // Reread the entry and check that the description has been added and that
    // the etag has changed.
    Entry e2 = readEntry(userDN);

    String etag2 = e2.parseAttribute(ETAG).asString();
    assertNotNull(etag2);
    assertFalse(etag1.equals(etag2));

    String description2 = e2.parseAttribute(DESCRIPTION).asString();
    assertNotNull(description2);
    assertEquals(description2, "first modify");

    // Simulate a concurrent update: perform another update using the old etag.
    Attribute attr2 = Attributes.create(DESCRIPTION, "second modify");
    mods = newArrayList(new Modification(ModificationType.REPLACE, attr2));
    modifyOperation = conn.processModify(userDN, mods, ctrls);
    assertEquals(modifyOperation.getResultCode(), ResultCode.ASSERTION_FAILED);

    // Reread the entry and check that the description and etag have not changed
    Entry e3 = readEntry(userDN);

    String etag3 = e3.parseAttribute(ETAG).asString();
    assertNotNull(etag3);
    assertEquals(etag2, etag3);

    String description3 = e3.parseAttribute(DESCRIPTION).asString();
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
    DN userDN = DN.valueOf("uid=test.user,ou=People,o=test");
    InternalClientConnection conn = getRootConnection();

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
    Entry e1 = readEntry(userDN);
    String etag1 = e1.parseAttribute(ETAG).asString();
    assertNotNull(etag1);

    // Apply a change using the pre and post read controls.
    Attribute attr = Attributes.create(DESCRIPTION, "modified value");
    List<Modification> mods = newArrayList(new Modification(ModificationType.REPLACE, attr));
    List<Control> ctrls = singletonList((Control) new LDAPPreReadRequestControl(true, singleton(ETAG)));
    ModifyOperation modifyOperation = conn.processModify(userDN, mods, ctrls);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);

    // Reread the entry and check that the description has been added and that
    // the etag has changed.
    Entry e2 = readEntry(userDN);

    String etag2 = e2.parseAttribute(ETAG).asString();
    assertNotNull(etag2);
    assertFalse(etag1.equals(etag2));

    String description2 = e2.parseAttribute(DESCRIPTION).asString();
    assertNotNull(description2);
    assertEquals(description2, "modified value");

    // Now check that the pre-read is the same as the initial etag.
    LDAPPreReadResponseControl preReadControl = getLDAPPreReadResponseControl(modifyOperation);
    String etagPreRead = preReadControl.getSearchEntry().parseAttribute(ETAG).asString();
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
    DN userDN = DN.valueOf("uid=test.user,ou=People,o=test");
    InternalClientConnection conn = getRootConnection();

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
    Entry e1 = readEntry(userDN);
    String etag1 = e1.parseAttribute(ETAG).asString();
    assertNotNull(etag1);

    // Apply a change using the pre and post read controls.
    Attribute attr = Attributes.create(DESCRIPTION, "modified value");
    List<Modification> mods = newArrayList(new Modification(ModificationType.REPLACE, attr));
    List<Control> ctrls = singletonList((Control) new LDAPPostReadRequestControl(true, singleton(ETAG)));
    ModifyOperation modifyOperation = conn.processModify(userDN, mods, ctrls);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);

    // Reread the entry and check that the description has been added and that
    // the etag has changed.
    Entry e2 = readEntry(userDN);

    String etag2 = e2.parseAttribute(ETAG).asString();
    assertNotNull(etag2);
    assertFalse(etag1.equals(etag2));

    String description2 = e2.parseAttribute(DESCRIPTION).asString();
    assertNotNull(description2);
    assertEquals(description2, "modified value");

    // Now check that the post-read is the same as the initial etag.
    LDAPPostReadResponseControl postReadControl = getLDAPPostReadResponseControl(modifyOperation);
    String etagPostRead = postReadControl.getSearchEntry().parseAttribute(ETAG).asString();
    assertEquals(etagPostRead, etag2);
  }

  private LDAPPostReadResponseControl getLDAPPostReadResponseControl(ModifyOperation modifyOperation)
  {
    for (Control control : modifyOperation.getResponseControls())
    {
      if (control instanceof LDAPPostReadResponseControl)
      {
        return (LDAPPostReadResponseControl) control;
      }
    }
    fail("Expected the ModifyOperation to have a LDAPPostReadResponseControl");
    return null;
  }

  private LDAPPreReadResponseControl getLDAPPreReadResponseControl(ModifyOperation modifyOperation)
  {
    for (Control control : modifyOperation.getResponseControls())
    {
      if (control instanceof LDAPPreReadResponseControl)
      {
        return (LDAPPreReadResponseControl) control;
      }
    }
    fail("Expected the ModifyOperation to have a LDAPPreReadResponseControl");
    return null;
  }

  private Entry readEntry(DN userDN) throws DirectoryException
  {
    SearchRequest request = Requests.newSearchRequest(userDN, SearchScope.BASE_OBJECT).addAttribute("*", ETAG);
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    assertEquals(searchOperation.getSearchEntries().size(), 1);
    Entry e = searchOperation.getSearchEntries().get(0);
    assertNotNull(e);
    return e;
  }

  private ByteString getEntityTag(final Entry e, VirtualAttributeRule rule)
  {
    final Attribute values = provider.getValues(e, rule);
    assertEquals(values.size(), 1);
    final ByteString value = values.iterator().next();
    assertEquals(value.length(), 16);
    for (int i = 0; i < 16; i++)
    {
      assertTrue(StaticUtils.isHexDigit(value.byteAt(i)));
      if (value.byteAt(i) != 0x30)
      {
        return value;
      }
    }
    fail("Expected to find a non zero byte");
    return null;
  }

  private VirtualAttributeRule getRule()
  {
    AttributeType type = DirectoryServer.getAttributeType("etag");
    return new VirtualAttributeRule(type, provider,
        Collections.<DN>emptySet(), SearchScope.WHOLE_SUBTREE,
        Collections.<DN>emptySet(),
        Collections.<SearchFilter>emptySet(),
        VirtualAttributeCfgDefn.ConflictBehavior.VIRTUAL_OVERRIDES_REAL);
  }

}
