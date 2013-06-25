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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.core;



/**
 * This class defines a number of constant values that are used in core
 * Directory Server processing.
 */
public class CoreConstants
{
  /**
   * The name of the log element that will be used to hold the authentication
   * type for a bind operation.
   */
  public static final String LOG_ELEMENT_AUTH_TYPE = "authType";



  /**
   * The name of the log element that will be used to hold the base DN for a
   * search operation.
   */
  public static final String LOG_ELEMENT_BASE_DN = "baseDN";



  /**
   * The name of the log element that will be used to hold the bind DN.
   */
  public static final String LOG_ELEMENT_BIND_DN = "bindDN";



  /**
   * The name of the log element that will be used to hold the name of the
   * attribute to compare.
   */
  public static final String LOG_ELEMENT_COMPARE_ATTR = "compareAttribute";



  /**
   * The name of the log element that will be used to connection ID for the
   * client connection that requested the operation.
   */
  public static final String LOG_ELEMENT_CONNECTION_ID = "connID";



  /**
   * The name of the log element that will be used to indicate whether the old
   * RDN attribute value should be removed from an entry.
   */
  public static final String LOG_ELEMENT_DELETE_OLD_RDN = "deleteOldRDN";



  /**
   * The name of the log element that will be used to hold the number of entries
   * returned to the client for a search operation.
   */
  public static final String LOG_ELEMENT_ENTRIES_SENT = "entriesSent";



  /**
   * The name of the log element that will be used to hold the DN of an entry
   * targeted by an operation.
   */
  public static final String LOG_ELEMENT_ENTRY_DN = "entryDN";



  /**
   * The name of the log element that will be used to hold the error message for
   * an operation.
   */
  public static final String LOG_ELEMENT_ERROR_MESSAGE = "errorMessage";



  /**
   * The name of the log element that will be used to hold the request OID for
   * an extended operation.
   */
  public static final String LOG_ELEMENT_EXTENDED_REQUEST_OID = "requestOID";



  /**
   * The name of the log element that will be used to hold the response OID for
   * an extended operation.
   */
  public static final String LOG_ELEMENT_EXTENDED_RESPONSE_OID = "responseOID";



  /**
   * The name of the log element that will be used to hold the filter for a
   * search operation.
   */
  public static final String LOG_ELEMENT_FILTER = "filter";



  /**
   * The name of the log element that will be used to hold the message ID of an
   * operation to abandon.
   */
  public static final String LOG_ELEMENT_ID_TO_ABANDON = "idToAbandon";



  /**
   * The name of the log element that will be used to hold the matched DN for
   * an operation.
   */
  public static final String LOG_ELEMENT_MATCHED_DN = "matchedDN";



  /**
   * The name of the log element that will be used to message ID for an
   * operation.
   */
  public static final String LOG_ELEMENT_MESSAGE_ID = "messageID";



  /**
   * The name of the log element that will be used to hold the new RDN for a
   * modify DN operation.
   */
  public static final String LOG_ELEMENT_NEW_RDN = "newRDN";



  /**
   * The name of the log element that will be used to hold the new superior DN
   * for a modify DN operation.
   */
  public static final String LOG_ELEMENT_NEW_SUPERIOR = "newSuperior";



  /**
   * The name of the log element that will be used to operation ID for an
   * operation.
   */
  public static final String LOG_ELEMENT_OPERATION_ID = "opID";



  /**
   * The name of the log element that will be used to hold the length of time
   * spent processing an operation.
   */
  public static final String LOG_ELEMENT_PROCESSING_TIME = "processingTime";



  /**
   * The name of the log element that will be used to hold the number of search
   * references returned to the client for a search operation.
   */
  public static final String LOG_ELEMENT_REFERENCES_SENT = "referencesSent";



  /**
   * The name of the log element that will be used to hold the referral URLs for
   * an operation.
   */
  public static final String LOG_ELEMENT_REFERRAL_URLS = "referralURLs";



  /**
   * The name of the log element that will be used to hold the requested
   * attributes for a search operation.
   */
  public static final String LOG_ELEMENT_REQUESTED_ATTRIBUTES = "attributes";



  /**
   * The name of the log element that will be used to hold the result code for
   * an operation.
   */
  public static final String LOG_ELEMENT_RESULT_CODE = "resultCode";



  /**
   * The name of the log element that will be used to hold the SASL mechanism
   * for a bind operation.
   */
  public static final String LOG_ELEMENT_SASL_MECHANISM = "saslMechanism";



  /**
   * The name of the log element that will be used to hold the scope for a
   * search operation.
   */
  public static final String LOG_ELEMENT_SCOPE = "scope";



  /**
   * The name of the log element that will be used to hold the size limit for a
   * search operation.
   */
  public static final String LOG_ELEMENT_SIZE_LIMIT = "sizeLimit";



  /**
   * The name of the log element that will be used to hold the time limit for a
   * search operation.
   */
  public static final String LOG_ELEMENT_TIME_LIMIT = "timeLimit";
}

