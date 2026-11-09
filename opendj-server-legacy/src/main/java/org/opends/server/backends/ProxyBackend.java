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
 * Copyright 2025-2026 3A Systems, LLC.
 */
package org.opends.server.backends;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.ldap.*;
import org.forgerock.opendj.ldap.controls.GenericControl;
import org.forgerock.opendj.ldap.controls.ProxiedAuthV2RequestControl;
import org.forgerock.opendj.ldap.requests.*;
import org.forgerock.opendj.ldap.responses.*;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldif.ConnectionEntryReader;
import org.forgerock.opendj.server.config.server.ProxyBackendCfg;
import org.forgerock.util.Options;
import org.opends.server.api.Backend;
import org.opends.server.controls.ProxiedAuthV2Control;
import org.opends.server.core.*;
import org.opends.server.discovery.Partition;
import org.opends.server.discovery.ServiceDiscoveryMechanism;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.types.*;
import org.opends.server.types.Attribute;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;

import java.io.IOException;
import java.util.*;
import java.util.stream.StreamSupport;

import static org.forgerock.opendj.ldap.LDAPConnectionFactory.AUTHN_BIND_REQUEST;
import static org.forgerock.opendj.ldap.LDAPConnectionFactory.HEARTBEAT_ENABLED;
import static org.forgerock.opendj.ldap.requests.Requests.newSimpleBindRequest;
import static org.opends.messages.BackendMessages.*;
import static org.opends.server.util.ServerConstants.OID_PROXIED_AUTH_V1;
import static org.opends.server.util.ServerConstants.OID_PROXIED_AUTH_V2;

public class ProxyBackend extends Backend<ProxyBackendCfg> implements ConfigurationChangeListener<ProxyBackendCfg> {
    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    ProxyBackendCfg cfg;
    ServerContext serverContext;

    @Override
    public void configureBackend(ProxyBackendCfg cfg, ServerContext serverContext) throws ConfigException {
        this.serverContext=serverContext;
        applyConfigurationChange(cfg);
    }

    ConnectionFactory bindFactory=null;
    ConnectionFactory factory=null;
    @Override
    public ConfigChangeResult applyConfigurationChange(ProxyBackendCfg configuration) {
        this.cfg=configuration;

        final ServiceDiscoveryMechanism<?> discoveryMechanism=serverContext.getServiceDiscoveryMechanismConfigManager().getMechanism(cfg.getShard());

        final Partition partition=discoveryMechanism.getPartition();

        final List<ConnectionFactory> primaryBind = new LinkedList<>();
        final List<ConnectionFactory> secondaryBind = new LinkedList<>();
        final List<ConnectionFactory> primary = new LinkedList<>();
        final List<ConnectionFactory> secondary = new LinkedList<>();
        if (partition!=null) {
            for (Partition.Server server : partition.getPrimaryServers()) {
                primaryBind.add(Connections.newCachedConnectionPool(new LDAPConnectionFactory(server.getHostPort().getHost(), server.getHostPort().getPort(),
                        server.getOptions()
                                .set(HEARTBEAT_ENABLED, true)
                )));
                primary.add(Connections.newCachedConnectionPool(new LDAPConnectionFactory(server.getHostPort().getHost(), server.getHostPort().getPort(),
                        server.getOptions()
                                .set(HEARTBEAT_ENABLED, true)
                                .set(AUTHN_BIND_REQUEST, newSimpleBindRequest(discoveryMechanism.cfg.getBindDN().toString(), discoveryMechanism.cfg.getBindPassword().toCharArray()))
                )));
            }
            for (Partition.Server server : partition.getSecondaryServers()) {
                secondaryBind.add(Connections.newCachedConnectionPool(new LDAPConnectionFactory(server.getHostPort().getHost(), server.getHostPort().getPort(),
                        server.getOptions()
                                .set(HEARTBEAT_ENABLED, true)
                )));
                secondary.add(Connections.newCachedConnectionPool(new LDAPConnectionFactory(server.getHostPort().getHost(), server.getHostPort().getPort(),
                        server.getOptions()
                                .set(HEARTBEAT_ENABLED, true)
                                .set(AUTHN_BIND_REQUEST, newSimpleBindRequest(discoveryMechanism.cfg.getBindDN().toString(), discoveryMechanism.cfg.getBindPassword().toCharArray()))
                )));
            }
        }

        bindFactory = prioritizedAffinity(primaryBind, secondaryBind);
        factory = prioritizedAffinity(primary, secondary);

        return new ConfigChangeResult();
    }

