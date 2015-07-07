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
 *      Copyright 2007-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.admin.server;



import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.opends.messages.AdminMessages;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.opends.server.admin.AbsoluteInheritedDefaultBehaviorProvider;
import org.opends.server.admin.AbstractManagedObjectDefinition;
import org.opends.server.admin.AliasDefaultBehaviorProvider;
import org.opends.server.admin.Configuration;
import org.opends.server.admin.Constraint;
import org.opends.server.admin.DecodingException;
import org.opends.server.admin.DefaultBehaviorProvider;
import org.opends.server.admin.DefaultBehaviorProviderVisitor;
import org.opends.server.admin.DefinedDefaultBehaviorProvider;
import org.opends.server.admin.ManagedObjectDefinition;
import org.opends.server.admin.ManagedObjectPath;
import org.opends.server.admin.PropertyDefinition;
import org.opends.server.admin.RelativeInheritedDefaultBehaviorProvider;
import org.opends.server.admin.UndefinedDefaultBehaviorProvider;
import org.opends.server.api.ConfigChangeListener;
import org.opends.server.api.ConfigDeleteListener;
import org.opends.server.config.ConfigEntry;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.opends.server.types.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.util.StaticUtils;



/**
 * An adaptor class which converts {@link ConfigChangeListener}
 * call-backs to {@link ServerManagedObjectChangeListener}
 * call-backs.
 *
 * @param <S>
 *          The type of server configuration handled by the change
 *          listener.
 */
