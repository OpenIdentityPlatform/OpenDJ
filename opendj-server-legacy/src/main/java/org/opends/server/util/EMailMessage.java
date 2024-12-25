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
 * Portions Copyright 2014-2016 ForgeRock AS.
 * Portions copyright 2015 Edan Idzerda
 */
package org.opends.server.util;



import static com.forgerock.opendj.cli.CommonArguments.*;

import static org.opends.messages.ToolMessages.*;
import static org.opends.messages.UtilityMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import jakarta.activation.DataHandler;
import jakarta.activation.FileDataSource;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.core.DirectoryServer;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ArgumentParser;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.StringArgument;



/**
 * This class defines an e-mail message that may be sent to one or more
 * recipients via SMTP.  This is a wrapper around JavaMail to make this process
 * more convenient and fit better into the Directory Server framework.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public final class EMailMessage
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();


  /** The addresses of the recipients to whom this message should be sent. */
  private List<String> recipients;

  /** The set of attachments to include in this message. */
  private final List<MimeBodyPart> attachments;

  /** The MIME type for the message body. */
  private String bodyMIMEType;

  /** The address of the sender for this message. */
  private String sender;

  /** The subject for the mail message. */
  private String subject;

  /** The body for the mail message. */
  private LocalizableMessageBuilder body;

  /**
   * Creates a new e-mail message with the provided information.
   *
   * @param  sender      The address of the sender for the message.
   * @param  recipients  The addresses of the recipients for the message.
   * @param  subject     The subject to use for the message.
   */
  public EMailMessage(String sender, List<String> recipients,
                      String subject)
  {
    this.sender     = sender;
    this.recipients = recipients;
    this.subject    = subject;

    body         = new LocalizableMessageBuilder();
    attachments  = new LinkedList<>();
    bodyMIMEType = "text/plain";
  }



  /**
   * Retrieves the sender for this message.
   *
   * @return  The sender for this message.
   */
  public String getSender()
  {
    return sender;
  }



  /**
   * Specifies the sender for this message.
   *
   * @param  sender  The sender for this message.
   */
  public void setSender(String sender)
  {
    this.sender = sender;
  }



  /**
   * Retrieves the set of recipients for this message.  This list may be
   * directly manipulated by the caller.
   *
   * @return  The set of recipients for this message.
   */
  public List<String> getRecipients()
  {
    return recipients;
  }



  /**
   * Specifies the set of recipients for this message.
   *
   * @param  recipients The set of recipients for this message.
   */
  public void setRecipients(ArrayList<String> recipients)
  {
    this.recipients = recipients;
  }



  /**
   * Adds the specified recipient to this message.
   *
   * @param  recipient  The recipient to add to this message.
   */
  public void addRecipient(String recipient)
  {
    recipients.add(recipient);
  }



  /**
   * Retrieves the subject for this message.
   *
   * @return  The subject for this message.
   */
  public String getSubject()
  {
    return subject;
  }



  /**
   * Specifies the subject for this message.
   *
   * @param  subject  The subject for this message.
   */
  public void setSubject(String subject)
  {
    this.subject = subject;
  }


  /**
   * Retrieves the MIME Type for the body of this message.
   *
   * @return The MIME Type for this message.
   */
   public String getBodyMIMEType()
   {
     return bodyMIMEType;
   }

  /**
   * Specifies the MIME Type for the body of this message.
   *
   * @param bodyMIMEType  The MIME Type for this message.
   */
  public void setBodyMIMEType(String bodyMIMEType)
  {
    this.bodyMIMEType = bodyMIMEType;
  }

  /**
   * Retrieves the body for this message.  It may be directly manipulated by the
   * caller.
   *
   * @return  The body for this message.
   */
  public LocalizableMessageBuilder getBody()
  {
    return body;
  }



  /**
   * Specifies the body for this message.
   *
   * @param  body  The body for this message.
   */
  public void setBody(LocalizableMessageBuilder body)
  {
    this.body = body;
  }



  /**
   * Specifies the body for this message.
   *
   * @param  body  The body for this message.
   */
  public void setBody(LocalizableMessage body)
  {
    this.body = new LocalizableMessageBuilder(body);
  }



  /**
   * Appends the provided text to the body of this message.
   *
   * @param  text  The text to append to the body of the message.
   */
  public void appendToBody(String text)
  {
    body.append(text);
  }



  /**
   * Retrieves the set of attachments for this message.  This list may be
   * directly modified by the caller if desired.
   *
   * @return  The set of attachments for this message.
   */
  public List<MimeBodyPart> getAttachments()
  {
    return attachments;
  }



  /**
   * Adds the provided attachment to this mail message.
   *
   * @param  attachment  The attachment to add to this mail message.
   */
  public void addAttachment(MimeBodyPart attachment)
  {
    attachments.add(attachment);
  }



  /**
   * Adds an attachment to this mail message with the provided text.
   *
   * @param  attachmentText  The text to include in the attachment.
   *
   * @throws  MessagingException  If there is a problem of some type with the
   *                              attachment.
   */
  public void addAttachment(String attachmentText)
         throws MessagingException
  {
    MimeBodyPart attachment = new MimeBodyPart();
    attachment.setText(attachmentText);
    attachments.add(attachment);
  }



  /**
   * Adds the provided attachment to this mail message.
   *
   * @param  attachmentFile  The file containing the attachment data.
   *
   * @throws  MessagingException  If there is a problem of some type with the
   *                              attachment.
   */
  private void addAttachment(File attachmentFile)
         throws MessagingException
  {
    MimeBodyPart attachment = new MimeBodyPart();

    FileDataSource dataSource = new FileDataSource(attachmentFile);
    attachment.setDataHandler(new DataHandler(dataSource));
    attachment.setFileName(attachmentFile.getName());

    attachments.add(attachment);
  }



  /**
   * Attempts to send this message to the intended recipient(s).  This will use
   * the mail server(s) defined in the Directory Server mail handler
   * configuration.  If multiple servers are specified and the first is
   * unavailable, then the other server(s) will be tried before returning a
   * failure to the caller.
   *
   * @throws  MessagingException  If a problem occurred while attempting to send
   *                              the message.
   */
  public void send()
         throws MessagingException
  {
    send(DirectoryServer.getCoreConfigManager().getMailServerPropertySets());
  }



  /**
   * Attempts to send this message to the intended recipient(s).  If multiple
   * servers are specified and the first is unavailable, then the other
   * server(s) will be tried before returning a failure to the caller.
   *
   * @param  mailServerPropertySets  A list of property sets providing
   *                                 information about the mail servers to use
   *                                 when sending the message.
   *
   * @throws  MessagingException  If a problem occurred while attempting to send
   *                              the message.
   */
  private void send(List<Properties> mailServerPropertySets)
         throws MessagingException
  {
    // Get information about the available mail servers that we can use.
    MessagingException sendException = null;
    for (Properties props : mailServerPropertySets)
    {
      // Get a session and use it to create a new message.
      Session session = Session.getInstance(props);
      MimeMessage message = new MimeMessage(session);
      message.setSubject(subject);
      message.setSentDate(new Date());


      // Add the sender address.  If this fails, then it's a fatal problem we'll
      // propagate to the caller.
      try
      {
        message.setFrom(new InternetAddress(sender));
      }
      catch (MessagingException me)
      {
        logger.traceException(me);

        LocalizableMessage msg = ERR_EMAILMSG_INVALID_SENDER_ADDRESS.get(sender, me.getMessage());
        throw new MessagingException(msg.toString(), me);
      }


      // Add the recipient addresses.  If any of them fail, then that's a fatal
      // problem we'll propagate to the caller.
      InternetAddress[] recipientAddresses =
           new InternetAddress[recipients.size()];
      for (int i=0; i < recipientAddresses.length; i++)
      {
        String recipient = recipients.get(i);

        try
        {
          recipientAddresses[i] = new InternetAddress(recipient);
        }
        catch (MessagingException me)
        {
          logger.traceException(me);

          LocalizableMessage msg = ERR_EMAILMSG_INVALID_RECIPIENT_ADDRESS.get(recipient, me.getMessage());
          throw new MessagingException(msg.toString(), me);
        }
      }
      message.setRecipients(
              jakarta.mail.Message.RecipientType.TO,
              recipientAddresses);


      // If we have any attachments, then the whole thing needs to be
      // multipart.  Otherwise, just set the text of the message.
      if (attachments.isEmpty())
      {
        message.setContent(body.toString(), bodyMIMEType);
      }
      else
      {
        MimeMultipart multiPart = new MimeMultipart();

        MimeBodyPart bodyPart = new MimeBodyPart();
        bodyPart.setText(body.toString());
        multiPart.addBodyPart(bodyPart);

        for (MimeBodyPart attachment : attachments)
        {
          multiPart.addBodyPart(attachment);
        }

        message.setContent(multiPart);
      }


      // Try to send the message.  If this fails, it can be a complete failure
      // or a partial one.  If it's a complete failure then try rolling over to
      // the next server.  If it's a partial one, then that likely means that
      // the message was sent but one or more recipients was rejected, so we'll
      // propagate that back to the caller.
      try
      {
        Transport.send(message);
        return;
      }
      catch (SendFailedException sfe)
      {
        logger.traceException(sfe);

        // We'll ignore this and hope that another server is available.  If not,
        // then at least save the exception so that we can throw it if all else
        // fails.
        if (sendException == null)
        {
          sendException = sfe;
        }
      }
      // FIXME -- Are there any other types of MessagingException that we might
      //          want to catch so we could try again on another server?
    }


    // If we've gotten here, then we've tried all of the servers in the list and
    // still failed.  If we captured an earlier exception, then throw it.
    // Otherwise, throw a generic exception.
    if (sendException != null)
    {
      throw sendException;
    }
    throw new MessagingException(ERR_EMAILMSG_CANNOT_SEND.get().toString());
  }



  /**
   * Provide a command-line mechanism for sending an e-mail message via SMTP.
   *
   * @param  args  The command-line arguments provided to this program.
   */
  public static void main(String[] args)
  {
    LocalizableMessage description = INFO_EMAIL_TOOL_DESCRIPTION.get();
    ArgumentParser argParser = new ArgumentParser(EMailMessage.class.getName(),
                                                  description, false);

    BooleanArgument showUsage  = null;
    StringArgument  attachFile = null;
    StringArgument  bodyFile   = null;
    StringArgument  host       = null;
    StringArgument  from       = null;
    StringArgument  subject    = null;
    StringArgument  to         = null;

    try
    {
      host =
              StringArgument.builder("host")
                      .shortIdentifier('h')
                      .description(INFO_EMAIL_HOST_DESCRIPTION.get())
                      .multiValued()
                      .required()
                      .defaultValue("127.0.0.1")
                      .valuePlaceholder(INFO_HOST_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      from =
              StringArgument.builder("from")
                      .shortIdentifier('f')
                      .description(INFO_EMAIL_FROM_DESCRIPTION.get())
                      .required()
                      .valuePlaceholder(INFO_ADDRESS_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      to =
              StringArgument.builder("to")
                      .shortIdentifier('t')
                      .description(INFO_EMAIL_TO_DESCRIPTION.get())
                      .multiValued()
                      .required()
                      .valuePlaceholder(INFO_ADDRESS_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      subject =
              StringArgument.builder("subject")
                      .shortIdentifier('s')
                      .description(INFO_EMAIL_SUBJECT_DESCRIPTION.get())
                      .required()
                      .valuePlaceholder(INFO_SUBJECT_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      bodyFile =
              StringArgument.builder("body")
                      .shortIdentifier('b')
                      .description(INFO_EMAIL_BODY_DESCRIPTION.get())
                      .multiValued()
                      .required()
                      .valuePlaceholder(INFO_PATH_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      attachFile =
              StringArgument.builder("attach")
                      .shortIdentifier('a')
                      .description(INFO_EMAIL_ATTACH_DESCRIPTION.get())
                      .multiValued()
                      .valuePlaceholder(INFO_PATH_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);

      showUsage = showUsageArgument();
      argParser.addArgument(showUsage);
      argParser.setUsageArgument(showUsage);
    }
    catch (ArgumentException ae)
    {
      System.err.println(ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage()));
      System.exit(1);
    }

    try
    {
      argParser.parseArguments(args);
    }
    catch (ArgumentException ae)
    {
      argParser.displayMessageAndUsageReference(System.err, ERR_ERROR_PARSING_ARGS.get(ae.getMessage()));
      System.exit(1);
    }

    if (showUsage.isPresent())
    {
      return;
    }

    LinkedList<Properties> mailServerProperties = new LinkedList<>();
    for (String s : host.getValues())
    {
      Properties p = new Properties();
      p.setProperty(SMTP_PROPERTY_HOST, s);
      mailServerProperties.add(p);
    }

    EMailMessage message = new EMailMessage(from.getValue(), to.getValues(),
                                            subject.getValue());

    for (String s : bodyFile.getValues())
    {
      try
      {
        File f = new File(s);
        if (! f.exists())
        {
          System.err.println(ERR_EMAIL_NO_SUCH_BODY_FILE.get(s));
          System.exit(1);
        }

        BufferedReader reader = new BufferedReader(new FileReader(f));
        while (true)
        {
          String line = reader.readLine();
          if (line == null)
          {
            break;
          }

          message.appendToBody(line);
          message.appendToBody("\r\n"); // SMTP says we should use CRLF.
        }

        reader.close();
      }
      catch (Exception e)
      {
        System.err.println(ERR_EMAIL_CANNOT_PROCESS_BODY_FILE.get(s,
                                getExceptionMessage(e)));
        System.exit(1);
      }
    }

    if (attachFile.isPresent())
    {
      for (String s : attachFile.getValues())
      {
        File f = new File(s);
        if (! f.exists())
        {
          System.err.println(ERR_EMAIL_NO_SUCH_ATTACHMENT_FILE.get(s));
          System.exit(1);
        }

        try
        {
          message.addAttachment(f);
        }
        catch (Exception e)
        {
          System.err.println(ERR_EMAIL_CANNOT_ATTACH_FILE.get(s,
                                  getExceptionMessage(e)));
        }
      }
    }

    try
    {
      message.send(mailServerProperties);
    }
    catch (Exception e)
    {
      System.err.println(ERR_EMAIL_CANNOT_SEND_MESSAGE.get(
                              getExceptionMessage(e)));
      System.exit(1);
    }
  }
}

