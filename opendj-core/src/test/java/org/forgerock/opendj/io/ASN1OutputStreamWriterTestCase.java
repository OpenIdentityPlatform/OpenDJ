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
 * Copyright 2010 Sun Microsystems, Inc.
 * Portions copyright 2013 ForgeRock AS.
 */
package org.forgerock.opendj.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * Test class for ASN1OutputStreamWriter.
 */
public class ASN1OutputStreamWriterTestCase extends ASN1WriterTestCase {
    private final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
    private final ASN1Writer writer = new ASN1OutputStreamWriter(outStream, 1);

    @Override
    protected byte[] getEncodedBytes() {
        return outStream.toByteArray();
    }

    @Override
    protected ASN1Reader getReader(final byte[] encodedBytes) {
        final ByteArrayInputStream inStream = new ByteArrayInputStream(encodedBytes);
        return new ASN1InputStreamReader(inStream, 0);
    }

    @Override
    protected ASN1Writer getWriter() {
        outStream.reset();
        return writer;
    }
}
