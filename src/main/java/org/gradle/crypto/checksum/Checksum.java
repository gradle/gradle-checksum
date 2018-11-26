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
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.api.tasks.incremental.InputFileDetails;

import java.io.File;
import java.io.IOException;

public class Checksum extends DefaultTask {
    private FileCollection files;
    private File outputDir;
    private Algorithm algorithm;

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

    public Checksum() {
        outputDir = new File(getProject().getBuildDir(), "checksums");
        algorithm = Algorithm.SHA256;
    }

    @InputFiles
    public FileCollection getFiles() {
        return files;
    }

    public void setFiles(FileCollection files) {
        this.files = files;
    }

    @Input
    public Algorithm getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(Algorithm algorithm) {
        this.algorithm = algorithm;
    }

    @OutputDirectory
    public File getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(File outputDir) {
        if (outputDir.exists() && !outputDir.isDirectory()) {
            throw new IllegalArgumentException("Output directory must be a directory.");
        }
        this.outputDir = outputDir;
    }

    @TaskAction
    public void generateChecksumFiles(IncrementalTaskInputs inputs) throws IOException {
        if (!getOutputDir().exists()) {
            if (!getOutputDir().mkdirs()) {
                throw new IOException("Could not create directory:" + getOutputDir());
            }
        }
        if (!inputs.isIncremental()) {
            getProject().delete(allPossibleChecksumFiles());
        }

        inputs.outOfDate(new Action<InputFileDetails>() {
            @Override
            public void execute(InputFileDetails inputFileDetails) {
                File input = inputFileDetails.getFile();
                if (input.isDirectory()) {
                    return;
                }
                File sumFile = outputFileFor(input);
                HashCode hashCode = null;
                try {
                    hashCode = Files.asByteSource(input).hash(algorithm.hashFunction);
                    Files.write(hashCode.toString().getBytes(), sumFile);
                } catch (IOException e) {
                    throw new GradleException("Trouble creating checksum", e);
                }
            }
        });

        inputs.removed(new Action<InputFileDetails>() {
            @Override
            public void execute(InputFileDetails inputFileDetails) {
                File input = inputFileDetails.getFile();
                if (input.isDirectory()) {
                    return;
                }
                getProject().delete(outputFileFor(input));
            }
        });
    }

    private File outputFileFor(File inputFile) {
        return new File(getOutputDir(), inputFile.getName() + "." + algorithm.toString().toLowerCase());
    }

    private FileCollection allPossibleChecksumFiles() {
        FileCollection possibleFiles = null;
        for (Algorithm algo : Algorithm.values()) {
            if (possibleFiles == null) {
                possibleFiles = filesFor(algo);
            } else {
                possibleFiles = possibleFiles.plus(filesFor(algo));
            }
        }
        return possibleFiles;
    }

    private FileCollection filesFor(final Algorithm algo) {
        return getProject().fileTree(getOutputDir(), new Action<ConfigurableFileTree>() {
            @Override
            public void execute(ConfigurableFileTree files) {
                files.include("**/*." + algo.toString().toLowerCase());
            }
        });
    }
}
