#!/usr/bin/env bash
# Adds one app's files to the version's single GitHub release (`v<version>`), once.
#
# Everything shares one version (docs/VERSIONING.md), so there is one release per version: release.yml makes `v<version>` for the Home
# Assistant integration and the apps' files (APKs and Play bundles) are added to it here. Whichever job gets there first creates it.
#
#   tools/publish-release-assets.sh <version> <marker> <file>...
#
# <marker> is the start of an asset name that says this app has already been released at this version (for example
# `church-drive-manager-0.80.0-`). If the release already has such an asset the files are NOT uploaded (the first build of a version is the
# one kept, as before) and the last line printed is `new=false`; otherwise they are uploaded and it is `new=true`. All other output goes to stderr.
# Needs GH_TOKEN and GITHUB_SHA (the commit a new release is made from).
set -euo pipefail
version=${1:?version}; marker=${2:?marker}; shift 2
tag="v${version}"

if ! gh release view "$tag" >/dev/null 2>&1; then
  # Another job may make it between the check and the create: that is fine, the release exists either way.
  gh release create "$tag" --target "${GITHUB_SHA:?}" --title "$tag" --generate-notes >&2 || gh release view "$tag" >/dev/null
fi

if gh release view "$tag" --json assets --jq '.assets[].name' | grep -q "^${marker}"; then
  echo "$tag already has ${marker}*: not adding these files" >&2
  echo "new=false"
  exit 0
fi

gh release upload "$tag" "$@" --clobber >&2
echo "new=true"