    private static ConnectionFactory prioritizedAffinity(List<ConnectionFactory> primary,
                                                         List<ConnectionFactory> secondary) {
        if (primary.isEmpty() || secondary.isEmpty()) {
            return Connections.newAffinityRequestLoadBalancer(primary.isEmpty() ? secondary : primary,
                    Options.defaultOptions());
        }

        final ConnectionFactory secondaryAffinity =
                Connections.newAffinityRequestLoadBalancer(secondary, Options.defaultOptions());
        final List<ConnectionFactory> routes = new ArrayList<>(primary.size());
        for (int start = 0; start < primary.size(); start++) {
            final List<ConnectionFactory> candidates = new ArrayList<>(primary.size() + 1);
            for (int offset = 0; offset < primary.size(); offset++) {
                candidates.add(primary.get((start + offset) % primary.size()));
            }
            // The request-level secondary balancer must be last: it returns a
            // virtual connection before checking whether a server is reachable.
            candidates.add(secondaryAffinity);
            routes.add(Connections.newFailoverLoadBalancer(candidates, Options.defaultOptions()));
        }
        return Connections.newAffinityRequestLoadBalancer(routes, Options.defaultOptions());
    }

    @Override
    public void openBackend() throws ConfigException, InitializationException {
        logger.info(LocalizableMessage.raw("openBackend remote backend %s", cfg.getBackendId()));
        for (DN dn : cfg.getBaseDN())
        {
            try
            {
                serverContext.getBackendConfigManager().registerBaseDN(dn, this, false);
            }
            catch (Exception e)
            {
                throw new InitializationException(ERR_BACKEND_CANNOT_REGISTER_BASEDN.get(dn, e), e);
            }
        }
        cfg.addProxyChangeListener(this);
    }

    @Override
    public void finalizeBackend() {
        logger.info(LocalizableMessage.raw("finalizeBackend remote backend %s", cfg.getBackendId()));
        cfg.removeProxyChangeListener(this);

        // Deregister our base DNs.
        for (DN dn : cfg.getBaseDN())
        {
            try
            {
                serverContext.getBackendConfigManager().deregisterBaseDN(dn);
            }
            catch (Exception e)
            {
                logger.traceException(e);
            }
        }
    }

    @Override
    public Set<DN> getBaseDNs() {
        return cfg.getBaseDN();
    }

    @Override
    public boolean isDefaultRoute() {
        return false;
    }

    @Override
    public Set<String> getSupportedControls() {
        return Collections.emptySet();
    }

    @Override
    public Set<String> getSupportedFeatures() {
        return Collections.emptySet();
    }

    @Override
    public boolean isPrivateBackend() {
        return false;
    }

