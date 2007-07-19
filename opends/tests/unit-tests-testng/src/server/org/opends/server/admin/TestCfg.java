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



import org.opends.server.admin.std.meta.RootCfgDefn;



/**
 * Common methods for hooking in the test components.
 */
public final class TestCfg {

  /**
   * A one-to-many relation between the root and test-parent
   * components.
   */
  private static final InstantiableRelationDefinition<TestParentCfgClient, TestParentCfg> RD_TEST_ONE_TO_MANY_PARENT;

  /**
   * A one-to-zero-or-one relation between the root and a test-parent
   * component.
   */
  private static final OptionalRelationDefinition<TestParentCfgClient, TestParentCfg> RD_TEST_ONE_TO_ZERO_OR_ONE_PARENT;

  // Create a one-to-many relation for test-parent components.
  static {
    RD_TEST_ONE_TO_MANY_PARENT = new InstantiableRelationDefinition<TestParentCfgClient, TestParentCfg>(
        RootCfgDefn.getInstance(), "test-one-to-many-parent",
        "test-one-to-many-parents", TestParentCfgDefn.getInstance());
  }

  // Create a one-to-many relation for test-parent components.
  static {
    RD_TEST_ONE_TO_ZERO_OR_ONE_PARENT = new OptionalRelationDefinition<TestParentCfgClient, TestParentCfg>(
        RootCfgDefn.getInstance(), "test-one-to-zero-or-one-parent",
        TestParentCfgDefn.getInstance());

  }



  /**
   * Deregisters the test configurations from the administration
   * framework.
   */
  public static void cleanup() {
    RootCfgDefn.getInstance().deregisterRelationDefinition(
        RD_TEST_ONE_TO_MANY_PARENT);
    RootCfgDefn.getInstance().deregisterRelationDefinition(
        RD_TEST_ONE_TO_MANY_PARENT);
  }



  /**
   * Gets the one-to-many relation between the root and test-parent
   * components.
   * <p>
   * Unit tests which call this method <b>must</b> call
   * {@link #cleanup()} on completion.
   *
   * @return Returns the one-to-many relation between the root and
   *         test-parent components.
   */
  public static InstantiableRelationDefinition<TestParentCfgClient, TestParentCfg> getTestOneToManyParentRelationDefinition() {
    // Ensure that the relation is registered.
    RootCfgDefn.getInstance().registerRelationDefinition(
        RD_TEST_ONE_TO_MANY_PARENT);
    return RD_TEST_ONE_TO_MANY_PARENT;
  }



  /**
   * Gets the one-to-zero-or-one relation between the root and a
   * test-parent component.
   * <p>
   * Unit tests which call this method <b>must</b> call
   * {@link #cleanup()} on completion.
   *
   * @return Returns the one-to-zero-or-one relation between the root
   *         and a test-parent component.
   */
  public static OptionalRelationDefinition<TestParentCfgClient, TestParentCfg> getTestOneToZeroOrOneParentRelationDefinition() {
    // Ensure that the relation is registered.
    RootCfgDefn.getInstance().registerRelationDefinition(
        RD_TEST_ONE_TO_ZERO_OR_ONE_PARENT);
    return RD_TEST_ONE_TO_ZERO_OR_ONE_PARENT;
  }



  // Prevent instantiation.
  private TestCfg() {
    // No implementation required.
  }

}
