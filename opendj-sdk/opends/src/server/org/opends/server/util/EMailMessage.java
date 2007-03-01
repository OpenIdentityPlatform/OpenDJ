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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.util;



import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.Properties;
import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.SendFailedException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.opends.server.core.DirectoryServer;

import static org.opends.server.loggers.debug.DebugLogger.debugCought;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.UtilityMessages.*;


/**
 * This class defines an e-mail message that may be sent to one or more
 * recipients via SMTP.  This is a wrapper around JavaMail to make this process
 * more convenient and fit better into the Directory Server framework.
 */
public class EMailMessage
{



  // The addresses of the recipients to whom this message should be sent.
  private ArrayList<String> recipients;

  // The set of attachments to include in this message.
  private LinkedList<MimeBodyPart> attachments;

  // The MIME type for the message body.
  private String bodyMIMEType;

  // The address of the sender for this message.
  private String sender;

  // The subject for the mail message.
  private String subject;

  // The body for the mail message.
  private StringBuilder body;



  /**
   * Creates a new e-mail message with the provided information.
   *
   * @param  sender     The address of the sender for the message.
   * @param  recipient  The address of the recipient for the message.
   * @param  subject    The subject to use for the message.
   */
  public EMailMessage(String sender, String recipient, String subject)
  {

    this.sender  = sender;
    this.subject = subject;

    recipients = new ArrayList<String>();
    recipients.add(recipient);

    body         = new StringBuilder();
    attachments  = new LinkedList<MimeBodyPart>();
    bodyMIMEType = "text/plain";
  }



  /**
   * Creates a new e-mail message with the provided information.
   *
   * @param  sender      The address of the sender for the message.
   * @param  recipients  The addresses of the recipients for the message.
   * @param  subject     The subject to use for the message.
   */
  public EMailMessage(String sender, ArrayList<String> recipients,
                      String subject)
  {

    this.sender     = sender;
    this.recipients = recipients;
    this.subject    = subject;

    body = new StringBuilder();
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
  public ArrayList<String> getRecipients()
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
   * Retrieves the body for this message.  It may be directly manipulated by the
   * caller.
   *
   * @return  The body for this message.
   */
  public StringBuilder getBody()
  {

    return body;
  }



  /**
   * Specifies the body for this message.
   *
   * @param  body  The body for this message.
   */
  public void setBody(StringBuilder body)
  {

    this.body = body;
  }



  /**
   * Specifies the body for this message.
   *
   * @param  bodyString  The body for this message.
   */
  public void setBody(String bodyString)
  {

    body = new StringBuilder(bodyString);
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
  public LinkedList<MimeBodyPart> getAttachments()
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
  public void addAttachment(File attachmentFile)
         throws MessagingException
  {

    MimeBodyPart attachment = new MimeBodyPart();

    FileDataSource dataSource = new FileDataSource(attachmentFile);
    attachment.setDataHandler(new DataHandler(dataSource));

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


    // Get information about the available mail servers that we can use.
    MessagingException sendException = null;
    for (Properties props : DirectoryServer.getMailServerPropertySets())
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
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, me);
        }

        int msgID = MSGID_EMAILMSG_INVALID_SENDER_ADDRESS;
        String msg = getMessage(msgID, String.valueOf(sender),
                                me.getMessage());
        throw new MessagingException(msg, me);
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
          if (debugEnabled())
          {
            debugCought(DebugLogLevel.ERROR, me);
          }

          int msgID = MSGID_EMAILMSG_INVALID_RECIPIENT_ADDRESS;
          String msg = getMessage(msgID, String.valueOf(recipient),
                                  me.getMessage());
          throw new MessagingException(msg, me);
        }
      }
      message.setRecipients(Message.RecipientType.TO, recipientAddresses);


      // If we have any attachments, then the whole thing needs to be
      // multipart.  Otherwise, just set the text of the message.
      if (attachments.isEmpty())
      {
        message.setText(body.toString());
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
      }


      // Try to send the message.  If this fails, it can be a complete failure
      // or a partial one.  If it's a complete failure then try rolling over to
      // the next server.  If it's a partial one, then that likely means that
      // the message was sent but one or more recipients was rejected, so we'll
      // propagate that back to the caller.
      try
      {
        Transport.send(message);
      }
      catch (SendFailedException sfe)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, sfe);
        }

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
    if (sendException == null)
    {
      int    msgID   = MSGID_EMAILMSG_CANNOT_SEND;
      String message = getMessage(msgID);
      throw new MessagingException(message);
    }
    else
    {
      throw sendException;
    }
  }
}

