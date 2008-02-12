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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.util.cli;



/**
 * An interface for validating user input.
 *
 * @param <T>
 *          The type of the decoded input.
 */
public interface ValidationCallback<T> {

  /**
   * Validates and decodes the user-provided input. Implementations
   * must validate <code>input</code> and return the decoded value
   * if the input is acceptable. If the input is unacceptable,
   * implementations must return <code>null</code> and output a user
   * friendly error message to the provided application console.
   *
   * @param app
   *          The console application.
   * @param input
   *          The user input to be validated.
   * @return Returns the decoded input if the input is valid, or
   *         <code>null</code> if it is not.
   * @throws CLIException
   *           If an unexpected error occurred which prevented
   *           validation.
   */
  T validate(ConsoleApplication app, String input) throws CLIException;
}
