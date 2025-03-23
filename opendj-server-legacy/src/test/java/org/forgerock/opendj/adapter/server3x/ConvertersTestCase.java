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

import static org.assertj.core.api.Assertions.*;
import static org.forgerock.opendj.adapter.server3x.Converters.*;
import static org.forgerock.opendj.ldap.schema.CoreSchema.*;
import static org.mockito.Mockito.*;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.LinkedAttribute;
import org.forgerock.opendj.ldap.LinkedHashMapEntry;
import org.forgerock.opendj.ldap.Modification;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.GenericControl;
import org.forgerock.opendj.ldap.controls.PersistentSearchChangeType;
import org.forgerock.opendj.ldap.controls.PersistentSearchRequestControl;
import org.forgerock.opendj.ldap.controls.PostReadRequestControl;
import org.forgerock.opendj.ldap.controls.ProxiedAuthV2RequestControl;
import org.forgerock.opendj.ldap.requests.BindClient;
import org.forgerock.opendj.ldap.requests.CRAMMD5SASLBindRequest;
import org.forgerock.opendj.ldap.requests.GenericBindRequest;
import org.forgerock.opendj.ldap.requests.PlainSASLBindRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.GenericExtendedResult;
import org.forgerock.opendj.ldap.responses.PasswordModifyExtendedResult;
import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.server.config.meta.VirtualAttributeCfgDefn.Scope;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.BindOperation;
import org.opends.server.core.CompareOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ExtendedOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.FilterType;
import org.opends.server.types.LDAPException;
import org.opends.server.types.Operation;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;
import org.opends.server.util.CollectionUtils;
import org.opends.server.util.ServerConstants;
import org.testng.annotations.BeforeGroups;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * This class defines a set of tests for the StaticUtils.class.
 * <p>
 * Reminder :
 *
 * <pre>
 * to - from SDK to server.
 * from - from server to SDK.
 * </pre>
 */
@SuppressWarnings("javadoc")
@Test(singleThreaded = true)
public class ConvertersTestCase extends DirectoryServerTestCase {

    /**
     * Launched before the tests, this function starts the embedded server.
     *
     * @throws Exception
     *             If the server could not be initialized.
     */
    @BeforeGroups(groups = "needRunningServer")
    public void startServer() throws Exception {
        TestCaseUtils.startServer();
    }

    /**
     * Converts a SDK {@link SearchResultEntry} to an LDAP Server
     * {@link SearchResultEntry}.
     */
    @Test
    public final void testToSearchResultEntry() throws Exception {
        org.forgerock.opendj.ldap.responses.SearchResultEntry entry =
            Responses.newSearchResultEntry(DN.valueOf("uid=scarter,ou=People,dc=example,dc=com"));
        for (Control control : generateSdkControlsList()) {
            entry.addControl(control);
        }
        entry.addAttribute(new LinkedAttribute("test", "value1"));
        entry.addAttribute(new LinkedAttribute("Another", ByteString.valueOfUtf8("myValue")));

        SearchResultEntry result = to(entry);
        assertThat(result.getName().toString()).isEqualTo(entry.getName().toString());
        assertThat(result.getControls()).hasSize(entry.getControls().size());
        assertThat(result.getAllAttributes()).hasSize(2);
    }

    /**
     * Converts a SDK {@link Entry} to an LDAP Server
     * {@link Entry}.
     */
    @Test
    public final void testToEntry() throws Exception {
        org.forgerock.opendj.ldap.Entry entry =
            new LinkedHashMapEntry(DN.valueOf("uid=scarter,ou=People,dc=example,dc=com"));
        entry.addAttribute(new LinkedAttribute("test", "value1"));
        entry.addAttribute(new LinkedAttribute("Another", ByteString.valueOfUtf8("myValue")));

        org.opends.server.types.Entry result = to(entry);
        assertThat(result.getName().toString()).isEqualTo(entry.getName().toString());
        assertThat(result.getAllAttributes()).hasSize(2);
    }

