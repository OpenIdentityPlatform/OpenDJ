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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.types;



import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;



/**
 * This class defines a set of tests for the
 * org.opends.server.core.AttributeBuilder class.
 */
public class AttributeBuilderTest extends TypesTestCase
{

  // CN attribute type used in all tests.
  private AttributeType cnType = null;

  // CN attribute value used in all tests.
  private AttributeValue cnValue = null;

  private final String[] noOptions = new String[] {};

  private final String[] noValues = new String[] {};

  private final String[] oneOption = new String[]
  {
    "option1"
  };

  private final String[] oneValue = new String[]
  {
    "value1"
  };

  private final String[] replaceValues = new String[]
  {
      "value2", "value4"
  };

  private final String[] threeOptions = new String[]
  {
      "option1", "option2", "option3"
  };

  private final String[] threeValues = new String[]
  {
      "value1", "value2", "value3"
  };

  private final String[] twoOptions = new String[]
  {
      "option1", "option2"
  };

  private final String[] twoValues = new String[]
  {
      "value1", "value2"
  };



  /**
   * Attribute data provider.
   *
   * @return The array of test attributes and their expected state.
   */
  @DataProvider(name = "createAttributes")
  public Object[][] createAttributes()
  {
    // Test testCase #, Attribute a, AttributeType type, String name,
    // String[] options, String[] values.

    return new Object[][]
    {
        {
            1,
            Attributes.empty(cnType),
            cnType,
            "cn",
            noOptions,
            noValues
        },
        {
            2, Attributes.empty("cn"), cnType, "cn", noOptions, noValues
        },
        {
            3, Attributes.empty("CN"), cnType, "CN", noOptions, noValues
        },
        {
            4,
            Attributes.empty(cnType, "CN"),
            cnType,
            "CN",
            noOptions,
            noValues
        },
        {
            5,
            Attributes.empty(Attributes.empty(cnType, "CN")),
            cnType,
            "CN",
            noOptions,
            noValues
        },
        {
            6,
            Attributes.empty(Attributes.create(cnType, "CN",
                "john doe")),
            cnType,
            "CN",
            noOptions,
            noValues
        },
        {
            7,
            Attributes.create(cnType, cnValue),
            cnType,
            "cn",
            noOptions,
            new String[]
            {
              cnValue.getStringValue()
            }
        },
        {
            8,
            Attributes.create(cnType, "JOHN DOE"),
            cnType,
            "cn",
            noOptions,
            new String[]
            {
              cnValue.getStringValue()
            }
        },
        {
            9,
            Attributes.create("cn", "JOHN DOE"),
            cnType,
            "cn",
            noOptions,
            new String[]
            {
              cnValue.getStringValue()
            }
        },
        {
            10,
            Attributes.create("CN", "JOHN DOE"),
            cnType,
            "CN",
            noOptions,
            new String[]
            {
              cnValue.getStringValue()
            }
        },
        {
            11,
            Attributes.create(cnType, "CN", cnValue),
            cnType,
            "CN",
            noOptions,
            new String[]
            {
              cnValue.getStringValue()
            }
        },
        {
            12,
            Attributes.create(cnType, "CN", "JOHN DOE"),
            cnType,
            "CN",
            noOptions,
            new String[]
            {
              cnValue.getStringValue()
            }
        },
        {
            13,
            createAttribute(cnType, "cn", noOptions, noValues),
            cnType,
            "cn",
            noOptions,
            noValues
        },
        {
            14,
            createAttribute(cnType, "cn", oneOption, noValues),
            cnType,
            "cn",
            oneOption,
            noValues
        },
        {
            15,
            createAttribute(cnType, "cn", twoOptions, noValues),
            cnType,
            "cn",
            twoOptions,
            noValues
        },
        {
            16,
            createAttribute(cnType, "cn", threeOptions, noValues),
            cnType,
            "cn",
            threeOptions,
            noValues
        },
        {
            17,
            createAttribute(cnType, "cn", noOptions, oneValue),
            cnType,
            "cn",
            noOptions,
            oneValue
        },
        {
            18,
            createAttribute(cnType, "cn", oneOption, oneValue),
            cnType,
            "cn",
            oneOption,
            oneValue
        },
        {
            19,
            createAttribute(cnType, "cn", twoOptions, oneValue),
            cnType,
            "cn",
            twoOptions,
            oneValue
        },
        {
            20,
            createAttribute(cnType, "cn", threeOptions, oneValue),
            cnType,
            "cn",
            threeOptions,
            oneValue
        },
        {
            21,
            createAttribute(cnType, "cn", noOptions, twoValues),
            cnType,
            "cn",
            noOptions,
            twoValues
        },
        {
            22,
            createAttribute(cnType, "cn", oneOption, twoValues),
            cnType,
            "cn",
            oneOption,
            twoValues
        },
        {
            23,
            createAttribute(cnType, "cn", twoOptions, twoValues),
            cnType,
            "cn",
            twoOptions,
            twoValues
        },
        {
            24,
            createAttribute(cnType, "cn", threeOptions, twoValues),
            cnType,
            "cn",
            threeOptions,
            twoValues
        },
        {
            25,
            createAttribute(cnType, "cn", noOptions, threeValues),
            cnType,
            "cn",
            noOptions,
            threeValues
        },
        {
            26,
            createAttribute(cnType, "cn", oneOption, threeValues),
            cnType,
            "cn",
            oneOption,
            threeValues
        },
        {
            27,
            createAttribute(cnType, "cn", twoOptions, threeValues),
            cnType,
            "cn",
            twoOptions,
            threeValues
        },
        {
            28,
            createAttribute(cnType, "cn", threeOptions, threeValues),
            cnType,
            "cn",
            threeOptions,
            threeValues
        },
        {
            29,
            new AttributeBuilder(cnType).toAttribute(),
            cnType,
            "cn",
            noOptions,
            noValues
        },
        {
            30,
            new AttributeBuilder("cn").toAttribute(),
            cnType,
            "cn",
            noOptions,
            noValues
        },
        {
            31,
            new AttributeBuilder("CN").toAttribute(),
            cnType,
            "CN",
            noOptions,
            noValues
        },
        {
            32,
            new AttributeBuilder(cnType, "cn").toAttribute(),
            cnType,
            "cn",
            noOptions,
            noValues
        },
        {
            33,
            new AttributeBuilder(cnType, "CN").toAttribute(),
            cnType,
            "CN",
            noOptions,
            noValues
        },
        {
            34,
            new AttributeBuilder(createAttribute(cnType, "CN", threeOptions,
                threeValues)).toAttribute(),
            cnType,
            "CN",
            threeOptions,
            threeValues
        },
        {
            35,
            new AttributeBuilder(createAttribute(cnType, "CN", threeOptions,
                threeValues), false).toAttribute(),
            cnType,
            "CN",
            threeOptions,
            threeValues
        },
        {
            36,
            new AttributeBuilder(createAttribute(cnType, "CN", threeOptions,
                threeValues), true).toAttribute(),
            cnType,
            "CN",
            threeOptions,
            noValues
        },
    };
  }



