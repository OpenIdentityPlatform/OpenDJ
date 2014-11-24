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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2012-2014 ForgeRock AS.
 */

package org.forgerock.opendj.ldif;

import java.io.IOException;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.forgerock.opendj.ldap.LdapException.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

/**
 * This class tests the ConnectionChangeRecordWriter functionality.
 */
@SuppressWarnings("javadoc")
public class ConnectionChangeRecordWriterTestCase extends AbstractLDIFTestCase {

    /**
     * Provide a standard LDIF Change Record, valid, for tests below.
     *
     * @return a string containing a standard LDIF Change Record.
     */
    public final String[] getStandardLDIFChangeRecord() {
        // @formatter:off
        return new String[] {
            "version: 1",
            "dn: uid=scarter,ou=People,dc=example,dc=com",
            "changetype: add",
            "sn: Carter",
            "cn: Samnatha Carter",
            "givenName: Sam",
            "objectClass: inetOrgPerson",
            "telephoneNumber: 555 555-5555",
            "mail: scarter@mail.org",
            "entryDN: uid=scarter,ou=people,dc=example,dc=org",
            "entryUUID: ad55a34a-763f-358f-93f9-da86f9ecd9e4",
            "modifyTimestamp: 20120903142126Z",
            "modifiersName: cn=Internal Client,cn=Root DNs,cn=config"
        };
        // @formatter:on
    }

    /**
     * Write a Change Record - AddRequest.
     *
     * @throws Exception
     */
    @Test
    public final void testWriteChangeRecordAddRequest() throws Exception {
        Connection connection = mock(Connection.class);
        ConnectionChangeRecordWriter writer = null;

        try {
            writer = new ConnectionChangeRecordWriter(connection);
            writer.writeChangeRecord(Requests.newAddRequest(getStandardLDIFChangeRecord()));
            verify(connection, times(1)).add(any(AddRequest.class));
        } finally {
            writer.close();
        }
    }

    /**
     * The writeChangeRecord (AddRequest) doesn't allow a null.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NullPointerException.class)
    public final void testWriteChangeRecordAddRequestDoesntAllowNull() throws Exception {
        final Connection connection = mock(Connection.class);
        ConnectionChangeRecordWriter writer = null;
        try {
            writer = new ConnectionChangeRecordWriter(connection);
            writer.writeChangeRecord((AddRequest) null);
        } finally {
            writer.close();
        }
    }

    /**
     * ConnectionChangeRecordWriter write a change record (in this example the
     * ChangeRecord is an AddRequest).
     *
     * @throws Exception
     */
    @Test
    public final void testWriteChangeRecordContainingAddRequest() throws Exception {
        Connection connection = mock(Connection.class);
        ConnectionChangeRecordWriter writer = null;

        try {
            writer = new ConnectionChangeRecordWriter(connection);
            writer.writeChangeRecord(Requests.newChangeRecord(getStandardLDIFChangeRecord()));
            Assert.assertTrue(Requests.newChangeRecord(getStandardLDIFChangeRecord()) instanceof AddRequest);
            verify(connection, times(1)).add(any(AddRequest.class));
        } finally {
            writer.close();
        }
    }

    /**
     * ConnectionChangeRecordWriter write a change record (in this example the
     * ChangeRecord is a DeleteRequest).
     *
     * @throws Exception
     */
    @Test
    public final void testWriteChangeRecordContainingDeleteRequest() throws Exception {
        Connection connection = mock(Connection.class);
        ConnectionChangeRecordWriter writer = null;

        try {
            writer = new ConnectionChangeRecordWriter(connection);
            // @formatter:off
            ChangeRecord cr = Requests.newChangeRecord(
                "dn: dc=example,dc=com",
                "changetype: delete"
            );
            writer.writeChangeRecord(cr);
            // @formatter:on
            Assert.assertTrue(cr instanceof DeleteRequest);
            verify(connection, times(1)).delete(any(DeleteRequest.class));
        } finally {
            writer.close();
        }
    }