    @Test
    public final void testToEntryDoesNotMixObjectClassAndAttributeTypeOIDs() throws Exception {
        org.forgerock.opendj.ldap.Entry entry =
            new LinkedHashMapEntry(DN.valueOf("uid=scarter,ou=People,dc=example,dc=com"));
        entry.addAttribute(new LinkedAttribute("objectClass", "ds-cfg-backend", "ds-cfg-create-placeholder-for-me"));
        org.opends.server.types.Entry result = to(entry);

        assertThat(result.getName().toString()).isEqualTo(entry.getName().toString());
        List<ObjectClass> ocs = new ArrayList<>(result.getObjectClasses().keySet());
        assertThat(ocs).hasSize(2);
        Schema schema = DirectoryServer.getInstance().getServerContext().getSchema();
        assertThat(ocs.get(0).getOID()).isEqualTo(schema.getObjectClass("ds-cfg-backend").getOID());
        assertThat(ocs.get(1).getOID()).as("This should be a placeholder").endsWith("-oid");
    }

    /**
     * Converts a SDK control to a LDAP server control.
     *
     * @throws DirectoryException
     */
    @Test
    public final void testToControl() throws DirectoryException {
        final PersistentSearchRequestControl control =
                PersistentSearchRequestControl.newControl(false, true,
                        true, // isCritical, changesOnly, returnECs
                        PersistentSearchChangeType.ADD, PersistentSearchChangeType.DELETE,
                        PersistentSearchChangeType.MODIFY, PersistentSearchChangeType.MODIFY_DN);

        // control :
        // PersistentSearchRequestControl(oid=2.16.840.1.113730.3.4.3, criticality=true,
        // changeTypes=[add, delete, modify, modifyDN]([add, delete, modify, modifyDN]),
        // changesOnly=true, returnECs=true)

        org.opends.server.protocols.ldap.LDAPControl srvControl = to(control);
        assertThat(srvControl.isCritical()).isFalse();
        assertThat(srvControl.getOID()).isEqualTo("2.16.840.1.113730.3.4.3");
        assertThat(control.getValue().toString()).isEqualTo(srvControl.getValue().toString());

        // A PostReadRequestControl(SDK)
        final PostReadRequestControl control2 =
                PostReadRequestControl.newControl(true, "description");

        srvControl = to(control2);
        assertThat(srvControl.getOID()).isEqualTo("1.3.6.1.1.13.2");
        assertThat(srvControl.isCritical()).isTrue();
        assertThat(control2.getValue().toString()).isEqualTo(srvControl.getValue().toString());
    }

    /**
     * Converts a list of SDK controls to a list of LDAP server controls.
     *
     * @throws DirectoryException
     */
    @Test
    public final void testToListOfControl() throws DirectoryException {
        List<org.forgerock.opendj.ldap.controls.Control> mySDKControlsList =
            generateSdkControlsList();

        List<org.opends.server.types.Control> listofControl = to(mySDKControlsList);
        assertThat(listofControl.size()).isEqualTo(3);
        assertThat(listofControl.get(0).getOID()).isEqualTo(mySDKControlsList.get(0).getOID());
        assertThat(listofControl.get(0).isCritical()).isFalse();
        assertThat(listofControl.get(1).getOID()).isEqualTo(mySDKControlsList.get(1).getOID());
        assertThat(listofControl.get(1).isCritical()).isTrue();
        assertThat(listofControl.get(2).getOID()).isEqualTo(mySDKControlsList.get(2).getOID());
        assertThat(listofControl.get(2).isCritical()).isTrue();
    }