  /**
   * Attribute data provider.
   *
   * @return The array of test attributes and their expected state.
   */
  @DataProvider(name = "createCompareAttributes")
  public Object[][] createCompareAttributes()
  {
    // Test testCase #, Attribute a1, Attribute a2, isEquals.

    return new Object[][]
    {
        {
            1,
            Attributes.empty(cnType),
            Attributes.empty(cnType),
            true
        },
        {
            2,
            Attributes.empty(cnType, "CN"),
            Attributes.empty(cnType, "cn"),
            true
        },
        {
            3,
            Attributes.empty(cnType),
            Attributes.empty("CN"),
            true
        },
        {
            4, Attributes.empty("cn"), Attributes.empty("cn"), true
        },
        {
            5, Attributes.empty("cn"), Attributes.empty("CN"), true
        },
        {
            6, Attributes.empty("CN"), Attributes.empty("cn"), true
        },
        {
            7,
            Attributes.empty("CN"),
            Attributes.empty("description"),
            false
        },
        {
            8,
            Attributes.empty("description"),
            Attributes.empty("cn"),
            false
        },
        {
            9,
            Attributes.create("CN", "test"),
            Attributes.create("cn", "test"),
            true
        },
        {
            10,
            Attributes.create("description", "test"),
            Attributes.create("cn", "test"),
            false
        },
        {
            11,
            Attributes.create("cn", "test1"),
            Attributes.create("cn", "test2"),
            false
        },
        {
            12,
            Attributes.create("CN", "test"),
            Attributes.create("cn", "TEST"),
            true
        },
        {
            13,
            Attributes.empty("cn"),
            Attributes.create("cn", "TEST"),
            false
        },
        {
            14,
            createAttribute(cnType, "cn", noOptions, noValues),
            createAttribute(cnType, "cn", oneOption, noValues),
            false
        },
        {
            15,
            createAttribute(cnType, "cn", noOptions, noValues),
            createAttribute(cnType, "cn", twoOptions, noValues),
            false
        },
        {
            16,
            createAttribute(cnType, "cn", oneOption, noValues),
            createAttribute(cnType, "cn", oneOption, noValues),
            true
        },
        {
            17,
            createAttribute(cnType, "cn", twoOptions, noValues),
            createAttribute(cnType, "cn", twoOptions, noValues),
            true
        },
        {
            18,
            createAttribute(cnType, "cn", oneOption, noValues),
            createAttribute(cnType, "cn", noOptions, noValues),
            false
        },
        {
            19,
            createAttribute(cnType, "cn", twoOptions, noValues),
            createAttribute(cnType, "cn", noOptions, noValues),
            false
        },
        {
            20,
            createAttribute(cnType, "cn", noOptions, noValues),
            createAttribute(cnType, "cn", noOptions, oneValue),
            false
        },
        {
            21,
            createAttribute(cnType, "cn", noOptions, oneValue),
            createAttribute(cnType, "cn", noOptions, noValues),
            false
        },
        {
            22,
            createAttribute(cnType, "cn", noOptions, noValues),
            createAttribute(cnType, "cn", noOptions, twoValues),
            false
        },
        {
            23,
            createAttribute(cnType, "cn", noOptions, twoValues),
            createAttribute(cnType, "cn", noOptions, noValues),
            false
        },
        {
            24,
            createAttribute(cnType, "cn", noOptions, oneValue),
            createAttribute(cnType, "cn", noOptions, twoValues),
            false
        },
        {
            25,
            createAttribute(cnType, "cn", noOptions, twoValues),
            createAttribute(cnType, "cn", noOptions, oneValue),
            false
        },
        {
            26,
            createAttribute(cnType, "cn", oneOption, oneValue),
            createAttribute(cnType, "cn", oneOption, oneValue),
            true
        },
        {
            27,
            createAttribute(cnType, "cn", twoOptions, twoValues),
            createAttribute(cnType, "cn", twoOptions, twoValues),
            true
        },
    };
  }



  /**
   * Set up the environment for performing the tests in this suite.
   *
   * @throws Exception
   *           If the environment could not be set up.
   */
  @BeforeClass
  public void setUp() throws Exception
  {
    // This test suite depends on having the schema available, so
    // we'll start the server.
    TestCaseUtils.startServer();

    // Initialize the CN attribute type used in all tests.
    cnType = DirectoryServer.getAttributeType("cn");
    Assert.assertNotNull(cnType);

    cnValue = new AttributeValue(cnType, "john doe");
  }



  /**
   * Tests {@link AttributeBuilder#addAll(Attribute)}.
   *
   * @throws Exception
   *           If an unexpected error occurs.
   */
  @Test
  public void testAttributeBuilderAddAllAttribute() throws Exception
  {
    AttributeBuilder builder = new AttributeBuilder(cnType);

    Assert.assertTrue(builder.addAll(createAttribute(cnType, "cn", noOptions,
        twoValues)));
    Assert.assertEquals(builder.size(), 2);

    // Add same values.
    Assert.assertFalse(builder.addAll(createAttribute(cnType, "cn", noOptions,
        twoValues)));
    Assert.assertEquals(builder.size(), 2);

    Attribute a = builder.toAttribute();
    Assert.assertEquals(a.size(), 2);
    Assert.assertTrue(a.contains(new AttributeValue(cnType, "value1")));
    Assert.assertTrue(a.contains(new AttributeValue(cnType, "value2")));
  }



  /**
   * Tests {@link AttributeBuilder#addAll(java.util.Collection)}.
   *
   * @throws Exception
   *           If an unexpected error occurs.
   */
  @Test
  public void testAttributeBuilderAddAllAttributeValues() throws Exception
  {
    AttributeBuilder builder = new AttributeBuilder(cnType);

    // Note duplicate values.
    Assert.assertTrue(builder.addAll(Arrays.asList(new AttributeValue(cnType,
        "value1"), new AttributeValue(cnType, "value1"), new AttributeValue(
        cnType, "value2"))));
    Assert.assertEquals(builder.size(), 2);

    // Add same values.
    Assert.assertFalse(builder.addAll(Arrays.asList(new AttributeValue(cnType,
        "value1"), new AttributeValue(cnType, "value1"), new AttributeValue(
        cnType, "value2"))));
    Assert.assertEquals(builder.size(), 2);

    Attribute a = builder.toAttribute();
    Assert.assertEquals(a.size(), 2);
    Assert.assertTrue(a.contains(new AttributeValue(cnType, "value1")));
    Assert.assertTrue(a.contains(new AttributeValue(cnType, "value2")));
  }



