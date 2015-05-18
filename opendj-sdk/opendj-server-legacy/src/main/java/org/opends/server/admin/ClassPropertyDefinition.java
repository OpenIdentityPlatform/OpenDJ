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
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS.
 */

package org.opends.server.admin;



import static org.forgerock.util.Reject.ifNull;
import static org.opends.server.admin.PropertyException.*;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;



/**
 * Class property definition.
 * <p>
 * A class property definition defines a property whose values
 * represent a Java class. It is possible to restrict the type of java
 * class by specifying "instance of" constraints.
 * <p>
 * Note that in a client/server environment, the client is probably
 * not capable of validating the Java class (e.g. it will not be able
 * to load it nor have access to the interfaces it is supposed to
 * implement). For this reason, it is possible to switch off
 * validation in the client by calling the static method
 * {@link #setAllowClassValidation(boolean)}.
 */
public final class ClassPropertyDefinition extends PropertyDefinition<String> {

  /**
   * An interface for incrementally constructing class property
   * definitions.
   */
  public static class Builder extends
      AbstractBuilder<String, ClassPropertyDefinition> {

    /** List of interfaces which property values must implement. */
    private List<String> instanceOfInterfaces;



    /** Private constructor. */
    private Builder(
        AbstractManagedObjectDefinition<?, ?> d, String propertyName) {
      super(d, propertyName);

      this.instanceOfInterfaces = new LinkedList<String>();
    }



    /**
     * Add an class name which property values must implement.
     *
     * @param className
     *          The name of a class which property values must
     *          implement.
     */
    public final void addInstanceOf(String className) {
      ifNull(className);

      /*
       * Do some basic checks to make sure the string representation is valid.
       */
      String value = className.trim();
      if (!value.matches(CLASS_RE)) {
        throw new IllegalArgumentException("\"" + value
            + "\" is not a valid Java class name");
      }

      /*
       * If possible try and load the class in order to perform additional
       * validation.
       */
      if (isAllowClassValidation()) {
        /*
         * Check that the class can be loaded so that validation can be
         * performed.
         */
        try {
          loadClass(value, true);
        } catch (ClassNotFoundException e) {
          // TODO: can we do something better here?
          throw new RuntimeException(e);
        }
      }

      instanceOfInterfaces.add(value);
    }



    /** {@inheritDoc} */
    @Override
    protected ClassPropertyDefinition buildInstance(
        AbstractManagedObjectDefinition<?, ?> d,
        String propertyName, EnumSet<PropertyOption> options,
        AdministratorAction adminAction,
        DefaultBehaviorProvider<String> defaultBehavior) {
      return new ClassPropertyDefinition(d, propertyName, options,
          adminAction, defaultBehavior, instanceOfInterfaces);
    }

  }

  /** Regular expression for validating class names. */
  private static final String CLASS_RE =
    "^([A-Za-z][A-Za-z0-9_]*\\.)*[A-Za-z][A-Za-z0-9_]*(\\$[A-Za-z0-9_]+)*$";

  /**
   * Flag indicating whether class property values should be validated.
   */
  private static boolean allowClassValidation = true;



  /**
   * Create a class property definition builder.
   *
   * @param d
   *          The managed object definition associated with this
   *          property definition.
   * @param propertyName
   *          The property name.
   * @return Returns the new class property definition builder.
   */
  public static Builder createBuilder(
      AbstractManagedObjectDefinition<?, ?> d, String propertyName) {
    return new Builder(d, propertyName);
  }



  /**
   * Determine whether or not class property definitions should
   * validate class name property values. Validation involves checking
   * that the class exists and that it implements the required
   * interfaces.
   *
   * @return Returns <code>true</code> if class property definitions
   *         should validate class name property values.
   */
  public static boolean isAllowClassValidation() {
    return allowClassValidation;
  }



  /**
   * Specify whether or not class property definitions should validate
   * class name property values. Validation involves checking that the
   * class exists and that it implements the required interfaces.
   * <p>
   * By default validation is switched on.
   *
   * @param value
   *          <code>true</code> if class property definitions should
   *          validate class name property values.
   */
  public static void setAllowClassValidation(boolean value) {
    allowClassValidation = value;
  }



  /** Load a named class. */
  private static Class<?> loadClass(String className, boolean initialize)
      throws ClassNotFoundException, LinkageError {
    return Class.forName(className, initialize, ClassLoaderProvider
        .getInstance().getClassLoader());
  }

