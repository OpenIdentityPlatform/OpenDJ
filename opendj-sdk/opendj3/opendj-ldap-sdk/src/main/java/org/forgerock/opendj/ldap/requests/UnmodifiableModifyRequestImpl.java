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



import java.util.Collections;
import java.util.List;

import org.forgerock.opendj.ldap.*;
import org.forgerock.opendj.ldif.ChangeRecordVisitor;

import com.forgerock.opendj.util.Collections2;
import com.forgerock.opendj.util.Function;
import com.forgerock.opendj.util.Functions;



/**
 * Unmodifiable modify request implementation.
 */
final class UnmodifiableModifyRequestImpl extends
    AbstractUnmodifiableRequest<ModifyRequest> implements ModifyRequest
{
  UnmodifiableModifyRequestImpl(ModifyRequest impl)
  {
    super(impl);
  }



  public <R, P> R accept(ChangeRecordVisitor<R, P> v, P p)
  {
    return v.visitChangeRecord(p, this);
  }



  public ModifyRequest addModification(Modification modification)
  {
    throw new UnsupportedOperationException();
  }



  public ModifyRequest addModification(ModificationType type,
      String attributeDescription, Object... values)
  {
    throw new UnsupportedOperationException();
  }



  public List<Modification> getModifications()
  {
    // We need to make all attributes unmodifiable as well.
    Function<Modification, Modification, Void> function =
        new Function<Modification, Modification, Void>()
    {

      public Modification apply(Modification value, Void p)
      {
        ModificationType type = value.getModificationType();
        Attribute attribute = Attributes.unmodifiableAttribute(value
            .getAttribute());
        return new Modification(type, attribute);
      }

    };

    List<Modification> unmodifiableModifications = Collections2
        .transformedList(impl.getModifications(), function,
            Functions.<Modification> identityFunction());
    return Collections.unmodifiableList(unmodifiableModifications);
  }



  public DN getName()
  {
    return impl.getName();
  }



  public ModifyRequest setName(DN dn)
  {
    throw new UnsupportedOperationException();
  }



  public ModifyRequest setName(String dn)
  {
    throw new UnsupportedOperationException();
  }
}
