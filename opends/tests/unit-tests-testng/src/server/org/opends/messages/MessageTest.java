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
import org.opends.server.TestCaseUtils;
import static org.testng.Assert.*;

import java.util.Locale;
import java.util.Properties;
import java.util.ResourceBundle;
import java.io.File;
import java.io.IOException;
import java.io.FileOutputStream;

/**
 * Message Tester.
 */
public class MessageTest extends MessagesTestCase {

  /** Locale for accessing a pseudo localized test messages file. */
  private static final Locale TEST_LOCALE = Locale.CHINA;

  /** Message to appear in pseudo localized test messages file. */
  private static final String TEST_MSG = "XXX";

  @BeforeClass
  public void setUp() throws IOException {
    createDummyLocalizedCoreMessagesFile();
  }

  @DataProvider(name = "rawData")
  public Object[][] rawData() {
    return new Object[][]{
            {"Hello %s", "Hello World", new Object[]{"World"}},
            {"Hel%nlo %s", "Hel\nlo World", new Object[]{"World"}},
            {"Hel%%lo %s", "Hel%lo World", new Object[]{"World"}},
            {"Hel%%lo", "Hel%lo", new Object[]{}},
            {"Hel%nlo", "Hel\nlo", new Object[]{}},
            {"Hel%Dlo", "Hel%Dlo", new Object[]{}},
            {"Hel%Dlo", "Hel%Dlo", new Object[]{ "abc"}},

            // %d and String arg mismatch
            {"Hel%dlo", "Hel%dlo", new Object[]{ "abc"}},
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
    assertTrue(message.toString().equals(result));
    assertTrue(message.toString(TEST_LOCALE).equals(result));
  }

  @Test(dataProvider = "rawData1")
  public void testRaw1(String fmt, Category c, Severity s,
                       String result, Object... args) {
    Message message = Message.raw(c, s, fmt, args);
    assertTrue(message.toString().equals(result));
    assertTrue(message.toString(TEST_LOCALE).equals(result));
  }

  @DataProvider(name = "messages")
  public Object[][] getMessages() {
    return new Object[][]
    {
      { Message.raw("Hi Ho") },
      { Message.raw("Hi Ho %n") },
      { Message.raw("Hi Ho %%") },
      { Message.raw("Hi Ho %s", new Object[]{null}) },
      { Message.raw("Hi Ho", "Hum") },
      { Message.raw("Hi Ho %s", "Hum") },
      { Message.raw("Hi Ho %d", "Hum") },
      { Message.raw("Hi Ho %c", "Hum") },
      { Message.raw("Hi Ho %d", "Hum") },
      { CoreMessages.ERR_ADD_CANNOT_ADD_ROOT_DSE.get() },
      { CoreMessages.ERR_ABANDON_OP_NO_SUCH_OPERATION.get(1) },
      { CoreMessages.ERR_ABANDON_OP_NO_SUCH_OPERATION.get(null) },
      { CoreMessages.ERR_ADD_CANNOT_ADD_ROOT_DSE.get() }
    };
  }

  @DataProvider(name = "localizableMessages")
  public Object[][] getLocalizableMessages() {
    return new Object[][]
    {
      { CoreMessages.ERR_ADD_CANNOT_ADD_ROOT_DSE.get() },
      { CoreMessages.ERR_ABANDON_OP_NO_SUCH_OPERATION.get(1) },
      { CoreMessages.ERR_ABANDON_OP_NO_SUCH_OPERATION.get(null) },
      { CoreMessages.ERR_ADD_CANNOT_ADD_ROOT_DSE.get() }
    };
  }

  @Test(dataProvider = "messages")
  public void testToString(Message msg) {
    assertNotNull(msg.toString());
  }

  @Test(dataProvider = "localizableMessages")
  public void testToString1(Message msg) throws IOException {
    assertEquals(msg.toString(TEST_LOCALE), TEST_MSG);
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

  private void createDummyLocalizedCoreMessagesFile() throws IOException {
    Properties corePseudoI18nMsgs = new Properties();
    ResourceBundle coreDefaultMsgs = ResourceBundle.getBundle("messages/core");
    for (Object key : coreDefaultMsgs.keySet()) {
      corePseudoI18nMsgs.put(key, TEST_MSG);
    }
    File buildRoot = new File(System.getProperty(TestCaseUtils.PROPERTY_BUILD_ROOT));
    File corePseudoI18nMsgsFile = new File(buildRoot,
            "build" + File.separator + "unit-tests" +
                    File.separator + "classes" +
                    File.separator + "messages" +
                    File.separator + "core_" + TEST_LOCALE.getLanguage() +
                    ".properties");
    if (!corePseudoI18nMsgsFile.getParentFile().exists()) {
      corePseudoI18nMsgsFile.getParentFile().mkdirs();
    }
    corePseudoI18nMsgs.store(new FileOutputStream(corePseudoI18nMsgsFile), "");
  }

}
