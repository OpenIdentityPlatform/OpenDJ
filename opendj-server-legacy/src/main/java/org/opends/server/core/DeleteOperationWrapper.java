/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.core;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;

/**
 * This abstract class wraps/decorates a given delete operation.
 * This class will be extended by sub-classes to enhance the
 * functionality of the DeleteOperationBasis.
 */
public abstract class DeleteOperationWrapper extends
    OperationWrapper<DeleteOperation> implements DeleteOperation
{
  /**
   * Creates a new delete operation based on the provided delete operation.
   *
   * @param delete The delete operation to wrap
   */
  public DeleteOperationWrapper(DeleteOperation delete)
  {
    super(delete);
  }

  @Override
  public DN getEntryDN()
  {
    return getOperation().getEntryDN();
  }

  @Override
  public ByteString getRawEntryDN()
  {
    return getOperation().getRawEntryDN();
  }

  @Override
  public void setRawEntryDN(ByteString rawEntryDN)
  {
    getOperation().setRawEntryDN(rawEntryDN);
  }

  @Override
  public String toString()
  {
    return getOperation().toString();
  }
}
