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
package org.opends.server.admin.server;
import org.opends.messages.Message;



import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.admin.Configuration;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.messages.AdminMessages;

import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;



/**
 * A skeleton implementation of a configuration manager. This type of
 * manager can be used for managing "optional" configurations (i.e.
 * where there is a one to zero or one relationship).
 * <p>
 * Configuration managers are responsible for initializing and
 * finalizing instances as required. During initialization, the
 * manager takes responsibility for loading and instantiating the
 * implementation class. It then initializes the implementation
 * instance by invoking a method having the following signature:
 *
 * <pre>
 * void initializeXXX(YYY config) throws ConfigException,
 *     InitializationException;
 * </pre>
 *
 * Where <code>XXX</code> is the simple name of the instance type
 * <code>T</code>, and <code>YYY</code> is the expected
 * configuration type for the implementation, which is either the
 * configuration type <code>C</code> or a sub-type thereof.
 *
 * @param <C>
 *          The type of configuration referenced by this manager.
 * @param <T>
 *          The type of component represented by the configuration.
 */
public abstract class AbstractOptionalConfigurationManager
    <C extends Configuration, T> {

  /**
   * Private add listener implementation.
   */
  private class AddListener implements ConfigurationAddListener<C> {

    // Private constructor.
    private AddListener() {
      // No implementation required.
    }



    /**
     * {@inheritDoc}
     */
    public ConfigChangeResult applyConfigurationAdd(C config) {
      // Default result code.
      ResultCode resultCode = ResultCode.SUCCESS;
      boolean adminActionRequired = false;
      ArrayList<Message> messages = new ArrayList<Message>();

      // We have committed to this change so always update the
      // configuration.
      setConfiguration(config);

      if (isEnabled(config)) {
        // The configuration is enabled so it should be instantiated.
        try {
          // Notify that a new instance has been added.
          doRegisterInstance(getImplementation(config));
        } catch (ConfigException e) {
          messages.add(e.getMessageObject());
          resultCode = DirectoryServer.getServerErrorResultCode();
        } catch (InitializationException e) {
          messages.add(e.getMessageObject());
          resultCode = DirectoryServer.getServerErrorResultCode();
        }
      } else {
        // Ignore this configuration if it is disabled - we don't need
        // to set instance to null here since it should already be
        // null, but we do just to make the behavior explicit.
        if (instance != null) {
          finalizeInstance(instance);
          instance = null;
        }
        notifyDisableInstance(config);
      }

      // Return the configuration result.
      return new ConfigChangeResult(resultCode, adminActionRequired,
          messages);
    }



    /**
     * {@inheritDoc}
     */
    public boolean isConfigurationAddAcceptable(C config,
        List<Message> unacceptableReasons) {
      if (isEnabled(config)) {
        // It's enabled so always validate the class.
        return isJavaClassAcceptable(config, unacceptableReasons);
      } else {
        // It's disabled so ignore it.
        return true;
      }
    }
  }



  /**
   * Private change listener implementation.
   */
  private class ChangeListener implements
      ConfigurationChangeListener<C> {

    // Private constructor.
    private ChangeListener() {
      // No implementation required.
    }



    /**
     * {@inheritDoc}
     */
    public ConfigChangeResult applyConfigurationChange(C config) {
      // Default result code.
      ResultCode resultCode = ResultCode.SUCCESS;
      boolean adminActionRequired = false;
      ArrayList<Message> messages = new ArrayList<Message>();

      // We have committed to this change so always update the
      // configuration.
      setConfiguration(config);

      // See whether the configuration should be enabled.
      if (instance == null) {
        if (isEnabled(config)) {
          // The configuration is enabled so it should be
          // instantiated.
          try {
            // Notify that a new instance has been added.
            doRegisterInstance(getImplementation(config));
          } catch (ConfigException e) {
            messages.add(e.getMessageObject());
            resultCode = DirectoryServer.getServerErrorResultCode();
          } catch (InitializationException e) {
            messages.add(e.getMessageObject());
            resultCode = DirectoryServer.getServerErrorResultCode();
          }
        } else {
          // Do nothing: we could notify that the configuration is
          // disabled, but it was already, so there's no point in
          // notifying again.
        }
      } else if (isEnabled(config)) {
        // The instance is currently active, so we don't need to do
        // anything. Changes to the class name should not be applied
        // dynamically, so if the class name did change then
        // indicate that administrative action is required for that
        // change to take effect.
        String className = getJavaImplementationClass(config);
        if (!className.equals(instance.getClass().getName())) {
          adminActionRequired = true;
        }
      } else {
        // We need to disable the instance.
        if (instance != null) {
          finalizeInstance(instance);
          instance = null;
        }
        notifyDisableInstance(config);
      }

      // Return the configuration result.
      return new ConfigChangeResult(resultCode, adminActionRequired,
          messages);
    }



    /**
     * {@inheritDoc}
     */
    public boolean isConfigurationChangeAcceptable(C config,
        List<Message> unacceptableReasons) {
      if (isEnabled(config)) {
        // It's enabled so always validate the class.
        return isJavaClassAcceptable(config, unacceptableReasons);
      } else if (isEnabled(getConfiguration())) {
        return isDisableInstanceAcceptable(config,
            unacceptableReasons);
      } else {
        // It's already disabled so ignore it.
        return true;
      }
    }
  }



  /**
   * Private delete listener implementation.
   */
  private class DeleteListener implements
      ConfigurationDeleteListener<C> {

    // Private constructor.
    private DeleteListener() {
      // No implementation required.
    }



    /**
     * {@inheritDoc}
     */
    public ConfigChangeResult applyConfigurationDelete(C config) {
      // Default result code.
      ResultCode resultCode = ResultCode.SUCCESS;
      boolean adminActionRequired = false;

      // We have committed to this change so always update the
      // configuration and finalize the instance.
      setConfiguration(null);

      if (instance != null) {
        finalizeInstance(instance);
        instance = null;
      }
      notifyDeleteInstance(config);

      return new ConfigChangeResult(resultCode, adminActionRequired);
    }



    /**
     * {@inheritDoc}
     */
    public boolean isConfigurationDeleteAcceptable(C config,
        List<Message> unacceptableReasons) {
      if (isEnabled(getConfiguration())) {
        return isDisableInstanceAcceptable(config,
            unacceptableReasons);
      } else {
        return true;
      }
    }
  }

  // The property definition defining the Java implementation class.
  private final ClassPropertyDefinition propertyDefinition;

  // The instance class.
  private final Class<T> theClass;

  // Configuration add listener.
  private final AddListener addListener;

  // Configuration change listener.
  private final ChangeListener changeListener;

  // Configuration delete listener.
  private final DeleteListener deleteListener;

  // Indicates whether or not the parent listeners have been
  // registered.
  private boolean listenersRegistered;

  // The current active configuration.
  private C currentConfig;

  // The current active instance.
  private T instance;



  /**
   * Create a new optional configuration manager.
   *
   * @param theClass
   *          The instance class.
   * @param propertyDefinition
   *          The property definition defining the Java implementation
   *          class.
   */
  protected AbstractOptionalConfigurationManager(Class<T> theClass,
      ClassPropertyDefinition propertyDefinition) {
    this.currentConfig = null;
    this.instance = null;
    this.theClass = theClass;
    this.propertyDefinition = propertyDefinition;
    this.listenersRegistered = false;

    this.addListener = new AddListener();
    this.deleteListener = new DeleteListener();
    this.changeListener = new ChangeListener();
  }



  /**
   * Performs any finalization that may be necessary for this manager.
   */
  public final void finalizeManager() {
    deregisterAddListener(addListener);
    deregisterDeleteListener(deleteListener);

    if (currentConfig != null) {
      deregisterChangeListener(changeListener, currentConfig);
    }

    if (instance != null) {
      finalizeInstance(instance);
    }
  }



  /**
   * Get the current active configuration.
   *
   * @return Returns the current active configuration.
   */
  public final C getConfiguration() {
    return currentConfig;
  }



  /**
   * Get the current active instance.
   *
   * @return Returns the current active instance.
   */
  public final T getInstance() {
    return instance;
  }



  /**
   * Initializes this manager based on the information in the provided
   * configuration if available. The implementation of this method
   * will first register this manager as an add/delete listener before
   * processing the provided initial configuration if it is available.
   * <p>
   * It is safe to initialize the manager more than once. This is
   * useful during testing as an easy way to set the manager's
   * configuration.
   *
   * @param config
   *          The configuration (can be <code>null</code> if there
   *          is no initial configuration.
   * @throws ConfigException
   *           If there is a problem with the configuration.
   * @throws InitializationException
   *           If a problem occurs during initialization.
   */
  public final void initialize(C config) throws ConfigException,
      InitializationException {
    // Register to be notified when a configuration is added or
    // removed (only do this once).
    if (!listenersRegistered) {
      registerAddListener(addListener);
      registerDeleteListener(deleteListener);
      listenersRegistered = true;
    }

    // Make sure that there is no current instance.
    instance = null;

    if (config != null) {
      // Always register as a listener.
      setConfiguration(config);

      if (isEnabled(config)) {
        // We have a configuration and it is enabled.
        doRegisterInstance(getImplementation(config));
      } else {
        // There is a configuration present but it is disabled. Don't
        // process the configuration in case it is invalid. We don't
        // want to issue warnings for components which are disabled.
        notifyDisableInstance(config);
      }
    }
  }



  /**
   * Deregisters an add listener.
   *
   * @param listener
   *          The configuration add listener.
   */
  protected abstract void deregisterAddListener(
      ConfigurationAddListener<C> listener);



  /**
   * Deregisters a change listener.
   *
   * @param listener
   *          The configuration change listener.
   * @param config
   *          The configuration from which to deregister the change
   *          listener.
   */
  protected abstract void deregisterChangeListener(
      ConfigurationChangeListener<C> listener, C config);



  /**
   * Deregisters a delete listener.
   *
   * @param listener
   *          The configuration delete listener.
   */
  protected abstract void deregisterDeleteListener(
      ConfigurationDeleteListener<C> listener);



  /**
   * Finalizes a previously activate instance.
   *
   * @param instance
   *          The instance to be finalized.
   */
  protected abstract void finalizeInstance(T instance);



  /**
   * Get the name of the Java implementation class.
   * <p>
   * Sub-classes should usually implement this method using a call to
   * the configuration's <code>getJavaImplementationClass()</code>
   * method.
   *
   * @param config
   *          The active configuration.
   * @return Returns the name of the Java implementation class.
   */
  protected abstract String getJavaImplementationClass(C config);



  /**
   * Indicates whether the active instance can be disabled or deleted.
   *
   * @param config
   *          The configuration that will be disabled or deleted.
   * @param unacceptableReasons
   *          A list that can be used to hold messages about why the
   *          active instance cannot be disabled or deleted..
   * @return Returns <code>true</code> if the active instance can be
   *         disabled or deleted.
   */
  protected abstract boolean isDisableInstanceAcceptable(C config,
      List<Message> unacceptableReasons);



  /**
   * Determines whether or not the active configuration is enabled or
   * not.
   * <p>
   * Sub-classes should usually implement this method using a call to
   * the configuration's <code>isEnabled()</code> method.
   *
   * @param config
   *          The active configuration.
   * @return Returns <code>true</code> if the configuration is
   *         enabled.
   */
  protected abstract boolean isEnabled(C config);



  /**
   * Notify that the configuration has been deleted. This may involve
   * logging a message. Implementations do not need to worry about
   * finalizing the instance, since that is performed by the
   * {@link #finalizeInstance(Object)} method.
   *
   * @param config
   *          The deleted configuration.
   */
  protected abstract void notifyDeleteInstance(C config);



  /**
   * Notify that the configuration has been disabled. This may involve
   * logging a message. Implementations do not need to worry about
   * finalizing the instance, since that is performed by the
   * {@link #finalizeInstance(Object)} method.
   *
   * @param config
   *          The disabled configuration.
   */
  protected abstract void notifyDisableInstance(C config);



  /**
   * Register to be notified when the configuration is added.
   *
   * @param listener
   *          The configuration add listener.
   * @throws ConfigException
   *           If the add listener could not be registered.
   */
  protected abstract void registerAddListener(
      ConfigurationAddListener<C> listener) throws ConfigException;



  /**
   * Register to be notified when a configuration is changed.
   *
   * @param listener
   *          The configuration change listener.
   * @param config
   *          Receive change notifications for this configuration.
   */
  protected abstract void registerChangeListener(
      ConfigurationChangeListener<C> listener, C config);



  /**
   * Register to be notified when the configuration is deleted.
   *
   * @param listener
   *          The configuration delete listener.
   * @throws ConfigException
   *           If the add listener could not be registered.
   */
  protected abstract void registerDeleteListener(
      ConfigurationDeleteListener<C> listener) throws ConfigException;



  /**
   * Registers a newly activated instance. This may involve updating
   * the parent configuration, starting a thread, etc. This manager
   * will have already invoked <code>initialize(C config)</code>
   * against the instance.
   *
   * @param instance
   *          The new instance.
   * @throws InitializationException
   *           If a problem occurs during initialization.
   */
  protected abstract void registerInstance(T instance)
      throws InitializationException;



  // Notify listeners that a new instance has been added and/or
  // enabled.
  private void doRegisterInstance(T instance)
      throws InitializationException {
    this.instance = instance;

    // Let sub-class implementation decide what to do with the new
    // instance.
    registerInstance(instance);
  }



  // Load, instantiate, and initialize the class named in the Java
  // implementation class property of the provided configuration.
  private T getImplementation(C config) throws ConfigException {
    String className = getJavaImplementationClass(config);

    // Load the class and cast it to a T.
    Class<? extends T> implClass;
    T instance;

    try {
      implClass = propertyDefinition.loadClass(className, theClass);
      instance = implClass.newInstance();
    } catch (Exception e) {
      Message message = AdminMessages.ERR_ADMIN_CANNOT_INSTANTIATE_CLASS.
          get(String.valueOf(className), String.valueOf(config.dn()),
              stackTraceToSingleLineString(e));
      throw new ConfigException(message, e);
    }

    // Perform the necessary initialization for the instance.
    try {
      // Determine the initialization method to use: it must take a
      // single parameter which is the exact type of the configuration
      // object.
      String name = getInitializationMethodName();
      Method method = implClass.getMethod(name, config.definition()
          .getServerConfigurationClass());

      method.invoke(instance, config);
    } catch (Exception e) {
      Message message = AdminMessages.ERR_ADMIN_CANNOT_INITIALIZE_COMPONENT.
          get(String.valueOf(className), String.valueOf(config.dn()),
              stackTraceToSingleLineString(e));
      throw new ConfigException(message, e);
    }

    // The instance has been successfully initialized.
    return instance;
  }



  // Get the name of the method which should be used
  // to initialize instances.
  private String getInitializationMethodName() {
    return "initialize" + theClass.getSimpleName();
  }



  // Determines whether or not the new configuration's implementation
  // class is acceptable.
  private boolean isJavaClassAcceptable(C config,
      List<Message> unacceptableReasons) {
    String className = getJavaImplementationClass(config);

    // Load the class and cast it to a T.
    Class<? extends T> implClass;

    try {
      implClass = propertyDefinition.loadClass(className, theClass);
      implClass.newInstance();
    } catch (Exception e) {
      unacceptableReasons.add(
              AdminMessages.ERR_ADMIN_CANNOT_INSTANTIATE_CLASS.get(
                      String.valueOf(className),
                      String.valueOf(config.dn()),
                      stackTraceToSingleLineString(e)));
      return false;
    }

    // Perform the necessary initialization for the instance.
    try {
      // Determine the initialization method to use: it must take a
      // single parameter which is the exact type of the configuration
      // object.
      String name = getInitializationMethodName();
      implClass.getMethod(name, config.definition()
          .getServerConfigurationClass());
    } catch (Exception e) {
      unacceptableReasons.add(
              AdminMessages.ERR_ADMIN_CANNOT_INITIALIZE_COMPONENT.get(
                      String.valueOf(className), String.valueOf(config.dn()),
                      stackTraceToSingleLineString(e)));
      return false;
    }

    // The class is valid as far as we can tell.
    return true;
  }



  // Set the new configuration and register for change events.
  private void setConfiguration(C config) {
    // We only need to register for change events if there was no
    // previous configuration. This is because the notification
    // frameworks ensures that listeners are preserved when a new
    // configuration replaces an old one.
    if (currentConfig == null && config != null) {
      registerChangeListener(changeListener, config);
    }
    currentConfig = config;
  }
}
