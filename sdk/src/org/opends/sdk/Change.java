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

package org.opends.sdk;



import org.opends.sdk.util.ByteString;
import org.opends.sdk.util.Validator;



/**
 * A modification to be performed on an entry during a Modify operation.
 * <p>
 * TODO: other constructors.
 */
public final class Change
{
  private final ModificationType modificationType;

  private final Attribute attribute;



  /**
   * Creates a new modification having the provided modification type
   * and attribute values to be updated. Note that while the returned
   * {@code Change} is immutable, the underlying attribute may not be.
   * The following code ensures that the returned {@code Change} is
   * fully immutable:
   *
   * <pre>
   * Change change =
   *     new Change(modificationType, Types.unmodifiableAttribute(attribute));
   * </pre>
   *
   * @param modificationType
   *          The type of change to be performed.
   * @param attribute
   *          The the attribute containing the values to be modified.
   */
  public Change(ModificationType modificationType, Attribute attribute)
  {
    Validator.ensureNotNull(modificationType, attribute);

    this.modificationType = modificationType;
    this.attribute = attribute;
  }



  /**
   * Returns the type of change to be performed.
   *
   * @return The type of change to be performed.
   */
  public ModificationType getModificationType()
  {
    return modificationType;
  }



  /**
   * Returns the attribute containing the values to be modified.
   *
   * @return The the attribute containing the values to be modified.
   */
  public Attribute getAttribute()
  {
    return attribute;
  }



  /**
   * {@inheritDoc}
   */
  public String toString()
  {
    StringBuilder builder = new StringBuilder();
    builder.append("Change(modificationType=");
    builder.append(modificationType);
    builder.append(", attributeDescription=");
    builder.append(attribute.getAttributeDescriptionAsString());
    builder.append(", attributeValues={");
    boolean firstValue = true;
    for (ByteString value : attribute)
    {
      if (!firstValue)
      {
        builder.append(", ");
      }
      builder.append(value);
      firstValue = false;
    }
    builder.append("})");
    return builder.toString();
  }

}
