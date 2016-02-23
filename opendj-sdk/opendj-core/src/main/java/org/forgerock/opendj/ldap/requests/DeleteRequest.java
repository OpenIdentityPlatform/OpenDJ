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
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.ControlDecoder;
import org.forgerock.opendj.ldif.ChangeRecord;
import org.forgerock.opendj.ldif.ChangeRecordVisitor;

/**
 * The Delete operation allows a client to request the removal of an entry from
 * the Directory.
 * <p>
 * Only leaf entries (those with no subordinate entries) can be deleted with
 * this operation. However, addition of the {@code SubtreeDeleteControl} permits
 * whole sub-trees to be deleted using a single Delete request.
 *
 * <pre>
 * Connection connection = ...;
 * String baseDN = ...;
 *
 * DeleteRequest request =
 *         Requests.newDeleteRequest(baseDN)
 *             .addControl(SubtreeDeleteRequestControl.newControl(true));
 * connection.delete(request);
 * </pre>
 */
public interface DeleteRequest extends Request, ChangeRecord {

    @Override
    <R, P> R accept(ChangeRecordVisitor<R, P> v, P p);

    @Override
    DeleteRequest addControl(Control control);

    @Override
    <C extends Control> C getControl(ControlDecoder<C> decoder, DecodeOptions options)
            throws DecodeException;

    @Override
    List<Control> getControls();

    /**
     * Returns the distinguished name of the entry to be deleted. The server
     * shall not dereference any aliases in locating the entry to be deleted.
     *
     * @return The distinguished name of the entry.
     */
    @Override
    DN getName();

    /**
     * Sets the distinguished name of the entry to be deleted. The server shall
     * not dereference any aliases in locating the entry to be deleted.
     *
     * @param dn
     *            The distinguished name of the entry to be deleted.
     * @return This delete request.
     * @throws UnsupportedOperationException
     *             If this delete request does not permit the distinguished name
     *             to be set.
     * @throws NullPointerException
     *             If {@code dn} was {@code null}.
     */
    DeleteRequest setName(DN dn);

    /**
     * Sets the distinguished name of the entry to be deleted. The server shall
     * not dereference any aliases in locating the entry to be deleted.
     *
     * @param dn
     *            The distinguished name of the entry to be deleted.
     * @return This delete request.
     * @throws LocalizedIllegalArgumentException
     *             If {@code dn} could not be decoded using the default schema.
     * @throws UnsupportedOperationException
     *             If this delete request does not permit the distinguished name
     *             to be set.
     * @throws NullPointerException
     *             If {@code dn} was {@code null}.
     */
    DeleteRequest setName(String dn);

}
