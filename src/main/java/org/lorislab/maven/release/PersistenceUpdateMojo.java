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
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.lorislab.maven.release.model.SearchResultItem;
import org.lorislab.maven.release.util.FileSystemUtil;

/**
 * The update task.
 *
 * @author Andrej Petras
 */
@Mojo(name = "update", inheritByDefault = false, requiresDependencyResolution = ResolutionScope.COMPILE,
        threadSafe = true)
@Execute(goal = "update", phase = LifecyclePhase.PREPARE_PACKAGE)
public class PersistenceUpdateMojo extends AbstractPersistenceMojo {

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
     * The update artifact <groupId>:<artifactId>
     */
    @Parameter(required = true)
    private String updateArtifact;

    /**
     * Delete the backup files.
     */
    @Parameter(required = false, defaultValue = "true")
    private boolean deleteBackup;

    /**
     * The output file name.
     */
    @Parameter
    private String filename;
    
    /**
     * {@inheritDoc }
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        // load the filter file
        final Map<String, String> values = loadProperties(properties);

        Artifact artifact = null;
        String[] ii = updateArtifact.split(":");
        String groupId = ii[0];
        String artifactId = ii[1];

        Set<Artifact> arts = (Set<Artifact>) project.getDependencyArtifacts();
        if (arts != null) {
            Iterator<Artifact> iter = arts.iterator();
            while (artifact == null && iter.hasNext()) {
                Artifact i = iter.next();
                if (i.getGroupId().equals(groupId) && i.getArtifactId().equals(artifactId)) {
                    artifact = i;
                }
            }
        }

        // build directory: target
        Path buildDir = Paths.get(project.getBuild().getDirectory());
        buildDir = FileSystemUtil.createDirectory(buildDir, "persistence-update");

        if (filename == null || filename.isEmpty()) {
            filename = artifact.getArtifactId() + "-" + artifact.getVersion() + "." + artifact.getType();
        }
        
        Path ap = artifact.getFile().toPath();
        Path releaseFile = buildDir.resolve(filename);

        FileSystemUtil.copyFile(ap, releaseFile);

        // create the persistence temporary directory
        final Path tmpDir = FileSystemUtil.createDirectory(buildDir, "persistence-tmp");

        if ("jar".equals(artifact.getType()) || "war".equals(artifact.getType())) {

            final Path releasePersistenceFile = buildDir.resolve(ap.getFileName() + "-update");
            FileSystemUtil.copyFile(releaseFile, releasePersistenceFile);

            final Set<Path> changeFiles = new HashSet<>();
            SearchResultItem item = new SearchResultItem(releasePersistenceFile, project.getPackaging());
            updatePersistenceXml(item, changeFiles, tmpDir, values);

            if (!changeFiles.isEmpty()) {
                if (deleteBackup) {
                    FileSystemUtil.delete(releaseFile);                    
                } else {
                    final Path backupFile = buildDir.resolve(ap.getFileName() + "-backup");
                    FileSystemUtil.moveFile(releaseFile, backupFile);                    
                }
                FileSystemUtil.moveFile(releasePersistenceFile, releaseFile);
                
                if (releaseDir) {
                    Path releasePersistenceDir = buildDir.resolve(ap.getFileName() + "-update");
                    FileSystemUtil.unzip(releasePersistenceFile, releasePersistenceDir);
                }

            } else {
                getLog().info("No files containing the persistence.xml found.");
                FileSystemUtil.delete(releasePersistenceFile);
                FileSystemUtil.delete(tmpDir);
            }

        } else if ("ear".equals(artifact.getType())) {

            // unzip the release file to tmp directory: target/project-test
            Path releasePersistenceDir = buildDir.resolve(ap.getFileName() + "-update");
            FileSystemUtil.unzip(releaseFile, releasePersistenceDir);

            Set<SearchResultItem> files = FileSystemUtil.findFilesInDirectory(releasePersistenceDir, PATTERNS);

            final Set<Path> changeFiles = new HashSet<>();

            if (files != null && !files.isEmpty()) {
                for (final SearchResultItem file : files) {
                    updatePersistenceXml(file, changeFiles, tmpDir, values);
                }
            }

            if (!changeFiles.isEmpty()) {

                if (deleteBackup) {
                    FileSystemUtil.delete(releaseFile);
                } else {
                    final Path backupFile = buildDir.resolve(ap.getFileName() + "-backup");
                    FileSystemUtil.moveFile(releaseFile, backupFile);
                }

                // create new archive: target/project-test.ear
                FileSystemUtil.zip(releasePersistenceDir, releaseFile);

                if (!releaseDir) {
                    FileSystemUtil.delete(releasePersistenceDir);
                }
            } else {
                getLog().info("No files containing the persistence.xml found.");
            }
        } else {
            getLog().warn("Not supported packing type: " + artifact.getType());
        }

        FileSystemUtil.delete(tmpDir);
    }

}
