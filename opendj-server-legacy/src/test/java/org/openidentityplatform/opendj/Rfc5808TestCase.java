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
 * Copyright 2025 3A Systems, LLC.
 */
package org.openidentityplatform.opendj;


import com.forgerock.opendj.ldap.extensions.EndTransactionExtendedRequest;
import com.forgerock.opendj.ldap.extensions.EndTransactionExtendedResult;
import com.forgerock.opendj.ldap.extensions.StartTransactionExtendedRequest;
import org.forgerock.opendj.ldap.*;
import org.forgerock.opendj.ldap.controls.TransactionSpecificationRequestControl;
import org.forgerock.opendj.ldap.requests.*;
import com.forgerock.opendj.ldap.extensions.StartTransactionExtendedResult;
import org.forgerock.opendj.ldap.responses.Result;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.forgerock.opendj.ldap.requests.Requests.newAddRequest;
import static org.testng.Assert.assertThrows;

@Test(sequential = true)
public class Rfc5808TestCase extends DirectoryServerTestCase {
    Connection connection;

    @BeforeClass
    public void startServer() throws Exception {
        TestCaseUtils.startServer();
        TestCaseUtils.initializeTestBackend(true);

        final LDAPConnectionFactory factory =new LDAPConnectionFactory("localhost", TestCaseUtils.getServerLdapPort());
        connection = factory.getConnection();
        connection.bind("cn=Directory Manager", "password".toCharArray());
        assertThat(connection.isValid()).isTrue();
    }

