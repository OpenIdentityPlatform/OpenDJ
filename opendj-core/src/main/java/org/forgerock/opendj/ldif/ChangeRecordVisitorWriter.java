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
 * Portions Copyright 2016 ForgeRock AS.
 */

package org.forgerock.opendj.ldif;

import java.io.IOException;

import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;

/**
 * A visitor which can be used to write generic change records.
 */
final class ChangeRecordVisitorWriter implements
        ChangeRecordVisitor<IOException, ChangeRecordWriter> {
    /** Visitor used for writing generic change records. */
    private static final ChangeRecordVisitorWriter VISITOR = new ChangeRecordVisitorWriter();

    /**
     * Returns the singleton instance.
     *
     * @return The instance.
     */
    static ChangeRecordVisitorWriter getInstance() {
        return VISITOR;
    }

    private ChangeRecordVisitorWriter() {
        // Nothing to do.
    }

    @Override
    public IOException visitChangeRecord(final ChangeRecordWriter p, final AddRequest change) {
        try {
            p.writeChangeRecord(change);
            return null;
        } catch (final IOException e) {
            return e;
        }
    }

    @Override
    public IOException visitChangeRecord(final ChangeRecordWriter p, final DeleteRequest change) {
        try {
            p.writeChangeRecord(change);
            return null;
        } catch (final IOException e) {
            return e;
        }
    }

    @Override
    public IOException visitChangeRecord(final ChangeRecordWriter p, final ModifyDNRequest change) {
        try {
            p.writeChangeRecord(change);
            return null;
        } catch (final IOException e) {
            return e;
        }
    }

    @Override
    public IOException visitChangeRecord(final ChangeRecordWriter p, final ModifyRequest change) {
        try {
            p.writeChangeRecord(change);
            return null;
        } catch (final IOException e) {
            return e;
        }
    }
}
