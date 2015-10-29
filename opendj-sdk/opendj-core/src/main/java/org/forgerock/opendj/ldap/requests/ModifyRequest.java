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
 *      Portions copyright 2012-2015 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.requests;

import java.util.List;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.Modification;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.ControlDecoder;
import org.forgerock.opendj.ldif.ChangeRecord;
import org.forgerock.opendj.ldif.ChangeRecordVisitor;

/**
 * The Modify operation allows a client to request that a modification of an
 * entry be performed on its behalf by a server.
 * <p>
 * The following example adds a member to a static group entry.
 *
 * <pre>
 * Connection connection = ...;
 * String groupDN = ...;
 * String memberDN = ...;
 *
 * ModifyRequest addMember = Requests.newModifyRequest(groupDN)
 *         .addModification(ModificationType.ADD, "member", memberDN);
 * connection.modify(addMember);
 * </pre>
 */
public interface ModifyRequest extends Request, ChangeRecord {

    @Override
    <R, P> R accept(ChangeRecordVisitor<R, P> v, P p);

    @Override
    ModifyRequest addControl(Control control);

    /**
     * Appends the provided modification to the list of modifications included
     * with this modify request.
     *
     * @param modification
     *            The modification to be performed.
     * @return This modify request.
     * @throws UnsupportedOperationException
     *             If this modify request does not permit modifications to be
     *             added.
     * @throws NullPointerException
     *             If {@code modification} was {@code null}.
     */
    ModifyRequest addModification(Modification modification);

    /**
     * Appends the provided modification to the list of modifications included
     * with this modify request.
     * <p>
     * If the attribute value is not an instance of {@code ByteString} then it
     * will be converted using the {@link ByteString#valueOfObject(Object)} method.
     *
     * @param type
     *            The type of modification to be performed.
     * @param attributeDescription
     *            The name of the attribute to be modified.
     * @param values
     *            The attribute values to be modified.
     * @return This modify request.
     * @throws LocalizedIllegalArgumentException
     *             If {@code attributeDescription} could not be decoded using
     *             the default schema.
     * @throws UnsupportedOperationException
     *             If this modify request does not permit modifications to be
     *             added.
     * @throws NullPointerException
     *             If {@code type}, {@code attributeDescription}, or
     *             {@code value} was {@code null}.
     */
    ModifyRequest addModification(ModificationType type, String attributeDescription,
            Object... values);

    @Override
    <C extends Control> C getControl(ControlDecoder<C> decoder, DecodeOptions options)
            throws DecodeException;

    @Override
    List<Control> getControls();

    /**
     * Returns a {@code List} containing the modifications included with this
     * modify request. The returned {@code List} may be modified if permitted by
     * this modify request.
     *
     * @return A {@code List} containing the modifications.
     */
    List<Modification> getModifications();

    /**
     * Returns the distinguished name of the entry to be modified. The server
     * shall not perform any alias dereferencing in determining the object to be
     * modified.
     *
     * @return The distinguished name of the entry to be modified.
     */
    @Override
    DN getName();

    /**
     * Sets the distinguished name of the entry to be modified. The server shall
     * not perform any alias dereferencing in determining the object to be
     * modified.
     *
     * @param dn
     *            The the distinguished name of the entry to be modified.
     * @return This modify request.
     * @throws UnsupportedOperationException
     *             If this modify request does not permit the distinguished name
     *             to be set.
     * @throws NullPointerException
     *             If {@code dn} was {@code null}.
     */
    ModifyRequest setName(DN dn);

    /**
     * Sets the distinguished name of the entry to be modified. The server shall
     * not perform any alias dereferencing in determining the object to be
     * modified.
     *
     * @param dn
     *            The the distinguished name of the entry to be modified.
     * @return This modify request.
     * @throws LocalizedIllegalArgumentException
     *             If {@code dn} could not be decoded using the default schema.
     * @throws UnsupportedOperationException
     *             If this modify request does not permit the distinguished name
     *             to be set.
     * @throws NullPointerException
     *             If {@code dn} was {@code null}.
     */
    ModifyRequest setName(String dn);

}
