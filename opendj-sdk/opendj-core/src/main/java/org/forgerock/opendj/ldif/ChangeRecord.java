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
 * Portions copyright 2012-2016 ForgeRock AS.
 */
package org.forgerock.opendj.ldif;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.requests.Request;

/**
 * A request to modify the content of the Directory in some way. A change record
 * represents one of the following operations:
 * <ul>
 * <li>An {@code Add} operation.
 * <li>An {@code Delete} operation.
 * <li>An {@code Modify} operation.
 * <li>An {@code ModifyDN} operation.
 * </ul>
 */
public interface ChangeRecord extends Request {
    /**
     * Applies a {@code ChangeRecordVisitor} to this {@code ChangeRecord}.
     *
     * @param <R>
     *            The return type of the visitor's methods.
     * @param <P>
     *            The type of the additional parameters to the visitor's
     *            methods.
     * @param v
     *            The change record visitor.
     * @param p
     *            Optional additional visitor parameter.
     * @return A result as specified by the visitor.
     */
    <R, P> R accept(ChangeRecordVisitor<R, P> v, P p);

    /**
     * Returns the distinguished name of the entry being modified by this
     * {@code ChangeRecord}.
     *
     * @return The distinguished name of the entry being modified.
     */
    DN getName();


    /*
     * Uncomment both setName methods when we require JDK7 since JDK6 fails
     * cannot deal with multiple inheritance of covariant return types
     * (AddRequest inherits from both ChangeRecord and Entry).
     *
     * See http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6970851
     */

    /**
     * Sets the distinguished name of the entry to be updated. The server shall
     * not perform any alias dereferencing in determining the object to be
     * updated.
     *
     * @param dn
     *            The the distinguished name of the entry to be updated.
     * @return This change record.
     * @throws UnsupportedOperationException
     *             If this change record does not permit the distinguished name
     *             to be set.
     * @throws NullPointerException
     *             If {@code dn} was {@code null}.
     */
    // ChangeRecord setName(DN dn);

    /**
     * Sets the distinguished name of the entry to be updated. The server shall
     * not perform any alias dereferencing in determining the object to be
     * updated.
     *
     * @param dn
     *            The the distinguished name of the entry to be updated.
     * @return This change record.
     * @throws LocalizedIllegalArgumentException
     *             If {@code dn} could not be decoded using the default schema.
     * @throws UnsupportedOperationException
     *             If this change record does not permit the distinguished name
     *             to be set.
     * @throws NullPointerException
     *             If {@code dn} was {@code null}.
     */
    // ChangeRecord setName(String dn);
}
