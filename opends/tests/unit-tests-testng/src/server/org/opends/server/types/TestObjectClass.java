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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.types;



import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opends.server.core.DirectoryServer;
import org.opends.server.util.ServerConstants;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;



/**
 * This class defines a set of tests for the
 * {@link org.opends.server.types.ObjectClass} class.
 */
public final class TestObjectClass extends TestCommonSchemaElements {
  /**
   * Internal class to simplify construction of object classes.
   */
  private static final class ObjectClassBuilder extends
      SchemaDefinitionBuilder<ObjectClass> {
    // The superior object class from which this object class
    // inherits.
    private ObjectClass superior;

    // The type of object class.
    private ObjectClassType objectClassType;

    // The set of required attribute types.
    private Set<AttributeType> requiredAttributeTypes;

    // The set of optional attribute types.
    private Set<AttributeType> optionalAttributeTypes;



    /**
     * {@inheritDoc}
     */
    protected void resetBuilder() {
      this.superior = null;
      this.objectClassType = ObjectClassType.STRUCTURAL;
      this.requiredAttributeTypes = null;
      this.optionalAttributeTypes = null;
    }



    /**
     * Create a new object class builder.
     */
    public ObjectClassBuilder() {
      super();
    }



    /**
     * Create a new object class builder.
     *
     * @param primaryName
     *          The object class primary name.
     * @param oid
     *          The object class OID.
     */
    public ObjectClassBuilder(String primaryName, String oid) {
      super(primaryName, oid);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    protected ObjectClass buildInstance(String primaryName,
        Collection<String> names, String oid, String description,
        boolean isObsolete, Map<String, List<String>> extraProperties) {

      StringBuilder definition = new StringBuilder();
      definition.append("( ");
      definition.append(oid);

      LinkedHashSet<String> nameSet = new LinkedHashSet<String>();
      if (primaryName != null)
      {
        nameSet.add(primaryName);
      }

      if (names != null)
      {
        for (String name : names)
        {
          nameSet.add(name);
        }
      }

      if (! nameSet.isEmpty())
      {
        if (nameSet.size() == 1)
        {
          definition.append(" NAME '");
          definition.append(nameSet.iterator().next());
          definition.append("'");
        }
        else
        {
          Iterator<String> iterator = nameSet.iterator();

          definition.append(" NAME ( '");
          definition.append(iterator.next());

          while (iterator.hasNext())
          {
            definition.append("' '");
            definition.append(iterator.next());
          }

          definition.append("' )");
        }
      }

      if (description != null)
      {
        definition.append(" DESC '");
        definition.append(description);
        definition.append("'");
      }

      if (isObsolete)
      {
        definition.append(" OBSOLETE");
      }

      if (superior != null)
      {
        definition.append(" SUP ");
        definition.append(superior.getNameOrOID());
      }

      if (objectClassType != null)
      {
        definition.append(" ");
        definition.append(objectClassType.toString());
      }

      if ((requiredAttributeTypes != null) &&
          (! requiredAttributeTypes.isEmpty()))
      {
        if (requiredAttributeTypes.size() == 1)
        {
          definition.append(" MUST ");
          definition.append(
               requiredAttributeTypes.iterator().next().getNameOrOID());
        }
        else
        {
          Iterator<AttributeType> iterator = requiredAttributeTypes.iterator();

          definition.append(" MUST ( ");
          definition.append(iterator.next().getNameOrOID());
          while (iterator.hasNext())
          {
            definition.append(" $ ");
            definition.append(iterator.next().getNameOrOID());
          }
          definition.append(" )");
        }
      }

      if ((optionalAttributeTypes != null) &&
          (! optionalAttributeTypes.isEmpty()))
      {
        if (optionalAttributeTypes.size() == 1)
        {
          definition.append(" MUST ");
          definition.append(
               optionalAttributeTypes.iterator().next().getNameOrOID());
        }
        else
        {
          Iterator<AttributeType> iterator = optionalAttributeTypes.iterator();

          definition.append(" MUST ( ");
          definition.append(iterator.next().getNameOrOID());
          while (iterator.hasNext())
          {
            definition.append(" $ ");
            definition.append(iterator.next().getNameOrOID());
          }
          definition.append(" )");
        }
      }

      if (extraProperties != null)
      {
        for (String property : extraProperties.keySet())
        {
          List<String> values = extraProperties.get(property);
          if ((values == null) || values.isEmpty())
          {
            continue;
          }
          else if (values.size() == 1)
          {
            definition.append(" ");
            definition.append(property);
            definition.append(" '");
            definition.append(values.get(0));
            definition.append("'");
          }
          else
          {
            definition.append(" ");
            definition.append(property);
            definition.append(" (");
            for (String value : values)
            {
              definition.append(" '");
              definition.append(value);
              definition.append("'");
            }

            definition.append(" )");
          }
        }
      }

      definition.append(" )");


      return new ObjectClass(definition.toString(), primaryName, names, oid,
                             description, superior, requiredAttributeTypes,
                             optionalAttributeTypes, objectClassType,
                             isObsolete, extraProperties);
    }



    /**
     * Set the objectClassType.
     *
     * @param objectClassType
     *          The objectClassType.
     */
    public void setObjectClassType(ObjectClassType objectClassType) {
      this.objectClassType = objectClassType;
    }



    /**
     * Set the superior.
     *
     * @param superior
     *          The superior.
     */
    public void setSuperior(ObjectClass superior) {
      this.superior = superior;
    }



    /**
     * Add required attribute types.
     *
     * @param types
     *          The required attribute type(s) to add.
     */
    public void addRequiredAttributeTypes(AttributeType... types) {
      if (types == null) {
        throw new NullPointerException("Null types");
      }

      if (this.requiredAttributeTypes == null) {
        this.requiredAttributeTypes = new LinkedHashSet<AttributeType>();
      }

      this.requiredAttributeTypes.addAll(Arrays.asList(types));
    }



    /**
     * Add optional attribute types.
     *
     * @param types
     *          The optional attribute type(s) to add.
     */
    public void addOptionalAttributeTypes(AttributeType... types) {
      if (types == null) {
        throw new NullPointerException("Null types");
      }

      if (this.optionalAttributeTypes == null) {
        this.optionalAttributeTypes = new LinkedHashSet<AttributeType>();
      }

      this.optionalAttributeTypes.addAll(Arrays.asList(types));
    }
  }



  // Array of attribute types to use in tests.
  private AttributeType[] types;



  /**
   * Once-only initialization.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @BeforeClass(dependsOnMethods = "setUp")
  public final void setUpTypes() throws Exception {
    types = new AttributeType[10];

    for (int i = 0; i < types.length; i++) {
      String name = "testType" + i;
      types[i] = DirectoryServer.getDefaultAttributeType(name);
    }
  }



  /**
   * Check that the constructor throws an NPE when mandatory
   * parameters are not specified.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void testConstructorNPE() throws Exception {
    Set<AttributeType> emptySet = Collections.emptySet();
    Map<String, List<String>> emptyMap = Collections.emptyMap();

    new ObjectClass(null, "test", Collections.singleton("test"), null,
        "description", DirectoryServer.getTopObjectClass(), emptySet,
        emptySet, ObjectClassType.STRUCTURAL, false, emptyMap);
  }



  /**
   * Check that the constructor does not throw an exception when all
   * optional parameters are not specified.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testConstructorDefault() throws Exception {
    String definition = "( 1.2.3 )";
    ObjectClass type = new ObjectClass(definition, null, null, "1.2.3", null,
        null, null, null, null, false, null);

    Assert.assertNull(type.getPrimaryName());
  }



  /**
   * Create test data for testing the
   * {@link AttributeType#isOperational()} method.
   *
   * @return Returns the array of test data.
   */
  @DataProvider(name = "getObjectClassTypeTestData")
  public Object[][] createGetObjectClassTypeTestData() {
    return new Object[][] { { null, ObjectClassType.STRUCTURAL },
        { ObjectClassType.STRUCTURAL, ObjectClassType.STRUCTURAL },
        { ObjectClassType.ABSTRACT, ObjectClassType.ABSTRACT },
        { ObjectClassType.AUXILIARY, ObjectClassType.AUXILIARY } };
  }



  /**
   * Check that the {@link ObjectClass#getObjectClassType()} method.
   *
   * @param type
   *          The object class type.
   * @param result
   *          Expected result.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "getObjectClassTypeTestData")
  public void testGetObjectClassType(ObjectClassType type,
      ObjectClassType result) throws Exception {
    ObjectClassBuilder builder = new ObjectClassBuilder("testType",
        "1.2.3");
    builder.setObjectClassType(type);
    ObjectClass c = builder.getInstance();
    Assert.assertEquals(c.getObjectClassType(), result);
  }



  /**
   * Check the {@link ObjectClass#getOptionalAttributeChain()} method
   * with no superior and no optional attributes.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetOptionalAttributeChainNoSuperiorEmpty()
      throws Exception {
    ObjectClassBuilder builder = new ObjectClassBuilder("testType",
        "1.2.3");
    ObjectClass c = builder.getInstance();
    Assert.assertTrue(c.getOptionalAttributeChain().isEmpty());
  }



  /**
   * Check the {@link ObjectClass#getOptionalAttributeChain()} method
   * with no superior and some optional attributes.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetOptionalAttributeChainNoSuperior()
      throws Exception {
    ObjectClassBuilder builder = new ObjectClassBuilder("testType",
        "1.2.3");
    builder.addOptionalAttributeTypes(types[0], types[1], types[2]);
    ObjectClass c = builder.getInstance();

    Set<AttributeType> chain = c.getOptionalAttributeChain();
    Assert.assertEquals(chain.size(), 3);
    Assert.assertTrue(chain.contains(types[0]));
    Assert.assertTrue(chain.contains(types[1]));
    Assert.assertTrue(chain.contains(types[2]));
  }



  /**
   * Check the {@link ObjectClass#getOptionalAttributeChain()} method
   * with a superior but no optional attributes of its own.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetOptionalAttributeChainEmpty() throws Exception {
    ObjectClassBuilder builder = new ObjectClassBuilder("parent",
        "1.2.3");
    builder.addOptionalAttributeTypes(types[0], types[1], types[2]);
    ObjectClass parent = builder.getInstance();

    builder = new ObjectClassBuilder("child", "1.2.3");
    builder.setSuperior(parent);
    ObjectClass child = builder.getInstance();

    Set<AttributeType> chain = child.getOptionalAttributeChain();
    Assert.assertEquals(chain.size(), 3);
    Assert.assertTrue(chain.contains(types[0]));
    Assert.assertTrue(chain.contains(types[1]));
    Assert.assertTrue(chain.contains(types[2]));
  }



  /**
   * Check the {@link ObjectClass#getOptionalAttributeChain()} method
   * with a superior and some optional attributes of its own.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetOptionalAttributeChain() throws Exception {
    ObjectClassBuilder builder = new ObjectClassBuilder("parent",
        "1.2.3");
    builder.addOptionalAttributeTypes(types[0], types[1], types[2]);
    ObjectClass parent = builder.getInstance();

    builder = new ObjectClassBuilder("child", "1.2.3");
    builder.addOptionalAttributeTypes(types[3], types[4], types[5]);
    builder.setSuperior(parent);
    ObjectClass child = builder.getInstance();

    Set<AttributeType> chain = child.getOptionalAttributeChain();
    Assert.assertEquals(chain.size(), 6);
    Assert.assertTrue(chain.contains(types[0]));
    Assert.assertTrue(chain.contains(types[1]));
    Assert.assertTrue(chain.contains(types[2]));
    Assert.assertTrue(chain.contains(types[3]));
    Assert.assertTrue(chain.contains(types[4]));
    Assert.assertTrue(chain.contains(types[5]));
  }



  /**
   * Check the {@link ObjectClass#getOptionalAttributes()} method with
   * no superior and no optional attributes.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetOptionalAttributesNoSuperiorEmpty()
      throws Exception {
    ObjectClassBuilder builder = new ObjectClassBuilder("testType",
        "1.2.3");
    ObjectClass c = builder.getInstance();
    Assert.assertTrue(c.getOptionalAttributes().isEmpty());
  }



  /**
   * Check the {@link ObjectClass#getOptionalAttributes()} method with
   * no superior and some optional attributes.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetOptionalAttributesNoSuperior() throws Exception {
    ObjectClassBuilder builder = new ObjectClassBuilder("testType",
        "1.2.3");
    builder.addOptionalAttributeTypes(types[0], types[1], types[2]);
    ObjectClass c = builder.getInstance();

    Set<AttributeType> chain = c.getOptionalAttributes();
    Assert.assertEquals(chain.size(), 3);
    Assert.assertTrue(chain.contains(types[0]));
    Assert.assertTrue(chain.contains(types[1]));
    Assert.assertTrue(chain.contains(types[2]));
  }



  /**
   * Check the {@link ObjectClass#getOptionalAttributes()} method with
   * a superior but no optional attributes of its own.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetOptionalAttributesEmpty() throws Exception {
    ObjectClassBuilder builder = new ObjectClassBuilder("parent",
        "1.2.3");
    builder.addOptionalAttributeTypes(types[0], types[1], types[2]);
    ObjectClass parent = builder.getInstance();

    builder = new ObjectClassBuilder("child", "1.2.3");
    builder.setSuperior(parent);
    ObjectClass child = builder.getInstance();

    Assert.assertTrue(child.getOptionalAttributes().isEmpty());
  }



  /**
   * Check the {@link ObjectClass#getOptionalAttributes()} method with
   * a superior and some optional attributes of its own.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetOptionalAttributes() throws Exception {
    ObjectClassBuilder builder = new ObjectClassBuilder("parent",
        "1.2.3");
    builder.addOptionalAttributeTypes(types[0], types[1], types[2]);
    ObjectClass parent = builder.getInstance();

    builder = new ObjectClassBuilder("child", "1.2.3");
    builder.addOptionalAttributeTypes(types[3], types[4], types[5]);
    builder.setSuperior(parent);
    ObjectClass child = builder.getInstance();

    Set<AttributeType> chain = child.getOptionalAttributes();
    Assert.assertEquals(chain.size(), 3);
    Assert.assertTrue(chain.contains(types[3]));
    Assert.assertTrue(chain.contains(types[4]));
    Assert.assertTrue(chain.contains(types[5]));
  }



  /**
   * Check the {@link ObjectClass#getRequiredAttributeChain()} method
   * with no superior and no optional attributes.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetRequiredAttributeChainNoSuperiorEmpty()
      throws Exception {
    ObjectClassBuilder builder = new ObjectClassBuilder("testType",
        "1.2.3");
    ObjectClass c = builder.getInstance();
    Assert.assertTrue(c.getRequiredAttributeChain().isEmpty());
  }



  /**
   * Check the {@link ObjectClass#getRequiredAttributeChain()} method
   * with no superior and some optional attributes.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetRequiredAttributeChainNoSuperior()
      throws Exception {
    ObjectClassBuilder builder = new ObjectClassBuilder("testType",
        "1.2.3");
    builder.addRequiredAttributeTypes(types[0], types[1], types[2]);
    ObjectClass c = builder.getInstance();

    Set<AttributeType> chain = c.getRequiredAttributeChain();
    Assert.assertEquals(chain.size(), 3);
    Assert.assertTrue(chain.contains(types[0]));
    Assert.assertTrue(chain.contains(types[1]));
    Assert.assertTrue(chain.contains(types[2]));
  }



  /**
   * Check the {@link ObjectClass#getRequiredAttributeChain()} method
   * with a superior but no optional attributes of its own.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetRequiredAttributeChainEmpty() throws Exception {
    ObjectClassBuilder builder = new ObjectClassBuilder("parent",
        "1.2.3");
    builder.addRequiredAttributeTypes(types[0], types[1], types[2]);
    ObjectClass parent = builder.getInstance();

    builder = new ObjectClassBuilder("child", "1.2.3");
    builder.setSuperior(parent);
    ObjectClass child = builder.getInstance();

    Set<AttributeType> chain = child.getRequiredAttributeChain();
    Assert.assertEquals(chain.size(), 3);
    Assert.assertTrue(chain.contains(types[0]));
    Assert.assertTrue(chain.contains(types[1]));
    Assert.assertTrue(chain.contains(types[2]));
  }



  /**
   * Check the {@link ObjectClass#getRequiredAttributeChain()} method
   * with a superior and some optional attributes of its own.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetRequiredAttributeChain() throws Exception {
    ObjectClassBuilder builder = new ObjectClassBuilder("parent",
        "1.2.3");
    builder.addRequiredAttributeTypes(types[0], types[1], types[2]);
    ObjectClass parent = builder.getInstance();

    builder = new ObjectClassBuilder("child", "1.2.3");
    builder.addRequiredAttributeTypes(types[3], types[4], types[5]);
    builder.setSuperior(parent);
    ObjectClass child = builder.getInstance();

    Set<AttributeType> chain = child.getRequiredAttributeChain();
    Assert.assertEquals(chain.size(), 6);
    Assert.assertTrue(chain.contains(types[0]));
    Assert.assertTrue(chain.contains(types[1]));
    Assert.assertTrue(chain.contains(types[2]));
    Assert.assertTrue(chain.contains(types[3]));
    Assert.assertTrue(chain.contains(types[4]));
    Assert.assertTrue(chain.contains(types[5]));
  }



  /**
   * Check the {@link ObjectClass#getRequiredAttributes()} method with
   * no superior and no optional attributes.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetRequiredAttributesNoSuperiorEmpty()
      throws Exception {
    ObjectClassBuilder builder = new ObjectClassBuilder("testType",
        "1.2.3");
    ObjectClass c = builder.getInstance();
    Assert.assertTrue(c.getRequiredAttributes().isEmpty());
  }



  /**
   * Check the {@link ObjectClass#getRequiredAttributes()} method with
   * no superior and some optional attributes.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetRequiredAttributesNoSuperior() throws Exception {
    ObjectClassBuilder builder = new ObjectClassBuilder("testType",
        "1.2.3");
    builder.addRequiredAttributeTypes(types[0], types[1], types[2]);
    ObjectClass c = builder.getInstance();

    Set<AttributeType> chain = c.getRequiredAttributes();
    Assert.assertEquals(chain.size(), 3);
    Assert.assertTrue(chain.contains(types[0]));
    Assert.assertTrue(chain.contains(types[1]));
    Assert.assertTrue(chain.contains(types[2]));
  }



  /**
   * Check the {@link ObjectClass#getRequiredAttributes()} method with
   * a superior but no optional attributes of its own.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetRequiredAttributesEmpty() throws Exception {
    ObjectClassBuilder builder = new ObjectClassBuilder("parent",
        "1.2.3");
    builder.addRequiredAttributeTypes(types[0], types[1], types[2]);
    ObjectClass parent = builder.getInstance();

    builder = new ObjectClassBuilder("child", "1.2.3");
    builder.setSuperior(parent);
    ObjectClass child = builder.getInstance();

    Assert.assertTrue(child.getRequiredAttributes().isEmpty());
  }



  /**
   * Check the {@link ObjectClass#getRequiredAttributes()} method with
   * a superior and some optional attributes of its own.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetRequiredAttributes() throws Exception {
    ObjectClassBuilder builder = new ObjectClassBuilder("parent",
        "1.2.3");
    builder.addRequiredAttributeTypes(types[0], types[1], types[2]);
    ObjectClass parent = builder.getInstance();

    builder = new ObjectClassBuilder("child", "1.2.3");
    builder.addRequiredAttributeTypes(types[3], types[4], types[5]);
    builder.setSuperior(parent);
    ObjectClass child = builder.getInstance();

    Set<AttributeType> chain = child.getRequiredAttributes();
    Assert.assertEquals(chain.size(), 3);
    Assert.assertTrue(chain.contains(types[3]));
    Assert.assertTrue(chain.contains(types[4]));
    Assert.assertTrue(chain.contains(types[5]));
  }



  /**
   * Check the {@link ObjectClass#getSuperiorClass()} method with no
   * superior.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetSuperiorClassNoSuperior() throws Exception {
    ObjectClassBuilder builder = new ObjectClassBuilder("testType",
        "1.2.3");
    ObjectClass c = builder.getInstance();
    Assert.assertNull(c.getSuperiorClass());
  }



  /**
   * Check the {@link ObjectClass#getSuperiorClass()} method with a
   * superior.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetSuperiorClassWithSuperior() throws Exception {
    ObjectClassBuilder builder = new ObjectClassBuilder("parent",
        "1.2.3");
    ObjectClass parent = builder.getInstance();

    builder = new ObjectClassBuilder("child", "1.2.3");
    builder.setSuperior(parent);
    ObjectClass child = builder.getInstance();

    Assert.assertEquals(child.getSuperiorClass(), parent);
  }



  /**
   * Check the {@link ObjectClass#isDescendantOf(ObjectClass)} method
   * with no superior.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testIsDescendantOfNoSuperior() throws Exception {
    ObjectClassBuilder builder = new ObjectClassBuilder("testType1",
        "1.2.1");
    ObjectClass c1 = builder.getInstance();

    builder = new ObjectClassBuilder("testType2", "1.2.2");
    ObjectClass c2 = builder.getInstance();

    Assert.assertFalse(c1.isDescendantOf(c2));
  }



  /**
   * Check the {@link ObjectClass#isDescendantOf(ObjectClass)} method
   * with a superior.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testIsDescendantOfWithSuperior() throws Exception {
    ObjectClassBuilder builder = new ObjectClassBuilder(
        "grandParent", "1.2.1");
    ObjectClass grandParent = builder.getInstance();

    builder = new ObjectClassBuilder("parent", "1.2.2");
    builder.setSuperior(grandParent);
    ObjectClass parent = builder.getInstance();

    builder = new ObjectClassBuilder("child", "1.2.3");
    builder.setSuperior(parent);
    ObjectClass child = builder.getInstance();

    Assert.assertTrue(parent.isDescendantOf(grandParent));
    Assert.assertTrue(child.isDescendantOf(parent));
    Assert.assertTrue(child.isDescendantOf(grandParent));

    Assert.assertFalse(child.isDescendantOf(child));
    Assert.assertFalse(parent.isDescendantOf(child));
    Assert.assertFalse(grandParent.isDescendantOf(child));
  }



  /**
   * Create test data for testing the
   * {@link ObjectClass#isExtensibleObject()} method.
   *
   * @return Returns the array of test data.
   */
  @DataProvider(name = "isExtensibleObjectTestData")
  public Object[][] createIsExtensibleObjectTestData() {
    return new Object[][] { { "test", "1.2.3", false },
        { "extensibleObject", "1.2.3", true },
        { "test", "1.3.6.1.4.1.1466.101.120.111", true },
        { "extensibleObject", "1.3.6.1.4.1.1466.101.120.111", true } };
  }



  /**
   * Check that the {@link ObjectClass#getObjectClassType()} method.
   *
   * @param name
   *          The object class name.
   * @param oid
   *          The object class oid.
   * @param result
   *          Expected result.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "isExtensibleObjectTestData")
  public void testIsExtensibleObject(String name, String oid,
      boolean result) throws Exception {
    ObjectClassBuilder builder = new ObjectClassBuilder(name, oid);
    ObjectClass c = builder.getInstance();
    Assert.assertEquals(c.isExtensibleObject(), result);
  }



  /**
   * Check that the {@link ObjectClass#isOptional(AttributeType)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testIsOptionalEmpty() throws Exception {
    ObjectClassBuilder builder = new ObjectClassBuilder("test",
        "1.2.3");
    ObjectClass c = builder.getInstance();
    Assert.assertFalse(c.isOptional(types[0]));
  }



  /**
   * Check that the {@link ObjectClass#isOptional(AttributeType)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testIsOptionalNoSuperior() throws Exception {
    ObjectClassBuilder builder = new ObjectClassBuilder("test",
        "1.2.3");
    builder.addOptionalAttributeTypes(types[0]);
    ObjectClass c = builder.getInstance();
    Assert.assertTrue(c.isOptional(types[0]));
    Assert.assertFalse(c.isOptional(types[1]));
  }



  /**
   * Check that the {@link ObjectClass#isOptional(AttributeType)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testIsOptionalEmptyWithSuperior() throws Exception {
    ObjectClassBuilder builder = new ObjectClassBuilder("parent",
        "1.2.3");
    builder.addOptionalAttributeTypes(types[0]);
    ObjectClass parent = builder.getInstance();
    builder = new ObjectClassBuilder("child", "1.2.3");
    builder.setSuperior(parent);
    ObjectClass child = builder.getInstance();

    Assert.assertTrue(child.isOptional(types[0]));
    Assert.assertFalse(child.isOptional(types[1]));
  }



  /**
   * Check that the {@link ObjectClass#isOptional(AttributeType)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testIsOptionalWithSuperior() throws Exception {
    ObjectClassBuilder builder = new ObjectClassBuilder("parent",
        "1.2.3");
    builder.addOptionalAttributeTypes(types[0]);
    ObjectClass parent = builder.getInstance();
    builder = new ObjectClassBuilder("child", "1.2.3");
    builder.addOptionalAttributeTypes(types[1]);
    builder.setSuperior(parent);
    ObjectClass child = builder.getInstance();

    Assert.assertTrue(child.isOptional(types[0]));
    Assert.assertTrue(child.isOptional(types[1]));
    Assert.assertFalse(child.isOptional(types[2]));
  }



  /**
   * Check that the {@link ObjectClass#isOptional(AttributeType)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dependsOnMethods = "testIsExtensibleObject")
  public void testIsOptionalExtensible() throws Exception {
    ObjectClassBuilder builder = new ObjectClassBuilder(
        "extensibleObject", "1.2.3");
    ObjectClass c = builder.getInstance();
    Assert.assertTrue(c.isOptional(types[0]));
  }



  /**
   * Check that the {@link ObjectClass#isOptional(AttributeType)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dependsOnMethods = "testIsExtensibleObject")
  public void testIsOptionalExtensibleRequired() throws Exception {
    ObjectClassBuilder builder = new ObjectClassBuilder(
        "extensibleObject", "1.2.3");
    builder.addRequiredAttributeTypes(types[0]);
    ObjectClass c = builder.getInstance();
    Assert.assertFalse(c.isOptional(types[0]));
    Assert.assertTrue(c.isOptional(types[1]));
  }



  /**
   * Check that the {@link ObjectClass#isOptional(AttributeType)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dependsOnMethods = "testIsExtensibleObject")
  public void testIsOptionalExtensibleRequiredSuperior()
      throws Exception {
    ObjectClassBuilder builder = new ObjectClassBuilder("parent",
        "1.2.3");
    builder.addRequiredAttributeTypes(types[0]);
    ObjectClass parent = builder.getInstance();

    builder = new ObjectClassBuilder("extensibleObject", "1.2.3");
    builder.setSuperior(parent);
    ObjectClass c = builder.getInstance();

    Assert.assertFalse(c.isOptional(types[0]));
    Assert.assertTrue(c.isOptional(types[1]));
  }



  /**
   * Check that the {@link ObjectClass#isRequired(AttributeType)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testIsRequiredEmpty() throws Exception {
    ObjectClassBuilder builder = new ObjectClassBuilder("test",
        "1.2.3");
    ObjectClass c = builder.getInstance();
    Assert.assertFalse(c.isRequired(types[0]));
  }



  /**
   * Check that the {@link ObjectClass#isRequired(AttributeType)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testIsRequiredNoSuperior() throws Exception {
    ObjectClassBuilder builder = new ObjectClassBuilder("test",
        "1.2.3");
    builder.addRequiredAttributeTypes(types[0]);
    ObjectClass c = builder.getInstance();
    Assert.assertTrue(c.isRequired(types[0]));
    Assert.assertFalse(c.isRequired(types[1]));
  }



  /**
   * Check that the {@link ObjectClass#isRequired(AttributeType)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testIsRequiredEmptyWithSuperior() throws Exception {
    ObjectClassBuilder builder = new ObjectClassBuilder("parent",
        "1.2.3");
    builder.addRequiredAttributeTypes(types[0]);
    ObjectClass parent = builder.getInstance();
    builder = new ObjectClassBuilder("child", "1.2.3");
    builder.setSuperior(parent);
    ObjectClass child = builder.getInstance();

    Assert.assertTrue(child.isRequired(types[0]));
    Assert.assertFalse(child.isRequired(types[1]));
  }



  /**
   * Check that the {@link ObjectClass#isRequired(AttributeType)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testIsRequiredWithSuperior() throws Exception {
    ObjectClassBuilder builder = new ObjectClassBuilder("parent",
        "1.2.3");
    builder.addRequiredAttributeTypes(types[0]);
    ObjectClass parent = builder.getInstance();
    builder = new ObjectClassBuilder("child", "1.2.3");
    builder.addRequiredAttributeTypes(types[1]);
    builder.setSuperior(parent);
    ObjectClass child = builder.getInstance();

    Assert.assertTrue(child.isRequired(types[0]));
    Assert.assertTrue(child.isRequired(types[1]));
    Assert.assertFalse(child.isRequired(types[2]));
  }



  /**
   * Check that the
   * {@link ObjectClass#isRequiredOrOptional(AttributeType)} method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testIsRequiredOrOptionalEmpty() throws Exception {
    ObjectClassBuilder builder = new ObjectClassBuilder("test",
        "1.2.3");
    ObjectClass c = builder.getInstance();
    Assert.assertFalse(c.isRequiredOrOptional(types[0]));
  }



  /**
   * Check that the
   * {@link ObjectClass#isRequiredOrOptional(AttributeType)} method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testIsRequiredOrOptionalNoSuperior() throws Exception {
    ObjectClassBuilder builder = new ObjectClassBuilder("test",
        "1.2.3");
    builder.addOptionalAttributeTypes(types[0]);
    builder.addRequiredAttributeTypes(types[1]);
    ObjectClass c = builder.getInstance();
    Assert.assertTrue(c.isRequiredOrOptional(types[0]));
    Assert.assertTrue(c.isRequiredOrOptional(types[1]));
    Assert.assertFalse(c.isRequiredOrOptional(types[2]));
  }



  /**
   * Check that the
   * {@link ObjectClass#isRequiredOrOptional(AttributeType)} method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testIsRequiredOrOptionalEmptyWithSuperior()
      throws Exception {
    ObjectClassBuilder builder = new ObjectClassBuilder("parent",
        "1.2.3");
    builder.addOptionalAttributeTypes(types[0]);
    builder.addRequiredAttributeTypes(types[1]);
    ObjectClass parent = builder.getInstance();
    builder = new ObjectClassBuilder("child", "1.2.3");
    builder.setSuperior(parent);
    ObjectClass child = builder.getInstance();

    Assert.assertTrue(child.isRequiredOrOptional(types[0]));
    Assert.assertTrue(child.isRequiredOrOptional(types[1]));
    Assert.assertFalse(child.isRequiredOrOptional(types[2]));
  }



  /**
   * Check that the
   * {@link ObjectClass#isRequiredOrOptional(AttributeType)} method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testIsRequiredOrOptionalWithSuperior() throws Exception {
    ObjectClassBuilder builder = new ObjectClassBuilder("parent",
        "1.2.3");
    builder.addOptionalAttributeTypes(types[0]);
    builder.addRequiredAttributeTypes(types[1]);
    ObjectClass parent = builder.getInstance();
    builder = new ObjectClassBuilder("child", "1.2.3");
    builder.addOptionalAttributeTypes(types[2]);
    builder.addRequiredAttributeTypes(types[3]);
    builder.setSuperior(parent);
    ObjectClass child = builder.getInstance();

    Assert.assertTrue(child.isRequiredOrOptional(types[0]));
    Assert.assertTrue(child.isRequiredOrOptional(types[1]));
    Assert.assertTrue(child.isRequiredOrOptional(types[2]));
    Assert.assertTrue(child.isRequiredOrOptional(types[3]));
    Assert.assertFalse(child.isRequiredOrOptional(types[4]));
  }



  /**
   * Check that the
   * {@link ObjectClass#isRequiredOrOptional(AttributeType)} method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dependsOnMethods = "testIsExtensibleObject")
  public void testIsRequiredOrOptionalExtensible() throws Exception {
    ObjectClassBuilder builder = new ObjectClassBuilder(
        "extensibleObject", "1.2.3");
    ObjectClass c = builder.getInstance();
    Assert.assertTrue(c.isRequiredOrOptional(types[0]));
  }



  /**
   * Check that the
   * {@link ObjectClass#isRequiredOrOptional(AttributeType)} method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dependsOnMethods = "testIsExtensibleObject")
  public void testIsRequiredOrOptionalExtensibleRequired()
      throws Exception {
    ObjectClassBuilder builder = new ObjectClassBuilder(
        "extensibleObject", "1.2.3");
    builder.addRequiredAttributeTypes(types[0]);
    ObjectClass c = builder.getInstance();
    Assert.assertTrue(c.isRequiredOrOptional(types[0]));
    Assert.assertTrue(c.isRequiredOrOptional(types[1]));
  }



  /**
   * Check that the
   * {@link ObjectClass#isRequiredOrOptional(AttributeType)} method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dependsOnMethods = "testIsExtensibleObject")
  public void testIsRequiredOrOptionalExtensibleRequiredSuperior()
      throws Exception {
    ObjectClassBuilder builder = new ObjectClassBuilder("parent",
        "1.2.3");
    builder.addRequiredAttributeTypes(types[0]);
    ObjectClass parent = builder.getInstance();

    builder = new ObjectClassBuilder("extensibleObject", "1.2.3");
    builder.setSuperior(parent);
    ObjectClass c = builder.getInstance();

    Assert.assertTrue(c.isRequiredOrOptional(types[0]));
    Assert.assertTrue(c.isRequiredOrOptional(types[1]));
  }



  /**
   * Check the {@link ObjectClass#toString()} method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testToStringDefault() throws Exception {
    ObjectClassBuilder builder = new ObjectClassBuilder(null, "1.2.3");
    ObjectClass type = builder.getInstance();
    Assert.assertEquals(type.toString(), "( 1.2.3 STRUCTURAL )");
  }



  /**
   * Check the {@link ObjectClass#toString()} method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testToString() throws Exception {
    ObjectClassBuilder builder = new ObjectClassBuilder(
        "parentClass", "1.2.1");
    ObjectClass parent = builder.getInstance();

    builder = new ObjectClassBuilder("childClass", "1.2.2");
    builder.addTypeNames("anotherName");
    builder.setDescription("A description");
    builder.setObjectClassType(ObjectClassType.ABSTRACT);
    builder.setObsolete(true);
    builder.setSuperior(parent);
    builder.addRequiredAttributeTypes(types[0], types[1], types[2]);
    builder.addOptionalAttributeTypes(types[3]);
    builder.addExtraProperty(
        ServerConstants.SCHEMA_PROPERTY_FILENAME, "/foo/bar");
    ObjectClass type = builder.getInstance();
    Assert.assertEquals(type.toString(), "( 1.2.2 "
        + "NAME ( 'childClass' 'anotherName' ) "
        + "DESC 'A description' " + "OBSOLETE " + "SUP parentClass "
        + "ABSTRACT " + "MUST ( testType0 $ testType1 $ testType2 ) "
        + "MAY testType3 " + "X-SCHEMA-FILE '/foo/bar' )");
  }



  /**
   * {@inheritDoc}
   */
  @Override
  protected SchemaDefinitionBuilder getBuilder(String name, String oid) {
    return new ObjectClassBuilder(name, oid);
  }

}
