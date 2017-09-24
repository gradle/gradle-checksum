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
          id 'org.gradle.crypto.checksum' version '1.1.0'
        }
        
        import org.gradle.crypto.checksum.Checksum
        import java.nio.file.Paths
        
        task makeAFile {
          doLast {
            file('build').mkdir()
            file(Paths.get('build', 'aFile.txt')) << 'Hello, Checksum!'
          }
        }
        
        task sumAFile(type: Checksum, dependsOn: 'makeAFile') {
          files = files(Paths.get('build', 'aFile.txt'))
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
        'MD5'    | '5691b5ad8da499aa156eda19eafa8ca3'
        'SHA256' | '17dc1d7c1912574351e67069fef64e603d435c7b04197ddb95237cc93ebbe973'
        'SHA384' | '8c92bd249565af6c1c323450e7f2834a306da1295fa305cfece7c1af1617df6e0213f8b81c8c2765407908f7d2065ced'
        'SHA512' | '32ae12e4d047303297158cd23a93ba5d7f531b0b8597949a800e0ed57c0d0f6463563f9cb16b99f208bca97cd7471cc4d3ae1ab2b88c026016975dff68c39eb9'
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
