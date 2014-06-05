package org.technicalsoftwareconfigurationmanagement.maven.plugin;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.ReaderFactory;
import org.codehaus.plexus.util.StringUtils;
import org.dom4j.Document;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;

/**
 * The XML Formatter is a plugin that is designed to be run from the parent POM of a project, so that all XML files
 * within the project can be formatting using one formatting option (either spaces or tabs). This is due to the fact
 * that when a big project is being worked on by many different people, with each person using their own preferred
 * formatting style, the files become hard to read.
 * 
 * 
 * <p>
 * The plugin contains two arrays in which you can specify which files to include/exclude from the formatting. <strong>
 * By default all XML files are included, except those in the target folder.</strong>
 * 
 * <p>
 * To use this plugin, type <strong>one</strong> of the following at the command line:
 * <UL>
 * <LI>mvn org.technicalsoftwareconfigurationmanagement.maven-plugin:tscm-maven-plugin:2.1-SNAPSHOT:xmlFormatter
 * <LI>mvn org.technicalsoftwareconfigurationmanagement.maven-plugin:tscm-maven-plugin:xmlFormatter
 * <LI>mvn tscm:xmlFormatter
 * </UL>
 * 
 * <p>
 * To format the files using tabs instead of spaces, add this onto the end of one of the above commands.
 * <UL>
 * <LI>-DxmlFormatter.useTabs="true"
 * 
 * <p>
 * Developer's Note: At the moment the code is setup to only work with Java 1.6 or newer because of the use of
 * transformations (JAXP which was included in Java in version 1.6).
 * 
 * @goal xmlFormatter
 **/
public class XmlFormatter extends AbstractMojo {

    private static MessageDigest sha;

    static {
        try {
            sha = MessageDigest.getInstance("SHA1");
        }
        catch (NoSuchAlgorithmException ignored) {
        }
    }

    static final String LINE_ENDING_AUTO = "AUTO";

    static final String LINE_ENDING_KEEP = "KEEP";

    static final String LINE_ENDING_LF = "LF";

    static final String LINE_ENDING_CRLF = "CRLF";

    static final String LINE_ENDING_CR = "CR";

    static final String LINE_ENDING_LF_CHAR = "\n";

    static final String LINE_ENDING_CRLF_CHARS = "\r\n";

    static final String LINE_ENDING_CR_CHAR = "\r";

    /**
     * Since parent pom execution causes multiple executions we created this static map that contains the fully
     * qualified file names for all of the files we process and we check it before processing a file and skip if we have
     * already processed the file. note that the execution method is called numerous times within the same JVM (hence
     * the static reference works but a local reference might not work).
     **/
    private static Set<String> processedFileNames = new HashSet<String>();

    /**
     * Called automatically by each module in the project, including the parent module. All files will formatted with
     * either <i>spaces</i> or <i>tabs</i>, and will be written back to it's original location.
     * 
     * @throws MojoExecutionException
     **/
    public void execute() throws MojoExecutionException {

        if ((baseDirectory != null) && (getLog().isDebugEnabled())) {
            getLog().debug("[xml formatter] Base Directory:" + baseDirectory);
        }

        if (!LINE_ENDING_AUTO.equals(lineEnding) && !LINE_ENDING_KEEP.equals(lineEnding)
            && !LINE_ENDING_LF.equals(lineEnding) && !LINE_ENDING_CRLF.equals(lineEnding)
            && !LINE_ENDING_CR.equals(lineEnding)) {
            throw new MojoExecutionException("Unknown value for lineEnding parameter");
        }

        if (includes != null) {
            String[] filesToFormat = getIncludedFiles(baseDirectory, includes, excludes);

            if (getLog().isDebugEnabled()) {
                getLog().debug("[xml formatter] Format " + filesToFormat.length + " source files in " + baseDirectory);
            }

            for (String include : filesToFormat) {
                try {

                    if (!processedFileNames.contains(baseDirectory + File.separator + include)) {
                        processedFileNames.add(baseDirectory + File.separator + include);
                        format(new File(baseDirectory + File.separator + include));
                    }
                }
                catch (RuntimeException re) {
                    getLog().error(
                        "File <" + baseDirectory + File.separator + include
                            + "> failed to parse, skipping and moving on to the next file", re);
                }
            }
        }
    }

