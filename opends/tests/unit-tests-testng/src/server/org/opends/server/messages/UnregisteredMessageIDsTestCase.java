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
package org.opends.server.messages;



import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;

import static org.testng.Assert.*;



/**
 * This class provides a mechanism for determining whether any of the message
 * IDs defined have not been registered with the server (and therefore may not
 * have a default message).
 */
public class UnregisteredMessageIDsTestCase
       extends MessagesTestCase
{
  /**
   * Make sure that the Directory Server is running.
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
   * Look in the build filesystem for files in the org.opends.server.messages
   * package.  Dynamically load all of those classes and use reflection to look
   * at all fields in those classes.  For each field, make sure that it has a
   * corresponding message registered with the server.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void TestUnregisteredMessageIDs()
         throws Exception
  {
    ConcurrentHashMap<Integer,String> messages = MessageHandler.getMessages();

    String s = File.separator;
    String buildRoot = System.getProperty(TestCaseUtils.PROPERTY_BUILD_ROOT);
    String messageClassesDir = buildRoot + s + "build" + s + "classes" + s +
                               "org" + s + "opends" + s + "server" + s +
                               "messages";
    File f = new File(messageClassesDir);
    LinkedList<String> classNames = new LinkedList<String>();
    for (String filename : f.list())
    {
      if (! filename.endsWith(".class"))
      {
        continue;
      }

      classNames.add("org.opends.server.messages." +
                     filename.substring(0, filename.length()-6));
    }

    assertFalse(classNames.isEmpty());

    LinkedList<String> unregisteredMessageIDs = new LinkedList<String>();
    for (String className : classNames)
    {
      Class c = Class.forName(className);
      for (Field field : c.getDeclaredFields())
      {
        if (field.getType().getName().equals("int"))
        {
          if (Modifier.isStatic(field.getModifiers()) &&
              Modifier.isFinal(field.getModifiers()) &&
              field.getName().startsWith("MSGID_"))
          {
            String fieldName  = className + "." + field.getName();
            int    fieldValue = field.getInt(null);

            if (! messages.containsKey(fieldValue))
            {
              unregisteredMessageIDs.add(fieldName);
            }
          }
        }
      }
    }

    if (! unregisteredMessageIDs.isEmpty())
    {
      StringBuilder buffer = new StringBuilder();

      Iterator<String> iterator = unregisteredMessageIDs.iterator();
      buffer.append("Unregistered message IDs detected:  ");
      buffer.append(iterator.next());

      while (iterator.hasNext())
      {
        buffer.append(", ");
        buffer.append(iterator.next());
      }

      assertTrue(unregisteredMessageIDs.isEmpty(), buffer.toString());
    }
  }
}

