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
 * Portions copyright 2012 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.requests;

import java.util.List;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.RDN;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.ControlDecoder;
import org.forgerock.opendj.ldif.ChangeRecord;
import org.forgerock.opendj.ldif.ChangeRecordVisitor;

/**
 * The Modify DN operation allows a client to change the Relative Distinguished
 * Name (RDN) of an entry in the Directory and/or to move a subtree of entries
 * to a new location in the Directory.
 */
public interface ModifyDNRequest extends Request, ChangeRecord {

    @Override
    <R, P> R accept(ChangeRecordVisitor<R, P> v, P p);

    @Override
    ModifyDNRequest addControl(Control control);

    @Override
    <C extends Control> C getControl(ControlDecoder<C> decoder, DecodeOptions options)
            throws DecodeException;

    @Override
    List<Control> getControls();

    /**
     * Returns the distinguished name of the entry to be renamed. This entry may
     * or may not have subordinate entries. The server shall not dereference any
     * aliases in locating the entry to be renamed.
     *
     * @return The distinguished name of the entry.
     */
    @Override
    DN getName();

    /**
     * Returns the new RDN of the entry to be renamed. The value of the old RDN
     * is supplied when moving the entry to a new superior without changing its
     * RDN. Attribute values of the new RDN not matching any attribute value of
     * the entry are added to the entry, and an appropriate error is returned if
     * this fails.
     *
     * @return The new RDN of the entry.
     */
    RDN getNewRDN();

    /**
     * Returns the distinguished name of an existing entry that will become the
     * immediate superior (parent) of the entry to be renamed. The server shall
     * not dereference any aliases in locating the new superior entry. The
     * default value is {@code null}, indicating that the entry is to remain
     * under the same parent entry.
     *
     * @return The distinguished name of the new superior entry, or {@code null}
     *         if the entry is to remain under the same parent entry.
     */
    DN getNewSuperior();

    /**
     * Indicates whether the old RDN attribute values are to be retained as
     * attributes of the entry or deleted from the entry. The default value is
     * {@code false}.
     *
     * @return {@code true} if the old RDN attribute values are to be deleted
     *         from the entry, or {@code false} if they are to be retained.
     */
    boolean isDeleteOldRDN();

    /**
     * Specifies whether the old RDN attribute values are to be retained as
     * attributes of the entry or deleted from the entry. The default value is
     * {@code false}.
     *
     * @param deleteOldRDN
     *            {@code true} if the old RDN attribute values are to be deleted
     *            from the entry, or {@code false} if they are to be retained.
     * @return This modify DN request.
     * @throws UnsupportedOperationException
     *             If this modify DN request does not permit the delete old RDN
     *             parameter to be set.
     */
    ModifyDNRequest setDeleteOldRDN(boolean deleteOldRDN);

    /**
     * Sets the distinguished name of the entry to be renamed. This entry may or
     * may not have subordinate entries. The server shall not dereference any
     * aliases in locating the entry to be renamed.
     *
     * @param dn
     *            The distinguished name of the entry to be renamed.
     * @return This modify DN request.
     * @throws UnsupportedOperationException
     *             If this modify DN request does not permit the distinguished
     *             name to be set.
     * @throws NullPointerException
     *             If {@code dn} was {@code null}.
     */
    ModifyDNRequest setName(DN dn);

    /**
     * Sets the distinguished name of the entry to be renamed. This entry may or
     * may not have subordinate entries. The server shall not dereference any
     * aliases in locating the entry to be renamed.
     *
     * @param dn
     *            The distinguished name of the entry to be renamed.
     * @return This modify DN request.
     * @throws LocalizedIllegalArgumentException
     *             If {@code dn} could not be decoded using the default schema.
     * @throws UnsupportedOperationException
     *             If this modify DN request does not permit the distinguished
     *             name to be set.
     * @throws NullPointerException
     *             If {@code dn} was {@code null}.
     */
    ModifyDNRequest setName(String dn);

    /**
     * Sets the new RDN of the entry to be renamed. The value of the old RDN is
     * supplied when moving the entry to a new superior without changing its
     * RDN. Attribute values of the new RDN not matching any attribute value of
     * the entry are added to the entry, and an appropriate error is returned if
     * this fails.
     *
     * @param rdn
     *            The new RDN of the entry to be renamed.
     * @return This modify DN request.
     * @throws UnsupportedOperationException
     *             If this modify DN request does not permit the new RDN to be
     *             set.
     * @throws NullPointerException
     *             If {@code rdn} was {@code null}.
     */
    ModifyDNRequest setNewRDN(RDN rdn);

    /**
     * Sets the new RDN of the entry to be renamed. The value of the old RDN is
     * supplied when moving the entry to a new superior without changing its
     * RDN. Attribute values of the new RDN not matching any attribute value of
     * the entry are added to the entry, and an appropriate error is returned if
     * this fails.
     *
     * @param rdn
     *            The new RDN of the entry to be renamed.
     * @return This modify DN request.
     * @throws LocalizedIllegalArgumentException
     *             If {@code rdn} could not be decoded using the default schema.
     * @throws UnsupportedOperationException
     *             If this modify DN request does not permit the new RDN to be
     *             set.
     * @throws NullPointerException
     *             If {@code rdn} was {@code null}.
     */
    ModifyDNRequest setNewRDN(String rdn);

    /**
     * Sets the distinguished name of an existing entry that will become the
     * immediate superior (parent) of the entry to be renamed. The server shall
     * not dereference any aliases in locating the new superior entry. The
     * default value is {@code null}, indicating that the entry is to remain
     * under the same parent entry.
     *
     * @param dn
     *            The distinguished name of an existing entry that will become
     *            the immediate superior (parent) of the entry to be renamed,
     *            may be {@code null}.
     * @return This modify DN request.
     * @throws UnsupportedOperationException
     *             If this modify DN request does not permit the new superior to
     *             be set.
     */
    ModifyDNRequest setNewSuperior(DN dn);

    /**
     * Sets the distinguished name of an existing entry that will become the
     * immediate superior (parent) of the entry to be renamed. The server shall
     * not dereference any aliases in locating the new superior entry. The
     * default value is {@code null}, indicating that the entry is to remain
     * under the same parent entry.
     *
     * @param dn
     *            The distinguished name of an existing entry that will become
     *            the immediate superior (parent) of the entry to be renamed,
     *            may be {@code null}.
     * @return This modify DN request.
     * @throws LocalizedIllegalArgumentException
     *             If {@code dn} could not be decoded using the default schema.
     * @throws UnsupportedOperationException
     *             If this modify DN request does not permit the new superior to
     *             be set.
     */
    ModifyDNRequest setNewSuperior(String dn);

}
