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
package org.opends.server.plugins.profiler;
import org.opends.messages.Message;



import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Font;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.DefaultTreeSelectionModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;

import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.asn1.ASN1Reader;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.StringArgument;

import static org.opends.messages.PluginMessages.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a Directory Server utility that may be used to view
 * profile information that has been captured by the profiler plugin.  It
 * supports viewing this information in either a command-line mode or using a
 * simple GUI.
 */
public class ProfileViewer
       implements TreeSelectionListener
{
  // The root stack frames for the profile information that has been captured.
  private HashMap<ProfileStackFrame,ProfileStackFrame> rootFrames;

  // A set of stack traces indexed by class and method name.
  private HashMap<String,HashMap<ProfileStack,Long>> stacksByMethod;

  // The editor pane that will provide detailed information about the selected
  // stack frame.
  private JEditorPane frameInfoPane;

  // The GUI tree that will be used to hold stack frame information;
  private JTree profileTree;

  // The total length of time in milliseconds for which data is available.
  private long totalDuration;

  // The total number of profile intervals for which data is available.
  private long totalIntervals;



  /**
   * Parses the command-line arguments and creates an instance of the profile
   * viewer as appropriate.
   *
   * @param  args  The command-line arguments provided to this program.
   */
  public static void main(String[] args)
  {
    // Define the command-line arguments that may be used with this program.
    BooleanArgument displayUsage = null;
    BooleanArgument useGUI       = null;
    StringArgument  fileNames    = null;


    // Create the command-line argument parser for use with this program.
    Message toolDescription = INFO_PROFILEVIEWER_TOOL_DESCRIPTION.get();
    ArgumentParser argParser =
         new ArgumentParser("org.opends.server.plugins.profiler.ProfileViewer",
                            toolDescription, false);


    // Initialize all the command-line argument types and register them with the
    // parser.
    try
    {
      fileNames =
        new StringArgument("filenames", 'f', "fileName", true, true, true,
                           "{file}", null, null,
                           INFO_PROFILEVIEWER_DESCRIPTION_FILENAMES.get());
      argParser.addArgument(fileNames);

      useGUI = new BooleanArgument(
              "usegui", 'g', "useGUI",
              INFO_PROFILEVIEWER_DESCRIPTION_USE_GUI.get());
      argParser.addArgument(useGUI);

      displayUsage = new BooleanArgument(
              "help", 'H', "help",
              INFO_PROFILEVIEWER_DESCRIPTION_USAGE.get());
      argParser.addArgument(displayUsage);
      argParser.setUsageArgument(displayUsage);
    }
    catch (ArgumentException ae)
    {
      Message message =
              ERR_PROFILEVIEWER_CANNOT_INITIALIZE_ARGS.get(ae.getMessage());

      System.err.println(message);
      System.exit(1);
    }


    // Parse the command-line arguments provided to this program.
    try
    {
      argParser.parseArguments(args);
    }
    catch (ArgumentException ae)
    {
      Message message =
              ERR_PROFILEVIEWER_ERROR_PARSING_ARGS.get(ae.getMessage());

      System.err.println(message);
      System.err.println(argParser.getUsage());
      System.exit(1);
    }


    // If we should just display usage or versionn information,
    // then print it and exit.
    if (argParser.usageOrVersionDisplayed())
    {
      System.exit(0);
    }


    // Create the profile viewer and read in the data files.
    ProfileViewer viewer = new ProfileViewer();
    for (String filename : fileNames.getValues())
    {
      try
      {
        viewer.processDataFile(filename);
      }
      catch (Exception e)
      {
        Message message =
                ERR_PROFILEVIEWER_CANNOT_PROCESS_DATA_FILE.get(filename,
                                    stackTraceToSingleLineString(e));
        System.err.println(message);
      }
    }


    // Write the captured information to standard output or display it in a GUI.
    if (useGUI.isPresent())
    {
      viewer.displayGUI();
    }
    else
    {
      viewer.printProfileData();
    }
  }



  /**
   * Creates a new profile viewer object without any data.  It should be
   * populated with one or more calls to <CODE>processDataFile</CODE>
   */
  public ProfileViewer()
  {
    rootFrames     = new HashMap<ProfileStackFrame,ProfileStackFrame>();
    stacksByMethod = new HashMap<String,HashMap<ProfileStack,Long>>();
    totalDuration  = 0;
    totalIntervals = 0;
  }



  /**
   * Reads and processes the information in the provided data file into this
   * profile viewer.
   *
   * @param  filename  The path to the file containing the data to be read.
   *
   * @throws  IOException  If a problem occurs while trying to read from the
   *                       data file.
   *
   * @throws  ASN1Exception  If an error occurs while trying to decode the
   *                         contents of the file into profile stack objects.
   */
  public void processDataFile(String filename)
         throws IOException, ASN1Exception
  {
    // Try to open the file for reading.
    ASN1Reader reader = new ASN1Reader(new FileInputStream(filename));


    try
    {
      // The first element in the file must be a sequence with the header
      // information.
      ASN1Element element = reader.readElement();
      ArrayList<ASN1Element> elements = element.decodeAsSequence().elements();
      totalIntervals += elements.get(0).decodeAsLong().longValue();

      long startTime = elements.get(1).decodeAsLong().longValue();
      long stopTime  = elements.get(2).decodeAsLong().longValue();
      totalDuration += (stopTime - startTime);


      // The remaining elements will contain the stack frames.
      while (true)
      {
        element = reader.readElement();
        if (element == null)
        {
          break;
        }


        ProfileStack stack = ProfileStack.decode(element);

        element    = reader.readElement();
        long count = element.decodeAsLong().longValue();

        int pos = stack.getNumFrames() - 1;
        if (pos < 0)
        {
          continue;
        }

        String[] classNames  = stack.getClassNames();
        String[] methodNames = stack.getMethodNames();
        int[]    lineNumbers = stack.getLineNumbers();

        ProfileStackFrame frame = new ProfileStackFrame(classNames[pos],
                                                        methodNames[pos]);

        ProfileStackFrame existingFrame = rootFrames.get(frame);
        if (existingFrame == null)
        {
          existingFrame = frame;
        }

        String classAndMethod = classNames[pos] + "." + methodNames[pos];
        HashMap<ProfileStack,Long> stackMap =
             stacksByMethod.get(classAndMethod);
        if (stackMap == null)
        {
          stackMap = new HashMap<ProfileStack,Long>();
          stacksByMethod.put(classAndMethod, stackMap);
        }
        stackMap.put(stack, count);

        existingFrame.updateLineNumberCount(lineNumbers[pos], count);
        rootFrames.put(existingFrame, existingFrame);

        existingFrame.recurseSubFrames(stack, pos-1, count, stacksByMethod);
      }
    }
    finally
    {
      try
      {
        reader.close();
      } catch (Exception e) {}
    }
  }



  /**
   * Retrieves an array containing the root frames for the profile information.
   * The array will be sorted in descending order of matching stacks.  The
   * elements of this array will be the leaf method names with sub-frames
   * holding information about the callers of those methods.
   *
   * @return  An array containing the root frames for the profile information.
   */
  public ProfileStackFrame[] getRootFrames()
  {
    ProfileStackFrame[] frames = new ProfileStackFrame[0];
    frames = rootFrames.values().toArray(frames);

    Arrays.sort(frames);

    return frames;
  }



  /**
   * Retrieves the total number of sample intervals for which profile data is
   * available.
   *
   * @return  The total number of sample intervals for which profile data is
   *          available.
   */
  public long getTotalIntervals()
  {
    return totalIntervals;
  }



  /**
   * Retrieves the total duration in milliseconds covered by the profile data.
   *
   * @return  The total duration in milliseconds covered by the profile data.
   */
  public long getTotalDuration()
  {
    return totalDuration;
  }



  /**
   * Prints the profile information to standard output in a human-readable
   * form.
   */
  public void printProfileData()
  {
    System.out.println("Total Intervals:     " + totalIntervals);
    System.out.println("Total Duration:      " + totalDuration);

    System.out.println();
    System.out.println();

    for (ProfileStackFrame frame : getRootFrames())
    {
      printFrame(frame, 0);
    }
  }



  /**
   * Prints the provided stack frame and its subordinates using the provided
   * indent.
   *
   * @param  frame   The stack frame to be printed, followed by recursive
   *                 information about all its subordinates.
   * @param  indent  The number of tabs to indent the stack frame information.
   */
  private static void printFrame(ProfileStackFrame frame, int indent)
  {
    for (int i=0; i < indent; i++)
    {
      System.out.print("\t");
    }

    System.out.print(frame.getTotalCount());
    System.out.print("\t");
    System.out.print(frame.getClassName());
    System.out.print(".");
    System.out.println(frame.getMethodName());

    if (frame.hasSubFrames())
    {
      for (ProfileStackFrame f : frame.getSubordinateFrames())
      {
        printFrame(f, indent+1);
      }
    }
  }



  /**
   * Displays a simple GUI with the profile data.
   */
  public void displayGUI()
  {
    JFrame appWindow = new JFrame("Directory Server Profile Data");
    appWindow.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

    JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);

    Container contentPane = appWindow.getContentPane();
    contentPane.setLayout(new BorderLayout());
    contentPane.setFont(new Font("Monospaced", Font.PLAIN, 12));

    String blankHTML = "<HTML><BODY><BR><BR><BR><BR><BR><BR><BR><BR><BR><BR>" +
                       "</BODY></HTML>";
    frameInfoPane = new JEditorPane("text/html", blankHTML);
    splitPane.setBottomComponent(new JScrollPane(frameInfoPane));

    String label = "Profile Data:  " + totalIntervals + " sample intervals " +
                   "captured over " + totalDuration + " milliseconds";
    DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(label, true);

    ProfileStackFrame[] rootFrames = getRootFrames();
    if (rootFrames.length == 0)
    {
      System.err.println("ERROR:  No data available for viewing.");
      return;
    }

    for (ProfileStackFrame frame : getRootFrames())
    {
      boolean hasChildren = frame.hasSubFrames();

      DefaultMutableTreeNode frameNode =
          new DefaultMutableTreeNode(frame, hasChildren);
      recurseTreeNodes(frame, frameNode);

      rootNode.add(frameNode);
    }

    profileTree = new JTree(new DefaultTreeModel(rootNode, true));
    profileTree.setFont(new Font("Monospaced", Font.PLAIN, 12));

    DefaultTreeSelectionModel selectionModel = new DefaultTreeSelectionModel();
    selectionModel.setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    profileTree.setSelectionModel(selectionModel);
    profileTree.addTreeSelectionListener(this);
    profileTree.setSelectionPath(new TreePath(rootNode.getFirstChild()));
    valueChanged(null);

    splitPane.setTopComponent(new JScrollPane(profileTree));
    splitPane.setResizeWeight(0.5);
    splitPane.setOneTouchExpandable(true);
    contentPane.add(splitPane, BorderLayout.CENTER);

    appWindow.pack();
    appWindow.setVisible(true);
  }



  /**
   * Recursively adds subordinate nodes to the provided parent node with the
   * provided information.
   *
   * @param  parentFrame  The stack frame whose children are to be added as
   *                      subordinate nodes of the provided tree node.
   * @param  parentNode   The tree node to which the subordinate nodes are to be
   *                      added.
   */
  private void recurseTreeNodes(ProfileStackFrame parentFrame,
                                DefaultMutableTreeNode parentNode)
  {
    ProfileStackFrame[] subFrames = parentFrame.getSubordinateFrames();
    if (subFrames.length == 0)
    {
      return;
    }

    String largestCountString = String.valueOf(subFrames[0].getTotalCount());

    for (ProfileStackFrame subFrame : subFrames)
    {
      boolean hasChildren = parentFrame.hasSubFrames();

      DefaultMutableTreeNode subNode =
           new DefaultMutableTreeNode(subFrame, hasChildren);
      if (hasChildren)
      {
        recurseTreeNodes(subFrame, subNode);
      }

      parentNode.add(subNode);
    }
  }



  /**
   * Formats the provided count, padding with leading spaces as necessary.
   *
   * @param  count   The count value to be formatted.
   * @param  length  The total length for the string to return.
   *
   * @return  The formatted count string.
   */
  private String formatCount(long count, int length)
  {
    StringBuilder buffer = new StringBuilder(length);

    buffer.append(count);
    while (buffer.length() < length)
    {
      buffer.insert(0, ' ');
    }

    return buffer.toString();
  }



  /**
   * Indicates that a node in the tree has been selected or deselected and that
   * any appropriate action should be taken.
   *
   * @param  tse  The tree selection event with information about the selection
   *              or deselection that occurred.
   */
  public void valueChanged(TreeSelectionEvent tse)
  {
    try
    {
      TreePath path = profileTree.getSelectionPath();
      if (path == null)
      {
        // Nothing is selected, so we'll use use an empty panel.
        frameInfoPane.setText("");
        return;
      }


      DefaultMutableTreeNode selectedNode =
           (DefaultMutableTreeNode) path.getLastPathComponent();
      if (selectedNode == null)
      {
        // No tree node is selected, so we'll just use an empty panel.
        frameInfoPane.setText("");
        return;
      }


      // It is possible that this is the root node, in which case we'll empty
      // the info pane.
      Object selectedObject = selectedNode.getUserObject();
      if (! (selectedObject instanceof ProfileStackFrame))
      {
        frameInfoPane.setText("");
        return;
      }


      // There is a tree node selected, so we should convert it to a stack
      // frame and display information about it.
      ProfileStackFrame frame = (ProfileStackFrame) selectedObject;

      StringBuilder html = new StringBuilder();
      html.append("<HTML><BODY><PRE>");
      html.append("Information for stack frame <B>");
      html.append(frame.getClassName());
      html.append(".");
      html.append(frame.getHTMLSafeMethodName());
      html.append("</B><BR><BR>Occurrences by Source Line Number:<BR>");

      HashMap<Integer,Long> lineNumbers = frame.getLineNumbers();
      for (Integer lineNumber : lineNumbers.keySet())
      {
        html.append("     ");

        long count = lineNumbers.get(lineNumber);

        if (lineNumber == ProfileStack.LINE_NUMBER_NATIVE)
        {
          html.append("&lt;native&gt;");
        }
        else if (lineNumber == ProfileStack.LINE_NUMBER_UNKNOWN)
        {
          html.append("&lt;unknown&gt;");
        }
        else
        {
          html.append("Line ");
          html.append(lineNumber);
        }

        html.append(":  ");
        html.append(count);

        if (count == 1)
        {
          html.append(" occurrence<BR>");
        }
        else
        {
          html.append(" occurrences<BR>");
        }
      }

      html.append("<BR><BR>");
      html.append("<HR>Stack Traces Including this Method:");

      String classAndMethod = frame.getClassName() + "." +
                              frame.getMethodName();
      HashMap<ProfileStack,Long> stacks = stacksByMethod.get(classAndMethod);

      for (ProfileStack stack : stacks.keySet())
      {
        html.append("<BR><BR>");
        html.append(stacks.get(stack));
        html.append(" occurrence(s):");

        appendHTMLStack(stack, html, classAndMethod);
      }


      html.append("</PRE></BODY></HTML>");

      frameInfoPane.setText(html.toString());
      frameInfoPane.setSelectionStart(0);
      frameInfoPane.setSelectionEnd(0);
    }
    catch (Exception e)
    {
      e.printStackTrace();
      frameInfoPane.setText("");
    }
  }



  /**
   * Appends an HTML representation of the provided stack to the given buffer.
   *
   * @param  stack                    The stack trace to represent in HTML.
   * @param  html                     The buffer to which the HTML version of
   *                                  the stack should be appended.
   * @param  highlightClassAndMethod  The name of the class and method that
   *                                  should be highlighted in the stack frame.
   */
  private void appendHTMLStack(ProfileStack stack, StringBuilder html,
                               String highlightClassAndMethod)
  {
    int numFrames = stack.getNumFrames();
    for (int i=(numFrames-1); i >= 0; i--)
    {
      html.append("<BR>     ");

      String className  = stack.getClassName(i);
      String methodName = stack.getMethodName(i);
      int    lineNumber = stack.getLineNumber(i);

      String safeMethod =
           (methodName.equals("<init>") ? "&lt;init&gt;" : methodName);

      String classAndMethod = className + "." + methodName;
      if (classAndMethod.equals(highlightClassAndMethod))
      {
        html.append("<B>");
        html.append(className);
        html.append(".");
        html.append(safeMethod);
        html.append(":");

        if (lineNumber == ProfileStack.LINE_NUMBER_NATIVE)
        {
          html.append("&lt;native&gt;");
        }
        else if (lineNumber == ProfileStack.LINE_NUMBER_UNKNOWN)
        {
          html.append("&lt;unknown&gt;");
        }
        else
        {
          html.append(lineNumber);
        }

        html.append("</B>");
      }
      else
      {
        html.append(className);
        html.append(".");
        html.append(safeMethod);
        html.append(":");

        if (lineNumber == ProfileStack.LINE_NUMBER_NATIVE)
        {
          html.append("&lt;native&gt;");
        }
        else if (lineNumber == ProfileStack.LINE_NUMBER_UNKNOWN)
        {
          html.append("&lt;unknown&gt;");
        }
        else
        {
          html.append(lineNumber);
        }
      }
    }
  }
}

