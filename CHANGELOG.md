## Changelog

- For newer versions, see [GitHub Releases](https://github.com/jenkinsci/pipeline-graph-analysis-plugin/releases)

### 1.12 (22 Nov 2021)

This plugin now requires Jenkins 2.303.3 or newer.

-   [PR #33](https://github.com/jenkinsci/pipeline-graph-analysis-plugin/pull/33)
    Update and clean up POM, adapt to new dependencies
-   [PR #34](https://github.com/jenkinsci/pipeline-graph-analysis-plugin/pull/34)
    Add dependabot
-   [PR #35](https://github.com/jenkinsci/pipeline-graph-analysis-plugin/pull/35)
    Use container agent in Jenkinsfile and test with Java 11
-   [PR #36](https://github.com/jenkinsci/pipeline-graph-analysis-plugin/pull/36)
    Update BOM from 987.v4ade2e49fe70 to 1008.vb9e22885c9cf
-   [PR #37](https://github.com/jenkinsci/pipeline-graph-analysis-plugin/pull/37)
    Bump json from 20190722 to 20210307


### 1.11 (02 Jun 2021)

This plugin now requires Jenkins 2.222.4 or newer.

-   [PR #26](https://github.com/jenkinsci/pipeline-graph-analysis-plugin/pull/26)
    Make StatusAndTiming#findWarningBetween public
-   [PR #27](https://github.com/jenkinsci/pipeline-graph-analysis-plugin/pull/27)
    Consistently use sandbox for CpsFlowDefinitions
-   [PR #28](https://github.com/jenkinsci/pipeline-graph-analysis-plugin/pull/28)
    Code cleanup; Replace Guava functional primitives with Java API
-   [PR #29](https://github.com/jenkinsci/pipeline-graph-analysis-plugin/pull/29)
    Bump plugin parent POM
-   [PR #30](https://github.com/jenkinsci/pipeline-graph-analysis-plugin/pull/30)
    Added CHANGELOG
-   [PR #31](https://github.com/jenkinsci/pipeline-graph-analysis-plugin/pull/31)
    Bump plugin POM; use plugin BOM


### 1.10 (15 May 2019)

This plugin now requires Jenkins 2.138.4 or newer.

-   [JENKINS-39203](https://issues.jenkins-ci.org/browse/JENKINS-39203) -
    Integrate with a new API provided by Pipeline: API Plugin version
    2.34 that makes it possible for Pipeline steps to report additional
    status information. In combination with new Pipeline steps provided
    by Pipeline: Basic Steps Plugin version 2.16 and updates to other
    steps such as those in JUnit Plugin version 1.28, this change allows
    visualizations such as Blue Ocean to identify the stage that caused
    a build to become `UNSTABLE` and display it appropriately.
    -   This behavior can be disabled by setting the system
        property `org.jenkinsci.plugins.workflow.pipelinegraphanalysis.StatusAndTiming.DISABLE_WARNING_ACTION_LOOKUP` to
        "true", or by setting the static variable of the same name to
        `true` via the Jenkins script console.
-   [JENKINS-43292](https://issues.jenkins-ci.org/browse/JENKINS-43292) -
    The status of aborted parallel branches (for example branches that
    are aborted due to `failFast: true`) is now correctly computed as
    `ABORTED` rather than `FAILURE`.
-   [JENKINS-55255](https://issues.jenkins-ci.org/browse/JENKINS-55255) -
    The status of chunks that complete with
    a `FlowInterruptedException` is now computed
    using `FlowInterruptedException.result` instead of assuming the
    result is `ABORTED`. 

### 1.9 (14 Nov 2018)

-   Bugfix: Regression from 1.8 - AbortException is a true failure not
    just an aborted case

### 1.8 (14 Nov 2018)

-   Mark parts of the Pipeline as ABORTED status when we're explicitly
    halting vs timeouts, etc (thanks community member Georg Henzler)

### 1.7 (25 June 2018)

-   Bump dependencies to resolve plugin compatibility test (PCT)
    failures
-   Better handle TimeoutExceptions & InterruptedException while trying
    to determine if step is an input step

### 1.6 (3 Jan 2018)

-   Support skipped status for parallels (Declarative Pipeline)
    - [JENKINS-47219](https://issues.jenkins-ci.org/browse/JENKINS-47219)
-   Small restructure of how license info is stored (thanks to [Victor
    Martinez](https://github.com/v1v))

### 1.5 (10 Aug 2017)

-   Support GenericStatus.QUEUED status for
    pipelines [JENKINS-44981](https://issues.jenkins-ci.org/browse/JENKINS-44981)
-   Add new coerce API for API consumers to use to support new
    GenericStatus results without back-compatibility breakage

### 1.4 (5 June 2017)

-   Bump dependencies and additional testcase for
    [JENKINS-38536](https://issues.jenkins-ci.org/browse/JENKINS-38536)

### 1.3 (2 Dec 2016)

-   Fix incorrect status coding of PAUSED\_PENDING\_INPUT branches when
    multiple branches are present
    ([JENKINS-40139](https://issues.jenkins-ci.org/browse/JENKINS-40139)).

### 1.2 (30 Sept 2016)

-   Javadocs fixes to pass Java 8's more stringent doclint
    \[[JENKINS-38632](https://issues.jenkins-ci.org/browse/JENKINS-38632)\]

### 1.1 (25 August 2016)

-   Provide support for block-scoped stages using final release version
    of stage step
-   Use a more robust algorithm to discover stages (less susceptible to
    false positives)

## 1.0 (25 August 2016)

-   Initial release of plugin
