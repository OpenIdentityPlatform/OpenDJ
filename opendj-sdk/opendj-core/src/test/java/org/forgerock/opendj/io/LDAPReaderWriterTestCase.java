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
 *      Copyright 2013-2015 ForgeRock AS.
 */
package org.forgerock.opendj.io;

import static org.fest.assertions.Assertions.*;
import static org.forgerock.opendj.ldap.LDAPConnectionFactory.LDAP_DECODE_OPTIONS;

import java.io.IOException;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.LinkedHashMapEntry;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SdkTestCase;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.requests.AbandonRequest;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.CancelExtendedRequest;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ExtendedRequest;
import org.forgerock.opendj.ldap.requests.GenericBindRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.requests.UnbindRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.IntermediateResponse;
import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;
import org.forgerock.util.Options;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Support class for testing {@code LDAPWriter} and {@code LDAPReader} classes
 * in a specific transport provider.
 * <p>
 * Exercices a write and a read for all LDAP messages supported by the
 * {@code LDAPMessageHandler} class.
 * <p>
 * A specific transport provider should provide a test case by :
 * <ul>
 * <li>Extending this class</li>
 * <li>Implementing the 3 abstract methods {@code getLDAPReader()},
 * {@code getLDAPReader()} and {@code transferFromWriterToReader()}</li>
 * </ul>
 */
@SuppressWarnings("javadoc")
public abstract class LDAPReaderWriterTestCase extends SdkTestCase {

    /** Message ID is used in all tests. */
    private static final int MESSAGE_ID = 0;

    /** DN used is several tests. */
    private static final String TEST_DN = "cn=test";

    interface LDAPWrite {
        void perform(LDAPWriter<? extends ASN1Writer> writer) throws IOException;
    }

    @DataProvider
    protected Object[][] messagesFactories() {
        return new Object[][] { abandonRequest(), addRequest(), addResult(), abandonRequest(),
            bindRequest(), bindResult(), compareRequest(), compareResult(), deleteRequest(),
            deleteResult(), extendedRequest(), extendedResult(), intermediateResponse(),
            modifyDNRequest(), modifyDNResult(), modifyRequest(), modifyResult(), searchRequest(),
            searchResult(), searchResultEntry(), searchResultReference(), unbindRequest(),
            unrecognizedMessage() };
    }

    Object[] abandonRequest() {
        final int requestID = 1;
        return new Object[] { new LDAPWrite() {
            @Override
            public void perform(LDAPWriter<? extends ASN1Writer> writer) throws IOException {
                writer.writeAbandonRequest(MESSAGE_ID, Requests.newAbandonRequest(requestID));
            }
        }, new AbstractLDAPMessageHandler() {
            @Override
            public void abandonRequest(int messageID, AbandonRequest request)
                    throws DecodeException, IOException {
                assertThat(request.getRequestID()).isEqualTo(requestID);
            }
        } };
    }

    Object[] addRequest() {
        return new Object[] { new LDAPWrite() {
            @Override
            public void perform(LDAPWriter<? extends ASN1Writer> writer) throws IOException {
                writer.writeAddRequest(MESSAGE_ID, Requests.newAddRequest(TEST_DN));
            }
        }, new AbstractLDAPMessageHandler() {
            @Override
            public void addRequest(int messageID, AddRequest request) throws DecodeException,
                    IOException {
                assertThat(request.getName().toString()).isEqualTo(TEST_DN);
            }
        } };
    }

    Object[] addResult() {
        final ResultCode resultCode = ResultCode.SUCCESS;
        return new Object[] { new LDAPWrite() {
            @Override
            public void perform(LDAPWriter<? extends ASN1Writer> writer) throws IOException {
                writer.writeAddResult(MESSAGE_ID, Responses.newResult(resultCode).setMatchedDN(
                        TEST_DN));
            }
        }, new AbstractLDAPMessageHandler() {
            @Override
            public void addResult(int messageID, Result result) throws DecodeException, IOException {
                assertThat(result.getResultCode()).isEqualTo(resultCode);
                assertThat(result.getMatchedDN()).isEqualTo(TEST_DN);
            }
        } };
    }

