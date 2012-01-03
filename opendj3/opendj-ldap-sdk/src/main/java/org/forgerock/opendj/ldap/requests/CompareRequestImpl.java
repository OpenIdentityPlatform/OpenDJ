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



import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;

import com.forgerock.opendj.util.Validator;



/**
 * Compare request implementation.
 */
final class CompareRequestImpl extends AbstractRequestImpl<CompareRequest>
    implements CompareRequest
{

  private AttributeDescription attributeDescription;

  private ByteString assertionValue;

  private DN name;



  /**
   * Creates a new compare request using the provided distinguished name,
   * attribute name, and assertion value.
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
  CompareRequestImpl(final DN name,
      final AttributeDescription attributeDescription,
      final ByteString assertionValue)
  {
    this.name = name;
    this.attributeDescription = attributeDescription;
    this.assertionValue = assertionValue;
  }



  /**
   * Creates a new compare request that is an exact copy of the provided
   * request.
   *
   * @param compareRequest
   *          The compare request to be copied.
   * @throws NullPointerException
   *           If {@code compareRequest} was {@code null} .
   */
  CompareRequestImpl(final CompareRequest compareRequest)
  {
    super(compareRequest);
    this.name = compareRequest.getName();
    this.attributeDescription = compareRequest.getAttributeDescription();
    this.assertionValue = compareRequest.getAssertionValue();
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
  public CompareRequest setAssertionValue(final ByteString value)
  {
    Validator.ensureNotNull(value);
    this.assertionValue = value;
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public CompareRequest setAssertionValue(final Object value)
  {
    Validator.ensureNotNull(value);
    this.assertionValue = ByteString.valueOf(value);
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public CompareRequest setAttributeDescription(
      final AttributeDescription attributeDescription)
  {
    Validator.ensureNotNull(attributeDescription);
    this.attributeDescription = attributeDescription;
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public CompareRequest setAttributeDescription(
      final String attributeDescription)
  {
    Validator.ensureNotNull(attributeDescription);
    this.attributeDescription = AttributeDescription
        .valueOf(attributeDescription);
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public CompareRequest setName(final DN dn)
  {
    Validator.ensureNotNull(dn);
    this.name = dn;
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public CompareRequest setName(final String dn)
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



  @Override
  CompareRequest getThis()
  {
    return this;
  }

}
