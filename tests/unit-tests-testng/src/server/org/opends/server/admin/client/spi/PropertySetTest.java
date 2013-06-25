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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.server.admin.client.spi;

import static org.testng.Assert.*;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.SortedSet;

import org.opends.server.admin.AbstractManagedObjectDefinition;
import org.opends.server.admin.AdminTestCase;
import org.opends.server.admin.BooleanPropertyDefinition;
import org.opends.server.admin.Configuration;
import org.opends.server.admin.ConfigurationClient;
import org.opends.server.admin.DefaultBehaviorProvider;
import org.opends.server.admin.DefinedDefaultBehaviorProvider;
import org.opends.server.admin.ManagedObjectDefinition;
import org.opends.server.admin.PropertyDefinition;
import org.opends.server.admin.PropertyOption;
import org.opends.server.admin.PropertyProvider;
import org.opends.server.admin.StringPropertyDefinition;
import org.opends.server.admin.TopCfgDefn;
import org.opends.server.admin.client.ManagedObject;
import org.opends.server.admin.server.ServerManagedObject;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * PropertySet Tester.
 */
public class PropertySetTest extends AdminTestCase {

  /** Default value for boolean property */
  private static final Boolean BOOL_DEFAULT = Boolean.TRUE;

  /** Default value for string properties */
  private static final String STR_DEFAULT = "str def";

  /** Test boolean property def */
  private BooleanPropertyDefinition testBoolPropertyDefinition = null;

  /** Test single valued string property def */
  private StringPropertyDefinition testSvStringPropertyDefinition = null;

  /** Test multi-valued string property def */
  private StringPropertyDefinition testMvStringPropertyDefinition = null;

  private PropertyProvider emptyPropertyProvider = new PropertyProvider() {
    public <T> Collection<T> getPropertyValues(PropertyDefinition<T> d) throws IllegalArgumentException {
        return Collections.emptySet();
    }
  };


  /**
   * Creates property definitions for testing
   */
  @BeforeClass
  public void setUp() {
    BooleanPropertyDefinition.Builder builder =
            BooleanPropertyDefinition.createBuilder(TopCfgDefn.getInstance(), "test-bool-prop");
    DefinedDefaultBehaviorProvider<Boolean> dbp =
            new DefinedDefaultBehaviorProvider<Boolean>(BOOL_DEFAULT.toString());
    builder.setDefaultBehaviorProvider(dbp);
    testBoolPropertyDefinition = builder.getInstance();

    StringPropertyDefinition.Builder builder2 =
            StringPropertyDefinition.createBuilder(TopCfgDefn.getInstance(), "test-sv-str-prop");
    DefinedDefaultBehaviorProvider<String> dbp2 =
            new DefinedDefaultBehaviorProvider<String>(STR_DEFAULT);
    builder2.setDefaultBehaviorProvider(dbp2);
    testSvStringPropertyDefinition = builder2.getInstance();

    StringPropertyDefinition.Builder builder3 =
            StringPropertyDefinition.createBuilder(TopCfgDefn.getInstance(), "test-mv-str-prop");
    DefinedDefaultBehaviorProvider<String> dbp3 =
            new DefinedDefaultBehaviorProvider<String>(STR_DEFAULT);
    builder3.setDefaultBehaviorProvider(dbp3);
    builder3.setOption(PropertyOption.MULTI_VALUED);
    testMvStringPropertyDefinition = builder3.getInstance();
  }

  /**
   * Creates data for tests requiring property definitions
   * @return Object[][] or property definitions
   */
  @DataProvider(name = "propertyDefinitionData")
  public Object[][] createPropertyDefinitionData() {
    return new Object[][] {
            { testBoolPropertyDefinition },
            { testSvStringPropertyDefinition },
            { testMvStringPropertyDefinition }
    };
  }

