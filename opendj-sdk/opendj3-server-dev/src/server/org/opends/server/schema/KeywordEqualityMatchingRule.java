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
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014 ForgeRock AS
 */
package org.opends.server.schema;

import java.util.Collection;
import java.util.Collections;

import static org.opends.server.schema.SchemaConstants.*;

/**
 * This class implements the keywordMatch matching rule defined in X.520.  That
 * document defines "keyword" as implementation-specific, but in this case we
 * will interpret it in the same way as "word" for the wordMatch rule.
 */
class KeywordEqualityMatchingRule
       extends WordEqualityMatchingRule
{
  /**
   * Creates a new instance of this wordMatch matching rule.
   */
  public KeywordEqualityMatchingRule()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public Collection<String> getNames()
  {
    return Collections.singleton(EMR_KEYWORD_NAME);
  }


  /**
   * Retrieves the OID for this matching rule.
   *
   * @return  The OID for this matching rule.
   */
  @Override
  public String getOID()
  {
    return EMR_KEYWORD_OID;
  }
}

