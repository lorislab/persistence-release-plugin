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

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
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
import org.lorislab.maven.release.util.FileSystemUtil;

/**
 * The deployment task.
 *
 * @author Andrej Petras
 */
@Mojo(name = "release", inheritByDefault = false, requiresDependencyResolution = ResolutionScope.COMPILE,
        threadSafe = true)
@Execute(goal = "release", phase = LifecyclePhase.PACKAGE)
public class PersistenceReleaseMojo extends AbstractMojo {

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

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        
        // release file: tarhet/project.ear
        Path releaseFile = project.getArtifact().getFile().toPath();
        
        // build directory: target
        Path buildDir = Paths.get(project.getBuild().getDirectory());
        
        // build release dir: target/project
        Path buildReleaseDir = buildDir.resolve(project.getBuild().getFinalName());

        // unzip the release file to tmp directory: target/project-test
        Path releasePersistenceDir = buildDir.resolve(buildReleaseDir.getFileName() + "-" + classifier);
        FileSystemUtil.unzip(releaseFile, releasePersistenceDir);
        
        
        // create new archive: target/project-test.ear
        Path releasePersistenceFile = buildDir.resolve(releasePersistenceDir.getFileName() + "." + project.getPackaging());
        FileSystemUtil.zip(releasePersistenceDir, releasePersistenceFile);

        // attache the artifact to the project
        projectHelper.attachArtifact(project, releasePersistenceFile.toFile(), classifier);
    }

}
