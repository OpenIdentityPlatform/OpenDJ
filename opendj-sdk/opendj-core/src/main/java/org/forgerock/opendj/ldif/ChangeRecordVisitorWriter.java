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

    public IOException visitChangeRecord(final ChangeRecordWriter p, final AddRequest change) {
        try {
            p.writeChangeRecord(change);
            return null;
        } catch (final IOException e) {
            return e;
        }
    }

    public IOException visitChangeRecord(final ChangeRecordWriter p, final DeleteRequest change) {
        try {
            p.writeChangeRecord(change);
            return null;
        } catch (final IOException e) {
            return e;
        }
    }

    public IOException visitChangeRecord(final ChangeRecordWriter p, final ModifyDNRequest change) {
        try {
            p.writeChangeRecord(change);
            return null;
        } catch (final IOException e) {
            return e;
        }
    }

    public IOException visitChangeRecord(final ChangeRecordWriter p, final ModifyRequest change) {
        try {
            p.writeChangeRecord(change);
            return null;
        } catch (final IOException e) {
            return e;
        }
    }
}
