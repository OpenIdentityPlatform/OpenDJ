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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.opends.sdk.schema;



/**
 * This class provides schema-local variables. These variables differ
 * from their normal counterparts in that each schema has its own
 * independently initialized copy of the variable. {@code SchemaLocal}
 * instances are typically private static fields in classes that wish to
 * associate state with a schema (e.g., a schema dependent cache).
 * 
 * @param <T>
 *          The type of the schema-local variable.
 */
public class SchemaLocal<T>
{
  /**
   * Creates a schema-local variable.
   */
  public SchemaLocal()
  {
    // Nothing to do.
  }



  /**
   * Returns the value in the provided schema's copy of this
   * schema-local variable. If the variable has no value associated with
   * the schema, it is first initialized to the value returned by an
   * invocation of the {@link #initialValue} method.
   * 
   * @param schema
   *          The schema whose copy of the schema-local variable is
   *          being requested.
   * @return The schema-local value.
   */
  public final T get(Schema schema)
  {
    // Schema calls back to initialValue() if this is the first time.
    return schema.getAttachment(this);
  }



  /**
   * Removes the provided schema's value for this schema-local variable.
   * If this schema-local variable is subsequently read, its value will
   * be reinitialized by invoking its {@link #initialValue} method,
   * unless its value is set in the interim. This may result in multiple
   * invocations of the {@link #initialValue} method.
   * 
   * @param schema
   *          The schema whose copy of the schema-local variable is
   *          being removed.
   */
  public final void remove(Schema schema)
  {
    schema.removeAttachment(this);
  }



  /**
   * Sets the provided schema's copy of this schema-local variable to
   * the specified value.
   * 
   * @param schema
   *          The schema whose copy of the schema-local variable is
   *          being set.
   * @param value
   *          The schema-local value.
   */
  public final void set(Schema schema, T value)
  {
    schema.setAttachment(this, value);
  }



  /**
   * Returns the provided schema's "initial value" for this schema-local
   * variable. This method will be invoked the first time the variable
   * is accessed with the {@link #get} method for each schema, unless
   * the {@link #set} method has been previously invoked, in which case
   * the {@link #initialValue} method will not be invoked.
   * <p>
   * Normally, this method is invoked at most once per schema, but it
   * may be invoked again in case of subsequent invocations of
   * {@link #remove} followed by {@link #get}. This implementation
   * simply returns {@code null}; if the programmer desires schema-local
   * variables to have an initial value other than {@code null}, {@code
   * SchemaLocal} must be subclassed, and this method overridden.
   * Typically, an anonymous inner class will be used.
   * 
   * @return The initial value for this schema-local.
   */
  protected T initialValue()
  {
    // Default implementation.
    return null;
  }
}
