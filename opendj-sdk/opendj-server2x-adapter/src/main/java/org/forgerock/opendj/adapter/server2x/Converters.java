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
import org.forgerock.opendj.ldap.RDN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.GenericControl;
import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.opends.server.core.BindOperation;
import org.opends.server.core.CompareOperation;
import org.opends.server.core.ExtendedOperation;
import org.opends.server.protocols.asn1.ASN1;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ByteStringBuilder;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.LDAPException;
import org.opends.server.types.Operation;

/**
 * Common utility methods.
 */
public final class Converters {

    // Prevent instantiation.
    private Converters() {
        throw new AssertionError();
    }

    /**
     * Converts from OpenDJ LDAP SDK {@link org.forgerock.opendj.ldap.responses.SearchResultEntry} to OpenDJ
     * server {@link org.opends.server.types.SearchResultEntry}.
     *
     * @param value
     *          value to convert
     * @return the converted value
     */
    public static org.opends.server.types.SearchResultEntry to(
            final org.forgerock.opendj.ldap.responses.SearchResultEntry value) {
        if (value != null) {
            org.opends.server.types.Entry entry =
                new org.opends.server.types.Entry(to(value.getName()), null, null, null);
            org.opends.server.types.SearchResultEntry searchResultEntry =
                new org.opends.server.types.SearchResultEntry(entry, to(value.getControls()));
            List<AttributeValue> duplicateValues = new ArrayList<AttributeValue>();
            for (org.opends.server.types.Attribute attribute : toAttributes(value.getAllAttributes())) {
                searchResultEntry.addAttribute(attribute, duplicateValues);
            }
            return searchResultEntry;
        }
        return null;
    }

    /**
     * Converts from OpenDJ LDAP SDK {@link DereferenceAliasesPolicy} to OpenDJ
     * server {@link DereferencePolicy}.
     *
     * @param dereferenceAliasesPolicy
     *          value to convert
     * @return the converted value
     */
    public static org.opends.server.types.DereferencePolicy to(
            final DereferenceAliasesPolicy dereferenceAliasesPolicy) {
        return DereferencePolicy.values()[dereferenceAliasesPolicy.intValue()];
    }

