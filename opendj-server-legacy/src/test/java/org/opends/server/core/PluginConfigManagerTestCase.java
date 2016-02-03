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
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.core;

import java.util.ArrayList;

import org.opends.server.TestCaseUtils;
import org.opends.server.api.plugin.DirectoryServerPlugin;
import org.opends.server.api.plugin.PluginType;
import org.forgerock.opendj.ldap.DN;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.opends.server.util.ServerConstants.*;
import static org.testng.Assert.*;

/** A set of test cases for the plugin config manager. */
public class PluginConfigManagerTestCase
       extends CoreTestCase
{
  /**
   * The name of the delay preoperation plugin, formatted in all lowercase.
   */
  private static final String PLUGIN_NAME_DELAY = "delay preoperation plugin";



  /**
   * The name of the disconnect client plugin, formatted in all lowercase.
   */
  private static final String PLUGIN_NAME_DISCONNECT =
       "disconnect client plugin";



  /**
   * The name of the invocation counter plugin, formatted in all lowercase.
   */
  private static final String PLUGIN_NAME_INVOCATION_COUNTER =
       "invocation counter plugin";



  /**
   * The name of the short-circuit plugin, formatted in all lowercase.
   */
  private static final String PLUGIN_NAME_SHORT_CIRCUIT =
       "short circuit plugin";



  /**
   * The name of the update preoperation plugin, formatted in all lowercase.
   */
  private static final String PLUGIN_NAME_UPDATE = "update preoperation plugin";



  /**
   * Ensure that the server is running.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass
  public void startServer()
         throws Exception
  {
    TestCaseUtils.restartServer();
  }



  /**
   * Retrieves a set of data that may be used for testing plugin ordering.  The
   * returned array will be two-dimensional, with the first element being a
   * plugin order string and the second being a string array of the names of the
   * plugins in the order they should appear when using the specified order.
   *
   * @return  A set of data that may be used for testing plugin ordering.
   */
  @DataProvider(name = "pluginOrders")
  public Object[][] getPluginOrders()
  {
    return new Object[][]
    {
      new Object[]
      {
        null,
        new String[]
        {
          PLUGIN_NAME_DELAY,
          PLUGIN_NAME_DISCONNECT,
          PLUGIN_NAME_INVOCATION_COUNTER,
          PLUGIN_NAME_SHORT_CIRCUIT,
          PLUGIN_NAME_UPDATE
        }
      },

      new Object[]
      {
        "",
        new String[]
        {
          PLUGIN_NAME_DELAY,
          PLUGIN_NAME_DISCONNECT,
          PLUGIN_NAME_INVOCATION_COUNTER,
          PLUGIN_NAME_SHORT_CIRCUIT,
          PLUGIN_NAME_UPDATE
        }
      },

      new Object[]
      {
        "*",
        new String[]
        {
          PLUGIN_NAME_DELAY,
          PLUGIN_NAME_DISCONNECT,
          PLUGIN_NAME_INVOCATION_COUNTER,
          PLUGIN_NAME_SHORT_CIRCUIT,
          PLUGIN_NAME_UPDATE
        }
      },

      new Object[]
      {
        "undefined",
        new String[]
        {
          PLUGIN_NAME_DELAY,
          PLUGIN_NAME_DISCONNECT,
          PLUGIN_NAME_INVOCATION_COUNTER,
          PLUGIN_NAME_SHORT_CIRCUIT,
          PLUGIN_NAME_UPDATE
        }
      },

      new Object[]
      {
        "undefined1, undefined2",
        new String[]
        {
          PLUGIN_NAME_DELAY,
          PLUGIN_NAME_DISCONNECT,
          PLUGIN_NAME_INVOCATION_COUNTER,
          PLUGIN_NAME_SHORT_CIRCUIT,
          PLUGIN_NAME_UPDATE
        }
      },

      new Object[]
      {
        "undefined1, *, undefined2",
        new String[]
        {
          PLUGIN_NAME_DELAY,
          PLUGIN_NAME_DISCONNECT,
          PLUGIN_NAME_INVOCATION_COUNTER,
          PLUGIN_NAME_SHORT_CIRCUIT,
          PLUGIN_NAME_UPDATE
        }
      },

      new Object[]
      {
        "undefined, *",
        new String[]
        {
          PLUGIN_NAME_DELAY,
          PLUGIN_NAME_DISCONNECT,
          PLUGIN_NAME_INVOCATION_COUNTER,
          PLUGIN_NAME_SHORT_CIRCUIT,
          PLUGIN_NAME_UPDATE
        }
      },

      new Object[]
      {
        "*, undefined",
        new String[]
        {
          PLUGIN_NAME_DELAY,
          PLUGIN_NAME_DISCONNECT,
          PLUGIN_NAME_INVOCATION_COUNTER,
          PLUGIN_NAME_SHORT_CIRCUIT,
          PLUGIN_NAME_UPDATE
        }
      },

      new Object[]
      {
        PLUGIN_NAME_DELAY,
        new String[]
        {
          PLUGIN_NAME_DELAY,
          PLUGIN_NAME_DISCONNECT,
          PLUGIN_NAME_INVOCATION_COUNTER,
          PLUGIN_NAME_SHORT_CIRCUIT,
          PLUGIN_NAME_UPDATE
        }
      },

      new Object[]
      {
        PLUGIN_NAME_DISCONNECT,
        new String[]
        {
          PLUGIN_NAME_DISCONNECT,
          PLUGIN_NAME_DELAY,
          PLUGIN_NAME_INVOCATION_COUNTER,
          PLUGIN_NAME_SHORT_CIRCUIT,
          PLUGIN_NAME_UPDATE
        }
      },

      new Object[]
      {
        PLUGIN_NAME_INVOCATION_COUNTER,
        new String[]
        {
          PLUGIN_NAME_INVOCATION_COUNTER,
          PLUGIN_NAME_DELAY,
          PLUGIN_NAME_DISCONNECT,
          PLUGIN_NAME_SHORT_CIRCUIT,
          PLUGIN_NAME_UPDATE
        }
      },

      new Object[]
      {
        PLUGIN_NAME_SHORT_CIRCUIT,
        new String[]
        {
          PLUGIN_NAME_SHORT_CIRCUIT,
          PLUGIN_NAME_DELAY,
          PLUGIN_NAME_DISCONNECT,
          PLUGIN_NAME_INVOCATION_COUNTER,
          PLUGIN_NAME_UPDATE
        }
      },

      new Object[]
      {
        PLUGIN_NAME_UPDATE,
        new String[]
        {
          PLUGIN_NAME_UPDATE,
          PLUGIN_NAME_DELAY,
          PLUGIN_NAME_DISCONNECT,
          PLUGIN_NAME_INVOCATION_COUNTER,
          PLUGIN_NAME_SHORT_CIRCUIT
        }
      },

      new Object[]
      {
        PLUGIN_NAME_DELAY + ", *",
        new String[]
        {
          PLUGIN_NAME_DELAY,
          PLUGIN_NAME_DISCONNECT,
          PLUGIN_NAME_INVOCATION_COUNTER,
          PLUGIN_NAME_SHORT_CIRCUIT,
          PLUGIN_NAME_UPDATE
        }
      },

      new Object[]
      {
        PLUGIN_NAME_DISCONNECT + ", *",
        new String[]
        {
          PLUGIN_NAME_DISCONNECT,
          PLUGIN_NAME_DELAY,
          PLUGIN_NAME_INVOCATION_COUNTER,
          PLUGIN_NAME_SHORT_CIRCUIT,
          PLUGIN_NAME_UPDATE
        }
      },

      new Object[]
      {
        PLUGIN_NAME_INVOCATION_COUNTER + ", *",
        new String[]
        {
          PLUGIN_NAME_INVOCATION_COUNTER,
          PLUGIN_NAME_DELAY,
          PLUGIN_NAME_DISCONNECT,
          PLUGIN_NAME_SHORT_CIRCUIT,
          PLUGIN_NAME_UPDATE
        }
      },

      new Object[]
      {
        PLUGIN_NAME_SHORT_CIRCUIT + ", *",
        new String[]
        {
          PLUGIN_NAME_SHORT_CIRCUIT,
          PLUGIN_NAME_DELAY,
          PLUGIN_NAME_DISCONNECT,
          PLUGIN_NAME_INVOCATION_COUNTER,
          PLUGIN_NAME_UPDATE
        }
      },

      new Object[]
      {
        PLUGIN_NAME_UPDATE + ", *",
        new String[]
        {
          PLUGIN_NAME_UPDATE,
          PLUGIN_NAME_DELAY,
          PLUGIN_NAME_DISCONNECT,
          PLUGIN_NAME_INVOCATION_COUNTER,
          PLUGIN_NAME_SHORT_CIRCUIT
        }
      },

      new Object[]
      {
        "*, " + PLUGIN_NAME_DELAY,
        new String[]
        {
          PLUGIN_NAME_DISCONNECT,
          PLUGIN_NAME_INVOCATION_COUNTER,
          PLUGIN_NAME_SHORT_CIRCUIT,
          PLUGIN_NAME_UPDATE,
          PLUGIN_NAME_DELAY
        }
      },

      new Object[]
      {
        "*, " + PLUGIN_NAME_DISCONNECT,
        new String[]
        {
          PLUGIN_NAME_DELAY,
          PLUGIN_NAME_INVOCATION_COUNTER,
          PLUGIN_NAME_SHORT_CIRCUIT,
          PLUGIN_NAME_UPDATE,
          PLUGIN_NAME_DISCONNECT
        }
      },

      new Object[]
      {
        "*, " + PLUGIN_NAME_INVOCATION_COUNTER,
        new String[]
        {
          PLUGIN_NAME_DELAY,
          PLUGIN_NAME_DISCONNECT,
          PLUGIN_NAME_SHORT_CIRCUIT,
          PLUGIN_NAME_UPDATE,
          PLUGIN_NAME_INVOCATION_COUNTER
        }
      },

      new Object[]
      {
        "*, " + PLUGIN_NAME_SHORT_CIRCUIT,
        new String[]
        {
          PLUGIN_NAME_DELAY,
          PLUGIN_NAME_DISCONNECT,
          PLUGIN_NAME_INVOCATION_COUNTER,
          PLUGIN_NAME_UPDATE,
          PLUGIN_NAME_SHORT_CIRCUIT
        }
      },

      new Object[]
      {
        "*, " + PLUGIN_NAME_UPDATE,
        new String[]
        {
          PLUGIN_NAME_DELAY,
          PLUGIN_NAME_DISCONNECT,
          PLUGIN_NAME_INVOCATION_COUNTER,
          PLUGIN_NAME_SHORT_CIRCUIT,
          PLUGIN_NAME_UPDATE
        }
      },

      new Object[]
      {
        PLUGIN_NAME_UPDATE + ", *, " + PLUGIN_NAME_DELAY,
        new String[]
        {
          PLUGIN_NAME_UPDATE,
          PLUGIN_NAME_DISCONNECT,
          PLUGIN_NAME_INVOCATION_COUNTER,
          PLUGIN_NAME_SHORT_CIRCUIT,
          PLUGIN_NAME_DELAY
        }
      },

      new Object[]
      {
        PLUGIN_NAME_DELAY + ", " + PLUGIN_NAME_DISCONNECT + ", " +
             PLUGIN_NAME_INVOCATION_COUNTER + ", " + PLUGIN_NAME_SHORT_CIRCUIT +
             ", " + PLUGIN_NAME_UPDATE,
        new String[]
        {
          PLUGIN_NAME_DELAY,
          PLUGIN_NAME_DISCONNECT,
          PLUGIN_NAME_INVOCATION_COUNTER,
          PLUGIN_NAME_SHORT_CIRCUIT,
          PLUGIN_NAME_UPDATE
        }
      },

      new Object[]
      {
        PLUGIN_NAME_DELAY + ", " + PLUGIN_NAME_DISCONNECT + ", " +
             PLUGIN_NAME_INVOCATION_COUNTER + ", " + PLUGIN_NAME_SHORT_CIRCUIT +
             ", " + PLUGIN_NAME_UPDATE + ", *",
        new String[]
        {
          PLUGIN_NAME_DELAY,
          PLUGIN_NAME_DISCONNECT,
          PLUGIN_NAME_INVOCATION_COUNTER,
          PLUGIN_NAME_SHORT_CIRCUIT,
          PLUGIN_NAME_UPDATE
        }
      },

      new Object[]
      {
        "*, " + PLUGIN_NAME_DELAY + ", " + PLUGIN_NAME_DISCONNECT + ", " +
             PLUGIN_NAME_INVOCATION_COUNTER + ", " + PLUGIN_NAME_SHORT_CIRCUIT +
             ", " + PLUGIN_NAME_UPDATE,
        new String[]
        {
          PLUGIN_NAME_DELAY,
          PLUGIN_NAME_DISCONNECT,
          PLUGIN_NAME_INVOCATION_COUNTER,
          PLUGIN_NAME_SHORT_CIRCUIT,
          PLUGIN_NAME_UPDATE
        }
      },

      new Object[]
      {
        PLUGIN_NAME_DELAY + ", " + PLUGIN_NAME_DISCONNECT + ", *, " +
             PLUGIN_NAME_INVOCATION_COUNTER + ", " + PLUGIN_NAME_SHORT_CIRCUIT +
             ", " + PLUGIN_NAME_UPDATE,
        new String[]
        {
          PLUGIN_NAME_DELAY,
          PLUGIN_NAME_DISCONNECT,
          PLUGIN_NAME_INVOCATION_COUNTER,
          PLUGIN_NAME_SHORT_CIRCUIT,
          PLUGIN_NAME_UPDATE
        }
      },

      new Object[]
      {
        PLUGIN_NAME_DELAY + ", " + PLUGIN_NAME_DISCONNECT + ", undefined, " +
             PLUGIN_NAME_INVOCATION_COUNTER + ", " + PLUGIN_NAME_SHORT_CIRCUIT +
             ", " + PLUGIN_NAME_UPDATE,
        new String[]
        {
          PLUGIN_NAME_DELAY,
          PLUGIN_NAME_DISCONNECT,
          PLUGIN_NAME_INVOCATION_COUNTER,
          PLUGIN_NAME_SHORT_CIRCUIT,
          PLUGIN_NAME_UPDATE
        }
      },

      new Object[]
      {
        PLUGIN_NAME_UPDATE + ", " + PLUGIN_NAME_SHORT_CIRCUIT + ", " +
             PLUGIN_NAME_INVOCATION_COUNTER + ", " + PLUGIN_NAME_DISCONNECT +
             ", " + PLUGIN_NAME_DELAY,
        new String[]
        {
          PLUGIN_NAME_UPDATE,
          PLUGIN_NAME_SHORT_CIRCUIT,
          PLUGIN_NAME_INVOCATION_COUNTER,
          PLUGIN_NAME_DISCONNECT,
          PLUGIN_NAME_DELAY
        }
      },

      new Object[]
      {
        PLUGIN_NAME_UPDATE + ", " + PLUGIN_NAME_SHORT_CIRCUIT + ", " +
             PLUGIN_NAME_INVOCATION_COUNTER + ", " + PLUGIN_NAME_DISCONNECT +
             ", " + PLUGIN_NAME_DELAY + ", *",
        new String[]
        {
          PLUGIN_NAME_UPDATE,
          PLUGIN_NAME_SHORT_CIRCUIT,
          PLUGIN_NAME_INVOCATION_COUNTER,
          PLUGIN_NAME_DISCONNECT,
          PLUGIN_NAME_DELAY
        }
      },

      new Object[]
      {
        "*, " + PLUGIN_NAME_UPDATE + ", " + PLUGIN_NAME_SHORT_CIRCUIT + ", " +
             PLUGIN_NAME_INVOCATION_COUNTER + ", " + PLUGIN_NAME_DISCONNECT +
             ", " + PLUGIN_NAME_DELAY,
        new String[]
        {
          PLUGIN_NAME_UPDATE,
          PLUGIN_NAME_SHORT_CIRCUIT,
          PLUGIN_NAME_INVOCATION_COUNTER,
          PLUGIN_NAME_DISCONNECT,
          PLUGIN_NAME_DELAY
        }
      },

      new Object[]
      {
        PLUGIN_NAME_UPDATE + ", " + PLUGIN_NAME_SHORT_CIRCUIT + ", *, " +
             PLUGIN_NAME_INVOCATION_COUNTER + ", " + PLUGIN_NAME_DISCONNECT +
             ", " + PLUGIN_NAME_DELAY,
        new String[]
        {
          PLUGIN_NAME_UPDATE,
          PLUGIN_NAME_SHORT_CIRCUIT,
          PLUGIN_NAME_INVOCATION_COUNTER,
          PLUGIN_NAME_DISCONNECT,
          PLUGIN_NAME_DELAY
        }
      },

      new Object[]
      {
        PLUGIN_NAME_UPDATE + ", " + PLUGIN_NAME_SHORT_CIRCUIT +
             ", undefined, " + PLUGIN_NAME_INVOCATION_COUNTER + ", " +
             PLUGIN_NAME_DISCONNECT + ", " + PLUGIN_NAME_DELAY,
        new String[]
        {
          PLUGIN_NAME_UPDATE,
          PLUGIN_NAME_SHORT_CIRCUIT,
          PLUGIN_NAME_INVOCATION_COUNTER,
          PLUGIN_NAME_DISCONNECT,
          PLUGIN_NAME_DELAY
        }
      },
    };
  }



  /**
   * Tests the ability of the server to order plugins correctly.
   *
   * @param  pluginOrderString  The string that defines the plugin order to be
   *                            used.
   * @param  expectedNameOrder  An array  of the plugin names in the order they
   *                            are expected to appear when sorted.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider="pluginOrders")
  public void testPluginOrder(String pluginOrderString,
                              String[] expectedNameOrder)
         throws Exception
  {
    String pluginBase = ",cn=Plugins,cn=config";

    ArrayList<DN> dnList = new ArrayList<>();
    dnList.add(DN.valueOf("cn=" + PLUGIN_NAME_DELAY + pluginBase));
    dnList.add(DN.valueOf("cn=" + PLUGIN_NAME_DISCONNECT + pluginBase));
    dnList.add(DN.valueOf("cn=" + PLUGIN_NAME_INVOCATION_COUNTER + pluginBase));
    dnList.add(DN.valueOf("cn=" + PLUGIN_NAME_SHORT_CIRCUIT + pluginBase));
    dnList.add(DN.valueOf("cn=" + PLUGIN_NAME_UPDATE + pluginBase));

    ArrayList<DirectoryServerPlugin> pluginList = new ArrayList<>(dnList.size());
    for (DN dn : dnList)
    {
      DirectoryServerPlugin p =
           DirectoryServer.getPluginConfigManager().getRegisteredPlugin(dn);
      assertNotNull(p, "The " + dn + " plugin is not registered with the server.");
      pluginList.add(p);
    }

    DirectoryServerPlugin[] pluginArray = new DirectoryServerPlugin[0];
    for (DirectoryServerPlugin p : pluginList)
    {
      pluginArray = PluginConfigManager.addPlugin(pluginArray, p,
                                                  PluginType.PRE_OPERATION_ADD,
                                                  pluginOrderString);
    }

    assertEquals(pluginArray.length, expectedNameOrder.length);


    boolean match = true;
    StringBuilder expectedOrder = new StringBuilder();
    StringBuilder actualOrder   = new StringBuilder();
    for (int i=0; i < pluginArray.length; i++)
    {
      if (i > 0)
      {
        expectedOrder.append(", ");
        actualOrder.append(", ");
      }

      expectedOrder.append(expectedNameOrder[i]);

      DN dn = pluginArray[i].getPluginEntryDN();
      String name = dn.rdn().getFirstAVA().getAttributeValue().toString().toLowerCase();
      actualOrder.append(name);

      if (! name.equals(expectedNameOrder[i]))
      {
        match = false;
      }
    }

    assertTrue(match,
               EOL + "Expected order:  " + expectedOrder + EOL +
               "Actual order:    " + actualOrder);
  }
}