final class ConfigChangeListenerAdaptor<S extends Configuration> extends
    AbstractConfigListenerAdaptor implements ConfigChangeListener {

  /**
   * A default behavior visitor used for determining the set of
   * dependencies.
   *
   * @param <T>
   *          The type of property.
   */
  private static final class Visitor<T> implements
      DefaultBehaviorProviderVisitor<T, Void, ManagedObjectPath<?, ?>> {

    /**
     * Finds the dependencies associated with the provided property
     * definition.
     *
     * @param <T>
     * @param path
     *          The current base path used for relative name
     *          resolution.
     * @param pd
     *          The property definition.
     * @param dependencies
     *          Add dependencies names to this collection.
     */
    public static <T> void find(ManagedObjectPath<?, ?> path,
        PropertyDefinition<T> pd, Collection<DN> dependencies) {
      Visitor<T> v = new Visitor<>(dependencies);
      DefaultBehaviorProvider<T> db = pd.getDefaultBehaviorProvider();
      db.accept(v, path);
    }

    /** The names of entries that this change listener depends on. */
    private final Collection<DN> dependencies;



    /** Prevent instantiation. */
    private Visitor(Collection<DN> dependencies) {
      this.dependencies = dependencies;
    }



    /** {@inheritDoc} */
    public Void visitAbsoluteInherited(
        AbsoluteInheritedDefaultBehaviorProvider<T> d,
        ManagedObjectPath<?, ?> p) {
      ManagedObjectPath<?, ?> next = d.getManagedObjectPath();
      dependencies.add(DNBuilder.create(next));

      // If the dependent property uses inherited defaults then
      // recursively get those as well.
      String propertyName = d.getPropertyName();
      AbstractManagedObjectDefinition<?, ?> mod = d
          .getManagedObjectDefinition();
      PropertyDefinition<?> pd = mod.getPropertyDefinition(propertyName);
      find(next, pd, dependencies);

      return null;
    }



    /** {@inheritDoc} */
    public Void visitAlias(AliasDefaultBehaviorProvider<T> d,
        ManagedObjectPath<?, ?> p) {
      return null;
    }



    /** {@inheritDoc} */
    public Void visitDefined(DefinedDefaultBehaviorProvider<T> d,
        ManagedObjectPath<?, ?> p) {
      return null;
    }



    /** {@inheritDoc} */
    public Void visitRelativeInherited(
        RelativeInheritedDefaultBehaviorProvider<T> d,
        ManagedObjectPath<?, ?> p) {
      ManagedObjectPath<?, ?> next = d.getManagedObjectPath(p);
      dependencies.add(DNBuilder.create(next));

      // If the dependent property uses inherited defaults then
      // recursively get those as well.
      String propertyName = d.getPropertyName();
      AbstractManagedObjectDefinition<?, ?> mod = d
          .getManagedObjectDefinition();
      PropertyDefinition<?> pd = mod.getPropertyDefinition(propertyName);
      find(next, pd, dependencies);

      return null;
    }



    /** {@inheritDoc} */
    public Void visitUndefined(UndefinedDefaultBehaviorProvider<T> d,
        ManagedObjectPath<?, ?> p) {
      return null;
    }
  }
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** Cached managed object between accept/apply call-backs. */
  private ServerManagedObject<? extends S> cachedManagedObject;

  /**
   * The delete listener which is used to remove this listener and any
   * dependencies.
   */
  private final ConfigDeleteListener cleanerListener;

  /** The names of entries that this change listener depends on. */
  private final Set<DN> dependencies;

  /**
   * The listener used to notify this listener when dependency entries
   * are modified.
   */
  private final ConfigChangeListener dependencyListener;

  /** The DN associated with this listener. */
  private final DN dn;

  /** The underlying change listener. */
  private final ServerManagedObjectChangeListener<? super S> listener;

  /** The managed object path. */
  private final ManagedObjectPath<?, S> path;



  /**
   * Create a new configuration change listener adaptor.
   *
   * @param path
   *          The managed object path.
   * @param listener
   *          The underlying change listener.
   */
  public ConfigChangeListenerAdaptor(ManagedObjectPath<?, S> path,
      ServerManagedObjectChangeListener<? super S> listener) {
    this.path = path;
    this.dn = DNBuilder.create(path);
    this.listener = listener;
    this.cachedManagedObject = null;

    // This change listener should be notified when dependent entries
    // are modified. Determine the dependencies and register change
    // listeners against them.
    this.dependencies = new HashSet<>();
    this.dependencyListener = new ConfigChangeListener() {

      public ConfigChangeResult applyConfigurationChange(
          ConfigEntry configEntry) {
        ConfigEntry dependentConfigEntry = getConfigEntry(dn);
        if (dependentConfigEntry != null) {
          return ConfigChangeListenerAdaptor.this
              .applyConfigurationChange(dependentConfigEntry);
        } else {
          // The dependent entry was not found.
          configEntry.deregisterChangeListener(this);
          return new ConfigChangeResult();
        }
      }



      public boolean configChangeIsAcceptable(ConfigEntry configEntry,
          LocalizableMessageBuilder unacceptableReason) {
        ConfigEntry dependentConfigEntry = getConfigEntry(dn);
        if (dependentConfigEntry != null) {
          return ConfigChangeListenerAdaptor.this.configChangeIsAcceptable(
              dependentConfigEntry, unacceptableReason, configEntry);
        } else {
          // The dependent entry was not found.
          configEntry.deregisterChangeListener(this);
          return true;
        }
      }

    };

    AbstractManagedObjectDefinition<?, ?> d = path.getManagedObjectDefinition();
    for (PropertyDefinition<?> pd : d.getAllPropertyDefinitions()) {
      Visitor.find(path, pd, dependencies);
    }

    for (DN entryDN : dependencies) {
      // Be careful not to register listeners against the dependent
      // entry itself.
      if (!entryDN.equals(dn)) {
        ConfigEntry configEntry = getConfigEntry(entryDN);
        if (configEntry != null) {
          configEntry.registerChangeListener(dependencyListener);
        }
      }
    }

    // Register a delete listener against the parent which will
    // finalize this change listener when the monitored configuration
    // entry is removed.
    this.cleanerListener = new ConfigDeleteListener() {

      public ConfigChangeResult applyConfigurationDelete(
          ConfigEntry configEntry) {
        // Perform finalization if the deleted entry is the monitored
        // entry.
        if (configEntry.getDN().equals(dn)) {
          finalizeChangeListener();
        }
        return new ConfigChangeResult();
      }



      public boolean configDeleteIsAcceptable(ConfigEntry configEntry,
          LocalizableMessageBuilder unacceptableReason) {
        // Always acceptable.
        return true;
      }

    };

    DN parent = dn.parent();
    if (parent != null) {
      ConfigEntry configEntry = getConfigEntry(dn.parent());
      if (configEntry != null) {
        configEntry.registerDeleteListener(cleanerListener);
      }
    }
  }



  /** {@inheritDoc} */
  public ConfigChangeResult applyConfigurationChange(ConfigEntry configEntry) {
    // Looking at the ConfigFileHandler implementation reveals
    // that this ConfigEntry will actually be a different object to
    // the one passed in the previous call-back (it will have the same
    // content though). This configuration entry has the correct
    // listener lists.
    cachedManagedObject.setConfigEntry(configEntry);

    ConfigChangeResult result = listener
        .applyConfigurationChange(cachedManagedObject);

    // Now apply post constraint call-backs.
    if (result.getResultCode() == ResultCode.SUCCESS) {
      ManagedObjectDefinition<?, ?> d = cachedManagedObject
          .getManagedObjectDefinition();
      for (Constraint constraint : d.getAllConstraints()) {
        for (ServerConstraintHandler handler : constraint
            .getServerConstraintHandlers()) {
          try {
            handler.performPostModify(cachedManagedObject);
          } catch (ConfigException e) {
            logger.traceException(e);
          }
        }
      }
    }

    return result;
  }



  /** {@inheritDoc} */
  public boolean configChangeIsAcceptable(ConfigEntry configEntry,
      LocalizableMessageBuilder unacceptableReason) {
    return configChangeIsAcceptable(configEntry, unacceptableReason,
        configEntry);
  }



  /**
   * Indicates whether the configuration entry that will result from a
   * proposed modification is acceptable to this change listener.
   *
   * @param configEntry
   *          The configuration entry that will result from the
   *          requested update.
   * @param unacceptableReason
   *          A buffer to which this method can append a
   *          human-readable message explaining why the proposed
   *          change is not acceptable.
   * @param newConfigEntry
   *          The configuration entry that caused the notification
   *          (will be different from <code>configEntry</code> if a
   *          dependency was modified).
   * @return <CODE>true</CODE> if the proposed entry contains an
   *         acceptable configuration, or <CODE>false</CODE> if it
   *         does not.
   */
  public boolean configChangeIsAcceptable(ConfigEntry configEntry,
      LocalizableMessageBuilder unacceptableReason, ConfigEntry newConfigEntry) {
    try {
      ServerManagementContext context = ServerManagementContext.getInstance();
      cachedManagedObject = context.decode(path, configEntry, newConfigEntry);
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

    // Let the change listener decide.
    List<LocalizableMessage> reasons = new LinkedList<>();
    if (listener.isConfigurationChangeAcceptable(cachedManagedObject,reasons)) {
      return true;
    } else {
      generateUnacceptableReason(reasons, unacceptableReason);
      return false;
    }
  }



  /**
   * Finalizes this configuration change listener adaptor. This method
   * must be called before this change listener is removed.
   */
  public void finalizeChangeListener() {
    // Remove the dependency listeners.
    for (DN dependency : dependencies) {
      ConfigEntry listenerConfigEntry = getConfigEntry(dependency);
      if (listenerConfigEntry != null) {
        listenerConfigEntry.deregisterChangeListener(dependencyListener);
      }
    }

    // Now remove the cleaner listener as it will no longer be
    // needed.
    ConfigEntry parentConfigEntry = getConfigEntry(dn.parent());
    if (parentConfigEntry != null) {
      parentConfigEntry.deregisterDeleteListener(cleanerListener);
    }

  }



  /**
   * Get the server managed object change listener associated with
   * this adaptor.
   *
   * @return Returns the server managed object change listener
   *         associated with this adaptor.
   */
  ServerManagedObjectChangeListener<? super S>
  getServerManagedObjectChangeListener() {
    return listener;
  }



  /**
   * Returns the named configuration entry or null if it could not be retrieved.
   */
  private ConfigEntry getConfigEntry(DN dn) {
    try {
      ConfigEntry configEntry = DirectoryServer.getConfigEntry(dn);
      if (configEntry != null) {
        return configEntry;
      } else {
        logger.error(AdminMessages.ERR_ADMIN_MANAGED_OBJECT_DOES_NOT_EXIST.get(dn));
      }
    } catch (ConfigException e) {
      // The dependent entry could not be retrieved.
      logger.traceException(e);
      logger.error(AdminMessages.ERR_ADMIN_CANNOT_GET_MANAGED_OBJECT.get(
          dn, StaticUtils.getExceptionMessage(e)));
    }

    return null;
  }

}