    /**
     * Converts from OpenDJ LDAP SDK {@link DN} to OpenDJ server
     * {@link org.opends.server.types.DN}.
     *
     * @param dn
     *          value to convert
     * @return the converted value
     */
    public static org.opends.server.types.DN to(final DN dn) {
        try {
            return org.opends.server.types.DN.decode(dn.toString());
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage());
        }
    }

    /**
     * Converts from OpenDJ LDAP SDK {@link RDN} to OpenDJ server
     * {@link org.opends.server.types.RDN}.
     *
     * @param rdn
     *          value to convert
     * @return the converted value
     */
    public static org.opends.server.types.RDN to(final RDN rdn) {
        try {
            return org.opends.server.types.RDN.decode(rdn.toString());
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage());
        }
    }

    /**
     * Converts from OpenDJ LDAP SDK {@link ByteString} to OpenDJ server
     * {@link org.opends.server.types.ByteString}.
     *
     * @param value
     *          value to convert
     * @return the converted value
     */
    public static org.opends.server.types.ByteString to(final ByteString value) {
        if (value != null) {
            return org.opends.server.types.ByteString.wrap(value.toByteArray());
        }
        return null;
    }

    /**
     * Converts from OpenDJ LDAP SDK {@link org.forgerock.opendj.ldap.SearchScope}
     * to OpenDJ server {@link org.opends.server.types.SearchScope}.
     *
     * @param searchScope
     *          value to convert
     * @return the converted value
     */
    public static org.opends.server.types.SearchScope to(
            final org.forgerock.opendj.ldap.SearchScope searchScope) {
        if (searchScope == null) {
            return null;
        }
        return org.opends.server.types.SearchScope.values()[searchScope.intValue()];
    }

    /**
     * Converts from OpenDJ LDAP SDK {@link org.forgerock.opendj.ldap.Filter} to
     * OpenDJ server {@link org.opends.server.types.RawFilter}.
     *
     * @param filter
     *          value to convert
     * @return the converted value
     */
    public static org.opends.server.types.RawFilter to(final org.forgerock.opendj.ldap.Filter filter) {
        org.opends.server.protocols.ldap.LDAPFilter ldapFilter = null;
        try {
            ldapFilter = LDAPFilter.decode(filter.toString());
        } catch (LDAPException e) {
            throw new IllegalStateException(e);
        }
        return ldapFilter;
    }

    /**
     * Converts from OpenDJ LDAP SDK
     * {@link org.forgerock.opendj.ldap.responses.SearchResultReference} to OpenDJ
     * server {@link org.opends.server.types.SearchResultReference}.
     *
     * @param searchResultReference
     *          value to convert
     * @return the converted value
     */
    public static org.opends.server.types.SearchResultReference to(
            final org.forgerock.opendj.ldap.responses.SearchResultReference searchResultReference) {
        return new org.opends.server.types.SearchResultReference(
                searchResultReference.getURIs(), to(searchResultReference.getControls()));
    }

    /**
     * Converts from OpenDJ LDAP SDK {@link String} to OpenDJ server
     * {@link org.opends.server.types.ByteString}.
     *
     * @param value
     *          value to convert
     * @return the converted value
     */
    public static org.opends.server.types.ByteString to(final String value) {
        return org.opends.server.types.ByteString.valueOf(value);
    }

    /**
     * Converts from OpenDJ LDAP SDK {@link Control} to OpenDJ server
     * {@link org.opends.server.protocols.ldap.LDAPControl}.
     *
     * @param control
     *          value to convert
     * @return the converted value
     */
    public static org.opends.server.protocols.ldap.LDAPControl to(final Control control) {
        return new LDAPControl(control.getOID(), control.isCritical(), to(control.getValue()));
    }

    /**
     * Converts from a <code>List</code> of OpenDJ LDAP SDK
     * {@link org.forgerock.opendj.ldap.controls.Control} to a <code>List</code>
     * of OpenDJ server {@link org.opends.server.types.Control}.
     *
     * @param listOfControl
     *          value to convert
     * @return the converted value
     */
    public static List<org.opends.server.types.Control> to(
            final List<org.forgerock.opendj.ldap.controls.Control> listOfControl) {
        List<org.opends.server.types.Control> toListOfControl =
                new ArrayList<org.opends.server.types.Control>(listOfControl.size());
        for (org.forgerock.opendj.ldap.controls.Control c : listOfControl) {
            toListOfControl.add(to(c));
        }
        return toListOfControl;
    }

    /**
     * Converts from OpenDJ LDAP SDK {@link org.forgerock.opendj.ldap.Attribute}
     * to OpenDJ server {@link org.opends.server.types.RawAttribute}.
     *
     * @param attribute
     *          value to convert
     * @return the converted value
     */
    public static org.opends.server.types.RawAttribute to(
            final org.forgerock.opendj.ldap.Attribute attribute) {
        ArrayList<org.opends.server.types.ByteString> listAttributeValues =
                new ArrayList<org.opends.server.types.ByteString>(attribute.size());

        for (ByteString b : attribute.toArray()) {
            listAttributeValues.add(to(b));
        }

        return new LDAPAttribute(attribute.getAttributeDescriptionAsString(), listAttributeValues);
    }

    /**
     * Converts from an <code>Iterable</code> of OpenDJ LDAP SDK
     * {@link org.forgerock.opendj.ldap.Attribute} to a <code>List</code> of
     * OpenDJ server {@link org.opends.server.types.RawAttribute}.
     *
     * @param listOfAttributes
     *          value to convert
     * @return the converted value
     */
    public static List<org.opends.server.types.RawAttribute> to(
            final Iterable<org.forgerock.opendj.ldap.Attribute> listOfAttributes) {
        List<org.opends.server.types.RawAttribute> toListOfAttributes =
                new ArrayList<org.opends.server.types.RawAttribute>(
                        ((Collection<org.forgerock.opendj.ldap.Attribute>) listOfAttributes).size());
        for (org.forgerock.opendj.ldap.Attribute a : listOfAttributes) {
            toListOfAttributes.add(to(a));
        }
        return toListOfAttributes;
    }

    /**
     * Converts from OpenDJ LDAP SDK
     * {@link org.forgerock.opendj.ldap.Modification} to OpenDJ server
     * {@link org.opends.server.types.RawModification}.
     *
     * @param modification
     *          value to convert
     * @return the converted value
     */
    public static org.opends.server.types.RawModification to(
            final org.forgerock.opendj.ldap.Modification modification) {
        return new LDAPModification(to(modification.getModificationType()), to(modification
                .getAttribute()));
    }

    /**
     * Converts from a <code>List</code> of OpenDJ LDAP SDK
     * {@link org.forgerock.opendj.ldap.Modification} to a <code>List</code> of
     * OpenDJ server {@link org.opends.server.types.RawModification}.
     *
     * @param listOfModifications
     *          value to convert
     * @return the converted value
     */
    public static List<org.opends.server.types.RawModification> toRawModifications(
            final List<org.forgerock.opendj.ldap.Modification> listOfModifications) {
        List<org.opends.server.types.RawModification> toListOfModifications =
                new ArrayList<org.opends.server.types.RawModification>(listOfModifications.size());
        for (org.forgerock.opendj.ldap.Modification m : listOfModifications) {
            toListOfModifications.add(to(m));
        }
        return toListOfModifications;
    }

    /**
     * Converts from OpenDJ LDAP SDK {@link org.forgerock.opendj.ldap.Attribute}
     * to OpenDJ server {@link org.opends.server.types.Attribute}.
     *
     * @param attribute
     *          value to convert
     * @return the converted value
     */
    public static org.opends.server.types.Attribute toAttribute(
            final org.forgerock.opendj.ldap.Attribute attribute) {
        final AttributeBuilder attrBuilder =
            new AttributeBuilder(attribute.getAttributeDescriptionAsString());
        for (ByteString b : attribute.toArray()) {
            attrBuilder.add(to(b));
        }
        return attrBuilder.toAttribute();
    }

    /**
     * Converts from an <code>Iterable</code> of OpenDJ LDAP SDK
     * {@link org.forgerock.opendj.ldap.Attribute} to a <code>List</code> of
     * OpenDJ server {@link org.opends.server.types.RawAttribute}.
     *
     * @param listOfAttributes
     *          value to convert
     * @return the converted value
     */
    public static List<org.opends.server.types.Attribute> toAttributes(
            final Iterable<org.forgerock.opendj.ldap.Attribute> listOfAttributes) {
        List<org.opends.server.types.Attribute> toListOfAttributes =
                new ArrayList<org.opends.server.types.Attribute>(
                        ((Collection<org.forgerock.opendj.ldap.Attribute>) listOfAttributes).size());
        for (org.forgerock.opendj.ldap.Attribute a : listOfAttributes) {
            toListOfAttributes.add(toAttribute(a));
        }
        return toListOfAttributes;
    }

    /**
     * Converts from OpenDJ LDAP SDK
     * {@link org.forgerock.opendj.ldap.Modification} to OpenDJ server
     * {@link org.opends.server.types.Modification}.
     *
     * @param modification
     *          value to convert
     * @return the converted value
     */
    public static org.opends.server.types.Modification toModification(
            final org.forgerock.opendj.ldap.Modification modification) {
        return new org.opends.server.types.Modification(to(modification.getModificationType()),
            toAttribute(modification.getAttribute()));
    }

    /**
     * Converts from a <code>List</code> of OpenDJ LDAP SDK
     * {@link org.forgerock.opendj.ldap.Modification} to a <code>List</code> of
     * OpenDJ server {@link org.opends.server.types.Modification}.
     *
     * @param listOfModifications
     *          value to convert
     * @return the converted value
     */
    public static List<org.opends.server.types.Modification> toModifications(
            final List<org.forgerock.opendj.ldap.Modification> listOfModifications) {
        List<org.opends.server.types.Modification> toListOfModifications =
                new ArrayList<org.opends.server.types.Modification>(
                        listOfModifications.size());
        for (org.forgerock.opendj.ldap.Modification m : listOfModifications) {
            toListOfModifications.add(toModification(m));
        }
        return toListOfModifications;
    }

    /**
     * Converts from OpenDJ LDAP SDK
     * {@link org.forgerock.opendj.ldap.ModificationType} to OpenDJ server
     * {@link org.opends.server.types.ModificationType}.
     *
     * @param modificationType
     *          value to convert
     * @return the converted value
     */
    public static org.opends.server.types.ModificationType to(
            final org.forgerock.opendj.ldap.ModificationType modificationType) {
        return org.opends.server.types.ModificationType.values()[modificationType.intValue()];
    }

    /**
     * Converts from OpenDJ server {@link org.opends.server.types.ByteString} to
     * OpenDJ LDAP SDK {@link ByteString}.
     *
     * @param value
     *          value to convert
     * @return the converted value
     */
    public static ByteString from(final org.opends.server.types.ByteString value) {
        if (value != null) {
            return ByteString.wrap(value.toByteArray());
        }
        return null;
    }

    /**
     * Converts from OpenDJ server {@link org.opends.server.types.SearchScope}. to
     * OpenDJ LDAP SDK {@link org.forgerock.opendj.ldap.SearchScope}.
     *
     * @param searchScope
     *          value to convert
     * @return the converted value
     */
    public static org.forgerock.opendj.ldap.SearchScope from(
            final org.opends.server.types.SearchScope searchScope) {
        if (searchScope == null) {
            return null;
        }
        return org.forgerock.opendj.ldap.SearchScope.values().get(searchScope.intValue());
    }

    /**
     * Converts from OpenDJ server
     * {@link org.opends.server.protocols.ldap.LDAPControl} to OpenDJ LDAP SDK
     * {@link Control}.
     *
     * @param ldapControl
     *          value to convert
     * @return the converted value
     */
    public static Control from(final org.opends.server.protocols.ldap.LDAPControl ldapControl) {
        return GenericControl.newControl(ldapControl.getOID(), ldapControl.isCritical(),
                from(ldapControl.getValue()));
    }

    /**
     * Converts from OpenDJ server {@link org.opends.server.types.Control} to
     * OpenDJ LDAP SDK {@link Control}.
     *
     * @param control
     *          value to convert
     * @return the converted value
     */
    public static Control from(final org.opends.server.types.Control control) {

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

    /**
     * Converts from a <code>List</code> of OpenDJ server
     * {@link org.opends.server.types.Control} to a <code>List</code> of OpenDJ
     * LDAP SDK {@link org.forgerock.opendj.ldap.controls.Control}.
     *
     * @param listOfControl
     *          value to convert
     * @return the converted value
     */
    public static List<org.forgerock.opendj.ldap.controls.Control> from(
            final List<org.opends.server.types.Control> listOfControl) {
        List<org.forgerock.opendj.ldap.controls.Control> fromListofControl =
                new ArrayList<org.forgerock.opendj.ldap.controls.Control>(listOfControl.size());
        for (org.opends.server.types.Control c : listOfControl) {
            fromListofControl.add(from(c));
        }
        return fromListofControl;
    }

    /**
     * Converts from OpenDJ server
     * {@link org.opends.server.types.SearchResultReference} to OpenDJ LDAP SDK
     * {@link org.forgerock.opendj.ldap.responses.SearchResultReference}.
     *
     * @param srvResultReference
     *          value to convert
     * @return the converted value
     */
    public static org.forgerock.opendj.ldap.responses.SearchResultReference from(
            final org.opends.server.types.SearchResultReference srvResultReference) {
        return Responses.newSearchResultReference(srvResultReference.getReferralURLString());
    }

    /**
     * Converts from OpenDJ server {@link org.opends.server.types.Attribute} to
     * OpenDJ LDAP SDK {@link org.forgerock.opendj.ldap.Attribute}.
     *
     * @param attribute
     *          value to convert
     * @return the converted value
     */
    public static org.forgerock.opendj.ldap.Attribute from(
            final org.opends.server.types.Attribute attribute) {
        Attribute sdkAttribute = new LinkedAttribute(attribute.getNameWithOptions());
        for (AttributeValue value : attribute) {
            sdkAttribute.add(from(value.getValue()));
        }
        return sdkAttribute;
    }

    /**
     * Converts from an <code>Iterable</code> of OpenDJ server
     * {@link org.opends.server.types.Attribute} to a <code>List</code> of OpenDJ
     * LDAP SDK {@link org.forgerock.opendj.ldap.Attribute}.
     *
     * @param listOfAttributes
     *          value to convert
     * @return the converted value
     */
    public static List<org.forgerock.opendj.ldap.Attribute> from(
            final Iterable<org.opends.server.types.Attribute> listOfAttributes) {
        List<org.forgerock.opendj.ldap.Attribute> fromListofAttributes =
                new ArrayList<org.forgerock.opendj.ldap.Attribute>(
                        ((Collection<org.opends.server.types.Attribute>) listOfAttributes).size());
        for (org.opends.server.types.Attribute a : listOfAttributes) {
            fromListofAttributes.add(from(a));
        }
        return fromListofAttributes;
    }

    /**
     * Converts from OpenDJ server
     * {@link org.opends.server.types.SearchResultEntry} to OpenDJ LDAP SDK
     * {@link org.forgerock.opendj.ldap.responses.SearchResultEntry}.
     *
     * @param srvResultEntry
     *          value to convert
     * @return the converted value
     */
    public static org.forgerock.opendj.ldap.responses.SearchResultEntry from(
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

    /**
     * Populates the result object with the operation details and return the
     * result object if it was successful. Otherwise, it throws an
     * {@link ErrorResultException}.
     *
     * @param <T>
     *          the type of the result object
     * @param operation
     *          used to populate the result
     * @param result
     *          the result object to populate from the Operation
     * @return the result if successful, an {@link ErrorResultException} is thrown
     *         otherwise
     * @throws ErrorResultException
     *           when an error occurs
     */
    public static <T extends Result> T getResponseResult(final Operation operation, final T result)
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

    /**
     * Converts the OpenDJ server {@link Operation} object into an OpenDJ LDAP SDK
     * {@link Result} object.
     *
     * @param operation
     *          value to convert
     * @return the converted value
     * @throws ErrorResultException
     *           when an error occurs
     */
    public static Result getResponseResult(final Operation operation) throws ErrorResultException {
        return getResponseResult(operation, newSDKResult(operation));
    }

    private static Result newSDKResult(final Operation operation) {
        ResultCode rc = getResultCode(operation);
        if (operation instanceof BindOperation) {
            return Responses.newBindResult(rc);
        } else if (operation instanceof CompareOperation) {
            return Responses.newCompareResult(rc);
        } else if (operation instanceof ExtendedOperation) {
            return Responses.newGenericExtendedResult(rc);
        }
        return Responses.newResult(rc);
    }

    /**
     * Returns the OpenDJ LDAP SDK {@link ResultCode} extracted out of the OpenDJ
     * server {@link Operation}.
     *
     * @param operation
     *          value to convert
     * @return the converted value
     */
    public static ResultCode getResultCode(final Operation operation) {
        return ResultCode.valueOf(operation.getResultCode().getIntValue());
    }

    /**
     * Converts from <code>byte[]</code> to OpenDJ server
     * {@link org.opends.server.types.ByteString}.
     *
     * @param authenticationValue
     *          value to convert
     * @return the converted value
     */
    public static org.opends.server.types.ByteString getCredentials(final byte[] authenticationValue) {
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
