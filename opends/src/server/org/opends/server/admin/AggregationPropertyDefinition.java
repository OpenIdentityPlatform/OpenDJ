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
package org.opends.server.admin;



import static org.opends.messages.AdminMessages.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.Validator.*;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

import org.opends.messages.Message;
import org.opends.server.admin.client.AuthorizationException;
import org.opends.server.admin.client.ClientConstraintHandler;
import org.opends.server.admin.client.CommunicationException;
import org.opends.server.admin.client.ManagedObject;
import org.opends.server.admin.client.ManagedObjectDecodingException;
import org.opends.server.admin.client.ManagementContext;
import org.opends.server.admin.condition.Condition;
import org.opends.server.admin.condition.Conditions;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.server.ServerConstraintHandler;
import org.opends.server.admin.server.ServerManagedObject;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.admin.std.meta.RootCfgDefn;
import org.opends.server.config.ConfigException;
import org.opends.server.loggers.ErrorLogger;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.ResultCode;
import org.opends.server.util.StaticUtils;



/**
 * Aggregation property definition.
 * <p>
 * An aggregation property names one or more managed objects which are
 * required by the managed object associated with this property. An
 * aggregation property definition takes care to perform referential
 * integrity checks: referenced managed objects cannot be deleted. Nor
 * can an aggregation reference non-existent managed objects.
 * Referential integrity checks are <b>not</b> performed during value
 * validation. Instead they are performed when changes to the managed
 * object are committed.
 * <p>
 * An aggregation property definition can optionally identify two
 * properties:
 * <ul>
 * <li>an <code>enabled</code> property in the aggregated managed
 * object - the property must be a {@link BooleanPropertyDefinition}
 * and indicate whether the aggregated managed object is enabled or
 * not. If specified, the administration framework will prevent the
 * aggregated managed object from being disabled while it is
 * referenced
 * <li>an <code>enabled</code> property in this property's managed
 * object - the property must be a {@link BooleanPropertyDefinition}
 * and indicate whether this property's managed object is enabled or
 * not. If specified, and as long as there is an equivalent
 * <code>enabled</code> property defined for the aggregated managed
 * object, the <code>enabled</code> property in the aggregated
 * managed object will only be checked when this property is true.
 * </ul>
 * In other words, these properties can be used to make sure that
 * referenced managed objects are not disabled while they are
 * referenced.
 *
 * @param <C>
 *          The type of client managed object configuration that this
 *          aggregation property definition refers to.
 * @param <S>
 *          The type of server managed object configuration that this
 *          aggregation property definition refers to.
 */
