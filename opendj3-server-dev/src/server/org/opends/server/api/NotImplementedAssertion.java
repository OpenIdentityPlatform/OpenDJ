/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *      Copyright 2014 ForgeRock AS
 */
package org.opends.server.api;

import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.spi.IndexQueryFactory;

/**
 * Avoids repeating again and again the same code when
 * Assertion.createIndexQuery() is not implemented.
 * <p>
 * To be removed once we switch the schema to the SDK.
 */
public class NotImplementedAssertion implements Assertion
{

  /** {@inheritDoc} */
  @Override
  public ConditionResult matches(ByteSequence normalizedAttributeValue)
  {
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public <T> T createIndexQuery(IndexQueryFactory<T> factory)
      throws DecodeException
  {
    throw new RuntimeException("Not implemented");
  }

}
