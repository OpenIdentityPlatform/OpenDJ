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
package org.forgerock.opendj.grizzly;

import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.io.LDAPReader;
import org.forgerock.opendj.io.LDAPReaderWriterTestCase;
import org.forgerock.opendj.io.LDAPWriter;
import org.forgerock.util.Options;
import org.glassfish.grizzly.memory.HeapMemoryManager;
import static org.forgerock.opendj.ldap.LDAPConnectionFactory.LDAP_DECODE_OPTIONS;

/**
 * Tests for LDAPWriter / LDAPReader classes using specific implementations of
 * ASN1 writer and ASN1 reader with Grizzly.
 */
public class GrizzlyLDAPReaderWriterTestCase extends LDAPReaderWriterTestCase {

    @Override
    protected LDAPWriter<? extends ASN1Writer> getLDAPWriter() {
        return GrizzlyUtils.getWriter();
    }

    @Override
    protected LDAPReader<? extends ASN1Reader> getLDAPReader() {
        return GrizzlyUtils.createReader(Options.defaultOptions().get(LDAP_DECODE_OPTIONS), 0, new HeapMemoryManager());
    }

    @Override
    protected void transferFromWriterToReader(LDAPWriter<? extends ASN1Writer> writer,
            LDAPReader<? extends ASN1Reader> reader) {
        ASN1BufferReader asn1Reader = (ASN1BufferReader) reader.getASN1Reader();
        ASN1BufferWriter asn1Writer = (ASN1BufferWriter) writer.getASN1Writer();
        asn1Reader.appendBytesRead(asn1Writer.getBuffer());
    }

}
