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
package org.lorislab.maven.release.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.spi.FileSystemProvider;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The file system utility.
 *
 * @author Andrej_Petras
 */
public final class FileSystemUtil {

    /**
     * The ZIP provider.
     */
    private static final FileSystemProvider ZIP_PROVIDER;

    /**
     * The ZIP environment.
     */
    private static final Map<String, String> ZIP_ENV = new HashMap<>();

    /**
     * Static block.
     */
    static {

        ZIP_ENV.put("create", "true");

        FileSystemProvider tmp = null;
        for (FileSystemProvider provider : FileSystemProvider.installedProviders()) {
            if ("jar".equalsIgnoreCase(provider.getScheme())) {
                tmp = provider;
            }
        }
        ZIP_PROVIDER = tmp;
    }

    /**
     * The default constructor.
     */
    private FileSystemUtil() {
        // empty constructor
    }

    /**
     * Copies the file from source to target.
     *
     * @param source the source file.
     * @param target the target file.
     */
    public static void copyFile(Path source, Path target) {
        try {
            Files.copy(source, target);
        } catch (Exception ex) {
            throw new RuntimeException("Can not copy the source file " + source.toString() + " to target " + target.toString(), ex);
        }
    }

    /**
     * Moves the file from source to target.
     *
     * @param source the source file.
     * @param target the target file.
     */
    public static void moveFile(Path source, Path target) {
        try {
            Files.move(source, target);
        } catch (Exception ex) {
            throw new RuntimeException("Can not move the source file " + source.toString() + " to target " + target.toString(), ex);
        }
    }

    /**
     * Loads properties from the file.
     *
     * @param file the property file.
     * @return the corresponding properties.
     */
    public static Properties loadProperties(String file) {
        Properties result = new Properties();

        if (file == null || file.isEmpty()) {
            throw new RuntimeException("The property file is null!");
        }

        Path propertyFile = Paths.get(file);
        if (!Files.exists(propertyFile)) {
            throw new RuntimeException("Missing properties filter file");
        }

        try (InputStream input = new FileInputStream(propertyFile.toFile())) {
            result.load(input);
        } catch (Exception ex) {
            throw new RuntimeException("Error loading the property from file: " + file, ex);
        }

        return result;
    }

    /**
     * Creates the directory with {@code name} in the parent directory
     * {@code parent}
     *
     * @param parent the parent directory.
     * @param name the new directory name.
     * @return the corresponding new directory.
     */
    public static Path createDirectory(Path parent, String name) {
        Path result = null;
        try {
            if (name != null) {
                result = parent.resolve(name);
            } else {
                result = parent;
            }

            if (!Files.exists(result)) {
                Files.createDirectories(result);
            }
        } catch (Exception ex) {
            throw new RuntimeException("Error creating the directory " + name + " in the directory " + parent.toString(), ex);
        }
        return result;
    }

    /**
     * Processing the ZIP file.
     *
     * @param zipFile the ZIP file.
     * @param pattern the resource pattern.
     * @param callback the call-back method.
     */
    public static void processFileInsideZip(final Path zipFile, final Pattern pattern, final ProcessingCallback callback) {

        try (FileSystem zipfs = ZIP_PROVIDER.newFileSystem(zipFile, new HashMap<String, Object>())) {
            Files.walkFileTree(zipfs.getPath("/"), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    boolean matches = pattern.matcher(file.toString()).matches();
                    if (matches) {
                        try {
                            callback.execute(file);
                        } catch (Exception ex) {
                            throw new RuntimeException("Error execute the file: " + file.toString(), ex);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception ex) {
            throw new RuntimeException("Error searching files inside the ZIP archive " + zipFile.toString(), ex);
        }
    }

    /**
     * Finds all files in the root directory corresponding the pattern.
     *
     * @param dir the root directory.
     * @param pattern the file pattern.
     * @return the set of corresponding files.
     */
    public static Set<Path> findFilesInDirectory(final Path dir, final Pattern pattern) {

        final Set<Path> result = new HashSet<>();
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult preVisitDirectory(Path path,
                        BasicFileAttributes atts) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path path, BasicFileAttributes mainAtts)
                        throws IOException {

                    boolean matches = pattern.matcher(path.toString()).matches();
                    if (matches) {
                        result.add(path);
                    }
                    return FileVisitResult.CONTINUE;
                }

            });
        } catch (Exception ex) {
            throw new RuntimeException("Error searching files in the directory " + dir.toString(), ex);
        }
        return result;
    }

    /**
     * Unzip the source file to the target directory.
     *
     * @param sourceFile the source archive file.
     * @param targetDir the target directory.
     */
    public static void unzip(final Path sourceFile, final Path targetDir) {

        if (sourceFile == null || targetDir == null) {
            throw new RuntimeException("The source file or target directory can not be null!");
        }

        if (!Files.isRegularFile(sourceFile)) {
            throw new RuntimeException("The source file is not regular file!");
        }

        try {
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }
        } catch (Exception ex) {
            throw new RuntimeException("Error create the target directory: " + targetDir.toString(), ex);
        }

        try (FileSystem zipfs2 = FileSystems.newFileSystem(sourceFile, null)) {

            Files.walkFileTree(zipfs2.getPath("/"), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path toFile = Paths.get(targetDir.toString(), file.toString());
                    Files.createDirectories(toFile);
                    Files.copy(file, toFile, StandardCopyOption.REPLACE_EXISTING);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception ex) {
            throw new RuntimeException("Error creating the target directory " + targetDir.toString() + "from archive file " + sourceFile.toString(), ex);
        }
    }

    /**
     * Creates the zip archive from the source directory.
     *
     * @param sourceDir the source directory.
     * @param targetFile the target archive.
     */
    public static void zip(final Path sourceDir, final Path targetFile) {

        if (sourceDir == null || targetFile == null) {
            throw new RuntimeException("The source directory or target file can not be null!");
        }

        if (!Files.isDirectory(sourceDir)) {
            throw new RuntimeException("The source directory is not directory!");
        }

        if (Files.exists(targetFile)) {
            throw new RuntimeException("The target file already exists!");
        }

        try (FileSystem zipfs3 = ZIP_PROVIDER.newFileSystem(targetFile, ZIP_ENV)) {

            Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes atts) throws IOException {
                    if (!sourceDir.equals(path)) {
                        Path pathInZipfile = zipfs3.getPath(path.toString().replace(sourceDir.toString(), ""));
                        Files.createDirectories(pathInZipfile);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path path, BasicFileAttributes mainAtts) throws IOException {
                    Path pathInZipfile = zipfs3.getPath(path.toString().replace(sourceDir.toString(), ""));
                    Files.copy(path, pathInZipfile);
                    return FileVisitResult.CONTINUE;
                }

            });

        } catch (Exception ex) {
            throw new RuntimeException("Error creating the zip file " + targetFile.toString() + " from the directory " + sourceDir.toString(), ex);
        }
    }

    /**
     * Deletes the path from the file system.
     *
     * @param path the path.
     */
    public static void delete(Path path) {
        if (path != null) {
            try {
                if (Files.exists(path)) {

                    if (Files.isDirectory(path)) {

                        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                            @Override
                            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                                Files.delete(file);
                                return FileVisitResult.CONTINUE;
                            }

                            @Override
                            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                                Files.delete(dir);
                                return FileVisitResult.CONTINUE;
                            }

                        });
                    } else {
                        Files.delete(path);
                    }
                }
            } catch (Exception ex) {
                throw new RuntimeException("Error deleting the path : " + path.toString(), ex);
            }
        }
    }
}
