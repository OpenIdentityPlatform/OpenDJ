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
package org.opends.server.tools;



/**
 * This class defines a number of constants used in one or more Directory Server
 * tools.
 */
public class ToolConstants
{
  /**
   * The name of the SASL property that can be used to provide the
   * authentication ID for the bind.
   */
  public static final String SASL_PROPERTY_AUTHID = "authid";



  /**
   * The name of the SASL property that can be used to provide the authorization
   * ID for the bind.
   */
  public static final String SASL_PROPERTY_AUTHZID = "authzid";



  /**
   * The name of the SASL property that can be used to provide the digest URI
   * for the bind.
   */
  public static final String SASL_PROPERTY_DIGEST_URI = "digest-uri";



  /**
   * The name of the SASL property that can be used to provide the KDC for use
   * in Kerberos authentication.
   */
  public static final String SASL_PROPERTY_KDC = "kdc";



  /**
   * The name of the SASL property that can be used to provide the quality of
   * protection for the bind.
   */
  public static final String SASL_PROPERTY_QOP = "qop";



  /**
   * The name of the SASL property that can be used to provide the realm for the
   * bind.
   */
  public static final String SASL_PROPERTY_REALM = "realm";



  /**
   * The name of the SASL property that can be used to provide trace information
   * for a SASL ANONYMOUS request.
   */
  public static final String SASL_PROPERTY_TRACE = "trace";
}