  /**
   * Creates data for tests requiring property definitions
   * and sample data
   * @return Object[][] consisting of property defs and sets
   *         of sample data
   */
  @DataProvider(name = "propertyDefinitionAndValuesData")
  public Object[][] createPropertyDefinitionAndValuesData() {

    Set<Boolean> sb = new HashSet<Boolean>();
    sb.add(Boolean.TRUE);

    Set<String> ss1 = new HashSet<String>();
    ss1.add("v");

    Set<String> ss2 = new HashSet<String>();
    ss2.add("v1");
    ss2.add("v2");

    return new Object[][] {
            { testBoolPropertyDefinition, sb },
            { testSvStringPropertyDefinition, ss1 },
            { testMvStringPropertyDefinition, ss2 }
    };
  }

  /**
   * Test basic property set creation
   */
  @Test
  public void testCreate() {
    PropertySet ps = createTestPropertySet();
    assertNotNull(ps);
  }

  /**
   * Tests setting and getting property values
   * @param pd PropertyDefinition for which values are set and gotten
   * @param values property values to test
   */
  @Test(dataProvider = "propertyDefinitionAndValuesData")
  public <T> void testSetGetPropertyValue(PropertyDefinition<T> pd, Collection<T> values) {
    PropertySet ps = createTestPropertySet();
    Property<T> p = ps.getProperty(pd);
    assertFalse(p.isModified());
    ps.setPropertyValues(pd, values);
    p = ps.getProperty(pd);
    assertTrue(p.isModified());
    SortedSet<T> vs = p.getPendingValues();
    assert(values.size() == vs.size());
    for (T value : values) {
      assertTrue(vs.contains(value));
    }
  }

  /**
   * Tests toString()
   * @param pd PropertyDefinition for testing
   */
  @Test(dataProvider = "propertyDefinitionData")
  public void testToString(PropertyDefinition pd) {
    PropertySet ps = createTestPropertySet();
    ps.toString();
  }

  /**
   * Tests the active values property
   * @param pd PropertyDefinition for testing
   * @param values for testing
   */
  @Test(dataProvider = "propertyDefinitionAndValuesData")
  public <T> void testGetActiveValues(final PropertyDefinition<T> pd, final Collection<T> values) {
    PropertyProvider pp = new TestPropertyProvider<T>(pd, values);
    PropertySet ps = createTestPropertySet(pp);
    Property<T> p = ps.getProperty(pd);
    SortedSet<T> ss = p.getActiveValues();
    assertTrue(ss.size() == values.size());
    for (T v : values) {
      assertTrue(ss.contains(v));
    }
  }

  /**
   * Creates data for default test
   * @return Object[][] data for test
   */
  @DataProvider(name = "defaultData")
  public Object[][] createDefaultData() {

    Set<Boolean> sb = new HashSet<Boolean>();
    sb.add(BOOL_DEFAULT);

    Set<String> ss1 = new HashSet<String>();
    ss1.add(STR_DEFAULT);

    Set<String> ss2 = new HashSet<String>();
    ss2.add(STR_DEFAULT);

    return new Object[][] {
            { testBoolPropertyDefinition, sb },
            { testSvStringPropertyDefinition, ss1 },
            { testMvStringPropertyDefinition, ss2 }
    };
  }

  /**
   * Tests default values property
   * @param pd PropertyDefinition to test
   * @param expected default values
   */
  @Test(dataProvider = "defaultData")
  public <T> void testGetDefaultValues(PropertyDefinition<T> pd, Set<T> expected) {
    PropertySet ps = createTestPropertySet();
    Property<T> p = ps.getProperty(pd);
    SortedSet<T> ss = p.getDefaultValues();
    assertEquals(ss.size(), expected.size());
    for (T v : expected) {
      assertTrue(ss.contains(v), "does not contain " + v);
    }
  }

