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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
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
import org.opends.server.types.Entry;

import static org.testng.Assert.*;



/**
 * A set of generic test cases for config change listeners.
 */
public class ConfigChangeListenerTestCase
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
   * Retrieves the set of config change listeners registered with the server.
   *
   * @return  The set of config change listeners registered with the server.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "configChangeListeners")
  public Object[][] getConfigChangeListeners()
         throws Exception
  {
    ArrayList<DN> dns = new ArrayList<DN>();
    ArrayList<ConfigChangeListener> listeners =
         new ArrayList<ConfigChangeListener>();
    getChangeListeners(DirectoryServer.getConfigHandler().getConfigRootEntry(),
                       dns, listeners);


    Object[][] componentArray = new Object[listeners.size()][1];
    for (int i=0; i < componentArray.length; i++)
    {
      componentArray[i] = new Object[] { dns.get(i), listeners.get(i) };
    }

    return componentArray;
  }



  /**
   * Retrieves the config change listeners from the provided configuration
   * entry, as well as recursively from all of the its subordinate entries.
   *
   * @param  configEntry  The configuration entry from which to retrieve the
   *                      change listeners.
   * @param  dns          The list into which to add the DNs of the
   *                      configuration entries with the change listeners.
   * @param  listeners    The list into which to add all identified change
   *                      listeners.
   */
  private void getChangeListeners(ConfigEntry configEntry,
                                  ArrayList<DN> dns,
                                  ArrayList<ConfigChangeListener> listeners)
  {
    for (ConfigChangeListener l : configEntry.getChangeListeners())
    {
      dns.add(configEntry.getDN());
      listeners.add(l);
    }

    if (configEntry.hasChildren())
    {
      for (ConfigEntry e : configEntry.getChildren().values())
      {
        getChangeListeners(e, dns, listeners);
      }
    }
  }



  /**
   * Tests the <CODE>configChangeIsAccpetable</CODE> method with the current
   * configuration.
   *
   * @param  dn  The DN of the configuration entry for the provided listener.
   * @param  l   The listener to be tested.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "configChangeListeners")
  public void testConfigChangeIsAcceptable(DN dn, ConfigChangeListener l)
         throws Exception
  {
    ConfigEntry e = DirectoryServer.getConfigEntry(dn);
    assertNotNull(e);

    assertTrue(l.configChangeIsAcceptable(e, new StringBuilder()));
  }
}

