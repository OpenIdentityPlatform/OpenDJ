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
import org.forgerock.opendj.ldif.ChangeRecord;
import org.forgerock.opendj.ldif.ChangeRecordVisitor;

/**
 * The Add operation allows a client to request the addition of an entry into
 * the Directory.
 * <p>
 * The RDN attribute(s) may or may not be included in the Add request.
 * NO-USER-MODIFICATION attributes such as the {@code createTimestamp} or
 * {@code creatorsName} attributes must not be included, since the server
 * maintains these automatically.
 */
public interface AddRequest extends Request, ChangeRecord, Entry {

    @Override
    <R, P> R accept(ChangeRecordVisitor<R, P> v, P p);

    @Override
    boolean addAttribute(Attribute attribute);

    @Override
    boolean addAttribute(Attribute attribute, Collection<? super ByteString> duplicateValues);

    @Override
    AddRequest addAttribute(String attributeDescription, Object... values);

    @Override
    AddRequest addControl(Control control);

    @Override
    AddRequest clearAttributes();

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
    AddRequest removeAttribute(String attributeDescription, Object... values);

    @Override
    boolean replaceAttribute(Attribute attribute);

    @Override
    AddRequest replaceAttribute(String attributeDescription, Object... values);

    @Override
    AddRequest setName(DN dn);

    @Override
    AddRequest setName(String dn);

}
