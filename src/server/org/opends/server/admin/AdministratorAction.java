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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.admin;
import org.opends.messages.Message;



import java.util.Locale;
import java.util.MissingResourceException;



/**
 * Defines an optional action which administators must perform after
 * they have modified a property. By default modifications to
 * properties are assumed to take effect immediately and require no
 * additional administrative action. Developers should be aware that,
 * where feasible, they should implement components such that property
 * modifications require no additional administrative action. This is
 * required in order to minimize server downtime during administration
 * and provide a more user-friendly experience.
 */
public final class AdministratorAction {

  /**
   * Specifies the type of administrator action which must be
   * performed in order for pending changes to take effect.
   */
  public static enum Type {
    /**
     * Used when modifications to a property require a component
     * restart in order to take effect (usually by disabling and
     * re-enabling the component). May have a description describing
     * any additional administrator action that is required when the
     * component is restarted.
     */
    COMPONENT_RESTART("component-restart"),

    /**
     * Used when modifications to a property take effect immediately,
     * and no additional administrator action is required. May have a
     * description describing how changes to the modified property
     * will take effect.
     */
    NONE("none"),

    /**
     * Used when modifications to a property require an additional
     * administrative action in order to take effect. This should be
     * used when neither a server restart nor a component restart are
     * applicable. Always has a description which describes the
     * additional administrator action which is required when the
     * property is modified.
     */
    OTHER("other"),

    /**
     * Used when modifications to a property require a server restart
     * in order to take effect. May have a description describing any
     * additional administrator action that is required when the
     * component is restarted.
     */
    SERVER_RESTART("server-restart");

    // The user-friendly name of the type.
    private final String name;



    // Private constructor.
    private Type(String name) {
      this.name = name;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
      return name;
    }

  }

  // The managed object definition associated with this administrator
  // action.
  private final AbstractManagedObjectDefinition<?, ?> definition;

  // The name of the property definition associated with this
  // administrator action.
  private final String propertyName;

  // The type of administration action.
  private final Type type;



  /**
   * Create a new administrator action.
   *
   * @param type
   *          The type of this administration action.
   * @param d
   *          The managed object definition associated with this
   *          administrator action.
   * @param propertyName
   *          The name of the property definition associated with this
   *          administrator action.
   */
  public AdministratorAction(Type type,
      AbstractManagedObjectDefinition<?, ?> d, String propertyName) {
    this.type = type;
    this.definition = d;
    this.propertyName = propertyName;
  }



  /**
   * Gets the synopsis of this administrator action in the default
   * locale.
   *
   * @return Returns the synopsis of this administrator action in the
   *         default locale, or <code>null</code> if there is no
   *         synopsis defined.
   */
  public final Message getSynopsis() {
    return getSynopsis(Locale.getDefault());
  }



  /**
   * Gets the synopsis of this administrator action in the specified
   * locale.
   *
   * @param locale
   *          The locale.
   * @return Returns the synopsis of this administrator action in the
   *         specified locale, or <code>null</code> if there is no
   *         synopsis defined.
   */
  public final Message getSynopsis(Locale locale) {
    ManagedObjectDefinitionI18NResource resource =
      ManagedObjectDefinitionI18NResource.getInstance();
    String property = "property." + propertyName
        + ".requires-admin-action.synopsis";
    try {
      return resource.getMessage(definition, property, locale);
    } catch (MissingResourceException e) {
      return null;
    }
  }



  /**
   * Gets the type of this administrator action.
   *
   * @return Returns the type of this administrator action.
   */
  public final Type getType() {
    return type;
  }
}
