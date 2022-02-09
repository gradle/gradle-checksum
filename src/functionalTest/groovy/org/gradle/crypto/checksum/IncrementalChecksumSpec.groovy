package org.gradle.crypto.checksum

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class IncrementalChecksumSpec extends Specification {

    @Rule
    TemporaryFolder projectDir = new TemporaryFolder()

    def setup() {
        newFile('settings.gradle')
    }

    def 'can handle changing file trees on #gradleVersion with configuration cache = #isConfigurationCacheEnabled'() {
        given:
        newFile('build.gradle') << """
            plugins {
                id 'org.gradle.crypto.checksum' version '1.1.0'
            }

            import org.gradle.crypto.checksum.Checksum

            task checksums(type: Checksum) {
                files = fileTree("input") {
                    include "**/*.txt"
                }
                outputDir = file("checksums")
            }
        """
        newFolder('input')
        newFile('input/foo.txt') << 'foo'
        newFile('input/bar.txt') << 'bar'

        when:
        def firstResult = build(gradleVersion, isConfigurationCacheEnabled)

        then:
        firstResult.task(':checksums').getOutcome() == TaskOutcome.SUCCESS

        when:
        newFolder('input', 'subdir')
        newFile('input/subdir/sub-foo.txt') << 'sub-foo'
        newFile('input/baz.txt') << 'baz'
        def secondResult = build(gradleVersion, isConfigurationCacheEnabled)

        then:
        secondResult.task(':checksums').getOutcome() == TaskOutcome.SUCCESS
        file('checksums/sub-foo.txt.sha256').exists()
        file('checksums/baz.txt.sha256').exists()
        if(isConfigurationCacheEnabled) {
            secondResult.output.contains("Reusing configuration cache.")
        }

        where:
        gradleVersion | isConfigurationCacheEnabled
        '4.0'         | false
        '5.0'         | false
        '6.6'         | false
        '6.6'         | true
        '7.4'         | false
        '7.4'         | true
    }

    private BuildResult build(String gradleVersion, boolean isConfigurationCacheEnabled) {
        List<String> args = new ArrayList<>()
        if(isConfigurationCacheEnabled) {
            args.add('--configuration-cache')
        }
        args.add('--stacktrace')
        args.add('checksum')

        GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(projectDir.root)
                .withArguments(args)
                .withPluginClasspath()
                .build()
    }

    private File newFile(String fileName) {
        projectDir.newFile(fileName)
    }

    private File newFolder(String... folder) {
        projectDir.newFolder(folder)
    }

    private File file(String fileName) {
        new File(projectDir.root, fileName)
    }
}
