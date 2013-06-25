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
package org.opends.server.extensions;



import java.util.ArrayList;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanException;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.messages.Message;
import org.opends.server.api.AlertHandler;
import org.opends.server.core.DirectoryServer;

import static org.testng.Assert.*;



/**
 * A set of test cases for the JMX Alert Handler.
 */
public class JMXAlertHandlerTestCase
       extends ExtensionsTestCase
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
   * Retrieves the set of JMX alert handlers registered with the Directory
   * Server.  There should just be one.
   *
   * @return  The set of JMX alert handlers registered with the Directory
   *          Server.
   */
  @DataProvider(name = "jmxAlertHandlers")
  public Object[][] getJMXAlertHandlers()
  {
    ArrayList<AlertHandler> handlers = new ArrayList<AlertHandler>();
    for (AlertHandler handler : DirectoryServer.getAlertHandlers())
    {
      if (handler instanceof JMXAlertHandler)
      {
        handlers.add(handler);
      }
    }

    Object[][] handlerArray = new Object[handlers.size()][1];
    for (int i=0; i < handlerArray.length; i++)
    {
      handlerArray[i] = new Object[] { handlers.get(i) };
    }

    return handlerArray;
  }



  /**
   * Tests the <CODE>getObjectName</CODE> method.
   *
   * @param  handler  The JMX alert handler to use for the test.
   */
  @Test(dataProvider = "jmxAlertHandlers")
  public void testGetObjectName(JMXAlertHandler handler)
  {
    assertNotNull(handler.getObjectName());
  }



  /**
   * Tests the <CODE>sendAlertNotification</CODE> method.
   *
   * @param  handler  The JMX alert handler to use for the test.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "jmxAlertHandlers")
  public void testSendAlertNotification(JMXAlertHandler handler)
         throws Exception
  {
    TestAlertGenerator generator = new TestAlertGenerator();

    handler.sendAlertNotification(generator, generator.getAlertType(),
            Message.raw("This is a test alert message."));
  }



  /**
   * Tests the <CODE>getNotificationInfo</CODE> method.
   *
   * @param  handler  The JMX alert handler to use for the test.
   */
  @Test(dataProvider = "jmxAlertHandlers")
  public void testGetNotificationInfo(JMXAlertHandler handler)
  {
    assertNotNull(handler.getNotificationInfo());
  }



  /**
   * Tests the <CODE>getAttribute</CODE> method.
   *
   * @param  handler  The JMX alert handler to use for the test.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "jmxAlertHandlers",
        expectedExceptions = { AttributeNotFoundException.class })
  public void testGetAttribute(JMXAlertHandler handler)
         throws AttributeNotFoundException
  {
    assertNull(handler.getAttribute("foo"));
  }



  /**
   * Tests the <CODE>getAttributes</CODE> method.
   *
   * @param  handler  The JMX alert handler to use for the test.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "jmxAlertHandlers")
  public void testGetAttributes(JMXAlertHandler handler)
  {
    assertEquals(handler.getAttributes(new String[] { "foo" }),
                 new AttributeList());
  }



  /**
   * Tests the <CODE>setAttribute</CODE> method.
   *
   * @param  handler  The JMX alert handler to use for the test.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "jmxAlertHandlers",
        expectedExceptions = { AttributeNotFoundException.class,
                               InvalidAttributeValueException.class })
  public void testSetAttribute(JMXAlertHandler handler)
         throws AttributeNotFoundException, InvalidAttributeValueException
  {

    handler.setAttribute(new Attribute("foo", "bar"));
  }



  /**
   * Tests the <CODE>setAttributes</CODE> method.
   *
   * @param  handler  The JMX alert handler to use for the test.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "jmxAlertHandlers")
  public void testSetAttributes(JMXAlertHandler handler)
  {
    assertEquals(handler.setAttributes(new AttributeList()),
                 new AttributeList());
  }



  /**
   * Tests the <CODE>invoke</CODE> method.
   *
   * @param  handler  The JMX alert handler to use for the test.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "jmxAlertHandlers",
        expectedExceptions = { MBeanException.class })
  public void testInvoke(JMXAlertHandler handler)
         throws MBeanException
  {
    handler.invoke("foo", new Object[] { "bar", "baz" },
                   new String[] { "java.lang.String", "java.lang.String" });
  }



  /**
   * Tests the <CODE>getMBeanInfo</CODE> method.
   *
   * @param  handler  The JMX alert handler to use for the test.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "jmxAlertHandlers")
  public void testGetMBeanInfo(JMXAlertHandler handler)
  {
    assertNotNull(handler.getMBeanInfo());
  }
}

