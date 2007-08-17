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
 
package org.opends.messages;

import static org.testng.Assert.*;
import org.testng.annotations.*;

import java.util.Set;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.EnumSet;
import java.util.Map;
import java.util.HashMap;
import java.lang.reflect.Field;

/**
 * MessageDescriptorRegistry Tester.
 *
 */
public class MessageDescriptorRegistryTest
{

  @DataProvider(name = "message descriptors")
  public Object[][] getMessageDescriptors() {
    return new Object[][] {
            { CoreMessages.ERR_ABANDON_OP_NO_SUCH_OPERATION }
    };
  }

  @Test(dataProvider = "message descriptors")
  public void testGetMessageDescriptor(MessageDescriptor md) {
    MessageDescriptor md2 = MessageDescriptorRegistry.getMessageDescriptor(md.getId());
    assertEquals(md, md2);
  }

  @DataProvider(name = "message classes")
  public Object[][] getMessageClasses() {
    Set<Class> mdClasses = MessageDescriptorRegistry.getRegisteredClasses();
    List<Class> classesToTest = new ArrayList<Class>(mdClasses);

    // These newer message files don't comply
    classesToTest.remove(AdminToolMessages.class);
    classesToTest.remove(QuickSetupMessages.class);

    Object[][] ret = new Object[classesToTest.size()][1];
    for (int i = 0; i < ret.length; i++) {
      ret[i] = new Object[] { classesToTest.get(i) };
    }
    return ret;
  }

  /**
   * Tests that messages don't end with a period (.) excluding those that end
   * with an ellipsis (...)
   * 
   * @param  messagesClass containing definitions of MessageDescriptor objects
   * @throws IllegalAccessException if there is a problem accessing the
   *         class through reflection
   */
  @Test(dataProvider = "message classes")
  public void testFormatStringsDontEndWithPeriod(Class messagesClass)
          throws IllegalAccessException
  {
    Field[] fa = messagesClass.getFields();
    if (fa != null) {
      for (Field f : fa) {
        Class<?> c = f.getType();
        if (MessageDescriptor.class.isAssignableFrom(c)) {
          MessageDescriptor md = (MessageDescriptor)f.get(null);
          String fmtString = md.getFormatString(Locale.getDefault());
          boolean bad = fmtString.endsWith(".") && !fmtString.endsWith("...");
          assertFalse(bad,
                  "Format string for message descriptor " + f.getName() +
                  " obtained through key " + md.getKey() + 
                  " defined in class " + messagesClass.getName() +
                  " \'" + md.getFormatString(Locale.getDefault()) +
                  "\' ends with a '.'");
        }
      }
    }
  }

  /**
   * Tests that messages for each category are restricted to a single
   * messages file and that each file only contains messages from a
   * single category.
   */
  @Test
  public void testCategoriesDontSpanFiles() {
    Map<Category,Class> categoriesToClass = new HashMap<Category,Class>();
    Set categories = EnumSet.allOf(Category.class);
    Set<Class> msgClasses = MessageDescriptorRegistry.getRegisteredClasses();
    for (Class msgClass : msgClasses) {
      List<MessageDescriptor> mds =
              MessageDescriptorRegistry.getMessageDescriptorsForClass(msgClass);
      Category currentCategory = null;
      for (MessageDescriptor md : mds) {
        if (currentCategory == null) {
          currentCategory = md.getCategory();
          if (categories.contains(currentCategory)) {
            categories.remove(currentCategory);
            categoriesToClass.put(currentCategory, msgClass);
          } else {
            assertTrue(false,
                    "Message file " + msgClass + " defines descriptors " +
                    "for category " + currentCategory + " but message file " +
                    categoriesToClass.get(currentCategory) + " defines descriptors " +
                    "of " + currentCategory + ".  Descriptors for a particular " +
                    "category can only be defined in a single messages file.");
          }
        } else {
          boolean categoriesMatch = currentCategory.equals(md.getCategory());
          assertTrue(categoriesMatch,
                  "Message file " + msgClass + " contains descriptors from at least " +
                          "two different categories: descriptor of key " +
                          md.getFormatString() + " is of category " + md.getCategory() +
                          " but expected category was " + currentCategory);

        }
      }
    }
  }
}
