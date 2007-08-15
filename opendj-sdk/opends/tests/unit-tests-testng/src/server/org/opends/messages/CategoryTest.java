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

/**
 * Category Tester.
 *
 */
public class CategoryTest
{

    @DataProvider(name = "messageDescriptors")
    public Object[][] getMessageDescriptors() {
      return new Object[][] {
              {CoreMessages.ERR_ABANDON_OP_NO_SUCH_OPERATION}
      };
    }

    @Test(dataProvider = "messageDescriptors")
    public void testParseMessageId(MessageDescriptor md)
    {
      assertEquals(md.getCategory(), Category.parseMessageId(md.getId()));
    }

    @Test(dataProvider = "messageDescriptors")
    public void testParseMask(MessageDescriptor md)
    {
      assertEquals(md.getCategory(), Category.parseMask(md.getMask()));
    }

    @Test
    public void testGetMask()
    {
      assertNotNull(Category.ACCESS_CONTROL.getMask());
    }

}
