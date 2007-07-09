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

package org.opends.server.messages;

import org.testng.annotations.Test;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import org.opends.server.TestCaseUtils;

import java.util.LinkedList;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * This class provides a mechanism for determining whether messages
 * in a given category are defined in a single class file.
 */
public class CategoriesSpanFilesTestCase
     extends MessagesTestCase
{
  /**
   * Look in the build filesystem for files in the org.opends.server.messages
   * package.  Dynamically load all of those classes and use reflection to look
   * at all fields in those classes.  Make sure that messages in a given
   * category are defined in a single class file.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testCategories()
         throws Exception
  {
    // Construct a list of classes in the messages package.
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

    // Construct a map from category mask value to user-friendly string.
    HashMap<Integer,String> categoryMap = new HashMap<Integer, String>(100);
    Class categoryDefnClass =
         Class.forName("org.opends.server.messages.MessageHandler");
    for (Field field : categoryDefnClass.getDeclaredFields())
    {
      if (field.getType().getName().equals("int"))
      {
        if (Modifier.isStatic(field.getModifiers()) &&
            Modifier.isFinal(field.getModifiers()) &&
            field.getName().startsWith("CATEGORY_MASK_"))
        {
          int    category = field.getInt(null);
          categoryMap.put(category, field.getName());
        }
      }
    }

    // Construct a map from category mask value to the list of classes
    // containing messages of that category.
    HashMap<Integer,ArrayList<String>> classesByCategory =
         new HashMap<Integer, ArrayList<String>>(100);
    for (String className : classNames)
    {
      Class c = Class.forName(className);
      HashSet<Integer> categories = new HashSet<Integer>(1);
      for (Field field : c.getDeclaredFields())
      {
        if (field.getType().getName().equals("int"))
        {
          if (Modifier.isStatic(field.getModifiers()) &&
              Modifier.isFinal(field.getModifiers()) &&
              field.getName().startsWith("MSGID_"))
          {
            int    fieldValue = field.getInt(null);
            int    category = fieldValue & 0xFFF00000;
            categories.add(category);
          }
        }
      }
      for (Integer category : categories)
      {
        ArrayList<String> classes = classesByCategory.get(category);
        if (classes == null)
        {
          classes = new ArrayList<String>(1);
          classes.add(className);
          classesByCategory.put(category, classes);
        }
        else
        {
          classes.add(className);
        }
      }
    }

    // Construct an error message for any categories having multiple classes.
    StringBuilder buffer = new StringBuilder();
    for (Map.Entry<Integer,ArrayList<String>> entry :
         classesByCategory.entrySet())
    {
      ArrayList<String> classes = entry.getValue();
      if (classes.size() > 1)
      {
        buffer.append("Messages of category ");
        buffer.append(categoryMap.get(entry.getKey()));
        buffer.append(" are defined in multiple classes: ");
        Iterator<String> iterator = classes.iterator();
        buffer.append(iterator.next());

        while (iterator.hasNext())
        {
          buffer.append(", ");
          buffer.append(iterator.next());
        }

        buffer.append(". ");
      }
    }

    assertTrue(buffer.length() == 0, buffer.toString());
  }
}
