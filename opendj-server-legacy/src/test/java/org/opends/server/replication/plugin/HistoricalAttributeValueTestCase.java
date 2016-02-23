/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2015 ForgeRock AS.
 */
package org.opends.server.replication.plugin;

import org.assertj.core.api.Assertions;
import org.opends.server.replication.ReplicationTestCase;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Test the naming conflict resolution code. */
@SuppressWarnings("javadoc")
public class HistoricalAttributeValueTestCase extends ReplicationTestCase
{
  @DataProvider
  public Object[][] values()
  {
    return new Object[][] {
      { "description:0000014f2d0c9f53000100000001:add:added_value" },
      { "description:0000014f2d0c9f53000100000001:add" },
      { "description:0000014f2d0c9f53000100000001:del:deleted_value" },
      { "description:0000014f2d0c9f53000100000001:del" },
      { "description:0000014f2d0c9f53000100000001:repl:new_value" },
      { "description:0000014f2d0c9f53000100000001:repl" },
      { "description:0000014f2d0c9f53000100000001:attrDel" },
      { "dn:0000014f2d0c9f53000100000001:add" },
      { "dn:0000014f2d0c9f53000100000001:moddn" },
      { "description;FR;France:0000014f2d0c9f53000100000001:add:added_value" },
    };
  }

  @Test(dataProvider = "values")
  public void testCtor(String strVal)
  {
    HistoricalAttributeValue val = new HistoricalAttributeValue(strVal);
    Assertions.assertThat(strVal).isEqualTo(val.toString());
  }
}