    private List<org.forgerock.opendj.ldap.controls.Control> convertRequestControls(Operation operation)
            throws DirectoryException {
        final List<org.forgerock.opendj.ldap.controls.Control> controls = new ArrayList<>();
        for (org.opends.server.types.Control control : operation.getRequestControls()) {
            // Never let a client override the identity used by this proxy's service account.
            if (OID_PROXIED_AUTH_V1.equals(control.getOID()) || OID_PROXIED_AUTH_V2.equals(control.getOID())) {
                throw new DirectoryException(ResultCode.UNAVAILABLE_CRITICAL_EXTENSION,
                        LocalizableMessage.raw("Client-supplied proxy authorization controls are not supported"));
            }
            try {
                final ByteStringBuilder encoded = new ByteStringBuilder();
                control.write(ASN1.getWriter(encoded));
                final ASN1Reader reader = ASN1.getReader(encoded.toByteString().toByteArray());
                reader.readStartSequence();
                reader.readOctetString(); // OID: use the value from the original control below.
                if (reader.hasNextElement() && reader.peekType() == ASN1.UNIVERSAL_BOOLEAN_TYPE) {
                    reader.readBoolean();
                }
                final ByteString value = reader.hasNextElement() ? reader.readOctetString() : null;
                reader.readEndSequence();
                controls.add(GenericControl.newControl(control.getOID(), control.isCritical(), value));
            } catch (IOException e) {
                throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                        LocalizableMessage.raw("Could not encode an LDAP request control"), e);
            }
        }
        return controls;
    }

    private void addRequestControls(Request request, List<org.forgerock.opendj.ldap.controls.Control> controls) {
        for (org.forgerock.opendj.ldap.controls.Control control : controls) {
            request.addControl(control);
        }
    }

    private List<org.opends.server.types.Control> convertResponseControls(
            List<org.forgerock.opendj.ldap.controls.Control> controls) {
        final List<org.opends.server.types.Control> converted = new ArrayList<>(controls.size());
        for (org.forgerock.opendj.ldap.controls.Control control : controls) {
            converted.add(new LDAPControl(control.getOID(), control.isCritical(),
                    control.hasValue() ? control.getValue() : null));
        }
        return converted;
    }

    private void addResponseControls(Operation operation, Result result) {
        for (org.opends.server.types.Control control : convertResponseControls(result.getControls())) {
            operation.addResponseControl(control);
        }
    }

    private void addProxiedAuthControl(Operation operation,final Request request) {
        final DN authorizationDN = operation.getClientConnection().getAuthenticationInfo().getAuthorizationDN();
        final String authorizationID = authorizationDN == null || authorizationDN.isRootDN()
                ? "dn:"
                : "dn:" + authorizationDN;
        request.addControl(ProxiedAuthV2RequestControl.newControl(authorizationID));
    }
    @Override
    public void execute(Operation operation) throws CanceledOperationException {
        if (operation.getOperationType() == OperationType.BIND) {
            operation.getClientConnection().setAuthenticationInfo(AuthenticationInfo.anonymous());
        }
        final List<org.forgerock.opendj.ldap.controls.Control> controls;
        try {
            controls = convertRequestControls(operation);
        } catch (DirectoryException e) {
            operation.setResponseData(e);
            return;
        }
        switch (operation.getOperationType()) {
            case BIND: {
                final BindOperation request = (BindOperation) operation;
                if (request.getAuthenticationType() != AuthenticationType.SIMPLE) {
                    // TODO: SASL authentication not implemented.
                    request.setResponseData(new DirectoryException(ResultCode.PROTOCOL_ERROR, LocalizableMessage.raw("non-SIMPLE authentication not supported: " + request.getAuthenticationType())));
                } else {
                    final SimpleBindRequest bindRequest = newSimpleBindRequest(
                            request.getBindDN().toString(), request.getSimplePassword().toCharArray());
                    addRequestControls(bindRequest, controls);
                    Result result;
                    try (Connection connection = bindFactory.getConnection()) {
                        result = connection.bind(bindRequest);
                    } catch (LdapException e) {
                        result =e.getResult();
                    }
                    request.setResultCode(result.getResultCode());
                    addResponseControls(operation, result);
                    request.setAuthFailureReason(LocalizableMessage.raw(result.getDiagnosticMessage()));
                    if (result.getResultCode().equals(ResultCode.SUCCESS)) {
                        operation.getClientConnection().setAuthenticationInfo(AuthenticationInfo.internal(new Entry(request.getBindDN(),null,null,null)));
                    }
                }
                break;
            }
            case ADD: {
                final AddOperation request = (AddOperation) operation;
                final AddRequest addRequest=Requests.newAddRequest(
                        new Entry(
                                request.getEntryDN(),
                                request.getObjectClasses(),
                                request.getUserAttributes(),
                                request.getOperationalAttributes()
                        ).toLDIFString().split(System.lineSeparator())
                );
                addRequestControls(addRequest, controls);
                addProxiedAuthControl(operation,addRequest);
                Result result;
                try (Connection connection = factory.getConnection()) {
                    result = connection.add(addRequest);
                } catch (LdapException e) {
                    result =e.getResult();
                }
                request.setResultCode(result.getResultCode());
                addResponseControls(operation, result);
                break;
            }
            case MODIFY: {
                final ModifyOperation request = (ModifyOperation) operation;
                ModifyRequest modifyRequest=Requests.newModifyRequest(request.getEntryDN());
                addRequestControls(modifyRequest, controls);
                addProxiedAuthControl(operation,modifyRequest);
                for(Modification  modify: request.getModifications()){
                    modifyRequest=modifyRequest.addModification(
                            modify.getModificationType(),
                            modify.getAttribute().getAttributeDescription().getNameOrOID(),
                            StreamSupport.stream(modify.getAttribute().spliterator(),false).toArray(Object[]::new)
                    );
                }
                Result result;
                try (Connection connection = factory.getConnection()) {
                    result = connection.modify(modifyRequest);
                } catch (LdapException e) {
                    result =e.getResult();
                }
                request.setResultCode(result.getResultCode());
                addResponseControls(operation, result);
                break;
            }
            case MODIFY_DN:{
                final ModifyDNOperation request = (ModifyDNOperation) operation;
                final ModifyDNRequest modifyDNRequest=Requests.newModifyDNRequest(request.getEntryDN(),request.getNewRDN())
                        .setNewSuperior(request.getNewSuperior())
                        .setDeleteOldRDN(request.deleteOldRDN());
                addRequestControls(modifyDNRequest, controls);
                addProxiedAuthControl(operation,modifyDNRequest);
                Result result;
                try (Connection connection = factory.getConnection()) {
                    result = connection.modifyDN(modifyDNRequest);
                } catch (LdapException e) {
                    result =e.getResult();
                }
                request.setResultCode(result.getResultCode());
                addResponseControls(operation, result);
                break;
            }
            case COMPARE:{
                final CompareOperation request = (CompareOperation) operation;
                final CompareRequest compareRequest=Requests.newCompareRequest(request.getEntryDN(),request.getAttributeDescription(),request.getAssertionValue());
                addRequestControls(compareRequest, controls);
                addProxiedAuthControl(operation,compareRequest);
                Result result;
                try (Connection connection = factory.getConnection()) {
                    result = connection.compare(compareRequest);
                } catch (LdapException e) {
                    result =e.getResult();
                }
                request.setResultCode(result.getResultCode());
                addResponseControls(operation, result);
                break;
            }
            case DELETE:{
                final DeleteOperation request = (DeleteOperation) operation;
                final DeleteRequest deleteRequest=Requests.newDeleteRequest(request.getEntryDN());
                addRequestControls(deleteRequest, controls);
                addProxiedAuthControl(operation,deleteRequest);
                Result result;
                try (Connection connection = factory.getConnection()) {
                    result = connection.delete(deleteRequest);
                } catch (LdapException e) {
                    result =e.getResult();
                }
                request.setResultCode(result.getResultCode());
                addResponseControls(operation, result);
                break;
            }
            case EXTENDED:{
                final ExtendedOperation request = (ExtendedOperation) operation;
                final ExtendedRequest extendedRequest=Requests.newGenericExtendedRequest(request.getRequestOID(),request.getRequestValue());
                addRequestControls(extendedRequest, controls);
                addProxiedAuthControl(operation,extendedRequest);
                ExtendedResult result;
                try (Connection connection = factory.getConnection()) {
                    result = connection.extendedRequest(extendedRequest);
                    request.setResultCode(result.getResultCode());
                    request.setResponseValue(result.getValue());
                    addResponseControls(operation, result);
                } catch (LdapException e) {
                    request.setResultCode(e.getResult().getResultCode());
                    addResponseControls(operation, e.getResult());
                }
                break;
            }
            case SEARCH:{
                final SearchOperation request = (SearchOperation) operation;
                final SearchRequest searchRequest=Requests.newSearchRequest(
                        request.getBaseDN().toString(),
                        request.getScope(),
                        request.getFilter().toString(),
                        (String[])request.getAttributes().toArray(new String[0])
                ).setDereferenceAliasesPolicy(request.getDerefPolicy())
                 .setSizeLimit(request.getSizeLimit())
                 .setTimeLimit(request.getTimeLimit())
                 .setTypesOnly(request.getTypesOnly());
                addRequestControls(searchRequest, controls);
                addProxiedAuthControl(operation,searchRequest);
                try (Connection connection = factory.getConnection()) {
                    try (ConnectionEntryReader entryReader = connection.search(searchRequest)){
                        while (entryReader.hasNext())
                        {
                            if (entryReader.isEntry()) {
                                final SearchResultEntry sr = entryReader.readEntry();
                                final Map<AttributeType,List<Attribute>> userAttributes=new HashMap<>();
                                final Map<AttributeType,List<Attribute>> operationalAttributes=new HashMap<>();
                                for (org.forgerock.opendj.ldap.Attribute attr : sr.getAllAttributes()){
                                    final AttributeBuilder attrBuilder=new AttributeBuilder(attr.getAttributeDescription());
                                    for (final ByteString value: attr) {
                                        attrBuilder.add(value);
                                    }
                                    final AttributeType type = attr.getAttributeDescription().getAttributeType();
                                    final Map<AttributeType,List<Attribute>> attributes =
                                            type.isOperational() ? operationalAttributes : userAttributes;
                                    attributes.computeIfAbsent(type, ignored -> new ArrayList<>()).add(attrBuilder.toAttribute());
                                }
                                request.sendSearchEntry(new org.opends.server.types.SearchResultEntry(
                                        new Entry(sr.getName(),null,userAttributes,operationalAttributes),
                                        convertResponseControls(sr.getControls())));
                            }else{
                                final SearchResultReference sr=entryReader.readReference();
                                request.sendSearchReference(new org.opends.server.types.SearchResultReference(
                                        sr.getURIs(), convertResponseControls(sr.getControls())));
                            }
                        }
                        final Result result = entryReader.readResult();
                        request.setResultCode(result.getResultCode());
                        addResponseControls(operation, result);
                    }
                } catch (LdapException e) {
                   request.setResultCode(e.getResult().getResultCode());
                   addResponseControls(operation, e.getResult());
                } catch (SearchResultReferenceIOException | DirectoryException e) {
                    throw new RuntimeException(e);
                }
                break;
            }
            case ABANDON:
            case UNBIND:
            default:
                throw new IllegalStateException("Proxy backend should not receive " + operation.getOperationType() + " requests.");
        }
    }

    @Override
    public boolean isConfigurationChangeAcceptable(ProxyBackendCfg configuration, List<LocalizableMessage> unacceptableReasons) {
        return true;
    }
}
