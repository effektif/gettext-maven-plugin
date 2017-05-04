package org.xnap.commons.maven.gettext;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.apache.maven.model.FileSet;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;

/**
 * Invokes xgettext to extract messages from source code and store them in the
 * keys.pot file.
 */
@Mojo(name = "gettext", defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class GettextMojo extends AbstractGettextMojo {

    /**
     * The encoding of the source Java files. utf-8 is a superset of ascii.
     */
    @Parameter(defaultValue = "utf-8", required = true)
    protected String encoding;

    /**
     * The keywords the xgettext parser will look for to extract messages. The default value works with libraries that use the _-format method, as
     * recommended by Gettext documentation.
     */
    @Parameter(defaultValue = "-k_ -k_n1,2", required = true)
    protected String keywords;

    /**
     * The xgettext command.
     */
    @Parameter(defaultValue = "xgettext", required = true)
    protected String xgettextCmd;

    /**
     * An optional set of source files that should be parsed with xgettext.
     * <pre>
     * <extraSourceFiles>
     *   <directory>${basedir}</directory>
     *   <includes>
     *      <include>** /*.jsp</include>
     *    </includes>
     *    <excludes>
     *      <exclude>** /*.txt</exclude>
     *    </excludes>
     * </extraSourceFiles>
     * </pre>
     */
    @Parameter
    protected FileSet extraSourceFiles;

    @Parameter(defaultValue = "false")
    protected boolean omitHeader;

    @Parameter(defaultValue = "false")
    protected boolean joinExisting;

    /**
     * Set the copyright holder in the output. string should be the copyright
     * holder of the surrounding package. (Note that the msgstr strings,
     * extracted from the package’s sources, belong to the copyright holder of
     * the package.) Translators are expected to transfer or disclaim the
     * copyright for their translations, so that package maintainers can
     * distribute them without legal risk. If string is empty, the output files
     * are marked as being in the public domain; in this case, the translators
     * are expected to disclaim their copyright, again so that package
     * maintainers can distribute them without legal risk.
     *
     * The default value for string is the '' so public domain.
     */
    @Parameter(defaultValue = "")
    protected String copyrightHolder;

    /**
     * Set the package name in the header of the output.
     */
    @Parameter
    protected String packageName;

    /**
     * Set the package version in the header of the output. This option has an
     * effect only if the ‘packageName’ option is also used.
     */
    @Parameter
    protected String packageVersion;

    /**
     * Set the reporting address for msgid bugs. This is the email address or
     * URL to which the translators shall report bugs in the untranslated
     * strings: - Strings which are not entire sentences; see the maintainer
     * guidelines in Preparing Strings. - Strings which use unclear terms or
     * require additional context to be understood. - Strings which make invalid
     * assumptions about notation of date, time or money. - Pluralisation
     * problems. - Incorrect English spelling. - Incorrect formatting. It can be
     * your email address, or a mailing list address where translators can write
     * to without being subscribed, or the URL of a web page through which the
     * translators can contact you.
     *
     * The default value is empty, which means that translators will be
     * clueless! Don’t forget to specify this option.
     */
    @Parameter
    protected String msgidBugsAddress;

    public void execute() throws MojoExecutionException {
        getLog().info("Invoking xgettext for Java files in '"
                + sourceDirectory.getAbsolutePath() + "'.");

        Commandline cl = new Commandline();
        cl.setExecutable(xgettextCmd);
        addExtraArguments(cl);
        cl.createArg().setValue("--from-code=" + encoding);
        cl.createArg().setValue("--output=" + new File(poDirectory, keysFile).getAbsolutePath());
        cl.createArg().setValue("--language=Java");
        cl.createArg().setValue("--sort-output");
        if (omitHeader) {
            cl.createArg().setValue("--omit-header");
        } else {
            cl.createArg().setValue("--copyright-holder=" + copyrightHolder);
            if (packageName != null) {
                cl.createArg().setValue("--package-name=" + packageName);
            } else {
                cl.createArg().setValue("--package-name=" + project.getGroupId() + ":" + project.getArtifactId());
            }
            if (packageVersion != null) {
                cl.createArg().setValue("--package-version=" + packageVersion);
            } else {
                cl.createArg().setValue("--package-version=" + project.getVersion());
            }
            if (msgidBugsAddress != null) {
                cl.createArg().setValue("--msgid-bugs-address=" + msgidBugsAddress);
            } else {
                String bugAddress = project.getUrl();
                if (bugAddress == null || bugAddress.trim().isEmpty()) {
                    if (project.getOrganization() != null) {
                        bugAddress = project.getOrganization().getUrl();
                    }
                }
                cl.createArg().setValue("--msgid-bugs-address=" + bugAddress);
            }

        }
        if (omitLocation) {
            cl.createArg().setValue("--no-location");
        }

        if (joinExisting) {
            cl.createArg().setValue("--join-existing");
        }
        cl.createArg().setLine(keywords);
        cl.setWorkingDirectory(sourceDirectory.getAbsolutePath());

        DirectoryScanner ds = new DirectoryScanner();
        ds.setBasedir(sourceDirectory);
        ds.setIncludes(new String[]{"**/*.java"});
        ds.scan();
        String[] files = ds.getIncludedFiles();
        List<String> fileNameList = Collections.emptyList();
        if (extraSourceFiles != null && extraSourceFiles.getDirectory() != null) {
            try {
                fileNameList = FileUtils.getFileNames(new File(extraSourceFiles.getDirectory()),
                        StringUtils.join(extraSourceFiles.getIncludes().iterator(), ","),
                        StringUtils.join(extraSourceFiles.getExcludes().iterator(), ","), false);
            } catch (IOException e) {
                throw new MojoExecutionException("error finding extra source files", e);
            }
        }

        File file = createListFile(files, fileNameList);
        if (file != null) {
            cl.createArg().setValue("--files-from=" + file.getAbsolutePath());
        } else {
            for (String file1 : files) {
                cl.createArg().setValue(getAbsolutePath(file1));
            }
        }

        getLog().info("Executing: " + cl.toString());
        StreamConsumer out = new LoggerStreamConsumer(getLog(), LoggerStreamConsumer.INFO);
        StreamConsumer err = new LoggerStreamConsumer(getLog(), LoggerStreamConsumer.WARN);
        try {
            CommandLineUtils.executeCommandLine(cl, out, err);
        } catch (CommandLineException e) {
            throw new MojoExecutionException("Could not execute " + xgettextCmd + ".", e);
        }
    }

    private File createListFile(String[] files, List<String> fileList) {
        try {
            File listFile = File.createTempFile("maven", null);
            listFile.deleteOnExit();

            BufferedWriter writer = new BufferedWriter(new FileWriter(listFile));
            try {
                for (String file : files) {
                    writer.write(toUnixPath(file));
                    writer.newLine();
                }
                for (String file : fileList) {
                    writer.write(toUnixPath(file));
                    writer.newLine();
                }
            } finally {
                writer.close();
            }

            return listFile;
        } catch (IOException e) {
            getLog().error("Could not create list file.", e);
            return null;
        }
    }

    private String getAbsolutePath(String path) {
        return sourceDirectory.getAbsolutePath() + File.separator + path;
    }

    private String toUnixPath(String path) {
        if (File.separatorChar != '/') {
            return path.replace(File.separatorChar, '/');
        }
        return path;
    }

}
