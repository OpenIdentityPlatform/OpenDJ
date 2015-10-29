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
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.ControlDecoder;

/**
 * The Compare operation allows a client to compare an assertion value with the
 * values of a particular attribute in a particular entry in the Directory.
 * <p>
 * Note that some directory systems may establish access controls that permit
 * the values of certain attributes (such as {@code userPassword} ) to be
 * compared but not interrogated by other means.
 * <p>
 * The following excerpt shows how to use the Compare operation to check whether
 * a member belongs to a (possibly large) static group.
 *
 * <pre>
 * Connection connection = ...;
 * String groupDN = ...;
 * String memberDN = ...;
 *
 * CompareRequest request =
 *          Requests.newCompareRequest(groupDN, "member", memberDN);
 * CompareResult result = connection.compare(request);
 * if (result.matched()) {
 *     // The member belongs to the group.
 * }
 * </pre>
 */
public interface CompareRequest extends Request {

    @Override
    CompareRequest addControl(Control control);

    /**
     * Returns the assertion value to be compared.
     *
     * @return The assertion value.
     */
    ByteString getAssertionValue();

    /**
     * Returns the assertion value to be compared decoded as a UTF-8 string.
     *
     * @return The assertion value decoded as a UTF-8 string.
     */
    String getAssertionValueAsString();

    /**
     * Returns the name of the attribute to be compared.
     *
     * @return The name of the attribute.
     */
    AttributeDescription getAttributeDescription();

    @Override
    <C extends Control> C getControl(ControlDecoder<C> decoder, DecodeOptions options)
            throws DecodeException;

    @Override
    List<Control> getControls();

    /**
     * Returns the distinguished name of the entry to be compared. The server
     * shall not dereference any aliases in locating the entry to be compared.
     *
     * @return The distinguished name of the entry.
     */
    DN getName();

    /**
     * Sets the assertion value to be compared.
     * <p>
     * If the assertion value is not an instance of {@code ByteString} then it
     * will be converted using the {@link ByteString#valueOfObject(Object)} method.
     *
     * @param value
     *            The assertion value to be compared.
     * @return This compare request.
     * @throws UnsupportedOperationException
     *             If this compare request does not permit the assertion value
     *             to be set.
     * @throws NullPointerException
     *             If {@code value} was {@code null}.
     */
    CompareRequest setAssertionValue(Object value);

    /**
     * Sets the name of the attribute to be compared.
     *
     * @param attributeDescription
     *            The name of the attribute to be compared.
     * @return This compare request.
     * @throws UnsupportedOperationException
     *             If this compare request does not permit the attribute
     *             description to be set.
     * @throws NullPointerException
     *             If {@code attributeDescription} was {@code null}.
     */
    CompareRequest setAttributeDescription(AttributeDescription attributeDescription);

    /**
     * Sets the name of the attribute to be compared.
     *
     * @param attributeDescription
     *            The name of the attribute to be compared.
     * @return This compare request.
     * @throws LocalizedIllegalArgumentException
     *             If {@code attributeDescription} could not be decoded using
     *             the default schema.
     * @throws UnsupportedOperationException
     *             If this compare request does not permit the attribute
     *             description to be set.
     * @throws NullPointerException
     *             If {@code attributeDescription} was {@code null}.
     */
    CompareRequest setAttributeDescription(String attributeDescription);

    /**
     * Sets the distinguished name of the entry to be compared. The server shall
     * not dereference any aliases in locating the entry to be compared.
     *
     * @param dn
     *            The distinguished name of the entry to be compared.
     * @return This compare request.
     * @throws UnsupportedOperationException
     *             If this compare request does not permit the distinguished
     *             name to be set.
     * @throws NullPointerException
     *             If {@code dn} was {@code null}.
     */
    CompareRequest setName(DN dn);

    /**
     * Sets the distinguished name of the entry to be compared. The server shall
     * not dereference any aliases in locating the entry to be compared.
     *
     * @param dn
     *            The distinguished name of the entry to be compared.
     * @return This compare request.
     * @throws LocalizedIllegalArgumentException
     *             If {@code dn} could not be decoded using the default schema.
     * @throws UnsupportedOperationException
     *             If this compare request does not permit the distinguished
     *             name to be set.
     * @throws NullPointerException
     *             If {@code dn} was {@code null}.
     */
    CompareRequest setName(String dn);

}
