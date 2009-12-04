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



import org.opends.sdk.AttributeDescription;
import org.opends.sdk.ByteString;
import org.opends.sdk.DN;

import com.sun.opends.sdk.util.LocalizedIllegalArgumentException;
import com.sun.opends.sdk.util.Validator;



/**
 * Compare request implementation.
 */
final class CompareRequestImpl extends
    AbstractRequestImpl<CompareRequest> implements CompareRequest
{

  private AttributeDescription attributeDescription;

  private ByteString assertionValue;

  private DN name;



  /**
   * Creates a new compare request using the provided distinguished
   * name, attribute name, and assertion value.
   * 
   * @param name
   *          The distinguished name of the entry to be compared.
   * @param attributeDescription
   *          The name of the attribute to be compared.
   * @param assertionValue
   *          The assertion value to be compared.
   * @throws NullPointerException
   *           If {@code name}, {@code attributeDescription}, or {@code
   *           assertionValue} was {@code null}.
   */
  CompareRequestImpl(DN name,
      AttributeDescription attributeDescription,
      ByteString assertionValue) throws NullPointerException
  {
    this.name = name;
    this.attributeDescription = attributeDescription;
    this.assertionValue = assertionValue;
  }



  /**
   * {@inheritDoc}
   */
  public ByteString getAssertionValue()
  {
    return assertionValue;
  }



  /**
   * {@inheritDoc}
   */
  public String getAssertionValueAsString()
  {
    return assertionValue.toString();
  }



  /**
   * {@inheritDoc}
   */
  public AttributeDescription getAttributeDescription()
  {
    return attributeDescription;
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
  public CompareRequest setAssertionValue(ByteString value)
      throws UnsupportedOperationException, NullPointerException
  {
    Validator.ensureNotNull(value);
    this.assertionValue = value;
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public CompareRequest setAssertionValue(Object value)
      throws UnsupportedOperationException, NullPointerException
  {
    Validator.ensureNotNull(value);
    this.assertionValue = ByteString.valueOf(value);
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public CompareRequest setAttributeDescription(
      AttributeDescription attributeDescription)
      throws UnsupportedOperationException, NullPointerException
  {
    Validator.ensureNotNull(attributeDescription);
    this.attributeDescription = attributeDescription;
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public CompareRequest setAttributeDescription(
      String attributeDescription)
      throws LocalizedIllegalArgumentException,
      UnsupportedOperationException, NullPointerException
  {
    Validator.ensureNotNull(attributeDescription);
    this.attributeDescription = AttributeDescription
        .valueOf(attributeDescription);
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public CompareRequest setName(DN dn)
      throws UnsupportedOperationException, NullPointerException
  {
    Validator.ensureNotNull(dn);
    this.name = dn;
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public CompareRequest setName(String dn)
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
    builder.append("CompareRequest(name=");
    builder.append(getName());
    builder.append(", attributeDescription=");
    builder.append(getAttributeDescription());
    builder.append(", assertionValue=");
    builder.append(getAssertionValueAsString());
    builder.append(", controls=");
    builder.append(getControls());
    builder.append(")");
    return builder.toString();
  }



  CompareRequest getThis()
  {
    return this;
  }

}
