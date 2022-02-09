/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.crypto.checksum;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.util.GradleVersion;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;

@CacheableTask
public class Checksum extends DefaultTask {

    interface FileOps {
        @Inject
        FileSystemOperations getFs();
    }

    private final ObjectFactory objectFactory;
    private final FileOps fileOps;

    private final DirectoryProperty outputDir;
    private final Property<Algorithm> algorithm;
    private final ConfigurableFileCollection files;

    public enum Algorithm {
        MD5(Hashing.md5()),
        SHA256(Hashing.sha256()),
        SHA384(Hashing.sha384()),
        SHA512(Hashing.sha512());

        private final HashFunction hashFunction;

        Algorithm(HashFunction hashFunction) {
            this.hashFunction = hashFunction;
        }
    }

    @Inject
    public Checksum(ObjectFactory objectFactory) {
        this.objectFactory = objectFactory;
        this.outputDir = objectFactory.directoryProperty();
        //TODO use convention when stopping Gradle 5.0 reports
        outputDir.set(getProject().getLayout().getBuildDirectory().dir("checksums"));
        this.algorithm = objectFactory.property(Algorithm.class);
        //TODO use convention when stopping Gradle 5.0 reports
        algorithm.set(Algorithm.SHA256);
        if(isFileCollectionFromObjectFactorySupported()) {
            this.files = objectFactory.fileCollection();
        } else {
            this.files = getProject().files();
        }
        if(isFileSystemOperationsSupported()) {
            this.fileOps = objectFactory.newInstance(FileOps.class);
        } else {
            this.fileOps = null;
        }
    }

    @InputFiles
    @SkipWhenEmpty
    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.RELATIVE)
    public ConfigurableFileCollection getInputFiles() {
        return files;
    }

    /**
     * @return files to compute checksum for
     * @deprecated Use getInputFiles() instead
     */
    @Deprecated
    @Internal
    public FileCollection getFiles() {
        return files;
    }

    /**
     * @param files files to compute checksum for
     * @deprecated Use getInputFiles().setFrom() instead
     */
    @Deprecated
    public void setFiles(FileCollection files) {
        this.files.setFrom(files);
    }

    /**
     * @return algorithm
     * @deprecated Use getChecksumAlgorithm() instead
     */
    @Deprecated
    @Internal
    public Algorithm getAlgorithm() {
        return algorithm.get();
    }

    @Input
    public Property<Algorithm> getChecksumAlgorithm() {
        return algorithm;
    }

    /**
     * @param algorithm algorithm to set
     * @deprecated Use getChecksumAlgorithm().set() instead
     */
    @Deprecated
    public void setAlgorithm(Algorithm algorithm) {
        this.algorithm.set(algorithm);
    }

    /**
     * @return output directory
     * @deprecated Use getOutputDirectory() instead
     */
    @Deprecated
    @Internal
    public File getOutputDir() {
        return getOutputDirAsFile();
    }

    private File getOutputDirAsFile(){
        return outputDir.get().getAsFile();
    }

    @OutputDirectory
    public DirectoryProperty getOutputDirectory() {
        return outputDir;
    }

    /**
     * @param outputDirAsFile output directory to set
     * @deprecated Use getOutputDirectory().set() instead
     */
    @Deprecated
    public void setOutputDir(File outputDirAsFile) {
        this.outputDir.set(outputDirAsFile);
    }

    //TODO Migrate to InputChanges
    @TaskAction
    public void generateChecksumFiles(IncrementalTaskInputs inputs) throws IOException {
        File outputDirAsFile = getOutputDirAsFile();
        if (!outputDirAsFile.exists()) {
            if (!outputDirAsFile.mkdirs()) {
                throw new IOException("Could not create directory:" + outputDirAsFile);
            }
        }
        if (!inputs.isIncremental()) {
            if (isFileSystemOperationsSupported()) {
                fileOps.getFs().delete(spec ->
                    spec.delete(allPossibleChecksumFiles(outputDirAsFile))
                );
            } else {
                getProject().delete(allPossibleChecksumFiles(outputDirAsFile));
            }
        }

        Algorithm algo = algorithm.get();
        inputs.outOfDate(inputFileDetails -> {
            File input = inputFileDetails.getFile();
            if (input.isDirectory()) {
                return;
            }
            File sumFile = outputFileFor(outputDirAsFile, input, algo);
            try {
                HashCode hashCode = Files.asByteSource(input).hash(algo.hashFunction);
                Files.write(hashCode.toString().getBytes(), sumFile);
            } catch (IOException e) {
                throw new GradleException("Trouble creating checksum", e);
            }
        });

        inputs.removed(inputFileDetails -> {
            File input = inputFileDetails.getFile();
            if (input.isDirectory()) {
                return;
            }
            if (isFileSystemOperationsSupported()) {
                fileOps.getFs().delete(spec ->
                    spec.delete(outputFileFor(outputDirAsFile, input, algo))
                );
            } else {
                getProject().delete(outputFileFor(outputDirAsFile, input, algo));
            }
        });
    }

    private File outputFileFor(File outputDirAsFile, File inputFile, Algorithm algo) {
        return new File(outputDirAsFile, inputFile.getName() + "." + algo.toString().toLowerCase());
    }

    private FileCollection allPossibleChecksumFiles(File outputDirAsFile) {
        FileCollection possibleFiles = null;
        for (Algorithm algo : Algorithm.values()) {
            if (possibleFiles == null) {
                possibleFiles = filesFor(outputDirAsFile, algo);
            } else {
                possibleFiles = possibleFiles.plus(filesFor(outputDirAsFile, algo));
            }
        }
        return possibleFiles;
    }

    private FileCollection filesFor(File outputDirAsFile, Algorithm algo) {
        if(isFileTreeFromObjectFactorySupported()){
            return objectFactory.fileTree().from(outputDirAsFile).filter(file ->
                file.getName().endsWith(algo.toString().toLowerCase())
            );
        } else {
            return getProject().fileTree(outputDirAsFile, files -> files.include("**/*." + algo.toString().toLowerCase()));
        }
    }

    private boolean isFileSystemOperationsSupported(){
        return isGradle6OrAbove();
    }

    private boolean isFileTreeFromObjectFactorySupported(){
        return isGradle6OrAbove();
    }

    private boolean isFileCollectionFromObjectFactorySupported(){
        return GradleVersion.current().compareTo(GradleVersion.version("5.3")) >= 0;
    }

    //TODO remove version specific logic with plugin version 2.X
    private boolean isGradle6OrAbove(){
        return GradleVersion.current().compareTo(GradleVersion.version("6.0")) >= 0;
    }

}