    Object[] bindRequest() {
        final int version = 1;
        final byte type = 0x01;
        final byte[] value = new byte[] { 0x01, 0x02 };
        return new Object[] { new LDAPWrite() {
            @Override
            public void perform(LDAPWriter<? extends ASN1Writer> writer) throws IOException {
                writer.writeBindRequest(MESSAGE_ID, version, Requests.newGenericBindRequest(
                        TEST_DN, type, value));
            }
        }, new AbstractLDAPMessageHandler() {
            @Override
            public void bindRequest(final int messageID, final int version,
                    final GenericBindRequest request) throws DecodeException, IOException {
                assertThat(request.getAuthenticationType()).isEqualTo(type);
                assertThat(request.getAuthenticationValue()).isEqualTo(value);
                assertThat(request.getName()).isEqualTo(TEST_DN);
            }
        } };
    }

    Object[] bindResult() {
        final ResultCode resultCode = ResultCode.SUCCESS;
        return new Object[] { new LDAPWrite() {
            @Override
            public void perform(LDAPWriter<? extends ASN1Writer> writer) throws IOException {
                writer.writeBindResult(MESSAGE_ID, Responses.newBindResult(resultCode));
            }
        }, new AbstractLDAPMessageHandler() {
            @Override
            public void bindResult(final int messageID, final BindResult result)
                    throws DecodeException, IOException {
                assertThat(result.getResultCode()).isEqualTo(resultCode);
            }
        } };
    }

    Object[] compareRequest() {
        final String description = "cn";
        final String value = "test";
        return new Object[] { new LDAPWrite() {
            @Override
            public void perform(LDAPWriter<? extends ASN1Writer> writer) throws IOException {
                writer.writeCompareRequest(MESSAGE_ID, Requests.newCompareRequest(TEST_DN,
                        description, value));
            }
        }, new AbstractLDAPMessageHandler() {
            @Override
            public void compareRequest(final int messageID, final CompareRequest request)
                    throws DecodeException, IOException {
                assertThat(request.getName().toString()).isEqualTo(TEST_DN);
                assertThat(request.getAttributeDescription().toString()).isEqualTo(description);
                assertThat(request.getAssertionValue().toString()).isEqualTo(value);
            }
        } };
    }

    Object[] compareResult() {
        final ResultCode resultCode = ResultCode.SUCCESS;
        return new Object[] { new LDAPWrite() {
            @Override
            public void perform(LDAPWriter<? extends ASN1Writer> writer) throws IOException {
                writer.writeCompareResult(MESSAGE_ID, Responses.newCompareResult(resultCode));
            }
        }, new AbstractLDAPMessageHandler() {
            @Override
            public void compareResult(int messageID, CompareResult result) throws DecodeException,
                    IOException {
                assertThat(result.getResultCode()).isEqualTo(resultCode);
            }
        } };
    }

    Object[] deleteRequest() {
        return new Object[] { new LDAPWrite() {
            @Override
            public void perform(LDAPWriter<? extends ASN1Writer> writer) throws IOException {
                writer.writeDeleteRequest(MESSAGE_ID, Requests.newDeleteRequest(TEST_DN));
            }
        }, new AbstractLDAPMessageHandler() {
            @Override
            public void deleteRequest(int messageID, DeleteRequest request) throws DecodeException,
                    IOException {
                assertThat(request.getName().toString()).isEqualTo(TEST_DN);
            }
        } };
    }

    Object[] deleteResult() {
        final ResultCode resultCode = ResultCode.SUCCESS;
        return new Object[] { new LDAPWrite() {
            @Override
            public void perform(LDAPWriter<? extends ASN1Writer> writer) throws IOException {
                writer.writeDeleteResult(MESSAGE_ID, Responses.newResult(resultCode));
            }
        }, new AbstractLDAPMessageHandler() {
            @Override
            public void deleteResult(int messageID, Result result) throws DecodeException,
                    IOException {
                assertThat(result.getResultCode()).isEqualTo(resultCode);
            }
        } };
    }

    Object[] extendedRequest() {
        final int requestID = 1;
        final String oidCancel = CancelExtendedRequest.OID;
        return new Object[] { new LDAPWrite() {
            @Override
            public void perform(LDAPWriter<? extends ASN1Writer> writer) throws IOException {
                writer.writeExtendedRequest(MESSAGE_ID, Requests
                        .newCancelExtendedRequest(requestID));
            }
        }, new AbstractLDAPMessageHandler() {
            @Override
            public <R extends ExtendedResult> void extendedRequest(int messageID,
                    ExtendedRequest<R> request) throws DecodeException, IOException {
                CancelExtendedRequest cancelRequest =
                        CancelExtendedRequest.DECODER.decodeExtendedRequest(request,
                            Options.defaultOptions().get(LDAP_DECODE_OPTIONS));
                assertThat(cancelRequest.getOID()).isEqualTo(oidCancel);
                assertThat(cancelRequest.getRequestID()).isEqualTo(requestID);
            }
        } };
    }

