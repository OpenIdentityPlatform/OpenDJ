package org.opends.sdk.controls;



import static org.opends.messages.ProtocolMessages.*;

import java.util.Arrays;
import java.util.List;

import org.opends.messages.Message;



/**
 * This enumeration defines the set of password policy warnings that may
 * be included in the password policy response control defined in
 * draft-behera-ldap-password-policy.
 */
public class PasswordPolicyErrorType
{
  private static final PasswordPolicyErrorType[] ELEMENTS = new PasswordPolicyErrorType[9];

  public static final PasswordPolicyErrorType PASSWORD_EXPIRED = register(
      0, INFO_PWPERRTYPE_DESCRIPTION_PASSWORD_EXPIRED.get());

  public static final PasswordPolicyErrorType ACCOUNT_LOCKED = register(
      1, INFO_PWPERRTYPE_DESCRIPTION_ACCOUNT_LOCKED.get());

  public static final PasswordPolicyErrorType CHANGE_AFTER_RESET = register(
      2, INFO_PWPERRTYPE_DESCRIPTION_CHANGE_AFTER_RESET.get());

  public static final PasswordPolicyErrorType PASSWORD_MOD_NOT_ALLOWED = register(
      3, INFO_PWPERRTYPE_DESCRIPTION_PASSWORD_MOD_NOT_ALLOWED.get());

  public static final PasswordPolicyErrorType MUST_SUPPLY_OLD_PASSWORD = register(
      4, INFO_PWPERRTYPE_DESCRIPTION_MUST_SUPPLY_OLD_PASSWORD.get());

  public static final PasswordPolicyErrorType INSUFFICIENT_PASSWORD_QUALITY = register(
      5, INFO_PWPERRTYPE_DESCRIPTION_INSUFFICIENT_PASSWORD_QUALITY
          .get());

  public static final PasswordPolicyErrorType PASSWORD_TOO_SHORT = register(
      6, INFO_PWPERRTYPE_DESCRIPTION_PASSWORD_TOO_SHORT.get());

  public static final PasswordPolicyErrorType PASSWORD_TOO_YOUNG = register(
      7, INFO_PWPERRTYPE_DESCRIPTION_PASSWORD_TOO_YOUNG.get());

  public static final PasswordPolicyErrorType PASSWORD_IN_HISTORY = register(
      8, INFO_PWPERRTYPE_DESCRIPTION_PASSWORD_IN_HISTORY.get());



  public static PasswordPolicyErrorType valueOf(int intValue)
  {
    PasswordPolicyErrorType e = ELEMENTS[intValue];
    if (e == null)
    {
      e = new PasswordPolicyErrorType(intValue, Message
          .raw("undefined(" + intValue + ")"));
    }
    return e;
  }



  public static List<PasswordPolicyErrorType> values()
  {
    return Arrays.asList(ELEMENTS);
  }



  private static PasswordPolicyErrorType register(int intValue,
      Message name)
  {
    PasswordPolicyErrorType t = new PasswordPolicyErrorType(intValue,
        name);
    ELEMENTS[intValue] = t;
    return t;
  }



  private final int intValue;

  private final Message name;



  private PasswordPolicyErrorType(int intValue, Message name)
  {
    this.intValue = intValue;
    this.name = name;
  }



  @Override
  public boolean equals(Object o)
  {
    return (this == o)
        || ((o instanceof PasswordPolicyErrorType) && (this.intValue == ((PasswordPolicyErrorType) o).intValue));

  }



  @Override
  public int hashCode()
  {
    return intValue;
  }



  public int intValue()
  {
    return intValue;
  }



  @Override
  public String toString()
  {
    return name.toString();
  }
}
