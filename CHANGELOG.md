<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# GitHub-Actions-Manager Changelog

## Unreleased

## 1.21.6

- Fix bug failing to load annotator #131

## 1.21.5

### 🐛 Bug Fixes

- Fix bug of jobs not being refreshed while workflow is running

## 1.21.4

### 🐛 Bug Fixes

- Fix memory leak with log panel
- Workflow runs list is getting updated with the latest status

## 1.21.3

### 🐛 Bug Fixes

- Fix bug loading null value to cache #128
- Show go to GitHub settings when error occurs
- Show reload jobs button

## 1.21.2

### 🐛 Bug Fixes

- Fix bug scanning workflow files #126 #125

## 1.21.1

### 🐛 Bug Fixes

- Storing action data in project settings instead of a separate file.

## 1.21.0

### 🚀 Features

- Highlight outdated actions in workflow files. #122
- Quick fix action to update to the latest major version of the action. #122
- Error reporting directly to GitHub issues.

### 🧰 Maintenance

- Update bug-report template.

## 1.20.0

### 🚀 Features

- Tooltip on tab showing real repo and GitHub account
- Prefer GitHub account that is the owner of the repository

### 🐛 Bug Fixes

- Using new error handlers.
- Create GitHub Request executor that supports redirects. # 119

### 🧰 Maintenance

- Update deprecated code.

## 1.19.0

- Update license to GPL-3.0

### 🚀 Features

- Support for JetBrains IDEs v2024.1

### 🧰 Maintenance

- Improve tests and increase coverage
- Update dependencies to latest versions

## 1.18.0

### 🚀 Features

- Add quick-filter to show runs based on the current branch (updates when branch is updated) #115
- Add ability to position workflow runs list on top of jobs list #116
- Show link to step log when log is too large #118

### 🧰 Maintenance

- Major refactoring better performance and code quality

### 🐛 Bug Fixes

- Fix not allowing custom repositories on plugin settings

## 1.17.0

### 🚀 Features

- Extract all messages to i18n file #114
- Update info bar on jobs-panel

### 🐛 Bug Fixes

- Improve logic for identifying steps in job logs

### 🧰 Maintenance

- Update dependencies to latest versions
- Major code refactoring
- Implement tests

### 🐛 Bug Fixes

- Minor bug when unable to parse Instant

## 1.16.1

### 🐛 Bug Fixes

- Fix job log parsing dates #109

## 1.16.0

### 🚀 Features

- Downloading job logs instead of entire run logs #108

## 1.15.2

### 🐛 Bug Fixes

- Fix run in EDT thread #106

## 1.15.1

### 🧰 Maintenance

- Update dependencies to latest versions

## 1.15.0

### 🚀 Features

- Trigger workflow dispatch event #75

### 🐛 Bug Fixes

- Using camel-case variable names in POJOs #99
- WfRunsListPersistentSearchHistory state is not being save properly #102
- Fix updating logs from non-EDT #101

## 1.14.0

### 🚀 Features

- Filter by workflow type #98

### 🧰 Maintenance

- Improve filter behavior

## 1.13.5

### 🧰 Maintenance

- Add support for build 223.3 and fixed a few warnings. @wyatt-herkamp #97

## 1.13.4

### 🐛 Bug Fixes

- Fix exception thrown due to EDT thread #95

## 1.13.3

### 🐛 Bug Fixes

- Limiting requests to contributors and branches.

## 1.13.2

### 🐛 Bug Fixes

- Fix refreshing of jobs list.
- Updated dependencies

## 1.13.1

### 🐛 Bug Fixes

- Fix time shown for workflow runs and jobs.

## 1.13.0

### 🚀 Features

- Add workflow runs filter based on event.

### 🐛 Bug Fixes

- Filters now work for future IDE versions.

## 1.12.2

### 🐛 Bug Fixes

- Showing all branches and all collaborators on filters #92

## 1.12.1

### 🐛 Bug Fixes

- Fix times for workflow-runs #91
- Showing right status for jobs that haven't started
- Improved filters behavior

## 1.12.0

### 🚀 Features

- Simplified Jobs panel logic
- Add ability to filter workflow runs by actor, branch and status.

## 1.11.0

### 🚀 Features

- Add info bar with number of jobs loaded

### 🐛 Bug Fixes

- Fix bug showing seconds without padding #88
- Fix jobs request pagination, now pagesize=100 #89
- Fix updating job logs during theme change #85

## 1.10.4

### 🐛 Bug Fixes

- Fix bug when step does not have logs #83
- Support for 2023.2-EAP

## 1.10.3

