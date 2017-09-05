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
package org.gradle.crypto.checksum

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.lang.Unroll

class ChecksumSpec extends Specification {
    @Rule
    TemporaryFolder projectDir = new TemporaryFolder()
    File buildFile

    def setup() {
        buildFile = projectDir.newFile('build.gradle')
        buildFile << """
        plugins {
          id 'org.gradle.crypto.checksum' version '0.1.0'
        }
        
        import org.gradle.crypto.checksum.Checksum
        
        task makeAFile {
          doLast {
            file("build").mkdir()
            file("build/aFile.txt") << "Hello, Checksum!" + System.lineSeparator()
          }
        }
        
        task sumAFile(type: Checksum, dependsOn: 'makeAFile') {
          files = files("build/aFile.txt")
        }
        """
    }

    def 'has expected defaults'() {
        given:
        def expectedSumFile = new File(projectDir.getRoot(), 'build/checksums/aFile.txt.sha256')

        when:
        def result = GradleRunner.create()
                .withProjectDir(projectDir.root)
                .withArguments('sumAFile')
                .withPluginClasspath()
                .build()

        then:
        result.task(':sumAFile').getOutcome() == TaskOutcome.SUCCESS
        expectedSumFile.exists()
    }

    def 'can change outputDir'() {
        given:
        buildFile << """
        sumAFile {
          outputDir = new File(project.getBuildDir(), 'mySpecialChecksums')
        }
        """
        def expectedSumFile = new File(projectDir.getRoot(), 'build/mySpecialChecksums/aFile.txt.sha256')

        when:
        def result = GradleRunner.create()
                .withProjectDir(projectDir.root)
                .withArguments('sumAFile')
                .withPluginClasspath()
                .build()

        then:
        result.task(':sumAFile').getOutcome() == TaskOutcome.SUCCESS
        expectedSumFile.exists()
    }

    @Unroll
    def 'can use #algo'() {
        given:
        buildFile << """
        sumAFile {
          algorithm = Checksum.Algorithm.$algo
        }
        """
        def expectedSumFile = new File(projectDir.getRoot(), 'build/checksums/aFile.txt.' + algo.toLowerCase())

        when:
        def result = GradleRunner.create()
                .withProjectDir(projectDir.root)
                .withArguments('sumAFile')
                .withPluginClasspath()
                .build()

        then:
        result.task(':sumAFile').getOutcome() == TaskOutcome.SUCCESS
        expectedSumFile.exists()
        expectedSumFile.getText('UTF8') == sum

        where:
        algo     | sum
        'SHA256' | '0397ecd4cc6615e6b1d7ecd3b0e5c3d882cd7b662dc0d9f0d74264088e4794e1'
        'SHA384' | 'f5bf11a6a00b8bc8a09fcc61572e489ec9796ead02b1b11b47431496cb8090190533ba0781d86a0ff1120eafd57fc6da'
        'SHA512' | '856fbe321f76529c9319a659cb32fb0e5cfb7888b60f42abcdbe0363eb1cd8dfb6e09b09d2587b7f0d973ff2a8c9b7d6aaece5336eda905d27dede45b75924a3'
    }

    def 'supports overlapping output directories'() {
        given:
        projectDir.newFolder("build", "checksums")
        def otherFile = getProjectDir().newFile("build/checksums/notMyFile")
        def expectedSumFile = new File(projectDir.root, 'build/checksums/aFile.txt.sha256')

        when:
        def result = GradleRunner.create()
                .withProjectDir(projectDir.root)
                .withArguments('sumAFile')
                .withPluginClasspath()
                .build()

        then:
        result.task(':sumAFile').outcome == TaskOutcome.SUCCESS
        expectedSumFile.exists()
        otherFile.exists()
    }

    def 'removes previous algorithm files'() {
        given:
        projectDir.newFolder("build", "checksums")
        def otherFile = getProjectDir().newFile("build/checksums/notMyFile")
        def expected256SumFile = new File(projectDir.root, "build/checksums/aFile.txt.sha256")
        def expected384SumFile = new File(projectDir.root, "build/checksums/aFile.txt.sha384")

        when:
        def result = GradleRunner.create()
                .withProjectDir(projectDir.root)
                .withArguments('sumAFile')
                .withPluginClasspath()
                .build()

        then:
        result.task(':sumAFile').outcome == TaskOutcome.SUCCESS
        expected256SumFile.exists()
        otherFile.exists()

        when:
        buildFile << """
        sumAFile {
            algorithm = Checksum.Algorithm.SHA384
        }
        """
        result = GradleRunner.create()
            .withProjectDir(projectDir.root)
            .withArguments('sumAFile')
            .withPluginClasspath()
            .build()

        then:
        result.task(':sumAFile').outcome == TaskOutcome.SUCCESS
        !expected256SumFile.exists()
        expected384SumFile.exists()
        otherFile.exists()
    }
}
