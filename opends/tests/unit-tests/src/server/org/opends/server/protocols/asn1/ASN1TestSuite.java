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
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.protocols.asn1;



import junit.framework.*;



/**
 * This class defines a JUnit test suite that will launch all the tests for the
 * classes in the ASN.1 package.  Whenever a new ASN.1 test class is defined,
 * this suite should be updated to include it.
 *
 *
 * @author   Neil A. Wilson
 */
public class ASN1TestSuite
       extends TestCase
{
  /**
   * Retrieves a test suite containing all of the ASN.1-related tests.
   *
   * @return  A test suite containing all of the ASN.1-related tests.
   */
  public static Test suite()
  {
    TestSuite asn1Suite = new TestSuite("ASN.1 Unit Tests");

    asn1Suite.addTestSuite(TestASN1Element.class);
    asn1Suite.addTestSuite(TestASN1Boolean.class);
    asn1Suite.addTestSuite(TestASN1Enumerated.class);
    asn1Suite.addTestSuite(TestASN1Integer.class);
    asn1Suite.addTestSuite(TestASN1Long.class);
    asn1Suite.addTestSuite(TestASN1Null.class);
    asn1Suite.addTestSuite(TestASN1OctetString.class);
    asn1Suite.addTestSuite(TestASN1Sequence.class);
    asn1Suite.addTestSuite(TestASN1Set.class);
    asn1Suite.addTestSuite(TestASN1ReaderAndWriter.class);

    return asn1Suite;
  }
}