  /**
   * Creates data for effective test
   * @return Object[][] data for test
   */
  @DataProvider(name = "effectiveData")
  public Object[][] createEffectiveData() {

    Set<Boolean> nvb = new HashSet<Boolean>();
    nvb.add(Boolean.FALSE);

    Set<Boolean> edb = new HashSet<Boolean>();
    edb.add(BOOL_DEFAULT);

    Set<String> nvss1 = new HashSet<String>();
    nvss1.add("new value");

    Set<String> edss1 = new HashSet<String>();
    edss1.add(STR_DEFAULT);

    Set<String> nvss2 = new HashSet<String>();
    nvss2.add("new value 1");
    nvss2.add("new value 2");

    Set<String> edss2 = new HashSet<String>();
    edss2.add(STR_DEFAULT);

    return new Object[][] {
            { testBoolPropertyDefinition, nvb, edb },
            { testSvStringPropertyDefinition, nvss1, edss1 },
            { testMvStringPropertyDefinition, nvss2, edss2 }
    };
  }

  /**
   * Tests effective values property
   * @param pd PropertyDefinition
   * @param newValues to apply
   * @param expectedDefaults for test comparison
   */
  @Test(dataProvider = "effectiveData")
  public <T> void testGetEffectiveValues(PropertyDefinition<T> pd, Set<T> newValues, Set<T> expectedDefaults) {
    PropertySet ps = createTestPropertySet();
    Property<T> p = ps.getProperty(pd);

    // before setting any values, the effective data
    // is supposed to just be the defaults
    Set<T> ev1 = p.getEffectiveValues();
    assertEquals(ev1.size(), expectedDefaults.size());
    for(T v : ev1) {
      assertTrue(expectedDefaults.contains(v), "does not contain " + v);
    }

    // now set some data and make sure the effective
    // values now reflect the pending values
    ps.setPropertyValues(pd, newValues);

    Set<T> ev2 = p.getEffectiveValues();
    assertEquals(ev2.size(), newValues.size());
    for(T v : ev2) {
      assertTrue(newValues.contains(v), "does not contain " + v);
    }

  }

  /**
   * Tests pending values property
   * @param pd PropertyDefinition
   * @param newValues set of new values to apply
   * @param ignore parameter
   */
  @Test(dataProvider = "effectiveData")
  public <T> void testGetPendingValues(PropertyDefinition<T> pd, Set<T> newValues, Set<T> ignore) {
    PropertySet ps = createTestPropertySet();
    Property<T> p = ps.getProperty(pd);

    // now set some data and make sure the effective
    // values now reflect the pending values
    ps.setPropertyValues(pd, newValues);

    Set<T> ev2 = p.getEffectiveValues();
    assertTrue(ev2.size() == newValues.size());
    for(T v : ev2) {
      assertTrue(newValues.contains(v));
    }

  }

  /**
   * Tests getPropertyDefinition()
   * @param pd property definition to test
   */
  @Test(dataProvider = "propertyDefinitionData")
  public <T> void testGetPropertyDefinition(PropertyDefinition<T> pd) {
    PropertySet ps = createTestPropertySet();
    Property<T> p = ps.getProperty(pd);
    PropertyDefinition<T> pd2 = p.getPropertyDefinition();
    assertEquals(pd, pd2);
  }

  /**
   * Tests isEmpty property
   * @param pd PropertyDefinition
   * @param newValues set of new values to apply
   * @param ignore parameter
   */
  @Test(dataProvider = "effectiveData")
  public <T> void testIsEmpty(PropertyDefinition<T> pd, Set<T> newValues, Set<T> ignore) {
    PropertySet ps = createTestPropertySet();
    Property<T> p = ps.getProperty(pd);
    assertTrue(p.isEmpty());

    ps.setPropertyValues(pd, newValues);
    assertFalse(p.isEmpty());
  }

  /**
   * Tests isEmpty property
   * @param pd PropertyDefinition
   * @param newValues set of new values to apply
   * @param ignore parameter
   */
  @Test(dataProvider = "effectiveData")
  public <T> void testIsModified(PropertyDefinition<T> pd, Set<T> newValues, Set<T> ignore) {
    PropertySet ps = createTestPropertySet();
    Property<T> p = ps.getProperty(pd);
    assertFalse(p.isModified());

    ps.setPropertyValues(pd, newValues);
    p = ps.getProperty(pd);

    assertTrue(p.isModified());
  }

