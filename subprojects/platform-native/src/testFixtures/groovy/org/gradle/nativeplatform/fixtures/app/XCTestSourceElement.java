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

package org.gradle.nativeplatform.fixtures.app;

import com.google.common.collect.Lists;
import org.gradle.api.Transformer;
import org.gradle.integtests.fixtures.SourceFile;
import org.gradle.integtests.fixtures.TestExecutionResult;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.util.CollectionUtils;

import java.util.List;

public abstract class XCTestSourceElement extends SwiftSourceElement implements XCTestElement {
    @Override
    public String getSourceSetName() {
        return "test";
    }

    @Override
    public List<SourceFile> getFiles() {
        List<SourceFile> result = Lists.newArrayList(CollectionUtils.collect(getTestSuites(), new Transformer<SourceFile, XCTestSourceFileElement>() {
            @Override
            public SourceFile transform(XCTestSourceFileElement element) {
                return element.getSourceFile();
            }
        }));

        if (OperatingSystem.current().isLinux()) {
            result.add(getLinuxMainSourceFile());
        }
        return result;
    }

    private SourceFile getLinuxMainSourceFile() {
        StringBuilder content = new StringBuilder();
//        content.append("#if !os(macOS)\n");
        content.append("  import XCTest\n");
        content.append("  XCTMain([\n");
        for (XCTestSourceFileElement testSuite : getTestSuites()) {
            content.append("    testCase([\n");
            for (XCTestCaseElement testCase : testSuite.getTestCases()) {
                content.append("      (\"" + testCase.getName() + "\", " + testCase.getName() + "),\n");
            }
            content.append("    ]),\n");
        }
        content.append("  ])\n");
//        content.append("#endif\n");

        return sourceFile("swift", "LinuxMain.swift", content.toString());
    }

    @Override
    public int getFailureCount() {
        int result = 0;
        for (XCTestElement element : getTestSuites()) {
            result += element.getFailureCount();
        }
        return result;
    }

    @Override
    public int getPassCount() {
        int result = 0;
        for (XCTestElement element : getTestSuites()) {
            result += element.getPassCount();
        }
        return result;
    }

    @Override
    public int getTestCount() {
        int result = 0;
        for (XCTestElement element : getTestSuites()) {
            result += element.getTestCount();
        }
        return result;
    }

    public abstract List<XCTestSourceFileElement> getTestSuites();

    public void assertTestCasesRan(TestExecutionResult testExecutionResult) {
        assertTestCasesRanInSuite(testExecutionResult, getTestSuites());
    }

    static void assertTestCasesRanInSuite(TestExecutionResult testExecutionResult, List<XCTestSourceFileElement> testSuites) {
        for (XCTestSourceFileElement element : testSuites) {
            element.assertTestCasesRan(testExecutionResult.testClass(element.getTestSuiteName()));
        }
    }

    public XCTestSourceElement withInfoPlist() {
        final XCTestSourceElement delegate = this;
        return new XCTestSourceElement() {
            @Override
            public List<XCTestSourceFileElement> getTestSuites() {
                return delegate.getTestSuites();
            }

            @Override
            public List<SourceFile> getFiles() {
                List<SourceFile> result = Lists.newArrayList(delegate.getFiles());
                result.add(emptyInfoPlist());
                return result;
            }

            @Override
            public String getModuleName() {
                return delegate.getModuleName();
            }

            @Override
            public XCTestSourceElement withImport(String moduleName) {
                return delegate.withImport(moduleName);
            }
        };
    }

    public XCTestSourceElement asModule(final String moduleName) {
        final XCTestSourceElement delegate = this;
        return new XCTestSourceElement() {
            @Override
            public List<XCTestSourceFileElement> getTestSuites() {
                List<XCTestSourceFileElement> result = Lists.newArrayList();
                for (XCTestSourceFileElement testSuite : delegate.getTestSuites()) {
                    result.add(testSuite.inModule(moduleName));
                }
                return result;
            }

            @Override
            public String getModuleName() {
                return moduleName;
            }

            @Override
            public XCTestSourceElement withImport(String importModuleName) {
                return delegate.withImport(importModuleName).asModule(moduleName);
            }
        };
    }

    public XCTestSourceElement withImport(final String moduleName) {
        final XCTestSourceElement delegate = this;
        return new XCTestSourceElement() {
            @Override
            public List<XCTestSourceFileElement> getTestSuites() {
                List<XCTestSourceFileElement> result = Lists.newArrayList();
                for (XCTestSourceFileElement testSuite : delegate.getTestSuites()) {
                    result.add(testSuite.withImport(moduleName));
                }
                return result;
            }

            @Override
            public String getModuleName() {
                return delegate.getModuleName();
            }
        };
    }

    public SourceFile emptyInfoPlist() {
        return sourceFile("resources", "Info.plist",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n"
                + "<plist version=\"1.0\">\n"
                + "<dict>\n"
                + "</dict>\n"
                + "</plist>");
    }
}
