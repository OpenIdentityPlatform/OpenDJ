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
 *      Copyright 2012 ForgeRock AS
 */
package org.opends.server.extensions;



import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.util.*;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.server.TestCaseUtils;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.meta.VirtualAttributeCfgDefn;
import org.opends.server.admin.std.meta.EntityTagVirtualAttributeCfgDefn.ChecksumAlgorithm;
import org.opends.server.admin.std.meta.VirtualAttributeCfgDefn.ConflictBehavior;
import org.opends.server.admin.std.meta.VirtualAttributeCfgDefn.Scope;
import org.opends.server.admin.std.server.EntityTagVirtualAttributeCfg;
import org.opends.server.admin.std.server.VirtualAttributeCfg;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.SearchOperation;
import org.opends.server.core.SearchOperationWrapper;
import org.opends.server.types.*;
import org.opends.server.util.StaticUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;



/**
 * A set of test cases for the entity tag virtual attribute provider.
 */
public class EntityTagVirtualAttributeProviderTestCase extends
    ExtensionsTestCase
{
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
    assertFalse(provider.isSearchable(null, null));
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
        DirectoryServer.getAttributeType("etag"), provider,
        Collections.<DN> emptySet(), SearchScope.WHOLE_SUBTREE,
        Collections.<DN> emptySet(), Collections.<SearchFilter> emptySet(),
        VirtualAttributeCfgDefn.ConflictBehavior.REAL_OVERRIDES_VIRTUAL);
    provider.processSearch(rule, search);
    assertEquals(search.getResultCode(), ResultCode.UNWILLING_TO_PERFORM);
    assertNotNull(search.getErrorMessage());
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
