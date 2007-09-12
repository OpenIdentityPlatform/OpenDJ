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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.admin;



import static org.opends.messages.AdminMessages.*;
import static org.opends.server.util.Validator.*;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

import org.opends.messages.Message;
import org.opends.server.admin.client.ClientConstraintHandler;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.server.ServerConstraintHandler;
import org.opends.server.admin.server.ServerManagedObject;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.config.ConfigException;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.ResultCode;



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
    extends PropertyDefinition<String> implements Constraint {

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

    // The type of referenced managed objects.
    private AbstractManagedObjectDefinition<?, ?> cd = null;

    // The name of the managed object which is the parent of the
    // aggregated managed objects.
    private ManagedObjectPath<?, ?> p = null;

    // The name of a relation in the parent managed object which
    // contains the aggregated managed objects.
    private String rdName = null;

    // The optional name of a boolean "enabled" property in this
    // managed object. When this property is true, the enabled
    // property in the aggregated managed object must also be true.
    private String sourceEnabledPropertyName = null;

    // The optional name of a boolean "enabled" property in the
    // aggregated managed object. This property must not be false
    // while the aggregated managed object is referenced.
    private String targetEnabledPropertyName = null;



    // Private constructor
    private Builder(AbstractManagedObjectDefinition<?, ?> d,
        String propertyName) {
      super(d, propertyName);
    }



    /**
     * Sets the definition of the type of referenced managed objects.
     * <p>
     * This must be defined before the property definition can be
     * built.
     *
     * @param d
     *          The definition of the type of referenced managed
     *          objects.
     */
    public final void setManagedObjectDefinition(
        AbstractManagedObjectDefinition<C, S> d) {
      this.cd = d;
    }



    /**
     * Sets the name of the managed object which is the parent of the
     * aggregated managed objects.
     * <p>
     * This must be defined before the property definition can be
     * built.
     *
     * @param p
     *          The name of the managed object which is the parent of
     *          the aggregated managed objects.
     */
    public final void setParentPath(ManagedObjectPath<?, ?> p) {
      this.p = p;
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
     * Sets the optional boolean "enabled" property in this managed
     * object. When this property is true, the enabled property in the
     * aggregated managed object must also be true.
     * <p>
     * By default no source property is defined. When it is defined,
     * the target property must also be defined.
     *
     * @param sourceEnabledPropertyName
     *          The optional boolean "enabled" property in this
     *          managed object.
     */
    public final void setSourceEnabledPropertyName(
        String sourceEnabledPropertyName) {
      this.sourceEnabledPropertyName = sourceEnabledPropertyName;
    }



    /**
     * Sets the optional boolean "enabled" property in the aggregated
     * managed object. This property must not be false while the
     * aggregated managed object is referenced.
     * <p>
     * By default no target property is defined. It must be defined,
     * if the source property is defined.
     *
     * @param targetEnabledPropertyName
     *          The optional boolean "enabled" property in the
     *          aggregated managed object.
     */
    public final void setTargetEnabledPropertyName(
        String targetEnabledPropertyName) {
      this.targetEnabledPropertyName = targetEnabledPropertyName;
    }



    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    protected AggregationPropertyDefinition<C, S> buildInstance(
        AbstractManagedObjectDefinition<?, ?> d, String propertyName,
        EnumSet<PropertyOption> options, AdministratorAction adminAction,
        DefaultBehaviorProvider<String> defaultBehavior) {
      // Make sure that the parent path has been defined.
      if (p == null) {
        throw new IllegalStateException("Parent path undefined");
      }

      // Make sure that the relation definition has been defined.
      if (rdName == null) {
        throw new IllegalStateException("Relation definition undefined");
      }

      // Make sure that the managed object definition has been
      // defined.
      if (cd == null) {
        throw new IllegalStateException("Managed object definition undefined");
      }

      // Make sure that the relation definition is a member of the
      // parent path's definition.
      AbstractManagedObjectDefinition<?, ?> parent = p
          .getManagedObjectDefinition();
      RelationDefinition<?, ?> rd = parent.getRelationDefinition(rdName);

      // Make sure the relation refers to the child type.
      AbstractManagedObjectDefinition<?, ?> dTmp = rd.getChildDefinition();
      if (dTmp != cd) {
        throw new IllegalStateException("Relation definition \"" + rd.getName()
            + "\" does not refer to definition " + d.getName());
      }

      // Force the relation to the correct type.
      InstantiableRelationDefinition<C, S> relation =
        (InstantiableRelationDefinition<C, S>) rd;

      // Make sure that if a source property is specified then a
      // target property is also specified.
      if (sourceEnabledPropertyName != null
          && targetEnabledPropertyName == null) {
        throw new IllegalStateException(
            "Source property defined but target property is undefined");
      }

      return new AggregationPropertyDefinition<C, S>(d, propertyName, options,
          adminAction, defaultBehavior, p, relation, sourceEnabledPropertyName,
          targetEnabledPropertyName);
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
      PropertyProvider provider = configuration.properties();
      Collection<Boolean> values = provider
          .getPropertyValues(getTargetEnabledPropertyDefinition());
      if (values.iterator().next() == false) {
        unacceptableReasons.add(message);
        return false;
      }
      return true;
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
      if (configuration.dn().equals(dn)) {
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

    // The associated property definition.
    private final AggregationPropertyDefinition<C, S> pd;



    // Creates a new server-side constraint handler.
    private ServerHandler(AggregationPropertyDefinition<C, S> pd) {
      this.pd = pd;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isUsable(ServerManagedObject<?> managedObject,
        Collection<Message> unacceptableReasons) throws ConfigException {
      SortedSet<String> names = managedObject.getPropertyValues(pd);
      ServerManagementContext context = ServerManagementContext.getInstance();
      BooleanPropertyDefinition tpd = pd.getTargetEnabledPropertyDefinition();
      BooleanPropertyDefinition spd = pd.getSourceEnabledPropertyDefinition();
      Message thisUFN = managedObject.getManagedObjectDefinition()
          .getUserFriendlyName();
      String thisDN = managedObject.getDN().toString();
      Message thatUFN = pd.getRelationDefinition().getUserFriendlyName();

      boolean isUsable = true;
      for (String name : names) {
        ManagedObjectPath<C, S> path = pd.getChildPath(name);
        String thatDN = path.toDN().toString();

        if (!context.managedObjectExists(path)) {
          Message msg = ERR_SERVER_REFINT_DANGLING_REFERENCE.get(name, pd
              .getName(), thisUFN, thisDN, thatUFN, thatDN);
          unacceptableReasons.add(msg);
          isUsable = false;
        } else if (tpd != null) {
          // Check that the referenced component is enabled.
          ServerManagedObject<? extends S> ref = context.getManagedObject(path);

          if (spd != null) {
            // Target must be enabled but only if the source is
            // enabled.
            if (managedObject.getPropertyValue(spd)
                && !ref.getPropertyValue(tpd)) {
              Message msg = ERR_SERVER_REFINT_SOURCE_ENABLED_TARGET_DISABLED
                  .get(name, pd.getName(), thisUFN, thisDN, thatUFN, thatDN);
              unacceptableReasons.add(msg);
              isUsable = false;
            }
          } else {
            // Target must always be enabled.
            if (!ref.getPropertyValue(tpd)) {
              Message msg = ERR_SERVER_REFINT_TARGET_DISABLED.get(name, pd
                  .getName(), thisUFN, thisDN, thatUFN, thatDN);
              unacceptableReasons.add(msg);
              isUsable = false;
            }
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
      BooleanPropertyDefinition tpd = pd.getTargetEnabledPropertyDefinition();
      BooleanPropertyDefinition spd = pd.getSourceEnabledPropertyDefinition();
      Message thisUFN = managedObject.getManagedObjectDefinition()
          .getUserFriendlyName();
      String thisDN = managedObject.getDN().toString();
      Message thatUFN = pd.getRelationDefinition().getUserFriendlyName();

      // Referenced managed objects will only need a change listener
      // if they have can be disabled.
      boolean needsChangeListeners;
      if (tpd != null) {
        if (spd == null) {
          needsChangeListeners = true;
        } else {
          needsChangeListeners = managedObject.getPropertyValue(spd);
        }
      } else {
        needsChangeListeners = false;
      }

      // Delete listeners need to be registered against the parent
      // entry of the referenced components.
      ServerManagementContext context = ServerManagementContext.getInstance();
      ManagedObjectPath<?, ?> parentPath = pd.getParentPath();
      ServerManagedObject<?> parent = context.getManagedObject(parentPath);

      // Create entries in the listener tables.
      List<ReferentialIntegrityDeleteListener> dlist =
        new LinkedList<ReferentialIntegrityDeleteListener>();
      deleteListeners.put(managedObject.getDN(), dlist);

      List<ReferentialIntegrityChangeListener> clist =
        new LinkedList<ReferentialIntegrityChangeListener>();
      changeListeners.put(managedObject.getDN(), clist);

      for (String name : managedObject.getPropertyValues(pd)) {
        ManagedObjectPath<C, S> path = pd.getChildPath(name);
        DN dn = path.toDN();
        String thatDN = dn.toString();

        // Register the delete listener.
        Message msg = ERR_SERVER_REFINT_CANNOT_DELETE.get(thatUFN, thatDN, pd
            .getName(), thisUFN, thisDN);
        ReferentialIntegrityDeleteListener dl =
          new ReferentialIntegrityDeleteListener(dn, msg);
        parent.registerDeleteListener(pd.getRelationDefinition(), dl);
        dlist.add(dl);

        // Register the change listener if required.
        if (needsChangeListeners) {
          ServerManagedObject<? extends S> ref = context.getManagedObject(path);
          msg = ERR_SERVER_REFINT_CANNOT_DISABLE.get(thatUFN, thatDN, pd
              .getName(), thisUFN, thisDN);
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
      ManagedObjectPath<?, ?> parentPath = pd.getParentPath();
      ServerManagedObject<?> parent = context.getManagedObject(parentPath);
      if (deleteListeners.containsKey(dn)) {
        for (ReferentialIntegrityDeleteListener dl : deleteListeners.get(dn)) {
          parent.deregisterDeleteListener(pd.getRelationDefinition(), dl);
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
      changeListeners =
        new HashMap<DN, List<ReferentialIntegrityChangeListener>>();

  // The active server-side referential integrity delete listeners
  // associated with this property.
  private final Map<DN, List<ReferentialIntegrityDeleteListener>>
      deleteListeners =
        new HashMap<DN, List<ReferentialIntegrityDeleteListener>>();

  // The name of the managed object which is the parent of the
  // aggregated managed objects.
  private final ManagedObjectPath<?, ?> parentPath;

  // The relation in the parent managed object which contains the
  // aggregated managed objects.
  private final InstantiableRelationDefinition<C, S> relationDefinition;

  // The optional name of a boolean "enabled" property in this managed
  // object. When this property is true, the enabled property in the
  // aggregated managed object must also be true.
  private final String sourceEnabledPropertyName;

  // The optional name of a boolean "enabled" property in the
  // aggregated managed object. This property must not be false while
  // the aggregated managed object is referenced.
  private final String targetEnabledPropertyName;



  // Private constructor.
  private AggregationPropertyDefinition(
      AbstractManagedObjectDefinition<?, ?> d, String propertyName,
      EnumSet<PropertyOption> options, AdministratorAction adminAction,
      DefaultBehaviorProvider<String> defaultBehavior,
      ManagedObjectPath<?, ?> parentPath,
      InstantiableRelationDefinition<C, S> relationDefinition,
      String sourceEnabledPropertyName, String targetEnabledPropertyName) {
    super(d, String.class, propertyName, options, adminAction, defaultBehavior);

    this.parentPath = parentPath;
    this.relationDefinition = relationDefinition;
    this.sourceEnabledPropertyName = sourceEnabledPropertyName;
    this.targetEnabledPropertyName = targetEnabledPropertyName;
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
   * {@inheritDoc}
   */
  public Collection<ClientConstraintHandler> getClientConstraintHandlers() {
    // TODO: not yet implemented.
    return Collections.emptyList();
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
   * {@inheritDoc}
   */
  public Collection<ServerConstraintHandler> getServerConstraintHandlers() {
    ServerConstraintHandler handler = new ServerHandler(this);
    return Collections.singleton(handler);
  }



  /**
   * Gets the optional boolean "enabled" property in this managed
   * object. When this property is true, the enabled property in the
   * aggregated managed object must also be true.
   *
   * @return Returns the optional boolean "enabled" property in this
   *         managed object, or <code>null</code> if none is
   *         defined.
   * @throws IllegalArgumentException
   *           If the named property does not exist in this property's
   *           associated managed object definition.
   * @throws ClassCastException
   *           If the named property does exist but is not a
   *           {@link BooleanPropertyDefinition}.
   */
  public final BooleanPropertyDefinition getSourceEnabledPropertyDefinition()
      throws IllegalArgumentException, ClassCastException {
    if (sourceEnabledPropertyName == null) {
      return null;
    }

    AbstractManagedObjectDefinition<?, ?> d = getManagedObjectDefinition();

    PropertyDefinition<?> pd;
    pd = d.getPropertyDefinition(sourceEnabledPropertyName);

    // Runtime cast is required to workaround a
    // bug in JDK versions prior to 1.5.0_08.
    return BooleanPropertyDefinition.class.cast(pd);
  }



  /**
   * Gets the optional boolean "enabled" property in the aggregated
   * managed object. This property must not be false while the
   * aggregated managed object is referenced.
   *
   * @return Returns the optional boolean "enabled" property in the
   *         aggregated managed object, or <code>null</code> if none
   *         is defined.
   * @throws IllegalArgumentException
   *           If the named property does not exist in the aggregated
   *           managed object's definition.
   * @throws ClassCastException
   *           If the named property does exist but is not a
   *           {@link BooleanPropertyDefinition}.
   */
  public final BooleanPropertyDefinition getTargetEnabledPropertyDefinition()
      throws IllegalArgumentException, ClassCastException {
    if (targetEnabledPropertyName == null) {
      return null;
    }

    AbstractManagedObjectDefinition<?, ?> d;
    PropertyDefinition<?> pd;

    d = relationDefinition.getChildDefinition();
    pd = d.getPropertyDefinition(targetEnabledPropertyName);

    // Runtime cast is required to workaround a
    // bug in JDK versions prior to 1.5.0_08.
    return BooleanPropertyDefinition.class.cast(pd);
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

    if (sourceEnabledPropertyName != null) {
      builder.append(" sourceEnabledPropertyName=");
      builder.append(sourceEnabledPropertyName);
    }

    if (targetEnabledPropertyName != null) {
      builder.append(" targetEnabledPropertyName=");
      builder.append(targetEnabledPropertyName);
    }
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

}