  /**
   * Tests {@link AttributeBuilder#add(AttributeValue)}.
   *
   * @throws Exception
   *           If an unexpected error occurs.
   */
  @Test
  public void testAttributeBuilderAddAttributeValue() throws Exception
  {
    AttributeBuilder builder = new AttributeBuilder(cnType);

    Assert.assertTrue(builder.add(cnValue));
    Assert.assertEquals(builder.size(), 1);

    Assert.assertFalse(builder.add(cnValue));
    Assert.assertEquals(builder.size(), 1);

    Assert.assertTrue(builder.add(new AttributeValue(cnType, "jane doe")));
    Assert.assertEquals(builder.size(), 2);

    Attribute a = builder.toAttribute();
    Assert.assertEquals(a.size(), 2);
    Assert.assertTrue(a.contains(cnValue));
    Assert.assertTrue(a.contains(new AttributeValue(cnType, "jane doe")));
  }



  /**
   * Tests {@link AttributeBuilder#add(String)}.
   *
   * @throws Exception
   *           If an unexpected error occurs.
   */
  @Test
  public void testAttributeBuilderAddString() throws Exception
  {
    AttributeBuilder builder = new AttributeBuilder(cnType);

    Assert.assertTrue(builder.add("value1"));
    Assert.assertEquals(builder.size(), 1);

    Assert.assertFalse(builder.add("value1"));
    Assert.assertEquals(builder.size(), 1);

    Assert.assertTrue(builder.add("value2"));
    Assert.assertEquals(builder.size(), 2);

    Attribute a = builder.toAttribute();
    Assert.assertEquals(a.size(), 2);
    Assert.assertTrue(a.contains(new AttributeValue(cnType, "value1")));
    Assert.assertTrue(a.contains(new AttributeValue(cnType, "value2")));
  }



  /**
   * Tests {@link AttributeBuilder#clear()}.
   *
   * @throws Exception
   *           If an unexpected error occurs.
   */
  @Test
  public void testAttributeBuilderClear() throws Exception
  {
    AttributeBuilder builder = new AttributeBuilder(cnType);

    builder.addAll(createAttribute(cnType, "cn", noOptions, twoValues));
    Assert.assertEquals(builder.size(), 2);

    builder.clear();
    Assert.assertEquals(builder.size(), 0);

    Attribute a = builder.toAttribute();
    Assert.assertTrue(a.isEmpty());
  }



  /**
   * Tests {@link AttributeBuilder#contains(AttributeValue)}.
   *
   * @throws Exception
   *           If an unexpected error occurs.
   */
  @Test
  public void testAttributeBuilderContains() throws Exception
  {
    AttributeBuilder builder = new AttributeBuilder();

    builder.addAll(createAttribute(cnType, "cn", noOptions, twoValues));

    Assert.assertTrue(builder.contains(new AttributeValue(cnType, "value1")));
    Assert.assertTrue(builder.contains(new AttributeValue(cnType, "value2")));
    Assert.assertFalse(builder.contains(new AttributeValue(cnType, "value3")));
  }



  /**
   * Tests {@link AttributeBuilder#containsAll(java.util.Collection)}.
   *
   * @throws Exception
   *           If an unexpected error occurs.
   */
  @Test
  public void testAttributeBuilderContainsAll() throws Exception
  {
    AttributeBuilder builder = new AttributeBuilder(cnType);

    builder.addAll(createAttribute(cnType, "cn", noOptions, twoValues));

    AttributeValue av1 = new AttributeValue(cnType, "value1");
    AttributeValue av2 = new AttributeValue(cnType, "value2");
    AttributeValue av3 = new AttributeValue(cnType, "value3");

    Assert.assertTrue(builder.containsAll(Collections
        .<AttributeValue> emptySet()));

    Assert.assertTrue(builder.containsAll(Collections.singleton(av1)));
    Assert.assertTrue(builder.containsAll(Collections.singleton(av2)));
    Assert.assertFalse(builder.containsAll(Collections.singleton(av3)));

    Assert.assertTrue(builder.containsAll(Arrays.asList(av1, av2)));
    Assert.assertFalse(builder.containsAll(Arrays.asList(av1, av3)));
    Assert.assertFalse(builder.containsAll(Arrays.asList(av2, av3)));

    Assert.assertFalse(builder.containsAll(Arrays.asList(av1, av2, av3)));
  }



  /**
   * Tests {@link AttributeBuilder#getAttributeType()}.
   *
   * @throws Exception
   *           If an unexpected error occurs.
   */
  @Test
  public void testAttributeBuilderGetAttributeType() throws Exception
  {
    AttributeBuilder builder = new AttributeBuilder(cnType);
    Assert.assertEquals(builder.getAttributeType(), cnType);
  }



  /**
   * Tests {@link AttributeBuilder#toAttribute()} throws
   * IllegalStateException after default constructor.
   *
   * @throws Exception
   *           If an unexpected error occurs.
   */
  @Test(expectedExceptions = IllegalStateException.class)
  public void testAttributeBuilderIllegalStateException1() throws Exception
  {
    AttributeBuilder builder = new AttributeBuilder();
    builder.toAttribute();
  }



  /**
   * Tests {@link AttributeBuilder#toAttribute()} throws
   * IllegalStateException when called twice.
   *
   * @throws Exception
   *           If an unexpected error occurs.
   */
  @Test(expectedExceptions = IllegalStateException.class)
  public void testAttributeBuilderIllegalStateException2() throws Exception
  {
    AttributeBuilder builder = new AttributeBuilder(cnType);

    try
    {
      builder.toAttribute();
    }
    catch (IllegalStateException e)
    {
      Assert.fail("Got unexpected IllegalStateException");
    }

    builder.toAttribute();
  }



  /**
   * Tests {@link AttributeBuilder#isEmpty()}.
   *
   * @throws Exception
   *           If an unexpected error occurs.
   */
  @Test
  public void testAttributeBuilderIsEmpty() throws Exception
  {
    AttributeBuilder builder = new AttributeBuilder(cnType);

    Assert.assertTrue(builder.isEmpty());

    builder.add("value1");
    Assert.assertFalse(builder.isEmpty());

    builder.add("value2");
    Assert.assertFalse(builder.isEmpty());
  }