    private List<org.forgerock.opendj.ldap.controls.Control> generateSdkControlsList() {
        final PersistentSearchRequestControl control =
                PersistentSearchRequestControl.newControl(false, true,
                        true, // isCritical, changesOnly, returnECs
                        PersistentSearchChangeType.ADD, PersistentSearchChangeType.DELETE,
                        PersistentSearchChangeType.MODIFY, PersistentSearchChangeType.MODIFY_DN);

        assertThat(control.getOID()).isEqualTo("2.16.840.1.113730.3.4.3");

        final PostReadRequestControl control2 =
                PostReadRequestControl.newControl(true, "description");
        assertThat(control2.getOID()).isEqualTo("1.3.6.1.1.13.2");

        final ProxiedAuthV2RequestControl control3 =
                ProxiedAuthV2RequestControl
                        .newControl("dn:uid=kvaughan,ou=People,dc=example,dc=com");
        assertThat(control3.getOID()).isEqualTo("2.16.840.1.113730.3.4.18");

        return CollectionUtils.newLinkedList(control, control2, control3);
    }

    /**
     * Converts an SDK attribute to an LDAP server attribute.
     */
    @Test
    public final void testToRawAttribute() throws DirectoryException {
        org.forgerock.opendj.ldap.Attribute attribute = new LinkedAttribute("test", "value1");

        org.opends.server.types.RawAttribute srvAttribute = to(attribute);
        assertThat(srvAttribute.getAttributeType()).isEqualTo("test");
        assertThat(srvAttribute.getValues()).hasSize(1);
        assertThat(srvAttribute.getValues().get(0).toString()).isEqualTo("value1");

        org.forgerock.opendj.ldap.Attribute attribute2 =
                new LinkedAttribute("Another", ByteString.valueOfUtf8("myValue"));

        org.opends.server.types.RawAttribute srvAttribute2 = to(attribute2);
        assertThat(srvAttribute2.getAttributeType()).isEqualTo("Another");
        assertThat(srvAttribute2.getValues()).hasSize(1);
        assertThat(srvAttribute2.getValues().get(0).toString()).isEqualTo("myValue");
    }

    @Test(groups = { "needRunningServer" })
    public final void testToAttribute() throws DirectoryException {
        org.forgerock.opendj.ldap.Attribute attribute = new LinkedAttribute("test", "value1");

        org.opends.server.types.Attribute srvAttribute = toAttribute(attribute);
        assertThat(srvAttribute.getAttributeDescription().getAttributeType().getNameOrOID()).isEqualTo("test");
        assertThat(srvAttribute.size()).isEqualTo(1);
        assertThat(srvAttribute.iterator().next().toString()).isEqualTo("value1");

        org.forgerock.opendj.ldap.Attribute attribute2 =
                new LinkedAttribute("Another", ByteString.valueOfUtf8("myValue"));

        org.opends.server.types.Attribute srvAttribute2 = toAttribute(attribute2);
        assertThat(srvAttribute2.getAttributeDescription().getAttributeType().getNameOrOID()).isEqualTo("Another");
        assertThat(srvAttribute2.size()).isEqualTo(1);
        assertThat(srvAttribute2.iterator().next().toString()).isEqualTo("myValue");
    }

    /**
     * Converts an SDK multi-valued attribute to an LDAP Server Attribute.
     */
    @Test(groups = { "needRunningServer" })
    public final void testToRawAttributeMultiValued() throws DirectoryException {
        org.forgerock.opendj.ldap.Attribute attribute =
                new LinkedAttribute("testMultiValuedAttribute", "value1", "value2");

        org.opends.server.types.RawAttribute srvAttribute = to(attribute);
        assertThat(srvAttribute.getAttributeType())
                .isEqualTo("testMultiValuedAttribute");
        assertThat(srvAttribute.getValues().size()).isEqualTo(2);
        assertThat(srvAttribute.getValues().get(0).toString()).isEqualTo("value1");
        assertThat(srvAttribute.getValues().get(1).toString()).isEqualTo("value2");

        org.forgerock.opendj.ldap.Attribute attribute2 =
                new LinkedAttribute("AnotherMultiValuedAttribute", "value1", "value2", "value3",
                        "value4");

        org.opends.server.types.RawAttribute srvAttribute2 = to(attribute2);
        assertThat(srvAttribute2.getAttributeType()).isEqualTo(
                "AnotherMultiValuedAttribute");
        assertThat(srvAttribute2.getValues().size()).isEqualTo(4);
        assertThat(srvAttribute2.getValues().get(0).toString()).isEqualTo("value1");
        assertThat(srvAttribute2.getValues().get(1).toString()).isEqualTo("value2");
        assertThat(srvAttribute2.getValues().get(2).toString()).isEqualTo("value3");
        assertThat(srvAttribute2.getValues().get(3).toString()).isEqualTo("value4");
    }

