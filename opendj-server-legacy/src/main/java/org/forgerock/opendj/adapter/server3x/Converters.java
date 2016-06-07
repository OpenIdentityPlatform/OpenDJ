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
 * Copyright 2013-2016 ForgeRock AS.
 */
package org.forgerock.opendj.adapter.server3x;

import static com.forgerock.opendj.ldap.CoreMessages.*;
import static com.forgerock.opendj.util.StaticUtils.*;

import static org.forgerock.opendj.ldap.LdapException.*;
import static org.opends.server.extensions.ExtensionsConstants.*;
import static org.opends.server.util.CollectionUtils.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LinkedAttribute;
import org.forgerock.opendj.ldap.LinkedHashMapEntry;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.GenericControl;
import org.forgerock.opendj.ldap.responses.PasswordModifyExtendedResult;
import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.server.config.meta.VirtualAttributeCfgDefn;
import org.opends.server.core.BindOperation;
import org.opends.server.core.CompareOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ExtendedOperation;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.LDAPException;
import org.opends.server.types.Operation;
import org.opends.server.types.SearchFilter;
import org.opends.server.util.ServerConstants;

/** Common utility methods. */
public final class Converters {

    /** Prevent instantiation. */
    private Converters() {
        throw new AssertionError();
    }

    /**
     * Converts from OpenDJ LDAP SDK {@link org.forgerock.opendj.ldap.Entry} to OpenDJ
     * server {@link org.opends.server.types.Entry}.
     *
     * @param sdkEntry
     *          SDK entry to convert
     * @return the converted entry
     */
    public static org.opends.server.types.Entry to(
            final org.forgerock.opendj.ldap.Entry sdkEntry) {
        if (sdkEntry != null) {
            org.opends.server.types.Entry entry =
                new org.opends.server.types.Entry(sdkEntry.getName(), null, null, null);
            List<ByteString> duplicateValues = new ArrayList<>();
            for (org.opends.server.types.Attribute attribute : toAttributes(sdkEntry.getAllAttributes())) {
                if (attribute.getAttributeDescription().getAttributeType().isObjectClass()) {
                    for (ByteString attrName : attribute) {
                        try {
                            entry.addObjectClass(DirectoryServer.getSchema().getObjectClass(attrName.toString()));
                        } catch (DirectoryException e) {
                            throw new IllegalStateException(e.getMessage(), e);
                        }
                    }
                } else {
                    entry.addAttribute(attribute, duplicateValues);
                }
            }
            return entry;
        }
        return null;
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
                new org.opends.server.types.Entry(value.getName(), null, null, null);
            org.opends.server.types.SearchResultEntry searchResultEntry =
                new org.opends.server.types.SearchResultEntry(entry, to(value.getControls()));
            List<ByteString> duplicateValues = new ArrayList<>();
            for (org.opends.server.types.Attribute attribute : toAttributes(value.getAllAttributes())) {
                searchResultEntry.addAttribute(attribute, duplicateValues);
            }
            return searchResultEntry;
        }
        return null;
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
        try {
            return LDAPFilter.decode(filter.toString());
        } catch (LDAPException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Converts from OpenDJ LDAP SDK {@link org.forgerock.opendj.ldap.Filter} to
     * OpenDJ server {@link org.opends.server.types.RawFilter}.
     *
     * @param filter
     *          value to convert
     * @return the converted value
     */
    public static SearchFilter toSearchFilter(final org.forgerock.opendj.ldap.Filter filter) {
        try {
            return SearchFilter.createFilterFromString(filter.toString());
        } catch (DirectoryException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
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
     * Converts from OpenDJ LDAP SDK {@link Control} to OpenDJ server
     * {@link org.opends.server.protocols.ldap.LDAPControl}.
     *
     * @param control
     *          value to convert
     * @return the converted value
     */
    public static org.opends.server.protocols.ldap.LDAPControl to(final Control control) {
        return new LDAPControl(control.getOID(), control.isCritical(), control.getValue());
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
        List<org.opends.server.types.Control> toListOfControl = new ArrayList<>(listOfControl.size());
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
        ArrayList<ByteString> listAttributeValues = newArrayList(attribute.toArray());
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
                new ArrayList<>(((Collection<?>) listOfAttributes).size());
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
        return new LDAPModification(modification.getModificationType(), to(modification
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
                new ArrayList<>(listOfModifications.size());
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
            attrBuilder.add(b);
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
        List<org.opends.server.types.Attribute> toListOfAttributes = new ArrayList<>();
        Iterator<Attribute> it = listOfAttributes.iterator();
        while (it.hasNext())
        {
          toListOfAttributes.add(toAttribute(it.next()));
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
        return new org.opends.server.types.Modification(modification.getModificationType(),
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
        List<org.opends.server.types.Modification> toListOfModifications = new ArrayList<>(listOfModifications.size());
        for (org.forgerock.opendj.ldap.Modification m : listOfModifications) {
            toListOfModifications.add(toModification(m));
        }
        return toListOfModifications;
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
                ldapControl.getValue());
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

        final ByteString sdkByteString = builder.toByteString();
        final org.forgerock.opendj.io.ASN1Reader sdkReaderASN1 =
                org.forgerock.opendj.io.ASN1.getReader(sdkByteString.toByteArray());

        // Reads the ASN1 SDK byte string.
        try {
            sdkReaderASN1.readStartSequence();
            oid = sdkReaderASN1.readOctetStringAsString();
            if (sdkReaderASN1.hasNextElement()
                    && sdkReaderASN1.peekType() == ASN1.UNIVERSAL_BOOLEAN_TYPE) {
                isCritical = sdkReaderASN1.readBoolean();
            }
            if (sdkReaderASN1.hasNextElement()
                    && sdkReaderASN1.peekType() == ASN1.UNIVERSAL_OCTET_STRING_TYPE) {
                value = sdkReaderASN1.readOctetString();
            }
            sdkReaderASN1.readEndSequence();
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
        List<org.forgerock.opendj.ldap.controls.Control> fromListofControl = new ArrayList<>(listOfControl.size());
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
        Attribute sdkAttribute = new LinkedAttribute(attribute.getAttributeDescription());
        for (ByteString value : attribute) {
            sdkAttribute.add(value);
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
                new ArrayList<>(((Collection<?>) listOfAttributes).size());
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
                Responses.newSearchResultEntry(srvResultEntry.getName().toString());
        for (org.opends.server.types.Attribute a : srvResultEntry.getAttributes()) {
            searchResultEntry.addAttribute(from(a));
        }
        for (org.opends.server.types.Control c : srvResultEntry.getControls()) {
            searchResultEntry.addControl(from(c));
        }
        return searchResultEntry;
    }

    /**
     * Converts from OpenDJ server
     * {@link org.opends.server.types.Entry} to OpenDJ LDAP SDK
     * {@link org.forgerock.opendj.ldap.Entry}.
     *
     * @param srvResultEntry
     *          value to convert
     * @return the converted value
     */
    public static org.forgerock.opendj.ldap.Entry from(
        final org.opends.server.types.Entry srvResultEntry) {

        final org.forgerock.opendj.ldap.Entry entry = new LinkedHashMapEntry(srvResultEntry.getName().toString());
        entry.addAttribute(from(srvResultEntry.getObjectClassAttribute()));
        for (org.opends.server.types.Attribute a : srvResultEntry.getAttributes()) {
            entry.addAttribute(from(a));
        }
        return entry;
    }

    /**
     * Converts from OpenDJ server
     * {@link org.forgerock.opendj.server.config.meta.VirtualAttributeCfgDefn.Scope}
     *  to OpenDJ LDAP SDK {@link org.forgerock.opendj.ldap.SearchScope}.
     *
     * @param srvScope
     *          The server scope value.
     * @return The SDK scope value.
     */
    public static SearchScope from(VirtualAttributeCfgDefn.Scope srvScope) {
        if (srvScope != null) {
            switch (srvScope) {
            case BASE_OBJECT:
                return SearchScope.BASE_OBJECT;
            case SINGLE_LEVEL:
                return SearchScope.SINGLE_LEVEL;
            case SUBORDINATE_SUBTREE:
                return SearchScope.SUBORDINATES;
            case WHOLE_SUBTREE:
                return SearchScope.WHOLE_SUBTREE;
            default:
                return null;
            }
        }
        return null;
    }

    /**
     * Populates the result object with the operation details and return the
     * result object if it was successful. Otherwise, it throws an
     * {@link LdapException}.
     *
     * @param <T>
     *          the type of the result object
     * @param operation
     *          used to populate the result
     * @param result
     *          the result object to populate from the Operation
     * @return the result if successful, an {@link LdapException} is thrown
     *         otherwise
     * @throws LdapException
     *           when an error occurs
     */
    public static <T extends Result> T getResponseResult(final Operation operation, final T result)
            throws LdapException {
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
        final LocalizableMessageBuilder errorMsg = operation.getErrorMessage();
        final DN matchedDN = operation.getMatchedDN();
        result.setDiagnosticMessage(errorMsg != null ? errorMsg.toString() : null);
        result.setMatchedDN(matchedDN != null ? matchedDN.toString() : null);
        if (result.isSuccess()) {
            return result;
        } else {
            throw newLdapException(result);
        }
    }

    /**
     * Converts the OpenDJ server {@link Operation} object into an OpenDJ LDAP SDK
     * {@link Result} object.
     *
     * @param operation
     *          value to convert
     * @return the converted value
     * @throws LdapException
     *           when an error occurs
     */
    public static Result getResponseResult(final Operation operation) throws LdapException {
        return getResponseResult(operation, newSDKResult(operation));
    }

    private static Result newSDKResult(final Operation operation) throws LdapException {
        ResultCode rc = operation.getResultCode();
        if (operation instanceof BindOperation) {
            return Responses.newBindResult(rc);
        } else if (operation instanceof CompareOperation) {
            return Responses.newCompareResult(rc);
        } else if (operation instanceof ExtendedOperation) {
            ExtendedOperation extendedOperation = (ExtendedOperation) operation;
            switch (extendedOperation.getRequestOID()) {
            case ServerConstants.OID_PASSWORD_MODIFY_REQUEST:
                PasswordModifyExtendedResult result = Responses.newPasswordModifyExtendedResult(rc);
                ByteString generatedPwd = getGeneratedPassword(extendedOperation);
                if (generatedPwd != null) {
                    result.setGeneratedPassword(generatedPwd.toByteArray());
                }
                return result;

            default:
                return Responses.newGenericExtendedResult(rc);
            }
        }
        return Responses.newResult(rc);
    }

    private static ByteString getGeneratedPassword(ExtendedOperation op) throws LdapException {
        // FIXME this code is duplicated with code in the SDK
        // see PasswordModifyExtendedRequestImpl#ResultDecoder#decodeExtendedResult()
        ByteString responseValue = op.getResponseValue();
        if (responseValue != null) {
            try {
                ASN1Reader reader = ASN1.getReader(responseValue);
                reader.readStartSequence();
                return reader.readOctetString(TYPE_PASSWORD_MODIFY_GENERATED_PASSWORD);
            } catch (IOException e) {
                throw LdapException.newLdapException(ResultCode.PROTOCOL_ERROR,
                        ERR_EXTOP_PASSMOD_CANNOT_DECODE_REQUEST.get(getExceptionMessage(e)), e);
            }
        }
        return null;
    }

    /**
     * Converts from <code>byte[]</code> to OpenDJ server {@link ByteString}.
     *
     * @param authenticationValue
     *          value to convert
     * @return the converted value
     */
    public static ByteString getCredentials(final byte[] authenticationValue) {
        final ASN1Reader reader = ASN1.getReader(authenticationValue);
        try {
            reader.readOctetStringAsString(); // Reads SASL Mechanism - RFC 4511 4.2
            if (reader.hasNextElement()) {
                return reader.readOctetString().toByteString();
            }
        } catch (IOException e) {
            // Nothing to do.
        }
        return ByteString.empty();
    }
}
