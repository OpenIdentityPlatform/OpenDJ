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
 *      Copyright 2013 ForgeRock AS.
 */
package org.forgerock.opendj.io;

import static org.fest.assertions.Assertions.*;

import java.io.IOException;

import org.forgerock.opendj.asn1.ASN1Reader;
import org.forgerock.opendj.asn1.ASN1Writer;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SdkTestCase;
import org.forgerock.opendj.ldap.requests.AbandonRequest;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.GenericBindRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.spi.AbstractLDAPMessageHandler;
import org.forgerock.opendj.ldap.spi.LDAPMessageHandler;
import org.forgerock.opendj.ldap.spi.UnexpectedRequestException;
import org.forgerock.opendj.ldap.spi.UnexpectedResponseException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Support class for testing {@code LDAPWriter} and {@code LDAPReader}
 * classes in a specific transport provider.
 *
 * A specific transport provider should provide a test case by :
 * <ul>
 *   <li>Extending this class</li>
 *   <li>Implementing the 3 abstract methods {@code getLDAPReader()}, {@code getLDAPReader()}
 *   and {@code transferFromWriterToReader()}</li>
 * </ul>
 */
public abstract class LDAPReaderWriterTestCase extends SdkTestCase {

    // DN used is several tests
    private static final String TEST_DN = "cn=test";

    interface LDAPWrite {
        public void perform(LDAPWriter<? extends ASN1Writer> writer) throws IOException;
    }

    @DataProvider
    protected Object[][] messagesFactories() {
        return new Object[][] {
                addRequest(),
                addResult(),
                abandonRequest(),
                bindRequest(),
        };
    }

    Object[] addRequest() {
        return new Object[] { new LDAPWrite() {
            @Override
            public void perform(LDAPWriter<? extends ASN1Writer> writer) throws IOException {
                writer.writeAddRequest(0, Requests.newAddRequest(TEST_DN));
            }
        }, new AbstractLDAPMessageHandler() {
            @Override
            public void addRequest(int messageID, AddRequest request) throws UnexpectedRequestException, IOException {
                assertThat(request.getName().toString()).isEqualTo(TEST_DN);
            }
        } };
    }

    Object[] addResult() {
        final ResultCode resultCode = ResultCode.SUCCESS;
        return new Object[] { new LDAPWrite() {
            @Override
            public void perform(LDAPWriter<? extends ASN1Writer> writer) throws IOException {
                writer.writeAddResult(1, Responses.newResult(resultCode).setMatchedDN(TEST_DN));
            }
        }, new AbstractLDAPMessageHandler() {
            @Override
            public void addResult(int messageID, Result result) throws UnexpectedResponseException, IOException {
                assertThat(result.getResultCode()).isEqualTo(resultCode);
                assertThat(result.getMatchedDN().toString()).isEqualTo(TEST_DN);
            }
        } };
    }

    Object[] abandonRequest() {
        final int requestID = 1;
        return new Object[] { new LDAPWrite() {
            @Override
            public void perform(LDAPWriter<? extends ASN1Writer> writer) throws IOException {
                writer.writeAbandonRequest(0, Requests.newAbandonRequest(requestID));
            }
        }, new AbstractLDAPMessageHandler() {
            @Override
            public void abandonRequest(int messageID, AbandonRequest request) throws UnexpectedRequestException,
            IOException {
                assertThat(request.getRequestID()).isEqualTo(requestID);
            }
        } };
    }

    Object[] bindRequest() {
        final byte type = 0x01;
        final byte[] value = new byte[] {0x01, 0x02};
        return new Object[] { new LDAPWrite() {
            @Override
            public void perform(LDAPWriter<? extends ASN1Writer> writer) throws IOException {
                writer.writeBindRequest(0, 1,
                        Requests.newGenericBindRequest(TEST_DN, type, value));
            }
        }, new AbstractLDAPMessageHandler() {
            @Override
            public void bindRequest(final int messageID, final int version, final GenericBindRequest request)
                    throws UnexpectedRequestException, IOException {
                assertThat(request.getAuthenticationType()).isEqualTo(type);
                assertThat(request.getAuthenticationValue()).isEqualTo(value);
                assertThat(request.getName()).isEqualTo(TEST_DN);
            }
        } };
    }

    /**
     * Test that a LDAP message written by LDAPWriter is read correctly using LDAPReader.
     *
     * @param writing write instruction to perform
     * @param messageHandler handler of message read, containing assertion
     *
     * @throws Exception
     */
    @Test(dataProvider = "messagesFactories")
    public void testWriteReadMessage(LDAPWrite writing, LDAPMessageHandler messageHandler)
            throws Exception {
        LDAPWriter<? extends ASN1Writer> writer = getLDAPWriter();
        writing.perform(writer);
        LDAPReader<? extends ASN1Reader> reader = getLDAPReader();
        transferFromWriterToReader(writer, reader);
        reader.readMessage(messageHandler);
    }

    /**
     * Returns a writer specific to the transport module.
     */
    abstract protected LDAPWriter<? extends ASN1Writer> getLDAPWriter();

    /**
     * Returns a reader specific to the transport module.
     */
    abstract protected LDAPReader<? extends ASN1Reader> getLDAPReader();

    /**
     * Transfer raw data from writer to the reader.
     */
    abstract protected void transferFromWriterToReader(LDAPWriter<? extends ASN1Writer> writer,
            LDAPReader<? extends ASN1Reader> reader);
}
