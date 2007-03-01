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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.dsml.protocol;



import com.sun.xml.bind.marshaller.NamespacePrefixMapper;



/**
 * This class provides a custom namespace mapping.
 */
class NamespacePrefixMapperImpl extends NamespacePrefixMapper
{
  /**
   * Returns a preferred prefix for the given namespace URI.
   *
   * This method is intended to be overrided by a derived class.
   *
   * @param namespaceUri
   *      The namespace URI for which the prefix needs to be found.
   *      Never be null. "" is used to denote the default namespace.
   * @param suggestion
   *      When the content tree has a suggestion for the prefix
   *      to the given namespaceUri, that suggestion is passed as a
   *      parameter. Typicall this value comes from the QName.getPrefix
   *      to show the preference of the content tree. This parameter
   *      may be null, and this parameter may represent an already
   *      occupied prefix.
   * @param requirePrefix
   *      If this method is expected to return non-empty prefix.
   *      When this flag is true, it means that the given namespace URI
   *      cannot be set as the default namespace.
   *
   * @return
   *      null if there's no prefered prefix for the namespace URI.
   *      In this case, the system will generate a prefix.
   *
   *      Otherwise the system will try to use the returned prefix,
   *      but generally there's no guarantee if the prefix will be
   *      actually used or not.
   *
   *      If this method returns "" when requirePrefix=true, the return
   *      value will be ignored and the system will generate one.
   */
  public String getPreferredPrefix(String namespaceUri, String suggestion,
          boolean requirePrefix)
  {
    // Map this namespace to "xsi"
    if( "http://www.w3.org/2001/XMLSchema-instance".equals(namespaceUri) )
        return "xsi";

    // Map this namespace to dsml.
    if( "urn:oasis:names:tc:DSML:2:0:core".equals(namespaceUri) )
        return "dsml";

    // Just use the default suggestion, whatever it may be.
    return suggestion;
  }
}

