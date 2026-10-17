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
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.replication.server;

import static org.assertj.core.api.Assertions.*;

import org.opends.server.DirectoryServerTestCase;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Tests how a {@link ReplicationServerDomain} tells the generation id warning of a peer replication
 * server by the id of its message (issue #1207).
 */
@SuppressWarnings("javadoc")
public class ReplicationServerDomainGenerationIdWarningTest extends DirectoryServerTestCase
{
  @DataProvider
  public Object[][] msgIDs()
  {
    return new Object[][] {
      // since OpenDJ 3.0: <resource name>-<ordinal>
      { "org.opends.messages.replication-77", true },
      // OpenDJ 2.6 (ErrorMsg, MessageDescriptor.getId() at tag 2.6.0): SYNC 0x00E00000 | SEVERE_WARNING 0x00020000 | 77
      { "14811213", true },
      // another message of the same file, in either form
      { "org.opends.messages.replication-78", false },
      { "14811214", false },
      // the same ordinal with another severity (SEVERE_ERROR 0x00040000) or in another file
      { "14942285", false },
      { "org.opends.messages.backend-77", false },
      { "77", false },
      { null, false },
    };
  }

  @Test(dataProvider = "msgIDs")
  public void generationIdWarningIsToldByTheIdOfItsMessage(String msgID, boolean isGenerationIdWarning)
  {
    assertThat(ReplicationServerDomain.isGenerationIdWarningMsgID(msgID)).isEqualTo(isGenerationIdWarning);
  }
}