    /**
     * The writer allow only one LDIF ChangeRecord. Exception expected.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = LocalizedIllegalArgumentException.class)
    public final void testWriteChangeRecordDoesntAllowMultipleLDIF() throws Exception {
        Connection connection = mock(Connection.class);
        ConnectionChangeRecordWriter writer = null;

        try {
            writer = new ConnectionChangeRecordWriter(connection);
            // @formatter:off
            writer.writeChangeRecord(Requests.newChangeRecord(
                "dn: uid=scarter,ou=People,dc=example,dc=com",
                "changetype: modify",
                "replace:sn",
                "sn: scarter",
                "",
                "dn: uid=user.0,ou=People,dc=example,dc=com",
                "changetype: modify",
                "replace:sn",
                "sn: Amarr")
            );
            // @formatter:on

        } finally {
            writer.close();
        }
    }

    /**
     * ConnectionChangeRecordWriter writes a ChangeRecord and an IOException
     * occurs on the ChangeAccept call.
     *
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    @Test(expectedExceptions = RuntimeException.class)
    public final void testWriteChangeRecordChangeAcceptSendIOException() throws Exception {
        Connection connection = mock(Connection.class);
        ConnectionChangeRecordWriter writer = null;
        ChangeRecord cr = mock(ChangeRecord.class);

        when(cr.accept(any(ChangeRecordVisitor.class), any(ConnectionChangeRecordWriter.class)))
                .thenAnswer(new Answer<IOException>() {
                    @Override
                    public IOException answer(final InvocationOnMock invocation) throws Throwable {
                        // Execute handler and return future.
                        final ChangeRecordVisitor<?, ?> handler =
                                (ChangeRecordVisitor<?, ?>) invocation.getArguments()[0];
                        if (handler != null) {
                            // Data here if needed.
                        }
                        return new IOException("IOException_e_is_not_null");
                    }
                });

        try {
            writer = new ConnectionChangeRecordWriter(connection);
            writer.writeChangeRecord(cr);
        } finally {
            writer.close();
        }
    }

    /**
     * ConnectionChangeRecordWriter writes a ChangeRecord and an
     * LdapException occurs on the ChangeAccept call.
     *
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    @Test(expectedExceptions = LdapException.class)
    public final void testWriteChangeRecordChangeAcceptSendLdapException() throws Exception {
        Connection connection = mock(Connection.class);
        ConnectionChangeRecordWriter writer = null;
        ChangeRecord cr = mock(ChangeRecord.class);

        when(cr.accept(any(ChangeRecordVisitor.class), any(ConnectionChangeRecordWriter.class)))
                .thenAnswer(new Answer<LdapException>() {
                    @Override
                    public LdapException answer(final InvocationOnMock invocation) throws Throwable {
                        // Execute handler and return future.
                        final ChangeRecordVisitor<?, ?> handler =
                                (ChangeRecordVisitor<?, ?>) invocation.getArguments()[0];
                        if (handler != null) {
                            // Data here if needed.
                        }
                        return newLdapException(ResultCode.UNAVAILABLE_CRITICAL_EXTENSION);
                    }
                });

        try {
            writer = new ConnectionChangeRecordWriter(connection);
            writer.writeChangeRecord(cr);
        } finally {
            writer.close();
        }
    }

    /**
     * The writeChangeRecord (ChangeRecord) doesn't allow a null.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NullPointerException.class)
    public final void testWriteChangeRecordChangeRecordDoesntAllowNull() throws Exception {
        final Connection connection = mock(Connection.class);
        ConnectionChangeRecordWriter writer = null;
        try {
            writer = new ConnectionChangeRecordWriter(connection);
            writer.writeChangeRecord((ChangeRecord) null);
        } finally {
            writer.close();
        }
    }

    /**
     * Use the ConnectionChangeRecordWriter to write a DeleteRequest.
     *
     * @throws Exception
     */
    @Test
    public final void testWriteChangeRecordDeleteRequest() throws Exception {
        Connection connection = mock(Connection.class);
        ConnectionChangeRecordWriter writer = null;

        try {
            ChangeRecord cr = Requests.newDeleteRequest(DN.valueOf("cn=scarter,dc=example,dc=com"));
            writer = new ConnectionChangeRecordWriter(connection);
            writer.writeChangeRecord(cr);
            verify(connection, times(1)).delete(any(DeleteRequest.class));
        } finally {
            writer.close();
        }
    }

