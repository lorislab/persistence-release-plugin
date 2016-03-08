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
import java.util.Properties;
import java.util.Set;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
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
 * The update task.
 *
 * @author Andrej Petras
 */
public abstract class AbstractPersistenceMojo extends AbstractMojo {

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
    protected static final Set<SearchPattern> PATTERNS = new HashSet<>();
    
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

    protected Map<String, String> loadProperties(String properties) {
        Properties prop = FileSystemUtil.loadProperties(properties);
        final Map<String, String> values = new HashMap<>();
        for (String key : prop.stringPropertyNames()) {
            String value = prop.getProperty(key);
            getLog().debug("property: " + key + " new value: " + value);
            values.put(key, value);
        }
        return values;        
    }
   
    /**
     * Updates the persistence XML files.
     *
     * @param changeFiles the set of change files.
     * @param tmpDir the temporary directory.
     * @param values the map of properties values.
     */
    protected void updatePersistenceXml(final SearchResultItem file, final Set<Path> changeFiles, final Path tmpDir, final Map<String, String> values) {        
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
