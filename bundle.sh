#!/bin/bash

# Extract version from the CMS Maven pom.xml
VERSION=$(grep -m1 '<version>' implementation/server/testgenesis-cms/pom.xml | sed 's/.*<version>\(.*\)<\/version>.*/\1/')
OUTPUT_FILE="testgenesis-v${VERSION}.zip"

echo "Bundling TestGenesis v${VERSION} into ${OUTPUT_FILE}..."

# Remove existing bundle if exists
if [ -f "$OUTPUT_FILE" ]; then
    rm "$OUTPUT_FILE"
fi

# Check if git is available
if ! command -v git &> /dev/null; then
    echo "Error: git is not installed or not in PATH."
    exit 1
fi

# Check if zip is available
if ! command -v zip &> /dev/null; then
    echo "Error: zip is not installed or not in PATH."
    exit 1
fi

# Create zip bundle using git ls-files to respect .gitignore
# This ensures we don't include node_modules, target folders, or IDE configs
echo "Gathering file list..."
git ls-files --cached --others --exclude-standard | zip "$OUTPUT_FILE" -@

echo "Done! Bundle created at ${OUTPUT_FILE}"
