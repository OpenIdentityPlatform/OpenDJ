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
import org.forgerock.opendj.ldap.*;
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
import org.opends.server.types.*;
import org.opends.server.types.Attribute;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;

import java.util.*;
import java.util.stream.StreamSupport;

import static org.forgerock.opendj.ldap.LDAPConnectionFactory.AUTHN_BIND_REQUEST;
import static org.forgerock.opendj.ldap.LDAPConnectionFactory.HEARTBEAT_ENABLED;
import static org.forgerock.opendj.ldap.requests.Requests.newSimpleBindRequest;
import static org.opends.messages.BackendMessages.*;

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

        final List<ConnectionFactory> failoverBind = new LinkedList<>();
        if (!primaryBind.isEmpty()){
            failoverBind.add(Connections.newAffinityRequestLoadBalancer(primaryBind,Options.defaultOptions()));
        }
        if (!secondaryBind.isEmpty()){
            failoverBind.add(Connections.newAffinityRequestLoadBalancer(secondaryBind,Options.defaultOptions()));
        }
        bindFactory=Connections.newLeastRequestsLoadBalancer(failoverBind,Options.defaultOptions());

        final List<ConnectionFactory> failover = new LinkedList<>();
        if (!primary.isEmpty()){
            failover.add(Connections.newAffinityRequestLoadBalancer(primary,Options.defaultOptions()));
        }
        if (!secondary.isEmpty()){
            failover.add(Connections.newAffinityRequestLoadBalancer(secondary,Options.defaultOptions()));
        }
        factory=Connections.newLeastRequestsLoadBalancer(failover,Options.defaultOptions());

        return new ConfigChangeResult();
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

    private void addProxiedAuthControl(Operation operation,final Request request) {
        request.addControl(ProxiedAuthV2RequestControl.newControl("dn:"+Objects.requireNonNull(operation.getClientConnection().getAuthenticationInfo().getAuthorizationDN()).toString()));
    }
    @Override
    public void execute(Operation operation) throws CanceledOperationException {
        switch (operation.getOperationType()) {
            case BIND: {
                final BindOperation request = (BindOperation) operation;
                if (request.getAuthenticationType() != AuthenticationType.SIMPLE) {
                    // TODO: SASL authentication not implemented.
                    request.setResponseData(new DirectoryException(ResultCode.PROTOCOL_ERROR, LocalizableMessage.raw("non-SIMPLE authentication not supported: " + request.getAuthenticationType())));
                } else {
                    operation.getClientConnection().setAuthenticationInfo(AuthenticationInfo.anonymous());
                    Result result;
                    try (Connection connection = bindFactory.getConnection()) {
                        result = connection.bind(request.getBindDN().toString(), request.getSimplePassword().toCharArray());
                    } catch (LdapException e) {
                        result =e.getResult();
                    }
                    request.setResultCode(result.getResultCode());
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
                Result result;
                try (Connection connection = factory.getConnection()) {
                    result = connection.add(addRequest);
                } catch (LdapException e) {
                    result =e.getResult();
                }
                request.setResultCode(result.getResultCode());
                break;
            }
            case MODIFY: {
                final ModifyOperation request = (ModifyOperation) operation;
                ModifyRequest modifyRequest=Requests.newModifyRequest(request.getEntryDN());
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
                break;
            }
            case MODIFY_DN:{
                final ModifyDNOperation request = (ModifyDNOperation) operation;
                final ModifyDNRequest modifyDNRequest=Requests.newModifyDNRequest(request.getEntryDN(),request.getNewRDN());
                Result result;
                try (Connection connection = factory.getConnection()) {
                    result = connection.modifyDN(modifyDNRequest);
                } catch (LdapException e) {
                    result =e.getResult();
                }
                request.setResultCode(result.getResultCode());
                break;
            }
            case COMPARE:{
                final CompareOperation request = (CompareOperation) operation;
                final CompareRequest compareRequest=Requests.newCompareRequest(request.getEntryDN(),request.getAttributeDescription(),request.getAssertionValue());
                Result result;
                try (Connection connection = factory.getConnection()) {
                    result = connection.compare(compareRequest);
                } catch (LdapException e) {
                    result =e.getResult();
                }
                request.setResultCode(result.getResultCode());
                break;
            }
            case DELETE:{
                final DeleteOperation request = (DeleteOperation) operation;
                final DeleteRequest deleteRequest=Requests.newDeleteRequest(request.getEntryDN());
                Result result;
                try (Connection connection = factory.getConnection()) {
                    result = connection.delete(deleteRequest);
                } catch (LdapException e) {
                    result =e.getResult();
                }
                request.setResultCode(result.getResultCode());
                break;
            }
            case EXTENDED:{
                final ExtendedOperation request = (ExtendedOperation) operation;
                final ExtendedRequest extendedRequest=Requests.newGenericExtendedRequest(request.getRequestOID(),request.getRequestValue());
                addProxiedAuthControl(operation,extendedRequest);
                ExtendedResult result;
                try (Connection connection = factory.getConnection()) {
                    result = connection.extendedRequest(extendedRequest);
                    request.setResultCode(result.getResultCode());
                    request.setResponseValue(result.getValue());
                } catch (LdapException e) {
                    request.setResultCode(e.getResult().getResultCode());
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
                );
                try (Connection connection = factory.getConnection()) {
                    try (ConnectionEntryReader entryReader = connection.search(searchRequest)){
                        while (entryReader.hasNext())
                        {
                            if (entryReader.isEntry()) {
                                final SearchResultEntry sr = entryReader.readEntry();
                                final Map<AttributeType,List<Attribute>> userAttributes=new HashMap<>();
                                for (org.forgerock.opendj.ldap.Attribute attr : sr.getAllAttributes()){
                                    final List<Attribute> values=new ArrayList<>(attr.size());
                                    for (final ByteString value: attr) {
                                        final AttributeBuilder attrBuilder=new AttributeBuilder(attr.getAttributeDescription());
                                        attrBuilder.add(value);
                                        values.add(attrBuilder.toAttribute());
                                    }
                                    userAttributes.put(attr.getAttributeDescription().getAttributeType(), values);
                                }
                                request.sendSearchEntry(new org.opends.server.types.SearchResultEntry(new Entry(sr.getName(),null,userAttributes,null)));
                            }else{
                                final SearchResultReference sr=entryReader.readReference();
                                request.sendSearchReference(new org.opends.server.types.SearchResultReference(sr.getURIs()));
                            }
                        }
                        request.setResultCode(entryReader.readResult().getResultCode());
                    }
                } catch (LdapException e) {
                   request.setResultCode(e.getResult().getResultCode());
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