  /**
   * Tests {@link AttributeBuilder#iterator()}.
   *
   * @throws Exception
   *           If an unexpected error occurs.
   */
  @Test
  public void testAttributeBuilderIterator() throws Exception
  {
    AttributeBuilder builder = new AttributeBuilder(cnType);

    Assert.assertFalse(builder.iterator().hasNext());

    try
    {
      builder.iterator().next();
      Assert.fail("Iteration possible when builder is empty");
    }
    catch (NoSuchElementException e)
    {
      // Expected.
    }

    builder.add("value1");
    Assert.assertTrue(builder.iterator().hasNext());
    Assert.assertEquals(builder.iterator().next(), new AttributeValue(cnType,
        "value1"));
  }



  /**
   * Tests {@link AttributeBuilder#removeAll(Attribute)}.
   *
   * @throws Exception
   *           If an unexpected error occurs.
   */
  @Test
  public void testAttributeBuilderRemoveAllAttribute() throws Exception
  {
    AttributeBuilder builder = new AttributeBuilder(cnType);

    builder.addAll(createAttribute(cnType, "cn", noOptions, threeValues));

    // Remove existing values.
    Assert.assertTrue(builder.removeAll(createAttribute(cnType, "cn",
        noOptions, twoValues)));
    Assert.assertEquals(builder.size(), 1);

    // Remove removed values.
    Assert.assertFalse(builder.removeAll(createAttribute(cnType, "cn",
        noOptions, twoValues)));
    Assert.assertEquals(builder.size(), 1);

    // Remove nothing.
    Assert.assertFalse(builder.removeAll(Attributes.empty(cnType)));
    Assert.assertEquals(builder.size(), 1);

    // Remove non existent value.
    Assert.assertFalse(builder.removeAll(Attributes.create(cnType,
        "value4")));
    Assert.assertEquals(builder.size(), 1);

    Attribute a = builder.toAttribute();
    Assert.assertEquals(a.size(), 1);
    Assert.assertTrue(a.contains(new AttributeValue(cnType, "value3")));
  }



  /**
   * Tests {@link AttributeBuilder#removeAll(java.util.Collection)}.
   *
   * @throws Exception
   *           If an unexpected error occurs.
   */
  @Test
  public void testAttributeBuilderRemoveAllAttributeValues() throws Exception
  {
    AttributeBuilder builder = new AttributeBuilder(cnType);

    builder.addAll(createAttribute(cnType, "cn", noOptions, threeValues));

    // Remove existing values.
    Assert.assertTrue(builder.removeAll(Arrays.asList(new AttributeValue(
        cnType, "value1"), new AttributeValue(cnType, "value2"))));
    Assert.assertEquals(builder.size(), 1);

    // Remove removed values.
    Assert.assertFalse(builder.removeAll(Arrays.asList(new AttributeValue(
        cnType, "value1"), new AttributeValue(cnType, "value2"))));
    Assert.assertEquals(builder.size(), 1);

    // Remove nothing.
    Assert.assertFalse(builder.removeAll(Collections
        .<AttributeValue> emptySet()));
    Assert.assertEquals(builder.size(), 1);

    // Remove non existent value.
    Assert.assertFalse(builder.removeAll(Collections
        .singleton(new AttributeValue(cnType, "value4"))));
    Assert.assertEquals(builder.size(), 1);

    Attribute a = builder.toAttribute();
    Assert.assertEquals(a.size(), 1);
    Assert.assertTrue(a.contains(new AttributeValue(cnType, "value3")));
  }



  /**
   * Tests {@link AttributeBuilder#remove(AttributeValue)}.
   *
   * @throws Exception
   *           If an unexpected error occurs.
   */
  @Test
  public void testAttributeBuilderRemoveAttributeValue() throws Exception
  {
    AttributeBuilder builder = new AttributeBuilder(cnType);

    builder.addAll(createAttribute(cnType, "cn", noOptions, threeValues));

    Assert.assertTrue(builder.remove(new AttributeValue(cnType, "value1")));
    Assert.assertEquals(builder.size(), 2);

    // Already removed.
    Assert.assertFalse(builder.remove(new AttributeValue(cnType, "value1")));
    Assert.assertEquals(builder.size(), 2);

    // Non existent.
    Assert.assertFalse(builder.remove(new AttributeValue(cnType, "value4")));
    Assert.assertEquals(builder.size(), 2);

    Attribute a = builder.toAttribute();
    Assert.assertEquals(a.size(), 2);
    Assert.assertTrue(a.contains(new AttributeValue(cnType, "value2")));
    Assert.assertTrue(a.contains(new AttributeValue(cnType, "value3")));
  }



  /**
   * Tests {@link AttributeBuilder#remove(String)}.
   *
   * @throws Exception
   *           If an unexpected error occurs.
   */
  @Test
  public void testAttributeBuilderRemoveString() throws Exception
  {
    AttributeBuilder builder = new AttributeBuilder(cnType);

    builder.addAll(createAttribute(cnType, "cn", noOptions, threeValues));

    Assert.assertTrue(builder.remove("value1"));
    Assert.assertEquals(builder.size(), 2);

    // Already removed.
    Assert.assertFalse(builder.remove("value1"));
    Assert.assertEquals(builder.size(), 2);

    // Non existent.
    Assert.assertFalse(builder.remove("value4"));
    Assert.assertEquals(builder.size(), 2);

    Attribute a = builder.toAttribute();
    Assert.assertEquals(a.size(), 2);
    Assert.assertTrue(a.contains(new AttributeValue(cnType, "value2")));
    Assert.assertTrue(a.contains(new AttributeValue(cnType, "value3")));
  }



  /**
   * Tests {@link AttributeBuilder#replaceAll(Attribute)}.
   *
   * @throws Exception
   *           If an unexpected error occurs.
   */
  @Test
  public void testAttributeBuilderReplaceAllAttribute() throws Exception
  {
    AttributeBuilder builder = new AttributeBuilder(cnType);
    builder.addAll(createAttribute(cnType, "cn", noOptions, threeValues));

    builder.replaceAll(createAttribute(cnType, "cn", noOptions, replaceValues));
    Assert.assertEquals(builder.size(), 2);

    Attribute a = builder.toAttribute();
    Assert.assertEquals(a.size(), 2);
    Assert.assertTrue(a.contains(new AttributeValue(cnType, "value2")));
    Assert.assertTrue(a.contains(new AttributeValue(cnType, "value4")));
  }



