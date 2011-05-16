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
 *      Copyright 2010 Sun Microsystems, Inc.
 */

package org.opends.sdk.requests;



import java.util.LinkedList;
import java.util.List;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.opends.sdk.*;
import org.opends.sdk.ldif.ChangeRecordVisitor;

import com.sun.opends.sdk.util.Validator;



/**
 * Modify request implementation.
 */
final class ModifyRequestImpl extends AbstractRequestImpl<ModifyRequest>
    implements ModifyRequest
{
  private final List<Modification> changes = new LinkedList<Modification>();

  private DN name;



  /**
   * Creates a new modify request using the provided distinguished name.
   *
   * @param name
   *          The distinguished name of the entry to be modified.
   * @throws NullPointerException
   *           If {@code name} was {@code null}.
   */
  ModifyRequestImpl(final DN name) throws NullPointerException
  {
    this.name = name;
  }



  /**
   * Creates a new modify request that is an exact copy of the provided
   * request.
   *
   * @param modifyRequest
   *          The modify request to be copied.
   * @throws NullPointerException
   *           If {@code modifyRequest} was {@code null} .
   */
  ModifyRequestImpl(final ModifyRequest modifyRequest)
      throws NullPointerException
  {
    super(modifyRequest);
    this.name = modifyRequest.getName();

    // Deep copy.
    for (Modification modification : modifyRequest.getModifications())
    {
      ModificationType type = modification.getModificationType();
      Attribute attribute = new LinkedAttribute(modification.getAttribute());
      Modification copy = new Modification(type, attribute);
      this.changes.add(copy);
    }
  }



  /**
   * {@inheritDoc}
   */
  public <R, P> R accept(final ChangeRecordVisitor<R, P> v, final P p)
  {
    return v.visitChangeRecord(p, this);
  }



  public ModifyRequest addChange(final ModificationType type,
      final String attributeDescription, final Object firstValue,
      final Object... remainingValues)
      throws LocalizedIllegalArgumentException, UnsupportedOperationException,
      NullPointerException
  {
    // TODO Auto-generated method stub
    return null;
  }



  /**
   * {@inheritDoc}
   */
  public ModifyRequest addModification(final Modification change)
      throws UnsupportedOperationException, NullPointerException
  {
    Validator.ensureNotNull(change);
    changes.add(change);
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public ModifyRequest addModification(final ModificationType type,
      final String attributeDescription, final Object... values)
      throws LocalizedIllegalArgumentException, UnsupportedOperationException,
      NullPointerException
  {
    Validator.ensureNotNull(type, attributeDescription, values);
    changes.add(new Modification(type, new LinkedAttribute(
        attributeDescription, values)));
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public List<Modification> getModifications()
  {
    return changes;
  }



  /**
   * {@inheritDoc}
   */
  public DN getName()
  {
    return name;
  }



  /**
   * {@inheritDoc}
   */
  public ModifyRequest setName(final DN dn)
      throws UnsupportedOperationException, NullPointerException
  {
    Validator.ensureNotNull(dn);
    this.name = dn;
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public ModifyRequest setName(final String dn)
      throws LocalizedIllegalArgumentException, UnsupportedOperationException,
      NullPointerException
  {
    Validator.ensureNotNull(dn);
    this.name = DN.valueOf(dn);
    return this;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    final StringBuilder builder = new StringBuilder();
    builder.append("ModifyRequest(dn=");
    builder.append(getName());
    builder.append(", changes=");
    builder.append(getModifications());
    builder.append(", controls=");
    builder.append(getControls());
    builder.append(")");
    return builder.toString();
  }



  @Override
  ModifyRequest getThis()
  {
    return this;
  }

}
