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
 *      Copyright 2013 ForgeRock AS.
 */
package org.forgerock.opendj.adapter.server2x;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DereferenceAliasesPolicy;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.LinkedAttribute;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.GenericControl;
import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.opends.server.protocols.asn1.ASN1;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ByteStringBuilder;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.LDAPException;
import org.opends.server.types.ModificationType;
import org.opends.server.types.Operation;
import org.opends.server.types.SearchResultReference;
import org.opends.server.types.SearchScope;

/**
 * Common utility methods.
 */
final class StaticUtils {

    // Prevent instantiation.
    private StaticUtils() {
        throw new AssertionError();
    }

    static final org.opends.server.types.DereferencePolicy to(
            final DereferenceAliasesPolicy dereferenceAliasesPolicy) {
        return DereferencePolicy.values()[dereferenceAliasesPolicy.intValue()];
    }

    static final org.opends.server.types.DN to(final DN userDn) {
        try {
            return org.opends.server.types.DN.decode(userDn.toString());
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage());
        }

    }

    static final org.opends.server.types.ByteString to(ByteString value) {
        if (value != null) {
            return org.opends.server.types.ByteString.wrap(value.toByteArray());
        }
        return null;
    }

    static final org.opends.server.types.SearchScope to(
            final org.forgerock.opendj.ldap.SearchScope searchScope) {
        return SearchScope.values()[searchScope.intValue()];
    }

    static final org.opends.server.types.RawFilter to(final org.forgerock.opendj.ldap.Filter filter) {
        org.opends.server.protocols.ldap.LDAPFilter ldapFilter = null;
        try {
            ldapFilter = LDAPFilter.decode(filter.toString());
        } catch (LDAPException e) {
            throw new IllegalStateException(e);
        }
        return ldapFilter;
    }

    static final org.opends.server.types.SearchResultReference to(
            final org.forgerock.opendj.ldap.responses.SearchResultReference searchResultReference) {
        return new SearchResultReference(searchResultReference.getURIs(), to(searchResultReference
                .getControls()));
    }

    static final org.opends.server.types.ByteString to(final String value) {
        return org.opends.server.types.ByteString.valueOf(value);
    }

    static final org.opends.server.protocols.ldap.LDAPControl to(final Control control) {
        return new LDAPControl(control.getOID(), control.isCritical(), to(control.getValue()));
    }

    static final List<org.opends.server.types.Control> to(
            final List<org.forgerock.opendj.ldap.controls.Control> listOfControl) {
        List<org.opends.server.types.Control> toListofControl =
                new ArrayList<org.opends.server.types.Control>(listOfControl.size());
        for (org.forgerock.opendj.ldap.controls.Control c : listOfControl) {
            toListofControl.add(to(c));
        }
        return toListofControl;
    }

    static final org.opends.server.types.RawAttribute to(
            final org.forgerock.opendj.ldap.Attribute attribute) {
        ArrayList<org.opends.server.types.ByteString> listAttributeValues =
                new ArrayList<org.opends.server.types.ByteString>(attribute.size());

        for (ByteString b : attribute.toArray()) {
            listAttributeValues.add(to(b));
        }

        return new LDAPAttribute(attribute.getAttributeDescriptionAsString(), listAttributeValues);
    }

    static final List<org.opends.server.types.RawAttribute> to(
            final Iterable<org.forgerock.opendj.ldap.Attribute> listOfAttributes) {
        List<org.opends.server.types.RawAttribute> toListofAttributes =
                new ArrayList<org.opends.server.types.RawAttribute>(
                        ((Collection<org.forgerock.opendj.ldap.Attribute>) listOfAttributes).size());
        for (org.forgerock.opendj.ldap.Attribute a : listOfAttributes) {
            toListofAttributes.add(to(a));
        }
        return toListofAttributes;
    }

    static final org.opends.server.types.RawModification to(
            final org.forgerock.opendj.ldap.Modification modification) {
        return new LDAPModification(to(modification.getModificationType()), to(modification
                .getAttribute()));
    }

    static final List<org.opends.server.types.RawModification> toModifications(
            final List<org.forgerock.opendj.ldap.Modification> listOfModifications) {
        List<org.opends.server.types.RawModification> toListofModifications =
                new ArrayList<org.opends.server.types.RawModification>(listOfModifications.size());
        for (org.forgerock.opendj.ldap.Modification m : listOfModifications) {
            toListofModifications.add(to(m));
        }
        return toListofModifications;
    }

    static final org.opends.server.types.ModificationType to(
            final org.forgerock.opendj.ldap.ModificationType modificationType) {
        return ModificationType.values()[modificationType.intValue()];
    }

    static final ByteString from(final org.opends.server.types.ByteString value) {
        if (value != null) {
            return ByteString.wrap(value.toByteArray());
        }
        return null;
    }

    static final Control from(final org.opends.server.protocols.ldap.LDAPControl ldapControl) {
        return GenericControl.newControl(ldapControl.getOID(), ldapControl.isCritical(),
                from(ldapControl.getValue()));
    }

    static final Control from(final org.opends.server.types.Control control) {

        String oid = null;
        boolean isCritical = false;
        ByteString value = null;
        // The server control doesn't have a method for extracting directly the value so, we need to ASN1 it.
        ByteStringBuilder builder = new ByteStringBuilder();
        final ASN1Writer writer = ASN1.getWriter(builder);
        try {
            control.write(writer);
        } catch (IOException e) {
            // Nothing to do.
        }

        final ByteString sdkByteString = from(builder.toByteString());
        final org.forgerock.opendj.asn1.ASN1Reader sdkReaderASN1 =
                org.forgerock.opendj.asn1.ASN1.getReader(sdkByteString.toByteArray());

        // Reads the ASN1 SDK byte string.
        try {
            sdkReaderASN1.readStartSequence();
            oid = sdkReaderASN1.readOctetStringAsString();
            isCritical = false;
            value = null;
            if (sdkReaderASN1.hasNextElement()
                    && (sdkReaderASN1.peekType() == org.forgerock.opendj.asn1.ASN1.UNIVERSAL_BOOLEAN_TYPE)) {
                isCritical = sdkReaderASN1.readBoolean();
            }
            if (sdkReaderASN1.hasNextElement()
                    && (sdkReaderASN1.peekType() == org.forgerock.opendj.asn1.ASN1.UNIVERSAL_OCTET_STRING_TYPE)) {
                value = sdkReaderASN1.readOctetString();
            }
            sdkReaderASN1.readEndSequence();
        } catch (DecodeException e) {
            // Nothing to do.
        } catch (IOException e) {
            // Nothing to do.
        }
        // Creates the control
        return GenericControl.newControl(oid, isCritical, value);
    }

    static final List<org.forgerock.opendj.ldap.controls.Control> from(
            final List<org.opends.server.types.Control> listOfControl) {
        List<org.forgerock.opendj.ldap.controls.Control> fromListofControl =
                new ArrayList<org.forgerock.opendj.ldap.controls.Control>(listOfControl.size());
        for (org.opends.server.types.Control c : listOfControl) {
            fromListofControl.add(from(c));
        }
        return fromListofControl;
    }

    static final org.forgerock.opendj.ldap.responses.SearchResultReference from(
            final org.opends.server.types.SearchResultReference srvResultReference) {
        return Responses.newSearchResultReference(srvResultReference.getReferralURLString());
    }

    static final org.forgerock.opendj.ldap.Attribute from(
            final org.opends.server.types.Attribute attribute) {
        Attribute sdkAttribute = new LinkedAttribute(attribute.getNameWithOptions());
        for (AttributeValue value : attribute) {
            sdkAttribute.add(value);
        }
        return sdkAttribute;
    }

    static final List<org.forgerock.opendj.ldap.Attribute> from(
            final Iterable<org.opends.server.types.Attribute> listOfAttributes) {
        List<org.forgerock.opendj.ldap.Attribute> fromListofAttributes =
                new ArrayList<org.forgerock.opendj.ldap.Attribute>(
                        ((Collection<org.opends.server.types.Attribute>) listOfAttributes).size());
        for (org.opends.server.types.Attribute a : listOfAttributes) {
            fromListofAttributes.add(from(a));
        }
        return fromListofAttributes;
    }

    static final org.forgerock.opendj.ldap.responses.SearchResultEntry from(
            final org.opends.server.types.SearchResultEntry srvResultEntry) {

        final SearchResultEntry searchResultEntry =
                Responses.newSearchResultEntry(srvResultEntry.getDN().toString());
        if (srvResultEntry.getAttributes() != null) {
            for (org.opends.server.types.Attribute a : srvResultEntry.getAttributes()) {
                searchResultEntry.addAttribute(from(a));
            }
        }

        if (srvResultEntry.getControls() != null) {
            for (org.opends.server.types.Control c : srvResultEntry.getControls()) {
                searchResultEntry.addControl(from(c));
            }
        }

        return searchResultEntry;
    }

    static final <T extends Result> T getResponseResult(final Operation operation, final T result)
            throws ErrorResultException {
        if (operation.getReferralURLs() != null) {
            for (String ref : operation.getReferralURLs()) {
                result.addReferralURI(ref);
            }
        }
        if (operation.getResponseControls() != null) {
            for (org.opends.server.types.Control c : operation.getResponseControls()) {
                result.addControl(from(c));
            }
        }
        result.setDiagnosticMessage((operation.getErrorMessage() != null ? operation
                .getErrorMessage().toString() : null));
        result.setMatchedDN((operation.getMatchedDN() != null) ? operation.getMatchedDN()
                .toString() : null);
        if (result.isSuccess()) {
            return result;
        } else {
            throw ErrorResultException.newErrorResult(result);
        }
    }

    static final Result getResponseResult(final Operation operation) throws ErrorResultException {
        Result result = Responses.newResult(getResultCode(operation));
        return getResponseResult(operation, result);
    }

    static final ResultCode getResultCode(final Operation operation) {
        return ResultCode.valueOf(operation.getResultCode().getIntValue());
    }

    static final org.opends.server.types.ByteString getCredentials(final byte[] authenticationValue) {
        final org.opends.server.protocols.asn1.ASN1Reader reader =
                ASN1.getReader(authenticationValue);
        org.opends.server.types.ByteString saslCred = org.opends.server.types.ByteString.empty();
        try {
            reader.readOctetStringAsString(); // Reads SASL Mechanism - RFC 4511 4.2
            if (reader.hasNextElement()) {
                saslCred = reader.readOctetString(); // Reads credentials.
            }
        } catch (ASN1Exception e) {
            // Nothing to do.
        }

        return saslCred.toByteString();
    }

}