  /**
   * Tests {@link AttributeBuilder#replaceAll(java.util.Collection)}.
   *
   * @throws Exception
   *           If an unexpected error occurs.
   */
  @Test
  public void testAttributeBuilderReplaceAllAttributeValues() throws Exception
  {
    AttributeBuilder builder = new AttributeBuilder(cnType);
    builder.addAll(createAttribute(cnType, "cn", noOptions, threeValues));

    // Note duplicate values.
    builder.replaceAll(Arrays.asList(new AttributeValue(cnType, "value2"),
        new AttributeValue(cnType, "value2"), new AttributeValue(cnType,
            "value4")));
    Assert.assertEquals(builder.size(), 2);

    Attribute a = builder.toAttribute();
    Assert.assertEquals(a.size(), 2);
    Assert.assertTrue(a.contains(new AttributeValue(cnType, "value2")));
    Assert.assertTrue(a.contains(new AttributeValue(cnType, "value4")));
  }



  /**
   * Tests {@link AttributeBuilder#replace(AttributeValue)}.
   *
   * @throws Exception
   *           If an unexpected error occurs.
   */
  @Test
  public void testAttributeBuilderReplaceAttributeValue() throws Exception
  {
    AttributeBuilder builder = new AttributeBuilder(cnType);
    builder.addAll(createAttribute(cnType, "cn", noOptions, threeValues));

    builder.replace(new AttributeValue(cnType, "value4"));
    Assert.assertEquals(builder.size(), 1);

    Attribute a = builder.toAttribute();
    Assert.assertEquals(a.size(), 1);
    Assert.assertTrue(a.contains(new AttributeValue(cnType, "value4")));
  }



  /**
   * Tests {@link AttributeBuilder#replace(String)}.
   *
   * @throws Exception
   *           If an unexpected error occurs.
   */
  @Test
  public void testAttributeBuilderReplaceString() throws Exception
  {
    AttributeBuilder builder = new AttributeBuilder(cnType);
    builder.addAll(createAttribute(cnType, "cn", noOptions, threeValues));

    builder.replace("value4");
    Assert.assertEquals(builder.size(), 1);

    Attribute a = builder.toAttribute();
    Assert.assertEquals(a.size(), 1);
    Assert.assertTrue(a.contains(new AttributeValue(cnType, "value4")));
  }



  /**
   * Tests {@link AttributeBuilder#setAttributeType(AttributeType)}.
   *
   * @throws Exception
   *           If an unexpected error occurs.
   */
  @Test
  public void testAttributeBuilderSetAttributeType1() throws Exception
  {
    AttributeBuilder builder = new AttributeBuilder();

    Assert.assertNull(builder.getAttributeType());

    builder.setAttributeType(cnType);
    Assert.assertEquals(builder.getAttributeType(), cnType);

    Attribute a = builder.toAttribute();
    Assert.assertEquals(a.getAttributeType(), cnType);
    Assert.assertEquals(a.getName(), "cn");
  }



  /**
   * Tests {@link AttributeBuilder#setAttributeType(String)}.
   *
   * @throws Exception
   *           If an unexpected error occurs.
   */
  @Test
  public void testAttributeBuilderSetAttributeType2() throws Exception
  {
    AttributeBuilder builder = new AttributeBuilder();

    Assert.assertNull(builder.getAttributeType());

    builder.setAttributeType("cn");
    Assert.assertEquals(builder.getAttributeType(), cnType);

    Attribute a = builder.toAttribute();
    Assert.assertEquals(a.getAttributeType(), cnType);
    Assert.assertEquals(a.getName(), "cn");
  }



  /**
   * Tests {@link AttributeBuilder#setAttributeType(String)}.
   *
   * @throws Exception
   *           If an unexpected error occurs.
   */
  @Test
  public void testAttributeBuilderSetAttributeType3() throws Exception
  {
    AttributeBuilder builder = new AttributeBuilder();

    Assert.assertNull(builder.getAttributeType());

    builder.setAttributeType("CN");
    Assert.assertEquals(builder.getAttributeType(), cnType);

    Attribute a = builder.toAttribute();
    Assert.assertEquals(a.getAttributeType(), cnType);
    Assert.assertEquals(a.getName(), "CN");
  }



  /**
   * Tests
   * {@link AttributeBuilder#setAttributeType(AttributeType, String)}.
   *
   * @throws Exception
   *           If an unexpected error occurs.
   */
  @Test
  public void testAttributeBuilderSetAttributeType4() throws Exception
  {
    AttributeBuilder builder = new AttributeBuilder();

    Assert.assertNull(builder.getAttributeType());

    builder.setAttributeType(cnType, "CN");
    Assert.assertEquals(builder.getAttributeType(), cnType);

    Attribute a = builder.toAttribute();
    Assert.assertEquals(a.getAttributeType(), cnType);
    Assert.assertEquals(a.getName(), "CN");
  }



  /**
   * Tests {@link AttributeBuilder#setOptions(java.util.Collection)}.
   *
   * @throws Exception
   *           If an unexpected error occurs.
   */
  @Test
  public void testAttributeBuilderSetOptions() throws Exception
  {
    AttributeBuilder builder = new AttributeBuilder(cnType);
    builder.setOptions(Arrays.asList(threeOptions));
    Attribute a = builder.toAttribute();

    Assert.assertTrue(a.getOptions().containsAll(Arrays.asList(threeOptions)));
    Assert.assertEquals(a.getOptions().size(), threeOptions.length);
  }



  /**
   * Tests {@link AttributeBuilder#size()}.
   *
   * @throws Exception
   *           If an unexpected error occurs.
   */
  @Test
  public void testAttributeBuilderSize() throws Exception
  {
    AttributeBuilder builder = new AttributeBuilder(cnType);

    Assert.assertEquals(builder.size(), 0);

    builder.add("value1");
    Assert.assertEquals(builder.size(), 1);

    builder.add("value2");
    Assert.assertEquals(builder.size(), 2);

    builder.add("value3");
    Assert.assertEquals(builder.size(), 3);
  }



  /**
   * Tests {@link Attribute#contains(AttributeValue)}.
   *
   * @param testCase
   *          Test case index (useful for debugging).
   * @param a
   *          The attribute.
   * @param type
   *          The expected attribute type.
   * @param name
   *          The expected user provided attribute name.
   * @param options
   *          The expected attribute options.
   * @param values
   *          The expected attribute values.
   * @throws Exception
   *           If an unexpected error occurs.
   */
  @Test(dataProvider = "createAttributes", dependsOnMethods = "testAttributeNotNull")
  public void testAttributeContains(int testCase, Attribute a,
      AttributeType type, String name, String[] options, String[] values)
      throws Exception
  {
    // Check contains().
    for (String value : values)
    {
      Assert.assertTrue(a.contains(new AttributeValue(type, value)));

      // Assumes internal normalization to lower-case.
      Assert.assertTrue(a
          .contains(new AttributeValue(type, value.toUpperCase())));
    }

    Assert.assertFalse(a.contains(new AttributeValue(type, "xxxx")));
  }



