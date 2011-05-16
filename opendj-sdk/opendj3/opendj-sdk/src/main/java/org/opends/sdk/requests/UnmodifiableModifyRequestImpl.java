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

import java.util.Collections;
import java.util.List;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.opends.sdk.*;
import org.opends.sdk.ldif.ChangeRecordVisitor;

import com.sun.opends.sdk.util.Collections2;
import com.sun.opends.sdk.util.Function;
import com.sun.opends.sdk.util.Functions;

/**
 * Unmodifiable modify request implementation.
 */
final class UnmodifiableModifyRequestImpl
    extends AbstractUnmodifiableRequest<ModifyRequest>
    implements ModifyRequest
{
  UnmodifiableModifyRequestImpl(ModifyRequest impl) {
    super(impl);
  }

  public <R, P> R accept(ChangeRecordVisitor<R, P> v, P p) {
    return v.visitChangeRecord(p, this);
  }

  public ModifyRequest addModification(Modification modification)
      throws UnsupportedOperationException, NullPointerException {
    throw new UnsupportedOperationException();
  }

  public ModifyRequest addModification(ModificationType type,
                                       String attributeDescription,
                                       Object... values)
      throws LocalizedIllegalArgumentException,
      UnsupportedOperationException, NullPointerException {
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

    List<Modification> unmodifiableModifications = Collections2.transformedList(
        impl.getModifications(), function,
        Functions.<Modification> identityFunction());
    return Collections.unmodifiableList(unmodifiableModifications);
  }

  public DN getName() {
    return impl.getName();
  }

  public ModifyRequest setName(DN dn)
      throws UnsupportedOperationException, NullPointerException {
    throw new UnsupportedOperationException();
  }

  public ModifyRequest setName(String dn)
      throws LocalizedIllegalArgumentException,
      UnsupportedOperationException, NullPointerException {
    throw new UnsupportedOperationException();
  }
}
