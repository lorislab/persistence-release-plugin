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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
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
import org.lorislab.maven.release.model.SearchResultItem;
import org.lorislab.maven.release.util.FileSystemUtil;

/**
 * The deployment task.
 *
 * @author Andrej Petras
 */
@Mojo(name = "release", inheritByDefault = false, requiresDependencyResolution = ResolutionScope.COMPILE,
        threadSafe = true)
@Execute(goal = "release", phase = LifecyclePhase.PREPARE_PACKAGE)
public class PersistenceReleaseMojo extends AbstractPersistenceMojo {

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
        final Map<String, String> values = loadProperties(properties);

        // release file: tarhet/project.ear
        Path releaseFile = project.getArtifact().getFile().toPath();

        // build directory: target
        Path buildDir = Paths.get(project.getBuild().getDirectory());

        // build release dir: target/project
        Path buildReleaseDir = buildDir.resolve(project.getBuild().getFinalName());

        // create the persistence temporary directory
        final Path tmpDir = FileSystemUtil.createDirectory(buildDir, "persistence-release");

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

}