  /**
   * Tests wasEmpty property
   * @param pd property def to test
   */
  @Test(dataProvider = "propertyDefinitionData")
  public <T> void testWasEmpty(PropertyDefinition<T> pd) {
    PropertySet ps = createTestPropertySet(emptyPropertyProvider);
    Property<T> p = ps.getProperty(pd);
    assertTrue(p.wasEmpty());
  }

  /**
   * Tests property toString()
   * @param pd definition of property to test
   */
  @Test(dataProvider = "propertyDefinitionData")
  public <T> void testToString1(PropertyDefinition<T> pd) {
    PropertySet ps = createTestPropertySet();
    Property<T> p = ps.getProperty(pd);
    p.toString();
  }

  private PropertySet createTestPropertySet(PropertyProvider pp) {
    ManagedObjectDefinition<?, ?> d = new TestManagedObjectDefinition<ConfigurationClient, Configuration>("test-mod", null);
    PropertySet ps = new PropertySet();
    for (PropertyDefinition<?> pd : d.getPropertyDefinitions()) {
      addProperty(ps, pd, pp);
    }
    return ps;
  }

  private <T> void addProperty(PropertySet ps, PropertyDefinition<T> pd, PropertyProvider pp) {
    Collection<T> defaultValues = new LinkedList<T>();
    DefaultBehaviorProvider<T> dbp = pd.getDefaultBehaviorProvider();
    if (dbp instanceof DefinedDefaultBehaviorProvider) {
      DefinedDefaultBehaviorProvider<T> ddbp = (DefinedDefaultBehaviorProvider<T>) dbp;
      Collection<String> stringValues = ddbp.getDefaultValues();
      for (String sv : stringValues) {
        defaultValues.add(pd.decodeValue(sv));
      }
    }
    
    Collection<T> activeValues = pp.getPropertyValues(pd);
    ps.addProperty(pd, defaultValues, activeValues);
  }

  private class TestManagedObjectDefinition<C extends ConfigurationClient,S extends Configuration> extends
          ManagedObjectDefinition<C, S> {
    /**
     * Create a new managed object definition.
     *
     * @param name   The name of the definition.
     * @param parent The parent definition, or <code>null</code> if there
     *               is no parent.
     */
    protected
    TestManagedObjectDefinition(String name,
                                AbstractManagedObjectDefinition<? super C,? super S> parent) {
      super(name, parent);
      registerPropertyDefinition(testBoolPropertyDefinition);
      registerPropertyDefinition(testSvStringPropertyDefinition);
      registerPropertyDefinition(testMvStringPropertyDefinition);
    }

    /**
     * {@inheritDoc}
     */
    public C createClientConfiguration(ManagedObject managedObject) {
      System.out.println("createClientConfiguration mo=" + managedObject);
      return null;
    }

    /**
     * {@inheritDoc}
     */
    public S createServerConfiguration(ServerManagedObject serverManagedObject) {
      System.out.println("createServerConfiguration smo=" + serverManagedObject);
      return null;
    }

    /**
     * {@inheritDoc}
     */
    public Class<S> getServerConfigurationClass() {
      System.out.println("getServerConfigurationClass");
      return null;
    }
  }

  private class TestPropertyProvider<T> implements PropertyProvider {
    
    PropertyDefinition<T> pd = null;
    Collection<T> values = null;

    public TestPropertyProvider(PropertyDefinition<T> pd, Collection<T> values) {
      this.pd = pd;
      this.values = values;
    }

    @SuppressWarnings("unchecked")
    public <S> Collection<S> getPropertyValues(PropertyDefinition<S> d) throws IllegalArgumentException {
      if (d.equals(pd)) {
        return (Collection<S>) values;
      } else {
        return Collections.emptySet();
      }
    }
  }


  private PropertySet createTestPropertySet() {
    return createTestPropertySet(PropertyProvider.DEFAULT_PROVIDER);
  }

}

