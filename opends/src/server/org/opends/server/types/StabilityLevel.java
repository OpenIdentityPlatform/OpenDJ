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
package org.opends.server.types;



/**
 * This class implements an enumeration whose values may be used to
 * indicate the stability level of API classes and/or methods.  Code
 * which is part of the OpenDS public API should be marked with a
 * {@code COMMITTED}, {@code UNCOMMITTED}, {@code VOLAITLE}, or
 * {@code OBSOLETE} stability level in order to indicate the relative
 * likelihood that the associated interface will be changed in an
 * incompatible way in the future.
 * <BR><BR>
 * Third-party developers are free to create code that introduces
 * dependencies on OpenDS APIs that are marked {@code COMMITTED},
 * {@code UNCOMMITTED}, or {@code VOLATILE}, with an understanding
 * that the less stable an OpenDS API is, the more likely that
 * third-party code which relies upon it may need to be altered in
 * order to work properly with future versions.
 * <BR><BR>
 * Changes to the stability level of a class or package should only be
 * made between major releases and must be denoted in the release
 * notes for all releases with that major version.  If a public API
 * element that is marked {@code COMMITTED}, {@code UNCOMMITTED}, or
 * {@code VOLATILE} is to be made private, it is strongly recommended
 * that it first be transitioned to {@code OBSOLETE} before ultimately
 * being marked {@code PRIVATE}.
 * <BR><BR>
 * New packages and classes introduced into the OpenDS code base may
 * be assigned any stability level.  New methods introduced into
 * existing classes that are part of the public API may be created
 * with any stability level as long as the introduction of that method
 * is compliant with the stability level of the class.  If a method
 * that is part of the OpenDS public API is not marked with an
 * explicit stability level, then it should be assumed that it has the
 * same stability level as the class that contains it.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public enum StabilityLevel
{
  /**
   * The associated package, class, or method may be made available
   * for third-party use, and the APIs that it exposes should be
   * considered stable.  Incompatible changes may only be introduced
   * between major versions, and even then such changes should be
   * considered very rare and will require strong justification and
   * be explicitly denoted in the release notes for all releases with
   * that major version.
   * <BR><BR>
   * Note that interface changes may be allowed between non-major
   * releases if they do not impact backward compatibility.
   */
  COMMITTED,



  /**
   * The associated package, class, or method may be made available
   * for third-party use, and the APIs that it exposes may be
   * considered moderately stable.  Incompatible changes may be
   * introduced between major and/or minor versions, but only with
   * strong justification and explicit denotation in the release notes
   * for all subsequent releases with that major version.
   * <BR><BR>
   * Note that interface changes may be allowed between non-major and
   * non-minor releases if they do not impact backward compatibility.
   */
  UNCOMMITTED,



  /**
   * The associated package, class, or method may be made available
   * for third-party use, but the APIs that it exposes should not be
   * considered stable.  Incompatible changes may be introduced
   * between major, minor, and point versions, and may also be
   * introduced in patches or hotfixes.  Any incompatible interface
   * changes should be denoted in the release notes for all subsequent
   * releases with that major version.
   * <BR><BR>
   * Note that if it is believed that a given class or interface will
   * likely have incompatible changes in the future, then it should be
   * declared with a stability level of {@code VOLATILE}, even if that
   * those incompatible changes are expected to occur between major
   * releases.
   */
  VOLATILE,



  /**
   * The associated package, class, or method should be considered
   * obsolete, and no new code should be created that depends on it.
   * The associated code may be removed in future versions without any
   * additional prior notice.
   */
  OBSOLETE,



  /**
   * The associated package, class, or method should be considered
   * part of the OpenDS private API and should not be used by
   * third-party code.  No prior notice is required for incompatible
   * changes to code with a {@code PRIVATE} classification.
   */
  PRIVATE;
}

