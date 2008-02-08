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
package org.opends.server.types;



import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;



/**
 * This class defines an annotation type that can be used to describe
 * the position of a package, class, or method in the OpenDS public
 * API (including to denote that the associated code should NOT be
 * considered part of the public API).  Third-party developers should
 * pay attention to these annotations in order to understand how best
 * to interact with the OpenDS code.  For the purposes of this
 * annotation, a "third-party developer" should be assumed to refer to
 * anyone who is interacting with the OpenDS code in a manner in which
 * their work is not expected to become part of the core OpenDS code
 * base.
 * <BR><BR>
 * This annotation type may be used to describe things like:
 * <UL>
 *   <LI>The stability of the code (how likely it is to change in the
 *       future and whether those changes may be incompatible with
 *       previous implementations).</LI>
 *   <LI>Whether third-party code may be allowed to create new
 *       instances of the associated object type.</LI>
 *   <LI>Whether a class or method may be extended by third-party
 *       code.</LI>
 *   <LI>Whether a class or method may be invoked by third-party
 *       code.</LI>
 * </UL>
 * <BR><BR>
 * Note that for cases in which there are conflicting public API
 * annotations, the most specific annotation should be considered
 * authoritative.  For example, if a class is marked with
 * {@code mayInvoke=true} but a method in that class is marked with
 * {@code mayInvoke=false}, then third-party code should not attempt
 * to invoke that method because the method-level annotation is more
 * specific (and therefore overrides) the less-specific class-level
 * annotation.
 * <BR><BR>
 * If a method does not include this annotation, then it should be
 * assumed to inherit the class-level annotation.  If a class does not
 * include this annotation, then it should be assumed to inherit the
 * package-level annotation.  If a package does not include this
 * annotation, then it should be assumed the package is private and
 * should not be used by third-party code.
 */
@Documented()
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.PACKAGE,
          ElementType.TYPE,
          ElementType.METHOD,
          ElementType.CONSTRUCTOR })
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=false)
public @interface PublicAPI
{
  /**
   * Retrieves the stability level for the associated class or method.
   *
   * @return  The stability level for the associated class or method.
   */
  StabilityLevel stability() default StabilityLevel.PRIVATE;



  /**
   * Indicates whether third-party code should be allowed to directly
   * create new instances of the associated object type by calling the
   * constructor or a static factory method defined in that class.
   * Note that even in cases where third-party code should not
   * instantiate a given object type, it may be permissible for
   * third-party code to invoke methods on instances of that object
   * obtained elsewhere (e.g., provided as an argument to a method
   * overridden by the third-party code).
   *
   * @return  {@code true} if third-party code should be allowed to
   *          create new instances of the associated object type, or
   *          {@code false} if not.
   */
  boolean mayInstantiate() default false;



  /**
   * Indicates whether the associated class/interface/method may be
   * extended/implemented/overridden by third-party code.  In some
   * cases, the OpenDS code may define an abstract class, interface,
   * or non-final method that is intended only for internal use and
   * may be extended by internal code but should not be extended by
   * classes outside the OpenDS code base.
   *
   * @return  {@code true} if the associated class/interface/method
   *          may be extended by third-party code, or {@code false} if
   *          not.
   */
  boolean mayExtend() default false;



  /**
   * Indicates whether the associated method may be invoked by
   * third-party code.
   *
   * @return  {@code true} if third-party code should be allowed to
   *          invoke the associated method, or {@code false} if not.
   */
  boolean mayInvoke() default false;



  /**
   * Retrieves a string that may contain additional notes that should
   * be taken into consideration by third-party developers that may be
   * interested in using the associated code.
   *
   * @return  A string that may contain additional notes that should
   *          be taken into consideration by third-party developers
   *          that may be interested in using the associated code.
   */
  String notes() default "";
}

