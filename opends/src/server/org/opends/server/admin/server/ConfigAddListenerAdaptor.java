/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2007-2008 Sun Microsystems, Inc.
 */
package org.opends.server.admin.server;



import static org.opends.server.loggers.debug.DebugLogger.*;

import java.util.LinkedList;
import java.util.List;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.server.admin.Configuration;
import org.opends.server.admin.Constraint;
import org.opends.server.admin.DecodingException;
import org.opends.server.admin.InstantiableRelationDefinition;
import org.opends.server.admin.ManagedObjectDefinition;
import org.opends.server.admin.ManagedObjectPath;
import org.opends.server.admin.OptionalRelationDefinition;
import org.opends.server.api.ConfigAddListener;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.ResultCode;



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

  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // Cached managed object between accept/apply callbacks.
  private ServerManagedObject<? extends S> cachedManagedObject;

  // The instantiable relation.
  private final InstantiableRelationDefinition<?, S> instantiableRelation;

  // The underlying add listener.
  private final ServerManagedObjectAddListener<S> listener;

  // The optional relation.
  private final OptionalRelationDefinition<?, S> optionalRelation;

  // The managed object path of the parent.
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
    this.listener = listener;
    this.cachedManagedObject = null;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationAdd(ConfigEntry configEntry) {
    if (optionalRelation != null) {
      // Optional managed objects are located directly beneath the
      // parent and have a well-defined name. We need to make sure
      // that we are handling the correct entry.
      ManagedObjectPath<?, ?> childPath = path.child(optionalRelation);
      DN expectedDN = DNBuilder.create(childPath);
      if (!configEntry.getDN().equals(expectedDN)) {
        // Doesn't apply to us.
        return new ConfigChangeResult(ResultCode.SUCCESS, false);
      }
    }

    // Cached objects are guaranteed to be from previous acceptable
    // callback.
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
            if (debugEnabled()) {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }
          }
        }
      }
    }

    return result;
  }



  /**
   * {@inheritDoc}
   */
  public boolean configAddIsAcceptable(ConfigEntry configEntry,
      MessageBuilder unacceptableReason) {
    DN dn = configEntry.getDN();
    AttributeValue av = dn.getRDN().getAttributeValue(0);
    String name = av.getStringValue().trim();

    ManagedObjectPath<?, S> childPath;
    if (instantiableRelation != null) {
      childPath = path.child(instantiableRelation, name);
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

    try {
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
    List<Message> reasons = new LinkedList<Message>();
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