  /** List of interfaces which property values must implement. */
  private final List<String> instanceOfInterfaces;



  /** Private constructor. */
  private ClassPropertyDefinition(
      AbstractManagedObjectDefinition<?, ?> d, String propertyName,
      EnumSet<PropertyOption> options,
      AdministratorAction adminAction,
      DefaultBehaviorProvider<String> defaultBehavior,
      List<String> instanceOfInterfaces) {
    super(d, String.class, propertyName, options, adminAction, defaultBehavior);

    this.instanceOfInterfaces = Collections
        .unmodifiableList(new LinkedList<String>(instanceOfInterfaces));
  }



  /** {@inheritDoc} */
  @Override
  public <R, P> R accept(PropertyDefinitionVisitor<R, P> v, P p) {
    return v.visitClass(this, p);
  }



  /** {@inheritDoc} */
  @Override
  public <R, P> R accept(PropertyValueVisitor<R, P> v, String value, P p) {
    return v.visitClass(this, value, p);
  }



  /** {@inheritDoc} */
  @Override
  public String decodeValue(String value)
      throws PropertyException {
    ifNull(value);

    try {
      validateValue(value);
    } catch (PropertyException e) {
      throw illegalPropertyValueException(this, value, e.getCause());
    }

    return value;
  }



  /**
   * Get an unmodifiable list of classes which values of this property
   * must implement.
   *
   * @return Returns an unmodifiable list of classes which values of
   *         this property must implement.
   */
  public List<String> getInstanceOfInterface() {
    return instanceOfInterfaces;
  }



  /**
   * Validate and load the named class, and cast it to a subclass of
   * the specified class.
   *
   * @param <T>
   *          The requested type.
   * @param className
   *          The name of the class to validate and load.
   * @param instanceOf
   *          The class representing the requested type.
   * @return Returns the named class cast to a subclass of the
   *         specified class.
   * @throws PropertyException
   *           If the named class was invalid, could not be loaded, or
   *           did not implement the required interfaces.
   * @throws ClassCastException
   *           If the referenced class does not implement the
   *           requested type.
   */
  public <T> Class<? extends T> loadClass(String className,
      Class<T> instanceOf) throws PropertyException,
      ClassCastException {
    ifNull(className, instanceOf);

    // Make sure that the named class is valid.
    validateClassName(className);
    Class<?> theClass = validateClassInterfaces(className, true);

    // Cast it to the required type.
    return theClass.asSubclass(instanceOf);
  }



  /** {@inheritDoc} */
  @Override
  public String normalizeValue(String value)
      throws PropertyException {
    ifNull(value);

    return value.trim();
  }



  /** {@inheritDoc} */
  @Override
  public void validateValue(String value)
      throws PropertyException {
    ifNull(value);

    // Always make sure the name is a valid class name.
    validateClassName(value);

    /*
     * If additional validation is enabled then attempt to load the class and
     * check the interfaces that it implements/extends.
     */
    if (allowClassValidation) {
      validateClassInterfaces(value, false);
    }
  }



  /**
   * Make sure that named class implements the interfaces named by this
   * definition.
   */
  private Class<?> validateClassInterfaces(String className, boolean initialize)
      throws PropertyException {
    Class<?> theClass = loadClassForValidation(className, className,
        initialize);
    for (String i : instanceOfInterfaces) {
      Class<?> instanceOfClass = loadClassForValidation(className, i,
          initialize);
      if (!instanceOfClass.isAssignableFrom(theClass)) {
        throw PropertyException.illegalPropertyValueException(this, className);
      }
    }
    return theClass;
  }



  private Class<?> loadClassForValidation(String componentClassName,
      String classToBeLoaded, boolean initialize) {
    try {
      return loadClass(classToBeLoaded.trim(), initialize);
    } catch (ClassNotFoundException | LinkageError e) {
      // If the class cannot be loaded then it is an invalid value.
      throw illegalPropertyValueException(this, componentClassName, e);
    }
  }



  /**
   * Do some basic checks to make sure the string representation is valid.
   */
  private void validateClassName(String className)
      throws PropertyException {
    String nvalue = className.trim();
    if (!nvalue.matches(CLASS_RE)) {
      throw PropertyException.illegalPropertyValueException(this, className);
    }
  }
}
