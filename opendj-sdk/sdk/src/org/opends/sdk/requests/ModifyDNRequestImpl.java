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
import org.opends.sdk.RDN;
import org.opends.sdk.ldif.ChangeRecordVisitor;
import org.opends.sdk.util.LocalizedIllegalArgumentException;
import org.opends.sdk.util.Validator;



/**
 * Modify DN request implementation.
 */
final class ModifyDNRequestImpl extends
    AbstractRequestImpl<ModifyDNRequest> implements ModifyDNRequest
{
  private DN name;

  private DN newSuperior = null;

  private RDN newRDN;

  private boolean deleteOldRDN = false;



  /**
   * Creates a new modify DN request using the provided distinguished
   * name and new RDN.
   *
   * @param name
   *          The distinguished name of the entry to be renamed.
   * @param newRDN
   *          The new RDN of the entry.
   * @throws NullPointerException
   *           If {@code name} or {@code newRDN} was {@code null}.
   */
  ModifyDNRequestImpl(DN name, RDN newRDN) throws NullPointerException
  {
    this.name = name;
    this.newRDN = newRDN;
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
  public RDN getNewRDN()
  {
    return newRDN;
  }



  /**
   * {@inheritDoc}
   */
  public DN getNewSuperior()
  {
    return newSuperior;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isDeleteOldRDN()
  {
    return deleteOldRDN;
  }



  /**
   * {@inheritDoc}
   */
  public ModifyDNRequestImpl setDeleteOldRDN(boolean deleteOldRDN)
      throws UnsupportedOperationException
  {
    this.deleteOldRDN = deleteOldRDN;
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public ModifyDNRequest setName(DN dn)
      throws UnsupportedOperationException, NullPointerException
  {
    Validator.ensureNotNull(dn);
    this.name = dn;
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public ModifyDNRequest setName(String dn)
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
  public ModifyDNRequest setNewRDN(RDN rdn)
      throws UnsupportedOperationException, NullPointerException
  {
    Validator.ensureNotNull(rdn);
    this.newRDN = rdn;
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public ModifyDNRequest setNewRDN(String rdn)
      throws LocalizedIllegalArgumentException,
      UnsupportedOperationException, NullPointerException
  {
    Validator.ensureNotNull(rdn);
    this.newRDN = RDN.valueOf(rdn);
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public ModifyDNRequest setNewSuperior(DN dn)
      throws UnsupportedOperationException
  {
    this.newSuperior = dn;
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public ModifyDNRequest setNewSuperior(String dn)
      throws LocalizedIllegalArgumentException,
      UnsupportedOperationException
  {
    this.newSuperior = (dn != null) ? DN.valueOf(dn) : null;
    return this;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    final StringBuilder builder = new StringBuilder();
    builder.append("ModifyDNRequest(name=");
    builder.append(getName());
    builder.append(", newRDN=");
    builder.append(getNewRDN());
    builder.append(", deleteOldRDN=");
    builder.append(isDeleteOldRDN());
    builder.append(", newSuperior=");
    builder.append(String.valueOf(getNewSuperior()));
    builder.append(", controls=");
    builder.append(getControls());
    builder.append(")");
    return builder.toString();
  }



  ModifyDNRequest getThis()
  {
    return this;
  }

}
