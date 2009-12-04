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

package org.opends.sdk.schema;



import static org.opends.sdk.schema.SchemaConstants.*;

import org.opends.sdk.ByteSequence;

import com.sun.opends.sdk.util.MessageBuilder;



/**
 * This class implements the fax attribute syntax. This should be
 * restricted to holding only fax message contents, but we will accept
 * any set of bytes. It will be treated much like the octet string
 * attribute syntax.
 */
final class FaxSyntaxImpl extends AbstractSyntaxImpl
{

  @Override
  public String getEqualityMatchingRule()
  {
    return EMR_OCTET_STRING_OID;
  }



  public String getName()
  {
    return SYNTAX_FAX_NAME;
  }



  @Override
  public String getOrderingMatchingRule()
  {
    return OMR_OCTET_STRING_OID;
  }



  public boolean isHumanReadable()
  {
    return false;
  }



  /**
   * Indicates whether the provided value is acceptable for use in an
   * attribute with this syntax. If it is not, then the reason may be
   * appended to the provided buffer.
   * 
   * @param schema
   *          The schema in which this syntax is defined.
   * @param value
   *          The value for which to make the determination.
   * @param invalidReason
   *          The buffer to which the invalid reason should be appended.
   * @return <CODE>true</CODE> if the provided value is acceptable for
   *         use with this syntax, or <CODE>false</CODE> if not.
   */
  public boolean valueIsAcceptable(Schema schema, ByteSequence value,
      MessageBuilder invalidReason)
  {
    // All values will be acceptable for the fax syntax.
    return true;
  }
}
