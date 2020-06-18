Maven Cloner
============

Clone / mirror all or part of a remote maven repository into another one.

**⚠️ This project is currently a work-in-progress.**

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

The command-line arguments are as follows:

Argument        | Mandatory | Description
----------------|-----------|---------------
source.root-url | Yes | The root URL from which the source repository should be scraped.
source.user     | No  | The username used on the source repository.
source.password | No  | The password used on the source repository.
mirror-path     | No  | The path on the local file system to be used for mirroring repository content (default: `./mirror/`)

(To be continued...)