Maven Cloner
============

Clone / mirror all or part of a remote maven repository into another one.

## Motivation

First and foremost: there are a ton of better options out there for moving data from one Maven repository to another. Some of them include:

* set up a local Maven repository as mirror for your legacy one,
* use the repository manufacturer's tooling,
* ask the team running the repositories for help,
* re-upload your artifacts and metadata manually (if there are few of them),
* don't do it: are you sure you really have to move parts or all of your artifact history to another Maven repository server?

Sometimes though, murphy puts you into a situation where all else fails. This is what let to the creation of this repository. Without going into details: we were faced with migrating repositories from a private Nexus2 to Nexus3 without access to its storage or admin access to the instances. Nexus2 not being equipped with a REST API, the smallest common denominator was using the "index"-style HTML pages it generates when accessing the repository paths with a browser directly. Since this is a pretty common way of navigating a Maven repository, this might work with other repository products as well, but as always: no guarantee.

## Be responsible

If you cannot use any of the options above and have to use this software, please do not abuse it. Do not hammer Maven repositories. Respect the services and the people who run them.

## Usage

Run the tool like so:

    java -jar mvncloner.jar <arguments>

Each argument is prepended with "`--`".

### Requirements

* You need at least Java 11 to run the tool.
* Obviously: read access on the source repository, write access on the target repository.
* The source maven repository has to provide an "index-like" html view of your repository (successfully tested with Nexus2 and Nexus3).
* The destination repository must accept HTTP PUT requests for storing maven artifacts and accept HTTP Basic authentication (successfully tested with Nexus3).

### Configuration
The command-line arguments are as follows (arguments are prepended with "`--`"):

Argument        | Mandatory | Description
----------------|----------|---------------
source.root-url          | Yes      | The root URL from which the source repository should be scraped.
source.user              | No       | The username used on the source repository (default: no authentication).
source.password          | No       | The password used on the source repository (default: no authentication).
source.download-interval | No       | The interval between downloads, value in milliseconds, used to reduce load on source-server (default: 1000) 
mirror-path              | No       | The path on the local file system to be used for mirroring repository content (default: `./mirror/`)
target.root-url          | Yes      | The root URL where the content of the mirror repository should be uploaded.
target.user              | No       | The username used on the target repository (default: no authentication).
target.password          | No       | The password used on the target repository (default: no authentication).
target.upload-interval   | No       | The interval between uploads, value in milliseconds, used to reduce load on target-server (default: 1000)
target.concurrent-uploads| No       | Number of parallel uploads, parallelism is only supported on same folder, like foo/bar/artifact/1.0.0/ (default: 2)
target.skip-existing     | No       | Boolean value (`true` or `false`).<p>If set to `true`, skip upload of artifacts that already exist on the target. Setting this to `true` also adds an additionnal http HEAD request before each upload to check whether the file already exists on the target.<p>Setting this to `false` will skip the http HEAD request and will upload every mirrored file regardles of whether it already exists on target. (default: `false`)
target.abort-on-error    | No       | Boolean value (`true` or `false`).<p>If set to `true`, abort processing immediately when an upload fails. If set to `false`, continue uploading until all mirrored files have been processed, regardless of errors. (default: `true`) 
actions                  | No       | Comma-separated list of actions to be performed.<br/>Possible values:<ul><li>**`check`:** just check source and target connections by trying to read the repositories' indexes. This is a read-only action and doesn't write anything, neither locally nor rempotely.</li><li>**`mirror`:** copy content from the source repository to the `mirror-path` on the local filesystem</li><li>**`publish`:** upload content from the `mirror-path` on the local filesystem to the target repository</li></ul>(default: `mirror,publish`)

### Example

    java -jar mvncloner.jar \
        --source.root-url=https://sourcerepo/nexus/content/repositories/my-source-repo/ \
        --target.root-url=https://targetrepo/repository/my-target-repo/ \
        --target.user=target-user \
        --target.password=target-pwd

This would:
* mirror everything starting from the anonymously accessible repository location `https://sourcerepo/nexus/content/repositories/my-source-repo/` and then
* publish everything to the target repository at `https://targetrepo/repository/my-target-repo/` using `target-user` as username and `target-pwd` as password.

## Noteworthy

### Resumability
* The mirroring can be resumed later: artifacts already present in the mirror-path won't be downloaded again (no consistency checks are done though!).
* If `target.skip-existing` is set to `true`, the publish action will still run through all mirrored files to check whether they're present on target, but won't re-transfer them if they're already present (no consistency checks are done though!).

### Publishing path consistency
You can:
* mirror only a part of a repository.
* first mirror some repositorie's content and then manually modify things in your `mirror-path` before you re-upload it to your target repository.

However, when publishing from your `mirror-path`, make sure the paths to your artifacts (starting from `mirror-path`) match those in your POM. We found e.g. that in our case, Nexus3 refuses uploads whose path doesn't match the usual maven structure of `<path/to/group-id>/<artifact-id>/<version>/<artifact-file-version.xyz>` with an HTTP 400 error but without further visible hints at the cause.
