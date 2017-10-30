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
package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import spock.lang.Unroll

class StrictDependenciesIntegrationTest extends AbstractHttpDependencyResolutionTest {
    def resolve = new ResolveTestFixture(buildFile, "conf")


    def setup() {
        settingsFile << "rootProject.name = 'test'"
        resolve.prepare()
        server.start()
    }


    void "can declare a strict dependency onto an external component"() {
        given:
        def m = mavenHttpRepo.module("org", "foo", '1.0').publish()

        buildFile << """
            repositories {
                maven { url "${mavenHttpRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf('org:foo:1.0') {
                    strictVersion '1.0'
                }
            }           
        """

        when:
        m.pom.expectGet()
        m.artifact.expectGet()
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:foo:1.0")
            }
        }

    }

    void "can make a version strict"() {
        given:
        def m = mavenHttpRepo.module("org", "foo", '1.0').publish()

        buildFile << """
            repositories {
                maven { url "${mavenHttpRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf('org:foo:1.0') {
                    strictVersion()
                }
            }           
        """

        when:
        m.pom.expectGet()
        m.artifact.expectGet()
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:foo:1.0")
            }
        }

    }

    void "cannot make an empty version strict"() {
        given:
        mavenHttpRepo.module("org", "foo", '1.0').publish()

        buildFile << """
            repositories {
                maven { url "${mavenHttpRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf('org:foo') {
                    strictVersion()
                }
            }           
        """

        when:
        fails 'checkDeps'

        then:
        failure.assertHasCause("You need to provide an explicit version number using strictVersion('someVersionNumber')")

    }

    void "should fail if transitive dependency version doesn't match the strict dependency version"() {
        given:
        def foo10 = mavenHttpRepo.module("org", "foo", '1.0').publish()
        def foo11 = mavenHttpRepo.module("org", "foo", '1.1').publish()
        def bar10 = mavenHttpRepo.module('org', 'bar', '1.0')
            .dependsOn(foo11)
            .publish()

        buildFile << """
            repositories {
                maven { url "${mavenHttpRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf('org:foo') {
                    strictVersion '1.0'
                }
                conf('org:bar:1.0')
            }           
        """

        when:
        bar10.pom.expectGet()
        foo10.pom.expectGet()
        fails 'checkDeps'

        then:
        failure.assertHasCause('Bad luck')

    }

    void "should pass if transitive dependency version matches exactly the strict dependency version"() {
        given:
        def foo10 = mavenHttpRepo.module("org", "foo", '1.0').publish()
        def bar10 = mavenHttpRepo.module('org', 'bar', '1.0')
            .dependsOn(foo10)
            .publish()

        buildFile << """
            repositories {
                maven { url "${mavenHttpRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf('org:foo') {
                    strictVersion '1.0'
                }
                conf('org:bar:1.0')
            }           
        """

        when:
        bar10.pom.expectGet()
        foo10.pom.expectGet()
        bar10.artifact.expectGet()
        foo10.artifact.expectGet()
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge "org:foo:1.0", "org:foo:1.0"
                edge ("org:bar:1.0", "org:bar:1.0") {
                    edge "org:foo:1.0", "org:foo:1.0"
                }
            }
        }
    }

    void "can upgrade a non-strict dependency"() {
        given:
        def foo10 = mavenHttpRepo.module("org", "foo", '1.0').publish()
        def foo11 = mavenHttpRepo.module("org", "foo", '1.1').publish()
        def bar10 = mavenHttpRepo.module('org', 'bar', '1.0')
            .dependsOn(foo10)
            .publish()

        buildFile << """
            repositories {
                maven { url "${mavenHttpRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf('org:foo') {
                    strictVersion '1.1'
                }
                conf('org:bar:1.0')
            }           
        """

        when:
        foo11.pom.expectGet()
        bar10.pom.expectGet()
        bar10.artifact.expectGet()
        foo11.artifact.expectGet()
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge "org:foo:1.1", "org:foo:1.1"
                edge ("org:bar:1.0", "org:bar:1.0") {
                    edge ("org:foo:1.0", "org:foo:1.1").byConflictResolution()
                }
            }
        }
    }

    @Unroll
    void "should pass if transitive dependency version matches a strict dependency version range"() {
        given:
        def foo10 = mavenHttpRepo.module("org", "foo", '1.0').publish()
        def foo11 = mavenHttpRepo.module("org", "foo", '1.1').publish()
        def foo12 = mavenHttpRepo.module("org", "foo", '1.2').publish()
        def foo13 = mavenHttpRepo.module("org", "foo", '1.3').publish()
        def bar10 = mavenHttpRepo.module('org', 'bar', '1.0')
            .dependsOn(mavenHttpRepo.module("org", "foo", secondSeenInGraph))
            .publish()

        buildFile << """
            repositories {
                maven { url "${mavenHttpRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf('org:foo') {
                    strictVersion '$firstSeenInGraph'
                }
                conf('org:bar:1.0')
            }
            
            task checkDeps {
                doLast {
                    def files = configurations.conf*.name.sort()
                    assert files == ['bar-1.0.jar', 'foo-1.2.jar']
                }
            }                  
        """

        when:
        foo10.rootMetaData.expectGet()
        foo13.pom.expectGet()
        bar10.pom.expectGet()
        foo12.pom.expectGet()
        bar10.artifact.expectGet()
        foo12.artifact.expectGet()
        run 'checkDeps'

        then:
        noExceptionThrown()

        where:
        firstSeenInGraph << ['[1.0,1.3]', '1.2']
        secondSeenInGraph << ['1.2', '[1.0,1.3]']
    }

    def "should not downgrade dependency version when a transitive dependency has strict version"() {
        given:
        def foo15 = mavenHttpRepo.module("org", "foo", '15').publish()
        def foo17 = mavenHttpRepo.module("org", "foo", '17').publish()

        buildFile << """
            repositories {
                maven { url "${mavenHttpRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf('org:foo:17')
                conf project(path: 'other', configuration: 'conf')
            }                       
        """
        file("other/build.gradle") << """
            repositories {
                maven { url "${mavenHttpRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf('org:foo') {
                    strictVersion '15'
                }
            }       
        """
        settingsFile << "\ninclude 'other'"

        when:
        foo17.pom.expectGet()
        fails 'checkDeps'

        then:
        failure.assertHasCause('Bad luck')

    }

    def "should fail if 2 strict versions disagree"() {
        given:
        def foo15 = mavenHttpRepo.module("org", "foo", '15').publish()
        def foo17 = mavenHttpRepo.module("org", "foo", '17').publish()

        buildFile << """
            repositories {
                maven { url "${mavenHttpRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf('org:foo') {
                    strictVersion '17'
                }
                conf project(path: 'other', configuration: 'conf')
            }                       
        """
        file("other/build.gradle") << """
            repositories {
                maven { url "${mavenHttpRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf('org:foo:15') {
                    strictVersion '15'
                }
            }       
        """
        settingsFile << "\ninclude 'other'"

        when:
        foo17.pom.expectGet()
        fails 'checkDeps'

        then:
        failure.assertHasCause('Bad luck')

    }

    void "should pass if strict version ranges overlap"() {
        given:
        def foo10 = mavenHttpRepo.module("org", "foo", '1.0').publish()
        def foo11 = mavenHttpRepo.module("org", "foo", '1.1').publish()
        def foo12 = mavenHttpRepo.module("org", "foo", '1.2').publish()
        def foo13 = mavenHttpRepo.module("org", "foo", '1.3').publish()

        buildFile << """
            repositories {
                maven { url "${mavenHttpRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf('org:foo') {
                    strictVersion '[1.0,1.2]'
                }
                conf project(path:'other', configuration: 'conf')
            }
            
            task checkDeps {
                doLast {
                    def files = configurations.conf*.name.sort()
                    assert files == ['foo-1.2.jar']
                }
            }                  
        """
        file("other/build.gradle") << """
            repositories {
                maven { url "${mavenHttpRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf('org:foo:[1.1,1.3]') {
                    strictVersion '[1.1,1.3]'
                }
            }       
        """
        settingsFile << "\ninclude 'other'"

        when:
        foo10.rootMetaData.expectGet()
        foo12.pom.expectGet()
        foo13.pom.expectGet()
        foo12.artifact.expectGet()
        run ':checkDeps'

        then:
        noExceptionThrown()
    }

}
