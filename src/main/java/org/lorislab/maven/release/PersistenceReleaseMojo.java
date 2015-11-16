/*
 * Copyright 2015 Andrej Petras.
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
package org.lorislab.maven.release;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.lorislab.maven.release.model.SearchPattern;
import org.lorislab.maven.release.model.SearchResultItem;
import org.lorislab.maven.release.persistence.PersistenceModifier;
import org.lorislab.maven.release.persistence.PersistenceModifier10;
import org.lorislab.maven.release.persistence.PersistenceModifier20;
import org.lorislab.maven.release.persistence.PersistenceModifier21;
import org.lorislab.maven.release.util.FileSystemUtil;
import org.lorislab.maven.release.util.ProcessingCallback;
import org.lorislab.maven.release.util.XMLUtil;

/**
 * The deployment task.
 *
 * @author Andrej Petras
 */
@Mojo(name = "release", inheritByDefault = false, requiresDependencyResolution = ResolutionScope.COMPILE,
        threadSafe = true)
@Execute(goal = "release", phase = LifecyclePhase.PREPARE_PACKAGE)
public class PersistenceReleaseMojo extends AbstractMojo {

    /**
     * The EJB/JAR file pattern.
     */
    private static final SearchPattern JAR_SEARCH_PATTERN = new SearchPattern("^(.*?[.jar])", "jar");
    /**
     * The WAR file pattern.
     */    
    private static final SearchPattern WAR_SEARCH_PATTERN = new SearchPattern("^(.*?[.war])", "war");

    /**
     * The set of archive patterns.
     */
    private static final Set<SearchPattern> PATTERNS = new HashSet<>();
    
    /**
     * The archive files patterns.
     */
    static {
        PATTERNS.add(JAR_SEARCH_PATTERN);
        PATTERNS.add(WAR_SEARCH_PATTERN);
    }

    /**
     * The persistence modifier.
     */
    private static final Map<String, PersistenceModifier> MODIFIER = new HashMap<>();

    /**
     * Persistence version.
     */
    static {
        MODIFIER.put("1.0", new PersistenceModifier10());
        MODIFIER.put("2.0", new PersistenceModifier20());
        MODIFIER.put("2.1", new PersistenceModifier21());
    }
    
    /**
     * The map of persistence file location.
     */
    private static final Map<String, String> PERSISTENCE_XML = new HashMap<>();
    
    /**
     * Static block.
     */
    static {
        PERSISTENCE_XML.put("war", "\\WEB-INF\\classes\\META-INF\\persistence.xml");
        PERSISTENCE_XML.put("jar", "\\META-INF\\persistence.xml");
    }
    
    /**
     * The release archive classifier.
     */
    @Parameter(required = true)
    private String classifier;

    /**
     * The MAVEN ProjectHelper.
     */
    @Component
    private MavenProjectHelper projectHelper;

    /**
     * The MAVEN project.
     */
    @Component
    protected MavenProject project;

    /**
     * The filter property file.
     */
    @Parameter(required = true)
    private String properties;

    /**
     * The create release directory flag.
     */
    @Parameter(required = false, defaultValue = "false")
    private boolean releaseDir;
    
