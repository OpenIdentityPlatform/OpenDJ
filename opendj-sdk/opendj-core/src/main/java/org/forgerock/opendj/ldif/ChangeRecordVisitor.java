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
 * Copyright 2009 Sun Microsystems, Inc.
 */

package org.forgerock.opendj.ldif;

import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;

/**
 * A visitor of {@code ChangeRecord}s, in the style of the visitor design
 * pattern.
 * <p>
 * Classes implementing this interface can query change records in a type-safe
 * manner. When a visitor is passed to a change record's accept method, the
 * corresponding visit method most applicable to that change record is invoked.
 *
 * @param <R>
 *            The return type of this visitor's methods. Use
 *            {@link java.lang.Void} for visitors that do not need to return
 *            results.
 * @param <P>
 *            The type of the additional parameter to this visitor's methods.
 *            Use {@link java.lang.Void} for visitors that do not need an
 *            additional parameter.
 */
public interface ChangeRecordVisitor<R, P> {

    /**
     * Visits an {@code Add} change record.
     *
     * @param p
     *            A visitor specified parameter.
     * @param change
     *            The {@code Add} change record.
     * @return Returns a visitor specified result.
     */
    R visitChangeRecord(P p, AddRequest change);

    /**
     * Visits an {@code Delete} change record.
     *
     * @param p
     *            A visitor specified parameter.
     * @param change
     *            The {@code Delete} change record.
     * @return Returns a visitor specified result.
     */
    R visitChangeRecord(P p, DeleteRequest change);

    /**
     * Visits an {@code ModifyDN} change record.
     *
     * @param p
     *            A visitor specified parameter.
     * @param change
     *            The {@code ModifyDN} change record.
     * @return Returns a visitor specified result.
     */
    R visitChangeRecord(P p, ModifyDNRequest change);

    /**
     * Visits an {@code Modify} change record.
     *
     * @param p
     *            A visitor specified parameter.
     * @param change
     *            The {@code Modify} change record.
     * @return Returns a visitor specified result.
     */
    R visitChangeRecord(P p, ModifyRequest change);

}
