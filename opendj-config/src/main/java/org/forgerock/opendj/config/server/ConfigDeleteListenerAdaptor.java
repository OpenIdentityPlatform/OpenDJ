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
 * Copyright 2007-2009 Sun Microsystems, Inc.
 * Portions copyright 2015-2016 ForgeRock AS.
 */
package org.forgerock.opendj.config.server;

import static com.forgerock.opendj.ldap.config.ConfigMessages.*;

import java.util.LinkedList;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.config.Configuration;
import org.forgerock.opendj.config.Constraint;
import org.forgerock.opendj.config.DecodingException;
import org.forgerock.opendj.config.DefinitionDecodingException;
import org.forgerock.opendj.config.InstantiableRelationDefinition;
import org.forgerock.opendj.config.ManagedObjectDefinition;
import org.forgerock.opendj.config.ManagedObjectPath;
import org.forgerock.opendj.config.OptionalRelationDefinition;
import org.forgerock.opendj.config.SetRelationDefinition;
import org.forgerock.opendj.config.DefinitionDecodingException.Reason;
import org.forgerock.opendj.config.server.spi.ConfigDeleteListener;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.forgerock.opendj.ldap.ResultCode;

/**
 * An adaptor class which converts {@link ConfigDeleteListener} callbacks to
 * {@link ServerManagedObjectDeleteListener} callbacks.
 *
 * @param <S>
 *            The type of server configuration handled by the delete listener.
 */