    @Test
    public final void testToAttributeMultiValued() throws DirectoryException {
        org.forgerock.opendj.ldap.Attribute attribute =
                new LinkedAttribute("testMultiValuedAttribute", "value1", "value2");

        org.opends.server.types.Attribute srvAttribute = toAttribute(attribute);
        assertThat(srvAttribute.getAttributeDescription().getAttributeType().getNameOrOID())
            .isEqualTo("testMultiValuedAttribute");
        assertThat(srvAttribute.size()).isEqualTo(2);
        Iterator<ByteString> iter = srvAttribute.iterator();
        assertThat(iter.next().toString()).isEqualTo("value1");
        assertThat(iter.next().toString()).isEqualTo("value2");

        org.forgerock.opendj.ldap.Attribute attribute2 =
                new LinkedAttribute("AnotherMultiValuedAttribute", "value1", "value2", "value3",
                        "value4");

        org.opends.server.types.Attribute srvAttribute2 = toAttribute(attribute2);
        assertThat(srvAttribute2.getAttributeDescription().getAttributeType().getNameOrOID())
            .isEqualTo("AnotherMultiValuedAttribute");
        assertThat(srvAttribute2.size()).isEqualTo(4);
        iter = srvAttribute2.iterator();
        assertThat(iter.next().toString()).isEqualTo("value1");
        assertThat(iter.next().toString()).isEqualTo("value2");
        assertThat(iter.next().toString()).isEqualTo("value3");
        assertThat(iter.next().toString()).isEqualTo("value4");
    }

    /**
     * Converts a SDK modification to an LDAP server raw modification.
     */
    @Test
    public final void testToRawModification() {
        org.forgerock.opendj.ldap.Attribute attribute =
                new LinkedAttribute("test", ByteString.valueOfUtf8("value1"), ByteString
                        .valueOfUtf8("value2"));

        Modification mod = new Modification(ModificationType.ADD, attribute);

        org.opends.server.types.RawModification srvModification = to(mod);
        assertThat(srvModification.getModificationType()).isEqualTo(ModificationType.ADD);
        assertThat(srvModification.getAttribute().getAttributeType()).isEqualTo("test");
        assertThat(srvModification.getAttribute().getValues().size()).isEqualTo(2);

        mod = new Modification(ModificationType.INCREMENT, attribute);
        srvModification = to(mod);
        assertThat(srvModification.getModificationType()).isEqualTo(ModificationType.INCREMENT);
    }

    @Test
    public final void testToModification() {
        org.forgerock.opendj.ldap.Attribute attribute =
                new LinkedAttribute("test", ByteString.valueOfUtf8("value1"), ByteString
                        .valueOfUtf8("value2"));

        Modification mod = new Modification(ModificationType.ADD, attribute);

        org.opends.server.types.Modification srvModification = toModification(mod);
        Attribute attr = srvModification.getAttribute();
        assertThat(srvModification.getModificationType()).isEqualTo(ModificationType.ADD);
        assertThat(attr.getAttributeDescription().getAttributeType().getNameOrOID()).isEqualTo("test");
        assertThat(attr.size()).isEqualTo(2);

        mod = new Modification(ModificationType.INCREMENT, attribute);
        srvModification = toModification(mod);
        assertThat(srvModification.getModificationType()).isEqualTo(ModificationType.INCREMENT);
    }

