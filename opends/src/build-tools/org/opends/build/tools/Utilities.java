package org.opends.build.tools;
import org.opends.messages.Message;

import java.util.StringTokenizer;

/**
 */
public class Utilities {

  /**
   * The end-of-line character for this platform.
   */
  public static final String EOL = System.getProperty("line.separator");  

  /**
   * Inserts line breaks into the provided buffer to wrap text at no more than
   * the specified column width.  Wrapping will only be done at space boundaries
   * and if there are no spaces within the specified width, then wrapping will
   * be performed at the first space after the specified column.
   *
   * @param  text   The text to be wrapped.
   * @param  width  The maximum number of characters to allow on a line if there
   *                is a suitable breaking point.
   *
   * @return  The wrapped text.
   */
  public static String wrapText(String text, int width)
  {
    StringBuilder   buffer        = new StringBuilder();
    StringTokenizer lineTokenizer = new StringTokenizer(text, "\r\n", true);
    while (lineTokenizer.hasMoreTokens())
    {
      String line = lineTokenizer.nextToken();
      if (line.equals("\r") || line.equals("\n"))
      {
        // It's an end-of-line character, so append it as-is.
        buffer.append(line);
      }
      else if (line.length() < width)
      {
        // The line fits in the specified width, so append it as-is.
        buffer.append(line);
      }
      else
      {
        // The line doesn't fit in the specified width, so it needs to be
        // wrapped.  Do so at space boundaries.
        StringBuilder   lineBuffer    = new StringBuilder();
        StringBuilder   delimBuffer   = new StringBuilder();
        StringTokenizer wordTokenizer = new StringTokenizer(line, " ", true);
        while (wordTokenizer.hasMoreTokens())
        {
          String word = wordTokenizer.nextToken();
          if (word.equals(" "))
          {
            // It's a space, so add it to the delim buffer only if the line
            // buffer is not empty.
            if (lineBuffer.length() > 0)
            {
              delimBuffer.append(word);
            }
          }
          else if (word.length() > width)
          {
            // This is a long word that can't be wrapped, so we'll just have to
            // make do.
            if (lineBuffer.length() > 0)
            {
              buffer.append(lineBuffer);
              buffer.append(EOL);
              lineBuffer = new StringBuilder();
            }
            buffer.append(word);

            if (wordTokenizer.hasMoreTokens())
            {
              // The next token must be a space, so remove it.  If there are
              // still more tokens after that, then append an EOL.
              wordTokenizer.nextToken();
              if (wordTokenizer.hasMoreTokens())
              {
                buffer.append(EOL);
              }
            }

            if (delimBuffer.length() > 0)
            {
              delimBuffer = new StringBuilder();
            }
          }
          else
          {
            // It's not a space, so see if we can fit it on the curent line.
            int newLineLength = lineBuffer.length() + delimBuffer.length() +
                                word.length();
            if (newLineLength < width)
            {
              // It does fit on the line, so add it.
              lineBuffer.append(delimBuffer).append(word);

              if (delimBuffer.length() > 0)
              {
                delimBuffer = new StringBuilder();
              }
            }
            else
            {
              // It doesn't fit on the line, so end the current line and start
              // a new one.
              buffer.append(lineBuffer);
              buffer.append(EOL);

              lineBuffer = new StringBuilder();
              lineBuffer.append(word);

              if (delimBuffer.length() > 0)
              {
                delimBuffer = new StringBuilder();
              }
            }
          }
        }

        // If there's anything left in the line buffer, then add it to the
        // final buffer.
        buffer.append(lineBuffer);
      }
    }

    return buffer.toString();
  }

}
