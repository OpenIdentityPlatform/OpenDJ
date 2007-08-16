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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.api;
import org.opends.messages.Message;



import java.util.List;

import org.opends.server.admin.std.server.AttributeSyntaxCfg;
import org.opends.server.config.ConfigException;
import org.opends.server.types.ByteString;
import org.opends.server.types.InitializationException;

import org.opends.messages.MessageBuilder;


/**
 * This class defines the set of methods and structures that must be
 * implemented by a Directory Server module that implements an
 * attribute syntax.
 *
 * @param  <T>  The type of configuration handled by this attribute
 *              syntax.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=true,
     mayInvoke=false)
public abstract class AttributeSyntax<T extends AttributeSyntaxCfg>
{
  /**
   * Initializes this attribute syntax based on the information in the
   * provided configuration entry.
   *
   * @param  configuration  The configuration to use to initialize
   *                        this attribute syntax.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in
   *                           the process of performing the
   *                           initialization.
   *
   * @throws  InitializationException  If a problem occurs during
   *                                   initialization that is not
   *                                   related to the server
   *                                   configuration.
   */
  public abstract void initializeSyntax(T configuration)
         throws ConfigException, InitializationException;



  /**
   * Indicates whether the provided configuration is acceptable for
   * this attribute syntax.  It should be possible to call this method
   * on an uninitialized attribute syntax instance in order to
   * determine whether the syntax would be able to use the provided
   * configuration.
   * <BR><BR>
   * Note that implementations which use a subclass of the provided
   * configuration class will likely need to cast the configuration
   * to the appropriate subclass type.
   *
   * @param  configuration        The attribute syntax configuration
   *                              for which to make the determination.
   * @param  unacceptableReasons  A list that may be used to hold the
   *                              reasons that the provided
   *                              configuration is not acceptable.
   *
   * @return  {@code true} if the provided configuration is acceptable
   *          for this attribute syntax, or {@code false} if not.
   */
  public boolean isConfigurationAcceptable(
                      AttributeSyntaxCfg configuration,
                      List<Message> unacceptableReasons)
  {
    // This default implementation does not perform any special
    // validation.  It should be overridden by attribute syntax
    // implementations that wish to perform more detailed validation.
    return true;
  }



  /**
   * Performs any finalization that may be necessary for this
   * attribute syntax. By default, no finalization is performed.
   */
  public void finalizeSyntax()
  {
    // No implementation required.
  }



  /**
   * Retrieves the common name for this attribute syntax.
   *
   * @return  The common name for this attribute syntax.
   */
  public abstract String getSyntaxName();



  /**
   * Retrieves the OID for this attribute syntax.
   *
   * @return  The OID for this attribute syntax.
   */
  public abstract String getOID();



  /**
   * Retrieves a description for this attribute syntax.
   *
   * @return  A description for this attribute syntax.
   */
  public abstract String getDescription();



  /**
   * Retrieves the default equality matching rule that will be used
   * for attributes with this syntax.
   *
   * @return  The default equality matching rule that will be used for
   *          attributes with this syntax, or {@code null} if equality
   *          matches will not be allowed for this type by default.
   */
  public abstract EqualityMatchingRule getEqualityMatchingRule();



  /**
   * Retrieves the default ordering matching rule that will be used
   * for attributes with this syntax.
   *
   * @return  The default ordering matching rule that will be used for
   *          attributes with this syntax, or {@code null} if ordering
   *          matches will not be allowed for this type by default.
   */
  public abstract OrderingMatchingRule getOrderingMatchingRule();



  /**
   * Retrieves the default substring matching rule that will be used
   * for attributes with this syntax.
   *
   * @return  The default substring matching rule that will be used
   *          for attributes with this syntax, or {@code null} if
   *          substring matches will not be allowed for this type by
   *          default.
   */
  public abstract SubstringMatchingRule getSubstringMatchingRule();



  /**
   * Retrieves the default approximate matching rule that will be used
   * for attributes with this syntax.
   *
   * @return  The default approximate matching rule that will be used
   *          for attributes with this syntax, or {@code null} if
   *          approximate matches will not be allowed for this type by
   *          default.
   */
  public abstract ApproximateMatchingRule
                       getApproximateMatchingRule();



  /**
   * Indicates whether the provided value is acceptable for use in an
   * attribute with this syntax.  If it is not, then the reason may be
   * appended to the provided buffer.
   *
   * @param  value          The value for which to make the
   *                        determination.
   * @param  invalidReason  The buffer to which the invalid reason
   *                        should be appended.
   *
   * @return  {@code true} if the provided value is acceptable for use
   *          with this syntax, or {@code false} if not.
   */
  public abstract boolean valueIsAcceptable(ByteString value,
                               MessageBuilder invalidReason);



  /**
   * Retrieves the hash code for this attribute syntax.  It will be
   * calculated as the sum of the characters in the OID.
   *
   * @return  The hash code for this attribute syntax.
   */
  public final int hashCode()
  {
    int hashCode = 0;

    String oidString = getOID();
    int    oidLength = oidString.length();
    for (int i=0; i < oidLength; i++)
    {
      hashCode += oidString.charAt(i);
    }

    return hashCode;
  }



  /**
   * Indicates whether the provided object is equal to this attribute
   * syntax. The provided object will be considered equal to this
   * attribute syntax only if it is an attribute syntax with the same
   * OID.
   *
   * @param  o  The object for which to make the determination.
   *
   * @return  {@code true} if the provided object is equal to this
   *          attribute syntax, or {@code false} if it is not.
   */
  public final boolean equals(Object o)
  {
    if (o == null)
    {
      return false;
    }

    if (this == o)
    {
      return true;
    }

    if (! (o instanceof AttributeSyntax))
    {
      return false;
    }

    return getOID().equals(((AttributeSyntax) o).getOID());
  }



  /**
   * Retrieves a string representation of this attribute syntax in the
   * format defined in RFC 2252.
   *
   * @return  A string representation of this attribute syntax in the
   *          format defined in RFC 2252.
   */
  public final String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this attribute syntax in the
   * format defined in RFC 2252 to the provided buffer.
   *
   * @param  buffer  The buffer to which the information should be
   *                 appended.
   */
  public final void toString(StringBuilder buffer)
  {
    buffer.append("( ");
    buffer.append(getOID());

    String description = getDescription();
    if ((description == null) || (description.length() == 0))
    {
      buffer.append(" )");
    }
    else
    {
      buffer.append(" DESC '");
      buffer.append(description);
      buffer.append("' )");
    }
  }
}