    /**
     * Converts a SDK filter to an LDAP server filter.
     */
    @Test
    public final void testToFilter() throws LDAPException {
        Filter filter = Filter.valueOf("!(description=*)");
        org.opends.server.protocols.ldap.LDAPFilter srvFilter =
                LDAPFilter.decode(filter.toString());
        assertThat(srvFilter.getAttributeType()).isNull();
        assertThat(srvFilter.getFilterType()).isEqualTo(FilterType.NOT);
        assertThat(srvFilter.getNOTComponent().toString()).isEqualTo("(description=*)");

        filter = Filter.valueOf("(description=bjensen)");
        srvFilter = LDAPFilter.decode(filter.toString());
        assertThat(srvFilter.getAttributeType()).isEqualTo("description");
        assertThat(srvFilter.getFilterType()).isEqualTo(FilterType.EQUALITY);
        assertThat(srvFilter.getAssertionValue().toString()).isEqualTo("bjensen");
    }

    /**
     * Converts a SDK search result reference to a LDAP server search result
     * reference.
     */
    @Test
    public final void testToSearchResultReference() throws LDAPException {
        String uri = "ldap://hostb/OU=People,O=MNN,C=WW??sub";
        final org.forgerock.opendj.ldap.responses.SearchResultReference sdkSearchResultReference =
                Responses.newSearchResultReference(uri);

        final org.opends.server.types.SearchResultReference srvResultReference =
                new SearchResultReference(uri);

        final org.opends.server.types.SearchResultReference srvResultReference2 =
                to(sdkSearchResultReference);
        assertThat(srvResultReference.getReferralURLString()).isEqualTo(
                srvResultReference2.getReferralURLString());
    }

    /**
     * Converts an LDAP attribute to an SDK attribute.
     */
    @Test(groups = { "needRunningServer" })
    public final void testFromAttribute() throws DirectoryException {
        final org.opends.server.types.Attribute srvAttribute = Attributes.create("CN", "JOHN DOE");
        final org.forgerock.opendj.ldap.Attribute sdkAttribute = from(srvAttribute);

        assertThat(sdkAttribute.getAttributeDescriptionAsString()).isEqualTo("CN");
        assertThat(sdkAttribute.size()).isEqualTo(1);
        assertThat(sdkAttribute.firstValueAsString()).isEqualTo("JOHN DOE");
    }

    /**
     * Converts an LDAP attribute to an SDK attribute using binary attribute value.
     */
    @Test(groups = { "needRunningServer" })
    public final void testFromAttributeUsingBinary() throws DirectoryException {
        byte[] data = { 0x00, 0x01, 0x02, (byte) 0xff };

        ByteString attrValue = ByteString.wrap(data);
        Attribute attribute = Attributes.create(getCNAttributeType(), attrValue);
        assertThat(from(attribute).firstValue().toByteArray()).isEqualTo(data);
    }

    /**
     * Converts an LDAP control to an SDK control.
     */
    @Test
    public static void testFromLDAPControl() {
        org.opends.server.protocols.ldap.LDAPControl ldapControl =
                new LDAPControl("1.2.3.4", false, ByteString.valueOfUtf8("myData"));
        Control sdkControl = from(ldapControl);

        Control expectedSdkControl = GenericControl.newControl("1.2.3.4", false, "myData");
        assertThat(sdkControl.getOID()).isEqualTo(expectedSdkControl.getOID());
        assertThat(sdkControl.isCritical()).isEqualTo(expectedSdkControl.isCritical());
        assertThat(sdkControl.getValue()).isEqualTo(expectedSdkControl.getValue());
    }

    /**
     * Converts an Scope to an SDK Scope.
     */
    @Test
    public static void testFromScope() {
        // WHOLE SUBTREE
        assertThat(org.forgerock.opendj.ldap.SearchScope.WHOLE_SUBTREE).isEqualTo(from(Scope.WHOLE_SUBTREE));
        // BASE OBJECT
        assertThat(org.forgerock.opendj.ldap.SearchScope.BASE_OBJECT).isEqualTo(from(Scope.BASE_OBJECT));
        // SINGLE LEVEL
        assertThat(org.forgerock.opendj.ldap.SearchScope.SINGLE_LEVEL).isEqualTo(from(Scope.SINGLE_LEVEL));
        // SUBORDINATE
        assertThat(org.forgerock.opendj.ldap.SearchScope.SUBORDINATES).isEqualTo(from(Scope.SUBORDINATE_SUBTREE));
    }

