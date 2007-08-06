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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.api;



import java.util.LinkedHashMap;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.api.AlertGenerator;
import org.opends.server.core.DirectoryServer;
import org.opends.server.extensions.DummyAlertHandler;
import org.opends.server.tools.dsconfig.DSConfig;
import org.opends.server.types.DN;

import static org.testng.Assert.*;



/**
 * A set of generic alert handler test cases.
 */
public class AlertHandlerTestCase
       extends APITestCase
       implements AlertGenerator
{
  /**
   * The alert ID to use for these tests.
   */
  public static final int ALERT_ID = 12345;



  /**
   * The alert type to use for these tests.
   */
  public static final String ALERT_TYPE = "org.opends.test.TestAlert";



  /**
   * The alert description to use for these tests.
   */
  public static final String ALERT_DESCRIPTION =
       "This is the alert description";



  /**
   * The alert message to use for these tests.
   */
  public static final String ALERT_MESSAGE = "This is the alert message";



  /**
   * Ensures that the Directory Server is running and registers with it as an
   * alert generator.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass()
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();
    DirectoryServer.registerAlertGenerator(this);
  }



  /**
   * Tests the ability of the alert handler to send an administrative alert
   * with no special configuration.
   */
  @Test()
  public void testAlertHandler()
  {
    int count = DummyAlertHandler.getAlertCount();

    DirectoryServer.sendAlertNotification(this, ALERT_TYPE, ALERT_ID,
                                          ALERT_MESSAGE);
    assertEquals(DummyAlertHandler.getAlertCount(), (count+1));
  }



  /**
   * Tests alert handler functionality when a given alert is explicitly enabled.
   */
  @Test()
  public void testEnabledAlert()
  {
    TestCaseUtils.dsconfig(
      "set-alert-handler-prop",
      "--handler-name", "Dummy Alert Handler",
      "--add", "enabled-alert-type:" + ALERT_TYPE);

    try
    {
      int count = DummyAlertHandler.getAlertCount();

      DirectoryServer.sendAlertNotification(this, ALERT_TYPE, ALERT_ID,
                                            ALERT_MESSAGE);
      assertEquals(DummyAlertHandler.getAlertCount(), (count+1));
    }
    finally
    {
      TestCaseUtils.dsconfig(
        "set-alert-handler-prop",
        "--handler-name", "Dummy Alert Handler",
        "--remove", "enabled-alert-type:" + ALERT_TYPE);
    }
  }



  /**
   * Tests alert handler functionality when a given alert is not explicitly
   * enabled but others are.
   */
  @Test()
  public void testNotEnabledAlert()
  {
    TestCaseUtils.dsconfig(
      "set-alert-handler-prop",
      "--handler-name", "Dummy Alert Handler",
      "--add", "enabled-alert-type:" + ALERT_TYPE + ".bogus");

    try
    {
      int count = DummyAlertHandler.getAlertCount();

      DirectoryServer.sendAlertNotification(this, ALERT_TYPE, ALERT_ID,
                                            ALERT_MESSAGE);
      assertEquals(DummyAlertHandler.getAlertCount(), count);
    }
    finally
    {
      TestCaseUtils.dsconfig(
        "set-alert-handler-prop",
        "--handler-name", "Dummy Alert Handler",
        "--remove", "enabled-alert-type:" + ALERT_TYPE + ".bogus");
    }
  }



  /**
   * Tests alert handler functionality when a given alert is explicitly
   * disabled.
   */
  @Test()
  public void testDisabledAlert()
  {
    TestCaseUtils.dsconfig(
      "set-alert-handler-prop",
      "--handler-name", "Dummy Alert Handler",
      "--add", "disabled-alert-type:" + ALERT_TYPE);

    try
    {
      int count = DummyAlertHandler.getAlertCount();

      DirectoryServer.sendAlertNotification(this, ALERT_TYPE, ALERT_ID,
                                            ALERT_MESSAGE);
      assertEquals(DummyAlertHandler.getAlertCount(), count);
    }
    finally
    {
      TestCaseUtils.dsconfig(
        "set-alert-handler-prop",
        "--handler-name", "Dummy Alert Handler",
        "--remove", "disabled-alert-type:" + ALERT_TYPE);
    }
  }



  /**
   * Tests alert handler functionality when a given alert is not explicitly
   * disabled but others are.
   */
  @Test()
  public void testNotDisabledAlert()
  {
    TestCaseUtils.dsconfig(
      "set-alert-handler-prop",
      "--handler-name", "Dummy Alert Handler",
      "--add", "disabled-alert-type:" + ALERT_TYPE + ".bogus");

    try
    {
      int count = DummyAlertHandler.getAlertCount();

      DirectoryServer.sendAlertNotification(this, ALERT_TYPE, ALERT_ID,
                                            ALERT_MESSAGE);
      assertEquals(DummyAlertHandler.getAlertCount(), (count+1));
    }
    finally
    {
      TestCaseUtils.dsconfig(
        "set-alert-handler-prop",
        "--handler-name", "Dummy Alert Handler",
        "--remove", "disabled-alert-type:" + ALERT_TYPE + ".bogus");
    }
  }



  /**
   * Tests alert handler functionality when a given alert is both enabled and
   * disabled.
   */
  @Test()
  public void testEnabledAndDisabledAlert()
  {
    TestCaseUtils.dsconfig(
      "set-alert-handler-prop",
      "--handler-name", "Dummy Alert Handler",
      "--add", "enabled-alert-type:" + ALERT_TYPE,
      "--add", "disabled-alert-type:" + ALERT_TYPE);

    try
    {
      int count = DummyAlertHandler.getAlertCount();

      DirectoryServer.sendAlertNotification(this, ALERT_TYPE, ALERT_ID,
                                            ALERT_MESSAGE);
      assertEquals(DummyAlertHandler.getAlertCount(), count);
    }
    finally
    {
      TestCaseUtils.dsconfig(
        "set-alert-handler-prop",
        "--handler-name", "Dummy Alert Handler",
        "--remove", "enabled-alert-type:" + ALERT_TYPE,
        "--remove", "disabled-alert-type:" + ALERT_TYPE);
    }
  }



  /**
   * Retrieves the DN of the configuration entry with which this alert
   * generator is associated.
   *
   * @return  The DN of the configuration entry with which this alert
   *          generator is associated.
   */
  public DN getComponentEntryDN()
  {
    try
    {
      return DN.decode("cn=Alert Handler Test Case,cn=config");
    }
    catch (Exception e)
    {
      return DN.nullDN();
    }
  }



  /**
   * Retrieves the fully-qualified name of the Java class for this
   * alert generator implementation.
   *
   * @return  The fully-qualified name of the Java class for this
   *          alert generator implementation.
   */
  public String getClassName()
  {
    return getClass().getName();
  }



  /**
   * Retrieves information about the set of alerts that this generator
   * may produce.  The map returned should be between the notification
   * type for a particular notification and the human-readable
   * description for that notification.  This alert generator must not
   * generate any alerts with types that are not contained in this
   * list.
   *
   * @return  Information about the set of alerts that this generator
   *          may produce.
   */
  public LinkedHashMap<String,String> getAlerts()
  {
    LinkedHashMap<String,String> alerts = new LinkedHashMap<String,String>();

    alerts.put(ALERT_TYPE, ALERT_DESCRIPTION);

    return alerts;
  }
}

