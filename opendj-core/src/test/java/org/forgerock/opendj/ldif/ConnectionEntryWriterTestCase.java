/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2011 ForgeRock AS
 *      Portions copyright 2012 ForgeRock AS.
 */

package org.forgerock.opendj.ldif;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.LinkedHashMapEntry;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.opendj.ldap.responses.Result;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.Assert;
import org.testng.annotations.Test;


import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

/**
 * This class tests the ConnectionEntryWriter functionality.
 */
@SuppressWarnings("javadoc")
public class ConnectionEntryWriterTestCase extends AbstractLDIFTestCase {

    /**
     * ConnectionEntryWriter writes entry to the directory server.
     *
     * @throws Exception
     */
    @Test
    public final void testConnectionEntryWriterWritesEntry() throws Exception {
        Connection connection = mock(Connection.class);
        ConnectionEntryWriter writer = null;

        final Entry entry =
                new LinkedHashMapEntry("cn=scarter,dc=example,dc=com").addAttribute("objectclass",
                        "top").addAttribute("objectclass", "person").addAttribute("objectclass",
                        "organizationalPerson").addAttribute("objectclass", "inetOrgPerson")
                        .addAttribute("mail", "subgenius@example.com").addAttribute("sn", "carter");

        when(connection.add(any(Entry.class))).thenAnswer(new Answer<Result>() {
            @Override
            public Result answer(final InvocationOnMock invocation) throws Throwable {
                // Execute handler and return future.
                final Entry handler = (Entry) invocation.getArguments()[0];
                if (handler != null) {
                    Assert.assertEquals(handler.getName().toString(), "cn=scarter,dc=example,dc=com");
                    Assert.assertEquals(handler.getAttribute("sn").firstValueAsString(), "carter");
                    Assert.assertEquals(handler.getAttributeCount(), 3);
                }
                return Responses.newResult(ResultCode.SUCCESS);
            }
        });

        try {
            writer = new ConnectionEntryWriter(connection);
            writer.writeComment("This is a test for the ConnectionEntryWriter");
            writer.writeEntry(entry);
            verify(connection, times(1)).add(any(Entry.class));
        } finally {
            writer.close();
        }
    }

    /**
     * The ConnectionEntryWriter doesn't allow a null comment.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NullPointerException.class)
    public final void testConnectionEntryWriterDoesntAllowNullComment() throws Exception {
        ConnectionEntryWriter writer = null;
        try {
            writer = new ConnectionEntryWriter(null);
            writer.writeComment(null);
        } finally {
            writer.close();
        }
    }

    /**
     * The ConnectionEntryWriter doesn't allow a null connection.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NullPointerException.class)
    public final void testConnectionEntryWriterDoesntAllowNull() throws Exception {
        ConnectionEntryWriter writer = null;
        try {
            writer = new ConnectionEntryWriter(null);
        } finally {
            writer.close();
        }
    }

    /**
     * Verify ConnectionEntryWriter close function.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public final void testConnectionEntryWriterClose() throws Exception {
        Connection connection = mock(Connection.class);
        ConnectionEntryWriter writer = null;
        try {
            writer = new ConnectionEntryWriter(connection);
        } finally {
            writer.close();
            verify(connection, times(1)).close();
        }
    }
}