    /**
     * A flag used to tell the program to format with either <i>spaces</i> or <i>tabs</i>. By default, the formatter
     * uses spaces.
     * 
     * <UL>
     * <LI><tt>true</tt> - tabs</LI>
     * <LI><tt>false</tt> - spaces</LI>
     * <UL>
     * 
     * <p>
     * In configure this parameter to use tabs, use the following at the command line: -DxmlFormatter.useTabs="true"
     * 
     * @parameter expression="${xmlFormatter.useTabs}" default-value="false"
     **/
    private boolean useTabs;

    /**
     * The base directory of the project.
     * 
     * @parameter expression="${basedir}"
     **/
    private File baseDirectory;

    /**
     * A set of file patterns that dictates which files should be included in the formatting with each file pattern
     * being relative to the base directory. <i>By default all xml files are included.</i> This parameter is most easily
     * configured in the parent pom file.
     * 
     * @parameter alias="includes"
     **/
    private String[] includes = { "**/*.xml" };

    /**
     * A set of file patterns that allow you to exclude certain files/folders from the formatting. <i>By default the
     * target folder is excluded from the formatting.</i> This parameter is most easily configured in the parent pom
     * file.
     * 
     * @parameter alias="excludes"
     **/
    private String[] excludes = { "**/target/**" };

    /**
     * By default we have setup the exclude list to remove the target folders. Setting any value including an empty
     * array will overide this functionality. This parameter can be configured in the POM file using the 'excludes'
     * alias in the configuration option. Note that all files are relative to the parent POM.
     * 
     * @param excludes
     *            - String array of patterns or filenames to exclude from formatting.
     **/
    public void setExcludes(String[] excludes) {
        this.excludes = excludes;
    }

    /**
     * By default all XML files ending with .xml are included for formatting. This parameter can be configured in the
     * POM file using the 'includes' alias in the configuration option. Note that all files are relative to the parent
     * POM.
     * 
     * @param includes
     *            - Default "**\/*.xml". Assigning a new value overrides the default settings.
     **/
    public void setIncludes(String[] includes) {
        this.includes = includes;
    }

    /**
     * The file encoding used to read and write source files. When not specified and sourceEncoding also not set,
     * default is platform file encoding.
     * 
     * @parameter default-value="${project.build.sourceEncoding}"
     */
    private String encoding;

    /**
     * Sets the line-ending of files after formatting. Valid values are:
     * <ul>
     * <li><b>"AUTO"</b> - Use line endings of current system</li>
     * <li><b>"KEEP"</b> - Preserve line endings of files, default to AUTO if ambiguous</li>
     * <li><b>"LF"</b> - Use Unix and Mac style line endings</li>
     * <li><b>"CRLF"</b> - Use DOS and Windows style line endings</li>
     * <li><b>"CR"</b> - Use early Mac style line endings</li>
     * </ul>
     * 
     * @parameter default-value="AUTO"
     **/
    private String lineEnding;

    /**
     * Scans the given directory for files to format, and returns them in an array. The files are only added to the
     * array if they match a pattern in the <tt>includes</tt> array, and <strong>do not</strong> match any pattern in
     * the <tt>excludes</tt> array.
     * 
     * @param directory
     *            - Base directory from which we start scanning for files. Note that this must be the root directory of
     *            the project in order to obtain the pom.xml as part of the XML files. This is one other differentiator
     *            when we were looking for tools, anything we found remotely like this did not start at the root
     *            directory.
     * @param includes
     *            - A string array containing patterns that are used to search for files that should be formatted.
     * @param excludes
     *            - A string array containing patterns that are used to filter out files so that they are
     *            <strong>not</strong> formatted.
     * @return - A string array containing all the files that should be formatted.
     **/
    public String[] getIncludedFiles(File directory, String[] includes, String[] excludes) {

        DirectoryScanner dirScanner = new DirectoryScanner();
        dirScanner.setBasedir(directory);
        dirScanner.setIncludes(includes);
        dirScanner.setExcludes(excludes);
        dirScanner.scan();

        String[] filesToFormat = dirScanner.getIncludedFiles();

        if (getLog().isDebugEnabled()) {

            if (useTabs) {
                getLog().debug("[xml formatter] Formatting with tabs...");
            }
            else {
                getLog().debug("[xml formatter] Formatting with spaces...");
            }

            getLog().debug("[xml formatter] Files:");
            for (String file : filesToFormat) {
                getLog().debug("[xml formatter] file<" + file + "> is scheduled for formatting");
            }
        }

        return filesToFormat;
    }

