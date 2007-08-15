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

import org.testng.annotations.*;
import org.opends.server.DirectoryServerTestCase;

import java.util.Locale;

/**
 * Message Tester.
 */
public class MessageTest extends DirectoryServerTestCase {

  //@BeforeClass
  public void setUp() {

  }

  @DataProvider(name = "rawData")
  public Object[][] rawData() {
    return new Object[][]{
            {"Hello %s", "Hello World", new Object[]{"World"}},
            {"Hel%nlo %s", "Hel\nlo World", new Object[]{"World"}},
         {"Hel%%lo %s", "Hel%lo World", new Object[]{"World"}},            
            {"Hel%%lo", "Hel%lo", new Object[]{}},
            {"Hel%nlo", "Hel\nlo", new Object[]{}}
    };
  }

  @DataProvider(name = "rawData1")
  public Object[][] rawData1() {
    return new Object[][]{
            {"Hello %s", Category.CORE, Severity.INFORMATION,
                    "Hello World", new Object[]{"World"}}
    };
  }

  @Test(dataProvider = "rawData")
  public void testRaw(String fmt, String result, Object... args) {
    Message message = Message.raw(fmt, args);
    assert (message.toString().equals(result));
    assert (message.toString(Locale.CHINESE).equals(result));
  }

  @Test(dataProvider = "rawData1")
  public void testRaw1(String fmt, Category c, Severity s,
                       String result, Object... args) {
    Message message = Message.raw(c, s, fmt, args);
    assert (message.toString().equals(result));
    assert (message.toString(Locale.CHINESE).equals(result));
  }

  //@Test
  public void testToString() {
    //TODO: Test goes here...
    assert false : "testToString not implemented.";
  }

  //@Test
  public void testToString1() {
    //TODO: Test goes here...
    assert false : "testToString1 not implemented.";
  }

  @Test(dataProvider = "rawData1")
  public void testGetDescriptor(String fmt, Category c, Severity s,
                                String result, Object... args) {
    Message message = Message.raw(c, s, fmt, args);
    MessageDescriptor desc = message.getDescriptor();
    assert(desc.getCategory().equals(c));
    assert(desc.getSeverity().equals(s));

    Message message2 = Message.raw(fmt, args);
    MessageDescriptor desc2 = message2.getDescriptor();
    assert(desc2.getCategory().equals(Category.USER_DEFINED));
    assert(desc2.getSeverity().equals(Severity.INFORMATION));
  }

}
