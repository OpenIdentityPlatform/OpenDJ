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
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions copyright 2012 ForgeRock AS
 */

package org.forgerock.opendj.ldif;

import java.util.List;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.ControlDecoder;
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

    /** {@inheritDoc} */
    Request addControl(Control control);

    /** {@inheritDoc} */
    <C extends Control> C getControl(ControlDecoder<C> decoder, DecodeOptions options)
            throws DecodeException;

    /** {@inheritDoc} */
    List<Control> getControls();
}
