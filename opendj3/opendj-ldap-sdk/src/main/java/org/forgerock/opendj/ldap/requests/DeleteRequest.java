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
 *      Portions copyright 2012 ForgeRock AS.
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
