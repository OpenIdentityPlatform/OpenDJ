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



/**
 * Unmodifiable modify DN request implementation.
 */
final class UnmodifiableModifyDNRequestImpl extends
    AbstractUnmodifiableRequest<ModifyDNRequest> implements ModifyDNRequest
{
  UnmodifiableModifyDNRequestImpl(ModifyDNRequest impl)
  {
    super(impl);
  }



  public <R, P> R accept(ChangeRecordVisitor<R, P> v, P p)
  {
    return v.visitChangeRecord(p, this);
  }



  public DN getName()
  {
    return impl.getName();
  }



  public RDN getNewRDN()
  {
    return impl.getNewRDN();
  }



  public DN getNewSuperior()
  {
    return impl.getNewSuperior();
  }



  public boolean isDeleteOldRDN()
  {
    return impl.isDeleteOldRDN();
  }



  public ModifyDNRequest setDeleteOldRDN(boolean deleteOldRDN)
  {
    throw new UnsupportedOperationException();
  }



  public ModifyDNRequest setName(DN dn)
  {
    throw new UnsupportedOperationException();
  }



  public ModifyDNRequest setName(String dn)
  {
    throw new UnsupportedOperationException();
  }



  public ModifyDNRequest setNewRDN(RDN rdn)
  {
    throw new UnsupportedOperationException();
  }



  public ModifyDNRequest setNewRDN(String rdn)
  {
    throw new UnsupportedOperationException();
  }



  public ModifyDNRequest setNewSuperior(DN dn)
  {
    throw new UnsupportedOperationException();
  }



  public ModifyDNRequest setNewSuperior(String dn)
  {
    throw new UnsupportedOperationException();
  }
}
