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

package org.forgerock.opendj.ldap.responses;

import java.util.Collection;
import java.util.List;

import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.ControlDecoder;

/**
 * A Search Result Entry represents an entry found during a Search operation.
 * <p>
 * Each entry returned in a Search Result Entry will contain all appropriate
 * attributes as specified in the Search request, subject to access control and
 * other administrative policy.
 * <p>
 * Note that a Search Result Entry may hold zero attributes. This may happen
 * when none of the attributes of an entry were requested or could be returned.
 * <p>
 * Note also that each returned attribute may hold zero attribute values. This
 * may happen when only attribute types are requested, access controls prevent
 * the return of values, or other reasons.
 */
public interface SearchResultEntry extends Response, Entry {

    @Override
    boolean addAttribute(Attribute attribute);

    @Override
    boolean addAttribute(Attribute attribute, Collection<? super ByteString> duplicateValues);

    @Override
    SearchResultEntry addAttribute(String attributeDescription, Object... values);

    @Override
    SearchResultEntry addControl(Control control);

    @Override
    SearchResultEntry clearAttributes();

    @Override
    boolean containsAttribute(Attribute attribute, Collection<? super ByteString> missingValues);

    @Override
    boolean containsAttribute(String attributeDescription, Object... values);

    @Override
    Iterable<Attribute> getAllAttributes();

    @Override
    Iterable<Attribute> getAllAttributes(AttributeDescription attributeDescription);

    @Override
    Iterable<Attribute> getAllAttributes(String attributeDescription);

    @Override
    Attribute getAttribute(AttributeDescription attributeDescription);

    @Override
    Attribute getAttribute(String attributeDescription);

    @Override
    int getAttributeCount();

    @Override
    <C extends Control> C getControl(ControlDecoder<C> decoder, DecodeOptions options)
            throws DecodeException;

    @Override
    List<Control> getControls();

    @Override
    DN getName();

    @Override
    boolean removeAttribute(Attribute attribute, Collection<? super ByteString> missingValues);

    @Override
    boolean removeAttribute(AttributeDescription attributeDescription);

    @Override
    SearchResultEntry removeAttribute(String attributeDescription, Object... values);

    @Override
    boolean replaceAttribute(Attribute attribute);

    @Override
    SearchResultEntry replaceAttribute(String attributeDescription, Object... values);

    @Override
    SearchResultEntry setName(DN dn);

    @Override
    SearchResultEntry setName(String dn);

}
