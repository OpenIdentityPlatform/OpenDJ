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

package org.opends.messages;

import static org.testng.Assert.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Locale;

/**
 * Message Tester.
 */
public class MessageBuilderTest extends MessagesTestCase {

  @BeforeClass
  public void setUp() throws IOException {
    createDummyLocalizedCoreMessagesFile();
  }


  @DataProvider(name = "toMessageData")
  public Object[][] toMessageData() {
    return new Object[][]{

            // All strings
            { new CharSequence[] {
                    "Once", " upon", " a", " time." },
                    "Once upon a time." },

            // All messages
            { new CharSequence[] {
                    Message.raw("Once"),
                    Message.raw(" upon"),
                    Message.raw(" a"),
                    Message.raw(" time.") },
                    "Once upon a time." },

            // Mix of strings and messages
            { new CharSequence[] {
                    Message.raw("Once"),
                    " upon",
                    Message.raw(" a"),
                    " time." },
                    "Once upon a time." },

    };
  }

  @Test (dataProvider = "toMessageData")
  public void testToMessage(CharSequence[] content, String result)
  {
    MessageBuilder mb = new MessageBuilder();
    for (CharSequence c : content) {
      mb.append(c);
    }
    Message m = mb.toMessage();
    assertTrue(result.equals(m.toString()));
  }


  @DataProvider(name = "toMessageData1")
  public Object[][] toMessageData1() {
    return new Object[][]{

            // default locale
            { new CharSequence[] {
                    CoreMessages.ERR_ADD_CANNOT_ADD_ROOT_DSE.get(),
                    CoreMessages.ERR_ABANDON_OP_NO_SUCH_OPERATION.get(1) },
                    Locale.getDefault(),
                    CoreMessages.ERR_ADD_CANNOT_ADD_ROOT_DSE.get().toString() +
                    CoreMessages.ERR_ABANDON_OP_NO_SUCH_OPERATION.get(1).toString() },

            { new CharSequence[] {
                    CoreMessages.ERR_ADD_CANNOT_ADD_ROOT_DSE.get(),
                    CoreMessages.ERR_ABANDON_OP_NO_SUCH_OPERATION.get(1) },
                    TEST_LOCALE,
                    CoreMessages.ERR_ADD_CANNOT_ADD_ROOT_DSE.get().toString(TEST_LOCALE) +
                    CoreMessages.ERR_ABANDON_OP_NO_SUCH_OPERATION.get(1).toString(TEST_LOCALE) }

    };
  }


  @Test (dataProvider = "toMessageData1")
  public void testToMessage1(CharSequence[] content, Locale locale, String result)
  {
    MessageBuilder mb = new MessageBuilder();
    for (CharSequence c : content) {
      mb.append(c);
    }
    Message m = mb.toMessage();
    assertTrue(result.equals(m.toString(locale)));
  }

}
