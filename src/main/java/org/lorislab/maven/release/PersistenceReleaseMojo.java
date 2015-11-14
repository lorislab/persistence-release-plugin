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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
import org.lorislab.maven.release.persistence.PersistenceModifier;
import org.lorislab.maven.release.persistence.PersistenceModifier10;
import org.lorislab.maven.release.persistence.PersistenceModifier20;
import org.lorislab.maven.release.persistence.PersistenceModifier21;
import org.lorislab.maven.release.util.FileSystemUtil;
import org.lorislab.maven.release.util.ProcessingCallback;

/**
 * The deployment task.
 *
 * @author Andrej Petras
 */
@Mojo(name = "release", inheritByDefault = false, requiresDependencyResolution = ResolutionScope.COMPILE,
        threadSafe = true)
@Execute(goal = "release", phase = LifecyclePhase.PACKAGE)
public class PersistenceReleaseMojo extends AbstractMojo {
    
    private static final Pattern FILE_PATTERN = Pattern.compile("^(.*?[.jar|.war])");
    
    private static final Pattern PERSISTENCE_XML_PATTERN = Pattern.compile("^(.*?persistence.xml)");
    
    private static final Map<String, PersistenceModifier> MODIFIER = new HashMap<>();
    
    static {
        MODIFIER.put("1.0", new PersistenceModifier10());
        MODIFIER.put("2.0", new PersistenceModifier20());
        MODIFIER.put("2.1", new PersistenceModifier21());
    }
    /**
     * The release archive classifier.
     */
    @Parameter(required = true)
    private String classifier;

    /**
     * The persistence XML version.
     */
    @Parameter(required = true, defaultValue = "2.0")
    private String version;

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
    private String filterFile;
    
    /**
     * {@inheritDoc }
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        
        Path propertyFile = Paths.get(filterFile);
        if (!Files.exists(propertyFile)) {
            throw new MojoExecutionException("Missing properties filter file");
        }
        
        Properties
        final Map<String, String> values = new HashMap<>();
        values.put("hibernate.dialect", "XXXXXXXXXXX");

        // release file: tarhet/project.ear
        Path releaseFile = project.getArtifact().getFile().toPath();

        // build directory: target
        Path buildDir = Paths.get(project.getBuild().getDirectory());

        // build release dir: target/project
        Path buildReleaseDir = buildDir.resolve(project.getBuild().getFinalName());

        // unzip the release file to tmp directory: target/project-test
        Path releasePersistenceDir = buildDir.resolve(buildReleaseDir.getFileName() + "-" + classifier);
        FileSystemUtil.unzip(releaseFile, releasePersistenceDir);
        
        Set<Path> files = FileSystemUtil.findFilesInDirectory(releasePersistenceDir, FILE_PATTERN);
        
        final Set<Path> changeFiles = new HashSet<>();
        
        if (files != null && !files.isEmpty()) {
            
            final PersistenceModifier modifier = MODIFIER.get(version);
            if (modifier == null) {
                throw new MojoExecutionException("Missing the persistence.xml modifier for the version: " + version);
            }
            
            // create the persistence temporary directory
            final Path tmpDir = FileSystemUtil.createDirectory(buildDir, "persistence");
            
            
            for (final Path file : files) {

                FileSystemUtil.processFileInsideZip(file, PERSISTENCE_XML_PATTERN, new ProcessingCallback() {
                    @Override
                    public void execute(Path path) throws Exception {
                        changeFiles.add(path);

                        getLog().info("Update the persistence.xml in the file: " + file.toString());
                        
                        // copy from archive
                        Path dir = FileSystemUtil.createDirectory(tmpDir, file.getFileName().toString());
                        System.out.println(dir.toString());
                        Path tmpFile = FileSystemUtil.createDirectory(Paths.get(dir.toString() + path.toString()), null);                       
                        System.out.println(tmpFile.toString());
                        Files.copy(path, tmpFile, StandardCopyOption.REPLACE_EXISTING);

                        // change the persistence.xml
                        modifier.modifier(tmpFile, values);

                        // copy back to archive
                        Files.copy(tmpFile, path, StandardCopyOption.REPLACE_EXISTING);
                    }
                });
            }            
        }
        
        if (!changeFiles.isEmpty()) {
            // create new archive: target/project-test.ear
            Path releasePersistenceFile = buildDir.resolve(releasePersistenceDir.getFileName() + "." + project.getPackaging());
            FileSystemUtil.zip(releasePersistenceDir, releasePersistenceFile);

            // attache the artifact to the project
            projectHelper.attachArtifact(project, releasePersistenceFile.toFile(), classifier);
        } else {
            getLog().info("No files for with persistence.xml found.");
        }
    }
    
}
