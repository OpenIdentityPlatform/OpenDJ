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
