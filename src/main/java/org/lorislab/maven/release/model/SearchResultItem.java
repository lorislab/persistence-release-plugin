/*
 * Copyright 2015 Andrej_Petras.
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
package org.lorislab.maven.release.model;

import java.nio.file.Path;

/**
 * The search result item.
 *
 * @author Andrej_Petras
 */
public class SearchResultItem {

    /**
     * The file path.
     */
    private final Path path;

    /**
     * The file extension.
     */
    private final String extension;

    /**
     * The default constructor.
     *
     * @param path the file path.
     * @param extension the file extension.
     */
    public SearchResultItem(Path path, String extension) {
        this.path = path;
        this.extension = extension;
    }

    /**
     * Gets the file extension.
     *
     * @return the file extension.
     */
    public String getExtension() {
        return extension;
    }

    /**
     * Gets the file path.
     *
     * @return the file path.
     */
    public Path getPath() {
        return path;
    }

}
