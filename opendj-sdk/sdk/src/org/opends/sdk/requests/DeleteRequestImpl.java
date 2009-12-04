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



import org.opends.sdk.DN;
import org.opends.sdk.ldif.ChangeRecordVisitor;

import com.sun.opends.sdk.util.LocalizedIllegalArgumentException;
import com.sun.opends.sdk.util.Validator;



/**
 * Delete request implementation.
 */
final class DeleteRequestImpl extends
    AbstractRequestImpl<DeleteRequest> implements DeleteRequest
{
  private DN name;



  /**
   * Creates a new delete request using the provided distinguished name.
   * 
   * @param name
   *          The distinguished name of the entry to be deleted.
   * @throws NullPointerException
   *           If {@code name} was {@code null}.
   */
  DeleteRequestImpl(DN name) throws NullPointerException
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
  public DN getName()
  {
    return name;
  }



  /**
   * {@inheritDoc}
   */
  public DeleteRequest setName(DN dn)
      throws UnsupportedOperationException, NullPointerException
  {
    Validator.ensureNotNull(dn);
    this.name = dn;
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public DeleteRequest setName(String dn)
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
    builder.append("DeleteRequest(name=");
    builder.append(getName());
    builder.append(", controls=");
    builder.append(getControls());
    builder.append(")");
    return builder.toString();
  }



  DeleteRequest getThis()
  {
    return this;
  }

}