    Object[] extendedResult() {
        final ResultCode resultCode = ResultCode.SUCCESS;
        final String oidCancel = CancelExtendedRequest.OID;
        return new Object[] { new LDAPWrite() {
            @Override
            public void perform(LDAPWriter<? extends ASN1Writer> writer) throws IOException {
                writer.writeExtendedResult(MESSAGE_ID, Responses.newGenericExtendedResult(
                        resultCode).setOID(oidCancel));
            }
        }, new AbstractLDAPMessageHandler() {
            @Override
            public void extendedResult(int messageID, ExtendedResult result)
                    throws DecodeException, IOException {
                assertThat(result.getResultCode()).isEqualTo(resultCode);
                assertThat(result.getOID()).isEqualTo(oidCancel);
            }
        } };
    }

    Object[] intermediateResponse() {
        final String oid = "1.2.3";
        final String responseValue = "value";
        return new Object[] { new LDAPWrite() {
            @Override
            public void perform(LDAPWriter<? extends ASN1Writer> writer) throws IOException {
                writer.writeIntermediateResponse(MESSAGE_ID, Responses
                        .newGenericIntermediateResponse(oid, responseValue));
            }
        }, new AbstractLDAPMessageHandler() {
            @Override
            public void intermediateResponse(int messageID, IntermediateResponse response)
                    throws DecodeException, IOException {
                assertThat(response.getOID()).isEqualTo(oid);
                assertThat(response.getValue()).isEqualTo(ByteString.valueOfUtf8(responseValue));
            }
        } };
    }

    Object[] modifyDNRequest() {
        final String newRDN = "cn=test2";
        return new Object[] { new LDAPWrite() {
            @Override
            public void perform(LDAPWriter<? extends ASN1Writer> writer) throws IOException {
                writer.writeModifyDNRequest(MESSAGE_ID, Requests
                        .newModifyDNRequest(TEST_DN, newRDN));
            }
        }, new AbstractLDAPMessageHandler() {
            @Override
            public void modifyDNRequest(int messageID, ModifyDNRequest request)
                    throws DecodeException, IOException {
                assertThat(request.getName().toString()).isEqualTo(TEST_DN);
                assertThat(request.getNewRDN().toString()).isEqualTo(newRDN);
            }
        } };
    }

    Object[] modifyDNResult() {
        final ResultCode resultCode = ResultCode.SUCCESS;
        return new Object[] { new LDAPWrite() {
            @Override
            public void perform(LDAPWriter<? extends ASN1Writer> writer) throws IOException {
                writer.writeModifyDNResult(MESSAGE_ID, Responses.newResult(resultCode));
            }
        }, new AbstractLDAPMessageHandler() {
            @Override
            public void modifyDNResult(int messageID, Result result) throws DecodeException,
                    IOException {
                assertThat(result.getResultCode()).isEqualTo(resultCode);
            }
        } };
    }

    Object[] modifyRequest() {
        return new Object[] { new LDAPWrite() {
            @Override
            public void perform(LDAPWriter<? extends ASN1Writer> writer) throws IOException {
                writer.writeModifyRequest(MESSAGE_ID, Requests.newModifyRequest(TEST_DN));
            }
        }, new AbstractLDAPMessageHandler() {
            @Override
            public void modifyRequest(int messageID, ModifyRequest request) throws DecodeException,
                    IOException {
                assertThat(request.getName().toString()).isEqualTo(TEST_DN);
            }
        } };
    }

    Object[] modifyResult() {
        final ResultCode resultCode = ResultCode.SUCCESS;
        return new Object[] { new LDAPWrite() {
            @Override
            public void perform(LDAPWriter<? extends ASN1Writer> writer) throws IOException {
                writer.writeModifyResult(MESSAGE_ID, Responses.newResult(resultCode));
            }
        }, new AbstractLDAPMessageHandler() {
            @Override
            public void modifyResult(int messageID, Result result) throws DecodeException,
                    IOException {
                assertThat(result.getResultCode()).isEqualTo(resultCode);
            }
        } };
    }

