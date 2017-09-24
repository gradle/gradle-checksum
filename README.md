# gradle-checksum
A Gradle plugin for creating checksums for files in your build.

# Usage

```$gradle
plugins {
    id 'org.gradle.crypto.checksum' version '1.1.0'
}

import org.gradle.crypto.checksum.Checksum

task generateFiles {
  // Generates some files.
}

task createChecksums(type: Checksum, dependsOn: 'generateFiles') {
  files = generateFiles.outputs.files
  outputDir = new File(project.buildDir, "generatedChecksums")
  algorithm = Checksum.Algorithm.SHA512
}
```

When the `createChecksums` task is finished, there will be a file ending in
`.sha512` for each of the files output by the `generateFiles` task.

Currently, only `SHA256` (default), `SHA384`, `SHA512`, and `MD5` are
supported. Please file an issue or make a pull request if you need support
for some other hashing algorithm.

By default, the `outputDir` will be set to `project.buildDir + "checksums"`.

The task is incremental at the file level, and will only alter files in the
output directory which end with file extensions managed by this task
(e.g. `.sha256` or `.sha512`). However, for optimal caching of other tasks
it is still advisable to try and use independent output directories for
each task in your build.