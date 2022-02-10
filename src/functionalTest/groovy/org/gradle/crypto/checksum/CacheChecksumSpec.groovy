package org.gradle.crypto.checksum

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.TempDir

class CacheChecksumSpec extends Specification {

    @TempDir File testProjectDir
    File localBuildCacheDirectory
    File buildDirRun1
    File buildDirRun2

    def setup() {
        localBuildCacheDirectory = new File(testProjectDir, 'local-cache')
        buildDirRun1 = new File(testProjectDir, 'run1')
        setupBuildDir(buildDirRun1)
        buildDirRun2 = new File(testProjectDir, 'run2')
        setupBuildDir(buildDirRun2)

        new File(testProjectDir,'input').mkdir()
        new File(testProjectDir,'input/foo.txt') << 'foo'
        new File(testProjectDir,'input/bar.txt') << 'bar'
        new File(testProjectDir,'input/subdir').mkdirs()
        new File(testProjectDir,'input/subdir/sub-foo.txt') << 'sub-foo'
        new File(testProjectDir,'input/baz.txt') << 'baz'
    }

    def 'can use build cache on #gradleVersion'() {
        given:
        localBuildCacheDirectory.deleteDir()
        localBuildCacheDirectory.mkdir()

        when:
        def firstResult = build(buildDirRun1, gradleVersion, false)

        then:
        firstResult.task(':checksums').getOutcome() == TaskOutcome.SUCCESS
        new File(buildDirRun1,'checksums/sub-foo.txt.sha256').exists()
        new File(buildDirRun1,'checksums/baz.txt.sha256').exists()

        when:
        def secondResult = build(buildDirRun2, gradleVersion, false)

        then:
        secondResult.task(':checksums').getOutcome() == TaskOutcome.FROM_CACHE
        new File(buildDirRun2,'checksums/sub-foo.txt.sha256').exists()
        new File(buildDirRun2,'checksums/baz.txt.sha256').exists()

        where:
        gradleVersion << ['5.0', '6.6', '7.4']
    }

    def 'can use configuration cache on #gradleVersion'() {
        given:
        localBuildCacheDirectory.deleteDir()
        localBuildCacheDirectory.mkdir()

        when:
        def firstResult = build(buildDirRun1, gradleVersion, true)

        then:
        firstResult.task(':checksums').getOutcome() == TaskOutcome.SUCCESS
        new File(buildDirRun1,'checksums/sub-foo.txt.sha256').exists()
        new File(buildDirRun1,'checksums/baz.txt.sha256').exists()
        !firstResult.output.contains('Reusing configuration cache.')

        when:
        def secondResult = build(buildDirRun1, gradleVersion, true)

        then:
        secondResult.task(':checksums').getOutcome() == TaskOutcome.UP_TO_DATE
        new File(buildDirRun1,'checksums/sub-foo.txt.sha256').exists()
        new File(buildDirRun1,'checksums/baz.txt.sha256').exists()
        secondResult.output.contains('Reusing configuration cache.')

        where:
        gradleVersion << ['6.6', '7.4']
    }

    private void setupBuildDir(File buildDir){
        buildDir.mkdir()

        new File(buildDir,'settings.gradle') << """
            buildCache {
                local {
                    directory '${localBuildCacheDirectory.toURI()}'
                }
            }
        """

        new File(buildDir,'build.gradle') << """
            plugins {
                id 'org.gradle.crypto.checksum'
            }

            import org.gradle.crypto.checksum.Checksum

            task checksums(type: Checksum) {
                files = fileTree("../input") {
                    include "**/*.txt"
                }
                outputDir = file("checksums")
            }
        """
    }

    private BuildResult build(File buildDir, String gradleVersion, boolean isConfigurationCacheEnabled) {
        List<String> args = new ArrayList<>()
        if(isConfigurationCacheEnabled) {
            args.add('--configuration-cache')
        }
        args.add('--build-cache')
        args.add('checksum')

        GradleRunner.create()
            .withGradleVersion(gradleVersion)
            .withProjectDir(buildDir)
            .withArguments(args)
            .withPluginClasspath()
            .build()
    }

}
