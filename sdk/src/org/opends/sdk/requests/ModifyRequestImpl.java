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

package org.opends.sdk.requests;



import java.util.LinkedList;
import java.util.List;

import org.opends.sdk.Change;
import org.opends.sdk.DN;
import org.opends.sdk.LinkedAttribute;
import org.opends.sdk.ModificationType;
import org.opends.sdk.ldif.ChangeRecordVisitor;

import com.sun.opends.sdk.util.LocalizedIllegalArgumentException;
import com.sun.opends.sdk.util.Validator;



/**
 * Modify request implementation.
 */
final class ModifyRequestImpl extends
    AbstractRequestImpl<ModifyRequest> implements ModifyRequest
{
  private final List<Change> changes = new LinkedList<Change>();

  private DN name;



  /**
   * Creates a new modify request using the provided distinguished name.
   *
   * @param name
   *          The distinguished name of the entry to be modified.
   * @throws NullPointerException
   *           If {@code name} was {@code null}.
   */
  ModifyRequestImpl(DN name) throws NullPointerException
  {
    this.name = name;
  }



  /**
   * {@inheritDoc}
   */
  public <R, P> R accept(ChangeRecordVisitor<R, P> v, P p)
  {
    return v.visitChangeRecord(p, this);
  }



  /**
   * {@inheritDoc}
   */
  public ModifyRequest addChange(Change change)
      throws UnsupportedOperationException, NullPointerException
  {
    Validator.ensureNotNull(change);
    changes.add(change);
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public ModifyRequest addChange(ModificationType type,
      String attributeDescription, Object... values)
      throws LocalizedIllegalArgumentException,
      UnsupportedOperationException, NullPointerException
  {
    Validator.ensureNotNull(type, attributeDescription, values);
    changes.add(new Change(type, new LinkedAttribute(
        attributeDescription, values)));
    return this;
  }



  public ModifyRequest addChange(ModificationType type,
      String attributeDescription, Object firstValue,
      Object... remainingValues)
      throws LocalizedIllegalArgumentException,
      UnsupportedOperationException, NullPointerException
  {
    // TODO Auto-generated method stub
    return null;
  }



  /**
   * {@inheritDoc}
   */
  public ModifyRequest clearChanges()
      throws UnsupportedOperationException
  {
    changes.clear();
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public int getChangeCount()
  {
    return changes.size();
  }



  /**
   * {@inheritDoc}
   */
  public Iterable<Change> getChanges()
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
  public boolean hasChanges()
  {
    return !changes.isEmpty();
  }



  /**
   * {@inheritDoc}
   */
  public ModifyRequest setName(DN dn)
      throws UnsupportedOperationException, NullPointerException
  {
    Validator.ensureNotNull(dn);
    this.name = dn;
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public ModifyRequest setName(String dn)
      throws LocalizedIllegalArgumentException,
      UnsupportedOperationException, NullPointerException
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
    builder.append(getChanges());
    builder.append(", controls=");
    builder.append(getControls());
    builder.append(")");
    return builder.toString();
  }



  ModifyRequest getThis()
  {
    return this;
  }

}
