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
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions copyright 2011-2013 ForgeRock AS
 */

package org.forgerock.opendj.ldap.requests;

import static com.forgerock.opendj.util.StaticUtils.EMPTY_BYTES;
import static com.forgerock.opendj.util.StaticUtils.getBytes;
import static org.testng.Assert.assertEquals;

import java.util.Arrays;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Tests Digest MD5 SASL requests.
 */
@SuppressWarnings("javadoc")
public class DigestMD5SASLBindRequestTestCase extends BindRequestTestCase {
    @DataProvider(name = "DigestMD5SASLBindRequests")
    public Object[][] getDigestMD5SASLBindRequests() throws Exception {
        return getTestRequests();
    }

    @Override
    protected DigestMD5SASLBindRequest[] createTestRequests() throws Exception {
        return new DigestMD5SASLBindRequest[] {
                Requests.newDigestMD5SASLBindRequest("id1", EMPTY_BYTES),
                Requests.newDigestMD5SASLBindRequest("id2", getBytes("password"))
        };
    }

    @Test(dataProvider = "DigestMD5SASLBindRequests")
    public void testQOP(DigestMD5SASLBindRequest request) throws Exception {
        String[] options =
                new String[] { DigestMD5SASLBindRequest.QOP_AUTH,
                    DigestMD5SASLBindRequest.QOP_AUTH_INT, DigestMD5SASLBindRequest.QOP_AUTH_CONF };
        request.addQOP(options);
        assertEquals(request.getQOPs(), Arrays.asList(options));
    }

    @Test(dataProvider = "DigestMD5SASLBindRequests")
    public void testStrength(DigestMD5SASLBindRequest request) throws Exception {
        request.setCipher(DigestMD5SASLBindRequest.CIPHER_3DES);
        assertEquals(request.getCipher(), DigestMD5SASLBindRequest.CIPHER_3DES);

        request.setCipher(DigestMD5SASLBindRequest.CIPHER_MEDIUM);
        assertEquals(request.getCipher(), DigestMD5SASLBindRequest.CIPHER_MEDIUM);
    }

    @Test(dataProvider = "DigestMD5SASLBindRequests")
    public void testServerAuth(DigestMD5SASLBindRequest request) throws Exception {
        request.setServerAuth(true);
        assertEquals(request.isServerAuth(), true);
    }

    @Test(dataProvider = "DigestMD5SASLBindRequests")
    public void testSendBuffer(DigestMD5SASLBindRequest request) throws Exception {
        request.setMaxSendBufferSize(1024);
        assertEquals(request.getMaxSendBufferSize(), 1024);
    }

    @Test(dataProvider = "DigestMD5SASLBindRequests")
    public void testRecieveBuffer(DigestMD5SASLBindRequest request) throws Exception {
        request.setMaxReceiveBufferSize(1024);
        assertEquals(request.getMaxReceiveBufferSize(), 1024);
    }
}