public final class AggregationPropertyDefinition
    <C extends ConfigurationClient, S extends Configuration>
    extends PropertyDefinition<String> {

  /**
   * An interface for incrementally constructing aggregation property
   * definitions.
   *
   * @param <C>
   *          The type of client managed object configuration that
   *          this aggregation property definition refers to.
   * @param <S>
   *          The type of server managed object configuration that
   *          this aggregation property definition refers to.
   */
  public static class Builder
      <C extends ConfigurationClient, S extends Configuration>
      extends AbstractBuilder<String, AggregationPropertyDefinition<C, S>> {

    // The string representation of the managed object path specifying
    // the parent of the aggregated managed objects.
    private String parentPathString = null;

    // The name of a relation in the parent managed object which
    // contains the aggregated managed objects.
    private String rdName = null;

    // The condition which is used to determine if a referenced
    // managed object is enabled.
    private Condition targetIsEnabledCondition = Conditions.TRUE;

    // The condition which is used to determine whether or not
    // referenced managed objects need to be enabled.
    private Condition targetNeedsEnablingCondition = Conditions.TRUE;



    // Private constructor
    private Builder(AbstractManagedObjectDefinition<?, ?> d,
        String propertyName) {
      super(d, propertyName);
    }



    /**
     * Sets the name of the managed object which is the parent of the
     * aggregated managed objects.
     * <p>
     * This must be defined before the property definition can be
     * built.
     *
     * @param pathString
     *          The string representation of the managed object path
     *          specifying the parent of the aggregated managed
     *          objects.
     */
    public final void setParentPath(String pathString) {
      this.parentPathString = pathString;
    }



    /**
     * Sets the relation in the parent managed object which contains
     * the aggregated managed objects.
     * <p>
     * This must be defined before the property definition can be
     * built.
     *
     * @param rdName
     *          The name of a relation in the parent managed object
     *          which contains the aggregated managed objects.
     */
    public final void setRelationDefinition(String rdName) {
      this.rdName = rdName;
    }



    /**
     * Sets the condition which is used to determine if a referenced
     * managed object is enabled. By default referenced managed
     * objects are assumed to always be enabled.
     *
     * @param condition
     *          The condition which is used to determine if a
     *          referenced managed object is enabled.
     */
    public final void setTargetIsEnabledCondition(Condition condition) {
      this.targetIsEnabledCondition = condition;
    }



    /**
     * Sets the condition which is used to determine whether or not
     * referenced managed objects need to be enabled. By default
     * referenced managed objects must always be enabled.
     *
     * @param condition
     *          The condition which is used to determine whether or
     *          not referenced managed objects need to be enabled.
     */
    public final void setTargetNeedsEnablingCondition(Condition condition) {
      this.targetNeedsEnablingCondition = condition;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    protected AggregationPropertyDefinition<C, S> buildInstance(
        AbstractManagedObjectDefinition<?, ?> d, String propertyName,
        EnumSet<PropertyOption> options, AdministratorAction adminAction,
        DefaultBehaviorProvider<String> defaultBehavior) {
      // Make sure that the parent path has been defined.
      if (parentPathString == null) {
        throw new IllegalStateException("Parent path undefined");
      }

      // Make sure that the relation definition has been defined.
      if (rdName == null) {
        throw new IllegalStateException("Relation definition undefined");
      }

      return new AggregationPropertyDefinition<C, S>(d, propertyName, options,
          adminAction, defaultBehavior, parentPathString, rdName,
          targetNeedsEnablingCondition, targetIsEnabledCondition);
    }

  }



  /**
   * A change listener which prevents the named component from being
   * disabled.
   */
  private class ReferentialIntegrityChangeListener implements
      ConfigurationChangeListener<S> {

    // The error message which should be returned if an attempt is
    // made to disable the referenced component.
    private final Message message;

    // The path of the referenced component.
    private final ManagedObjectPath<C, S> path;



    // Creates a new referential integrity delete listener.
    private ReferentialIntegrityChangeListener(ManagedObjectPath<C, S> path,
        Message message) {
      this.path = path;
      this.message = message;
    }



    /**
     * {@inheritDoc}
     */
    public ConfigChangeResult applyConfigurationChange(S configuration) {
      ServerManagedObject<?> mo = configuration.managedObject();
      try {
        if (targetIsEnabledCondition.evaluate(mo)) {
          return new ConfigChangeResult(ResultCode.SUCCESS, false);
        }
      } catch (ConfigException e) {
        // This should not happen - ignore it and throw an exception
        // anyway below.
      }

      // This should not happen - the previous call-back should have
      // trapped this.
      throw new IllegalStateException("Attempting to disable a referenced "
          + configuration.definition().getUserFriendlyName());
    }



    /**
     * {@inheritDoc}
     */
    public boolean isConfigurationChangeAcceptable(S configuration,
        List<Message> unacceptableReasons) {
      // Always prevent the referenced component from being
      // disabled.
      ServerManagedObject<?> mo = configuration.managedObject();
      try {
        if (!targetIsEnabledCondition.evaluate(mo)) {
          unacceptableReasons.add(message);
          return false;
        } else {
          return true;
        }
      } catch (ConfigException e) {
        // The condition could not be evaluated.
        if (debugEnabled()) {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        Message message = ERR_REFINT_UNABLE_TO_EVALUATE_TARGET_CONDITION.get(mo
            .getManagedObjectDefinition().getUserFriendlyName(), String
            .valueOf(configuration.dn()), StaticUtils.getExceptionMessage(e));
        ErrorLogger.logError(message);
        unacceptableReasons.add(message);
        return false;
      }
    }



    // Gets the path associated with this listener.
    private ManagedObjectPath<C, S> getManagedObjectPath() {
      return path;
    }

  }



  /**
   * A delete listener which prevents the named component from being
   * deleted.
   */
  private class ReferentialIntegrityDeleteListener implements
      ConfigurationDeleteListener<S> {

    // The DN of the referenced configuration entry.
    private final DN dn;

    // The error message which should be returned if an attempt is
    // made to delete the referenced component.
    private final Message message;



    // Creates a new referential integrity delete listener.
    private ReferentialIntegrityDeleteListener(DN dn, Message message) {
      this.dn = dn;
      this.message = message;
    }



    /**
     * {@inheritDoc}
     */
    public ConfigChangeResult applyConfigurationDelete(S configuration) {
      // This should not happen - the
      // isConfigurationDeleteAcceptable() call-back should have
      // trapped this.
      if (configuration.dn().equals(dn)) {
        // This should not happen - the
        // isConfigurationDeleteAcceptable() call-back should have
        // trapped this.
        throw new IllegalStateException("Attempting to delete a referenced "
            + configuration.definition().getUserFriendlyName());
      } else {
        return new ConfigChangeResult(ResultCode.SUCCESS, false);
      }
    }



    /**
     * {@inheritDoc}
     */
    public boolean isConfigurationDeleteAcceptable(S configuration,
        List<Message> unacceptableReasons) {
      if (configuration.dn().equals(dn)) {
        // Always prevent deletion of the referenced component.
        unacceptableReasons.add(message);
        return false;
      }

      return true;
    }

  }



  /**
   * The server-side constraint handler implementation.
   */
  private class ServerHandler extends ServerConstraintHandler {

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isUsable(ServerManagedObject<?> managedObject,
        Collection<Message> unacceptableReasons) throws ConfigException {
      SortedSet<String> names = managedObject
          .getPropertyValues(AggregationPropertyDefinition.this);
      ServerManagementContext context = ServerManagementContext.getInstance();
      Message thisUFN = managedObject.getManagedObjectDefinition()
          .getUserFriendlyName();
      String thisDN = managedObject.getDN().toString();
      Message thatUFN = getRelationDefinition().getUserFriendlyName();

      boolean isUsable = true;
      boolean needsEnabling = targetNeedsEnablingCondition
          .evaluate(managedObject);
      for (String name : names) {
        ManagedObjectPath<C, S> path = getChildPath(name);
        String thatDN = path.toDN().toString();

        if (!context.managedObjectExists(path)) {
          Message msg = ERR_SERVER_REFINT_DANGLING_REFERENCE.get(name,
              getName(), thisUFN, thisDN, thatUFN, thatDN);
          unacceptableReasons.add(msg);
          isUsable = false;
        } else if (needsEnabling) {
          // Check that the referenced component is enabled if
          // required.
          ServerManagedObject<? extends S> ref = context.getManagedObject(path);
          if (!targetIsEnabledCondition.evaluate(ref)) {
            Message msg = ERR_SERVER_REFINT_TARGET_DISABLED.get(name,
                getName(), thisUFN, thisDN, thatUFN, thatDN);
            unacceptableReasons.add(msg);
            isUsable = false;
          }
        }
      }

      return isUsable;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void performPostAdd(ServerManagedObject<?> managedObject)
        throws ConfigException {
      // First make sure existing listeners associated with this
      // managed object are removed. This is required in order to
      // prevent multiple change listener registrations from
      // occurring, for example if this call-back is invoked multiple
      // times after the same add event.
      performPostDelete(managedObject);

      // Add change and delete listeners against all referenced
      // components.
      Message thisUFN = managedObject.getManagedObjectDefinition()
          .getUserFriendlyName();
      String thisDN = managedObject.getDN().toString();
      Message thatUFN = getRelationDefinition().getUserFriendlyName();

      // Referenced managed objects will only need a change listener
      // if they have can be disabled.
      boolean needsChangeListeners = targetNeedsEnablingCondition
          .evaluate(managedObject);

      // Delete listeners need to be registered against the parent
      // entry of the referenced components.
      ServerManagementContext context = ServerManagementContext.getInstance();
      ManagedObjectPath<?, ?> parentPath = getParentPath();
      ServerManagedObject<?> parent = context.getManagedObject(parentPath);

      // Create entries in the listener tables.
      List<ReferentialIntegrityDeleteListener> dlist =
        new LinkedList<ReferentialIntegrityDeleteListener>();
      deleteListeners.put(managedObject.getDN(), dlist);

      List<ReferentialIntegrityChangeListener> clist =
        new LinkedList<ReferentialIntegrityChangeListener>();
      changeListeners.put(managedObject.getDN(), clist);

      for (String name : managedObject
          .getPropertyValues(AggregationPropertyDefinition.this)) {
        ManagedObjectPath<C, S> path = getChildPath(name);
        DN dn = path.toDN();
        String thatDN = dn.toString();

        // Register the delete listener.
        Message msg = ERR_SERVER_REFINT_CANNOT_DELETE.get(thatUFN, thatDN,
            getName(), thisUFN, thisDN);
        ReferentialIntegrityDeleteListener dl =
          new ReferentialIntegrityDeleteListener(dn, msg);
        parent.registerDeleteListener(getRelationDefinition(), dl);
        dlist.add(dl);

        // Register the change listener if required.
        if (needsChangeListeners) {
          ServerManagedObject<? extends S> ref = context.getManagedObject(path);
          msg = ERR_SERVER_REFINT_CANNOT_DISABLE.get(thatUFN, thatDN,
              getName(), thisUFN, thisDN);
          ReferentialIntegrityChangeListener cl =
            new ReferentialIntegrityChangeListener(path, msg);
          ref.registerChangeListener(cl);
          clist.add(cl);
        }
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void performPostDelete(ServerManagedObject<?> managedObject)
        throws ConfigException {
      // Remove any registered delete and change listeners.
      ServerManagementContext context = ServerManagementContext.getInstance();
      DN dn = managedObject.getDN();

      // Delete listeners need to be deregistered against the parent
      // entry of the referenced components.
      ManagedObjectPath<?, ?> parentPath = getParentPath();
      ServerManagedObject<?> parent = context.getManagedObject(parentPath);
      if (deleteListeners.containsKey(dn)) {
        for (ReferentialIntegrityDeleteListener dl : deleteListeners.get(dn)) {
          parent.deregisterDeleteListener(getRelationDefinition(), dl);
        }
        deleteListeners.remove(dn);
      }

      // Change listeners need to be deregistered from their
      // associated referenced component.
      if (changeListeners.containsKey(dn)) {
        for (ReferentialIntegrityChangeListener cl : changeListeners.get(dn)) {
          ManagedObjectPath<C, S> path = cl.getManagedObjectPath();
          ServerManagedObject<? extends S> ref = context.getManagedObject(path);
          ref.deregisterChangeListener(cl);
        }
        changeListeners.remove(dn);
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void performPostModify(ServerManagedObject<?> managedObject)
        throws ConfigException {
      // Remove all the constraints associated with this managed
      // object and then re-register them.
      performPostDelete(managedObject);
      performPostAdd(managedObject);
    }
  }



  /**
   * The client-side constraint handler implementation which enforces
   * referential integrity when aggregating managed objects are added
   * or modified.
   */
  private class SourceClientHandler extends ClientConstraintHandler {

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAddAcceptable(ManagementContext context,
        ManagedObject<?> managedObject, Collection<Message> unacceptableReasons)
        throws AuthorizationException, CommunicationException {
      // If all of this managed object's "enabled" properties are true
      // then any referenced managed objects must also be enabled.
      boolean needsEnabling = targetNeedsEnablingCondition.evaluate(context,
          managedObject);

      // Check the referenced managed objects exist and, if required,
      // are enabled.
      boolean isAcceptable = true;
      Message ufn = getRelationDefinition().getUserFriendlyName();
      for (String name : managedObject
          .getPropertyValues(AggregationPropertyDefinition.this)) {
        // Retrieve the referenced managed object and make sure it
        // exists.
        ManagedObjectPath<?, ?> path = getChildPath(name);
        ManagedObject<?> ref;
        try {
          ref = context.getManagedObject(path);
        } catch (DefinitionDecodingException e) {
          Message msg = ERR_CLIENT_REFINT_TARGET_INVALID.get(ufn, name,
              getName(), e.getMessageObject());
          unacceptableReasons.add(msg);
          isAcceptable = false;
          continue;
        } catch (ManagedObjectDecodingException e) {
          Message msg = ERR_CLIENT_REFINT_TARGET_INVALID.get(ufn, name,
              getName(), e.getMessageObject());
          unacceptableReasons.add(msg);
          isAcceptable = false;
          continue;
        } catch (ManagedObjectNotFoundException e) {
          Message msg = ERR_CLIENT_REFINT_TARGET_DANGLING_REFERENCE.get(ufn,
              name, getName());
          unacceptableReasons.add(msg);
          isAcceptable = false;
          continue;
        }

        // Make sure the reference managed object is enabled.
        if (needsEnabling) {
          if (!targetIsEnabledCondition.evaluate(context, ref)) {
            Message msg = ERR_CLIENT_REFINT_TARGET_DISABLED.get(ufn, name,
                getName());
            unacceptableReasons.add(msg);
            isAcceptable = false;
          }
        }
      }
      return isAcceptable;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isModifyAcceptable(ManagementContext context,
        ManagedObject<?> managedObject, Collection<Message> unacceptableReasons)
        throws AuthorizationException, CommunicationException {
      // The same constraint applies as for adds.
      return isAddAcceptable(context, managedObject, unacceptableReasons);
    }

  }



  /**
   * The client-side constraint handler implementation which enforces
   * referential integrity when aggregated managed objects are deleted
   * or modified.
   */
  private class TargetClientHandler extends ClientConstraintHandler {

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isDeleteAcceptable(ManagementContext context,
        ManagedObjectPath<?, ?> path, Collection<Message> unacceptableReasons)
        throws AuthorizationException, CommunicationException {
      // Any references to the deleted managed object should cause a
      // constraint violation.
      boolean isAcceptable = true;
      for (ManagedObject<?> mo : findReferences(context,
          getManagedObjectDefinition(), path.getName())) {
        String name = mo.getManagedObjectPath().getName();
        if (name == null) {
          Message msg = ERR_CLIENT_REFINT_CANNOT_DELETE_WITHOUT_NAME.get(
              getName(), mo.getManagedObjectDefinition().getUserFriendlyName(),
              getManagedObjectDefinition().getUserFriendlyName());
          unacceptableReasons.add(msg);
        } else {
          Message msg = ERR_CLIENT_REFINT_CANNOT_DELETE_WITH_NAME.get(
              getName(), mo.getManagedObjectDefinition().getUserFriendlyName(),
              name, getManagedObjectDefinition().getUserFriendlyName());
          unacceptableReasons.add(msg);
        }
        isAcceptable = false;
      }
      return isAcceptable;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isModifyAcceptable(ManagementContext context,
        ManagedObject<?> managedObject, Collection<Message> unacceptableReasons)
        throws AuthorizationException, CommunicationException {
      // If the modified managed object is disabled and there are some
      // active references then refuse the change.
      if (targetIsEnabledCondition.evaluate(context, managedObject)) {
        return true;
      }

      // The referenced managed object is disabled. Need to check for
      // active references.
      boolean isAcceptable = true;
      for (ManagedObject<?> mo : findReferences(context,
          getManagedObjectDefinition(), managedObject.getManagedObjectPath()
              .getName())) {
        if (targetNeedsEnablingCondition.evaluate(context, mo)) {
          String name = mo.getManagedObjectPath().getName();
          if (name == null) {
            Message msg = ERR_CLIENT_REFINT_CANNOT_DISABLE_WITHOUT_NAME.get(
                managedObject.getManagedObjectDefinition()
                    .getUserFriendlyName(), getName(), mo
                    .getManagedObjectDefinition().getUserFriendlyName());
            unacceptableReasons.add(msg);
          } else {
            Message msg = ERR_CLIENT_REFINT_CANNOT_DISABLE_WITH_NAME.get(
                managedObject.getManagedObjectDefinition()
                    .getUserFriendlyName(), getName(), mo
                    .getManagedObjectDefinition().getUserFriendlyName(), name);
            unacceptableReasons.add(msg);
          }
          isAcceptable = false;
        }
      }
      return isAcceptable;
    }



    // Find all managed objects which reference the named managed
    // object using this property.
    private <CC extends ConfigurationClient>
        List<ManagedObject<? extends CC>> findReferences(
        ManagementContext context, AbstractManagedObjectDefinition<CC, ?> mod,
        String name) throws AuthorizationException, CommunicationException {
      List<ManagedObject<? extends CC>> instances = findInstances(context, mod);

      Iterator<ManagedObject<? extends CC>> i = instances.iterator();
      while (i.hasNext()) {
        ManagedObject<? extends CC> mo = i.next();
        boolean hasReference = false;

        for (String value : mo
            .getPropertyValues(AggregationPropertyDefinition.this)) {
          if (compare(value, name) == 0) {
            hasReference = true;
            break;
          }
        }

        if (!hasReference) {
          i.remove();
        }
      }

      return instances;
    }



    // Find all instances of a specific type of managed object.
    @SuppressWarnings("unchecked")
    private <CC extends ConfigurationClient>
        List<ManagedObject<? extends CC>> findInstances(
        ManagementContext context, AbstractManagedObjectDefinition<CC, ?> mod)
        throws AuthorizationException, CommunicationException {
      List<ManagedObject<? extends CC>> instances =
        new LinkedList<ManagedObject<? extends CC>>();

      if (mod == RootCfgDefn.getInstance()) {
        instances.add((ManagedObject<? extends CC>) context
            .getRootConfigurationManagedObject());
      } else {
        for (RelationDefinition<? super CC, ?> rd : mod
            .getAllReverseRelationDefinitions()) {
          for (ManagedObject<?> parent : findInstances(context, rd
              .getParentDefinition())) {
            try {
              if (rd instanceof SingletonRelationDefinition) {
                SingletonRelationDefinition<? super CC, ?> srd =
                  (SingletonRelationDefinition<? super CC, ?>) rd;
                ManagedObject<?> mo = parent.getChild(srd);
                if (mo.getManagedObjectDefinition().isChildOf(mod)) {
                  instances.add((ManagedObject<? extends CC>) mo);
                }
              } else if (rd instanceof OptionalRelationDefinition) {
                OptionalRelationDefinition<? super CC, ?> ord =
                  (OptionalRelationDefinition<? super CC, ?>) rd;
                ManagedObject<?> mo = parent.getChild(ord);
                if (mo.getManagedObjectDefinition().isChildOf(mod)) {
                  instances.add((ManagedObject<? extends CC>) mo);
                }
              } else if (rd instanceof InstantiableRelationDefinition) {
                InstantiableRelationDefinition<? super CC, ?> ird =
                  (InstantiableRelationDefinition<? super CC, ?>) rd;

                for (String name : parent.listChildren(ird)) {
                  ManagedObject<?> mo = parent.getChild(ird, name);
                  if (mo.getManagedObjectDefinition().isChildOf(mod)) {
                    instances.add((ManagedObject<? extends CC>) mo);
                  }
                }
              }
            } catch (OperationsException e) {
              // Ignore all operations exceptions.
            }
          }
        }
      }

      return instances;
    }
  }



  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



  /**
   * Creates an aggregation property definition builder.
   *
   * @param <C>
   *          The type of client managed object configuration that
   *          this aggregation property definition refers to.
   * @param <S>
   *          The type of server managed object configuration that
   *          this aggregation property definition refers to.
   * @param d
   *          The managed object definition associated with this
   *          property definition.
   * @param propertyName
   *          The property name.
   * @return Returns the new aggregation property definition builder.
   */
  public static <C extends ConfigurationClient, S extends Configuration>
      Builder<C, S> createBuilder(
      AbstractManagedObjectDefinition<?, ?> d, String propertyName) {
    return new Builder<C, S>(d, propertyName);
  }

  // The active server-side referential integrity change listeners
  // associated with this property.
  private final Map<DN, List<ReferentialIntegrityChangeListener>>
    changeListeners = new HashMap<DN,
      List<ReferentialIntegrityChangeListener>>();

  // The active server-side referential integrity delete listeners
  // associated with this property.
  private final Map<DN, List<ReferentialIntegrityDeleteListener>>
    deleteListeners = new HashMap<DN,
      List<ReferentialIntegrityDeleteListener>>();

  // The name of the managed object which is the parent of the
  // aggregated managed objects.
  private ManagedObjectPath<?, ?> parentPath;

  // The string representation of the managed object path specifying
  // the parent of the aggregated managed objects.
  private final String parentPathString;

  // The name of a relation in the parent managed object which
  // contains the aggregated managed objects.
  private final String rdName;

  // The relation in the parent managed object which contains the
  // aggregated managed objects.
  private InstantiableRelationDefinition<C, S> relationDefinition;

  // The source constraint.
  private final Constraint sourceConstraint;

  // The condition which is used to determine if a referenced managed
  // object is enabled.
  private final Condition targetIsEnabledCondition;

  // The condition which is used to determine whether or not
  // referenced managed objects need to be enabled.
  private final Condition targetNeedsEnablingCondition;



  // Private constructor.
  private AggregationPropertyDefinition(
      AbstractManagedObjectDefinition<?, ?> d, String propertyName,
      EnumSet<PropertyOption> options, AdministratorAction adminAction,
      DefaultBehaviorProvider<String> defaultBehavior, String parentPathString,
      String rdName, Condition targetNeedsEnablingCondition,
      Condition targetIsEnabledCondition) {
    super(d, String.class, propertyName, options, adminAction, defaultBehavior);

    this.parentPathString = parentPathString;
    this.rdName = rdName;
    this.targetNeedsEnablingCondition = targetNeedsEnablingCondition;
    this.targetIsEnabledCondition = targetIsEnabledCondition;
    this.sourceConstraint = new Constraint() {

      /**
       * {@inheritDoc}
       */
      public Collection<ClientConstraintHandler> getClientConstraintHandlers() {
        ClientConstraintHandler handler = new SourceClientHandler();
        return Collections.singleton(handler);
      }



      /**
       * {@inheritDoc}
       */
      public Collection<ServerConstraintHandler> getServerConstraintHandlers() {
        ServerConstraintHandler handler = new ServerHandler();
        return Collections.singleton(handler);
      }
    };
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public <R, P> R accept(PropertyDefinitionVisitor<R, P> v, P p) {
    return v.visitAggregation(this, p);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public <R, P> R accept(PropertyValueVisitor<R, P> v, String value, P p) {
    return v.visitAggregation(this, value, p);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String decodeValue(String value)
      throws IllegalPropertyValueStringException {
    ensureNotNull(value);

    try {
      validateValue(value);
      return value;
    } catch (IllegalPropertyValueException e) {
      throw new IllegalPropertyValueStringException(this, value);
    }
  }



  /**
   * Constructs a DN for a referenced managed object having the
   * provided name. This method is implemented by first calling
   * {@link #getChildPath(String)} and then invoking
   * {@code ManagedObjectPath.toDN()} on the returned path.
   *
   * @param name
   *          The name of the child managed object.
   * @return Returns a DN for a referenced managed object having the
   *         provided name.
   */
  public final DN getChildDN(String name) {
    return getChildPath(name).toDN();
  }



  /**
   * Constructs a managed object path for a referenced managed object
   * having the provided name.
   *
   * @param name
   *          The name of the child managed object.
   * @return Returns a managed object path for a referenced managed
   *         object having the provided name.
   */
  public final ManagedObjectPath<C, S> getChildPath(String name) {
    return parentPath.child(relationDefinition, name);
  }



  /**
   * Gets the name of the managed object which is the parent of the
   * aggregated managed objects.
   *
   * @return Returns the name of the managed object which is the
   *         parent of the aggregated managed objects.
   */
  public final ManagedObjectPath<?, ?> getParentPath() {
    return parentPath;
  }



  /**
   * Gets the relation in the parent managed object which contains the
   * aggregated managed objects.
   *
   * @return Returns the relation in the parent managed object which
   *         contains the aggregated managed objects.
   */
  public final InstantiableRelationDefinition<C, S> getRelationDefinition() {
    return relationDefinition;
  }



  /**
   * Gets the constraint which should be enforced on the aggregating
   * managed object.
   *
   * @return Returns the constraint which should be enforced on the
   *         aggregating managed object.
   */
  public Constraint getSourceConstraint() {
    return sourceConstraint;
  }



  /**
   * Gets the condition which is used to determine if a referenced
   * managed object is enabled.
   *
   * @return Returns the condition which is used to determine if a
   *         referenced managed object is enabled.
   */
  public final Condition getTargetIsEnabledCondition() {
    return targetIsEnabledCondition;
  }



  /**
   * Gets the condition which is used to determine whether or not
   * referenced managed objects need to be enabled.
   *
   * @return Returns the condition which is used to determine whether
   *         or not referenced managed objects need to be enabled.
   */
  public final Condition getTargetNeedsEnablingCondition() {
    return targetNeedsEnablingCondition;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String normalizeValue(String value)
      throws IllegalPropertyValueException {
    try {
      Reference<C, S> reference = Reference.parseName(parentPath,
          relationDefinition, value);
      return reference.getNormalizedName();
    } catch (IllegalArgumentException e) {
      throw new IllegalPropertyValueException(this, value);
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void toString(StringBuilder builder) {
    super.toString(builder);

    builder.append(" parentPath=");
    builder.append(parentPath);

    builder.append(" relationDefinition=");
    builder.append(relationDefinition.getName());

    builder.append(" targetNeedsEnablingCondition=");
    builder.append(String.valueOf(targetNeedsEnablingCondition));

    builder.append(" targetIsEnabledCondition=");
    builder.append(String.valueOf(targetIsEnabledCondition));
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void validateValue(String value) throws IllegalPropertyValueException {
    try {
      Reference.parseName(parentPath, relationDefinition, value);
    } catch (IllegalArgumentException e) {
      throw new IllegalPropertyValueException(this, value);
    }
  }



  /**
   * {@inheritDoc}
   */
  @SuppressWarnings("unchecked")
  @Override
  public void initialize() throws Exception {
    // Decode the path.
    parentPath = ManagedObjectPath.valueOf(parentPathString);

    // Decode the relation definition.
    AbstractManagedObjectDefinition<?, ?> parent = parentPath
        .getManagedObjectDefinition();
    RelationDefinition<?, ?> rd = parent.getRelationDefinition(rdName);
    relationDefinition = (InstantiableRelationDefinition<C, S>) rd;

    // Now decode the conditions.
    targetNeedsEnablingCondition.initialize(getManagedObjectDefinition());
    targetIsEnabledCondition.initialize(rd.getChildDefinition());

    // Register a client-side constraint with the referenced
    // definition. This will be used to enforce referential integrity
    // for actions performed against referenced managed objects.
    Constraint constraint = new Constraint() {

      /**
       * {@inheritDoc}
       */
      public Collection<ClientConstraintHandler> getClientConstraintHandlers() {
        ClientConstraintHandler handler = new TargetClientHandler();
        return Collections.singleton(handler);
      }



      /**
       * {@inheritDoc}
       */
      public Collection<ServerConstraintHandler> getServerConstraintHandlers() {
        return Collections.emptyList();
      }
    };

    rd.getChildDefinition().registerConstraint(constraint);
  }

}
