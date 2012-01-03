/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions copyright 2012 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.requests;



import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.RDN;
import org.forgerock.opendj.ldif.ChangeRecordVisitor;

import com.forgerock.opendj.util.Validator;



/**
 * Modify DN request implementation.
 */
final class ModifyDNRequestImpl extends AbstractRequestImpl<ModifyDNRequest>
    implements ModifyDNRequest
{
  private DN name;

  private DN newSuperior = null;

  private RDN newRDN;

  private boolean deleteOldRDN = false;



  /**
   * Creates a new modify DN request using the provided distinguished name and
   * new RDN.
   *
   * @param name
   *          The distinguished name of the entry to be renamed.
   * @param newRDN
   *          The new RDN of the entry.
   * @throws NullPointerException
   *           If {@code name} or {@code newRDN} was {@code null}.
   */
  ModifyDNRequestImpl(final DN name, final RDN newRDN)
  {
    this.name = name;
    this.newRDN = newRDN;
  }



  /**
   * Creates a new modify DN request that is an exact copy of the provided
   * request.
   *
   * @param modifyDNRequest
   *          The modify DN request to be copied.
   * @throws NullPointerException
   *           If {@code modifyDNRequest} was {@code null} .
   */
  ModifyDNRequestImpl(final ModifyDNRequest modifyDNRequest)
  {
    super(modifyDNRequest);
    this.name = modifyDNRequest.getName();
    this.newSuperior = modifyDNRequest.getNewSuperior();
    this.newRDN = modifyDNRequest.getNewRDN();
    this.deleteOldRDN = modifyDNRequest.isDeleteOldRDN();
  }



  /**
   * {@inheritDoc}
   */
  public <R, P> R accept(final ChangeRecordVisitor<R, P> v, final P p)
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
  public ModifyDNRequestImpl setDeleteOldRDN(final boolean deleteOldRDN)
  {
    this.deleteOldRDN = deleteOldRDN;
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public ModifyDNRequest setName(final DN dn)
  {
    Validator.ensureNotNull(dn);
    this.name = dn;
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public ModifyDNRequest setName(final String dn)
  {
    Validator.ensureNotNull(dn);
    this.name = DN.valueOf(dn);
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public ModifyDNRequest setNewRDN(final RDN rdn)
  {
    Validator.ensureNotNull(rdn);
    this.newRDN = rdn;
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public ModifyDNRequest setNewRDN(final String rdn)
  {
    Validator.ensureNotNull(rdn);
    this.newRDN = RDN.valueOf(rdn);
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public ModifyDNRequest setNewSuperior(final DN dn)
  {
    this.newSuperior = dn;
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public ModifyDNRequest setNewSuperior(final String dn)
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



  @Override
  ModifyDNRequest getThis()
  {
    return this;
  }

}
