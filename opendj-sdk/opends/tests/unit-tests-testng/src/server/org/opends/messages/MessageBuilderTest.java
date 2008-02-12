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
 *      Copyright 2007-2008 Sun Microsystems, Inc.
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

            // Lots of messages
            { new CharSequence[] {
                    Message.raw("North"),
                    Message.raw(" America"),
                    Message.raw(" is"),
                    Message.raw(" divided"),
                    Message.raw(" into"),
                    Message.raw(" two"),
                    Message.raw(" vast"),
                    Message.raw(" regions,"),
                    Message.raw(" one"),
                    Message.raw(" inclining"),
                    Message.raw(" towards"),
                    Message.raw(" the"),
                    Message.raw(" Pole,"),
                    Message.raw(" the"),
                    Message.raw(" other"),
                    Message.raw(" towards"),
                    Message.raw(" the"),
                    Message.raw(" Equator--Valley"),
                    Message.raw(" of"),
                    Message.raw(" the"),
                    Message.raw(" Mississippi--Traces"),
                    Message.raw(" found"),
                    Message.raw(" there"),
                    Message.raw(" of"),
                    Message.raw(" the"),
                    Message.raw(" revolutions"),
                    Message.raw(" of"),
                    Message.raw(" the"),
                    Message.raw(" globe"),
                    Message.raw(" --Shore"),
                    Message.raw(" of"),
                    Message.raw(" the"),
                    Message.raw(" Atlantic"),
                    Message.raw(" Ocean,"),
                    Message.raw(" on"),
                    Message.raw(" which"),
                    Message.raw(" the"),
                    Message.raw(" English"),
                    Message.raw(" colonies"),
                    Message.raw(" were"),
                    Message.raw(" founded--Different"),
                    Message.raw(" aspects"),
                    Message.raw(" of"),
                    Message.raw(" North"),
                    Message.raw(" and"),
                    Message.raw(" of"),
                    Message.raw(" South"),
                    Message.raw(" America"),
                    Message.raw(" at"),
                    Message.raw(" the"),
                    Message.raw(" time"),
                    Message.raw(" of"),
                    Message.raw(" their"),
                    Message.raw(" discovery--Forests"),
                    Message.raw(" of"),
                    Message.raw(" North"),
                    Message.raw(" America"),
                    Message.raw(" --Prairies--Wandering"),
                    Message.raw(" tribes"),
                    Message.raw(" of"),
                    Message.raw(" natives--Their"),
                    Message.raw(" outward"),
                    Message.raw(" appearance,"),
                    Message.raw(" customs,"),
                    Message.raw(" and"),
                    Message.raw(" languages--Traces"),
                    Message.raw(" of"),
                    Message.raw(" an"),
                    Message.raw(" unknown"),
                    Message.raw(" people.") },
              "North America is divided into two vast regions, one inclining towards the Pole," +
              " the other towards the Equator--Valley of the Mississippi--Traces found there of" +
              " the revolutions of the globe --Shore of the Atlantic Ocean, on which the English" +
              " colonies were founded--Different aspects of North and of South America at the" +
              " time of their discovery--Forests of North America --Prairies--Wandering tribes" +
              " of natives--Their outward appearance, customs, and languages--Traces of an" +
              " unknown people."
            }

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



  @DataProvider(name = "toMessageData2")
  public Object[][] toMessageData2() {
    return new Object[][] {
        {
            CoreMessages.ERR_ADD_CANNOT_ADD_ROOT_DSE.get(),
            CoreMessages.ERR_ABANDON_OP_NO_SUCH_OPERATION.get(1),
            CoreMessages.ERR_ADD_CANNOT_ADD_ROOT_DSE.getCategory(),
            CoreMessages.ERR_ADD_CANNOT_ADD_ROOT_DSE.getSeverity()
        },
        {
            Message.raw(Category.JEB, Severity.FATAL_ERROR, "test message"),
            CoreMessages.ERR_ABANDON_OP_NO_SUCH_OPERATION.get(1),
            Category.JEB,
            Severity.FATAL_ERROR
        },
        {
            Message.raw("test message"),
            CoreMessages.ERR_ABANDON_OP_NO_SUCH_OPERATION.get(1),
            Category.USER_DEFINED,
            Severity.INFORMATION
        }
    };
  }



  @Test(dataProvider = "toMessageData2")
  public void testToMessage2(Message m1, Message m2, Category c, Severity s) {
    MessageBuilder mb = new MessageBuilder();

    mb.append(m1);
    mb.append(m2);
    Message m = mb.toMessage();

    assertEquals(m.getDescriptor().getCategory(), c);
    assertEquals(m.getDescriptor().getSeverity(), s);
  }
}
