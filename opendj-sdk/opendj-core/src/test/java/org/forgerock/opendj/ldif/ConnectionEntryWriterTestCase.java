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
 * Copyright 2011 ForgeRock AS.
 * Portions copyright 2012-2016 ForgeRock AS.
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

        try (ConnectionEntryWriter writer = new ConnectionEntryWriter(connection)) {
            writer.writeComment("This is a test for the ConnectionEntryWriter");
            writer.writeEntry(entry);
            verify(connection, times(1)).add(any(Entry.class));
        }
    }

    /**
     * The ConnectionEntryWriter doesn't allow a null comment.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NullPointerException.class)
    public final void testConnectionEntryWriterDoesntAllowNullComment() throws Exception {
        try (ConnectionEntryWriter writer = new ConnectionEntryWriter(null)) {
            writer.writeComment(null);
        }
    }

    /**
     * The ConnectionEntryWriter doesn't allow a null connection.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NullPointerException.class)
    public final void testConnectionEntryWriterDoesntAllowNull() throws Exception {
        new ConnectionEntryWriter(null);
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
        new ConnectionEntryWriter(connection).close();
        verify(connection, times(1)).close();
    }
}
