# Overview

This directory contains examples for Photon-Model provisioning.  While the
scripts are written in bash and depend on the tools listed below, they
also show examples of the HTTP verbs and JSON payloads which can be
used from any language.

# Dependencies

## Tooling

### jq

The example scripts require _jq_ for JSON parsing: http://stedolan.github.io/jq/

### xenonc

The _xenonc_ tool needs to be available in your `$PATH`.

To compile it from source, you need to have the Go toolchain installed. See: https://golang.org/doc/install.

Then, pull the source code from [Xenon Github](https://github.com/vmware/xenon) and build it with maven. Use the xenon-host-jar-with-dependencies.jar.

### genisoimage

Used by photon-model to generate iso images used for CoreOS configuration.

OSX installation:

```bash
% brew install cdrtools
% ln -s /usr/local/bin/mkisofs /usr/local/bin/genisoimage
```

## Photon-model host

You'll need a photon-model host to test against. To build from source, please see the
[Photon Model Developer Guide](https://github.com/vmware/photon-model/wiki/DeveloperGuide)

## Environment

### XENONC

The _XENONC_ environment variable must be set for use by the _xenonc_
program, for example:

```bash
% export XENONC=http://localhost:8000