  /**
   * Tests {@link Attribute#containsAll(java.util.Collection)}.
   *
   * @param testCase
   *          Test case index (useful for debugging).
   * @param a
   *          The attribute.
   * @param type
   *          The expected attribute type.
   * @param name
   *          The expected user provided attribute name.
   * @param options
   *          The expected attribute options.
   * @param values
   *          The expected attribute values.
   * @throws Exception
   *           If an unexpected error occurs.
   */
  @Test(dataProvider = "createAttributes", dependsOnMethods = "testAttributeNotNull")
  public void testAttributeContainsAll(int testCase, Attribute a,
      AttributeType type, String name, String[] options, String[] values)
      throws Exception
  {
    // Check containsAll().
    Set<AttributeValue> expectedValues = new HashSet<AttributeValue>();
    for (String value : values)
    {
      expectedValues.add(new AttributeValue(type, value));
    }

    Assert.assertTrue(a.containsAll(Collections.<AttributeValue> emptySet()));
    Assert.assertTrue(a.containsAll(expectedValues));

    if (values.length > 1)
    {
      Set<AttributeValue> subSet = new HashSet<AttributeValue>(expectedValues);
      subSet.remove(subSet.iterator());
      Assert.assertTrue(a.containsAll(subSet));
    }

    Set<AttributeValue> bigSet = new HashSet<AttributeValue>(expectedValues);
    bigSet.add(new AttributeValue(type, "xxxx"));
    Assert.assertFalse(a.containsAll(bigSet));

    expectedValues.clear();
    for (String value : values)
    {
      // Assumes internal normalization to lower-case.
      expectedValues.add(new AttributeValue(type, value.toUpperCase()));
    }
    Assert.assertTrue(a.containsAll(expectedValues));
  }



  /**
   * Tests {@link Attribute#equals(Object)}.
   *
   * @param testCase
   *          Test case index (useful for debugging).
   * @param a1
   *          The first attribute.
   * @param a2
   *          The second attribute.
   * @param isEqual
   *          The expected result of equals.
   * @throws Exception
   *           If an unexpected error occurs.
   */
  @Test(dataProvider = "createCompareAttributes", dependsOnMethods = "testAttributeNotNull")
  public void testAttributeEquals(int testCase, Attribute a1, Attribute a2,
      boolean isEqual) throws Exception
  {
    Assert.assertEquals(a1.equals(a2), isEqual);
  }



  /**
   * Tests {@link Attribute#getAttributeType()}.
   *
   * @param testCase
   *          Test case index (useful for debugging).
   * @param a
   *          The attribute.
   * @param type
   *          The expected attribute type.
   * @param name
   *          The expected user provided attribute name.
   * @param options
   *          The expected attribute options.
   * @param values
   *          The expected attribute values.
   * @throws Exception
   *           If an unexpected error occurs.
   */
  @Test(dataProvider = "createAttributes", dependsOnMethods = "testAttributeNotNull")
  public void testAttributeGetAttribute(int testCase, Attribute a,
      AttributeType type, String name, String[] options, String[] values)
      throws Exception
  {
    // Check type and provided name.
    Assert.assertEquals(a.getAttributeType(), type);
  }



  /**
   * Tests {@link Attribute#getName()}.
   *
   * @param testCase
   *          Test case index (useful for debugging).
   * @param a
   *          The attribute.
   * @param type
   *          The expected attribute type.
   * @param name
   *          The expected user provided attribute name.
   * @param options
   *          The expected attribute options.
   * @param values
   *          The expected attribute values.
   * @throws Exception
   *           If an unexpected error occurs.
   */
  @Test(dataProvider = "createAttributes", dependsOnMethods = "testAttributeNotNull")
  public void testAttributeGetName(int testCase, Attribute a,
      AttributeType type, String name, String[] options, String[] values)
      throws Exception
  {
    Assert.assertEquals(a.getName(), name);
  }



  /**
   * Tests {@link Attribute#getNameWithOptions()}.
   *
   * @param testCase
   *          Test case index (useful for debugging).
   * @param a
   *          The attribute.
   * @param type
   *          The expected attribute type.
   * @param name
   *          The expected user provided attribute name.
   * @param options
   *          The expected attribute options.
   * @param values
   *          The expected attribute values.
   * @throws Exception
   *           If an unexpected error occurs.
   */
  @Test(dataProvider = "createAttributes", dependsOnMethods = "testAttributeNotNull")
  public void testAttributeGetNameWithOptions(int testCase, Attribute a,
      AttributeType type, String name, String[] options, String[] values)
      throws Exception
  {
    // Check name and options.
    String[] elements = a.getNameWithOptions().split(";");
    switch (elements.length)
    {
    case 0:
      Assert.fail("Name and options could not be split: "
          + a.getNameWithOptions());
      break;
    case 1:
      Assert.assertEquals(elements[0], name);
      Assert.assertEquals(elements.length - 1 /* 0 */, options.length);
      break;
    default:
      Assert.assertEquals(elements[0], name);
      Assert.assertEquals(elements.length - 1, options.length);

      List<String> expected = Arrays.asList(options);
      List<String> actual = Arrays.asList(elements).subList(1, elements.length);
      Assert.assertTrue(actual.containsAll(expected));
      break;
    }
  }



  /**
   * Tests {@link Attribute#getOptions()}.
   *
   * @param testCase
   *          Test case index (useful for debugging).
   * @param a
   *          The attribute.
   * @param type
   *          The expected attribute type.
   * @param name
   *          The expected user provided attribute name.
   * @param options
   *          The expected attribute options.
   * @param values
   *          The expected attribute values.
   * @throws Exception
   *           If an unexpected error occurs.
   */
  @Test(dataProvider = "createAttributes", dependsOnMethods = "testAttributeNotNull")
  public void testAttributeGetOptions(int testCase, Attribute a,
      AttributeType type, String name, String[] options, String[] values)
      throws Exception
  {
    // Check getOptions().
    Set<String> s = a.getOptions();

    Assert.assertEquals(s.size(), options.length);
    Assert.assertTrue(s.containsAll(Arrays.asList(options)));

    try
    {
      // The option set must be unmodifiable.
      s.add("xxxx");
      Assert.fail("getOptions() returned a modifiable option set");
    }
    catch (UnsupportedOperationException e)
    {
      // Expected exception.
    }
  }