    /**
     * The writeChangeRecord (DeleteRequest) doesn't allow a null.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NullPointerException.class)
    public final void testWriteChangeRecordDeleteRequestDoesntAllowNull() throws Exception {
        final Connection connection = mock(Connection.class);
        ConnectionChangeRecordWriter writer = null;
        try {
            writer = new ConnectionChangeRecordWriter(connection);
            writer.writeChangeRecord((DeleteRequest) null);
        } finally {
            writer.close();
        }
    }

    /**
     * Use the ConnectionChangeRecordWriter to write a ModifyDNRequest.
     *
     * @throws Exception
     */
    @Test
    public final void testWriteChangeRecordModifyDNRequest() throws Exception {
        Connection connection = mock(Connection.class);
        ConnectionChangeRecordWriter writer = null;

        try {
            // @formatter:off
            ChangeRecord cr = Requests.newModifyDNRequest(
                "cn=scarter,dc=example,dc=com",
                "cn=Susan Jacobs");
            //@formatter:on
            writer = new ConnectionChangeRecordWriter(connection);
            writer.writeChangeRecord(cr);
            verify(connection, times(1)).modifyDN(any(ModifyDNRequest.class));
        } finally {
            writer.close();
        }
    }

    /**
     * The writeChangeRecord (ModifyDNRequest) doesn't allow a null.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NullPointerException.class)
    public final void testWriteChangeRecordModifyDNRequestDoesntAllowNull() throws Exception {
        final Connection connection = mock(Connection.class);
        ConnectionChangeRecordWriter writer = null;
        try {
            writer = new ConnectionChangeRecordWriter(connection);
            writer.writeChangeRecord((ModifyDNRequest) null);
        } finally {
            writer.close();
        }
    }

    /**
     * Use the ConnectionChangeRecordWriter to write a ModifyRequest.
     *
     * @throws Exception
     */
    @Test
    public final void testWriteChangeRecordModifyRequest() throws Exception {
        Connection connection = mock(Connection.class);
        ConnectionChangeRecordWriter writer = null;

        try {
            writer = new ConnectionChangeRecordWriter(connection);
            // @formatter:off
            ChangeRecord cr = Requests.newModifyRequest(
                    "dn: cn=Fiona Jensen, ou=Marketing, dc=airius, dc=com",
                    "changetype: modify",
                    "delete: telephonenumber",
                    "telephonenumber: +1 408 555 1212"
            );
            writer.writeChangeRecord(cr);
            // @formatter:on
            verify(connection, times(1)).modify(any(ModifyRequest.class));
        } finally {
            writer.close();
        }
    }

    /**
     * The writeChangeRecord (ModifyRequest) doesn't allow a null.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NullPointerException.class)
    public final void testWriteChangeRecordModifyRequestDoesntAllowNull() throws Exception {
        final Connection connection = mock(Connection.class);
        ConnectionChangeRecordWriter writer = null;
        try {
            writer = new ConnectionChangeRecordWriter(connection);
            writer.writeChangeRecord((ModifyRequest) null);
        } finally {
            writer.close();
        }
    }

    /**
     * The writeComment do nothing. ConnectionChangeRecordWriter do not support
     * Comment.
     *
     * @throws Exception
     */
    @Test
    public final void testWriteCommentDoNotSupportComment() throws Exception {
        Connection connection = mock(Connection.class);
        ConnectionChangeRecordWriter writer = null;

        try {
            writer = new ConnectionChangeRecordWriter(connection);
            writer.writeComment("# A new comment");
            verify(connection, Mockito.never()).add(any(String.class));
            verify(connection, Mockito.never()).delete(any(String.class));
            verify(connection, Mockito.never()).modify(any(String.class));
            verify(connection, Mockito.never()).modifyDN(any(String.class), any(String.class));
        } finally {
            writer.close();
        }
    }

    /**
     * The writeComment doesn't allow a null.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NullPointerException.class)
    public final void testWriteCommentDoesntAllowNull() throws Exception {
        final Connection connection = mock(Connection.class);
        ConnectionChangeRecordWriter writer = null;
        try {
            writer = new ConnectionChangeRecordWriter(connection);
            writer.writeComment(null);
        } finally {
            writer.close();
        }
    }

    /**
     * The ConnectionChangeRecordWriter doesn't allow a null connection.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = NullPointerException.class)
    public final void testConnectionChangeRecordWriterDoesntAllowNull() throws Exception {
        ConnectionChangeRecordWriter writer = null;
        try {
            writer = new ConnectionChangeRecordWriter(null);
        } finally {
            writer.close();
        }
    }

    /**
     * Verify ConnectionChangeRecordWriter close function.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public final void testConnectionChangeRecordWriterClose() throws Exception {
        Connection connection = mock(Connection.class);
        ConnectionChangeRecordWriter writer = null;
        try {
            writer = new ConnectionChangeRecordWriter(connection);
        } finally {
            writer.close();
            verify(connection, times(1)).close();
        }
    }
}
