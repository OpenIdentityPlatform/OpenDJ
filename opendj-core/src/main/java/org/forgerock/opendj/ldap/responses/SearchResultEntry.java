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