  /**
   * Tests {@link Attribute#hasAllOptions(java.util.Collection)}.
   *
   * @param testCase
   *          Test case index (useful for debugging).
   * @param a
   *          The attribute.
   * @param type
   *          The expected attribute type.
   * @param name
   *          The expected user provided attribute name.
   * @param options
   *          The expected attribute options.
   * @param values
   *          The expected attribute values.
   * @throws Exception
   *           If an unexpected error occurs.
   */
  @Test(dataProvider = "createAttributes", dependsOnMethods = "testAttributeNotNull")
  public void testAttributeHasAllOptions(int testCase, Attribute a,
      AttributeType type, String name, String[] options, String[] values)
      throws Exception
  {
    // Check hasAllOptions().
    Assert.assertTrue(a.hasAllOptions(Collections.<String> emptySet()));
    Assert.assertTrue(a.hasAllOptions(Arrays.asList(options)));

    if (options.length > 1)
    {
      Assert.assertTrue(a.hasAllOptions(Arrays.asList(options).subList(1,
          options.length)));
    }

    List<String> tmp = new ArrayList<String>(Arrays.asList(options));
    tmp.add("xxxx");
    Assert.assertFalse(a.hasAllOptions(tmp));

    tmp.clear();
    for (String option : options)
    {
      // Assumes internal normalization to lower-case.
      tmp.add(option.toUpperCase());
    }
    Assert.assertTrue(a.hasAllOptions(tmp));
  }



  /**
   * Tests {@link Attribute#hashCode()}.
   *
   * @param testCase
   *          Test case index (useful for debugging).
   * @param a1
   *          The first attribute.
   * @param a2
   *          The second attribute.
   * @param isEqual
   *          The expected result of equals.
   * @throws Exception
   *           If an unexpected error occurs.
   */
  @Test(dataProvider = "createCompareAttributes", dependsOnMethods = "testAttributeNotNull")
  public void testAttributeHashCode(int testCase, Attribute a1, Attribute a2,
      boolean isEqual) throws Exception
  {
    // The hash code must be equal if the attributes are equal. Hash
    // codes are not required to be different if the attributes are
    // different.
    if (isEqual)
    {
      Assert.assertEquals(a1.hashCode(), a2.hashCode());
    }
  }



  /**
   * Tests {@link Attribute#hasOption(String)}.
   *
   * @param testCase
   *          Test case index (useful for debugging).
   * @param a
   *          The attribute.
   * @param type
   *          The expected attribute type.
   * @param name
   *          The expected user provided attribute name.
   * @param options
   *          The expected attribute options.
   * @param values
   *          The expected attribute values.
   * @throws Exception
   *           If an unexpected error occurs.
   */
  @Test(dataProvider = "createAttributes", dependsOnMethods = "testAttributeNotNull")
  public void testAttributeHasOption(int testCase, Attribute a,
      AttributeType type, String name, String[] options, String[] values)
      throws Exception
  {
    // Check hasOption().
    for (String option : options)
    {
      Assert.assertTrue(a.hasOption(option));

      // Assumes internal normalization to lower-case.
      Assert.assertTrue(a.hasOption(option.toUpperCase()));
    }

    Assert.assertFalse(a.hasOption("xxxx"));
  }



  /**
   * Tests {@link Attribute#hasOptions()}.
   *
   * @param testCase
   *          Test case index (useful for debugging).
   * @param a
   *          The attribute.
   * @param type
   *          The expected attribute type.
   * @param name
   *          The expected user provided attribute name.
   * @param options
   *          The expected attribute options.
   * @param values
   *          The expected attribute values.
   * @throws Exception
   *           If an unexpected error occurs.
   */
  @Test(dataProvider = "createAttributes", dependsOnMethods = "testAttributeNotNull")
  public void testAttributeHasOptions(int testCase, Attribute a,
      AttributeType type, String name, String[] options, String[] values)
      throws Exception
  {
    // Check hasOptions().
    if (options.length == 0)
    {
      Assert.assertFalse(a.hasOptions());
    }
    else
    {
      Assert.assertTrue(a.hasOptions());
    }
  }



  /**
   * Tests {@link Attribute#isEmpty()}.
   *
   * @param testCase
   *          Test case index (useful for debugging).
   * @param a
   *          The attribute.
   * @param type
   *          The expected attribute type.
   * @param name
   *          The expected user provided attribute name.
   * @param options
   *          The expected attribute options.
   * @param values
   *          The expected attribute values.
   * @throws Exception
   *           If an unexpected error occurs.
   */
  @Test(dataProvider = "createAttributes", dependsOnMethods = "testAttributeNotNull")
  public void testAttributeIsEmpty(int testCase, Attribute a,
      AttributeType type, String name, String[] options, String[] values)
      throws Exception
  {
    // Check isEmpty().
    if (values.length == 0)
    {
      Assert.assertTrue(a.isEmpty());
    }
    else
    {
      Assert.assertFalse(a.isEmpty());
    }
  }



  /**
   * Tests {@link Attribute#isVirtual()}.
   *
   * @param testCase
   *          Test case index (useful for debugging).
   * @param a
   *          The attribute.
   * @param type
   *          The expected attribute type.
   * @param name
   *          The expected user provided attribute name.
   * @param options
   *          The expected attribute options.
   * @param values
   *          The expected attribute values.
   * @throws Exception
   *           If an unexpected error occurs.
   */
  @Test(dataProvider = "createAttributes", dependsOnMethods = "testAttributeNotNull")
  public void testAttributeIsVirtual(int testCase, Attribute a,
      AttributeType type, String name, String[] options, String[] values)
      throws Exception
  {
    // Check isVirtual().
    Assert.assertFalse(a.isVirtual());
  }



  /**
   * Tests {@link Attribute#iterator()}.
   *
   * @param testCase
   *          Test case index (useful for debugging).
   * @param a
   *          The attribute.
   * @param type
   *          The expected attribute type.
   * @param name
   *          The expected user provided attribute name.
   * @param options
   *          The expected attribute options.
   * @param values
   *          The expected attribute values.
   * @throws Exception
   *           If an unexpected error occurs.
   */
  @Test(dataProvider = "createAttributes", dependsOnMethods = "testAttributeNotNull")
  public void testAttributeIterator(int testCase, Attribute a,
      AttributeType type, String name, String[] options, String[] values)
      throws Exception
  {
    // Check iterator().
    Assert.assertNotNull(a.iterator());

    if (values.length == 0)
    {
      Assert.assertFalse(a.iterator().hasNext());

      try
      {
        a.iterator().next();
        Assert
            .fail("iterator() contains at least one value for empty attribute");
      }
      catch (NoSuchElementException e)
      {
        // Expected.
      }
    }
    else
    {
      // Values must be returned in the correct order.
      Iterator<AttributeValue> i = a.iterator();
      for (String value : values)
      {
        Assert.assertTrue(i.hasNext());

        AttributeValue v = i.next();
        Assert.assertEquals(v, new AttributeValue(type, value));

        try
        {
          i.remove();
          Assert.fail("value iterator() supports remove");
        }
        catch (UnsupportedOperationException e)
        {
          // Expected.
        }
      }

      // There should not be any more values.
      Assert.assertFalse(i.hasNext());

      try
      {
        i.next();
        Assert.fail("iterator() contains too many values");
      }
      catch (NoSuchElementException e)
      {
        // Expected.
      }
    }
  }