### 🐛 Bug Fixes

- Fix icons for new UI look

## 1.10.2

### 🐛 Bug Fixes

- Fix error when using IntelliJ 2023.1.RC #83
- Using GHAccountManager instead of deprecated GithubAuthenticationManager

## 1.10.1

### 🐛 Bug Fixes

- Fix bug requiring to pick job after logs are loaded.

## 1.10.0

### 🚀 Features

- Add ability to configure number of workflow runs on list.
- Add ability to configure GitHub token instead of using IDE GitHub settings.

### Changed

- Support 2023.1 EAP release

## 1.9.2

### Changed

- Fix bugs refreshing workflow runs #81
- Fix bugs calling getComponent from non dispatch thread #78
- Add icon for action_required conclusion.

## 1.9.1

### Changed

- Upgrade gradle to `7.6`
- Upgrade `org.jetbrains.kotlin.jvm` from 1.7.22 to 1.8.0

## 1.9.0

### Changed

- Clean up code on `GhActionsManagerConfigurable` by @cunla in https://github.com/cunla/ghactions-manager/pull/71
- Logpanel wrap by @cunla in https://github.com/cunla/ghactions-manager/pull/72

## 1.7.0

### 🚀 Features

- Make ghactions-manager available during indexing (#66)
- Sorting jobs by completed date, or started date, else run id (#61)

### 🐛 Bug Fixes

- Fix deadlock when refreshing workflow runs (#64)

## 1.6.1

### Fixed

- Link to pull-request
- Reset log when workflow-run unselected
- Keep workflow-run selected after refresh

## 1.6.0

### Added

- Step logs - Showing failed step title in red
- Refresh of runs only for active tab

### Changed

- Better github REST API error handling

### Fixed

- Update jobs panel and log panel to loading state when a new run is selected
- Clean up more code

## 1.5.4

### Added

- Open workflow file action

### Fixed

- Update workflow run state

## 1.5.1

### Fixed

- Running refresh in background

## 1.5.0

### Added

- Ability to change how often refresh of workflow runs is done
- Refresh jobs of workflow run if still in progress

### Changed

- Allowing job log to be beneath jobs list - configurable in plugin settings

### Fixed

- Showing logs of selected job once logs are loaded

## 1.4.0

### Added

- Refresh workflow runs status in the background

## 1.3.0 - 2022-10-07

### Added

- Refresh jobs + rerun workflow run by @cunla in https://github.com/cunla/ghactions-manager/pull/40
- Update cancelled icon by @cunla in https://github.com/cunla/ghactions-manager/pull/44
- Add cancel workflow action #43 by @cunla in https://github.com/cunla/ghactions-manager/pull/46
- Guess GitHub account per repo when there are multiple GitHub accounts by @cunla
  in https://github.com/cunla/ghactions-manager/pull/48

## 1.2.0 - 2022-10-01

### Added

- New icons for in progress/queued workflows
- Ability to configure tab name for each repo (Fix #38)

### Fixed

- Exception when workflow is in progress #35

### Changed

- Logs less verbose

## 1.1.1 - 2022-08-27

### Fixed

- Bug serializing status of job
- Viewing job logs while it is in progress #34

### Changed

- Gradle version building project
- Cleanup code

## 1.1.0 - 2022-08-25

### Added

- Jobs panel view

### Fixed

- Allow empty conclusion, support in progress json - fix #30) by @cunla in #32
- Multiple instances bug by @cunla in #31

## 1.0.1

### Added

- Support for IntelliJ 2022.2

### Fixed

- Issue with `GithubApiRequestExecutorManager.getExecutor`

## 0.0.8 - 2022-06-26

### Fixed

- Issue with `GithubApiRequestExecutorManager.getExecutor`

## 0.0.7 - 2022-06-26

### Added

- Add a link to GitHub accounts settings in case GitHub account is not set #19
- Add a link from toolbar window to Toolbar Settings #21
- Toolbar settings - Resolve #18 by @cunla in https://github.com/cunla/ghactions-manager/pull/25

### Fixed

- Fix memory leak issue #22

## 0.0.6 - 2022-06-19

### Added

- Contribution guide.
- Documentation: Screenshots on README, contribution guide, etc.
- Message when there is no GitHub account configured.
- Message when there is no repository in the project.

### Changed

- Improved code structure

## 0.0.5 - 2022-06-14

### Added

- Plugin Icon
- Add tab per repo
- Add log folding
- Remove redundant code and deprecated code
- Migrated code from https://github.com/Otanikotani/view-github-actions-idea-plugin
- Initial scaffold created
  from [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template)
