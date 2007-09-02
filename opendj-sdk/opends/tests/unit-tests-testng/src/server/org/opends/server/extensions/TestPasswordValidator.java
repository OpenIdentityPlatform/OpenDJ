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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import java.util.Set;

import org.opends.server.admin.std.server.PasswordValidatorCfg;
import org.opends.server.api.PasswordValidator;
import org.opends.server.config.ConfigEntry;
import org.opends.server.types.ByteString;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.Operation;
import org.opends.messages.MessageBuilder;
import org.opends.messages.Message;


/**
 * This class provides a very simple password validator that can be used for
 * testing purposes.  It provides the ability to inspect the arguments provided
 * to the password validator, as well as to manipulate the result that it will
 * return.
 */
public class TestPasswordValidator
       extends PasswordValidator<PasswordValidatorCfg>
{
  /**
   * The singleton instance of this test password validator.
   */
  private static TestPasswordValidator instance = null;



  // The next value to return from the passwordIsAcceptable method.
  private boolean nextReturnValue;

  // The last new password provided to the passwordIsAcceptable method.
  private ByteString lastNewPassword;

  // The last user entry provided to the passwordIsAcceptable method.
  private Entry lastUserEntry;

  // The last operation provided to the passwordIsAcceptable method.
  private Operation lastOperation;

  // The last set of current passwords provided to the passwordIsAcceptable
  // method.
  private Set<ByteString> lastCurrentPasswords;

  // The next invalid reason that should be used in the passwordIsAcceptable
  // method.
  private String nextInvalidReason;



  /**
   * Creates a new instance of this password validator.
   */
  public TestPasswordValidator()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void initializePasswordValidator(
                   PasswordValidatorCfg configuration)
         throws InitializationException
  {
    instance = this;

    lastNewPassword      = null;
    lastCurrentPasswords = null;
    lastOperation        = null;
    lastUserEntry        = null;
    nextReturnValue      = true;
    nextInvalidReason    = null;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean passwordIsAcceptable(ByteString newPassword,
                                      Set<ByteString> currentPasswords,
                                      Operation operation, Entry userEntry,
                                      MessageBuilder invalidReason)
  {
    lastNewPassword      = newPassword;
    lastCurrentPasswords = currentPasswords;
    lastOperation        = operation;
    lastUserEntry        = userEntry;

    if (nextInvalidReason != null)
    {
      invalidReason.append(nextInvalidReason);
    }

    return nextReturnValue;
  }



  /**
   * Retrieves an instance of this test password validator.
   *
   * @return  An instance of this test password validator, or <CODE>null</CODE>
   *          if no instance has been created.
   */
  public static TestPasswordValidator getInstance()
  {
    return instance;
  }



  /**
   * Clears the instance after the tests so that it's memory can be reclaimed.
   * This can actually be quite a bit of memory since it references the
   * Schema, ConfigEntryS, etc
   */
  public static void clearInstanceAfterTests()
  {
    instance = null;
  }



  /**
   * Retrieves the last <CODE>newPassword</CODE> value provided to the
   * <CODE>passwordIsAcceptable</CODE> method.
   *
   * @return  The last <CODE>newPassword</CODE> value provided to the
   *          <CODE>passwordIsAcceptable</CODE> method.
   */
  public static ByteString getLastNewPassword()
  {
    return instance.lastNewPassword;
  }



  /**
   * Retrieves the last <CODE>currentPasswords</CODE> value provided to the
   * <CODE>passwordIsAcceptable</CODE> method.
   *
   * @return  The last <CODE>currentPasswords</CODE> value provided to the
   *          <CODE>passwordIsAcceptable</CODE> method.
   */
  public static Set<ByteString> getLastCurrentPasswords()
  {
    return instance.lastCurrentPasswords;
  }



  /**
   * Retrieves the last <CODE>operation</CODE> value provided to the
   * <CODE>passwordIsAcceptable</CODE> method.
   *
   * @return  The last <CODE>operation</CODE> value provided to the
   *          <CODE>passwordIsAcceptable</CODE> method.
   */
  public static Operation getLastOperation()
  {
    return instance.lastOperation;
  }



  /**
   * Retrieves the last <CODE>userEntry</CODE> value provided to the
   * <CODE>passwordIsAcceptable</CODE> method.
   *
   * @return  The last <CODE>userEntry</CODE> value provided to the
   *          <CODE>passwordIsAcceptable</CODE> method.
   */
  public static Entry getLastUserEntry()
  {
    return instance.lastUserEntry;
  }



  /**
   * Sets the next value to return from the <CODE>passwordIsAcceptable</CODE>
   * method.
   *
   * @param  nextReturnValue  The next value to return from the
   *                          <CODE>passwordIsAcceptable</CODE> method.
   */
  public static void setNextReturnValue(boolean nextReturnValue)
  {
    instance.nextReturnValue = nextReturnValue;
  }



  /**
   * Sets the next string to append to the <CODE>invalidReason</CODE> buffer in
   * the <CODE>passwordIsAcceptable</CODE> method.
   *
   * @param  nextReturnValue  The next string to append to the
   *                          <CODE>invalidReason</CODE> buffer in the
   *                          <CODE>passwordIsAcceptable</CODE> method.
   */
  public static void setNextInvalidReason(String nextInvalidReason)
  {
    instance.nextInvalidReason = nextInvalidReason;
  }
}

