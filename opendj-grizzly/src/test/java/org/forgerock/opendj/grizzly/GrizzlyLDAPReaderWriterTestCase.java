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
 * Copyright 2013-2016 ForgeRock AS.
 */
package org.forgerock.opendj.grizzly;

import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.io.LDAP;
import org.forgerock.opendj.io.LDAPReader;
import org.forgerock.opendj.io.LDAPReaderWriterTestCase;
import org.forgerock.opendj.io.LDAPWriter;
import org.forgerock.util.Options;
import org.glassfish.grizzly.memory.HeapMemoryManager;
import org.glassfish.grizzly.memory.MemoryManager;

import static org.forgerock.opendj.ldap.LDAPConnectionFactory.LDAP_DECODE_OPTIONS;

/**
 * Tests for LDAPWriter / LDAPReader classes using specific implementations of
 * ASN1 writer and ASN1 reader with Grizzly.
 */
public class GrizzlyLDAPReaderWriterTestCase extends LDAPReaderWriterTestCase {

    @Override
    protected LDAPWriter<? extends ASN1Writer> getLDAPWriter() {
        return GrizzlyUtils.getWriter(MemoryManager.DEFAULT_MEMORY_MANAGER, 3);
    }

    @Override
    protected LDAPReader<? extends ASN1Reader> getLDAPReader() {
        return GrizzlyUtils.createReader(Options.defaultOptions().get(LDAP_DECODE_OPTIONS), 0, new HeapMemoryManager());
    }

    @Override
    protected LDAPReader<? extends ASN1Reader> getLDAPReader(LDAPWriter<? extends ASN1Writer> writer) {
        return LDAP.<ASN1BufferReader> getReader(
                new ASN1BufferReader(0, ((ASN1BufferWriter) writer.getASN1Writer()).getBuffer()),
                Options.defaultOptions().get(LDAP_DECODE_OPTIONS));
    }
}
