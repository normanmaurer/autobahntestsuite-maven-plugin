/*
 * Copyright 2013 The Apache Software Foundation.
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
package me.normanmaurer.maven.autobahntestsuite;


import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mojo which execute the autobahntestsuite
 */
@Mojo(name = "fuzzingclient", defaultPhase = LifecyclePhase.INTEGRATION_TEST,
requiresDependencyResolution = ResolutionScope.TEST)
public class FuzzingClientMojo
        extends AbstractMojo {
    private static final List<String> ALL_CASES = Collections.unmodifiableList(Arrays.asList("*"));
    private static final Map<String, Object> OPTIONS;
    static {
        Map<String, Object> opts = new HashMap<String, Object>();
        opts.put("version", 18);
        OPTIONS = Collections.unmodifiableMap(opts);
    }

    @Parameter(defaultValue = "autobahntestsuite-agent", property="agent", required = true)
    private String agent;

    @Parameter(defaultValue = "ws://127.0.0.1:9001", property="url", required = true)
    private String url;

    @Parameter(property = "cases")
    private List<String> cases;

    @Parameter(property = "excludeCases")
    private List<String> excludeCases;

    @Parameter(property = "mainClass")
    private String mainClass;

    @Parameter(property = "failOnNonStrict")
    private boolean failOnNonStrict;

    @Component
    private MavenProject project;

    private ClassLoader getClassLoader() throws MojoExecutionException {
        try {
            List<String> classpathElements = project.getCompileClasspathElements();
            classpathElements.add(project.getBuild().getOutputDirectory() );
            classpathElements.add(project.getBuild().getTestOutputDirectory() );
            URL urls[] = new URL[classpathElements.size()];

            for ( int i = 0; i < classpathElements.size(); i++) {
                urls[i] = new File(classpathElements.get(i)).toURI().toURL();
            }
            return new URLClassLoader(urls, getClass().getClassLoader());
        } catch (Exception e) {
            throw new MojoExecutionException("Couldn't create a classloader", e);
        }
    }

    @Override
    public void execute()
            throws MojoExecutionException, MojoFailureException {
        Thread.currentThread().setContextClassLoader(getClassLoader());

        if (mainClass != null) {
            try {
               Thread.currentThread().getContextClassLoader().loadClass(mainClass).newInstance();
            } catch (Exception e) {
                throw new MojoExecutionException("Unable to start server for class " + mainClass, e);
            }
        }

        if (cases == null || cases.isEmpty()) {
            cases = ALL_CASES;
        }
        if (excludeCases == null) {
            excludeCases = Collections.emptyList();
        }
        List<FuzzingCaseResult> results = AutobahnTestSuite.runFuzzingClient(agent, url,  OPTIONS, cases, excludeCases);
        List<FuzzingCaseResult> failed = new ArrayList<FuzzingCaseResult>();
        for (FuzzingCaseResult result: results) {
            FuzzingCaseResult.Behavior behavior = result.behavior();
            if (failOnNonStrict && behavior == FuzzingCaseResult.Behavior.NON_STRICT) {
                failed.add(result);
            } else if (behavior!= FuzzingCaseResult.Behavior.OK
                    && behavior != FuzzingCaseResult.Behavior.INFORMATIONAL
                    && behavior != FuzzingCaseResult.Behavior.NON_STRICT) {
                failed.add(result);
            }
        }
        if (!failed.isEmpty()) {
            StringBuilder sb = new StringBuilder("\nFailed test cases:\n");
            for (FuzzingCaseResult result: failed) {
                sb.append("\t");
                sb.append(result.toString());
                sb.append("\n");
            }
            throw new MojoFailureException(sb.toString());
        } else {
            getLog().info("All test cases passed" );
        }
    }
}
