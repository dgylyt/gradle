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

package org.gradle.integtests.resource.gcs.maven

import org.gradle.integtests.resource.gcs.AbstractGcsDependencyResolutionTest
import org.gradle.integtests.resource.gcs.fixtures.MavenGcsModule

class MavenGcsRepoResolveIntegrationTest extends AbstractGcsDependencyResolutionTest {

    String artifactVersion = "1.85"
    MavenGcsModule module

    @Override
    String getRepositoryPath() {
        return '/maven/release/'
    }

    def setup(){
        module = getMavenGcsRepo().module("org.gradle", "test", artifactVersion)
    }

    def "should not download artifacts when already present in maven home"() {
        setup:
        module.publish()

        m2.generateGlobalSettingsFile()
        def localModule = m2.mavenRepo().module("org.gradle", "test", artifactVersion).publish()

        buildFile << mavenGcsRepoDsl()
        buildFile << """
configurations { compile }

dependencies{
    compile 'org.gradle:test:$artifactVersion'
}

task retrieve(type: Sync) {
    from configurations.compile
    into 'libs'
}
"""
        and:
        module.pom.expectMetadataRetrieve()
        module.pom.sha1.expectDownload()
        module.artifact.expectMetadataRetrieve()
        module.artifact.sha1.expectDownload()

        when:
        using m2

        then:
        succeeds 'retrieve'

        and:
        localModule.artifactFile.assertIsCopyOf(module.artifactFile)
        localModule.pomFile.assertIsCopyOf(module.pomFile)
        file('libs/test-1.85.jar').assertIsCopyOf(module.artifactFile)

        and:
        assertLocallyAvailableLogged(module.pom, module.artifact)
    }

    def "should download artifacts when maven local artifacts are different to remote "() {
        setup:
        module.publish()
        m2.generateGlobalSettingsFile()
        def localModule = m2.mavenRepo().module("org.gradle", "test", artifactVersion).publishWithChangedContent()

        buildFile << mavenGcsRepoDsl()
        buildFile << """
configurations { compile }

dependencies{
    compile 'org.gradle:test:$artifactVersion'
}

task retrieve(type: Sync) {
    from configurations.compile
    into 'libs'
}
"""
        and:
        module.pom.expectMetadataRetrieve()
        module.pom.sha1.expectDownload()
        module.pom.expectDownload()
        module.artifact.expectMetadataRetrieve()
        module.artifact.sha1.expectDownload()
        module.artifact.expectDownload()

        when:
        using m2

        then:
        succeeds 'retrieve'

        and:
        localModule.artifactFile.assertIsDifferentFrom(module.artifactFile)
        localModule.pomFile.assertIsDifferentFrom(module.pomFile)
        file('libs/test-1.85.jar').assertIsCopyOf(module.artifactFile)
    }
}
