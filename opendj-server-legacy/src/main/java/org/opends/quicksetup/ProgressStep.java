/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2008 Sun Microsystems, Inc.
 */

package org.opends.quicksetup;

/**
 * Interface describing the different installation steps in which we can
 * be.
 */
public interface ProgressStep {

  /**
   * Indicates whether this Progress step is a final step.
   * @return true if this is a final step
   */
  boolean isLast();

  /**
   * Indicates whether this Progress step is arrived at
   * through an error in the application.
   * @return true if this is an error step
   */
  boolean isError();

}