final class ConfigDeleteListenerAdaptor<S extends Configuration> extends AbstractConfigListenerAdaptor implements
        ConfigDeleteListener {

    private static final Logger debugLogger = LoggerFactory.getLogger(ConfigDeleteListenerAdaptor.class);

    /** Cached managed object between accept/apply callbacks. */
    private ServerManagedObject<? extends S> cachedManagedObject;

    /** The instantiable relation. */
    private final InstantiableRelationDefinition<?, S> instantiableRelation;

    /** The set relation. */
    private final SetRelationDefinition<?, S> setRelation;

    /** The underlying delete listener. */
    private final ServerManagedObjectDeleteListener<S> listener;

    /** The optional relation. */
    private final OptionalRelationDefinition<?, S> optionalRelation;

    /** The managed object path of the parent. */
    private final ManagedObjectPath<?, ?> path;

    private final ServerManagementContext serverContext;

    /**
     * Create a new configuration delete listener adaptor for an instantiable
     * relation.
     *
     * @param serverContext
     *            The server context.
     * @param path
     *            The managed object path of the parent.
     * @param relation
     *            The instantiable relation.
     * @param listener
     *            The underlying delete listener.
     */
    public ConfigDeleteListenerAdaptor(ServerManagementContext serverContext, ManagedObjectPath<?, ?> path,
            InstantiableRelationDefinition<?, S> relation, ServerManagedObjectDeleteListener<S> listener) {
        this.serverContext = serverContext;
        this.path = path;
        this.optionalRelation = null;
        this.instantiableRelation = relation;
        this.setRelation = null;
        this.listener = listener;
        this.cachedManagedObject = null;
    }

    /**
     * Create a new configuration delete listener adaptor for an optional
     * relation.
     * @param serverContext
     *            The server context.
     * @param path
     *            The managed object path of the parent.
     * @param relation
     *            The optional relation.
     * @param listener
     *            The underlying delete listener.
     */
    public ConfigDeleteListenerAdaptor(ServerManagementContext serverContext, ManagedObjectPath<?, ?> path,
            OptionalRelationDefinition<?, S> relation, ServerManagedObjectDeleteListener<S> listener) {
        this.serverContext = serverContext;
        this.path = path;
        this.optionalRelation = relation;
        this.instantiableRelation = null;
        this.setRelation = null;
        this.listener = listener;
        this.cachedManagedObject = null;
    }

    /**
     * Create a new configuration delete listener adaptor for an set relation.
     * @param serverContext
     *            The server context.
     * @param path
     *            The managed object path of the parent.
     * @param relation
     *            The set relation.
     * @param listener
     *            The underlying delete listener.
     */
    public ConfigDeleteListenerAdaptor(ServerManagementContext serverContext, ManagedObjectPath<?, ?> path,
            SetRelationDefinition<?, S> relation, ServerManagedObjectDeleteListener<S> listener) {
        this.serverContext = serverContext;
        this.path = path;
        this.optionalRelation = null;
        this.instantiableRelation = null;
        this.setRelation = relation;
        this.listener = listener;
        this.cachedManagedObject = null;
    }

    @Override
    public ConfigChangeResult applyConfigurationDelete(Entry configEntry) {
        if (optionalRelation != null) {
            // Optional managed objects are located directly beneath the
            // parent and have a well-defined name. We need to make sure
            // that we are handling the correct entry.
            ManagedObjectPath<?, ?> childPath = path.child(optionalRelation);
            DN expectedDN = DNBuilder.create(childPath);
            if (!configEntry.getName().equals(expectedDN)) {
                // Doesn't apply to us.
                return new ConfigChangeResult();
            }
        }

        // Cached objects are guaranteed to be from previous acceptable
        // callback.
        ConfigChangeResult result = listener.applyConfigurationDelete(cachedManagedObject);

        // Now apply post constraint call-backs.
        if (result.getResultCode() == ResultCode.SUCCESS) {
            ManagedObjectDefinition<?, ?> d = cachedManagedObject.getManagedObjectDefinition();
            for (Constraint constraint : d.getAllConstraints()) {
                for (ServerConstraintHandler handler : constraint.getServerConstraintHandlers()) {
                    try {
                        handler.performPostDelete(cachedManagedObject);
                    } catch (ConfigException e) {
                        debugLogger.trace("Unable to perform post delete", e);
                    }
                }
            }
        }

        return result;
    }

    @Override
    public boolean configDeleteIsAcceptable(Entry configEntry, LocalizableMessageBuilder unacceptableReason) {
        DN dn = configEntry.getName();
        String name = dn.rdn().getFirstAVA().getAttributeValue().toString().trim();

        try {
            ManagedObjectPath<?, ? extends S> childPath;
            if (instantiableRelation != null) {
                childPath = path.child(instantiableRelation, name);
            } else if (setRelation != null) {
                try {
                    childPath = path.child(setRelation, name);
                } catch (IllegalArgumentException e) {
                    throw new DefinitionDecodingException(setRelation.getChildDefinition(),
                            Reason.WRONG_TYPE_INFORMATION);
                }
            } else {
                // Optional managed objects are located directly beneath the
                // parent and have a well-defined name. We need to make sure
                // that we are handling the correct entry.
                childPath = path.child(optionalRelation);
                DN expectedDN = DNBuilder.create(childPath);
                if (!dn.equals(expectedDN)) {
                    // Doesn't apply to us.
                    return true;
                }
            }

            cachedManagedObject = serverContext.decode(childPath, configEntry);
        } catch (DecodingException e) {
            unacceptableReason.append(e.getMessageObject());
            return false;
        }

        List<LocalizableMessage> reasons = new LinkedList<>();

        // Enforce any constraints.
        boolean isDeleteAllowed = true;
        ManagedObjectDefinition<?, ?> d = cachedManagedObject.getManagedObjectDefinition();
        for (Constraint constraint : d.getAllConstraints()) {
            for (ServerConstraintHandler handler : constraint.getServerConstraintHandlers()) {
                try {
                    if (!handler.isDeleteAllowed(cachedManagedObject, reasons)) {
                        isDeleteAllowed = false;
                    }
                } catch (ConfigException e) {
                    LocalizableMessage message = ERR_SERVER_CONSTRAINT_EXCEPTION.get(e.getMessageObject());
                    reasons.add(message);
                    isDeleteAllowed = false;
                }
            }
        }

        // Give up immediately if a constraint violation occurs.
        if (!isDeleteAllowed) {
            generateUnacceptableReason(reasons, unacceptableReason);
            return false;
        }

        // Let the delete listener decide.
        if (listener.isConfigurationDeleteAcceptable(cachedManagedObject, reasons)) {
            return true;
        } else {
            generateUnacceptableReason(reasons, unacceptableReason);
            return false;
        }
    }

    /**
     * Get the server managed object delete listener associated with this
     * adaptor.
     *
     * @return Returns the server managed object delete listener associated with
     *         this adaptor.
     */
    ServerManagedObjectDeleteListener<S> getServerManagedObjectDeleteListener() {
        return listener;
    }
}
