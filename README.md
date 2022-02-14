# gradle-checksum
A Gradle plugin for creating checksums for files in your build.

# Requirements
- Version 1.4.0 requires Gradle 5.4 or above
- Version 1.3.0 requires Gradle 5.0 or above

# Usage

```$gradle
plugins {
    id 'org.gradle.crypto.checksum' version '1.4.0'
}

import org.gradle.crypto.checksum.Checksum

task generateFiles {
  // Generates some files.
}

task createChecksums(type: Checksum, dependsOn: 'generateFiles') {
  inputFiles.setFrom(generateFiles.outputs.files)
  outputDirectory.set(layout.buildDirectory.dir("foo/checksums"))
  checksumAlgorithm.set(Checksum.Algorithm.SHA512)
  appendFileNameToChecksum.set(true)
}
```

When the `createChecksums` task is finished, there will be a file ending in
`.sha512` for each of the files output by the `generateFiles` task.

Currently, only `SHA256` (default), `SHA384`, `SHA512`, and `MD5` are
supported. Please file an issue or make a pull request if you need support
for some other hashing algorithm.

If you want to append the filename to the checksum value, set `appendFileNameToChecksum` to true.
This makes it easier to automate the checksum verification with the `md5sum`, `sha256sum`, `sha384sum` or `sha512sum` tools.
set the `appendFileNameToChecksum` flag to `true` (`false` by default):

```
> sha512sum -c ../build/checksums/foo.txt.sha512
foo.txt: OK
```

By default, the `outputDirectory` will be set to `project.buildDir + "checksums"`.

The task is incremental at the file level, and will only alter files in the
output directory which end with file extensions managed by this task
(e.g. `.sha256` or `.sha512`). However, for optimal caching of other tasks
it is still advisable to try and use independent output directories for
each task in your build.