    Object[] searchRequest() {
        final SearchScope scope = SearchScope.BASE_OBJECT;
        final String filter = "(&(objectClass=person)(objectClass=user))";
        final String attribute = "cn";
        return new Object[] { new LDAPWrite() {
            @Override
            public void perform(LDAPWriter<? extends ASN1Writer> writer) throws IOException {
                writer.writeSearchRequest(MESSAGE_ID, Requests.newSearchRequest(TEST_DN, scope,
                        filter, attribute));
            }
        }, new AbstractLDAPMessageHandler() {
            @Override
            public void searchRequest(int messageID, SearchRequest request) throws DecodeException,
                    IOException {
                assertThat(request.getName().toString()).isEqualTo(TEST_DN);
                assertThat(request.getScope()).isEqualTo(scope);
                assertThat(request.getFilter().toString()).isEqualTo(filter);
                assertThat(request.getAttributes()).containsExactly(attribute);
            }
        } };
    }

    Object[] searchResult() {
        final ResultCode resultCode = ResultCode.SUCCESS;
        return new Object[] { new LDAPWrite() {
            @Override
            public void perform(LDAPWriter<? extends ASN1Writer> writer) throws IOException {
                writer.writeSearchResult(MESSAGE_ID, Responses.newResult(resultCode));
            }
        }, new AbstractLDAPMessageHandler() {
            @Override
            public void searchResult(int messageID, Result result) throws DecodeException,
                    IOException {
                assertThat(result.getResultCode()).isEqualTo(resultCode);
            }
        } };
    }

    Object[] searchResultEntry() {
        final Entry entry =
                new LinkedHashMapEntry("dn: cn=test", "objectClass: top", "objectClass: test");
        return new Object[] { new LDAPWrite() {
            @Override
            public void perform(LDAPWriter<? extends ASN1Writer> writer) throws IOException {
                writer.writeSearchResultEntry(1, Responses.newSearchResultEntry(entry));
            }
        }, new AbstractLDAPMessageHandler() {
            @Override
            public void searchResultEntry(int messageID, SearchResultEntry resultEntry)
                    throws DecodeException, IOException {
                assertThat(resultEntry).isEqualTo(entry);
            }
        } };
    }

    Object[] searchResultReference() {
        final String uri = "ldap://ldap.example.com/cn=test??sub?(sn=Jensen)";
        return new Object[] { new LDAPWrite() {
            @Override
            public void perform(LDAPWriter<? extends ASN1Writer> writer) throws IOException {
                writer.writeSearchResultReference(MESSAGE_ID, Responses
                        .newSearchResultReference(uri));
            }
        }, new AbstractLDAPMessageHandler() {
            @Override
            public void searchResultReference(int messageID, SearchResultReference reference)
                    throws DecodeException, IOException {
                assertThat(reference.getURIs()).containsExactly(uri);
            }
        } };
    }

    Object[] unbindRequest() {
        return new Object[] { new LDAPWrite() {
            @Override
            public void perform(LDAPWriter<? extends ASN1Writer> writer) throws IOException {
                writer.writeUnbindRequest(MESSAGE_ID, Requests.newUnbindRequest());
            }
        }, new AbstractLDAPMessageHandler() {
            @Override
            public void unbindRequest(int messageID, UnbindRequest request) throws DecodeException,
                    IOException {
                assertThat(request).isNotNull();
            }
        } };
    }

    Object[] unrecognizedMessage() {
        final byte messageTag = 0x01;
        final ByteString messageBytes = ByteString.valueOfUtf8("message");
        return new Object[] { new LDAPWrite() {
            @Override
            public void perform(LDAPWriter<? extends ASN1Writer> writer) throws IOException {
                writer.writeUnrecognizedMessage(MESSAGE_ID, messageTag, messageBytes);
            }
        }, new AbstractLDAPMessageHandler() {
            @Override
            public void unrecognizedMessage(int messageID, byte tag, ByteString message)
                    throws DecodeException, IOException {
                assertThat(messageID).isEqualTo(MESSAGE_ID);
                assertThat(tag).isEqualTo(messageTag);
                assertThat(message).isEqualTo(messageBytes);
            }
        } };
    }

    /**
     * Test that a LDAP message written by LDAPWriter is read correctly using
     * LDAPReader.
     *
     * @param writing
     *            write instruction to perform
     * @param messageHandler
     *            handler of message read, containing assertion(s) to check that
     *            message is as expected
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
    protected abstract LDAPWriter<? extends ASN1Writer> getLDAPWriter();

    /**
     * Returns a reader specific to the transport module.
     */
    protected abstract LDAPReader<? extends ASN1Reader> getLDAPReader();

    /**
     * Transfer raw data from writer to the reader.
     */
    protected abstract void transferFromWriterToReader(LDAPWriter<? extends ASN1Writer> writer,
            LDAPReader<? extends ASN1Reader> reader);
}
