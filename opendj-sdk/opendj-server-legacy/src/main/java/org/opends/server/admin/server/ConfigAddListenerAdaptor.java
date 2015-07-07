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
 *      Copyright 2007-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.admin.server;

import java.util.LinkedList;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.admin.Configuration;
import org.opends.server.admin.Constraint;
import org.opends.server.admin.DecodingException;
import org.opends.server.admin.DefinitionDecodingException;
import org.opends.server.admin.DefinitionDecodingException.Reason;
import org.opends.server.admin.InstantiableRelationDefinition;
import org.opends.server.admin.ManagedObjectDefinition;
import org.opends.server.admin.ManagedObjectPath;
import org.opends.server.admin.OptionalRelationDefinition;
import org.opends.server.admin.SetRelationDefinition;
import org.opends.server.api.ConfigAddListener;
import org.opends.server.config.ConfigEntry;
import org.opends.server.types.DN;

/**
 * An adaptor class which converts {@link ConfigAddListener} callbacks
 * to {@link ServerManagedObjectAddListener} callbacks.
 *
 * @param <S>
 *          The type of server configuration handled by the add
 *          listener.
 */
final class ConfigAddListenerAdaptor<S extends Configuration> extends
    AbstractConfigListenerAdaptor implements ConfigAddListener {
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** Cached managed object between accept/apply callbacks. */
  private ServerManagedObject<? extends S> cachedManagedObject;

  /** The instantiable relation. */
  private final InstantiableRelationDefinition<?, S> instantiableRelation;

  /** The set relation. */
  private final SetRelationDefinition<?, S> setRelation;

  /** The underlying add listener. */
  private final ServerManagedObjectAddListener<S> listener;

  /** The optional relation. */
  private final OptionalRelationDefinition<?, S> optionalRelation;

  /** The managed object path of the parent. */
  private final ManagedObjectPath<?, ?> path;



  /**
   * Create a new configuration add listener adaptor for an
   * instantiable relation.
   *
   * @param path
   *          The managed object path of the parent.
   * @param relation
   *          The instantiable relation.
   * @param listener
   *          The underlying add listener.
   */
  public ConfigAddListenerAdaptor(ManagedObjectPath<?, ?> path,
      InstantiableRelationDefinition<?, S> relation,
      ServerManagedObjectAddListener<S> listener) {
    this.path = path;
    this.instantiableRelation = relation;
    this.optionalRelation = null;
    this.setRelation = null;
    this.listener = listener;
    this.cachedManagedObject = null;
  }



  /**
   * Create a new configuration add listener adaptor for an optional
   * relation.
   *
   * @param path
   *          The managed object path of the parent.
   * @param relation
   *          The optional relation.
   * @param listener
   *          The underlying add listener.
   */
  public ConfigAddListenerAdaptor(ManagedObjectPath<?, ?> path,
      OptionalRelationDefinition<?, S> relation,
      ServerManagedObjectAddListener<S> listener) {
    this.path = path;
    this.optionalRelation = relation;
    this.instantiableRelation = null;
    this.setRelation = null;
    this.listener = listener;
    this.cachedManagedObject = null;
  }



  /**
   * Create a new configuration add listener adaptor for a
   * set relation.
   *
   * @param path
   *          The managed object path of the parent.
   * @param relation
   *          The set relation.
   * @param listener
   *          The underlying add listener.
   */
  public ConfigAddListenerAdaptor(ManagedObjectPath<?, ?> path,
      SetRelationDefinition<?, S> relation,
      ServerManagedObjectAddListener<S> listener) {
    this.path = path;
    this.instantiableRelation = null;
    this.optionalRelation = null;
    this.setRelation = relation;
    this.listener = listener;
    this.cachedManagedObject = null;
  }



  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationAdd(ConfigEntry configEntry) {
    if (optionalRelation != null) {
      // Optional managed objects are located directly beneath the
      // parent and have a well-defined name. We need to make sure
      // that we are handling the correct entry.
      ManagedObjectPath<?, ?> childPath = path.child(optionalRelation);
      DN expectedDN = DNBuilder.create(childPath);
      if (!configEntry.getDN().equals(expectedDN)) {
        // Doesn't apply to us.
        return new ConfigChangeResult();
      }
    }

    // Cached objects are guaranteed to be from previous acceptable callback.
    ConfigChangeResult result = listener
        .applyConfigurationAdd(cachedManagedObject);

    // Now apply post constraint call-backs.
    if (result.getResultCode() == ResultCode.SUCCESS) {
      ManagedObjectDefinition<?, ?> d = cachedManagedObject
          .getManagedObjectDefinition();
      for (Constraint constraint : d.getAllConstraints()) {
        for (ServerConstraintHandler handler : constraint
            .getServerConstraintHandlers()) {
          try {
            handler.performPostAdd(cachedManagedObject);
          } catch (ConfigException e) {
            logger.traceException(e);
          }
        }
      }
    }

    return result;
  }



  /** {@inheritDoc} */
  @Override
  public boolean configAddIsAcceptable(ConfigEntry configEntry,
      LocalizableMessageBuilder unacceptableReason) {
    DN dn = configEntry.getDN();
    ByteString av = dn.rdn().getAttributeValue(0);
    String name = av.toString().trim();

    try {
      ManagedObjectPath<?, ? extends S> childPath;
      if (instantiableRelation != null) {
        childPath = path.child(instantiableRelation, name);
      } else if (setRelation != null) {
        try {
          childPath = path.child(setRelation, name);
        } catch (IllegalArgumentException e) {
          throw new DefinitionDecodingException(setRelation
              .getChildDefinition(), Reason.WRONG_TYPE_INFORMATION);
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

      ServerManagementContext context = ServerManagementContext.getInstance();
      cachedManagedObject = context.decode(childPath, configEntry, configEntry);
    } catch (DecodingException e) {
      unacceptableReason.append(e.getMessageObject());
      return false;
    }

    // Give up immediately if a constraint violation occurs.
    try {
      cachedManagedObject.ensureIsUsable();
    } catch (ConstraintViolationException e) {
      generateUnacceptableReason(e.getMessages(), unacceptableReason);
      return false;
    }

    // Let the add listener decide.
    List<LocalizableMessage> reasons = new LinkedList<>();
    if (listener.isConfigurationAddAcceptable(cachedManagedObject, reasons)) {
      return true;
    } else {
      generateUnacceptableReason(reasons, unacceptableReason);
      return false;
    }
  }



  /**
   * Get the server managed object add listener associated with this
   * adaptor.
   *
   * @return Returns the server managed object add listener associated
   *         with this adaptor.
   */
  ServerManagedObjectAddListener<S> getServerManagedObjectAddListener() {
    return listener;
  }
}
