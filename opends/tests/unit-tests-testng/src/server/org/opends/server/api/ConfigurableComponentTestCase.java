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
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.api;



import java.util.ArrayList;
import java.util.List;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.config.ConfigAttribute;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.JMXMBean;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DN;

import static org.testng.Assert.*;



/**
 * A set of generic test cases for configurable components.
 */
public class ConfigurableComponentTestCase
       extends APITestCase
{
  /**
   * Ensures that the Directory Server is running.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass()
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();
  }



  /**
   * Retrieves the set of configurable components registered with the server.
   *
   * @return  The set of configurable components registered with the server.
   */
  @DataProvider(name = "configurableComponents")
  public Object[][] getConfigurableComponents()
  {
    ArrayList<ConfigurableComponent> components =
         new ArrayList<ConfigurableComponent>();
    for (JMXMBean b : DirectoryServer.getJMXMBeans().values())
    {
      for (ConfigurableComponent c : b.getConfigurableComponents())
      {
        components.add(c);
      }
    }

    Object[][] componentArray = new Object[components.size()][1];
    for (int i=0; i < componentArray.length; i++)
    {
      componentArray[i] = new Object[] { components.get(i) };
    }

    return componentArray;
  }



  /**
   * Tests the <CODE>getConfigurableComponentEntryDN</CODE> method.
   *
   * @param  c  The configurable component to use in the test.
   */
  @Test(dataProvider = "configurableComponents")
  public void testGetConfigurableComponentEntryDN(ConfigurableComponent c)
  {
    assertNotNull(c.getConfigurableComponentEntryDN());
  }



  /**
   * Tests the <CODE>getConfigurationAttributes</CODE> method.
   *
   * @param  c  The configurable component to use in the test.
   */
  @Test(dataProvider = "configurableComponents")
  public void testGetConfigurationAttributes(ConfigurableComponent c)
  {
    List<ConfigAttribute> attrs = c.getConfigurationAttributes();
    assertNotNull(attrs);
  }



  /**
   * Tests the <CODE>hasAcceptableConfiguration</CODE> method with the
   * associated configuration entry.
   *
   * @param  c  The configurable component to use in the test.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "configurableComponents")
  public void testHasAcceptableConfiguration(ConfigurableComponent c)
         throws Exception
  {
    DN          configEntryDN = c.getConfigurableComponentEntryDN();
    ConfigEntry configEntry   = DirectoryServer.getConfigEntry(configEntryDN);

    if (configEntry != null)
    {
      ArrayList<String> unacceptableReasons = new ArrayList<String>();
      assertTrue(c.hasAcceptableConfiguration(configEntry,
                                              unacceptableReasons));
    }
  }
}