    @Test
    public void test() throws LdapException {
        //unknown transaction in TransactionSpecificationRequestControl
        assertThrows(CancelledResultException.class, new Assert.ThrowingRunnable() {
            @Override
            public void run() throws Throwable {
                AddRequest add=Requests
                        .newAddRequest("ou=People,o=test")
                        .addAttribute("objectClass", "top", "organizationalUnit")
                        .addAttribute("ou", "People")
                        .addControl(new TransactionSpecificationRequestControl("bad"))
                        ;
                Result result = connection.add(add);
            }
        });

        //unknown transaction in EndTransactionExtendedRequest
        assertThrows(CancelledResultException.class, new Assert.ThrowingRunnable() {
            @Override
            public void run() throws Throwable {
                EndTransactionExtendedResult resEnd=connection.extendedRequest(new EndTransactionExtendedRequest().setTransactionID("unknown").setCommit(true));
            }
        });
        assertThrows(CancelledResultException.class, new Assert.ThrowingRunnable() {
            @Override
            public void run() throws Throwable {
                EndTransactionExtendedResult resEnd=connection.extendedRequest(new EndTransactionExtendedRequest().setTransactionID("unknown"));
            }
        });

        //commit
        StartTransactionExtendedResult resStart=connection.extendedRequest(new StartTransactionExtendedRequest());
        assertThat(resStart.isSuccess()).isTrue();
        assertThat(resStart.getOID()).isEqualTo("1.3.6.1.1.21.1");
        String transactionID=resStart.getTransactionID();
        assertThat(transactionID).isNotEmpty();

        AddRequest add=Requests
                .newAddRequest("ou=People,o=test")
                .addAttribute("objectClass", "top", "organizationalUnit")
                .addAttribute("ou", "People")
                .addControl(new TransactionSpecificationRequestControl(transactionID))
                ;
        Result result = connection.add(add);
        assertThat(result.isSuccess()).isTrue();

        add= Requests.newAddRequest("sn=bjensen,ou=People,o=test")
                .addAttribute("objectClass","top","person")
                .addAttribute("cn","bjensen")
                .addControl(new TransactionSpecificationRequestControl(transactionID))
        ;
        result = connection.add(add);
        assertThat(result.isSuccess()).isTrue();

        ModifyDNRequest mdn=Requests.newModifyDNRequest("sn=bjensen,ou=People,o=test","sn=bjensen2")
        .addControl(new TransactionSpecificationRequestControl(transactionID))
        ;
        result = connection.modifyDN(mdn);
        assertThat(result.isSuccess()).isTrue();

        ModifyRequest edit= Requests.newModifyRequest("sn=bjensen2,ou=People,o=test")
                .addModification(ModificationType.REPLACE,"cn","bjensen2")
        .addControl(new TransactionSpecificationRequestControl(transactionID))
        ;
        result = connection.modify(edit);
        assertThat(result.isSuccess()).isTrue();

        DeleteRequest delete=Requests.newDeleteRequest("sn=bjensen2,ou=People,o=test")
        .addControl(new TransactionSpecificationRequestControl(transactionID))
        ;
        result = connection.delete(delete);
        assertThat(result.isSuccess()).isTrue();

        assertThrows(EntryNotFoundException.class, new Assert.ThrowingRunnable() {
            @Override
            public void run() throws Throwable {
                connection.searchSingleEntry("o=test",SearchScope.SINGLE_LEVEL,"(ou=People)");
            }
        });

        EndTransactionExtendedResult resEnd=connection.extendedRequest(new EndTransactionExtendedRequest().setTransactionID(transactionID));
        assertThat(resEnd.isSuccess()).isTrue();
        assertThat(resEnd.getOID()).isEqualTo("1.3.6.1.1.21.3");

        //check commit successfully
        assertThat(connection.searchSingleEntry("o=test",SearchScope.SINGLE_LEVEL,"(ou=People)")).isNotNull();

        //check transaction finished
        String finalTransactionID = transactionID;
        assertThrows(CancelledResultException.class, new Assert.ThrowingRunnable() {
            @Override
            public void run() throws Throwable {
                EndTransactionExtendedResult resEnd=connection.extendedRequest(new EndTransactionExtendedRequest().setTransactionID(finalTransactionID).setCommit(true));
            }
        });

        //rollback by EndTransactionExtendedRequest
        resStart=connection.extendedRequest(new StartTransactionExtendedRequest());
        assertThat(resStart.isSuccess()).isTrue();
        assertThat(resStart.getOID()).isEqualTo("1.3.6.1.1.21.1");
        transactionID=resStart.getTransactionID();
        assertThat(transactionID).isNotEmpty();

        add=Requests
                .newAddRequest("ou=People2,o=test")
                .addAttribute("objectClass", "top", "organizationalUnit")
                .addAttribute("ou", "People2")
                .addControl(new TransactionSpecificationRequestControl(transactionID))
                ;
        result = connection.add(add);
        assertThat(result.isSuccess()).isTrue();

        resEnd=connection.extendedRequest(new EndTransactionExtendedRequest().setTransactionID(transactionID).setCommit(false));
        assertThat(resEnd.isSuccess()).isTrue();
        assertThat(resEnd.getOID()).isEqualTo("1.3.6.1.1.21.3");

        //check transaction finished
        String finalTransactionID1 = transactionID;
        assertThrows(CancelledResultException.class, new Assert.ThrowingRunnable() {
            @Override
            public void run() throws Throwable {
                EndTransactionExtendedResult resEnd=connection.extendedRequest(new EndTransactionExtendedRequest().setTransactionID(finalTransactionID1).setCommit(false));
            }
        });

        //check rollback successfully
        assertThrows(EntryNotFoundException.class, new Assert.ThrowingRunnable() {
            @Override
            public void run() throws Throwable {
                connection.searchSingleEntry("o=test",SearchScope.SINGLE_LEVEL,"(ou=People2)");
            }
        });

        //rollback by error
        resStart=connection.extendedRequest(new StartTransactionExtendedRequest());
        assertThat(resStart.isSuccess()).isTrue();
        assertThat(resStart.getOID()).isEqualTo("1.3.6.1.1.21.1");
        transactionID=resStart.getTransactionID();
        assertThat(transactionID).isNotEmpty();

        add= Requests.newAddRequest("sn=bjensen0,ou=People,o=test")
                .addAttribute("objectClass","top","person")
                .addAttribute("cn","bjensen0")
                .addControl(new TransactionSpecificationRequestControl(transactionID))
        ;
        result = connection.add(add);
        assertThat(result.isSuccess()).isTrue();

        add= Requests.newAddRequest("sn=bjensen,ou=People,o=test")
                .addAttribute("objectClass","top","person")
                .addAttribute("cn","bjensen")
                .addControl(new TransactionSpecificationRequestControl(transactionID))
        ;
        result = connection.add(add);
        assertThat(result.isSuccess()).isTrue();

        mdn=Requests.newModifyDNRequest("sn=bjensen,ou=People,o=test","sn=bjensen2")
                .addControl(new TransactionSpecificationRequestControl(transactionID))
                ;
        result = connection.modifyDN(mdn);
        assertThat(result.isSuccess()).isTrue();

        edit= Requests.newModifyRequest("sn=bjensen2,ou=People,o=test")
                .addModification(ModificationType.REPLACE,"cn","bjensen2")
                .addControl(new TransactionSpecificationRequestControl(transactionID))
                ;
        result = connection.modify(edit);
        assertThat(result.isSuccess()).isTrue();

        delete=Requests.newDeleteRequest("sn=bjensen2,ou=People,o=test")
                .addControl(new TransactionSpecificationRequestControl(transactionID))
                ;
        result = connection.delete(delete);
        assertThat(result.isSuccess()).isTrue();

        delete=Requests.newDeleteRequest("sn=bjensen3,ou=People,o=test")
                .addControl(new TransactionSpecificationRequestControl(transactionID))
        ;
        result = connection.delete(delete);
        assertThat(result.isSuccess()).isTrue();

        String finalTransactionID3 = transactionID;
        assertThrows(EntryNotFoundException.class, new Assert.ThrowingRunnable() {
            @Override
            public void run() throws Throwable {
                EndTransactionExtendedResult resEnd=connection.extendedRequest(new EndTransactionExtendedRequest().setTransactionID(finalTransactionID3).setCommit(true));
            }
        });

        //check transaction finished
        String finalTransactionID2 = transactionID;
        assertThrows(CancelledResultException.class, new Assert.ThrowingRunnable() {
            @Override
            public void run() throws Throwable {
                EndTransactionExtendedResult resEnd=connection.extendedRequest(new EndTransactionExtendedRequest().setTransactionID(finalTransactionID2).setCommit(false));
            }
        });

        //check rollback successfully
        assertThrows(EntryNotFoundException.class, new Assert.ThrowingRunnable() {
            @Override
            public void run() throws Throwable {
                connection.searchSingleEntry("ou=People,o=test",SearchScope.SINGLE_LEVEL,"(cn=bjensen0)");
            }
        });

    }
}