  /**
   * Tests that the built attribute is non-null.
   *
   * @param testCase
   *          Test case index (useful for debugging).
   * @param a
   *          The attribute.
   * @param type
   *          The expected attribute type.
   * @param name
   *          The expected user provided attribute name.
   * @param options
   *          The expected attribute options.
   * @param values
   *          The expected attribute values.
   * @throws Exception
   *           If an unexpected error occurs.
   */
  @Test(dataProvider = "createAttributes")
  public void testAttributeNotNull(int testCase, Attribute a,
      AttributeType type, String name, String[] options, String[] values)
      throws Exception
  {
    // Sanity test.
    Assert.assertNotNull(a);
  }



  /**
   * Tests that the generated attribute is optimized correctly for
   * storage of attribute options. This test is very implementation
   * dependent, but because Attributes are so performance sensitive it
   * is worth doing.
   *
   * @param testCase
   *          Test case index (useful for debugging).
   * @param a
   *          The attribute.
   * @param type
   *          The expected attribute type.
   * @param name
   *          The expected user provided attribute name.
   * @param options
   *          The expected attribute options.
   * @param values
   *          The expected attribute values.
   * @throws Exception
   *           If an unexpected error occurs.
   */
  @Test(dataProvider = "createAttributes", dependsOnMethods = "testAttributeGetOptions")
  public void testAttributeOptionOptimization(int testCase, Attribute a,
      AttributeType type, String name, String[] options, String[] values)
      throws Exception
  {
    switch (options.length)
    {
    case 0:
      // Attribute must be optimized for zero options.
      Assert.assertEquals(a.getClass().getName(),
          "org.opends.server.types.AttributeBuilder$RealAttributeNoOptions");
      break;
    case 1:
      // Attribute must be optimized for single option.
      Assert.assertEquals(a.getClass().getName(),
          "org.opends.server.types.AttributeBuilder$RealAttributeSingleOption");
      break;
    default:
      // Attribute must be optimized for many options.
      Assert.assertEquals(a.getClass().getName(),
          "org.opends.server.types.AttributeBuilder$RealAttributeManyOptions");
      break;
    }
  }



  /**
   * Tests {@link Attribute#optionsEqual(Set)}.
   *
   * @param testCase
   *          Test case index (useful for debugging).
   * @param a
   *          The attribute.
   * @param type
   *          The expected attribute type.
   * @param name
   *          The expected user provided attribute name.
   * @param options
   *          The expected attribute options.
   * @param values
   *          The expected attribute values.
   * @throws Exception
   *           If an unexpected error occurs.
   */
  @Test(dataProvider = "createAttributes", dependsOnMethods = "testAttributeNotNull")
  public void testAttributeOptionsEquals(int testCase, Attribute a,
      AttributeType type, String name, String[] options, String[] values)
      throws Exception
  {
    // Check optionsEquals.
    Assert.assertTrue(a
        .optionsEqual(new HashSet<String>(Arrays.asList(options))));

    if (options.length > 1)
    {
      Assert.assertFalse(a.optionsEqual(Collections.singleton(options[0])));
    }

    Set<String> stmp = new HashSet<String>(Arrays.asList(options));
    stmp.add("xxxx");
    Assert.assertFalse(a.optionsEqual(stmp));

    stmp.clear();
    for (String option : options)
    {
      // Assumes internal normalization to lower-case.
      stmp.add(option.toUpperCase());
    }
    Assert.assertTrue(a.optionsEqual(stmp));
  }



  /**
   * Tests {@link Attribute#size()}.
   *
   * @param testCase
   *          Test case index (useful for debugging).
   * @param a
   *          The attribute.
   * @param type
   *          The expected attribute type.
   * @param name
   *          The expected user provided attribute name.
   * @param options
   *          The expected attribute options.
   * @param values
   *          The expected attribute values.
   * @throws Exception
   *           If an unexpected error occurs.
   */
  @Test(dataProvider = "createAttributes", dependsOnMethods = "testAttributeNotNull")
  public void testAttributeSize(int testCase, Attribute a, AttributeType type,
      String name, String[] options, String[] values) throws Exception
  {
    // Check size().
    Assert.assertEquals(a.size(), values.length);
  }



  /**
   * Tests that the generated attribute is optimized correctly for
   * storage of attribute values. This test is very implementation
   * dependent, but because Attributes are so performance sensitive it
   * is worth doing.
   *
   * @param testCase
   *          Test case index (useful for debugging).
   * @param a
   *          The attribute.
   * @param type
   *          The expected attribute type.
   * @param name
   *          The expected user provided attribute name.
   * @param options
   *          The expected attribute options.
   * @param values
   *          The expected attribute values.
   * @throws Exception
   *           If an unexpected error occurs.
   */
  @Test(dataProvider = "createAttributes", dependsOnMethods = "testAttributeIterator")
  public void testAttributeValueOptimization(int testCase, Attribute a,
      AttributeType type, String name, String[] options, String[] values)
      throws Exception
  {
    // Determine the value set implementation class.
    String iteratorName = a.iterator().getClass().getName();
    int i = iteratorName.lastIndexOf('$');
    String className = iteratorName.substring(0, i);

    switch (values.length)
    {
    case 0:
      // Attribute must be optimized for zero values.
      Assert.assertEquals(className, "java.util.Collections$EmptySet");
      break;
    case 1:
      // Attribute must be optimized for single value.
      Assert.assertEquals(className, "java.util.Collections$SingletonSet");
      break;
    default:
      // Attribute must be optimized for many values.
      Assert.assertEquals(className,
          "java.util.Collections$UnmodifiableCollection");
      break;
    }
  }



  // Creates a new attribute.
  private Attribute createAttribute(AttributeType type, String name,
      String[] options, String[] values)
  {
    AttributeBuilder builder = new AttributeBuilder(type, name);
    for (String option : options)
    {
      builder.setOption(option);
    }
    for (String value : values)
    {
      builder.add(value);
    }
    return builder.toAttribute();
  }
}
