package org.opends.sdk.controls;



import static org.opends.messages.ProtocolMessages.INFO_PWPWARNTYPE_DESCRIPTION_GRACE_LOGINS_REMAINING;
import static org.opends.messages.ProtocolMessages.INFO_PWPWARNTYPE_DESCRIPTION_TIME_BEFORE_EXPIRATION;

import java.util.Arrays;
import java.util.List;

import org.opends.messages.Message;



/**
 * This enumeration defines the set of password policy warnings that may
 * be included in the password policy response control defined in
 * draft-behera-ldap-password-policy.
 */
public class PasswordPolicyWarningType
{
  private static final PasswordPolicyWarningType[] ELEMENTS = new PasswordPolicyWarningType[2];

  public static final PasswordPolicyWarningType TIME_BEFORE_EXPIRATION = register(
      0, INFO_PWPWARNTYPE_DESCRIPTION_TIME_BEFORE_EXPIRATION.get());

  public static final PasswordPolicyWarningType GRACE_LOGINS_REMAINING = register(
      1, INFO_PWPWARNTYPE_DESCRIPTION_GRACE_LOGINS_REMAINING.get());



  public static PasswordPolicyWarningType valueOf(int intValue)
  {
    PasswordPolicyWarningType e = ELEMENTS[intValue];
    if (e == null)
    {
      e = new PasswordPolicyWarningType(intValue, Message
          .raw("undefined(" + intValue + ")"));
    }
    return e;
  }



  public static List<PasswordPolicyWarningType> values()
  {
    return Arrays.asList(ELEMENTS);
  }



  private static PasswordPolicyWarningType register(int intValue,
      Message name)
  {
    PasswordPolicyWarningType t = new PasswordPolicyWarningType(
        intValue, name);
    ELEMENTS[intValue] = t;
    return t;
  }



  private final int intValue;

  private final Message name;



  private PasswordPolicyWarningType(int intValue, Message name)
  {
    this.intValue = intValue;
    this.name = name;
  }



  @Override
  public boolean equals(Object o)
  {
    return (this == o)
        || ((o instanceof PasswordPolicyWarningType) && (this.intValue == ((PasswordPolicyWarningType) o).intValue));

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
