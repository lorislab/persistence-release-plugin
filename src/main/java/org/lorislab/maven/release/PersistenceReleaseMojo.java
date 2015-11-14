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
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.lorislab.maven.release.util.FileSystemUtil;

/**
 * The deployment task.
 *
 * @author Andrej Petras
 */
@Mojo(name = "release",
        defaultPhase = LifecyclePhase.DEPLOY,
        requiresProject = true,
        threadSafe = true)
@Execute(goal = "release", phase = LifecyclePhase.INSTALL)
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
     * The path of the file to release.
     */
    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}.${project.packaging}", required = true)
    protected File releaseFile;
    
    /**
     * The target directory.
     */
    @Parameter(defaultValue = "${project.build.directory}", required = true)
    protected File targetDir;   
    
    /**
     * The release file.
     */
    @Parameter(defaultValue = "${project.build.finalName}.${project.packaging}", required = true)
    protected String releaseFileName;  
    
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
        File destFile = null;
        
        String unzipDirName = releaseFileName + "-" + classifier;
        
        // unzip the release file to tmp directory
        Path unzipDir = Paths.get(targetDir.toPath().toString() + unzipDirName);
        FileSystemUtil.unzip(releaseFile.toPath(), unzipDir);
        
        // attache the artifact to the project
        projectHelper.attachArtifact( project, destFile, classifier );
    }
    
   

}