    /**
     * Converts a server control to an SDK control.
     */
    @Test
    public static void testFromControl() {
        final org.opends.server.types.Control control =
                new LDAPControl("1.2.3.4", false, ByteString.valueOfUtf8("myData"));
        Control sdkControl = from(control);

        Control expectedSdkControl = GenericControl.newControl("1.2.3.4", false, "myData");
        assertThat(sdkControl.getOID()).isEqualTo(expectedSdkControl.getOID());
        assertThat(sdkControl.isCritical()).isEqualTo(expectedSdkControl.isCritical());
        assertThat(sdkControl.getValue()).isEqualTo(expectedSdkControl.getValue());
    }

    /**
     * For an SASL bind request, credentials are composed by uid and password
     * (in this config).
     */
    @Test(groups = { "needRunningServer" })
    public static void testgetCredentials() throws Exception {
        final PlainSASLBindRequest request =
                Requests.newPlainSASLBindRequest("u:user.0", "password".toCharArray());

        String serverName = InetAddress.getByName(null).getCanonicalHostName();
        final BindClient bindClient = request.createBindClient(serverName);
        final GenericBindRequest genericBindRequest = bindClient.nextBindRequest();

        assertThat(getCredentials(genericBindRequest.getAuthenticationValue())).isEqualTo(
                ByteString.valueOfUtf8("\u0000u:user.0\u0000password"));
    }

    /**
     * For an CRAMMD5 SALS request, the credentials are empty.
     */
    @Test(groups = { "needRunningServer" })
    public static void testgetCredentialsEmptyByteString() throws Exception {
        final CRAMMD5SASLBindRequest request =
                Requests.newCRAMMD5SASLBindRequest("u:user.2", "password".toCharArray());

        String serverName = InetAddress.getByName(null).getCanonicalHostName();
        final BindClient bindClient = request.createBindClient(serverName);
        final GenericBindRequest genericBindRequest = bindClient.nextBindRequest();

        assertThat(getCredentials(genericBindRequest.getAuthenticationValue())).isEqualTo(
                ByteString.empty());
    }

    @DataProvider
    public Object[][] operationResultTypes() {
        return new Object[][] {
            { BindOperation.class, BindResult.class },
            { CompareOperation.class, CompareResult.class },
            { SearchOperation.class, Result.class },
        };
    }

    /** Tests the type of the result based on the type of the provided operation. */
    @Test(dataProvider = "operationResultTypes")
    public <O extends Operation, R extends Result> void testGetResponseResultAndResultType(
        Class<O> operationType, Class<R> resultType) throws Exception {
        // Given
        O operation = mock(operationType);
        when(operation.getResultCode()).thenReturn(ResultCode.SUCCESS);
        // When
        Result result = getResponseResult(operation);
        // Then
        assertThat(result.getResultCode()).isEqualTo(org.forgerock.opendj.ldap.ResultCode.SUCCESS);
        assertThat(result).isInstanceOf(resultType);
    }

    @DataProvider
    public Object[][] operationExtendedResultTypes() {
        return new Object[][] {
                { "", GenericExtendedResult.class },
                { ServerConstants.OID_PASSWORD_MODIFY_REQUEST , PasswordModifyExtendedResult.class }
        };
    }

    /** Tests the type of the result based on the type of the provided operation. */
    @Test(dataProvider = "operationExtendedResultTypes")
    public <R extends ExtendedResult> void testGetResponseResultAndResultType(
            String requestOID, Class<R> resultType) throws Exception {
        // Given
        ExtendedOperation operation = mock(ExtendedOperation.class);
        when(operation.getResultCode()).thenReturn(ResultCode.SUCCESS);
        when(operation.getRequestOID()).thenReturn(requestOID);
        // When
        Result result = getResponseResult(operation);
        // Then
        assertThat(result.getResultCode()).isEqualTo(org.forgerock.opendj.ldap.ResultCode.SUCCESS);
        assertThat(result).isInstanceOf(resultType);
    }

}
