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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.server.admin;



import static org.testng.Assert.assertEquals;

import org.opends.server.TestCaseUtils;
import org.opends.server.admin.std.meta.RootCfgDefn;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;



/**
 * ClassPropertyDefinition Tester.
 */
public class DNPropertyDefinitionTest {

  /**
   * Sets up tests
   *
   * @throws Exception
   *           If the server could not be initialized.
   */
  @BeforeClass
  public void setUp() throws Exception {
    // This test suite depends on having the schema available, so
    // we'll start the server.
    TestCaseUtils.startServer();
  }



  /**
   * @return data for testing
   */
  @DataProvider(name = "testBuilderSetBaseDN")
  public Object[][] createBuilderSetBaseDN() {
    return new Object[][] { { null },
        { "cn=key manager providers, cn=config" } };
  }



  /**
   * Tests builder.setBaseDN with valid data.
   *
   * @param baseDN
   *          The base DN.
   * @throws DirectoryException
   *           If the DN could not be decoded.
   */
  @Test(dataProvider = "testBuilderSetBaseDN")
  public void testBuilderSetBaseDN(String baseDN)
      throws DirectoryException {
    DNPropertyDefinition.Builder localBuilder = DNPropertyDefinition
        .createBuilder(RootCfgDefn.getInstance(), "test-property");
    localBuilder.setBaseDN(baseDN);
    DNPropertyDefinition pd = localBuilder.getInstance();

    DN actual = pd.getBaseDN();
    DN expected = baseDN == null ? null : DN.decode(baseDN);

    assertEquals(actual, expected);
  }



  /**
   * @return data for testing
   */
  @DataProvider(name = "testLegalValues")
  public Object[][] createLegalValues() {
    return new Object[][] {
        { null, "cn=config" },
        { null, "dc=example,dc=com" },
        { "", "cn=config" },
        { "cn=config", "cn=key manager providers, cn=config" },
        { "cn=key manager providers, cn=config",
            "cn=my provider, cn=key manager providers, cn=config" }, };
  }



  /**
   * @return data for testing
   */
  @DataProvider(name = "testIllegalValues")
  public Object[][] createIllegalValues() {
    return new Object[][] {
    // Above base DN.
        { "cn=config", "" },

        // Same as base DN.
        { "cn=config", "cn=config" },

        // Same as base DN.
        { "cn=key manager providers, cn=config",
            "cn=key manager providers, cn=config" },

        // Too far beneath base DN.
        { "cn=config",
            "cn=my provider, cn=key manager providers, cn=config" },

        // Unrelated to base DN.
        { "cn=config", "dc=example, dc=com" }, };
  }



  /**
   * Tests validation with valid data.
   *
   * @param baseDN
   *          The base DN.
   * @param value
   *          The value to be validated.
   * @throws DirectoryException
   *           If the DN could not be decoded.
   */
  @Test(dataProvider = "testLegalValues")
  public void testValidateLegalValues(String baseDN, String value)
      throws DirectoryException {
    DNPropertyDefinition.Builder localBuilder = DNPropertyDefinition
        .createBuilder(RootCfgDefn.getInstance(), "test-property");
    localBuilder.setBaseDN(baseDN);
    DNPropertyDefinition pd = localBuilder.getInstance();
    pd.validateValue(DN.decode(value));
  }



  /**
   * Tests validation with invalid data.
   *
   * @param baseDN
   *          The base DN.
   * @param value
   *          The value to be validated.
   * @throws DirectoryException
   *           If the DN could not be decoded.
   */
  @Test(dataProvider = "testIllegalValues", expectedExceptions = IllegalPropertyValueException.class)
  public void testValidateIllegalValues(String baseDN, String value)
      throws DirectoryException {
    DNPropertyDefinition.Builder localBuilder = DNPropertyDefinition
        .createBuilder(RootCfgDefn.getInstance(), "test-property");
    localBuilder.setBaseDN(baseDN);
    DNPropertyDefinition pd = localBuilder.getInstance();
    pd.validateValue(DN.decode(value));
  }



  /**
   * Tests decoding with valid data.
   *
   * @param baseDN
   *          The base DN.
   * @param value
   *          The value to be validated.
   */
  @Test(dataProvider = "testLegalValues")
  public void testDecodeLegalValues(String baseDN, String value) {
    DNPropertyDefinition.Builder localBuilder = DNPropertyDefinition
        .createBuilder(RootCfgDefn.getInstance(), "test-property");
    localBuilder.setBaseDN(baseDN);
    DNPropertyDefinition pd = localBuilder.getInstance();
    pd.decodeValue(value);
  }



  /**
   * Tests validation with invalid data.
   *
   * @param baseDN
   *          The base DN.
   * @param value
   *          The value to be validated.
   */
  @Test(dataProvider = "testIllegalValues", expectedExceptions = IllegalPropertyValueStringException.class)
  public void testDecodeIllegalValues(String baseDN, String value) {
    DNPropertyDefinition.Builder localBuilder = DNPropertyDefinition
        .createBuilder(RootCfgDefn.getInstance(), "test-property");
    localBuilder.setBaseDN(baseDN);
    DNPropertyDefinition pd = localBuilder.getInstance();
    pd.decodeValue(value);
  }
}