    /**
     * Formats the provided file, writing it back to it's original location.
     * 
     * @param formatFile
     *            - File to be formatted. The output file is the same as the input file. Please be sure that you have
     *            your files in a revision control system (and saved before running this plugin).
     **/
    public void format(File formatFile) {

        if (formatFile.exists() && formatFile.isFile()) {

            InputStream inputStream = null;
            Document xmlDoc = null;
            XMLWriter xmlWriter = null;

            try {
                inputStream = new FileInputStream(formatFile);

                SAXReader reader = new SAXReader();
                xmlDoc = reader.read(inputStream);

                getLog().debug("[xml formatter] Successfully parsed file: " + formatFile);

            }
            catch (Throwable t) {
                throw new RuntimeException("[xml formatter] Failed to parse..." + t.getMessage(), t);
            }
            finally {
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    }
                    catch (Throwable tr) {
                        // intentially exception hiding for failures on close....
                    }
                }
            }

            FileOutputStream fos = null;
            File tmpFile = null;

            try {
                tmpFile = File.createTempFile("xmlFormatter", ".xml");
                fos = new FileOutputStream(tmpFile);
                final OutputFormat outputFormat = OutputFormat.createPrettyPrint();
                outputFormat.setIndentSize(4);
                outputFormat.setNewLineAfterDeclaration(false);
                outputFormat.setPadText(false);

                final String lineSeparator = getLineEnding(formatFile);
                outputFormat.setLineSeparator(lineSeparator);
                xmlWriter = new XMLWriter(
                    fos, outputFormat) {
                    @Override
                    protected void writeComment(String text) throws IOException {
                        // must replace the line endings in comments
                        if (StringUtils.isNotBlank(text) && !LINE_ENDING_KEEP.equals(lineEnding)) {
                            text = text.replaceAll("\\r\\n|\\r|\\n", lineSeparator);
                            text = text.replaceAll("\t", outputFormat.getIndent());
                        }

                        super.writeComment(text);
                    }
                };
                xmlWriter.write(xmlDoc);
                xmlWriter.flush();

            }
            catch (Throwable t) {
                if (tmpFile != null) {
                    tmpFile.delete();
                }
                throw new RuntimeException("[xml formatter] Failed to parse..." + t.getMessage(), t);
            }
            finally {
                if (fos != null) {
                    try {
                        fos.close();
                    }
                    catch (Throwable t) {
                        // intentially exception hiding for failures on close....
                    }
                }
                if (xmlWriter != null) {
                    try {
                        xmlWriter.close();
                    }
                    catch (IOException e) {
                        getLog().error(e.getMessage(), e);
                    }
                }
            }

            // Now that we know that the indent is set to four spaces, we can either
            // keep it like that or change them to tabs depending on which 'mode' we
            // are in.

            if (useTabs) {
                indentFile(tmpFile);
            }

