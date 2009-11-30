package org.opends.sdk.controls;



import org.opends.sdk.AttributeDescription;
import org.opends.sdk.util.Validator;



/**
 * This class defines a data structure that may be used as a sort key.
 * It includes an attribute description and a boolean value that
 * indicates whether the sort should be ascending or descending. It may
 * also contain a specific ordering matching rule that should be used
 * for the sorting process, although if none is provided it will use the
 * default ordering matching rule for the attribute type.
 */
public class SortKey
{
  private String attributeDescription;

  private String orderingRule;

  boolean reverseOrder;



  public SortKey(AttributeDescription attributeDescription)
  {
    this(attributeDescription.toString(), null, false);
  }



  public SortKey(AttributeDescription attributeDescription,
      String orderingRule)
  {
    this(attributeDescription.toString(), orderingRule, false);
  }



  public SortKey(AttributeDescription attributeDescription,
      String orderingRule, boolean reverseOrder)
  {
    this(attributeDescription.toString(), orderingRule, reverseOrder);
  }



  public SortKey(String attributeDescription)
  {
    this(attributeDescription, null, false);
  }



  public SortKey(String attributeDescription, String orderingRule)
  {
    this(attributeDescription, orderingRule, false);
  }



  public SortKey(String attributeDescription, String orderingRule,
      boolean reverseOrder)
  {
    Validator.ensureNotNull(attributeDescription);
    this.attributeDescription = attributeDescription;
    this.orderingRule = orderingRule;
    this.reverseOrder = reverseOrder;
  }



  public String getAttributeDescription()
  {
    return attributeDescription;
  }



  public String getOrderingRule()
  {
    return orderingRule;
  }



  public boolean isReverseOrder()
  {
    return reverseOrder;
  }



  public SortKey setAttributeDescription(String attributeDescription)
  {
    Validator.ensureNotNull(attributeDescription);
    this.attributeDescription = attributeDescription;
    return this;
  }



  public SortKey setOrderingRule(String orderingRule)
  {
    this.orderingRule = orderingRule;
    return this;
  }



  public void setReverseOrder(boolean reverseOrder)
  {
    this.reverseOrder = reverseOrder;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  public void toString(StringBuilder buffer)
  {
    buffer.append("SortKey(attributeDescription=");
    buffer.append(attributeDescription);
    buffer.append(", orderingRule=");
    buffer.append(orderingRule);
    buffer.append(", reverseOrder=");
    buffer.append(reverseOrder);
    buffer.append(")");
  }
}