    /**
     * {@inheritDoc }
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        // load the filter file
        Properties prop = FileSystemUtil.loadProperties(properties);
        final Map<String, String> values = new HashMap<>();
        for (String key : prop.stringPropertyNames()) {
            String value = prop.getProperty(key);
            getLog().debug("property: " + key + " new value: " + value);
            values.put(key, value);
        }

        // release file: tarhet/project.ear
        Path releaseFile = project.getArtifact().getFile().toPath();

        // build directory: target
        Path buildDir = Paths.get(project.getBuild().getDirectory());

        // build release dir: target/project
        Path buildReleaseDir = buildDir.resolve(project.getBuild().getFinalName());

        // create the persistence temporary directory
        final Path tmpDir = FileSystemUtil.createDirectory(buildDir, "persistence");

        if ("jar".equals(project.getPackaging()) || "war".equals(project.getPackaging())) {
            
            final Path releasePersistenceFile = buildDir.resolve(buildReleaseDir.getFileName() + "-" + classifier + "." + project.getPackaging());
            FileSystemUtil.copyFile(releaseFile, releasePersistenceFile);
            
            final Set<Path> changeFiles = new HashSet<>();
            SearchResultItem item = new SearchResultItem(releasePersistenceFile, project.getPackaging());            
            updatePersistenceXml(item, changeFiles, tmpDir, values);
            
            if (!changeFiles.isEmpty()) {
                // attache the artifact to the project
                projectHelper.attachArtifact(project, releasePersistenceFile.toFile(), classifier);
            
                if (releaseDir) {
                    Path releasePersistenceDir = buildDir.resolve(buildReleaseDir.getFileName() + "-" + classifier);                
                    FileSystemUtil.unzip(releasePersistenceFile, releasePersistenceDir);                
                }
            } else {
                getLog().info("No files containing the persistence.xml found.");
                FileSystemUtil.delete(releasePersistenceFile);
            }

        } else if ("ear".equals(project.getPackaging())) {

            // unzip the release file to tmp directory: target/project-test
            Path releasePersistenceDir = buildDir.resolve(buildReleaseDir.getFileName() + "-" + classifier);
            FileSystemUtil.unzip(releaseFile, releasePersistenceDir);

            Set<SearchResultItem> files = FileSystemUtil.findFilesInDirectory(releasePersistenceDir, PATTERNS);

            final Set<Path> changeFiles = new HashSet<>();

            if (files != null && !files.isEmpty()) {
                for (final SearchResultItem file : files) {
                    updatePersistenceXml(file, changeFiles, tmpDir, values);
                }
            }

            if (!changeFiles.isEmpty()) {
                // create new archive: target/project-test.ear
                Path releasePersistenceFile = buildDir.resolve(releasePersistenceDir.getFileName() + "." + project.getPackaging());
                FileSystemUtil.zip(releasePersistenceDir, releasePersistenceFile);

                // attache the artifact to the project
                projectHelper.attachArtifact(project, releasePersistenceFile.toFile(), classifier);
                
                if (!releaseDir) {
                    FileSystemUtil.delete(releasePersistenceDir);
                }
            } else {
                getLog().info("No files containing the persistence.xml found.");
            }
        } else {
            getLog().warn("Not supported packing type: " + project.getPackaging());
        }
    }

    /**
     * Updates the persistence XML files.
     *
     * @param zip the archive file.
     * @param changeFiles the set of change files.
     * @param modifier the persistence XML modifier.
     * @param tmpDir the temporary directory.
     * @param values the map of properties values.
     */
    private void updatePersistenceXml(final SearchResultItem file, final Set<Path> changeFiles, final Path tmpDir, final Map<String, String> values) {        
        FileSystemUtil.getFileInZip(file.getPath(),  PERSISTENCE_XML.get(file.getExtension()), new ProcessingCallback() {
            @Override
            public void execute(Path path) throws Exception {
                changeFiles.add(path);

                getLog().info("Start update of the persistence.xml in the file: " + file.getPath().toString());

                // copy from archive
                Path dir = FileSystemUtil.createDirectory(tmpDir, file.getPath().getFileName().toString());
                Path tmpFile = FileSystemUtil.createDirectory(Paths.get(dir.toString() + path.toString()), null);
                Files.copy(path, tmpFile, StandardCopyOption.REPLACE_EXISTING);

                String version = XMLUtil.getXMLVersion(tmpFile);
                getLog().debug("Version of the persistence.xml : " + file.toString() + " version: " + version);
                
                final PersistenceModifier modifier = MODIFIER.get(version);
                if (modifier == null) {
                    throw new MojoExecutionException("Missing the persistence.xml modifier for the version: " + version);
                }                
                
                // change the persistence.xml
                modifier.modifier(tmpFile, values);

                // copy back to archive
                Files.copy(tmpFile, path, StandardCopyOption.REPLACE_EXISTING);
                
                getLog().info("Finished update of the persistence.xml in the file: " + file.getPath().toString());
            }
        });
    }
}