            // Copy tmpFile to formatFile, but only if the content has actually changed
            String tmpFileHash = getSha1(tmpFile);
            String formatFileHash = getSha1(formatFile);
            if (tmpFileHash != null && formatFileHash != null && tmpFileHash.equals(formatFileHash)) {
                // Exact match, so skip
                getLog().info("[xml formatter] File unchanged after formatting: " + formatFile);
                tmpFile.delete();
                return;
            }
            // To get here indicates a hash comparison failure, or the file has modified after formatting. Copy the
            // bytes
            FileInputStream source = null;
            FileOutputStream destination = null;
            try {
                source = new FileInputStream(tmpFile);
                destination = new FileOutputStream(formatFile);
                byte[] buffer = new byte[4096];
                int bytesRead;

                while ((bytesRead = source.read(buffer)) != -1) {
                    destination.write(buffer, 0, bytesRead); // write
                }
                getLog().info("[xml formatter] File reformatted: " + formatFile);
            }
            catch (IOException ioe) {
                getLog().error("[xml formatter] File copying failed for: " + tmpFile + " -> " + formatFile);
            }
            finally {
                if (source != null) {
                    try {
                        source.close();
                    }
                    catch (IOException ignored) {
                    }
                }
                if (destination != null) {
                    try {
                        destination.close();
                    }
                    catch (IOException ignored) {
                    }
                }
                tmpFile.delete();
            }
        }
        else {
            getLog().info("[xml formatter] File was not valid: " + formatFile + "; skipping");
        }
    }

    private String getSha1(File file) {
        FileInputStream fis = null;
        byte[] dataBytes = new byte[1024];
        byte[] sha1Bytes = null;
        int read = 0;

        if (sha == null) {
            return null;
        }

        try {
            fis = new FileInputStream(file);
            while ((read = fis.read(dataBytes)) != -1) {
                sha.update(dataBytes, 0, read);
            }
            ;

            sha1Bytes = sha.digest();
        }
        catch (IOException ioe) {
            return null;
        }
        finally {
            sha.reset();
            if (fis != null) {
                try {
                    fis.close();
                }
                catch (IOException ignored) {
                    return null;
                }
            }
        }

        StringBuffer sha1AsHex = new StringBuffer("");
        for (int i = 0; i < sha1Bytes.length; i++) {
            sha1AsHex.append(Integer.toString((sha1Bytes[i] & 0xff) + 0x100, 16).substring(1));
        }

        return sha1AsHex.toString();
    }

    /**
     * Indents the file using tabs, writing it back to its original location. This method is only called if useTabs is
     * set to true.
     * 
     * @param file
     *            The file to be indented using tabs.
     **/
    private void indentFile(File file) {

        List<String> temp = new ArrayList<String>(); // a temporary list to hold the lines
        BufferedReader reader = null;
        BufferedWriter writer = null;

        // Read the file, and replace the four spaces with tabs.
        try {
            reader = new BufferedReader(new FileReader(file));
            String line = null;

            while ((line = reader.readLine()) != null) {
                temp.add(line.replaceAll("[\\s]{4}", "\t"));
            }

            writer = new BufferedWriter(new FileWriter(file));

            for (String ln : temp) {
                writer.write(ln);
                writer.newLine();
            }
        }
        catch (Throwable t) {
            throw new RuntimeException("[xml formatter] Failed to read file..." + t.getMessage(), t);
        }
        finally {
            if (reader != null) {
                try {
                    reader.close();
                }
                catch (Throwable t) {
                    // Intentionally catching exception...
                }
            }

            if (writer != null) {
                try {
                    writer.flush();
                    writer.close();
                }
                catch (Throwable t) {
                    // Intentionally catching exception...
                }
            }
        }
    }

    /**
     * Read the given file and return the content as a string.
     * 
     * @param file
     * @return
     * @throws java.io.IOException
     */
    private String readFileAsString(File file) throws java.io.IOException {
        StringBuilder fileData = new StringBuilder(1000);
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(ReaderFactory.newReader(file, encoding));
            char[] buf = new char[1024];
            int numRead = 0;
            while ((numRead = reader.read(buf)) != -1) {
                String readData = String.valueOf(buf, 0, numRead);
                fileData.append(readData);
                buf = new char[1024];
            }
        }
        finally {
            IOUtil.close(reader);
        }
        return fileData.toString();
    }

    /**
     * Returns the lineEnding parameter as characters when the value is known (LF, CRLF, CR) or can be determined from
     * the file text (KEEP). Otherwise null is returned.
     * 
     * @return the line ending
     */
    String getLineEnding(File formatFile) throws java.io.IOException {
        String lineEnd = null;
        if (LINE_ENDING_KEEP.equals(lineEnding)) {
            String fileDataString = readFileAsString(formatFile);
            lineEnd = determineLineEnding(fileDataString);
        }
        else if (LINE_ENDING_LF.equals(lineEnding)) {
            lineEnd = LINE_ENDING_LF_CHAR;
        }
        else if (LINE_ENDING_CRLF.equals(lineEnding)) {
            lineEnd = LINE_ENDING_CRLF_CHARS;
        }
        else if (LINE_ENDING_CR.equals(lineEnding)) {
            lineEnd = LINE_ENDING_CR_CHAR;
        }
        else if (LINE_ENDING_AUTO.equals(lineEnding)) {
            lineEnd = System.getProperty("line.separator");
        }
        return lineEnd;
    }

    /**
     * Returns the most occurring line-ending characters in the file text or null if no line-ending occurs the most.
     * 
     * @return
     */
    String determineLineEnding(String fileDataString) {
        int lfCount = 0;
        int crCount = 0;
        int crlfCount = 0;

        for (int i = 0; i < fileDataString.length(); i++) {
            char c = fileDataString.charAt(i);
            if (c == '\r') {
                if ((i + 1) < fileDataString.length() && fileDataString.charAt(i + 1) == '\n') {
                    crlfCount++;
                    i++;
                }
                else {
                    crCount++;
                }
            }
            else if (c == '\n') {
                lfCount++;
            }
        }
        if (lfCount > crCount && lfCount > crlfCount) {
            return LINE_ENDING_LF_CHAR;
        }
        else if (crlfCount > lfCount && crlfCount > crCount) {
            return LINE_ENDING_CRLF_CHARS;
        }
        else if (crCount > lfCount && crCount > crlfCount) {
            return LINE_ENDING_CR_CHAR;
        }
        return null;
    }
}
